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

package com.android.internal.telephony;

import java.io.FileDescriptor;
import java.io.PrintWriter;

import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.Message;


public class PhoneSubInfoProxy extends IPhoneSubInfo.Stub {
    private PhoneSubInfo mPhoneSubInfo;

    public PhoneSubInfoProxy(PhoneSubInfo phoneSubInfo) {
        mPhoneSubInfo = phoneSubInfo;
    }

    public void setmPhoneSubInfo(PhoneSubInfo phoneSubInfo) {
        mPhoneSubInfo = phoneSubInfo;
    }

    @Override
    public String getDeviceId() {
        return mPhoneSubInfo.getDeviceId();
    }

    public String getImei() {
        return mPhoneSubInfo.getImei();
    }

    public String getNai() {
        return mPhoneSubInfo.getNai();
    }

    @Override
    public String getDeviceSvn() {
        return mPhoneSubInfo.getDeviceSvn();
    }

    /**
     * Retrieves the unique subscriber ID, e.g., IMSI for GSM phones.
     */
    @Override
    public String getSubscriberId() {
        return mPhoneSubInfo.getSubscriberId();
    }

    /**
     * Retrieves the Group Identifier Level1 for GSM phones.
     */
    public String getGroupIdLevel1() {
        return mPhoneSubInfo.getGroupIdLevel1();
    }

    /**
     * Retrieves the serial number of the ICC, if applicable.
     */
    @Override
    public String getIccSerialNumber() {
        return mPhoneSubInfo.getIccSerialNumber();
    }

    /**
     * Retrieves the phone number string for line 1.
     */
    @Override
    public String getLine1Number() {
        return mPhoneSubInfo.getLine1Number();
    }

    /**
     * Retrieves the alpha identifier for line 1.
     */
    @Override
    public String getLine1AlphaTag() {
        return mPhoneSubInfo.getLine1AlphaTag();
    }

    /**
     * Retrieves the MSISDN Number.
     */
    @Override
    public String getMsisdn() {
        return mPhoneSubInfo.getMsisdn();
    }

    /**
     * Retrieves the voice mail number.
     */
    @Override
    public String getVoiceMailNumber() {
        return mPhoneSubInfo.getVoiceMailNumber();
    }

    /**
     * Retrieves the complete voice mail number.
     */
    @Override
    public String getCompleteVoiceMailNumber() {
        return mPhoneSubInfo.getCompleteVoiceMailNumber();
    }

    /**
     * Retrieves the alpha identifier associated with the voice mail number.
     */
    @Override
    public String getVoiceMailAlphaTag() {
        return mPhoneSubInfo.getVoiceMailAlphaTag();
    }

    /**
     * Returns the IMS private user identity (IMPI) that was loaded from the ISIM.
     * @return the IMPI, or null if not present or not loaded
     */
    @Override
    public String getIsimImpi() {
        return mPhoneSubInfo.getIsimImpi();
    }

    /**
     * Returns the IMS home network domain name that was loaded from the ISIM.
     * @return the IMS domain name, or null if not present or not loaded
     */
    @Override
    public String getIsimDomain() {
        return mPhoneSubInfo.getIsimDomain();
    }

    /**
     * Returns the IMS public user identities (IMPU) that were loaded from the ISIM.
     * @return an array of IMPU strings, with one IMPU per string, or null if
     *      not present or not loaded
     */
    @Override
    public String[] getIsimImpu() {
        return mPhoneSubInfo.getIsimImpu();
    }

    @Override
    public String getDeviceIdForPhone(int phoneId) throws RemoteException {
        // FIXME: getDeviceIdForPhone
        return null;
    }

    @Override
    public String getImeiForSubscriber(int subId) throws RemoteException {
        // FIXME: getImeiForSubscriber
        return null;
    }

    @Override
    public String getDeviceSvnUsingSubId(int subId) throws RemoteException {
        // FIXME: getDeviceSvnUsingSubId
        return null;
    }

    @Override
    public String getNaiForSubscriber(int subId) throws RemoteException {
        // FIXME: NaiForSubscriber
        return null;
    }

    @Override
    public String getSubscriberIdForSubscriber(int subId) throws RemoteException {
        // FIXME: getSubscriberIdForSubscriber
        return null;
    }

    @Override
    public String getGroupIdLevel1ForSubscriber(int subId) throws RemoteException {
        // FIXME: getGroupIdLevel1ForSubscriber
        return null;
    }

    @Override
    public String getIccSerialNumberForSubscriber(int subId) throws RemoteException {
        // FIXME: getIccSerialNumberForSubscriber
        return null;
    }

    @Override
    public String getLine1NumberForSubscriber(int subId) throws RemoteException {
        // FIXME: getLine1NumberForSubscriber
        return null;
    }

    @Override
    public String getLine1AlphaTagForSubscriber(int subId) throws RemoteException {
        // FIXME: getLine1AlphaTagForSubscriber
        return null;
    }

