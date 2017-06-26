/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (c) 2013 The Android Open Source Project
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

package com.android.ims;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.IImsEcbm;
import com.android.ims.internal.IImsEcbmListener;
import com.android.ims.internal.IImsRegistrationListener;
import com.android.ims.internal.IImsService;
import com.android.ims.internal.IImsUt;
import com.android.ims.internal.ImsCallSession;
import com.android.ims.internal.IImsConfig;
import com.android.internal.telephony.PhoneConstants;

import java.util.HashMap;

import org.gsma.joyn.JoynService;
import org.gsma.joyn.JoynServiceException;
import org.gsma.joyn.JoynServiceListener;
import org.gsma.joyn.capability.CapabilityService;
import org.gsma.joyn.chat.ChatService;
import org.gsma.joyn.contacts.ContactsService;
import org.gsma.joyn.ft.FileTransferService;
import org.gsma.joyn.gsh.GeolocSharingService;
import org.gsma.joyn.ish.ImageSharingService;
import org.gsma.joyn.vsh.VideoSharingService;


import com.mediatek.common.MPlugin;
import com.mediatek.common.ims.IImsManagerExt;

import com.mediatek.ims.WfcReasonInfo;

/**
 * Provides APIs for IMS services, such as initiating IMS calls, and provides access to
 * the operator's IMS network. This class is the starting point for any IMS actions.
 * You can acquire an instance of it with {@link #getInstance getInstance()}.</p>
 * <p>The APIs in this class allows you to:</p>
 *
 * @hide
 */
public class ImsManager {

    /*
     * Debug flag to override configuration flag
     */
    public static final String PROPERTY_DBG_VOLTE_AVAIL_OVERRIDE = "persist.dbg.volte_avail_ovr";
    public static final int PROPERTY_DBG_VOLTE_AVAIL_OVERRIDE_DEFAULT = 0;
    public static final String PROPERTY_DBG_VT_AVAIL_OVERRIDE = "persist.dbg.vt_avail_ovr";
    public static final int PROPERTY_DBG_VT_AVAIL_OVERRIDE_DEFAULT = 0;

    /**
     * For accessing the IMS related service.
     * Internal use only.
     * @hide
     */
    public static final String IMS_SERVICE = "ims";

    /**
     * The result code to be sent back with the incoming call {@link PendingIntent}.
     * @see #open(PendingIntent, ImsConnectionStateListener)
     */
    public static final int INCOMING_CALL_RESULT_CODE = 101;

    /**
     * Key to retrieve the call ID from an incoming call intent.
     * @see #open(PendingIntent, ImsConnectionStateListener)
     */
    public static final String EXTRA_CALL_ID = "android:imsCallID";

    /// M: IMS VoLTE refactoring. @{
    /**
     * Key to retrieve the sequence number from an incoming call intent.
     * @see #open(PendingIntent, ImsConnectionStateListener)
     * @hide
     */
    public static final String EXTRA_SEQ_NUM = "android:imsSeqNum";

    /*
     * Key to retrieve the sequence number from an incoming call intent.
     * @see #open(PendingIntent, ImsConnectionStateListener)
     * @hide
     */
    public static final String EXTRA_DIAL_STRING = "android:imsDialString";
    /// @}

    /**
     * Action to broadcast when ImsService is up.
     * Internal use only.
     * @hide
     */
    public static final String ACTION_IMS_SERVICE_UP =
            "com.android.ims.IMS_SERVICE_UP";

    /**
     * Action to broadcast when ImsService is down.
     * Internal use only.
     * @hide
     */
    public static final String ACTION_IMS_SERVICE_DOWN =
            "com.android.ims.IMS_SERVICE_DOWN";

    /* M WFC */
    public static final String ACTION_IMS_STATE_CHANGED =
            "com.android.ims.IMS_STATE_CHANGED";


    public static final String ACTION_IMS_CAPABILITIES_CHANGED =
            "com.android.ims.IMS_CAPABILITIES_CHANGED";

    /* M WFC */

    /**
     * Part of the ACTION_IMS_SERVICE_UP or _DOWN intents.
     * A long value; the phone ID corresponding to the IMS service coming up or down.
     * Internal use only.
     * @hide
     */
    public static final String EXTRA_PHONE_ID = "android:phone_id";

    /**
     * Action for the incoming call intent for the Phone app.
     * Internal use only.
     * @hide
     */
    public static final String ACTION_IMS_INCOMING_CALL =
            "com.android.ims.IMS_INCOMING_CALL";

    /**
     * Action for the incoming call indication intent for the Phone app.
     * Internal use only.
     * @hide
     */
    public static final String ACTION_IMS_INCOMING_CALL_INDICATION =
            "com.android.ims.IMS_INCOMING_CALL_INDICATION";

    /**
     * Part of the ACTION_IMS_INCOMING_CALL intents.
     * An integer value; service identifier obtained from {@link ImsManager#open}.
     * Internal use only.
     * @hide
     */
    public static final String EXTRA_SERVICE_ID = "android:imsServiceId";

