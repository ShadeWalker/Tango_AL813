package com.mediatek.nfc.handover;


import java.io.File;
import java.util.Arrays;


import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;


import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;

import com.mediatek.nfc.porting.*;

import android.util.Log;

//import com.android.nfc.P2pLinkManager;
import com.android.nfc.mtknfcproxy;


import com.mediatek.nfc.handoverprotocol.CarrierData.HandoverCarrierRecord;

import com.mediatek.nfc.handoverprotocol.BTCarrierConfiguration;
import com.mediatek.nfc.handoverprotocol.WifiCarrierConfiguration;
import com.mediatek.nfc.handoverprotocol.HandoverMessageElement;
import com.mediatek.nfc.handoverprotocol.HandoverMessage;

import com.mediatek.nfc.handover.IWifiP2pProxy.IFastConnectInfo;
import com.mediatek.nfc.handoverprotocol.WifiCarrierConfiguration.Credential;
import com.mediatek.nfc.handoverprotocol.WifiCarrierConfiguration.TLV;
import com.mediatek.nfc.handoverprotocol.HandoverBuilderParser;
//import com.android.nfc.handover.HandoverManager;

import java.io.UnsupportedEncodingException;

import com.mediatek.nfc.Util;

import com.mediatek.nfc.handover.MtkNfcEntry.IMtkHandoverManager;


/**
 * Manages handover of NFC to other technologies.
 */
public class MtkHandoverManager implements MtkNfcEntry.IMtkHandoverManager {

    static final String TAG = "MtkHandoverManager";
    static final boolean DBG = true;

    //public static final boolean BEAM_PLUS_SUPPORT = true;

    static final String NFC_HANDOVER_INTENT_ACTION_WFD_ACTIVE =
            "mediatek.nfc.handover.intent.action.WFD_ACTIVE";


    /** intent extra used to provide MAC addr*/
    public static final String EXTRA_NFC_WFD_MAC_ADDR =
            "mediatek.nfc.handover.intent.extra.WFD_MAC_ADDR";

    public static final String EXTRA_NFC_WFD_SSID =
            "mediatek.nfc.handover.intent.extra.WFD_SSID";

    public static final String EXTRA_NFC_WFD_NETWORK_KEY =
            "mediatek.nfc.handover.intent.extra.WFD_NETWORK_KEY";

    public static final String EXTRA_NFC_WFD_NETWORK_ID =
            "mediatek.nfc.handover.intent.extra.WFD_NETWORK_ID";

    public static final String EXTRA_NFC_WFD_AUTH_TYPE =
            "mediatek.nfc.handover.intent.extra.WFD_AUTH_TYPE";

    public static final String EXTRA_NFC_WFD_ENC_TYPE =
            "mediatek.nfc.handover.intent.extra.WFD_ENC_TYPE";

    public static final String EXTRA_NFC_WFD_VENDOR_ID =
            "mediatek.nfc.handover.intent.extra.WFD_VENDOR_ID";

    public static final String EXTRA_NFC_WFD_GC_IP =
            "mediatek.nfc.handover.intent.extra.WFD_GC_IP";

    public static final String EXTRA_NFC_WFD_GO_IP =
            "mediatek.nfc.handover.intent.extra.WFD_GO_IP";

    public static final String EXTRA_NFC_WFD_MAX_HEIGHT =
            "mediatek.nfc.handover.intent.extra.WFD_MAX_HEIGHT";

    public static final String EXTRA_NFC_WFD_MAX_WIDTH =
            "mediatek.nfc.handover.intent.extra.WFD_MAX_WIDTH";

    /** Scenario String is used on whichScenario()*/
    public static final int SCENARIO_JB_ORIGINAL     = 0; //= "com.mediatek.nfc.handover.SCENARIO_JB_ORIGINAL";
    public static final int SCENARIO_BEAMPLUS_P2P    = 1; //= "com.mediatek.nfc.handover.SCENARIO_BEAMPLUS_P2P";
    public static final int SCENARIO_WFD             = 2; //= "com.mediatek.nfc.handover.SCENARIO_WFD";
    public static final int SCENARIO_WIFI_LEGACY     = 3; //= "com.mediatek.nfc.handover.SCENARIO_WIFI_LEGACY";
    public static final int SCENARIO_HR_COLLISION    = 4; //= "com.mediatek.nfc.handover.SCENARIO_HR_COLLISION";

    public static final int SCENARIO_WFA_P2P         = 5;
    //public static final int SCENARIO_WFA_HR_COLLISION    =6;

    // values for mSendState, should the same with p2plinkmanager.java
    //static final int SEND_STATE_NOTHING_TO_SEND = 1;
    //static final int SEND_STATE_NEED_CONFIRMATION = 2;
    static final int SEND_STATE_SENDING = 3;



    final Context mContext;
    final BluetoothAdapter mBluetoothAdapter;
    //HandoverManager mHandoverManager;
    //P2pLinkManager mP2pLinkManager;

    final BeamPlusHandover mMtkWifiP2pHandover;

    WifiDisplayProxy mWifiDisplayProxy;

    public byte[] mP2pRequesterRandom;

