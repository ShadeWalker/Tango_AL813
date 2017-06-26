package com.android.settings.applications;

import java.util.List;

import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.preference.DialogPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.ListPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.ListView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.preference.PreferenceActivity;
import android.os.Bundle;
import android.os.Environment;
import android.os.PatternMatcher;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.provider.MediaStore;
import android.app.ListActivity;
import android.app.Activity;
import android.app.FragmentManager;
import android.net.Uri;

import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Collection;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import com.android.settings.location.ImgRadioButtonPreference;
import com.android.settings.R;
import com.android.internal.content.PackageMonitor;
import com.android.settings.Utils;
import com.android.internal.telephony.SmsApplication;

public class PreferredSettingsActivity extends Activity{
    private static final String TAG = "PreferredSettingsActivity";
    private Context mContext;
    private String mKey = null;
    private String mTitle = null;
    private List<ResolveInfo> mList;
    private PackageManager mPm;
    private ListAdapter mListAdapter;
    private LayoutInflater mInflater;
    private int mResource = R.layout.app_list_item;
    private int mClickedDialogEntryIndex;
    private int mDefaultPosition = 0;
    private PreferenceScreen mRoot = null;
    private Intent mIntent = null;

    private static final String LISTPREF_HOME_KEY = "key_preference_home";
    private static final String LISTPREF_CALL_KEY = "key_preference_call";
    private static final String LISTPREF_MESSAGE_KEY = "key_preference_message";
    private static final String LISTPREF_CAMERA_KEY = "key_preference_camera";

    private static final String LISTPREF_GALLERY_KEY = "key_preference_gallery";
    private static final String LISTPREF_MUSIC_KEY = "key_preference_music";
    private static final String LISTPREF_VIDEO_KEY = "key_preference_video";
    private static final String LISTPREF_EMAIL_KEY = "key_preference_email";

    private static final String LISTPREF_BROWSER_KEY = "key_preference_browser";
    private static final String FRAGMENT_TAG = "preferred_setting_fragment";
    private static FragmentManager mFM;
    public static String updatedPreferenceSummary = null;
    private boolean hasOneApp = false;
    //add by wangmingyue for HQ01512387
    private SharedPreferences sharePre ;
    public static final String DEFUALT_BROWSER_SHARED= "defualt_browser";
    public static final String SET_DEFUALT_BROWSER= "set_defualt_browser";

    //mPackageMonitor just deal with uninstall someapks,we should refresh preferences when this activity resumed.
    private boolean mRegistered;

	private String smsLable = null;
	
