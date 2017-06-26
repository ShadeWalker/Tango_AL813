/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (c) 2011-2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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

import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.Message;
import android.telephony.SubscriptionManager;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;

import java.lang.NullPointerException;
import java.lang.ArrayIndexOutOfBoundsException;

import com.android.internal.telephony.IPhoneSubInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneSubInfoProxy;

//HQ_xionghaifeng 20150930 add for HW systemmanager for 4G net start
import com.mediatek.internal.telephony.ITelephonyEx;
import android.content.Context;
import android.util.Log;
import android.provider.Settings;
//HQ_xionghaifeng 20150930 add for HW systemmanager for 4G net end

public class PhoneSubInfoController extends IPhoneSubInfo.Stub {
    private static final String TAG = "PhoneSubInfoController";
    private Phone[] mPhone;
	//HQ_xionghaifeng 20150930 add for HW systemmanager for 4G net start
	private static int mGetImsiCount = 0;
	
    public PhoneSubInfoController(Phone[] phone) {
        mPhone = phone;
        if (ServiceManager.getService("iphonesubinfo") == null) {
            ServiceManager.addService("iphonesubinfo", this);
        }
    }


    public String getDeviceId() {
        return getDeviceIdForPhone(SubscriptionManager.getPhoneId(getDefaultSubscription()));
    }

    public String getDeviceIdForPhone(int phoneId) {
        Phone phone = getPhone(phoneId);
        if (phone != null) {
            phone.getContext().enforceCallingOrSelfPermission(
                    android.Manifest.permission.READ_PHONE_STATE,
                    "Requires READ_PHONE_STATE");
            return phone.getDeviceId();
        } else {
            Rlog.e(TAG,"getDeviceIdForPhone phone " + phoneId + " is null");
            return null;
        }
    }

