/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.server;

import android.database.ContentObserver;
import android.os.BatteryStats;

import com.android.internal.app.IBatteryStats;
import com.android.server.am.BatteryStatsService;
import com.android.server.lights.Light;
import com.android.server.lights.LightsManager;

import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.os.BatteryManager;
import android.os.BatteryManagerInternal;
import android.os.BatteryProperties;
import android.os.Binder;
import android.os.FileUtils;
import android.os.Handler;
import android.os.IBatteryPropertiesListener;
import android.os.IBatteryPropertiesRegistrar;
import android.os.IBinder;
import android.os.DropBoxManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UEventObserver;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.EventLog;
import android.util.Slog;

/*HQ_zhangjing add for sim card pop up tips begin*/
import com.android.internal.telephony.TelephonyIntents;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.PhoneConstants;
import android.net.Uri;
import android.widget.CheckBox;
import android.view.View;
import android.app.AlertDialog;
import android.view.WindowManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.ContentValues;
import android.database.Cursor;
/*HQ_zhangjing add for sim card pop up tips end*/

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Timer;
import java.util.TimerTask;

import com.mediatek.xlog.Xlog;
/**
 * <p>BatteryService monitors the charging status, and charge level of the device
 * battery.  When these values change this service broadcasts the new values
 * to all {@link android.content.BroadcastReceiver IntentReceivers} that are
 * watching the {@link android.content.Intent#ACTION_BATTERY_CHANGED
 * BATTERY_CHANGED} action.</p>
 * <p>The new values are stored in the Intent data and can be retrieved by
 * calling {@link android.content.Intent#getExtra Intent.getExtra} with the
 * following keys:</p>
 * <p>&quot;scale&quot; - int, the maximum value for the charge level</p>
 * <p>&quot;level&quot; - int, charge level, from 0 through &quot;scale&quot; inclusive</p>
 * <p>&quot;status&quot; - String, the current charging status.<br />
 * <p>&quot;health&quot; - String, the current battery health.<br />
 * <p>&quot;present&quot; - boolean, true if the battery is present<br />
 * <p>&quot;icon-small&quot; - int, suggested small icon to use for this state</p>
 * <p>&quot;plugged&quot; - int, 0 if the device is not plugged in; 1 if plugged
 * into an AC power adapter; 2 if plugged in via USB.</p>
 * <p>&quot;voltage&quot; - int, current battery voltage in millivolts</p>
 * <p>&quot;temperature&quot; - int, current battery temperature in tenths of
 * a degree Centigrade</p>
 * <p>&quot;technology&quot; - String, the type of battery installed, e.g. "Li-ion"</p>
 *
 * <p>
 * The battery service may be called by the power manager while holding its locks so
 * we take care to post all outcalls into the activity manager to a handler.
 *
 * FIXME: Ideally the power manager would perform all of its calls into the battery
 * service asynchronously itself.
 * </p>
 */
public final class BatteryService extends SystemService {
    private static final String TAG = BatteryService.class.getSimpleName();

    private static final boolean DEBUG = false;

    private static final int BATTERY_SCALE = 100;    // battery capacity is a percentage

    // Used locally for determining when to make a last ditch effort to log
    // discharge stats before the device dies.
    private int mCriticalBatteryLevel;

    private static final int DUMP_MAX_LENGTH = 24 * 1024;
    private static final String[] DUMPSYS_ARGS = new String[] { "--checkin", "--unplugged" };

    private static final String DUMPSYS_DATA_PATH = "/data/system/";

    // This should probably be exposed in the API, though it's not critical
    private static final int BATTERY_PLUGGED_NONE = 0;

    private final Context mContext;
    private final IBatteryStats mBatteryStats;
    private final Handler mHandler;

    private final Object mLock = new Object();

    private BatteryProperties mBatteryProps;
    private final BatteryProperties mLastBatteryProps = new BatteryProperties();
    private boolean mBatteryLevelCritical;
    private int mLastBatteryStatus;
    private int mLastBatteryStatus_smb;
    private int mLastBatteryHealth;
    private boolean mLastBatteryPresent;
    private boolean mLastBatteryPresent_smb;
    private int mLastBatteryLevel;
    private int mLastBatteryLevel_smb;
    private int mLastBatteryVoltage;
    private int mLastBatteryTemperature;
    private boolean mLastBatteryLevelCritical;

    private int mInvalidCharger;
    private int mLastInvalidCharger;

    private int mLowBatteryWarningLevel;
    private int mLowBatteryCloseWarningLevel;
    private int mShutdownBatteryTemperature;

    private int mPlugType;
    private int mLastPlugType = -1; // Extra state so we can detect first run

    private boolean mBatteryLevelLow;

    private long mDischargeStartTime;
    private int mDischargeStartLevel;

    private boolean mUpdatesStopped;

    private Led mLed;

    private boolean mSentLowBatteryBroadcast = false;

    private boolean mIPOShutdown = false;
    private boolean mIPOed = false;
    private boolean mIPOBoot = false;
    private static final String IPO_POWER_ON  = "android.intent.action.ACTION_BOOT_IPO";
    private static final String IPO_POWER_OFF = "android.intent.action.ACTION_SHUTDOWN_IPO";
    private boolean ipo_led_on = false;
    private boolean ipo_led_off = false;
    private boolean LowLevelFlag = false;
	// True if boot completed occurred.  We keep the battery status update hold until this happens.
    private boolean mBootCompleted = false;
	
	private boolean mSimCardIn[] = {false, false};
	boolean isShowingSimPopDlg = false;
	//HQ_xionghaifeng 20151001 add for remove sim hot swap 
    private boolean mIsAirplaneModeOn = false;
    private boolean mAirplaneModeIsClosing = false;

    //yanqing
    public static final String STOP_SIM_PLUG_OUT_TIMER = "com.android.server.BatteryService.STOP_SIM_PLUG_OUT_TIMER";
    private final int WAIT_FOR_TIME_BEFORE_SHOWDIALOG = 5000; 
    private final int AIRPLANE_CLOSE_NEED_TIME = 40000; 

