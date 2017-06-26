package com.mediatek.nfc.handoverprotocol;


//import java.io.File;
//import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
//import java.nio.charset.Charset;
//import java.text.SimpleDateFormat;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
//import java.util.Date;
//import java.util.HashMap;
import java.util.Iterator;
//import java.util.Map;
//import java.util.Random;

//import android.app.Activity;
//import android.app.PendingIntent;
//import android.app.PendingIntent.CanceledException;
//import android.app.Notification;
//import android.app.NotificationManager;
//import android.app.PendingIntent;
//import android.app.Notification.Builder;
//import android.bluetooth.BluetoothA2dp;
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.BluetoothDevice;
//import android.bluetooth.BluetoothHeadset;
//import android.bluetooth.BluetoothProfile;
//import android.content.BroadcastReceiver;
//import android.content.ContentResolver;
//import android.content.Context;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.media.MediaScannerConnection;
//import android.net.Uri;


//import android.nfc.NfcAdapter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
//import android.os.Environment;
//import android.os.Handler;
//import android.os.Message;
//import android.os.Parcelable;
//import android.os.SystemClock;
import android.util.Log;
//import android.util.Pair;



//import com.mediatek.nfc.handover.CarrierData.HandoverCarrierRecord;
//import com.mediatek.nfc.handover.HandoverMessage.HandoverCarrier;
//import com.mediatek.nfc.handover.HandoverMessage.HandoverRequest;
//import com.mediatek.nfc.handover.HandoverMessage.HandoverSelect;
//import com.mediatek.nfc.handover.IWifiP2pProxy.IFastConnectInfo;
//import com.mediatek.nfc.handover.WifiCarrierConfiguration.Credential;
//import com.mediatek.nfc.handover.WifiCarrierConfiguration.TLV;
// TODO::  IWifiP2pProxy
//import com.mediatek.nfc.handover.testWifiProxy;
//import com.mediatek.nfc.handover.IWifiP2pProxy;

//import com.mediatek.nfc.handover.MtkWifiP2pHandover;
//import com.mediatek.nfc.handover.HandoverBuilderParser;
//import com.android.nfc.handover.HandoverManager;





//import java.io.UnsupportedEncodingException;

import com.mediatek.nfc.Util;

//import com.mediatek.nfc.handover.BeamPlusHandover;
//import com.mediatek.nfc.handover.WifiP2pProxy;
//import com.mediatek.nfc.wps.INfcWpsAppInternal;
//import com.mediatek.nfc.wps.INfcWpsTestBed;
//import com.mediatek.nfc.wps.NfcForegroundDispatchActivity;
//import com.mediatek.nfc.wps.WpsCredential;

