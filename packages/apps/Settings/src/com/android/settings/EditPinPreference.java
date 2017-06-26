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

package com.android.settings;

import android.app.Dialog;
import android.content.Context;
import android.preference.EditTextPreference;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.text.Selection;
import android.text.TextWatcher;
import android.text.Editable;
import android.text.InputFilter;
import android.app.AlertDialog;


/**
 * TODO: Add a soft dialpad for PIN entry.
 */
class EditPinPreference extends EditTextPreference {

    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;

    interface OnPinEnteredListener {
        void onPinEntered(EditPinPreference preference, boolean positiveResult);
    }
    
    private OnPinEnteredListener mPinListener;
    
    public EditPinPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EditPinPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    public void setOnPinEnteredListener(OnPinEnteredListener listener) {
        mPinListener = listener;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        final EditText editText = getEditText();

        if (editText != null) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            editText.setFilters(new InputFilter[]{
                    new InputFilter.LengthFilter(MAX_PIN_LENGTH)
            });
            editText.addTextChangedListener(new MaxLengthWatcher(MAX_PIN_LENGTH, editText));
            AlertDialog alert_dialog = (AlertDialog)getDialog();
            if (alert_dialog != null) {
                alert_dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            }
        }
    }

    public boolean isDialogOpen() {
        Dialog dialog = getDialog();
        return dialog != null && dialog.isShowing();
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (mPinListener != null) {
            mPinListener.onPinEntered(this, positiveResult);
        }
    }

    public void showPinDialog() {
        Dialog dialog = getDialog();
        if (dialog == null || !dialog.isShowing()) {
            showDialog(null);
        }
    }

   class MaxLengthWatcher implements TextWatcher {

    private int maxLen = 0;
    private EditText editText = null;


    public MaxLengthWatcher(int maxLen, EditText editText) {
             this.maxLen = maxLen;
             this.editText = editText;
        }
    
        public void afterTextChanged(Editable arg0) {
          // TODO Auto-generated method stub
    
        }
    
        public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
         // TODO Auto-generated method stub
        }
    
        public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
             // TODO Auto-generated method stub
             Editable editable = editText.getText();
             int len = editable.length();
            
             if(len > maxLen)
             {
                int selEndIndex = Selection.getSelectionEnd(editable);
                String str = editable.toString();
                //截取新字符串
                String newStr = str.substring(0,maxLen);
                editText.setText(newStr);
                editable = editText.getText();

                //新字符串的长度
                int newLen = editable.length();
                //旧光标位置超过字符串长度
                if(selEndIndex > newLen)
                {
                    selEndIndex = editable.length();
                }
                //设置新光标所在的位置
                Selection.setSelection(editable, selEndIndex);
            
             }
            AlertDialog alert_dialog = (AlertDialog)getDialog();
            if (alert_dialog != null) {
                 if (len<MIN_PIN_LENGTH) {
                    alert_dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); 
                 }
                 else {
                    alert_dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);  
                 }
            }

        }

}

}