    private boolean mShouldCancelReboot = false;
    private int rebootForSlotID = -1;

	
    public BatteryService(Context context) {
        super(context);

        mContext = context;
        mHandler = new Handler(true /*async*/);
        mLed = new Led(context, getLocalService(LightsManager.class));
        mBatteryStats = BatteryStatsService.getService();

        mCriticalBatteryLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel);
        mLowBatteryWarningLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryWarningLevel);
        mLowBatteryCloseWarningLevel = mLowBatteryWarningLevel + mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryCloseWarningBump);
        mShutdownBatteryTemperature = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_shutdownBatteryTemperature);

        // watch for invalid charger messages if the invalid_charger switch exists
        if (new File("/sys/devices/virtual/switch/invalid_charger/state").exists()) {
            mInvalidChargerObserver.startObserving(
                    "DEVPATH=/devices/virtual/switch/invalid_charger");
        }
    }

    @Override
    public void onStart() {
        IBinder b = ServiceManager.getService("batteryproperties");
        final IBatteryPropertiesRegistrar batteryPropertiesRegistrar =
                IBatteryPropertiesRegistrar.Stub.asInterface(b);
        try {
            batteryPropertiesRegistrar.registerListener(new BatteryListener());
        } catch (RemoteException e) {
            // Should never happen.
        }
        if (SystemProperties.get("ro.mtk_ipo_support").equals("1")) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(IPO_POWER_ON);
            filter.addAction(IPO_POWER_OFF);
            filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
			filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
			// add by lipeng for APN Pop-up window
			if (SystemProperties.get("ro.hq.choose.default.apn").equals("1")) {
				filter.addAction("HQ_CHOOSE_DEFAULT_APN_FOR_GLOBE");
				filter.addAction("HQ_CHOOSE_DEFAULT_APN_FOR_GID");
				Slog.d("simrecords_lp", "1test2");
			}// end by lipeng
            mContext.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (IPO_POWER_ON.equals(intent.getAction())) {
                        mIPOShutdown = false;
                        mIPOBoot = true;
                        // Let BatteryService to handle low battery warning.
                        mLastBatteryLevel = mLowBatteryWarningLevel + 1;
                        update(mBatteryProps);
                    } else
                        if (IPO_POWER_OFF.equals(intent.getAction())) {
                            mIPOShutdown = true;
                    }

                    if ("HQ_CHOOSE_DEFAULT_APN_FOR_GLOBE".equals(intent.getAction())) {
                    	Slog.d("simrecords_lp", "1test3");
                    	chooseDefaultAPN(intent.getIntExtra("simId",0), intent.getIntExtra("mccmnc",0));
                    	Slog.d("simrecords_lp", "lipeng");
                     }else if("HQ_CHOOSE_DEFAULT_APN_FOR_GID".equals(intent.getAction())){
                    	 chooseDefaultGidAPN(intent.getIntExtra("simId",0), intent.getIntExtra("mccmnc",0));
                     }

/*HQ_zhangjing add for sim card pop up tips begin*/
					if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction()))
					{
                        mShouldCancelReboot = true;
						mIsAirplaneModeOn = intent.getBooleanExtra("state", false);
        				Slog.d("xionghaifeng", "air plane mode is = " + mIsAirplaneModeOn);
                        if(!mIsAirplaneModeOn)
                        {
                            mAirplaneModeIsClosing = true;
                            mHandler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            
                                            mAirplaneModeIsClosing = false;
                                        }
                                    }, AIRPLANE_CLOSE_NEED_TIME);
                            Slog.d("yanqing", "air plane mode is = " + mIsAirplaneModeOn);
                        }
					}
                    else if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                        int slotId = intent.getIntExtra(PhoneConstants.SLOT_KEY, -1);
                        if(slotId == -1) {
                            Slog.w(TAG, "SIM_STATE_CHANGED but slotId is -1");
                            return;
                        }

                        boolean simInserted = hasIccCard(slotId);
                        Slog.d(TAG, "SIM_STATE_CHANGED slotId =" + slotId + ",simInserted = "
                                + simInserted + ", mSimCardIn[slotId]" + mSimCardIn[slotId]);
                        if (mSimCardIn[slotId] == true && simInserted == false) {
                            if (ActivityManagerNative.isSystemReady()) {
	                            if(!isShowingSimPopDlg && !mIsAirplaneModeOn && !mAirplaneModeIsClosing) {
	                		        //showSimPopDlg();
                                    mShouldCancelReboot = false;
                                    rebootForSlotID = slotId;
                                    mHandler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            
                                            showSimPopDlg();
                                        }
                                    }, WAIT_FOR_TIME_BEFORE_SHOWDIALOG);
			                    }
							}
                        } else {
                            mSimCardIn[slotId] = simInserted;
                        }
                        //yanqing
                        Slog.d(TAG, "yanqing SIM_STATE_CHANGED slotId =" + slotId + ",simInserted = "
                            + simInserted + ", mSimCardIn[slotId]" + mSimCardIn[slotId]);
                        if(simInserted && rebootForSlotID == slotId)
                        {
                            mShouldCancelReboot = true;
                            notifyRebootTimerStop();
                        }
                    } 
                }

            }, filter);
        } else {
            IntentFilter filter = new IntentFilter();
            filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
			filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
			// add by lipeng for APN Pop-up window
			if (SystemProperties.get("ro.hq.choose.default.apn").equals("1")) {
				filter.addAction("HQ_CHOOSE_DEFAULT_APN_FOR_GLOBE");
				Slog.d("simrecords_lp", "1test2");
			}// end by lipeng
            mContext.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if ("HQ_CHOOSE_DEFAULT_APN_FOR_GLOBE".equals(intent.getAction())) {
                    	Slog.d("simrecords_lp", "1test3");
                    	chooseDefaultAPN(intent.getIntExtra("simId",0), intent.getIntExtra("mccmnc",0));
                    	Slog.d("simrecords_lp", "lipeng2");
                     }else if("HQ_CHOOSE_DEFAULT_APN_FOR_GID".equals(intent.getAction())){
                    	 chooseDefaultGidAPN(intent.getIntExtra("simId",0), intent.getIntExtra("mccmnc",0));
                     }
					//HQ_xionghaifeng 20151001 add for remove sim hot swap 
					if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED))
					{
                        mShouldCancelReboot = true;
						mIsAirplaneModeOn = intent.getBooleanExtra("state", false);
        				Slog.d("xionghaifeng", "air plane mode is = " + mIsAirplaneModeOn);
                        if(!mIsAirplaneModeOn)
                        {
                            mAirplaneModeIsClosing = true;
                            mHandler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            
                                            mAirplaneModeIsClosing = false;
                                        }
                                    }, AIRPLANE_CLOSE_NEED_TIME);
                            Slog.d("yanqing", "air plane mode is = " + mIsAirplaneModeOn);
                        }
					}
                    else if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) 
					{
                        int slotId = intent.getIntExtra(PhoneConstants.SLOT_KEY, -1);

                        if(slotId == -1) {
                            Slog.w(TAG, "SIM_STATE_CHANGED but slotId is -1");
                            return;
                        }

                        boolean simInserted = hasIccCard(slotId);
                        Slog.d(TAG, "SIM_STATE_CHANGED slotId =" + slotId + ",simInserted = "
                                + simInserted + ", mSimCardIn[slotId]" + mSimCardIn[slotId]);
                        if (mSimCardIn[slotId] == true && simInserted == false) {
                            if (ActivityManagerNative.isSystemReady()) {
	                            if(!isShowingSimPopDlg && !mIsAirplaneModeOn && !mAirplaneModeIsClosing) {
	                		        //showSimPopDlg();
                                    mShouldCancelReboot = false;
                                    rebootForSlotID = slotId;
                                    mHandler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            showSimPopDlg();
                                        }
                                    }, WAIT_FOR_TIME_BEFORE_SHOWDIALOG);
			                    }
							}
                        } else {
                            mSimCardIn[slotId] = simInserted;
                        }
                        //yanqing
                        Slog.d(TAG, "yanqing SIM_STATE_CHANGED slotId =" + slotId + ",simInserted = "
                            + simInserted + ", mSimCardIn[slotId]" + mSimCardIn[slotId]);
                        if(simInserted && rebootForSlotID == slotId)
                        {
                            mShouldCancelReboot = true;
                            notifyRebootTimerStop();
                        }
                    } 
                }
