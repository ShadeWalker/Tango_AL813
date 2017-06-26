/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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

package com.android.dialer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.os.Build;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import com.android.common.io.MoreCloseables;
import com.android.contacts.common.database.NoNullCursorAsyncQueryHandler;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment.SelectPhoneAccountListener;
import com.android.dialer.calllog.PhoneAccountUtils;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.dialer.ext.ExtensionManager;
import com.mediatek.dialer.util.DialerFeatureOptions;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

//add by majian
import android.os.SystemProperties;

//add by majian
import android.os.SystemProperties;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import android.content.DialogInterface;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import android.os.Build;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.huawei.util.FSJ;
import com.mediatek.telephony.TelephonyManagerEx;
import android.telephony.TelephonyManager;





/**
 * Helper class to listen for some magic character sequences
 * that are handled specially by the dialer.
 *
 * Note the Phone app also handles these sequences too (in a couple of
 * relatively obscure places in the UI), so there's a separate version of
 * this class under apps/Phone.
 *
 * TODO: there's lots of duplicated code between this class and the
 * corresponding class under apps/Phone.  Let's figure out a way to
 * unify these two classes (in the framework? in a common shared library?)
 */
public class SpecialCharSequenceMgr {
    private static final String TAG = "SpecialCharSequenceMgr";

    private static final String SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE";
    private static final String MMI_IMEI_DISPLAY = "*#06#";
    private static final String MMI_REGULATORY_INFO_DISPLAY = "*#07#";
	  private static final String HQ_VERSION_DISPLAY = "*#*#929#*#*";//Modify by liugang for HQ02052413
    private static final boolean DEBUG = DialtactsActivity.DEBUG;

	private static final String HQ_UNLOCK_SIMLOCK_CODE="*#*#86012715#*#*";

    /// M: for OP09 6M project @{
    private static final String PRL_VERSION_DISPLAY = "*#0000#";
    private static final String ACTION_CDMAINFO = "android.intent.action.CdmaInfoSpecification";
    private static final String KEY_SUB_ID = "subid";
    /// @}

    /**
     * Remembers the previous {@link QueryHandler} and cancel the operation when needed, to
     * prevent possible crash.
     *
     * QueryHandler may call {@link ProgressDialog#dismiss()} when the screen is already gone,
     * which will cause the app crash. This variable enables the class to prevent the crash
     * on {@link #cleanup()}.
     *
     * TODO: Remove this and replace it (and {@link #cleanup()}) with better implementation.
     * One complication is that we have SpecialCharSequenceMgr in Phone package too, which has
     * *slightly* different implementation. Note that Phone package doesn't have this problem,
     * so the class on Phone side doesn't have this functionality.
     * Fundamental fix would be to have one shared implementation and resolve this corner case more
     * gracefully.
     */
    private static QueryHandler sPreviousAdnQueryHandler;
    private static final String ICC_ADN_SUBID_URI = "content://icc/adn/subId/";

    /** This class is never instantiated. */
    private SpecialCharSequenceMgr() {
    }

    public static boolean handleChars(Context context, String input, EditText textField) {
        /// M: for ALPS01692450 @{
        // check null
        if(context == null) {
            return false;
        }
        /// @}

        //get rid of the separators so that the string gets parsed correctly
        String dialString = PhoneNumberUtils.stripSeparators(input);

        if (
            		handleSIMLockCode(context,input)//add by zhangjinqiang for HW_SIMLock
				||handlePSNInfo(context, dialString)
			    ||handleDeviceIdDisplay(context, dialString)
                //|| handleRegulatoryInfoDisplay(context, dialString)//add by zhaizhanfeng for SAR at 151027
                || handlePinEntry(context, dialString)
                || handleAdnEntry(context, dialString, textField)
                //|| handleSecretCode(context, dialString) Delete byliugang for HQ02052413
                || handleHarawareInfo(context, input) //add by majian for ####7599#
		|| handleFactoryTestCode(context, dialString)//add for factory test
		|| handleFactory2TestCode(context, dialString) //add for mmi2
                || handleRuntimeTestCode(context, dialString) //add for running test
                || handleHuaweiEngineeringModeCode(context,input) //by yanzewen
                || handleVersionCode(context,input) // by jingguangyao
                /// M: for OP09 6M project @{
                || mtkHandleVersionCode(context, dialString)
                /// @}
                /// M: for plug-in @{
                || ExtensionManager.getInstance().getDialPadExtension().handleChars(context, dialString)
                /// @}
                || handleRecoverySysCode(context, input)
                || handleSnCode(context, input)
                || handleLoggerCode(context, input)
                || handleHQInternalVersionCode(context, input)
                || handleHWInternalVersionCode(context, input)
                || handleHuaweiMMIModeCode(context, input)
                || handleSARCode(context, input)//add by zhaizhanfeng for SAR at 151027
                || handleSecretCode(context, dialString)
				) {
            return true;
        }

        return false;
    }

