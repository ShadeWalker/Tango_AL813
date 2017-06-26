package com.mediatek.systemui.ext;

import android.annotation.SuppressLint;
import android.telephony.SubscriptionInfo;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.List;

/**
 * Default ISignalClusterExt Empty implements.
 */
public class DefaultSignalClusterExt implements ISignalClusterExt {

    @Override
    public void setSignalClusterInfo(ISignalClusterInfo signalClusterInfo) {
    }

    @Override
    public void setNetworkControllerExt(INetworkControllerExt networkControllerExt) {
    }

    @Override
    public void setSubs(List<SubscriptionInfo> subs, PhoneStateExt[] states) {
    }

    @Override
    public void setMobileDataIndicators(int subId, boolean mobileVisible,
            ViewGroup signalClusterCombo, ImageView mobileNetworkType,
            ViewGroup mobileGroup, ImageView mobileStrength, ImageView mobileType,
            int mobileStrengthIconId, int mobileDataTypeIconId,
            String mobileDescription, String mobileTypeDescription,
            boolean isMobileTypeIconWide) {
    }

    @Override
    public void onAttachedToWindow(LinearLayout mobileSignalGroup, ImageView noSimsView) {
    }

    @Override
    @SuppressLint("MissingSuperCall")
    public void onDetachedFromWindow() {
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
    }

    @Override
    public void apply() {
    }
}
