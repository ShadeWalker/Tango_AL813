
package com.mediatek.keyguard.Telephony;


import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.Settings.System;
import android.util.Slog;
import android.view.LayoutInflater;
import android.view.WindowManager;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.keyguard.R ;

import android.util.Log;
import com.mediatek.keyguard.ext.IOperatorSIMString;
import com.mediatek.keyguard.ext.IOperatorSIMString.SIMChangedTag;
import com.mediatek.keyguard.ext.KeyguardPluginFactory;
import java.util.LinkedList;
import java.util.Queue;

import com.mediatek.keyguard.PowerOffAlarm.PowerOffAlarmManager ;
import android.content.ContentValues;//add by lipeng
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;


public class KeyguardDialogManager {

    private static final String TAG = "KeyguardDialogManager";
    private static final boolean DEBUG = true;

    private static KeyguardDialogManager sInstance;

    private final Context mContext;
    private KeyguardUpdateMonitor mUpdateMonitor;

    /// M: Manage the dialog sequence.
    private DialogSequenceManager mDialogSequenceManager;
    //add by lipeng for APN Pop-up window
    public static final String PREFERRED_APN_URI = "content://telephony/carriers/preferapn";
	public static final Uri URI_LIST[] = {
			Telephony.Carriers.CONTENT_URI,
			Telephony.Carriers.CONTENT_URI };

