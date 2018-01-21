package com.satoshilabs.trezor.intents

import android.os.Handler
import android.os.Looper
import android.util.Log

import com.google.protobuf.Message
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage
import com.satoshilabs.trezor.lib.protobuf.TrezorType
import com.satoshilabs.trezor.lib.TrezorManager

class TrezorApi(private val trezorManager: TrezorManager) {
    companion object {
        private val TAG = "TrezorConnect"
    }

    var deviceResponseListener: DeviceResponseListener? = null
    private val mainThreadHandler: Handler = Handler(Looper.getMainLooper())

    fun initialize(): Boolean {
        val message = TrezorMessage.Initialize.newBuilder().build()
        val response = trezorManager.sendMessage(message)
        return response is TrezorMessage.Features && response.isInitialized
    }

    fun cancel() {
        sendMessage(TrezorMessage.Cancel.newBuilder().build())
    }

    fun clearSession() {
        sendMessage(TrezorMessage.ClearSession.newBuilder().build())
    }

    fun getPublicKey(path: List<Int>) {
        sendMessage(TrezorMessage.GetPublicKey.newBuilder()
                .addAllAddressN(path)
                .build())
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

    private fun sendMessage(message: Message) {
        val response = trezorManager.sendMessage(message)
        mainThreadHandler.post { handleResponse(response) }
    }

    private fun handleResponse(message: Message) {
        if (deviceResponseListener == null) return

        if (message is TrezorMessage.PinMatrixRequest) {
            deviceResponseListener?.onPinMatrixRequest()
        } else if (message is TrezorMessage.PassphraseRequest) {
            deviceResponseListener?.onPassphraseRequest()
        } else if (message is TrezorMessage.ButtonRequest) {
            deviceResponseListener?.onButtonRequest()
        } else if (message is TrezorMessage.PublicKey) {
            deviceResponseListener?.onPublicKey(message)
        } else if (message is TrezorMessage.Failure) {
            deviceResponseListener?.onFailure()
        } else {
            Log.d(TAG, "Unknown message: " + message)
        }
    }

    interface DeviceResponseListener {
        fun onPinMatrixRequest()
        fun onPassphraseRequest()
        fun onButtonRequest()
        fun onPublicKey(publicKey: TrezorMessage.PublicKey)
        fun onFailure()
    }
}
