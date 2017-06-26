package com.android.music;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;


import com.mediatek.drm.OmaDrmStore;
import com.mediatek.drm.OmaDrmClient;
import com.mediatek.drm.OmaDrmUiUtils;
/**
 * M: AudioPreviewStarter is an Activity which is used to check the DRM file
 * and decide launch the AudioPreview or not.
 */
public class AudioPreviewStarter extends Activity
        implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

    private static final String TAG = "AudioPreStarter";
    private Intent mIntent;
    /// M: Use member variable to show toast to avoid show the toast on screen for a long time if user click many time.
    private Toast mToast;
    /// M: Drm manager client.
    private OmaDrmClient mDrmClient = null;
    /**
        * M: onCreate to check the DRM file
        * and decide launch the AudioPreview or show DRM dialog.
        *
        * @param icicle If the activity is being re-initialized after
        *     previously being shut down then this Bundle contains the data it most
        *     recently supplied in
        */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        MusicLogUtils.v(TAG, ">> onCreate");
        Intent intent = getIntent();
        Uri uri = intent.getData();
        if (uri == null) {
            finish();
            return;
        }
        MusicLogUtils.v(TAG, "uri=" + uri);
        mIntent = new Intent(getIntent());
        mIntent.setClass(this, AudioPreview.class);
        if (!MusicFeatureOption.IS_SUPPORT_DRM) {
            MusicLogUtils.v(TAG, "DRM is off");
            startActivity(mIntent);
            finish();
            return;
        }
        /// M: Add for CTA level 5, check taken valid if open from DataProtection
        mDrmClient = new OmaDrmClient(this);
        final String ctaAction = "com.mediatek.dataprotection.ACTION_VIEW_LOCKED_FILE";
        String action = intent.getAction();
        if (ctaAction.equals(action)) {
            String token = intent.getStringExtra("TOKEN");
            String tokenKey = intent.getData().toString();
            MusicLogUtils.v(TAG, "onCreate: action = " + action + ", tokenKey = " + tokenKey + ", token = " + token);
            if (token == null || !mDrmClient.isTokenValid(tokenKey, token)) {
                finish();
            }
        }
        processForDrm(uri);
        MusicLogUtils.v(TAG, "<< onCreate");
    }

    /**
     * M: handle the DRM dialog click event.
     *
     * @param dialog
     *            The dialog that was dismissed will be passed into the method.
     * @param which
     *            The button that was clicked.
     */
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            /// M: continue to play
            MusicLogUtils.v(TAG, "onClick: BUTTON_POSITIVE");
            startActivity(mIntent);
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            /// M: do nothing but finish itself
            MusicLogUtils.v(TAG, "onClick: BUTTON_NEGATIVE");
        } else {
            MusicLogUtils.w(TAG, "undefined button on DRM consume dialog!");
        }
    }

    /**
     * M: finish itself when dialog dismiss.
     *
     * @param dialog
     *            The dialog that was dismissed will be passed into the method.
     */
    public void onDismiss(DialogInterface dialog) {
        MusicLogUtils.v(TAG, "onDismiss");
        finish();
    }

    /**
     * M: the method is to do DRM process by uri.
     *
     * @param uri
     *            the uri of the playing file
     */
    private void processForDrm(Uri uri) {
        final String schemeContent = "content";
        final String schemeFile = "file";
        final String hostMedia = "media";
        final String drmFileSuffix = ".dcf";
        final int isDrmIndex = 1;
        final int drmMethonIndex = 2;
        boolean isFileInDB = false;
        String scheme = uri.getScheme();
        String host = uri.getHost();
        MusicLogUtils.v(TAG, "scheme=" + scheme + ", host=" + host);
        /// M: to resolve the bug when modify suffix of drm file
        /// ALPS00677354 @{
        ContentResolver resolver = getContentResolver();
        Cursor c = null;
        if (schemeContent.equals(scheme) && hostMedia.equals(host)) {
            /// M: query DB for drm info
            c = resolver.query(uri, new String[] {
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.IS_DRM,
                    MediaStore.Audio.Media.DRM_METHOD
            }, null, null, null);
        } else if (schemeFile.equals(scheme)) {
            /// M: a file opened from FileManager/ other app
            String path = uri.getPath();
            path = path.replaceAll("'", "''");
            MusicLogUtils.v(TAG, "file path=" + path);
            if (path == null) {
                finish();
                return;
            }
            Uri contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            StringBuilder where = new StringBuilder();
            where.append(MediaStore.Audio.Media.DATA + "='" + path + "'");
            c = resolver.query(contentUri, new String[] {
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.IS_DRM,
                    MediaStore.Audio.Media.DRM_METHOD
            }, where.toString(), null, null);
        }
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    /// M: cursor is valid
                    isFileInDB = true;
                    int isDrm = c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_DRM));
                    MusicLogUtils.v(TAG, "isDrm=" + isDrm);
                    if (isDrm == 1) {
                        /// M: is a DRM file
                        checkDrmRightStatus(uri, c.getInt(drmMethonIndex));
                        return;
                    }
                }
            } finally {
                c.close();
            }
        }
        if (!isFileInDB) {
            String path = uri.getPath();
            path = path.replaceAll("'", "''");
            if (path.endsWith(drmFileSuffix)) {
                MusicLogUtils.d(TAG, "drm file is not in db, file path=" + path);
                showToast(getString(R.string.playback_failed));
                finish();
                return;
            }
        }
        /// @}
        startActivity(mIntent);
        finish();
    }

    /**
     * M: the method is to check the drm right of the playing file.
     *
     * @param uri
     *            the uri of the playing file
     * @param drmMethod
     *            the drm method of the playing file, it will retrive by drm client if the value is -1
     */
    private void checkDrmRightStatus(Uri uri, int drmMethod) {
        int rightsStatus = -1;
        int method = drmMethod;
        /// M: when modify the suffix of drm file ,drmMedthod in db is -1 in JB edtion
        if (method == -1) {
            showToast(getString(R.string.playback_failed));
            finish();
            return;
        }
        MusicLogUtils.v(TAG, "drmMethod=" + method);

        try {
            rightsStatus = mDrmClient.checkRightsStatus(uri, OmaDrmStore.Action.PLAY);
        } catch (IllegalArgumentException e) {
            MusicLogUtils.e(TAG, "checkRightsStatus throw IllegalArgumentException " + e);
        }
        MusicLogUtils.v(TAG, "checkDrmRightStatus: rightsStatus=" + rightsStatus);
        switch (rightsStatus) {
            case OmaDrmStore.RightsStatus.RIGHTS_VALID:
                if (method == OmaDrmStore.DrmMethod.METHOD_FL) {
                    /// M: FL does not have constraints
                    startActivity(mIntent);
                    finish();
                    return;
                }
                OmaDrmUiUtils.showConsumeDialog(this, this, this);
                break;
            case OmaDrmStore.RightsStatus.RIGHTS_INVALID:
                if (method == OmaDrmStore.DrmMethod.METHOD_FL) {
                    /// M: FL does not have constraints
                    showToast(getString(R.string.fl_invalid));
                    finish();
                    return;
                }
                Dialog licenseDialog = OmaDrmUiUtils.showRefreshLicenseDialog(mDrmClient, this, uri, this);
                if (licenseDialog == null || method == OmaDrmStore.DrmMethod.METHOD_CD) {
                    finish();
                }
                break;
            case OmaDrmStore.RightsStatus.SECURE_TIMER_INVALID:
                OmaDrmUiUtils.showSecureTimerInvalidDialog(this, null, this);
                break;
            default:
                break;
        }
    }
    /**
     * M: Show the given text to screen.
     *
     * @param toastText Need show text.
     */
    private void showToast(CharSequence toastText) {
        if (mToast == null) {
            mToast = Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_SHORT);
        }
        mToast.setText(toastText);
        mToast.show();
    }

    @Override
    protected void onDestroy() {
        if (mDrmClient != null) {
            mDrmClient.release();
            mDrmClient = null;
        }
        super.onDestroy();
    }
}
