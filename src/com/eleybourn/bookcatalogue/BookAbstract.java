package com.eleybourn.bookcatalogue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.CoverBrowser.OnImageSelectedListener;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.cropper.CropCropImage;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Abstract class for creating activities containing book details.
 * Here we define common method for all children: database and background initializing,
 * initializing fields and display metrics and other common tasks.
 *
 * @author n.silin
 */
public abstract class BookAbstract extends BookEditFragmentAbstract {

    public static final Character BOOKSHELF_SEPARATOR = ',';

    // Target size of a thumbnail in edit dialog and zoom dialog (bounding box dim)
    protected static final int MAX_EDIT_THUMBNAIL_SIZE = 256;
    protected static final int MAX_ZOOM_THUMBNAIL_SIZE = 1024;

    private static final int CONTEXT_ID_DELETE = 1;
    private static final int CONTEXT_SUBMENU_REPLACE_THUMB = 2;
    private static final int CONTEXT_ID_SUBMENU_ROTATE_THUMB = 3;
    private static final int CONTEXT_ID_CROP_THUMB = 6;
    private static final int CODE_ADD_PHOTO = 21;
    private static final int CODE_ADD_GALLERY = 22;
    private static final int CONTEXT_ID_ROTATE_THUMB_CW = 31;
    private static final int CONTEXT_ID_ROTATE_THUMB_CCW = 32;
    private static final int CONTEXT_ID_ROTATE_THUMB_180 = 33;

    private CoverBrowser mCoverBrowser = null;

    /**
     * Counter used to prevent images being reused accidentally
     */
    private static int mTempImageCounter = 0;

    /**
     * Used to display a hint if user rotates a camera image
     */
    private boolean mGotCameraImage = false;

    protected android.util.DisplayMetrics mMetrics;
    /**
     * Minimum of MAX_EDIT_THUMBNAIL_SIZE and 1/3rd of largest screen dimension
     */
    protected Integer mThumbEditSize;
    /**
     * Zoom size is minimum of MAX_ZOOM_THUMBNAIL_SIZE and largest screen dimension.
     */
    protected Integer mThumbZoomSize;

