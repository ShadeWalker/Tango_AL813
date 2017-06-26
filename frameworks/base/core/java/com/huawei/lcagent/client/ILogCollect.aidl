package com.huawei.lcagent.client;
import com.huawei.lcagent.client.LogMetricInfo;

interface ILogCollect{
	/**
     * log snippet submit interface
     */
    int setMetricStoargeHeader(int metricID, in byte[]payloadBytes, int payloadLen);

    int setMetricCommonHeader(int metricID, in byte[]payloadBytes, int payloadLen);

    int setMetricStoargeTail(int metricID, in byte[] payloadBytes, int payloadLen);

    int submitMetric(int metricID, int level, in byte[] payloadBytes, int payloadLen);

    boolean shouldSubmitMetric(int metricID, int level);
    /**
     * capture log interface
     */
    LogMetricInfo captureLogMetric(int metricID);

    void clearLogMetric(long id);

    int feedbackUploadResult(long hashId, int status);

    /**
     * setting interface
     */
    int allowUploadInMobileNetwork (boolean allow);

    int allowUploadAlways (boolean allow);

    int configureUserType(int type);
    
    int getUserType();

    int forceUpload();

    int configure(String strCommand);

    /**
     * capture all log interface, for auto test script.
     */
    LogMetricInfo captureAllLog();

    long getFirstErrorTime();

    int resetFirstErrorTime();
    /**
     * get type of error which is first happen.
     */
    String getFirstErrorType();

    int configureModemlogcat(int mode, String parameters);
    int configureBluetoothlogcat(int enable, String parameters);
    int configureLogcat(int enable, String parameters);
    int configureAPlogs(int enable);
    int configureCoredump(int enable);
    int configureGPS(int enable);
    void configureWithPara(String cmd, String parameters);
}