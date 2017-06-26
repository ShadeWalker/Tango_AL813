/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.uicc;

import android.os.Build;
import android.os.Environment;
import android.util.Xml;
import android.text.TextUtils;//add by zhaizhanfeng for virtual voicemail at150811
import java.util.ArrayList;//add by zhaizhanfeng for virtual voicemail at150811
import android.util.Log;//add by zhaizhanfeng for virtual voicemail at 150811

import android.telephony.Rlog;

import java.util.HashMap;
import java.io.FileReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.android.internal.util.XmlUtils;

/**
 * {@hide}
 */
class VoiceMailConstants {
    private HashMap<String, String[]> CarrierVmMap;

    static final String LOG_TAG = "VoiceMailConstants";
    static final String PARTNER_VOICEMAIL_PATH ="system/etc/voicemail-conf.xml";
	static final String PARTNER_VIRTUALVOICEMAIL_PATH ="system/etc/virtualNets-conf.xml";//add by zhaizhanfeng for virtual voicemail at150811

    static final int NAME = 0;
    static final int NUMBER = 1;
    static final int TAG = 2;
    static final int SIZE = 3;
	//add by zhaizhanfeng for virtual voicemail at150811 start
	public static final int VOICEMIAL_BY_SPN = 0;
    public static final int VOICEMIAL_BY_IMSI = 1;
    public static final int VOICEMIAL_BY_GID1 = 2;
    public static final int VOICEMIAL_BY_SST = 3;
	private ArrayList CarrierVirtualVmMapByImsi;
    private HashMap<String, String[]> CarrierVirtualVmMapBySpn;
    private HashMap<String, String[]> CarrierVirtualVmMapByGid1;
    private HashMap<String, String[]> CarrierVirtualVmMapBySst;
    class VirtualVMByImsi {
        private String pattern;
        private String numeric;
        public String[] data;
		
		public VirtualVMByImsi(String pattern, String numeric, String[] data) {
            this.pattern = pattern;
            this.numeric = numeric;
            this.data = data;
        }

        public boolean imsiMatches(String numeric, String imsiSIM) {
            int len = this.pattern.length();
            int idxCompare = 0;
            if (len <= 0 || len > imsiSIM.length() || !this.numeric.equals(numeric))
                return false;
            for (int idx = 0; idx < len; idx++) {
                char c = this.pattern.charAt(idx);
                if ((c == 'x') || (c == 'X') || (c == imsiSIM.charAt(idx))) {
                    continue;
                } else {
                    return false;
                }
            }
            return true;
        }
    }
	/*class VirtualVMByImsi Judge pattern ?<=imsiSIM,imsi contains special symbols*/
   //add by zhaizhanfeng for virtual voicemail at150811 end
    VoiceMailConstants () {
        CarrierVmMap = new HashMap<String, String[]>();
	   //add by zhaizhanfeng for virtual voicemail at150811 start 
		CarrierVirtualVmMapByImsi = new ArrayList();
        CarrierVirtualVmMapBySpn = new HashMap<String, String[]>();
        CarrierVirtualVmMapByGid1 = new HashMap<String, String[]>();
        CarrierVirtualVmMapBySst = new HashMap<String, String[]>();
		//add by zhaizhanfeng for virtual voicemail at150811 end
        loadVoiceMail();
    }

    boolean containsCarrier(String carrier) {
        return CarrierVmMap.containsKey(carrier);
    }

    String getCarrierName(String carrier) {
        String[] data = CarrierVmMap.get(carrier);
        return data[NAME];
    }

    String getVoiceMailNumber(String carrier) {
        String[] data = CarrierVmMap.get(carrier);
        return data[NUMBER];
    }

    String getVoiceMailTag(String carrier) {
        String[] data = CarrierVmMap.get(carrier);
        return data[TAG];
    }

