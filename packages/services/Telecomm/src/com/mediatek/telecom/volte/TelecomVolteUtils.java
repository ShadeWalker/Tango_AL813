package com.mediatek.telecom.volte;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.server.telecom.R;
import com.android.server.telecom.ErrorDialogActivity;
import com.mediatek.telecom.TelecomManagerEx;

public class TelecomVolteUtils {

    private static final String LOG_TAG = "TelecomVolteUtils";

    public static final String EXTRA_IS_IMS_CALL = "com.mediatek.phone.extra.ims";
    public static final String ACTION_IMS_SETTING = "android.settings.WIRELESS_SETTINGS";

    public static final boolean MTK_IMS_SUPPORT = SystemProperties.get("ro.mtk_ims_support")
            .equals("1");
    public static final boolean MTK_VOLTE_SUPPORT = SystemProperties.get("ro.mtk_volte_support")
            .equals("1");

    public static boolean isVolteSupport() {
        return MTK_IMS_SUPPORT && MTK_VOLTE_SUPPORT;
    }

    private static boolean isEqualForRelatedType(Object oldValue, Object newValue) {
        boolean result = false;
        if ((oldValue instanceof Boolean) && (newValue instanceof Boolean)) {
            if ((Boolean)newValue == (Boolean)oldValue) {
                result = true;
            }
        } else if ((oldValue instanceof Integer) && (newValue instanceof Integer)) {
            if ((Integer)newValue == (Integer)oldValue) {
                result = true;
            }
        } else if ((oldValue instanceof String) && (newValue instanceof String)) {
            if (TextUtils.equals((String)newValue, (String)oldValue)) {
                result = true;
            }
        } else {
            log("not expected type, need check!");
        }
        return result;
    }

    private static boolean updateCertainExtra(Bundle targetExtras, Bundle newExtras, String key) {
        boolean isChanged = false;

        if (newExtras.containsKey(key)) {
            Object newValue = newExtras.get(key);
            if (!targetExtras.containsKey(key)) {
                isChanged = true;
            } else {
                Object oldValue = targetExtras.get(key);
                if (!isEqualForRelatedType(oldValue, newValue)) {
                    isChanged = true;
                }
            }
            if (isChanged) {
                if (newValue instanceof Boolean) {
                    targetExtras.putBoolean(key, (Boolean)newValue);
                } else if (newValue instanceof Integer) {
                    targetExtras.putInt(key, (Integer)newValue);
                } else if (newValue instanceof String) {
                    targetExtras.putString(key, (String)newValue);
                }
            }
        }
        if (isChanged) {
            log("updateCertainExtra()... The key changed: " + key);
        }
        return isChanged;
    }

//----------------------------For volte ims call only------------------------------------
    public static boolean isImsCallOnlyRequest(Intent intent) {
        boolean result = false;
        if (isVolteSupport() && intent != null) {
            Uri handle = intent.getData();
            if (handle != null) {
                String scheme = handle.getScheme();
                String uriString = handle.getSchemeSpecificPart();
                if (PhoneAccount.SCHEME_TEL.equals(scheme)
                        && PhoneNumberUtils.isUriNumber(uriString)) {
                    result = true;
                }
            }
            // maybe we can use extra to decide, just like KK
            // result = intent.getBooleanExtra(EXTRA_IS_IMS_CALL, false);
        }
        return result;
    }

    /**
     * re-get handle uri from intent.
     * For Ims only(tel:xxx@xx), will be changed to sip:xxx@xx in some judge, then we re-get it.
     * @param intent
     * @param defaultHandle
     * @return
     */
    public static Uri getHandleFromIntent(Intent intent, Uri defaultHandle) {
        Uri handle = defaultHandle;
        if (intent != null) {
            handle = intent.getData();
        }
        if (handle == null) {
            log("getHandleFromIntent()... handle is null, need check!");
        }
        return handle;
    }

    public static boolean isImsEnabled(Context context) {
        boolean isImsEnabled = (1 == Settings.Global.getInt(
                context.getContentResolver(), Settings.Global.IMS_SWITCH, 0));
        return isImsEnabled;
    }

