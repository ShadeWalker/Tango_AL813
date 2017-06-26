package com.mediatek.settings.ext;

import java.util.ArrayList;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.ServiceManager;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;

import com.android.internal.telephony.TelephonyIntents;
import com.mediatek.internal.telephony.ITelephonyEx;

/* Dummy implmentation , do nothing */
public class DefaultApnSettingsExt implements IApnSettingsExt {

    private static final String TAG = "DefaultApnSettingsExt";
    private static final String TYPE_MMS = "mms";
    private static final String TYPE_IMS = "ims";
    private static final String CMMAIL_TYPE = "cmmail";
    private static final String RCSE_TYPE = "rcse";
    private static final String TYPE_IA = "ia";

    public static final String PREFERRED_APN_URI = "content://telephony/carriers/preferapn";

    public static final int MENU_NEW = Menu.FIRST;
    public static final int MENU_RESTORE = Menu.FIRST + 1;

    /** the default implementation is not null ,so when operator part
     *  extends this class to over write it , must need to think that
     *  whether call super class's implementaion or not.
     */
    public boolean isAllowEditPresetApn(String type, String apn, String numeric, int sourcetype) {
        Log.d(TAG, "isAllowEditPresetApn");
        return true;
    }

    public void customizeTetherApnSettings(PreferenceScreen root) {
    }

    /** the default implementation is not null ,so when operator part
     *  extends this class to over write it , must need to think that
     *  whether call super class's implementaion or not.
     */
    public boolean isSelectable(String type) {
        return !TYPE_MMS.equals(type) && !TYPE_IA.equals(type) && !TYPE_IMS.equals(type);
    }

    /** the default implementation is not null ,so when operator part
     *  extends this class to over write it , must need to think that
     *  whether call super class's implementaion or not.
     */
    public IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter(
                TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        return filter;
    }

    /** the default implementation is not null ,so when operator part
     *  extends this class to over write it , must need to think that
     *  whether call super class's implementaion or not.
     */
    public BroadcastReceiver getBroadcastReceiver(BroadcastReceiver receiver) {
        return receiver;
    }

