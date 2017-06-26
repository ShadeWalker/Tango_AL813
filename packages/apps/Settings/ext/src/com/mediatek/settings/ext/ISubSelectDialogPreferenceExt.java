package com.mediatek.settings.ext;

import android.preference.Preference;
import android.telephony.SubscriptionInfo;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

/**
 * CT Dual sim indicator new design: Add sim indicator to left of Preference summary text
 */
public interface ISubSelectDialogPreferenceExt {

    /**
     * CT Dual SIM indicator new design, add SIM indicator in DefualtSimPreference's summary
     *
     * Used in SimSelectDialogPreference.java
     *
     * @param pref The preference to add SIM indicator
     */
    void updateDefaultSimPreferenceLayout(Preference pref);

    /**
     * CT Dual SIM indicator new design, update SIM indicator when preference value changed.
     * For example, if user selects sim1, add SIM slot 1's SIM indicator to view, if user selects
     * SIM slot 0, hide SIM indicator.
     * Used in SubSelectDialogPreference.java
     *
     * @param viewContainer The context used to get LayoutInflater
     * @param subInfos, subInfo Array from host
     * @param index
     * @return The LayoutInflater
     */
    void updateDefaultSimPreferenceSimIndicator(View viewContainer, List<SubscriptionInfo> subInfos, int index);

    /**
     * CT Dual SIM indicator new design, create plugin view for CT dual sim indicator
     * Used in SimSelectDialogPreference.java
     *
     * @param pref The preference which will show the view
     * @param parent The parent view to add layout to
     * @return The LayoutInflater
     */
    View onCreateView(Preference pref, ViewGroup parent);
}