    private static MtkHandoverManager mStaticInstance = null;

    public MtkHandoverManager(Context context  /*,HandoverManager handoverManager*/) {
        mContext = context;
        Log.i(TAG, " MtkHandoverManager Construct ");
        //if (handoverManager != null)
        //mHandoverManager = handoverManager;

        //mP2pLinkManager = p2pLinkManager;

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        mMtkWifiP2pHandover = new BeamPlusHandover(context);

        mWifiDisplayProxy = new WifiDisplayProxy(context);
    }

    public static void createSingleton(Context context /*, HandoverManager handoverManager*/) {
        if (mStaticInstance == null) {
            mStaticInstance = new MtkHandoverManager(context);
        }
    }

    public static IMtkHandoverManager getInstance() {
        return (IMtkHandoverManager) mStaticInstance;
    }



    public byte[][] genHandoverRequestAuxData(boolean WifiDisplayActive, boolean isBigFile) {

        byte[] wifiMacAddress = null;
        Log.i(TAG, "genHandoverRequestAuxData    WifiDisplayActive:" + WifiDisplayActive);

        IFastConnectInfo mDefaultIFastConnectInfo;
        mDefaultIFastConnectInfo = mMtkWifiP2pHandover.createDefaultFastConnectInfo();

        /* String AAA = "12345abcde";
         *  byte[] CCC = AAA.getBytes();
         *   CCC[0]:0x31 CCC[1]:0x32 ... CCC[9]:0x65
         */
        String strWifiMacAddress = mDefaultIFastConnectInfo.getDeviceAddress();
        Log.i(TAG, " strWifiMacAddress   :" + strWifiMacAddress);
        if (strWifiMacAddress != null)
            wifiMacAddress = Util.addressToByteArray(strWifiMacAddress); //strWifiMacAddress.getBytes();


        //int:0x112233 btyeCount:2
        //array[0]: 0x33     array[1]: 0x22
        int iWifiVendorId = mDefaultIFastConnectInfo.getVenderId();
        byte[] wifiVendorId = Util.intToByteCountArray(iWifiVendorId, (byte) 2);
        Log.i(TAG, " iWifiVendorId   :" + iWifiVendorId);

        byte[] isBigFileAry = new byte[1];
        isBigFileAry[0] = (byte) (isBigFile ? 1 : 0);

        // create HrM with HcR and Aux. In HrM, Extension TLV(Aux) is been append after HcR.
        // set Aux[][]
        if (WifiDisplayActive == true) {

            //byte[] rtspPort = null;
            byte[] rtspPort = Util.intToByteCountArray(mWifiDisplayProxy.getRtspPortNumber(), (byte) 2);

            byte[][] AuxData = {wifiMacAddress,
                            wifiVendorId,
                            //isBigFileAry,
                            rtspPort};

            return AuxData;
        }
        else {
            byte[][] AuxData = {wifiMacAddress,
                            wifiVendorId,
                            isBigFileAry};

            return AuxData;
        }


    }

    //return the status of BlueTooth , not related to enable success or not
    public boolean getBluetoothPowerState() {
        boolean powerState = mBluetoothAdapter.isEnabled();
        if (DBG) Log.d(TAG, "  getBluetoothPowerState, powerState = "  + powerState);
        return powerState;
    }

    //return the status of Wifi, not related to enable success or not
    public boolean powerUpWifi() {
        boolean powerState = mMtkWifiP2pHandover.isWifiEnabled();
        if (DBG) Log.d(TAG, "  powerUpWifi, powerState = " + powerState);
        if (!powerState) {
            mMtkWifiP2pHandover.enableWifi();
        }
        return powerState;
    }



    NdefMessage createBeamPlusRequestMessage(Uri[] uris) {
        Log.d(TAG, "    createBeamPlusRequestMessage():  ");
        mMtkWifiP2pHandover.startNfcSession();
        return packHandoverRequestMessage(uris, false);
    }


