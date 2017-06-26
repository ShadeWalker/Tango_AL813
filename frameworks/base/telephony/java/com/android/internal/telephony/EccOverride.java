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
import java.util.Iterator;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.os.Environment;
import android.util.Log;
import android.util.Xml;

import com.android.internal.util.XmlUtils;

// MVNO-API START
//import com.mediatek.settings.FeatureOption;
import java.util.ArrayList;
import java.lang.Integer;
// MVNO-API END

public class EccOverride {
    private static HashMap<String, String> eccMapWithSim;
	private static HashMap<String, String> EccMapWithoutSim;
	private static HashMap<String, String> realEccMapWithSim;
	private static HashMap<String, String> specialEccMapWithSim;
	private static String separator=",";
	//Ecc without card autoAdapt by Network Mcc
	private static HashMap<String, String> eccMapByNetworkMccWithoutSim;
    private static String eccWithSimDefault;
    private static String eccWithoutSimDefault;
    private static String realEccWithSimDefault;
    private static String specialEccWithSimDefault;
	/*
	* All virtual mccmncgid, mccmncpnn, mccmncspn, imsipattern
	*/
    private static ArrayList<String> virtualKeyList;
	/*
	* All virtual mccmnc 
	*/
    private static ArrayList<String> virtualMccmncList;
	
    private static EccOverride sInstance;
    static final Object sInstSync = new Object();
    private static final String LOG_TAG = "EccOverride";
    private static final String PARTNER_ECC_WITHOUTSIM_OVERRIDE_PATH ="etc/globalEcc-conf.xml";
    private static final String PARTNER_ECC_WITHSIM_OVERRIDE_PATH ="etc/global-ecc-withsim-conf.xml";
    private static final String PARTNER_VIRTUAL_ECC_OVERRIDE_PATH ="etc/virtualNets-conf.xml";
    public static EccOverride getInstance() {
        synchronized (sInstSync) {
            if (sInstance == null) {
                sInstance = new EccOverride();
            }
        }
        return sInstance;
    }

    private EccOverride() {
        eccMapWithSim 		 = new HashMap<String, String>();
        EccMapWithoutSim          = new HashMap<String, String>();
        realEccMapWithSim 	 = new HashMap<String, String>();
        specialEccMapWithSim = new HashMap<String, String>();
        virtualKeyList 		 = new ArrayList<String>();
        virtualMccmncList	 = new ArrayList<String>();
		//eccWithSimDefault 		 ={"112,911,000,08,110,118,119,999,120,122"};
		//realEccWithSimDefault 	 = {"112,911"};
                // specialEccWithSimDefault = "000,08,110,118,119,999,120,122";
		loadEccWithoutSIMOverrides();
		loadEccWithSIMOverrides();
		loadVirtualEccOverrideByEfSpn();
		loadVirtualEccOverrideByGID1();
		loadVirtualEccOverrideByImsi();
		loadVirtualEccOverrideByEFfile() ;
		
    }
      public String getEccWithoutSim(String mccmnc) {
		if(EccMapWithoutSim.containsKey(mccmnc)){
			return EccMapWithoutSim.get(mccmnc);
		}
		return null;
	}

       public String getEccWithSim(String mccmnc) {
	        if(getSpecialEccWithSim( mccmnc)==null ||getSpecialEccWithSim( mccmnc).length()==0){
		       return getRealEccWithSim(mccmnc) ;
	   	}else if(getRealEccWithSim(mccmnc)==null ||getRealEccWithSim(mccmnc).length()==0 ){
	   	      return  getSpecialEccWithSim( mccmnc);     
	   	}else{
	   	          return  getRealEccWithSim(mccmnc) +separator+ getSpecialEccWithSim( mccmnc);     
	   	 }
      }
	public String getRealEccWithSim(String mccmnc) {
		if(realEccMapWithSim.containsKey(mccmnc)){
          	return realEccMapWithSim.get(mccmnc);
		}
		return null;
    }

    public String getSpecialEccWithSim(String mccmnc) {
		if(specialEccMapWithSim.containsKey(mccmnc)){
        	         return specialEccMapWithSim.get(mccmnc);
		}
		return null;
    }

	public boolean hasVirtualKey(String virtualkey) {
		if(virtualkey!=null && virtualkey.length()>0){
			return virtualKeyList.contains(virtualkey);
		}
		return false;
    }

