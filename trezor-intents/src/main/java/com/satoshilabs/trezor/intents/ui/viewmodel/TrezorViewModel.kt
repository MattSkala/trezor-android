package com.satoshilabs.trezor.intents.ui.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.google.protobuf.Message
import com.satoshilabs.trezor.intents.ui.data.GetAddressResult
import com.satoshilabs.trezor.intents.ui.data.GetPublicKeyResult
import com.satoshilabs.trezor.intents.ui.data.InitializeResult
import com.satoshilabs.trezor.intents.ui.data.TrezorResult
import com.satoshilabs.trezor.lib.TrezorException
import com.satoshilabs.trezor.lib.TrezorManager
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage
import com.satoshilabs.trezor.lib.protobuf.TrezorType
import timber.log.Timber
import java.nio.charset.Charset
import java.util.concurrent.LinkedBlockingQueue

class TrezorViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        val TAG = "TrezorViewModel"
    }

    enum class State {
        DISCONNECTED,
        CONNECTED,
        FAILURE,
        PIN_MATRIX_REQUEST,
        PASSPHRASE_REQUEST,
        BUTTON_REQUEST,
        TX_REQUEST
    }

    lateinit var trezorManager: TrezorManager

    private val handlerThread = HandlerThread("HandlerThread")
    private val backgroundHandler: Handler
    private val mainThreadHandler: Handler = Handler(Looper.getMainLooper())

    val pinMatrixEntry = LinkedBlockingQueue<String>(1)
    val passphraseEntry = LinkedBlockingQueue<String>(1)

    var initialized: Boolean = false

    val state: MutableLiveData<State> = MutableLiveData()
    val result: MutableLiveData<TrezorResult> = MutableLiveData()
    var buttonRequest: TrezorMessage.ButtonRequest? = null
    //var transaction: TrezorType.TransactionType? = null

    val trezorConnectionChangedReceiver = object : TrezorManager.TrezorConnectionChangedReceiver() {
        override fun onTrezorConnectionChanged(connected: Boolean) {
            Log.d(TAG, "trezorConnectionChangedReceiver: onTrezorConnectionChanged: connected = " + connected)
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
            Log.d(TAG, "usbPermissionReceiver: onUsbPermissionResult: granted = " + granted)

            if (granted) {
                state.value = State.CONNECTED
            }
        }
    }

    init {
        handlerThread.start()
        backgroundHandler = Handler(handlerThread.looper)
    }

    fun init() {
        if (initialized) return

        trezorManager = TrezorManager(getApplication())
        state.value = State.DISCONNECTED

        if (trezorManager.tryConnectDevice()) {
            Timber.tag(TAG).d("device is connected")
            state.value = State.CONNECTED
        } else {
            trezorManager.requestDevicePermissionIfCan(false)
        }

        initialized = true
    }

    override fun onCleared() {
        handlerThread.quit()
    }

    fun sendMessage(message: Message) {
        backgroundHandler.post {
            Log.d(TAG, "sendMessage " + message)
            try {
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

    fun signTx(tx: TrezorType.TransactionType, inputTxs: Map<ByteArray, TrezorType.TransactionType>) {
        backgroundHandler.post {
            signTxInternal(tx, inputTxs)
        }
    }

    fun sendPinMatrixAck(pin: String) {
        pinMatrixEntry.put(pin)
    }

    fun sendPassphraseAck(passphrase: String) {
        passphraseEntry.put(passphrase)
    }

    fun sendCancel() {
        sendMessage(TrezorMessage.Cancel.newBuilder().build())
    }

    private fun handleResponse(message: Message) {
        Log.d(TAG, "handleResponse " + message)

        when (message) {
            is TrezorMessage.PublicKey -> {
                result.value = GetPublicKeyResult(message)
            }
            is TrezorMessage.Failure -> {
                state.value = State.FAILURE
            }
            is TrezorMessage.Features -> {
                result.value = InitializeResult(message)
            }
            is TrezorMessage.Address -> {
                result.value = GetAddressResult(message)
            }
        }
    }

    private fun signTxInternal(tx: TrezorType.TransactionType,
                               inputTxs: Map<ByteArray, TrezorType.TransactionType>) {
        try {
            val signTx = TrezorMessage.SignTx.newBuilder()
                    .setInputsCount(tx.inputsCount)
                    .setOutputsCount(tx.outputsCount)
                    .build()

            var response = trezorManager.sendMessage(signTx)

            while (true) {
                response = handleCommonRequests(response)

                if (response !is TrezorMessage.TxRequest) {
                    Log.e(TAG, "Unexpected response: $response")
                    return
                }

                Log.d(TAG, response.toString())

                // TODO: save signed transaction part

                if (response.requestType == TrezorType.RequestType.TXFINISHED) {
                    // Transaction is signed
                    // TODO: return signed transaction
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
                handleCommonRequests(handlePinMatrixRequest(message))
            is TrezorMessage.PassphraseRequest ->
                handleCommonRequests(handlePassphraseRequest(message))
            is TrezorMessage.ButtonRequest ->
                handleCommonRequests(handleButtonRequest(message))
            else -> message
        }
    }

    private fun handlePinMatrixRequest(pinMatrixRequest: TrezorMessage.PinMatrixRequest): Message {
        mainThreadHandler.post {
            state.value = State.PIN_MATRIX_REQUEST
        }

        val pin = try {
            pinMatrixEntry.take()
        } catch (e: InterruptedException) {
            ""
        }

        val pinMatrixAck = TrezorMessage.PinMatrixAck.newBuilder()
                .setPin(pin)
                .build()

        return trezorManager.sendMessage(pinMatrixAck)
    }

    private fun handlePassphraseRequest(passphraseRequest: TrezorMessage.PassphraseRequest): Message {
        mainThreadHandler.post {
            state.value = State.PASSPHRASE_REQUEST
        }

        val passphrase = try {
            passphraseEntry.take()
        } catch (e: InterruptedException) {
            ""
        }

        val passphraseAck = TrezorMessage.PassphraseAck.newBuilder()
                .setPassphrase(passphrase)
                .build()

        return trezorManager.sendMessage(passphraseAck)
    }

    private fun handleButtonRequest(buttonRequest: TrezorMessage.ButtonRequest): Message {
        mainThreadHandler.post {
            // TODO: refactor passing requests to activity
            this.buttonRequest = buttonRequest
            state.value = State.BUTTON_REQUEST
        }

        val buttonAck = TrezorMessage.ButtonAck.newBuilder().build()
        return trezorManager.sendMessage(buttonAck)
    }

    private fun handleTxRequest(unsignedTx: TrezorType.TransactionType,
                                inputTxs: Map<ByteArray, TrezorType.TransactionType>,
                                message: TrezorMessage.TxRequest): Message {

        inputTxs.forEach {
            Log.d(TAG, "related tx: ${it.key.toString(Charset.defaultCharset())}}")
        }

        val tx = if (message.details.hasTxHash()) {
            // TREZOR requested information about a previous transaction
            // TODO: check txid encoding
            val txidBytes = message.details.txHash.toByteArray()
            val txid = message.details.txHash.toByteArray().toString(Charset.defaultCharset())
            Log.d(TAG, "required tx: ${message.details.txHash.toString(Charset.defaultCharset())} $txid")
            inputTxs[txidBytes] ?: throw Exception("Related input transaction not provided")
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
                val output = tx.getOutputs(message.details.requestIndex)
                val responseTx = TrezorType.TransactionType.newBuilder()
                        .addOutputs(output)
                        .build()
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