    /**
     * Cleanup everything around this class. Must be run inside the main thread.
     *
     * This should be called when the screen becomes background.
     */
    public static void cleanup() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.wtf(TAG, "cleanup() is called outside the main thread");
            return;
        }

        if (sPreviousAdnQueryHandler != null) {
            sPreviousAdnQueryHandler.cancel();
            sPreviousAdnQueryHandler = null;
        }
    }

    /**
     * Handles secret codes to launch arbitrary activities in the form of *#*#<code>#*#*.
     * If a secret code is encountered an Intent is started with the android_secret_code://<code>
     * URI.
     *
     * @param context the context to use
     * @param input the text to check for a secret code in
     * @return true if a secret code was encountered
     */
    static boolean handleSecretCode(Context context, String input) {
        // Secret codes are in the form *#*#<code>#*#*

        /// M: for plug-in @{
        input = ExtensionManager.getInstance().getDialPadExtension().handleSecretCode(input);
        /// @}

        int len = input.length();
        if (len > 8 && input.startsWith("*#*#") && input.endsWith("#*#*")) {
            final Intent intent = new Intent(SECRET_CODE_ACTION,
                    Uri.parse("android_secret_code://" + input.substring(4, len - 4)));
			
			// /added by guofeiyao
            //if ("1234".equals(input.substring(4, len - 4))) return false;
            //if ("4636".equals(input.substring(4, len - 4))) return false;
			// /end
			  
		if ("2846579".equals(input.substring(4, len - 4))) return false;
		
		/*HQ_liugang add for remove 784512*/
		if ("784512".equals(input.substring(4, len - 4))) return false;
		/*HQ_liugang add end*/
		
            context.sendBroadcast(intent);
            return true;
        }

        return false;
    }

    /**
     * Handle ADN requests by filling in the SIM contact number into the requested
     * EditText.
     *
     * This code works alongside the Asynchronous query handler {@link QueryHandler}
     * and query cancel handler implemented in {@link SimContactQueryCookie}.
     */
    static boolean handleAdnEntry(final Context context, String input, EditText textField) {
        /* ADN entries are of the form "N(N)(N)#" */

        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        /** M: Bug Fix for ALPS02007941 @{ */
        if (telephonyManager == null
                || (telephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_GSM
                    && telephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_CDMA)) {
            return false;
        }
        /** @} */
        // if the phone is keyguard-restricted, then just ignore this
        // input.  We want to make sure that sim card contacts are NOT
        // exposed unless the phone is unlocked, and this code can be
        // accessed from the emergency dialer.
        KeyguardManager keyguardManager =
                (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager.inKeyguardRestrictedInputMode()) {
            return false;
        }

        int len = input.length();
        if ((len > 1) && (len < 5) && (input.endsWith("#"))) {
            try {
                // get the ordinal number of the sim contact
                final int index = Integer.parseInt(input.substring(0, len-1));

                /// M: ALPS01760178, The index of contacts saved in sim account starts from 1. @{ 
                if (index <= 0) {
                    return false;
                }
                /// @}

                // The original code that navigated to a SIM Contacts list view did not
                // highlight the requested contact correctly, a requirement for PTCRB
                // certification.  This behaviour is consistent with the UI paradigm
                // for touch-enabled lists, so it does not make sense to try to work
                // around it.  Instead we fill in the the requested phone number into
                // the dialer text field.

                // create the async query handler
                final QueryHandler handler = new QueryHandler (context.getContentResolver());

                // create the cookie object
                final SimContactQueryCookie sc = new SimContactQueryCookie(index, handler,
                        ADN_QUERY_TOKEN);

                /// M: Fix CR ALPS01863413. Record the ADN query cookie.
                sSimContactQueryCookie = sc;

                // setup the cookie fields
                sc.setTextField(textField);

                // create the progress dialog
                sc.progressDialog = new ProgressDialog(context);
                sc.progressDialog.setTitle(R.string.simContacts_title);
                sc.progressDialog.setMessage(context.getText(R.string.simContacts_emptyLoading));
                sc.progressDialog.setIndeterminate(true);
                sc.progressDialog.setCancelable(true);
                sc.progressDialog.setOnCancelListener(sc);
                sc.progressDialog.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

                /// M: for plug-in @{
                ExtensionManager.getInstance().getDialPadExtension().customADNProgressDialog(sc.progressDialog);
                /// @}

                final TelecomManager telecomManager =
                        (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                List<PhoneAccountHandle> subscriptionAccountHandles =
                        PhoneAccountUtils.getSubscriptionPhoneAccounts(context);

                boolean hasUserSelectedDefault = subscriptionAccountHandles.contains(
                        telecomManager.getUserSelectedOutgoingPhoneAccount());

                if (subscriptionAccountHandles.size() == 1) {
                    Uri uri = SubInfoUtils.getIccProviderUri(Integer.parseInt(subscriptionAccountHandles.get(0).getId()));
                    handleAdnQuery(handler, sc, uri);
                } else if (hasUserSelectedDefault) {
                    PhoneAccountHandle defaultPhoneAccountHandle = telecomManager.getUserSelectedOutgoingPhoneAccount();
                    Uri uri = SubInfoUtils.getIccProviderUri(Integer.parseInt(defaultPhoneAccountHandle.getId()));
                    handleAdnQuery(handler, sc, uri);
                } else if (subscriptionAccountHandles.size() > 1){
                    SelectPhoneAccountListener listener = new SelectPhoneAccountListener() {
                        @Override
                        public void onPhoneAccountSelected(PhoneAccountHandle selectedAccountHandle,
                                boolean setDefault) {
                            Uri uri = SubInfoUtils.getIccProviderUri(Integer.parseInt(selectedAccountHandle.getId()));
                            handleAdnQuery(handler, sc, uri);
                            //TODO: show error dialog if result isn't valid
                        }
                        @Override
                        public void onDialogDismissed() {}
                    };

                    SelectPhoneAccountDialogFragment.showAccountDialog(
                            ((Activity) context).getFragmentManager(), subscriptionAccountHandles,
                            listener);
                } else {
                    return false;
                }

                return true;
            } catch (NumberFormatException ex) {
                // Ignore
            }
        }
        return false;
    }

    private static void handleAdnQuery(QueryHandler handler, SimContactQueryCookie cookie,
            Uri uri) {
        if (handler == null || cookie == null || uri == null) {
            Log.w(TAG, "queryAdn parameters incorrect");
            return;
        }

        // display the progress dialog
        cookie.progressDialog.show();

        // run the query.
        handler.startQuery(ADN_QUERY_TOKEN, cookie, uri, new String[]{ADN_PHONE_NUMBER_COLUMN_NAME},
                null, null, null);

        if (sPreviousAdnQueryHandler != null) {
            // It is harmless to call cancel() even after the handler's gone.
            sPreviousAdnQueryHandler.cancel();
        }
        sPreviousAdnQueryHandler = handler;
    }

    static boolean handlePinEntry(Context context, final String input) {
        if ((input.startsWith("**04") || input.startsWith("**05")) && input.endsWith("#")) {
            final TelecomManager telecomManager =
                    (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            List<PhoneAccountHandle> subscriptionAccountHandles =
                    PhoneAccountUtils.getSubscriptionPhoneAccounts(context);
            boolean hasUserSelectedDefault = subscriptionAccountHandles.contains(
                    telecomManager.getUserSelectedOutgoingPhoneAccount());

            if (subscriptionAccountHandles.size() == 1 || hasUserSelectedDefault) {
                // Don't bring up the dialog for single-SIM or if the default outgoing account is
                // a subscription account.
                return telecomManager.handleMmi(input);
            } else if (subscriptionAccountHandles.size() > 1){
                SelectPhoneAccountListener listener = new SelectPhoneAccountListener() {
                    @Override
                    public void onPhoneAccountSelected(PhoneAccountHandle selectedAccountHandle,
                            boolean setDefault) {
                        telecomManager.handleMmi(selectedAccountHandle, input);
                        //TODO: show error dialog if result isn't valid
                    }
                    @Override
                    public void onDialogDismissed() {}
                };

                SelectPhoneAccountDialogFragment.showAccountDialog(
                        ((Activity) context).getFragmentManager(), subscriptionAccountHandles,
                        listener);
            }
            return true;
        }
        return false;
    }

    // TODO: Use TelephonyCapabilities.getDeviceIdLabel() to get the device id label instead of a
    // hard-coded string.
    static boolean handleDeviceIdDisplay(Context context, String input) {
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager != null && input.equals(MMI_IMEI_DISPLAY)) {
            int labelResId = (telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) ?
                    R.string.imei : R.string.meid;

            String imei_invalid = context.getResources().getString(R.string.imei_invalid);
			
            List<String> deviceIds = new ArrayList<String>();
			/*HQ_xionghaifeng add 20150730 for display meid and two imei start*/
			/*
            for (int slot = 0; slot < telephonyManager.getPhoneCount(); slot++) 
			{
				String imei = telephonyManager.getDeviceId(slot);				
				String isShow = SystemProperties.get("ro.show.imei.meid", "0");
				if(isShow.equals("1")) 
				{
		 			if(slot == 0) 
					{
						imei = "MEID:"+imei;
		 			}
					else 
					{
						imei = "IMEI:"+imei;
		 			}
				}
				else 
				{
					imei = "IMEI:"+imei;
				}
                deviceIds.add(TextUtils.isEmpty(imei) ? imei_invalid : imei);
            }

            /// M: for ALPS01954192 @{
            // Add single IMEI plugin       
            deviceIds = ExtensionManager.getInstance().getDialPadExtension().getSingleIMEI(deviceIds);
            /// @}
			*/
			String productName = SystemProperties.get("ro.product.name", "");
			String OperatorIsoCountry = telephonyManager.getNetworkCountryIso();

			String meid = SystemProperties.get("cdma.meid", null);
			String imei1 = SystemProperties.get("gsm.imei1", null);
			String imei2 = SystemProperties.get("gsm.imei2", null);

			//Log.d("xionghaifeng", "meid: " + meid + " imei1 " + imei1 + " imei2 " + imei2

			
			/*zhongshengbin modified for HQ0891994*/
			if(Build.TYPE.equals("eng")){
                        Log.d("xhfEzio", "meid: " + meid + " imei1 " + imei1 + " imei2 " + imei2
				+ " product " + productName + " PLMN " + OperatorIsoCountry);
			}

			//Log.d("xhfEzio", "meid: " + meid + " imei1 " + imei1 + " imei2 " + imei2
			//	+ " product " + productName + " PLMN " + OperatorIsoCountry);

			if (productName.equalsIgnoreCase("TAG-TL00")
				&& OperatorIsoCountry.equals("cn"))
			{
				//meid
				if (meid != null && !TextUtils.isEmpty(meid))
				{
					//deviceIds.add("MEID:" + meid);
				}
				else
				{
					/*HQ_liugang delete for HQ01304149*/
					//deviceIds.add("MEID:" + imei_invalid);
				}

				//imei
				if (imei1 != null && !TextUtils.isEmpty(imei1))
				{
					deviceIds.add("IMEI:" + imei1);
				}
				else
				{
					/*HQ_liugang delete for HQ01304149*/
					//deviceIds.add("IMEI:" + imei_invalid);
				}
			}
			else
			{
				//meid
				if (meid != null && !TextUtils.isEmpty(meid))
				{
					deviceIds.add("MEID:" + meid.toUpperCase());//HQ_liugang modify for HQ01346970
				}
				else
				{
					/*HQ_liugang delete for HQ01304149*/
					//deviceIds.add("MEID:" + imei_invalid);
				}

				//add by caoxuhao for HQ01574182 begin
				//show IMEI, not IMEI1 or IMEI2, if there's only one card
				boolean have1stCard = (imei1 != null && !TextUtils.isEmpty(imei1));
				boolean have2ndCard = (imei2 != null && !TextUtils.isEmpty(imei2));
				if(have1stCard && have2ndCard){
					deviceIds.add("IMEI1:" + imei1);
					deviceIds.add("IMEI2:" + imei2);
				}else if(have1stCard){
					deviceIds.add("IMEI:" + imei1);
				}else if(have2ndCard){
					deviceIds.add("IMEI:" + imei2);
				}
				//add by caoxuhao for HQ01574182 end


/*
				//imei1
				if (imei1 != null && !TextUtils.isEmpty(imei1))
				{
					deviceIds.add("IMEI1:" + imei1);
				}
				else
				{
					//HQ_liugang delete for HQ01304149
					//deviceIds.add("IMEI1:" + imei_invalid);
				}

				//imei2
				if (imei2 != null && !TextUtils.isEmpty(imei2))
				{
					deviceIds.add("IMEI2:" + imei2);
				}
				else
				{
					//HQ_liugang delete for HQ01304149
					//deviceIds.add("IMEI2:" + imei_invalid);
				}
*/
			}
			/*HQ_xionghaifeng add 20150730 for display meid and two imei end*/
			
			//add by HQ_caoxuhao at 20150915 HQ01366102 begin
			//add title
			String alrDlgTle = "IMEI_IMEI";

			// / Added by guofeiyao 2015/11/30
			// For 813 L03C481 single sim
			if ( !telephonyManager.isMultiSimEnabled() ) {
                 alrDlgTle = "IMEI";
			}
			// / End
			
            AlertDialog alert = new AlertDialog.Builder(context)
            		.setTitle(alrDlgTle)
                    .setItems(deviceIds.toArray(new String[deviceIds.size()]), null)
                    .setPositiveButton(android.R.string.ok, null)
                    .setCancelable(false)
                    .show();
            //add by HQ_caoxuhao at 20150915 HQ01366102 end
            return true;
        }
        return false;
    }

    private static boolean handleRegulatoryInfoDisplay(Context context, String input) {
        if (input.equals(MMI_REGULATORY_INFO_DISPLAY)) {
            Log.d(TAG, "handleRegulatoryInfoDisplay() sending intent to settings app");
            Intent showRegInfoIntent = new Intent(Settings.ACTION_SHOW_REGULATORY_INFO);
            try {
                context.startActivity(showRegInfoIntent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "startActivity() failed: " + e);
            }
            return true;
        }
        return false;
    }

    /**
     * Get the Id of each slot
     * @return int array of slot Ids
     */
    private static int[] getSlotIds() {
        int slotCount = TelephonyManager.getDefault().getPhoneCount();
        int[] slotIds = new int[slotCount];
        for (int i = 0; i < slotCount; i++) {
            slotIds[i] = i;
        }
        return slotIds;
    }

	
	//add by majian for ####7599#
	private static boolean handlePSNInfo(Context context, String input)
	{
		String specialCode = "*#*#1357946#*#*";
		String psninfo = new String();		
		String productName = SystemProperties.get("ro.product.name", "");
		
		String imei1 = SystemProperties.get("gsm.imei1", null);
					
		if( specialCode.equals(input) )
		{
			psninfo = FSJ.getFSJCode(productName + imei1);
			AlertDialog.Builder builder = new AlertDialog.Builder(context);
    		builder.setTitle("Product Number");
    		builder.setMessage(psninfo);
        	builder.setPositiveButton("OK",
    		new android.content.DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface arg0, int arg1) {
    				arg0.dismiss();
    			}
    		}).show(); 
			return true;
			
        }
		return false;
	}
	
	
	//add by majian for ####7599#
	private static boolean handleHarawareInfo(Context context, String input)
	{
        String specialCode = "*937*761#";
        String path = "/sys/bus/platform/devices/HardwareInfo/hq_hardwareinfo";
        String info = new String();

		/*liugang add for HQ01009961 20150316*/
		String modules[] = {"00_lcm", "01_ctp", "02_main_camera", "03_sub_camera", "09_wifi", "10_bt", "11_gps", "12_fm"};
		/*end add*/

        if( specialCode.equals(input) ){

			//for(String module : modules){//liugang add for HQ01009961 20150316
	            File file = new File(path);
	            
	            if(!file.exists()){
	                return false;
	            }

	            try {
	                FileReader fr = new FileReader(file);
	                BufferedReader br = new BufferedReader(fr);
	                String s;
	            
	                while((s = br.readLine()) != null){
	                    Log.i("HarawareInfo", "info " + info);
	                    info += s + "\n";
	                }
	            } catch (FileNotFoundException e){
	                e.printStackTrace();
	            } catch (IOException e){
	                e.printStackTrace();
	            //}
				}

        	AlertDialog.Builder builder = new AlertDialog.Builder(context);
        	builder.setTitle("Hardware info");
        	builder.setMessage(info.trim());
            builder.setPositiveButton("OK",
        		new android.content.DialogInterface.OnClickListener() {
        			public void onClick(DialogInterface arg0, int arg1) {
        				arg0.dismiss();
        			}
        	}).show();       
			return true;     
        }
        return false;
    }
	//add end

    /*liugang add for factory test*/
    static boolean handleFactoryTestCode(Context context, String input) {
                String specialCode ="*937*0#";
		String specialCode1 ="*937*1#";
		String specialCode2 ="*937*2#";
		String specialCode3 ="*937*3#";
		String specialCode4 ="*937*4#";
		String specialCode5 ="*937*5#";
		String specialCode6 ="*937*6#";
		
        if (input.equals(specialCode)) {
            Intent intent = new Intent();
            intent.setClassName("com.android.factory", "com.android.factory.ControlCenterActivity");
            intent.putExtra("red_safe","factory_kit_test");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                // TODO Auto-generated catch block
                Log.i(TAG, "===ActivityNotFoundException===");
                return false;
            }
            return true;
        }else if (input.equals(specialCode1)) {
                  Intent intent = new Intent();
                  intent.setAction("com.set_fkt_flag_fulltest");
                  context.sendBroadcast(intent);
		 return true;
        	}else if (input.equals(specialCode2)) {
                  Intent intent = new Intent();
                  intent.setAction("com.set_fkt_flag_pcbatest");
                  context.sendBroadcast(intent);
		 return true;
        	}else if (input.equals(specialCode3)) {
                  Intent intent = new Intent();
                  intent.setAction("com.set_fkt_flag_mm2");
                  context.sendBroadcast(intent);
		 return true;
        	}else if (input.equals(specialCode4)) {
                  Intent intent = new Intent();
                  intent.setAction("com.set_fkt_flag_fulltest_clear");
                  context.sendBroadcast(intent);
		 return true;
        	}else if (input.equals(specialCode5)) {
                  Intent intent = new Intent();
                  intent.setAction("com.set_fkt_flag_pcba_clear");
                  context.sendBroadcast(intent);
		 return true;
        	}else if (input.equals(specialCode6)) {
                   Intent intent = new Intent();
                  intent.setAction("android.intent.action.factorytestinfo");
                  context.sendBroadcast(intent);
		 return true;
        	}
        return false;
    }    
    /*end add*/

    /*liugang add for factory test*/
    static boolean handleFactory2TestCode(Context context, String input) {
        String specialCode ="####1111#";
        if (input.equals(specialCode)) {
            Intent intent = new Intent();
            intent.setClassName("com.android.factory_mmi2", "com.android.factory_mmi2.ControlCenterActivity");
            intent.putExtra("red_safe","factory_kit_test");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                // TODO Auto-generated catch block
                Log.i(TAG, "===ActivityNotFoundException===");
                return false;
            }
            return true;
        }
        return false;
    }    
    /*end add*/

