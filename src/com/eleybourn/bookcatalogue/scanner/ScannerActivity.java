package com.eleybourn.bookcatalogue.scanner;

import android.Manifest.permission;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class ScannerActivity
		extends BookCatalogueActivity
{
	public static final String KEY_ISBN = "ISBN";
	/**
	 * Some older devices needs a small delay between UI widget updates
	 * and a change of the status and navigation bar.
	 */
	private SurfaceView mCameraView;

	private CameraSource mCameraSource;

	private boolean mHasSurface = false;

	@Override
	protected RequiredPermission[] getRequiredPermissions() {
		return mScannerPermissions;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_scanner);

		mCameraView = findViewById(R.id.cameraView);

		BarcodeDetector barcodeDetector =
				new BarcodeDetector.Builder(this)
						.setBarcodeFormats(Barcode.EAN_13)
						.build();

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;

		mCameraSource = new CameraSource
				.Builder(this, barcodeDetector)
				.setRequestedPreviewSize(width, height)
				.setFacing(CameraSource.CAMERA_FACING_BACK)
				.setAutoFocusEnabled(true)
				.build();

		mCameraView.getHolder().addCallback(new SurfaceHolder.Callback() {
			@Override
			public void surfaceCreated(@NonNull SurfaceHolder holder) {
				mHasSurface = true;
				try {
					if (ActivityCompat.checkSelfPermission(ScannerActivity.this, permission.CAMERA)
							!= PackageManager.PERMISSION_GRANTED)
					{
						return;
					}
					mCameraSource.start(mCameraView.getHolder());
				} catch (IOException ie) {
					Logger.logError(ie, "CAMERA SOURCE");
				}
			}

			@Override
			public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
			}

			@Override
			public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
				mCameraSource.stop();
			}
		});


		barcodeDetector.setProcessor(new Detector.Processor<>() {
            @Override
            public void release() {
            }

            @Override
            public void receiveDetections(@NonNull Detector.Detections<Barcode> detections) {

                final SparseArray<Barcode> barcodes = detections.getDetectedItems();

                if (barcodes.size() != 0) {
                    // Use the post method of the TextView
                    mCameraView.post(() -> {
                        int sz = barcodes.size();
                        for (int i = 0; i < sz; i++) {
                            Barcode bc = barcodes.get(barcodes.keyAt(i));
                            if (bc.isRecognized && IsbnUtils.isValid(bc.displayValue)) {
                                Intent intent = getIntent();
                                intent.putExtra(KEY_ISBN, bc.displayValue);
                                setResult(Activity.RESULT_OK, intent);
                                finish();
                            }
                        }
                    });
                }
            }
        });
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		// Trigger the initial hide() shortly after the activity has been
		// created, to briefly hint to the user that UI controls
		// are available.
		//delayedHide(100);
	}

	public static String getIsbn(Intent i) {
		return i.getStringExtra(KEY_ISBN);
	}

	@Override
	public void onRequestPermissionsResult(
			int requestCode,
			@NonNull String[] permissions,
			@NonNull int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (mHasSurface) {
			if (ActivityCompat.checkSelfPermission(ScannerActivity.this, permission.CAMERA)
					!= PackageManager.PERMISSION_GRANTED)
			{
				return;
			}
			try {
				mCameraSource.start(mCameraView.getHolder());
			} catch (IOException e) {
				Logger.logError(e, "Camera failed to start");
			}
		}
	}
}