package com.mediatek.systemui.ext;

import android.content.res.Resources;

/**
 * M: Default INetworkControllerExt Empty implements.
 */
public class DefaultNetworkControllerExt implements INetworkControllerExt {

    @Override
    public boolean isShowAtLeast3G() {
        return false;
    }

    @Override
    public boolean isHspaDataDistinguishable() {
        return false;
    }

    @Override
    public boolean hasMobileDataFeature() {
        return true;
    }

    @Override
    public Resources getResources() {
        return null;
    }

    @Override
    public void getDefaultSignalNullIcon(IconIdWrapper icon) {
    }

    @Override
    public void getDefaultRoamingIcon(IconIdWrapper icon) {
    }

    @Override
    public boolean hasService(int subId) {
        return false;
    }

    @Override
    public boolean isDataConnected(int subId) {
        return false;
    }

    @Override
    public boolean isEmergencyOnly(int subId) {
        return false;
    }

    @Override
    public boolean isRoaming(int subId) {
        return false;
    }

    @Override
    public int getSignalStrengthLevel(int subId) {
        return 0;
    }

    @Override
    public NetworkType getNetworkType(int subId) {
        return null;
    }

    @Override
    public DataType getDataType(int subId) {
        return null;
    }

    @Override
    public int getDataActivity(int subId) {
        return 0;
    }

    @Override
    public boolean isOffline(int subId) {
        return false;
    }

    @Override
    public boolean isLteTddSingleDataMode(int subId) {
        return false;
    }

    @Override
    public boolean isRoamingGGMode() {
        return false;
    }

    @Override
    public SvLteController getSvLteController(int subId) {
        return null;
    }
}
