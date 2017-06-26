package com.android.gestures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.android.gestures.GestureSurfaceView.OnEndOfAnimationInterface;
import com.android.gestures.service.GestureService;
import com.android.gestures.util.Utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
//HQ_wuhuihui add to optmise keylock gesture start
import android.content.pm.ResolveInfo;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 * 
 * @see SystemUiHider
 */
public class GestureFullscreenActivity extends Activity implements OnEndOfAnimationInterface{
	/**
	 * Whether or not the system UI should be auto-hidden after
	 * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
	 */
	private static final String TAG = "FullscreenActivity";
	private static final boolean AUTO_HIDE = true;
        private String mStartClass;

	/**
	 * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
	 * user interaction before hiding the system UI.
	 */
	private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

	/**
	 * If set, will toggle the system UI visibility upon interaction. Otherwise,
	 * will show the system UI visibility upon interaction.
	 */
	private static final boolean TOGGLE_ON_CLICK = true;

	
	public static int screenHeight;
	public static int screenWidth;
	
	public static int gesType = 0;
	public static float gesStartX;
	public static float gesStartY;
	public static float gesEndX;
	public static float gesEndY;
	public static float gesXlen;
	public static float gesYlen;
	
	public static float centerX;
	public static float centerY;
	public static float polar1X;
	public static float polar1Y;
	public static float polar2X;
	public static float polar2Y;
	public static float polar3X;
	public static float polar3Y;
	public static float polar4X;
	public static float polar4Y;
	
	public static boolean isDebugMode = false;
	public static int pointNum = 0;
	public static boolean isStartThread = false;
	
	private static final String GESTURE_DEF_LAUNCHER = "com.huawei.android.launcher;com.huawei.android.launcher.Launcher";
	private String GESTURE_DEF_C = "com.huawei.camera;com.huawei.camera";
	private String GESTURE_DEF_E = "com.android.browser;com.android.browser.BrowserActivity";
        private String GESTURE_DEF_W = "com.huawei.android.totemweather;com.huawei.android.totemweather.WeatherHome";
        private String GESTURE_DEF_M = "com.android.mediacenter;com.android.mediacenter.PageActivity";
	