    /**
     * Handler to process a cover selected from the CoverBrowser.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private final OnImageSelectedListener mOnImageSelectedListener = fileSpec -> {
        if (mCoverBrowser != null && fileSpec != null) {
            // Get the current file
            File bookFile = getCoverFile(mEditManager.getBookData().getRowId());
            // Get the new file
            File newFile = new File(fileSpec);
            // Overwrite with new file
            newFile.renameTo(bookFile);
            // update current activity
            setCoverImage();
        }
        if (mCoverBrowser != null)
            mCoverBrowser.dismiss();
        mCoverBrowser = null;
    };

    ActivityResultLauncher<String[]> mCameraPermissionsLauncher = registerForActivityResult(
            new RequestMultiplePermissions(),
            result -> {
                for (Entry<String, Boolean> e : result.entrySet()) {
                    if (!e.getValue()) {
                        return;
                    }
                }
                requestPhoto();
            }
    );

    // Launcher for taking a photo
    private final ActivityResultLauncher<Void> mTakePhotoLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            bitmap -> {
                if (bitmap != null) {
                    try {
                        // Apply rotation preference
                        Matrix m = new Matrix();
                        m.postRotate(BookCatalogueApp.getAppPreferences().getInt(BookCataloguePreferences.PREF_AUTOROTATE_CAMERA_IMAGES, 90));
                        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);

                        File cameraFile = getCameraImageFile();
                        try (FileOutputStream f = new FileOutputStream(cameraFile.getAbsoluteFile())) {
                            rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, f);
                        }

                        // Start crop on the newly saved file
                        cropCoverImage(cameraFile);
                        mGotCameraImage = true;
                    } catch (IOException e) {
                        Logger.logError(e);
                    }
                } else {
                    Tracker.handleEvent(BookAbstract.this, "TakePhotoLauncher - bitmap empty", Tracker.States.Running);
                }
            }
    );

    // Launcher for picking from gallery (CODE_ADD_GALLERY)
    private final ActivityResultLauncher<String> mGetContentLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) return;

                // Handle the URI selection
                handleGalleryResult(uri);
            }
    );

    // Launcher for cropping (Replaces CODE_CROP_RESULT_INTERNAL / EXTERNAL)
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private final ActivityResultLauncher<Intent> mCropLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // This logic comes from your old onActivityResult for the crop codes
                    try {
                        File cropFile = getCroppedImageFileName();
                        File coverFile = getCoverFile(mEditManager.getBookData().getRowId());

                        if (cropFile.exists()) {
                            // Copy the cropped temp file to the actual book cover file
                            Utils.copyFile(cropFile, coverFile);
                            // Refresh the view
                            setCoverImage();
                            // Cleanup
                            cropFile.delete();
                        }
                    } catch (IOException e) {
                        Logger.logError(e, "Failed to save cropped image");
                    }
                }
            }
    );

    private void handleGalleryResult(Uri selectedImageUri) {
        if (selectedImageUri == null) return;

        // Use the original BC code for anything that has a 'content' scheme and if pre-KitKat
        if (Build.VERSION.SDK_INT < 19 && "content".equalsIgnoreCase(selectedImageUri.getScheme())) {
            String[] projection = {MediaStore.Images.Media.DATA};
            assert this.getActivity() != null;
            try (Cursor cursor = this.getActivity().getContentResolver().query(selectedImageUri, projection, null, null, null)) {
                int column_index = (cursor != null) ? cursor.getColumnIndex(MediaStore.Images.Media.DATA) : -1;
                if (cursor == null || column_index < 0 || !cursor.moveToFirst()) {
                    Logger.logError(new RuntimeException("Add from gallery failed (col = " + column_index + "), name = " + MediaStore.Images.Media.DATA));
                    String s = getResources().getString(R.string.no_image_found) + ". " + getResources().getString(R.string.if_the_problem_persists);
                    Toast.makeText(getActivity(), s, Toast.LENGTH_LONG).show();
                } else {
                    String selectedImagePath = cursor.getString(column_index);
                    File thumb = new File(selectedImagePath);
                    File real = getCoverFile(mEditManager.getBookData().getRowId());
                    try {
                        Utils.copyFile(thumb, real);
                        setCoverImage();
                    } catch (IOException e) {
                        Logger.logError(e, "copyImage failed in add from gallery");
                        showCopyErrorToast();
                    }
                }
            } catch (Exception e) {
                Logger.logError(e);
            }
        } else {
            // Modern approach / content resolver
            boolean imageOk = false;
            assert getActivity() != null;
            try (InputStream in = getActivity().getContentResolver().openInputStream(selectedImageUri)) {
                assert in != null;
                imageOk = Utils.saveInputToFile(in, getCoverFile(mEditManager.getBookData().getRowId()));
            } catch (IOException e) {
                Logger.logError(e, "Unable to copy content to file");
            }
            if (imageOk) {
                setCoverImage();
            } else {
                showCopyErrorToast();
            }
        }
    }

    private void showCopyErrorToast() {
        String s = getResources().getString(R.string.could_not_copy_image) + ". " + getResources().getString(R.string.if_the_problem_persists);
        Toast.makeText(getActivity(), s, Toast.LENGTH_LONG).show();
    }


    /**
     * Listener for creating context menu for book thumbnail.
     */
    private final OnCreateContextMenuListener mCreateBookThumbContextMenuListener = (menu, v, menuInfo) -> {
        MenuItem delete = menu.add(0, CONTEXT_ID_DELETE, 0, R.string.menu_delete_thumb);
        delete.setIcon(android.R.drawable.ic_menu_delete);

        // Creating submenu item for rotate
        android.view.SubMenu replaceSubmenu = menu.addSubMenu(0, CONTEXT_SUBMENU_REPLACE_THUMB, 2,
                R.string.menu_replace_thumb);
        replaceSubmenu.setIcon(android.R.drawable.ic_menu_gallery);

        MenuItem add_photo = replaceSubmenu.add(0, CODE_ADD_PHOTO, 1, R.string.menu_add_thumb_photo);
        add_photo.setIcon(R.drawable.ic_menu_camera);
        MenuItem add_gallery = replaceSubmenu.add(0, CODE_ADD_GALLERY, 2, R.string.menu_add_thumb_gallery);
        add_gallery.setIcon(android.R.drawable.ic_menu_gallery);
        // TODO EDITIONS
        //MenuItem alt_covers = replaceSubmenu.add(0, CONTEXT_ID_SHOW_ALT_COVERS, 3, R.string.menu_thumb_alt_editions);
        //alt_covers.setIcon(android.R.drawable.ic_menu_zoom);

        // Implementing submenu for rotate
        android.view.SubMenu submenu = menu.addSubMenu(0, CONTEXT_ID_SUBMENU_ROTATE_THUMB, 3, R.string.menu_rotate_thumb);
        add_gallery.setIcon(android.R.drawable.ic_menu_rotate);

        MenuItem rotate_photo_cw = submenu.add(0, CONTEXT_ID_ROTATE_THUMB_CW, 1, R.string.menu_rotate_thumb_cw);
        rotate_photo_cw.setIcon(android.R.drawable.ic_menu_rotate);
        MenuItem rotate_photo_ccw = submenu.add(0, CONTEXT_ID_ROTATE_THUMB_CCW, 2, R.string.menu_rotate_thumb_ccw);
        rotate_photo_ccw.setIcon(android.R.drawable.ic_menu_rotate);
        MenuItem rotate_photo_180 = submenu.add(0, CONTEXT_ID_ROTATE_THUMB_180, 3, R.string.menu_rotate_thumb_180);
        rotate_photo_180.setIcon(android.R.drawable.ic_menu_rotate);

        MenuItem crop_thumb = menu.add(0, CONTEXT_ID_CROP_THUMB, 4, R.string.menu_crop_thumb);
        crop_thumb.setIcon(android.R.drawable.ic_menu_crop);
    };

