package com.mediatek.mms.service.ext;

import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.content.Context;
import android.content.ContextWrapper;

public class DefaultMmsServiceTransactionExt extends ContextWrapper implements IMmsServiceTransactionExt {

    private static final int SOCKET_TIMEOUT = 60 * 1000;

    public DefaultMmsServiceTransactionExt(Context context) {
        super(context);
    }

    @Override
    public boolean isAllowRetryForPermanentFail() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isRetainRetryIndexWhenInCall() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isGminiMultiTransactionEnabled() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isSyncPdpConnectedState() {
        // TODO Auto-generated method stub
        return false;
    }
    
    @Override
    public void setSocketTimeout(HttpParams params,int socketTimeout) {
    	HttpConnectionParams.setSoTimeout(params, socketTimeout);
    }
    
    @Override
    public void setSoSndTimeout(HttpParams params) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setSoSendTimeoutProperty() {
        // TODO Auto-generated method stub

    }

    @Override
    public void setMmsServerStatusCode(int code) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean updateConnection() {
        // TODO Auto-generated method stub
    	return false;
    }

}
