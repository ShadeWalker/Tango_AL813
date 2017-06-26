package com.mediatek.contacts.util;

import android.content.Context;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;

import com.mediatek.contacts.ContactsApplicationEx;

import java.util.Comparator;

public class ContactsSettingsUtils {

    public static final long DEFAULT_SIM_SETTING_ALWAYS_ASK = Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK;
    public static final long VOICE_CALL_SIM_SETTING_INTERNET = Settings.System.VOICE_CALL_SIM_SETTING_INTERNET;
    public static final long DEFAULT_SIM_NOT_SET = Settings.System.DEFAULT_SIM_NOT_SET;

    // Contact list to show phone / exchange / sim card contact
    public static final String ACCOUNT_TYPE = "account_type";
    public static final int ALL_TYPE_ACCOUNT = 0;
    // Include exchange account
    public static final int PHONE_TYPE_ACCOUNT = 1;
    public static final int SIM_TYPE_ACCOUNT = 2;


    protected Context mContext;

    private static ContactsSettingsUtils sMe;

    private static final String TAG = "ContactsSettingsUtils";

    private ContactsSettingsUtils(Context context) {
        mContext = context;
    }

    public static ContactsSettingsUtils getInstance() {
        if (sMe == null) {
            sMe = new ContactsSettingsUtils(ContactsApplicationEx.getContactsApplication());
        }
        return sMe;
    }

    public static long getDefaultSIMForVoiceCall() {
        return DEFAULT_SIM_SETTING_ALWAYS_ASK;
    }

    public static long getDefaultSIMForVideoCall() {
        return 0;
    }

    protected void registerSettingsObserver() {
        //
    }

    /**
     * a class for sort the sim info in order of slot id
     *
     */
    public static class SubInfoComparable implements Comparator<SubscriptionInfo> {
        @Override
        public int compare(SubscriptionInfo sim1, SubscriptionInfo sim2) {
            return sim1.getSimSlotIndex() - sim2.getSimSlotIndex();
        }
    }
}
