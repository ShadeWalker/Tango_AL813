/*
* This Software is the property of VIA Telecom, Inc. and may only be used pursuant to a
license from VIA Telecom, Inc.
* Any unauthorized use inconsistent with the terms of such license is strictly prohibited.
* Copyright (c) 2013 -2015 VIA Telecom, Inc. All rights reserved.
*/

package com.android.internal.telephony.cdma.utk;


public interface BipConstants {

    //via defined

    //arg1
    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_ERROR = 1;

    public static final int RESULT_CODE_OK = 0; //ResultCode.OK
    //ResultCode.PRFRMD_WITH_MISSING_INFO
    public static final int RESULT_CODE_PRFRMD_WITH_MISSING_INFO = 1;
    public static final int RESULT_CODE_BIP_ERROR = 2; //ResultCode.BIP_ERROR
    //ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS
    public static final int RESULT_CODE_NETWORK_CRNTLY_UNABLE_TO_PROCESS = 3;
    //ResultCode.PRFRMD_WITH_MODIFICATION
    public static final int RESULT_CODE_RESULT_SUCCESS_PERFORMED_WITH_MODIFICATION = 4;
    //ResultCode.BEYOND_TERMINAL_CAPABILITY
    public static final int RESULT_CODE_BEYOND_TERMINAL_CAPABILITY = 5;

    public static final int BUFFER_SIZE_MAX = 1422;
    public static final int RECEIVE_DATA_MAX_LEN = 228;

    //for send/receive data
    public static final int ADDITIONAL_INFO_CHANNEL_CLOSED = 0x01;
    public static final int ADDITIONAL_INFO_CHANNEL_ID_NOT_AVAILABLE = 0x02;
    public static final int ADDITIONAL_INFO_CHANNEL_NO_DATA = 0x03;

    //see class ChannelStatus
    public static final int CHANNEL_STATUS_NO_LINK = 0x0;
    public static final int CHANNEL_STATUS_LISTEN  = 0x01;
    public static final int CHANNEL_STATUS_LINKED  = 0x02;
    public static final int CHANNEL_STATUS_ERROR   = 0x03;

    ///////////////////////////////////////////////////////////////////////////

    //etsi
    //timer management
    /*
    bits 1 to 2: 00 = start;
                 01 = deactivate;
                 10 = get current value;
                 11 = RFU.
    */
    public static final int BIP_TIMER_MANAGEMENT_START = 0x00;
    public static final int BIP_TIMER_MANAGEMENT_DEACTIVATE = 0x01;
    public static final int BIP_TIMER_MANAGEMENT_GET_VALUE = 0x02;

    //ip type
    public static final int BIP_OTHER_ADDRESS_TYPE_IPV4 = 0x21;
    public static final int BIP_OTHER_ADDRESS_TYPE_IPV6 = 0x57;

    //Transport protocol type
    public static final int TRANSPORT_TYPE_UDP_CLIENT_REMOTE = 0x01;
    public static final int TRANSPORT_TYPE_TCP_CLIENT_REMOTE = 0x02;
    public static final int TRANSPORT_TYPE_TCP_SERVER = 0x03;
    public static final int TRANSPORT_TYPE_UDP_CLIENT_LOCAL = 0x04;
    public static final int TRANSPORT_TYPE_TCP_CLIENT_LOCAL = 0x05;

    /* Bearer
    The terminal shall use this list to choose which bearers are allowed in order of priority
    - '00' = short message;
    - '01' = circuit switched data;
    - '02' = reserved for GSM/3G;
    - '03' = packet switched;
    - '04' to 'FF' = RFU.
    */
    public static final int BIP_BEARER_SMS = 0x00;
    public static final int BIP_BEARER_CS = 0x01;
    public static final int BIP_BEARER_GSM_RESERVED = 0x02;
    public static final int BIP_BEARER_PACKET_DATA = 0x03;

    //bearer description, bearer type
    public static final int BEARER_TYPE_GSM_3GPP_1 = 0x01;
    public static final int BEARER_TYPE_GSM_3GPP_2 = 0x02;
    public static final int BEARER_TYPE_DEFAULT = 0x03;
    public static final int BEARER_TYPE_LOCAL_LINK = 0x04;
    public static final int BEARER_TYPE_BLUETOOTH = 0x05;
    public static final int BEARER_TYPE_IRDA = 0x06;
    public static final int BEARER_TYPE_RS232 = 0x07;
    public static final int BEARER_TYPE_PACKET_DATA = 0x08;
    public static final int BEARER_TYPE_PACKET_USB = 0x10;

    //additional info
    public static final int CHANNEL_STATUS_INFO_NO_INFO = 0x00;
    public static final int CHANNEL_STATUS_INFO_LINK_DROPED = 0x05;

    /*8.27
    '00' = Normal service;
    '01' = Limited service;
    '02' = No service.
    */
    public static final int SERVICE_STATE_NORMAL = 0;
    public static final int SERVICE_STATE_LIMITED = 1;
    public static final int SERVICE_STATE_NOSERVICE = 2;

    public static final int BIP_TIME_UINIT_MINUTERS = 0x0;
    public static final int BIP_TIME_UINIT_SECONDS = 0x1;
    public static final int BIP_TIME_UINIT_TENTH_SECONDS = 0x2;
}

