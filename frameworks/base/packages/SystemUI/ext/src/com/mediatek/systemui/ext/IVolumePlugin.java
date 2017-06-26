package com.mediatek.systemui.ext;

/**
 * M: the interface for Plug-in definition of Volume Ext.
 */
public interface IVolumePlugin {
    /**
     * Customize Zen mode no interruptions title.
     *
     * @param orgTitle default title.
     * @return Zen mode no interruptions title.
     */
    String customizeZenModeNoInterruptionsTitle(String orgTitle);
}
