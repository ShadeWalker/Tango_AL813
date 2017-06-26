
package com.mediatek.apn;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ListView;

import com.android.settings.R;
import com.mediatek.settings.FeatureOption;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.ext.IApnSettingsExt;


public class ApnTypePreference extends DialogPreference implements
        DialogInterface.OnMultiChoiceClickListener {

    private int mApnTypeNum;
    private static final String TAG = "ApnTypePreference";
    private boolean[] mCheckState;
    private boolean[] mUiCheckState;
    private String[] mApnTypeArray;

    private String mTypeString;

    private ListView mListView;

    IApnSettingsExt mExt;

    public ApnTypePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mExt = UtilsExt.getApnSettingsPlugin(context);

        String apnType = null;
        if (context instanceof Activity) {
            Intent intent = ((Activity) context).getIntent();
            apnType = intent.getStringExtra(ApnUtils.APN_TYPE);
        }

        boolean isTethering = ApnUtils.TETHER_TYPE.equals(apnType);

        String[] tempArray = mExt.getApnTypeArray(context,
                R.array.apn_type_generic, isTethering);

        Log.d(TAG, "isTethering " + isTethering
                + " FeatureOption.MTK_VOLTE_SUPPORT" + FeatureOption.MTK_VOLTE_SUPPORT);
        if (FeatureOption.MTK_VOLTE_SUPPORT && !isTethering) {
            mApnTypeArray = new String[tempArray.length + 1];
            for (int i = 0; i < tempArray.length; i++) {
                mApnTypeArray[i] = tempArray[i];
            }
            mApnTypeArray[mApnTypeArray.length - 1] = "ims";
        } else {
            mApnTypeArray = tempArray;
        }

        if (mApnTypeArray != null) {
            mApnTypeNum = mApnTypeArray.length;
        }
        mCheckState = new boolean[mApnTypeNum];
    }

    public ApnTypePreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);

        builder.setMultiChoiceItems(mApnTypeArray, mCheckState, this);

        mListView = builder.create().getListView();

        mUiCheckState = new boolean[mApnTypeNum];
        // Set UI CheckBox Status:
        for (int i = 0; i < mApnTypeNum; i++) {
            mUiCheckState[i] = mCheckState[i];
            Log.i(TAG, "onPrepareDialogBuilder mUiCheckState[" + i + "]="
                    + mUiCheckState[i]);
        }
    }

    private void updateUiCheckBoxStatus() {
        for (int i = 0; i < mApnTypeNum; i++) {
            mCheckState[i] = mUiCheckState[i];
            Log.i(TAG, "updateUiCheckBoxStatus mCheckState[" + i + "]="
                    + mCheckState[i]);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            updateUiCheckBoxStatus();
            updateRecord();
            callChangeListener(mTypeString);
        } else {
            intCheckState(mTypeString);
        }
    }

    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
        mCheckState[which] = isChecked;

        // Set UI CheckBox Status:
        mUiCheckState[which] = isChecked;
    }

    private void updateRecord() {

        if (mListView != null) {

            StringBuilder strTemp = new StringBuilder("");

            for (int i = 0; i < mApnTypeNum; i++) {

                if (mCheckState[i]) {
                    strTemp.append(mApnTypeArray[i]).append(',');
                }
            }

            int length = strTemp.length();
            if (length > 1) {
                mTypeString = strTemp.substring(0, length - 1);
            } else {
                mTypeString = "";
            }
            Log.i(TAG, "mTypeString is " + mTypeString);

        }

    }

    public void intCheckState(String strType) {

        Log.d(TAG, "init CheckState: " + strType);
        if (strType == null) {
            return;
        }

        String[] tempstrType = strType.split(",");
        mTypeString = strType;

        for (int i = 0; i < mApnTypeNum; i++) {
            //mCheckState[i] = strType.contains(mApnTypeArray[i]);
             for(int j=0;j < tempstrType.length;j++){
                  mCheckState[i] = tempstrType[j].equals(mApnTypeArray[i]);
 				  if(mCheckState[i] == true)
					break;
           }
        }
    }

    public String getTypeString() {
        return mTypeString;
    }
}
