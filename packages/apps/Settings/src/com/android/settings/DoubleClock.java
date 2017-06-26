package com.android.settings;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.text.BidiFormatter;
import java.util.TimeZone;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import libcore.icu.TimeZoneNames;
import android.os.SystemProperties;
import android.content.res.XmlResourceParser;
import org.xmlpull.v1.XmlPullParserException;

public class DoubleClock extends SettingsPreferenceFragment implements OnPreferenceChangeListener{
    private static final String TAG = "DoubleClock";

    private SwitchPreference mSwitchPrefs;
	private Preference mCityListPrefs;
    private static String KEY_DOUBLE_CLOCK_SWITCH = "double_clock_prefs_switch";
	private static String KEY_DOUBLE_CLCOK_CITY_LIST = "double_clock_prefs_city_list";
    private static final String XMLTAG_TIMEZONE = "timezone";
	///M: support RTL
    private static BidiFormatter mBidiFormatter;
	
	@Override
	public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
	
	///M: support RTL
        mBidiFormatter = BidiFormatter.getInstance();
	
		Settings.System.putInt(getActivity().getContentResolver(),Settings.System.DOUBLE_CLOCK_CITY_LIST_SWITCH,0);
        PreferenceScreen root = getPreferenceScreen();
        if (root != null) {
            root.removeAll();
        }
        addPreferencesFromResource(R.xml.double_clock_prefs);
		initUI();
	}

    private void initUI() {
        mSwitchPrefs = (SwitchPreference) findPreference(KEY_DOUBLE_CLOCK_SWITCH);
		mCityListPrefs = findPreference(KEY_DOUBLE_CLCOK_CITY_LIST);
        if (mSwitchPrefs != null) {
            mSwitchPrefs.setOnPreferenceChangeListener(this);
            mSwitchPrefs.setChecked(isDoubleClockAllowed());
        }
    }

    private boolean isDoubleClockAllowed() {
        int speed = Settings.System.getInt(getContentResolver(),Settings.System.DOUBLE_CLOCK_SWITCH, 0);
        if (speed == 0) {
            return false;
        } else {
            return true;
        }
    }

	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,Preference preference) {
        if(mCityListPrefs == preference){
           Settings.System.putInt(getActivity().getContentResolver(),Settings.System.DOUBLE_CLOCK_CITY_LIST_SWITCH,1);
		}
        return super.onPreferenceTreeClick(preferenceScreen, preference);   
     }

	@Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        boolean result = true;
        final String key = preference.getKey();
        if (KEY_DOUBLE_CLOCK_SWITCH.equals(key)) {
            mSwitchPrefs.setChecked((boolean)value);
            Settings.System.putInt(getActivity().getContentResolver(),Settings.System.DOUBLE_CLOCK_SWITCH, (boolean)value ? 1 : 0);
        }
        return result;
	}

	 @Override
    public void onResume() {
        super.onResume();
		Settings.System.putInt(getActivity().getContentResolver(),Settings.System.DOUBLE_CLOCK_CITY_LIST_SWITCH,0);
		String olsonId = Settings.System.getString(getActivity().getContentResolver(),Settings.System.DOUBLE_CLOCK_DEFAULT_TIME_ZONE);
	    String timeZoneSummary = (olsonId != null) ? getTimeZone(getActivity(), olsonId) : null;
        
            olsonId = Settings.System.getString(getActivity().getContentResolver(), Settings.System.TIMEZONE);
            if(timeZoneSummary == null && olsonId != null) {
                timeZoneSummary = getTimeZone(getActivity(), olsonId);
            }
      
	    mCityListPrefs.setSummary(timeZoneSummary);
	}

	//add by HQ_caoxuhao at 20150916 HQ01322676 begin
	private String getTimeZone(Context context, String olsonId) {
        // We always need the "GMT-07:00" string.
        final TimeZone tz = TimeZone.getTimeZone(olsonId);

        // For the display name, we treat time zones within the country differently
        // from other countries' time zones. So in en_US you'd get "Pacific Daylight Time"
        // but in de_DE you'd get "Los Angeles" for the same time zone.
        String displayName = getOlsonSummary(context, olsonId);
        String timezone="";
      try{
           timezone=mBidiFormatter.unicodeWrap(displayName) + "("
                + mBidiFormatter.unicodeWrap(DateTimeSettings.getTimeZoneText(tz, false) + ")");
      }catch (Exception e) {
    	  Log.e(TAG, "getimezonetext exception");
      }
     
        
        return timezone;
    }
	//add by HQ_caoxuhao at 20150916 HQ01322676 end

    private String getOlsonSummary(Context context, String id) {
        String summary = "";
        HashMap<String, String> timeZonesMap = new HashMap<String, String>();
        try {
            XmlResourceParser xrp = context.getResources().getXml(R.xml.timezones);
            while (xrp.next() != XmlResourceParser.START_TAG) {
                continue;
            }
            xrp.next();
            while (xrp.getEventType() != XmlResourceParser.END_TAG) {
                while (xrp.getEventType() != XmlResourceParser.START_TAG) {
                    if (xrp.getEventType() == XmlResourceParser.END_DOCUMENT) {
                        return "";
                    }
                    xrp.next();
                }
                if (xrp.getName().equals(XMLTAG_TIMEZONE)) {
                    String olsonId = xrp.getAttributeValue(0);
                    String title = xrp.nextText();
                    timeZonesMap.put(olsonId, title);
                }
                while (xrp.getEventType() != XmlResourceParser.END_TAG) {
                    xrp.next();
                }
                xrp.next();
            }
            xrp.close();
        } catch (XmlPullParserException xppe) {
            Log.e(TAG, "Ill-formatted timezones.xml file");
        } catch (java.io.IOException ioe) {
            Log.e(TAG, "Unable to read timezones.xml file");
        }
        summary = timeZonesMap.get(id);
        return summary;
    }

	 @Override
    public void onDestroy() {
        super.onDestroy();
		Settings.System.putInt(getActivity().getContentResolver(),Settings.System.DOUBLE_CLOCK_CITY_LIST_SWITCH,0);
	}
	
}