    /**
     * Show the context menu for the cover thumbnail
     */
    public void showCoverContextMenu() {
        assert getView() != null;
        View v = getView().findViewById(R.id.row_img);
        v.showContextMenu();
    }

    /* Note that you should use setContentView() method in descendant before
     * running this.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // See how big the display is and use that to set bitmap sizes
        setDisplayMetrics();
        initThumbSizes();

        initFields();

        //Set zooming by default on clicking on image
        view.findViewById(R.id.row_img).setOnClickListener(v -> showZoomedThumb(mEditManager.getBookData().getRowId()));

    }

    @Override
    public void onPause() {
        Tracker.enterOnPause(this);
        super.onPause();

        // Close down the cover browser.
        if (mCoverBrowser != null) {
            mCoverBrowser.dismiss();
            mCoverBrowser = null;
        }
        Tracker.exitOnPause(this);
    }

    @Override
    public void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        Tracker.exitOnResume(this);
    }

    @Override
    public void onDestroy() {
        Tracker.enterOnDestroy(this);
        super.onDestroy();
        mDbHelper.close();
        Tracker.exitOnDestroy(this);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Tracker.handleEvent(this, "Context Menu Item " + item.getItemId(), Tracker.States.Enter);

        try {
            assert getView() != null;
            ImageView iv = getView().findViewById(R.id.row_img);
            File thumbFile = getCoverFile(mEditManager.getBookData().getRowId());

            switch (item.getItemId()) {
                case CONTEXT_ID_DELETE:
                    deleteThumbnail();
                    Utils.fetchFileIntoImageView(thumbFile, iv, mThumbEditSize, mThumbEditSize, true);
                    return true;
                case CONTEXT_ID_SUBMENU_ROTATE_THUMB:
                    // Just a submenu; skip, but display a hint if user is rotating a camera image
                    if (mGotCameraImage) {
                        HintManager.displayHint(getActivity(), R.string.hint_autorotate_camera_images, null, null);
                        mGotCameraImage = false;
                    }
                    return true;
                case CONTEXT_ID_ROTATE_THUMB_CW:
                    rotateThumbnail(90);
                    Utils.fetchFileIntoImageView(thumbFile, iv, mThumbEditSize, mThumbEditSize, true);
                    return true;
                case CONTEXT_ID_ROTATE_THUMB_CCW:
                    rotateThumbnail(-90);
                    Utils.fetchFileIntoImageView(thumbFile, iv, mThumbEditSize, mThumbEditSize, true);
                    return true;
                case CONTEXT_ID_ROTATE_THUMB_180:
                    rotateThumbnail(180);
                    Utils.fetchFileIntoImageView(thumbFile, iv, mThumbEditSize, mThumbEditSize, true);
                    return true;
                case CODE_ADD_PHOTO:
                    BookCatalogueActivity a = (BookCatalogueActivity) getActivity();
                    if (!BookCatalogueActivity.checkPermissions(a, false, mCameraPermissionsLauncher, BookCatalogueActivity.mScannerPermissions)) {
                        return true;
                    }
                    requestPhoto();
                    return true;
                case CODE_ADD_GALLERY:
                    mGetContentLauncher.launch("image/*");
                    return true;
                case CONTEXT_ID_CROP_THUMB:
                    cropCoverImage(thumbFile);
                    return true;
            }
            return super.onContextItemSelected(item);
        } finally {
            Tracker.handleEvent(this, "Context Menu Item " + item.getItemId(), Tracker.States.Exit);
        }
    }

    private void requestPhoto() {
        // Increment the temp counter and cleanup the temp directory
        mTempImageCounter++;
        cleanupTempImages();
        // Get a photo
        mTakePhotoLauncher.launch(null);
    }

    /**
     * Delete everything in the temp file directory
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void cleanupTempImages() {
        File[] files = getTempImageDir().listFiles();
        if (files != null) {
            for (File f : files) {
                try {
                    f.delete();
                } catch (Exception e) {
                    Logger.logError(e, "Unable to clean up temp file");
                }
            }
        }
    }

    private void cropCoverImage(File thumbFile) {
        String prefName = BookCataloguePreferences.PREF_USE_EXTERNAL_IMAGE_CROPPER;
        boolean useExt = BookCatalogueApp.getAppPreferences().getBoolean(prefName, false);
        if (useExt) {
            cropCoverImageExternal(thumbFile);
        } else {
            cropCoverImageInternal(thumbFile);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void cropCoverImageInternal(File thumbFile) {
        Intent crop_intent = new Intent(getActivity(), CropCropImage.class);
        // here you have to pass absolute path to your file
        crop_intent.putExtra("image-path", thumbFile.getAbsolutePath());
        crop_intent.putExtra("scale", true);

        String prefName = BookCataloguePreferences.PREF_CROP_FRAME_WHOLE_IMAGE;
        crop_intent.putExtra("whole-image", BookCatalogueApp.getAppPreferences().getBoolean(prefName, false));
        // Get and set the output file spec, and make sure it does not already exist.
        File cropped = this.getCroppedImageFileName();
        if (cropped.exists()) {
            cropped.delete();
        }
        crop_intent.putExtra("output", cropped.getAbsolutePath());
        mCropLauncher.launch(crop_intent);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void cropCoverImageExternal(File thumbFile) {
        Tracker.handleEvent(this, "cropCoverImageExternal", Tracker.States.Enter);
        try {
            Context context = getContext();
            if (context == null)
                return;

            Intent intent = new Intent("com.android.camera.action.CROP");
            //this will open any image file
            Uri uriImage = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider",
                    new File(thumbFile.getAbsolutePath()));
            intent.setDataAndType(uriImage, "image/*");
            intent.putExtra("crop", "true");
            intent.putExtra("scale", true);
            intent.putExtra("noFaceDetection", true);
            // True to return a Bitmap, false to directly save the cropped image
            intent.putExtra("return-data", false);
            // Save output image in uri
            File cropped = this.getCroppedImageFileName();
            if (cropped.exists())
                cropped.delete();

            assert getActivity() != null;
            Uri uriCropped = FileProvider.getUriForFile(getActivity(), BuildConfig.APPLICATION_ID + ".fileprovider",
                    new File(cropped.getAbsolutePath()));
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uriCropped);
            // These flags seem insufficient on Android 9 at least; need to grant each possible package access.
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            List<ResolveInfo> list = getActivity().getPackageManager().queryIntentActivities(intent, 0);
            int size = list.size();
            if (size == 0) {
                Toast.makeText(getActivity(), "Can not find image crop app", Toast.LENGTH_SHORT).show();
            } else {
                // Grant each possible package for the intent access.
                for (ResolveInfo resolveInfo : list) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    context.grantUriPermission(packageName, uriCropped, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                mCropLauncher.launch(intent);
            }
        } finally {
            Tracker.handleEvent(this, "cropCoverImageExternal", Tracker.States.Exit);
        }
    }

    /**
     * Delete the provided thumbnail from the sdcard
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void deleteThumbnail() {
        try {
            File thumbFile = getCoverFile(mEditManager.getBookData().getRowId());
            if (thumbFile != null && thumbFile.exists()) {
                thumbFile.delete();
            }
        } catch (Exception e) {
            Logger.logError(e);
        }
        // Make sure the cached thumbnails (if present) are deleted
        invalidateCachedThumbnail();
    }

    /**
     * Get a temp file for camera images
     */
    private File getCameraImageFile() {
        return new File(getTempImageDir().getAbsolutePath() + "/camera" + mTempImageCounter + ".jpg");
    }

