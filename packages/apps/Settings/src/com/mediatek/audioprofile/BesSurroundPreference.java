package com.mediatek.audioprofile;

import android.content.Context;
import android.preference.PreferenceActivity;
import android.preference.TwoStatePreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.app.FragmentTransaction;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.android.settings.Utils;
import com.mediatek.xlog.Xlog;

import com.mediatek.audioprofile.AudioProfileManager;

public class BesSurroundPreference extends TwoStatePreference {

    private static final String XLOGTAG = "Settings/AudioP";

    private AudioProfileManager mProfileManager;
    private final Listener mListener = new Listener();  
 
    private class Listener implements CompoundButton.OnCheckedChangeListener {
        @Override         
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Xlog.d(XLOGTAG, "BesSurroundPreference onChange value :" + isChecked);
            //set the bessurround state
            mProfileManager.setBesSurroundState(isChecked);
            BesSurroundPreference.this.setChecked(isChecked);
        }    
    }// 

    public BesSurroundPreference(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        mProfileManager = (AudioProfileManager) context.getSystemService(Context.AUDIO_PROFILE_SERVICE);
    }

    public BesSurroundPreference(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.switchPreferenceStyle);
    }

    public BesSurroundPreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        BesSurroundPreference.this.setChecked(mProfileManager.getBesSurroundState());
        View checkableView = view.findViewById(com.mediatek.internal.R.id.imageswitch);
        if (checkableView != null && checkableView instanceof Checkable) {
            ((Checkable) checkableView).setChecked(isChecked());

            //sendAccessibilityEvent(checkableView);

            if (checkableView instanceof Switch) {
                final Switch switchView = (Switch) checkableView;
                switchView.setFocusable(false);
                switchView.setOnCheckedChangeListener(mListener);
            }
        }

        //syncSummaryView(view);
    }


    @Override
    protected void onClick() {
    	super.onClick();
    }

}