    @Override
    public String getMsisdnForSubscriber(int subId) throws RemoteException {
        // FIXME: getMsisdnForSubscriber
        return null;
    }

    @Override
    public String getVoiceMailNumberForSubscriber(int subId) throws RemoteException {
        // FIXME: getVoiceMailNumberForSubscriber
        return null;
    }

    @Override
    public String getCompleteVoiceMailNumberForSubscriber(int subId) throws RemoteException {
        // FIXME: getCompleteVoiceMailNumberForSubscriber
        return null;
    }

    @Override
    public String getVoiceMailAlphaTagForSubscriber(int subId) throws RemoteException {
        // FIXME: getVoiceMailAlphaTagForSubscriber
        return null;
    }

    /**
     * Returns the IMS Service Table (IST) that was loaded from the ISIM.
     * @return IMS Service Table or null if not present or not loaded
     */
    @Override
    public String getIsimIst() {
        return mPhoneSubInfo.getIsimIst();
    }

    /**
     * Returns the IMS Proxy Call Session Control Function(PCSCF) that were loaded from the ISIM.
     * @return an array of  PCSCF strings with one PCSCF per string, or null if
     *      not present or not loaded
     */
    @Override
    public String[] getIsimPcscf() {
        return mPhoneSubInfo.getIsimPcscf();
    }

    /**
     * Returns the response of ISIM Authetification through RIL.
     * Returns null if the Authentification hasn't been successed or isn't present iphonesubinfo.
     * @return the response of ISIM Authetification, or null if not available
     * @deprecated
     * @see getIccSimChallengeResponse
     */
    public String getIsimChallengeResponse(String nonce) {
        return mPhoneSubInfo.getIsimChallengeResponse(nonce);
    }

    /**
     * Returns the response of the SIM application on the UICC to authentication
     * challenge/response algorithm. The data string and challenge response are
     * Base64 encoded Strings.
     * Can support EAP-SIM, EAP-AKA with results encoded per 3GPP TS 31.102.
     *
     * @param appType ICC application type (@see com.android.internal.telephony.PhoneConstants#APPTYPE_xxx)
     * @param data authentication challenge data
     * @return challenge response
     */
    public String getIccSimChallengeResponse(int subId, int appType, String data) {
        return mPhoneSubInfo.getIccSimChallengeResponse(subId, appType, data);
    }

    // MTK-START

    /**
     * Returns the IMS private user identity (IMPI) that was loaded from the ISIM for
     * particular subId.
     * @param subId subscription ID to be queried
     * @return the IMPI, or null if not present or not loaded
     */
    @Override
    public String getIsimImpiForSubscriber(int subId) throws RemoteException {
        return null;
    }

    /**
     * Returns the IMS home network domain name that was loaded from the ISIM for particular subId.
     * @param subId subscription ID to be queried
     * @return the IMS domain name, or null if not present or not loaded
     */
    @Override
    public String getIsimDomainForSubscriber(int subId) throws RemoteException {
        return null;
    }

    /**
     * Returns the IMS public user identities (IMPU) that were loaded from the
     * ISIM for particular subId.
     * @param subId subscription ID to be queried
     * @return an array of IMPU strings, with one IMPU per string, or null if
     *      not present or not loaded
     */
    @Override
    public String[] getIsimImpuForSubscriber(int subId) throws RemoteException {
        return null;
    }

    /**
     * Returns the IMS Service Table (IST) that was loaded from the ISIM for particular subId.
     * @param subId subscription ID to be queried
     * @return IMS Service Table or null if not present or not loaded
     */
    @Override
    public String getIsimIstForSubscriber(int subId) throws RemoteException {
        return null;
    }

    /**
     * Returns the IMS Proxy Call Session Control Function(PCSCF) that were loaded from
     * the ISIM for particular subId.
     * @param subId subscription ID to be queried
     * @return an array of PCSCF strings with one PCSCF per string, or null if
     *      not present or not loaded
     */
    @Override
    public String[] getIsimPcscfForSubscriber(int subId) throws RemoteException {
        return null;
    }

    /**
     * Returns the GBA bootstrapping parameters (GBABP) that was loaded from the ISIM.
     * @return GBA bootstrapping parameters or null if not present or not loaded
     */
    @Override
    public String getIsimGbabp() {
        return mPhoneSubInfo.getIsimGbabp();
    }

    /**
     * Returns the GBA bootstrapping parameters (GBABP) that was loaded from the ISIM for
     * particular subId.
     * @param subId subscription ID to be queried
     * @return GBA bootstrapping parameters or null if not present or not loaded
     */
    @Override
    public String getIsimGbabpForSubscriber(int subId) throws RemoteException {
        return null;
    }