	public boolean hasVirtualMccmnc(String mccmnc) {
		if(mccmnc!=null && mccmnc.length()>0){
			return virtualMccmncList.contains(mccmnc);
		}
		return false;
    }

	public String getVirtualKeyByImsi(String imsi) {
		for (String mccmncKey : virtualKeyList) {
			Log.i("huangshuo","[getVirtualKeyByImsi]:mccmncKey"+mccmncKey);
			if(imsi !=null && imsi.startsWith(mccmncKey))
				return mccmncKey;
		}
		return null;
    }


	
    public String getEccByNetworkMccWithoutSim(String mcc) {
		if(eccMapByNetworkMccWithoutSim.containsKey(mcc)){
        	return eccMapByNetworkMccWithoutSim.get(mcc);
		}
		return eccWithoutSimDefault;
    }
    
	private static void loadVirtualEccOverrideByEfSpn() {
			FileReader eccReader;
			Log.d(LOG_TAG, "loadVirtualEccOverrideByEfSpn");
			Log.w(LOG_TAG, "open file:	" + Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_ECC_OVERRIDE_PATH);
			final File eccFile = new File(Environment.getRootDirectory(), PARTNER_VIRTUAL_ECC_OVERRIDE_PATH);
	
			try {
				eccReader = new FileReader(eccFile);
			} catch (FileNotFoundException e) {
				Log.w(LOG_TAG, "Can't open " + Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_ECC_OVERRIDE_PATH);
				return;
			}
			try {
				XmlPullParser parser = Xml.newPullParser();
				parser.setInput(eccReader);
				XmlUtils.beginDocument(parser, "virtualNets");
				while (true) {
					XmlUtils.nextElement(parser);
					String name = parser.getName();
					//Log.w(LOG_TAG, "name = "+name);
					if (name==null || !"virtualNet".equals(name)) {
						break;
					}
					String mccmnc			 = parser.getAttributeValue(null, "numeric");
					String spn		 = parser.getAttributeValue(null, "spn");
					String realEccWithSim	 = parser.getAttributeValue(null, "real_ecc_withcard");
					String specialEccWithSim = parser.getAttributeValue(null, "special_ecc_withcard");
					//String eccWithoutSim = parser.getAttributeValue(null, "ecc_without_card");
					Log.w(LOG_TAG, "mccmnc = "+mccmnc);
					Log.w(LOG_TAG, "spn = "+spn);
					Log.w(LOG_TAG, "realEccWithSim = "+realEccWithSim);
					Log.w(LOG_TAG, "specialEccWithSim = "+specialEccWithSim);
					//Log.w(LOG_TAG, "eccWithoutSim = "+eccWithoutSim);
					if(mccmnc!=null && spn!=null && spn.length()>0){		
						if(!virtualMccmncList.contains(mccmnc)){
							virtualMccmncList.add(mccmnc);
							Log.w(LOG_TAG, "mccmnc+spn = "+mccmnc+spn);
						}
						virtualKeyList.add(mccmnc+spn);
						eccMapWithSim.put(mccmnc+spn, realEccWithSim+","+specialEccWithSim);
						realEccMapWithSim.put(mccmnc+spn, realEccWithSim);
						specialEccMapWithSim.put(mccmnc+spn, specialEccWithSim);	
						//EccMapWithoutSim.put(mccmnc, eccWithoutSim);
					}
				}
				eccReader.close();
				Log.w(LOG_TAG, "loadVirtualEccOverrideByEfSpn.close()");
			} catch (XmlPullParserException e) {
				Log.w(LOG_TAG, "Exception in global-specialecc-conf parser " + e);
			} catch (IOException e) {
				Log.w(LOG_TAG, "Exception in global-specialEcc-conf parser " + e);
			}
		}
	        private static void loadVirtualEccOverrideByEFfile() {
			FileReader eccReader;
			Log.d(LOG_TAG, "loadVirtualEccOverrideByEFfile");
			Log.w(LOG_TAG, "open file:	" + Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_ECC_OVERRIDE_PATH);
			final File eccFile = new File(Environment.getRootDirectory(), PARTNER_VIRTUAL_ECC_OVERRIDE_PATH);
			if(eccFile==null){
				Log.w(LOG_TAG, "eccFile == null ");
			}
			try {
				eccReader = new FileReader(eccFile);
			} catch (FileNotFoundException e) {
				Log.w(LOG_TAG, "Can't open " + Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_ECC_OVERRIDE_PATH);
				return;
			}
			try {
				XmlPullParser parser = Xml.newPullParser();
				parser.setInput(eccReader);
				XmlUtils.beginDocument(parser, "virtualNets");
				while (true) {
					XmlUtils.nextElement(parser);
					String name = parser.getName();
					//Log.w(LOG_TAG, "name = "+name);
					if (name==null || !"virtualNet".equals(name)) {
						break;
					}
					String mccmnc			 = parser.getAttributeValue(null, "numeric");
					String filepattern		 = parser.getAttributeValue(null, "match_file");
					String realEccWithSim	 = parser.getAttributeValue(null, "real_ecc_withcard");
					String specialEccWithSim = parser.getAttributeValue(null, "special_ecc_withcard");	
					//String eccWithoutSim = parser.getAttributeValue(null, "ecc_without_card");
					Log.w(LOG_TAG, "mccmnc = "+mccmnc);
					Log.w(LOG_TAG, "filepattern = "+filepattern);
					Log.w(LOG_TAG, "realEccWithSim = "+realEccWithSim);
					Log.w(LOG_TAG, "specialEccWithSim = "+specialEccWithSim);	
					//Log.w(LOG_TAG, "eccWithoutSim = "+eccWithoutSim);
					if(mccmnc!=null && filepattern!=null  && filepattern.length()>0){		
						if(!virtualMccmncList.contains(mccmnc)){
							virtualMccmncList.add(mccmnc);
						}
						virtualKeyList.add(mccmnc+filepattern);
						eccMapWithSim.put(filepattern, realEccWithSim+","+specialEccWithSim);
						realEccMapWithSim.put(filepattern, realEccWithSim);
						specialEccMapWithSim.put(filepattern, specialEccWithSim);	
						//EccMapWithoutSim.put(mccmnc, eccWithoutSim);
					}
				}
				eccReader.close();
				Log.w(LOG_TAG, "loadVirtualEccOverrideByImsi.close()");
			} catch (XmlPullParserException e) {
				Log.w(LOG_TAG, "Exception in global-specialecc-conf parser " + e);
			} catch (IOException e) {
				Log.w(LOG_TAG, "Exception in global-specialEcc-conf parser " + e);
			}finally{
				Log.w(LOG_TAG, "close()");
			}
		}
	
