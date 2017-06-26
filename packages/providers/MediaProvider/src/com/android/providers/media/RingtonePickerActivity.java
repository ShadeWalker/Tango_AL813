/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.providers.media;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.HeaderViewListAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
// Add by HQ_pangxuhui_20150410 request AudioFocus 
import android.content.ContentUris;
import android.media.AudioManager;
//End
/**
 * The {@link RingtonePickerActivity} allows the user to choose one from all of the
 * available ringtones. The chosen ringtone's URI will be persisted as a string.
 *
 * @see RingtoneManager#ACTION_RINGTONE_PICKER
 */
public final class RingtonePickerActivity extends AlertActivity implements
        AdapterView.OnItemSelectedListener, Runnable, DialogInterface.OnClickListener,
        AlertController.AlertParams.OnPrepareListViewListener {

    private static final int POS_UNKNOWN = -1;

    private static final String TAG = "RingtonePickerActivity";

    private static final int DELAY_MS_SELECTION_PLAYED = 300;

    private static final String SAVE_CLICKED_POS = "clicked_pos";
    /// M: Request codes to MusicPicker for add more ringtone
    private static final int ADD_MORE_RINGTONES = 1;

    private RingtoneManager mRingtoneManager;

    private Cursor mCursor;
    private Handler mHandler;

    /** The position in the list of the 'Silent' item. */
    private int mSilentPos = POS_UNKNOWN;

    /** The position in the list of the 'Default' item. */
    private int mDefaultRingtonePos = POS_UNKNOWN;

    /** The position in the list of the last clicked item. */
    private int mClickedPos = POS_UNKNOWN;

    /** The position in the list of the ringtone to sample. */
    private int mSampleRingtonePos = POS_UNKNOWN;

    /** Whether this list has the 'Silent' item. */
    private boolean mHasSilentItem;

    /** The Uri to place a checkmark next to. */
    private Uri mExistingUri;

    /** The number of static items in the list. */
    private int mStaticItemCount;

    /** Whether this list has the 'Default' item. */
    private boolean mHasDefaultItem;

    /** The Uri to play when the 'Default' item is clicked. */
    private Uri mUriForDefaultItem;
    
    /** M: Whether this list has the 'More Ringtongs' item. */
    private boolean mHasMoreRingtonesItem = false;
    
    /** M: The position in the list of the 'More Ringtongs' item. */
    private int mMoreRingtonesPos = POS_UNKNOWN;
    
    /** M: The ringtone type to show and add in the list. */
    private int mType = -1;
    
    /** M: Whether need to refresh listview after activity on resume. */
    private boolean mNeedRefreshOnResume = false;
    
    /**
     * A Ringtone for the default ringtone. In most cases, the RingtoneManager
     * will stop the previous ringtone. However, the RingtoneManager doesn't
     * manage the default ringtone for us, so we should stop this one manually.
     */
    private Ringtone mDefaultRingtone;

    /**
     * The ringtone that's currently playing, unless the currently playing one is the default
     * ringtone.
     */
    private Ringtone mCurrentRingtone;

    /**
     * Keep the currently playing ringtone around when changing orientation, so that it
     * can be stopped later, after the activity is recreated.
     */
    private static Ringtone sPlayingRingtone;

    private DialogInterface.OnClickListener mRingtoneClickListener =
            new DialogInterface.OnClickListener() {

        /*
         * On item clicked
         */
        public void onClick(DialogInterface dialog, int which) {
            
            /// M: Show MusicPicker activity to let user choose song to be ringtone @{
            if (which == mMoreRingtonesPos){
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("vnd.android.cursor.dir/audio");
                intent.setData(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, ADD_MORE_RINGTONES);
            /// @}
            } else {
                // Save the position of most recently clicked item
                mClickedPos = which;
                // Play clip
                playRingtone(which, 0);
                /// M: save the uri of current position
                mExistingUri = mRingtoneManager.getRingtoneUri(getRingtoneManagerPosition(which));
            }
        }
        
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandler = new Handler();

        Intent intent = getIntent();

        /*
         * Get whether to show the 'Default' item, and the URI to play when the
         * default is clicked
         */
        mHasDefaultItem = intent.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        mUriForDefaultItem = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI);
        if (mUriForDefaultItem == null) {
            mUriForDefaultItem = Settings.System.DEFAULT_RINGTONE_URI;
        }

        if (savedInstanceState != null) {
            mClickedPos = savedInstanceState.getInt(SAVE_CLICKED_POS, POS_UNKNOWN);
        }
        // Get whether to show the 'Silent' item
        mHasSilentItem = intent.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        
        /// M: Get whether to show the 'More Ringtones' item
        mHasMoreRingtonesItem = intent.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_MORE_RINGTONES, false);
        
        // Give the Activity so it can do managed queries
        mRingtoneManager = new RingtoneManager(this);

        // Get whether to include DRM ringtones
        final boolean includeDrm = intent.getBooleanExtra(
                RingtoneManager.EXTRA_RINGTONE_INCLUDE_DRM, true);
        mRingtoneManager.setIncludeDrm(includeDrm);
        
        // Get the types of ringtones to show
        mType = intent.getIntExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, -1);
        if (mType != -1) {
            mRingtoneManager.setType(mType);
        }

        mCursor = mRingtoneManager.getCursor();

        // The volume keys will control the stream that we are choosing a ringtone for
        setVolumeControlStream(mRingtoneManager.inferStreamType());

        // Get the URI whose list item should have a checkmark
        mExistingUri = intent
                .getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI);

        final AlertController.AlertParams p = mAlertParams;
        p.mCursor = mCursor;
        p.mOnClickListener = mRingtoneClickListener;
        p.mLabelColumn = MediaStore.Audio.Media.TITLE;
        p.mIsSingleChoice = true;
        p.mOnItemSelectedListener = this;
        p.mPositiveButtonText = getString(com.android.internal.R.string.ok);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = getString(com.android.internal.R.string.cancel);
        p.mPositiveButtonListener = this;
        p.mOnPrepareListViewListener = this;

        p.mTitle = intent.getCharSequenceExtra(RingtoneManager.EXTRA_RINGTONE_TITLE);
        if (p.mTitle == null) {
            p.mTitle = getString(com.android.internal.R.string.ringtone_picker_title);
        }

        setupAlert();
    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        MtkLog.d(TAG, "onSaveInstanceState: mClickedPos = " + mClickedPos);
        outState.putInt(SAVE_CLICKED_POS, mClickedPos);
    }

    public void onPrepareListView(ListView listView) {
        MtkLog.d(TAG, "onPrepareListView>>>: mClickedPos = " + mClickedPos);
        /// M: Add "More Ringtone" to the top of listview to let user choose more ringtone
        if (mHasMoreRingtonesItem) {
            mMoreRingtonesPos = addMoreRingtonesItem(listView);
        }
        
        if (mHasDefaultItem) {
            mDefaultRingtonePos = addDefaultRingtoneItem(listView);

            if (RingtoneManager.isDefault(mExistingUri)) {
                mClickedPos = mDefaultRingtonePos;
            }
        }

        if (mHasSilentItem) {
            mSilentPos = addSilentItem(listView);

            // The 'Silent' item should use a null Uri
            if (mExistingUri == null) {
                mClickedPos = mSilentPos;
            }
        }

        if (mClickedPos == POS_UNKNOWN) {
            /// M: if the given uri not exist, show default ringtone.
            if (RingtoneManager.isRingtoneExist(getApplicationContext(), mExistingUri)) {
                mClickedPos = getListPosition(mRingtoneManager.getRingtonePosition(mExistingUri));
            } else {
                if (mHasDefaultItem) {
                    mClickedPos = mDefaultRingtonePos;
                } else {
                    mClickedPos = getListPosition(mRingtoneManager.getRingtonePosition(
                            RingtoneManager.getDefaultRingtoneUri(getApplicationContext(), mType)));
                }
                
            }
        }
        
        // Put a checkmark next to an item.
        mAlertParams.mCheckedItem = mClickedPos;
        MtkLog.d(TAG, "onPrepareListView<<<: mClickedPos = " + mClickedPos + ", mExistingUri = " + mExistingUri);
    }

    /**
     * Adds a static item to the top of the list. A static item is one that is not from the
     * RingtoneManager.
     *
     * @param listView The ListView to add to.
     * @param textResId The resource ID of the text for the item.
     * @return The position of the inserted item.
     */
    private int addStaticItem(ListView listView, int textResId) {
        TextView textView = (TextView) getLayoutInflater().inflate(
                com.android.internal.R.layout.select_dialog_singlechoice_material, listView, false);
        textView.setText(textResId);
        listView.addHeaderView(textView);
        mStaticItemCount++;
        return listView.getHeaderViewsCount() - 1;
    }

    private int addDefaultRingtoneItem(ListView listView) {
        return addStaticItem(listView, com.android.internal.R.string.ringtone_default);
    }

    private int addSilentItem(ListView listView) {
        return addStaticItem(listView, com.android.internal.R.string.ringtone_silent);
    }

    /*
     * On click of Ok/Cancel buttons
     */
    public void onClick(DialogInterface dialog, int which) {
        boolean positiveResult = which == DialogInterface.BUTTON_POSITIVE;

        // Stop playing the previous ringtone
        mRingtoneManager.stopPreviousRingtone();

        if (positiveResult) {
            Intent resultIntent = new Intent();
            Uri uri = null;

            if (mClickedPos == mDefaultRingtonePos) {
                // Set it to the default Uri that they originally gave us
                uri = mUriForDefaultItem;
                /// M: When the ringtone list has not init, we should set the ringtone to be null instead set to
                /// to be mUriForDefaultItem, because we should not set value to ringtone before MediaScanner init
                /// these ringtone uri. {@
                if (mDefaultRingtonePos == POS_UNKNOWN) {
                    uri = null;
                    MtkLog.w(TAG, "onClick with no list item, set uri to be null! mDefaultRingtonePos = "
                            + mDefaultRingtonePos);
                }
                /// @}
            } else if (mClickedPos == mSilentPos) {
                // A null Uri is for the 'Silent' item
                uri = null;
            } else {
                uri = mRingtoneManager.getRingtoneUri(getRingtoneManagerPosition(mClickedPos));
            }

            resultIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, uri);
            /// M: return the picked position to caller to distinguish default item and the same ringtone item.
            resultIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_POSITION, mClickedPos);
            setResult(RESULT_OK, resultIntent);
        } else {
            setResult(RESULT_CANCELED);
        }

        getWindow().getDecorView().post(new Runnable() {
            public void run() {
                mCursor.deactivate();
            }
        });

        finish();
    }

    /*
     * On item selected via keys
     */
    public void onItemSelected(AdapterView parent, View view, int position, long id) {
        playRingtone(position, DELAY_MS_SELECTION_PLAYED);
    }

    public void onNothingSelected(AdapterView parent) {
    }

    private void playRingtone(int position, int delayMs) {
	    ((AudioManager) getSystemService(AUDIO_SERVICE)).requestAudioFocus(null, AudioManager.STREAM_RING,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);/* add by HQ_pangxuhui_20150410 request AudioFocus */
        mHandler.removeCallbacks(this);
        mSampleRingtonePos = position;
        mHandler.postDelayed(this, delayMs);
    }

    public void run() {
        stopAnyPlayingRingtone();
        if (mSampleRingtonePos == mSilentPos) {
            return;
        }
        
        /*
         * Stop the default ringtone, if it's playing (other ringtones will be
         * stopped by the RingtoneManager when we get another Ringtone from it.
         */
        if (mDefaultRingtone != null && mDefaultRingtone.isPlaying()) {
            mDefaultRingtone.stop();
            mDefaultRingtone = null;
        }
        
        Ringtone ringtone;
        if (mSampleRingtonePos == mDefaultRingtonePos) {
            /// M: if default URI is "", it means silent if default item selected. @{
            if(mUriForDefaultItem.toString().isEmpty()) {
                return;
            }
            ///@}

            if (mDefaultRingtone == null) {
                mDefaultRingtone = RingtoneManager.getRingtone(this, mUriForDefaultItem);
            }
           /*
            * Stream type of mDefaultRingtone is not set explicitly here.
            * It should be set in accordance with mRingtoneManager of this Activity.
            */
            if (mDefaultRingtone != null) {
                mDefaultRingtone.setStreamType(mRingtoneManager.inferStreamType());
            }
            ringtone = mDefaultRingtone;
            mCurrentRingtone = null;
        } else {
            ringtone = mRingtoneManager.getRingtone(getRingtoneManagerPosition(mSampleRingtonePos));
            mCurrentRingtone = ringtone;
        }

        if (ringtone != null) {
            ringtone.play();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            stopAnyPlayingRingtone();
        } else {
            saveAnyPlayingRingtone();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isChangingConfigurations()) {
            stopAnyPlayingRingtone();
        }
        mNeedRefreshOnResume = true;
    }

    private void saveAnyPlayingRingtone() {
        if (mDefaultRingtone != null && mDefaultRingtone.isPlaying()) {
            MtkLog.d(TAG, "saveAnyPlayingRingtone>>>:  mDefaultRingtone "  + mDefaultRingtone);
            sPlayingRingtone = mDefaultRingtone;
        } else if (mCurrentRingtone != null && mCurrentRingtone.isPlaying()) {
            MtkLog.d(TAG, "saveAnyPlayingRingtone>>>:   mCurrentRingtone "  + mCurrentRingtone);
            sPlayingRingtone = mCurrentRingtone;
        }
    }

    private void stopAnyPlayingRingtone() {
        if (sPlayingRingtone != null && sPlayingRingtone.isPlaying()) {
            sPlayingRingtone.stop();
        }
        sPlayingRingtone = null;

        if (mDefaultRingtone != null && mDefaultRingtone.isPlaying()) {
            mDefaultRingtone.stop();
        }

        if (mRingtoneManager != null) {
            mRingtoneManager.stopPreviousRingtone();
        }
    }

    private int getRingtoneManagerPosition(int listPos) {
        return listPos - mStaticItemCount;
    }

    private int getListPosition(int ringtoneManagerPos) {

        // If the manager position is -1 (for not found), return that
        if (ringtoneManagerPos < 0) return ringtoneManagerPos;

        return ringtoneManagerPos + mStaticItemCount;
    }
    
    /// M: Add to restore user's choice after activity has been killed.
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        MtkLog.d(TAG, "onRestoreInstanceState: savedInstanceState = " + savedInstanceState + ",mClickedPos = " +
                mClickedPos + ",this = " + this);
        super.onRestoreInstanceState(savedInstanceState);
        mClickedPos = savedInstanceState.getInt(SAVE_CLICKED_POS, mClickedPos);
    }
    
    /// M: Add to refresh activity because some new ringtones will insert to listview.
    @Override
    protected void onResume() {
        super.onResume();
        MtkLog.d(TAG, "onResume>>>: mNeedRefreshOnResume = " + mNeedRefreshOnResume);
        /// When activity first start, just return. Only when restart we need to refresh in resume.
        if (!mNeedRefreshOnResume) {
            return;
        }
        MtkLog.d(TAG, "onResume>>>: mClickedPos = " + mClickedPos);
        ListView listView = mAlert.getListView();
        if (null == listView) {
            MtkLog.e(TAG, "onResume: listview is null, return!");
            return;
        }
        /// Refresh the checked position after activity resume, because maybe there are new ringtone
        /// insert to listview.
        ListAdapter adapter = listView.getAdapter();
        ListAdapter headAdapter = adapter;
        if (null != headAdapter && (headAdapter instanceof HeaderViewListAdapter)) {
            /// Get the cursor adapter with the listview
            adapter = ((HeaderViewListAdapter) headAdapter).getWrappedAdapter();
            mCursor = mRingtoneManager.getNewCursor();
            ((SimpleCursorAdapter) adapter).changeCursor(mCursor);
            MtkLog.d(TAG, "onResume: notify adapter update listview with new cursor!");
        } else {
            MtkLog.e(TAG, "onResume: cursor adapter is null!");
        }
        /// Get position from ringtone list with this uri, if the return position is
        /// valid value, set it to be current clicked position
        if ((mClickedPos >= mStaticItemCount || ( mHasSilentItem && mClickedPos == 1)) && (null != mExistingUri)) {
            /// M: TODO avoid cursor out of bound, so move cursor position.
            if (null != mCursor && mCursor.moveToFirst()) {
                mClickedPos = getListPosition(mRingtoneManager.getRingtonePosition(mExistingUri));
                MtkLog.d(TAG, "onResume: get the position of uri = " + mExistingUri
                        + ", position = " + mClickedPos);
                if (POS_UNKNOWN != mClickedPos) {
                    mAlertParams.mCheckedItem = mClickedPos;
                } else {
                    MtkLog.w(TAG, "onResume: get position is invalid!");
                }
            }
        }
        
        /// If no ringtone has been checked, show default instead.
        if (POS_UNKNOWN == mClickedPos) {
            MtkLog.w(TAG, "onResume: no ringtone checked, show default instead!");
            if (mHasDefaultItem) {
                mClickedPos = mDefaultRingtonePos;
            } else {
                if (null != mCursor && mCursor.moveToFirst()) {
                    mClickedPos = getListPosition(mRingtoneManager.getRingtonePosition(
                            RingtoneManager.getDefaultRingtoneUri(getApplicationContext(), mType)));
                }
            }
        }
        listView.setItemChecked(mClickedPos, true);
        listView.setSelection(mClickedPos);
        mNeedRefreshOnResume = false;
        MtkLog.d(TAG, "onResume<<<: set position to be checked: mClickedPos = " + mClickedPos);
    }
    
    /// M: Add to close cursor if the cursor is not close when activity destroy and remove messages
    @Override
    protected void onDestroy() {
       ((AudioManager) getSystemService(AUDIO_SERVICE)).abandonAudioFocus(null);/* add by HQ_pangxuhui_20150410 request AudioFocus */
        mHandler.removeCallbacksAndMessages(null);
        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.close();
        }
        super.onDestroy();
    }
    
    /**
     * M: Add more ringtone item to given listview and return it's position.
     * 
     * @param listView The listview which need to add more ringtone item.
     * @return The position of more ringtone item in listview
     */
    private int addMoreRingtonesItem(ListView listView) {
        TextView textView = (TextView) getLayoutInflater().inflate(
                com.android.internal.R.layout.simple_list_item_1, listView, false);
        textView.setText(R.string.Add_More_Ringtones);
        listView.addHeaderView(textView);
        mStaticItemCount++;
        return listView.getHeaderViewsCount() - 1;
    }
    
    /// M: Add to handle user choose a ringtone from MusicPicker
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case ADD_MORE_RINGTONES:
                if (resultCode == RESULT_OK) {
                    Uri uri = (null == intent ? null : intent.getData());
                    if (uri != null ) {
                        setRingtone(this.getContentResolver(), uri);
                        MtkLog.v(TAG, "onActivityResult: RESULT_OK, so set to be ringtone! " + uri);
                    }
                } else {
                    MtkLog.v(TAG, "onActivityResult: Cancel to choose more ringtones, so do nothing!");
                }
                break;
        }
    }
    
    /**
     * M: Set the given uri to be ringtone
     * 
     * @param resolver content resolver
     * @param uri the given uri to set to be ringtones
     */
    private void setRingtone(ContentResolver resolver, Uri uri) {
        /// Set the flag in the database to mark this as a ringtone
        try {
            ContentValues values = new ContentValues(1);
            if ((RingtoneManager.TYPE_RINGTONE == mType)
                    || (RingtoneManager.TYPE_VIDEO_CALL == mType)
                    || (RingtoneManager.TYPE_SIP_CALL == mType)) {
                values.put(MediaStore.Audio.Media.IS_RINGTONE, "1");
            } else if (RingtoneManager.TYPE_ALARM == mType) {
                values.put(MediaStore.Audio.Media.IS_ALARM, "1");
            } else if (RingtoneManager.TYPE_NOTIFICATION == mType) {
                values.put(MediaStore.Audio.Media.IS_NOTIFICATION, "1");
            } else {
                MtkLog.e(TAG, "Unsupport ringtone type =  " + mType);
                return;
            }
            resolver.update(uri, values, null, null);
            /// Restore the new uri and set it to be checked after resume
            mExistingUri = uri;
        } catch (UnsupportedOperationException ex) {
            /// most likely the card just got unmounted
            MtkLog.e(TAG, "couldn't set ringtone flag for uri " + uri);
        }
    }
}
