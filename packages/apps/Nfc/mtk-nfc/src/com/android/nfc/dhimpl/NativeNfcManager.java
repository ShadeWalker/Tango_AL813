/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.nfc.dhimpl;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.Context;
import android.nfc.ErrorCodes;
import android.nfc.tech.Ndef;
import android.nfc.tech.TagTechnology;
import android.util.Log;

import com.android.nfc.DeviceHost;
import com.android.nfc.LlcpException;
import com.android.nfc.NfcDiscoveryParameters;

/// M: @ {
import com.mediatek.nfc.dynamicload.NativeDynamicLoad;
import android.provider.Settings;
import com.mediatek.nfc.addon.MtkNfcAddonSequence;
//import com.mediatek.nfc.addon.NfcStatusNotificationUi;
/// }

/**
 * Native interface to the NFC Manager functions
 */
public class NativeNfcManager implements DeviceHost {
    private static final String TAG = "NativeNfcManager";

    private static String NFC_CONTROLLER_FIRMWARE_FILE_NAME;

    static final String PREF = "NciDeviceHost";

    static final int DEFAULT_LLCP_MIU = 128;
    static final int DEFAULT_LLCP_RWSIZE = 1;

    static final String DRIVER_NAME = "android-nci";

    /// M: @ {
    private static final int MTK_NFC_CHIP_TYPE_MT6605 = 0x02;
    private static final int MTK_NFC_CHIP_TYPE_INVALID = -1;
    private static final String NFC_CONTROLLER_CODE = "nfc_controller_code";
    private static int sNfcController = MTK_NFC_CHIP_TYPE_INVALID;

    static public boolean sIsUnsupportedChip = false;
    /// }