    //
    /*  WifiDisplayActive:: True :: means it's WFD request,  BT ignore
     *  P2p HrM   HR (CR+AC+AC) +  BTCCR + WiFi Aux  (2 elements,without RTSP number)
     *  WFD HrM   HR (CR+AC)    + WiFi Aux           (3 elements,with RTSP number)
    */
    synchronized NdefMessage packHandoverRequestMessage(Uri[] uris, boolean WifiDisplayActive) {
    //to check Url, check return, check synchronized
        String strBTMac = null;
        boolean bTPowerState = false;

        Log.i(TAG, "packHandoverRequestMessage    WifiDisplayActive:" + WifiDisplayActive);

        if (WifiDisplayActive == false) {
            if (mBluetoothAdapter == null) {
                Log.e(TAG, "  mBluetoothAdapter == null  error exception");
                return null;
            }

            bTPowerState = getBluetoothPowerState();
            bTPowerState = true;
            Log.e(TAG, "!!!!  set BT PowerState to ACTIVE  !!!!");
        }

        boolean wifiPowerState = powerUpWifi();

        HandoverMessage hoMessage = new HandoverMessage();

        if (WifiDisplayActive == false) {
            strBTMac = mBluetoothAdapter.getAddress(); //= "11:22:33:44:55:66";
            Log.i(TAG, "   BT Mac Address:: " + strBTMac);

            // start BT CCR pack
            BTCarrierConfiguration btCCR = new BTCarrierConfiguration(strBTMac);
            hoMessage.appendAlternativeCarrier(bTPowerState ? HandoverMessage.CARRIER_POWER_STATE_ACTIVE : HandoverMessage.CARRIER_POWER_STATE_ACTIVATING, btCCR);
        }

        // start WiFi pack
        byte[][] Aux = genHandoverRequestAuxData(WifiDisplayActive, isBigFile(uris));

        //handle getting Wifi MAC null case
        if (Aux[0] == null) {
            Log.e(TAG, "!!!! not ERROR,exception: Device doesn't open Wifi on Life Cycle, use BT instead this time");

            Log.e(TAG, "!!!! not add Wi-Fi record, use BT only");
            //return mHandoverManager.createHandoverRequestMessage();
        } else {

        Log.d(TAG, "!!!! add Wi-Fi record");
        //HandoverMessage msg_WFp2p_R = new HandoverMessage();
        HandoverCarrierRecord HcR = HandoverCarrierRecord.newInstance(WifiCarrierConfiguration.TYPE);  //application/vnd.wfa.wsc

        hoMessage.appendAlternativeCarrier(wifiPowerState ? HandoverMessage.CARRIER_POWER_STATE_ACTIVE : HandoverMessage.CARRIER_POWER_STATE_ACTIVATING, HcR, Aux);
        }

        // HrM = HrR + HcR + Aux
        // Create total HandoverRequest Message
        NdefMessage hrM = hoMessage.createHandoverRequestMessage();
        //Log.d(TAG, "HrM = " + Util.bytesToString(wfp2p_hr.toByteArray()));


        // TODO:AntiCollision
        //state = InHandoverSection //
        if (WifiDisplayActive == false)
             mP2pRequesterRandom = hoMessage.mRequesterRandom;

        Log.i(TAG, "  mP2pRequesterRandom:  " + Util.bytesToString(mP2pRequesterRandom));
        return hrM;
    }

    NdefMessage createWiFiDisplayRequestMessage(Uri[] uris) {
        Log.d(TAG, "    createWiFiDisplayRequestMessage():  ");
        return packHandoverRequestMessage(uris, true);
    }

    //Check Wifi Alternative Carrier Roeord exist or not
    /*
    *   Check Wifi CPS and BT CPS
    *
    *
    *   return ture :: choose BT
    *          False ::           Wifi
    *
    */
    boolean determineConnWay(HandoverMessageElement HandoverMsgElement) {

        Log.d(TAG, "!!  ALWAYS USE WIFI   !!");
        return false;

    }


    private byte[] parseTLV(TLV[] tlvArray, short tagName) {
        //TLV element = tlvArray[0];
        for (TLV element:tlvArray)
        {
            if (element.getTag() == tagName)
            return element.getValue();
        }

        return null;
    }

