package com.satoshilabs.trezor.lib.ui.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.TextView;

import com.satoshilabs.trezor.lib.protobuf.TrezorType.PinMatrixRequestType;

import java.util.ArrayList;
import java.util.List;
import com.satoshilabs.trezor.lib.R;

public class EnterPinActivity extends AppCompatActivity {
    private static final int PIN_MAX_LENGTH = 9;

    public static final String EXTRA_PIN_MATRIX_REQUEST_TYPE = "pin_matrix_request_type";
    public static final String EXTRA_PIN_ENCODED = "pin_encoded";

    // Views
    private TextView txtTitle;
    private TextView txtText;
    private TextView txtPinStars;
    private View btnBackspace;
    private TextView txtPinStrength;
    private View btnCancel;
    private View btnConfirm;
    private List<View> buttons = new ArrayList<>();

    // Immutable members
    private PinMatrixRequestType pinMatrixRequestType;
    private final int[] tmpNumCounts = new int[9];

    public static Intent createIntent(Context context, PinMatrixRequestType pinMatrixRequestType) {
        return new Intent(context, EnterPinActivity.class)
            .putExtra(EXTRA_PIN_MATRIX_REQUEST_TYPE, pinMatrixRequestType);
    }


    //
    // LIFECYCLE CALLBACKS
    //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.enter_pin_activity);

        this.txtTitle = (TextView)findViewById(R.id.txt_title);
        this.txtText = (TextView)findViewById(R.id.txt_text);
        this.txtPinStars = (TextView)findViewById(R.id.txt_pin_stars);
        this.btnBackspace = (View)findViewById(R.id.btn_backspace);
        this.txtPinStrength = (TextView)findViewById(R.id.txt_pin_strength);
        this.btnCancel = (View)findViewById(R.id.btn_cancel);
        this.btnConfirm = (View)findViewById(R.id.btn_confirm);

        this.pinMatrixRequestType = (PinMatrixRequestType) getIntent().getSerializableExtra(EXTRA_PIN_MATRIX_REQUEST_TYPE);

        addPinButton(buttons, R.id.button1, 1);
        addPinButton(buttons, R.id.button2, 2);
        addPinButton(buttons, R.id.button3, 3);
        addPinButton(buttons, R.id.button4, 4);
        addPinButton(buttons, R.id.button5, 5);
        addPinButton(buttons, R.id.button6, 6);
        addPinButton(buttons, R.id.button7, 7);
        addPinButton(buttons, R.id.button8, 8);
        addPinButton(buttons, R.id.button9, 9);

        btnBackspace.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                String s = txtPinStars.getText().toString();
                if (s.length() > 0) {
                    txtPinStars.setText(s.substring(0, s.length() - 1));
                }
            }
        });

        txtPinStrength.setVisibility(pinMatrixRequestType != PinMatrixRequestType.PinMatrixRequestType_Current ? View.VISIBLE : View.GONE);

        txtPinStars.addTextChangedListener(new TextWatcher() {

            @Override public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            @Override public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.length() == 0) {
                    btnConfirm.setEnabled(false);
                    //txtPinStrength.setText(String.valueOf(CustomHtml.HARD_SPACE));
                    txtPinStrength.setText("");
                }
                else {
                    btnConfirm.setEnabled(true);

                    for (int i = 0; i < tmpNumCounts.length; i++)
                        tmpNumCounts[i] = 0;

                    for (int i = 0; i < editable.length(); i++) {
                        int ind = (int) editable.charAt(i) - 49;
                        tmpNumCounts[ind]++;
                    }

                    int diffDigits = 0;
                    for (int i = 0; i < tmpNumCounts.length; i++) {
                        if (tmpNumCounts[i] > 0)
                            diffDigits++;
                    }

                    if (diffDigits < 4) {
                        /*
                        final int problemColor = ContextCompat.getColor(EnterPinActivity.this, R.color.text_problem);
                        final String html = CustomHtml.getFontColorTag(getString(R.string.enter_pin_strength_weak), problemColor);
                        txtPinStrength.setText(CustomHtml.fromHtmlWithCustomSpans(html));
                        */
                        txtPinStrength.setText(R.string.enter_pin_strength_weak);
                    } else if (diffDigits < 6) txtPinStrength.setText(R.string.enter_pin_strength_fine);
                    else if (diffDigits < 8) txtPinStrength.setText(R.string.enter_pin_strength_strong);
                    else txtPinStrength.setText(R.string.enter_pin_strength_ultimate);
                }

                boolean pinBtnsEnabled = editable.length() < PIN_MAX_LENGTH;
                for (View button : buttons)
                    button.setEnabled(pinBtnsEnabled);
            }
        });

        btnCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        btnConfirm.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                String pin = txtPinStars.getText().toString();
                if (pin.length() > 0) {
                    Intent data = new Intent();
                    data.putExtra(EXTRA_PIN_ENCODED, pin);
                    setResult(RESULT_OK, data);
                    finish();
                }
            }
        });

        switch (pinMatrixRequestType) {
            case PinMatrixRequestType_Current:
                txtTitle.setText(R.string.enter_pin_current_prompt);
                txtText.setText(R.string.enter_pin_text);
                break;

            case PinMatrixRequestType_NewFirst:
                txtTitle.setText(R.string.enter_pin_new_prompt);
                txtText.setText(R.string.enter_pin_text);
                break;

            case PinMatrixRequestType_NewSecond:
                txtTitle.setText(R.string.enter_pin_repeat_prompt);
                txtText.setText(R.string.enter_pin_text_repeat);
                break;
        }
    }


    //
    // PRIVATE
    //

    void addPinButton(List<View> buttons, int rid, final int number) {
        View button = findViewById(rid);
        button.setOnClickListener(new OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View view) {
                if (txtPinStars.getText().length() < PIN_MAX_LENGTH) {
                    txtPinStars.setText(txtPinStars.getText().toString() + number);
                }
            }
        });
        buttons.add(button);
    }
}
