package com.android.mms;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import com.android.internal.telephony.TelephonyProperties;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.Log;

/// M:
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.mediatek.ipmsg.util.IpMessageUtils;
import com.mediatek.mms.ext.DefaultMmsFeatureManagerExt;
import com.mediatek.mms.ext.IMmsConfigExt;
import com.mediatek.mms.ext.IMmsFeatureManagerExt;
import com.mediatek.mms.ext.DefaultMmsConfigExt;
import android.content.Intent;
import android.database.Cursor;
import android.provider.Telephony;

import com.android.mms.util.FeatureOption;
import com.android.mms.util.MmsLog;
/// M: add for ipmessage
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.android.mms.ui.MessageUtils;
import com.mediatek.custom.CustomProperties;
/// M: add for CMCC FT
import org.apache.http.params.HttpParams;

/// M: ALPS00527989, Extend TextView URL handling @ {
import android.widget.TextView;
/// @}
/// M: ALPS00956607, not show modify button on recipients editor @{
import android.view.inputmethod.EditorInfo;
/// @}
/// M: Add MmsService configure param @{
import android.os.Bundle;
/// @}

import com.mediatek.common.MPlugin;
import android.provider.Settings;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.os.Environment;
import android.util.Log;
import android.util.Xml;
import com.android.internal.util.XmlUtils;
import java.util.ArrayList;
import java.lang.Integer;

/**
 * @author lipeng 
 * for AL813  2015.08.14
 *
 */
public class HQSmsToMmsConfigOverride {
    private static HashMap<String, MmsConfigBean> mHQSmsToMmsConfigMaps;
    private static HQSmsToMmsConfigOverride sInstance;
    static final Object sInstSync = new Object();
    static final String TAG = "HQSmsToMmsConfigOverride";
    static final String PARTNER_HQMMS_OVERRIDE_PATH ="etc/mms_config.xml";
    public class MmsConfigBean{
    	public String mcc;
    	public String mnc;
    	public String mMccMnc;
    	public int smsToMmsTextThreshold;
    	MmsConfigBean(){
    	}
    }

    public static HQSmsToMmsConfigOverride getInstance() {
        synchronized (sInstSync) {
            if (sInstance == null) {
                sInstance = new HQSmsToMmsConfigOverride();
            }
        }
        return sInstance;
    }

    private HQSmsToMmsConfigOverride() {
        mHQSmsToMmsConfigMaps = new HashMap<String, MmsConfigBean>();
        loadMmsConfigOverrides();
    }

    public boolean containsMccmncForMmsConfig(String mccmnc) {
        return mHQSmsToMmsConfigMaps.containsKey(mccmnc);
    }

    public MmsConfigBean getMmsConfigWithSim(String mccmnc) {
        return mHQSmsToMmsConfigMaps.get(mccmnc);
    }

    private void loadMmsConfigOverrides() {
        FileReader mReader;
        Log.d(TAG, "loadHQSmsToMmsConfigOverride");
        final File mFile = new File(Environment.getRootDirectory(),PARTNER_HQMMS_OVERRIDE_PATH);

        try {
            mReader = new FileReader(mFile);
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_HQMMS_OVERRIDE_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(mReader);

            XmlUtils.beginDocument(parser, "mms_config");

            while (true) {
                XmlUtils.nextElement(parser);
                String tag = parser.getName();
                if (tag == null) {
                    break;
                }
                String name = parser.getAttributeName(0);
                String value = parser.getAttributeValue(0);
                String text = null;
                MmsConfigBean bean = new MmsConfigBean();
                if (parser.next() == XmlPullParser.TEXT) {
                    text = parser.getText();
                }
                bean.mcc = parser.getAttributeValue(null, "mcc");
                bean.mnc = parser.getAttributeValue(null, "mnc");
                bean.mMccMnc = bean.mcc + bean.mnc;
                String keyword = bean.mMccMnc;
                if ("name".equalsIgnoreCase(name)) {
                    if ("int".equals(tag)) {
                        if ("smsToMmsTextThreshold".equalsIgnoreCase(value)) {
                            //mMmsConfigPlugin.setSmsToMmsTextThreshold(Integer.parseInt(text));
                        	bean.smsToMmsTextThreshold = Integer.parseInt(text);
                        }
                    }
                }
                
                mHQSmsToMmsConfigMaps.put(keyword, bean);
            }
            mReader.close();
        } catch (XmlPullParserException e) {
            Log.e(TAG, "loadMmsSettings caught ", e);
        } catch (NumberFormatException e) {
            Log.e(TAG, "loadMmsSettings caught ", e);
        } catch (IOException e) {
            Log.e(TAG, "loadMmsSettings caught ", e);
        } 
    }


    public void setSmsToMmsTextThreshold(int value) {
        int SmsToMmsTextThreshold = 0;
        if (value > -1) {
            SmsToMmsTextThreshold = value;
        }
        Log.d(TAG, "set SmsToMmsTextThreshold: " + SmsToMmsTextThreshold);
    }

}
