package com.huawei.lcagent.client;

public class UploadStatus {
    /**
     * Client upload log file successfully
     */
    public static final int SUCESSS = 0x0;
    /**
     * Client upload log file failed with unknown reason
     */
    public static final int UNKNOWN_ERROR = 0x1;
    /**
     * Client upload log file fail with unknown reason
     */
    public static final int NETWORK_UNAVAILABLE = 0x2;
}
