package com.eleybourn.bookcatalogue.scanner;

import android.Manifest.permission;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
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
 * Full-screen activity that displays a camera preview and scans for ISBN barcodes.
 */
public class ScannerActivity extends BookCatalogueActivity {
	public static final String KEY_ISBN = "ISBN";

	private CameraSourcePreview mPreview;
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

		mPreview = findViewById(R.id.preview);
		mCameraView = findViewById(R.id.cameraView);

		BarcodeDetector barcodeDetector =
				new BarcodeDetector.Builder(this)
						.setBarcodeFormats(Barcode.EAN_13 | Barcode.EAN_8 | Barcode.UPC_A | Barcode.UPC_E)
						.build();

		mCameraSource = new CameraSource
				.Builder(this, barcodeDetector)
				.setFacing(CameraSource.CAMERA_FACING_BACK)
				.setRequestedPreviewSize(1920, 1080)
				.setRequestedFps(30.0f)
				.setAutoFocusEnabled(true)
				.build();

		mPreview.setCameraSource(mCameraSource);

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
					mPreview.post(() -> mPreview.requestLayout());
				} catch (IOException ie) {
					Logger.logError(ie, "CAMERA SOURCE");
				}
			}

			@Override
			public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
			}

			@Override
			public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
				if (mCameraSource != null) {
					mCameraSource.stop();
				}
			}
		});

		barcodeDetector.setProcessor(new Detector.Processor<>() {
            @Override
            public void release() {
            }

            @Override
            public void receiveDetections(@NonNull Detector.Detections<Barcode> detections) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                final SparseArray<Barcode> barcodes = detections.getDetectedItems();

                if (barcodes.size() != 0) {
                    mCameraView.post(() -> {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        int sz = barcodes.size();
                        for (int i = 0; i < sz; i++) {
                            Barcode bc = barcodes.get(barcodes.keyAt(i));
                            if (bc != null && IsbnUtils.isValid(bc.displayValue)) {
                                Intent resultIntent = new Intent();
                                resultIntent.putExtra(KEY_ISBN, bc.displayValue);
                                setResult(Activity.RESULT_OK, resultIntent);
                                finish();
                                return;
                            }
                        }
                    });
                }
            }
        });
	}

	public static String getIsbn(Intent i) {
		return i.getStringExtra(KEY_ISBN);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mHasSurface && mCameraSource != null) {
			if (ActivityCompat.checkSelfPermission(this, permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
				try {
					mCameraSource.start(mCameraView.getHolder());
					if (mPreview != null) {
						mPreview.post(() -> mPreview.requestLayout());
					}
				} catch (IOException e) {
					Logger.logError(e, "Camera failed to start in onResume");
				}
			}
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mCameraSource != null) {
			mCameraSource.stop();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mCameraSource != null) {
			mCameraSource.release();
			mCameraSource = null;
		}
	}

	@Override
	public void onRequestPermissionsResult(
			int requestCode,
			@NonNull String[] permissions,
			@NonNull int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (mHasSurface && mCameraSource != null) {
			if (ActivityCompat.checkSelfPermission(ScannerActivity.this, permission.CAMERA)
					!= PackageManager.PERMISSION_GRANTED)
			{
				return;
			}
			try {
				mCameraSource.start(mCameraView.getHolder());
				if (mPreview != null) {
					mPreview.post(() -> mPreview.requestLayout());
				}
			} catch (IOException e) {
				Logger.logError(e, "Camera failed to start");
			}
		}
	}
}
