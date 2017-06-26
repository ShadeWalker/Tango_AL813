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
import android.content.DialogInterface;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.NumberPicker.OnValueChangeListener;

public class WeekSelector extends Activity {
    /// M: constant and variable @{
    private static final int ALERT_DIALOG_KEY = 0;
    private static final int WEEK_START = 1;
    private static final int WEEK_END = 12;
    private static final int UPDATE_INTERVAL = 200;
    private static final int EDITTEXT_POSITION = 0;
    private int mCurrentSelectedPos;
    private NumberPicker mNumberPicker;
    private View mView;
    /// @}
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        /// M: get view by layoutinflater
        mView = getLayoutInflater().inflate(R.layout.weekpicker, null);
        /// M: get number picker and initial it @{
        mNumberPicker = (NumberPicker) mView.findViewById(R.id.weeks);
        mNumberPicker.setOnValueChangedListener(mChangeListener);
        mNumberPicker.setDisplayedValues(getResources().getStringArray(R.array.weeklist));

        int def = MusicUtils.getIntPref(this, "numweeks", WEEK_START);
        int pos = icicle != null ? icicle.getInt("numweeks", def) : def;
        mCurrentSelectedPos = pos;
        mNumberPicker.setMinValue(WEEK_START);
        mNumberPicker.setMaxValue(WEEK_END);
        mNumberPicker.setValue(pos);
        mNumberPicker.setWrapSelectorWheel(false);
        mNumberPicker.setOnLongPressUpdateInterval(UPDATE_INTERVAL);
        EditText spinnerInput = (EditText) mNumberPicker.getChildAt(EDITTEXT_POSITION);
        if (spinnerInput != null) {
            spinnerInput.setFocusable(false);
        }
        /// @}

        /// M: Only show dialog when activity first onCreate(), because if activity is restarted(Such as change
        /// language cause activity restart), system will restore the dialog. {@
        if (icicle == null) {
            showDialog(ALERT_DIALOG_KEY);
        }
        /// @}
    }
    
    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        outcicle.putInt("numweeks", mCurrentSelectedPos);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    /**
     * M: This listener save the week number to SharedPreferences when user click positive button
     * otherwise finish current acitivity
     */
    final private DialogInterface.OnClickListener  mButtonClicked = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface mDialogInterface, int button) {
            if (button == DialogInterface.BUTTON_POSITIVE) {
                int numweeks = mCurrentSelectedPos;
                MusicUtils.setIntPref(WeekSelector.this, "numweeks", numweeks);
                setResult(RESULT_OK);
            } else if (button == DialogInterface.BUTTON_NEUTRAL) {
                setResult(RESULT_CANCELED);
            }
            finish();
        }
    };

    /**
     * M: to create dialogs for week select
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == ALERT_DIALOG_KEY) {
            MusicDialog dialog = new MusicDialog(this, mButtonClicked, mView);
            if (dialog != null) {
                dialog.setTitle(R.string.weekpicker_title);
                dialog.setPositiveButton(getResources().getString(R.string.weekpicker_set));
                dialog.setNeutralButton(getResources().getString(R.string.cancel));
                dialog.setCanceledOnTouchOutside(true);
                dialog.setCancelable(true);
                dialog.setSearchKeyListener();
                return dialog;
            }
        }
        return null;
    }

    /**
     * M: This listener monitor the value change of NumberPicker
     */
    OnValueChangeListener mChangeListener = new OnValueChangeListener() {
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            if (picker == mNumberPicker) {
                mCurrentSelectedPos = newVal;
            }
        }
    };
}
