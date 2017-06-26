package com.mediatek.nfc.handoverprotocol;

import java.util.Arrays;

import android.util.Log;

import com.mediatek.nfc.handoverprotocol.CarrierData.CarrierConfigurationRecord;
//import com.mediatek.nfc.handover.HandoverMessage.HandoverCarrier;


import android.nfc.NdefMessage;
import android.nfc.NdefRecord;

/**
 * WFA P2P CCR
 */
public class WfaP2pCarrierConfiguration extends CarrierConfigurationRecord {

    static final String TAG = "WfaP2pCarrierConfiguration";
    static final boolean DBG = true;

    // Carrier type name for WFA P2P
    public static final String WFA_P2P_CARRIER_TYPE = "application/vnd.wfa.p2p"; //INfcWfaAppInternal.WFA_P2P_CARRIER_TYPE;
    //public static final byte[] TYPE = WFA_P2P_CARRIER_TYPE.getBytes();

    // P2p Dev Info payload
    private byte[] mPayload;

    //P2p Dev Info payload ID   ex: "1" = 0x31
    private byte[] mId;

    //private static final byte[] ID_BYTE = new byte[]{'b'};


    /**
     * Constructor
     */
    private WfaP2pCarrierConfiguration() {
        this.mCarrierType = WFA_P2P_CARRIER_TYPE;
        Log.d(TAG, "WfaP2pCarrierConfiguration() ");
    }

    public WfaP2pCarrierConfiguration(byte[] p2pDevInfoPayload, byte[] p2pDevInfoId) {
        this();

        mPayload    = p2pDevInfoPayload;
        mId         = p2pDevInfoId;
    }

   /**
     * Create NDEF Message (for PasswordToken or Configuration Token)
     *
     * @return
     */
    public NdefMessage createMessage() {
    return new NdefMessage(new NdefRecord(NdefRecord.TNF_MIME_MEDIA,
        WFA_P2P_CARRIER_TYPE.getBytes(), getId(), getPayload()));
    }


    @Override
    public byte[] getPayload() {
    return mPayload;
    }

    // TODO:: parse HandoverCarrier not ready
/*
    public static WfaP2pCarrierConfiguration tryParse(HandoverCarrier carrier) {

    if (carrier == null)
        return null;

    if (HandoverCarrier.HANDOVER_CARRIER_CONFIGURATION_RECORD != carrier
        .getFormat()) {
        return null;
    }

    // Only WFA P2P can pass
    if (!WFA_P2P_CARRIER_TYPE.equals(carrier.getProtocol())) {
        return null;
    }

    byte[] raw = carrier.getData();
    if (raw == null || raw.length < 3) {
        return null;
    }
    // Log.d("NfcFloat", "[raw] = "+Util.bytesToString(raw));

    WfaP2pCarrierConfiguration ccr = new WfaP2pCarrierConfiguration();
    // TODO::

    byte[] macAddress = new byte[6];
    for (int i = 0; i < 6; i++) {
        macAddress[i] = raw[7 - i];
    }
    ccr.setMacAddress(macAddress);


    return ccr;
    }
*/

    //can parse WFA P2P Record whether has HR/HS header
    public static WfaP2pCarrierConfiguration tryParse(NdefMessage tryMessage) {

        byte[] p2pDevInfoPayload = null;
        byte[] p2pDevInfoId = null;
        NdefMessage p2pDevInfoMessage = null;
        int mRecordCount = 0;

        for (NdefRecord mRecord : tryMessage.getRecords()) {
            if (mRecord != null) {

                if (mRecord.getTnf() == NdefRecord.TNF_MIME_MEDIA &&
                    Arrays.equals(mRecord.getType(), WFA_P2P_CARRIER_TYPE.getBytes())) {

                    //if(mRecordCount == 1){
                        //Log.d(TAG, " SUCCESS Pass p2p check");
                        //return;
                    //}
                    p2pDevInfoPayload = mRecord.getPayload();
                    p2pDevInfoId = mRecord.getId();
                }
                mRecordCount++;
            }
        }

        if (p2pDevInfoPayload == null || p2pDevInfoId == null) {
            Log.e(TAG, "tryParse()  p2pDevInfoPayload or p2pDevInfoId == null ");
            return null;
        }

        WfaP2pCarrierConfiguration ccr = new WfaP2pCarrierConfiguration(p2pDevInfoPayload, p2pDevInfoId);

        return ccr;
    }

    @Override
    public short getTnf() {
    return NdefRecord.TNF_MIME_MEDIA;
    }

    @Override
    public byte[] getType() {
    return mCarrierType.getBytes();
    }

    public byte[] getId() {
        return mId;
    }

    public void setId(byte[] payloadID) {
        mId = payloadID;
    }

} // end of WfaP2pCarrierConfiguration
