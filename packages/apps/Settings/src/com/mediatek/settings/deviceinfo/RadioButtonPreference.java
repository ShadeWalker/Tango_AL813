package com.mediatek.settings.deviceinfo;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.android.settings.R;

public class RadioButtonPreference extends CheckBoxPreference {
    private String mMountPath;

    public interface OnClickListener {
        public abstract void onRadioButtonClicked(RadioButtonPreference emiter);
    }

    private OnClickListener mListener = null;

    public RadioButtonPreference(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        setWidgetLayoutResource(R.layout.preference_widget_radiobutton);
    }

    public RadioButtonPreference(Context context, AttributeSet attrs) {
        this(context, attrs,
                com.android.internal.R.attr.checkBoxPreferenceStyle);
    }

    public RadioButtonPreference(Context context) {
        this(context, null);
    }

    void setOnClickListener(OnClickListener listener) {
        mListener = listener;
    }

    @Override
    public void onClick() {
        if (isChecked()) {
            return;
        }
        setChecked(true);
        callChangeListener(true);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        TextView title = (TextView) view.findViewById(android.R.id.title);
        if (title != null) {
            title.setSingleLine(false);
            title.setMaxLines(3);
        }
    }

    public void setPath(String path) {
        mMountPath = path;
    }

    public String getPath() {
        return mMountPath;
    }
}