    public String getNaiForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getNai();
        } else {
            Rlog.e(TAG,"getNai phoneSubInfoProxy is null" +
                      " for Subscription:" + subId);
            return null;
        }
    }

    public String getImeiForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getImei();
        } else {
            Rlog.e(TAG,"getDeviceId phoneSubInfoProxy is null" +
                    " for Subscription:" + subId);
            return null;
        }
    }

    public String getDeviceSvn() {
        return getDeviceSvnUsingSubId(getDefaultSubscription());
    }

    public String getDeviceSvnUsingSubId(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getDeviceSvn();
        } else {
            Rlog.e(TAG,"getDeviceSvn phoneSubInfoProxy is null" +
                      " for Subscription:" + subId);
            return null;
        }
    }

    public String getSubscriberId() {
        return getSubscriberIdForSubscriber(getDefaultSubscription());
    }

    public String getSubscriberIdForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
			//HQ_xionghaifeng 20150930 add for HW systemmanager for 4G net start
			int slotid = SubscriptionManager.getSlotId(subId);
			int currentPhoneId = SubscriptionManager.getPhoneId(subId);
			
			TelephonyManager tm = TelephonyManager.getDefault();
			
			if (SubscriptionManager.isValidSlotId(slotid)
				&& tm.getSimState(slotid) == TelephonyManager.SIM_STATE_READY
				&& tm.getCurrentPhoneType(subId) == PhoneConstants.PHONE_TYPE_CDMA
				&& tm.getNetworkType(subId) == TelephonyManager.NETWORK_TYPE_LTE)
			{
				String imsi_4g = Settings.System.getString(
					getPhone(currentPhoneId).getContext().getContentResolver(),	"cdma_4g_imsi");
				Log.d("xionghaifeng", "getSubscriberId 4G: mGetImsiCount = " + mGetImsiCount + " imsi_4g :" + imsi_4g);
				if (mGetImsiCount < 100)
				{
					mGetImsiCount++;
					try {
						ITelephonyEx iTelephonyEx = ITelephonyEx.Stub.asInterface(
	                		ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
						imsi_4g = iTelephonyEx.getSubscriberIdForLteDcPhone(subId);
						//Log.d("xionghaifeng", "getSubscriberId 4G: imsi_4g = " + imsi_4g);
						Settings.System.putString(getPhone(currentPhoneId).getContext().getContentResolver(),
                			"cdma_4g_imsi", imsi_4g);
						return imsi_4g;

		            } catch (RemoteException ex) {
		                return null;
		            } catch (NullPointerException ex) {
		                // This could happen before phone restarts due to crashing
		                return null;
		            }
				}
				else
				{
					imsi_4g = Settings.System.getString(
						getPhone(currentPhoneId).getContext().getContentResolver(), "cdma_4g_imsi");
					return imsi_4g == null ? "000000000000000" : imsi_4g;
				}

			}
			//HQ_xionghaifeng 20150930 add for HW systemmanager for 4G net end

            return phoneSubInfoProxy.getSubscriberId();
        } else {
            Rlog.e(TAG,"getSubscriberId phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    /**
     * Retrieves the serial number of the ICC, if applicable.
     */
    public String getIccSerialNumber() {
        return getIccSerialNumberForSubscriber(getDefaultSubscription());
    }

    public String getIccSerialNumberForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getIccSerialNumber();
        } else {
            Rlog.e(TAG,"getIccSerialNumber phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public String getLine1Number() {
        return getLine1NumberForSubscriber(getDefaultSubscription());
    }

    public String getLine1NumberForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getLine1Number();
        } else {
            Rlog.e(TAG,"getLine1Number phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public String getLine1AlphaTag() {
        return getLine1AlphaTagForSubscriber(getDefaultSubscription());
    }

    public String getLine1AlphaTagForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getLine1AlphaTag();
        } else {
            Rlog.e(TAG,"getLine1AlphaTag phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public String getMsisdn() {
        return getMsisdnForSubscriber(getDefaultSubscription());
    }

    public String getMsisdnForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getMsisdn();
        } else {
            Rlog.e(TAG,"getMsisdn phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public String getVoiceMailNumber() {
        return getVoiceMailNumberForSubscriber(getDefaultSubscription());
    }

    public String getVoiceMailNumberForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getVoiceMailNumber();
        } else {
            Rlog.e(TAG,"getVoiceMailNumber phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public String getCompleteVoiceMailNumber() {
        return getCompleteVoiceMailNumberForSubscriber(getDefaultSubscription());
    }

    public String getCompleteVoiceMailNumberForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getCompleteVoiceMailNumber();
        } else {
            Rlog.e(TAG,"getCompleteVoiceMailNumber phoneSubInfoProxy" +
                      " is null for Subscription:" + subId);
            return null;
        }
    }

    public String getVoiceMailAlphaTag() {
        return getVoiceMailAlphaTagForSubscriber(getDefaultSubscription());
    }

    public String getVoiceMailAlphaTagForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getVoiceMailAlphaTag();
        } else {
            Rlog.e(TAG,"getVoiceMailAlphaTag phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    /**
     * get Phone sub info proxy object based on subId.
     **/
    private PhoneSubInfoProxy getPhoneSubInfoProxy(int subId) {

        int phoneId = SubscriptionManager.getPhoneId(subId);

        try {
            return getPhone(phoneId).getPhoneSubInfoProxy();
        } catch (NullPointerException e) {
            Rlog.e(TAG, "Exception is :" + e.toString() + " For subId :" + subId);
            e.printStackTrace();
            return null;
        }
    }

    private PhoneProxy getPhone(int phoneId) {
        if (phoneId < 0 || phoneId >= TelephonyManager.getDefault().getPhoneCount()) {
            phoneId = 0;
        }
        return (PhoneProxy) mPhone[phoneId];
    }

    private int getDefaultSubscription() {
        return  PhoneFactory.getDefaultSubscription();
    }


    public String getIsimImpi() {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(getDefaultSubscription());
        return phoneSubInfoProxy.getIsimImpi();
    }

    public String getIsimDomain() {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(getDefaultSubscription());
        return phoneSubInfoProxy.getIsimDomain();
    }

    public String[] getIsimImpu() {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(getDefaultSubscription());
        return phoneSubInfoProxy.getIsimImpu();
    }

    public String getIsimIst() throws RemoteException {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(getDefaultSubscription());
        return phoneSubInfoProxy.getIsimIst();
    }

    public String[] getIsimPcscf() throws RemoteException {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(getDefaultSubscription());
        return phoneSubInfoProxy.getIsimPcscf();
    }

    public String getIsimChallengeResponse(String nonce) throws RemoteException {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(getDefaultSubscription());
        return phoneSubInfoProxy.getIsimChallengeResponse(nonce);
    }

    public String getIccSimChallengeResponse(int subId, int appType, String data)
            throws RemoteException {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        return phoneSubInfoProxy.getIccSimChallengeResponse(subId, appType, data);
    }

     public String getGroupIdLevel1() {
         return getGroupIdLevel1ForSubscriber(getDefaultSubscription());
     }

     public String getGroupIdLevel1ForSubscriber(int subId) {
         PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
         if (phoneSubInfoProxy != null) {
             return phoneSubInfoProxy.getGroupIdLevel1();
         } else {
             Rlog.e(TAG,"getGroupIdLevel1 phoneSubInfoProxy is" +
                       " null for Subscription:" + subId);
             return null;
         }
     }

    // MTK-START
    public String getIsimImpiForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getIsimImpi();
        } else {
            Rlog.e(TAG,"getIsimImpi phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public String getIsimDomainForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getIsimDomain();
        } else {
            Rlog.e(TAG,"getIsimDomain phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public String[] getIsimImpuForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getIsimImpu();
        } else {
            Rlog.e(TAG,"getIsimImpu phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public String getIsimIstForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getIsimIst();
        } else {
            Rlog.e(TAG,"getIsimIst phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public String[] getIsimPcscfForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getIsimPcscf();
        } else {
            Rlog.e(TAG,"getIsimPcscf phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    // ISIM - GBA related support START
    public String getIsimGbabp() {
        return getIsimGbabpForSubscriber(getDefaultSubscription());
    }

    public String getIsimGbabpForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getIsimGbabp();
        } else {
            Rlog.e(TAG,"getIsimGbabp phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public void setIsimGbabp(String gbabp, Message onComplete) {
        setIsimGbabpForSubscriber(getDefaultSubscription(), gbabp, onComplete);
    }

    public void setIsimGbabpForSubscriber(int subId, String gbabp, Message onComplete) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            phoneSubInfoProxy.setIsimGbabp(gbabp, onComplete);
        } else {
            Rlog.e(TAG,"setIsimGbabp phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
        }
    }

    public boolean getUsimService(int service) {
        return getUsimServiceForSubscriber(getDefaultSubscription(), service);
    }

    public boolean getUsimServiceForSubscriber(int subId, int service) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getUsimService(service);
        } else {
            Rlog.e(TAG,"getUsimService phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return false;
        }
    }

    public String getUsimGbabp() {
        return getUsimGbabpForSubscriber(getDefaultSubscription());
    }

    public String getUsimGbabpForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getUsimGbabp();
        } else {
            Rlog.e(TAG,"getUsimGbabp phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public void setUsimGbabp(String gbabp, Message onComplete) {
        setUsimGbabpForSubscriber(getDefaultSubscription(), gbabp, onComplete);
    }

    public void setUsimGbabpForSubscriber(int subId, String gbabp, Message onComplete) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            phoneSubInfoProxy.setUsimGbabp(gbabp, onComplete);
        } else {
            Rlog.e(TAG,"setUsimGbabp phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
        }
    }
    // ISIM - GBA related support END

    public byte[] getIsimPsismsc() {
        return getIsimPsismscForSubscriber(getDefaultSubscription());
    }

    public byte[] getIsimPsismscForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getIsimPsismsc();
        } else {
            Rlog.e(TAG,"getIsimPsismsc phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public byte[] getUsimPsismsc() {
        return getUsimPsismscForSubscriber(getDefaultSubscription());
    }

    public byte[] getUsimPsismscForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getUsimPsismsc();
        } else {
            Rlog.e(TAG,"getUsimPsismsc phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public byte[] getUsimSmsp() {
        return getUsimSmspForSubscriber(getDefaultSubscription());
    }

    public byte[] getUsimSmspForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getUsimSmsp();
        } else {
            Rlog.e(TAG,"getUsimSmsp phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public int getMncLength() {
        return getMncLengthForSubscriber(getDefaultSubscription());
    }

    public int getMncLengthForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getMncLength();
        } else {
            Rlog.e(TAG,"getMncLength phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return 0;
        }
    }

    // MTK-END
}
