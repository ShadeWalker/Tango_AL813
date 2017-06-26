package com.android.settings;

import java.util.ArrayList;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.provider.SettingsEx;
import android.util.Log;

import com.android.settings.location.ImgRadioButtonPreference;
import com.android.settings.location.RadioButtonPreference;

public class LauncherModeSetting extends SettingsPreferenceFragment implements RadioButtonPreference.OnClickListener{
	private static final String TAG = "LauncherModeSetting";
	private static final String NOMALUI_MODE = "nomalUiMode";
	private static final String SIMPLEUI_MODE = "simpleUiMode";
	private static RadioButtonPreference mNomalUiMode;
	private static RadioButtonPreference mSimpleUiMode;
	private Context context;
	private PackageManager mPm;
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		context = this.getActivity();
		createPreferenceHierarchy();
	}
	 private PreferenceScreen createPreferenceHierarchy() {
	        PreferenceScreen root = getPreferenceScreen();
	        if (root != null) {
	            root.removeAll();
	        }
	        addPreferencesFromResource(R.xml.launcher_mode_setting);
	        root = getPreferenceScreen();
	      
	        mNomalUiMode = (RadioButtonPreference) root.findPreference(NOMALUI_MODE);
	        mSimpleUiMode = (RadioButtonPreference) root.findPreference(SIMPLEUI_MODE);
	       
	        mNomalUiMode.setOnClickListener(this);
	        mSimpleUiMode.setOnClickListener(this);
	        refreshLocationMode();
	        return root;
	    }
	 
	 public void refreshLocationMode() {
	        
        boolean mCurrentMode = isSimpleModeOn();
         
         onModeChanged(mCurrentMode);
 }
	 public void onModeChanged(boolean mode) {
	        if(mode){
	                updateRadioButtons(mSimpleUiMode);
	        }else{
	        	updateRadioButtons(mNomalUiMode);
	        }
       
	    }
	 private void updateRadioButtons(RadioButtonPreference activated) {
	        if (activated == null) {
	        	mNomalUiMode.setChecked(true);
	        	mSimpleUiMode.setChecked(false);
	        } else if (activated == mNomalUiMode) {
	        	mNomalUiMode.setChecked(true);
	        	mSimpleUiMode.setChecked(false);
	        } else if (activated == mSimpleUiMode) {
	        	mNomalUiMode.setChecked(false);
	        	mSimpleUiMode.setChecked(true);
	        } 
	    }
	@Override
	public void onRadioButtonClicked(RadioButtonPreference emiter) {
		boolean uiMode = false;
        if (emiter == mNomalUiMode) {
        	uiMode = false;
        } else if (emiter == mSimpleUiMode) {
        	uiMode = true;
        } 
        
		if(uiMode != isSimpleModeOn()){
			boolean isSimpleModeOn = uiMode;
			HomeStyleSettingUtil.changeUIMode(context, isSimpleModeOn, true);
			HomeStyleSettingUtil.changeFontSize(context, isSimpleModeOn);
		}
	}
	 public boolean isSimpleModeOn(){ 
		 if (null == mPm) {
				mPm = context.getPackageManager();
			}
		 ArrayList<ResolveInfo> homeActivities = new ArrayList<ResolveInfo>(); 
		 ComponentName currentDefaultHome = mPm.getHomeActivities(homeActivities); 
		 final ResolveInfo candidate = homeActivities.get(0); 
		 final ActivityInfo info = candidate.activityInfo; 
		 ComponentName activityName = new ComponentName(info.packageName, info.name); 
		 int flag = activityName.compareTo(HomeStyleSettingUtil.mSimpleui); 
		 if(flag == 0){ 
		 Log.v(TAG, "activityName == HomeStyleSettingUtil.mSimpleui==" + true); 
		 return true; 
		 } 
		 return false; 
		 } 
}