    //deal with BeamPlus P2p Handover Select Message
    //Client Get HsM response
    private void dealBeamPlusP2pHsM(Uri[] uris, NdefMessage BPhandoverMessage) {

        Log.d(TAG, "  dealBeamPlusP2pHsM "  );

        Credential mCredentialP2p = null;

        try {
            mCredentialP2p = HandoverBuilderParser.credentialFromParseP2pHsM(BPhandoverMessage);
        } catch (FormatException e) {
            Log.e(TAG, "format exception = " + e);
        }
        catch (UnsupportedEncodingException unE) {
            Log.e(TAG, "Unsupport exception = " + unE);
        }

        byte[] macAddr = mCredentialP2p.getMacAddress();
        Log.d(TAG, "macAddr = " + Util.bytesToString(macAddr));

        byte mNetworkId = mCredentialP2p.getNetworkIndex();
        Log.d(TAG, "mNetworkId = " + mNetworkId);

        short mAuthType = mCredentialP2p.getAuthenticationType();
        short mEncType = mCredentialP2p.getEncryptionType();
        String mNetworkKey = mCredentialP2p.getNetworkKey();
        String mSSID = mCredentialP2p.getSSID();

        Log.d(TAG, "mNetworkKey = " + mNetworkKey);
        Log.d(TAG, "mSSID = " + mSSID);
        Log.d(TAG, "mAuthType =" + mAuthType + "  mEncType=" + mEncType);

        Log.d(TAG, " addPrefixShortString(mAuthType)=" + Util.addPrefixShortString(mAuthType) + "  addPrefixShortString(mEncType)=" + Util.addPrefixShortString(mEncType));


        TLV[] tLVArray = mCredentialP2p.getExtensions();

        byte[] vendorId = parseTLV(tLVArray, HandoverBuilderParser.WPS_ATTRIBUTE_TYPE_VENDOR_ID);
        byte[] goIp = parseTLV(tLVArray, HandoverBuilderParser.WPS_ATTRIBUTE_TYPE_GO_IP);
        byte[] gcIp = parseTLV(tLVArray, HandoverBuilderParser.WPS_ATTRIBUTE_TYPE_GC_IP);

        boolean GoDisconnectingFlag = false;

        try {
        byte[] extraExchangeInfo = parseTLV(tLVArray, HandoverBuilderParser.WPS_ATTRIBUTE_EXTRA_INFO_CHANGE);
            GoDisconnectingFlag = (extraExchangeInfo[1]==1?true:false);
        } catch (Exception e) {
            Log.e(TAG, "NO NFC payload, GoDisconnectingFlag 0x10DO  exception:" + e);
        }


        Log.d(TAG, "vendorId = " + Util.bytesToString(vendorId));
        Log.d(TAG, "goIp = " + Util.bytesToString(goIp));
        Log.d(TAG, "gcIp = " + Util.bytesToString(gcIp));



        byte[] maxHeight = parseTLV(tLVArray, HandoverBuilderParser.WPS_ATTRIBUTE_TYPE_MAX_HEIGHT);
        byte[] maxWidth = parseTLV(tLVArray, HandoverBuilderParser.WPS_ATTRIBUTE_TYPE_MAX_WIDTH);
        if (maxHeight != null || maxWidth != null) {
            Log.d(TAG, "should null !! maxHeight = " + Util.bytesToString(maxHeight));
            Log.d(TAG, "should null !! maxWidth = " + Util.bytesToString(maxWidth));
        }


        // TODO:: Phase II     handover.startOnConnected();
        // Register a new handover transfer object
        //getOrCreateHandoverTransfer(data.device.getAddress(), false, true); //sure
        //MtkWifiP2pHandover handover = new MtkWifiP2pHandover(mContext,uris,macAddr,goIp,gcIp,vendorId);

        Log.d(TAG, "to BeamPlushandover mAuthType =" + Util.addPrefixShortString(mAuthType) + "  mEncType =" + Util.addPrefixShortString(mEncType));


        IFastConnectInfo mIDefaultFastConnectInfo = mMtkWifiP2pHandover.createDefaultFastConnectInfo();

        mIDefaultFastConnectInfo.setNetworkId((int) mNetworkId);
        mIDefaultFastConnectInfo.setSsid(mSSID);
        mIDefaultFastConnectInfo.setAuthType(Util.addPrefixShortString(mAuthType));
        mIDefaultFastConnectInfo.setEncrType(Util.addPrefixShortString(mEncType));
        mIDefaultFastConnectInfo.setPsk(mNetworkKey); //Network Key


        mIDefaultFastConnectInfo.setVenderId(Util.byteArrayToint(vendorId));

        String macAddrStr = Util.macBytesArrayToReverseString(macAddr);
        Log.d(TAG, " to BeamPlushandover macAddrStr = " + macAddrStr);
        mIDefaultFastConnectInfo.setDeviceAddress(macAddrStr); //macBytesArrayToReverseString //macAddr.toString()

        String gcIpStr = Util.ipBytesArrayToReverseString(gcIp);
        Log.d(TAG, " to BeamPlushandover gcIpStr = " + gcIpStr);
        mIDefaultFastConnectInfo.setGcIpAddress(gcIpStr); //gcIp.toString()

        String goIpStr = Util.ipBytesArrayToReverseString(goIp);
        Log.d(TAG, " to BeamPlushandover goIpStr = " + goIpStr);
        mIDefaultFastConnectInfo.setGoIpAddress(goIpStr); // goIp.toString()



        //IFastConnectInfo mIFastConnectInfo = mMtkWifiP2pHandover.getFastConnectInfo(mIDefaultFastConnectInfo);

        Log.d(TAG, "BeamPlusHandover.startBeam  [HS end]");

        //mMtkWifiP2pHandover.startBeam(mIFastConnectInfo,uris);
        mMtkWifiP2pHandover.startBeam(mIDefaultFastConnectInfo, uris, GoDisconnectingFlag);

    }

    //  *   WFD Wifi CCR (with MaxH,MaxW) HS(+AC)    + Wifi CCR (with H,W)

