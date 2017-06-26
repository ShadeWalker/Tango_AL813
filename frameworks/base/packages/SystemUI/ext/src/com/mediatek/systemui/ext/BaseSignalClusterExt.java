package com.mediatek.systemui.ext;

import android.annotation.SuppressLint;
import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.mediatek.systemui.statusbar.util.SIMHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * M: Base ISignalClusterExt implements.
 */
public abstract class BaseSignalClusterExt implements ISignalClusterExt {
    private static final String TAG = "BaseSignalClusterExt";
    protected static final boolean DEBUG = !FeatureOptionUtils.isUserLoad();

    protected Context mContext;
    protected int mSlotCount = 0;

    protected boolean mWifiVisible = false;
    protected boolean mIsAirplaneMode = false;
    protected boolean mNoSimsVisible = false;
    /// show data activity icon by apn type.
    protected boolean mShouldShowDataActivityIcon = true;
    protected int mWideTypeIconStartPadding = 0;
    protected int mSecondaryTelephonyPadding = 0;

    protected IconIdWrapper mDefaultSignalNullIconId;
    protected IconIdWrapper mDefaultRoamingIconId;

    protected LinearLayout mMobileSignalGroup;
    protected ImageView mNoSimsView;

    protected IStatusBarPlugin mStatusBarPlugin;
    protected INetworkControllerExt mNetworkControllerExt;
    protected ISignalClusterInfo mSignalClusterInfo;
    protected List<SubscriptionInfo> mCurrentSubscriptions = new ArrayList<SubscriptionInfo>();

    protected final BasePhoneStateExt[] mPhoneStates;

    /**
     * Constructs a new BaseSignalClusterExt instance.
     *
     * @param context A Context object.
     * @param statusBarPlugin The IStatusBarPlugin interface object.
     */
    public BaseSignalClusterExt(Context context, IStatusBarPlugin statusBarPlugin) {
        mContext = context;
        mStatusBarPlugin = statusBarPlugin;

        mSlotCount = SIMHelper.getSlotCount();
        mPhoneStates = new BasePhoneStateExt[mSlotCount];

        mDefaultSignalNullIconId = new IconIdWrapper();
        mDefaultRoamingIconId = new IconIdWrapper();

        mNetworkControllerExt = new DefaultNetworkControllerExt();
    }

    @Override
    public void setSignalClusterInfo(ISignalClusterInfo signalClusterInfo) {
        mSignalClusterInfo = signalClusterInfo;
    }

    @Override
    public void setNetworkControllerExt(INetworkControllerExt networkControllerExt) {
        if (networkControllerExt != null) {
            mNetworkControllerExt = networkControllerExt;

            mNetworkControllerExt.getDefaultSignalNullIcon(mDefaultSignalNullIconId);
            mNetworkControllerExt.getDefaultRoamingIcon(mDefaultRoamingIconId);
        }
    }

    @Override
    public void setSubs(List<SubscriptionInfo> subs, PhoneStateExt[] orgStates) {
        Log.d(TAG, "setSubs(), subs = " + subs + ", orgStates = " + orgStates);

        // Clear out all old subIds.
        mCurrentSubscriptions = subs;

        for (int i = SIMHelper.SLOT_INDEX_DEFAULT; i < mSlotCount; i++) {
            if (orgStates[i] != null) {
                if (DEBUG) {
                    Log.d(TAG, "setSubs(), inflatePhoneState orgStates = " + orgStates[i]);
                }
                mPhoneStates[i] = inflatePhoneState(orgStates[i]);
            } else {
                if (DEBUG) {
                    Log.d(TAG, "setSubs(), createDefaultPhoneState IfNecessary");
                }
                mPhoneStates[i] = createDefaultPhoneState(i);
            }
        }

        if (mMobileSignalGroup != null) {
            mMobileSignalGroup.removeAllViews();
        }
        for (BasePhoneStateExt state : mPhoneStates) {
            if (state != null) {
                state.addToSignalGroup();
            }
        }
    }

    private BasePhoneStateExt inflatePhoneState(PhoneStateExt orgState) {
        return inflatePhoneState(orgState.mSlotId, orgState.mSubId,
                orgState.mSignalClusterCombo, orgState.mMobileNetworkType,
                orgState.mMobileGroup, orgState.mMobileStrength,
                orgState.mMobileType);
    }

