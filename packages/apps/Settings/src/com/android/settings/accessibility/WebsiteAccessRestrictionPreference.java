package com.android.settings.accessibility;

import android.preference.SwitchPreference;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.provider.ChildMode;

/**
 * added by machao 20150424 for WebsiteAccessRestriction
 */
public class WebsiteAccessRestrictionPreference extends SwitchPreference {
    
    private static final String TAG = "WebsiteAccessRestrictionPreference";
    
    public  WebsiteAccessRestrictionPreference(Context context) {
        super(context);
    }
    
    public  WebsiteAccessRestrictionPreference (Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    public WebsiteAccessRestrictionPreference(Context context, AttributeSet attrs) {
        super(context,attrs);
    }
    
    @Override
    protected void onClick(){
        Log.d(TAG, "onClick");
    }

}