	GestureSurfaceView msv;
	private Handler mHandler = new Handler();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		isStartThread = true;
		msv = new GestureSurfaceView(this);
		DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);

        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
        msv.setOnEndOfAnimation(this);

	    if(!initData()){
            Log.d(TAG,"Read file error!");
		}

	Log.d(TAG,
		"startX=" + Float.toString(gesStartX) + "," + "startY="
		          + Float.toString(gesStartY) + "," + "endX ="
			  + Float.toString(gesEndX) + "," + "endY ="
			  + Float.toString(gesEndY) + "," + "Xlen  ="
			  + Float.toString(gesXlen) + "," + "Ylen  ="
			  + Float.toString(gesYlen) + ", polar1X = "
			  + polar1X + ", polar1Y = " + polar1Y
			  + ", polar2X = " + polar2X + ", polar2Y = "
			  + polar2Y + ", polar3X = " + polar3X
			  + ", polar3Y = " + polar3Y + ", polar4X = "
			  + polar4X + ", polar4Y = " + polar4Y
			  + ", centerX = " + centerX + ", centerY = " + centerY);
        
		setContentView(msv);

	}

	private boolean initData(){
		InputStream inStream = null;
		
			 try {
				 inStream = new FileInputStream("/proc/gesture_echo");
		
				 if (inStream != null) {
					 InputStreamReader inputReader = new InputStreamReader(inStream);
					 BufferedReader buffReader = new BufferedReader(inputReader);
		
					 String line = buffReader.readLine();
					 String[] subString = line.split(" ");
					 for (int i=0; i<subString.length; i++){
						 Log.d(TAG, "subString " + i + "=" + subString[i]);
					 }
					 if(subString.length < 17) {
                         Log.d(TAG, "Error in read file");
						 return false;
					 } else {
					     gesType = Integer.parseInt(subString[0]);
                         gesStartX = Integer.parseInt(subString[1]);
                         gesStartY = Integer.parseInt(subString[2]);
                         gesEndX = Integer.parseInt(subString[3]);
                         gesEndY = Integer.parseInt(subString[4]);
                         gesXlen = Integer.parseInt(subString[5]);
                         gesYlen = Integer.parseInt(subString[6]);
        
                         centerX = Integer.parseInt(subString[7]);
                         centerY = Integer.parseInt(subString[8]);
                         polar1X = Integer.parseInt(subString[9]);
                         polar1Y = Integer.parseInt(subString[10]);
                         polar2X = Integer.parseInt(subString[11]);
                         polar2Y = Integer.parseInt(subString[12]);
                         polar3X = Integer.parseInt(subString[13]);
                         polar3Y = Integer.parseInt(subString[14]);
                         polar4X = Integer.parseInt(subString[15]);
                         polar4Y = Integer.parseInt(subString[16]);

						 return true;
					 }
				 }
				 return false;
			 } catch (IOException e) {
			     Log.d(TAG, "Read file failed");
				 return false;
			 } finally {
				 if (inStream != null) {
					 try {
						 inStream.close();
						 Log.d(TAG, "close stream");
					 } catch (IOException e) {
					    Log.d(TAG, "close stream failed");
					 }
				 }
			 }

	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		 if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			 GestureSurfaceView.isRuning = false;
			 this.finish();
		 }
		 else if(keyCode == KeyEvent.KEYCODE_HOME && event.getRepeatCount() == 0) {
		 }
		return false;
	}



	@Override
	public void onEndOfAnimation() {
		Log.d("GOODIXGES", "onEndOfAnimation");
		
		if(isDebugMode) {
		    this.finish();
			return;
		}
		String gestureDateBaseDef = Utils.getInstance().getGetureDateBaseDefineByGetureType(gesType);
		Intent startIntent = new Intent();
		switch(gesType) {
		case 'c':
		case 'e':
		case 'm':
		case 'o':
		case 'w':
		case 'v':
		case 's':
		case 'z':
		case 186:
		case 171:
		case 187:
		case 170:
		case 204:  // double click 	
			startIntent = generateGestureIntent(gestureDateBaseDef, gesType);
			break;
		default:
			this.finish();
			return;
		}
		try {
                    if (mStartClass != null && mStartClass.equals("com.android.keyguard;com.android.keyguard.keyguardplus.OneKeyLockActivity")){
                        Log.d("GOODIXGES", "one key lock, do not start activity");
                    } else {
                        startActivity(startIntent);
                    }
		} catch (Exception e) {
			e.printStackTrace();
//			Toast.makeText(FullscreenActivity.this, "application not found!!", Toast.LENGTH_SHORT).show();
		}
		mHandler.postDelayed(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				GestureFullscreenActivity.this.finish();
			}
		}, 100);
	}
	
	private Intent generateGestureIntent(String gestureDateBaseDef, int gesType) {
		
		Intent gesIntent = new Intent();
		String GestureValue = Settings.System.getString(getContentResolver(), gestureDateBaseDef);
                mStartClass = GestureValue;
		String[] componentName = GestureValue.split(";");
                //HQ_wuhuihui_20151007 modified for system reboot start
		ComponentName cmp;
		if(componentName == null || componentName.length < 2){
			cmp = getDefaultComponentName(gesType);
		} else {
			cmp = new ComponentName(componentName[0],componentName[1]);
		}
                //HQ_wuhuihui_20151007 modified for system reboot end
		gesIntent.setAction(Intent.ACTION_MAIN);
		gesIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		if(GestureValue != null && GestureValue.equals(GESTURE_DEF_C)) {
			gesIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		} else {
			gesIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		}
		gesIntent.putExtra("magazineunlock", true);
		gesIntent.setComponent(cmp);
		List<ResolveInfo> apps = getPackageManager().queryIntentActivities(gesIntent, 0);
		if (apps == null || apps.size() == 0) {
                    Log.d("GOODIXGES", "invalid component, gestureValue:" + GestureValue + "use default instead");
                    //Get default componentName
                    ComponentName defComponentName = getDefaultComponentName(gesType);
                    if (defComponentName != null) {
                        gesIntent.setComponent(defComponentName);
                     }
		 }
		return gesIntent;
	}

        private ComponentName getDefaultComponentName(int gesType){
            String[] componentNameString;
            ComponentName cmp;
            switch(gesType) {
                case 'c':
                    componentNameString = GESTURE_DEF_C.split(";");
                    cmp = new ComponentName(componentNameString[0],componentNameString[1]);
                    break;
		case 'e':
                    componentNameString = GESTURE_DEF_E.split(";");
                    cmp = new ComponentName(componentNameString[0],componentNameString[1]);
                    break;
		case 'm':
                    componentNameString = GESTURE_DEF_M.split(";");
                    cmp = new ComponentName(componentNameString[0],componentNameString[1]);
                    break;
	       case 'w':
                    componentNameString = GESTURE_DEF_W.split(";");
                    cmp = new ComponentName(componentNameString[0],componentNameString[1]);
                    break;
               default:
                    Log.d("GOODIXGES", "Bad gesture type");
                    return null;
            }
         Log.d("GOODIXGES", "componentNameString[0]:" + componentNameString[0] + ", componentNameString[1]:" + componentNameString[1]);
         return cmp;
        }
        //HQ_wuhuihui add for key lock gesture optmised end
	
	public List<String> getInstallPackageNames() {
	    PackageManager mPackMgr = getPackageManager();
	    List<String> packageNames = new ArrayList<String>();
	    List<PackageInfo> list = mPackMgr.getInstalledPackages(0);
	    int count = list.size();
	    for (int i = 0; i < count; i++) {
	        PackageInfo p = list.get(i);
	        if (p.versionName == null) {
	            continue;
	        }
	        packageNames.add(p.packageName);
	    }
	    return packageNames;
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		msv.release();
		GestureService.screenON.release();
	}
	
}
