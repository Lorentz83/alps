package com.github.lorentz83.alps;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;

import com.github.lorentz83.alps.utils.LogUtility;
import com.github.lorentz83.alps.utils.OverlayBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class EditImageActivity extends AppCompatActivity {
    private final static LogUtility log = new LogUtility(EditImageActivity.class);

    private final static int REQUEST_EDIT_IMAGE = 1234;

    private StoragePermissionRequest _storagePermissionRequest;

    private Spinner _overlay;
    private SeekBar _overlayParam1;
    private SeekBar _overlayParam2;

    private ImageView _preview;
    private Bitmap _bmp;
    private Uri _uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        _storagePermissionRequest = new StoragePermissionRequest(this);

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
        externalEdit.setOnClickListener(v -> {
            callExternalEditor();
        });

        ArrayAdapter<OverlayBuilder.Overlayer> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        adapter.add(OverlayBuilder.noOverlay()); // No overlay must be the 1st. It is used later to disable the overlay.
        adapter.add(OverlayBuilder.horizontalStripes());
        adapter.add(OverlayBuilder.verticalStripes());
        adapter.add(OverlayBuilder.chessboard());
        adapter.add(OverlayBuilder.rectangles());

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        _overlay = findViewById(R.id.overlay_type);
        _overlay.setAdapter(adapter);

        _overlayParam1 = findViewById(R.id.overlay_param_1);
        _overlayParam2 = findViewById(R.id.overlay_param_2);


        SeekBar.OnSeekBarChangeListener paramListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                setBitmap(_bmp);
            }
        };

        _overlayParam1.setOnSeekBarChangeListener(paramListener);
        _overlayParam2.setOnSeekBarChangeListener(paramListener);
        _overlay.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                log.i("overlay.onItemSelected(%s, %s, %s)", selectedItemView, position, id);
                setBitmap(_bmp);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {}
        });

        try {
            _uri = getIntent().getData();
            log.i("get stream %s", _uri);
            setBitmap(MediaStore.Images.Media.getBitmap(getContentResolver(), _uri));
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

        Bitmap overlaid = getOverlaidBitmap();

        BitmapDrawable myBitmapDrawable = new BitmapDrawable(getResources(), overlaid);
        myBitmapDrawable.getPaint().setFilterBitmap(false);
        _preview.setImageDrawable(myBitmapDrawable);
    }

    private Bitmap getOverlaidBitmap() {
        OverlayBuilder.Overlayer overlayer = (OverlayBuilder.Overlayer) _overlay.getSelectedItem();
        return overlayer.apply(_bmp, _overlayParam1.getProgress() + 1, _overlayParam2.getProgress() + 1);
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
        try(OutputStream os = getContentResolver().openOutputStream(_uri)) {
            getOverlaidBitmap().compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        _overlay.setSelection(0); // No overlay, otherwise it'll be applied twice.
    }

    private void callExternalEditor() {
        try {
            saveBitmapToUri();
            Intent intent = new Intent(Intent.ACTION_EDIT);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            // com.google.android.markup gets the image from here.
            intent.setDataAndType(_uri, "image/*");

            // Snapseed gets the image from here.
            intent.putExtra(Intent.EXTRA_STREAM, _uri);

            intent.putExtra(MediaStore.EXTRA_OUTPUT, _uri); // set the image file name

            log.i("sending uri %s, intent %s", _uri, intent);
            startActivityForResult(Intent.createChooser(intent, "Edit in"), REQUEST_EDIT_IMAGE);
        } catch (IOException e) {
            log.e("IO error", e);
            showToast("cannot save temporary file");
        }
    }

    private void readReturnBitmap(Uri uri) {
        if (uri.toString().startsWith("content://com.google.android.apps.snapseed/snapseed/")) {
            // This is honestly awful. But after wasting one day I couldn't find any better solution.
            // The content:// uri should be an opaque identifier, but snapseed doesn't give rights
            // to read it.
            // After some testing I noticed that the last component of the URI is the filename, and
            // that snapseed stores all the files in a single directory.
            // This code relies on this, but it may (and very likely will) break in the future.
            String fname = uri.toString().replace("content://com.google.android.apps.snapseed/snapseed/", "");
            Path p = Paths.get(Environment.getExternalStorageDirectory().getAbsolutePath(), "Snapseed", fname);
            Uri fileUri = Uri.fromFile(p.toFile());
            log.i("snapseed new uri: %s", fileUri);

            if (!_storagePermissionRequest.hasPermission()) {
                log.i("asking for storage permission");
                _storagePermissionRequest.askPermission(() -> {
                    readReturnBitmap(fileUri);
                });
                return;
            }
            uri = fileUri;
        }

        try {
            setBitmap(MediaStore.Images.Media.getBitmap(getContentResolver(), uri));
        } catch (IOException e) {
            showToast("Error loading the new image");
            log.e("cannot load edited image", e);
        } catch (SecurityException e) {
            showToast("Don't have permission to read the image");
            log.e("SecurityException, cannot read image", e);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        log.i("onActivityResult %d, result: %d", requestCode, resultCode);

        if (requestCode == REQUEST_EDIT_IMAGE && resultCode == Activity.RESULT_OK) {
            if (data != null)
                readReturnBitmap(data.getData());
            else
                readReturnBitmap(_uri);
        }
    }

    private void showToast(final String msg) {
        final Context ctx = this;
        runOnUiThread(() -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
    }
}


/**
 * Helper to handle the READ_EXTERNAL_STORAGE permission request.
 */
class StoragePermissionRequest {
    private final static LogUtility log = new LogUtility(StoragePermissionRequest.class);

    private final AlertDialog.Builder _infoDialog;
    private final AlertDialog.Builder _denialExplanation;
    private final ActivityResultLauncher<String> _requestStoragePermissionLauncher;
    private final AppCompatActivity _ctx;

    private OnGranted _onGranted = null;

    public interface OnGranted {
        /**
         * Called if the StoragePermissionRequest has been granted.
         */
        void execute();
    }

    public StoragePermissionRequest(@NonNull AppCompatActivity ctx) {
        _ctx = ctx;
        _denialExplanation = new AlertDialog.Builder(ctx)
                .setTitle("Storage permission")
                .setMessage("The storage permission is required only because some external apps don't grant read access to their edited images.\nYou can try to use another external app to edit your images if you don't want to grant this permission.")
                .setNeutralButton("OK", (dialog, which) -> dialog.dismiss());

        _requestStoragePermissionLauncher =
                ctx.registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        if (_onGranted != null) {
                            _onGranted.execute();
                        }
                    } else {
                        _denialExplanation.show();
                    }
                });

        _infoDialog = new AlertDialog.Builder(ctx)
                .setTitle("Storage permission required")
                .setMessage("The app you chose didn't share the image with ALPS after editing it.\nThe storage permission is required to read it. Without it you cannot get the new image.\nIn the next window you can choose if you want to grant this permission to ALPS.")
                .setNeutralButton("OK", (d, w) -> {
                    log.i("launching permission request");
                    _requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                    d.dismiss();
                });

    }

    public boolean hasPermission() {
        return _ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public void askPermission(OnGranted onGranted) {
        if ( hasPermission() ) {
            log.w("READ_EXTERNAL_STORAGE has been granted already");
            return;
        }
        log.i("asking user for READ_EXTERNAL_STORAGE");
        _onGranted = onGranted;
        _infoDialog.show();
    }
}