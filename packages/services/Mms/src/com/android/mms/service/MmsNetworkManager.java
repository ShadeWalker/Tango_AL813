/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.service;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkInfo;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import com.android.mms.service.exception.MmsNetworkException;
import com.android.okhttp.ConnectionPool;
import com.android.okhttp.HostResolver;
import com.mediatek.mms.service.ext.IMmsServiceCancelDownloadExt;

import java.net.InetAddress;
import java.net.UnknownHostException;
import android.os.SystemProperties;
/**
 * Manages the MMS network connectivity
 */
public class MmsNetworkManager implements HostResolver {
    // Timeout used to call ConnectivityManager.requestNetwork
    private static final int NETWORK_REQUEST_TIMEOUT_MILLIS = 60 * 1000;
    // Wait timeout for this class, a little bit longer than the above timeout
    // to make sure we don't bail prematurely
    private static final int NETWORK_ACQUIRE_TIMEOUT_MILLIS =
            NETWORK_REQUEST_TIMEOUT_MILLIS + (5 * 1000);

    // Borrowed from {@link android.net.Network}
    private static final boolean httpKeepAlive =
            Boolean.parseBoolean(System.getProperty("http.keepAlive", "true"));
    private static final int httpMaxConnections =
            httpKeepAlive ? Integer.parseInt(System.getProperty("http.maxConnections", "5")) : 0;
    private static final long httpKeepAliveDurationMs =
            Long.parseLong(System.getProperty("http.keepAliveDuration", "300000"));  // 5 minutes.

    private final Context mContext;

    // The requested MMS {@link android.net.Network} we are holding
    // We need this when we unbind from it. This is also used to indicate if the
    // MMS network is available.
    private Network mNetwork;
    // The current count of MMS requests that require the MMS network
    // If mMmsRequestCount is 0, we should release the MMS network.
    private int mMmsRequestCount;
    // This is really just for using the capability
    private final NetworkRequest mNetworkRequest;
    // The callback to register when we request MMS network
    private ConnectivityManager.NetworkCallback mNetworkCallback;

    private volatile ConnectivityManager mConnectivityManager;

    // The OkHttp's ConnectionPool used by the HTTP client associated with this network manager
    private ConnectionPool mConnectionPool;

    // The MMS HTTP client for this network
    private MmsHttpClient mMmsHttpClient;

    // The SIM ID which we use to connect
    private final int mSubId;

    private boolean mIsNetworkLost;
    private boolean mIsNetworkReleased;

    public MmsNetworkManager(Context context, int subId) {
        mContext = context;
        mNetworkCallback = null;
        mNetwork = null;
        mMmsRequestCount = 0;
        mConnectivityManager = null;
        mConnectionPool = null;
        mMmsHttpClient = null;
        mSubId = subId;
        mIsNetworkLost = false;
        mIsNetworkReleased = true;
        NetworkRequest.Builder builder = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                .setNetworkSpecifier(Integer.toString(mSubId));
        if ("1".equals(SystemProperties.get("ro.mtk_epdg_support"))) {
            builder.addTransportType(NetworkCapabilities.TRANSPORT_EPDG);
        }
        mNetworkRequest = builder.build();
    }

