package com.mediatek.systemui.ext;

import android.content.Context;
import android.content.ContextWrapper;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.mediatek.common.PluginImpl ;

/**
 * Default implementation of Plug-in definition of Status bar.
 */
@PluginImpl(interfaceName = "com.mediatek.systemui.ext.IStatusBarPlmnPlugin")
public class DefaultStatusBarPlmnPlugin extends ContextWrapper implements IStatusBarPlmnPlugin {
    private static final String TAG = "DefaultStatusBarPlmnPlugin";

    /**
     * Constructs a new DefaultStatusBarPlmnPlugin instance with Context.
     * @param context A Context object
     */
    public DefaultStatusBarPlmnPlugin(Context context) {
        super(context);
    }

    @Override
    public TextView getPlmnTextView(Context context) {
        Log.d(TAG, "into getPlmnTextView: null");
        return null;
    }

    @Override
    public void bindSettingService(Context context) {
        Log.d(TAG, "into bindSettingService");
    }

    @Override
    public boolean supportCustomizeCarrierLabel() {
        Log.d(TAG, "into supportCustomizeCarrierLabel: false");
        return false;
    }

    @Override
    public View customizeCarrierLabel(ViewGroup parentView, View orgCarrierLabel) {
        Log.d(TAG, "into customizeCarrierLabel: null");
        return null;
    }

    @Override
    public void updateCarrierLabelVisibility(boolean force, boolean makeVisible) {
        Log.d(TAG, "into updateCarrierLabelVisibility");
    }

    @Override
    public void updateCarrierLabel(int slotId, boolean isSimInserted, boolean isHasSimService,
            String[] networkNames) {
        Log.d(TAG, "into updateCarrierLabel, slotId=" + slotId
                + ", isSimInserted=" + isSimInserted
                + ", isHasSimService=" + isHasSimService);
    }
}
