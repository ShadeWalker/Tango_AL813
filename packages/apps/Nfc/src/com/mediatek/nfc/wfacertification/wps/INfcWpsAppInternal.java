package com.mediatek.nfc.wfacertification.wps;

/** @hide */
public interface INfcWpsAppInternal {

    /** intent extra field, NFC Foreground Dispatch command */
    public static final String EXTRA_NFC_WPS_CMD =
            "com.mediatek.nfc.wps.extra.WPS_CMD";

    /** intent extra field, NFC Foreground Dispatch parcel*/
    public static final String EXTRA_NFC_WPS_CONFIGURATION_TOKEN =
            "com.mediatek.nfc.wps.extra.WPS_CONFIGURATION_TOKEN";
    public static final String EXTRA_NFC_WPS_PWD_TOKEN =
            "com.mediatek.nfc.wps.extra.WPS_PWD_TOKEN";

    public static final String EXTRA_NFC_HANDOVER_PAYLOAD =
            "com.mediatek.nfc.wps.extra.WPS_HANDOVER_PAYLOAD";


    /** NFC Foreground Dispatch command  */
    public static final int UNKNOWN_CMD                     = 0;
    public static final int READ_CONFIGURATION_TOKEN_CMD     = 1;
    public static final int WRITE_CONFIGURATION_TOKEN_CMD    = 2;
    public static final int READ_PASSWORD_TOKEN_CMD          = 3;
    public static final int WRITE_PASSWORD_TOKEN_CMD         = 4;
    public static final int ER_READ_PASSWORD_TOKEN_CMD       = 5;

    public static final int WPS_P2P_AUTOGO_AS_SEL_CMD       = 6; //Wi-Fi Direct test item:6.1.18

    //public static final int READ_ALL_TAG_CMD                =7;
    public static final int WPS_HANDOVER_REQUEST_CMD        = 8;
    public static final int WPS_HANDOVER_SELECT_CMD         = 9;

    public static final int SIGMA_HANDOVER_EXECUTION_CMD    = 0x0a;  //EXTRA_NFC_WPS_CMD category


    public static final int TEST_WRITE_HS_CONFIGURATION_TOKEN_CMD = 0x10;



    /** intent extra field, NFC Foreground Dispatch command */
    public static final String EXTRA_NFC_WPS_INTERNAL_CMD =
            "com.mediatek.nfc.wps.extra.WPS_INTERNAL_CMD";

    public static final int HANDOVER_REQUEST_CMD    = 0x10;
    public static final int HANDOVER_SELECT_CMD     = 0x11;
    public static final int HANDOVER_FINISH_CMD     = 0x12;





};





