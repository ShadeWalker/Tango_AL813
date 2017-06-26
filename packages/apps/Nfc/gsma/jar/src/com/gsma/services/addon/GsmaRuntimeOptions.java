package com.gsma.services.addon;

//import com.mediatek.nfc.configutil.ConfigUtil;

public class GsmaRuntimeOptions {
    static public ConfigUtil.IParser sConfigFileParser;

    static private final String CFG_FILE_PATH = "system/etc/gsma.cfg";
    static private final String CFG_FILE_RULES[] = {
        /**
         *  MultiSE related config
         */
        "VERSION=1:3.0=1,4.1=2,5.0=3,6.0=4",

    };

    /**
     * Options
     */
    static private final int GSMA_VERSION = 1;
         
    static private final int NON_NFC_SIM_POPUP = 5;
    static private final int EVT_BROADCAST_AC = 6;
    static private final int BUNDLE_SIM_STATE = 7;
    static private final int NO_EMU_IN_FLYMODE = 8;
    static private final int BEAM_SEND_FAIL_CNT = 9;
    static private final int BEAM_RECV_FAIL_CNT = 10;
    static private final int BEAM_SEND_SLEEP_TIME = 11;
    static private final int BEAM_RECV_SLEEP_TIME = 12;
    static private final int BEAM_SETUP_CONNECTIION_TIME = 13;
    static private final int GSMA_EVT_BROADCAST = 14;
    static private final int SEAPI_SUPPORT_CMCC = 15;
    static private final int HCE_DEFAULT_ROUTE_HOST = 16;

    /**
     * Values
     */
    static private final int GSMA_VERSION_VALUE[] = { 0x0, 0x30, 0x41, 0x50, 0x60, };
         
    static private final int NO = 0;
    static private final int YES = 1;
    static private final int TABLE_BEAM_SEND_FAIL_CNT[] = {0, 5, 10, 30, 40, 50, 60, 70, 80, 90, 100};
    static private final int TABLE_BEAM_RECV_FAIL_CNT[] = {0, 5, 10, 30, 40, 50, 60, 70, 80, 90, 100};
    static private final int TABLE_BEAM_SEND_SLEEP_TIME[] = {0, 2, 5, 10, 15, 20, 30, 40, 50, 60, 100};
    static private final int TABLE_BEAM_RECV_SLEEP_TIME[] = {0, 2, 5, 10, 15, 20, 30, 40, 50, 60, 100};

    static private final int TABLE_BEAM_SETUP_CONNECTION_TIME[] = {    0, 20000, 25000, 30000, 35000,
                                                                   40000, 45000, 50000, 55000, 60000};
    /**
     * EVT_BROADCAST_AC values
     */
    static private final int EVT_AC_DEFAULT = 0;
    static private final int EVT_AC_BYPASS = 1;

    static {
        sConfigFileParser = ConfigUtil.createParser(CFG_FILE_RULES);
        sConfigFileParser.parse(CFG_FILE_PATH);
    }

    static public ConfigUtil.IParser getParser() {
        return sConfigFileParser;
    }

    static public int getGsmaVersion() {
        int userConfig[] = new int[1];
        int ret = 0;
        try {
            if (sConfigFileParser.get(GSMA_VERSION, userConfig)) {
                ret = GSMA_VERSION_VALUE[userConfig[0]];
            }       } catch (Exception e) { }
        return ret;
    }


    static public boolean isGsmaEvtBroadcast() {
        int userConfig[] = new int[1];
        boolean ret = false;
        try {
            if (sConfigFileParser.get(GSMA_EVT_BROADCAST, userConfig)) {
                ret = (userConfig[0] == YES) ? true : false;
            }
        } catch (Exception e) { }
        return ret;
    }

}