    private final PackageMonitor mPackageMonitor = new PackageMonitor() {
        @Override public void onSomePackagesChanged() {
            Log.d(TAG, "PackageMonitor onSomePackagesChanged should refresh current list");
            if (mRoot != null) {
                mRoot.removeAll();
                //to rebuild preferences
                if (mFM != null) {
                    SetDefaultAppFragment pf = (SetDefaultAppFragment)mFM.findFragmentByTag(FRAGMENT_TAG);
                    if (pf != null) {
                        //update mList
                        mList = mPm.queryIntentActivities(mIntent, (PackageManager.GET_INTENT_FILTERS
                                         | PackageManager.MATCH_DEFAULT_ONLY
                                         | PackageManager.GET_RESOLVED_FILTER
                                    ));
                        //readd Preference
                        int i = 0;
                        if (mList != null && mList.size() > 0) {
                            if (mList.size() == 1) {
                                mPackageMonitor.unregister();
                                mRegistered = false;
                            }

                            for (ResolveInfo resolveInfo : mList) {
                                String packageName = resolveInfo.activityInfo.applicationInfo.packageName;
                                String className = resolveInfo.activityInfo.name;
                                Drawable icon = resolveInfo.loadIcon(mPm);
                                String label = null;
                                try {
                                    label = getPackageManager().getApplicationLabel(
                                        getPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString();
                                } catch(NameNotFoundException e) {
                                    e.printStackTrace();
                                }
								/*HQ_liugang add for HQ01699074 20160127*/
								if("com.android.contacts".equals(packageName)&& mKey.equals(LISTPREF_MESSAGE_KEY)) {
									label = smsLable;
								}
								/*HQ_liugang add end*/
                                boolean isDefault = resolveInfo.isDefault;
                                IntentFilter intentFilter = resolveInfo.filter;
                                ImgRadioButtonPreference setDefaultPreference = new ImgRadioButtonPreference(pf.getActivity());
                                setDefaultPreference.setKey("set_default_key" + i);
                                setDefaultPreference.setTitle(label);
                                setDefaultPreference.setIcon(icon);
                                setDefaultPreference.setPersistent(false);
                                setDefaultPreference.setOnClickListener(checkBoxClickListener);
                                if (isDefaultApp(packageName)) {
                                    setDefaultPreference.setChecked(true);
                                    mDefaultPosition = i;
                                    updatedPreferenceSummary = mKey + ":" + label;
                                }
                                mRoot.addPreference(setDefaultPreference);
                                i ++;
                            }
                        }
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(Utils.isMonkeyRunning()) {
            Log.d(TAG, "to avoid the fragment crash in monkey testing,we finish the activity.");
            finish();
        }
        super.onCreate(savedInstanceState);
        mPm = getPackageManager();
        mContext = this;
        mIntent = getActualIntent();
        this.setIntent(mIntent);
        //mTitle init in getActualIntent()
        //init the summary in parent activity.
        updatedPreferenceSummary = getResources().getString(R.string.app_default_summary);
        Log.d(TAG, "mTitle: " + mTitle + " intent: " + mIntent + " mPm: " + mPm);
        if (mTitle != null && !mTitle.isEmpty()) {
            this.setTitle(mTitle);
        }
        if (mIntent != null && (!mKey.equals(LISTPREF_MESSAGE_KEY))) {
            mList = mPm.queryIntentActivities(mIntent, (PackageManager.GET_INTENT_FILTERS
                                                     | PackageManager.MATCH_DEFAULT_ONLY
                                                     | PackageManager.GET_RESOLVED_FILTER
                                                ));
        } else if (mIntent != null && mKey.equals(LISTPREF_MESSAGE_KEY)) {
            mList = SmsApplication.getSmsApplicationCollection(mContext);
        }
        mInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new SetDefaultAppFragment(), FRAGMENT_TAG).commit();
        mFM = getFragmentManager();
        mPackageMonitor.register(this, getMainLooper(), false);
        mRegistered = true;
        sharePre = mContext.getSharedPreferences(DEFUALT_BROWSER_SHARED, Activity.MODE_PRIVATE); //add by wangmingyue for HQ01512387
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (!mRegistered) {
            mPackageMonitor.register(this, getMainLooper(), false);
            mRegistered = true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mRegistered) {
            mPackageMonitor.unregister();
            mRegistered = false;
        }
    }

    @Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		if (mRegistered) {
		    mPackageMonitor.unregister();
		    mRegistered = false;
		}
		super.onDestroy();
	}

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    int i = 0;
    private boolean isDefaultApp(String packageName) {
        boolean isDefault = false;
        // Get list of preferred activities
        List<ComponentName> prefActList = new ArrayList<ComponentName>();

        // Intent list cannot be null. so pass empty list
        List<IntentFilter> intentList = new ArrayList<IntentFilter>();
        mPm.getPreferredActivities(intentList, prefActList, packageName);
        Log.d(TAG, "prefActList.size: " + prefActList.size() + " packageName: " + packageName + " intentList.size: " + intentList.size());
        isDefault = prefActList.size() > 0;
        if (isDefault) {
            hasOneApp = true;
            i++;
            if (i > 1) {
                isDefault = false;
                //we just deal with twice situation
                Log.d(TAG, i + "times into isDefaultApp packageName: " + packageName + " is defaultApp");
            }
        }
        return isDefault;
    }

    public class SetDefaultAppFragment extends PreferenceFragment implements ImgRadioButtonPreference.OnClickListener {
    
        public SetDefaultAppFragment() {super();}
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            createPreferenceHierarchy(getPreferenceScreen());
        }

        public PreferenceScreen createPreferenceHierarchy(PreferenceScreen root) {
            if (root != null) {
                root.removeAll();
            }
            addPreferencesFromResource(R.xml.set_default_app);
            mRoot = getPreferenceScreen();
            //add Preference
            int i = 0;
            if (mList != null && mList.size() > 0) {
                if (mList.size() == 1) {
                    mPackageMonitor.unregister();
                    mRegistered = false;
                }

                for (ResolveInfo resolveInfo : mList) {
                    String packageName = resolveInfo.activityInfo.applicationInfo.packageName;
                    String className = resolveInfo.activityInfo.name;
                    Drawable icon = resolveInfo.loadIcon(mPm);
                    String label = null;
                    try {
                        label = getPackageManager().getApplicationLabel(
                            getPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString();
                    } catch(NameNotFoundException e) {
                        e.printStackTrace();
                    }
					
					/*HQ_liugang add for HQ01699074 20160127*/
					if(mKey.equals("key_preference_message") && packageName.equals("com.android.contacts")){
						className = "com.android.mms.ui.ConversationList";
						Intent smsIntent = new Intent();
						ComponentName smsComponent = new ComponentName(packageName, className);
						smsIntent.setComponent(smsComponent);

			        	List<ResolveInfo> smsList = getPackageManager().queryIntentActivities(smsIntent, (PackageManager.GET_INTENT_FILTERS
			                                                 | PackageManager.MATCH_DEFAULT_ONLY
			                                                 | PackageManager.GET_RESOLVED_FILTER
			                                                 ));
						label = smsList.get(0).loadLabel(mPm).toString();
						icon  = smsList.get(0).loadIcon(mPm);

						smsLable = label;
					}
					/*HQ_liugang add end*/
					
                    boolean isDefault = resolveInfo.isDefault;
                    IntentFilter intentFilter = resolveInfo.filter;
                    Log.d(TAG, "intentFilter: " + intentFilter + " className: " + className +  " isDefault: " + isDefault);
                    ImgRadioButtonPreference setDefaultPreference = new ImgRadioButtonPreference(getActivity());
                    setDefaultPreference.setKey("set_default_key" + i);
                    setDefaultPreference.setTitle(label);
                    setDefaultPreference.setIcon(icon);
                    setDefaultPreference.setPersistent(false);
                    setDefaultPreference.setOnClickListener(checkBoxClickListener);
                    if (isDefaultApp(packageName)) {
                        mDefaultPosition = i;
                        setDefaultPreference.setChecked(true);
                    }
                    mRoot.addPreference(setDefaultPreference);
                    i ++;
                }
                if (!hasOneApp) {
                    Log.d(TAG, "no default app, and set the first one as default");
                    ImgRadioButtonPreference firstPre = (ImgRadioButtonPreference) mRoot.findPreference("set_default_key0");
                    if (firstPre != null) {
                        firstPre.setChecked(true);
                    }
                    ResolveInfo selectedResolveInfo = mList.get(0);
                    mDefaultPosition = 0;
                    String preferredPkgName = selectedResolveInfo.activityInfo.applicationInfo.packageName;
                    String prefrredPkgClsName = selectedResolveInfo.activityInfo.name;
                    String preferredPkgLabel = null;
                    try {
                        preferredPkgLabel = getPackageManager().getApplicationLabel(
                            getPackageManager().getApplicationInfo(preferredPkgName, PackageManager.GET_META_DATA)).toString();
                    } catch(NameNotFoundException e) {
                        e.printStackTrace();
                    }
                    if (mKey.equals("key_preference_home")) {
                        //home special
                        changePreferredLauncher(preferredPkgName);
                    } else if (mKey.equals("key_preference_call")) {
                        Intent clearIntent = new Intent(Intent.ACTION_DIAL);
                        clearIntent.setData(Uri.parse("tel:"));

                        Intent dialerIntent = new Intent(Intent.ACTION_DIAL);
                        dialerIntent.setData(Uri.parse("tel:"));
                        dialerIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        setAlwaysDefault(selectedResolveInfo, clearIntent, dialerIntent);
                    } else if (mKey.equals("key_preference_message")) {
                        Intent clearIntent = new Intent(Intent.ACTION_SENDTO);
                        clearIntent.setData(Uri.parse("smsto:"));

                        Intent msgIntent = new Intent(Intent.ACTION_SENDTO);
                        msgIntent.setAction(Intent.ACTION_SENDTO);
                        msgIntent.setData(Uri.parse("smsto:"));
                        msgIntent.setFlags(IntentFilter.MATCH_CATEGORY_HOST);
                        msgIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        //setAlwaysDefault(selectedResolveInfo, clearIntent, msgIntent);
                        SmsApplication.setDefaultApplication(preferredPkgName, getActivity());
                    } else if (mKey.equals("key_preference_camera")) {
                        Intent clearIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                        Intent  cameraIntent = new Intent();
                        cameraIntent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
                        cameraIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        setAlwaysDefault(selectedResolveInfo, clearIntent, cameraIntent);
                    } else if (mKey.equals("key_preference_gallery")) {
                        Intent clearIntent = new Intent();
                        clearIntent.setAction(Intent.ACTION_VIEW);
                        clearIntent.setDataAndType(Uri.parse("content:"), "image/*");
                        //clearIntent.addCategory(Intent.CATEGORY_APP_GALLERY);

                        Intent galleryIntent = new Intent();
                        galleryIntent.setAction(Intent.ACTION_VIEW);
                        galleryIntent.setDataAndType(Uri.parse("content:"), "image/*");
                        //galleryIntent.addCategory(Intent.CATEGORY_APP_GALLERY);
                        galleryIntent.setFlags(IntentFilter.MATCH_CATEGORY_HOST);
                        galleryIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        
                        selectedResolveInfo.match |= IntentFilter.MATCH_CATEGORY_TYPE;
                        setAlwaysDefault(selectedResolveInfo, clearIntent, galleryIntent);
                    } else if (mKey.equals("key_preference_music")) {
                        Intent clearIntent = new Intent();
                        clearIntent.setAction(Intent.ACTION_VIEW);
                        clearIntent.setDataAndType(Uri.parse("file:///"), "audio/*");

                        Intent musicIntent = new Intent();
                        musicIntent.setAction(Intent.ACTION_VIEW);
                        musicIntent.setDataAndType(Uri.parse("file:///"), "audio/*");
                        musicIntent.setFlags(IntentFilter.MATCH_CATEGORY_HOST);
                        musicIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        setAlwaysDefault(selectedResolveInfo, clearIntent, musicIntent);
                    } else if (mKey.equals("key_preference_video")) {
                        Intent clearIntent = new Intent();
                        clearIntent.setAction(Intent.ACTION_VIEW);
                        clearIntent.setDataAndType(Uri.parse("file:///"), "video/*");

                        Intent videoIntent = new Intent();
                        videoIntent.setAction(Intent.ACTION_VIEW);
                        videoIntent.setDataAndType(Uri.parse("file:///"), "video/*");
                        videoIntent.setFlags(IntentFilter.MATCH_CATEGORY_HOST);
                        videoIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        setAlwaysDefault(selectedResolveInfo, clearIntent, videoIntent);
                    } else if (mKey.equals("key_preference_email")) {
                        Intent clearIntent = new Intent();
                        clearIntent.setAction(Intent.ACTION_SENDTO);
                        clearIntent.setData(Uri.parse("mailto:"));

                        Intent mailIntent = new Intent();
                        mailIntent.setAction(Intent.ACTION_SENDTO);
                        mailIntent.setData(Uri.parse("mailto:"));
                        mailIntent.setFlags(IntentFilter.MATCH_CATEGORY_HOST);
                        mailIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        setAlwaysDefault(selectedResolveInfo, clearIntent, mailIntent);
                    } else if (mKey.equals("key_preference_browser")) {
                        Intent clearIntent = new Intent();
                        clearIntent.setAction("android.intent.action.VIEW");
                        clearIntent.addCategory(Intent.CATEGORY_BROWSABLE);
                        clearIntent.setData(Uri.parse("http://"));

                        Intent browserIntent = new Intent();
                        browserIntent.setAction("android.intent.action.VIEW");
                        browserIntent.setData(Uri.parse("http://"));
                        browserIntent.setFlags(IntentFilter.MATCH_CATEGORY_HOST);
                        browserIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        setAlwaysDefault(selectedResolveInfo, clearIntent, browserIntent);
                    }
                    updatedPreferenceSummary = mKey + ":" + preferredPkgLabel;
                }
            }
            return mRoot;
        }

        @Override
        public void onImgRadioButtonClicked(ImgRadioButtonPreference emiter) {
        }
    }

    private ImgRadioButtonPreference.OnClickListener checkBoxClickListener = new ImgRadioButtonPreference.OnClickListener() {
        @Override
        public void onImgRadioButtonClicked(ImgRadioButtonPreference preference) {
            Log.d(TAG, "click preference key: " + preference.getKey());
            createDialog(mContext, preference);
        }
    };

    protected void createDialog(final Context ctx, final ImgRadioButtonPreference selectPreference) {
        ImgRadioButtonPreference clickPreference = selectPreference;
        String key = clickPreference.getKey();
        final int position = Integer.parseInt(key.substring(key.length() -1, key.length()));
        Log.d(TAG, "createDialog position: " + position + " mDefaultPosition: " + mDefaultPosition);
        if (clickPreference != null && position != mDefaultPosition) {
            clickPreference.setChecked(true);
            //set preDefault checked false
            ImgRadioButtonPreference defaultPreference = (ImgRadioButtonPreference)mRoot.getPreference(mDefaultPosition);
            defaultPreference.setChecked(false);

            AlertDialog.Builder builder = new Builder(ctx);
            builder.setMessage(R.string.settings_application_preferred_dialog_msg);
            builder.setTitle(R.string.settings_application_preferred_dialog_title);
            builder.setPositiveButton(R.string.settings_application_preferred_dialog_positext, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ResolveInfo selectedResolveInfo = mList.get(position);
                    mDefaultPosition = position;
                    Log.d(TAG, "createDialog ri: " + selectedResolveInfo + " mKey: " + mKey + " mDefaultPosition: " + mDefaultPosition);
                    String preferredPkgName = selectedResolveInfo.activityInfo.applicationInfo.packageName;
                    String prefrredPkgClsName = selectedResolveInfo.activityInfo.name;
                    String preferredPkgLabel = null;
                    try {
                        preferredPkgLabel = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(preferredPkgName, PackageManager.GET_META_DATA)).toString();
                    } catch(NameNotFoundException e) {
                        e.printStackTrace();
                    }
                    if (mKey.equals("key_preference_home")) {
                        //home special
                        changePreferredLauncher(preferredPkgName);
                    } else if (mKey.equals("key_preference_call")) {
                        Intent clearIntent = new Intent(Intent.ACTION_DIAL);
                        clearIntent.setData(Uri.parse("tel:"));

                        Intent dialerIntent = new Intent(Intent.ACTION_DIAL);
                        dialerIntent.setData(Uri.parse("tel:"));
                        dialerIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        setAlwaysDefault(selectedResolveInfo, clearIntent, dialerIntent);
                    } else if (mKey.equals("key_preference_message")) {
                        Intent clearIntent = new Intent(Intent.ACTION_SENDTO);
                        clearIntent.setData(Uri.parse("smsto:"));

                        Intent msgIntent = new Intent(Intent.ACTION_SENDTO);
                        msgIntent.setAction(Intent.ACTION_SENDTO);
                        msgIntent.setData(Uri.parse("smsto:"));
                        msgIntent.setFlags(IntentFilter.MATCH_CATEGORY_HOST);
                        msgIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        //setAlwaysDefault(selectedResolveInfo, clearIntent, msgIntent);
                        SmsApplication.setDefaultApplication(preferredPkgName, ctx);
						/*HQ_liugang add for HQ01699074 20160127*/
						if("com.android.contacts".equals(preferredPkgName)){
							preferredPkgLabel = smsLable;
						}
						/*HQ_liugang add end*/
                    } else if (mKey.equals("key_preference_camera")) {
                        Intent clearIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                        Intent  cameraIntent = new Intent();
                        cameraIntent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
                        cameraIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        setAlwaysDefault(selectedResolveInfo, clearIntent, cameraIntent);
                    } else if (mKey.equals("key_preference_gallery")) {
                        Intent clearIntent = new Intent();
                        clearIntent.setAction(Intent.ACTION_VIEW);
                        //clearIntent.addCategory(Intent.CATEGORY_APP_GALLERY);
                        clearIntent.setDataAndType(Uri.parse("content:"), "image/*");

                        Intent galleryIntent = new Intent();
                        galleryIntent.setAction(Intent.ACTION_VIEW);
                        galleryIntent.setDataAndType(Uri.parse("content:"), "image/*");
                        //galleryIntent.addCategory(Intent.CATEGORY_APP_GALLERY);
                        galleryIntent.setFlags(IntentFilter.MATCH_CATEGORY_HOST);
                        galleryIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        
                        selectedResolveInfo.match |= IntentFilter.MATCH_CATEGORY_TYPE;                        
                        setAlwaysDefault(selectedResolveInfo, clearIntent, galleryIntent);
                    } else if (mKey.equals("key_preference_music")) {
                        Intent clearIntent = new Intent();
                        clearIntent.setAction(Intent.ACTION_VIEW);
                        clearIntent.setDataAndType(Uri.parse("file:///"), "audio/*");

                        Intent musicIntent = new Intent();
                        musicIntent.setAction(Intent.ACTION_VIEW);
                        musicIntent.setDataAndType(Uri.parse("file:///"), "audio/*");
                        musicIntent.setFlags(IntentFilter.MATCH_CATEGORY_HOST);
                        musicIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        setAlwaysDefault(selectedResolveInfo, clearIntent, musicIntent);
                    } else if (mKey.equals("key_preference_video")) {
                        Intent clearIntent = new Intent();
                        clearIntent.setAction(Intent.ACTION_VIEW);
                        clearIntent.setDataAndType(Uri.parse("file:///"), "video/*");

                        Intent videoIntent = new Intent();
                        videoIntent.setAction(Intent.ACTION_VIEW);
                        videoIntent.setDataAndType(Uri.parse("file:///"), "video/*");
                        videoIntent.setFlags(IntentFilter.MATCH_CATEGORY_HOST);
                        videoIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        setAlwaysDefault(selectedResolveInfo, clearIntent, videoIntent);
                    } else if (mKey.equals("key_preference_email")) {
                        Intent clearIntent = new Intent();
                        clearIntent.setAction(Intent.ACTION_SENDTO);
                        clearIntent.setData(Uri.parse("mailto:"));

                        Intent mailIntent = new Intent();
                        mailIntent.setAction(Intent.ACTION_SENDTO);
                        mailIntent.setData(Uri.parse("mailto:"));
                        mailIntent.setFlags(IntentFilter.MATCH_CATEGORY_HOST);
                        mailIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        setAlwaysDefault(selectedResolveInfo, clearIntent, mailIntent);
                    } else if (mKey.equals("key_preference_browser")) {
                        Intent clearIntent = new Intent();
                        clearIntent.setAction("android.intent.action.VIEW");
                        clearIntent.addCategory(Intent.CATEGORY_BROWSABLE);
                        clearIntent.setData(Uri.parse("http://"));

                        Intent browserIntent = new Intent();
                        browserIntent.setAction("android.intent.action.VIEW");
                        browserIntent.setData(Uri.parse("http://"));
                        browserIntent.setFlags(IntentFilter.MATCH_CATEGORY_HOST);
                        browserIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
                        setAlwaysDefault(selectedResolveInfo, clearIntent, browserIntent);
                    }
                    updatedPreferenceSummary = mKey + ":" + preferredPkgLabel;
                    sharePre.edit().putBoolean(SET_DEFUALT_BROWSER, true).commit();
                }
            });
            builder.setNegativeButton(R.string.settings_application_preferred_dialog_negtext, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Log.d(TAG, "createDialog position: " + position + " mDefaultPosition: " + mDefaultPosition);
                    ImgRadioButtonPreference clickPreference = (ImgRadioButtonPreference)mRoot.getPreference(position);
                    clickPreference.setChecked(false);
                    ImgRadioButtonPreference defaultPreference = (ImgRadioButtonPreference)mRoot.getPreference(mDefaultPosition);
                    defaultPreference.setChecked(true);
                    dialog.dismiss();
                }
            });
            builder.create().show();
        }
    }

    public void setAlwaysDefault(ResolveInfo ri, Intent clearIntent, Intent intent) {
        if (null == mPm || ri == null || clearIntent == null || intent == null) {
            return;
        }

        List<ResolveInfo> resolveInfos = mPm.queryIntentActivities(
                clearIntent, (PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_RESOLVED_FILTER));
        Log.d(TAG, "in changePreferredBrowser resolveInfos: " + resolveInfos.size());

        for (int i = 0; i < resolveInfos.size(); i++) {
            ResolveInfo resolveInfo = resolveInfos.get(i);
            if (null != resolveInfo) {
                mPm.clearPackagePreferredActivities(resolveInfo.activityInfo.packageName);
            }
        }

        // Build a reasonable intent filter, based on what matched.
        IntentFilter filter = new IntentFilter();

        if (intent.getAction() != null) {
            filter.addAction(intent.getAction());
        }
        Set<String> categories = intent.getCategories();
        if (categories != null) {
            for (String cat : categories) {
                filter.addCategory(cat);
            }
        }
        filter.addCategory(Intent.CATEGORY_DEFAULT);

        int cat = ri.match&IntentFilter.MATCH_CATEGORY_MASK;
        Uri data = intent.getData();
        if (cat == IntentFilter.MATCH_CATEGORY_TYPE) {
            String mimeType = intent.resolveType(this);
            if (mimeType != null) {
                try {
                    filter.addDataType(mimeType);
                } catch (IntentFilter.MalformedMimeTypeException e) {
                    Log.w("ResolverActivity", e);
                    filter = null;
                }
            }
        }
        if (data != null && data.getScheme() != null) {
            // We need the data specification if there was no type,
            // OR if the scheme is not one of our magical "file:"
            // or "content:" schemes (see IntentFilter for the reason).
            if (cat != IntentFilter.MATCH_CATEGORY_TYPE
                    || (!"file".equals(data.getScheme())
                            && !"content".equals(data.getScheme()))) {
                filter.addDataScheme(data.getScheme());

                // Look through the resolved filter to determine which part
                // of it matched the original Intent.
                Iterator<PatternMatcher> pIt = ri.filter.schemeSpecificPartsIterator();
                if (pIt != null) {
                    String ssp = data.getSchemeSpecificPart();
                    while (ssp != null && pIt.hasNext()) {
                        PatternMatcher p = pIt.next();
                        if (p.match(ssp)) {
                            filter.addDataSchemeSpecificPart(p.getPath(), p.getType());
                            break;
                        }
                    }
                }
                Iterator<IntentFilter.AuthorityEntry> aIt = ri.filter.authoritiesIterator();
                if (aIt != null) {
                    while (aIt.hasNext()) {
                        IntentFilter.AuthorityEntry a = aIt.next();
                        if (a.match(data) >= 0) {
                            int port = a.getPort();
                            filter.addDataAuthority(a.getHost(),
                                    port >= 0 ? Integer.toString(port) : null);
                            break;
                        }
                    }
                }
                pIt = ri.filter.pathsIterator();
                if (pIt != null) {
                    String path = data.getPath();
                    while (path != null && pIt.hasNext()) {
                        PatternMatcher p = pIt.next();
                        if (p.match(path)) {
                            filter.addDataPath(p.getPath(), p.getType());
                            break;
                        }
                    }
                }
            }
        }

        if (filter != null) {
            final int N = resolveInfos.size();
            ComponentName[] set = new ComponentName[N];
            int bestMatch = 0;
            for (int i=0; i<N; i++) {
                ResolveInfo r = resolveInfos.get(i);
                set[i] = new ComponentName(r.activityInfo.packageName,
                        r.activityInfo.name);
                Log.d(TAG, "onIntentSelected r.match: " + r.match);
                if (r.match > bestMatch) bestMatch = r.match;
            }
            Log.d(TAG, "onIntentSelected alwaysCheck: true filter:"  + filter + " bestMatch: " + bestMatch
                + "  set: " + set + " comp: " + intent.getComponent() + " intent: " + intent);
            getPackageManager().addPreferredActivity(filter, bestMatch, set,
                    intent.getComponent());
        }
    }

    /**
     * change the Preferred Launcher.
     */
    public void changePreferredLauncher(String pkgName) {
        if (null == mPm) {
            return;
        }

        Intent mainIntent = new Intent(Intent.ACTION_MAIN).addCategory(
                Intent.CATEGORY_HOME).addCategory(Intent.CATEGORY_DEFAULT);
        List<ResolveInfo> resolveInfos = mPm.queryIntentActivities(
                mainIntent, 0);
        for (int i = 0; i < resolveInfos.size(); i++) {
            ResolveInfo resolveInfo = resolveInfos.get(i);
            if (null != resolveInfo) {
                mPm.clearPackagePreferredActivities(resolveInfo.activityInfo.packageName);
            }
        }

        int sz = resolveInfos.size();
        int find = -1;
        ComponentName[] set = new ComponentName[sz];
        for (int i = 0; i < sz; i++) {
            final ResolveInfo info = resolveInfos.get(i);
            set[i] = new ComponentName(info.activityInfo.packageName,
                    info.activityInfo.name);

            if (info.activityInfo.packageName.equals(pkgName)) {
                find = i;
            }
        }

        if (find != -1) {
            IntentFilter inf = new IntentFilter(Intent.ACTION_MAIN);
            inf.addCategory(Intent.CATEGORY_HOME);
            inf.addCategory(Intent.CATEGORY_DEFAULT);
            mPm.addPreferredActivity(inf,
                    IntentFilter.MATCH_CATEGORY_EMPTY, set, set[find]);
        }
    }

    private Intent getActualIntent() {
        Intent mainIntent = getIntent();
        Intent startIntent = null;
        String key = mainIntent.getStringExtra("key");
        String title = mainIntent.getStringExtra("title");
        mKey = key;
        mTitle = title;
        if (key == null)
            return null;
        if (key.equals(LISTPREF_HOME_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(Intent.ACTION_MAIN);
            startIntent.addCategory(Intent.CATEGORY_HOME);
        } else if (key.equals(LISTPREF_CALL_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(Intent.ACTION_DIAL);
            startIntent.setData(Uri.parse("tel:"));
        } else if (key.equals(LISTPREF_MESSAGE_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(Intent.ACTION_SENDTO);
            startIntent.setData(Uri.parse("smsto:"));
        } else if (key.equals(LISTPREF_CAMERA_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
        } else if (key.equals(LISTPREF_GALLERY_KEY)) {
            startIntent = new Intent();
            startIntent.setDataAndType(Uri.parse("content:"), "image/*");
            startIntent.setAction(Intent.ACTION_VIEW);
            //startIntent.addCategory(Intent.CATEGORY_APP_GALLERY);
        } else if (key.equals(LISTPREF_MUSIC_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(Intent.ACTION_VIEW);
            startIntent.setDataAndType(Uri.parse("file:///"), "audio/*");
        } else if (key.equals(LISTPREF_VIDEO_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(Intent.ACTION_VIEW);
            startIntent.setDataAndType(Uri.parse("file:///"), "video/*");
        } else if (key.equals(LISTPREF_EMAIL_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(Intent.ACTION_SENDTO);
            startIntent.setData(Uri.parse("mailto:"));
        } else if (key.equals(LISTPREF_BROWSER_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(Intent.ACTION_VIEW);
            startIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            startIntent.setData(Uri.parse("http://"));
        }
        return startIntent;
    }
}