    static {
/// M: @ {
            sNfcController = NativeDynamicLoad.queryVersion();

        try {
            switch(sNfcController) {
                case MTK_NFC_CHIP_TYPE_MT6605 :
                    Log.d(TAG, "Load MT6605 jni library.");
                    System.loadLibrary("nfc_mt6605_jni");
                    break;
                default :
                    Log.e(TAG, "Unknown Chip. disable all NFC functions.");
                    sIsUnsupportedChip = true;
                    break;
            }
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "NativeNfcManager library not found. (nfc_jni)");
        }
/// }
    }

    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String INTERNAL_TARGET_DESELECTED_ACTION = "com.android.nfc.action.INTERNAL_TARGET_DESELECTED";

    /* Native structure */
    private long mNative;

    private final DeviceHostListener mListener;
    private final Context mContext;

    public NativeNfcManager(Context context, DeviceHostListener listener) {
        mListener = listener;

        /// M: @ {
        if (sNfcController != MTK_NFC_CHIP_TYPE_INVALID &&
                Settings.Global.getInt(context.getContentResolver(), NFC_CONTROLLER_CODE, -1) == MTK_NFC_CHIP_TYPE_INVALID) {
            Log.d(TAG, "Write NfcController " + sNfcController + "  to Settings.Global");
            Settings.Global.putInt(context.getContentResolver(), NFC_CONTROLLER_CODE, sNfcController);
        }

        /// }

        /// M: @ {
        if (!sIsUnsupportedChip) {
            jniAdapterInitAddon(context, this);

            initializeNativeStructure();
        }
/// }
        mContext = context;
    }

    public native boolean initializeNativeStructure();

    private native boolean doDownload();

    public native int doGetLastError();

    @Override
    public void checkFirmware() {
        doDownload();
    }

    private native boolean doInitialize();

    //GSMA add
    private native boolean doActivateSecureElementInterfaceById(int simID);

    @Override
    public boolean initialize() {

        /// M: @ {
        boolean result = doInitialize();
        Log.d(TAG, "doInitialize() result: " + result);
        if (result) {
            MtkNfcAddonSequence.getInstance().applyInitializeSequence();
        }
        return result;
        /// }
    }

    private native boolean doDeinitialize();

    @Override
    public boolean deinitialize() {
        /// M: @ {
        MtkNfcAddonSequence.getInstance().applyDeinitializeSequence();
        /// }
        return doDeinitialize();
    }

    @Override
    public String getName() {
        return DRIVER_NAME;
    }

    @Override
    public native boolean sendRawFrame(byte[] data); // { return false; }

    @Override
    public native boolean routeAid(byte[] aid, int route); // { return false; }

    @Override
    public native boolean unrouteAid(byte[] aid); // { return false; }

    private native void doEnableDiscovery(int techMask,
                                          boolean enableLowPowerPolling,
                                          boolean enableReaderMode,
                                          boolean enableHostRouting,
                                          boolean enableP2p,
                                          boolean restart);
    @Override
    public void enableDiscovery(NfcDiscoveryParameters params, boolean restart) {
        /// M: @ {
        MtkNfcAddonSequence.getInstance().applyEnableDiscoverySequence();
        /// }
        doEnableDiscovery(params.getTechMask(), params.shouldEnableLowPowerDiscovery(),
                params.shouldEnableReaderMode(), params.shouldEnableHostRouting(),
                params.shouldEnableP2p(), restart);
    }

    /// M: @ {
    //public native void disableDiscovery();
    private native void doDisableDiscovery();


    @Override
    public void disableDiscovery() {

        doDisableDiscovery();
        MtkNfcAddonSequence.getInstance().applyDisableDiscoverySequence();
    }
    //
    /// }

    private native NativeLlcpConnectionlessSocket doCreateLlcpConnectionlessSocket(int nSap,
            String sn);

    @Override
    public LlcpConnectionlessSocket createLlcpConnectionlessSocket(int nSap, String sn)
            throws LlcpException {
        LlcpConnectionlessSocket socket = doCreateLlcpConnectionlessSocket(nSap, sn);
        if (socket != null) {
            return socket;
        } else {
            /* Get Error Status */
            int error = doGetLastError();

            Log.d(TAG, "failed to create llcp socket: " + ErrorCodes.asString(error));

            switch (error) {
                case ErrorCodes.ERROR_BUFFER_TO_SMALL:
                case ErrorCodes.ERROR_INSUFFICIENT_RESOURCES:
                    throw new LlcpException(error);
                default:
                    throw new LlcpException(ErrorCodes.ERROR_SOCKET_CREATION);
            }
        }
    }

    private native NativeLlcpServiceSocket doCreateLlcpServiceSocket(int nSap, String sn, int miu,
            int rw, int linearBufferLength);
    @Override
    public LlcpServerSocket createLlcpServerSocket(int nSap, String sn, int miu,
            int rw, int linearBufferLength) throws LlcpException {
        LlcpServerSocket socket = doCreateLlcpServiceSocket(nSap, sn, miu, rw, linearBufferLength);
        if (socket != null) {
            return socket;
        } else {
            /* Get Error Status */
            int error = doGetLastError();

            Log.d(TAG, "failed to create llcp socket: " + ErrorCodes.asString(error));

            switch (error) {
                case ErrorCodes.ERROR_BUFFER_TO_SMALL:
                case ErrorCodes.ERROR_INSUFFICIENT_RESOURCES:
                    throw new LlcpException(error);
                default:
                    throw new LlcpException(ErrorCodes.ERROR_SOCKET_CREATION);
            }
        }
    }

    private native NativeLlcpSocket doCreateLlcpSocket(int sap, int miu, int rw,
            int linearBufferLength);
    @Override
    public LlcpSocket createLlcpSocket(int sap, int miu, int rw,
            int linearBufferLength) throws LlcpException {
        LlcpSocket socket = doCreateLlcpSocket(sap, miu, rw, linearBufferLength);
        if (socket != null) {
            return socket;
        } else {
            /* Get Error Status */
            int error = doGetLastError();

            Log.d(TAG, "failed to create llcp socket: " + ErrorCodes.asString(error));

            switch (error) {
                case ErrorCodes.ERROR_BUFFER_TO_SMALL:
                case ErrorCodes.ERROR_INSUFFICIENT_RESOURCES:
                    throw new LlcpException(error);
                default:
                    throw new LlcpException(ErrorCodes.ERROR_SOCKET_CREATION);
            }
        }
    }

    @Override
    public native boolean doCheckLlcp();

    @Override
    public native boolean doActivateLlcp();

    private native void doResetTimeouts();

    @Override
    public void resetTimeouts() {
        doResetTimeouts();
    }

    /// M: @ {

    @Override
    public native void doAbort();

    /*
    public void doAbort(){
    // TODO:: AsyncTask already used
    Log.d(TAG, "doAbort() " );
    MtkNfcAddonSequence.getInstance().abortToDisable();
    }
    */

    /// }



    private native boolean doSetTimeout(int tech, int timeout);
    @Override
    public boolean setTimeout(int tech, int timeout) {
        return doSetTimeout(tech, timeout);
    }

    private native int doGetTimeout(int tech);
    @Override
    public int getTimeout(int tech) {
        return doGetTimeout(tech);
    }


    @Override
    public boolean canMakeReadOnly(int ndefType) {
        return (ndefType == Ndef.TYPE_1 || ndefType == Ndef.TYPE_2);
    }

    @Override
    public int getMaxTransceiveLength(int technology) {
        switch (technology) {
            case (TagTechnology.NFC_A):
            case (TagTechnology.MIFARE_CLASSIC):
            case (TagTechnology.MIFARE_ULTRALIGHT):
                return 253; // PN544 RF buffer = 255 bytes, subtract two for CRC
            case (TagTechnology.NFC_B):
                /////////////////////////////////////////////////////////////////
                // Broadcom: Since BCM2079x supports this, set NfcB max size.
                //return 0; // PN544 does not support transceive of raw NfcB
                return 253; // PN544 does not support transceive of raw NfcB
            case (TagTechnology.NFC_V):
                return 253; // PN544 RF buffer = 255 bytes, subtract two for CRC
            case (TagTechnology.ISO_DEP):
                /* The maximum length of a normal IsoDep frame consists of:
                 * CLA, INS, P1, P2, LC, LE + 255 payload bytes = 261 bytes
                 * such a frame is supported. Extended length frames however
                 * are not supported.
                 */
                return 261; // Will be automatically split in two frames on the RF layer
            case (TagTechnology.NFC_F):
                // for DTA T3T OP
                return 512; // PN544 RF buffer = 255 bytes, subtract one for SoD, two for CRC
            default:
                return 0;
        }

    }

    private native void doSetP2pInitiatorModes(int modes);
    @Override
    public void setP2pInitiatorModes(int modes) {
        doSetP2pInitiatorModes(modes);
    }

    private native void doSetP2pTargetModes(int modes);
    @Override
    public void setP2pTargetModes(int modes) {
        doSetP2pTargetModes(modes);
    }

    @Override
    public boolean getExtendedLengthApdusSupported() {
        // TODO check BCM support
        return false;
    }

    @Override
    public int getDefaultLlcpMiu() {
        return DEFAULT_LLCP_MIU;
    }

    @Override
    public int getDefaultLlcpRwSize() {
        return DEFAULT_LLCP_RWSIZE;
    }

    private native String doDump();
    @Override
    public String dump() {
        return doDump();
    }

    private native void doEnableScreenOffSuspend();
    @Override
    public boolean enableScreenOffSuspend() {
        doEnableScreenOffSuspend();
        return true;
    }

    private native void doDisableScreenOffSuspend();
    @Override
    public boolean disableScreenOffSuspend() {
        doDisableScreenOffSuspend();
        return true;
    }

    /**
     * Notifies Ndef Message (TODO: rename into notifyTargetDiscovered)
     */
    private void notifyNdefMessageListeners(NativeNfcTag tag) {
        mListener.onRemoteEndpointDiscovered(tag);
    }

    /**
     * Notifies P2P Device detected, to activate LLCP link
     */
    private void notifyLlcpLinkActivation(NativeP2pDevice device) {
        mListener.onLlcpLinkActivated(device);
    }

    /**
     * Notifies P2P Device detected, to activate LLCP link
     */
    private void notifyLlcpLinkDeactivated(NativeP2pDevice device) {
        mListener.onLlcpLinkDeactivated(device);
    }

    /**
     * Notifies first packet received from remote LLCP
     */
    private void notifyLlcpLinkFirstPacketReceived(NativeP2pDevice device) {
        mListener.onLlcpFirstPacketReceived(device);
    }

    private void notifySeFieldActivated() {
        mListener.onRemoteFieldActivated();
    }

    private void notifySeFieldDeactivated() {
        mListener.onRemoteFieldDeactivated();
    }
