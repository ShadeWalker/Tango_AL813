package com.mediatek.systemui.ext;

import android.content.Context;
import android.content.ContextWrapper;

import com.mediatek.common.PluginImpl;

/**
 * M: Default implementation of Plug-in definition of Volume Ext.
 */
@PluginImpl(interfaceName = "com.mediatek.systemui.ext.IVolumePlugin")
public class DefaultVolumePlugin extends ContextWrapper implements IVolumePlugin {

    /**
     * Constructs a new DefaultVolumePlugin instance with Context.
     *
     * @param context A Context object.
     */
    public DefaultVolumePlugin(Context context) {
        super(context);
    }

    @Override
    public String customizeZenModeNoInterruptionsTitle(String orgText) {
        return orgText;
    }

}
