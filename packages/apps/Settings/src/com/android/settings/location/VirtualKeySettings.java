package com.android.settings.location;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.provider.SettingsEx;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.preference.Preference.OnPreferenceChangeListener;
import android.content.res.Resources;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
public class VirtualKeySettings extends SettingsPreferenceFragment implements ImgRadioButtonPreference.OnClickListener,OnPreferenceChangeListener{
	private static final String MODE_1 = "mode1";
    private ImgRadioButtonPreference mMode1;
    private static final String MODE_2 = "mode2";
    private ImgRadioButtonPreference mMode2;
    private static final String MODE_CHANGING_ACTION =
            "com.android.settings.location.MODE_CHANGING";
    private static final String CURRENT_MODE_KEY = "CURRENT_MODE";
    private static final String NEW_MODE_KEY = "NEW_MODE";
    private int mCurrentMode;
    private static final String KEY_HIDE = "KeyHide";
    private static final String KEY_VIRTUAL_KEY_TYPE = "virtual_key_type";
    private SwitchPreference mKeyHidePref;

    
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		ContentResolver resolver = getActivity().getContentResolver();
		android.util.Log.i("xuqian","oncreate");
       // addPreferencesFromResource(R.xml.virtualkey_settings);
        createPreferenceHierarchy();
       
        
       
	}
	  @Override
	    public void onResume() {
	        super.onResume();
	        createPreferenceHierarchy();
	    }

	    @Override
	    public void onPause() {
	        super.onPause();
	    }

	    private PreferenceScreen createPreferenceHierarchy() {
	        PreferenceScreen root = getPreferenceScreen();
	        if (root != null) {
	            root.removeAll();
	        }
	        addPreferencesFromResource(R.xml.virtualkey_settings);
	        root = getPreferenceScreen();
	      
	        mMode1 = (ImgRadioButtonPreference) root.findPreference(MODE_1);
	        mMode2 = (ImgRadioButtonPreference) root.findPreference(MODE_2);
	       
	        mMode1.setOnClickListener(this);
	        mMode2.setOnClickListener(this);
	        refreshLocationMode();
	        
	        mKeyHidePref = (SwitchPreference) findPreference(KEY_HIDE); 
	        mKeyHidePref.setOnPreferenceChangeListener(this);
	        
	        if (mKeyHidePref != null) {
	        	mKeyHidePref.setChecked(Settings.System.getInt(getContentResolver(),
	        			SettingsEx.System.HIDE_VIRTUAL_KEY, 0) != 0);
	        }
	        return root;
	    }

	
	public void setLocationMode(int mode) {
        Settings.System.putInt(getContentResolver(), KEY_VIRTUAL_KEY_TYPE, mode);
        refreshLocationMode();
    }
	public void refreshLocationMode() {
        
            int mode = Settings.System.getInt(getContentResolver(), KEY_VIRTUAL_KEY_TYPE, 0);
            mCurrentMode = mode;
            
            onModeChanged(mode);
    }
	 public void onModeChanged(int mode) {
	        switch (mode) {
	            case 1:
	                updateRadioButtons(mMode2);
	                break;
	            case 0:
	                updateRadioButtons(mMode1);
	                break;
	            default:
	                break;
	        }

	       
	    }
	 private void updateRadioButtons(ImgRadioButtonPreference activated) {
	        if (activated == null) {
	            mMode1.setChecked(true);
	            mMode2.setChecked(false);
	        } else if (activated == mMode1) {
	        	mMode1.setChecked(true);
	        	mMode2.setChecked(false);
	        } else if (activated == mMode2) {
	        	mMode1.setChecked(false);
	        	mMode2.setChecked(true);
	        } 
	    }
	
	@Override
	public void onImgRadioButtonClicked(ImgRadioButtonPreference emiter) {
		
		int mode = 0;
        if (emiter == mMode1) {
            mode = 0;
        } else if (emiter == mMode2) {
            mode = 1;
        } 
        setLocationMode(mode);
		
	}
	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		final String key = preference.getKey();
		android.util.Log.i("jiangchao","   "+key);
		if (KEY_HIDE.equals(key)) {
			android.util.Log.i("jiangchao","value");
            Settings.System.putInt(getContentResolver(), SettingsEx.System.HIDE_VIRTUAL_KEY, ((Boolean) newValue) ? 1 : 0);
            
        }
		
		return true;
	}
	
}