    /**
    *   Requester parse the Handover Select Message on Beam Plus scenario
    *   <p>
    *   It's possible to receive two type HsM.
    *   <p>
    *   1.BT CCR only(JB original)      HS(+AC)    + BT CCR
    *   2.BT Wifi CCR (P2p usage)       HS(+AC+AC) + BT CCR + Wifi CCR
    *   <p>
    *   WFD and Legacy message should not enter this funciton
    *   <p>
    *
    * @param  uris  the URIs of beamPlus files
    * @param  BPhandoverMessage  the selector response message
    * @return      null
    * @see         null
    */
    public void doBeamPlusHandover(Uri[] uris, NdefMessage BPhandoverMessage) { //throws Exception{
        //to check Url, check return

        Log.d(TAG, "  doBeamPlusHandover ");

        /*
        *       1.parse HsM
        */



        //1.1 Try Wifi AC exist or not  , if not , BT hit  JB original
        NdefRecord r = BPhandoverMessage.getRecords()[0];
        if (r.getTnf() != NdefRecord.TNF_WELL_KNOWN) {
            Log.e(TAG, "  r.getTnf() != NdefRecord.TNF_WELL_KNOWN  return; ");
            return;
        }
        if (!Arrays.equals(r.getType(), NdefRecord.RTD_HANDOVER_SELECT)) {
            Log.e(TAG, "  r.getType() != NRTD_HANDOVER_SELECT  return; ");
            return;
        }


        dealBeamPlusP2pHsM(uris, BPhandoverMessage);

/*
        //= whichScenario(tryMessage);
        HandoverMessageElement mHandoverMsgElement = new HandoverMessageElement(BPhandoverMessage);
        int mScenario = mHandoverMsgElement.getScenario();
        Log.d(TAG, "mScenario:" + mScenario);

        //because we will determine connway on selector so we judge mScenario on doBeamPlusHandover() for JB original device(use BT)
        switch(mScenario){

            case SCENARIO_BEAMPLUS_P2P:
                dealBeamPlusP2pHsM(uris,BPhandoverMessage);
                return;
            case SCENARIO_WFD:
                //return mHandoverManager.tryHandoverRequest(tryMessage);
                Log.e(TAG, " should not get WFD HandoverSelectMessage   return "  );
                return;
                //break;

            case SCENARIO_WIFI_LEGACY:
                //return dealWifiLegacyHrM(BPhandoverMessage);
                Log.e(TAG, " should not get WFL HandoverSelectMessage   return"  );
                return;

            case SCENARIO_HR_COLLISION:
                //return dealWifiLegacyHrM(BPhandoverMessage);
                Log.e(TAG, " SCENARIO_HR_COLLISION  "  );
                IFastConnectInfo mIDefaultFastConnectInfo = mMtkWifiP2pHandover.createDefaultFastConnectInfo();

                String macAddrStr = Util.macBytesArrayToString(mHandoverMsgElement.getWinnerMac());
                Log.d(TAG, "Colli.. to BeamPlushandover set Device GcIp Addr  macAddrStr = " + macAddrStr);
                mIDefaultFastConnectInfo.setDeviceAddress(macAddrStr);
                //mIDefaultFastConnectInfo.setGcIpAddress(macAddrStr);
                Log.d(TAG, "BeamPlusHandover.startBeamWhenConnected  [HS end] Collision case !!!!" );

                mMtkWifiP2pHandover.startBeamWhenConnected(mIDefaultFastConnectInfo,uris);
                return;

            default:
            case SCENARIO_JB_ORIGINAL:
                mHandoverManager.doHandoverUri(uris,BPhandoverMessage);
                return;
        }
*/
    }


    /*
    *   handle Beam Plus P2p Handover Request Message
    *
    *   this function do the following..
    *       1.Set Hr AUX to wifi
    *       2.Set hr BT mac to BT
    *       3.judge MtkWifiP2PHandover status and start trigger
    *
    */
    public NdefMessage dealBeamPlusP2pHrM(NdefMessage BPP2pHrM) {

        byte[][]auxArray = null;

        Log.i(TAG, " dealBeamPlusP2pHrM  ");

        //parse P2p HrM
        try {
            auxArray = HandoverBuilderParser.auxFromParseP2pHrM(BPP2pHrM);
        } catch (FormatException fe) {
            Log.e(TAG, " FormatException = " + fe);
        }

        // TODO:: Error Handle, Create fail Ndef  null install
        if (auxArray == null) {
            Log.e(TAG, " auxArray == null ");
            return null;
        }

        //get early to PowerUp Wi-Fi on Receive case,  KK update
        powerUpWifi();

        Log.d(TAG, " auxArray.length = " + auxArray.length + "  should be 3 ");
        if (auxArray.length != 3)
            Log.e(TAG, " auxArray.length = " + auxArray.length);

        byte[] macAddr  = auxArray[0];
        byte[] vendorId = auxArray[1];

        Log.e(TAG, "  vendorId = " + Util.bytesToString(vendorId));
        Log.e(TAG, "  rec. wifi macAddr = " + Util.bytesToString(macAddr));

        //Log.e(TAG, " To ReverseString macAddr = " + macBytesArrayToReverseString(macAddr));
        Log.e(TAG, " rec. wifi macAddr To String  = " + Util.macBytesArrayToString(macAddr));



        //Set Hr AUX to wifi
        //if (false == mMtkWifiP2pHandover.isConnecting()) {

            IFastConnectInfo mDefaultIFastConnectInfo;
            mDefaultIFastConnectInfo = mMtkWifiP2pHandover.createDefaultFastConnectInfo();

            mDefaultIFastConnectInfo.setVenderId(Util.byteArrayToint(vendorId));
            mDefaultIFastConnectInfo.setDeviceAddress(Util.macBytesArrayToString(macAddr)); //(String)macAddr.toString()
            //mIFastConnectInfo.setGcIpAddress(gcIp.toString());
            //mIFastConnectInfo.setGoIpAddress(goIp.toString());
            Log.d(TAG, " BeamPlusHandover.acceptIncomingBeam    ");

            mMtkWifiP2pHandover.acceptIncomingBeam(mDefaultIFastConnectInfo);

            return createBeamPlusP2pHsM(mDefaultIFastConnectInfo);
        //}
        //else {
        // TODO:: isConnecting return true, start on connected ,Phase II
        //    Log.e(TAG, " TODO:: BeamPlusHandover isConnecting return true  ");
        //    return null;
        //}

        //Set hr BT mac to BT
        // TODO:: Do nothing


    }

