package com.mediatek.mms.service.ext;

import org.apache.http.params.HttpParams;

public interface IMmsServiceTransactionExt {

    boolean isAllowRetryForPermanentFail(); // return false on common

    boolean isRetainRetryIndexWhenInCall();// return false on common

    boolean isGminiMultiTransactionEnabled();// return false on common

    boolean isSyncPdpConnectedState(); // return false on common

    void setSocketTimeout(HttpParams params,int socketTimeout);
    
    void setSoSndTimeout(HttpParams params); //  do nothing on common

    void setSoSendTimeoutProperty();//  do nothing on common

    void setMmsServerStatusCode(int code); // do nothing on common part

    boolean updateConnection();//  return false on common


}
