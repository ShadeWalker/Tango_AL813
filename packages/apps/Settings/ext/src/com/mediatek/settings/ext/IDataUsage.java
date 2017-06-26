package com.mediatek.settings.ext;

import android.content.IntentFilter;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;


public interface IDataUsage {

	void updatePolicy(boolean refreshCycle);
}