    /**
     * Get the File object for the cover of the book we are editing. If the boo
     * is new, return the standard temp file.
     */
    protected File getCoverFile(Long rowId) {
        if (rowId == null || rowId == 0)
            return CatalogueDBAdapter.getTempThumbnail();
        else
            return CatalogueDBAdapter.fetchThumbnailByUuid(mDbHelper.getBookUuid(rowId));
    }

    /**
     * Get a temp file for cropping output
     */
    private File getCroppedImageFileName() {
        return new File(getTempImageDir().getAbsolutePath() + "/cropped" + mTempImageCounter + ".jpg");
    }

    /**
     * Get a temp directory for image manipulation (create if necessary)
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private File getTempImageDir() {
        File f = new File(StorageUtils.getBCCache() + "/tmp_images/");
        if (!f.exists())
            f.mkdirs();
        return f;
    }

    /**
     * Populate Author field by data from #mAuthorList.
     * If there is no data shows "Set author" text defined in resources.
     * <p>
     * Be sure that you get #mAuthorList. See #populateFieldsFromDb(Long)
     * for example.
     */
    protected void populateAuthorListField() {

        String newText = mEditManager.getBookData().getAuthorTextShort();
        if (newText == null || newText.isEmpty()) {
            newText = getResources().getString(R.string.label_set_authors);
        }
        mFields.getField(R.id.field_author).setValue(newText);
    }