    /**
     * Part of the ACTION_IMS_INCOMING_CALL intents.
     * An boolean value; Flag to indicate that the incoming call is a normal call or call for USSD.
     * The value "true" indicates that the incoming call is for USSD.
     * Internal use only.
     * @hide
     */
    public static final String EXTRA_USSD = "android:ussd";

    /* M WFC */
    public static final String EXTRA_IMS_REG_STATE_KEY = "android:regState";
    public static final String EXTRA_IMS_ENABLE_CAP_KEY = "android:enablecap";
    public static final String EXTRA_IMS_DISABLE_CAP_KEY = "android:disablecap";
    public static final String EXTRA_IMS_REG_ERROR_KEY = "android:regError";

    /* M WFC */

    private static final String TAG = "ImsManager";
    private static final boolean DBG = true;

    private static HashMap<Integer, ImsManager> sImsManagerInstances =
            new HashMap<Integer, ImsManager>();

    private Context mContext;
    private int mPhoneId;
    private IImsService mImsService = null;
       
    // RCS Terminal API Services     
    private CapabilityService mCapabilitiesApi;
    private ChatService mChatApi;
    private ContactsService mContactsApi;
    private FileTransferService mFileTransferApi;
    private GeolocSharingService mGeolocSharingApi;
    private VideoSharingService mVideoSharingApi;
    private ImageSharingService mImageSharingApi;
    
    private ImsServiceDeathRecipient mDeathRecipient = new ImsServiceDeathRecipient();
    // Ut interface for the supplementary service configuration
    private ImsUt mUt = null;
    // Interface to get/set ims config items
    private ImsConfig mConfig = null;

    // ECBM interface
    private ImsEcbm mEcbm = null;

    /**
     * Gets a manager instance.
     *
     * @param context application context for creating the manager object
     * @param phoneId the phone ID for the IMS Service
     * @return the manager instance corresponding to the phoneId
     */
    public static ImsManager getInstance(Context context, int phoneId) {
        synchronized (sImsManagerInstances) {
            if (sImsManagerInstances.containsKey(phoneId))
                return sImsManagerInstances.get(phoneId);

            ImsManager mgr = new ImsManager(context, phoneId);
            sImsManagerInstances.put(phoneId, mgr);

            return mgr;
        }
    }

    /**
      * An API to query carrier support Ims function or not according to its mcc/mnc on sim card.
      * @param context The context for retrive plug-in.
      * @return return support ims or not.
      * @hide
      */
    public static boolean isImsSupportByCarrier(Context context) {
        boolean isSupport = ("1".equals(SystemProperties.get("ro.mtk_ims_support"))) ? true : false;
        if (context == null) {
            Rlog.w(TAG, "Invalid context, return " + isSupport);
            return isSupport;
        }
        IImsManagerExt imsManagerPlugin =
                MPlugin.createInstance(IImsManagerExt.class.getName(), context);
        if (imsManagerPlugin == null) {
            Rlog.w(TAG, "Invalid imsManagerPlugin, return " + isSupport);
        } else {
            return imsManagerPlugin.isImsSupportBySim();
        }
        return isSupport;
    }

    /**
     * Returns the user configuration of Enhanced 4G LTE Mode setting
     */
    public static boolean isEnhanced4gLteModeSettingEnabledByUser(Context context) {
        int enabled = android.provider.Settings.Global.getInt(
                    context.getContentResolver(),
                    android.provider.Settings.Global.ENHANCED_4G_MODE_ENABLED, ImsConfig.FeatureValueConstants.ON);
        return (enabled == 1)? true:false;
    }

    /**
     * Change persistent Enhanced 4G LTE Mode setting
     */
    public static void setEnhanced4gLteModeSetting(Context context, boolean enabled) {
        int value = enabled ? 1 : 0;
        android.provider.Settings.Global.putInt(
                context.getContentResolver(),
                android.provider.Settings.Global.ENHANCED_4G_MODE_ENABLED, value);

        if (isNonTtyOrTtyOnVolteEnabled(context)) {
            ImsManager imsManager = ImsManager.getInstance(context,
                    SubscriptionManager.getDefaultVoicePhoneId());
            if (imsManager != null) {
                try {
                    imsManager.setAdvanced4GMode(enabled);
                } catch (ImsException ie) {
                    // do nothing
                }
            }
        }
    }


    /**
      * Returns if WFC is supported on platform
      */
     public static boolean isWfcEnabledByPlatform(Context context) {

        if (SystemProperties.get("ro.mtk_wfc_support").equals("1")) {
            return true;
        } else {
            return false;
        }
     }
    /**
      * Returns if WFC is enabled by the user
      */
     public static boolean isWfcEnabledByUser(Context context) {

        int enabled = Settings.System.getInt(context.getContentResolver(),
                Settings.System.WHEN_TO_MAKE_WIFI_CALLS,
                TelephonyManager.WifiCallingChoices.ALWAYS_USE);
        Rlog.d(TAG, "isWfcEnabledByUser" + enabled);
        return (enabled == TelephonyManager.WifiCallingChoices.NEVER_USE) ? false : true;
     }