    /**
     * Acquire the MMS network
     *
     * @throws com.android.mms.service.exception.MmsNetworkException if we fail to acquire it
     */
    public void acquireNetwork() throws MmsNetworkException {
        Log.d(MmsService.TAG, "MmsNetworkManager: acquireNetwork start");
        synchronized (this) {
            mMmsRequestCount += 1;
            mIsNetworkLost = false;
            mIsNetworkReleased = false;
            if (mNetwork != null) {
                // Already available
                Log.d(MmsService.TAG, "MmsNetworkManager: already available");
                return;
            }
            Log.d(MmsService.TAG, "MmsNetworkManager: start new network request");
            // Not available, so start a new request
            newRequest();
            final long shouldEnd = SystemClock.elapsedRealtime() + NETWORK_ACQUIRE_TIMEOUT_MILLIS;
            long waitTime = NETWORK_ACQUIRE_TIMEOUT_MILLIS;
            /// M: OP09 Mms Cancel downloading mms Feature @{
            IMmsServiceCancelDownloadExt mMmsServiceCancelDownloadPlugin =
                (IMmsServiceCancelDownloadExt) MmsServicePluginManager
                    .getMmsPluginObject(MmsServicePluginManager
                        .MMS_PLUGIN_TYPE_MMS_SERVICE_CANCEL_DOWNLOAD);
            if (mMmsServiceCancelDownloadPlugin.needBeCanceled(null)) {
                waitTime = -1;
                Log.d(MmsService.TAG,
                    "[acquireNetwork], op09 cancel download mms. Set waitTime = -1");
            }
            /// @}
            while (waitTime > 0) {
                try {
                    this.wait(waitTime);
                } catch (InterruptedException e) {
                    Log.w(MmsService.TAG, "MmsNetworkManager: acquire network wait interrupted");
                }
                if (mNetwork != null) {
                    // Success
                    return;
                }
                // if the network lost, no need to wait.
                if (mIsNetworkLost) {
                    Log.d(MmsService.TAG, "MmsNetworkManager: network already lost!");
                    break;
                }
                // Calculate remaining waiting time to make sure we wait the full timeout period
                waitTime = shouldEnd - SystemClock.elapsedRealtime();

                /// M: OP09 Mms Cancel downloading mms Feature @{
                if (mMmsServiceCancelDownloadPlugin.needBeCanceled(null)) {
                    waitTime = 0;
                    Log.d(MmsService.TAG,
                                "[acquireNetwork], op09 cancel download mms." +
                                " After wait, Set waitTime = 0");
                }
                /// @}
            }
            // Timed out, so release the request and fail
            Log.d(MmsService.TAG, "MmsNetworkManager: timed out");
            releaseRequestLocked(mNetworkCallback);
            throw new MmsNetworkException("Acquiring network timed out");
        }
    }

    /**
     * Release the MMS network when nobody is holding on to it.
     */
    public void releaseNetwork() {
        synchronized (this) {
            if (mMmsRequestCount > 0) {
                mMmsRequestCount -= 1;
                Log.d(MmsService.TAG, "MmsNetworkManager: release, count=" + mMmsRequestCount +
                        " mPendingMmsRequestCount = " + mPendingMmsRequestCount);
                if (mMmsRequestCount < 1 && mPendingMmsRequestCount < 1) {
                    releaseRequestLocked(mNetworkCallback);
                }
            }
        }
    }

