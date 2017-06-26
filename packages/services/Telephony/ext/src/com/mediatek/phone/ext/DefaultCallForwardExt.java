package com.mediatek.phone.ext;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.os.Handler;
import android.preference.EditTextPreference;
import android.util.Log;
import android.view.View;

import com.android.internal.telephony.Phone;

public class DefaultCallForwardExt implements ICallForwardExt {
    private static final String LOG_TAG = "DefaultCallForwardExt";

    /**
     * get activity and subId
     * @param activity
     * @param subId
     */
    public void onCreate(Activity activity, int subId) {
        log("default onCreate()");
    }

    public void onSelectComplete(Activity activity, int subId) {
        log("default onSelectComplete()");
    }

    /**
     * custom number editor dialog
     * @param preference
     * @param view
     */
    @Override
    public void onBindDialogView(EditTextPreference preference, View view) {
        log("default onBindDialogView()");
    }

    /**
     * onDialogClosed
     * @param preference Edit Text Preference.
     * @param action Commands Interface Action.
     * @return true when time slot is the same.
     */
    @Override
    public boolean onDialogClosed(EditTextPreference preference, int action) {
        log("default onDialogClosed()");
        return false;
    }

    /**
     * get result
     * @param requestCode
     * @param resultCode
     * @param data
     * @return true when get CallForwardTimeActivity result
     */
    @Override
    public boolean onCallForwardActivityResult(int requestCode, int resultCode, Intent data) {
        log("default onCallForwardActivityResult()");
        return false;
    }

    /**
     * get Call Forward Time Slot
     * @param preference
     * @param message
     * @param handler
     * @return true when CFU and support volte ims
     */
    @Override
    public boolean getCallForwardInTimeSlot(EditTextPreference preference,
            Message msg, Handler handler) {
        log("default getCallForwardInTimeSlot()");
        return false;
    }

    /**
     * set CallForward for time slot
     * @param preference
     * @param action
     * @param number
     * @param time
     * @param handler
     * @return true when CFU and support volte ims
     */
    @Override
    public boolean setCallForwardInTimeSlot(EditTextPreference preference, int action, 
            String number, int time, Handler handler) {
        log("default setCallForwardInTimeSlot()");
        return false;
    }

    /**
     * handle Get CF Time Slot Response
     * @param preference
     * @param msg
     * @return true when not support cfu time volte slot set
     */
    @Override
    public boolean handleGetCFInTimeSlotResponse(EditTextPreference preference,Message msg) {
        log("default handleGetCFTimeSlotResponse()");
        return false;
    }

    /**
     * add time slot for summary text
     * @param values
     */
    @Override
    public void updateSummaryTimeSlotText(EditTextPreference preference, String values[]){
        log("default updateSummaryTimeSlotText()");
    }

    /**
     * update time slot cfu icon
     * @param subId
     * @param context
     * @param cfiAction
     */
    @Override
    public void updateCfiIcon(int subId, Context context, ICfiAction cfiAction) {
        log("default updateCfiIcon()");
    }

    /**
     * Log the message
     * @param msg the message will be printed
     */
    void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
