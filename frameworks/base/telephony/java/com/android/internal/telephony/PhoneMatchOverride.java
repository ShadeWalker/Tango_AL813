/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.internal.telephony;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;


import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.os.Environment;
import android.util.Log;
import android.util.Xml;
import android.telephony.PhoneNumberUtils;

import com.android.internal.util.XmlUtils;

// MVNO-API START
import java.util.ArrayList;
import java.lang.Integer;
// MVNO-API END

public class PhoneMatchOverride {
    private static HashMap<String, String> phyPhoneMatchMapWithMcc;
	private static HashMap<String, String> virPhoneMatchMapWithMcc;
    private static PhoneMatchOverride sInstance;
    static final Object sInstSync = new Object();
    static final String LOG_TAG = "zhai";
    static final String PHYSICAL_PHONE_MATCH_OVERRIDE_PATH ="etc/globalAutoAdapt-conf.xml";
	static final String VIRTUAL_PHONE_MATCH_OVERRIDE_PATH ="etc/virtualNets-conf.xml";
    public static PhoneMatchOverride getInstance() {
        synchronized (sInstSync) {
            if (sInstance == null) {
                sInstance = new PhoneMatchOverride();
            }
        }
        return sInstance;
    }

    private PhoneMatchOverride() {
        phyPhoneMatchMapWithMcc = new HashMap<String, String>();
		virPhoneMatchMapWithMcc = new HashMap<String, String>();
		int slotId=PhoneNumberUtils.getSlotId();
		ArrayList<String> CarrierVmMapgetVirtualType = new ArrayList<String>(); 
		CarrierVmMapgetVirtualType = PhoneNumberUtils.getVirtualType(slotId);
		if(CarrierVmMapgetVirtualType.get(0).equals("16"))
            {
                loadPhyPhoneMatchOverrides();
            }else
            {
                loadVirPhoneMatchOverrides();
            }
    }
	
     public boolean containsMccForPhoneMatch(String mccmnc) {
        return phyPhoneMatchMapWithMcc.containsKey(mccmnc);
    }

     public String getSphoneMatchWithMcc(String mccmnc) {
        return phyPhoneMatchMapWithMcc.get(mccmnc);
    }
	
	 public boolean containsMccForVirPhoneMatch(String virmccmnc) {

		Iterator iter = virPhoneMatchMapWithMcc.entrySet().iterator();
		while (iter!= null && iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			Object key = entry.getKey();
			Log.d("zhai", "containsMccForVirPhoneMatch, virmccmnc="+virmccmnc+", (String)key)="+(String)key);
			if(virmccmnc != null && virmccmnc.startsWith((String)key)) {
				return true;
			}
		}
		return false;

        //return virPhoneMatchMapWithMcc.containsKey(virmccmnc);
    }

     public String getSVirphoneMatchWithMcc(String virmccmnc) {

		Iterator iter = virPhoneMatchMapWithMcc.entrySet().iterator();
		while (iter!= null && iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			Object key = entry.getKey();
			Log.d("zhai", "getSVirphoneMatchWithMcc, virmccmnc="+virmccmnc+", (String)key)="+(String)key);
			if(virmccmnc != null && virmccmnc.startsWith((String)key)) {
				virmccmnc = (String)key;
			}
		}
        return virPhoneMatchMapWithMcc.get(virmccmnc);
    }
	 