    /*
    *   Create Beam Plus P2p Handover Selcet Message
    *
    *   this function do the following..
    *       1.Get Fast Conn Info by hr's defaultFastConnInfo
    *       2.
    *
    */
    public NdefMessage createBeamPlusP2pHsM(IFastConnectInfo connInfoWithVidMAC) {
        NdefMessage p2pHsM = null;

        Log.i(TAG, " createBeamPlusP2pHsM  ");
        //Get Wiif Mac,vender,GOIP

        IFastConnectInfo mIFastConnectInfo;

        String strBTMac = mBluetoothAdapter.getAddress(); //= "11:22:33:44:55:66";

        /// R: @ {
        boolean disconnectingFlag = mMtkWifiP2pHandover.isDisconnecting();
        
        byte[] extraExchangeInfo = fillExtraExchangeInfo(disconnectingFlag);

        
        if (mMtkWifiP2pHandover.isConnected() && ( disconnectingFlag == false)) {
            if (mMtkWifiP2pHandover.isDeviceAlreadyConnected(connInfoWithVidMAC)) {
                Log.d(TAG, " ===> Already CONNECTED to the same device");
                mIFastConnectInfo = mMtkWifiP2pHandover.createDefaultFastConnectInfo();
            } else {
                Log.d(TAG, " ===> Already CONNECTED, but not this one. Ignore it.");
                return null;
            }
        } else {
            if(disconnectingFlag){
                Log.d(TAG, " ===> in Disconnecting, call fastconnectAsGo again");
            }
            else{
            Log.d(TAG, " ===> Not CONNECTED, normal case");
            }
            mIFastConnectInfo = mMtkWifiP2pHandover.getFastConnectInfo(connInfoWithVidMAC);
            mMtkWifiP2pHandover.setThisDeviceInfo(mIFastConnectInfo);
        }
        /// }

        int vendorId = mIFastConnectInfo.getVenderId();
        String macAddr = mIFastConnectInfo.getDeviceAddress();
        String gcIp = mIFastConnectInfo.getGcIpAddress();
        String goIp = mIFastConnectInfo.getGoIpAddress();


        Log.i(TAG, " NetworkId:" + mIFastConnectInfo.getNetworkId());
        Log.i(TAG, " Hs wifi macAddr:" + macAddr);

        String mSSID = mIFastConnectInfo.getSsid();

        String mAuthType = mIFastConnectInfo.getAuthType();

        String mEncrType = mIFastConnectInfo.getEncrType();

        String mNetworkKey = mIFastConnectInfo.getPsk();


        Log.i(TAG, " mSSID:" + mSSID);
        Log.i(TAG, " mNetworkKey:" + mNetworkKey);

        String mCutPrefixAuthType = Util.splitPrefixString(mAuthType);
        String mCutPrefixEncrType = Util.splitPrefixString(mEncrType);

        Log.i(TAG, " mAuthType:" + mAuthType + "   mCutPrefixAuthType:" + mCutPrefixAuthType);
        Log.i(TAG, " mEncrType:" + mEncrType + "   mCutPrefixEncrType:" + mCutPrefixEncrType);


        //pack HS
        // TODO::  Credential just use MAC, clientTable is null
        p2pHsM = HandoverBuilderParser.createP2PHsM(strBTMac,
                mNetworkKey, //String wifi_NetworkKey,
                mSSID, //String wifi_SSID,
                Short.parseShort(mCutPrefixAuthType, 16),
                Short.parseShort(mCutPrefixEncrType, 16),
                Util.addressToReverseBytes(macAddr), //macAddr.getBytes(),//byte[] wifi_MACAddress,
                Util.intToByteCountArray(vendorId, (byte) 2), //byte[] vendorID,
                Util.ipAddressToReverseBytes(goIp), //goIp.getBytes(),//byte[] GOIP,
                Util.ipAddressToReverseBytes(gcIp), //gcIp.getBytes(),//byte[] GCIP,
                null, //byte[][] clientTable);
                getBluetoothPowerState(),
                powerUpWifi(),
                extraExchangeInfo);

        if (DBG) Log.d(TAG, "  p2pHsM" + p2pHsM);
        return p2pHsM;
    }