    /**
     * Populate Series field by data from #mSeriesList.
     * If there is no data shows "Set series..." text defined in resources.
     * <p>
     * Be sure that you get #mSeriesList. See #populateFieldsFromDb(Long)
     * for example.
     */
    protected void populateSeriesListField() {
        String newText;
        int size;
        ArrayList<Series> list = mEditManager.getBookData().getSeriesList();
        try {
            size = list.size();
        } catch (NullPointerException e) {
            size = 0;
        }
        if (size == 0) {
            newText = getResources().getString(R.string.set_series);
        } else {
            boolean trimmed = Utils.pruneSeriesList(list);
            trimmed |= Utils.pruneList(mDbHelper, list);
            if (trimmed) {
                mEditManager.getBookData().setSeriesList(list);
            }
            newText = list.get(0).getDisplayName();
            if (list.size() > 1)
                newText += " " + getResources().getString(R.string.and_others);
        }
        mFields.getField(R.id.field_series).setValue(newText);
    }

    /**
     * Rotate the thumbnail a specified amount
     */
    private void rotateThumbnail(long angle) {
        boolean retry = true;
        while (retry) {
            try {
                File thumbFile = getCoverFile(mEditManager.getBookData().getRowId());

                Bitmap origBm = Utils.fetchFileIntoImageView(thumbFile, null, mThumbZoomSize * 2, mThumbZoomSize * 2, true);
                if (origBm == null)
                    return;

                Matrix m = new Matrix();
                m.postRotate(angle);
                Bitmap rotBm = Bitmap.createBitmap(origBm, 0, 0, origBm.getWidth(), origBm.getHeight(), m, true);
                if (rotBm != origBm) {
                    origBm.recycle();
                }

                /* Create a file to copy the thumbnail into */
                FileOutputStream f;
                try {
                    f = new FileOutputStream(thumbFile.getAbsoluteFile());
                } catch (FileNotFoundException e) {
                    Logger.logError(e);
                    return;
                }
                rotBm.compress(Bitmap.CompressFormat.PNG, 100, f);
                rotBm.recycle();
            } catch (java.lang.OutOfMemoryError e) {
                System.gc();
            }
            retry = false;
        }
    }

