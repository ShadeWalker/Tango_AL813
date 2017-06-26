package com.mediatek.settings.ext;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceScreen;
import android.view.View;


public interface IWfcSettingsExt {

    /** Shows alert dialog to confirm whteher to turn on hotspot or not
         *  @param  context
         *  @return  true: if alert is shown, false: if alert is not shown
         */
    boolean showWfcTetheringAlertDialog(Context context);

    /** Customize the WFC settings preference
    *  @param  context
    *  @param  preferenceScreen
    */

    void customizedWfcPreference(Context context, PreferenceScreen preferenceScreen);
   
    /**
     * Called to modify Wfc preference Dialog View
     * @param cntx context of host app activity
     * @param dialog is a View object of dialog
     * @param dialogData is a Bundle object
     */
     void modifyWfcPreferenceDialog(Context cntx, View dialog, Bundle dialogData);
}