//import com.mediatek.nfc.wfa.NfcWfaHandoverManager;
//import com.mediatek.nfc.wfa.INfcWfaAppInternal;


        /**
     *  this class is used to set common element on HR/HS
     */
    public class HandoverMessageElement {

        static final String TAG = "HandoverMessageElement";

        static final String TYPE_MTK_L_PROPRIETARY = "vendor_proprietary";


        /** Scenario String is used on whichScenario()*/
        public static final int SCENARIO_JB_ORIGINAL     = 0; //= "com.mediatek.nfc.handover.SCENARIO_JB_ORIGINAL";
        public static final int SCENARIO_BEAMPLUS_P2P    = 1; //= "com.mediatek.nfc.handover.SCENARIO_BEAMPLUS_P2P";
        public static final int SCENARIO_WFD             = 2; //= "com.mediatek.nfc.handover.SCENARIO_WFD";
        public static final int SCENARIO_WIFI_LEGACY     = 3; //= "com.mediatek.nfc.handover.SCENARIO_WIFI_LEGACY";
        public static final int SCENARIO_HR_COLLISION    = 4; //= "com.mediatek.nfc.handover.SCENARIO_HR_COLLISION";

        public static final int SCENARIO_WFA_P2P         = 5;
        //public static final int SCENARIO_WFA_HR_COLLISION    =6;
        public static final int SCENARIO_MTK_L_WIFI_BEAM = 7;

        // wfa.p2p CARRIER_TYPE
        public static final String WFA_P2P_CARRIER_TYPE = "application/vnd.wfa.p2p";

          public static final short WPS_ATTRIBUTE_TYPE_VENDOR_ID    = 0x5001;
        // Carrier type name for BT
          public static final String BT_CARRIER_TYPE = "application/vnd.bluetooth.ep.oob";

        static final byte CARRIER_DATA_REF_LENGTH = 1;
        static final boolean HME_BDG = true;

        //static final String TAG = "Credential";
        byte[] mHandoverType; //Hr or Hs

        //CR related
        byte[] mCRArray;

        //AC related
        byte[] mAcCpsArray;
        byte[][] mAcPayloadArray;
        byte mAcCount;
        byte mWifiCPS;
        byte mBtCPS;
        byte mAuxDataCount;

        //Specific related
        byte[] mWinnerMac;

        //All related
        byte mRecordCount;
        int mscenario;

        byte mIsBigFile;


        ArrayList<Byte> mCpsArrayList;

        ArrayList<byte[]> mAcPayloadArrayList;

        List<Byte> mTotalAcPayloadList = new ArrayList<Byte>();

        byte btAcCount = 0;
        byte wscAcCount = 0;
        byte wscInHcCount = 0;
        byte octAuxCount = 0;
        boolean withMtkVendorId = false;
        byte p2pAcCount = 0;
        byte mtkProprietaryCount = 0;

        //constructor
        public HandoverMessageElement(NdefMessage m) {

            try {
                ParsedMessage(m);
            } catch (FormatException fe) {
                Log.e(TAG, "    FormatException fe:" + fe);
                fe.printStackTrace();
            } catch (Exception e) {
                Log.e(TAG, "    Exception e:" + e);
                e.printStackTrace();
            }


            dump();
        }

        void ParsedMessage(NdefMessage m)throws FormatException {
            Log.d(TAG, "HandoverMessageElement.ParsedMesssage(remove static)");

            NdefRecord r0 = m.getRecords()[0];
            short r0Tnf = r0.getTnf();
            mHandoverType = r0.getType();
            byte[] r0Payload = r0.getPayload();
            mAcCount = 0;
            mRecordCount = 0;

            byte version = r0Payload[0];
            byte[] acMessageBytes = new byte[r0Payload.length - 1];
            System.arraycopy(r0Payload, 1, acMessageBytes, 0, r0Payload.length - 1);
            r0Payload = null;



            NdefMessage ac = null;

            try {
                ac = new NdefMessage(acMessageBytes);
            } catch (FormatException E) {
                Log.e(TAG, "    FormatException " + E);
            }
            NdefRecord[] recordItems = ac.getRecords();

            //parse record (cr,ac)  of HR/HS
            for (NdefRecord mRecord : recordItems) {

                if (mRecord.getTnf() != NdefRecord.TNF_WELL_KNOWN) {
                    Log.e(TAG, "    NdefRecord.tnf  not match ");
                    //handle by JB original
                    //return SCENARIO_JB_ORIGINAL;
                    throw new FormatException(" record in HR/HS  TNF not match:" + mRecord.getTnf());
                    //continue;
                }

                if (Arrays.equals(HandoverMessage.COLLISION_RESOLUTION_RECORD_TYPE, mRecord.getType())) {
                    mCRArray = mRecord.getPayload();
                    continue;
                }

                if (Arrays.equals(HandoverMessage.SPECIFIC_RECORD_TYPE, mRecord.getType())) {
                    byte[] specArray = mRecord.getPayload();
                    //array[0] :: error reason
                    //array[1] :: error data
                    if (specArray[0] == HandoverMessage.SPECIFIC_RECORD_TYPE_BEAMPLUS_HANDOVER_REQUEST_COLLISION) {
                        //mHrCollision = true;
                        mscenario = SCENARIO_HR_COLLISION;
                        mWinnerMac = new byte[specArray.length - 1];
                        System.arraycopy(specArray, 1, mWinnerMac, 0, specArray.length - 1);
                        Log.e(TAG, "  Handover Request Collision Happen");
                    }
                    else if (specArray[0] == HandoverMessage.SPECIFIC_RECORD_TYPE_WFA_HANDOVER_REQUEST_COLLISION) {
                        Log.e(TAG, "  WFA P2p Handover Request Collision Happen");
                        mscenario = SCENARIO_HR_COLLISION;
                    }
                    else
                        Log.e(TAG, " SPECIFIC_RECORD_TYPE exception error case");

                    return;
                }


                if (Arrays.equals(NdefRecord.RTD_ALTERNATIVE_CARRIER, mRecord.getType())) {
                    byte[] mAcRecord = mRecord.getPayload();
                    int cursor = 0;
                    byte acLength = (byte) mAcRecord.length;
                    byte carrierDataLength = 0;

                    Log.i(TAG, "    AC Record  payload count:" + ((acLength - 2) / 2) + "  acLength:" + acLength);

                    ByteBuffer acPayload = ByteBuffer.allocate((acLength - 2) / 2); //-2 :: means cps 1byte, AuxRefCount 1byte,


                    if (mCpsArrayList == null)
                        mCpsArrayList = new ArrayList<Byte>();

                    mCpsArrayList.add(mAcRecord[cursor++]);

                    //mAcCpsArray[mAcCount] = mAcRecord[cursor++];

                    carrierDataLength = mAcRecord[cursor++];
                    if (carrierDataLength != CARRIER_DATA_REF_LENGTH)
                        throw new FormatException(" CARRIER_DATA_REF_LENGTH !=1  value:" + carrierDataLength);

                    acPayload.put(mAcRecord[cursor++]);

                    mAuxDataCount = mAcRecord[cursor++];

                    //Log.i(TAG, "   cursor should be 4    cursor:"+cursor );

                    while (cursor < acLength) {
                        carrierDataLength = mAcRecord[cursor++];
                        if (carrierDataLength != CARRIER_DATA_REF_LENGTH)
                            throw new FormatException(" CARRIER_DATA_REF_LENGTH !=1  value:" + carrierDataLength);

                        acPayload.put(mAcRecord[cursor++]);
                    }

                    if (mAcPayloadArrayList == null)
                        mAcPayloadArrayList = new ArrayList<byte[]>();

                    mAcPayloadArrayList.add(acPayload.array());
                    //mAcPayloadArray[mAcCount]=acPayload.array();

                    mAcCount++;
                    Log.i(TAG, "    mAcCount:" + mAcCount);

                }
             }

            int cpsSize = mCpsArrayList.size();
            if (cpsSize == 1)
            Log.i(TAG, "    mCpsArrayList.size():" + cpsSize + "(==1)  [0]:" + mCpsArrayList.get(0));
            else if (cpsSize >= 2)
            Log.i(TAG, "    mCpsArrayList.size():" + cpsSize + "  [0]:" + mCpsArrayList.get(0) + "   [1]:" + mCpsArrayList.get(1));

            mAcCpsArray = new byte[cpsSize];
            mAcPayloadArray = new byte[cpsSize][];

            int i, j;
            for (i = 0; i < cpsSize; i++) {
                mAcCpsArray[i] = mCpsArrayList.get(i);
                byte[] test = (byte[]) mAcPayloadArrayList.get(i);
                Log.i(TAG, "    mAcPayload:" + Util.bytesToString(test));
                for (j = 0; j < test.length; j++) {
                    mTotalAcPayloadList.add(test[j]);
                }
                mAcPayloadArray[i] = test;
            }

              Log.i(TAG, "    mAcCpsArray:" + Util.bytesToString(mAcCpsArray));

              for (NdefRecord mRecord : m.getRecords()) {
                  if (mRecord != null) {

                      mRecordCount++;

                      if (mRecord.getTnf() == NdefRecord.TNF_MIME_MEDIA && Arrays.equals(mRecord.getType(), HandoverMessage.OCTET_STREAM) &&
                          Arrays.equals(mHandoverType, NdefRecord.RTD_HANDOVER_REQUEST) && mRecordCount == 6) {
                           byte[] mdata = mRecord.getPayload();

                           if (mdata.length != 1)
                            throw new FormatException("mRecordCount:" + mRecordCount + "  IsBigFile not match : " + Util.bytesToString(mdata));

                           mIsBigFile = mdata[0];
                           Log.i(TAG, " mRecordCount:" + mRecordCount + "    mIsBigFile assign:" + mIsBigFile);

                      }

                      countRecordType(mRecord);

                  }

              }



             switch(mAcCount) {
                 case 2:

                    if (btAcCount == 1 &&
                        wscInHcCount == 1 &&
                        octAuxCount == 3) {

                        mscenario = SCENARIO_BEAMPLUS_P2P;
                        mBtCPS = mAcCpsArray[0];
                        mWifiCPS = mAcCpsArray[1];
                        Log.i(TAG, "Request Beam+   mAcCpsArray:" + Util.bytesToString(mAcCpsArray));
                        break;
                    }

                    if (btAcCount == 1 &&
                        wscAcCount == 1 &&
                        withMtkVendorId == true &&
                        Arrays.equals(mHandoverType, NdefRecord.RTD_HANDOVER_SELECT)) {

                        mscenario = SCENARIO_BEAMPLUS_P2P;
                        mBtCPS = mAcCpsArray[0];
                        mWifiCPS = mAcCpsArray[1];
                        Log.i(TAG, "Select Beam+   mAcCpsArray:" + Util.bytesToString(mAcCpsArray));
                        break;
                    }

                    if (btAcCount == 1 &&
                        p2pAcCount  == 1 &&
                        mtkProprietaryCount  == 1) {
                        mscenario = SCENARIO_MTK_L_WIFI_BEAM;
                        // TODO:: cps not assign, should be ok
                        break;
                    }


                        Log.i(TAG, "!!!! AcCount = 2 Unknown NDEF message  !!!!  set to JB_ORIGINAL");
                        mscenario = SCENARIO_JB_ORIGINAL;

                     //return SCENARIO_BEAMPLUS_P2P;
                     break;
                 case 1:
                     //BT, WFD , Legacy
                     NdefRecord r1 = m.getRecords()[1];
                     short r1TNF = r1.getTnf();
                     byte[] r1Type = r1.getType();
                     byte[] r1Payload = r1.getPayload();

                     if (r1TNF == NdefRecord.TNF_MIME_MEDIA && Arrays.equals(r1Type, BT_CARRIER_TYPE.getBytes())) {
                            mscenario = SCENARIO_JB_ORIGINAL;
                            mBtCPS = mAcCpsArray[0];
                            break;
                         //return SCENARIO_JB_ORIGINAL;
                     }


                     Log.i(TAG, "    mRecordCount:" + mRecordCount);
/*
                     if (mMode != MANAGER_MODE_WFA_CERTIFICATION) {
                         Log.e(TAG, " mMode != MANAGER_MODE_WFA_CERTIFICATION , handle by  SCENARIO_JB_ORIGINAL  mAcCount== 1" );
                         // TODO::  not consider WFD scenario
                         mscenario = SCENARIO_JB_ORIGINAL;
                         break;
                     }
*/

                     if (r1TNF == NdefRecord.TNF_MIME_MEDIA && Arrays.equals(r1Type, WifiCarrierConfiguration.TYPE.getBytes()) &&
                         Arrays.equals(mHandoverType, NdefRecord.RTD_HANDOVER_REQUEST) && mRecordCount == 2) {
                         //Legacy Hr case only, (Hr(cr+ac)+HCR),
                         //v2.0.21 r6 change
                         Log.e(TAG, " Hr case   SCENARIO_WIFI_LEGACY ");
                         mscenario = SCENARIO_WIFI_LEGACY;
                         mWifiCPS = mAcCpsArray[0];
                         break;
                     }

                     if (r1TNF == NdefRecord.TNF_MIME_MEDIA && Arrays.equals(r1Type, WifiCarrierConfiguration.TYPE.getBytes()) &&
                         Arrays.equals(mHandoverType, NdefRecord.RTD_HANDOVER_SELECT)) {
                         //WFD Hs    : Hr(cr+ac)+wifiCCR with TLV
                         //Legacy Hs : Hr(cr+ac)+wifiCCR


                         if (elementExistInTLVByteArray(r1Payload, WPS_ATTRIBUTE_TYPE_VENDOR_ID)) {
                            mscenario = SCENARIO_WFD;
                            Log.i(TAG, "    SCENARIO_WFD ");
                         }
                         else {
                            Log.i(TAG, "    SCENARIO_WIFI_LEGACY ");
                            mscenario = SCENARIO_WIFI_LEGACY;
                         }

                         mWifiCPS = mAcCpsArray[0];

                         break;
                     }

                        //WFA P2p case, parse HR case
                     if (r1TNF == NdefRecord.TNF_MIME_MEDIA && Arrays.equals(r1Type, WFA_P2P_CARRIER_TYPE.getBytes())) {
                        //&& mRecordCount==2

                         Log.e(TAG, "    SCENARIO_WFA_P2P ");
                         mscenario = SCENARIO_WFA_P2P;
                         mWifiCPS = mAcCpsArray[0];
                         break;
                     }

                     Log.e(TAG, " Error case handle by  SCENARIO_JB_ORIGINAL  mAcCount== 1");
                     mscenario = SCENARIO_JB_ORIGINAL;
                     break;

                 case 0:
                 default:
                    Log.e(TAG, " Error case -- handle by  SCENARIO_JB_ORIGINAL  mAcCount == 0");
                     mscenario =  SCENARIO_JB_ORIGINAL;
                     break;

             }
        }

    static boolean elementExistInTLVByteArray(byte[] tlvData, short tagName) {


        int cursor = 0;
        short innerTag = 0;
        int innerLen = 0;
        int dataLen = tlvData.length;

        while (cursor < dataLen) {
            innerTag = (short) (tlvData[cursor++] & 0xFF);
            innerTag = (short) ((innerTag << 8) | (tlvData[cursor++] & 0xFF));

            innerLen = tlvData[cursor++] & 0xFF;
            innerLen = (innerLen << 8) | (tlvData[cursor++] & 0xFF);

            Log.i(TAG, "    innerTag:" + innerTag + "  innerLen:" + innerLen + "  cursor:" + cursor);

            if (innerTag == tagName)
                return true;

            if (innerTag == WifiCarrierConfiguration.WPS_ATTRIBUTE_TYPE_CREDENTIAL) {
                //cursor++;
            }
            else {
                cursor = cursor + innerLen;
            }
        }

        Log.i(TAG, " dataLen:" + dataLen + "     cursor:" + cursor + "   return false!!");

        return false;
    }

        void dump() {
            int i = 0;

            if (HME_BDG) {
                Log.i(TAG, "  mHandoverType:" + Util.bytesToString(mHandoverType));

                Log.i(TAG, "  mCRArray:" + Util.bytesToString(mCRArray));

                Log.i(TAG, "  mAcCpsArray:" + Util.bytesToString(mAcCpsArray));

                for (i = 0; i < mAcCount; i++)
                    Log.i(TAG, "  mAcPayloadArray[" + i + "]:" + Util.bytesToString(mAcPayloadArray[i]));

                Log.i(TAG, "  mAcCount:" + mAcCount);
                Log.i(TAG, "  mBtCPS:" + mBtCPS);
                Log.i(TAG, "  mWifiCPS:" + mWifiCPS);
                Log.i(TAG, "  mRecordCount:" + mRecordCount);
                Log.i(TAG, "  mscenario:" + mscenario + "  0:JB_Ori  1:P2P  2:WFD  3:WL  5:WFA.P2P");

            }
        }


        void countRecordType(NdefRecord mRecord) {

            Iterator itr = mTotalAcPayloadList.iterator();


            while (itr.hasNext()) {
                byte element = (Byte) itr.next();
                //Log.d(TAG, "element:"+element);
                if (mRecord.getId().length != 1) {
                    return;
                }

                byte[] id = mRecord.getId();
                if (element != id[0]) {
                    continue;
                }

                Log.d(TAG, "remove Element:" + Integer.toHexString(element));
                itr.remove();

                if (mRecord.getTnf() == NdefRecord.TNF_MIME_MEDIA &&
                  Arrays.equals(mRecord.getType(), BT_CARRIER_TYPE.getBytes())) {
                  btAcCount++;
                }

                //wsc in HC record payload
                if (mRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN &&
                  Arrays.equals(mRecord.getType(), NdefRecord.RTD_HANDOVER_CARRIER)) {

                  byte[] payload = mRecord.getPayload();
                  if (payload.length > 3) {
                      if (Arrays.equals(Arrays.copyOfRange(payload, 2, payload.length), WifiCarrierConfiguration.TYPE.getBytes())) {
                          wscInHcCount++;
                      }

                  }
                }

                if (mRecord.getTnf() == NdefRecord.TNF_MIME_MEDIA && Arrays.equals(mRecord.getType(), WifiCarrierConfiguration.TYPE.getBytes())) {
                    wscAcCount++;

                    if (elementExistInTLVByteArray(mRecord.getPayload(), WPS_ATTRIBUTE_TYPE_VENDOR_ID)) {
                        withMtkVendorId = true;
                    }
                }



                if (mRecord.getTnf() == NdefRecord.TNF_MIME_MEDIA && Arrays.equals(mRecord.getType(), HandoverMessage.OCTET_STREAM)) {
                    octAuxCount++;
                }

                if (mRecord.getTnf() == NdefRecord.TNF_MIME_MEDIA && Arrays.equals(mRecord.getType(), WFA_P2P_CARRIER_TYPE.getBytes())) {
                    p2pAcCount++;
                }

                if (mRecord.getTnf() == NdefRecord.TNF_MIME_MEDIA && Arrays.equals(mRecord.getType(), TYPE_MTK_L_PROPRIETARY.getBytes())) {
                    mtkProprietaryCount++;
                }

            }

            Log.d(TAG, "btAcCount:" + btAcCount + " wscInHcCount:" + wscInHcCount + " wscAcCount:" + wscAcCount + " p2pAcCount:" + p2pAcCount + " octAuxCount:" + octAuxCount + "  mtkProprietaryCount:" + mtkProprietaryCount + " withMtkVendorId:" + withMtkVendorId);
        }

    public int getScenario() {
        return mscenario;
    }
    public byte[] getWinnerMac() {
        return mWinnerMac;
    }
    public byte[] getCRArray() {
        return mCRArray;
    }

    }
