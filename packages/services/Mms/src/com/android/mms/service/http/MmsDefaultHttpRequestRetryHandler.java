package com.android.mms.service.http;

import android.util.Log;

import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.ExecutionContext;


import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLHandshakeException;

public class MmsDefaultHttpRequestRetryHandler extends DefaultHttpRequestRetryHandler { 
    private static final String TAG = "MmsDefaultHttpRequestRetryHandler";

    public MmsDefaultHttpRequestRetryHandler() {
        super(2, false);
    }
    /** 
     * Used <code>retryCount</code> and <code>requestSentRetryEnabled</code> to determine
     * if the given method should be retried.
     */
    public boolean retryRequest(
            final IOException exception,
            int executionCount,
            final HttpContext context) {
    
        Log.d(TAG, "retryRequest");
    
        if (exception == null) {
            throw new IllegalArgumentException("Exception parameter may not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }
        if (exception instanceof SocketException) {
            return true;
        }
    
        return super.retryRequest(exception, executionCount, context);
    }    
}

