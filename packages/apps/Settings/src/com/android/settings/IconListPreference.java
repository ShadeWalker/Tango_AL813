package com.android.settings;

import java.util.List;

import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.preference.DialogPreference;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.provider.Settings;
import com.android.settings.R;

public class IconListPreference extends DialogPreference{
	
	private List<ResolveInfo> mList;
	private int mResource = R.layout.icon_list_item;
	protected LayoutInflater mInflater;
	private PackageManager mPm;
	private int mClickedDialogEntryIndex;
	private String mPackageNameAndActivity;
	private String mDefaultPackageNameAndActivity = "com.huawei.android.launcher;com.huawei.android.launcher.Launcher";
	
    private static final String LISTPREF_C_KEY = "gesture_type_c";
    private static final String LISTPREF_W_KEY = "gesture_type_w";
    private static final String LISTPREF_E_KEY = "gesture_type_e";
    private static final String LISTPREF_M_KEY = "gesture_type_m";
    //HQ_wuhuihui modified for key lock gesture optmise start
    private static final String GESTURE_DEF_C = "com.huawei.camera;com.huawei.camera";
    private static final String GESTURE_DEF_E = "com.android.chrome;com.google.android.apps.chrome.Main";//HQ_hushunli 2016-08-16 modify for HQ02050710
    private static final String GESTURE_DEF_W = "com.huawei.android.totemweather;com.huawei.android.totemweather.WeatherHome";
    private static final String GESTURE_DEF_M = "com.android.mediacenter;com.android.mediacenter.PageActivity";
    
	private ListAdapter mListAdapter;
	private Context mContext;

	public IconListPreference(Context context, AttributeSet attrs) {
		super(context, attrs,R.attr.gesturePreferenceStyle);
		mContext = context;
		mPm = context.getPackageManager();
		Intent mainIntent = new Intent(Intent.ACTION_MAIN);
		mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		mList = loadTopResolveInfo();
		List<ResolveInfo> mTempList = mPm.queryIntentActivities(mainIntent, 0);
		mList.addAll(removeResolveInfo(mTempList));
		mList.addAll(loadLauncherResolveInfo());
		
		mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mListAdapter = new ArrayAdapter<ResolveInfo>(this.getContext(), mResource, mList) 
		{

            @Override
            public View getView(int position, View convertView, ViewGroup parent) 
            {
                TextView text;
                ImageView image;

                View view;
                if (convertView == null) 
				{
                    view = mInflater.inflate(mResource, parent, false);
                }
				else 
				{
                    view = convertView;
                }

                // Set text field
                text = (TextView) view.findViewById(R.id.appname);
                // Set resource icon
                image = (ImageView) view.findViewById(R.id.packageicon);

                if(mList.get(position).activityInfo.applicationInfo.packageName.equals("com.android.settings") && mList.get(position).activityInfo.name.equals("com.android.settings.Settings$GestureSettingsActivity")) 
				{
                	image.setImageDrawable(null);
                    text.setText(mContext.getResources().getString(R.string.empty_app));
                } 
				else 
                {
                	image.setImageDrawable(getItem(position).loadIcon(mPm));
                    text.setText(getItem(position).loadLabel(mPm));
                }
                return view;
            }
        };
		init();
		
	}
	
	private List<ResolveInfo> loadLauncherResolveInfo() {
		String[] pkgName = {"com.huawei.android.launcher"};
		String[] clsName = {"com.huawei.android.launcher.Launcher"};
		List<ResolveInfo> mLauncherList;
		Intent mainIntent = new Intent();
		for (int i = 0; i < pkgName.length; i++) {
			mainIntent.setComponent(new ComponentName(pkgName[i], clsName[i]));
			mLauncherList = mPm.queryIntentActivities(mainIntent, 0);
			return mLauncherList;
		}
		return null;
	}
	
	private List<ResolveInfo> loadTopResolveInfo() 
	{
		String[] pkgName = {"com.android.settings"};
		String[] clsName = {"com.android.settings.Settings$GestureSettingsActivity"};
		List<ResolveInfo> mTopList;
		Intent mainIntent = new Intent();
		for (int i = 0; i < pkgName.length; i++) 
		{
			mainIntent.setComponent(new ComponentName(pkgName[i], clsName[i]));
			mTopList = mPm.queryIntentActivities(mainIntent, 0);
			return mTopList;
		}
		return null;
	}
	
