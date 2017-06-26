package com.mediatek.settings.sim;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.mediatek.internal.telephony.CellConnMgr;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.ext.ISimManagementExt;

import com.android.settings.sim.UnLockSubDialog;
import com.android.settings.R;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import java.util.Iterator;

/**
 * Radio power manager to control radio state.
 */
public class RadioPowerManager {

    private static final String TAG = "RadioPowerManager";
    private Context mContext;
    private ITelephony mITelephony;
    private static final int MODE_PHONE1_ONLY = 1;
    private TelephonyManager mTelephonyManager;
    private ISimManagementExt mExt;
	private SubscriptionManager mSubscriptionManager;
   /**
    * Constructor.
    * @param context Context
    */
    public RadioPowerManager(Context context) {
        mContext = context;
        mITelephony = ITelephony.Stub.asInterface(ServiceManager.getService(
                Context.TELEPHONY_SERVICE));
        mTelephonyManager = TelephonyManager.from(mContext);
        mExt = UtilsExt.getSimManagmentExtPlugin(mContext);
		mSubscriptionManager = SubscriptionManager.from(context);
    }

    /**
     * Bind the preference with corresponding property.
     * @param preference {@link RadioPowerPreference}.
     * @param subId subId
     */
    public void bindPreference(final RadioPowerPreference preference, final int subId) {
        /* begin: change by donghongjing for HQ01342984 */
        preference.setRadioOn(TelephonyUtils.isRadioOn(subId)
                && (UnLockSubDialog.getCurrentStateForSubId(mContext, subId) != CellConnMgr.STATE_SIM_LOCKED));
        preference.setRadioEnabled(SubscriptionManager.isValidSubscriptionId(subId)
                && !TelephonyUtils.isAirplaneModeOn(mContext));
        /* end: change by donghongjing for HQ01342984 */
        preference.setRadioPowerChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                /* begin: change by donghongjing for HQ01382704 */
                preference.setRadioOn(!isChecked);
                if (TelecomManager.from(mContext).isInCall()) {
                    Toast.makeText(mContext, R.string.toast_in_call, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (UnLockSubDialog.showDialog((Activity) mContext, subId)) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                builder.setMessage(R.string.popup_default_id);
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

		            @Override
		            public void onClick(DialogInterface dialog, int which) {
                        boolean isChecked = !preference.getRadioOn();
                        if (isChanged(subId, isChecked)) {
                            preference.setRadioOn(isChecked);
                            setRadionOn(isChecked, subId);
                            preference.setRadioEnabled(false);
                            updateRadioMsimDb(isChecked, subId);
                        } else {
                            preference.setRadioOn(!isChecked);
                        }
		            }

	            });
	            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

		            @Override
		            public void onClick(DialogInterface dialog, int which) {
		            }

	            });
                builder.show();
                /* end: change by donghongjing for HQ01382704 */
            }
        });
    }

    private boolean isChanged(int subId , boolean turnOn) {
        boolean isChanged = false;
        int slotId = SubscriptionManager.getSlotId(subId);
        ITelephonyEx telephonyEx = ITelephonyEx.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
        if (SubscriptionManager.isValidSlotId(slotId)) {
            Bundle bundle = null;
            try {
                if (telephonyEx != null) {
                    bundle = telephonyEx.getServiceState(subId);
                } else {
                    Log.d(TAG, "mTelephonyEx is null, returen false");
                }
            } catch (RemoteException e) {
                Log.d(TAG, "getServiceState() error, subId: " + subId);
                e.printStackTrace();
            }
            if (bundle != null) {
                ServiceState serviceState = ServiceState.newFromBundle(bundle);
                if ((serviceState.getState() !=
                        ServiceState.STATE_POWER_OFF) != turnOn) {
                    isChanged = true;
                }
            }
        }
        Log.d(TAG, "isRadioSwitchComplete(" + subId + ")" + ", slotId: " + slotId
                + ", isChanged: " + isChanged);
        return isChanged;
    }

    protected void setRadionOn(boolean isChecked, int subId) {
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            try {
                mITelephony.setRadioForSubscriber(subId, isChecked);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateRadioMsimDb(boolean isChecked, int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            Log.d(TAG, "updateRadioMsimDb, subId id is invalid");
            return;
        }
        int priviousSimMode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.MSIM_MODE_SETTING, -1);
        Log.i(TAG, "updateRadioMsimDb, The current dual sim mode is " + priviousSimMode
                + ", with subId = " + subId);
        int currentSimMode;
        boolean isRadioOn = false;
        int slot = SubscriptionManager.getSlotId(subId);
        int modeSlot = MODE_PHONE1_ONLY << slot;
        if ((priviousSimMode & modeSlot) > 0) {
            currentSimMode = priviousSimMode & (~modeSlot);
            isRadioOn = false;
        } else {
            currentSimMode = priviousSimMode | modeSlot;
            isRadioOn = true;
        }
        if (mTelephonyManager.getPhoneCount()
        <= PhoneConstants.MAX_PHONE_COUNT_DUAL_SIM) {
            // Auto open the other card's data connection. when current card is
            // radio off
            mExt.setToClosedSimSlot(-1);
            if (TelephonyUtils.isAllSlotRadioOn(mContext) && (!isChecked)) {
                // Auto open the other card's data connection. when current card
                // is radio off
                mExt.setToClosedSimSlot(slot);
                Log.d(TAG, "setToClosedSimSlot " + slot);
                mExt.setToClosedSimSlotSwitch(slot, mContext);
            }
        }
        Log.d(TAG, "currentSimMode=" + currentSimMode + " isRadioOn=" + isRadioOn
                + ", isChecked: " + isChecked);

		///HQ_xionghaifeng 20151027 add for HQ01465886 start @{
		boolean statusBarDataIsOpen = Settings.Global.getInt(mContext.getContentResolver(),
			Settings.Global.MOBILE_DATA, 0) != 0;
		if (priviousSimMode == 0 && isRadioOn == true)
		{
			Log.i("xionghaifeng", "RadioPowerManager priviousSimMode " + priviousSimMode
                + ", with subId = " + subId + " statusBarDataIsOpen " + statusBarDataIsOpen);
			
			mSubscriptionManager.setDefaultDataSubId(subId);
			mSubscriptionManager.setDefaultSmsSubId(subId);
			PhoneAccountHandle phoneAccountHandle = subscriptionIdToPhoneAccountHandle(subId);
			setUserSelectedOutgoingPhoneAccount(phoneAccountHandle);
				
			if (statusBarDataIsOpen)
			{
				mTelephonyManager.setDataEnabled(subId, true);
			}
			else
			{
				mTelephonyManager.setDataEnabled(subId, false);
			}
		}
		///HQ_xionghaifeng 20151027 add for HQ01465886 end @}
		
        if (isChecked == isRadioOn) {
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.MSIM_MODE_SETTING, currentSimMode);
        } else {
            Log.w(TAG, "quickly click don't allow.");
        }
    }

	 private PhoneAccountHandle subscriptionIdToPhoneAccountHandle(final int subId) {
        final TelecomManager telecomManager = TelecomManager.from(mContext);
        final Iterator<PhoneAccountHandle> phoneAccounts =
                telecomManager.getCallCapablePhoneAccounts().listIterator();

        while (phoneAccounts.hasNext()) {
            final PhoneAccountHandle phoneAccountHandle = phoneAccounts.next();
            final PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
            final String phoneAccountId = phoneAccountHandle.getId();

            if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                    && TextUtils.isDigitsOnly(phoneAccountId)
                    && Integer.parseInt(phoneAccountId) == subId){
                return phoneAccountHandle;
            }
        }

        return null;
    }

	private void setUserSelectedOutgoingPhoneAccount(PhoneAccountHandle phoneAccount) {
        final TelecomManager telecomManager = TelecomManager.from(mContext);
        telecomManager.setUserSelectedOutgoingPhoneAccount(phoneAccount);
    }
}