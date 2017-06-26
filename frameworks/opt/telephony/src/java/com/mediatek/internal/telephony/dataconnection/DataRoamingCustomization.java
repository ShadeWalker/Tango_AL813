package com.mediatek.internal.telephony.dataconnection;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.WindowManager;

import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.uicc.UiccController;

import com.mediatek.internal.R;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
import com.mediatek.internal.telephony.ltedc.svlte.SvltePhoneProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;

/**
 * Customization from CT for data when roaming.
 * 1, Popup reminder dialog when roaming first time.
 * 2, Update prefer APN according current rat and area.
 */
public class DataRoamingCustomization extends Handler {
    private static final String TAG = "DataRoamingCustomization";

    private static final int EVENT_DATA_OR_ROAMING_SETTING_CHANGED = 1;
    private static final int EVENT_GSM_SERVICE_STATE_CHANGED = 2;
    private static final int EVENT_CDMA_SERVICE_STATE_CHANGED = 3;

    private static final String PREFER_APN_CTNET = "ctnet";
    private static final String PREFER_APN_CTLTE = "ctlte";
    private static final String PREFER_APN_UNKNOWN = "unknown";

    private static final String CHINA_MCC = "460";
    private static final int MCC_LENGTH = 3;

    private static final int APP_FAM_UNKNOWN = 0;

    private static final Uri PREFERAPN_NO_UPDATE_URI_USING_SUBID =
            Uri.parse("content://telephony/carriers/preferapn_no_update/subId/");
    private static final String APN_ID = "apn_id";

    private static final String PREFERENCE_NAME = "roaming_customization";
    private static final String FIRST_ROAMING_KEY = "first_roaming";
    private static final String LAST_REG_STATE_KEY = "last_reg_state";
    private static final String LAST_OPERATOR_NUMERIC_KEY = "last_operator_numeric";

    private static final String OPERATOR_OP09 = "OP09";

    private static final int APN_AUTO_MODE = 0;
    private static final int APN_MANUAL_MODE = 1;

    private Context mContext;
    private ContentResolver mResolver;
    private PhoneBase mGsmPhone;
    private PhoneBase mCdmaPhone;
    private SvltePhoneProxy mSvltePhoneProxy;

    private String mUri = Settings.Global.MOBILE_DATA;

