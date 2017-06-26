package com.mediatek.ims.internal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import com.mediatek.ims.ImsAdapter;
import com.mediatek.ims.ImsAdapter.VaSocketIO;
import com.mediatek.ims.ImsAdapter.VaEvent;
import com.mediatek.ims.ImsEventDispatcher;
import com.mediatek.ims.VaConstants;
import com.mediatek.xlog.Xlog;


import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;


import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;

import android.net.LinkAddress;
import android.os.IBinder;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.AsyncResult;
import android.os.ServiceManager;
import android.os.INetworkManagementService;
import android.os.RemoteException;

import android.net.MobileDataStateTracker;
import android.telephony.gsm.GsmCellLocation;
import android.telephony.TelephonyManager;

//import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;

//import com.android.internal.telephony.PhoneFactory;
//import com.android.internal.telephony.dataconnection.DataConnection;
import com.android.internal.telephony.DctConstants;
import android.telephony.SubscriptionManager;
import com.mediatek.internal.telephony.DedicateBearerProperties;
import com.mediatek.internal.telephony.DefaultBearerConfig;
import com.mediatek.internal.telephony.PcscfInfo;
import com.mediatek.internal.telephony.QosStatus;
import com.mediatek.internal.telephony.TftStatus;
import com.mediatek.internal.telephony.PacketFilterInfo;
import com.mediatek.internal.telephony.TftParameter;
import com.mediatek.internal.telephony.TftAuthToken;
import com.mediatek.internal.telephony.PcscfAddr;
import com.mediatek.internal.telephony.ITelephonyEx;

import android.telephony.CellIdentityLte;

//import com.android.ims.IImsManagerService;

public class DataDispatcherUtil {
    protected static final String TAG = "GSM";

    static final int IMC_MAX_PACKET_FILTER_NUM = 16;
    static final int IMC_MAX_REMOTE_ADDR_AND_MASK_LEN = 32;
    static final int IMC_MAX_AUTHTOKEN_FLOWID_NUM = 4;
    static final int IMC_MAX_AUTHORIZATION_TOKEN_LEN = 16;
    static final int IMC_MAX_FLOW_IDENTIFIER_NUM = 4;
    static final int IMC_MAX_CONCATENATED_NUM = 11;
    static final int IMC_MAXIMUM_NW_IF_NAME_STRING_SIZE = 100;
    static final int IMC_PCSCF_MAX_NUM = 10;
    static final int IMC_IPV4_ADDR_LEN = 0x04;
    static final int IMC_IPV6_ADDR_LEN = 0x10;
    private static final int IMC_CONCATENATED_MSG_TYPE_NONE = 0;
    private static final int IMC_CONCATENATED_MSG_TYPE_ACTIVATION = 1;
    private static final int IMC_CONCATENATED_MSG_TYPE_MODIFICATION = 2;
    private static final int IMC_CONCATENATED_MSG_TYPE_DELETION = 3;
    
    private static final int PDP_ADDR_TYPE_NONE = 0x0;
    private static final int PDP_ADDR_TYPE_IPV4 = 0x21;
    private static final int PDP_ADDR_TYPE_IPV6 = 0x57;
    private static final int PDP_ADDR_TYPE_IPV4v6 = 0x8D;
    private static final int PDP_ADDR_TYPE_NULL = 0x03;
    /*WFC*/
    static final int IMC_ACCESS_RAT_LTE = 1;
    static final int IMC_ACCESS_RAT_WIFI = 2; 
    private static final int INVALID_CID = -1;

    private static int RAT_TYPE_3G_FDD = 1;
    private static int RAT_TYPE_3G_TDD = 2;
    private static int RAT_TYPE_LTE_FDD = 3;
    private static int RAT_TYPE_LTE_TDD = 4;
    private static int RAT_TYPE_WIFI = 5;
    final int TRUE = 1;
    final int FALSE = 0;
    private static final String RIL_RAT_DETAIL = "ril.nw.rat.detail";

    static final boolean DBG = true;
    public static DedicateBearerProperties mWifiBearerProp = null;
    private static List<DedicateBearerProperties> mConnectedVoLTEDedicateBearerProp = new ArrayList <DedicateBearerProperties>(); 
    private static int sCid= INVALID_CID;
    private boolean mIsBearerEmergency = false;
    private static final boolean WFC_FEATURE = SystemProperties.get("ro.mtk_wfc_support")
                                                            .equals("1") ? true : false;

    public DataDispatcherUtil() {
    }

    static DedicateBearerProperties readDedicateBearer(VaEvent event) {
        //imcf_uint8 context_id
        //imcf_uint8 primary_context_id
        //imcf_uint8 ebi
        //imcf_uint8 qos_mod
        //imc_eps_qos_struct nw_assigned_eps_qos
        //imc_concatenated_msg_type_enum msg_type
        //imcf_uint8 tft_mod
        //imcf_uint8 signaling_flag
        //imcf_uint8 pcscf_mod
        //imc_tft_info_struct nw_assigned_tft
        //imc_pcscf_list_struct pcscf_list

        DedicateBearerProperties property = new DedicateBearerProperties();
        property.cid = event.getByte();
        property.defaultCid = event.getByte();
        property.bearerId = event.getByte();

        boolean hasQos = event.getByte() == 1 ? true : false;
        QosStatus qosStatus = readQos(event);
        property.qosStatus= hasQos ? qosStatus : null;

        event.getByte(); //msg

        boolean hasTft = event.getByte() == 1 ? true : false;
        property.signalingFlag = event.getByte();
        boolean hasPcscf = event.getByte() == 1 ? true : false;

        TftStatus tftStatus = readTft(event);
        PcscfInfo pcscfInfo = readPcscf(event);
        property.tftStatus = hasTft ? tftStatus : null;
        property.pcscfInfo = hasPcscf ? pcscfInfo : null;
        return property;
    }

    static void writeDedicateBearer(VaEvent event, int type, DedicateBearerProperties property, boolean isEmergency) {
        log("writeDedicateBearer property.cid" +property.cid+ "property.defaultCid "+property.defaultCid +" isEmergency "+isEmergency);

        int Id = writeCorrectBearerId(property.defaultCid);
        log("writeDedicateBearer Id= " +Id);
        if(!isEmergency && WFC_FEATURE){
            if(property.cid >-1 && property.cid <5) {
                event.putByte(Id);

            } else {
                event.putByte(property.cid);
            }
            event.putByte(Id);
        } else {
            event.putByte(property.cid);
            event.putByte(property.defaultCid);
        }

        event.putByte(property.bearerId);
        event.putByte(property.qosStatus == null ? 0 : 1);
        writeQos(event, property.qosStatus == null ? new QosStatus() : property.qosStatus);
        event.putByte(type);
        event.putByte(property.tftStatus == null ? 0 : 1);
        event.putByte(property.signalingFlag);
        event.putByte(property.pcscfInfo == null ? 0 : 1);
        writeTft(event, property.tftStatus == null ? new TftStatus() : property.tftStatus);
        writePcscf(event, property.pcscfInfo == null ? new PcscfInfo() : property.pcscfInfo);
    }

    static int writeCorrectBearerId(int cid){
        if((false == WFC_FEATURE) || (cid > 5)){
            return cid;
        }
        if(sCid == INVALID_CID){
            sCid = cid;
        }
 
        return sCid;
    }
 