//add by yanzewen for add engineering mode code begin
    static boolean handleHuaweiEngineeringModeCode(Context context, String input){
	
        if (input.equals("*#*#2846579#*#*")) {
            Intent intent = new Intent();
            intent.setClassName("com.enginner.engineering", "com.enginner.engineering.MainActivity");
            /// H: [zhaoguotao] 20140718, HQ00726037, add for RIntentFuzzer.apk test @{
            intent.putExtra("red_safe", -1);
              /// @}
        Log.i(TAG, "handleHuaweiEngineeringModeCode");
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                // TODO Auto-generated catch block
                Log.i(TAG, "===ActivityNotFoundException===");
                return false;
            }
            return true;
        }
	    return false;	
    }
//add by yanzewen for add engineering mode code end

	//add by yanzewen for add engineering mode code begin
		static boolean handleHuaweiMMIModeCode(Context context, String input){
		
			if (input.equals("*#2846#")) {
				Intent intent = new Intent();
				intent.setClassName("com.enginner.engineering", "com.enginner.engineering.EngineeringmodeActivity");
				/// H: [zhaoguotao] 20140718, HQ00726037, add for RIntentFuzzer.apk test @{
				intent.putExtra("red_safe", -1);
				  /// @}
			Log.i(TAG, "handleHuaweiEngineeringModeCode");
				try {
					context.startActivity(intent);
				} catch (ActivityNotFoundException e) {
					// TODO Auto-generated catch block
					Log.i(TAG, "===ActivityNotFoundException===");
					return false;
				}
				return true;
			}
			return false;	
		}
	//add by yanzewen for add engineering mode code end

    //add by zhaizhanfeng for SAR at 151027 start
   	static boolean handleSARCode(Context context, String input){
		String specialCode = "*#07#";
		if (input.equals(specialCode)) {
			Log.d("zhaizhanfeng","specialCode come in");
			Intent intent = new Intent();
			intent.setClassName("com.android.contacts","com.android.contacts.SpecialSarActivity");
		try {
				Log.d("zhaizhanfeng","Activity come in");
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.i(TAG, "===ActivityNotFoundException===");
				Log.d("zhaizhanfeng","ActivityNotFoundException");
                return false;
            }
		return true;
		}
		return false;
	}
	//add by zhaizhanfeng for SAR at 151027 end


    /*liugang add for runtime test*/
    static boolean handleRuntimeTestCode(Context context, String input){
        String specialCode = "*#6688#";
        //String specialCode_spkrev = "####9327#"; //HQ_yujianfeng,add for 9327 special code,2014.10.27
		
        /*HQ_liugang add for HQ00910881 20141217*/
        String specialCode2 = "####78646#";
	String runin_flag_success =  "*#66881#";
	String runin_flag_failed =  "*#66880#";
        /*HQ_liugang add end*/		

        Log.i(TAG, "handleRuntimeTestCode");
        
        if (input.equals(specialCode)) { //HQ_liugang modify for HQ00910881 20141217
            //Intent intent = new Intent();
            //intent.setAction("android.intent.action.runtimetest");
            //context.sendBroadcast(intent);
            Intent runtimeintent = new Intent("com.huaqin.runtime.start");
            runtimeintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            runtimeintent.putExtra("red_safe","factory_kit_test");
            try {
                context.startActivity(runtimeintent);
            } catch (ActivityNotFoundException e) {
                // TODO Auto-generated catch block
                Log.i(TAG, "===ActivityNotFoundException===");
                return false;
            }

            return true;
        }
        /*else if(input.equals(specialCode_spkrev)){//HQ_yujianfeng,add for 9327 special code.2014.10.27
            Intent runtimeintent = new Intent("com.huaqin.runtime.spkrev.start");
            runtimeintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(runtimeintent);

            return true;       
        }//added ends here.*/
        else if(input.equals(runin_flag_success)) {
	               Intent intent = new Intent();
	            intent.setAction("android.intent.set_runin_flag_success");
	            context.sendBroadcast(intent);
		return true;   
        	} else if(input.equals(runin_flag_failed)) {
                        Intent intent = new Intent();
	            intent.setAction("android.intent.set_runin_flag_failed");
	            context.sendBroadcast(intent);
		return true;   		
        	}
        
        return false;
    }    
    /*end add*/

	
	static boolean handleRecoverySysCode(Context context, String input) {
		String specialCode = "####7777#";
		final Context mContext = context;
		if (input.equals(specialCode)) {
			AlertDialog.Builder builder = new AlertDialog.Builder(context);
        	builder.setTitle(R.string.hq_recovery);
        	builder.setMessage(R.string.hq_recovery_msg);

            builder.setPositiveButton(R.string.hq_recovery_ok,
                
                		new android.content.DialogInterface.OnClickListener() {
                			public void onClick(DialogInterface arg0, int arg1) {
                                   Log.i("SpecialCharSequenceMgrProxy", "arg1 " + arg1);
                                if(SystemProperties.get("ro.crypto.state","unencrypted").equals("unencrypted"))
                                    mContext.sendBroadcast(new Intent("android.intent.action.MASTER_CLEAR"));                                   
                			}
        	        } );


             builder.setNegativeButton(R.string.hq_recovery_no,
                		new android.content.DialogInterface.OnClickListener() {
                			public void onClick(DialogInterface arg0, int arg1) {
                                Log.i("SpecialCharSequenceMgrProxy", "arg2 " + arg1);
                			}
        	        });

             AlertDialog dialog = builder.create();
             dialog.show();
			 
			 return true;
		}
		
		return false;
    }

	/*HQ_liugang add for */
	static boolean handleSnCode(Context context, String input){
		String specialCode = "*937*764#";
		final Context mContext = context;
		if (input.equals(specialCode)) {
			String snstr = SystemProperties.get("gsm.serial");
			Log.d("nnnnnnnnnn", "snstr = " + snstr);
			try {
				//HQ_caoxuhao 20151012 add for HQ01421014 start
				//String psn = null;
				String csn = null;
				String sn[] = null;
				if (snstr.length() >= 16) {
				//	psn = "PN:" + snstr.substring(0, 23);
					csn = "SN:" + snstr.substring(0, 16);
				//	sn = new String[] { psn, csn };
					sn = new String[] {csn};
				//} else if (snstr.length() < 25 && snstr.length() >= 23) {
				//	psn = "PN:" + snstr.substring(0, 23);
				//	sn = new String[] { psn };
				}else {
        				sn = new String[] {Build.SERIAL};
        			}
				//HQ_caoxuhao 20151012 add for HQ01421014 end

				AlertDialog.Builder builder = new AlertDialog.Builder(context);

				builder.setTitle("SN").setItems(sn, null)
						.setNegativeButton("OK", null).show();

			} catch (Exception e) {
				Log.d("nnnnnnnnnn", "exception");
			}			
			return true;
		}
		return false;
	}

	static boolean handleLoggerCode(Context context, String input){
		String specialCode = "####7878#";
		final Context mContext = context;
		if (input.equals(specialCode)) {
			Intent mIntent = new Intent();
			mIntent.setClassName("com.mediatek.mtklogger",
					"com.mediatek.mtklogger.MainActivity");
			mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			mContext.startActivity(mIntent);
			return  true;
		}
		return false;
	}

	static boolean handleHWInternalVersionCode(Context context, String input){
		String specialCode = "*#267#";
		final Context mContext = context;
		if (input.equals(specialCode)) {
			String hwVersion = SystemProperties.get("ro.huawei.internal.version");

			String hwDate = SystemProperties.get("ro.build.date");
			
			AlertDialog.Builder builder = new AlertDialog.Builder(context);

			builder.setTitle("HWInternalVersion").setItems(new String[]{"Version:" + hwVersion + "\n" + "Time:" + hwDate}, null)
					.setNegativeButton("OK", null).show();				
			return true;
		}
		return false;
	}

	static boolean handleHQInternalVersionCode(Context context, String input){
		String specialCode = "*937*772#";
		final Context mContext = context;
		if (input.equals(specialCode)) {
			String hqVersion = SystemProperties.get("ro.huaqin.internal.version");
			String hqDate = SystemProperties.get("ro.build.date");
			
			AlertDialog.Builder builder = new AlertDialog.Builder(context);

			builder.setTitle("HQInternalVersion").setItems(new String[]{"Version:" + hqVersion + "\n" + "Time:" + hqDate}, null)
					.setNegativeButton("OK", null).show();			
			return true;
		}
		return false;
	}	
    
    
    /*******
     * This code is used to handle SIM Contact queries
     *******/
    private static final String ADN_PHONE_NUMBER_COLUMN_NAME = "number";
    private static final String ADN_NAME_COLUMN_NAME = "name";
    private static final String ADN_ADDITIONAL_PHONE_NUMBER_COLUMN_NAME = "additionalNumber";

    /// M: ALPS01764940, Add index to indicate the queried contacts @{
    private static final String ADN_ID_COLUMN_NAME = "index";
    /// @}

    private static final int ADN_QUERY_TOKEN = -1;

    /**
     * Cookie object that contains everything we need to communicate to the
     * handler's onQuery Complete, as well as what we need in order to cancel
     * the query (if requested).
     *
     * Note, access to the textField field is going to be synchronized, because
     * the user can request a cancel at any time through the UI.
     */
    private static class SimContactQueryCookie implements DialogInterface.OnCancelListener{
        public ProgressDialog progressDialog;
        public int contactIndex;

        // Used to identify the query request.
        private int mToken;
        private QueryHandler mHandler;

        // The text field we're going to update
        private EditText textField;

        public SimContactQueryCookie(int index, QueryHandler handler, int token) {
            contactIndex = index;
            mHandler = handler;
            mToken = token;
        }

        /**
         * Synchronized getter for the EditText.
         */
        public synchronized EditText getTextField() {
            return textField;
        }

        /**
         * Synchronized setter for the EditText.
         */
        public synchronized void setTextField(EditText text) {
            textField = text;
        }

        /**
         * Cancel the ADN query by stopping the operation and signaling
         * the cookie that a cancel request is made.
         */
        public synchronized void onCancel(DialogInterface dialog) {
            /** M: Fix CR ALPS01863413. Call QueryHandler.cancel(). @{ */
            /* original code:
            // close the progress dialog
            if (progressDialog != null) {
                progressDialog.dismiss();
            }

            // setting the textfield to null ensures that the UI does NOT get
            // updated.
            textField = null;

            // Cancel the operation if possible.
            mHandler.cancelOperation(mToken);
            */
            mHandler.cancel();
            /** @} */
        }
    }

    /**
     * Asynchronous query handler that services requests to look up ADNs
     *
     * Queries originate from {@link #handleAdnEntry}.
     */
    private static class QueryHandler extends NoNullCursorAsyncQueryHandler {

        private boolean mCanceled;

        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        /**
         * Override basic onQueryComplete to fill in the textfield when
         * we're handed the ADN cursor.
         */
        @Override
        protected void onNotNullableQueryComplete(int token, Object cookie, Cursor c) {
            try {
                sPreviousAdnQueryHandler = null;
                /// M: Fix CR ALPS01863413. Clear the ADN query cookie.
                sSimContactQueryCookie = null;

                if (mCanceled) {
                    return;
                }

                SimContactQueryCookie sc = (SimContactQueryCookie) cookie;

                // close the progress dialog.
                sc.progressDialog.dismiss();

                // get the EditText to update or see if the request was cancelled.
                EditText text = sc.getTextField();

                // if the textview is valid, and the cursor is valid and postionable
                // on the Nth number, then we update the text field and display a
                // toast indicating the caller name.

                String name = null;
                String number = null;
                String additionalNumber = null;

                if ((c != null) && (text != null)) {

                    while (c.moveToNext()) {
                        if (c.getInt(c.getColumnIndexOrThrow(ADN_ID_COLUMN_NAME)) == sc.contactIndex) {
                            name = c.getString(c.getColumnIndexOrThrow(ADN_NAME_COLUMN_NAME));
                            number = c.getString(c.getColumnIndexOrThrow(ADN_PHONE_NUMBER_COLUMN_NAME));
                            additionalNumber = c.getString(c.getColumnIndexOrThrow(ADN_ADDITIONAL_PHONE_NUMBER_COLUMN_NAME));
                            break;
                        }
                    }

                    // fill the text in.
                    if (!TextUtils.isEmpty(number)) {
                        text.getText().replace(0, 0, number);
                    } else if (!TextUtils.isEmpty(additionalNumber)) {
                        text.getText().replace(0, 0, additionalNumber);
                    }

                    // display the name as a toast
                    if (name != null) {
                        Context context = sc.progressDialog.getContext();
                        name = context.getString(R.string.menu_callNumber, name);
                        Toast.makeText(context, name, Toast.LENGTH_SHORT)
                            .show();
                    }

                }
            } finally {
                MoreCloseables.closeQuietly(c);
            }
        }

        public void cancel() {
            mCanceled = true;
            // Ask AsyncQueryHandler to cancel the whole request. This will fails when the
            // query already started.
            cancelOperation(ADN_QUERY_TOKEN);
            /// M: Fix CR ALPS01863413. Dismiss the progress and clear the ADN query cookie.
            if (sSimContactQueryCookie != null
                    && sSimContactQueryCookie.progressDialog != null) {
                sSimContactQueryCookie.progressDialog.dismiss();
                sSimContactQueryCookie = null;
            }
        }
    }

    /**
     * Query Adn from the specific subscription
     * @param handler
     * @param cookie
     * @param uri
     */
    private static void queryAdn(QueryHandler handler, SimContactQueryCookie cookie, Uri uri) {
        if (handler == null || cookie == null || uri == null) {
            Log.w(TAG, "queryAdn parameters incorrect");
            return;
        }

        // display the progress dialog
        cookie.progressDialog.show();

        if (DEBUG) {
            Log.d(TAG, "AdnQuery onSubPick, uri=" + uri);
        }
        handler.startQuery(ADN_QUERY_TOKEN, cookie, uri,
                    new String[] {ADN_PHONE_NUMBER_COLUMN_NAME, ADN_ID_COLUMN_NAME, ADN_ADDITIONAL_PHONE_NUMBER_COLUMN_NAME}, null, null, null);
        if (sPreviousAdnQueryHandler != null) {
            // It is harmless to call cancel() even after the handler's gone.
            sPreviousAdnQueryHandler.cancel();
        }
        sPreviousAdnQueryHandler = handler;
    }

    /**
     * Handle PinMmi by the specific subscription
     * @param subId The id of the subscription
     * @param input The PinMmi text
     */
    private static boolean handlePinMmi(int subId, String input) {
        try {
            boolean result = ITelephony.Stub.asInterface(ServiceManager.getService("phone"))
                    .handlePinMmiForSubscriber(subId, input);
            if (DEBUG) {
                Log.d(TAG, "Pin onSubPick(" + subId + ", " + input + ")=" + result);
            }
            return result;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to handlePinMmi due to remote exception");
            return false;
        }
    }

    private static boolean isValidSubId(long subId) {
        return subId > SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    ///--------------------------Mediatek----------------------
    /** M: Fix CR ALPS01863413. Make the progress dismiss after the ADN query be cancelled.
     *  And make it support screen rotation while phone account pick dialog shown. @{ */
    private static SimContactQueryCookie sSimContactQueryCookie;

    /**
     * For ADN query with multiple phone accounts. If the the phone account pick
     * dialog shown, then rotate the screen and select one account to query ADN.
     * The ADN result would write into the old text view because the views
     * re-created but the class did not known. So, the dialpad fragment should
     * call this method to update the digits text filed view after it be
     * re-created.
     *
     * @param textFiled
     *            the digits text filed view
     */
    public static void updateTextFieldView(EditText textFiled) {
        if (sSimContactQueryCookie != null) {
            sSimContactQueryCookie.setTextField(textFiled);
        }
    }
    /** @} */

    public static boolean handleVersionCode(Context context, String input) {
        if (input.equals(HQ_VERSION_DISPLAY)) {
			  String buildVersion = SystemProperties.get("ro.build.version","unkonwn");
        Log.d(TAG, "buildVersion: " + buildVersion);
        	String kernelVersionOrg = getFormattedKernelVersion();
        	String kernelVersion = "";
        	if (kernelVersionOrg.length() >= 7) {
        		kernelVersion = kernelVersionOrg.substring(0, 7);
			}else {
				kernelVersion = kernelVersionOrg;
			}
            String strInfo = src2string(context, R.string.version_display_SW_version) + buildVersion + "\n"
                +src2string(context, R.string.version_display_android_version)+Build.VERSION.RELEASE+"\n"
                +src2string(context, R.string.version_display_baseband_version)+"MT6753"+"\n"
                +src2string(context, R.string.version_display_model_number)+Build.MODEL+"\n"
                +src2string(context, R.string.version_display_kernel_version)+kernelVersion+"\n"
                +src2string(context, R.string.version_display_build_number)+Build.DISPLAY+"\n"
		+src2string(context, R.string.version_display_HW_version)+ "Ver.A" +"\n"
                //+"HQ Internal version:\n"+Build.VERSION.HQ_INTERNAL_VERSION + "\n"  //add by wumin for huaqin internal version , 20140916
                ;
            try {
                AlertDialog alert = new AlertDialog.Builder(context).setTitle(src2string(context, R.string.version_display_title))
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .setMessage(strInfo)
                        .setNegativeButton("cancel", null).show();
            } catch (Exception e) {
            }
            return true;
        }

        return false;

    }

    private static String src2string(Context context, int src){
	return context.getResources().getString(src);
    }

	
    /// H: add huawei special code {@
    private static String getFormattedKernelVersion() {
        String procVersionStr;

        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/version"), 256);
            try {
                procVersionStr = reader.readLine();
            } finally {
                reader.close();
            }

            final String PROC_VERSION_REGEX =
                    "\\w+\\s+" + /* ignore: Linux */
                    "\\w+\\s+" + /* ignore: version */
                    "([^\\s]+)\\s+" + /* group 1: 2.6.22-omap1 */
                    "\\(([^\\s@]+(?:@[^\\s.]+)?)[^)]*\\)\\s+" + /* group 2: (xxxxxx@xxxxx.constant) */
                    "\\((?:[^(]*\\([^)]*\\))?[^)]*\\)\\s+" + /* ignore: (gcc ..) */
                    "([^\\s]+)\\s+" + /* group 3: #26 */
                    "(?:PREEMPT\\s+)?" + /* ignore: PREEMPT (optional) */
                    "(.+)"; /* group 4: date */

            Pattern p = Pattern.compile(PROC_VERSION_REGEX);
            Matcher m = p.matcher(procVersionStr);

            if (!m.matches()) {
                Log.e(TAG, "Regex did not match on /proc/version: " + procVersionStr);
                return "Unavailable";
            } else if (m.groupCount() < 4) {
                Log.e(TAG, "Regex match on /proc/version only returned " + m.groupCount()
                        + " groups");
                return "Unavailable";
            } else {
                ///H: [qinzhonghua] Add build information to version number {@
                return m.group(1) + "\n" + m.group(2) + " " + m.group(3) + "\n" + m.group(4);
                //return (new StringBuilder(m.group(1))).toString();
                ///@}
            }
        } catch (IOException e) {
            Log.e(TAG, "IO Exception when getting kernel version for Device Info screen", e);
            return "Unavailable";
        }
    }

    /// M: for OP09 6M project @{
    /**
     * handle version chars from user input on dial pad.
     *
     * @param context.
     * @param input from user input in dial pad.
     * @return boolean, check if the input string is handled.
     */
    private static boolean mtkHandleVersionCode(Context context, String input) {
        if (!DialerFeatureOptions.isCDMA6MSupport()) {
            Log.d(TAG, "handleVersionCode CDMA 6M not support!");
            return false;
        }

        if (input.equals(PRL_VERSION_DISPLAY)) {
            int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            try {
                int[] subIdList = SubscriptionManager.from(
                            context).getActiveSubscriptionIdList();
                int length = subIdList.length;
                ITelephony iTel = ITelephony.Stub.asInterface(
                        ServiceManager.getService(Context.TELEPHONY_SERVICE));
                Log.d(TAG, "handleVersionCode getActiveSubscriptionIdList length:" + length);
                for (int i = 0; i < length; i++) {
                    int activeSubId = subIdList[i];
                    int phoneType = iTel.getActivePhoneTypeForSubscriber(activeSubId);
                    if (PhoneConstants.PHONE_TYPE_CDMA == phoneType) {
                        subId = activeSubId;
                        Log.d(TAG, "handleVersionCode subId:" + subId);
                        break;
                    }
                }
            } catch (RemoteException ex) {
                ex.printStackTrace();
            }

            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                showPRLVersionSetting(context, subId);
                return true;
            } else {
                showPRLVersionSetting(context, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                return true;
            }
        }
        return false;
    }

    /**
     * show version by cdma phone provider info.
     *
     * @param context from host app.
     * @param slot indicator which slot is cdma phone.
     * @return void.
     */
    private static void showPRLVersionSetting(Context context, int subId) {
        Intent intentCdma = new Intent(ACTION_CDMAINFO);
        intentCdma.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intentCdma.putExtra(KEY_SUB_ID, subId);
        context.startActivity(intentCdma);
    }
    /// @}

	//add by zhangjinqiang for HW_SIMLock -start
	static boolean handleSIMLockCode(Context context,String input){
		final Context mContext = context;
		if(input.equals(HQ_UNLOCK_SIMLOCK_CODE)
		&& SystemProperties.get("ro.hq.telcel.simlock").equals("1")
		&&TelephonyManagerEx.getDefault().getSimState(0)==TelephonyManager.SIM_STATE_ABSENT){
			Intent intent = new Intent("android.intent.action.noSimCard_unLock");
			mContext.sendBroadcast(intent);
			android.util.Log.d("zhangjinqiang","sendbroadcast-nosimcard_unlock");
		     return true;
		}
		return false;
	}
	//add by zhangjinqiang end
}
