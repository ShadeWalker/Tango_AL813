package com.mediatek.systemui.ext;

import android.content.res.Resources;

/**
 * M: NetworkController ext interface to support
 * "Operator plugin - Data activity/type, strength icon".
 */
public interface INetworkControllerExt {

    /**
     * Whether show at least 3G.
     *
     * @return true If show at least 3G.
     */
    boolean isShowAtLeast3G();

    /**
     * Whether Hspa Data type Distinguishable.
     *
     * @return true If Distinguishable Hspa Data.
     */
    boolean isHspaDataDistinguishable();

    /**
     * Whether has MobileDataFeature.
     *
     * @return true If has MobileDataFeature.
     */
    boolean hasMobileDataFeature();

    /**
     * Get Host AP Resources.
     *
     * @return Resources.
     */
    Resources getResources();

    /**
     * Get default signal null icon id.
     *
     * @param icon The default signal null icon wrapper.
     */
    void getDefaultSignalNullIcon(IconIdWrapper icon);

    /**
     * Get default roaming icon id.
     *
     * @param icon The roaming icon wrapper.
     */
    void getDefaultRoamingIcon(IconIdWrapper icon);

    /**
     * Whether Sim service is available.
     *
     * @param subId The subId.
     * @return true If has sim service.
     */
    boolean hasService(int subId);

    /**
     * Whether data is connected.
     *
     * @param subId The subId.
     * @return true If data is connected.
     */
    boolean isDataConnected(int subId);

    /**
     * Whether Sim is Emergency Only.
     *
     * @param subId The subId.
     * @return true If Sim is Emergency Only.
     */
    boolean isEmergencyOnly(int subId);

    /**
     * Whether is Roaming.
     *
     * @param subId The subId.
     * @return true If is Roaming.
     */
    boolean isRoaming(int subId);

    /**
     * Get Signal Strength Level.
     *
     * @param subId The subId.
     * @return Signal Strength Level.
     */
    int getSignalStrengthLevel(int subId);

    /**
     * Get NetworkType by subId.
     *
     * @param subId The subId.
     * @return NetworkType.
     */
    NetworkType getNetworkType(int subId);

    /**
     * Get DataType by subId.
     *
     * @param subId The subId.
     * @return DataType.
     */
    DataType getDataType(int subId);

    /**
     * Get the data activity index.
     *
     * @param subId The subId.
     * @return the data activity index.
     */
    int getDataActivity(int subId);

    /**
     * Whether Sim is Offline.
     *
     * @param subId The subId.
     * @return true If Sim is Offline.
     */
    boolean isOffline(int subId);

    /**
     * Whether Sim is LTE TDD single data mode.
     *
     * @param subId The subId.
     * @return true If Sim is LTE TDD single data mode.
     */
    boolean isLteTddSingleDataMode(int subId);

    /**
     * Whether is Roaming GG Mode.
     *
     * @return true If is Roaming GG Mode.
     */
    boolean isRoamingGGMode();

    /**
     * Get the SV LTE Controller.
     *
     * @param subId The subId.
     * @return the SV LTE Controller.
     */
    SvLteController getSvLteController(int subId);
}