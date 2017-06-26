package com.mediatek.calendar.ext;

import android.app.Activity;
import android.text.format.Time;
import android.widget.Button;
import android.widget.RadioGroup;

import com.android.datetimepicker.date.DatePickerDialog;
import com.android.datetimepicker.date.DatePickerDialog.OnDateSetListener;

/**
 * Interface defines the way to extend the EditEventView's function
 */
public interface IEditEventViewExt {

    /**
     * Updates the DatePicker selection through the fragment or activity's
     * life cycle.
     */
    public void updateDatePickerSelection(Activity activity, Object model,
            RadioGroup radioGroup, int switch_lunar_id,  int switch_gregorian_id,
            Button startDateButton, Button endDateButton, String timezone,
            Time startTime, Time endTime);

    /**
     * Sets additional UI Elements for EditEventView
     * @param model model can provide some info
     */
    public void setDatePickerSwitchUi(Activity activity, Object model, Button startDateButton,
            Button endDateButton, String timezone, Time startTime, Time endTime);

    /**
     * Gets the extended string such as lunar string to tell the Date
     * @param millis the millis time
     * @return null means the extension won't handle the translation,
     * other means the extension had changed the millis to lunar string.
     */
    public String getDateStringFromMillis(Activity activity, long millis);

    /**
     * Constructs a new DatePickerDialog instance with the given initial field
     * @param callBack    How the parent is notified that the date is set
     * @param year        The initial year of the dialog
     * @param monthOfYear The initial month of the dialog
     * @param dayOfMonth  The initial day of the dialog
     * @return a instance of DatePickerDialog
     */
    public DatePickerDialog createDatePickerDialog(Activity activity, OnDateSetListener listener,
            int year, int monthOfYear, int dayOfMonth);
}
