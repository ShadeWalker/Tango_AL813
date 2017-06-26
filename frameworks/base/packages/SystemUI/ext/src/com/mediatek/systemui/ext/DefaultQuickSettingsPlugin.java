package com.mediatek.systemui.ext;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;
import android.view.ViewGroup;


import com.mediatek.common.PluginImpl ;

/**
 * Default implementation of Plug-in definition of Quick Settings.
 */
@PluginImpl(interfaceName="com.mediatek.systemui.ext.IQuickSettingsPlugin")
public class DefaultQuickSettingsPlugin extends ContextWrapper implements
        IQuickSettingsPlugin {
    protected Context mContext;
    private static final String TAG = "DefaultQuickSettingsPlugin";

    /**
     * Constructor.
     * @param context The context.
     */
    public DefaultQuickSettingsPlugin(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public boolean customizeDisplayDataUsage(boolean isDisplay) {
        Log.i(TAG, "customizeDisplayDataUsage, return isDisplay = " + isDisplay);
        return isDisplay;
    }

    @Override
    public String customizeQuickSettingsTileOrder(String defaultString) {
        return defaultString;
    }

    @Override
    public boolean customizeAddQSTile(boolean needAddQSTile) {
        return needAddQSTile;
    }

    @Override
    public String customizeDataConnectionTile(int dataState, IconIdWrapper icon,
            String orgLabelStr) {
        Log.i(TAG, "customizeDataConnectionTile, icon = " + icon + ", orgLabelStr=" + orgLabelStr);
        return orgLabelStr;
    }

    @Override
    public String customizeDualSimSettingsTile(boolean enable, IconIdWrapper icon,
            String labelStr) {
        Log.i(TAG, "customizeDualSimSettingsTile, enable = " + enable + " icon=" + icon
                + " labelStr=" + labelStr);
        return labelStr;
    }

    @Override
    public void customizeSimDataConnectionTile(int state, IconIdWrapper icon) {
        Log.i(TAG, "customizeSimDataConnectionTile, state = " + state + " icon=" + icon);
    }

    @Override
    public boolean customizeApnSettingsTile(boolean isDisplay) {
        return isDisplay;
    }

    @Override
    public String customizeApnSettingsTile(boolean enable, IconIdWrapper icon, String orgLabelStr) {
        return orgLabelStr;
    }
}