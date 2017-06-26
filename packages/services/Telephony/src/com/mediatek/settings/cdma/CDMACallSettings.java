package com.mediatek.settings.cdma;

import android.app.ActionBar;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.MenuItem;

import com.android.internal.telephony.PhoneConstants;
import com.android.phone.R;

/**
 * CDMA Call Settings Activity.
 */
public class CDMACallSettings extends PreferenceActivity {

    private static final String LOG_TAG = "CDMACallSettings";
    private static final boolean DBG = true;

    private static final String KEY_CALL_FORWARD = "button_cf_expand_key";
    private static final String KEY_ADDITIONAL = "button_more_expand_key";

    private static final String PHONE_PACKAGE_NAME = "com.android.phone";
    private static final String C2K_ADDITIONAL_SETTING_CLASS_NAME
            = "com.mediatek.settings.cdma.CdmaAdditionalCallOptions";
    private static final String C2K_CALL_FORWARD_SETTING_CLAS_NAME
            = "com.mediatek.settings.cdma.CdmaCallForwardOptions";

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.mtk_cdma_call_options);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        switch (itemId) {
        case android.R.id.home:
            finish();
            return true;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        Log.i(LOG_TAG, "onPreferenceTreeClick preference.getKey()=" + preference.getKey());
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        int [] subId = SubscriptionManager.getSubId(PhoneConstants.SIM_ID_1);
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId[0]);
        if (preference.getKey().equals(KEY_ADDITIONAL)) {
            intent.setClassName(PHONE_PACKAGE_NAME, C2K_ADDITIONAL_SETTING_CLASS_NAME);
            startActivity(intent);
            return true;
        } else if (preference.getKey().equals(KEY_CALL_FORWARD)) {
            intent.setClassName(PHONE_PACKAGE_NAME, C2K_CALL_FORWARD_SETTING_CLAS_NAME);
            startActivity(intent);
            return true;
        } else {
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }
    }
}