/*HQ_zhangjing add for sim card pop up tips end*/
            }, filter);
        }

        publishBinderService("battery", new BinderService());
        publishLocalService(BatteryManagerInternal.class, new LocalService());
    }

  //add by lipeng for APN Pop-up window
    public static final String PREFERRED_APN_URI = "content://telephony/carriers/preferapn";
    public static final Uri CONTENT_URI = Uri.parse("content://telephony/carriers");
	public static final Uri URI_LIST[] = {
			CONTENT_URI,
			CONTENT_URI };

	public static final Uri PREFERRED_URI_LIST[] = {
			Uri.parse(PREFERRED_APN_URI),
			Uri.parse(PREFERRED_APN_URI)};
	
    //end by lipeng
    public void chooseDefaultAPN(int simId, int mccmnc) {
    	Slog.d("simrecords_lp", "1test4");
	    final int slot = simId;
	    final String[] apnName = new String[]{"myGlobe Internet"};
	    final String[] apnName52501 = new String[]{"e-ideas"};
        final String[] apnName52505 = new String[]{"shwap"};
        final String[] apnName23415 = new String[]{"pp.vodafone.co.uk"};//apnName23415[0]
	    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext);
	    dialogBuilder.setCancelable(false);
	    dialogBuilder.setTitle("Choose your default APN");

		if(mccmnc==52501){	
			Slog.d(TAG, "mccmnc==52501");
		    dialogBuilder.setSingleChoiceItems(new String[]{"Singtel (Postpaid)","Singtel (Prepaid)"},0,
			new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog,int which){
					if (which == 0) {
						Slog.d(TAG, "0");
						apnName52501[0] = "e-ideas";//"SingTel (Postpaid)";//"myGlobe Internet";
					}else {
						apnName52501[0] = "hicard";//"SingTel (Prepaid)";//"myGlobe INET";
					}
					Slog.d(TAG, "apnName52501 = "+apnName52501[0]);
			    }	
			});
			dialogBuilder.setPositiveButton(android.R.string.ok, 
				new AlertDialog.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
					Cursor cursor = mContext.getContentResolver().query(URI_LIST[slot],new String[]{"_id"},"numeric = '52501' and apn = '"+apnName52501[0]+"'",null,null);
					
					Slog.d(TAG, "1	:	size = "+ cursor.getCount());
					if (cursor.moveToFirst()) {
						Slog.d(TAG, "2");
						ContentValues values = new ContentValues();
						values.put("apn_id",cursor.getString(0));
						mContext.getContentResolver().update(PREFERRED_URI_LIST[slot],values,null,null);
					}
					if (null != cursor) {
						Slog.d(TAG, "3");
						cursor.close();
					} 
				}
			});			
		}else if(mccmnc==52505){
            //maheling 2015.12.14 HQ01565385
            Slog.d(TAG, "mccmnc==52505");
            dialogBuilder.setSingleChoiceItems(new String[]{"StarHub (Postpaid)","StarHub (Prepaid)"},0,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int which){
                            if (which == 0) {
                                Slog.d(TAG, "0");
                                apnName52505[0] = "shwap";
                            }else{
                                apnName52505[0] = "shppd";
                            }
                            Slog.d(TAG, "apnName52505 = "+apnName52505[0]);
                        }
                    });
            dialogBuilder.setPositiveButton(android.R.string.ok,
                    new AlertDialog.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Cursor cursor = mContext.getContentResolver().query(URI_LIST[slot],new String[]{"_id"},"numeric = '52505' and apn = '"+apnName52505[0]+"'",null,null);

                            Slog.d(TAG, "1	:	size = "+ cursor.getCount());
                            if (cursor.moveToFirst()) {
                                Slog.d(TAG, "2");
                                ContentValues values = new ContentValues();
                                values.put("apn_id",cursor.getString(0));
                                mContext.getContentResolver().update(PREFERRED_URI_LIST[slot],values,null,null);
                            }
                            if (null != cursor) {
                                Slog.d(TAG, "3");
                                cursor.close();
                            }
                        }
                    });
        }else if(mccmnc==23415){
            //maheling 2015.12.14 HQ01565385
            Slog.d(TAG, "mccmnc==23415");
            dialogBuilder.setSingleChoiceItems(new String[]{"PAYG WAP","Contract WAP"},0,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int which){
                            if (which == 0) {
                                Slog.d(TAG, "0");
                                apnName23415[0] = "pp.vodafone.co.uk";
                            }else{
                                apnName23415[0] = "wap.vodafone.co.uk";
                            }
                            Slog.d(TAG, "apnName23415 = "+apnName23415[0]);
                        }
                    });
            dialogBuilder.setPositiveButton(android.R.string.ok,
                    new AlertDialog.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Cursor cursor = mContext.getContentResolver().query(URI_LIST[slot],new String[]{"_id"},"numeric = '23415' and apn = '"+apnName23415[0]+"'",null,null);

                            Slog.d(TAG, "1	:	size = "+ cursor.getCount());
                            if (cursor.moveToFirst()) {
                                Slog.d(TAG, "2");
                                ContentValues values = new ContentValues();
                                values.put("apn_id",cursor.getString(0));
                                mContext.getContentResolver().update(PREFERRED_URI_LIST[slot],values,null,null);
                            }
                            if (null != cursor) {
                                Slog.d(TAG, "3");
                                cursor.close();
                            }
                        }
                    });
        }else{
			Slog.d("simrecords_lp", "test4");
			dialogBuilder.setSingleChoiceItems(new String[]{"Globe Postpaid","Globe Prepaid"},0,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,int which){
						if (which == 0) {
							Slog.d(TAG, "0");
						    apnName[0] = "myGlobe Internet";
						}else {
						    apnName[0] = "myGlobe INET";
						}
						Slog.d(TAG, "apnName = "+apnName[0]);
					}	
				});
		    dialogBuilder.setPositiveButton(android.R.string.ok, 
				new AlertDialog.OnClickListener() {
	            	public void onClick(DialogInterface dialog, int which) {
						Cursor cursor = mContext.getContentResolver().query(URI_LIST[slot],new String[]{"_id"},"numeric = '51502' and name = '"+apnName[0]+"'",null,null);
						
						Slog.d(TAG, "1	:	size = "+ cursor.getCount());
						if (cursor.moveToFirst()) {
							Slog.d(TAG, "2");
						    ContentValues values = new ContentValues();
						    values.put("apn_id",cursor.getString(0));
						    mContext.getContentResolver().update(PREFERRED_URI_LIST[slot],values,null,null);
						}
						if (null != cursor) {
							Slog.d(TAG, "3");
						    cursor.close();
						} 
					}
			});
		}	
		
	    AlertDialog dialog = dialogBuilder.create();
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
	    dialog.show();
	}
    
    public void chooseDefaultGidAPN(int simId, int mccmnc) {  //HQ_CHOOSE_DEFAULT_APN_FOR_GID
    	Slog.d("simrecords_lp", "1test4");
	    final int slot = simId;
        final String[] apnName23415 = new String[]{"payg.talkmobile.co.uk"};
	    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext);
	    dialogBuilder.setCancelable(false);
	    dialogBuilder.setTitle("Choose your default APN");

		if(mccmnc==52501){	
			Slog.d(TAG, "mccmnc==23415");
		    dialogBuilder.setSingleChoiceItems(new String[]{"Talkmobile Prepay","Talkmobile Contract"},0,
			new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog,int which){
					if (which == 0) {
						Slog.d(TAG, "0");
						apnName23415[0] = "payg.talkmobile.co.uk";//"SingTel (Postpaid)";//"myGlobe Internet";
					}else {
						apnName23415[0] = "talkmobile.co.uk";//"SingTel (Prepaid)";//"myGlobe INET";
					}
					Slog.d(TAG, "apnName23415 = "+apnName23415[0]);
			    }	
			});
			dialogBuilder.setPositiveButton(android.R.string.ok, 
				new AlertDialog.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
					Cursor cursor = mContext.getContentResolver().query(URI_LIST[slot],new String[]{"_id"},"numeric = '23415' and apn = '"+apnName23415[0]+"'",null,null);
					
					Slog.d(TAG, "1	:	size = "+ cursor.getCount());
					if (cursor.moveToFirst()) {
						Slog.d(TAG, "2");
						ContentValues values = new ContentValues();
						values.put("apn_id",cursor.getString(0));
						mContext.getContentResolver().update(PREFERRED_URI_LIST[slot],values,null,null);
					}
					if (null != cursor) {
						Slog.d(TAG, "3");
						cursor.close();
					} 
				}
			});			
		}


	    AlertDialog dialog = dialogBuilder.create();
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
	    dialog.show();
	}
    
    
    
    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_ACTIVITY_MANAGER_READY) {
            // check our power situation now that it is safe to display the shutdown dialog.
            synchronized (mLock) {
                mBootCompleted = true;
               //HQ_xionghaifeng 20151001 add for remove sim hot swap 
                mIsAirplaneModeOn = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) != 0;

                ContentObserver obs = new ContentObserver(mHandler) {
                    @Override
                    public void onChange(boolean selfChange) {
                        synchronized (mLock) {
                            updateBatteryWarningLevelLocked();
                        }
                    }
                };
                final ContentResolver resolver = mContext.getContentResolver();
                resolver.registerContentObserver(Settings.Global.getUriFor(
                        Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL),
                        false, obs, UserHandle.USER_ALL);
                updateBatteryWarningLevelLocked();
            }
        }
    }

    private void updateBatteryWarningLevelLocked() {
        final ContentResolver resolver = mContext.getContentResolver();
        int defWarnLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryWarningLevel);
        mLowBatteryWarningLevel = Settings.Global.getInt(resolver,
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, defWarnLevel);
        if (mLowBatteryWarningLevel == 0) {
            mLowBatteryWarningLevel = defWarnLevel;
        }
        if (mLowBatteryWarningLevel < mCriticalBatteryLevel) {
            mLowBatteryWarningLevel = mCriticalBatteryLevel;
        }
        mLowBatteryCloseWarningLevel = mLowBatteryWarningLevel + mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryCloseWarningBump);
        processValuesLocked(true);
    }

    private boolean isPoweredLocked(int plugTypeSet) {
        // assume we are powered if battery state is unknown so
        // the "stay on while plugged in" option will work.
        if (mBatteryProps.batteryStatus == BatteryManager.BATTERY_STATUS_UNKNOWN) {
            return true;
        }
        if ((plugTypeSet & BatteryManager.BATTERY_PLUGGED_AC) != 0 && mBatteryProps.chargerAcOnline) {
            return true;
        }
        if ((plugTypeSet & BatteryManager.BATTERY_PLUGGED_USB) != 0 && mBatteryProps.chargerUsbOnline) {
            return true;
        }
        if ((plugTypeSet & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 && mBatteryProps.chargerWirelessOnline) {
            return true;
        }
        return false;
    }

    private boolean shouldSendBatteryLowLocked() {
        final boolean plugged = mPlugType != BATTERY_PLUGGED_NONE;
        final boolean oldPlugged = mLastPlugType != BATTERY_PLUGGED_NONE;

        /* The ACTION_BATTERY_LOW broadcast is sent in these situations:
         * - is just un-plugged (previously was plugged) and battery level is
         *   less than or equal to WARNING, or
         * - is not plugged and battery level falls to WARNING boundary
         *   (becomes <= mLowBatteryWarningLevel).
         */
        return !plugged
                && mBatteryProps.batteryStatus != BatteryManager.BATTERY_STATUS_UNKNOWN
                && mBatteryProps.batteryLevel <= mLowBatteryWarningLevel
                && (oldPlugged || mLastBatteryLevel > mLowBatteryWarningLevel);
    }

    private void shutdownIfNoPowerLocked() {
        // shut down gracefully if our battery is critically low and we are not powered.
        // wait until the system has booted before attempting to display the shutdown dialog.
        if (mBatteryProps.batteryLevel == 0 && !isPoweredLocked(BatteryManager.BATTERY_PLUGGED_ANY)) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (ActivityManagerNative.isSystemReady()) {
                        if (SystemProperties.get("ro.mtk_ipo_support").equals("1")) {
                            SystemProperties.set("sys.ipo.battlow","1");
                        }
                        Intent intent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
                        intent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivityAsUser(intent, UserHandle.CURRENT);
                    }
                }
            });
        }
    }

    private void shutdownIfOverTempLocked() {
        // shut down gracefully if temperature is too high (> 68.0C by default)
        // wait until the system has booted before attempting to display the
        // shutdown dialog.
        if (mBatteryProps.batteryTemperature > mShutdownBatteryTemperature) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (ActivityManagerNative.isSystemReady()) {
                        Intent intent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
                        intent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivityAsUser(intent, UserHandle.CURRENT);
                    }
                }
            });
        }
    }

    private void update(BatteryProperties props) {
        synchronized (mLock) {
            if (!mUpdatesStopped) {
                mBatteryProps = props;
                if (SystemProperties.get("ro.mtk_ipo_support").equals("1")) {
                    if (mIPOShutdown)
                        return;
                }
                // Process the new values.
                if (mBootCompleted)
                processValuesLocked(false);
            } else {
                mLastBatteryProps.set(props);
            }
        }
    }