/*

    private void notifySeListenActivated() {
        mListener.onSeListenActivated();
    }

    private void notifySeListenDeactivated() {
        mListener.onSeListenDeactivated();
    }

    private void notifySeApduReceived(byte[] apdu) {
        mListener.onSeApduReceived(apdu);
    }

    private void notifySeEmvCardRemoval() {
        mListener.onSeEmvCardRemoval();
    }

    private void notifySeMifareAccess(byte[] block) {
        mListener.onSeMifareAccess(block);
    }
*/
    private void notifyHostEmuActivated() {
        mListener.onHostCardEmulationActivated();
    }

    private void notifyHostEmuData(byte[] data) {
        mListener.onHostCardEmulationData(data);
    }

    private void notifyHostEmuDeactivated() {
        mListener.onHostCardEmulationDeactivated();
    }
    /**
     * Notifies transaction
     */

    /// M: @ {
    private void notifyTransactionListeners(byte[] aid, byte[] para) {
        //mListener.onCardEmulationAidSelected(aid);
        
        Log.d(TAG, "notifyTransactionListeners(aid,para) ");
        MtkNfcAddonSequence.getInstance().evtTransactionBroadcast(aid, para);
    }
    //public native void doSetNfc( int on_off);
    //public native void doSetNfcReaderP2p( int on_off);


    private native void doSetNfcModePolling(int mode  , boolean actNow);
    private native void doSelectSecureElementById(int seid);
    private native int[] doGetSecureElementList();
    private native void doDeselectSecureElement();

