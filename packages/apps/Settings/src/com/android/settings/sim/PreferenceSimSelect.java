package com.android.settings.sim;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.Preference;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.RadioButton;
import android.widget.Toast;

import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.ext.ISimManagementExt;
import com.mediatek.settings.ext.ISettingsMiscExt;

import com.android.settings.Utils;
import com.android.settings.R;

import java.util.Iterator;
import android.os.SystemProperties;

public class PreferenceSimSelect extends Preference {

    private static final String TAG = "PreferenceSimSelect";

    private static final String SHARED_PREFERENCES_NAME = "sim_state";

    public static final int DATA_PICK = 0;
    public static final int CALLS_PICK = 1;
    public static final int SMS_PICK = 2;

    private Context mContext;
    private RadioButton mSlot1;
    private RadioButton mSlot2;
    private boolean mSlot1Select = false;
    private boolean mSlot2Select = false;
    private boolean mSlot1Enabled = false;
    private boolean mSlot2Enabled = false;
    private SubscriptionManager mSubscriptionManager;
    private ISimManagementExt mExt;

    private int mPickId;

    private boolean isSwitchDialogShowing = false;

    private SharedPreferences mSharedPreferences = null;

    public PreferenceSimSelect(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutResource(R.layout.preference_sim_select);
        mContext = context;
        mSubscriptionManager = SubscriptionManager.from(context);
        mExt = UtilsExt.getSimManagmentExtPlugin(context);
        mSharedPreferences = mContext.getSharedPreferences(SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE);
    }

    public PreferenceSimSelect(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public PreferenceSimSelect(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.preferenceCategoryStyle);
    }

    public PreferenceSimSelect(Context context) {
        this(context, null);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mSlot1 = (RadioButton) view.findViewById(R.id.slot1_radio);
        mSlot2 = (RadioButton) view.findViewById(R.id.slot2_radio);
        mSlot1.setChecked(mSlot1Select);
        mSlot1.setEnabled(mSlot1Enabled);
        mSlot2.setChecked(mSlot2Select);
        mSlot2.setEnabled(mSlot2Enabled);
        mSlot1.setOnClickListener(mOnClickListener);
        mSlot2.setOnClickListener(mOnClickListener);
    }

    public void setPickId(int pickId) {
        mPickId = pickId;
    }

    public void updateSlotSelectState(int slotId) {
        if (slotId == 0) {
            mSlot1Select = true;
            mSlot2Select = false;
        } else if (slotId == 1) {
            mSlot1Select = false;
            mSlot2Select = true;
        } else {
            mSlot1Select = false;
            mSlot2Select = false;
        }
        if (mSlot1 != null) {
            mSlot1.setChecked(mSlot1Select);
        }
        if (mSlot2 != null) {
            mSlot2.setChecked(mSlot2Select);
        }
    }

    public void updateSlotEnabledState(int slotId, boolean enable) {
        if (slotId == 0) {
            mSlot1Enabled = enable;
        } else if (slotId == 1) {
            mSlot2Enabled = enable;
        }
    }