    private int mCurRegState = ServiceState.REGISTRATION_STATE_NOT_REGISTERED_AND_NOT_SEARCHING;
    private String mCurOpNumeric = "00000";

    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            logd("Observer Onchange");
            removeMessages(EVENT_DATA_OR_ROAMING_SETTING_CHANGED);
            sendEmptyMessage(EVENT_DATA_OR_ROAMING_SETTING_CHANGED);
        }
    };

    /**
     * Construct DataRoamingCustomization with context and phone.
     * @param context the context
     * @param gsmPhone GSM phone of svltePhoneProxy
     * @param cdmaPhone CDMA phone of svltePhoneProxy
     * @param svltePhoneProxy SvltePhoneProxy
     */
    public DataRoamingCustomization(Context context, PhoneBase gsmPhone, PhoneBase cdmaPhone,
            SvltePhoneProxy svltePhoneProxy) {
        String operator = SystemProperties.get("ro.operator.optr", "");
        if (operator != null && operator.equals(OPERATOR_OP09)) {
            logd("DataRoamingCustomization constructor");
            mContext = context;
            mGsmPhone = gsmPhone;
            mCdmaPhone = cdmaPhone;
            mSvltePhoneProxy = svltePhoneProxy;
            mResolver = mContext.getContentResolver();
            SharedPreferences roamingPreferences = mContext.getSharedPreferences(
                    PREFERENCE_NAME, 0);
            mCurRegState = roamingPreferences.getInt(LAST_REG_STATE_KEY,
                    ServiceState.REGISTRATION_STATE_NOT_REGISTERED_AND_NOT_SEARCHING);
            mCurOpNumeric = roamingPreferences.getString(LAST_OPERATOR_NUMERIC_KEY, "00000");

            mGsmPhone.registerForServiceStateChanged(
                    this, EVENT_GSM_SERVICE_STATE_CHANGED, null);
            mCdmaPhone.registerForServiceStateChanged(
                    this, EVENT_CDMA_SERVICE_STATE_CHANGED, null);

            IntentFilter filter = new IntentFilter();
            filter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
            mContext.registerReceiver(mIntentReceiver, filter);

        }
    }

    @Override
    public void handleMessage(Message msg) {
        int msgId = msg.what;
        logd("handleMessage: " + msgIdToString(msgId) + "(id=" + msgId + ")");
        switch (msgId) {
        case EVENT_DATA_OR_ROAMING_SETTING_CHANGED:
            checkFirstRoaming();
            break;
        case EVENT_GSM_SERVICE_STATE_CHANGED:
        case EVENT_CDMA_SERVICE_STATE_CHANGED:
            AsyncResult ar = (AsyncResult) msg.obj;
            ServiceState serviceState = (ServiceState) ar.result;
            logd("serviceState = " + serviceState.toString());
            final int dataRegState = serviceState.getDataRegState();
            logd("dataRegState = " + dataRegState);
            if (dataRegState == ServiceState.STATE_IN_SERVICE) {
                final int rilDataRegState = serviceState.getRilDataRegState();
                final String operatorNumeric = serviceState.getOperatorNumeric();
                logd("rilDataRegState = " + rilDataRegState + ",operatorNumeric = " +
                        operatorNumeric + ",mCurRegState = " + mCurRegState + ",mCurOpNumeric = "
                        + mCurOpNumeric);
                if (isMccInvalid(operatorNumeric)) {
                    return;
                }
                if (rilDataRegState != mCurRegState ||
                        (mCurOpNumeric != null && operatorNumeric != null &&
                        !mCurOpNumeric.equals(operatorNumeric))) {
                    if (rilDataRegState == ServiceState.REGISTRATION_STATE_ROAMING) {
                        saveLastRegInfo(rilDataRegState, operatorNumeric);
                        checkFirstRoaming();
                        updatePreferedApn();
                    } else if (rilDataRegState == ServiceState.REGISTRATION_STATE_HOME_NETWORK) {
                        saveLastRegInfo(rilDataRegState, operatorNumeric);
                        setFirstRoamingFlag(true);
                        updatePreferedApn();
                    }
                }
            }
            break;
        default:
            break;
        }
    }

    private boolean isMccInvalid(String opNumeric) {
	/*HQ_xionghaifeng Modify for eng mode crash start*/
        if (opNumeric == null || opNumeric.length() < 3) {
	/*HQ_xionghaifeng Modify for eng mode crash end*/
            logd("isMccInvalid, opNumeric=null");
            return false;
        }
        String mcc = opNumeric.substring(0, MCC_LENGTH);
        logd("isMccInvalid, mcc=" + mcc);
        return (mcc == null) || TextUtils.isEmpty(mcc) || mcc.equals("000") || mcc.equals("N/A");
    }

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            logd("onReceive: action=" + action);
            if (action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
                onSubInfoReady();
            }
        }
    };

    private void onSubInfoReady() {
        logd("onSubInfoReady");
        int subId = getSvlteSubId();
        String newUri = Settings.Global.MOBILE_DATA + subId;
        logd("onSubInfoReady: old uri:" + mUri + ", new uri:" + newUri);

        if (!newUri.equals(mUri)) {
            if (mUri != null && !mUri.equals(Settings.Global.MOBILE_DATA)) {
                mResolver.unregisterContentObserver(mObserver);
            }

            mResolver.registerContentObserver(
                    Settings.Global.getUriFor(newUri), false, mObserver);
            mUri = newUri;

            // Trigger a self change to check whether need to popup prompt
            // dialog, in case the sub info ready is later than network
            // registered.
            mObserver.onChange(true);
        }
    }

    private int getSvlteSubId() {
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

        //TODO: FIXME: need check if all phoneproxys are SvltePhoneProxy.
        int[] subIds = SubscriptionManager.getSubId(SvlteModeController.getActiveSvlteModeSlotId());
        if (subIds != null && subIds.length > 0) {
            subId = subIds[0];
        }
        logd("getSvlteSubId: " + subId);

        return subId;
    }

    private void checkFirstRoaming() {
        if (isMccInvalid(mCurOpNumeric)) {
            return;
        }
        boolean userDataEnabled = Settings.Global.getInt(mResolver, mUri, 1) == 1;
        boolean isRoaming = mCurRegState == ServiceState.REGISTRATION_STATE_ROAMING;
        SharedPreferences roamingPreferences = mContext.getSharedPreferences(PREFERENCE_NAME, 0);
        boolean firstRoaming = roamingPreferences.getBoolean(FIRST_ROAMING_KEY, true);

        int defaultDataSub = SubscriptionManager.getDefaultDataSubId();
        int defaultDataSlot = SubscriptionManager.getSlotId(defaultDataSub);
        boolean isDefaultDataSim = defaultDataSlot == PhoneConstants.SIM_ID_1;

        logd("checkFirstRoaming, userDataEnabled=" + userDataEnabled + ",isRoaming="
                + isRoaming + ",firstRoaming=" + firstRoaming
                + ",defaultDataSub=" + defaultDataSub + ",defaultDataSlot=" + defaultDataSlot);
        if (userDataEnabled && isRoaming && firstRoaming && isDefaultDataSim) {
            popupDialog();
            setFirstRoamingFlag(false);
        }
    }

    private void setFirstRoamingFlag(boolean roaming) {
        logd("setFirstRoamingFlag, roaming=" + roaming);
        SharedPreferences roamingPreferences = mContext.getSharedPreferences(PREFERENCE_NAME, 0);
        Editor roamingEditor = roamingPreferences.edit();
        roamingEditor.putBoolean(FIRST_ROAMING_KEY, roaming);
        roamingEditor.commit();
    }

    private void saveLastRegInfo(int regState, String operatorNumeric) {
        logd("saveLastRegInfo, regState=" + regState + ",operatorNumeric=" + operatorNumeric);
        mCurRegState = regState;
        mCurOpNumeric = operatorNumeric;
        SharedPreferences roamingPreferences = mContext.getSharedPreferences(PREFERENCE_NAME, 0);
        Editor roamingEditor = roamingPreferences.edit();
        roamingEditor.putInt(LAST_REG_STATE_KEY, regState);
        roamingEditor.putString(LAST_OPERATOR_NUMERIC_KEY, operatorNumeric);
        roamingEditor.commit();
    }

    /**
     * Unregister from all events it registered for.
     */
    public void dispose() {
        String operator = SystemProperties.get("ro.operator.optr", "");
        if (operator != null && operator.equals(OPERATOR_OP09)) {
            mResolver.unregisterContentObserver(mObserver);
            mGsmPhone.unregisterForServiceStateChanged(this);
            mCdmaPhone.unregisterForServiceStateChanged(this);
        }
    }

    private void popupDialog() {
        logd("popupDialog for data enabled on roaming network.");
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setMessage(R.string.roaming_message);
        builder.setPositiveButton(R.string.known, null);
        AlertDialog dialog = builder.create();
        dialog.getWindow().setType(
                WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        dialog.show();
    }

    private void updatePreferedApn() {
        if (getPreferApnId() >= 0) {
            logd("Don't update when have prefer apn");
            return;
        }
        PhoneBase psPhone = mSvltePhoneProxy.getPsPhone();
        int psPhoneId = psPhone.getPhoneId();
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            psPhoneId = SvlteUtils.getSvltePhoneIdByPhoneId(psPhoneId);
        }
        String plmnNumeric = TelephonyManager.getTelephonyProperty(
                psPhoneId, TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC, "");
        int apnId = -1;
        String preferApnName = PREFER_APN_CTNET;

        logd("updatePreferedApn, plmnNumeric = " + plmnNumeric + ",psPhoneId = " + psPhoneId);

        if (plmnNumeric != null && !plmnNumeric.equals("")) {
            if (plmnNumeric.startsWith(CHINA_MCC)) {
                // China CT card
                int dataRat = psPhone.getServiceState().getRilDataRadioTechnology();
                preferApnName = getPreferApnNameByRat(dataRat);
                logd("updatePreferedApn, preferApnName = " + preferApnName);
            }
            apnId = getApnIdByName(preferApnName, plmnNumeric);
        }
        logd("updatePreferedApn, apnId = " + apnId);

        if (apnId >= 0) {
            // set prefered apn
            setPreferredApn(apnId);
        }
    }

    private String getPreferApnNameByRat(int rat) {
        final int family = getUiccFamilyByRat(rat);
        logd("getPreferApnNameByRat rat = " + rat + ",family = " + family);
        if (family == UiccController.APP_FAM_3GPP) {
            return PREFER_APN_CTLTE;
        } else if (family == UiccController.APP_FAM_3GPP2) {
            return PREFER_APN_CTNET;
        } else {
            return PREFER_APN_UNKNOWN;
        }
    }

    private static int getUiccFamilyByRat(int radioTech) {
        if (radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
            return APP_FAM_UNKNOWN;
        }

        if ((radioTech >= ServiceState.RIL_RADIO_TECHNOLOGY_IS95A &&
                radioTech <= ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A)
                || radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_B) {
            return UiccController.APP_FAM_3GPP2;
        } else {
            return UiccController.APP_FAM_3GPP;
        }
    }

    private int getApnIdByName(String apnName, String plmn) {
        logd("getApnIdByName: apnName  = " + apnName);

        int apnId = -1;
        String selection = "apn = '" + apnName + "'" + " and numeric = '" + plmn + "'";
        logd("getApnIdByName: selection = " + selection);

        Cursor cursor = null;
        try {
            cursor = mResolver.query(
                   Telephony.Carriers.CONTENT_URI, null, selection, null, null);

            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                apnId = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers._ID));
                logd("getApnIdByName: found, the apn id is:" + apnId);
                return apnId;
                }
        }  finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        logd("getApnIdByName: X not found");
        return -1;
    }

    private void setPreferredApn(int pos) {
        String subId = Long.toString(mSvltePhoneProxy.getPsPhone().getSubId());
        logd("setPreferredApn: subId = " + subId);
        Uri uri = Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, subId);
        logd("setPreferredApn: delete");
        mResolver.delete(uri, null, null);

        if (pos >= 0) {
            logd("setPreferredApn: insert");
            ContentValues values = new ContentValues();
            values.put(APN_ID, pos);
            mResolver.insert(uri, values);
        }
    }

    private int getPreferApnId() {
        int preferApnId = -1;
        String subId = Long.toString(mSvltePhoneProxy.getPsPhone().getSubId());
        Uri uri = Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, subId);
        Cursor cursor = mResolver.query(
                uri, new String[] { "_id", "name", "apn" },
                null, null, Telephony.Carriers.DEFAULT_SORT_ORDER);

        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            preferApnId = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers._ID));
        }
        return preferApnId;
    }

    private String msgIdToString(int id) {
        switch (id) {
        case EVENT_DATA_OR_ROAMING_SETTING_CHANGED:
            return "EVENT_DATA_OR_ROAMING_SETTING_CHANGED";
        case EVENT_GSM_SERVICE_STATE_CHANGED:
            return "EVENT_GSM_SERVICE_STATE_CHANGED";
        case EVENT_CDMA_SERVICE_STATE_CHANGED:
            return "EVENT_CDMA_SERVICE_STATE_CHANGED";
        default:
            return "unknown event";
        }
    }

    private void logd(String s) {
        Rlog.d(TAG, s);
    }
}