 static void resetCids(){
        sCid = INVALID_CID;
    }
 static void writeDedicateBearerForHandover(VaEvent event, DedicateBearerProperties property,boolean to3gpp) {
     int Id = writeCorrectBearerId(property.defaultCid);
     if(property.cid >-1 && property.cid <5) {
        event.putByte(Id);
     } else {
     event.putByte(property.cid);
     }
     event.putByte(Id);     
     event.putByte(property.bearerId);
     event.putByte(property.qosStatus == null ? 0 : 1);
     writeQos(event, property.qosStatus == null ? new QosStatus() : property.qosStatus);
     if(to3gpp == false){
         log("writeDedicateBearerForHandover to3gpp = false    property.cid"+property.cid+ "property.defaultCid "+property.defaultCid);
         if((property.cid == property.defaultCid) && ((property.cid != -1))){
             event.putByte(IMC_CONCATENATED_MSG_TYPE_MODIFICATION);
             log("writeDedicateBearerForHandover IMC_CONCATENATED_MSG_TYPE_MODIFICATION");
             } 
         else if((property.cid != property.defaultCid)&& ((property.cid != -1))){
              event.putByte(IMC_CONCATENATED_MSG_TYPE_DELETION);
              log("writeDedicateBearerForHandover IMC_CONCATENATED_MSG_TYPE_DELETION");
             } else{
             event.putByte(IMC_CONCATENATED_MSG_TYPE_NONE);
             log("writeDedicateBearerForHandover IMC_CONCATENATED_MSG_TYPE_NONE");
         }
     } else {
         log("writeDedicateBearerForHandover to3gpp = true    property.cid"+property.cid+ "property.defaultCid "+property.defaultCid);
         if ((property.cid == property.defaultCid) && (property.cid != -1)){
             event.putByte(IMC_CONCATENATED_MSG_TYPE_MODIFICATION);
             log("writeDedicateBearerForHandover IMC_CONCATENATED_MSG_TYPE_MODIFICATION");
         } else if((property.cid != property.defaultCid) && (property.cid != -1)){
             event.putByte(IMC_CONCATENATED_MSG_TYPE_ACTIVATION);
             log("writeDedicateBearerForHandover IMC_CONCATENATED_MSG_TYPE_ACTIVATION");
         } else{
             event.putByte(IMC_CONCATENATED_MSG_TYPE_NONE);
             log("writeDedicateBearerForHandover IMC_CONCATENATED_MSG_TYPE_NONE");
 
         }
     }
     event.putByte(property.tftStatus == null ? 0 : 1);
     event.putByte(property.signalingFlag);
     event.putByte(property.pcscfInfo == null ? 0 : 1);
     if(((property.cid == property.defaultCid) && ((property.cid != -1))) && (to3gpp == false)){
             property.tftStatus = new TftStatus();
             property.tftStatus.operation = TftStatus.OPCODE_CREATE_NEW_TFT;
             writeWifiTft(event, property.tftStatus == null ? new TftStatus() : property.tftStatus);
     } else {
           writeTft(event, property.tftStatus == null ? new TftStatus() : property.tftStatus);
     }
     writePcscf(event, property.pcscfInfo == null ? new PcscfInfo() : property.pcscfInfo);
     try{
        log("writeDedicateBearerForHandover "+ "cid"+property.cid+ " defaultcid "+property.defaultCid+"bearerId ="+property.bearerId+" signalflag"+property.signalingFlag);
        log("writeDedicateBearerForHandover event" +event);
        if (property.tftStatus != null) {
        log("writeDedicateBearerForHandover property.tftStatus "+property.tftStatus.operation + "property.tftStatus."+property.tftStatus.packetFilterInfoList);
         }else {
         log("writeDedicateBearerForHandover tftstatus is null");
             }
     } catch (NullPointerException ex){
        log("NullPointerException ex "+ex);
     }
 }
 
    static QosStatus readQos(VaEvent event) {
        //imcf_uint8 qci;
        //imcf_uint8 gbr_present;
        //imcf_uint8 mbr_present;
        //imcf_uint8 pad[1];
        //imcf_uint32 dl_gbr;
        //imcf_uint32 ul_gbr;
        //imcf_uint32 dl_mbr;
        //imcf_uint32 ul_mbr;

        QosStatus qosStatus = new QosStatus();
        qosStatus.qci = event.getByte();
        boolean isGbrPresent = event.getByte() == 1;
        boolean isMbrPresent = event.getByte() == 1;
        event.getByte(); //padding
        qosStatus.dlGbr= event.getInt();
        qosStatus.ulGbr= event.getInt();
        qosStatus.dlMbr= event.getInt();
        qosStatus.ulMbr= event.getInt();
        return qosStatus;
    }

    static void writeQos(VaEvent event, QosStatus qosStatus) {
        event.putByte(qosStatus.qci);
        event.putByte(qosStatus.dlGbr > 0 && qosStatus.ulGbr > 0 ? 1 : 0);
        event.putByte(qosStatus.dlMbr > 0 && qosStatus.ulMbr > 0 ? 1 : 0);
        event.putByte(0); //padding
        event.putInt(qosStatus.dlGbr);
        event.putInt(qosStatus.ulGbr);
        event.putInt(qosStatus.dlMbr);
        event.putInt(qosStatus.ulMbr);
    }

    static void writeWifiQos(VaEvent event, QosStatus qosStatus) {
        event.putByte(255);
        event.putByte(1);
        event.putByte(1);
        event.putByte(0); //padding
        event.putInt(0XFFFFFFFF);
        event.putInt(0XFFFFFFFF);
        event.putInt(0XFFFFFFFF);
        event.putInt(0XFFFFFFFF);
    }

