package com.android.settings;

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
 * for all  2016.04.25
 *
 */
public class ApnUpdateConfigOverride {
    private static HashMap<String, MmsConfigBean> mHQMmsConfigMaps;
    private static ApnUpdateConfigOverride sInstance;
    static final Object sInstSync = new Object();
    static final String LOG_TAG = "ApnUpdateConfigOverride";
    static final String PARTNER_HQMMS_OVERRIDE_PATH ="etc/ota-update-apn.xml";
    
    public class MmsConfigBean{
        public String mMccMnc;
    	public String mVersion;
    	MmsConfigBean(){
    	}
    }

    public static ApnUpdateConfigOverride getInstance() {
        synchronized (sInstSync) {
            if (sInstance == null) {
                sInstance = new ApnUpdateConfigOverride();
            }
        }
        return sInstance;
    }

    private ApnUpdateConfigOverride() {
        mHQMmsConfigMaps = new HashMap<String, MmsConfigBean>();
        loadMmsConfigOverrides();
    }

/*    public boolean containsMccmncForMmsConfig(String mccmnc) {
        return mHQMmsConfigMaps.containsKey(mccmnc);
    }*/

    public MmsConfigBean getMmsConfigWithSim(String mccmnc) {
        return mHQMmsConfigMaps.get(mccmnc);
    }

    private void loadMmsConfigOverrides() {
        FileReader mReader;
        Log.d(LOG_TAG, "loadMmsConfigOverrides");
        final File mFile = new File(Environment.getRootDirectory(),PARTNER_HQMMS_OVERRIDE_PATH);

        try {
            mReader = new FileReader(mFile);
        } catch (FileNotFoundException e) {
            Log.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_HQMMS_OVERRIDE_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(mReader);

            XmlUtils.beginDocument(parser, "Datas");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"apnconfig".equals(name)) {
                    break;
                }
                MmsConfigBean bean = new MmsConfigBean();
                bean.mVersion = parser.getAttributeValue(null, "version");
                bean.mMccMnc = parser.getAttributeValue(null, "apnupdate");
		        mHQMmsConfigMaps.put(bean.mMccMnc, bean);
		        }
			mReader.close();
        } catch (XmlPullParserException e) {
            Log.w(LOG_TAG, "Exception in loadMmsConfigOverrides parser " + e);
        } catch (IOException e) {
            Log.w(LOG_TAG, "Exception in loadMmsConfigOverrides parser " + e);
        }
    }

}