    private void loadVoiceMail() {
        FileReader vmReader;
		FileReader vmReader2;//add by zhaizhanfeng for virtual voicemail at150811
        final File vmFile = new File(PARTNER_VOICEMAIL_PATH);
		
        //add by zhaizhanfeng for virtual voicemail at150811 start 
		final File virtualvmFile = new File(PARTNER_VIRTUALVOICEMAIL_PATH);
		//add by zhaizhanfeng for virtual voicemail at150811 end
        try {
            vmReader = new FileReader(vmFile);
        } catch (FileNotFoundException e) {
            Rlog.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_VOICEMAIL_PATH);
            return;
        }
		//add by zhaizhanfeng for virtual voicemail at150811 start
		try {
            vmReader2= new FileReader(virtualvmFile);
        } catch (FileNotFoundException e) {
            Rlog.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_VIRTUALVOICEMAIL_PATH);
            return;
        }
		//add by zhaizhanfeng for virtual voicemail at150811 end
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(vmReader);

            XmlUtils.beginDocument(parser, "voicemail");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"voicemail".equals(name)) {
                    break;
                }

                String[] data = new String[SIZE];
                String numeric = parser.getAttributeValue(null, "numeric");
                data[NAME]     = parser.getAttributeValue(null, "carrier");
                data[NUMBER]   = parser.getAttributeValue(null, "vmnumber");
                data[TAG]      = parser.getAttributeValue(null, "vmtag");

                CarrierVmMap.put(numeric, data);
            }
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e);
        } finally {
            try {
                if (vmReader != null) {
                    vmReader.close();
                }
            } catch (IOException e) {}
        }

		//add by zhaizhanfeng for virtual voicemail at150811 start
	    try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(vmReader2);

            XmlUtils.beginDocument(parser, "virtualNets");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (name == null) {
                    break;
                }
				
				String[] data  = new String[SIZE];
				String numeric = parser.getAttributeValue(null, "numeric");
				data[NAME]	   = parser.getAttributeValue(null, "carrier");
				data[NUMBER]   = parser.getAttributeValue(null, "voicemail_number");
				data[TAG]	   = parser.getAttributeValue(null, "voicemail_tag");
				
				String spn = parser.getAttributeValue(null, "spn");
                String imsi = parser.getAttributeValue(null, "imsi_start");
                String gid1 = parser.getAttributeValue(null, "gid");
                String sst = parser.getAttributeValue(null, "match_value");
				
                boolean isVritual = false;
                if (!TextUtils.isEmpty(spn)) {
                    isVritual = true;
                    CarrierVirtualVmMapBySpn.put(numeric + spn, data);
                }
                if (!TextUtils.isEmpty(imsi)) {
                    isVritual = true;
                    CarrierVirtualVmMapByImsi.add(new VirtualVMByImsi(imsi,
                            numeric, data));
                }
				if (!TextUtils.isEmpty(gid1)) {
                    isVritual = true;
                    CarrierVirtualVmMapByGid1.put(numeric + gid1.toLowerCase().concat("ffffff".substring(0,10-gid1.length())), data);
                }
                if (!TextUtils.isEmpty(sst)) {
                    isVritual = true;
                    CarrierVirtualVmMapBySst.put(numeric + sst.concat("ffffffffffffffff".substring(0,20-sst.length())), data);
                }
                if (!isVritual) {
                    CarrierVmMap.put(numeric, data);
                }
				
            }
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in virtual voicemail parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in virtual voicemail parser " + e);
        } finally {
            try {
                if (vmReader2!= null) {
                    vmReader2.close();
                }
            } catch (IOException e) {}
        }
    }
	
	String[] getVirtualVoiceMailData(String carrier, String str, int mode) {
	    if (Build.TYPE.equals("eng")) {
		    Log.d("zhai","getVirtualVoiceMailData:carrier=" + carrier + " str=" + str + " mode=" + mode);
	    }
        switch (mode) {
            case VOICEMIAL_BY_SPN:
                return CarrierVirtualVmMapBySpn.get(carrier + str);
            case VOICEMIAL_BY_IMSI:
                VirtualVMByImsi vsbi;
                for (int i = 0; i < this.CarrierVirtualVmMapByImsi.size(); i++) {
                    vsbi = (VirtualVMByImsi) (this.CarrierVirtualVmMapByImsi
                            .get(i));
                    if (vsbi.imsiMatches(carrier, str) == true) {
                        return vsbi.data;
                    }
                }
                return null;
            case VOICEMIAL_BY_GID1:
                return CarrierVirtualVmMapByGid1.get(carrier + str);
            case VOICEMIAL_BY_SST:
                return CarrierVirtualVmMapBySst.get(carrier + str);
        }
        return null;
    }
	//add by zhaizhanfeng for virtual voicemail at150811 end
}
