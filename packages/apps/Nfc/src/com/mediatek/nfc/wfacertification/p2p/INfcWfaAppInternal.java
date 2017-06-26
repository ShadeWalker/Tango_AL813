package com.mediatek.nfc.wfacertification.p2p;

/** @hide */
public interface INfcWfaAppInternal {

    /** intent extra field, NFC Foreground Dispatch command */
    public static final String EXTRA_NFC_WFA_CMD =
            "com.mediatek.nfc.wfa.extra.WFA_CMD";

    /** NFC Foreground Dispatch command  */
    public static final int WFA_P2P_CMD         = 1;
    public static final int WRITE_TAG_CMD       = 2;
    public static final int WRITE_CFG_TAG_CMD   = 3;


    /** intent extra field, NFC Foreground Dispatch parcel*/
    public static final String EXTRA_NFC_WFA_P2P_INFO =
            "com.mediatek.nfc.wfa.extra.P2P_INFO";
    public static final String EXTRA_NFC_WFA_TAG_INFO =
            "com.mediatek.nfc.wfa.extra.TAG_INFO";

    // wfa.p2p CARRIER_TYPE
    public static final String WFA_P2P_CARRIER_TYPE = "application/vnd.wfa.p2p";


    /** intent extra field, NFC Foreground Dispatch command */
    public static final String EXTRA_NFC_WFA_INTERNAL_CMD =
            "com.mediatek.nfc.wfa.extra.WFA_INTERNAL_CMD";


    public static final int HANDOVER_FINISH_CMD     = 0x20;

};





