package com.android.settings.sim;

import java.lang.reflect.Field;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.os.UserHandle;
import android.view.View;
import android.view.WindowManager;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.util.Log;

import com.android.settings.R;

public class SimPlugOutAlert extends Activity {

	private ProgressDialog mProgressDialog;
	private CountDownTimer mTimerForSim;

    /** The status bar where back/home/recent buttons are shown. */
    private StatusBarManager mStatusBar;

    /** All the widgets to disable in the status bar */
    final private static int sWidgetsToDisable = StatusBarManager.DISABLE_EXPAND
            | StatusBarManager.DISABLE_NOTIFICATION_ICONS
            | StatusBarManager.DISABLE_NOTIFICATION_ALERTS
            | StatusBarManager.DISABLE_SYSTEM_INFO
            | StatusBarManager.DISABLE_HOME
            | StatusBarManager.DISABLE_BACK
            | StatusBarManager.DISABLE_SEARCH
            | StatusBarManager.DISABLE_RECENT;

    //yanqing
    private boolean mSimInsertedToCancelReboot = false;
    public static final String STOP_SIM_PLUG_OUT_TIMER = "com.android.server.BatteryService.STOP_SIM_PLUG_OUT_TIMER";

    private BroadcastReceiver stopRebootReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			stopRebootTimer();
		}
	};

	private void stopRebootTimer() {
		Log.d("SimPlugOutAlert", "yanqing stopRebootTimer");
		if (mTimerForSim != null) {
			mTimerForSim.cancel();
			mTimerForSim = null;
		}
		try {
			if (mProgressDialog != null) {
				mProgressDialog.dismiss();
                mProgressDialog = null;
			}
		} catch (Exception e) {
            // if this happened, it means that the activity has been killed.
			e.printStackTrace();
        }
        mSimInsertedToCancelReboot = true;
        finish();
	}

	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		//getWindow().setType(
		//		WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL);
        // set this flag so this activity will stay in front of the keyguard
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

        flags |= WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;

        final WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.flags |= flags;
        getWindow().setAttributes(lp);

		acquireCpuWakeLock(this);
		showRebootAlert();
        mStatusBar = (StatusBarManager) getSystemService(Context.STATUS_BAR_SERVICE);
        mStatusBar.disable(sWidgetsToDisable);
        //yanqing
        IntentFilter filter = new IntentFilter(STOP_SIM_PLUG_OUT_TIMER);
        registerReceiver(stopRebootReceiver, filter);
	}

	private void showRebootAlert() {
		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setTitle(R.string.sim_card_popup_illegal_title);
		mProgressDialog.setCancelable(false);
		mProgressDialog.setButton(ProgressDialog.BUTTON_POSITIVE,
				getString(R.string.sim_card_popup_illegal_restart),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (mTimerForSim != null) {
							mTimerForSim.cancel();
							mTimerForSim = null;
						}
						//yanqing
						if(!mSimInsertedToCancelReboot) {
							fireReboot();
						}
						
						releaseCpuWakeLock();
						dialog.dismiss();
                        mProgressDialog = null;
                        finish();
					}
				});
		mProgressDialog.setCanceledOnTouchOutside(false);
		mProgressDialog
				.setOnDismissListener(new DialogInterface.OnDismissListener() {
					public void onDismiss(DialogInterface dialog) {
						keepDialogShow(dialog);
					}
				});

		final int countSeconds = 11;
		// final int countSeconds = 60; //for test only
		final int millisSeconds = 1000;

		mProgressDialog.setMax(10);
		mProgressDialog.setProgress(0);
        mProgressDialog.setMessage(String.format(getString(R.string.sim_card_popup_illegal_msg), countSeconds));
        mProgressDialog.show();

		mTimerForSim = new CountDownTimer(countSeconds * millisSeconds,
				millisSeconds) {
			@Override
			public void onTick(long millisSeconds) {
				int seconds = (int) (millisSeconds / 1000);
                if (seconds == 0) {
                    try {
    				    if (mProgressDialog != null) {
	    				    mProgressDialog.dismiss();
                            mProgressDialog = null;
		    	    	}
		            } catch (Exception e) {
                        // if this happened, it means that the activity has been killed.
			            e.printStackTrace();
		            }
                    return;
                }
                mProgressDialog.setMessage(String.format(getString(R.string.sim_card_popup_illegal_msg), seconds));
                mProgressDialog.setProgress(seconds);
			}

			@Override
			public void onFinish() {
                try {
				    if (mProgressDialog != null) {
				    	mProgressDialog.dismiss();
                        mProgressDialog = null;
				    }
		        } catch (Exception e) {
                    // if this happened, it means that the activity has been killed.
			        e.printStackTrace();
                }
                //yanqing
				if(!mSimInsertedToCancelReboot) {
					fireReboot();
				}
				releaseCpuWakeLock();
                finish();
			}
		}.start();

	}

	private void keepDialogShow(DialogInterface dialog) {
		try {
			Field field = dialog.getClass().getSuperclass()
					.getDeclaredField("mShowing");
			field.setAccessible(true);
			field.set(dialog, false);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void fireReboot() {
		Intent intent = new Intent(Intent.ACTION_REBOOT);
		intent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivityAsUser(intent, UserHandle.CURRENT);
	}

	private static PowerManager.WakeLock sScreenOnWakeLock;
	private static PowerManager.WakeLock sCpuWakeLock;

	private static void acquireCpuWakeLock(Context context) {
		if (sScreenOnWakeLock != null && sCpuWakeLock != null) {
			return;
		}

		PowerManager pm = (PowerManager) context
				.getSystemService(Context.POWER_SERVICE);
		sScreenOnWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
				| PowerManager.ACQUIRE_CAUSES_WAKEUP
				| PowerManager.ON_AFTER_RELEASE, "SimPlugedOutReboot");
		sScreenOnWakeLock.acquire();
		// M: ALPS00850405, ALPS00881041 hold a cpu wake lock
		// Wake lock that ensures that the CPU is running. The screen might not
		// be on.
		sCpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				"SimPlugedOutRebootDlg");
		sCpuWakeLock.setReferenceCounted(false);
		sCpuWakeLock.acquire();
	}

	private static void releaseCpuWakeLock() {
		if (sScreenOnWakeLock != null) {
			sScreenOnWakeLock.release();
			sScreenOnWakeLock = null;
		}
		if (sCpuWakeLock != null) {
			sCpuWakeLock.release();
			sCpuWakeLock = null;
		}
	}

	public void onPause() {
		super.onPause();
        mStatusBar.disable(StatusBarManager.DISABLE_NONE);
	}

    public void onResume() {
        super.onResume();
    }

    //yanqing
    public void onDestroy() {
    	super.onDestroy();
    	unregisterReceiver(stopRebootReceiver);
    }
}
