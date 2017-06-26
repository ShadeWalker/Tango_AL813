package com.huawei.systemmanager.preventmode;

interface IHoldPreventService {
    /*if user is phone, isPhoneUsed is true. other false*/
    boolean isPrevent(String phoneNumber,boolean isPhoneUsed);
    /* < DTS2013112917953 yulu/00203837 20131129 begin */
    /*Get all the white list phone numbers*/
    String[] queryAllWhiteListPhoneNo();
    /* DTS2013112917953 yulu/00203837 20131129 end > */
}
