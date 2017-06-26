package com.huawei.lcagent.client;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import com.huawei.lcagent.client.ILogCollect;
public class LogCollectManager {

    private static final String TAG = "LogCollectManager";
    private Context mContext = null;

    public static final int ERROR_OTHER = -2;
    public static final int ERROR_SERVICE_NOT_CONNECTED = -1;
    public static final int SUCCESS = 0;
    /*< DTS2014121702804 linchenwei/00293626 20141218 begin */
    public static final int ALREADY_DONE = 1;
    public static final int FAIL = -3;
    /* DTS2014121702804 linchenwei/00293626 20141218 end >*/
    /*< DTS2014120100636 niexingxing/00271341 20141201 begin */
    private CallBack mCallerCallback = null;

    public interface CallBack {
        void serviceConnected();
    }
    public LogCollectManager(Context context) {
        mContext = context.getApplicationContext();
        bindToService(mContext);
    }

    public LogCollectManager(Context context, LogCollectManager.CallBack callBack) {
        mCallerCallback = callBack;
        mContext = context.getApplicationContext();
        bindToService(mContext);
    }
    /* DTS2014120100636 niexingxing/00271341 20141201 end >*/

    /**
     * log snippet submit interface
     * 
     * @throws RemoteException
     */
    public int setMetricStoargeHeader(int metricID, byte[] payloadBytes, int payloadLen) throws RemoteException {
        if (payloadBytes == null || payloadBytes.length < payloadLen) {
            return ERROR_OTHER;
        }

        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }

        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }

        return iLogCollect.setMetricStoargeHeader(metricID, payloadBytes, payloadLen);
    }

    public int setMetricStoargeHeader(int metricID, byte[] payloadBytes) throws RemoteException {
        return setMetricStoargeHeader(metricID, payloadBytes, payloadBytes.length);
    }

    public int setMetricStoargeTail(int metricID, byte[] payloadBytes, int payloadLen) throws RemoteException {
        if (payloadBytes == null || payloadBytes.length < payloadLen) {
            return ERROR_OTHER;
        }

        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.setMetricStoargeTail(metricID, payloadBytes, payloadLen);
    }

    public int setMetricStoargeTail(int metricID, byte[] payloadBytes) throws RemoteException {
        return setMetricStoargeTail(metricID, payloadBytes, payloadBytes.length);
    }

    public int setMetricCommonHeader(int metricID, byte[] payloadBytes, int payloadLen) throws RemoteException {
        if (payloadBytes == null || payloadBytes.length < payloadLen) {
            return ERROR_OTHER;
        }

        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.setMetricCommonHeader(metricID, payloadBytes, payloadLen);
    }

    public int setMetricCommonHeader(int metricID, byte[] payloadBytes) throws RemoteException {
        return setMetricCommonHeader(metricID, payloadBytes, payloadBytes.length);
    }

    public int submitMetric(int metricID, int level, byte[] payloadBytes, int payloadLen) throws RemoteException {

        if (payloadBytes == null || payloadBytes.length < payloadLen) {
            return ERROR_OTHER;
        }

        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.submitMetric(metricID, level, payloadBytes, payloadLen);
    }

    public int submitMetric(int metricID, int level, byte[] payloadBytes) throws RemoteException {
        return submitMetric(metricID, level, payloadBytes, payloadBytes.length);
    }

    public boolean shouldSubmitMetric(int metricID, int level) throws RemoteException {
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return false;
            }
        }
        if (iLogCollect == null) {
            return false;
        }
        return iLogCollect.shouldSubmitMetric(metricID, level);
    }

    /**
     * capture log interface
     */
    public LogMetricInfo captureLogMetric(int metricID) throws RemoteException {
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return null;
            }
        }
        if (iLogCollect == null) {
            return null;
        }
        return iLogCollect.captureLogMetric(metricID);
    }

    public LogMetricInfo captureAllLog() throws RemoteException {
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return null;
            }
        }
        if (iLogCollect == null) {
            return null;
        }
        return iLogCollect.captureAllLog();
    }

    public void clearLogMetric(long id) throws RemoteException {
        if (iLogCollect == null) {

            if (bindToService(mContext) == false) {
                return;
            }
        }
        if (iLogCollect == null) {
            return;
        }
        iLogCollect.clearLogMetric(id);
        return;
    }

    /**
     * setting interface
     */
    public int allowUploadInMobileNetwork(boolean allow) throws RemoteException {
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.allowUploadInMobileNetwork(allow);
    }

    public int allowUploadAlways(boolean allow) throws RemoteException {
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.allowUploadAlways(allow);
    }

    public int configureUserType(int type) throws RemoteException {
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.configureUserType(type);
    }

    public int forceUpload() throws RemoteException {
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.forceUpload();
    }

    /**
     * Client will call this to notify service the upload status of log
     * submission.
     */
    public int feedbackUploadResult(long hashId, int status) throws RemoteException {
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.feedbackUploadResult(hashId, status);
    }
    /**
     * Client process command from server.
     */
    public int configure(String strCommand) throws RemoteException {
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.configure(strCommand);
    }

    /**
     * Client get current user type.
     */
    public int getUserType() throws RemoteException {
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.getUserType();
    }

    public boolean bindToService(Context context) {
        Log.i(TAG, "start to bind to Log Collect service");
        if (mContext == null) {
            Log.e(TAG, "mContext == null");
            return false;
        }
        Intent serviceIntent = new Intent("com.huawei.lcagent.service.ILogCollect");
        /*< DTS2014122300493 niexingxing/00271341 20141203 begin */
        /*< DTS2014120100636 niexingxing/00271341 20141201 begin */
        serviceIntent.setClassName("com.huawei.lcagent", "com.huawei.lcagent.service.LogCollectService");
        /* DTS2014120100636 niexingxing/00271341 20141201 end >*/
        context.startService(serviceIntent);

        Intent tent = new Intent("com.huawei.lcagent.service.ILogCollect");
        tent.setClassName("com.huawei.lcagent", "com.huawei.lcagent.service.LogCollectService");
        /* DTS2014122300493 niexingxing/00271341 20141203 end >*/
        boolean bRet = mContext.bindService(tent, scLogCollect, Context.BIND_AUTO_CREATE);
        return bRet;
    }

    private void unbindToService() {
        if (mContext == null || scLogCollect == null) {
            Log.e(TAG, "mContext == null || scLogCollect == null");
            return;
        }
        mContext.unbindService(scLogCollect);
        return;
    }

    protected void finalize() {
        unbindToService();
    }

    protected ILogCollect iLogCollect = null;
    protected ServiceConnection scLogCollect = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "service is connected");
            iLogCollect = ILogCollect.Stub.asInterface(service);
            /*< DTS2014120100636 niexingxing/00271341 20141201 begin */
            if(null != mCallerCallback) {
                mCallerCallback.serviceConnected();
            }
            /* DTS2014120100636 niexingxing/00271341 20141201 end >*/
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.i(TAG, "service is disconnceted");
            iLogCollect = null;
        }
    };

    public long getFirstErrorTime() throws RemoteException{
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.getFirstErrorTime();
    }

    public int resetFirstErrorTime() throws RemoteException{
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.resetFirstErrorTime();
    }

    public String getFirstErrorType() throws RemoteException{
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return null;
            }
        }
        if (iLogCollect == null) {
            return null;
        }
        return iLogCollect.getFirstErrorType();
    }

    public int configureModemlogcat (int mode, String parameters) throws RemoteException{
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;  
        }
        return iLogCollect.configureModemlogcat(mode, parameters);
    }
    
    public int configureBluetoothlogcat(int enable, String parameters) throws RemoteException{
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;  
        }
        return iLogCollect.configureBluetoothlogcat(enable, parameters);
    }
    
    public int configureLogcat(int enable, String parameters) throws RemoteException{
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.configureLogcat(enable, parameters);
    }
    
    public int configureAPlogs(int enable) throws RemoteException {
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.configureAPlogs(enable);
    }
    
    public int configureCoredump(int enable) throws RemoteException {
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.configureCoredump(enable);
    }
    
    public int configureGPS(int enable) throws RemoteException {
        if (iLogCollect == null) {
            if (bindToService(mContext) == false) {
                return ERROR_SERVICE_NOT_CONNECTED;
            }
        }
        if (iLogCollect == null) {
            return ERROR_SERVICE_NOT_CONNECTED;
        }
        return iLogCollect.configureGPS(enable);
    }
}
