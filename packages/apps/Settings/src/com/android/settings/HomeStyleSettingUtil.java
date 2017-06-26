package com.android.settings;

import java.util.List;

import android.content.res.*;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.os.RemoteException;
import android.util.Log;
import java.lang.reflect.*;

public class HomeStyleSettingUtil {
	private final static String TAG = "HomeStyleSettingUtil";
	private static PackageManager mPackageManager;
	private final static String LAUNCHER_PACKAGE_NAME = "com.huawei.android.launcher";
	private final static String LAUNCHER_CLASS_NORMALUI = "com.huawei.android.launcher.Launcher";
	private final static String LAUNCHER_CLASS_SIMPLEUI = "com.huawei.android.launcher.simpleui.SimpleUILauncher";

	static ComponentName mSimpleui = new ComponentName(
			LAUNCHER_PACKAGE_NAME, LAUNCHER_CLASS_SIMPLEUI);
	private static ComponentName mUnihome = new ComponentName(
			LAUNCHER_PACKAGE_NAME, LAUNCHER_CLASS_NORMALUI);
	private static String[] entryvalues_font_size;
	private static Context mContext;
	
	/**
	 * Change UI mode.
	 */
	public static void changeUIMode(Context context, boolean isSimpleModeOn,
			boolean shouldChangeAndRestart) {
		if (null == context) {
			return;
		}
		mContext = context;

		if (null == mPackageManager) {
			mPackageManager = mContext.getPackageManager();
		}

		if (!isSimpleModeOn) {
			// Switch to NormalUI mode.
			try {
				disableMode(mSimpleui);
				enableMode(mUnihome);
			} catch (Exception e) {
				Log.e(TAG, "Error happened when switching to Normal UI Mode. "
						+ "Error msg is " + e.getMessage());
				enableMode(mUnihome); // enable NormalUI mode when error
										// happened.
			}
		} else {
			// Switch to SimpleUI mode.
			try {
				disableMode(mUnihome);
				enableMode(mSimpleui);
			} catch (Exception e) {
				Log.e(TAG, "Error happened when switching to Simple UI Mode. "
						+ "Error msg is " + e.getMessage());
				enableMode(mUnihome); // enable NormalUI mode when error
										// happened.
			}
		}

		if (shouldChangeAndRestart) {
			changeAndRestart(isSimpleModeOn);
		} else {
			changePreferredLauncher(LAUNCHER_PACKAGE_NAME);
		}

		// Update current system configuration.
		updateConfiguration(isSimpleModeOn);
	}

	/**
	 * Change font size when UI mode changed. This feature is defined in
	 * HAPRM-6935.
	 */
	public static void changeFontSize(Context context, boolean isSimpleModeOn) {
		if (null == context) {
			return;
		}

		Configuration curConfig = new Configuration();
		if (entryvalues_font_size == null || entryvalues_font_size.length == 0) {
			entryvalues_font_size = context.getResources().getStringArray(
					R.array.entryvalues_font_size);
		}

		try {
			if (!isSimpleModeOn) {
				// Swtich to normal font size.
				curConfig.fontScale = Float
						.parseFloat(entryvalues_font_size[1]);
			} else {
				// Switch to extra huge font size.
				curConfig.fontScale = Float
						.parseFloat(entryvalues_font_size[4]);
			}

			ActivityManagerNative.getDefault().updatePersistentConfiguration(
					curConfig);
		} catch (RemoteException e) {
			Log.e(TAG, "Unable to save font size");
		} catch (IndexOutOfBoundsException e) {
			Log.e(TAG, "Unable to switch to extra huge font size");
		}
	}

	/**
	 * Disable given mode.
	 */
	public static void disableMode(ComponentName name) {
		if (null == mPackageManager) {
			return;
		}

		mPackageManager.setComponentEnabledSetting(name,
				PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
	}

	/**
	 * Enable given mode.
	 */
	public static void enableMode(ComponentName name) {
		if (null == mPackageManager) {
			return;
		}

		mPackageManager.setComponentEnabledSetting(name,
				PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
	}

	/**
	 * Change preferred launcher and restart comphonent.
	 * 
	 * @param isSimpleModeOn
	 */
	public static void changeAndRestart(boolean isSimpleModeOn) {
		changePreferredLauncher(LAUNCHER_PACKAGE_NAME);
		if (isSimpleModeOn) {
			restartComponent(mSimpleui);
		} else {
			restartComponent(mUnihome);
		}
	}

	/**
	 * change the Preferred Launcher.
	 */
	public static void changePreferredLauncher(String pkgName) {
		if (null == mPackageManager) {
			return;
		}

		Intent mainIntent = new Intent(Intent.ACTION_MAIN).addCategory(
				Intent.CATEGORY_HOME).addCategory(Intent.CATEGORY_DEFAULT);
		List<ResolveInfo> resolveInfos = mPackageManager.queryIntentActivities(
				mainIntent, 0);
		for (int i = 0; i < resolveInfos.size(); i++) {
			ResolveInfo resolveInfo = resolveInfos.get(i);
			if (null != resolveInfo) {
				mPackageManager
						.clearPackagePreferredActivities(resolveInfo.activityInfo.packageName);
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
			mPackageManager.addPreferredActivity(inf,
					IntentFilter.MATCH_CATEGORY_EMPTY, set, set[find]);
		}
	}

	/**
	 * kill HwLauncher6's process and start the selected Activity.
	 */
	public static void restartComponent(ComponentName comp) {
		if (null == mContext) {
			return;
		}

		ActivityManager activityManager = (ActivityManager) mContext
				.getSystemService(Context.ACTIVITY_SERVICE);
		activityManager.killBackgroundProcesses(LAUNCHER_PACKAGE_NAME);
		/* < DTS2013052706916 yanyongming/KF67100 20130606 begin */
		Intent intent = new Intent(Intent.ACTION_MAIN, null);
		intent.addCategory(Intent.CATEGORY_HOME);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
				| Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		/* DTS2013052706916 yanyongming/KF67100 20130606 end > */
		mContext.startActivity(intent);
	}

	/**
	 * Update system configuration.
	 */
	public static void updateConfiguration(boolean isSimpleModeOn) {
		Configuration curConfig = new Configuration();
		try {
			// get extra configuration.
			curConfig.updateFrom(ActivityManagerNative.getDefault()
					.getConfiguration());

		Class clazz = Class.forName("android.content.res.ConfigurationEx");
		Object o = clazz.newInstance();
		Field cnfigex = clazz.getDeclaredField("simpleuiMode");
		Field simpleUiMode_yes = clazz.getDeclaredField("SIMPLEUIMODE_YES");
		Field simpleUiMode_no = clazz.getDeclaredField("SIMPLEUIMODE_NO");
		Object value_SIMPLEUIMODE_YES = simpleUiMode_yes.get(o);
		Object value_SIMPLEUIMODE_NO = simpleUiMode_no.get(o);
		cnfigex.set(o, isSimpleModeOn?value_SIMPLEUIMODE_YES:value_SIMPLEUIMODE_NO);
	
		ActivityManagerNative.getDefault().updatePersistentConfiguration(
					curConfig);



		} catch (Exception e) {
			e.printStackTrace();
		} catch (NoSuchFieldError err) {
			err.printStackTrace();
		}
	}
}