    /**
     * Ensure that the cached thumbnails for this book are deleted (if present)
     */
    private void invalidateCachedThumbnail() {
        final long rowId = mEditManager.getBookData().getRowId();
        if (rowId != 0) {
            try {
                String hash = mDbHelper.getBookUuid(rowId);
                Utils u = new Utils();
                u.deleteCachedBookCovers(hash);
            } catch (Exception e) {
                Logger.logError(e, "Error cleaning up cached cover images");
            }
        }
    }

    /**
     * Add all book fields with corresponding validators.
     */
    protected void initFields() {
        final View root = getView();

        /* Title has some post-processing on the text, to move leading 'A', 'The' etc to the end.
         * While we could do it in a formatter, it it not really a display-oriented function and
         * is handled in preprocessing in the database layer since it also needs to be applied
         * to imported record etc. */
        mFields.add(R.id.field_title, CatalogueDBAdapter.KEY_TITLE, null);

        /* Anthology needs special handling, and we use a formatter to do this. If the original
         * value was 0 or 1, then setting/clearing it here should just set the new value to 0 or 1.
         * However...if if the original value was 2, then we want setting/clearing to alternate
         * between 2 and 0, not 1 and 0.
         * So, despite if being a checkbox, we use an integerValidator and use a special formatter.
         * We also store it in the tag field so that it is automatically serialized with the
         * activity. */
        mFields.add(R.id.field_anthology, BookData.KEY_ANTHOLOGY, null);

        mFields.add(R.id.field_author, "", CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, null);
        mFields.add(R.id.field_isbn, CatalogueDBAdapter.KEY_ISBN, null);

        assert root != null;
        if (root.findViewById(R.id.field_publisher) != null)
            mFields.add(R.id.field_publisher, CatalogueDBAdapter.KEY_PUBLISHER, null);

        if (root.findViewById(R.id.button_date_published) != null)
            mFields.add(R.id.button_date_published, CatalogueDBAdapter.KEY_DATE_PUBLISHED, CatalogueDBAdapter.KEY_DATE_PUBLISHED,
                    null, new Fields.DateFieldFormatter());

        mFields.add(R.id.field_series, CatalogueDBAdapter.KEY_SERIES_NAME, CatalogueDBAdapter.KEY_SERIES_NAME, null);
        mFields.add(R.id.field_list_price, "list_price", null);
        mFields.add(R.id.field_pages, CatalogueDBAdapter.KEY_PAGES, null);
        mFields.add(R.id.field_format, CatalogueDBAdapter.KEY_FORMAT, null);
        //mFields.add(R.id.bookshelf, CatalogueDBAdapter.KEY_BOOKSHELF, null);
        mFields.add(R.id.field_description, CatalogueDBAdapter.KEY_DESCRIPTION, null)
                .setShowHtml(true);
        mFields.add(R.id.field_genre, CatalogueDBAdapter.KEY_GENRE, null);
        mFields.add(R.id.field_language, DatabaseDefinitions.DOM_LANGUAGE.name, null);

        mFields.add(R.id.row_img, "", "thumbnail", null);
        mFields.getField(R.id.row_img).getView().setOnCreateContextMenuListener(mCreateBookThumbContextMenuListener);

        mFields.add(R.id.format_dropdown_button, "", CatalogueDBAdapter.KEY_FORMAT, null);
        mFields.add(R.id.field_bookshelf, "bookshelf_text", null).doNoFetch = true; // Output-only field
        mFields.add(R.id.field_signed, CatalogueDBAdapter.KEY_SIGNED, null);
    }

    /**
     * Initializes {@link #mThumbEditSize} and {@link #mThumbZoomSize} values according
     * to screen size and {@link #MAX_EDIT_THUMBNAIL_SIZE}, {@link #MAX_ZOOM_THUMBNAIL_SIZE}
     * values.<p>
     * Be sure that you set {@link #mMetrics} before. See {@link #setDisplayMetrics()}
     * for it.
     */
    private void initThumbSizes() {
        mThumbEditSize = Math.min(MAX_EDIT_THUMBNAIL_SIZE, Math.max(mMetrics.widthPixels, mMetrics.heightPixels) / 3);
        mThumbZoomSize = Math.min(MAX_ZOOM_THUMBNAIL_SIZE, Math.max(mMetrics.widthPixels, mMetrics.heightPixels));
    }