/*
    public int setNfcMode(int mode){
        Log.d(TAG, "setNfcMode ENTRY, mode = " + mode);
        doSetNfcMode(mode);
        Log.d(TAG, "setNfcMode EXIT");
        return 0;
    }
    */
    public int setNfcModePolling(int mode, boolean actNow) {
        Log.d(TAG, "SetNfcModePolling ENTRY, mode = " + mode + " ActNow:" + actNow);
        doSetNfcModePolling(mode, actNow);
        Log.d(TAG, "SetNfcModePolling EXIT");
        return 0;
    }

    public void deselectSecureElement() {
        Log.d(TAG, "deselectSecureElement ENTRY");
        doDeselectSecureElement();
        Log.d(TAG, "deselectSecureElement EXIT");
    }

    public boolean selectSecureElementById(int seId) {
        Log.d(TAG, "selectSecureElementById ENTRY, seId = " + seId);
        doSelectSecureElementById(seId);
        Log.d(TAG, "selectSecureElementById EXIT");
        return true;
    }

    public int[] getSecureElementList() {
        Log.d(TAG, "getSecureElementList ENTRY");
        int[] seList = doGetSecureElementList();
        String msg = "";
        if (seList != null) {
            for (int se : seList) {
                msg += se + ", ";
            }
        }
        Log.d(TAG, "getSecureElementList EXIT, seList = " + msg);
        return seList;
    }

    private void jniAdapterInitAddon(Context context, NativeNfcManager manager) {

        MtkNfcAddonSequence.createSingleton(context, manager);
        //NfcStatusNotificationUi.createSingleton(mContext);
    }

    //GSMA add
    public boolean doActivateSecureElementById(int simID) {
        Log.d(TAG, "doActivateSecureElementById ENTRY, simID = " + simID);
        boolean result = doActivateSecureElementInterfaceById(simID);
        Log.d(TAG, "doActivateSecureElementById EXIT, result = " + result);
        return result;
    }
/*
    @Override
    public boolean commitRouting(){
        // TODO:: Mingyen implement JNI method
        return false;
    }
*/
    @Override
    public native boolean commitRouting();


    /// }
}
