package com.mediatek.systemui.ext;

import android.view.ViewGroup;

/**
 * M: the interface for Plug-in definition of QuickSettings.
 */
public interface IQuickSettingsPlugin {

    /**
     * Customize create the data usage tile.
     * @param isDisplay The default value.
     * @return if true create data usage; If false not create.
     */
    boolean customizeDisplayDataUsage(boolean isDisplay);

    /**
     * Customize the quick settings tile order.
     * @param defaultString the default String;
     * @return the tiles strings splited by comma.
     */
    String customizeQuickSettingsTileOrder(String defaultString);

    /**
     * Customize additional quick settings tile.
     * @param needAddQSTile The default whether need additional quick settings tile.
     * @return if true need additional quick settings tile.
     */
    boolean customizeAddQSTile(boolean needAddQSTile);

    /**
     * Customize the data connection tile view.
     * @param dataState The data state.
     * @param icon The icon wrapper.
     * @param orgLabelStr The dual data connection tile label.
     * @return the tile label.
     */
    String customizeDataConnectionTile(int dataState, IconIdWrapper icon, String orgLabelStr);

    /**
     * Customize the dual Sim Settings.
     * @param enable true is enable.
     * @param icon The icon wrapper.
     * @param labelStr The dual sim quick settings icon label
     * @return the tile label.
     */
    String customizeDualSimSettingsTile(boolean enable, IconIdWrapper icon, String labelStr);

    /**
     * Customize the sim data connection tile.
     * @param state The sim data state.
     * @param icon The icon wrapper.
     */
    void customizeSimDataConnectionTile(int state, IconIdWrapper icon);

    /**
     * Customize create apn settings tile.
     * @param isDisplay The default value.
     * @return if true create apn settings tile; If false not create.
     */
    boolean customizeApnSettingsTile(boolean isDisplay);
    /**
     * Customize the apn settings tile.
     *
     * @param enable true is enable.
     * @param icon The icon wrapper.
     * @param orgLabelStr The apn settings tile label.
     * @return the tile label.
     */
    String customizeApnSettingsTile(boolean enable, IconIdWrapper icon, String orgLabelStr);
}
