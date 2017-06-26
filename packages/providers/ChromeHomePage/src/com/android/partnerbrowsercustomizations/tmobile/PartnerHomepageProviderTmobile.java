// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Package path can be changed, but should match <manifest package="..."> in AndroidManifest.xml.
/* DTS2014081805667 yexiaoyan 20140819 created */
package com.android.partnerbrowsercustomizations.tmobile;

import com.android.partnerbrowsercustomizations.tmobile.R;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.content.Context;
//HQ_zhangteng added for HQ01878820 at 2016-04-26 begin
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.telecom.TelecomManager;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
//HQ_zhangteng added for HQ01878820 at 2016-04-26 end

// Class name can be changed, but should match <provider android:name="..."> in AndroidManifest.xml.
public class PartnerHomepageProviderTmobile extends ContentProvider {
    // "http://www.android.com/" is just an example. Please replace this to actual homepage.
    // Other strings in this class must remain as it is.
    // private static String HOMEPAGE_URI = "http://www.t-mobile-favoriten.de/";

    private static final int URI_MATCH_HOMEPAGE = 0;
    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
//    static {
//        URI_MATCHER.addURI("com.android.partnerbrowsercustomizations", "homepage",
//                URI_MATCH_HOMEPAGE);
//    }

    //HQ_zhangteng added for HQ01878820 at 2016-04-26 begin
    private static final String TAG = "ChromeHomepage";

    private static final String PREFERENCES_FILENAME = "chppref";

    private static final String ACTIVE_CONFIGURATION_PREFNAME = "config";

    private static final String FIXED_HOMEPAGE_URL = "fixed_homepage_url";

    private static final String BOOKMARKS_CONFIG_XML = "bookmarks_config.xml";

    private static final String BROWSER_CONFIG_XML = "browser_config.xml";

    private SharedPreferences sharedPreferences;

    private String matchedMccmnc = null;

    private String homepageUrlFromXML = null;

