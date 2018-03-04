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
import timber.log.Timber

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
        BUTTON_REQUEST
    }

    lateinit var trezorManager: TrezorManager

    private val handlerThread = HandlerThread("HandlerThread")
    private val backgroundHandler: Handler
    private val mainThreadHandler: Handler = Handler(Looper.getMainLooper())

    var initialized: Boolean = false

    val state: MutableLiveData<State> = MutableLiveData()
    val result: MutableLiveData<TrezorResult> = MutableLiveData()
    var buttonRequest: TrezorMessage.ButtonRequest? = null

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
                val response = trezorManager.sendMessage(message)

                mainThreadHandler.post {
                    handleResponse(response)
                }
            } catch (e: TrezorException) {
                e.printStackTrace()
            }
        }
    }

    fun sendPinMatrixAck(pin: String) {
        sendMessage(TrezorMessage.PinMatrixAck.newBuilder()
                .setPin(pin)
                .build())
    }

    fun sendPassphraseAck(passphrase: String) {
        sendMessage(TrezorMessage.PassphraseAck.newBuilder()
                .setPassphrase(passphrase)
                .build())
    }

    fun sendCancel() {
        sendMessage(TrezorMessage.Cancel.newBuilder().build())
    }

    private fun sendButtonAck() {
        sendMessage(TrezorMessage.ButtonAck.newBuilder().build())
    }

    private fun handleResponse(message: Message) {
        Log.d(TAG, "handleResponse " + message)

        when (message) {
            is TrezorMessage.PinMatrixRequest -> {
                state.value = State.PIN_MATRIX_REQUEST
            }
            is TrezorMessage.PassphraseRequest -> {
                state.value = State.PASSPHRASE_REQUEST
            }
            is TrezorMessage.ButtonRequest -> {
                buttonRequest = message
                state.value = State.BUTTON_REQUEST
                sendButtonAck()
            }
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
}
