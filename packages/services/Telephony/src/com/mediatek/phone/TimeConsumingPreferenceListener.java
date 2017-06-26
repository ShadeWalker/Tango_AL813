package com.mediatek.phone;

import android.preference.Preference;

import com.android.internal.telephony.CommandException;


public interface TimeConsumingPreferenceListener {
    void onStarted(Preference preference, boolean reading);
    void onFinished(Preference preference, boolean reading);
    void onError(Preference preference, int error);
    void onException(Preference preference, CommandException exception);
}