    private BasePhoneStateExt inflatePhoneState(int slotId, int subId,
            ViewGroup signalClusterCombo, ImageView mobileNetworkType,
            ViewGroup mobileGroup, ImageView mobileStrength, ImageView mobileType) {
        if (SubscriptionManager.isValidSlotId(slotId)) {
            mPhoneStates[slotId] = createPhoneState(slotId, subId,
                    signalClusterCombo, mobileNetworkType,
                    mobileGroup, mobileStrength, mobileType);
            if (DEBUG) {
                Log.d(TAG, "inflatePhoneState(), slotId = " + slotId + ", subId = " + subId
                        + " state = " + mPhoneStates[slotId]);
            }
            return mPhoneStates[slotId];
        } else {
            if (DEBUG) {
                Log.d(TAG, "inflatePhoneState(), slotId = " + slotId + ", subId = " + subId
                        + new IllegalArgumentException("INVALID_SIM_SLOT_ID"));
            }
            return null;
        }
    }

    private BasePhoneStateExt getOrInflatePhoneState(int subId,
            ViewGroup signalClusterCombo, ImageView mobileNetworkType,
            ViewGroup mobileGroup, ImageView mobileStrength, ImageView mobileType) {
        BasePhoneStateExt state = null;
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            final int slotId = getSlotId(subId);
            if (SubscriptionManager.isValidSlotId(slotId)) {
                state = getState(slotId);
                if (state == null) {
                    state = inflatePhoneState(slotId, subId, signalClusterCombo, mobileNetworkType,
                            mobileGroup, mobileStrength, mobileType);
                }
            }
        }
        return state;
    }

    @Override
    public void setMobileDataIndicators(int subId, boolean mobileVisible,
            ViewGroup signalClusterCombo, ImageView mobileNetworkType,
            ViewGroup mobileGroup, ImageView mobileStrength, ImageView mobileType,
            int mobileStrengthIconId, int mobileDataTypeIconId,
            String mobileDescription, String mobileTypeDescription,
            boolean isMobileTypeIconWide) {
        final PhoneStateExt state = getOrInflatePhoneState(
                subId, signalClusterCombo, mobileNetworkType,
                mobileGroup, mobileStrength, mobileType);
        if (state != null) {
            state.mMobileVisible = mobileVisible;
            state.mMobileStrengthIcon = mobileStrengthIconId;
            state.mMobileDataTypeIcon = mobileDataTypeIconId;
            state.mMobileDescription = mobileDescription;
            state.mMobileTypeDescription = mobileTypeDescription;
            state.mIsMobileTypeIconWide = isMobileTypeIconWide;
        } else {
            if (signalClusterCombo != null) {
                if (signalClusterCombo.getParent() != null) {
                    ((ViewGroup) signalClusterCombo.getParent()).removeView(signalClusterCombo);
                }
            }
        }

        if (DEBUG) {
            Log.d(TAG, "setMobileDataIndicators(), state = " + state);
        }
    }

    @Override
    public void onAttachedToWindow(LinearLayout mobileSignalGroup, ImageView noSimsView) {
        mMobileSignalGroup = mobileSignalGroup;
        mNoSimsView = noSimsView;

        for (BasePhoneStateExt state : mPhoneStates) {
            if (state != null) {
                state.addToSignalGroup();
            }
        }
    }

    @Override
    @SuppressLint("MissingSuperCall")
    public void onDetachedFromWindow() {
        if (mMobileSignalGroup != null) {
            mMobileSignalGroup.removeAllViews();
            mMobileSignalGroup = null;
        }
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
    }

    @Override
    public void apply() {
        if (mMobileSignalGroup == null) {
            return;
        }

        mNoSimsVisible = mSignalClusterInfo.isNoSimsVisible();
        mWifiVisible = mSignalClusterInfo.isWifiIndicatorsVisible();
        mIsAirplaneMode = mSignalClusterInfo.isAirplaneMode();
        mWideTypeIconStartPadding = mSignalClusterInfo.getWideTypeIconStartPadding();
        mSecondaryTelephonyPadding = mSignalClusterInfo.getSecondaryTelephonyPadding();

        for (BasePhoneStateExt state : mPhoneStates) {
            if (state == null) {
                Log.d(TAG, "apply(), state == null");
                continue;
            }

            // Sim available
            state.mIsSimInserted = isSimInsertedBySlot(state.mSlotId);
            state.mHasSimService = mNetworkControllerExt.hasService(state.mSubId);
            state.mIsSimAvailable = state.mIsSimInserted && state.mHasSimService;
            state.mIsSimOffline = mNetworkControllerExt.isOffline(state.mSubId)
                    || state.shouldShowOffline();
            state.mDataConnectioned = mNetworkControllerExt.isDataConnected(state.mSubId);

            state.mRoaming = mNetworkControllerExt.isRoaming(state.mSubId);

            state.mSignalStrengthLevel = mNetworkControllerExt.getSignalStrengthLevel(state.mSubId);
            state.mDataType = mNetworkControllerExt.getDataType(state.mSubId);
            state.mNetworkType = mNetworkControllerExt.getNetworkType(state.mSubId);
            state.mDataActivity = mNetworkControllerExt.getDataActivity(state.mSubId);

            if (mStatusBarPlugin.customizeBehaviorSet() == BehaviorSet.OP01_BS &&
                    mNetworkControllerExt.getSvLteController(state.mSubId) != null) {
                mShouldShowDataActivityIcon = mNetworkControllerExt.getSvLteController(state.mSubId)
                        .isShowDataActivityIcon();
            }

            state.apply();
        }
    }

    protected BasePhoneStateExt getState(int slotId) {
        if (slotId >= 0 && slotId < mSlotCount) {
            return mPhoneStates[slotId];
        }
        return null;
    }

    protected BasePhoneStateExt createDefaultPhoneState(int slotId) {
        return null;
    }

    protected abstract BasePhoneStateExt createPhoneState(int slotId, int subId,
            ViewGroup signalClusterCombo, ImageView mobileNetworkType,
            ViewGroup mobileGroup, ImageView mobileStrength, ImageView mobileType);

    protected final int getSlotId(int subId) {
        int slotId = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
        for (SubscriptionInfo subInfo : mCurrentSubscriptions) {
            if (subInfo.getSubscriptionId() == subId) {
                slotId = subInfo.getSimSlotIndex();
                break;
            }
        }
        return slotId;
    }

    protected final int getSubId(int slotId) {
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        for (SubscriptionInfo subInfo : mCurrentSubscriptions) {
            if (subInfo.getSimSlotIndex() == slotId) {
                subId = subInfo.getSubscriptionId();
                break;
            }
        }
        return subId;
    }

    /**
     * Set IconIdWrapper to ImageView.
     *
     * @param imageView ImageView
     * @param icon IconIdWrapper
     */
    protected static final void setImage(final ImageView imageView, final IconIdWrapper icon) {
        if (imageView != null) {
            if (icon != null) {
                if (icon.getResources() != null) {
                    imageView.setImageDrawable(icon.getDrawable());
                } else {
                    if (icon.getIconId() == 0) {
                        imageView.setImageDrawable(null);
                    } else {
                        imageView.setImageResource(icon.getIconId());
                    }
                }
            }
        }
    }

    protected static final String toString(IconIdWrapper icon) {
        if (icon == null) {
            return "null";
        } else {
            return icon.toString();
        }
    }

    protected boolean isMultiSlot() {
        return mSlotCount > 1;
    }

    protected final boolean isSimInsertedBySlot(int slotId) {
        return SIMHelper.isSimInsertedBySlot(mContext, slotId);
    }

    /**
     * PhoneState module.
     */
    protected class BasePhoneStateExt extends PhoneStateExt {
        private static final String TAG = "BaseSignalClusterExt.PhoneStateExt";

        // States
        protected boolean mIsSimInserted;
        protected boolean mHasSimService;
        protected boolean mIsSimAvailable;
        protected boolean mIsSimOffline;

        protected boolean mRoaming;

        protected boolean mDataConnectioned;

        protected int mSignalStrengthLevel;
        protected NetworkType mNetworkType;
        protected DataType mDataType;
        protected int mDataActivity;

        // Views
        protected FrameLayout mMobileNetworkDataGroup = new FrameLayout(mContext);
        protected ImageView mMobileDataActivity = null;

        protected ImageView mMobileRoamingIndicator = null;
        protected ImageView mMobileSlotIndicator = null;

        // IconIds.
        protected IconIdWrapper mMobileStrengthNullIconId = new IconIdWrapper();
        protected IconIdWrapper mMobileStrengthOfflineIconId = new IconIdWrapper();

        protected IconIdWrapper mMobileStrengthIconId = new IconIdWrapper();
        protected IconIdWrapper mMobileDataTypeIconId = new IconIdWrapper();

        protected IconIdWrapper mMobileNetworkTypeIconId = new IconIdWrapper();
        protected IconIdWrapper mMobileDataActivityIconId = new IconIdWrapper();
        protected IconIdWrapper mMobileSlotIndicatorIconId = new IconIdWrapper();

        public BasePhoneStateExt(int slotId, int subId) {
            super(slotId, subId);
        }

        /**
         * Add PhoneState view to mMobileSignalGroup.
         */
        public void addToSignalGroup() {
            if (mMobileSignalGroup != null) {
                if (mSignalClusterCombo.getParent() != null) {
                    ((ViewGroup) mSignalClusterCombo.getParent()).removeView(mSignalClusterCombo);
                }
                mMobileSignalGroup.addView(mSignalClusterCombo);
            }
        }

        @Override
        public boolean apply() {
            Log.d(TAG, "apply(), State=" + this.toString());

            if (mIsAirplaneMode) {
                mMobileNetworkDataGroup.setVisibility(View.GONE);
                mMobileGroup.setVisibility(View.GONE);
            } else {
                // Customize update icons
                customizeIcons();

                // Apply Signal strength
                applyMobileSignalStrength();

                // Apply Roaming.
                applyMobileRoamingIndicator();

                // Apply Slot Indicator
                applyMobileSlotIndicator();

                // Apply Network and data switch
                applyNetworkDataSwitch();

                // Apply Network and data control.
                applyNetworkDataType();

                // Apply Data activity
                applyMobileDataActivity();
            }

            return mMobileVisible;
        }

        protected boolean isNormalVisible() {
            return mMobileVisible && !mIsAirplaneMode && mIsSimAvailable;
        }

        @Override
        protected void toString(final StringBuilder builder) {
            super.toString(builder);
            builder.append(',')
                    .append("mIsSimInserted=").append(mIsSimInserted).append(',')
                    .append("mHasSimService=").append(mHasSimService).append(',')
                    .append("mIsSimOffline=").append(mIsSimOffline).append(',')
                    .append("mDataConnectioned=").append(mDataConnectioned).append(',')
                    .append("mNetworkType=").append(mNetworkType).append(',')
                    .append("mDataType=").append(mDataType).append(',');
        }

        protected void customizeIcons() {
            if (DEBUG) {
                Log.d(TAG, "customizeIcons(), mSlotId = " + mSlotId + ", mSubId = " + mSubId);
            }

            mMobileStrengthIconId.setIconId(mMobileStrengthIcon);
            mMobileStrengthIconId.setResources(mNetworkControllerExt.getResources());

            mStatusBarPlugin.customizeSignalStrengthIcon(mSignalStrengthLevel, mRoaming,
                    mMobileStrengthIconId);

            mStatusBarPlugin.customizeSignalStrengthNullIcon(mSlotId, mMobileStrengthNullIconId);
            mStatusBarPlugin.customizeSignalStrengthOfflineIcon(mSlotId,
                    mMobileStrengthOfflineIconId);

            mStatusBarPlugin.customizeDataTypeIcon(mMobileDataTypeIconId, mRoaming, mDataType);
            if (mStatusBarPlugin.customizeBehaviorSet() == BehaviorSet.OP09_BS) {
                mStatusBarPlugin.customizeDataNetworkTypeIcon(mMobileNetworkTypeIconId, mRoaming,
                        mNetworkType, mNetworkControllerExt.getSvLteController(mSubId));
            } else {
                mStatusBarPlugin.customizeDataNetworkTypeIcon(mMobileNetworkTypeIconId, mRoaming,
                        mNetworkType);
            }
            mStatusBarPlugin.customizeDataActivityIcon(mMobileDataActivityIconId, mDataActivity);
            mStatusBarPlugin.customizeSignalIndicatorIcon(mSlotId, mMobileSlotIndicatorIconId);

            setImage(mMobileType, mMobileDataTypeIconId);
            setImage(mMobileNetworkType, mMobileNetworkTypeIconId);
            setImage(mMobileDataActivity, mMobileDataActivityIconId);
            setImage(mMobileRoamingIndicator, mDefaultRoamingIconId);
            setImage(mMobileSlotIndicator, mMobileSlotIndicatorIconId);
        }

        protected boolean isSignalStrengthNullIcon() {
            final boolean isNullIcon = mMobileStrengthIcon == mDefaultSignalNullIconId
                    .getIconId();
            return isNullIcon;
        }

        /**
         * Apply Signal strength to view.
         */
        protected void applyMobileSignalStrength() {
        }

        /**
         * Apply Slot Indicator to view.
         */
        protected void applyMobileSlotIndicator() {
        }

        /**
         * Apply Roaming Indicator to view.
         */
        protected void applyMobileRoamingIndicator() {
            if (mMobileRoamingIndicator != null) {
                if (DEBUG) {
                    Log.d(TAG, "applyMobileRoamingIndicator(), mSlotId = " + mSlotId
                            + ", mSubId = " + mSubId + ", mRoaming = " + mRoaming);
                }

                if (mRoaming && isNormalVisible() && !isSignalStrengthNullIcon()) {
                    mMobileRoamingIndicator.setPaddingRelative(mWideTypeIconStartPadding,
                            mMobileRoamingIndicator.getPaddingTop(),
                            mMobileRoamingIndicator.getPaddingRight(),
                            mMobileRoamingIndicator.getPaddingBottom());
                    mMobileRoamingIndicator.setVisibility(View.VISIBLE);
                } else {
                    mMobileRoamingIndicator.setVisibility(View.INVISIBLE);
                }
            }
        }

        /**
         * Apply Data activity to view.
         */
        protected void applyMobileDataActivity() {
            if (mMobileDataActivity != null) {
                if (!mDataConnectioned || !isNormalVisible() || isSignalStrengthNullIcon()
                        || !mShouldShowDataActivityIcon) {
                    if (DEBUG) {
                        Log.d(TAG, "applyMobileDataActivity(), mMobileDataActivity is GONE");
                    }
                    mMobileDataActivity.setVisibility(View.INVISIBLE);
                } else {
                    if (DEBUG) {
                        Log.d(TAG, "applyMobileDataActivity(), mMobileDataActivity is VISIBLE");
                    }
                    mMobileDataActivity.setVisibility(View.VISIBLE);
                }
            }
        }

        /**
         * Apply Network & Data Switch.
         */
        protected void applyNetworkDataSwitch() {
            if (DEBUG) {
                Log.d(TAG, "applyNetworkDataSwitch(), mDataConnectioned = " + mDataConnectioned);
            }

            if (mMobileNetworkType == null || mMobileType == null) {
                return;
            }

            if (!isNormalVisible() || isSignalStrengthNullIcon()) {
                if (DEBUG) {
                    Log.d(TAG, "applyNetworkDataSwitch(), "
                            + "No SIM inserted/Service or Signal Strength Null: "
                            + "Hide network type icon and data icon");
                }

                mMobileNetworkDataGroup.setVisibility(View.GONE);
                mMobileNetworkType.setVisibility(View.GONE);
                mMobileType.setVisibility(View.GONE);
            } else {
                if (mWifiVisible) {
                    if (DEBUG) {
                        Log.d(TAG, "applyNetworkDataSwitch(), mWifiVisible = true,"
                                + " Show network type icon, Hide data type icon");
                    }

                    mMobileNetworkType.setVisibility(View.VISIBLE);
                    mMobileType.setVisibility(View.INVISIBLE);
                } else {
                    if (mDataConnectioned
                            && mMobileDataTypeIconId.getIconId() > 0
                            && mMobileDataTypeIcon > 0) {
                        mMobileNetworkType.setVisibility(View.INVISIBLE);
                        mMobileType.setVisibility(View.VISIBLE);
                    } else {
                        mMobileNetworkType.setVisibility(View.VISIBLE);
                        mMobileType.setVisibility(View.INVISIBLE);
                    }
                }

                mMobileNetworkDataGroup.setVisibility(View.VISIBLE);
            }
            if (DEBUG) {
                Log.d(TAG, "applyNetworkDataSwitch()"
                        + ", mMobileNetworkType isVisible: "
                        + (mMobileNetworkType.getVisibility() == View.VISIBLE)
                        + ", mMobileDataType isVisible: "
                        + (mMobileType.getVisibility() == View.VISIBLE));
            }
        }

        /**
         * Apply Network & Data to view.
         */
        protected void applyNetworkDataType() {
        }

        /**
         * Whether should Show Offline.
         *
         * @return true If should Show Offline.
         */
        protected boolean shouldShowOffline() {
            return false;
        }
    }
}
