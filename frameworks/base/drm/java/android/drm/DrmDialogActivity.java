/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.drm;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

/**
 * M: Add for huawei, use to show renew and expire dialog
 * 
 * @hide
 */
public class DrmDialogActivity extends AlertActivity implements DialogInterface.OnClickListener {

    private static final String TAG = "DrmDialogActivity";
    private int mDrmMethod;
    private String mRightIssuer;
    private String mPath;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: " + getIntent());
	/*modified by lhz at 20160506 for HQ01908928 by begin */
        try
	{
	Bundle extras = getIntent().getExtras();
        mRightIssuer = extras.getString(DrmStore.MetadataKey.META_KEY_RIGHTS_ISSUER);
        mDrmMethod = extras.getInt(DrmStore.MetadataKey.META_KEY_METHOD);
        mPath = extras.getString("path");
        createDialog();
	}
	catch(NullPointerException ex)
	{
	Log.e(TAG, "extras.getString: " + ex);	
	}
	/*modified by lhz at 20160506 for HQ01908928 by end */
    }

    private void createDialog() {
        final AlertController.AlertParams ap = mAlertParams;
        if (mDrmMethod == DrmStore.DrmMethod.METHOD_CD) {
            ap.mMessage = getString(com.mediatek.internal.R.string.drm_toast_license_expired);
            ap.mNegativeButtonText = getString(android.R.string.ok);
            ap.mNegativeButtonListener = this;
        } else if (mRightIssuer != null) {
            //ap.mTitle = getString(com.mediatek.internal.R.string.drm_toast_license_expired);
	    ap.mTitle = getString(com.mediatek.internal.R.string.drm_licenseacquisition_title);
            ap.mMessage = String.format(getString(
                    com.mediatek.internal.R.string.drm_licenseacquisition_message), mPath);
            ap.mPositiveButtonText = getString(
                    com.mediatek.internal.R.string.drm_protectioninfo_renew);
            ap.mPositiveButtonListener = this;
            ap.mNegativeButtonText = getString(android.R.string.cancel);
            ap.mNegativeButtonListener = this;
        }
        setupAlert();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if ((which == DialogInterface.BUTTON_POSITIVE) && (mRightIssuer != null)) {
            Log.d(TAG, "renew to start browser to download right");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mRightIssuer));
            startActivity(intent);
        }
        finish();
    }
}

