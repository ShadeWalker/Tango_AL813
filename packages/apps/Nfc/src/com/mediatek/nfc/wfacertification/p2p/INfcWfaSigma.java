package com.mediatek.nfc.wfacertification.p2p;

/** @hide */
public interface INfcWfaSigma {

    //Stacit Handover related
    //r4 modify
    final public String MTK_NFC_WFA_SIGMA_ALL_TAG_READ_ACTION           = "mtk.nfc.wfa.sigma.all.tag.READ_ACTION";
    final public String MTK_NFC_WFA_SIGMA_WPS_PWD_TAG_RECEIVE_ACTION    = "mtk.nfc.wfa.sigma.wps.pwd.tag.RECEIVE_ACTION";
    final public String MTK_NFC_WFA_SIGMA_WPS_PWD_TAG_WRITE_ACTION      = "mtk.nfc.wfa.sigma.wps.pwd.tag.WRITE_ACTION";

    //final public String MTK_NFC_WFA_TAG_WRITE_ACTION = "mtk.nfc.wfa.tag.WRITE_ACTION";//The device want to write Tag which includes WFA static handover info

    final public String MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_WRITE_ACTION      = "mtk.nfc.wfa.sigma.wps.cfg.tag.WRITE_ACTION"; //The device want to write Configuration Tag when it acts as GO
    final public String MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_READ_ACTION      = "mtk.nfc.wfa.sigma.wps.cfg.tag.READ_ACTION";
    final public String MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_RECEIVE_ACTION    = "mtk.nfc.wfa.sigma.wps.cfg.tag.RECEIVE_ACTION"; //The device read Tag with WFA static handover info.

    final public String MTK_NFC_WFA_SIGMA_TAG_EXTRA_DEV_INFO = "mtk.nfc.wfa.sigma.tag.extra.DEV_INFO"; //byte array

    //WPS related
    final public String MTK_NFC_WFA_SIGMA_WPS_HR_ACTION = "mtk.nfc.wfa.sigma.wps.HR_ACTION";
    final public String MTK_NFC_WFA_SIGMA_WPS_HS_ACTION = "mtk.nfc.wfa.sigma.wps.HS_ACTION";

    final public String MTK_NFC_WFA_SIGMA_WPS_HR_RECEIVE_ACTION = "mtk.nfc.wfa.sigma.wps.HR_RECEIVE_ACTION";
    final public String MTK_NFC_WFA_SIGMA_WPS_HS_RECEIVE_ACTION = "mtk.nfc.wfa.sigma.wps.HS_RECEIVE_ACTION";

    final public String MTK_NFC_WFA_SIGMA_WPS_EXTRA_HR_P2P_DEV_INFO = "mtk.nfc.wfa.sigma.wps.extra.HR_P2P_DEV_INFO"; //byte array
    final public String MTK_NFC_WFA_SIGMA_WPS_EXTRA_HS_P2P_DEV_INFO = "mtk.nfc.wfa.sigma.wps.extra.HS_P2P_DEV_INFO"; //byte array

    //P2P Negotiated Handover related
    final public String MTK_NFC_WFA_SIGMA_P2P_HR_ACTION = "mtk.nfc.wfa.sigma.p2p.HR_ACTION";
    //final public String MTK_NFC_WFA_P2P_HS_ACTION = "mtk.nfc.wfa.p2p.HS_ACTION";

    final public String MTK_NFC_WFA_SIGMA_P2P_HR_RECEIVE_ACTION = "mtk.nfc.wfa.sigma.p2p.HR_RECEIVE_ACTION";
    final public String MTK_NFC_WFA_SIGMA_P2P_HS_RECEIVE_ACTION = "mtk.nfc.wfa.sigma.p2p.HS_RECEIVE_ACTION";

    final public String MTK_NFC_WFA_SIGMA_P2P_EXTRA_HR_P2P_DEV_INFO = "mtk.nfc.wfa.sigma.p2p.extra.HR_P2P_DEV_INFO"; //byte array
    final public String MTK_NFC_WFA_SIGMA_P2P_EXTRA_HS_P2P_DEV_INFO = "mtk.nfc.wfa.sigma.p2p.extra.HS_P2P_DEV_INFO"; //byte array


    //P2P Static Handover related
    final public String MTK_NFC_WFA_SIGMA_P2P_TAG_WRITE_ACTION      = "mtk.nfc.wfa.sigma.p2p.tag.WRITE_ACTION";

    final public String MTK_NFC_WFA_SIGMA_P2P_TAG_READ_ACTION       = "mtk.nfc.wfa.sigma.p2p.tag.READ_ACTION";
    final public String MTK_NFC_WFA_SIGMA_P2P_TAG_RECEIVE_ACTION    = "mtk.nfc.wfa.sigma.p2p.tag.RECEIVE_ACTION";

    final public String MTK_NFC_WFA_SIGMA_P2P_TAG_EXTRA_INFO        = "mtk.nfc.wfa.sigma.p2p.tag.EXTRA_INFO"; //byte array


    /** intent extra field, NFC Wfa Sigma Foreground Dispatch command */
    public static final String EXTRA_NFC_SIGMA_CMD =
            "com.mediatek.nfc.wfa.sigma.extra.SIGMA_CMD";

    /** NFC Foreground Dispatch command  */
    //public static final int WFA_P2P_CMD         =1;
    //public static final int WRITE_TAG_CMD       =2;
    public static final int READ_ALL_TAG_CMD   = 1;

    /** intent extra field, NFC Wfa Sigma Foreground Dispatch command */
    public static final String EXTRA_ON_NEW_INTENT_CMD =
            "com.mediatek.nfc.wfa.sigma.extra.ON_NEW_INTENT_CMD";


    public static final int HANDOVER_FINISH_CMD     = 0x20;


};





