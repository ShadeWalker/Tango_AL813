package com.huawei.lcagent.client;

public class Configuration {
    /**
     * transmission channel FTP
     */
    public static final int FTP_CHANNEL = 0x1;
    /**
     * transmission channel Email
     */
    public static final int EMAIL_CHANNEL = 0x2;
    /**
     * transmission channel Https
     */
    public static final int HTTPS_CHANNEL = 0x3;
    /**
     * current version for commercial user
     */
    public static final int COMMERCIAL_USER = 0x1;
    /**
     * current version for commercial user
     */
    public static final int FANS_USER = 0x2;
    /**
     * current version for Beta test user
     */
    public static final int BETA_USER = 0x3;
    /**
     * current version for test user and R&D engineer
     */
    public static final int TEST_USER = 0x4;
    /**
     * current log can upload via WiFi
     */
    public static final int WIFI_UPLOAD_FLAGS = 0x1;
    /**
     * current log can upload via 3G wireless network
     */
    public static final int UPLOAD_ON_3G_FLAGS = 0x2;
    /**
     * current log can upload via 2G wireless network
     */
    public static final int UPLOAD_ON_2G_FLAGS = 0x4;
}