	public static final Uri PREFERRED_URI_LIST[] = {
			Uri.parse(PREFERRED_APN_URI),
			Uri.parse(PREFERRED_APN_URI)};
	
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
			  if ("HQ_CHOOSE_DEFAULT_APN_FOR_GLOBE".equals(action)) {
            	//int simTmp = intent.getIntExtra("simId",0);
            	//int mccTmp = intent.getIntExtra("mccmnc",0);
				//Log.d("lipeng", "simId = "+simTmp+",	mccmnc = "+mccTmp);
            	chooseDefaultAPN(intent.getIntExtra("simId",0), intent.getIntExtra("mccmnc",0));
             }
        }
    };//end by lipeng
    public void chooseDefaultAPN(int simId, int mccmnc) {
	    final int slot = simId;
	    final String[] apnName = new String[]{"myGlobe Internet"};
	    final String[] apnName52501 = new String[]{"e-ideas"};
        final String[] apnName52505 = new String[]{"shwap"};
	    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext);
	    dialogBuilder.setCancelable(false);
	    dialogBuilder.setTitle("Choose your default APN");

		if(mccmnc==52501){	
			Log.d(TAG, "mccmnc==52501");
		    dialogBuilder.setSingleChoiceItems(new String[]{"Singtel (Postpaid)","Singtel (Prepaid)"},0,
			new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog,int which){
					if (which == 0) {
						Log.d(TAG, "0");
						apnName52501[0] = "e-ideas";//"SingTel (Postpaid)";//"myGlobe Internet";
					}else {
						apnName52501[0] = "hicard";//"SingTel (Prepaid)";//"myGlobe INET";
					}
					Log.d(TAG, "apnName52501 = "+apnName52501[0]);
			    }	
			});
			dialogBuilder.setPositiveButton(android.R.string.ok, 
				new AlertDialog.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
					Cursor cursor = mContext.getContentResolver().query(URI_LIST[slot],new String[]{"_id"},"numeric = '52501' and apn = '"+apnName52501[0]+"'",null,null);
					
					Log.d(TAG, "1	:	size = "+ cursor.getCount());
					if (cursor.moveToFirst()) {
						Log.d(TAG, "2");
						ContentValues values = new ContentValues();
						values.put("apn_id",cursor.getString(0));
						mContext.getContentResolver().update(PREFERRED_URI_LIST[slot],values,null,null);
					}
					if (null != cursor) {
						Log.d(TAG, "3");
						cursor.close();
					} 
				}
			});			
		}else if(mccmnc==52505){
            //maheling 2015.12.14 HQ01565385
            Log.d(TAG, "mccmnc==52505");
            dialogBuilder.setSingleChoiceItems(new String[]{"SH Data Postpaid","SH Data Prepaid"},0,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int which){
                            if (which == 0) {
                                Slog.d(TAG, "0");
                                apnName52505[0] = "shwap";
                            }else{
                                apnName52505[0] = "shppd";
                            }
                            Log.d(TAG, "apnName52505 = "+apnName52505[0]);
                        }
                    });
            dialogBuilder.setPositiveButton(android.R.string.ok,
                    new AlertDialog.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Cursor cursor = mContext.getContentResolver().query(URI_LIST[slot],new String[]{"_id"},"numeric = '52505' and apn = '"+apnName52505[0]+"'",null,null);

                            Log.d(TAG, "1	:	size = "+ cursor.getCount());
                            if (cursor.moveToFirst()) {
                                Log.d(TAG, "2");
                                ContentValues values = new ContentValues();
                                values.put("apn_id",cursor.getString(0));
                                mContext.getContentResolver().update(PREFERRED_URI_LIST[slot],values,null,null);
                            }
                            if (null != cursor) {
                                Log.d(TAG, "3");
                                cursor.close();
                            }
                        }
                    });
        }else{
			Log.d(TAG, "else");
			dialogBuilder.setSingleChoiceItems(new String[]{"Globe Postpaid","Globe Prepaid"},0,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,int which){
						if (which == 0) {
							Log.d(TAG, "0");
						    apnName[0] = "myGlobe Internet";
						}else {
						    apnName[0] = "myGlobe INET";
						}
						Log.d(TAG, "apnName = "+apnName[0]);
					}	
				});
		    dialogBuilder.setPositiveButton(android.R.string.ok, 
				new AlertDialog.OnClickListener() {
	            	public void onClick(DialogInterface dialog, int which) {
						Cursor cursor = mContext.getContentResolver().query(URI_LIST[slot],new String[]{"_id"},"numeric = '51502' and name = '"+apnName[0]+"'",null,null);
						
						Log.d(TAG, "1	:	size = "+ cursor.getCount());
						if (cursor.moveToFirst()) {
							Log.d(TAG, "2");
						    ContentValues values = new ContentValues();
						    values.put("apn_id",cursor.getString(0));
						    mContext.getContentResolver().update(PREFERRED_URI_LIST[slot],values,null,null);
						}
						if (null != cursor) {
							Log.d(TAG, "3");
						    cursor.close();
						} 
					}
			});
		}	


	    AlertDialog dialog = dialogBuilder.create();
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
	    dialog.show();
	}
    private KeyguardDialogManager(Context context) {
        mContext = context;

        mDialogSequenceManager = new DialogSequenceManager();
    //add by lipeng for APN Pop-up window
        final IntentFilter filter = new IntentFilter();
	if (SystemProperties.get("ro.hq.choose.default.apn").equals("1")) {
	    filter.addAction("HQ_CHOOSE_DEFAULT_APN_FOR_GLOBE");
	}//end by lipeng
        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(context);
    }

    public static KeyguardDialogManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new KeyguardDialogManager(context);
        }
        return sInstance;
    }

    /**
     * M: Return device is in encrypte mode or not.
     * If it is in encrypte mode, we will not show lockscreen.
     */
    public boolean isEncryptMode() {
        String state = SystemProperties.get("vold.decrypt");
        return !("".equals(state) || "trigger_restart_framework".equals(state));
    }


     /**
     * M: interface is a call back for the user who need to popup Dialog.
     */
    public static interface DialogShowCallBack {
        public void show();
    }

    /**
     * M: request show dialog
     * @param callback the user need to implement the callback.
     */
    public void requestShowDialog(DialogShowCallBack callback) {
        //if (!KeyguardViewMediator.isKeyguardInActivity) {
            mDialogSequenceManager.requestShowDialog(callback);
        //} else {
        //    Log.d(TAG, "Ignore showing dialog in KeyguardMock");
        //}
    }

    /**
     * M: when the user close dialog, should report the status.
     */
    public void reportDialogClose() {
        mDialogSequenceManager.reportDialogClose();
    }

    /**
     * M: interface for showing dialog sequencely manager.
     *
     */
    public static interface SequenceDialog {
        /**
         * the client  needed to show a dialog should call this
         * @param callback the client should implement the callback.
         */
        public void requestShowDialog(DialogShowCallBack callback);
        /**
         * If the client close the dialog, should call this to report.
         */
        public void reportDialogClose();
    }

    /**
     * M: Manage the dialog sequence.
     * It implment the main logical of the sequence process.
     */
    private class DialogSequenceManager implements SequenceDialog {
        /// M: log tag for this class
        private static final String CLASS_TAG = "DialogSequenceManager";
        /// M: debug switch for the log.
        private static final boolean CLASS_DEBUG = true;
        /// M: The queue to save the call backs.
        private Queue<DialogShowCallBack> mDialogShowCallbackQueue;
        /// M: Whether the inner dialog is showing
        private boolean mInnerDialogShowing = false;
        /// M: If keyguard set the dialog sequence value, and inner dialog is showing.
        private boolean mLocked = false;

        public DialogSequenceManager() {
            if (CLASS_DEBUG) {
                Log.d(TAG, CLASS_TAG + " DialogSequenceManager()");
            }
            mDialogShowCallbackQueue = new LinkedList<DialogShowCallBack>();

            mContext.getContentResolver().registerContentObserver(System.getUriFor(System.DIALOG_SEQUENCE_SETTINGS),
                    false, mDialogSequenceObserver);
         }

        public void requestShowDialog(DialogShowCallBack callback) {
            if (CLASS_DEBUG) {
                Log.d(TAG, CLASS_TAG + " --requestShowDialog()");
            }
            mDialogShowCallbackQueue.add(callback);
            handleShowDialog();
        }

        public void handleShowDialog() {
            if (CLASS_DEBUG) {
                Log.d(TAG, CLASS_TAG + " --handleShowDialog()--enableShow() = " + enableShow());
            }
            if (enableShow()) {
                if (getLocked()) {
                    DialogShowCallBack dialogCallBack = mDialogShowCallbackQueue.poll();
                    if (CLASS_DEBUG) {
                        Log.d(TAG, CLASS_TAG + " --handleShowDialog()--dialogCallBack = " + dialogCallBack);
                    }
                    if (dialogCallBack != null) {
                        dialogCallBack.show();
                        setInnerDialogShowing(true);
                    }
                } else {
                    if (CLASS_DEBUG) {
                        Log.d(TAG, CLASS_TAG + " --handleShowDialog()--System.putInt( "
                                + System.DIALOG_SEQUENCE_SETTINGS + " value = " + System.DIALOG_SEQUENCE_KEYGUARD);
                    }
                    System.putInt(mContext.getContentResolver(), System.DIALOG_SEQUENCE_SETTINGS,
                            System.DIALOG_SEQUENCE_KEYGUARD);
                }
            }
        }

        public void reportDialogClose() {
            if (CLASS_DEBUG) {
                Log.d(TAG, CLASS_TAG + " --reportDialogClose()--mDialogShowCallbackQueue.isEmpty() = "
                        + mDialogShowCallbackQueue.isEmpty());
            }
            setInnerDialogShowing(false);

            if (mDialogShowCallbackQueue.isEmpty()) {
                if (CLASS_DEBUG) {
                    Log.d(TAG, CLASS_TAG + " --reportDialogClose()--System.putInt( "
                            + System.DIALOG_SEQUENCE_SETTINGS + " value = " + System.DIALOG_SEQUENCE_DEFAULT
                            + " --setLocked(false)--");
                }
                System.putInt(mContext.getContentResolver(), System.DIALOG_SEQUENCE_SETTINGS,
                        System.DIALOG_SEQUENCE_DEFAULT);
                setLocked(false);
            } else {
                handleShowDialog();
            }
        }

        /**
         * M : Combine the conditions to deceide whether enable showing or not
         */
        private boolean enableShow() {
            if (CLASS_DEBUG) {
                Log.d(TAG, CLASS_TAG + " --enableShow()-- !mDialogShowCallbackQueue.isEmpty() = " + !mDialogShowCallbackQueue.isEmpty()
                        + " !getInnerDialogShowing() = " + !getInnerDialogShowing()
                        + " !isOtherModuleShowing() = " + !isOtherModuleShowing()
                        + "!isAlarmBoot() = " + !PowerOffAlarmManager.isAlarmBoot()
                        + " isDeviceProvisioned() = " + mUpdateMonitor.isDeviceProvisioned()
                        /*+ " !isOOBEShowing() = " + !isOOBEShowing()*/);
            }

            return !mDialogShowCallbackQueue.isEmpty() && !getInnerDialogShowing() && !isOtherModuleShowing()
                    && !PowerOffAlarmManager.isAlarmBoot() && mUpdateMonitor.isDeviceProvisioned()
                    && !isEncryptMode();
        }

        /**
         * M : Query the dialog sequence settings to decide whether other module's dialog is showing or not.
         */
        private boolean isOtherModuleShowing() {
            int value = queryDialogSequenceSeetings();
            if (CLASS_DEBUG) {
                Log.d(TAG, CLASS_TAG + " --isOtherModuleShowing()--" + System.DIALOG_SEQUENCE_SETTINGS + " = " + value);
            }
            if (value == System.DIALOG_SEQUENCE_DEFAULT || value == System.DIALOG_SEQUENCE_KEYGUARD) {
                return false;
            }
            return true;
        }

        private void setInnerDialogShowing(boolean show) {
            mInnerDialogShowing = show;
        }

        private boolean getInnerDialogShowing() {
            return mInnerDialogShowing;
        }

        private void setLocked(boolean locked) {
            mLocked = locked;
        }

        private boolean getLocked() {
            return mLocked;
        }

        /**
         * M : Query dialog sequence settings value
         */
        private int queryDialogSequenceSeetings() {
            int value = System.getInt(mContext.getContentResolver(), System.DIALOG_SEQUENCE_SETTINGS,
                    System.DIALOG_SEQUENCE_DEFAULT);
            return value;
        }

        /// M: dialog sequence observer for dialog sequence settings
        private ContentObserver mDialogSequenceObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                int value = queryDialogSequenceSeetings();
                if (CLASS_DEBUG) {
                    Log.d(TAG, CLASS_TAG + " DialogSequenceObserver--onChange()--"
                            + System.DIALOG_SEQUENCE_SETTINGS + " = " + value);
                }
                if (value == System.DIALOG_SEQUENCE_DEFAULT) {
                    setLocked(false);
                    handleShowDialog();
                } else if (value == System.DIALOG_SEQUENCE_KEYGUARD) {
                    setLocked(true);
                    handleShowDialog();
                }
            }
        };
    }

}
