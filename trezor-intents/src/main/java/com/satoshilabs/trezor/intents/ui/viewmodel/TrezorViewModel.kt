package com.satoshilabs.trezor.intents.ui.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import android.os.AsyncTask
import android.util.Log
import com.satoshilabs.trezor.intents.TrezorApi
import com.satoshilabs.trezor.intents.ui.data.GetPublicKeyResult
import com.satoshilabs.trezor.intents.ui.data.TrezorResult
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
        PIN_MATRIX_REQUEST,
        PASSPHRASE_REQUEST,
        BUTTON_REQUEST
    }

    lateinit var trezorManager: TrezorManager
    lateinit var trezorApi: TrezorApi

    var initialized: Boolean = false

    val state: MutableLiveData<State> = MutableLiveData()
    val result: MutableLiveData<TrezorResult> = MutableLiveData()

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

    fun init() {
        if (initialized) return

        trezorManager = TrezorManager(getApplication())
        trezorApi = TrezorApi(trezorManager)
        state.value = State.DISCONNECTED

        trezorApi.deviceResponseListener = object : TrezorApi.DeviceResponseListener {
            override fun onFailure() {
                // set failure type?
                state.value = State.CONNECTED
            }

            override fun onPinMatrixRequest() {
                state.value = State.PIN_MATRIX_REQUEST
            }

            override fun onPassphraseRequest() {
                state.value = State.PASSPHRASE_REQUEST
            }

            override fun onButtonRequest() {
                state.value = State.BUTTON_REQUEST
            }

            override fun onPublicKey(publicKey: TrezorMessage.PublicKey) {
                result.value = GetPublicKeyResult(publicKey.xpub)
            }
        }

        if (trezorManager.tryConnectDevice()) {
            Timber.tag(TAG).d("device is connected")
            state.value = State.CONNECTED
        } else {
            trezorManager.requestDevicePermissionIfCan(false)
        }

        initialized = true
    }

    fun executeGetPublicKey(path: IntArray) {
        AsyncTask.execute {
            if (trezorApi.initialize()) {
                val pathList = path.toList()
                trezorApi.getPublicKey(pathList)
            }
        }
    }

    fun executePinMatrixAck(pin: String) {
        AsyncTask.execute {
            trezorApi.sendPinMatrixAck(pin)
        }
    }

    fun executeCancel() {
        AsyncTask.execute {
            if (trezorManager.tryConnectDevice()) {
                trezorApi.cancel()
            }
        }
    }
}
