/*< DTS2013120203869   lixue/00214442 20131202 begin */
package com.huawei.harassmentinterception.service;
import android.os.Bundle;

interface IHarassmentInterceptionService {

    /************************************************************************************************
    *  function:    set block phone numbers  
    *  parameter: blocknumberlist ，arraylist,key value：BLOCK_PHONENUMBER;
    *  Result： 0：success；others:   fail
    ************************************************************************************************/
    int setPhoneNumberBlockList(in Bundle blocknumberlist, int type, int source);
    // blocknumberlist ,arraylist,key value：BLOCK_PHONENUMBER
    //type:0:for sms and call,1:for sms;2:for call
    //source: 0:default; 1:manual; 2:cloud


    /************************************************************************************************
    *  function:    add phone number  to block list
    *  Result： 0：success；others:   fail
    ************************************************************************************************/
    int addPhoneNumberBlockItem(in Bundle blocknumber,int type, int source);
    // blocknumber ,string,key value：BLOCK_PHONENUMBER
    //type:0:for sms and call,1:for sms;2:for call
    //source: 0:default;1:manual;2:cloud


    /************************************************************************************************
    *  function:    remove phone number  from block list
    *  Result： 0：success；others:   faile
    ************************************************************************************************/
    int removePhoneNumberBlockItem(in Bundle blocknumber,int type, int source);
    // blocknumber ,string,key value：BLOCK_PHONENUMBER
    //type:0:for sms and call,1:for sms;2:for call
    //source: 0:default;1:manual;2:cloud


    /************************************************************************************************
    *  function:    query all phonenumber from block list
    *  Result： NULL: no data; others:   number list,  MAX VALUE: 200
    *******************************************************************************/
    String[] queryPhoneNumberBlockItem();
     

    /************************************************************************************************
    *  function:  to check if the phonenumber is in the block list
    *  Result： 0,1,2: the number is in list;  -1: not in the list
    *******************************************************************************/
    int checkPhoneNumberFromBlockItem(in Bundle checknumber,int type);
    // checknumber ,string,key value：CHECK_PHONENUMBER
    //type:-1:not blacklist,0:for sms and call,1:for sms;2:for call

    /************************************************************************************************
    *  function:    get block call record  
    ************************************************************************************************/
    void sendCallBlockRecords(in Bundle callBlockRecords);
    // callBlockRecords: key value：key: BLOCK_PHONENUMBER

    /************************************************************************************************
    *  function:    handle the sms deliver action before the default sms application to determine 
    if the sms should be blocked
    Result：-1: Invalid sms information; 0: Don't block the sms;  1: Block the sms 
    ************************************************************************************************/
    int handleSmsDeliverAction(in Bundle smsInfo);
    // smsInfo: key value：key: HANDLE_SMS_INTENT ,value: Intent of sms

    /* < DTS2014040902429 lixue/00214442 20140409 begin */
    /************************************************************************************************
    *  function:    handle the incoming call action to determine if the call should be blocked
    Result：-1: Invalid call information; 0: Don't block the call;  1: Block the call
    ************************************************************************************************/
    int handleIncomingCallAction(in Bundle callInfo);
    // smsInfo: key value：key: HANDLE_CALLINFO ,value: number of incoming call
    /* DTS2014040902429 lixue/00214442 20140409 end > */
}
/*  DTS2013120203869   lixue/00214442 20131202 end > */