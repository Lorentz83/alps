package com.github.lorentz83.alps;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;

import com.github.lorentz83.alps.utils.LogUtility;

import java.io.IOException;
import java.io.OutputStream;

public class EditImageActivity extends AppCompatActivity {
    private final static LogUtility log = new LogUtility(EditImageActivity.class);

    private final static int REQUEST_EDIT_IMAGE = 1234;

    private ImageView _preview;
    private Bitmap _bmp;
    private Uri _uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_image);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);   //show back button

        _preview = findViewById(R.id.image_preview);

        ImageButton rotateCW = findViewById(R.id.rotate_clockwise);
        rotateCW.setOnClickListener(c -> {
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            applyMatrix(matrix);
        });

        ImageButton rotateCCW = findViewById(R.id.rotate_counterclockwise);
        rotateCCW.setOnClickListener(c -> {
            Matrix matrix = new Matrix();
            matrix.postRotate(-90);
            applyMatrix(matrix);
        });

        ImageButton mirrorH = findViewById(R.id.horizontal_mirror);
        mirrorH.setOnClickListener(c -> {
            Matrix matrix = new Matrix();
            matrix.preScale(-1, 1);
            applyMatrix(matrix);
        });

        ImageButton mirrorW = findViewById(R.id.vertical_mirror);
        mirrorW.setOnClickListener(c -> {
            Matrix matrix = new Matrix();
            matrix.preScale(1, -1);
            applyMatrix(matrix);
        });

        ImageButton externalEdit = findViewById(R.id.external_edit);
        externalEdit.setOnClickListener(v -> {callExternalEditor();});


        try {
            //intent.putExtra(Intent.EXTRA_STREAM, uri);
            _uri = getIntent().getData();
            log.i("get stream %s", _uri);
            setBitmap(MediaStore.Images.Media.getBitmap(getContentResolver(),_uri));
        } catch (IOException e) {
            // TODO handle it.
            log.w("NO IMAGE");
        }
    }

    private void applyMatrix(Matrix matrix) {
        setBitmap(Bitmap.createBitmap(_bmp, 0, 0, _bmp.getWidth(), _bmp.getHeight(), matrix, true));
    }

    private void setBitmap(Bitmap bmp) {
        _bmp = bmp;
        _preview.setImageBitmap(_bmp);
    }

    @Override
    public boolean onSupportNavigateUp() {
        setResult(Activity.RESULT_CANCELED);
        finish();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_ok_menu, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_ok:
                try {
                    saveBitmapToUri();
                } catch (IOException e) {
                    log.e("cannot write temporary file", e);
                    showToast("ERROR: writing temporary file");
                    return true;
                }
                setResult(Activity.RESULT_OK);
                finish();
                return true;
            default:
                log.i("OptionItemSelected, unknown menu entry %s", id);
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveBitmapToUri() throws IOException {
        log.i("writing temporary file on %s", _uri);
        OutputStream os = getContentResolver().openOutputStream(_uri);
        _bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        os.close();
    }

    private void callExternalEditor() {
        try {
            saveBitmapToUri();
            Intent intent = new Intent(Intent.ACTION_EDIT);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            // com.google.android.markup gets the image from here.
            intent.setDataAndType(_uri, "image/png");

            // Snapseed gets the image from here.
            intent.putExtra(Intent.EXTRA_STREAM, _uri);

            log.i("sending uri %s, intent %s", _uri, intent);
        startActivityForResult(Intent.createChooser(intent, "Edit in"), REQUEST_EDIT_IMAGE);
        } catch (IOException e) {
           log.e("IO error", e);
           showToast("cannot save temporary file");
        }
    }

    public void readReturnBitmap(Intent data) {
        // Some app returns the edited image as new data.
        if (data != null) {
            log.i("result uri: %s", data.getData());
            try {
                setBitmap(MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData()));
            } catch (IOException e) {
                showToast("Cannot load new image");
                log.e("cannot load edited image", e);
            } catch (SecurityException e) {
                // TODO add storage read permission request and support it.
                log.e("Storage permission is required", e);
                showToast("Need storage permission");
            }
        } else { // Others overwrite the same
            try {
                setBitmap(MediaStore.Images.Media.getBitmap(getContentResolver(),_uri));
            } catch (IOException e) {
                log.e("cannot read back temporary file", e);
                showToast("Cannot read image");
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        log.i("onActivityResult %d, result: %d", requestCode, resultCode);

        if (requestCode == REQUEST_EDIT_IMAGE && resultCode == Activity.RESULT_OK) {
            readReturnBitmap(data);
        }
    }

    private void showToast(final String msg) {
        final Context ctx = this;
        runOnUiThread(() -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
    }
}