    /**
    *   Create Specific collision Handover Select Message when Beam+ P2P case
    *   we force return one NDEF record which include the MAC address of Selector with small Collision number
    *
    * @param  null
    * @return   NdefMessage  Beam+ P2P Collision Hs Message
    * @see         null
    */
    public NdefMessage createBeamPlusP2pCollisionHsM() {
        Log.i(TAG, " createBeamPlusP2pCollisionHsM  ");

        NdefMessage result;
        IFastConnectInfo mDefaultIFastConnectInfo;
        mDefaultIFastConnectInfo = mMtkWifiP2pHandover.createDefaultFastConnectInfo();

        /* String AAA = "12345abcde";
         *  byte[] CCC = AAA.getBytes();
         *   CCC[0]:0x31 CCC[1]:0x32 ... CCC[9]:0x65
         */
        String strWifiMacAddress = mDefaultIFastConnectInfo.getDeviceAddress();
        Log.i(TAG, "selector strWifiMacAddress   :" + strWifiMacAddress);
        byte[] wifiMacAddress = Util.addressToByteArray(strWifiMacAddress); //strWifiMacAddress.getBytes();


        result = HandoverBuilderParser.createMtkSpecificHsM(HandoverMessage.SPECIFIC_RECORD_TYPE_BEAMPLUS_HANDOVER_REQUEST_COLLISION, wifiMacAddress);

       return result;

    }


