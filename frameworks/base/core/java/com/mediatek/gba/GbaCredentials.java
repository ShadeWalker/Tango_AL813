package com.mediatek.gba;

import android.content.Context;
import android.net.Network;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionManager;
import android.util.Base64;
import android.util.Log;

import org.apache.http.auth.BasicUserPrincipal;
import org.apache.http.auth.Credentials;

import java.security.Principal;

/**
 * HTTP credentials for GBA procedure.
 *
 * @hide
 */
public class GbaCredentials implements Credentials {
    private final static String TAG = "GbaCredentials";
    private BasicUserPrincipal mUserPrincipal;
    IGbaService mService;
    private int mSubId;
    private boolean mIsTlsEnabled;
    private String mPasswd;
    private final Context mContext;
    private String mNafAddress;
    private boolean mCachedSessionKeyUsed = false;

    static final byte[] DEFAULT_UA_SECURITY_PROTOCOL_ID_HTTP =
        new byte[] {(byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02};
    static final byte[] DEFAULT_UA_SECURITY_PROTOCOL_ID_TLS =
        new byte[] {(byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x2F};
    final protected static char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    private static Object sSyncObject = new Object();
    private Network mNetwork;

    /**
      * Construction function for GbaCredentials.
      *
      * @param context the application context.
      * @param nafAddress the sceme name + FQDN value of NAF server address.
      * e.g. https://www.google.com or http://www.google.com
      *
      * @hide
      */
    public GbaCredentials(Context context, String nafAddress) {
        this(context, nafAddress, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
    }

    /**
      * Construction function for GbaCredentials.
      *
      * @param context the application context.
      * @param nafAddress the sceme name + FQDN value of NAF server address.
      * @param subId the subscription id.
      * e.g. https://www.google.com or http://www.google.com
      *
      * @hide
      */
    public GbaCredentials(Context context, String nafAddress, int subId) {
        super();
        mContext = context;
        mSubId = subId;

        if (nafAddress.charAt(nafAddress.length() - 1) == '/') {
            nafAddress = nafAddress.substring(0, nafAddress.length() - 1);
        }

        mIsTlsEnabled = true;
        mCachedSessionKeyUsed = false;
        mNafAddress = nafAddress.toLowerCase();

        if (mNafAddress.indexOf("http://") != -1) {
            mNafAddress = nafAddress.substring(7);
            mIsTlsEnabled = false;
        } else if (mNafAddress.indexOf("https://") != -1) {
            mNafAddress = nafAddress.substring(8);
            mIsTlsEnabled = true;
        }

        Log.d(TAG, "nafAddress:" + mNafAddress);
        mUserPrincipal = null;
    }


    @Override
    public Principal getUserPrincipal() {
        Log.i(TAG, "getUserPrincipal");

        if (mUserPrincipal == null || mCachedSessionKeyUsed == true) {
            Log.i(TAG, "Run GBA procedure");

            try {
                IBinder b = ServiceManager.getService("GbaService");

                if (b == null) {
                    Log.i("debug", "The binder is null");
                    return null;
                }

                mService = IGbaService.Stub.asInterface(b);
            } catch (NullPointerException e) {
                e.printStackTrace();
            }

            try {
                NafSessionKey nafSessionKey = null;
                byte[] uaId = DEFAULT_UA_SECURITY_PROTOCOL_ID_TLS;

                if (mIsTlsEnabled) {
                    String gbaStr = System.getProperty("gba.ciper.suite", "");
                    if (gbaStr.length() > 0) {
                        GbaCipherSuite cipherSuite = GbaCipherSuite.getByName(gbaStr);
                        if (cipherSuite != null) {
                            byte[] cipherSuiteCode = cipherSuite.getCode();
                            uaId[3] = cipherSuiteCode[0];
                            uaId[4] = cipherSuiteCode[1];
                        }
                    }
                } else {
                    uaId = DEFAULT_UA_SECURITY_PROTOCOL_ID_HTTP;
                }

                if (mNetwork != null) {
                    mService.setNetwork(mNetwork);
                }

                if (SubscriptionManager.INVALID_SUBSCRIPTION_ID == mSubId) {
                    nafSessionKey = mService.runGbaAuthentication(mNafAddress,
                        uaId, mCachedSessionKeyUsed);
                } else {
                    nafSessionKey = mService.runGbaAuthenticationForSubscriber(mNafAddress,
                        uaId, mCachedSessionKeyUsed, mSubId);
                }

                if (nafSessionKey != null) {
                    Log.i(TAG, "GBA Session Key:" + nafSessionKey);
                    mUserPrincipal = new BasicUserPrincipal(nafSessionKey.getBtid());
                    mPasswd = Base64.encodeToString(nafSessionKey.getKey(), Base64.NO_WRAP);
                    mCachedSessionKeyUsed = false;
                } else {
                    Log.i(TAG, "GBA Session Key is null");
                    mUserPrincipal = new BasicUserPrincipal(null);
                }
            } catch (RemoteException re) {
                re.printStackTrace();
            }
        } else {
            if (!mCachedSessionKeyUsed) {
                mCachedSessionKeyUsed = true;
            }
        }

        return mUserPrincipal;
    }

    @Override
    public String getPassword() {
        Log.i(TAG, "mPasswd:" + mPasswd);
        return mPasswd;
    }

    /**
      * Tell GbaCredential the connection is TLS or not.
      *
      * @param tlsEnabled indicate the connection is over TLS or not.
      *
      */
    public void setTlsEnabled(boolean tlsEnabled) {
        mIsTlsEnabled = tlsEnabled;
    }

    /**
      * Configure which subscription to use in GBA procedure.
      *
      * @param subId indicate the subscription id.
      *
      */
    public void setSubId(int subId) {
        mSubId = subId;
    }

    /**
      * Configure dedicated network.
      *
      * @param network network that will be used to establish socket connection.
      *
      */
    public void setNetwork(Network network) {
        if (network != null) {
            Log.i(TAG, "GBA dedicated network netid:" + network);
            mNetwork = network;
        }
    }
}