    static TftStatus readTft(VaEvent event) {
        //imc_tft_operation_enum tft_opcode
        //imcf_uint8 ebit_flag
        //imcf_uint8 pad[2];
        //imc_pkt_filter_struct pf_list [IMC_MAX_PACKET_FILTER_NUM]
        //imc_tft_parameter_list_struct parameter_list

        TftStatus tftStatus = new TftStatus();
        tftStatus.operation = event.getByte();
        boolean ebitFlag = event.getByte() == 1;
        event.getBytes(2); //padding
        for (int i=0; i<IMC_MAX_PACKET_FILTER_NUM; i++) {
            //====imc_pkt_filter_struct====
            //imcf_uint8 id
            //imcf_uint8 precedence
            //imc_pf_direction_enum direction
            //imcf_uint8 nw_id
            //imcf_uint32 bitmap
            //imcf_uint8 remote_addr_and_mask [IMC_MAX_REMOTE_ADDR_AND_MASK_LEN]
            //imcf_uint8 protocol_nxt_hdr
            //imcf_uint8 pad2 [3]
            //imcf_uint16 local_port_low
            //imcf_uint16 local_port_high
            //imcf_uint16 remote_port_low
            //imcf_uint16 remote_port_high
            //imcf_uint32 spi
            //imcf_uint8 tos
            //imcf_uint8 tos_msk
            //imcf_uint8 pad3 [2]
            //imcf_uint32 flow_label

            PacketFilterInfo pkFilterInfo = new PacketFilterInfo();
            pkFilterInfo.id = event.getByte();
            pkFilterInfo.precedence = event.getByte();
            pkFilterInfo.direction = event.getByte();
            pkFilterInfo.networkPfIdentifier = event.getByte();
            pkFilterInfo.bitmap= event.getInt();

            byte[] addrAndMaskArray = event.getBytes(IMC_MAX_REMOTE_ADDR_AND_MASK_LEN);
            if ((pkFilterInfo.bitmap & PacketFilterInfo.IMC_BMP_V4_ADDR) > 0) { //IPv4
                try {
                    InetAddress address = InetAddress.getByAddress(Arrays.copyOfRange(addrAndMaskArray, 0, 4));
                    InetAddress mask = InetAddress.getByAddress(Arrays.copyOfRange(addrAndMaskArray, 4, 8));
                    pkFilterInfo.address = address.getHostAddress();
                    pkFilterInfo.mask = mask.getHostAddress();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
            } else if ((pkFilterInfo.bitmap & PacketFilterInfo.IMC_BMP_V6_ADDR) > 0) { //IPv6
                try {
                    InetAddress address = InetAddress.getByAddress(Arrays.copyOfRange(addrAndMaskArray, 0, 16));
                    InetAddress mask = InetAddress.getByAddress(Arrays.copyOfRange(addrAndMaskArray, 16, 32));
                    pkFilterInfo.address = address.getHostAddress();
                    pkFilterInfo.mask = mask.getHostAddress();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
            }

            pkFilterInfo.protocolNextHeader = event.getByte();
            event.getBytes(3); //padding
            pkFilterInfo.localPortLow = event.getShort();
            pkFilterInfo.localPortHigh= event.getShort();
            pkFilterInfo.remotePortLow = event.getShort();
            pkFilterInfo.remotePortHigh= event.getShort();
            pkFilterInfo.spi = event.getInt();
            pkFilterInfo.tos = event.getByte();
            pkFilterInfo.tosMask = event.getByte();
            event.getBytes(2); //padding
            pkFilterInfo.flowLabel = event.getInt();

            if (pkFilterInfo.id > 0)
                tftStatus.packetFilterInfoList.add(pkFilterInfo);
        }

        //====imc_tft_parameter_list_struct====
        //imcf_uint8 linked_pf_id_num
        //imcf_uint8 pad [3]
        //imcf_uint8 linked_pf_id_list [IMC_MAX_PACKET_FILTER_NUM]
        //imcf_uint8 authtoken_flowid_num
        //imcf_uint8 pad2 [3]
        //imc_tft_authtoken_flowid_struct authtoken_flowid_list [IMC_MAX_AUTHTOKEN_FLOWID_NUM]

        TftParameter tftParameter = new TftParameter();
        int linkedPfNumber = event.getByte();
        event.getBytes(3); //padding
        byte[] linkedPfIdArray = event.getBytes(IMC_MAX_PACKET_FILTER_NUM);
        if (linkedPfIdArray != null) {
            for (int i = 0; i < linkedPfNumber; i++) {
                tftParameter.linkedPacketFilterIdList.add(linkedPfIdArray[i] & 0xFF);
            }
        }

        int authtokenFlowIdNum = event.getByte();
        event.getBytes(3); //padding

        //====imc_tft_authtoken_flowid_struct====
        //imcf_uint8 auth_token_len
        //imcf_uint8 pad [3]
        //imcf_uint8 auth_token [IMC_MAX_AUTHORIZATION_TOKEN_LEN]
        //imcf_uint8 flow_id_num
        //imcf_uint8 pad2 [3]
        //imcf_uint8 flow_id_list [IMC_MAX_FLOW_IDENTIFIER_NUM][IMC_FLOW_IDENTIFIER_LEN]
        for (int i=0; i<IMC_MAX_AUTHTOKEN_FLOWID_NUM; i++) {
            TftAuthToken authToken = new TftAuthToken();
            int authTokenLength = event.getByte();
            event.getBytes(3); //padding
            byte[] authTokenArray = event.getBytes(IMC_MAX_AUTHORIZATION_TOKEN_LEN);
            if (authTokenArray != null) {
                for (int j = 0; j < IMC_MAX_AUTHORIZATION_TOKEN_LEN; j++) {
                    if (j < authTokenLength) {
                        authToken.authTokenList.add(authTokenArray[j] & 0xFF);
                    }
                }
            }

            int flowIdLength = event.getByte();
            event.getBytes(3); //padding
            for (int j=0; j<IMC_MAX_FLOW_IDENTIFIER_NUM; j++) {
                byte[] flowIdArray = event.getBytes(TftAuthToken.FLOWID_LENGTH);
                Integer[] flowIds = new Integer[TftAuthToken.FLOWID_LENGTH];
                if (flowIdArray != null) {
                    for (int k = 0; k < TftAuthToken.FLOWID_LENGTH; k++) {
                        flowIds[k] = flowIdArray[k] & 0xFF;
                    }
                }

                if (j < flowIdLength)
                    authToken.flowIdList.add(flowIds);
            }

            if (i < authtokenFlowIdNum)
                tftParameter.authTokenFlowIdList.add(authToken);
        }

        if (ebitFlag)
            tftStatus.tftParameter = tftParameter;

        return tftStatus;
    }

    static void writeTft(VaEvent event, TftStatus tftStatus) {
        event.putByte(tftStatus.operation);
        event.putByte(tftStatus.tftParameter.isEmpty() ? 0 : 1);
        event.putBytes(new byte[2]); //padding
        for (int i=0; i<IMC_MAX_PACKET_FILTER_NUM; i++) {
            PacketFilterInfo pkFilterInfo = null;
            for (PacketFilterInfo pkt : tftStatus.packetFilterInfoList) {
                if (pkt.id == i+1) {
                    pkFilterInfo = pkt;
                    break;
                }
            }

            if (pkFilterInfo == null)
                pkFilterInfo = new PacketFilterInfo();

            event.putByte(pkFilterInfo.id);
            event.putByte(pkFilterInfo.precedence);
            event.putByte(pkFilterInfo.direction);
            event.putByte(pkFilterInfo.networkPfIdentifier);
            event.putInt(pkFilterInfo.bitmap);

            byte[] addrAndMaskArray = new byte[IMC_MAX_REMOTE_ADDR_AND_MASK_LEN];
            byte[] addressByteArray = null;
            byte[] maskByteArray = null;
            if (pkFilterInfo.address != null && pkFilterInfo.address.length() > 0) {
                String[] splitArray = pkFilterInfo.address == null ? null : pkFilterInfo.address.split("\\.");
                if (splitArray != null) {
                    addressByteArray = new byte[splitArray.length];
                    for (int j=0; j<splitArray.length; j++) {
                        if (splitArray[j].length() > 0)
                            addressByteArray[j] = (byte) (Integer.parseInt(splitArray[j]) & 0xFF);
                        else
                            addressByteArray[j] = 0;
                    }

                    for (int j=0; j<addressByteArray.length; j++)
                        addrAndMaskArray[j] = addressByteArray[j];
                }
            }

            if (pkFilterInfo.mask != null && pkFilterInfo.address.length() > 0) {
                String[] splitArray = pkFilterInfo.mask == null ? null : pkFilterInfo.mask.split("\\.");
                if (splitArray != null) {
                    maskByteArray = new byte[splitArray.length];
                    for (int j=0; j<splitArray.length; j++) {
                        if (splitArray[j].length() > 0)
                            maskByteArray[j] = (byte) (Integer.parseInt(splitArray[j]) & 0xFF);
                        else if (addressByteArray != null) {
                            addressByteArray[j] = 0;
                        }
                    }

                    if ((pkFilterInfo.bitmap & PacketFilterInfo.IMC_BMP_V4_ADDR) > 0) { //IPv4
                        for (int j=0; j<maskByteArray.length; j++)
                            addrAndMaskArray[j+4] = maskByteArray[j];
                    } else if ((pkFilterInfo.bitmap & PacketFilterInfo.IMC_BMP_V6_ADDR) > 0) {//IPv6
                        for (int j=0; j<maskByteArray.length; j++)
                            addrAndMaskArray[j+16] = maskByteArray[j];
                    }
                }
            }
            event.putBytes(addrAndMaskArray);

            event.putByte(pkFilterInfo.protocolNextHeader);
            event.putBytes(new byte[3]); //padding
            event.putShort(pkFilterInfo.localPortLow);
            event.putShort(pkFilterInfo.localPortHigh);
            event.putShort(pkFilterInfo.remotePortLow);
            event.putShort(pkFilterInfo.remotePortHigh);
            event.putInt(pkFilterInfo.spi);
            event.putByte(pkFilterInfo.tos);
            event.putByte(pkFilterInfo.tosMask);
            event.putBytes(new byte[2]); //padding
            event.putInt(pkFilterInfo.flowLabel);
        }
        
        event.putByte(tftStatus.tftParameter.linkedPacketFilterIdList.size());
        event.putBytes(new byte[3]); //padding
        for (int i=0; i<IMC_MAX_PACKET_FILTER_NUM; i++) {
            if (i < tftStatus.tftParameter.linkedPacketFilterIdList.size())
                event.putByte(tftStatus.tftParameter.linkedPacketFilterIdList.get(i).byteValue());
            else
                event.putByte(0);
        }

        event.putByte(tftStatus.tftParameter.authTokenFlowIdList.size());
        event.putBytes(new byte[3]); //padding

        for (int i=0; i<IMC_MAX_AUTHTOKEN_FLOWID_NUM; i++) {
            TftAuthToken authToken = null;
            if (i < tftStatus.tftParameter.authTokenFlowIdList.size())
                authToken = tftStatus.tftParameter.authTokenFlowIdList.get(i);
            else
                authToken = new TftAuthToken();

            event.putByte(authToken.authTokenList.size());
            event.putBytes(new byte[3]); //padding
            for (int j=0; j<IMC_MAX_AUTHORIZATION_TOKEN_LEN; j++) {
                if (j < authToken.authTokenList.size())
                    event.putByte(authToken.authTokenList.get(j));
                else
                    event.putByte(0);
            }

            event.putByte(authToken.flowIdList.size());
            event.putBytes(new byte[3]); //padding
            for (int j=0; j<IMC_MAX_FLOW_IDENTIFIER_NUM; j++) {
                if (j < authToken.flowIdList.size()) {
                    for (int k=0; k<TftAuthToken.FLOWID_LENGTH; k++)
                        event.putByte(authToken.flowIdList.get(j)[k]);
                } else {
                    event.putBytes(new byte[TftAuthToken.FLOWID_LENGTH]);
                }
            }

        }
    }

    static void writeWifiTft(VaEvent event, TftStatus tftStatus) {
        event.putByte(tftStatus.operation);
        event.putByte(tftStatus.tftParameter.isEmpty() ? 0 : 1);
        event.putBytes(new byte[2]); // padding
        log("writeWiFiTft tftStatys.operation" + tftStatus.operation
                + "tftStatus.tftParameter =" + tftStatus.tftParameter);
        for (int i = 0; i < IMC_MAX_PACKET_FILTER_NUM; i++) {
            PacketFilterInfo pkFilterInfo = null;
            for (PacketFilterInfo pkt : tftStatus.packetFilterInfoList) {
                if (pkt.id == i + 1) {
                    pkFilterInfo = pkt;
                    break;
                }
            }

            if (pkFilterInfo == null)
                pkFilterInfo = new PacketFilterInfo();
            if (i == 0) {

                pkFilterInfo.id = 1;
                pkFilterInfo.precedence = 255;
                pkFilterInfo.direction = 0x03;
                pkFilterInfo.networkPfIdentifier = 1;
                // pkFilterInfo.bitmap = PacketFilterInfo.IMC_BMP_V6_ADDR;
            }
            log("TFT info send to imcb for packetinfo array index " + i
                    + "equals " + pkFilterInfo);
            event.putByte(pkFilterInfo.id);
            event.putByte(pkFilterInfo.precedence);
            event.putByte(pkFilterInfo.direction);
            event.putByte(pkFilterInfo.networkPfIdentifier);
            event.putInt(pkFilterInfo.bitmap);

            byte[] addrAndMaskArray = new byte[IMC_MAX_REMOTE_ADDR_AND_MASK_LEN];
            byte[] addressByteArray = null;
            byte[] maskByteArray = null;
            if (pkFilterInfo.address != null
                    && pkFilterInfo.address.length() > 0) {
                String[] splitArray = pkFilterInfo.address == null ? null
                        : pkFilterInfo.address.split("\\.");
                if (splitArray != null) {
                    addressByteArray = new byte[splitArray.length];
                    for (int j = 0; j < splitArray.length; j++) {
                        if (splitArray[j].length() > 0)
                            addressByteArray[j] = (byte) (Integer
                                    .parseInt(splitArray[j]) & 0xFF);
                        else
                            addressByteArray[j] = 0;
                    }

                    for (int j = 0; j < addressByteArray.length; j++)
                        addrAndMaskArray[j] = addressByteArray[j];
                }
            }

            if (pkFilterInfo.mask != null && pkFilterInfo.address.length() > 0) {
                String[] splitArray = pkFilterInfo.mask == null ? null
                        : pkFilterInfo.mask.split("\\.");
                if (splitArray != null) {
                    maskByteArray = new byte[splitArray.length];
                    for (int j = 0; j < splitArray.length; j++) {
                        if (splitArray[j].length() > 0)
                            maskByteArray[j] = (byte) (Integer
                                    .parseInt(splitArray[j]) & 0xFF);
                        else if (addressByteArray != null) {
                            addressByteArray[j] = 0;
                        }
                    }

                    if ((pkFilterInfo.bitmap & PacketFilterInfo.IMC_BMP_V4_ADDR) > 0) { // IPv4
                        for (int j = 0; j < maskByteArray.length; j++)
                            addrAndMaskArray[j + 4] = maskByteArray[j];
                    } else if ((pkFilterInfo.bitmap & PacketFilterInfo.IMC_BMP_V6_ADDR) > 0) {// IPv6
                        for (int j = 0; j < maskByteArray.length; j++)
                            addrAndMaskArray[j + 16] = maskByteArray[j];
                    }
                }
            }
            event.putBytes(addrAndMaskArray);

            event.putByte(pkFilterInfo.protocolNextHeader);
            event.putBytes(new byte[3]); // padding
            event.putShort(pkFilterInfo.localPortLow);
            event.putShort(pkFilterInfo.localPortHigh);
            event.putShort(pkFilterInfo.remotePortLow);
            event.putShort(pkFilterInfo.remotePortHigh);
            event.putInt(pkFilterInfo.spi);
            event.putByte(pkFilterInfo.tos);
            event.putByte(pkFilterInfo.tosMask);
            event.putBytes(new byte[2]); // padding
            event.putInt(pkFilterInfo.flowLabel);
        }

        event.putByte(tftStatus.tftParameter.linkedPacketFilterIdList.size());
        event.putBytes(new byte[3]); // padding
        for (int i = 0; i < IMC_MAX_PACKET_FILTER_NUM; i++) {
            if (i < tftStatus.tftParameter.linkedPacketFilterIdList.size())
                event.putByte(tftStatus.tftParameter.linkedPacketFilterIdList
                        .get(i).byteValue());
            else
                event.putByte(0);
        }

        event.putByte(tftStatus.tftParameter.authTokenFlowIdList.size());
        event.putBytes(new byte[3]); // padding

        for (int i = 0; i < IMC_MAX_AUTHTOKEN_FLOWID_NUM; i++) {
            TftAuthToken authToken = null;
            if (i < tftStatus.tftParameter.authTokenFlowIdList.size())
                authToken = tftStatus.tftParameter.authTokenFlowIdList.get(i);
            else
                authToken = new TftAuthToken();

            event.putByte(authToken.authTokenList.size());
            event.putBytes(new byte[3]); // padding
            for (int j = 0; j < IMC_MAX_AUTHORIZATION_TOKEN_LEN; j++) {
                if (j < authToken.authTokenList.size())
                    event.putByte(authToken.authTokenList.get(j));
                else
                    event.putByte(0);
            }

            event.putByte(authToken.flowIdList.size());
            event.putBytes(new byte[3]); // padding
            for (int j = 0; j < IMC_MAX_FLOW_IDENTIFIER_NUM; j++) {
                if (j < authToken.flowIdList.size()) {
                    for (int k = 0; k < TftAuthToken.FLOWID_LENGTH; k++)
                        event.putByte(authToken.flowIdList.get(j)[k]);
                } else {
                    event.putBytes(new byte[TftAuthToken.FLOWID_LENGTH]);
                }
            }
        }
    }

    static PcscfInfo readPcscf(VaEvent event) {
        //====imc_pcscf_list_struct====
        //imcf_uint8 num_of_ipv4_pcscf_addr
        //imcf_uint8 pad [3]
        //imc_pcscf_ipv4_struct pcscf_v4 [IMC_PCSCF_MAX_NUM]
        //imcf_uint8 num_of_ipv6_pcscf_addr
        //imcf_uint8 pad2 [3]
        //imc_pcscf_ipv6_struct pcscf_v6 [IMC_PCSCF_MAX_NUM]

        PcscfInfo pcscfInfo = new PcscfInfo();
        int v4AddrNum = event.getByte();
        event.getBytes(3); //padding
        for (int i=0; i<IMC_PCSCF_MAX_NUM; i++) {
            PcscfAddr pcscfAddr = new PcscfAddr();
            //====imc_pcscf_ipv4_struct ====
            //imcf_uint8 protocol_type
            //imcf_uint8 pad [1]
            //imcf_uint16 port_num
            //imcf_uint8 addr[IMC_IPV4_ADDR_LEN]
            pcscfAddr.protocol = event.getByte();
            event.getByte(); //padding
            pcscfAddr.port = event.getShort();

            StringBuffer ipBuffer = new StringBuffer(IMC_IPV4_ADDR_LEN);
            for (int j=0; j<IMC_IPV4_ADDR_LEN; j++) {
                if (j != 0)    
                    ipBuffer.append("." + event.getByte());
                else
                    ipBuffer.append(event.getByte());
            }
            pcscfAddr.address = ipBuffer.toString();

            if (i < v4AddrNum && pcscfAddr.address != null)
                pcscfInfo.v4AddrList.add(pcscfAddr);
        }

        int v6AddrNum = event.getByte();
        event.getBytes(3); //padding
        for (int i=0; i<IMC_PCSCF_MAX_NUM; i++) {
            PcscfAddr pcscfAddr = new PcscfAddr();
            //====imc_pcscf_ipv6_struct ====
            //imcf_uint8 protocol_type
            //imcf_uint8 pad [1]
            //imcf_uint16 port_num
            //imcf_uint8 addr [IMC_IPV6_ADDR_LEN]
            pcscfAddr.protocol = event.getByte();
            event.getByte(); //padding
            pcscfAddr.port = event.getShort();

            StringBuffer ipBuffer = new StringBuffer(IMC_IPV6_ADDR_LEN);
            for (int j=0; j<IMC_IPV6_ADDR_LEN; j++) {
                if (j != 0)    
                    ipBuffer.append("." + event.getByte());
                else
                    ipBuffer.append(event.getByte());
            }
            pcscfAddr.address = ipBuffer.toString();

            if (i < v6AddrNum && pcscfAddr.address != null)
                pcscfInfo.v6AddrList.add(pcscfAddr);
        }
        return pcscfInfo;
    }

    static void writePcscf(VaEvent event, PcscfInfo pcscfInfo) {
        event.putByte(pcscfInfo.v4AddrList.size());
        event.putBytes(new byte[3]); //padding
        for (int i=0; i<IMC_PCSCF_MAX_NUM; i++) {
            PcscfAddr pcscfAddr = null;
            if (i < pcscfInfo.v4AddrList.size())
                pcscfAddr = pcscfInfo.v4AddrList.get(i);
            else
                pcscfAddr = new PcscfAddr();

            event.putByte(pcscfAddr.protocol);
            event.putByte(0); //padding
            event.putShort(pcscfAddr.port);

            String[] pcscfSplitArray = pcscfAddr.address == null ? null : pcscfAddr.address.split("\\.");
            for (int j=0; j<IMC_IPV4_ADDR_LEN; j++) {
                if (pcscfSplitArray != null && j < pcscfSplitArray.length) {
                    try {
                        event.putByte(Integer.parseInt(pcscfSplitArray[j]));
                        log("IPV4 send to imcb "+Integer.parseInt(pcscfSplitArray[j]));
                    } catch (NumberFormatException ex) {
                        loge("IPV4: Inavlid int: pcscfSplitArray[" + j + "]: " + pcscfSplitArray[j]);
                        event.putByte(0);
                    }
                }
                else
                    event.putByte(0);
            }
        }

        event.putByte(pcscfInfo.v6AddrList.size());
        event.putBytes(new byte[3]); //padding
        for (int i=0; i<IMC_PCSCF_MAX_NUM; i++) {
            PcscfAddr pcscfAddr = null;
            if (i < pcscfInfo.v6AddrList.size())
                pcscfAddr = pcscfInfo.v6AddrList.get(i);
            else
                pcscfAddr = new PcscfAddr();

            event.putByte(pcscfAddr.protocol);
            event.putByte(0); //padding
            event.putShort(pcscfAddr.port);

            String[] pcscfSplitArray = pcscfAddr.address == null ? null : pcscfAddr.address.split("\\.");
            for (int j=0; j<IMC_IPV6_ADDR_LEN; j++) {
                if (pcscfSplitArray != null && j < pcscfSplitArray.length) {
                    try {
                        event.putByte(Integer.parseInt(pcscfSplitArray[j]));
                        log("IPV6 send to imcb "+Integer.parseInt(pcscfSplitArray[j]));
                    } catch (NumberFormatException ex) {
                        loge("IPV6: Inavlid int: pcscfSplitArray[" + j + "]: " + pcscfSplitArray[j]);
                        event.putByte(0);
                    }
                }
                else
                    event.putByte(0);
            }
        }
    }
    static void dumpPdnAckRsp(VaEvent event) {
        String functionName = "[dumpPdnAckRsp] ";
        int transactionId;
        int pdnCnt = 0;
        byte [] pad2 = new byte[2];

        transactionId = event.getByte();
        pdnCnt = event.getByte();
        pad2 = event.getBytes(pad2.length);
        
        log(functionName + "transactionId: " + transactionId + ", pdn cnt: " + pdnCnt);
    }

    static void dumpPdnContextProp(VaEvent event) {
        final int nPAD3LEN = 3;
        byte [] pad3 = new byte[nPAD3LEN];
        String functionName = "[dumpPdnContextProp] ";

        //Bearers properties
        int addrType;
        DedicateBearerProperties property;

        addrType = event.getByte();
        pad3 = event.getBytes(nPAD3LEN);

        property = readDedicateBearer(event);

        log(functionName + "pdn_contexts, addrType: " + addrType + ", cid: " + property.cid
            + ", defaultCid: " + property.defaultCid + ", bearerId: " + property.bearerId
            + ", Qos: " + property.qosStatus + ", signalingFlag: " + property.signalingFlag
            + ", tft: " + property.tftStatus + ", pcscf:" + property.pcscfInfo);

        int num_of_concatenated_contexts = event.getByte();
        pad3 = event.getBytes(nPAD3LEN);                            // padding

        log(functionName + "concatenated num: " + num_of_concatenated_contexts);
        // concatenated properties
        for (int i = 0; i < IMC_MAX_CONCATENATED_NUM; i++) {    // write concatenated contexts    
            if (i < num_of_concatenated_contexts) {
                property = readDedicateBearer(event);
                log(functionName + "concatenated contexts[: " + i + "], cid: " 
                + property.cid + ", defaultCid: " + property.defaultCid + ", bearerId: "
                + property.bearerId + ", Qos: " + property.qosStatus + ", signalingFlag: "
                + property.signalingFlag + ", tft: " + property.tftStatus + ", pcscf:" + property.pcscfInfo);
            } 
        }
    }

    static void writeAllBearersProperties(VaEvent event, int msgType, int pdp_addr_type, DedicateBearerProperties property, boolean isEmergency) {
        //imc_pdn_context_struct contexts 
        //----------------------------------------------
        //imc_pdp_addr_type_enum pdp_addr_type
        //imcf_uint8 pad2[3]
        //imc_single_concatenated_msg_struct main_context
        //imcf_uint8 num_of_concatenated_contexts
        //imcf_uint8 pad3[3]
        //imc_single_concatenated_msg_struct concatenated_context[IMC_MAX_CONCATENATED_NUM];
        //----------------------------------------------
        int num_of_concatenated_contexts = property.concatenateBearers.size();

        event.putByte(pdp_addr_type);   //temporarily for imc_pdp_addr_type_enum
        event.putBytes(new byte[3]);    //padding

        writeDedicateBearer(event, msgType, property,isEmergency);    // write main_context
        event.putByte(num_of_concatenated_contexts);            // write concatenated number             
        event.putBytes(new byte[3]);                            // padding
        
        for (int i = 0; i < IMC_MAX_CONCATENATED_NUM; i++) {    // write concatenated contexts    
            if (i < num_of_concatenated_contexts)
                writeDedicateBearer(event, msgType, property.concatenateBearers.get(i),isEmergency);
            else
                writeDedicateBearer(event, msgType, new DedicateBearerProperties(),isEmergency);
        }

        if (DBG) dumpPdnContextProp(event);
    }

    static void writeWiFiBearerProperties(VaEvent event, int msgType, int pdp_addr_type, DedicateBearerProperties property) {
        //imc_pdn_context_struct contexts 
        //----------------------------------------------
        //imc_pdp_addr_type_enum pdp_addr_type
        //imcf_uint8 pad2[3]
        //imc_single_concatenated_msg_struct main_context
        //imcf_uint8 num_of_concatenated_contexts
        //imcf_uint8 pad3[3]
        //imc_single_concatenated_msg_struct concatenated_context[IMC_MAX_CONCATENATED_NUM];
        //----------------------------------------------
        //int num_of_concatenated_contexts = property.concatenateBearers.size();

        event.putByte(pdp_addr_type);   //temporarily for imc_pdp_addr_type_enum
        event.putBytes(new byte[3]);    //padding

        writeWifiBearer(event, msgType, property);    // write main_context
        event.putByte(0);            // write concatenated number             
        event.putBytes(new byte[3]);                            // padding
        //Save the properties for handover
        mWifiBearerProp = property;
        log("Saved writeWiFiBearerProperties for handover "+mWifiBearerProp);
        if (DBG) dumpPdnContextProp(event);
    }

    static void writeWifiBearer(VaEvent event, int type,
            DedicateBearerProperties property) {

        int Id = writeCorrectBearerId(DataDispatcher.getWiFicid());
        event.putByte(Id);
        event.putByte(Id);

        event.putByte(0);//0
        event.putByte(1);
        writeWifiQos(event, property.qosStatus == null ? new QosStatus()
                : property.qosStatus);
        event.putByte(type);
        event.putByte(1);
        event.putByte(1);
        event.putByte(property.pcscfInfo == null ? 0 : 1);
        TftStatus tftStat = new TftStatus();
        tftStat.operation = TftStatus.OPCODE_CREATE_NEW_TFT;
        // tftStat.tftParameter = 0;
        property.tftStatus = tftStat;
        writeWifiTft(event, property.tftStatus == null ? new TftStatus()
                : property.tftStatus);
	log("pcscf info send to imcb ="+property.pcscfInfo );
        writePcscf(event, property.pcscfInfo == null ? new PcscfInfo()
                : property.pcscfInfo);
    }

    DefaultPdnActInd extractDefaultPdnActInd(VaEvent event) {
        DefaultPdnActInd defaultPdnActInd = new DefaultPdnActInd();

        defaultPdnActInd.transactionId = event.getByte();
        defaultPdnActInd.pad = event.getBytes(defaultPdnActInd.pad.length);   //skip pad size 3
        defaultPdnActInd.qosStatus = DataDispatcherUtil.readQos(event);
        defaultPdnActInd.emergency_ind = event.getByte();
        defaultPdnActInd.pcscf_discovery = event.getByte();
        defaultPdnActInd.signalingFlag = (event.getByte() > 0) ? 1: 0;
        defaultPdnActInd.pad2 = event.getBytes(defaultPdnActInd.pad2.length);   //skip pad size 1
        /*WFC*/
        defaultPdnActInd.rat_type = event.getByte();
        defaultPdnActInd.pad3 = event.getBytes(defaultPdnActInd.pad3.length);

        log("extractDefaultPdnActInd DefaultPdnActInd" + defaultPdnActInd);
        return defaultPdnActInd;
    }

    PdnDeactInd extractPdnDeactInd(VaEvent event) {
        PdnDeactInd pdnDeactInd = new PdnDeactInd();
        //imcf_uint8    transaction_id
        //imcf_uint8    abort_activate_transaction_id
        //imcf_uint8    context_id_is_valid
        //imcf_uint8    context_id
      
        pdnDeactInd.transactionId = event.getByte();
        pdnDeactInd.abortTransactionId = event.getByte();
        pdnDeactInd.isCidValid = (event.getByte() == 1);
        pdnDeactInd.cid = event.getByte();

        log("extractDefaultPdnActInd PdnDeactInd" + pdnDeactInd);
        return pdnDeactInd;
    }

    VaEvent composeGlobalIPAddrVaEvent(int msgId, int cid, int networkId, byte [] addr
        , String intfName, int phoneId) {
        // imcf_uint8                               context_id
        // imcf_uint8 pad[3]                     padding 3 bytes
        // imcf_int32 network_id            network id for binding interface with ip address
        // imcf_uint8                               global_ipv4_addr[0x04] or global_ipv6_address[0x10]
        // char                                       nw_if_name[100]  
        VaEvent event = new VaEvent(phoneId, msgId);
        final int intfNamMaxLen = 100;         
        event.putByte(cid);
        event.putBytes(new byte[3]); //padding
        event.putInt(networkId);
        event.putBytes(addr);
        event.putString(intfName, intfNamMaxLen);

        return event;
    }

    /*
     * composeHandoverStartVaEvent - compose a Va event for Handover started
     * event. Send from DataDispatcher to IMCB- informing IMCB of Handover
     * started. inputs - to_3gpp : true for WiFi->LTE, false for LTE->WiFi
     */
        VaEvent composeHandoverStartVaEvent(int MsgId, boolean to3gpp) {
        int RAT_TYPE_LTE = 3;
        int RAT_TYPE_WIFI = 5;
        int srcRat;
        int tgtRat;
        VaEvent event = new VaEvent(0,MsgId);
        if (to3gpp == true) {
            srcRat = RAT_TYPE_WIFI;
            tgtRat = RAT_TYPE_LTE;
        } else {
            srcRat = RAT_TYPE_LTE;
            tgtRat = RAT_TYPE_WIFI;
        }
        event.putByte(srcRat);
        event.putByte(tgtRat);
        event.putBytes(new byte[2]); // padding
        return event;
    }

    /*
     * composeHandoverDoneVaEvent - compose a Va event for Handover done
     * event. Send from DataDispatcher to IMCB- informing IMCB of Handover
     * done. inputs - to_3gpp : true for WiFi->LTE, false for LTE->WiFi
     * result : handover successful or failed
     */
    VaEvent composeHandoverDoneVaEvent(int MsgId, boolean to3gpp, boolean result) {
        int RAT_TYPE_LTE = 3;
        int RAT_TYPE_WIFI = 5;
        int srcRat;
        int tgtRat;
        VaEvent event = new VaEvent(0, MsgId);
        int handoverResult = result ? TRUE : FALSE;
        if (to3gpp == true) {
            srcRat = RAT_TYPE_WIFI;
            tgtRat = RAT_TYPE_LTE;
        } else {
            srcRat = RAT_TYPE_LTE;
            tgtRat = RAT_TYPE_WIFI;
        }
        event.putByte(srcRat);
        event.putByte(tgtRat);
        event.putByte(handoverResult);
        event.putBytes(new byte[1]); // padding
        return event;
    }

    synchronized static void pdnModifyInfo(VaEvent event, boolean to3gpp, Context context,
            DedicateBearerProperties property) {
        log("pdnModifyInfo to3gpp " + to3gpp + "property " + property);
        if (property == null)
            DataDispatcherUtil.writeDedicateBearerForHandover(event,
                    new DedicateBearerProperties(), to3gpp);
        else {
            if (!to3gpp) {
                DedicateBearerProperties wifiBearerProp = new DedicateBearerProperties();
                wifiBearerProp.cid = property.cid;
                wifiBearerProp.defaultCid = property.defaultCid;
                wifiBearerProp.bearerId = 0;
                wifiBearerProp.qosStatus = new QosStatus();
                wifiBearerProp.qosStatus.qci = 255;
                wifiBearerProp.qosStatus.dlGbr = 0XFFFFFFFF;
                wifiBearerProp.qosStatus.ulGbr = 0XFFFFFFFF;
                wifiBearerProp.qosStatus.dlMbr = 0XFFFFFFFF;
                wifiBearerProp.qosStatus.ulMbr = 0XFFFFFFFF;
                wifiBearerProp.tftStatus = new TftStatus();
                wifiBearerProp.tftStatus.operation = TftStatus.OPCODE_CREATE_NEW_TFT;
                wifiBearerProp.pcscfInfo = property.pcscfInfo;
                writeDedicateBearerForHandover(event, wifiBearerProp, to3gpp);
                log("pdnModifyInfo : Handovet to wifi");
                log("pdnModifyInfo wifiBearerProp.cid " + wifiBearerProp.cid
                        + " wifiBearerProp.defaultCid "
                        + wifiBearerProp.defaultCid
                        + " wifiBearerProp.bearerId ="
                        + wifiBearerProp.bearerId + "wifiBearerProp.qosStatus"
                        + wifiBearerProp.qosStatus
                        + "wifiBearerProp.tftStatus.operation"
                        + wifiBearerProp.tftStatus.operation);
            } else {
                DataDispatcherUtil.writeDedicateBearerForHandover(event,
                        property, to3gpp); // write property itself
            }
        }

        for (int i = 0; i < DataDispatcherUtil.IMC_MAX_CONCATENATED_NUM - 1; i++) {
            if (property == null || i >= property.concatenateBearers.size())
                DataDispatcherUtil.writeDedicateBearerForHandover(event,
                        new DedicateBearerProperties(), to3gpp);
            else
                DataDispatcherUtil.writeDedicateBearerForHandover(event,
                        property.concatenateBearers.get(i), to3gpp); /*write its concatanate bearers*/
        }
        if (to3gpp == true) {
            log("Source RAT is epdg, handover");
            writeRatCellInfo(event, ConnectivityManager.TYPE_EPDG, context);
            /* write targer rat cell */
            writeRatCellInfo(event, ConnectivityManager.TYPE_MOBILE_IMS,
                    context);
        } else {
            log("Source RAT is mobile, handover");
            writeRatCellInfo(event, ConnectivityManager.TYPE_MOBILE_IMS,
                    context);
            /* write targer rat cell */
            writeRatCellInfo(event, ConnectivityManager.TYPE_EPDG, context);
        }

    }
    static void writeRatCellInfo(VaEvent event, int ratInfo, Context context) {
        TelephonyManager phone = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);
        final int phoneType = phone.getPhoneType();
        final int intfPlmnMaxLen = 100;
        String cid = "";
        String non3gppId = "";
        int lacTemp = -1;
        String lac = "";
        int rat = 0;
        int rilRat = 0;
        String plmn = "";
        int isEmsSupport = 0;
        int radio = 0;
        StringBuilder defaultLac = new StringBuilder("0000");
        StringBuilder defaultCid = new StringBuilder("00000000");
        // ServiceState mSS = new ServiceState();
        log("ratInfo " + ratInfo);

        if (ratInfo == ConnectivityManager.TYPE_EPDG) {
            /* get AP of WiFi cell id */
            rat = RAT_TYPE_WIFI;
            WifiManager wifi = (WifiManager) context
                    .getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifi.getConnectionInfo();
            non3gppId = wifiInfo.getBSSID();
            if(non3gppId != null)
                non3gppId = non3gppId.replaceAll(":", "");
            else
                non3gppId = "";
            log("cellid =" + non3gppId);
        } else {

            log("RAT type  mobile");
            rilRat = SystemProperties.getInt(RIL_RAT_DETAIL, 0);
            log("rilRat ="+rilRat);
            switch(rilRat){
                case 2:
                    rat = RAT_TYPE_3G_FDD;
                    break;
                case 3:
                    rat = RAT_TYPE_3G_TDD;
                    break;
                case 4:
                    rat = RAT_TYPE_LTE_FDD;
                    break;
                case 5:
                    rat = RAT_TYPE_LTE_TDD;
                    break;
                default:
                   rat = getActiveMDRat();
            }
            try {
            GsmCellLocation gsm_cell = (GsmCellLocation) phone
                    .getCellLocation();
            cid = Integer.toHexString(gsm_cell.getCid()); // get cid for the
                                                              // gsm
                                                       // cell
            lacTemp = gsm_cell.getLac(); // get lac for the gsm cell
            lac = Integer.toHexString(lacTemp);
            defaultLac.replace(defaultLac.length() - lac.length(), defaultLac.length(),lac);
            defaultCid.replace(defaultCid.length() - cid.length(), defaultCid.length(),cid);
            //CellIdentityGsm cell = new CellIdentityGsm();
            } catch (NullPointerException e) {
                log("Null pointer exception " + e);
            }

            try{
            TelephonyManager tm =(TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
            plmn = tm.getNetworkOperatorForPhone(event.getPhoneId());
            } catch (NullPointerException ex) {
                log("Null pointer exception " + ex);
            } catch (Exception ex) {
                log("Exception ex ="+ex);
            }
            isEmsSupport = 1;
            log("plmn =" + plmn);

            log("rat info send =");
        }
        log("rat info send rat=" + rat + "plmn" + plmn + "lac" + lac + "cid "
                + cid);
        event.putByte(rat);
        event.putBytes(new byte[3]);
        event.putString(plmn, 8);
        event.putString(defaultLac.toString(), 8);
        event.putString(defaultCid.toString(), 12);
        event.putString(non3gppId, 64);// null string for 3gpp
        event.putByte(isEmsSupport);
        event.putBytes(new byte[3]);
    }

    
    private static int getActiveMDRat() {
        int MD_TYPE_LWG = 5;
        int MD_TYPE_WG = 3;
        int MD_TYPE_UNKNOWN = 0;
        int worldPhone = SystemProperties.getInt("ro.mtk_world_phone", 0);
        int activeModem = Integer.valueOf(
                SystemProperties.get(TelephonyProperties.PROPERTY_ACTIVE_MD, Integer.toString(MD_TYPE_UNKNOWN)));
        if (worldPhone == 1) {
            //activeModem = ModemSwitchHandler.getActiveModemType();
            if (activeModem == MD_TYPE_LWG || activeModem == MD_TYPE_WG) {
                // when get ril.nw.rat.detail is 1(GSM), change RAT type to 4G_FDD
                return RAT_TYPE_LTE_FDD; //4G_FDD;
            } else {
                // when get ril.nw.rat.detail is 1(GSM), change RAT type to 4G_TDD
                return RAT_TYPE_LTE_TDD; //4G_TDD;
            }
        }
        return RAT_TYPE_LTE_TDD; //4G_TDD;
    }
    // TODO: add this code later [start]
    /*
    IImsManagerService getImsService() {
        IImsManagerService service = null;
        int retryCount = 0;
        do {
            try {
                IBinder b = ServiceManager.getService(Context.IMS_SERVICE);
                service = IImsManagerService.Stub.asInterface(b);
                if (service == null) {
                    loge("getImsService IBinder is null");
                    Thread.sleep(500);
                    retryCount++;
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        } while (service == null && retryCount < 6);

        return service;
    }
    */
    // TODO: add this code later [end]

    /**
     * This function will get Default Bearer properties for apn type.
     *
     * @param apnType input apn type for get the mapping default bearer properties
     * @param phoneId indicate input phoneId for MSIM
     * @return DedicateBearerProperties return the default beare properties for input apn type
     *                             return null if something wrong
     *
     */
    public static DedicateBearerProperties getDefaultBearerProperties(String apnType, int phoneId) {
        DedicateBearerProperties defaultBearerProp = null;
        try {
            defaultBearerProp = (ITelephonyEx.Stub.asInterface(
                    ServiceManager.getService(Context.TELEPHONY_SERVICE_EX))).getDefaultBearerProperties(apnType, phoneId);
        } catch (RemoteException ex) {
            log("getDefaultBearerProperties" +ex);
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            log("getDefaultBearerProperties" +ex);
            ex.printStackTrace();
        }
        log("getDefaultBearerProperties "+defaultBearerProp);
        return defaultBearerProp;
    }

    synchronized static void addVoLTEConnectedDedicatedBearer(DedicateBearerProperties prop) {
        mConnectedVoLTEDedicateBearerProp.add(prop);
    }
    synchronized static void clearVoLTEConnectedDedicatedBearer() {
        mConnectedVoLTEDedicateBearerProp.clear();
    } 
    synchronized static List<DedicateBearerProperties> getVoLTEConntectedDedicateBearer() {
        List<DedicateBearerProperties> propList = new ArrayList<DedicateBearerProperties>();
        for (DedicateBearerProperties prop : mConnectedVoLTEDedicateBearerProp) {
            propList.add(prop);
        }
        log("getVoLTEConntectedDedicateBearer value: " + propList);
        return Collections.unmodifiableList(propList);
    }
    synchronized static List<DedicateBearerProperties> getVoLTEConntectedDedicateBearer(int defaultCid) {
        List<DedicateBearerProperties> propList = new ArrayList<DedicateBearerProperties>();
        for (DedicateBearerProperties prop : mConnectedVoLTEDedicateBearerProp) {
            if (prop.defaultCid == defaultCid) {
                propList.add(prop);
            }
        }
        log("getVoLTEConntectedDedicateBearer for defaultCid: " + defaultCid +
            " value: " + propList);
        return Collections.unmodifiableList(propList);
    }
    synchronized static void removeVoLTEConntectedDedicateBearer(int cid, int defaultCid) {
        log("removeVoLTEConntectedDedicateBearer, cid: " + cid + " defaultCid: " + defaultCid +
            ", current connected size: " + mConnectedVoLTEDedicateBearerProp.size());
        for (Iterator <DedicateBearerProperties> it = mConnectedVoLTEDedicateBearerProp.iterator(); it.hasNext();) {
            DedicateBearerProperties prop = it.next();
            if (prop.cid == cid && prop.defaultCid == defaultCid) {
                it.remove();
                log("removed!!");
            }
        }
        log("removeVoLTEConntectedDedicateBearer, current connected size: " +
              mConnectedVoLTEDedicateBearerProp.size() + ", value: "
              + mConnectedVoLTEDedicateBearerProp); 
    }

    static void log(String text) {
        Xlog.d(TAG, "[dedicate] DataDispatcherUtil " + text);
    }

    private static void loge(String text) {
        Xlog.e(TAG, "[dedicate] DataDispatcherUtil " + text);
    }

    public class DefaultPdnActInd {
        // imcf_uint8                               transaction_id
        // imcf_uint8                               pad[3]
        // imc_eps_qos_struct                  ue_defined_eps_qos
        // imc_emergency_ind_enum       emergency_indidation
        // imc_pcscf_discovery_enum       pcscf_discovery_flag
        // imcf_uint8                               signaling_flag
        // imcf_uint8                               pad2[1]
        public int transactionId;
        public byte [] pad = new byte [3];
        public QosStatus qosStatus;
        public int emergency_ind;
        public int pcscf_discovery;
        public int signalingFlag;
        public byte [] pad2 = new byte [1];
        public int rat_type;
        public byte [] pad3 = new byte [3];
		

        @Override
        public String toString() {
            return "[transactionId=" + transactionId + ", Qos" + qosStatus + ", emergency_ind=" + emergency_ind +
                ", pcscf_discorvery=" + pcscf_discovery + ", signalingFlag=" + signalingFlag + "]";
        }
    }
    
    public class PdnDeactInd {
        // imcf_uint8                               transaction_id
        // imcf_uint8                               abort_activate_transaction_id
        // imcf_uint8                               context_id_is_valid
        // imcf_uint8                               context_id
        public int transactionId;
        public int abortTransactionId;
        public boolean isCidValid;
        public int cid;

        @Override
        public String toString() {
            return "[transactionId=" + transactionId + ", abortTransactionId=" + abortTransactionId + ", isCidValid=" + isCidValid + ", cid=" + cid + "]";
        }
    }
}
