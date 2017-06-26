package com.mediatek.deskclock.extension;

import android.content.Context;
import android.util.Log;

import com.mediatek.common.MPlugin;
import com.mediatek.deskclock.ext.DefaultAlarmControllerExt;
import com.mediatek.deskclock.ext.IAlarmControllerExt;

public class OPExtensionFactory {

    private static final String TAG = "OPExtensionFactory";
    private static IAlarmControllerExt sAlarmControllerExt = null;

    /**
     * The IAlarmControllerExt is an Single instance. it would hold the ApplicationContext,
     * and alive within the whole Application.
     * @param context MPlugin use it to retrieve the plug-in object.
     * @return the single instance of IAlarmControllerExt.
     */
    public synchronized static IAlarmControllerExt getAlarmControllerExt(Context context) {
        if (null == sAlarmControllerExt) {
            sAlarmControllerExt = (IAlarmControllerExt) MPlugin.createInstance(
                    IAlarmControllerExt.class.getName(), context);
            if (null == sAlarmControllerExt) {
                sAlarmControllerExt = new DefaultAlarmControllerExt();
                Log.d(TAG, "AlarmClock failed to create plugin, create default one");
            } else {
                Log.d(TAG, "AlarmClock create plugin successfully");
            }
        }
        return sAlarmControllerExt;
    }
}