		private static void loadVirtualEccOverrideByImsi() {
			FileReader eccReader;
			Log.d(LOG_TAG, "loadVirtualEccOverrideByImsi");
			Log.w(LOG_TAG, "open file:	" + Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_ECC_OVERRIDE_PATH);
			final File eccFile = new File(Environment.getRootDirectory(), PARTNER_VIRTUAL_ECC_OVERRIDE_PATH);
			if(eccFile==null){
				Log.w(LOG_TAG, "eccFile == null ");
			}
			try {
				eccReader = new FileReader(eccFile);
			} catch (FileNotFoundException e) {
				Log.w(LOG_TAG, "Can't open " + Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_ECC_OVERRIDE_PATH);
				return;
			}
	
			try {
				XmlPullParser parser = Xml.newPullParser();
				parser.setInput(eccReader);
				XmlUtils.beginDocument(parser, "virtualNets");
				while (true) {
					XmlUtils.nextElement(parser);
					String name = parser.getName();
					//Log.w(LOG_TAG, "name = "+name);
					if (name==null || !"virtualNet".equals(name)) {
						break;
					}
					String mccmnc			 = parser.getAttributeValue(null, "numeric");
					String imsipattern		 = parser.getAttributeValue(null, "imsi_start");
					String realEccWithSim	 = parser.getAttributeValue(null, "real_ecc_withcard");
					String specialEccWithSim = parser.getAttributeValue(null, "special_ecc_withcard");	
					//String eccWithoutSim = parser.getAttributeValue(null, "ecc_without_card");
					Log.w(LOG_TAG, "mccmnc = "+mccmnc);
					Log.w(LOG_TAG, "imsipattern = "+imsipattern);
					Log.w(LOG_TAG, "realEccWithSim = "+realEccWithSim);
					Log.w(LOG_TAG, "specialEccWithSim = "+specialEccWithSim);
					//Log.w(LOG_TAG, "eccWithoutSim = "+eccWithoutSim);
					if(mccmnc!=null && imsipattern!=null && imsipattern.length()>0){		
						if(!virtualMccmncList.contains(mccmnc)){
							virtualMccmncList.add(mccmnc);
						}
						virtualKeyList.add(imsipattern);
						eccMapWithSim.put(imsipattern, realEccWithSim+","+specialEccWithSim);
						realEccMapWithSim.put(imsipattern, realEccWithSim);
						specialEccMapWithSim.put(imsipattern, specialEccWithSim);
						//EccMapWithoutSim.put(mccmnc, eccWithoutSim);
					}
				}
				eccReader.close();
				Log.w(LOG_TAG, "loadVirtualEccOverrideByImsi.close()");
			} catch (XmlPullParserException e) {
				Log.w(LOG_TAG, "Exception in global-specialecc-conf parser " + e);
			} catch (IOException e) {
				Log.w(LOG_TAG, "Exception in global-specialEcc-conf parser " + e);
			}finally{
				Log.w(LOG_TAG, "close()");
			}
		}
	
