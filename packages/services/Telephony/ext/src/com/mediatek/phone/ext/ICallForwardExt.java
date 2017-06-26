package com.mediatek.phone.ext;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.os.Handler;
import android.preference.EditTextPreference;
import android.view.View;

import com.android.internal.telephony.Phone;

public interface ICallForwardExt {

    /**
     * get activity and subId
     * @param activity
     * @param subId
     */
    public void onCreate(Activity activity, int subId);

    /**
     * custom number editor dialog
     * @param preference
     * @param view
     */
    public void onBindDialogView(EditTextPreference preference, View view);

    /**
     * onDialogClosed
     * @param preference Edit Text Preference.
     * @param action Commands Interface Action.
     * @return true when time slot is the same.
     */
    public boolean onDialogClosed(EditTextPreference preference, int action);

    /**
     * get result
     * @param requestCode
     * @param resultCode
     * @param data
     * @return true when get CallForwardTimeActivity result
     */
    public boolean onCallForwardActivityResult(int requestCode, int resultCode, Intent data);

    /**
     * get Call Forward Time Slot
     * @param preference
     * @param message
     * @param handler
     * @return true when CFU and support volte ims
     */
    public boolean getCallForwardInTimeSlot(EditTextPreference preference,
            Message msg, Handler handler);

    /**
     * set CallForward for time slot
     * @param preference
     * @param action
     * @param number
     * @param time
     * @param handler
     * @return true when CFU and support volte ims
     */
    public boolean setCallForwardInTimeSlot(EditTextPreference preference, int action,
            String number, int time, Handler handler);

    /**
     * handle Get CF Time Slot Response
     * @param preference
     * @param msg
     * @return true when not support cfu volte time slot set
     */
    public boolean handleGetCFInTimeSlotResponse(EditTextPreference preference, Message msg);

    /**
     * add time slot for summary text
     * @param values
     */
    public void updateSummaryTimeSlotText(EditTextPreference preference, String values[]);

    /**
     * update time slot cfu icon
     * @param visible
     * @param subId
     * @param context
     * @param cfiAction
     */
    public void updateCfiIcon(int subId, Context context, ICfiAction cfiAction);

    /**
     * for op01
     * plug-in can callback to host through this interface to do specific things
     */
    public static interface ICfiAction {
        void updateCfiEx(int subId, boolean visible);
    }
}
