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

package com.android.settings.accessibility;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;


import android.R.integer;
import android.R.string;
import android.app.Activity;
import android.app.ActionBar;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.text.TextUtils;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.view.Gravity;
import android.preference.CheckBoxPreference;
import android.widget.Switch;
import com.android.settings.NetTrafficUtils;
import com.android.settings.R;
import android.view.MenuItem;
import android.provider.ChildMode;
import com.android.settings.childsecurity.ChooseChildLockHelper;
import com.android.settings.accessibility.networklimit.WebSiteListActivity;
import com.android.settings.accounts.AddAccountSettings;

import android.view.KeyEvent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.telephony.SubscriptionManager;//add by lihaizhou at 2015-08-05 
import android.telephony.TelephonyManager;//add by lihaizhou at 2015-08-05
import com.android.settings.DataUsageSummary;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;

public class ChildModeSettings extends PreferenceActivity 
implements CompoundButton.OnCheckedChangeListener, Preference.OnPreferenceChangeListener{

	private static final String MODIFY_PASSWORD_PREFERENCE ="modify_password";
	//private static final String APP_RESTRICTIONS_BLACKLIST_PREFERENCE ="application_restrictions_blacklist";
	private static final String CLOSE_APP_RESTRICTIONS = "close_application_restrictions_preference";
    private static final String APP_BLACKLIST = "application_restrictions_blacklist";
    private static final String WIBESITE_ACCESS_RESTRICTIONS = "website_access_restrictions_preference";
    private static final String INTERNET_TIME_SETTINGS ="internet_time_settings_preference";
	private static final String INTERNET_TRAFFIC_LIMITS = "internet_traffic_limits_preference";
    private static final String PROHIBIT_SEND_SMSMMS = "prohibit_send_smsmms_preference";
    private static final String PROHIBIT_CALL = "prohibit_call_preference";
    private static final String PROHIBIT_DATA_SERVICES ="prohibit_data_services_preference";
	private static final String PROHIBIT_WLAN = "prohibit_wlan_preference";
    private static final String PROHIBIT_DELETE_SMSMMS = "prohibit_delete_smsmms_preference";
    private static final String PROHIBIT_INSTALL_APP = "prohibit_install_application_preference";
    private static final String PROHIBIT_DELETE_APP = "prohibit_delete_application_preference";
    /* add lihaizhou to add password for child mode begin */
    private static final int CONFIRM_EXISTING_LOCK_REQUEST = 130;
    private static final int CONFIRM_EXISTING_FROM_RESUME = 131;
    private static final int SET_NEW_CHILD_LOCK_REQUEST = 132;
    private static final int CHANGE_CHILD_LOCK_REQUEST = 133;
    private ChooseChildLockHelper mChooseChildLockHelper;
    private boolean needToConfirm = false;
    private boolean selfChangeSwitchState = false;
    /* add lihaizhou to add password for child mode end */
    private static Boolean mMobileDataEnabled = false;
    private ConnectivityManager mConnService;
	private Preference mModifyPasswordPreference;
	//private Preference mPrihibitAppBlacklistPreference;
	
	//private CheckBoxPreference mCloseAppRestrictions;
	//private Preference mAppBalckList;
	private SwitchPreference mWebsiteAccessRestrictions;
	private SwitchPreference mAppBalckList;
	//private SwitchPreference mInternetTimeSettings;
	//private SwitchPreference mInternetTrafficLimits;
	private CheckBoxPreference mProhibitSendSmsmms;
	private CheckBoxPreference mProhibitCall;
	private CheckBoxPreference mProhibitDataServices;
	private CheckBoxPreference mProhibitWlan;
	private CheckBoxPreference mProhibitDeleteSmsmms;
	private CheckBoxPreference mProhibitInstallApp;
	private CheckBoxPreference mProhibitDeleteApp;
	private CheckExEditorPreference mNetworklimiteTimePref;
    private CheckExEditorPreference mNetworklimiteTrafficPref;
	
    private Switch mSwitch;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.child_mode);
        
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
        }
        boolean first = SystemProperties.getBoolean("persist.sys.firsttimeinchild", false);	
        if (!first) {
        	startActivity(new Intent(this, ChildmodeHelp.class));
        	SystemProperties.set("persist.sys.firsttimeinchild", "true");

		}
        ContentResolver resolver = this.getContentResolver();
        mModifyPasswordPreference =
                (Preference) findPreference(MODIFY_PASSWORD_PREFERENCE);
        /* add password for child mode begin */
        mChooseChildLockHelper = new ChooseChildLockHelper(this);
        needToConfirm = true;
        /*add password for child mode end */

        mSwitch = new Switch(this);
        mSwitch.setChecked(isChildModeOn());
        
        mConnService = (ConnectivityManager) this.
                getSystemService(Context.CONNECTIVITY_SERVICE);
        
        mSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		/*Modified by lihaizhou for add confirm dialog for open child mode begin*/
                Log.i(ChooseChildLockHelper.TAG, "open child mode " + isChecked);
                if(isChecked && !selfChangeSwitchState){ 
            		new AlertDialog.Builder(ChildModeSettings.this)
                		.setTitle(R.string.child_mode_open_title)
                		.setMessage(R.string.child_mode_open_message)
                		.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
								 
                           
								finish();
                            }
                        })
                		.setPositiveButton(android.R.string.ok,
                        		new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                      
                                        if (mConnService.getMobileDataEnabled()) {
                                            SystemProperties.set("persist.sys.childdataenable", "true");
                                         } else {
                                            SystemProperties.set("persist.sys.childdataenable", "false");
                                         }
                              
                                        if (!selfChangeSwitchState) {
                                            needToConfirm = true;
                                            if (!mChooseChildLockHelper.isChildLockSet()) {
                                                goSetChildLock(true);
                                            } else {
                                                startChildLockConfirm(CONFIRM_EXISTING_LOCK_REQUEST);
                                            }
                                        } else {
                                            selfChangeSwitchState = false;
                                        }
                                    }
                        })
						
						.setOnCancelListener(new DialogInterface.OnCancelListener(){
							@Override
							public void onCancel(DialogInterface dialog){

								finish();
							}
						})
                		.show();
                } else {
                	
                	
                    if (!selfChangeSwitchState) {
                        needToConfirm = true;
                        if (!mChooseChildLockHelper.isChildLockSet()) {
                            goSetChildLock(true);
                        } else {
                            startChildLockConfirm(CONFIRM_EXISTING_LOCK_REQUEST);
                        }
                    } else {
                        selfChangeSwitchState = false;
                    }
                  try {
                        boolean dataenable = SystemProperties.getBoolean("persist.sys.childdataenable", false);
                        if (dataenable) {
                            //mConnService.setMobileDataEnabled(true);//delete by lihaizhou
                           /*add by lihaizhou for open data at 2015-08-05 by begin*/
                            turnOnMobileData();
                           /*add by lihaizhou for open data at 2015-08-05 by end*/
                        }
                    } catch (Exception e) {
						// TODO: handle exception
					}
                	
                }

            }
        });
        final int padding = this.getResources().getDimensionPixelSize(
                R.dimen.action_bar_switch_padding);
        mSwitch.setPaddingRelative(0, 0, padding, 0);
        this.getActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                ActionBar.DISPLAY_SHOW_CUSTOM);
        this.getActionBar().setCustomView(mSwitch, new ActionBar.LayoutParams(
                ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.END));

        mAppBalckList = (SwitchPreference) findPreference(APP_BLACKLIST);
        mAppBalckList.setChecked(isCloseAppRestrictions());
        mAppBalckList.setOnPreferenceChangeListener(this);
        
        mWebsiteAccessRestrictions = (SwitchPreference) findPreference(WIBESITE_ACCESS_RESTRICTIONS);
        mWebsiteAccessRestrictions.setChecked(isWebsiteAccessRestrictions());
        mWebsiteAccessRestrictions.setOnPreferenceChangeListener(this);
        
        
        
        mProhibitSendSmsmms = (CheckBoxPreference) findPreference(PROHIBIT_SEND_SMSMMS);
        mProhibitSendSmsmms.setChecked(isProhibitSendSmsmms());
        mProhibitSendSmsmms.setOnPreferenceChangeListener(this);
        
        mProhibitCall = (CheckBoxPreference) findPreference(PROHIBIT_CALL);
        mProhibitCall.setChecked(isProhibitCall());
        mProhibitCall.setOnPreferenceChangeListener(this);
        
        mProhibitDataServices = (CheckBoxPreference) findPreference(PROHIBIT_DATA_SERVICES);
        mProhibitDataServices.setChecked(isProhibitDataServices());
        mProhibitDataServices.setOnPreferenceChangeListener(this);
        
        mProhibitWlan = (CheckBoxPreference) findPreference(PROHIBIT_WLAN);
        mProhibitWlan.setChecked(isProhibitWlan());
        mProhibitWlan.setOnPreferenceChangeListener(this);
        
        mProhibitDeleteSmsmms = (CheckBoxPreference) findPreference(PROHIBIT_DELETE_SMSMMS);
        mProhibitDeleteSmsmms.setChecked(isProhibitDeleteSmsmms());
        mProhibitDeleteSmsmms.setOnPreferenceChangeListener(this);
        
        mProhibitInstallApp = (CheckBoxPreference) findPreference(PROHIBIT_INSTALL_APP);
        mProhibitInstallApp.setChecked(isProhibitInstallApp());
        mProhibitInstallApp.setOnPreferenceChangeListener(this);
        
        mProhibitDeleteApp = (CheckBoxPreference) findPreference(PROHIBIT_DELETE_APP);
        mProhibitDeleteApp.setChecked(isProhibitDeleteApp());
        mProhibitDeleteApp.setOnPreferenceChangeListener(this);
        setupPrefernces();
        if (isChildModeOn()) {
        	refreshChildUi(true);
    	}else {
    		refreshChildUi(false);
    	}
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
    if (isChildModeOn()) {
    	refreshChildUi(true);
        startChildLockConfirm(CONFIRM_EXISTING_FROM_RESUME);
	}else {
		refreshChildUi(false);
	}

    }


    public boolean onPreferenceChange(Preference preference, Object newValue) {
    	final boolean flag = (Boolean) newValue;
    	Log.i("ZSP", "onPreferenceChange(" + preference + flag);
        if (preference == mAppBalckList) {
            if (mAppBalckList.isChecked() != flag) {
                setCloseAppRestrictions(flag);
            }
            return true;
        }else if (preference == mWebsiteAccessRestrictions) {
            if (mWebsiteAccessRestrictions.isChecked() != flag) {
                setWebsiteAccessRestrictions(flag);              
                Intent intent = new Intent("com.settings.childmode.disable.3rdBrowser");
                intent.putExtra("enable", flag);
                sendBroadcast(intent);
                
                if (flag) {
                    forceStop3rdBrowserApp(getBaseContext());
                }
            }
            return true;
		}
		else if (preference == mProhibitSendSmsmms) {
            if (mProhibitSendSmsmms.isChecked() != flag) {
            	setProhibitSendSmsmms(flag);
            }
            return true;
		}else if (preference == mProhibitCall) {
            if (mProhibitCall.isChecked() != flag) {
            	setProhibitCall(flag);
            }
            return true;
		}else if (preference == mProhibitDataServices) {
            if (mProhibitDataServices.isChecked() != flag) {
            	setProhibitDataServices(flag);
            }
            return true;
		}else if (preference == mProhibitWlan) {
            if (mProhibitWlan.isChecked() != flag) {
            	setProhibitWlan(flag);
            }
            return true;
		}else if (preference == mProhibitDeleteSmsmms) {
            if (mProhibitDeleteSmsmms.isChecked() != flag) {
            	setProhibitDeleteSmsmms(flag);
            }
            return true;
		}else if (preference == mProhibitInstallApp) {
            if (mProhibitInstallApp.isChecked() != flag) {
            	setProhibitInstallApp(flag);
            }
            return true;
		}else if (preference == mProhibitDeleteApp) {
            if (mProhibitDeleteApp.isChecked() != flag) {
            	setProhibitDeleteApp(flag);
            }
            return true;
		}
        return false;
    }
    
    public void onCheckedChanged(CompoundButton paramCompoundButton, boolean paramBoolean)
    {
    	Log.i("ZSP", "onCheckedChanged(" + paramCompoundButton + ") ");
        

    }
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
       
        final String key = preference.getKey();
        if (preference == mModifyPasswordPreference) {
            goSetChildLock(false);
        } else if(preference == mAppBalckList) {
        	if (mAppBalckList.isChecked()) {
        		startActivity(new Intent(this, ManageAppsActivity.class));
        	}
        } else if (preference == mWebsiteAccessRestrictions) {
            if (mWebsiteAccessRestrictions.isChecked()) {
                startActivity(new Intent(this, WebSiteListActivity.class));
            }
        } else {
            // If we didn't handle it, let preferences handle it.
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        return true;
    }
    private void refreshChildUi(boolean idEnable) {

    	mModifyPasswordPreference.setEnabled(idEnable);
    	//mPrihibitAppBlacklistPreference.setEnabled(idEnable);
    	mAppBalckList.setEnabled(idEnable);
    	mWebsiteAccessRestrictions.setEnabled(idEnable);
    	//mInternetTimeSettings.setEnabled(idEnable);
    	//mInternetTrafficLimits.setEnabled(idEnable);
    	mProhibitSendSmsmms.setEnabled(idEnable);
    	mProhibitCall.setEnabled(idEnable);
    	mProhibitDataServices.setEnabled(idEnable);
    	mProhibitWlan.setEnabled(idEnable);
    	mProhibitDeleteSmsmms.setEnabled(idEnable);
    	mProhibitInstallApp.setEnabled(idEnable);
    	mProhibitDeleteApp.setEnabled(idEnable);
    	mNetworklimiteTimePref.setEnabled(idEnable);
    	mNetworklimiteTrafficPref.setEnabled(idEnable);

        //begin:turn off mobile data when ChidlMode is on 
        if (idEnable && isProhibitDataServices()) {
            //if data service switch is on, then turn off mobile data.
            turnOffMobileData();
        }
        //end:turn off mobile data when ChidlMode is on 
    }

    /**
     * author: lihaizhou
     * date: 20150714
     * purpose: when to confirm password for child mode 
     */
    @Override
    public void onStart() {
        super.onStart();
        IntentFilter mHomeIntentFilter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomeReceiver, mHomeIntentFilter);
    }

    /**
     * author: lihaizhou
     * date: 20150714
     * purpose: when to confirm password for child mode 
     */
    @Override
    public void onStop() {
        /* modified when to confirm password for child mode begin */
        //needToConfirm = true;
        unregisterReceiver(mHomeReceiver);
        /* modified when to confirm password for child mode end */
        super.onStop();
    }

    /**
     * author: lihaizhou
     * date: 20150715
     * purpose: check password when start from launcher
     *          after HOME exit from sub activity
     */
    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(ChooseChildLockHelper.TAG, "onNewIntent reset confirm");
        needToConfirm = true;
    }
    /**
     * author: lihaizhou
     * date: 20150715
     * purpose: check password when start from launcher
     *          after HOME exit from sub activity 
     */
    private BroadcastReceiver mHomeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                String reason = intent.getStringExtra("reason");
                if ("homekey".equals(reason)) {
                    Log.i(ChooseChildLockHelper.TAG, "home key pressed");
                    needToConfirm = true;
                }
            }
        }
    };
    /**
     * author: lihaizhou
     * date: 20150714
     * purpose: when to confirm password for child mode 
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.i(ChooseChildLockHelper.TAG, "Press back, need confirm again");
            needToConfirm = true;
        }
        return super.onKeyDown(keyCode, event);
    }

   /**
     * author: lihaizhou
     * date: 20150714
     * purpose: when to confirm password for child mode 
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CONFIRM_EXISTING_LOCK_REQUEST || requestCode == CONFIRM_EXISTING_FROM_RESUME) {
            Log.i(ChooseChildLockHelper.TAG, "requestCode " + requestCode + " resultCode " + resultCode);
            if (resultCode == Activity.RESULT_OK) {
                needToConfirm = false;
                setChildModeOn(mSwitch.isChecked());
                refreshChildUi(mSwitch.isChecked());
            } else if (requestCode == CONFIRM_EXISTING_FROM_RESUME) {
                if (resultCode == RESULT_FIRST_USER) {
                    finish();
                }
            } else {
                needToConfirm = false;
                selfChangeSwitchState = true;
                mSwitch.setChecked(!mSwitch.isChecked());
            }
        } else if (requestCode == SET_NEW_CHILD_LOCK_REQUEST || requestCode == CHANGE_CHILD_LOCK_REQUEST) {
            needToConfirm = false;
            if (resultCode != Activity.RESULT_CANCELED) {
                setChildModeOn(mSwitch.isChecked());
                refreshChildUi(mSwitch.isChecked());
            } else if (requestCode == SET_NEW_CHILD_LOCK_REQUEST) {
                selfChangeSwitchState = true;
                mSwitch.setChecked(!mSwitch.isChecked());
            }
        }
    }
    /**
     * author: lihaizhou
     * date: 20150714
     * purpose: add password for child mode 
     */
    private void startChildLockConfirm(int request) {
        if (needToConfirm) {
            boolean launched = mChooseChildLockHelper
                    .launchConfirmationActivity(request, null, null);
            if (!launched) {
                setChildModeOn(mSwitch.isChecked());
                refreshChildUi(mSwitch.isChecked());
            } else {
                Log.i(ChooseChildLockHelper.TAG, "wait for confirm");
            }
        }
    }
    /**
     * author: lihaizhou
     * date: 20150714
     * purpose: add password for child mode 
     */
    private void goSetChildLock(boolean notSet) {
        Intent intent = new Intent("android.app.action.SET_NEW_CHILD_PASSWORD");
        startActivityForResult(intent, notSet ? SET_NEW_CHILD_LOCK_REQUEST : CHANGE_CHILD_LOCK_REQUEST);
    }

		    private boolean isChildModeOn() {
		        String isOn = ChildMode.getString(this.getContentResolver(),
		       		ChildMode.CHILD_MODE_ON);
		        if(isOn != null && "1".equals(isOn)){
		       	 return true;
		        }else {			
		       	 return false;
				}
		       	 
		   }
		    private void setChildModeOn(boolean isEnable) {
		    	if (isEnable) {
		    		boolean tempEnable = isChildModeOn();
                    boolean isAppBlackListOn =ChildMode.isAppBlackListOn(getContentResolver());
                    boolean isFlashLightInBackList = ChildMode.isInAppBlackList(getContentResolver(),"com.huawei.flashlight");
                    if (isAppBlackListOn && isFlashLightInBackList) {
                        turnOffFlashLight();
                    }
                    String Prohibit = ChildMode.getString(this.getContentResolver(),
		    		ChildMode.FORBID_UNINSTALL_APP );
                    if(Prohibit != null && "1".equals(Prohibit))
                    {
		    SystemProperties.set("persist.sys.isProhibit", "true");
		    Log.d("lihaizhou1","persist.sys.isProhibit"+SystemProperties.get("persist.sys.isProhibit", "false"));
		    }
		    String Prohibitinstall = ChildMode.getString(this.getContentResolver(),
		    		ChildMode.FORBID_INSTALL_APP );
                    if(Prohibitinstall != null && "1".equals(Prohibitinstall))
                    {
		    SystemProperties.set("persist.sys.Prohibitinstall", "true");
		    Log.d("lihaizhou1","persist.sys.Prohibitinstall"+SystemProperties.get("persist.sys.Prohibitinstall", "false"));
		    }
                    ChildMode.putString(this.getContentResolver(),ChildMode.CHILD_MODE_ON,"1");
                    Intent intent = new Intent();
                    intent.setAction("isChildMode.Switch.on");
                    sendBroadcast(intent);
                    Log.d("lihaizhou","isChildMode.Switch.On"+""+ChildMode.getString(this.getContentResolver(),ChildMode.CHILD_MODE_ON));
						if(!tempEnable){
							Log.d("byjlimit2","setChildModeOn true initnetwork");
							initnetwork();	
						}
				} else {
					 boolean tempEnable = isChildModeOn();                        
					 ChildMode.putString(this.getContentResolver(),ChildMode.CHILD_MODE_ON,"0");					 
                                         Intent intent = new Intent();
                                         intent.setAction("isChildMode.Switch.on");
                                         sendBroadcast(intent);
					 if(tempEnable){
					 	Log.d("byjlimit2","setChildModeOn false cancelnetwork");
							cancelnetwork();	
						}
					SystemProperties.set("persist.sys.isProhibitInstall", "false");
			                 SystemProperties.set("persist.sys.isProhibit", "false");
                                        Log.d("lihaizhou1","persist.sys.isProhibitInstall"+SystemProperties.get("persist.sys.isProhibitInstall", "false"));
					 Log.d("lihaizhou1","persist.sys.isProhibit"+SystemProperties.get("persist.sys.isProhibit", "false"));
				}
		    	updateAppLimit(isEnable,"setChildModeOn");
                //disable 3rdBrowser when WebsiteAccessRestriction is enabled -begin-
                boolean isWebsiteRestr = ChildMode.isUrlWhteListOn(getContentResolver());
                boolean enableWebsiteRestr = isWebsiteRestr && isEnable;
                Intent intent = new Intent("com.settings.childmode.disable.3rdBrowser");
                intent.putExtra("enable", enableWebsiteRestr);
                sendBroadcast(intent);
                
                if (enableWebsiteRestr) {
                    forceStop3rdBrowserApp(getBaseContext());
                }
                //disable 3rdBrowser when WebsiteAccessRestriction is enabled -end-

		    }
		    private boolean isCloseAppRestrictions() {
		         String isOn = ChildMode.getString(this.getContentResolver(),
		        		ChildMode.APP_BlACK_LIST_ON );
		         if(isOn != null && "1".equals(isOn)){
		        	 return true;
		         }else {
		        	 return false;
				}	 
		    }
		    private void setCloseAppRestrictions(boolean isEnable) {
		    	if (isEnable) {
                    boolean isFlashLightInBackList = ChildMode.isInAppBlackList(getContentResolver(),"com.huawei.flashlight");
                    if (isFlashLightInBackList) {
                        turnOffFlashLight();
                    }
		    		ChildMode.putString(this.getContentResolver(),ChildMode.APP_BlACK_LIST_ON ,"1");
				} else {
					ChildMode.putString(this.getContentResolver(),ChildMode.APP_BlACK_LIST_ON ,"0");
				}
		    	updateAppLimit(isEnable,"setCloseAppRestrictions");
		    }
    
		    private boolean isWebsiteAccessRestrictions() {
		         String isOn = ChildMode.getString(this.getContentResolver(),
		        		ChildMode.URL_WHITE_LIST_ON );
		         if(isOn != null && "1".equals(isOn)){
		        	 return true;
		         }else {
		        	 return false;
				}	 
		    }
		    private void setWebsiteAccessRestrictions(boolean isEnable) {
		    	if (isEnable) {
		    		ChildMode.putString(this.getContentResolver(),ChildMode.URL_WHITE_LIST_ON ,"1");
				} else {
					ChildMode.putString(this.getContentResolver(),ChildMode.URL_WHITE_LIST_ON ,"0");
				}
		    }
		    private boolean isInternetTimeSettings() {
		        String isOn = ChildMode.getString(this.getContentResolver(),
		       		ChildMode.INTERNET_TIME_RESTRICTION_ON );
		        if(isOn != null && "1".equals(isOn)){
		       	 return true;
		        }else {
		       	 return false;
				}	 
		   }
		   private void setInternetTimeSettings(boolean isEnable) {
		   	if (isEnable) {
		   		ChildMode.putString(this.getContentResolver(),ChildMode.INTERNET_TIME_RESTRICTION_ON ,"1");
				} else {
					ChildMode.putString(this.getContentResolver(),ChildMode.INTERNET_TIME_RESTRICTION_ON ,"0");
				}
		   }
		   private boolean isInternetTrafficLimits() {
		       String isOn = ChildMode.getString(this.getContentResolver(),
		      		ChildMode.INTERNET_TRAFFIC_RESTRICTION_ON );
		       if(isOn != null && "1".equals(isOn)){
		      	 return true;
		       }else {
		      	 return false;
				}	 
		  }
		  private void setInternetTrafficLimits(boolean isEnable) {
		  	if (isEnable) {
		  		ChildMode.putString(this.getContentResolver(),ChildMode.INTERNET_TRAFFIC_RESTRICTION_ON ,"1");
				} else {
					ChildMode.putString(this.getContentResolver(),ChildMode.INTERNET_TRAFFIC_RESTRICTION_ON ,"0");
				}
		  }
		  private boolean isProhibitSendSmsmms() {
		      String isOn = ChildMode.getString(this.getContentResolver(),
		     		ChildMode.FORBID_SEND_MESSAGE_ON );
		      if(isOn != null && "1".equals(isOn)){
		     	 return true;
		      }else {
		     	 return false;
				}	 
		 }
		 private void setProhibitSendSmsmms(boolean isEnable) {
		 	if (isEnable) {
		 		ChildMode.putString(this.getContentResolver(),ChildMode.FORBID_SEND_MESSAGE_ON ,"1");
				} else {
					ChildMode.putString(this.getContentResolver(),ChildMode.FORBID_SEND_MESSAGE_ON ,"0");
				}
		 }
		 private boolean isProhibitCall() {
		     String isOn = ChildMode.getString(this.getContentResolver(),
		    		ChildMode.FORBID_CALL );
		     if(isOn != null && "1".equals(isOn)){
		    	 return true;
		     }else {
		    	 return false;
			}	 
		}
		private void setProhibitCall(boolean isEnable) {
			if (isEnable) {
				ChildMode.putString(this.getContentResolver(),ChildMode.FORBID_CALL ,"1");
			} else {
				ChildMode.putString(this.getContentResolver(),ChildMode.FORBID_CALL ,"0");
			}
		}
		private boolean isProhibitDataServices() {
		     String isOn = ChildMode.getString(this.getContentResolver(),
		    		ChildMode.FORBID_DATA );
		     if(isOn != null && "1".equals(isOn)){
		    	 return true;
		     }else {
		    	 return false;
			}	 
		}
		private void setProhibitDataServices(boolean isEnable) {
			if (isEnable) {
				ChildMode.putString(this.getContentResolver(),ChildMode.FORBID_DATA ,"1");
                //begin:turn off mobile data when ChidlMode is on 
                turnOffMobileData();
                //end:turn off mobile data when ChidlMode is on 
            } else {
				ChildMode.putString(this.getContentResolver(),ChildMode.FORBID_DATA ,"0");
			}
		}

    //begin:turn off mobile data when ChidlMode is on
    private void turnOffMobileData() {
        //turn off mobile data switch
        //ConnectivityManager mConnService = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);//delete by lihaizhou
        //mConnService.setMobileDataEnabled(false);//delete by lihaizhou for this method is private in DatausageSummary class
        /*add by lihaizhou for forbid data at 2015-08-05 by begin*/
        TelephonyManager telephonyManager = (TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(this);
        int subId = subscriptionManager.getDefaultDataSubId();
        if(subId>0){ 
        telephonyManager.setDataEnabled (false); 
        telephonyManager.setDataEnabled (subId, false);        
        }else{ 
        //Log.d(TAG, " initial state ,mobile data haven't been set"); 
        } 
        //telephonyManager.setDataEnabled (subId, false);
         /*add by lihaizhou for forbid data at 2015-08-05 by end*/
    }
    //end:turn off mobile data when ChidlMode is on

/*add by lihaizhou at 2015-08-05 by begin */
      private void turnOnMobileData() {
        //turn on mobile data switch
        TelephonyManager telephonyManager = (TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(this);
        int subId = subscriptionManager.getDefaultDataSubId();
        if(subId>0){ 
        telephonyManager.setDataEnabled (subId, true); 
        }else{ 
        //Log.d(TAG, " initial state ,mobile data haven't been set"); 
        } 
    }
 /*add by lihaizhou at 2015-08-05 by end */

    private boolean isProhibitWlan() {
		     String isOn = ChildMode.getString(this.getContentResolver(),
		    		ChildMode.FORBID_WLAN );
		     if(isOn != null && "1".equals(isOn)){
		    	 return true;
		     }else {
		    	 return false;
			}	 
		}
		private void setProhibitWlan(boolean isEnable) {
			if (isEnable) {
				ChildMode.putString(this.getContentResolver(),ChildMode.FORBID_WLAN ,"1");
			} else {
				ChildMode.putString(this.getContentResolver(),ChildMode.FORBID_WLAN ,"0");
			}
		}
		private boolean isProhibitDeleteSmsmms() {
		     String isOn = ChildMode.getString(this.getContentResolver(),
		    		ChildMode.FORBID_DELETE_MESSAGE );
		     if(isOn != null && "1".equals(isOn)){
		    	 return true;
		     }else {
		    	 return false;
			}	 
		}
		private void setProhibitDeleteSmsmms(boolean isEnable) {
			if (isEnable) {
				ChildMode.putString(this.getContentResolver(),ChildMode.FORBID_DELETE_MESSAGE ,"1");
			} else {
				ChildMode.putString(this.getContentResolver(),ChildMode.FORBID_DELETE_MESSAGE ,"0");
			}
		}
		private boolean isProhibitInstallApp() {
		     String isOn = ChildMode.getString(this.getContentResolver(),
		    		ChildMode.FORBID_INSTALL_APP );
		     if(isOn != null && "1".equals(isOn)){
			
		    	 return true;
		     }else {
			
		    	 return false;
			}	 
		}
		private void setProhibitInstallApp(boolean isEnable) {
			if (isEnable) {
                                SystemProperties.set("persist.sys.isProhibitInstall", "true");
				Log.d("lihaizhou1","persist.sys.isProhibitInstall"+SystemProperties.get("persist.sys.isProhibitInstall", "false"));
				ChildMode.putString(this.getContentResolver(),ChildMode.FORBID_INSTALL_APP ,"1");
			} else {
				SystemProperties.set("persist.sys.isProhibitInstall", "false");
				Log.d("lihaizhou1","persist.sys.isProhibitInstall"+SystemProperties.get("persist.sys.isProhibitInstall", "false"));
				ChildMode.putString(this.getContentResolver(),ChildMode.FORBID_INSTALL_APP ,"0");
			}
		}
		private boolean isProhibitDeleteApp() {
		     String isOn = ChildMode.getString(this.getContentResolver(),
		    		ChildMode.FORBID_UNINSTALL_APP );
		     if(isOn != null && "1".equals(isOn)){
		    	 return true;
		     }else {
		    	 return false;
			}	 
		}
		private void setProhibitDeleteApp(boolean isEnable) {
			if (isEnable) {
                                SystemProperties.set("persist.sys.isProhibit", "true");
				Log.d("lihaizhou1","persist.sys.isProhibit"+SystemProperties.get("persist.sys.isProhibit", "false"));
				ChildMode.putString(this.getContentResolver(),ChildMode.FORBID_UNINSTALL_APP ,"1");
			} else {
				SystemProperties.set("persist.sys.isProhibit", "false");
				Log.d("lihaizhou1","persist.sys.isProhibit"+SystemProperties.get("persist.sys.isProhibit", "false"));
				ChildMode.putString(this.getContentResolver(),ChildMode.FORBID_UNINSTALL_APP ,"0");
			}
		}
		
		public void setupPrefernces()
		  {
			Log.d("zsp","setupPrefernces");
			String strtime = ChildMode.getString(this.getContentResolver(),ChildMode.INTERNET_TIME_RESTRICTION);
			String strtrafic = ChildMode.getString(this.getContentResolver(),ChildMode.INTERNET_TRAFFIC_RESTRICTION);
		    this.mNetworklimiteTimePref = ((CheckExEditorPreference)findPreference("network_limit_time"));
		    this.mNetworklimiteTimePref.setupEditor("is_network_limit_time");

		    this.mNetworklimiteTrafficPref = ((CheckExEditorPreference)findPreference("network_limit_traffic"));
		    this.mNetworklimiteTrafficPref.setupEditor("is_network_traffic_traffic");


		  }
		  private void initnetwork(){
			NetTrafficUtils.initnetwork(this);	
		}
		
		private void cancelnetwork(){
			NetTrafficUtils.cancelnetwork(this);	
		}
		public void turnOffFlashLight(){
            Intent intent = new Intent("com.huawei.flashlight.action.FlashLightService");
            Bundle bundle = new Bundle();
            int op  = 0;;
            try {
                Settings.System.putInt(getContentResolver(),"flashlight_current_state", op);
            } catch (Exception e) {
                Log.d("ChildModeSetting", "sendFlashlight putInt  flashlight_current_state exception:" + op);
            }
            bundle.putInt("status", op);
            intent.putExtras(bundle);
            Log.d("ChildModeSetting", "sendFlashlight intent :" + intent);
            startService(intent);
		}
        public void updateAppLimit(boolean enable,String tag){
            boolean isAppBlackListOn =ChildMode.isAppBlackListOn(getContentResolver());
            boolean isChildModeOn = ChildMode.isChildModeOn(getContentResolver());
            Intent i = new Intent("com.settings.childmode.appdisable.switch");
            boolean applimitEnable = true;
            if (isAppBlackListOn && isChildModeOn) {
                applimitEnable = true;
            }else {
                applimitEnable = false;
            }
            i.putExtra("enable", applimitEnable);
            sendBroadcast(i);
            int count=0;
            ActivityManager am = (ActivityManager)getSystemService("activity");
            List<String> appList = new ArrayList<String>();
            Cursor c = getContentResolver().query(ChildMode.APP_CONTENT_URI, null, null, null, null);
            if (c != null){
              try{
                int k = c.getColumnIndex("package_name");
                while (c.moveToNext())
                    appList.add(c.getString(k));
                 }finally{
                  c.close();
                }
            }
            Iterator<String> it=appList.iterator();
            count=appList.size();
            if (count==0) {
                return;
            }
            count = getAppConuntByName(appList);
            if ("setChildModeOn".equals(tag)) {
                if (enable && isAppBlackListOn) {
                    while(it.hasNext()){
                        am.forceStopPackage(it.next());
                    }
                    Toast.makeText(this, getString(R.string.apps_disabled, count), Toast.LENGTH_SHORT).show();
                }else if (!enable && isAppBlackListOn){
                    Toast.makeText(this, getString(R.string.apps_enabled, count), Toast.LENGTH_SHORT).show();
                }
            }else {
                if (enable) {
                    while(it.hasNext()){
                        am.forceStopPackage(it.next());
                    }
                    Toast.makeText(this, getString(R.string.apps_disabled, count), Toast.LENGTH_SHORT).show();
                }else {
                    Toast.makeText(this, getString(R.string.apps_enabled, count), Toast.LENGTH_SHORT).show();
                }
            }
		}

        private int getAppConuntByName(List<String> names){
            int result = names.size();
            for (int i = 0; i < names.size(); i++) {
                String packageName = names.get(i);
                try {
                    ApplicationInfo appInfo = getPackageManager().getApplicationInfo(packageName, 0);
                } catch (NameNotFoundException e) {
                    result--;
                    ChildMode.removeAppList(getContentResolver(), packageName);
                    Intent intent = new Intent("com.settings.childmode.appdisable.remove");
                    intent.putExtra("package_name", packageName);
                    sendBroadcast(intent);
                }
            }
            return result;
        }
        
    //disable 3rdBrowser when WebsiteAccessRestriction is enabled -begin-
    private void forceStop3rdBrowserApp(Context context){
    
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setData(Uri.parse("http://"));

        List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.GET_INTENT_FILTERS);

        if ((null != list) && (list.size() > 0)) {
            for (ResolveInfo resolveInfo : list) {
                String packageStr = resolveInfo.activityInfo.packageName;
                String acitivtyStr = resolveInfo.activityInfo.name;
                Log.d("ChildModeSetting", "forceStop3rdBrowserApp: packag=" + packageStr + ", acitivty=" + acitivtyStr);
                if (!("com.android.browser".equals(packageStr) && ("com.android.browser.BrowserActivity").equals(acitivtyStr))
                     && !("com.taobao.taobao".equals(packageStr) && ("com.taobao.tao.BrowserActivity").equals(acitivtyStr))) {
                    //The browser is not google browser and no taobao app.
                    Log.d("ChildModeSetting", "forceStop3rdBrowserApp force stop " + packageStr);
                    ActivityManager am = (ActivityManager)context.getSystemService("activity");
                    am.forceStopPackage(packageStr);
                }
            }
        }
    }
    //disable 3rdBrowser when WebsiteAccessRestriction is enabled -end-
}