    /**
     * Release the MMS network when nobody is holding on to it.
     */
    public void releaseNetworkImmediately() {
        synchronized (this) {
            Log.d(MmsService.TAG, "MmsNetworkManager: release network immdiately");
            releaseRequestLocked(mNetworkCallback);
        }
    }
    /**
     * Start a new {@link android.net.NetworkRequest} for MMS
     */
    private void newRequest() {
        final ConnectivityManager connectivityManager = getConnectivityManager();
        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);
                Log.d(MmsService.TAG, "NetworkCallbackListener.onAvailable: network=" + network);
                synchronized (MmsNetworkManager.this) {
                    mNetwork = network;
                    MmsNetworkManager.this.notifyAll();
                }
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                Log.d(MmsService.TAG, "NetworkCallbackListener.onLost: network=" + network);
                synchronized (MmsNetworkManager.this) {
                    mIsNetworkLost = true;
                    releaseRequestLocked(this);
                    MmsNetworkManager.this.notifyAll();
                }
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                Log.d(MmsService.TAG, "NetworkCallbackListener.onUnavailable");
                synchronized (MmsNetworkManager.this) {
                    mIsNetworkLost = true;
                    releaseRequestLocked(this);
                    MmsNetworkManager.this.notifyAll();
                }
            }
        };
        Log.d(MmsService.TAG, "newRequest subid = " + mSubId);
        connectivityManager.requestNetwork(
                mNetworkRequest, mNetworkCallback, NETWORK_REQUEST_TIMEOUT_MILLIS);
    }

    /**
     * Release the current {@link android.net.NetworkRequest} for MMS
     *
     * @param callback the {@link android.net.ConnectivityManager.NetworkCallback} to unregister
     */
    private void releaseRequestLocked(ConnectivityManager.NetworkCallback callback) {
        if (callback != null) {
            Log.d(MmsService.TAG, "MmsNetworkManager: releaseRequestLocked");
            final ConnectivityManager connectivityManager = getConnectivityManager();
            connectivityManager.unregisterNetworkCallback(callback);
        }
        resetLocked();
    }

    /**
     * Reset the state
     */
    private void resetLocked() {
        mIsNetworkReleased = true;
        mNetworkCallback = null;
        mNetwork = null;
        mMmsRequestCount = 0;
        // Currently we follow what android.net.Network does with ConnectionPool,
        // which is per Network object. So if Network changes, we should clear
        // out the ConnectionPool and thus the MmsHttpClient (since it is linked
        // to a specific ConnectionPool).
        mConnectionPool = null;
        mMmsHttpClient = null;
    }

    private static final InetAddress[] EMPTY_ADDRESS_ARRAY = new InetAddress[0];
    @Override
    public InetAddress[] getAllByName(String host) throws UnknownHostException {
        Network network = null;
        synchronized (this) {
            if (mNetwork == null) {
                return EMPTY_ADDRESS_ARRAY;
            }
            network = mNetwork;
        }
        return network.getAllByName(host);
    }

    private ConnectivityManager getConnectivityManager() {
        if (mConnectivityManager == null) {
            mConnectivityManager = (ConnectivityManager) mContext.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
        }
        return mConnectivityManager;
    }

    private ConnectionPool getOrCreateConnectionPoolLocked() {
        if (mConnectionPool == null) {
            mConnectionPool = new ConnectionPool(httpMaxConnections, httpKeepAliveDurationMs);
        }
        return mConnectionPool;
    }

    /**
     * Get an MmsHttpClient for the current network
     *
     * @return The MmsHttpClient instance
     */
    public MmsHttpClient getOrCreateHttpClient() {
        synchronized (this) {
            if (mMmsHttpClient == null) {
                if (mNetwork != null) {
                    // Create new MmsHttpClient for the current Network
                    mMmsHttpClient = new MmsHttpClient(
                            mContext,
                            mNetwork.getSocketFactory(),
                            MmsNetworkManager.this,
                            getOrCreateConnectionPoolLocked());
                }
            }
            return mMmsHttpClient;
        }
    }

    /**
     * Get the APN name for the active network
     *
     * @return The APN name if available, otherwise null
     */
    public String getApnName() {
        Network network = null;
        synchronized (this) {
            if (mNetwork == null) {
                Log.d(MmsService.TAG, "MmsNetworkManager: getApnName: network not available");
                return null;
            }
            network = mNetwork;
        }
        String apnName = null;
        final ConnectivityManager connectivityManager = getConnectivityManager();
        NetworkInfo mmsNetworkInfo = connectivityManager.getNetworkInfo(network);
        if (mmsNetworkInfo != null) {
            apnName = mmsNetworkInfo.getExtraInfo();
        }
        Log.d(MmsService.TAG, "MmsNetworkManager: getApnName: " + apnName);
        return apnName;
    }

    ///M: MTK add for multi MMS send performance improvment.
    private int mPendingMmsRequestCount;
    public void updatePendingMmsRequestCount(int count) {
        synchronized (this) {
            Log.d(MmsService.TAG, "MmsNetworkManager: update pending count: " + count);
            mPendingMmsRequestCount = count;
        }
    }

    public boolean isNetworkReleased() {
        return mIsNetworkReleased;
    }
}