	private List<ResolveInfo> removeResolveInfo(List<ResolveInfo> list) {
		List<ResolveInfo> oriResolveInfo = list;
		String clsName = "com.android.settings.Settings$GestureSettingsActivity";
		final int M = list.size();
		for(int i = 0; i < M; i++) 
		{
    		ResolveInfo info = list.get(i);
    		if(info.activityInfo.name.equals(clsName)) 
			{
    			oriResolveInfo.remove(i);
    			break;
    		}
    	}
		return oriResolveInfo;
    }

   private boolean isActivityExist(String component){
   	   Log.d("hhq","[isActivityExist] component = " + component);
       String[] componentName = component.split(";");
	   if(componentName.length < 2){
		   return false; 
	   }
       Intent mainIntent = new Intent();
       mainIntent.setComponent(new ComponentName(componentName[0],componentName[1]));
       List<ResolveInfo> queryActivityList;
       queryActivityList = mPm.queryIntentActivities(mainIntent, 0);
       if (queryActivityList == null || queryActivityList.size() == 0) {
           return false; 
       } else {
           return true;
       }	   
   }
    private void init()
	{
    	if(getKey().equals(LISTPREF_C_KEY)) 
	{
                //mPackageNameAndActivity = "com.huawei.camera;com.huawei.camera";
    		mPackageNameAndActivity = android.provider.Settings.System.getString(this.getContext().getContentResolver(), Settings.System.KEYLOCK_GESTURES_C_PKG);
               if(mPackageNameAndActivity == null || mPackageNameAndActivity.length()==0
                                                  || !isActivityExist(mPackageNameAndActivity)){
                   mPackageNameAndActivity = GESTURE_DEF_C;
               }
    	} 
        else if(getKey().equals(LISTPREF_E_KEY)) 
        {
            //mPackageNameAndActivity = "com.android.email;com.android.email2.ui.MailActivityEmail";
            mPackageNameAndActivity = android.provider.Settings.System.getString(this.getContext().getContentResolver(), Settings.System.KEYLOCK_GESTURES_E_PKG);
            if(mPackageNameAndActivity == null || mPackageNameAndActivity.length()==0
                                               || !isActivityExist(mPackageNameAndActivity)){
                mPackageNameAndActivity = GESTURE_DEF_E;
            }
    	} 
        else if(getKey().equals(LISTPREF_M_KEY)) 
        {
            //mPackageNameAndActivity = "com.baidu.BaiduMap;com.baidu.baidumaps.WelcomeScreen";
            mPackageNameAndActivity = android.provider.Settings.System.getString(this.getContext().getContentResolver(), Settings.System.KEYLOCK_GESTURES_M_PKG);
            if(mPackageNameAndActivity == null || mPackageNameAndActivity.length()==0
                                               || !isActivityExist(mPackageNameAndActivity)){
                mPackageNameAndActivity = GESTURE_DEF_M;
            }
    	} 
        else if(getKey().equals(LISTPREF_W_KEY)) 
        {
             //mPackageNameAndActivity = "com.android.browser;com.android.browser.BrowserActivity";
             mPackageNameAndActivity = android.provider.Settings.System.getString(this.getContext().getContentResolver(), Settings.System.KEYLOCK_GESTURES_W_PKG);
             if(mPackageNameAndActivity == null || mPackageNameAndActivity.length()==0
                                                || !isActivityExist(mPackageNameAndActivity)){
                 mPackageNameAndActivity = GESTURE_DEF_W;
             }
    	}//HQ_wuhuihui modified for key lock gesture optmise end
    	Log.d("majian", "mPackageNameAndActivity" + mPackageNameAndActivity);
		if(mPackageNameAndActivity ==  null)
		{
	    	mPackageNameAndActivity = mDefaultPackageNameAndActivity;
		}

        String[] names = mPackageNameAndActivity.split(";");
        if(names.length == 2)
		{
        	try
			{
             	mPm.getLaunchIntentForPackage(names[0]);
        	}
			catch (Exception e)
			{
             	mPackageNameAndActivity = mDefaultPackageNameAndActivity;
        	}
       
        	int size = 0;
        	ResolveInfo rInfo = null;
			for (int i = 0; i < mList.size(); i++) 
			{
				ResolveInfo tmp = mList.get(i);

				if ((!names[1].equals("null")
					 && (names[1].equals(tmp.activityInfo.targetActivity)||names[1].equals(tmp.activityInfo.name)) 
					 && tmp.activityInfo.applicationInfo.packageName.equals(names[0]))) 
				{
					rInfo = tmp;
					mClickedDialogEntryIndex = i;
					break;
				}
			}
			
        	if(rInfo != null)
			{
				if (rInfo.activityInfo.applicationInfo.packageName.equals("com.android.settings")
					&& rInfo.activityInfo.name.equals("com.android.settings.Settings$GestureSettingsActivity")) 
				{
					setSummary(mContext.getResources().getString(R.string.empty_app));
				} 
				else 
				{
					if(getKey().equals(LISTPREF_C_KEY)) 
						setSummary(rInfo.loadLabel(mPm));
					else if(getKey().equals(LISTPREF_E_KEY))						
						setSummary(rInfo.loadLabel(mPm));
					else if(getKey().equals(LISTPREF_M_KEY))
						setSummary(rInfo.loadLabel(mPm));
					else if(getKey().equals(LISTPREF_W_KEY)) 
						setSummary(rInfo.loadLabel(mPm));
				}
        	} 
			else 
			{
        		setSummary(mContext.getResources().getString(R.string.empty_app));
        	}
        }	
    }
	
    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);

        mClickedDialogEntryIndex = getValueIndex();
        builder.setSingleChoiceItems(mListAdapter, mClickedDialogEntryIndex, 
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mClickedDialogEntryIndex = which;

                        /*
                         * Clicking on an item simulates the positive button
                         * click, and dismisses the dialog.
                         */
                        IconListPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                        dialog.dismiss();
                    }
        });
        
        /*
         * The typical interaction for list-based dialogs is to have
         * click-on-an-item dismiss the dialog instead of the user having to
         * press 'Ok'.
         */
        builder.setPositiveButton(null, null);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        
        if (positiveResult && mClickedDialogEntryIndex >= 0) {
	    ResolveInfo rInfo = mList.get(mClickedDialogEntryIndex);

	    String className = (rInfo.activityInfo.name == null) ? "null":rInfo.activityInfo.name;
            String value = rInfo.activityInfo.applicationInfo.packageName+";"+className;
            Log.d("majian", "mPackageNameAndActivity" + value);
            if (callChangeListener(value)) {
				if (rInfo.activityInfo.applicationInfo.packageName
						.equals("com.android.settings")
						&& rInfo.activityInfo.name
								.equals("com.android.settings.Settings$GestureSettingsActivity")) {
					setSummary(mContext.getResources().getString(R.string.empty_app));
				} else {
					if(getKey().equals(LISTPREF_C_KEY)) 
						setSummary(rInfo.loadLabel(mPm));
					else if(getKey().equals(LISTPREF_E_KEY))						
						setSummary(rInfo.loadLabel(mPm));
					else if(getKey().equals(LISTPREF_M_KEY))
						setSummary(rInfo.loadLabel(mPm));
					else if(getKey().equals(LISTPREF_W_KEY)) 
						setSummary(rInfo.loadLabel(mPm));
				}
				setValue(value);
            }
        }
    }
    
    private int getValueIndex(){
	String names[] = mPackageNameAndActivity.split(";");
    	int size = mList.size();
    	for(int i = 0; i < size; i++){
		ResolveInfo tmp = mList.get(i);
    		if((!names[1].equals("null") && (names[1].equals(tmp.activityInfo.targetActivity) || names[1].equals(tmp.activityInfo.name)) && tmp.activityInfo.applicationInfo.packageName.equals(names[0])) || tmp.activityInfo.applicationInfo.packageName.equals(names[0]))
    			return i;
    	}
    	return 0;
    }
    
    private void setValue(String value)
	{
    	if(getKey().equals(LISTPREF_C_KEY)) 
		{
       		android.provider.Settings.System.putString(this.getContext().getContentResolver(), Settings.System.KEYLOCK_GESTURES_C_PKG, value);
       	} 
		else if(getKey().equals(LISTPREF_E_KEY)) 
		{
       		android.provider.Settings.System.putString(this.getContext().getContentResolver(), Settings.System.KEYLOCK_GESTURES_E_PKG, value);
       	} 
		else if(getKey().equals(LISTPREF_M_KEY)) 
		{
       		android.provider.Settings.System.putString(this.getContext().getContentResolver(), Settings.System.KEYLOCK_GESTURES_M_PKG, value);
       	} 
		else if(getKey().equals(LISTPREF_W_KEY)) 
		{
       		android.provider.Settings.System.putString(this.getContext().getContentResolver(), Settings.System.KEYLOCK_GESTURES_W_PKG, value);
       	}
    }

}
