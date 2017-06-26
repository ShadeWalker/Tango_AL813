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

package com.android.music;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class CreatePlaylist extends Activity
{
    /// M: add for debug log
    private static final String TAG = "CreatePlaylist";
    /// M: add dialog key
    private static final int ALERT_DIALOG_KEY = 0;

    private EditText mPlaylist;
    private Button mSaveButton;

    /// M: add for dialog
    private View mView;
    private MusicDialog mDialog;
    private String mPrompt;

    /** M: Add to restore selected item id, such as album id, artist id and audio id. */
    private String mSelectItemId = null;
    /** M: Add to restore start activity tab, so that result can return to right activity. */
    private int mStartActivityTab = -1;
    private Intent mIntent = null;
    /// M: Add to indicate the  save_as_playlist and new_playlsit
    private String mPlaylistFlag = "";

    private String mPlaylistName = null;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        /// M: get layout for create playlist @{
        mView = getLayoutInflater().inflate(R.layout.create_playlist, null);
        mPlaylist = (EditText) mView.findViewById(R.id.playlist);
        /// @}

        String defaultname = icicle != null ? icicle.getString("defaultname")
                : MusicUtils.makePlaylistName(getApplicationContext(), getString(R.string.new_playlist_name_template));
        if (defaultname == null) {
            String toastShow = getString(R.string.save_playlist_error);
            Toast.makeText(this, toastShow, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String promptformat = getString(R.string.create_playlist_create_text_prompt);
        /// M: get string for display in dialog
        mPrompt = String.format(promptformat, defaultname);
        mPlaylist.setText(defaultname);
        mPlaylist.setSelection(defaultname.length());
        mPlaylist.addTextChangedListener(mTextWatcher);
        /// M: register receiver about scanning sdcard @{
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_MEDIA_EJECT);
        f.addDataScheme("file");
        mIntent = registerReceiver(mScanListener, f);
        /// @}

        /// M: Only show dialog when activity first onCreate(), because if activity is restarted
        /// (Such as change language cause activity restart), system will restore the dialog. {@
        if (icicle == null) {
            showDialog(ALERT_DIALOG_KEY);
        }
        /// @}

        /// M: Restore ADD_TO_PLAYLIST_ITEM_ID in mSelectItemId to return back.
        Intent intent = getIntent();
        if (intent != null) {
            mSelectItemId = intent.getStringExtra(MusicUtils.ADD_TO_PLAYLIST_ITEM_ID);
            mStartActivityTab = intent.getIntExtra(MusicUtils.START_ACTIVITY_TAB_ID, -1);
            mPlaylistFlag = intent.getStringExtra(MusicUtils.SAVE_PLAYLIST_FLAG);
            mPlaylistName = intent.getStringExtra(MusicUtils.PLAYLIST_NAME);
        }
    }
    
    TextWatcher mTextWatcher = new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // don't care about this one
        }
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            /// M: move the original code to setSaveButton
            setSaveButton();
        };
        public void afterTextChanged(Editable s) {
            // don't care about this one
        }
    };

    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        outcicle.putString("defaultname", mPlaylist.getText().toString());
    }
    
    @Override
    public void onResume() {
        super.onResume();
        /// M: update the save button @{
        setSaveButton();
        /// @}
    }

    /**
     * M: override onDestroy() for unregister scan sdcard recever
     */
    @Override
    public void onDestroy() {
        if (mIntent != null) {
            unregisterReceiver(mScanListener);
        }
        super.onDestroy();
    }

    /**
     * M: override onCreateDialog() for create playlist dialog
     *
     * @param id The new create dialog id
     * @return Return a new dialog
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == ALERT_DIALOG_KEY) {
            mDialog = new MusicDialog(this, mButtonClicked, mView);
            mDialog.setTitle(mPrompt);
            mDialog.setPositiveButton(getResources().getString(
                    R.string.create_playlist_create_text));
            mDialog.setNeutralButton(getResources().getString(R.string.cancel));
            mDialog.setCanceledOnTouchOutside(true);
            mDialog.setCancelable(true);
            mDialog.setSearchKeyListener();
            return mDialog;
        }
        return null;
    }

    /**
     * M: update save button when the edit text is changing;
     */
    private void setSaveButton() {
        String newText = mPlaylist.getText().toString();
        if (mDialog == null) {
            MusicLogUtils.e(TAG, "setSaveButton with dialog is null return!");
            return;
        }
        if (mSaveButton == null) {
            mSaveButton = (Button) mDialog.getPositiveButton();
        }
        if (mSaveButton != null) {
            if (newText.trim().length() == 0) {
                mSaveButton.setEnabled(false);
            } else {
                mSaveButton.setEnabled(true);
                /// check if playlist with current name exists already, and warn the user if need.
                if (MusicUtils.idForplaylist(getApplicationContext(), newText.trim()) >= 0) {
                    mSaveButton.setText(R.string.create_playlist_overwrite_text);
                } else {
                    mSaveButton.setText(R.string.create_playlist_create_text);
                }
            }
        }
        MusicLogUtils.d(TAG, "setSaveButton " + mSaveButton);
    }

    /**
     * M: a button click listener for dialog; positive for save playlist;
     * neutral for quit directly.
     */
    private DialogInterface.OnClickListener mButtonClicked = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialogInterface, int button) {
            if (button == DialogInterface.BUTTON_POSITIVE) {
                String name = mPlaylist.getText().toString();
                if (name != null && name.length() > 0) {
                    name = name.trim();
                    Intent intent = new Intent();
                    ContentResolver resolver = getContentResolver();
                    int id = MusicUtils.idForplaylist(getApplicationContext(), name);
                    Uri uri = null;
                    /// M: Don't need to clearPlaylist when SAVE_AS_PLAYLIST @{
                    if (id >= 0 && (mPlaylistFlag.equals(MusicUtils.NEW_PLAYLIST))) {
                        uri = ContentUris.withAppendedId(
                                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, id);
                        if (MusicUtils.clearPlaylist(CreatePlaylist.this, id) == -1) {
                            finish();
                            return;
                        }
                        ContentValues values = new ContentValues(1);
                        values.put(MediaStore.Audio.Playlists.NAME, name);
                        resolver.update(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                                values, MediaStore.Audio.Playlists._ID + "=?", new String[] {
                                    Long.valueOf(id).toString()
                                });
                    } else if (id >= 0 && mPlaylistFlag.equals(MusicUtils.SAVE_AS_PLAYLIST)) {
                        if (!name.equals(mPlaylistName) && name.equalsIgnoreCase(mPlaylistName)) {
                            ContentValues values = new ContentValues(1);
                            values.put(MediaStore.Audio.Playlists.NAME, name);
                            resolver.update(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                                    values, MediaStore.Audio.Playlists._ID + "=?", new String[] {
                                        Long.valueOf(id).toString()
                                    });
                        } else if (!name.equals(mPlaylistName) && !name.equalsIgnoreCase(mPlaylistName)) {
                            //ContentValues values = new ContentValues(1);
                            //values.put(MediaStore.Audio.Playlists.NAME, name);
                            try {
                                int newPid = MusicUtils.idForplaylist(getApplicationContext(), name);
                                if (newPid >= 0) {
                                    uri = ContentUris
                                            .withAppendedId(
                                                    MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                                                    newPid);
                                    if (MusicUtils.clearPlaylist(CreatePlaylist.this, newPid) == -1) {
                                        finish();
                                        return;
                                    }
                                    ContentValues valuesNew = new ContentValues(1);
                                    valuesNew.put(MediaStore.Audio.Playlists.NAME, name);
                                    resolver.update(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                                            valuesNew, MediaStore.Audio.Playlists._ID + "=?", new String[] {
                                                Long.valueOf(newPid).toString()
                                            });
                                }
                                //resolver.insert(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                                        //values);
                            } catch (UnsupportedOperationException ex) {
                                MusicLogUtils.d(TAG, "OnClickListener() with UnsupportedOperationException:" + ex);
                                finish();
                                return;
                            }
                        }
                        intent.putExtra(MusicUtils.SAVE_PLAYLIST_FLAG, MusicUtils.SAVE_AS_PLAYLIST);
                    } else {
                        ContentValues values = new ContentValues(1);
                        values.put(MediaStore.Audio.Playlists.NAME, name);
                        try {
                            uri = resolver.insert(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                                    values);
                        } catch (UnsupportedOperationException ex) {
                            MusicLogUtils.d(TAG, "OnClickListener() with UnsupportedOperationException:" + ex);
                            finish();
                            return;
                        }
                    }
                    /// @}
                    intent.setData(uri);
                    /// M: Return the selected add to playlist item id to started activity.
                    intent.putExtra(MusicUtils.ADD_TO_PLAYLIST_ITEM_ID, mSelectItemId);
                    /// M: Return the start activity tab to MusicBrowser to return result to right activity.
                    intent.putExtra(MusicUtils.START_ACTIVITY_TAB_ID, mStartActivityTab);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            } else if (button == DialogInterface.BUTTON_NEUTRAL) {
                finish();
            }
        }
    };

    /**
     * M: Finish create playlist activity when sdcard has been unmounted.
     */
    private BroadcastReceiver mScanListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            /// M: When SD card is unmounted, finish the create playlist activity
            finish();
            MusicLogUtils.d(TAG, "SD card is ejected, finish CreatePlaylist activity!");
        }
    };

}
