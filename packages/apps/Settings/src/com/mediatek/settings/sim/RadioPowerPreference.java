package com.mediatek.settings.sim;

import android.content.Context;
import android.preference.Preference;
import android.view.View;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;

import com.android.settings.R;

/**
 * A preference for radio switch function.
 */
public class RadioPowerPreference extends Preference {

    protected boolean mPowerState;
    protected boolean mPowerEnabled = true;
    protected OnCheckedChangeListener mListener;

    /**
     * Construct of RadioPowerPreference.
     * @param context Context.
     */
    public RadioPowerPreference(Context context) {
        super(context);
        setWidgetLayoutResource(R.layout.radio_power_switch);
    }

    /**
     * Set the radio switch state.
     * @param state On/off.
     */
    public void setRadioOn(boolean state) {
        mPowerState = state;
        notifyChanged();
    }

    /* begin: add by donghongjing for HQ01382704 */
    /**
     * Get the radio switch state.
     * @return state On/off.
     */
    public boolean getRadioOn() {
        return mPowerState;
    }
    /* end: add by donghongjing for HQ01382704 */

    /**
     * Set the radio switch enable state.
     * @param enable Enable.
     */
    public void setRadioEnabled(boolean enable) {
        mPowerEnabled = enable;
        notifyChanged();
    }

    /**
     * Set the listener for radio switch.
     * @param listener Listener of {@link CheckedChangeListener}.
     */
    public void setRadioPowerChangeListener(OnCheckedChangeListener listener) {
        mListener = listener;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        Switch radioSwitch = (Switch) view.findViewById(R.id.radio_state);
        if (radioSwitch != null) {
            radioSwitch.setChecked(mPowerState);
            radioSwitch.setEnabled(mPowerEnabled);
            radioSwitch.setOnCheckedChangeListener(mListener);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        mPowerEnabled = enabled;
        super.setEnabled(enabled);
    }

}