    /**
    *       P2pLinkManager doGet
    *
    *       It's possible to receive three type HrM.
    *       1.BT CCR only(JB original)          HR(+AC)    + BT CCR
    *       2.BT Wifi  AUX  (P2p usage)                 HR(+AC+AC) + BT CCR + Wifi CCR(AUX only)
    *
    *       3.Wifi Legacy (WPS)                         HR(+AC)    + Wifi Type only
    *
    *       We always act as Client on WFD
    *       WFD Wifi Aux (with RTSP port)        HR(+AC)    + Wifi  Aux, (with RTSP port)
    *
    *       return null , p2pLinkManager will response SnepMessage.RESPONSE_NOT_FOUND
    */
    public NdefMessage tryMtkHandoverRequest(NdefMessage tryMessage) {
        NdefMessage result = null;
        try {
            result = tryMtkHandoverRequestImpl(tryMessage);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return result;
    }

    private NdefMessage tryMtkHandoverRequestImpl(NdefMessage tryMessage) {
        //to check Url, check return
        NdefMessage p2pHsM = null;
        boolean mWifiDisplayCase = true;

        int NfcSendState;


        if (tryMessage == null) return null;
        //if (mBluetoothAdapter == null) return null;

        NfcSendState = mtknfcproxy.getP2pSendState();

        //byte[] tryMessageByteArray = tryMessage.toByteArray();
        if (DBG) Log.d(TAG, "tryMtkHandoverRequest():  NfcSendState:" + NfcSendState);
        Log.d(TAG, "  tryMessageByteArray " + Util.printNdef(tryMessage)); //length:"+ tryMessageByteArray.length + " Array::" + Util.bytesToString(tryMessageByteArray));

        /*
        *       1.parse HrM
        */

        //1.1 Try Wifi AC exist or not  , if not , BT hit  JB original
        NdefRecord r = tryMessage.getRecords()[0];
        if (r.getTnf() != NdefRecord.TNF_WELL_KNOWN) return null;
        if (!Arrays.equals(r.getType(), NdefRecord.RTD_HANDOVER_REQUEST)) return null;


        //= whichScenario(tryMessage);
        HandoverMessageElement mHandoverMsgElement = new HandoverMessageElement(tryMessage);
        int mScenario = mHandoverMsgElement.getScenario();
        Log.d(TAG, "mScenario:" + mScenario);

        switch(mScenario) {

            case SCENARIO_BEAMPLUS_P2P:
                if (!determineConnWay(mHandoverMsgElement)){

                        //AntiCollision ,compare Random number
                        if ((NfcSendState == SEND_STATE_SENDING) && actAsRequester(mP2pRequesterRandom, mHandoverMsgElement.getCRArray())) {
                            Log.d(TAG, "P2pLink actAsRequester return True !!!!    p2pHsM set to Specific record  return!!!!");
                            p2pHsM = createBeamPlusP2pCollisionHsM();
                        }
                        else
                            p2pHsM = dealBeamPlusP2pHrM(tryMessage);

                        return p2pHsM;
                }


            case SCENARIO_WFD:
                Log.d(TAG, " should not get WFD HandoverRequestMessage   return null");
                return null;

            case SCENARIO_WIFI_LEGACY:

            case SCENARIO_WFA_P2P:

                Log.d(TAG, "WIFI_LEGACY or WFA_P2P ,return null");

                return null;

            default:
            case SCENARIO_JB_ORIGINAL:
                
                Log.d(TAG, "should not enter, exception SCENARIO_JB_ORIGINAL");
                return null;//mHandoverManager.tryHandoverRequest(tryMessage);
        }


    }


    /**
        *   The device with big Collision number will act as Requester
        *       only less will return false
        *
        * @param  SendRandomNumber          the RandomNumber We send
        * @param  RecieveRandomNumber       the RecieveRandomNumber we receive
        * @returrn boolean  true,act as Requester
        * @see         null
        * @hide
        */
    boolean actAsRequester(byte[] SendRandomNumber, byte[] ReceiveRandomNumber) {
        int mSendNumber = Util.byteArrayToint(SendRandomNumber);
        int mReceiveNumber = Util.byteArrayToint(ReceiveRandomNumber);

        Log.i(TAG, " actAsRequester  mSendNumber:" + mSendNumber + "   mReceiveNumber:" + mReceiveNumber);

        Log.i(TAG, " actAsRequester  Return :" + (mSendNumber >= mReceiveNumber));

        return (mSendNumber >= mReceiveNumber) ? true : false;
    }
/*
    private String getFilePathByContentUri(Uri uri) {
        Log.d(TAG, "getFilePathByContentUri(), uri.toString() = " + uri.toString());
        Uri filePathUri = uri;
        if (uri.getScheme().toString().compareTo("content")==0) {
            Cursor cursor = null;
            try {
                cursor = mContext.getContentResolver().query(uri, null, null, null, null);
                if (cursor.moveToFirst()) {
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);//Instead of "MediaStore.Images.Media.DATA" can be used "_data"
                    filePathUri = Uri.parse(cursor.getString(column_index));
                    Log.d(TAG, "getFilePathByContentUri : " + filePathUri.getPath());
                    return filePathUri.getPath();
                }
            } catch (Exception e) {
                Log.d(TAG, "exception...");
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        Log.d(TAG, "getFilePathByContentUri doesn't work, try direct getPath");
        return uri.getPath();
    }
*/
    //if File > 1M  return true
    boolean isBigFile(Uri[] Uris) {
      int sizeSummary = 0;

      if (Uris == null)
        return false;

      for (int i = 0; i < Uris.length; i++) {
       File file = new File(Util.getFilePathByContentUri(TAG, mContext, Uris[i]));
       Log.d(TAG, "isBigFile::  size of file X = " + file.length());

       sizeSummary = (int) (sizeSummary + file.length());
      }
        //1024*1024=1048576
      if (sizeSummary > 1048576) {
       return true;
      }
      else
       return false;
    }// end of isBigFile

    private void sendWFDActiveBroadcast(byte[] macAddr, String NetworkKey, String SSID, byte NetworkId , short AuthType, short EncType, byte[] vendorId, byte[] gcIp, byte[] goIp, byte[] maxHeight, byte[] maxWidth) {
        // Deal with handover-initiated transfers separately
        Intent handoverIntent = new Intent(NFC_HANDOVER_INTENT_ACTION_WFD_ACTIVE);

        // TODO:: PutExtra : mac,vendorId,GOIP,GCIP,max Width Height,Client (white)table
        handoverIntent.putExtra(EXTRA_NFC_WFD_MAC_ADDR, macAddr);
        handoverIntent.putExtra(EXTRA_NFC_WFD_SSID, SSID);
        handoverIntent.putExtra(EXTRA_NFC_WFD_NETWORK_KEY, NetworkKey);
        handoverIntent.putExtra(EXTRA_NFC_WFD_NETWORK_ID, NetworkId);
        handoverIntent.putExtra(EXTRA_NFC_WFD_AUTH_TYPE, AuthType);
        handoverIntent.putExtra(EXTRA_NFC_WFD_ENC_TYPE, EncType);


        handoverIntent.putExtra(EXTRA_NFC_WFD_VENDOR_ID, vendorId);

        handoverIntent.putExtra(EXTRA_NFC_WFD_GC_IP, gcIp);
        handoverIntent.putExtra(EXTRA_NFC_WFD_GO_IP, goIp);
        handoverIntent.putExtra(EXTRA_NFC_WFD_MAX_HEIGHT, maxHeight);
        handoverIntent.putExtra(EXTRA_NFC_WFD_MAX_WIDTH, maxWidth);

        Log.d(TAG, " sendWFDActiveBroadcast  sendBroadcast");

        mContext.sendBroadcast(handoverIntent);
        return;
    }

    public NdefMessage CreateHrMEntry(Uri[] uris) {
        Log.d(TAG, "    CreateHrMEntry()");
        NdefMessage request = null;
        try {
            request = createBeamPlusRequestMessage(uris);
            Log.d(TAG, "    HrM:" + Util.printNdef(request));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return request;
    }

    /**
        *   Requester deal Handover select Message Entry.
        *       it will dispatch to correct doHandover function
        *   <p>
        *   <p>
        *
        * @param
        * @param
        * @return      null
        * @see         null
        */
    public void doHsMHandoverEntry(Uri[] uris, NdefMessage response) {
        Log.d(TAG, "    doHsMEntry()");
        Log.d(TAG, "    responseByteArray " + Util.printNdef(response));
        doBeamPlusHandover(uris, response);
    }



    /**
    *   notifyHandoverComplete.
    *   <p>
    *   <p>
    *
    * @param  null
    * @return      null
    * @see         null
    */
    public void notifyHandoverComplete(){

    }

    public final byte EXTRA_INFO_VERSION     = 1;
    public final byte VERSION_1_LENGTH       = 2;




    /**
    *   Fill extra info array.
    *   <p>
    *   <p>
    *
    * @param  null
    * @return      null
    * @see         null
    */
    public byte[] fillExtraExchangeInfo(boolean disconnectingFlag){
        byte[] extraExchangeInfo = new byte[VERSION_1_LENGTH];
        
        extraExchangeInfo[0]    =   EXTRA_INFO_VERSION;
        extraExchangeInfo[1]    =   (byte)(disconnectingFlag?1:0);
        return extraExchangeInfo;
    }




}

