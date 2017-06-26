package com.mediatek.settings.ext;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.preference.DialogPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.telephony.SubscriptionInfo;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.view.View;

import com.mediatek.widget.AccountViewAdapter.AccountElements;


import java.util.ArrayList;
import java.util.List;

public interface ISimManagementExt {
    /**
     * Remove the Auto_wap push preference screen
     *
     * @param parent parent preference to set
     */
    void updateSimManagementPref(PreferenceGroup parent);
    /**
     * Remove the Sim color preference
     *
     * @param preference fragment
     */
    void updateSimEditorPref(PreferenceFragment pref);

    /**
     * hide SIM color view.
     * @param view view
     * @param context Context
     */
    void hideSimEditorView(View view, Context context);

    /**
     *Update change Data connect dialog state.
     *
     * @Param preferenceFragment
     * @Param isResumed
     */
    void dealWithDataConnChanged(Intent intent, boolean isResumed);

    /**
     * update default SIM Summary.
     * @param pref preference
     * @param subId subId
     */
    void updateDefaultSIMSummary(Preference pref, int subId);

    /**
     *Show change data connection dialog.
     *
     * @Param preferenceFragment
     * @Param isResumed
     */
    void showChangeDataConnDialog(PreferenceFragment prefFragment,
            boolean isResumed);

    /**
     *Set to close sim slot id
     *
     * @param simSlot
     */
    void setToClosedSimSlot(int simSlot);

    /**
     * Dual sim indicator new design, remove sim color editor preference
     *
     * @param pref
     *            : sim editor preference fragment
     * @param key
     *            : sim color editor preference key
     */
    void customizeSimColorEditPreference(PreferenceFragment pref, String key);

    /**
     * customize choice items for voice , such as "internet call" or
     * "always ask""
     *
     * @param the
     *            list displays all the normal voice items
     */
    void customizeVoiceChoiceArray(List<AccountElements> voiceList, boolean voipAvailable);

    /**
     * customize choice items for SMS , such as "always ask" or "Auto"
     *
     * @param smsList
     *            the list displays all the normal SMS items
     */
    void customizeSmsChoiceArray(List<AccountElements> smsList);

    /**
     * customize sms slection value.
     *
     * @param smsValueList
     *            the list for sms selection value
     * @deprecated
     */
    void customizeSmsChoiceValueArray(List<Long> smsValueList);

    /**
     * customize sms slection value.
     *
     * @param smsValueList
     *            the list for sms selection value
     */
    void customizeSmsChoiceValue(List<Object> smsValueList);
    /**
     * Customize default setting items
     * @param prefGroup The parent PreferenceGroup for all default setting items
     */
    void updateDefaultSettingsItem(PreferenceGroup prefGroup);

    /**
     * Whether enable SimInfoPreference's switch widget
     */
    boolean enableSwitchForSimInfoPref();

    /**
     * @param editTextPreference The preference which holds the SIM number EditText
     * @param slotId The slot id of current SIM
     * Update SIM number edit text max length, GSM max length is 19 and CDMA max length is 15
     */
    void updateSimNumberMaxLength(EditTextPreference editTextPreference, int slotId);

    /**
     * get SMS auto item icon.
     * @param context context
     * @return context application context
     */
    Drawable getSmsAutoItemIcon(Context context);

    /**
     * get default Auto item id.
     * @return the Id for Default SMS;
     */
    int getDefaultSmsSubIdForAuto();

    /**
     * init Auto item data.
     * @param list the name,like SIM card name
     * @param smsSubInfoList set null always
     */
    void initAutoItemForSms(final ArrayList<String> list,
            ArrayList<SubscriptionInfo> smsSubInfoList);

     /**
     * Called when register Observer
     */
    void registerObserver();
    /**
     * Called when unregister Observer
     */
    void unregisterObserver();

     /**
      * Called before SIM data switch.
      * @param context caller context
      * @param subId Switch data to this subId
      */
    boolean switchDefaultDataSub(Context context, int subId);
    
    
    void customizeListArray(List<String> strings);
    
    void customizeSubscriptionInfoArray(List<SubscriptionInfo> subscriptionInfo);

    int customizeValue(int value);

    SubscriptionInfo setDefaultSubId(Context context, SubscriptionInfo sir, int type);

    PhoneAccountHandle setDefaultCallValue(PhoneAccountHandle phoneAccount); 
    void setToClosedSimSlotSwitch(int simSlot, Context context);

    void setDataState(int subid);
    void setDataStateEnable(int subid);


}
