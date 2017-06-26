package com.mediatek.phone.ext;

import android.content.Context;
import android.os.AsyncResult;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

import com.android.internal.telephony.CommandException;

import android.util.Log;

public class DefaultCallFeaturesSettingExt implements ICallFeaturesSettingExt {

    @Override
    public void initOtherCallFeaturesSetting(PreferenceActivity activity) {
    }

    @Override
    public void initOtherCallFeaturesSetting(PreferenceFragment fragment) {
    }

    @Override
    public void initCdmaCallForwardOptionsActivity(PreferenceActivity activity) {
    }

    @Override
    public void initCdmaCallForwardOptionsActivity(PreferenceActivity activity, int subId) {
    }

    @Override
    public void resetImsPdnOverSSComplete(Context context, int msg) {
        Log.d("DefaultCallFeaturesSettingExt","resetImsPdnOverSSComplete");
    } 

    /**
     * For WWWOP, Whether need to show open mobile data dialog or not.
     *
     * @return true if need to show it.
     */
    @Override
    public boolean needShowOpenMobileDataDialog(Context context, int subId) {
        return true;
    }

    /**
     * handle preference status when error happens
     * @param preference
     */
    @Override
    public void onError(Preference preference) {
        Log.d("DefaultCallFeaturesSettingExt", "default onError");
    }

    /**
     * handle error dialog for different errors from framework
     * @param context
     * @param ar
     * @param preference
     */
    @Override 
	public boolean handleErrorDialog(Context context, AsyncResult ar, Preference preference){
		Log.d("DefaultCallFeaturesSettingExt", "default handleErrorDialog");
        return false;
    }
}