    @Override
    public boolean getScreenEnableState(int subId, Activity activity) {
        boolean simReady = TelephonyManager.SIM_STATE_READY ==
                TelephonyManager.getDefault().getSimState(SubscriptionManager.getSlotId(subId));
        boolean airplaneModeEnabled = android.provider.Settings.System.getInt(
                activity.getContentResolver(),
                android.provider.Settings.System.AIRPLANE_MODE_ON, -1) == 1;

        boolean isMMsNoTransac = isMMSNotTransaction(activity);
        boolean isMultiSimMode = true;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
           int multiMode = Settings.System.getInt(
                   activity.getContentResolver(), Settings.System.MSIM_MODE_SETTING, -1);
           isMultiSimMode = multiMode != 0;
        }
        Log.d(TAG, "subId = " + subId + ",isMMsNoTransac = "
                + isMMsNoTransac + " ,airplaneModeEnabled = "
                + airplaneModeEnabled + " ,simReady = " + simReady
                + " , isMultiSimMode = " + isMultiSimMode);
        return isMMsNoTransac && !airplaneModeEnabled && simReady && isMultiSimMode;
    }

    private boolean isMMSNotTransaction(Activity activity) {
        boolean isMMSNotProcess = true;
        ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE_MMS);
            if (networkInfo != null) {
                NetworkInfo.State state = networkInfo.getState();
                Log.d(TAG, "mms state = " + state);
                isMMSNotProcess = (state != NetworkInfo.State.CONNECTING
                    && state != NetworkInfo.State.CONNECTED);
            }
        }
        return isMMSNotProcess;
    }

    /** the default implementation is not null ,so when operator part
     *  extends this class to over write it , must need to think that
     *  whether call super class's implementaion or not.
     */
    public String getFillListQuery(String numeric, int subId) {
        // get mvno type and mvno match data
        String sqlStr = "";
        try {
            ITelephonyEx telephony = ITelephonyEx.Stub.asInterface(
                    ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
            if (telephony != null) {
                String mvnoType = telephony.getMvnoMatchType(subId);
                String mvnoPattern = telephony.getMvnoPattern(subId, mvnoType);
                // set sql string
                sqlStr = " mvno_type=\'" + mvnoType + "\'" +
                        " and mvno_match_data=\'" + mvnoPattern + "\'";
            } else {
                Log.d(TAG, "TelephonyEx service is null !");
            }
        }  catch (android.os.RemoteException e) {
            Log.e(TAG, "RemoteException " + e);
        }

        String result = "numeric=\'" + numeric + "\' and ( " + sqlStr + ")";
        Log.d(TAG, "getQuery result: " + result);
        return result;
    }

   public void updateMenu(Menu menu, int newMenuId, int restoreMenuId, String numeric) {

    }

    public void addApnTypeExtra(Intent it) {
    }

    public void updateTetherState(Activity activity) {
    }

    public void initTetherField(PreferenceFragment pref) {
    }

    /**
     * the default implementation is not null ,so when operator part
     *  extends this class to over write it , must need to think that
     *  whether call super class's implementaion or not.
     *  @param subId sub id
     */
    public Uri getRestoreCarrierUri(int subId) {
        Uri preferredUri = Uri.withAppendedPath(Uri.parse(PREFERRED_APN_URI), "/subId/" + subId);
        Log.d(TAG, "getRestoreCarrierUri: " + preferredUri);
        return preferredUri;
    }

    /**
     * the default implementation is not null ,so when operator part
     *  extends this class to over write it , must need to think that
     *  whether call super class's implementaion or not.
     */
    public boolean isSkipApn(String type, IRcseOnlyApnExtension rcseExt) {
        return CMMAIL_TYPE.equals(type)
                || (RCSE_TYPE.equals(type) && !rcseExt.isRcseOnlyApnEnabled());
    }

    public void setApnTypePreferenceState(Preference preference) {
    }

    public Uri getUriFromIntent(Context context, Intent intent) {
        return context.getContentResolver().insert(intent.getData(), new ContentValues());
    }

    public String[] getApnTypeArray(Context context, int defResId, boolean isTether) {
        return context.getResources().getStringArray(defResId);
    }

    @Override
    public void updateFieldsStatus(int subId, PreferenceScreen root) {
    }

    @Override
    public void setPreferenceTextAndSummary(int subId, String text) {
    }

    @Override
    public void customizePreference(int subId, PreferenceScreen root) {
    }

    @Override
    public String[] customizeApnProjection(String[] projection) {
        return projection;
    }

    @Override
    public void saveApnValues(ContentValues contentValues) {

    }

    /**
     * the default implementation is not null.
     * OP03 should consider its logic implementation
     */
    public Cursor customizeQueryResult(Activity activity, Cursor cursor, Uri uri, String numeric) {
        /// M: if query MVNO result is null ,need query MNO to display them
        /// but it dosen't apply to OP03 for tethering only {@
        if (cursor == null  || cursor.getCount() == 0) {
          // Close old cursor to avoid cursor leak
            if (cursor != null) {
                cursor.close();
            }
            String where = "numeric=\"" + numeric + "\"";
            Log.d(TAG, "query MNO apn list, where = " + where);
            return activity.getContentResolver().query(uri, new String[] {
                    "_id", "name", "apn", "type", "sourcetype"}, where, null, null);
        } else {
            return cursor;
        }
        /// @}
    }

    public void setMVNOPreferenceState(Preference preference) {
        if ("mvno_type".equals(preference.getKey())) {
            preference.setEnabled(false);
            Log.d(TAG, "disable MVNO type preference");
        } else if ("mvno_match_data".equals(preference.getKey())) {
            preference.setEnabled(false);
            Log.d(TAG, "disable MVNO match data preference");
        } else {
            Log.d(TAG, "nothing to do at present");
        }
    }

    @Override
    public boolean defaultApnCanDelete() {
        return false;
    }

    @Override
    public boolean isCtPlugin() {
        return false;
    }

    @Override
    public void customizeUnselectableApn(ArrayList<Preference> prefList, int subId) {
    }

    @Override
    public String updateAPNName(String name, int sourcetype){
        return name;
    }
}