    private OnClickListener mOnClickListener = new OnClickListener() {

			@Override
			public void onClick(View v) {
				final int selectingSlot = (v == mSlot1) ? 0 : 1;
                final int subId = slotIdToSubId(selectingSlot);
                final int oldSubId = slotIdToSubId((v == mSlot1) ? 1 : 0);
                android.util.Log.i(TAG, "mPickId = " + mPickId + ", selectingSlot = " + selectingSlot);

                switch (mPickId) {
                case DATA_PICK:
                    if (isSwitchDialogShowing) {
                        return;
                    }
                    SubscriptionInfo sir = Utils.findRecordBySubId(mContext,
                            mSubscriptionManager.getDefaultDataSubId());
                    sir = mExt.setDefaultSubId(mContext, sir, 2);
                    final int selectedSlot = (sir == null) ? -1 : sir.getSimSlotIndex();
                    android.util.Log.i(TAG, "DATA_PICK selectedSlot = " + selectedSlot);
                    if (selectedSlot == selectingSlot) {
                        return;
                    }
                    isSwitchDialogShowing = true;
                    AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                    builder.setTitle(R.string.dualcard_switch_alert_title_emui);
                    builder.setMessage(R.string.dualcard_switch_alert_message);
                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

			            @Override
			            public void onClick(DialogInterface dialog, int which) {
                            //yanqing add for HQ01445448
                            TelephonyManager tm = TelephonyManager.from(mContext);
                            boolean enabled = tm.getDataEnabled();
                            android.util.Log.i(TAG, "yanqing enabled = " + enabled);
				            setDefaultDataSubId(mContext, subId);
                            updateSlotSelectState(selectingSlot);
                            if(enabled){
                                tm.setDataEnabled(oldSubId, false);
                                android.util.Log.i(TAG, "yanqing oldSubId = " + oldSubId);
                                android.util.Log.i(TAG, "yanqing subId = " + subId);
                                tm.setDataEnabled(subId, true);
                                tm.setDataEnabled(true);
                            }
			            }

		            });
		            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

			            @Override
			            public void onClick(DialogInterface dialog, int which) {
				            updateSlotSelectState(selectedSlot);
			            }
			
		            });
                    builder.setOnCancelListener(new DialogInterface.OnCancelListener() {

			            @Override
			            public void onCancel(DialogInterface dialog) {
				            updateSlotSelectState(selectedSlot);
			            }
			
		            });
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            isSwitchDialogShowing = false;
                        }
                    });
                    builder.show();
                    break;
                case CALLS_PICK:
                    setUserSelectedOutgoingPhoneAccount(subscriptionIdToPhoneAccountHandle(subId));
                    updateSlotSelectState(selectingSlot);
                    break;
                case SMS_PICK:
                    setDefaultSmsSubId(mContext, subId);
                    updateSlotSelectState(selectingSlot);
                    break;
                }

			}
			
    };

    private static void setDefaultDataSubId(final Context context, final int subId) {
        ISettingsMiscExt miscExt = UtilsExt.getMiscPlugin(context);
        ISimManagementExt simExt = UtilsExt.getSimManagmentExtPlugin(context);
        if (TelecomManager.from(context).isInCall()) {
            String textErr =
                    context.getResources().getString(R.string.default_data_switch_err_msg1);
            textErr = miscExt.customizeSimDisplayString(textErr, subId);
            Toast.makeText(context, textErr, Toast.LENGTH_SHORT).show();
            return;
        }
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
		///HQ_xionghaifeng 20151110 modify for HQ01491175 start @{
		String productName = SystemProperties.get("ro.product.name", "");
		if (!productName.equalsIgnoreCase("TAG-TL00"))
		{
			miscExt.setDefaultDataEnable(context, subId);
	        simExt.setDataState(subId);
			simExt.setDataStateEnable(subId); 
		}
		///@}
		
        subscriptionManager.setDefaultDataSubId(subId);
        String text = context.getResources().getString(R.string.data_switch_started);
        text = miscExt.customizeSimDisplayString(text, subId);
        Toast.makeText(context, text, Toast.LENGTH_LONG).show();
    }

    private int slotIdToSubId(int slotId) {
        SubscriptionInfo sir = Utils.findRecordBySlotId(mContext, slotId);

        if (sir != null) {
            return sir.getSubscriptionId();
        }

        return -1;
    }

    private static void setDefaultSmsSubId(final Context context, final int subId) {
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
        subscriptionManager.setDefaultSmsSubId(subId);
    }

    private void setUserSelectedOutgoingPhoneAccount(PhoneAccountHandle phoneAccount) {
        final TelecomManager telecomManager = TelecomManager.from(mContext);
        telecomManager.setUserSelectedOutgoingPhoneAccount(phoneAccount);
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
}