    private static void loadPhyPhoneMatchOverrides() {
        FileReader eccReader;
        Log.d(LOG_TAG, "loadPhyPhonematchOverrides");
        final File eccFile = new File(Environment.getRootDirectory(),
                PHYSICAL_PHONE_MATCH_OVERRIDE_PATH);

        try {
            eccReader = new FileReader(eccFile);
        } catch (FileNotFoundException e) {
            Log.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PHYSICAL_PHONE_MATCH_OVERRIDE_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(eccReader);

            XmlUtils.beginDocument(parser, "Datas");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"numMatch".equals(name)) {
                    break;
                }
				
				String mcc = parser.getAttributeValue(null, "mcc");
				String mnc = parser.getAttributeValue(null, "mnc");
				/*String mcc = "460";
				try {
					if(mccmnc!=null && mccmnc.length()>=5 && mccmnc.length()<=6){
						mcc = mccmnc.substring(0, 3);
					}
				} catch (IndexOutOfBoundsException	e) {
					e.printStackTrace();
				}*/
                //String mcc = parser.getAttributeValue(null, "mcc");
                String minmatch = parser.getAttributeValue(null, "num_match");

                phyPhoneMatchMapWithMcc.put(mcc + mnc, minmatch);
            }
			eccReader.close();
        } catch (XmlPullParserException e) {
            Log.w(LOG_TAG, "Exception in globalAutoAdapt-conf parser " + e);
        } catch (IOException e) {
            Log.w(LOG_TAG, "Exception in globalAutoAdapt-conf parser " + e);
        } finally {
            try {
                if (eccReader != null) {
                    eccReader.close();
                }
            } catch (IOException e) {}
        }
    }
	private static void loadVirPhoneMatchOverrides() {
		  FileReader eccReader2;
		  Log.d(LOG_TAG, "loadVirPhonematchOverrides");
		  final File eccFile2 = new File(Environment.getRootDirectory(),
				  VIRTUAL_PHONE_MATCH_OVERRIDE_PATH);
	
		  try {
			  eccReader2 = new FileReader(eccFile2);
		  } catch (FileNotFoundException e) {
			  Log.w(LOG_TAG, "Can't open " +
					  Environment.getRootDirectory() + "/" + VIRTUAL_PHONE_MATCH_OVERRIDE_PATH);
			  return;
		  }
	
		  try {
			  XmlPullParser parser2 = Xml.newPullParser();
			  parser2.setInput(eccReader2);
	
			  XmlUtils.beginDocument(parser2, "virtualNets");
	
			  while (true) {
				  XmlUtils.nextElement(parser2);
	
				  String name = parser2.getName();
				  if (!"virtualNet".equals(name)) {
					  break;
				  }
				  
				  String mccmnc = parser2.getAttributeValue(null, "numeric");
                  String virspn = parser2.getAttributeValue(null, "spn");
                  String virimsi = parser2.getAttributeValue(null, "imsi_start");
                  String virgid = parser2.getAttributeValue(null, "gid");
                  String virsst = parser2.getAttributeValue(null, "match_value");
				  Log.d("zhai","loadVirPhoneMatchOverrides:mccmnc=" + mccmnc +" virspn=" + virspn +
				  				" virimsi=" + virimsi + " virgid=" + virgid + " virsst=" + virsst);
				  String virimsi_str ="";
				  String virgid_str = "";
				  String virsst_str = "";
				  if(!virimsi.equals(""))
				  virimsi_str = virimsi;
				  if(!virgid.equals(""))
				  virgid_str = virgid.concat("ffffff").substring(0,10).toLowerCase();
				  if(!virsst.equals(""))
				  virsst_str = virsst.concat("ffffffffffffffff").substring(0,20).toLowerCase();
				   Log.d("zhai","loadVirPhoneMatchOverrides:virimsi_str=" + virimsi_str +" virgid_str=" + 
				   	virgid_str +" virsst_str=" + virsst_str);
				  /*String mcc = "460";
				  try {
					  if(mccmnc!=null && mccmnc.length()>=5 && mccmnc.length()<=6){
						  mcc = mccmnc.substring(0, 3);
					  }
				  } catch (IndexOutOfBoundsException  e) {
					  e.printStackTrace();
				  }*/
				  //String mcc = parser.getAttributeValue(null, "mcc");
				  String minmatch = parser2.getAttributeValue(null, "num_match");
				  String putkey = mccmnc + virspn + virimsi_str + virgid_str + virsst_str;
				  Log.d("zhai","loadVirPhoneMatchOverrides:putkey=" + putkey + " minmatch=" + minmatch);
	
				  virPhoneMatchMapWithMcc.put(mccmnc + virspn + virimsi_str + virgid_str + virsst_str, minmatch);
			  }
			  eccReader2.close();
		  } catch (XmlPullParserException e) {
			  Log.w(LOG_TAG, "Exception in virtualNets-conf parser2 " + e);
		  } catch (IOException e) {
			  Log.w(LOG_TAG, "Exception in virtualNets-conf parser2 " + e);
		  } finally {
			  try {
				  if (eccReader2 != null) {
					  eccReader2.close();
				  }
			  } catch (IOException e) {}
		  }
		}

}