    private String getHomepageUrlFromXML(String mccmnc){
        XmlPullParserFactory mXmlPullParserFactory = null;
        InputStream mInputStream = null;
        String nodeName = null;
        boolean simMatched = false;
        String homepageUrl = null;
        try {
            mXmlPullParserFactory = XmlPullParserFactory.newInstance();
            XmlPullParser mXmlPullParser = mXmlPullParserFactory.newPullParser();
            mInputStream = new BufferedInputStream(new FileInputStream(new File("/data/cust/xml/" + BOOKMARKS_CONFIG_XML)));
            mXmlPullParser.setInput(mInputStream, "UTF-8");
            int eventType = mXmlPullParser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT){
                nodeName = mXmlPullParser.getName();
                switch (eventType){
                    case XmlPullParser.START_TAG:
                        if ("simcardtype".equalsIgnoreCase(nodeName)){
                            //attr(0) = mccmnc
                            if (mccmnc != null && mccmnc.equalsIgnoreCase(mXmlPullParser.getAttributeValue(0))){
                                matchedMccmnc = mXmlPullParser.getAttributeValue(0);
                                Log.i(TAG, "simMatched = true;");
                                simMatched = true;
                            }else{
                                simMatched = false;
                            }
                        }else if (simMatched && "homepage".equalsIgnoreCase(nodeName)){
                            //attr(0) = homepageurl
                            homepageUrl = mXmlPullParser.getAttributeValue(0);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        break;
                    default:
                        break;
                }
                eventType = mXmlPullParser.next();
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            Log.i(TAG, BOOKMARKS_CONFIG_XML + " not found!!!");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (mInputStream != null){
                try {
                    mInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (matchedMccmnc != null){
            Log.i(TAG, "parse from " + BOOKMARKS_CONFIG_XML);
            return homepageUrl;
        }else {
            Log.i(TAG, "parse from " + BROWSER_CONFIG_XML);
            return getHomepageUrlFromDefaultXML();
        }
    }

    private String getHomepageUrlFromDefaultXML() {
        XmlPullParserFactory mXmlPullParserFactory = null;
        InputStream mInputStream = null;
        String nodeName = null;
        String homepageUrl = null;
        try {
            mXmlPullParserFactory = XmlPullParserFactory.newInstance();
            XmlPullParser mXmlPullParser = mXmlPullParserFactory.newPullParser();
            mInputStream = new BufferedInputStream(new FileInputStream(new File("/data/cust/xml/" + BROWSER_CONFIG_XML)));
            mXmlPullParser.setInput(mInputStream, "UTF-8");
            int eventType = mXmlPullParser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT){
                nodeName = mXmlPullParser.getName();
                switch (eventType){
                    case XmlPullParser.START_TAG:
                        if ("homepage".equalsIgnoreCase(nodeName)){
                            //attr(0) = url
                            homepageUrl = mXmlPullParser.getAttributeValue(0);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        break;
                    default:
                        break;
                }
                eventType = mXmlPullParser.next();
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            Log.i(TAG, BROWSER_CONFIG_XML + " not found!!!");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (mInputStream != null){
                try {
                    mInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return homepageUrl;
    }

    private String getConfigSignature() {
        //HQ_zhangteng modified for Single Sim Card at 2016-05-18 begin
	String mccMnc = null;
	if (TelephonyManager.getDefault().getSimCount() < 2){
	    mccMnc = TelephonyManager.getDefault().getSimOperator();
	    Log.i(TAG, "simCount = " + TelephonyManager.getDefault().getSimCount() + ", mccMnc = " + mccMnc);
	}else{
            final TelecomManager telecomManager = TelecomManager.from(getContext());
            PhoneAccountHandle phoneAccountHandle = telecomManager.getUserSelectedOutgoingPhoneAccount();
            int defaultCallSub = -1;
            if (phoneAccountHandle != null) {
                final PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
                final String phoneAccountId = phoneAccountHandle.getId();
		Log.i(TAG, "phoneAccountId = " + phoneAccountId);
                if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                    	&& TextUtils.isDigitsOnly(phoneAccountId)){
                    defaultCallSub = Integer.parseInt(phoneAccountId);
		    Log.i(TAG, "defaultCallSub = " + defaultCallSub);
                }
            }
            if (SubscriptionManager.isValidSubscriptionId(defaultCallSub)) {
                mccMnc = TelephonyManager.getDefault().getSimOperator(defaultCallSub);
                Log.i(TAG, "mccMnc = " + mccMnc);
            }
	}
        return mccMnc;
	//HQ_zhangteng modified for Single Sim Card at 2016-05-18 end
    }

    private void setPreferenceConfig(String homepageUrl) {
        Editor editor = sharedPreferences.edit();
        editor.putString(ACTIVE_CONFIGURATION_PREFNAME, getConfigSignature());
        editor.putString(FIXED_HOMEPAGE_URL, homepageUrl == null?"http://www.google.com/":homepageUrl);
        editor.commit();
    }

    private boolean isSimcardChanged(){
        String newSignature = getConfigSignature();
        String activeSignature = sharedPreferences.getString(ACTIVE_CONFIGURATION_PREFNAME, null);
        if (activeSignature == null || !activeSignature.equals(newSignature)) {
            return true;
        }else {
            return false;
        }
    }
    //HQ_zhangteng added for HQ01878820 at 2016-04-26 end

    @Override
    public boolean onCreate() {

    	String HOMEPAGE_URI = getContext().getString(R.string.homepage_uri).toString();
    	 URI_MATCHER.addURI("com.android.partnerbrowsercustomizations", "homepage",
                 URI_MATCH_HOMEPAGE);
        //HQ_zhangteng added for HQ01878820 at 2016-04-26 begin
        sharedPreferences = getContext().getSharedPreferences(PREFERENCES_FILENAME, Context.MODE_PRIVATE);
        //HQ_zhangteng added for HQ01878820 at 2016-04-26 end
        return true;
    }


	@Override
    public String getType(Uri uri) {
        // In fact, Chrome does not call this.
        // Just a recommaned ContentProvider practice in general.
        switch (URI_MATCHER.match(uri)) {
            case URI_MATCH_HOMEPAGE:
                return "vnd.android.cursor.item/partnerhomepage";
            default:
                return null;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        switch (URI_MATCHER.match(uri)) {
            case URI_MATCH_HOMEPAGE:
                MatrixCursor cursor = new MatrixCursor(new String[] { "homepage" }, 1);
//              cursor.addRow(new Object[] { HOMEPAGE_URI });
                //HQ_zhangteng added for HQ01878820 at 2016-04-26 begin
                //cursor.addRow(new Object[] { getContext().getString(R.string.homepage_uri).toString() });
                String homepageUrl = null;
                if (isSimcardChanged()){
                    homepageUrl = getHomepageUrlFromXML(getConfigSignature());
                    setPreferenceConfig(homepageUrl);
                }else{
                    homepageUrl = sharedPreferences.getString(FIXED_HOMEPAGE_URL, "http://www.google.com/");
                }
                cursor.addRow(new Object[] { homepageUrl });
                //HQ_zhangteng added for HQ01878820 at 2016-04-26 end
                return cursor;
            default:
                return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

}

