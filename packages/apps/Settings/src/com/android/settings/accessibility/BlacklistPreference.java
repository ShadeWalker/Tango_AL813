package com.android.settings.accessibility;

import android.preference.SwitchPreference;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.provider.ChildMode;


public class BlacklistPreference extends SwitchPreference {
    
    private static final String TAG = "BlacklistPreference";
    
    public  BlacklistPreference(Context context) {
        super(context);
    }
    
    public  BlacklistPreference (Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    public BlacklistPreference(Context context, AttributeSet attrs) {
        super(context,attrs);
    }
    
    @Override
    protected void onClick(){
        Log.d(TAG, "BlacklistPreference onClick");
    }

}