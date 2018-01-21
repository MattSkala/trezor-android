package com.satoshilabs.trezor.intents.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import com.satoshilabs.trezor.intents.R;


public class EnterPassphraseActivity extends AppCompatActivity {
    public static final String EXTRA_PASSPHRASE = "passphrase";

    // Views
    private EditText editTextPass1;
    private Button btnCancel;
    private Button btnConfirm;

    public static Intent createIntent(Context context) {
        return new Intent(context, EnterPassphraseActivity.class);
    }


    //
    // LIFECYCLE CALLBACKS
    //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.enter_passphrase_activity);

        this.editTextPass1 = (EditText)findViewById(R.id.edit_text_pass1);
        this.btnCancel = (Button)findViewById(R.id.btn_cancel);
        this.btnConfirm = (Button)findViewById(R.id.btn_confirm);

        btnCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        btnConfirm.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                String passphrase = editTextPass1.getText().toString();

                Intent data = new Intent();
                data.putExtra(EXTRA_PASSPHRASE, passphrase);
                setResult(RESULT_OK, data);
                finish();
            }
        });

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

}
