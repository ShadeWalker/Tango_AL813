package com.android.mms;

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
 * @author zhailong 
 * for euro  2014.11.18
 *
 */
public class HQMmsConfigOverride {
    private static HashMap<String, MmsConfigBean> mHQMmsConfigMaps;
    private static HQMmsConfigOverride sInstance;
    static final Object sInstSync = new Object();
    static final String LOG_TAG = "HQMmsConfigOverride";
    static final String PARTNER_HQMMS_OVERRIDE_PATH ="etc/HQmms-conf.xml";
    
    public class MmsConfigBean{
    	public String mMccMnc;
    	public String mMmsSize;
    	public String mSmsToMms;
    	MmsConfigBean(){
    		
    	}
    }

    public static HQMmsConfigOverride getInstance() {
        synchronized (sInstSync) {
            if (sInstance == null) {
                sInstance = new HQMmsConfigOverride();
            }
        }
        return sInstance;
    }

    private HQMmsConfigOverride() {
        mHQMmsConfigMaps = new HashMap<String, MmsConfigBean>();
        loadMmsConfigOverrides();
    }

    public boolean containsMccmncForMmsConfig(String mccmnc) {
        return mHQMmsConfigMaps.containsKey(mccmnc);
    }

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
                if (!"mmsconfig".equals(name)) {
                    break;
                }
                MmsConfigBean bean = new MmsConfigBean();
                bean.mMccMnc = parser.getAttributeValue(null, "mccmnc");
		        bean.mMmsSize = parser.getAttributeValue(null, "mmssize");
		        bean.mSmsToMms = parser.getAttributeValue(null, "smstomms");
		        String virtualType = parser.getAttributeValue(null, "mvno_type");
		        String virtualValue = parser.getAttributeValue(null, "mvno_match_data");
		        String keyword = bean.mMccMnc;
		        if(virtualValue!=null && !virtualValue.equals("")){
		        	keyword = bean.mMccMnc+virtualValue;
		        	if(virtualType!=null && "imsi".equals(virtualType)){
		        		keyword = virtualValue;
		        	}
                    if(virtualType!=null && "gid1".equals(virtualType)){
                        String gid = virtualValue.concat("ffffff".substring(0,10-virtualValue.length())).substring(2);
						Log.i("lipeng_loadMmsConfigOverrides", "gid" + gid);
                        gid = gid.toLowerCase();
		        		keyword = bean.mMccMnc+gid;
		        	}
		        }
		        if(bean.mSmsToMms!=null && bean.mSmsToMms.equals("-1")){
		        	//bean.mSmsToMms = "5";
		        }

		        Log.i("lipeng_loadMmsConfigOverrides", "mncmcc" + keyword);
		        //if bean content is default value.not set it into map data
		        //if(
		        		//(bean.mMmsSize!=null && !bean.mMmsSize.equals("300")) ||
		        		//(bean.mSmsToMms!=null && !bean.mSmsToMms.equals("5"))
		        	//	){
                Log.i("lipeng_loadMmsConfigOverrides", "mncmcc_sp" + keyword);
		        mHQMmsConfigMaps.put(keyword, bean);
		       // }
            }
			mReader.close();
        } catch (XmlPullParserException e) {
            Log.w(LOG_TAG, "Exception in loadMmsConfigOverrides parser " + e);
        } catch (IOException e) {
            Log.w(LOG_TAG, "Exception in loadMmsConfigOverrides parser " + e);
        }
    }

}
