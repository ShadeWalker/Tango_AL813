
package com.mediatek.systemui.ext;

import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * PhoneState module.
 */
public class PhoneStateExt {
    public final int mSlotId;
    public final int mSubId;

    public boolean mMobileVisible = false;
    public int mMobileStrengthIcon = 0;
    public int mMobileDataTypeIcon = 0;
    public boolean mIsMobileTypeIconWide = false;
    public String mMobileDescription;
    public String mMobileTypeDescription;

    public ViewGroup mSignalClusterCombo;
    public ImageView mMobileNetworkType;
    public ViewGroup mMobileGroup;
    public ImageView mMobileStrength;
    public ImageView mMobileType;

    /**
     * Constructs a new PhoneStateExt instance. Inflate PhoneState module.
     *
     * @param slotId The slot Index.
     * @param subId the subId.
     */
    public PhoneStateExt(int slotId, int subId) {
        this.mSlotId = slotId;
        this.mSubId = subId;
    }

    /**
     * Set PhoneStateExt Views.
     *
     * @param signalClusterCombo signal cluster combo root ViewGroup.
     * @param mobileNetworkType Mobile Network Type ImageView.
     * @param mobileGroup Mobile root ViewGroup.
     * @param mobileStrength Mobile signal strength ImageView.
     * @param mobileType Mobile Type ImageView.
     */
    public void setViews(ViewGroup signalClusterCombo, ImageView mobileNetworkType,
            ViewGroup mobileGroup, ImageView mobileStrength, ImageView mobileType) {
        this.mSignalClusterCombo = signalClusterCombo;
        this.mMobileNetworkType = mobileNetworkType;
        this.mMobileGroup = mobileGroup;
        this.mMobileStrength = mobileStrength;
        this.mMobileType = mobileType;
    }

    protected LayoutParams generateLayoutParams() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Apply data to view, run after indicator change.
     *
     * @return true If the State is visible.
     */
    public boolean apply() {
        return true;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        toString(builder);
        return builder.toString();
    }

    protected void toString(final StringBuilder builder) {
        builder.append("mSubId=").append(mSubId).append(',').append("mSlotId=").append(mSlotId);
    }
}