    /**
     * Set the GBA bootstrapping parameters (GBABP) value into the ISIM.
     * @param gbabp a GBA bootstrapping parameters value in String type
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    @Override
    public void setIsimGbabp(String gbabp, Message onComplete) {
        mPhoneSubInfo.setIsimGbabp(gbabp, onComplete);
    }

    /**
     * Set the GBA bootstrapping parameters (GBABP) value into the ISIM for particular subId.
     * @param subId subscription ID to be queried
     * @param gbabp a GBA bootstrapping parameters value in String type
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    @Override
    public void setIsimGbabpForSubscriber(
            int subId, String gbabp, Message onComplete) throws RemoteException {
        return;
    }

    /**
     * Returns the USIM Service Table (UST) that was loaded from the USIM.
     * @param service service index on UST
     * @return the indicated service is supported or not
     */
    @Override
    public boolean getUsimService(int service) {
        return mPhoneSubInfo.getUsimService(service);
    }

    /**
     * Returns the USIM Service Table (UST) that was loaded from the USIM for particular subId.
     * @param subId subscription ID to be queried
     * @param service service index on UST
     * @return  the indicated service is supported or not
     */
    @Override
    public boolean getUsimServiceForSubscriber(int subId, int service) throws RemoteException {
        return false;
    }

    /**
     * Returns the GBA bootstrapping parameters (GBABP) that was loaded from the USIM.
     * @return GBA bootstrapping parameters or null if not present or not loaded
     */
    @Override
    public String getUsimGbabp() {
        return mPhoneSubInfo.getUsimGbabp();
    }

    /**
     * Returns the GBA bootstrapping parameters (GBABP) that was loaded from the USIM for
     * particular subId.
     * @param subId subscription ID to be queried
     * @return GBA bootstrapping parameters or null if not present or not loaded
     */
    @Override
    public String getUsimGbabpForSubscriber(int subId) throws RemoteException {
        return null;
    }

    /**
     * Set the GBA bootstrapping parameters (GBABP) value into the USIM.
     * @param gbabp a GBA bootstrapping parameters value in String type
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    @Override
    public void setUsimGbabp(String gbabp, Message onComplete) {
        mPhoneSubInfo.setUsimGbabp(gbabp, onComplete);
    }

    /**
     * Set the GBA bootstrapping parameters (GBABP) value into the USIM for particular subId.
     * @param subId subscription ID to be queried
     * @param gbabp a GBA bootstrapping parameters value in String type
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    @Override
    public void setUsimGbabpForSubscriber(
            int subId, String gbabp, Message onComplete) throws RemoteException {
        return;
    }

    /**
     * Returns the Public Service Identity of the SM-SC (PSISMSC) that was loaded from the USIM.
     * @return PSISMSC or null if not present or not loaded
     */
    @Override
    public byte[] getIsimPsismsc() {
        return mPhoneSubInfo.getIsimPsismsc();
    }

    /**
     * Returns the Public Service Identity of the SM-SC (PSISMSC) that was loaded from the
     * ISIM for particular subId.
     * @param subId subscription ID to be queried
     * @return PSISMSC or null if not present or not loaded
     */
    @Override
    public byte[] getIsimPsismscForSubscriber(int subId) throws RemoteException {
        return null;
    }

    /**
     * Returns the Public Service Identity of the SM-SC (PSISMSC) that was loaded from the USIM.
     * @return PSISMSC or null if not present or not loaded
     */
    @Override
    public byte[] getUsimPsismsc() {
        return mPhoneSubInfo.getUsimPsismsc();
    }

    /**
     * Returns the Public Service Identity of the SM-SC (PSISMSC) that was loaded from the USIM
     * for particular subId.
     * @param subId subscription ID to be queried
     * @return PSISMSC or null if not present or not loaded
     */
    @Override
    public byte[] getUsimPsismscForSubscriber(int subId) throws RemoteException {
        return null;
    }

    /**
     * Returns the Short message parameter (SMSP) that was loaded from the USIM.
     * @return PSISMSC or null if not present or not loaded
     */
    @Override
    public byte[] getUsimSmsp() {
        return mPhoneSubInfo.getUsimSmsp();
    }

    /**
     * Returns the Short message parameter (SMSP) that was loaded from the USIM for particular subId.
     * @param subId subscription ID to be queried
     * @return SMSP or null if not present or not loaded
     */
    @Override
    public byte[] getUsimSmspForSubscriber(int subId) throws RemoteException {
        return null;
    }

    /**
     * Returns the MCC+MNC length that was loaded from the USIM.
     * @return MCC+MNC length or 0 if not present or not loaded
     */
    @Override
    public int getMncLength() {
        return mPhoneSubInfo.getMncLength();
    }

    /**
     * Returns the MCC+MNC length that was loaded from the USIM for particular subId.
     * @param subId subscription ID to be queried
     * @return MCC+MNC length or 0 if not present or not loaded
     */
    @Override
    public int getMncLengthForSubscriber(int subId) throws RemoteException {
        return 0;
    }

    // MTK-END

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mPhoneSubInfo.dump(fd, pw, args);
    }
}
