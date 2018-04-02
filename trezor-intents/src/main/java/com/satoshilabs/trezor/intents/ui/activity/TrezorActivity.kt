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
import android.view.LayoutInflater
import android.view.ViewGroup
import com.satoshilabs.trezor.intents.R
import com.satoshilabs.trezor.intents.ui.data.CheckAddressRequest
import com.satoshilabs.trezor.intents.ui.data.SignTxRequest
import com.satoshilabs.trezor.intents.ui.data.TrezorRequest
import com.satoshilabs.trezor.intents.ui.data.TrezorResult
import com.satoshilabs.trezor.intents.ui.viewmodel.TrezorViewModel
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage
import com.satoshilabs.trezor.lib.protobuf.TrezorType

class TrezorActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "TrezorActivity"

        const val EXTRA_REQUEST = "request"
        const val EXTRA_RESULT = "result"
        const val EXTRA_SIGNED_TX = "signed_tx"
        const val EXTRA_FAILURE = "failure"

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

        viewModel.state.observe(this, Observer {
            when (it) {
                TrezorViewModel.State.DISCONNECTED -> showConnectDialog()
                TrezorViewModel.State.CONNECTED -> {
                    showLoadingDialog()
                    handleIntentRequest()
                }
                TrezorViewModel.State.FAILURE -> {
                    viewModel.sendCancel()
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
                TrezorViewModel.State.PIN_MATRIX_REQUEST -> startEnterPinActivity()
                TrezorViewModel.State.PASSPHRASE_REQUEST -> startEnterPassphraseActivity()
                TrezorViewModel.State.BUTTON_REQUEST -> {
                    showButtonRequestDialog(viewModel.buttonRequest)
                }
            }
        })

        viewModel.result.observe(this, Observer {
            val data = Intent()
            data.putExtra(EXTRA_RESULT, it)
            setResult(Activity.RESULT_OK, data)
            finish()
        })

        viewModel.signedTx.observe(this, Observer {
            val data = Intent()
            data.putExtra(EXTRA_SIGNED_TX, it)
            setResult(Activity.RESULT_OK, data)
            finish()
        })

        viewModel.failure.observe(this, Observer {
            val data = Intent()
            data.putExtra(EXTRA_FAILURE, it)
            setResult(Activity.RESULT_CANCELED, data)
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
                        viewModel.sendPinMatrixAck(pin)
                        return
                    }
                }

                viewModel.cancelPinMatrixRequest()
                setResult(RESULT_CANCELED)
                finish()
            }
            REQUEST_ENTER_PASSPHRASE -> {
                if (resultCode == RESULT_OK) {
                    val passphrase = data?.getStringExtra(EnterPassphraseActivity.EXTRA_PASSPHRASE)
                    if (passphrase != null) {
                        viewModel.sendPassphraseAck(passphrase)
                        return
                    }
                }

                viewModel.cancelPassphraseRequest()
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
        val request = intent.getParcelableExtra(EXTRA_REQUEST) as TrezorRequest
        if (request is SignTxRequest) {
            viewModel.signTx(request.message, request.inputTxs)
        } else {
            viewModel.sendMessage(request.message)
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
                .setCancelable(true)
                .setOnCancelListener {
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
                .setCancelable(false)
                .show()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showButtonRequestDialog(message: TrezorMessage.ButtonRequest?) {
        dialog?.dismiss()

        val request = intent.getParcelableExtra(EXTRA_REQUEST) as TrezorRequest

        if (request is CheckAddressRequest) {
            dialog = AlertDialog.Builder(this)
                    .setTitle(R.string.button_request_address_message)
                    .setMessage(request.address)
                    .setCancelable(false)
                    .show()
        } else {
            dialog = AlertDialog.Builder(this)
                    .setMessage(R.string.button_request_message)
                    .setCancelable(false)
                    .show()
        }
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