/*HQ_zhangjing add for sim card pop up tips begin*/
	void showSimPopDlg(){
        Slog.d(TAG, "showSimPopDlg yanqing");
        if(mShouldCancelReboot)
        {
            Slog.d(TAG, "showSimPopDlg yanqing mShouldCancelReboot true");
            return;
        }
        isShowingSimPopDlg = true;
        Intent intent = new Intent("android.intent.action.SIM_PLUG_OUT_ALERT");
        intent.setClassName("com.android.settings",
                "com.android.settings.sim.SimPlugOutAlert");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivityAsUser(intent, UserHandle.CURRENT);
	}

	private boolean hasIccCard(int slotId) {
        boolean bReturn =  false;
        TelephonyManager tpMgr = (TelephonyManager)
                mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (tpMgr != null) {
            bReturn = tpMgr.hasIccCard(slotId);
            Slog.d(TAG, "hasIccCard = " + bReturn);
            return bReturn;
        }
        return false;
    }
	/*HQ_zhangjing add for sim card pop up tips end*/	

    private void processValuesLocked(boolean force) {
        boolean logOutlier = false;
        long dischargeDuration = 0;

        mBatteryLevelCritical = (mBatteryProps.batteryLevel <= mCriticalBatteryLevel);
        if (mBatteryProps.chargerAcOnline) {
            mPlugType = BatteryManager.BATTERY_PLUGGED_AC;
        } else if (mBatteryProps.chargerUsbOnline) {
            mPlugType = BatteryManager.BATTERY_PLUGGED_USB;
        } else if (mBatteryProps.chargerWirelessOnline) {
            mPlugType = BatteryManager.BATTERY_PLUGGED_WIRELESS;
        } else {
            mPlugType = BATTERY_PLUGGED_NONE;
        }

        /// M: Add for DUAL_INPUT_CHARGER_SUPPORT @{
        if (SystemProperties.get("ro.mtk_diso_support").equals("true")){
            if (mBatteryProps.chargerAcOnline && mBatteryProps.chargerUsbOnline) {
                mPlugType = BatteryManager.BATTERY_PLUGGED_AC | BatteryManager.BATTERY_PLUGGED_USB;
            }
        }
        /// M: @}

        if (DEBUG) {
            Slog.d(TAG, "Processing new values: "
                    + "chargerAcOnline=" + mBatteryProps.chargerAcOnline
                    + ", chargerUsbOnline=" + mBatteryProps.chargerUsbOnline
                    + ", chargerWirelessOnline=" + mBatteryProps.chargerWirelessOnline
                    + ", batteryStatus=" + mBatteryProps.batteryStatus
                    + ", batteryHealth=" + mBatteryProps.batteryHealth
                    + ", batteryPresent=" + mBatteryProps.batteryPresent
                    + ", batteryLevel=" + mBatteryProps.batteryLevel
                    + ", batteryTechnology=" + mBatteryProps.batteryTechnology
                    + ", batteryVoltage=" + mBatteryProps.batteryVoltage
                    + ", batteryTemperature=" + mBatteryProps.batteryTemperature
                    + ", mBatteryLevelCritical=" + mBatteryLevelCritical
                    + ", mPlugType=" + mPlugType);
        }
	if (mLastBatteryVoltage != mBatteryProps.batteryVoltage) {
		Xlog.d(TAG, "mBatteryVoltage=" + mBatteryProps.batteryVoltage + ", batteryLevel=" + mBatteryProps.batteryLevel_smb);
	}
        // Update the battery LED
        mLed.updateLightsLocked();

        // Let the battery stats keep track of the current level.
        try {
            mBatteryStats.setBatteryState(mBatteryProps.batteryStatus, mBatteryProps.batteryHealth,
                    mPlugType, mBatteryProps.batteryLevel, mBatteryProps.batteryTemperature,
                    mBatteryProps.batteryVoltage);
        } catch (RemoteException e) {
            // Should never happen.
        }

        shutdownIfNoPowerLocked();
        shutdownIfOverTempLocked();

        if (force || (mBatteryProps.batteryStatus != mLastBatteryStatus ||
                mBatteryProps.batteryStatus_smb != mLastBatteryStatus_smb ||
                mBatteryProps.batteryHealth != mLastBatteryHealth ||
                mBatteryProps.batteryPresent != mLastBatteryPresent ||
		mBatteryProps.batteryPresent_smb != mLastBatteryPresent_smb ||
                mBatteryProps.batteryLevel != mLastBatteryLevel ||
		mBatteryProps.batteryLevel_smb != mLastBatteryLevel_smb ||
                mPlugType != mLastPlugType ||
                mBatteryProps.batteryVoltage != mLastBatteryVoltage ||
                mBatteryProps.batteryTemperature != mLastBatteryTemperature ||
                mInvalidCharger != mLastInvalidCharger)) {

            if (mPlugType != mLastPlugType) {
                if (mLastPlugType == BATTERY_PLUGGED_NONE) {
                    // discharging -> charging

                    // There's no value in this data unless we've discharged at least once and the
                    // battery level has changed; so don't log until it does.
                    if (mDischargeStartTime != 0 && mDischargeStartLevel != mBatteryProps.batteryLevel) {
                        dischargeDuration = SystemClock.elapsedRealtime() - mDischargeStartTime;
                        logOutlier = true;
                        EventLog.writeEvent(EventLogTags.BATTERY_DISCHARGE, dischargeDuration,
                                mDischargeStartLevel, mBatteryProps.batteryLevel);
                        // make sure we see a discharge event before logging again
                        mDischargeStartTime = 0;
                    }
                } else if (mPlugType == BATTERY_PLUGGED_NONE) {
                    // charging -> discharging or we just powered up
                    mDischargeStartTime = SystemClock.elapsedRealtime();
                    mDischargeStartLevel = mBatteryProps.batteryLevel;
                }
            }
            if (mBatteryProps.batteryStatus != mLastBatteryStatus ||
                    mBatteryProps.batteryHealth != mLastBatteryHealth ||
                    mBatteryProps.batteryPresent != mLastBatteryPresent ||
                    mPlugType != mLastPlugType) {
                EventLog.writeEvent(EventLogTags.BATTERY_STATUS,
                        mBatteryProps.batteryStatus, mBatteryProps.batteryHealth, mBatteryProps.batteryPresent ? 1 : 0,
                        mPlugType, mBatteryProps.batteryTechnology);
            }
            if (mBatteryProps.batteryLevel != mLastBatteryLevel) {
                // Don't do this just from voltage or temperature changes, that is
                // too noisy.
                EventLog.writeEvent(EventLogTags.BATTERY_LEVEL,
                        mBatteryProps.batteryLevel, mBatteryProps.batteryVoltage, mBatteryProps.batteryTemperature);
            }
            if (mBatteryLevelCritical && !mLastBatteryLevelCritical &&
                    mPlugType == BATTERY_PLUGGED_NONE) {
                // We want to make sure we log discharge cycle outliers
                // if the battery is about to die.
                dischargeDuration = SystemClock.elapsedRealtime() - mDischargeStartTime;
                logOutlier = true;
            }

            if (!mBatteryLevelLow) {
                // Should we now switch in to low battery mode?
                if (mPlugType == BATTERY_PLUGGED_NONE
                        && mBatteryProps.batteryLevel <= mLowBatteryWarningLevel) {
                    mBatteryLevelLow = true;
                }
            } else {
                // Should we now switch out of low battery mode?
                if (mPlugType != BATTERY_PLUGGED_NONE) {
                    mBatteryLevelLow = false;
                } else if (mBatteryProps.batteryLevel >= mLowBatteryCloseWarningLevel)  {
                    mBatteryLevelLow = false;
                } else if (force && mBatteryProps.batteryLevel >= mLowBatteryWarningLevel) {
                    // If being forced, the previous state doesn't matter, we will just
                    // absolutely check to see if we are now above the warning level.
                    mBatteryLevelLow = false;
                }
            }

            sendIntentLocked();

            // Separate broadcast is sent for power connected / not connected
            // since the standard intent will not wake any applications and some
            // applications may want to have smart behavior based on this.
            if (mPlugType != 0 && mLastPlugType == 0) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Intent statusIntent = new Intent(Intent.ACTION_POWER_CONNECTED);
                        statusIntent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                        mContext.sendBroadcastAsUser(statusIntent, UserHandle.ALL);
                    }
                });
            }
            else if (mPlugType == 0 && mLastPlugType != 0) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Intent statusIntent = new Intent(Intent.ACTION_POWER_DISCONNECTED);
                        statusIntent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                        mContext.sendBroadcastAsUser(statusIntent, UserHandle.ALL);
                    }
                });
            }

            if (shouldSendBatteryLowLocked()) {
                mSentLowBatteryBroadcast = true;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Intent statusIntent = new Intent(Intent.ACTION_BATTERY_LOW);
                        statusIntent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                        mContext.sendBroadcastAsUser(statusIntent, UserHandle.ALL);
                    }
                });
            } else if (mSentLowBatteryBroadcast && mLastBatteryLevel >= mLowBatteryCloseWarningLevel) {
                mSentLowBatteryBroadcast = false;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Intent statusIntent = new Intent(Intent.ACTION_BATTERY_OKAY);
                        statusIntent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                        mContext.sendBroadcastAsUser(statusIntent, UserHandle.ALL);
                    }
                });
            }

            if ( mBatteryProps.batteryStatus != mLastBatteryStatus &&
                    mBatteryProps.batteryStatus == BatteryManager.BATTERY_STATUS_CMD_DISCHARGING ) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
	                final String ACTION_IGNORE_DATA_USAGE_ALERT =
	                "android.intent.action.IGNORE_DATA_USAGE_ALERT";

	                Xlog.d(TAG, "sendBroadcast ACTION_IGNORE_DATA_USAGE_ALERT");
	                Intent statusIntent = new Intent(ACTION_IGNORE_DATA_USAGE_ALERT);
	                statusIntent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
	                mContext.sendBroadcastAsUser(statusIntent, UserHandle.ALL);
	            }
	        });
            }

            // Update the battery LED
            // mLed.updateLightsLocked();

            // This needs to be done after sendIntent() so that we get the lastest battery stats.
            if (logOutlier && dischargeDuration != 0) {
                logOutlierLocked(dischargeDuration);
            }

            mLastBatteryStatus = mBatteryProps.batteryStatus;
			mLastBatteryStatus_smb = mBatteryProps.batteryStatus_smb;
            mLastBatteryHealth = mBatteryProps.batteryHealth;
            mLastBatteryPresent = mBatteryProps.batteryPresent;
			mLastBatteryPresent_smb = mBatteryProps.batteryPresent_smb;
            mLastBatteryLevel = mBatteryProps.batteryLevel;
			mLastBatteryLevel_smb = mBatteryProps.batteryLevel_smb;
            mLastPlugType = mPlugType;
            mLastBatteryVoltage = mBatteryProps.batteryVoltage;
            mLastBatteryTemperature = mBatteryProps.batteryTemperature;
            mLastBatteryLevelCritical = mBatteryLevelCritical;
            mLastInvalidCharger = mInvalidCharger;
        }
    }

    private void sendIntentLocked() {
        //  Pack up the values and broadcast them to everyone
        final Intent intent = new Intent(Intent.ACTION_BATTERY_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY
                | Intent.FLAG_RECEIVER_REPLACE_PENDING);

        int icon = getIconLocked(mBatteryProps.batteryLevel);

        intent.putExtra(BatteryManager.EXTRA_STATUS, mBatteryProps.batteryStatus);
	intent.putExtra(BatteryManager.EXTRA_STATUS_SMARTBOOK, mBatteryProps.batteryStatus_smb);
        intent.putExtra(BatteryManager.EXTRA_HEALTH, mBatteryProps.batteryHealth);
        intent.putExtra(BatteryManager.EXTRA_PRESENT, mBatteryProps.batteryPresent);
	intent.putExtra(BatteryManager.EXTRA_PRESENT_SMARTBOOK, mBatteryProps.batteryPresent_smb);
        intent.putExtra(BatteryManager.EXTRA_LEVEL, mBatteryProps.batteryLevel);
	intent.putExtra(BatteryManager.EXTRA_LEVEL_SMARTBOOK, mBatteryProps.batteryLevel_smb);
        intent.putExtra(BatteryManager.EXTRA_SCALE, BATTERY_SCALE);
        intent.putExtra(BatteryManager.EXTRA_ICON_SMALL, icon);
        intent.putExtra(BatteryManager.EXTRA_PLUGGED, mPlugType);
        intent.putExtra(BatteryManager.EXTRA_VOLTAGE, mBatteryProps.batteryVoltage);
        intent.putExtra(BatteryManager.EXTRA_TEMPERATURE, mBatteryProps.batteryTemperature);
        intent.putExtra(BatteryManager.EXTRA_TECHNOLOGY, mBatteryProps.batteryTechnology);
        intent.putExtra(BatteryManager.EXTRA_INVALID_CHARGER, mInvalidCharger);

        if (DEBUG) {
            Slog.d(TAG, "Sending ACTION_BATTERY_CHANGED.  level:" + mBatteryProps.batteryLevel +
                    ", scale:" + BATTERY_SCALE + ", status:" + mBatteryProps.batteryStatus +
                    ", health:" + mBatteryProps.batteryHealth +  ", present:" + mBatteryProps.batteryPresent +
                    ", voltage: " + mBatteryProps.batteryVoltage +
                    ", temperature: " + mBatteryProps.batteryTemperature +
                    ", technology: " + mBatteryProps.batteryTechnology +
                    ", AC powered:" + mBatteryProps.chargerAcOnline + ", USB powered:" + mBatteryProps.chargerUsbOnline +
                    ", Wireless powered:" + mBatteryProps.chargerWirelessOnline +
                    ", icon:" + icon  + ", invalid charger:" + mInvalidCharger +
			", status_smb:" + mBatteryProps.batteryStatus_smb + ", present_smb:" +
                    mBatteryProps.batteryPresent_smb + ",level_smb:" + mBatteryProps.batteryLevel_smb);
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                ActivityManagerNative.broadcastStickyIntent(intent, null, UserHandle.USER_ALL);
            }
        });
    }

    private void logBatteryStatsLocked() {
        IBinder batteryInfoService = ServiceManager.getService(BatteryStats.SERVICE_NAME);
        if (batteryInfoService == null) return;

        DropBoxManager db = (DropBoxManager) mContext.getSystemService(Context.DROPBOX_SERVICE);
        if (db == null || !db.isTagEnabled("BATTERY_DISCHARGE_INFO")) return;

        File dumpFile = null;
        FileOutputStream dumpStream = null;
        try {
            // dump the service to a file
            dumpFile = new File(DUMPSYS_DATA_PATH + BatteryStats.SERVICE_NAME + ".dump");
            dumpStream = new FileOutputStream(dumpFile);
            batteryInfoService.dump(dumpStream.getFD(), DUMPSYS_ARGS);
            FileUtils.sync(dumpStream);

            // add dump file to drop box
            db.addFile("BATTERY_DISCHARGE_INFO", dumpFile, DropBoxManager.IS_TEXT);
        } catch (RemoteException e) {
            Slog.e(TAG, "failed to dump battery service", e);
        } catch (IOException e) {
            Slog.e(TAG, "failed to write dumpsys file", e);
        } finally {
            // make sure we clean up
            if (dumpStream != null) {
                try {
                    dumpStream.close();
                } catch (IOException e) {
                    Slog.e(TAG, "failed to close dumpsys output stream");
                }
            }
            if (dumpFile != null && !dumpFile.delete()) {
                Slog.e(TAG, "failed to delete temporary dumpsys file: "
                        + dumpFile.getAbsolutePath());
            }
        }
    }

    private void logOutlierLocked(long duration) {
        ContentResolver cr = mContext.getContentResolver();
        String dischargeThresholdString = Settings.Global.getString(cr,
                Settings.Global.BATTERY_DISCHARGE_THRESHOLD);
        String durationThresholdString = Settings.Global.getString(cr,
                Settings.Global.BATTERY_DISCHARGE_DURATION_THRESHOLD);

        if (dischargeThresholdString != null && durationThresholdString != null) {
            try {
                long durationThreshold = Long.parseLong(durationThresholdString);
                int dischargeThreshold = Integer.parseInt(dischargeThresholdString);
                if (duration <= durationThreshold &&
                        mDischargeStartLevel - mBatteryProps.batteryLevel >= dischargeThreshold) {
                    // If the discharge cycle is bad enough we want to know about it.
                    logBatteryStatsLocked();
                }
                if (DEBUG) Slog.v(TAG, "duration threshold: " + durationThreshold +
                        " discharge threshold: " + dischargeThreshold);
                if (DEBUG) Slog.v(TAG, "duration: " + duration + " discharge: " +
                        (mDischargeStartLevel - mBatteryProps.batteryLevel));
            } catch (NumberFormatException e) {
                Slog.e(TAG, "Invalid DischargeThresholds GService string: " +
                        durationThresholdString + " or " + dischargeThresholdString);
                return;
            }
        }
    }

    private int getIconLocked(int level) {
        if (mBatteryProps.batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING) {
            return com.android.internal.R.drawable.stat_sys_battery_charge;
        } else if (mBatteryProps.batteryStatus == BatteryManager.BATTERY_STATUS_DISCHARGING) {
            return com.android.internal.R.drawable.stat_sys_battery;
        } else if (mBatteryProps.batteryStatus == BatteryManager.BATTERY_STATUS_NOT_CHARGING
                || mBatteryProps.batteryStatus == BatteryManager.BATTERY_STATUS_FULL) {
            if (isPoweredLocked(BatteryManager.BATTERY_PLUGGED_ANY)
                    && mBatteryProps.batteryLevel >= 100) {
                return com.android.internal.R.drawable.stat_sys_battery_charge;
            } else {
                return com.android.internal.R.drawable.stat_sys_battery;
            }
        } else {
            return com.android.internal.R.drawable.stat_sys_battery_unknown;
        }
    }

    private void dumpInternal(PrintWriter pw, String[] args) {
        synchronized (mLock) {
            if (args == null || args.length == 0 || "-a".equals(args[0])) {
                pw.println("Current Battery Service state:");
                if (mUpdatesStopped) {
                    pw.println("  (UPDATES STOPPED -- use 'reset' to restart)");
                }
                pw.println("  AC powered: " + mBatteryProps.chargerAcOnline);
                pw.println("  USB powered: " + mBatteryProps.chargerUsbOnline);
                pw.println("  Wireless powered: " + mBatteryProps.chargerWirelessOnline);
                pw.println("  status: " + mBatteryProps.batteryStatus);
		pw.println("  status: " + mBatteryProps.batteryStatus_smb);
                pw.println("  health: " + mBatteryProps.batteryHealth);
                pw.println("  present: " + mBatteryProps.batteryPresent);
		pw.println("  present: " + mBatteryProps.batteryPresent_smb);
                pw.println("  level: " + mBatteryProps.batteryLevel);
		pw.println("  level: " + mBatteryProps.batteryLevel_smb);
                pw.println("  scale: " + BATTERY_SCALE);
                pw.println("  voltage: " + mBatteryProps.batteryVoltage);
                pw.println("  temperature: " + mBatteryProps.batteryTemperature);
                pw.println("  technology: " + mBatteryProps.batteryTechnology);
            } else if (args.length == 3 && "set".equals(args[0])) {
                String key = args[1];
                String value = args[2];
                try {
                    if (!mUpdatesStopped) {
                        mLastBatteryProps.set(mBatteryProps);
                    }
                    boolean update = true;
                    if ("ac".equals(key)) {
                        mBatteryProps.chargerAcOnline = Integer.parseInt(value) != 0;
                    } else if ("usb".equals(key)) {
                        mBatteryProps.chargerUsbOnline = Integer.parseInt(value) != 0;
                    } else if ("wireless".equals(key)) {
                        mBatteryProps.chargerWirelessOnline = Integer.parseInt(value) != 0;
                    } else if ("status".equals(key)) {
                        mBatteryProps.batteryStatus = Integer.parseInt(value);
			} else if ("status_smb".equals(key)) {
                        	mBatteryProps.batteryStatus_smb = Integer.parseInt(value);
                    } else if ("level".equals(key)) {
                        mBatteryProps.batteryLevel = Integer.parseInt(value);
				 } else if ("level_smb".equals(key)) {
                        		mBatteryProps.batteryLevel_smb = Integer.parseInt(value);
                    } else if ("invalid".equals(key)) {
                        mInvalidCharger = Integer.parseInt(value);
                    } else {
                        pw.println("Unknown set option: " + key);
                        update = false;
                    }
                    if (update) {
                        long ident = Binder.clearCallingIdentity();
                        try {
                            mUpdatesStopped = true;
                            processValuesLocked(false);
                        } finally {
                            Binder.restoreCallingIdentity(ident);
                        }
                    }
                } catch (NumberFormatException ex) {
                    pw.println("Bad value: " + value);
                }
            } else if (args.length == 1 && "reset".equals(args[0])) {
                long ident = Binder.clearCallingIdentity();
                try {
                    if (mUpdatesStopped) {
                        mUpdatesStopped = false;
                        mBatteryProps.set(mLastBatteryProps);
                        processValuesLocked(false);
                    }
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            } else {
                pw.println("Dump current battery state, or:");
                pw.println("  set [ac|usb|wireless|status|level|invalid] <value>");
                pw.println("  reset");
            }
        }
    }

    //yanqing
    private void notifyRebootTimerStop()
    {
/*
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Intent stopIntent = new Intent(STOP_SIM_PLUG_OUT_TIMER);
                mContext.sendBroadcast(stopIntent);
                isShowingSimPopDlg = false;
            }
        });
*/
    }

    private final UEventObserver mInvalidChargerObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            final int invalidCharger = "1".equals(event.get("SWITCH_STATE")) ? 1 : 0;
            synchronized (mLock) {
                if (mInvalidCharger != invalidCharger) {
                    mInvalidCharger = invalidCharger;
                }
            }
        }
    };

    private final class Led {
        private final Light mBatteryLight;

        private final int mBatteryLowARGB;
        private final int mBatteryMediumARGB;
        private final int mBatteryFullARGB;
        private final int mBatteryLedOn;
        private final int mBatteryLedOff;

        public Led(Context context, LightsManager lights) {
            mBatteryLight = lights.getLight(LightsManager.LIGHT_ID_BATTERY);

            mBatteryLowARGB = context.getResources().getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryLowARGB);
            mBatteryMediumARGB = context.getResources().getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryMediumARGB);
            mBatteryFullARGB = context.getResources().getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryFullARGB);
            mBatteryLedOn = context.getResources().getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryLedOn);
            mBatteryLedOff = context.getResources().getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryLedOff);
        }

        /**
         * Synchronize on BatteryService.
         */
        public void updateLightsLocked() {
            final int level = mBatteryProps.batteryLevel;
            final int status = mBatteryProps.batteryStatus;
            if(mIPOBoot)
            {
                //Get led status in IPO mode
                getIpoLedStatus();
            }
            if (level < mLowBatteryWarningLevel) {
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    updateLedStatus();
                    // Solid red when battery is charging
                    mBatteryLight.setColor(mBatteryLowARGB);
                } else {
                    LowLevelFlag = true;
                    updateLedStatus();
                    // Flash red when battery is low and not charging
                    mBatteryLight.setFlashing(mBatteryLowARGB, Light.LIGHT_FLASH_TIMED,
                            mBatteryLedOn, mBatteryLedOff);
                }
            } else if (status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL) {
                if (status == BatteryManager.BATTERY_STATUS_FULL || level >= 90) {
                    updateLedStatus();
                    // Solid green when full or charging and nearly full
                    mBatteryLight.setColor(mBatteryFullARGB);
                } else {
                    updateLedStatus();
                    // Solid orange when charging and halfway full
                    mBatteryLight.setColor(mBatteryMediumARGB);
                }
            } else {
                if(ipo_led_on && mIPOBoot){
                    if (status == BatteryManager.BATTERY_STATUS_FULL || level >= 90) {
                        mBatteryLight.setColor(mBatteryFullARGB);
                    }
                    else {
                        mBatteryLight.setColor(mBatteryMediumARGB);
                    }
                    mIPOBoot = false;
                    ipo_led_on = false;
                }
                // No lights if not charging and not low
                mBatteryLight.turnOff();
            }
        }
        private void getIpoLedStatus() {
            if ("1".equals(SystemProperties.get("sys.ipo.ledon"))) {
                ipo_led_on = true;
            }
            else if ("0".equals(SystemProperties.get("sys.ipo.ledon"))) {
                ipo_led_off = true;
            }
            if (DEBUG) {
                Slog.d(TAG, ">>>>>>>getIpoLedStatus ipo_led_on = "+ipo_led_on +",  ipo_led_off = " +ipo_led_off +"<<<<<<<");
            }
        }

        private void updateLedStatus() {
            // if LowBatteryWarning happened, we refresh the led state no matter ipo_led is on or off.
            if((ipo_led_off && mIPOBoot) || (LowLevelFlag && mIPOBoot)){
                mBatteryLight.turnOff();
                mIPOBoot = false;
                ipo_led_off = false;
                ipo_led_on = false;
                if (DEBUG) {
                    Slog.d(TAG, ">>>>>>>updateLedStatus  LowLevelFlag = "+LowLevelFlag +"<<<<<<<");
                }
            }
        }
    }

    private final class BatteryListener extends IBatteryPropertiesListener.Stub {
        @Override
        public void batteryPropertiesChanged(BatteryProperties props) {
            final long identity = Binder.clearCallingIdentity();
            try {
                BatteryService.this.update(props);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
       }
    }

    private final class BinderService extends Binder {
        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                    != PackageManager.PERMISSION_GRANTED) {

                pw.println("Permission Denial: can't dump Battery service from from pid="
                        + Binder.getCallingPid()
                        + ", uid=" + Binder.getCallingUid());
                return;
            }

            dumpInternal(pw, args);
        }
    }

    private final class LocalService extends BatteryManagerInternal {
        @Override
        public boolean isPowered(int plugTypeSet) {
            synchronized (mLock) {
                return isPoweredLocked(plugTypeSet);
            }
        }

        @Override
        public int getPlugType() {
            synchronized (mLock) {
                return mPlugType;
            }
        }

        @Override
        public int getBatteryLevel() {
            synchronized (mLock) {
                return mBatteryProps.batteryLevel;
            }
        }

        @Override
        public boolean getBatteryLevelLow() {
            synchronized (mLock) {
                return mBatteryLevelLow;
            }
        }

        @Override
        public int getInvalidCharger() {
            synchronized (mLock) {
                return mInvalidCharger;
            }
        }
    }

}
