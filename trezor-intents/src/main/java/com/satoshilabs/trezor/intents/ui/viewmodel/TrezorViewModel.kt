package com.satoshilabs.trezor.intents.ui.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.google.protobuf.ByteString
import com.google.protobuf.Message
import com.satoshilabs.trezor.intents.toHex
import com.satoshilabs.trezor.intents.ui.data.*
import com.satoshilabs.trezor.lib.TrezorException
import com.satoshilabs.trezor.lib.TrezorManager
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage
import com.satoshilabs.trezor.lib.protobuf.TrezorType
import timber.log.Timber
import java.util.concurrent.LinkedBlockingQueue

class TrezorViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "TrezorViewModel"

        private const val POISON = ""
    }

    enum class State {
        DISCONNECTED,
        CONNECTED,
        PIN_MATRIX_REQUEST,
        PASSPHRASE_REQUEST,
        BUTTON_REQUEST
    }

    lateinit var trezorManager: TrezorManager

    private val handlerThread = HandlerThread("HandlerThread")
    private val backgroundHandler: Handler
    private val mainThreadHandler: Handler = Handler(Looper.getMainLooper())

    private val pinMatrixEntry = LinkedBlockingQueue<String>(1)
    private val passphraseEntry = LinkedBlockingQueue<String>(1)

    val state = MutableLiveData<State>()
    val result = MutableLiveData<TrezorResult>()
    var failure = MutableLiveData<FailureResult>()
    var buttonRequest: TrezorMessage.ButtonRequest? = null

    var requestedDeviceState: ByteString? = null
    var deviceState: ByteString? = null

    val trezorConnectionChangedReceiver = object : TrezorManager.TrezorConnectionChangedReceiver() {
        override fun onTrezorConnectionChanged(connected: Boolean) {
            if (connected) {
                trezorManager.requestDevicePermissionIfCan(true)
            } else {
                trezorManager.closeDeviceConnection()
                state.value = State.DISCONNECTED
            }
        }
    }

    val usbPermissionReceiver = object : TrezorManager.UsbPermissionReceiver() {
        override fun onUsbPermissionResult(granted: Boolean) {
            if (granted) {
                state.value = State.CONNECTED
            }
        }
    }

    init {
        handlerThread.start()
        backgroundHandler = Handler(handlerThread.looper)

        trezorManager = TrezorManager(getApplication())
        state.value = State.DISCONNECTED

        if (trezorManager.tryConnectDevice()) {
            Timber.tag(TAG).d("device is connected")
            state.value = State.CONNECTED
        } else {
            trezorManager.requestDevicePermissionIfCan(false)
        }
    }

    override fun onCleared() {
        handlerThread.quit()
    }

    /**
     * Sends a message to TREZOR asynhronously and handles the response.
     */
    fun sendMessage(message: Message) {
        backgroundHandler.post {
            Log.d(TAG, "sendMessage $message")
            try {
                if (requestedDeviceState != null) {
                    val init = TrezorMessage.Initialize.newBuilder()
                            .setState(requestedDeviceState).build()
                    trezorManager.sendMessage(init)
                }

                var response = trezorManager.sendMessage(message)
                response = handleCommonRequests(response)

                mainThreadHandler.post {
                    handleResponse(response)
                }
            } catch (e: TrezorException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Signs a transaction.
     *
     * @param tx A transaction to sign.
     * @param inputTxs Details of the previous transactions that are being spent in the new transaction.
     */
    fun signTx(tx: TrezorType.TransactionType, inputTxs: Map<String, TrezorType.TransactionType>) {
        backgroundHandler.post {
            signTxInternal(tx, inputTxs)
        }
    }

    /**
     * Sends a pin entered by the user.
     */
    fun sendPinMatrixAck(pin: String) {
        pinMatrixEntry.put(pin)
    }

    /**
     * Cancels current pin matrix request.
     */
    fun cancelPinMatrixRequest() {
        pinMatrixEntry.put(POISON)
    }

    /**
     * Sends a passphrase entered by the user.
     */
    fun sendPassphraseAck(passphrase: String) {
        passphraseEntry.put(passphrase)
    }

    /**
     * Cancels current passphrase request.
     */
    fun cancelPassphraseRequest() {
        passphraseEntry.put(POISON)
    }

    /**
     * Cancels the current action.
     */
    fun sendCancel() {
        sendMessage(TrezorMessage.Cancel.newBuilder().build())
    }

    private fun handleResponse(message: Message) {
        Log.d(TAG, "handleResponse $message")

        when (message) {
            is TrezorMessage.Failure -> {
                failure.value = FailureResult(ErrorType.TREZOR_FAILURE, message)
            }
            else -> result.value = GenericResult(message, deviceState)
        }
    }

    private fun signTxInternal(tx: TrezorType.TransactionType,
                               inputTxs: Map<String, TrezorType.TransactionType>) {
        try {
            val signTx = TrezorMessage.SignTx.newBuilder()
                    .setInputsCount(tx.inputsCount)
                    .setOutputsCount(tx.outputsCount)
                    .build()

            var response = trezorManager.sendMessage(signTx)

            val signedTxBytes = ArrayList<Byte>(1024)

            while (true) {
                response = handleCommonRequests(response)

                if (response is TrezorMessage.Failure) {
                    Log.e(TAG, "Failure: " + response.code + ": " + response.message)
                    mainThreadHandler.post {
                        failure.value = FailureResult(ErrorType.TREZOR_FAILURE,
                                response as TrezorMessage.Failure)
                    }
                    return
                }

                if (response !is TrezorMessage.TxRequest) {
                    Log.e(TAG, "Unexpected response: $response")
                    mainThreadHandler.post {
                        failure.value = FailureResult(ErrorType.UNEXPECTED_MESSAGE)
                    }
                    return
                }

                Log.d(TAG, response.toString())

                if (response.hasSerialized() && response.serialized.hasSerializedTx()) {
                    signedTxBytes += response.serialized.serializedTx.toByteArray().toList()
                }

                if (response.requestType == TrezorType.RequestType.TXFINISHED) {
                    // Transaction is signed
                    mainThreadHandler.post {
                        val signedTx = signedTxBytes.toByteArray().toHex()
                        result.value = SignTxResult(signedTx, deviceState)
                    }
                    break
                }

                response = handleTxRequest(tx, inputTxs, response)
            }
        } catch (e: TrezorException) {
            e.printStackTrace()
        }
    }

    private fun handleCommonRequests(message: Message): Message {
        return when (message) {
            is TrezorMessage.PinMatrixRequest ->
                handleCommonRequests(handlePinMatrixRequest())
            is TrezorMessage.PassphraseRequest ->
                handleCommonRequests(handlePassphraseRequest(message))
            is TrezorMessage.ButtonRequest ->
                handleCommonRequests(handleButtonRequest(message))
            is TrezorMessage.PassphraseStateRequest ->
                handleCommonRequests(handlePassphraseStateRequest(message))
            else -> message
        }
    }

    private fun handlePinMatrixRequest(): Message {
        mainThreadHandler.post {
            state.value = State.PIN_MATRIX_REQUEST
        }

        val pin = try {
            pinMatrixEntry.take()
        } catch (e: InterruptedException) {
            ""
        }

        val pinMatrixAck = if (pin !== POISON) {
            TrezorMessage.PinMatrixAck.newBuilder()
                    .setPin(pin)
                    .build()
        } else {
            TrezorMessage.Cancel.getDefaultInstance()
        }

        return trezorManager.sendMessage(pinMatrixAck)
    }

    private fun handlePassphraseRequest(passphraseRequest: TrezorMessage.PassphraseRequest): Message {
        if (passphraseRequest.onDevice) {
            mainThreadHandler.post {
                state.value = State.BUTTON_REQUEST
            }
            val passphraseAckBuilder = TrezorMessage.PassphraseAck.newBuilder()
            if (requestedDeviceState != null) {
                passphraseAckBuilder.state = requestedDeviceState
            }
            val passphraseAck = passphraseAckBuilder.build()
            return trezorManager.sendMessage(passphraseAck)
        }

        mainThreadHandler.post {
            state.value = State.PASSPHRASE_REQUEST
        }

        val passphrase = try {
            passphraseEntry.take()
        } catch (e: InterruptedException) {
            ""
        }

        val passphraseAck = if (passphrase !== POISON) {
            val passphraseAckBuilder = TrezorMessage.PassphraseAck.newBuilder()
                    .setPassphrase(passphrase)
            if (requestedDeviceState != null) {
                passphraseAckBuilder.state = requestedDeviceState
            }
            passphraseAckBuilder.build()
        } else {
            TrezorMessage.Cancel.getDefaultInstance()
        }

        return trezorManager.sendMessage(passphraseAck)
    }

    private fun handleButtonRequest(buttonRequest: TrezorMessage.ButtonRequest): Message {
        mainThreadHandler.post {
            this.buttonRequest = buttonRequest
            state.value = State.BUTTON_REQUEST
        }

        val buttonAck = TrezorMessage.ButtonAck.newBuilder().build()
        return trezorManager.sendMessage(buttonAck)
    }

    private fun handlePassphraseStateRequest(stateRequest: TrezorMessage.PassphraseStateRequest): Message {
        if (requestedDeviceState != null) {
            if (stateRequest.state != requestedDeviceState) {
                val init = TrezorMessage.Initialize.newBuilder()
                        .setState(requestedDeviceState).build()
                mainThreadHandler.post {
                    failure.value = FailureResult(ErrorType.WRONG_PASSPHRASE)
                }
                return trezorManager.sendMessage(init)
            }
        }

        deviceState = stateRequest.state

        val stateAck = TrezorMessage.PassphraseStateAck.getDefaultInstance()
        return trezorManager.sendMessage(stateAck)
    }

    private fun handleTxRequest(unsignedTx: TrezorType.TransactionType,
                                inputTxs: Map<String, TrezorType.TransactionType>,
                                message: TrezorMessage.TxRequest): Message {

        val tx = if (message.details.hasTxHash()) {
            // TREZOR requested information about a previous transaction
            val txid = message.details.txHash.toByteArray().toHex()
            inputTxs[txid] ?: throw Exception("Related input transaction not provided")
        } else {
            // TREZOR requested information about the transaction being signed
            unsignedTx
        }

        return when (message.requestType) {
            TrezorType.RequestType.TXMETA -> {
                val responseTx = TrezorType.TransactionType.newBuilder()
                        .setVersion(tx.version)
                        .setLockTime(tx.lockTime)
                        .setInputsCnt(tx.inputsCnt)
                        .setOutputsCnt(tx.outputsCnt)
                        .setExtraDataLen(tx.extraDataLen)
                        .build()
                val responseMessage = TrezorMessage.TxAck.newBuilder()
                        .setTx(responseTx)
                        .build()
                Log.d(TAG, responseMessage.toString())
                trezorManager.sendMessage(responseMessage)
            }
            TrezorType.RequestType.TXINPUT -> {
                val input = tx.getInputs(message.details.requestIndex)
                val responseTx = TrezorType.TransactionType.newBuilder()
                        .addInputs(input)
                        .build()
                val responseMessage = TrezorMessage.TxAck.newBuilder()
                        .setTx(responseTx)
                        .build()
                Log.d(TAG, responseMessage.toString())
                trezorManager.sendMessage(responseMessage)
            }
            TrezorType.RequestType.TXOUTPUT -> {
                val responseTx = if (message.details.hasTxHash()) {
                    val output = tx.getBinOutputs(message.details.requestIndex)
                    TrezorType.TransactionType.newBuilder()
                            .addBinOutputs(output)
                            .build()
                } else {
                    val output = tx.getOutputs(message.details.requestIndex)
                    TrezorType.TransactionType.newBuilder()
                            .addOutputs(output)
                            .build()
                }
                val responseMessage = TrezorMessage.TxAck.newBuilder()
                        .setTx(responseTx)
                        .build()
                Log.d(TAG, responseMessage.toString())
                trezorManager.sendMessage(responseMessage)
            }
            else -> throw Exception("Unexpected request: $message")
        }
    }
}