    public static void showImsDisableDialog(Context context) {
        final Intent intent = new Intent(context, ErrorDialogActivity.class);
        intent.putExtra(ErrorDialogActivity.SHOW_IMS_DISABLE_DIALOG_EXTRA, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivityAsUser(intent, UserHandle.CURRENT);
    }

    public static void showNoImsServiceDialog(Context context) {
        // for now, we use "Call not sent."
        final Intent errorIntent = new Intent(context, ErrorDialogActivity.class);
        int errorMessageId = -1;
        errorMessageId = R.string.outgoing_call_failed;
        if (errorMessageId != -1) {
            errorIntent.putExtra(ErrorDialogActivity.ERROR_MESSAGE_ID_EXTRA, errorMessageId);
        }
        errorIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivityAsUser(errorIntent, UserHandle.CURRENT);
    }

    public static void checkAndCopyImsExtra(Intent src, Intent dst) {
        if (isVolteSupport()) {
            boolean isImsCallOnly = src.getBooleanExtra(EXTRA_IS_IMS_CALL, false);
            if (isImsCallOnly) {
                dst.putExtra(EXTRA_IS_IMS_CALL, true);
            }
        }
    }

    //-------------For VoLTE conference dial (one key conference)------------------
    public static boolean isConferenceDialRequest(Intent intent) {
        boolean result = false;
        if (isVolteSupport() && intent != null) {
            if (intent.hasExtra(TelecomManagerEx.EXTRA_VOLTE_CONF_CALL_DIAL)) {
                result = intent.getBooleanExtra(TelecomManagerEx.EXTRA_VOLTE_CONF_CALL_DIAL, false);
            }
        }
        log("isConferenceDialRequest()...result : " + result);
        return result;
    }

    public static List<String> getConferenceDialNumbers(Intent intent) {
        List<String> numbers = new ArrayList<String>();
        if (isVolteSupport() && intent != null) {
            numbers = intent.getStringArrayListExtra(TelecomManagerEx.EXTRA_VOLTE_CONF_CALL_NUMBERS);
        }
        if (numbers == null || numbers.isEmpty()) {
            log("getConferenceDialNumbers()...Can not get any number from intent, need check!");
        }
        return numbers;
    }

    public static boolean containsEccNumber(Context context, List<String> numbers) {
        boolean result = false;
        if (context != null && numbers != null && !numbers.isEmpty()) {
            for (String number : numbers) {
                result = PhoneNumberUtils.isPotentialLocalEmergencyNumber(context, number);
                if (result) {
                    break;
                }
            }
        }
        return result;
    }

    public static boolean isConferenceInvite(Bundle extras) {
        boolean result = false;
        if (extras != null && extras.containsKey(TelecomManagerEx.EXTRA_VOLTE_CONF_CALL_INCOMING)) {
            result = extras.getBoolean(TelecomManagerEx.EXTRA_VOLTE_CONF_CALL_INCOMING, false);
        }
        log("isConferenceInvite()...result : " + result);
        return result;
    }

    //-------------For VoLTE normal call switch to ECC------------------
    public static boolean updateVolteEccExtra(Bundle targetExtras, final Bundle newExtras) {
        boolean isChanged = false;
        if (isVolteSupport()) {
            isChanged = updateCertainExtra(targetExtras, newExtras,
                    TelecomManagerEx.EXTRA_VOLTE_MARKED_AS_EMERGENCY);
        }
        return isChanged;
    }

    public static boolean isVolteEcc(final Bundle bundle, boolean defaultValue) {
        boolean result = false;

        if (bundle.containsKey(TelecomManagerEx.EXTRA_VOLTE_MARKED_AS_EMERGENCY)) {
            Object value = bundle.get(TelecomManagerEx.EXTRA_VOLTE_MARKED_AS_EMERGENCY);
            if (value instanceof Boolean) {
                result = (Boolean)value;
            }
        }

        return result;
    }

    //-------------For VoLTE PAU field------------------
    public static boolean updateVoltePauFieldExtra(Bundle targetExtras, Bundle newExtras) {
        boolean isChanged = false;
        if (isVolteSupport()) {
            isChanged = updateCertainExtra(targetExtras, newExtras,
                    TelecomManagerEx.EXTRA_VOLTE_PAU_FIELD);
        }
        return isChanged;
    }

    /**
     * This function to get proper number from "from" and pau information.
     * if "from"(connection.getAddress() is null, always show "unknown"), so here do nothing.
     * if "from" is not null, and pau is not null, use number in pau to replace.
     * @param defaultNumber
     * @param pauField
     * @return
     */
    public static Uri updateHandle(Bundle bundle, Uri defaultHandle) {
        Uri handle = defaultHandle;
        if (bundle == null || !bundle.containsKey(TelecomManagerEx.EXTRA_VOLTE_PAU_FIELD)) {
            log("updateHandle()... bundle is null, or not contain pau field, return default handle: "
                    + handle);
            return handle;
        }
        String pauField = bundle.getString(TelecomManagerEx.EXTRA_VOLTE_PAU_FIELD,"");
        if (isVolteSupport() && handle != null) {
            String number = handle.getSchemeSpecificPart();
            if (!TextUtils.isEmpty(number) && !TextUtils.isEmpty(pauField)) {
                String numberInPAU = getNumberFromPAU(pauField);
                String sipNumberInPAU = getSipNumberFromPAU(pauField);
                if (!TextUtils.isEmpty(numberInPAU)) {
                    number = numberInPAU;
                } else if (!TextUtils.isEmpty(sipNumberInPAU)) {
                    number = sipNumberInPAU;
                }
            }
            handle = Uri.fromParts( "tel", number, null);
            log("updateHandle()... handle changed: " + defaultHandle + " -> " + handle);
        }
        return handle;
    }

    private static final String PAU_FIELD_NUMBER = "<tel:";
    private static final String PAU_FIELD_NAME = "<name:";
    private static final String PAU_FIELD_SIP_NUMBER = "<sip:";
    private static final String PAU_FIELD_END_FLAG = ">";
    private static final String[] absentNumbers = {"anonymous"};

    public static String getNumberFromPAU(String pau) {
        String number = "";
        if (!TextUtils.isEmpty(pau)) {
            number = getFieldValue(pau, PAU_FIELD_NUMBER);
        }
        Log.d(LOG_TAG, "getNumberFromPAU()... number / pau : " + number + " / " + pau);
        return number;
    }

    public static String getNameFromPAU(String pau) {
        String name = "";
        if (!TextUtils.isEmpty(pau)) {
            name = getFieldValue(pau, PAU_FIELD_NAME);
        }
        Log.d(LOG_TAG, "getNameFromPAU()... name / pau : " + name + " / " + pau);
        return name;
    }

    public static String getSipNumberFromPAU(String pau) {
        String sipNumber = "";
        if (!TextUtils.isEmpty(pau)) {
            sipNumber = getFieldValue(pau, PAU_FIELD_SIP_NUMBER);
        }
        Log.d(LOG_TAG, "getSipNumberFromPAU()... sipNumber / pau : " + sipNumber + " / " + pau);

        // If The sip number is comprised with digit only, then return number without domain name. Eg, "+14253269830@10.174.2.2" => "+14253269830".
        // and if is not only comprised with digit, then return number + domain name. Eg, "Baicolin@iptel.org", then return "Baicolin@iptel.org".
        // the first digit maybe contains "+" or "-", like "+10010",  then we think it as the first case.
        if (!TextUtils.isEmpty(sipNumber) && sipNumber.contains("@")) {
            int index = sipNumber.indexOf("@");
            String realNumber = sipNumber.substring(0, index);
            realNumber = realNumber.trim();
            if (realNumber.matches("^[+-]*[0-9]*$")) {
                sipNumber = realNumber;
            }
        }
        return sipNumber;
    }

    private static String getFieldValue(String pau, String field) {
        String value = "";
        if (TextUtils.isEmpty(pau) || TextUtils.isEmpty(field)) {
            Log.e(LOG_TAG, "getFieldValue()... pau or field is null !");
            return value;
        }

        if (!pau.contains(field)) {
            Log.i(LOG_TAG, "getFieldValue()... There is no such field in pau !" + " field / pau :" + field + " / " + pau);
            return value;
        }

        int startIndex = pau.indexOf(field);
        startIndex += field.length();
        int endIndex = pau.indexOf(PAU_FIELD_END_FLAG, startIndex);
        value = pau.substring(startIndex, endIndex);
        return value;
    }

    /**
     * we think "anonymous" as lack of address.
     * @param handle
     * @return
     */
    public static boolean isAbsentNumber(Uri handle) {
        boolean result = false;
        if (isVolteSupport() && handle != null) {
            String number = handle.getSchemeSpecificPart();
            if (!TextUtils.isEmpty(number)) {
                String formatNumber = number.replace(" ", "");
                formatNumber = formatNumber.toLowerCase();
                if (Arrays.asList(absentNumbers).contains(formatNumber)) {
                    result = true;
                }
            }
        }
        return result;
    }

    //--------------[VoLTE_SS] notify user when volte mmi request while data off-------------
    /**
     * Check whether the disconnect call is a mmi dial request with data off case.
     * @param disconnectCause use this info to check
     */
    public static boolean isMmiWithDataOff(DisconnectCause disconnectCause) {
        boolean result = false;
        if (disconnectCause != null) {
            int disconnectCode = disconnectCause.getCode();
            String disconnectReason = disconnectCause.getReason();
            if (disconnectCode == DisconnectCause.ERROR && !TextUtils.isEmpty(disconnectReason)
                    && disconnectReason.contains(
                    TelecomManagerEx.DISCONNECT_REASON_VOLTE_SS_DATA_OFF)) {
                result = true;
            }
        }
        return result;
    }

    /**
     * Notify user to open data connection.
     * @param context
     * @param phoneAccountHandle
     */
    public static void showNoDataDialog(Context context, PhoneAccountHandle phoneAccountHandle) {
        if (context == null || phoneAccountHandle == null) {
            log("showNoDataDialog()... context or phoneAccountHandle is null, need check!");
            return;
        }
        String subId = phoneAccountHandle.getId();
        String ErrorMessage = context.getString(
                R.string.volte_ss_not_available_tips,
                getSubDisplayName(context, Integer.parseInt(subId)));
        final Intent errorIntent = new Intent(context, ErrorDialogActivity.class);
        errorIntent.putExtra(ErrorDialogActivity.EXTRA_ERROR_MESSAGE, ErrorMessage);
        errorIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivityAsUser(errorIntent, UserHandle.CURRENT);
    }

    /**
     * Get the sub's display name.
     * @param subId the sub id
     * @return the sub's display name, may return null
     */
    private static String getSubDisplayName(Context context, int subId) {
        String displayName = "";
        SubscriptionInfo subInfo = SubscriptionManager.from(context).getActiveSubscriptionInfo(subId);
        if (subInfo != null) {
            displayName = subInfo.getDisplayName().toString();
        }
        if (TextUtils.isEmpty(displayName)) {
            log("getSubDisplayName()... subId / subInfo: " + subId + " / " + subInfo);
        }
        return displayName;
    }

    public static void dumpVolteExtra(Bundle extra) {
        if (extra == null) {
            log("dumpVolteExtra()... no extra to dump !");
            return;
        }
        log("----------dumpVolteExtra begin-----------");
        if (extra.containsKey(TelecomManagerEx.EXTRA_VOLTE_MARKED_AS_EMERGENCY)) {
            log(TelecomManagerEx.EXTRA_VOLTE_MARKED_AS_EMERGENCY + " = "
                    + extra.getBoolean(TelecomManagerEx.EXTRA_VOLTE_MARKED_AS_EMERGENCY));
        }
        if (extra.containsKey(TelecomManagerEx.EXTRA_VOLTE_PAU_FIELD)) {
            log(TelecomManagerEx.EXTRA_VOLTE_PAU_FIELD + " = "
                    + extra.getString(TelecomManagerEx.EXTRA_VOLTE_PAU_FIELD));
        }
        log("----------dumpVolteExtra end-----------");
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, ">>>>>" + msg);
    }
}
