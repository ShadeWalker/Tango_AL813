package com.mediatek.nfc.wfacertification;

/** @hide */
public interface INfcWfaIntent {

    public interface INfcWpsTestBed {

            final public String MTK_WPS_NFC_TESTBED_R_CONFIGURATION_ACTION = "mtk.wps.nfc.testbed.r.configuration";
            final public String MTK_WPS_NFC_TESTBED_W_PASSWORD_ACTION = "mtk.wps.nfc.testbed.w.password";
            final public String MTK_WPS_NFC_TESTBED_R_PASSWORD_ACTION = "mtk.wps.nfc.testbed.r.password";
            final public String MTK_WPS_NFC_TESTBED_W_CONFIGURATION_ACTION = "mtk.wps.nfc.testbed.w.configuration";

            final public String MTK_WPS_NFC_TESTBED_ER_R_PASSWORD_ACTION = "mtk.wps.nfc.testbed.externalRegistrar.r.password";


            final public String MTK_WPS_NFC_TESTBED_CONFIGURATION_RECEIVED_ACTION = "mtk.wps.nfc.testbed.configuration.received";
            final public String MTK_WPS_NFC_TESTBED_PASSWORD_RECEIVED_ACTION = "mtk.wps.nfc.testbed.password.received";

            final public String MTK_WPS_NFC_TESTBED_ER_PASSWORD_RECEIVED_ACTION = "mtk.wps.nfc.testbed.externalRegistrar.password.received";


            final public String MTK_WPS_NFC_TESTBED_HR_ACTION = "mtk.wps.nfc.testbed.hr";
            final public String MTK_WPS_NFC_TESTBED_HS_ACTION = "mtk.wps.nfc.testbed.hs";

            final public String MTK_WPS_NFC_TESTBED_HR_RECEIVED_ACTION = "mtk.wps.nfc.testbed.hr.received";
            final public String MTK_WPS_NFC_TESTBED_HS_RECEIVED_ACTION = "mtk.wps.nfc.testbed.hs.received";

            final public String MTK_WPS_NFC_TESTBED_P2P_AUTOGO_AS_SEL_ACTION = "mtk.wps.nfc.testbed.p2pgo.as.sel"; //Wi-Fi Direct test item:6.1.18


            final public String MTK_WPS_NFC_TESTBED_EXTRA_CONFIGURATION = "mtk.wps.nfc.testbed.extra.configuration";
            final public String MTK_WPS_NFC_TESTBED_EXTRA_PASSWORD = "mtk.wps.nfc.testbed.extra.password";
            final public String MTK_WPS_NFC_TESTBED_EXTRA_CREDENTIAL = "mtk.wps.nfc.testbed.extra.credential";


    };

    public interface INfcWfaP2p {

        //Stacit Handover related
        final public String MTK_NFC_WFA_TAG_WRITE_ACTION = "mtk.nfc.wfa.tag.WRITE_ACTION"; //The device want to write Tag which includes WFA static handover info
        final public String MTK_NFC_WFA_TAG_RECEIVE_ACTION = "mtk.nfc.wfa.tag.RECEIVE_ACTION"; //The device read Tag with WFA static handover info.

        final public String MTK_NFC_WFA_CFG_TAG_WRITE_ACTION = "mtk.nfc.wfa.cfg.tag.WRITE_ACTION"; //The device want to write Configuration Tag when it acts as GO

        final public String MTK_NFC_WFA_TAG_EXTRA_DEV_INFO = "mtk.nfc.wfa.tag.extra.DEV_INFO"; //byte array,same as HS_P2P_DEV_INFO

        //P2p related
        final public String MTK_NFC_WFA_P2P_HR_ACTION = "mtk.nfc.wfa.p2p.HR_ACTION";
        //final public String MTK_NFC_WFA_P2P_HS_ACTION = "mtk.nfc.wfa.p2p.HS_ACTION";

        final public String MTK_NFC_WFA_P2P_HR_RECEIVE_ACTION = "mtk.nfc.wfa.p2p.HR_RECEIVE_ACTION";
        final public String MTK_NFC_WFA_P2P_HS_RECEIVE_ACTION = "mtk.nfc.wfa.p2p.HS_RECEIVE_ACTION";

        final public String MTK_NFC_WFA_P2P_EXTRA_HR_P2P_DEV_INFO = "mtk.nfc.wfa.p2p.extra.HR_P2P_DEV_INFO"; //byte array
        final public String MTK_NFC_WFA_P2P_EXTRA_HS_P2P_DEV_INFO = "mtk.nfc.wfa.p2p.extra.HS_P2P_DEV_INFO"; //byte array
    };


}


