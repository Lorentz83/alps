package com.github.lorentz83.alps;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;

import com.github.lorentz83.alps.ui.ColorPicker;
import com.github.lorentz83.alps.utils.CustomTextResult;
import com.github.lorentz83.alps.utils.LogUtility;


// TODO add a font picker
// http://www.ulduzsoft.com/2012/01/enumerating-the-fonts-on-android-platform/

public class CustomTextActivity extends AppCompatActivity {
    private final static LogUtility log = new LogUtility(CustomTextActivity.class);

    private EditText _userText;
    private int _bgColor = Color.BLACK;
    private int _fgColor = Color.WHITE;

    private ColorPicker _colorPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_text);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);   //show back button

        _userText = findViewById(R.id.user_text);
        _colorPicker = new ColorPicker(this);

        Button foregroundBtn = findViewById(R.id.btn_foreground);
        foregroundBtn.setOnClickListener(v -> {
            _colorPicker.show(color -> {
                _fgColor = color;
                updatePreviewColor();
            }, _fgColor);
        });

        Button backgroundBtn = findViewById(R.id.btn_background);
        backgroundBtn.setOnClickListener(v -> {
            _colorPicker.show(color -> {
                _bgColor = color;
                updatePreviewColor();
            }, _bgColor);
        });

        _userText.requestFocus();
        updatePreviewColor();

//        Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                .setAction("Action", null).show());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_custom_text, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_ok:
                CustomTextResult res = new CustomTextResult(_userText.getText().toString(), _bgColor, _fgColor);

                Intent resultIntent = new Intent();
                resultIntent.putExtra(CustomTextResult.CUSTOM_TEXT_RESULT, res);
                setResult(Activity.RESULT_OK, resultIntent);

                finish();
                return true;
            default:
                log.i("OptionItemSelected, unknown menu entry %s", id);
        }
        return super.onOptionsItemSelected(item);
    }

    private void updatePreviewColor() {
        _userText.setBackgroundColor(_bgColor);
        _userText.setTextColor(_fgColor);
    }
}