    /**
     * Get display metrics and set {@link #mMetrics} with it.
     */
    private void setDisplayMetrics() {
        mMetrics = new android.util.DisplayMetrics();
        assert getActivity() != null;
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    /**
     * Populate all fields (See {@link #mFields} ) except of authors and series fields with
     * data from database. To set authors and series fields use {@link #populateAuthorListField()}
     * and {@link #populateSeriesListField()} methods.<br>
     * Also sets #mAuthorList and #mSeriesList values with data from database.
     * Data defined by its _id in db.
     *
     * @param book the selected book.
     */
    protected void populateFieldsFromBook(BookData book) {
        // From the database (edit)
        try {

            populateBookDetailsFields(book);
            setBookThumbnail(book.getRowId(), mThumbEditSize, mThumbEditSize);

        } catch (Exception e) {
            Logger.logError(e);
        }

        populateBookshelvesField(mFields, book);

    }

    /**
     * Inflates all fields with data from cursor and populates UI fields with it.
     * Also set thumbnail of the book.
     *
     * @param book database book record
     */
    protected void populateBookDetailsFields(BookData book) {
        //Set anthology field
        int anthologyNo = book.getInt(BookData.KEY_ANTHOLOGY);
        mFields.getField(R.id.field_anthology).setValue(Integer.toString(anthologyNo)); // Set checked if anthNo != 0
    }

    /**
     * Sets book thumbnail
     */
    protected void setBookThumbnail(Long rowId, int maxWidth, int maxHeight) {
        // Sets book thumbnail
        assert getView() != null;
        ImageView iv = getView().findViewById(R.id.row_img);
        Utils.fetchFileIntoImageView(getCoverFile(rowId), iv, maxWidth, maxHeight, true);
    }

    /**
     * Shows zoomed thumbnail in dialog. Closed by click on image area.
     *
     * @param rowId database row id for getting correct file
     */
    private void showZoomedThumb(Long rowId) {
        // Create dialog and set layout
        final Dialog dialog = new Dialog(requireActivity(), R.style.AppTheme);
        dialog.setContentView(R.layout.dialog_zoom_thumbnail);

        // Check if we have a file and/or it is valid
        File thumbFile = getCoverFile(rowId);

        if (thumbFile == null || !thumbFile.exists()) {
            showCoverContextMenu();
            return;
        } else {

            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(thumbFile.getAbsolutePath(), opt);

            // If no size info, assume file bad and return appropriate icon
            if (opt.outHeight <= 0 || opt.outWidth <= 0) {
                Toast.makeText(getActivity(), R.string.cover_corrupt, Toast.LENGTH_LONG).show();
                return;
            } else {
                dialog.setTitle(getResources().getString(R.string.cover_detail));
                ImageView cover = new ImageView(getActivity());
                Utils.fetchFileIntoImageView(thumbFile, cover, mThumbZoomSize, mThumbZoomSize, true);
                cover.setAdjustViewBounds(true);
                cover.setOnClickListener(v -> dialog.dismiss());

                LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                int pad = getResources().getDimensionPixelOffset(R.dimen.cover_zoom_padding);
                cover.setPadding(pad, pad, pad, pad);
                dialog.addContentView(cover, lp);
            }
        }
        dialog.show();
    }

    /**
     * Gets all bookshelves for the book from database and populate corresponding
     * filed with them.
     *
     * @param fields Fields containing book information
     * @param book   Database book record
     * @return true if populated, false otherwise
     */
    protected boolean populateBookshelvesField(Fields fields, BookData book) {
        boolean result = false;
        try {
            // Display the selected bookshelves
            Field bookshelfTextFe = fields.getField(R.id.field_bookshelf);
            String text = book.getBookshelfText();
            bookshelfTextFe.setValue(book.getBookshelfText());
            if (!text.isEmpty()) {
                result = true; // One or more bookshelves have been set
            }
        } catch (Exception e) {
            Logger.logError(e);
        }
        return result;
    }

    protected void setCoverImage() {
        assert getView() != null;
        ImageView iv = getView().findViewById(R.id.row_img);
        Utils.fetchFileIntoImageView(getCoverFile(mEditManager.getBookData().getRowId()), iv, mThumbEditSize, mThumbEditSize, true);
        // Make sure the cached thumbnails (if present) are deleted
        invalidateCachedThumbnail();
    }
}
