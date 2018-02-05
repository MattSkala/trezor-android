package com.satoshilabs.trezor.intents.ui.activity

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import com.satoshilabs.trezor.intents.R
import com.satoshilabs.trezor.intents.ui.data.GetPublicKeyRequest
import com.satoshilabs.trezor.intents.ui.data.InitializeRequest
import com.satoshilabs.trezor.intents.ui.data.TrezorRequest
import com.satoshilabs.trezor.intents.ui.data.TrezorResult
import com.satoshilabs.trezor.intents.ui.viewmodel.TrezorViewModel
import com.satoshilabs.trezor.lib.protobuf.TrezorType

class TrezorActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "TrezorActivity"

        const val EXTRA_REQUEST = "request"
        const val EXTRA_RESULT = "result"

        private const val REQUEST_ENTER_PIN = 1
        private const val REQUEST_ENTER_PASSPHRASE = 2

        @JvmStatic
        fun createIntent(context: Context, request: TrezorRequest): Intent {
            val intent = Intent(context, TrezorActivity::class.java)
            intent.putExtra(EXTRA_REQUEST, request)
            return intent
        }

        @JvmStatic
        fun getResult(data: Intent?): TrezorResult? {
            return data?.getParcelableExtra(EXTRA_RESULT)
        }
    }

    private lateinit var viewModel: TrezorViewModel

    private var dialog: Dialog? = null


    //
    // LIFECYCLE CALLBACKS
    //

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProviders.of(this).get(TrezorViewModel::class.java)
        viewModel.init()

        viewModel.state.observe(this, Observer {
            when (it) {
                TrezorViewModel.State.DISCONNECTED -> showConnectDialog()
                TrezorViewModel.State.CONNECTED -> {
                    showLoadingDialog()
                    handleIntentRequest()
                }
                TrezorViewModel.State.PIN_MATRIX_REQUEST -> startEnterPinActivity()
                TrezorViewModel.State.PASSPHRASE_REQUEST -> startEnterPassphraseActivity()
                TrezorViewModel.State.BUTTON_REQUEST -> showButtonDialog()
            }
        })

        viewModel.result.observe(this, Observer {
            val data = Intent()
            data.putExtra(EXTRA_RESULT, it)
            setResult(Activity.RESULT_OK, data)
            finish()
        })
    }

    override fun onStart() {
        super.onStart()
        viewModel.usbPermissionReceiver.register(this)
        viewModel.trezorConnectionChangedReceiver.register(this)
    }

    override fun onStop() {
        super.onStop()
        viewModel.usbPermissionReceiver.unregister(this)
        viewModel.trezorConnectionChangedReceiver.unregister(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_ENTER_PIN -> {
                if (resultCode == RESULT_OK) {
                    val pin = data?.getStringExtra(EnterPinActivity.EXTRA_PIN_ENCODED)
                    if (pin != null) {
                        viewModel.executePinMatrixAck(pin)
                        return
                    }
                }

                viewModel.executeCancel()
                setResult(RESULT_CANCELED)
                finish()
            }
            REQUEST_ENTER_PASSPHRASE -> {
                if (resultCode == RESULT_OK) {
                    val passphrase = data?.getStringExtra(EnterPassphraseActivity.EXTRA_PASSPHRASE)
                    if (passphrase != null) {
                        viewModel.executePassphraseAck(passphrase)
                        return
                    }
                }

                viewModel.executeCancel()
                setResult(RESULT_CANCELED)
                finish()
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
    }


    //
    // PRIVATE
    //

    private fun handleIntentRequest() {
        val request: TrezorRequest = intent.getParcelableExtra(EXTRA_REQUEST)
        handleRequest(request)
    }

    private fun handleRequest(request: TrezorRequest) {
        Log.d("TrezorActivity", "handleRequest " + request)
        when (request) {
            is InitializeRequest -> viewModel.executeInitialize()
            is GetPublicKeyRequest -> viewModel.executeGetPublicKey(request.path, request.initialize)
        }
    }


    //
    // DIALOGS
    //

    private fun showConnectDialog() {
        dialog?.dismiss()
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_connect, null)
        dialog = AlertDialog.Builder(this)
                .setView(view)
                .setOnCancelListener {
                    viewModel.executeCancel()
                    finish()
                }
                .show()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showLoadingDialog() {
        dialog?.dismiss()
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null)
        dialog = AlertDialog.Builder(this)
                .setView(view)
                .setOnCancelListener {
                    viewModel.executeCancel()
                    finish()
                }
                .show()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showButtonDialog() {
        // TODO
    }


    //
    // INPUT REQUESTS
    //

    private fun startEnterPinActivity() {
        dialog?.dismiss()
        val intent = Intent(this, EnterPinActivity::class.java)
        intent.putExtra(EnterPinActivity.EXTRA_PIN_MATRIX_REQUEST_TYPE,
                TrezorType.PinMatrixRequestType.PinMatrixRequestType_Current)
        startActivityForResult(intent, REQUEST_ENTER_PIN)
    }

    private fun startEnterPassphraseActivity() {
        dialog?.dismiss()
        val intent = Intent(this, EnterPassphraseActivity::class.java)
        startActivityForResult(intent, REQUEST_ENTER_PASSPHRASE)
    }
}
