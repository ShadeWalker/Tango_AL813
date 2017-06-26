package com.mediatek.systemui.qs.tiles;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.qs.QSTile;
import com.mediatek.systemui.ext.IQuickSettingsPlugin;
import com.mediatek.systemui.ext.IconIdWrapper;
import com.mediatek.systemui.ext.PluginFactory;

/**
 * M: Dual SIM settings tile Customization for operator .
 */
public class DualSimSettingsTile extends QSTile<QSTile.BooleanState> {
    private static final String TAG = "DualSimSettingsTile";
    private static final Intent DUAL_SIM_SETTINGS = new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.Settings$SimSettingsActivity"));

    private final IconIdWrapper mIconIdWrapper = new IconIdWrapper();
    private final Icon mDualSimSettingsIcon = new QsIconWrapper(mIconIdWrapper);

    /**
     * Constructor.
     * @param host The QSTileHost.
     */
    public DualSimSettingsTile(Host host) {
        super(host);
        registerSimStateReceiver();
   }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
    }

    @Override
    protected void handleClick() {
        final long subId = SubscriptionManager.getDefaultDataSubId();
        Log.d(TAG, "handleClick, " + DUAL_SIM_SETTINGS);
        DUAL_SIM_SETTINGS.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
        mHost.startSettingsActivity(DUAL_SIM_SETTINGS);
        refreshState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;

        Boolean simInserted = (Boolean) arg;
        Log.d(TAG, "handleUpdateState, " + " simInserted=" + simInserted);
        IQuickSettingsPlugin quickSettingsPlugin = PluginFactory
                .getQuickSettingsPlugin(mContext);

        if (simInserted != null && simInserted) {
            state.label = quickSettingsPlugin.customizeDualSimSettingsTile(
                    false, mIconIdWrapper, "");
        } else {
            state.label = quickSettingsPlugin.customizeDualSimSettingsTile(
                    true, mIconIdWrapper, "");
        }
        state.icon = mDualSimSettingsIcon;
    }

    private void registerSimStateReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(mSimStateIntentReceiver, filter);
     }

    // Broadcast receive to determine if SIM is absent or not.
    private BroadcastReceiver mSimStateIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive action is " + action);
            if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                Log.d(TAG, "onReceive action is " + action + " stateExtra=" + stateExtra);
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                    handleRefreshState(false);
                } else {
                    handleRefreshState(true);
                }
            }
        }
    };
}