    public static void setWfcSettings(boolean turnOn, Context context) {

        Settings.System.putInt(context.getContentResolver(),
                Settings.System.WHEN_TO_MAKE_WIFI_CALLS,
                (turnOn ? TelephonyManager.WifiCallingChoices.ALWAYS_USE :
                TelephonyManager.WifiCallingChoices.NEVER_USE));

        ImsManager imsManager = ImsManager.getInstance(context,
                    SubscriptionManager.getDefaultVoicePhoneId());
        if( imsManager != null) {
            try {
                ImsConfig config = imsManager.getConfigInterface();
                if (config != null) {
                    config.setFeatureValue(ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI,
                            TelephonyManager.NETWORK_TYPE_IWLAN,
                            turnOn ? ImsConfig.FeatureValueConstants.ON
                                    : ImsConfig.FeatureValueConstants.OFF, null);
                }
            } catch (ImsException ie) {
                // do nothing
            }
        }

    }

    /**
     * Indicates whether the call is non-TTY or if TTY - whether TTY on VoLTE is
     * supported.
     */
    public static boolean isNonTtyOrTtyOnVolteEnabled(Context context) {
        if (context.getResources().getBoolean(
                com.android.internal.R.bool.config_carrier_volte_tty_supported)) {
            return true;
        }

        return Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.PREFERRED_TTY_MODE, TelecomManager.TTY_MODE_OFF)
                == TelecomManager.TTY_MODE_OFF;
    }

    /**
     * Returns a platform configuration for VoLTE which may override the user setting.
     */
    public static boolean isVolteEnabledByPlatform(Context context) {
        if (SystemProperties.getInt(PROPERTY_DBG_VOLTE_AVAIL_OVERRIDE,
                PROPERTY_DBG_VOLTE_AVAIL_OVERRIDE_DEFAULT) == 1) {
            return true;
        }

        boolean disabledByGlobalSetting = android.provider.Settings.Global.getInt(
                context.getContentResolver(),
                android.provider.Settings.Global.VOLTE_FEATURE_DISABLED, 0) == 1;

        return context.getResources().getBoolean(
                com.android.internal.R.bool.config_device_volte_available) && context.getResources()
                .getBoolean(com.android.internal.R.bool.config_carrier_volte_available)
                && !disabledByGlobalSetting;
    }

    /*
     * Indicates whether VoLTE is provisioned on device
     */
    public static boolean isVolteProvisionedOnDevice(Context context) {
        boolean isProvisioned = true;
        if (context.getResources().getBoolean(
                        com.android.internal.R.bool.config_carrier_volte_provisioned)) {
            isProvisioned = false; // disable on any error
            ImsManager mgr = ImsManager.getInstance(context,
                    SubscriptionManager.getDefaultVoiceSubId());
            if (mgr != null) {
                try {
                    ImsConfig config = mgr.getConfigInterface();
                    if (config != null) {
                        isProvisioned = config.getVolteProvisioned();
                    }
                } catch (ImsException ie) {
                    // do nothing
                }
            }
        }

        return isProvisioned;
    }

    /**
     * Returns a platform configuration for VT which may override the user setting.
     *
     * Note: VT presumes that VoLTE is enabled (these are configuration settings
     * which must be done correctly).
     */
    public static boolean isVtEnabledByPlatform(Context context) {
        if (SystemProperties.getInt(PROPERTY_DBG_VT_AVAIL_OVERRIDE,
                PROPERTY_DBG_VT_AVAIL_OVERRIDE_DEFAULT) == 1) {
            return true;
        }

        return
                context.getResources().getBoolean(
                        com.android.internal.R.bool.config_device_vt_available) &&
                context.getResources().getBoolean(
                        com.android.internal.R.bool.config_carrier_vt_available);
    }

    private ImsManager(Context context, int phoneId) {
        mContext = context;
        mPhoneId = phoneId;
        createImsService(true);
        createTerminalApiServices(); 
    }
    
    private void createTerminalApiServices()
    {
        Log.d(TAG, "createTerminalApiServices entry");
        mCapabilitiesApi = new CapabilityService(mContext,
                new MyServiceListener());
        mCapabilitiesApi.connect(); 
        mChatApi = new ChatService(mContext,
                new MyServiceListener());
        mChatApi.connect();
        mContactsApi = new ContactsService(mContext,
                new MyServiceListener());
        mContactsApi.connect();
        mFileTransferApi = new FileTransferService(mContext,
                new MyServiceListener()); 
        mFileTransferApi.connect();
        mGeolocSharingApi = new GeolocSharingService(mContext,
                new MyServiceListener());
        mGeolocSharingApi.connect();      
        mImageSharingApi = new ImageSharingService(mContext,
                new MyServiceListener());       
        mImageSharingApi.connect();
        mVideoSharingApi = new VideoSharingService(mContext,
                new MyServiceListener());
        mVideoSharingApi.connect();
    }
    
    /**
     * Returns a Capabilities Service Terminal API to client.
     */
    public CapabilityService getCapabilitiesService() {
        return mCapabilitiesApi;
    }
    
    /**
     * Returns a Chat Service Terminal API to client.
     */
    public ChatService getChatService() {
        return mChatApi;
    }
    
    /**
     * Returns a File Sharing Terminal API to client.
     */
    public FileTransferService getFileTransferService() {
        return mFileTransferApi;
    }
    
    /**
     * Returns a Contacts Service Terminal API to client.
     */
    public ContactsService getContactsService() {
        return mContactsApi;
    }
    
    /**
     * Returns a Geoloc Sharing Terminal API to client.
     */
    public GeolocSharingService getGeolocSharingService() {
        return mGeolocSharingApi;
    }

    /**
     * Returns a Image Sharing Terminal API to client.
     */
    public ImageSharingService getImageSharingService() {
        return mImageSharingApi;
    }
    
    /**
     * Returns a Video Sharing Terminal API to client.
     */
    public VideoSharingService getVideoSharingService() {
        return mVideoSharingApi;
    }

    /**
     * MyServiceListener listen to connect/disconnect service events.
     */
    public class MyServiceListener implements JoynServiceListener {

        /**
         * On service connected.
         */
        @Override
        public void onServiceConnected() {
            Log.d(TAG, "onServiceConnected entry ");
        }

        /**
         * On service disconnected.
         *
         * @param error the error
         */
        @Override
        public void onServiceDisconnected(int error) {           
            Log.d(TAG, "onServiceDisconnected entry " + error);
        }

    }

    /**
     * Opens the IMS service for making calls and/or receiving generic IMS calls.
     * The caller may make subsquent calls through {@link #makeCall}.
     * The IMS service will register the device to the operator's network with the credentials
     * (from ISIM) periodically in order to receive calls from the operator's network.
     * When the IMS service receives a new call, it will send out an intent with
     * the provided action string.
     * The intent contains a call ID extra {@link getCallId} and it can be used to take a call.
     *
     * @param serviceClass a service class specified in {@link ImsServiceClass}
     *      For VoLTE service, it MUST be a {@link ImsServiceClass#MMTEL}.
     * @param incomingCallPendingIntent When an incoming call is received,
     *        the IMS service will call {@link PendingIntent#send(Context, int, Intent)} to
     *        send back the intent to the caller with {@link #INCOMING_CALL_RESULT_CODE}
     *        as the result code and the intent to fill in the call ID; It cannot be null
     * @param listener To listen to IMS registration events; It cannot be null
     * @return identifier (greater than 0) for the specified service
     * @throws NullPointerException if {@code incomingCallPendingIntent}
     *      or {@code listener} is null
     * @throws ImsException if calling the IMS service results in an error
     * @see #getCallId
     * @see #getServiceId
     */
    public int open(int serviceClass, PendingIntent incomingCallPendingIntent,
            ImsConnectionStateListener listener) throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        /// M: add for debug @{
        if (DBG) log("ImsManager: open");
        /// @}

        if (incomingCallPendingIntent == null) {
            throw new NullPointerException("incomingCallPendingIntent can't be null");
        }

        if (listener == null) {
            throw new NullPointerException("listener can't be null");
        }

        int result = 0;

        try {
            result = mImsService.open(mPhoneId, serviceClass, incomingCallPendingIntent,
                    createRegistrationListenerProxy(serviceClass, listener));
        } catch (RemoteException e) {
            throw new ImsException("open()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }

        if (result <= 0) {
            // If the return value is a minus value,
            // it means that an error occurred in the service.
            // So, it needs to convert to the reason code specified in ImsReasonInfo.
            throw new ImsException("open()", (result * (-1)));
        }

        return result;
    }

    /**
     * Closes the specified service ({@link ImsServiceClass}) not to make/receive calls.
     * All the resources that were allocated to the service are also released.
     *
     * @param serviceId a service id to be closed which is obtained from {@link ImsManager#open}
     * @throws ImsException if calling the IMS service results in an error
     */
    public void close(int serviceId) throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        /// M: add for debug @{
        if (DBG) log("ImsManager: close");
        /// @}

        try {
            mImsService.close(serviceId);
        } catch (RemoteException e) {
            throw new ImsException("close()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        } finally {
            mUt = null;
            mConfig = null;
            mEcbm = null;
        }
    }

    /**
     * Gets the configuration interface to provision / withdraw the supplementary service settings.
     *
     * @param serviceId a service id which is obtained from {@link ImsManager#open}
     * @return the Ut interface instance
     * @throws ImsException if getting the Ut interface results in an error
     */
    public ImsUtInterface getSupplementaryServiceConfiguration(int serviceId)
            throws ImsException {
        // FIXME: manage the multiple Ut interfaces based on the service id
        if (mUt == null) {
            checkAndThrowExceptionIfServiceUnavailable();

            try {
                IImsUt iUt = mImsService.getUtInterface(serviceId);

                if (iUt == null) {
                    throw new ImsException("getSupplementaryServiceConfiguration()",
                            ImsReasonInfo.CODE_UT_NOT_SUPPORTED);
                }

                mUt = new ImsUt(iUt);
            } catch (RemoteException e) {
                throw new ImsException("getSupplementaryServiceConfiguration()", e,
                        ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
            }
        }

        return mUt;
    }

    /**
     * Checks if the IMS service has successfully registered to the IMS network
     * with the specified service & call type.
     *
     * @param serviceId a service id which is obtained from {@link ImsManager#open}
     * @param serviceType a service type that is specified in {@link ImsCallProfile}
     *        {@link ImsCallProfile#SERVICE_TYPE_NORMAL}
     *        {@link ImsCallProfile#SERVICE_TYPE_EMERGENCY}
     * @param callType a call type that is specified in {@link ImsCallProfile}
     *        {@link ImsCallProfile#CALL_TYPE_VOICE_N_VIDEO}
     *        {@link ImsCallProfile#CALL_TYPE_VOICE}
     *        {@link ImsCallProfile#CALL_TYPE_VT}
     *        {@link ImsCallProfile#CALL_TYPE_VS}
     * @return true if the specified service id is connected to the IMS network;
     *        false otherwise
     * @throws ImsException if calling the IMS service results in an error
     */
    public boolean isConnected(int serviceId, int serviceType, int callType)
            throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        try {
            return mImsService.isConnected(serviceId, serviceType, callType);
        } catch (RemoteException e) {
            throw new ImsException("isServiceConnected()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    /**
     * Checks if the specified IMS service is opend.
     *
     * @param serviceId a service id which is obtained from {@link ImsManager#open}
     * @return true if the specified service id is opened; false otherwise
     * @throws ImsException if calling the IMS service results in an error
     */
    public boolean isOpened(int serviceId) throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        try {
            return mImsService.isOpened(serviceId);
        } catch (RemoteException e) {
            throw new ImsException("isOpened()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    /**
     * Creates a {@link ImsCallProfile} from the service capabilities & IMS registration state.
     *
     * @param serviceId a service id which is obtained from {@link ImsManager#open}
     * @param serviceType a service type that is specified in {@link ImsCallProfile}
     *        {@link ImsCallProfile#SERVICE_TYPE_NONE}
     *        {@link ImsCallProfile#SERVICE_TYPE_NORMAL}
     *        {@link ImsCallProfile#SERVICE_TYPE_EMERGENCY}
     * @param callType a call type that is specified in {@link ImsCallProfile}
     *        {@link ImsCallProfile#CALL_TYPE_VOICE}
     *        {@link ImsCallProfile#CALL_TYPE_VT}
     *        {@link ImsCallProfile#CALL_TYPE_VT_TX}
     *        {@link ImsCallProfile#CALL_TYPE_VT_RX}
     *        {@link ImsCallProfile#CALL_TYPE_VT_NODIR}
     *        {@link ImsCallProfile#CALL_TYPE_VS}
     *        {@link ImsCallProfile#CALL_TYPE_VS_TX}
     *        {@link ImsCallProfile#CALL_TYPE_VS_RX}
     * @return a {@link ImsCallProfile} object
     * @throws ImsException if calling the IMS service results in an error
     */
    public ImsCallProfile createCallProfile(int serviceId,
            int serviceType, int callType) throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        try {
            return mImsService.createCallProfile(serviceId, serviceType, callType);
        } catch (RemoteException e) {
            throw new ImsException("createCallProfile()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    /**
     * Creates a {@link ImsCall} to make a call.
     *
     * @param serviceId a service id which is obtained from {@link ImsManager#open}
     * @param profile a call profile to make the call
     *      (it contains service type, call type, media information, etc.)
     * @param participants participants to invite the conference call
     * @param listener listen to the call events from {@link ImsCall}
     * @return a {@link ImsCall} object
     * @throws ImsException if calling the IMS service results in an error
     */
    public ImsCall makeCall(int serviceId, ImsCallProfile profile, String[] callees,
            ImsCall.Listener listener) throws ImsException {
        if (DBG) {
            log("makeCall :: serviceId=" + serviceId
                    + ", profile=" + profile + ", callees=" + callees);
        }

        checkAndThrowExceptionIfServiceUnavailable();

        ImsCall call = new ImsCall(mContext, profile);

        call.setListener(listener);
        ImsCallSession session = createCallSession(serviceId, profile);

        if ((callees != null) && (callees.length == 1) &&
                /// M:  For VoLTE enhanced conference call. @{
                !profile.getCallExtraBoolean(ImsCallProfile.EXTRA_CONFERENCE)) {
                /// @}
            call.start(session, callees[0]);
        } else {
            call.start(session, callees);
        }

        return call;
    }

    /**
     * Creates a {@link ImsCall} to take an incoming call.
     *
     * @param serviceId a service id which is obtained from {@link ImsManager#open}
     * @param incomingCallIntent the incoming call broadcast intent
     * @param listener to listen to the call events from {@link ImsCall}
     * @return a {@link ImsCall} object
     * @throws ImsException if calling the IMS service results in an error
     */
    public ImsCall takeCall(int serviceId, Intent incomingCallIntent,
            ImsCall.Listener listener) throws ImsException {
        if (DBG) {
            log("takeCall :: serviceId=" + serviceId
                    + ", incomingCall=" + incomingCallIntent);
        }

        checkAndThrowExceptionIfServiceUnavailable();

        if (incomingCallIntent == null) {
            throw new ImsException("Can't retrieve session with null intent",
                    ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
        }

        int incomingServiceId = getServiceId(incomingCallIntent);

        if (serviceId != incomingServiceId) {
            throw new ImsException("Service id is mismatched in the incoming call intent",
                    ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
        }

        String callId = getCallId(incomingCallIntent);

        if (callId == null) {
            throw new ImsException("Call ID missing in the incoming call intent",
                    ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
        }

        try {
            IImsCallSession session = mImsService.getPendingCallSession(serviceId, callId);

            if (session == null) {
                throw new ImsException("No pending session for the call",
                        ImsReasonInfo.CODE_LOCAL_NO_PENDING_CALL);
            }

            ImsCall call = new ImsCall(mContext, session.getCallProfile());

            call.attachSession(new ImsCallSession(session));
            call.setListener(listener);

            return call;
        } catch (Throwable t) {
            throw new ImsException("takeCall()", t, ImsReasonInfo.CODE_UNSPECIFIED);
        }
    }

    /**
     * Gets the config interface to get/set service/capability parameters.
     *
     * @return the ImsConfig instance.
     * @throws ImsException if getting the setting interface results in an error.
     */
    public ImsConfig getConfigInterface() throws ImsException {

        if (mConfig == null) {
            checkAndThrowExceptionIfServiceUnavailable();

            try {
                IImsConfig config = mImsService.getConfigInterface(mPhoneId);
                if (config == null) {
                    throw new ImsException("getConfigInterface()",
                            ImsReasonInfo.CODE_LOCAL_SERVICE_UNAVAILABLE);
                }
                mConfig = new ImsConfig(config, mContext);
            } catch (RemoteException e) {
                throw new ImsException("getConfigInterface()", e,
                        ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
            }
        }
        if (DBG) log("getConfigInterface(), mConfig= " + mConfig);
        return mConfig;
    }

    public void setUiTTYMode(Context context, int serviceId, int uiTtyMode, Message onComplete)
            throws ImsException {

        checkAndThrowExceptionIfServiceUnavailable();

        try {
            mImsService.setUiTTYMode(serviceId, uiTtyMode, onComplete);
        } catch (RemoteException e) {
            throw new ImsException("setTTYMode()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }

        if (!context.getResources().getBoolean(
                com.android.internal.R.bool.config_carrier_volte_tty_supported)) {
            setAdvanced4GMode((uiTtyMode == TelecomManager.TTY_MODE_OFF) &&
                    isEnhanced4gLteModeSettingEnabledByUser(context));
        }
    }

    /**
     * Gets the call ID from the specified incoming call broadcast intent.
     *
     * @param incomingCallIntent the incoming call broadcast intent
     * @return the call ID or null if the intent does not contain it
     */
    private static String getCallId(Intent incomingCallIntent) {
        if (incomingCallIntent == null) {
            return null;
        }

        return incomingCallIntent.getStringExtra(EXTRA_CALL_ID);
    }

    /**
     * Gets the sequence number from the specified incoming call broadcast intent.
     *
     * @param incomingCallIntent the incoming call broadcast intent
     * @return the sequence number or null if the intent does not contain it
     * @hide
     */
    private static int getSeqNum(Intent incomingCallIntent) {
        if (incomingCallIntent == null) {
            return (-1);
        }

        return incomingCallIntent.getIntExtra(EXTRA_SEQ_NUM, -1);
    }

    /**
     * Gets the service type from the specified incoming call broadcast intent.
     *
     * @param incomingCallIntent the incoming call broadcast intent
     * @return the service identifier or -1 if the intent does not contain it
     */
    private static int getServiceId(Intent incomingCallIntent) {
        if (incomingCallIntent == null) {
            return (-1);
        }

        return incomingCallIntent.getIntExtra(EXTRA_SERVICE_ID, -1);
    }

    /**
     * Binds the IMS service only if the service is not created.
     */
    private void checkAndThrowExceptionIfServiceUnavailable()
            throws ImsException {
        if (mImsService == null) {
            createImsService(true);

            if (mImsService == null) {
                throw new ImsException("Service is unavailable",
                        ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
            }
        }
    }

    private static String getImsServiceName(int phoneId) {
        // TODO: MSIM implementation needs to decide on service name as a function of phoneId
        return IMS_SERVICE;
    }

    /**
     * Binds the IMS service to make/receive the call.
     */
    private void createImsService(boolean checkService) {
        if (checkService) {
            IBinder binder = ServiceManager.checkService(getImsServiceName(mPhoneId));

            if (binder == null) {
                /// M: add for debug @{
                if (DBG) log("ImsManager: createImsService binder is null");
                /// @}
                return;
            }
        }

        IBinder b = ServiceManager.getService(getImsServiceName(mPhoneId));

        if (b != null) {
            try {
                b.linkToDeath(mDeathRecipient, 0);
            } catch (RemoteException e) {
            }
        }

        mImsService = IImsService.Stub.asInterface(b);
        /// M: add for debug @{
        if (DBG) log("ImsManager: mImsService = " + mImsService);
        /// @}
    }

    /**
     * Creates a {@link ImsCallSession} with the specified call profile.
     * Use other methods, if applicable, instead of interacting with
     * {@link ImsCallSession} directly.
     *
     * @param serviceId a service id which is obtained from {@link ImsManager#open}
     * @param profile a call profile to make the call
     */
    private ImsCallSession createCallSession(int serviceId,
            ImsCallProfile profile) throws ImsException {
        try {
            return new ImsCallSession(mImsService.createCallSession(serviceId, profile, null));
        } catch (RemoteException e) {
            return null;
        }
    }

    private ImsRegistrationListenerProxy createRegistrationListenerProxy(int serviceClass,
            ImsConnectionStateListener listener) {
        ImsRegistrationListenerProxy proxy =
                new ImsRegistrationListenerProxy(serviceClass, listener);
        return proxy;
    }

    private void log(String s) {
        Rlog.d(TAG, s);
    }

    private void loge(String s) {
        Rlog.e(TAG, s);
    }

    private void loge(String s, Throwable t) {
        Rlog.e(TAG, s, t);
    }

    /**
     * Used for turning on IMS.if its off already
     */
    private void turnOnIms() throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        try {
            mImsService.turnOnIms(mPhoneId);
        } catch (RemoteException e) {
            throw new ImsException("turnOnIms() ", e, ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    private void setAdvanced4GMode(boolean turnOn) throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        ImsConfig config = getConfigInterface();
        if (config != null) {
            config.setFeatureValue(ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE,
                    TelephonyManager.NETWORK_TYPE_LTE, turnOn ? 1 : 0, null);
            if (isVtEnabledByPlatform(mContext)) {
                // TODO: once VT is available on platform replace the '1' with the current
                // user configuration of VT.
                config.setFeatureValue(ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE,
                        TelephonyManager.NETWORK_TYPE_LTE, turnOn ? 1 : 0, null);
            }
        }

        if (turnOn) {
            turnOnIms();
        } else if (mContext.getResources().getBoolean(
                com.android.internal.R.bool.imsServiceAllowTurnOff) &&
                !isWfcEnabledByPlatform(mContext) ||
                !isWfcEnabledByUser(mContext)) {
            log("setAdvanced4GMode() : imsServiceAllowTurnOff -> turnOffIms");
            turnOffIms();
        }
    }


    private void setWfcOnOff(boolean turnOn) throws ImsException {
          checkAndThrowExceptionIfServiceUnavailable();


          if (turnOn) {
              turnOnIms();
          } else if (!isEnhanced4gLteModeSettingEnabledByUser(mContext)) {
              Log.d(TAG, "setWfcOnOff() : LTE already off, so turn off IMS");
              turnOffIms();
          }
      }

    /**
     * Used for turning off IMS completely in order to make the device CSFB'ed.
     * Once turned off, all calls will be over CS.
     */
    private void turnOffIms() throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        try {
            mImsService.turnOffIms(mPhoneId);
        } catch (RemoteException e) {
            throw new ImsException("turnOffIms() ", e, ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    /**
     * Death recipient class for monitoring IMS service.
     */
    private class ImsServiceDeathRecipient implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
            mImsService = null;
            mUt = null;
            mConfig = null;
            mEcbm = null;

            if (mContext != null) {
                Intent intent = new Intent(ACTION_IMS_SERVICE_DOWN);
                intent.putExtra(EXTRA_PHONE_ID, mPhoneId);
                mContext.sendBroadcast(new Intent(intent));
            }
        }
    }

    /**
     * Adapter class for {@link IImsRegistrationListener}.
     */
    private class ImsRegistrationListenerProxy extends IImsRegistrationListener.Stub {
        private int mServiceClass;
        private ImsConnectionStateListener mListener;

        public ImsRegistrationListenerProxy(int serviceClass,
                ImsConnectionStateListener listener) {
            mServiceClass = serviceClass;
            mListener = listener;
        }

        public boolean isSameProxy(int serviceClass) {
            return (mServiceClass == serviceClass);
        }

        @Override
        public void registrationConnected() {
            if (DBG) {
                log("registrationConnected ::");
            }

            if (mListener != null) {
                mListener.onImsConnected();
            }
        }

        @Override
        public void registrationDisconnected() {
            if (DBG) {
                log("registrationDisconnected ::");
            }

            if (mListener != null) {
                mListener.onImsDisconnected();
            }
        }

        @Override
        public void registrationResumed() {
            if (DBG) {
                log("registrationResumed ::");
            }

            if (mListener != null) {
                mListener.onImsResumed();
            }
        }

        @Override
        public void registrationSuspended() {
            if (DBG) {
                log("registrationSuspended ::");
            }

            if (mListener != null) {
                mListener.onImsSuspended();
            }
        }

        @Override
        public void registrationServiceCapabilityChanged(int serviceClass, int event) {
            log("registrationServiceCapabilityChanged :: serviceClass=" +
                    serviceClass + ", event=" + event);

            if (mListener != null) {
                mListener.onImsConnected();
            }
        }

        @Override
        public void registrationFeatureCapabilityChanged(int serviceClass,
                int[] enabledFeatures, int[] disabledFeatures) {
            log("registrationFeatureCapabilityChanged :: serviceClass=" +
                    serviceClass);
            if (mListener != null) {
                mListener.onFeatureCapabilityChanged(serviceClass,
                        enabledFeatures, disabledFeatures);
            }
        }

        @Override
        public void registrationDisconnectedWithCause(int cause) {
            if (DBG) {
                log("registrationDisconnected ::");
            }

            if (mListener != null) {
                mListener.onImsDisconnectedWithCause(cause);
            }
        }

    }
    /**
     * Gets the ECBM interface to request ECBM exit.
     *
     * @param serviceId a service id which is obtained from {@link ImsManager#open}
     * @return the ECBM interface instance
     * @throws ImsException if getting the ECBM interface results in an error
     */
    public ImsEcbm getEcbmInterface(int serviceId) throws ImsException {
        if (mEcbm == null) {
            checkAndThrowExceptionIfServiceUnavailable();

            try {
                IImsEcbm iEcbm = mImsService.getEcbmInterface(serviceId);

                if (iEcbm == null) {
                    throw new ImsException("getEcbmInterface()",
                            ImsReasonInfo.CODE_ECBM_NOT_SUPPORTED);
                }
                mEcbm = new ImsEcbm(iEcbm);
            } catch (RemoteException e) {
                throw new ImsException("getEcbmInterface()", e,
                        ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
            }
        }
        return mEcbm;
    }

    /**
     * To Allow or refuse incoming call indication to send to App.
     *
     * @param serviceId a service id which is obtained from {@link ImsManager#open}
     * @param incomingCallIndication the incoming call broadcast intent.
     * @param isAllow to indication to allow or refuse the incoming call indication.
     * @throws ImsException if set call indication results in an error.
     * @hide
     */
    public void setCallIndication(int serviceId, Intent incomingCallIndication,
            boolean isAllow) throws ImsException {
        if (DBG) {
            log("setCallIndication :: serviceId=" + serviceId
                    + ", incomingCallIndication=" + incomingCallIndication);
        }

        checkAndThrowExceptionIfServiceUnavailable();

        if (incomingCallIndication == null) {
            throw new ImsException("Can't retrieve session with null intent",
                    ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
        }

        int incomingServiceId = getServiceId(incomingCallIndication);

        if (serviceId != incomingServiceId) {
            throw new ImsException("Service id is mismatched in the incoming call intent",
                    ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
        }

        String callId = getCallId(incomingCallIndication);

        if (callId == null) {
            throw new ImsException("Call ID missing in the incoming call intent",
                    ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
        }

        int seqNum = getSeqNum(incomingCallIndication);

        if (seqNum == -1) {
            throw new ImsException("seqNum missing in the incoming call intent",
                    ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
        }

        try {
            mImsService.setCallIndication(callId, seqNum, isAllow);
        } catch (RemoteException e) {
            throw new ImsException("setCallIndication()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    /**
     * To get IMS state.
     *
     * @return ims state - disabled, enabling, enable, disabling.
     * @throws ImsException if getting the ims status result in an error.
     * @hide
     */
    public int getImsState() throws ImsException {
        int imsState = PhoneConstants.IMS_STATE_DISABLED;

        checkAndThrowExceptionIfServiceUnavailable();

        try {
            imsState = mImsService.getImsState();
        } catch (RemoteException e) {
            throw new ImsException("getImsState()", e, ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
        return imsState;
    }

    /**
    * To get IMS registration status.
    *
    * @return true if ims is registered or false if ims is unregistered.
    * @throws ImsException if getting the ims registration result in an error.
    * @hide
    */
    public boolean getImsRegInfo() throws ImsException {
        boolean isImsReg = false;

        checkAndThrowExceptionIfServiceUnavailable();

        try {
            isImsReg = mImsService.getImsRegInfo(mPhoneId);
        } catch (RemoteException e) {
            throw new ImsException("getImsRegInfo", e, ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
        return isImsReg;
    }

    /**
    * To get IMS registration extension information.
    *
    * @return a string which is converted from the value of ims feature capability.
    * @throws ImsException if getting the ims extension information result in an error.
    * @hide
    */
    public String getImsExtInfo() throws ImsException {
        String imsExtInfo = "0";

        checkAndThrowExceptionIfServiceUnavailable();

        try {
            imsExtInfo = mImsService.getImsExtInfo();
        } catch (RemoteException e) {
            throw new ImsException("getImsExtInfo()", e, ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
        return imsExtInfo;
    }

    /**
    * To hangup all calls.
    * @throws ImsException if getting the ims status result in an error.
    * @hide
    */
    public void hangupAllCall() throws ImsException {

        checkAndThrowExceptionIfServiceUnavailable();

        try {
            mImsService.hangupAllCall();
        } catch (RemoteException e) {
            throw new ImsException("hangupAll()", e, ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    /* To get WFC state */
    public int getWfcStatusCode() /*throws RemoteException*/ {
        /*if (mImsNotificationController == null) {
            throw new RemoteException ("getWfcStatusCode:ImsNotification controller not created yet");
            }
            return mImsNotificationController.getRegistrationStatus(); */
        if (mImsService == null) return WfcReasonInfo.CODE_WFC_DEFAULT;
        try {
            return mImsService.getRegistrationStatus();
        } catch (RemoteException e) {
            return WfcReasonInfo.CODE_WFC_DEFAULT;
        }
    }

}
