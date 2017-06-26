package com.huawei.lcagent.client;

public class MetricConstant {
    /**
     * Log level A, permitted to send on commercial version.
     */
    public static final int LEVEL_A = 0x1;
    /**
     * Log level B, permitted to send on Huawei fans version.
     */
    public static final int LEVEL_B = 0x10;
    /**
     * Log level C, permitted to send on Beta test version.
     */
    public static final int LEVEL_C = 0x100;
    /**
     * Log level D, permitted to send on internal version.
     */
    public static final int LEVEL_D = 0x1000;

    /**
     * Minimum value for log metric ID.
     */
    public static final int METRIC_ID_MIN = 0x0;
    /**
     * Log metric ID for Radio.
     */
    public static final int RADIO_METRIC_ID = 0x1;
    /**
     * Log metric ID for Reset.
     */
    public static final int REBOOT_METRIC_ID = 0x2;
    /**
     * Log metric ID for App.
     */
    public static final int APP_METRIC_ID = 0x3;
    /**
     * Log metric ID for Touch Screen not response.
     */
    public static final int TOUCH_METRIC_ID = 0x4;
    /**
     * Log metric ID for Data Service.
     */
    public static final int INTERNET_METRIC_ID = 0x5;
    /**
     * Log metric ID for Sim card.
     */
    public static final int SIM_METRIC_ID = 0x6;
    /**
     * Log metric ID for Call related.
     */
    public static final int CALL_METRIC_ID = 0x7;
    /**
     * Log metric ID for Charger or Battery related.
     */
    public static final int BATTERY_METRIC_ID = 0x8;
    /**
     * APR metric ID
     */
    public static final int APR_STATISTICS_METRIC_ID = 0x9;
    /**
     * TEMPERATURE metric ID
     */
    public static final int METRIC_ID_TEMPERATURE = 10;
    /**
     * JANK metric ID
     */
    public static final int JANK_METRIC_ID = 0xB;
    /**
     * LogTrack metric ID
     */
    public static final int LOG_TRACK_METRIC_ID = 0xC;
    /**
     * AUDIO metric ID
     */
    public static final int AUDIO_METRIC_ID = 0xD;
    /**
     * GPS metric ID
     */
    public static final int GPS_METRIC_ID = 0xE;
    /**
     * WiFi metric ID
     */
    public static final int WIFI_METRIC_ID = 0xF;

    public static final int EX_METRIC_ID_MIN = 100;
    public static final int OTHER_METRIC_ID_EX = 100;
    public static final int REBOOT_METRIC_ID_EX = 101;
    public static final int COMMUNICATION_METRIC_ID_EX = 102;
    public static final int APP_METRIC_ID_EX = 103;
    public static final int CAMERA_METRIC_ID_EX = 104;
    public static final int POWER_METRIC_ID_EX = 105;
    public static final int WIFI_METRIC_ID_EX = 106;
    public static final int BLUETOOTH_METRIC_ID_EX = 107;
    public static final int GPS_METRIC_ID_EX = 108;
    public static final int SCREEN_METRIC_ID_EX = 109;
    public static final int SDCARD_METRIC_ID_EX = 110;
    public static final int EX_METRIC_ID_MAX = 110;
    /**
     * Maximum value for log metric ID.
     */
    public static final int METRIC_ID_MAX = 0x100;

    /**
     * validate the input integer.
     */
    public static boolean isValidMetricId(int id) {
        if (id >= METRIC_ID_MAX || id <= METRIC_ID_MIN) {
            return false;
        }

        return true;
    }

    /**
     * get String name of metric ID.
     */
    public static String getStringID(int metricId) {
        String result = null;
        switch (metricId) {
        case MetricConstant.RADIO_METRIC_ID:
            result = "LOG_CHR";
            break;
        case MetricConstant.GPS_METRIC_ID:
            result = "GPS_CHR";
            break;
        case MetricConstant.WIFI_METRIC_ID:
            result = "WIFI_CHR";
            break;
        case MetricConstant.REBOOT_METRIC_ID:
            result = "Reboot";
            break;
        case MetricConstant.APP_METRIC_ID:
            result = "App";
            break;
        case MetricConstant.TOUCH_METRIC_ID:
            result = "Touch";
            break;
        case MetricConstant.INTERNET_METRIC_ID:
            result = "Internet";
            break;
        case MetricConstant.SIM_METRIC_ID:
            result = "Sim";
            break;
        case MetricConstant.CALL_METRIC_ID:
            result = "Call";
            break;
        case MetricConstant.BATTERY_METRIC_ID:
            result = "Battery";
            break;
        default:
            result = String.valueOf(metricId);
            break;
        }
        return result;
    }

    /**
     * Intent for client to submit Log.
     */
    public static final String ACTION_SUBMIT_METRIC_INTENT = "com.huawei.lcagent.client.ACTION_SUBMIT_METRIC_INTENT";

    /**
     * Intent for client to submit Log.
     */
    public static final String ACTION_UPLOAD_REQUEST_INTENT = "com.huawei.lcagent.UPLOAD_REQUEST";

    /**
     * Intent for client to submit Log result.
     */
    public static final String ACTION_UPLOAD_RESULT_INTENT = "com.huawei.lcagent.UPLOAD_RESULT";
    /**
     * Intent for client to resume Log.
     */
    public static final String ACTION_RESUME_UPLOAD_INTENT = "com.huawei.lcagent.RESUME_UPLOAD";
    /**
     * Intent for client to set advanced switch for grabbing log
     */
    public static final String ACTION_POLICY_CONFIGURE_INTENT = "com.huawei.lcagent.POLICY_CONFIGURE";
    public static final String ACTION_POLICY_CONF_RESULT_INTENT = "com.huawei.lcagent.POLICY_CONF_RESULT";
    
    public static final int MANUAL_OFF = 0x0;
    public static final int MANUAL_ON = 0x1;
    public static final int AUTO_OFF = 0x2;
    public static final int AUTO_ON = 0x3;
    public static final int SWITCH_OFF = 0x0;
    public static final int SWITCH_ON = 0x1;
    public static final int MANUAL_MODE = 0x0;
    public static final int AUTO_MODE = 0x1;   
}
