/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/

/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class RenamePlaylist extends Activity
{
    /// M: add for debug log
    private static final String TAG = "RenamePlaylist";
    /// M: add dialog key
    private static final int ALERT_DIALOG_KEY = 0;
    private EditText mPlaylist;
    private Button mSaveButton;
    private long mRenameId;
    private String mOriginalName;
    /// M: dialog, view, prompt and exist id  @{
    private MusicDialog mDialog;
    private View mView;
    private long mExistingId;
    private String mPrompt;
    /// @}

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        MusicLogUtils.d(TAG, "onCreate");
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        /// M: get view by layoutinflater
        mView = getLayoutInflater().inflate(R.layout.create_playlist, null);
        mPlaylist = (EditText) mView.findViewById(R.id.playlist);

        mRenameId = icicle != null ? icicle.getLong("rename")
                : getIntent().getLongExtra("rename", -1);
        /// M: get exist id to restore when activity is restart again
        mExistingId = icicle != null ? icicle.getLong("existing", -1)
                : getIntent().getLongExtra("existing", -1);
        mOriginalName = nameForId(mRenameId);
        String defaultname = icicle != null ? icicle.getString("defaultname") : mOriginalName;
        
        if (mRenameId < 0 || mOriginalName == null || defaultname == null) {
            MusicLogUtils.i(TAG, "Rename failed: " + mRenameId + "/" + defaultname);
            finish();
            return;
        }
        
        String promptformat;
        if (mOriginalName.equals(defaultname)) {
            promptformat = getString(R.string.rename_playlist_same_prompt);
        } else {
            promptformat = getString(R.string.rename_playlist_diff_prompt);
        }
        /// M: get prompt string
        mPrompt = String.format(promptformat, mOriginalName, defaultname);
        mPlaylist.setText(defaultname);
        mPlaylist.setSelection(defaultname.length());
        mPlaylist.addTextChangedListener(mTextWatcher);
        /// M: register broadcast for the status change of SD Card @{
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_MEDIA_EJECT);
        f.addDataScheme("file");
        registerReceiver(mScanListener, f);
        /// @}

        /// M: Only show dialog when activity first onCreate(), because if activity is restarted(Such as change
        /// language cause activity restart), system will restore the dialog. {@
        if (icicle == null) {
            showDialog(ALERT_DIALOG_KEY);
        }
        /// @}
    }
    
    TextWatcher mTextWatcher = new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // don't care about this one
        }
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // check if playlist with current name exists already, and warn the user if so.
            setSaveButton();
        };
        public void afterTextChanged(Editable s) {
            // don't care about this one
        }
    };

    private void setSaveButton() {
        String typedname = mPlaylist.getText().toString().trim();
        MusicLogUtils.d(TAG, "setSaveButton " + mSaveButton);
        /// M: check and retrieve save button @{
        if (mSaveButton == null) {
            if (mDialog == null) {
                return;
            } else {
                mSaveButton = (Button) mDialog.getPositiveButton();
            }
        }
        /// @}
        /// M: check save button again
        if (mSaveButton != null) {
            if (typedname.length() == 0) {
                mSaveButton.setEnabled(false);
            } else {
                mSaveButton.setEnabled(true);
                /// M: record the exist id  @{
                final long id = MusicUtils.idForplaylist(getApplicationContext(), typedname);
                MusicLogUtils.d(TAG, "id " + id + ",mOriginalName:" + mOriginalName + ",typedname:" + typedname);
                if (id >= 0 && !mOriginalName.equals(typedname)
                        && !mOriginalName.equalsIgnoreCase(typedname)) {
                    mSaveButton.setText(R.string.create_playlist_overwrite_text);
                    mExistingId = id;
                } else {
                    mSaveButton.setText(R.string.create_playlist_create_text);
                    mExistingId = -1;
                }
                /// @}
            }
        }
    }

    private String nameForId(long id) {
        Cursor c = MusicUtils.query(this, MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                new String[] { MediaStore.Audio.Playlists.NAME },
                MediaStore.Audio.Playlists._ID + "=?",
                new String[] { Long.valueOf(id).toString() },
                MediaStore.Audio.Playlists.NAME);
        String name = null;
        if (c != null) {
            c.moveToFirst();
            if (!c.isAfterLast()) {
                name = c.getString(0);
            }
            /// M: close cursor if the cursor is not null.
            c.close();
        }
        return name;
    }
    
    private String dataForId(long id) {
        Cursor c = MusicUtils.query(this, MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                new String[] { MediaStore.Audio.Playlists.DATA },
                MediaStore.Audio.Playlists._ID + "=?",
                new String[] { Long.valueOf(id).toString() },
                MediaStore.Audio.Playlists.DATA);
        String data = null;
        if (c != null) {
            c.moveToFirst();
            if (!c.isAfterLast()) {
                data = c.getString(0);
            }
            /// M: close cursor if the cursor is not null.
            c.close();
        }
        return data;
    }
    
    
    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        outcicle.putString("defaultname", mPlaylist.getText().toString());
        outcicle.putLong("rename", mRenameId);
        /// M: store the exist id
        outcicle.putLong("existing", mExistingId);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        /// M: Update save button.
        setSaveButton();
    }

    /**
     * M: This listener save the rename playlist when user click positive button
     * otherwise finish current acitivity.
     */
    final private DialogInterface.OnClickListener  mButtonClicked = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface mDialogInterface, int button) {
            if (button == DialogInterface.BUTTON_POSITIVE) {
                String name = mPlaylist.getText().toString();
                if (name != null && name.length() > 0) {
                    name = name.trim();
                    ContentResolver resolver = getContentResolver();
                    if (mExistingId >= 0) {
                        /// M: There is another playlist which has the same name with renamed one
                        // we should overwrite existing one, i.e. delete it from database
                        resolver.delete(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                                MediaStore.Audio.Playlists._ID + "=?",
                                new String[] {Long.valueOf(mExistingId).toString()});
                        MusicLogUtils.d(TAG, "to overwrite, delete the existing one");
                    }
                    String oldData = dataForId(mRenameId);
                    if (oldData == null) {
                        MusicLogUtils.d(TAG, "oldData is null");
                        return;
                    }
                    String newData = oldData.replace(
                            oldData.substring(oldData.lastIndexOf("/") + 1), name);
                    MusicLogUtils.d(TAG, "oldData:" + oldData + ",mRenameId:" + mRenameId
                            + ",name:" + name + ",newData:" + newData);
                    ContentValues values = new ContentValues(1);
                    values.put(MediaStore.Audio.Playlists.NAME, name);
                    values.put(MediaStore.Audio.Playlists.DATA, newData);
                    resolver.update(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                            values,
                            MediaStore.Audio.Playlists._ID + "=?",
                            new String[] { Long.valueOf(mRenameId).toString()});
                    setResult(RESULT_OK);
                    Toast.makeText(RenamePlaylist.this, R.string.playlist_renamed_message, Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else if (button == DialogInterface.BUTTON_NEUTRAL) {
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    };

    /**
     * M: Unregister a receiver, but eat the exception that is thrown if the
     * receiver was never registered to begin with.
     */
    @Override
    public void onDestroy() {
        unregisterReceiver(mScanListener);
        super.onDestroy();
    }

    /**
     * M: to create alert dialogs.
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        MusicLogUtils.d(TAG, "onCreateDialog id=" + id);
        if (id == ALERT_DIALOG_KEY) {
            mDialog = new MusicDialog(this, mButtonClicked, mView);
            mDialog.setTitle(mPrompt);
            mDialog.setPositiveButton(getResources().getString(R.string.delete_confirm_button_text));
            mDialog.setNeutralButton(getResources().getString(R.string.cancel));
            mDialog.setCanceledOnTouchOutside(true);
            mDialog.setCancelable(true);
            mDialog.setSearchKeyListener();
            return mDialog;
        }
        return null;
    }

    /**
     * M: Finish rename playlist activity when sdcard has been unmounted.
     */
    final private BroadcastReceiver mScanListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            /// M: When SD card is unmounted, finish the rename playlist activity
            finish();
            MusicLogUtils.d(TAG, "SD card is ejected, finish RenamePlaylist activity!");
        }
    };
}
