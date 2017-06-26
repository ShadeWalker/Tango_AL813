package com.android.settings;

import android.app.Activity;
import android.util.Log;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RadioGroup.OnCheckedChangeListener;
import java.util.ArrayList;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.view.View;

public class LaunchModeSettingActivity extends Activity {

    private final static String TAG = "LaunchModeSettingActivity";
    private static final String NOMALUI_MODE = "nomalUiMode";
    private static final String SIMPLEUI_MODE = "simpleUiMode";
	private RadioGroup mGroup;
	private PackageManager mPm;
	RadioButton mSimpleModeButton;
	RadioButton mNormalModeButton;
	TextView mStyleDescription;
	TextView mNormalCurrentView; // Normal current view text
	TextView mSimpleCurrentView;//Simple current view text
	boolean mIsCurModeSimple = false; // judge if current mode is simple
	//wuhuihui add
    private static final int MENU_APPLY  = Menu.FIRST + 1;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.launch_mode_setting_activity);
		mSimpleModeButton = (RadioButton)findViewById(R.id.checkbox_simple);
		mNormalModeButton = (RadioButton)findViewById(R.id.checkbox_normal);
		mStyleDescription = (TextView)findViewById(R.id.style_description);

		mNormalCurrentView = (TextView)findViewById(R.id.normal_home_current);
		mSimpleCurrentView = (TextView)findViewById(R.id.simple_home_current);

		mGroup = (RadioGroup) findViewById(R.id.radioGroup1);
		mGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (R.id.checkbox_normal == checkedId) {
                    mIsCurModeSimple = false;
					mStyleDescription.setText(R.string.home_style_normal_description);
                } else if (R.id.checkbox_simple == checkedId) {
                    mIsCurModeSimple = true;
					mStyleDescription.setText(R.string.home_style_simple_description);
                }

            } });
			updateHomeScreenMode();

    }

    public void updateHomeScreenMode() {
	        
        boolean isSimpleMode = isSimpleModeOn();
		if (isSimpleMode) {
			mSimpleModeButton.setChecked(true);
			mNormalModeButton.setChecked(false);
			mIsCurModeSimple = true;
			mStyleDescription.setText(R.string.home_style_simple_description);
            //hide simple current used textview
            mSimpleCurrentView.setVisibility(View.VISIBLE);
            mNormalCurrentView.setVisibility(View.GONE);
		}else {
            mSimpleModeButton.setChecked(false);
            mNormalModeButton.setChecked(true);
			mIsCurModeSimple = false;
			mStyleDescription.setText(R.string.home_style_normal_description);
            //hide simple current used textview
            mSimpleCurrentView.setVisibility(View.GONE);
            mNormalCurrentView.setVisibility(View.VISIBLE);
		}
    }

    public boolean isSimpleModeOn(){ 
        if (null == mPm) {
            mPm = getPackageManager();
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

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_APPLY, 0, R.string.menu_apply)
                .setIcon(R.drawable.ic_menu_apply)
                .setEnabled(true)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM); 
	    return true;
	}

    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
        int menuId = item.getItemId();
		if (menuId == MENU_APPLY) {
            if(mIsCurModeSimple != isSimpleModeOn()){
                HomeStyleSettingUtil.changeUIMode(this, mIsCurModeSimple, true);
                HomeStyleSettingUtil.changeFontSize(this, mIsCurModeSimple);
            }
		}
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

}