		private static void loadVirtualEccOverrideByGID1() {
			FileReader eccReader;
			Log.d(LOG_TAG, "loadVirtualEccOverrideByGID1");
			Log.w(LOG_TAG, "open file:	" + Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_ECC_OVERRIDE_PATH);
			final File eccFile = new File(Environment.getRootDirectory(), PARTNER_VIRTUAL_ECC_OVERRIDE_PATH);
	
			try {
				eccReader = new FileReader(eccFile);
			} catch (FileNotFoundException e) {
				Log.w(LOG_TAG, "Can't open " + Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_ECC_OVERRIDE_PATH);
				return;
			}
	
			try {
				XmlPullParser parser = Xml.newPullParser();
				parser.setInput(eccReader);
				XmlUtils.beginDocument(parser, "virtualNets");
				while (true) {
					XmlUtils.nextElement(parser);
					String name = parser.getName();
					//Log.w(LOG_TAG, "name = "+name);
					if (name==null || !"virtualNet".equals(name)) {
						break;
					}
					String mccmnc			 = parser.getAttributeValue(null, "numeric");
					String gid	 = parser.getAttributeValue(null, "gid");
					Log.i("huangshuo","[loadVirtualEccOverrideByGID1]:gid="+gid);
					String realEccWithSim	 = parser.getAttributeValue(null, "real_ecc_withcard");
					String specialEccWithSim = parser.getAttributeValue(null, "special_ecc_withcard");
					//String eccWithoutSim = parser.getAttributeValue(null, "ecc_without_card");
					//Log.w(LOG_TAG, "eccWithoutSim = "+eccWithoutSim);
					if(gid!=null && mccmnc!=null && gid.length()>2){	
						gid= (gid.concat("ffffff".substring(0,10-gid.length()))).substring(2);
						Log.w(LOG_TAG, "gid = "+gid);
                        gid = gid.toLowerCase();
						if(!virtualMccmncList.contains(mccmnc)){
							virtualMccmncList.add(mccmnc);
							Log.w(LOG_TAG, "mccmnc+gid = "+mccmnc+gid);
						}
						virtualKeyList.add(mccmnc+gid);
						eccMapWithSim.put(mccmnc+gid, realEccWithSim+","+specialEccWithSim);
						realEccMapWithSim.put(mccmnc+gid, realEccWithSim);
						specialEccMapWithSim.put(mccmnc+gid, specialEccWithSim);
						//EccMapWithoutSim.put(mccmnc, eccWithoutSim);
					}
				}
				eccReader.close();
				Log.w(LOG_TAG, "loadVirtualEccOverrideByGID1.close()");
			} catch (XmlPullParserException e) {
				Log.w(LOG_TAG, "Exception in global-specialecc-conf parser " + e);
			} catch (IOException e) {
				Log.w(LOG_TAG, "Exception in global-specialEcc-conf parser " + e);
			}
		}
        private static void loadEccWithoutSIMOverrides() {
             FileReader eccReader;
             Log.d(LOG_TAG, "loadEccOverrides");
	     Log.w(LOG_TAG, "open file:" + Environment.getRootDirectory() + "/" + PARTNER_ECC_WITHOUTSIM_OVERRIDE_PATH);
             final File eccFile = new File(Environment.getRootDirectory(),PARTNER_ECC_WITHOUTSIM_OVERRIDE_PATH);
	     Log.w(LOG_TAG, "eccFile " +eccFile);
             try {
                    eccReader = new FileReader(eccFile);
             } catch (FileNotFoundException e) {
                     Log.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_ECC_WITHOUTSIM_OVERRIDE_PATH);
                    return;
             }
        try {
                  XmlPullParser parser = Xml.newPullParser();
                 parser.setInput(eccReader);
                 XmlUtils.beginDocument(parser, "eccs");
                 while (true) {
                     XmlUtils.nextElement(parser);
                     String name = parser.getName();
                if (!"globalEcc".equals(name)) {
                    break;
                }
                String mccmnc 	  = parser.getAttributeValue(null, "carrier");
                String eccWithoutSim = parser.getAttributeValue(null, "ecc_without_card");
                if(mccmnc!=null){
		   EccMapWithoutSim.put(mccmnc, eccWithoutSim);
			/*if( mccmnc.equals("999999")){
				eccWithoutSimDefault = parser.getAttributeValue(null, "ecc_without_card");
			 }*/
		}
              }
		 eccReader.close();
		 Log.w(LOG_TAG, "loadEccOverrides.close()");
        } catch (XmlPullParserException e) {
            Log.w(LOG_TAG, "Exception in global-ecc-conf parser " + e);
        } catch (IOException e) {
            Log.w(LOG_TAG, "Exception in global-ecc-conf parser " + e);
        }
    }

	/*
	*	parser 
	*
	*
	*/
	private static void loadEccWithSIMOverrides() {
        FileReader eccReader;
        Log.d(LOG_TAG, "loadEccWithSIMOverrides");
	Log.w(LOG_TAG, "open file:	" + Environment.getRootDirectory() + "/" + PARTNER_ECC_WITHSIM_OVERRIDE_PATH);
        final File eccFile = new File(Environment.getRootDirectory(), PARTNER_ECC_WITHSIM_OVERRIDE_PATH);

        try {
            eccReader = new FileReader(eccFile);
        } catch (FileNotFoundException e) {
            Log.w(LOG_TAG, "Can't open " + Environment.getRootDirectory() + "/" + PARTNER_ECC_WITHSIM_OVERRIDE_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(eccReader);
            XmlUtils.beginDocument(parser, "eccs");
            while (true) {
                XmlUtils.nextElement(parser);
                String name = parser.getName();
                if (name==null || !"globalEcc".equals(name)) {
					Log.w(LOG_TAG, "break ");
                    break;
                }
                String mccmnc 			 = parser.getAttributeValue(null, "carrier");
                String realEccWithSim 	 = parser.getAttributeValue(null, "real_ecc_withcard");
                String specialEccWithSim = parser.getAttributeValue(null, "special_ecc_withcard");
		if(mccmnc!=null){				
		    realEccMapWithSim.put(mccmnc, realEccWithSim);
	             specialEccMapWithSim.put(mccmnc, specialEccWithSim);
		     /* if( mccmnc.equals("999999")){
			    realEccWithSimDefault	 = parser.getAttributeValue(null, "real_ecc_withcard");
			    specialEccWithSimDefault = parser.getAttributeValue(null, "special_ecc_withcard");
			}*/
		  }
                }
		eccReader.close();
		//Log.w(LOG_TAG, "loadEccWithSIMOverrides.close()");
        } catch (XmlPullParserException e) {
            Log.w(LOG_TAG, "Exception in global-specialecc-conf parser " + e);
        } catch (IOException e) {
            Log.w(LOG_TAG, "Exception in global-specialEcc-conf parser " + e);
        }
      }
	private static void printMap(HashMap<String, String> map) {
		Iterator iter = map.entrySet().iterator(); 
		while (iter.hasNext()) { 
			Map.Entry entry = (Map.Entry) iter.next(); 
			Object key = entry.getKey(); 
			Object val = entry.getValue(); 
			Log.w(LOG_TAG, ""+key+","+val);
		} 
	}	


	private static String containsKey(ArrayList<String> list, String mKey) {
		for (String mccmncKey : list) {
			if(mccmncKey.startsWith(mKey))
				return mccmncKey;
		}
		return null;
	}

}

