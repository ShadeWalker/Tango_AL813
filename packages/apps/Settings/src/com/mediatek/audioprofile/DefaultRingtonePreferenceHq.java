package com.mediatek.audioprofile;

import com.mediatek.settings.FeatureOption;
import com.mediatek.settings.UtilsExt;
import com.mediatek.xlog.Xlog;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.RingtonePreference;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;

//import com.mediatek.settings.ext.IAudioProfileExt;
public class DefaultRingtonePreferenceHq extends RingtonePreference {

//    private AlertDialog mAlertDialog;
//    private Context mContext;
//    private Fragment mParentFragment;

    public DefaultRingtonePreferenceHq(Context ctx,
            AttributeSet attributeSet) {
        super(ctx, attributeSet);
//        mContext = ctx;
//        mExt = UtilsExt.getAudioProfilePlgin(ctx);
    }

//    public void changeRingtone(Uri uri) {
//        onSaveRingtone(uri);
//    }

    protected void onClick() {
        /*Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
//        Intent intent = new Intent("android.intent.action.RINGTONE_PICKER");
//        intent.addCategory("android.intent.category.HWRING");
        onPrepareRingtonePickerIntent(intent);
        try {
            if (mParentFragment != null){
            	android.util.Log.d("YKB", "HwRingtonePreferenceBase.java intent="+intent);
//                mParentFragment.startActivityForResult(intent, 20);
            }
            mParentFragment.startActivityForResult(intent, mRequestCode);
        } catch (Exception e) {
            e.printStackTrace();
//            showAlertDialog();
        }*/
    	super.onClick();
    }
//    private IAudioProfileExt mExt;
    protected void onPrepareRingtonePickerIntent(Intent intent) {
        super.onPrepareRingtonePickerIntent(intent);
        intent.putExtra(
                RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);

        /*if (mStreamType.equals(RING_TYPE)) {
        	intent.putExtra(
                    RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        }*/

//        mExt.setRingtonePickerParams(intent);
        /*intent.putExtra("android.intent.extra.ringtone.SHOW_DEFAULT", true);
        if (getRingtoneType() == 2) {
            String str = Settings.System.getString(getContext()
                    .getContentResolver(), "theme_notification_sound_path");
            if (str != null)
                intent.putExtra(
                        "android.intent.extra.ringtone.DEFAULT_STRING", str);
        }*/
    }

    /*protected void onRestoreInstanceState(Parcelable parcelable) {
        if ((parcelable == null)
                || (!parcelable.getClass().equals(SavedState.class)))
            super.onRestoreInstanceState(parcelable);
        SavedState localSavedState;
        do {
            localSavedState = (SavedState) parcelable;
            super.onRestoreInstanceState(localSavedState.getSuperState());
        } while (!localSavedState.isDialogShowing);
//        showAlertDialog();
    }

    protected Parcelable onSaveInstanceState() {
        Parcelable parcelable = super.onSaveInstanceState();
        if ((mAlertDialog == null) || (!mAlertDialog.isShowing()))
            return parcelable;
        SavedState savedState = new SavedState(parcelable);
        savedState.isDialogShowing = true;
        return savedState;
    }*/

//    public void setFragment(Fragment fragment) {
//        mParentFragment = fragment;
//    }

    /*private static class SavedState extends Preference.BaseSavedState {
        public static final Parcelable.Creator<SavedState> CREATOR =
        		new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(
                    Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

        boolean isDialogShowing;

        public SavedState(Parcel source) {
            super(source);
            isDialogShowing = source.readInt() == 1;
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public void writeToParcel(Parcel parcel, int value) {
            super.writeToParcel(parcel, value);
            parcel.writeInt(isDialogShowing ? 1 : 0);
        }
    }*/
    
    @Override
    protected Uri onRestoreRingtone() {
        return RingtoneManager.getActualDefaultRingtoneUri(getContext(),
                getRingtoneType());
    }

    @Override
    protected void onSaveRingtone(Uri uri) {
        RingtoneManager.setActualDefaultRingtoneUri(getContext(),
                getRingtoneType(), uri);
    }
}
