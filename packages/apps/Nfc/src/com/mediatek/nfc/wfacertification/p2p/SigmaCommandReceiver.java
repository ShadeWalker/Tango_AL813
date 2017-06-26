package com.mediatek.nfc.wfacertification.p2p;

import java.util.concurrent.Semaphore;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class SigmaCommandReceiver implements Runnable {
    private static final String TAG = "SigmaCommandReceiver";
    private static final int SIGMA_CMD_PORT = 20002;
    private static final int MAX_PROTOCOL_BYTES = 1024;

    final public String MTK_NFC_WFA_SIGMA_P2P_HR_ACTION = "mtk.nfc.wfa.sigma.p2p.HR_ACTION";
    final public String MTK_NFC_WFA_SIGMA_P2P_HR_RECEIVE_ACTION = "mtk.nfc.wfa.sigma.p2p.HR_RECEIVE_ACTION";
    final public String MTK_NFC_WFA_SIGMA_P2P_HS_RECEIVE_ACTION = "mtk.nfc.wfa.sigma.p2p.HS_RECEIVE_ACTION";
    final public String MTK_NFC_WFA_SIGMA_P2P_EXTRA_HR_P2P_DEV_INFO = "mtk.nfc.wfa.sigma.p2p.extra.HR_P2P_DEV_INFO"; //byte array
    final public String MTK_NFC_WFA_SIGMA_P2P_EXTRA_HS_P2P_DEV_INFO = "mtk.nfc.wfa.sigma.p2p.extra.HS_P2P_DEV_INFO"; //byte array

    final public String MTK_NFC_WFA_SIGMA_WPS_HR_ACTION = "mtk.nfc.wfa.sigma.wps.HR_ACTION";
    final public String MTK_NFC_WFA_SIGMA_WPS_HS_ACTION = "mtk.nfc.wfa.sigma.wps.HS_ACTION";
    final public String MTK_NFC_WFA_SIGMA_WPS_HR_RECEIVE_ACTION = "mtk.nfc.wfa.sigma.wps.HR_RECEIVE_ACTION";
    final public String MTK_NFC_WFA_SIGMA_WPS_HS_RECEIVE_ACTION = "mtk.nfc.wfa.sigma.wps.HS_RECEIVE_ACTION";
    final public String MTK_NFC_WFA_SIGMA_WPS_EXTRA_HR_P2P_DEV_INFO = "mtk.nfc.wfa.sigma.wps.extra.HR_P2P_DEV_INFO"; //byte array
    final public String MTK_NFC_WFA_SIGMA_WPS_EXTRA_HS_P2P_DEV_INFO = "mtk.nfc.wfa.sigma.wps.extra.HS_P2P_DEV_INFO"; //byte array

    final public String MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_WRITE_ACTION      = "mtk.nfc.wfa.sigma.wps.cfg.tag.WRITE_ACTION"; //The device want to write Configuration Tag when it acts as GO
    final public String MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_READ_ACTION      = "mtk.nfc.wfa.sigma.wps.cfg.tag.READ_ACTION";
    final public String MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_RECEIVE_ACTION    = "mtk.nfc.wfa.sigma.wps.cfg.tag.RECEIVE_ACTION"; //The device read Tag with WFA static handover info.
    final public String MTK_NFC_WFA_SIGMA_TAG_EXTRA_DEV_INFO = "mtk.nfc.wfa.sigma.tag.extra.DEV_INFO"; //byte array

    final public String MTK_NFC_WFA_SIGMA_P2P_TAG_WRITE_ACTION      = "mtk.nfc.wfa.sigma.p2p.tag.WRITE_ACTION";
    final public String MTK_NFC_WFA_SIGMA_P2P_TAG_READ_ACTION       = "mtk.nfc.wfa.sigma.p2p.tag.READ_ACTION";
    final public String MTK_NFC_WFA_SIGMA_P2P_TAG_RECEIVE_ACTION    = "mtk.nfc.wfa.sigma.p2p.tag.RECEIVE_ACTION";
    final public String MTK_NFC_WFA_SIGMA_P2P_TAG_EXTRA_INFO        = "mtk.nfc.wfa.sigma.p2p.tag.EXTRA_INFO"; //byte array

    final public String MTK_NFC_WFA_SIGMA_ALL_TAG_READ_ACTION           = "mtk.nfc.wfa.sigma.all.tag.READ_ACTION";
    final public String MTK_NFC_WFA_SIGMA_WPS_PWD_TAG_RECEIVE_ACTION    = "mtk.nfc.wfa.sigma.wps.pwd.tag.RECEIVE_ACTION";
    final public String MTK_NFC_WFA_SIGMA_WPS_PWD_TAG_WRITE_ACTION      = "mtk.nfc.wfa.sigma.wps.pwd.tag.WRITE_ACTION";

    static private final String SET_P2P_REQUESTER = "setp2preq";
    static private final String SET_P2P_SELECTOR = "setp2psel";
    static private final String NTF_SET_P2P_SELECTOR = "ntfsetp2psel";
    static private final String NTF_P2P_REQUESTER = "ntfp2preq";
    static private final String NTF_P2P_SELECTOR = "ntfp2psel";
    static private final String SET_WPS_SELECTOR = "setwpssel";
    static private final String SET_WPS_CFGTOK = "setwpscfgtok";
    static private final String GET_WPS_CFGTOK = "getwpscfgtok";
    static private final String NTF_WPS_CFGTOK = "wpscfgtok"; //response
    static private final String SET_WPS_REQUESTER = "setwpsreq";
    static private final String NTF_WPS_REQUESTER = "ntfwpsreq";
    static private final String NTF_WPS_SELECTOR = "ntfwpssel";

    static private final String SET_P2P_SEL_TAG = "setp2pseltag";
    static private final String GET_P2P_SEL_TAG = "getp2pseltag";
    static private final String P2P_TAG = "p2ptag";
    static private final String GET_TAG = "gettag";
    static private final String SET_WPS_PWD_TOK = "setwpspwdtok";
    static private final String WPS_PWD_TOK = "wpspwdtok";

    private volatile boolean mIsActive;
    private ServerSocket mServerSocket;
    private Context mContext;
    private SigmaTestSession mCurrentSession;

    private byte[] mSelectorPayload = null;

    private Semaphore mSem;


    public SigmaCommandReceiver(Context context) {
        mContext = context;
        mSem = new Semaphore(0);
    }

    public void start() {
        debugPrint("start");
        if (mIsActive == false) {
            mIsActive = true;
            registerReceiver();
            new Thread(this).start();
        }
    }

    public void stop() {
        debugPrint("stop");
        mIsActive = false;
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (Exception e) { }
            mServerSocket = null;
            unregisterReceiver();
        }
    }

    public byte[] getP2pSelect() {
        debugPrint("getP2pSelect");
        if (mCurrentSession != null) {
            return mCurrentSession.acquireSelectorPayload();
        }
        return null;
    }

    synchronized byte[] getSelectorPayload() {
        //debugPrint("getSelectorPayload Util.bytesToString(mSelectorPayload):"+Util.bytesToString(mSelectorPayload));
        return mSelectorPayload;
    }

    synchronized void setSelectorPayload(byte[] data) {
        debugPrint("setSelectorPayload bytesToString(data):" + bytesToString(data));
        mSelectorPayload = data;
    }

    public void run() {
        try {
            mServerSocket = new ServerSocket(SIGMA_CMD_PORT, 1);
        } catch (Exception e) {
            debugPrint("fail to create server socket");
            e.printStackTrace();
            return;
        }

        while (mIsActive) {
            debugPrint("waiting new connection...");
            try {
                Socket clientSocket = mServerSocket.accept();
                debugPrint("new connection accepted, start new session...");
                mCurrentSession = new SigmaTestSession(clientSocket);
                new Thread(mCurrentSession).start();
            } catch (Exception e) {
                debugPrint("exception during server main loop");
                e.printStackTrace();
            }
        }

        mCurrentSession = null;
        try {
            if (mServerSocket != null) {
                mServerSocket.close();
            }
        } catch (Exception e) { }
    }

    class SigmaTestSession implements Runnable {
        static private final int STATE_INIT = 0;
        static private final int STATE_WAITING_SELECTOR = 1;
        static private final int STATE_WAITING_REQUESTER = 2;
        static private final int STATE_TERMINATED = -1;

        private Socket mSocket;
        private InputStream mInStream;
        private OutputStream mOutStream;
        private byte[] mData = new byte[MAX_PROTOCOL_BYTES];
        private int mState;
        //private byte[] mSelectorPayload;

        public SigmaTestSession(Socket socket) {
            mSocket = socket;
            mState = STATE_INIT;
        }

        public void run() {
            String msg = "";
            try {
                mInStream = mSocket.getInputStream();
                mOutStream = mSocket.getOutputStream();
                while (mState != STATE_TERMINATED) {
                    doStateMachine();
                }
            } catch (Exception e) {
                debugPrint("exception during session run...");
                e.printStackTrace();
            } finally {
                try {
                    if (mInStream != null) {
                        mInStream.close();
                    }
                    if (mOutStream != null) {
                        mOutStream.close();
                    }
                    if (mSocket != null) {
                        mSocket.close();
                    }
                } catch (Exception e) { }
            }

            debugPrint("session terminated");
        }

        private void doStateMachine() {
            try {
                debugPrint("doStateMachine");
                int bytes = mInStream.read(mData, 0, MAX_PROTOCOL_BYTES);
                debugPrint("bytes read: " + bytes);
                if (bytes < 0) {
                    mState = STATE_TERMINATED;
                    return;
                }
                String msg = castToString(mData, bytes);
                debugPrint("sigma>>>> " + msg);

                String tokens[] = msg.trim().split(":");
                if (tokens.length == 0 || tokens[0] == null) {
                    throw new Exception("invalid command");
                } else if (tokens[0].equals(SET_P2P_REQUESTER)) {
                    debugPrint("payload length: " + Integer.valueOf(tokens[1], 16).intValue() + ", payload = " + tokens[2]);
                    notifyActAsRequester(tokens[2]);
                } else if (tokens[0].equals(SET_P2P_SELECTOR)) {
                    debugPrint("payload length: " + Integer.valueOf(tokens[1], 16).intValue() + ", payload = " + tokens[2]);
                    notifyActAsSelector(tokens[2]);
                } else if (tokens[0].equals(SET_WPS_SELECTOR)) {
                    debugPrint("payload length: " + Integer.valueOf(tokens[1], 16).intValue() + ", payload = " + tokens[2]);
                    notifyActAsWpsSelector(tokens[2]);
                } else if (tokens[0].equals(SET_WPS_CFGTOK)) {
                    debugPrint("payload length: " + Integer.valueOf(tokens[1], 16).intValue() + ", payload = " + tokens[2]);
                    notifySetWpsCfgTok(tokens[2]);
                } else if (tokens[0].equals(GET_WPS_CFGTOK)) {
                    notifyGetWpsCfgTok();
                } else if (tokens[0].equals(SET_WPS_REQUESTER)) {
                    debugPrint("payload length: " + Integer.valueOf(tokens[1], 16).intValue() + ", payload = " + tokens[2]);
                    notifySetWpsRequester(tokens[2]);
                } else if (tokens[0].equals(SET_P2P_SEL_TAG)) {
                    debugPrint("payload length: " + Integer.valueOf(tokens[1], 16).intValue() + ", payload = " + tokens[2]);
                    notifySetP2pSelectorTag(tokens[2]);
                } else if (tokens[0].equals(GET_P2P_SEL_TAG)) {
                    notifyGetP2pSelectorTag();
                } else if (tokens[0].equals(GET_TAG)) {
                    notifyGetTag();
                } else if (tokens[0].equals(SET_WPS_PWD_TOK)) {
                    debugPrint("payload length: " + Integer.valueOf(tokens[1], 16).intValue() + ", payload = " + tokens[2]);
                    notifySetWpsPwdTok(tokens[2]);
                }

            } catch (Exception e) {
                debugPrint("exception during doStateMachine");
                e.printStackTrace();
                mState = STATE_TERMINATED;
            } finally {

            }
        }

        public void sendMessageToDut(String cmdPrefix, byte[] payload) {
            debugPrint("sendMessageToDut, cmdPrefix = " + cmdPrefix);
            try {
                String response = cmdPrefix + castToProtocolString(payload);
                mOutStream.write(response.getBytes());
                mOutStream.flush();
                debugPrint("sigma<<<< " + response);
            } catch (Exception e) {
                debugPrint("exception during sendMessageToDut?");
                e.printStackTrace();
            }
        }

        public byte[] acquireSelectorPayload() {
            debugPrint("acquireSelectorPayload ");
            int i = 0;

            debugPrint(" force to get Select from Wi-Fi each time");
            setSelectorPayload(null);

            if (getSelectorPayload() == null) {
            debugPrint("getSelectorPayload() == null , get Select from Wi-Fi");


            try {
                mOutStream.write(NTF_SET_P2P_SELECTOR.getBytes());
                mOutStream.flush();

/*

                Long time = SystemClock.elapsedRealtime();
                Log.d(TAG, "!!!! mSem Lock , time = " + (time));



                /// now we're really waiting for notifyActAsSelector()
                try {
                    mSem.acquire();
                    Log.d(TAG, "!!!! mSem UnLock ,  time = " + (SystemClock.elapsedRealtime()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
*/


                while (true) { //stay simple, just polling it
                    Thread.sleep(50);

                    if (i % 20 == 0)
                    debugPrint("ThreadCheck i:" + i + "  bytesToString(getSelectorPayload()):" + bytesToString(getSelectorPayload()));
                    i++;

                    if (getSelectorPayload() != null) {
                        debugPrint("getSelectorPayload() != null  break ");
                        debugPrint("notifyActAsSelector bytesToString(getSelectorPayload()):" + bytesToString(getSelectorPayload()));
                        break;
                    }
                }
                debugPrint("exit While(1)");



            } catch (Exception e) {
                debugPrint("exception during acquireSelectorPayload");
                e.printStackTrace();
            }

            } else {
                debugPrint("mSelectorPayload != null, direct getSelectorPayload");
            }


            return getSelectorPayload(); //mSelectorPayload;
        }

        private void notifyActAsRequester(String requesterPayload) {
            debugPrint("notifyActAsRequester");
            Intent intent = new Intent(MTK_NFC_WFA_SIGMA_P2P_HR_ACTION);
            intent.putExtra(MTK_NFC_WFA_SIGMA_P2P_EXTRA_HR_P2P_DEV_INFO, castToByteArray(requesterPayload));
            mContext.sendBroadcast(intent);
        }

        synchronized private void notifyActAsSelector(String selectorPayload) {
            debugPrint("notifyActAsSelector  selectorPayload:" + selectorPayload);
            //mSelectorPayload = castToByteArray(selectorPayload);
            setSelectorPayload(castToByteArray(selectorPayload));

            //Log.d(TAG, "!!!! mSem.release()");
            //mSem.release();

        }

        private void notifyActAsWpsSelector(String wpsSelectorPayload) {
            debugPrint("notifyActAsWpsSelector");
            Intent intent = new Intent(MTK_NFC_WFA_SIGMA_WPS_HS_ACTION);
            intent.putExtra(MTK_NFC_WFA_SIGMA_WPS_EXTRA_HS_P2P_DEV_INFO, castToByteArray(wpsSelectorPayload));
            mContext.sendBroadcast(intent);
        }

        private void notifySetWpsCfgTok(String wpsCfgTokPayload) {
            debugPrint("notifySetWpsCfgTok");
            Intent intent = new Intent(MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_WRITE_ACTION);
            intent.putExtra(MTK_NFC_WFA_SIGMA_TAG_EXTRA_DEV_INFO, castToByteArray(wpsCfgTokPayload));
            mContext.sendBroadcast(intent);
        }

        private void notifyGetWpsCfgTok() {
            debugPrint("notifyGetWpsCfgTok");
            mContext.sendBroadcast(new Intent(MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_READ_ACTION));
        }

        private void notifySetWpsRequester(String wpsReqPayload) {
            debugPrint("notifySetWpsRequester");
            Intent intent = new Intent(MTK_NFC_WFA_SIGMA_WPS_HR_ACTION);
            intent.putExtra(MTK_NFC_WFA_SIGMA_WPS_EXTRA_HR_P2P_DEV_INFO, castToByteArray(wpsReqPayload));
            mContext.sendBroadcast(intent);
        }

        private void notifySetP2pSelectorTag(String p2pSelPayload) {
            debugPrint("notifySetP2pSelectorTag");
            Intent intent = new Intent(MTK_NFC_WFA_SIGMA_P2P_TAG_WRITE_ACTION);
            intent.putExtra(MTK_NFC_WFA_SIGMA_P2P_TAG_EXTRA_INFO, castToByteArray(p2pSelPayload));
            mContext.sendBroadcast(intent);
        }

        private void notifyGetP2pSelectorTag() {
            debugPrint("notifyGetP2pSelectorTag");
            mContext.sendBroadcast(new Intent(MTK_NFC_WFA_SIGMA_P2P_TAG_READ_ACTION));
        }

        private void notifyGetTag() {
            debugPrint("notifyGetTag");
            mContext.sendBroadcast(new Intent(MTK_NFC_WFA_SIGMA_ALL_TAG_READ_ACTION));
        }

        private void notifySetWpsPwdTok(String payload) {
            debugPrint("notifySetWpsPwdTok");
            Intent intent = new Intent(MTK_NFC_WFA_SIGMA_WPS_PWD_TAG_WRITE_ACTION);
            intent.putExtra(MTK_NFC_WFA_SIGMA_TAG_EXTRA_DEV_INFO, castToByteArray(payload));
            mContext.sendBroadcast(intent);
        }

    }

    private void registerReceiver() {
        debugPrint("registerReceiver");
        IntentFilter filter = new IntentFilter();
        filter.addAction(MTK_NFC_WFA_SIGMA_P2P_HR_RECEIVE_ACTION);
        filter.addAction(MTK_NFC_WFA_SIGMA_P2P_HS_RECEIVE_ACTION);
        filter.addAction(MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_RECEIVE_ACTION);

        filter.addAction(MTK_NFC_WFA_SIGMA_WPS_HR_RECEIVE_ACTION);
        filter.addAction(MTK_NFC_WFA_SIGMA_WPS_HS_RECEIVE_ACTION);
        filter.addAction(MTK_NFC_WFA_SIGMA_P2P_TAG_RECEIVE_ACTION);
        filter.addAction(MTK_NFC_WFA_SIGMA_WPS_PWD_TAG_RECEIVE_ACTION);
        mContext.registerReceiver(mReceiver, filter);
    }

    private void unregisterReceiver() {
        debugPrint("unregisterReceiver");
        mContext.unregisterReceiver(mReceiver);
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action == null){
                Log.e(TAG, "mReceiver onReceive() action == null");
                return;
            }
            if (action.equals(MTK_NFC_WFA_SIGMA_P2P_HR_RECEIVE_ACTION)) {
                debugPrint(MTK_NFC_WFA_SIGMA_P2P_HR_RECEIVE_ACTION);
                byte[] extra = intent.getByteArrayExtra(MTK_NFC_WFA_SIGMA_P2P_EXTRA_HR_P2P_DEV_INFO);
                if (mCurrentSession != null) {
                    mCurrentSession.sendMessageToDut(NTF_P2P_REQUESTER, extra);
                }
            } else if (action.equals(MTK_NFC_WFA_SIGMA_P2P_HS_RECEIVE_ACTION)) {
                debugPrint(MTK_NFC_WFA_SIGMA_P2P_HS_RECEIVE_ACTION);
                byte[] extra = intent.getByteArrayExtra(MTK_NFC_WFA_SIGMA_P2P_EXTRA_HS_P2P_DEV_INFO);
                if (mCurrentSession != null) {
                    mCurrentSession.sendMessageToDut(NTF_P2P_SELECTOR, extra);
                }
            } else if (action.equals(MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_RECEIVE_ACTION)) {
                debugPrint(MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_RECEIVE_ACTION);
                byte[] extra = intent.getByteArrayExtra(MTK_NFC_WFA_SIGMA_TAG_EXTRA_DEV_INFO);
                if (mCurrentSession != null) {
                    mCurrentSession.sendMessageToDut(NTF_WPS_CFGTOK, extra);
                }
            } else if (action.equals(MTK_NFC_WFA_SIGMA_WPS_HR_RECEIVE_ACTION)) {
                debugPrint(MTK_NFC_WFA_SIGMA_WPS_HR_RECEIVE_ACTION);
                byte[] extra = intent.getByteArrayExtra(MTK_NFC_WFA_SIGMA_WPS_EXTRA_HR_P2P_DEV_INFO);
                if (mCurrentSession != null) {
                    mCurrentSession.sendMessageToDut(NTF_WPS_REQUESTER, extra);
                }
            } else if (action.equals(MTK_NFC_WFA_SIGMA_WPS_HS_RECEIVE_ACTION)) {
                debugPrint(MTK_NFC_WFA_SIGMA_WPS_HS_RECEIVE_ACTION);
                byte[] extra = intent.getByteArrayExtra(MTK_NFC_WFA_SIGMA_WPS_EXTRA_HS_P2P_DEV_INFO);
                if (mCurrentSession != null) {
                    mCurrentSession.sendMessageToDut(NTF_WPS_SELECTOR, extra);
                }
            } else if (action.equals(MTK_NFC_WFA_SIGMA_P2P_TAG_RECEIVE_ACTION)) {
                debugPrint(MTK_NFC_WFA_SIGMA_P2P_TAG_RECEIVE_ACTION);
                byte[] extra = intent.getByteArrayExtra(MTK_NFC_WFA_SIGMA_P2P_TAG_EXTRA_INFO);
                if (mCurrentSession != null) {
                    mCurrentSession.sendMessageToDut(P2P_TAG, extra);
                }
            } else if (action.equals(MTK_NFC_WFA_SIGMA_WPS_PWD_TAG_RECEIVE_ACTION)) {
                debugPrint(MTK_NFC_WFA_SIGMA_WPS_PWD_TAG_RECEIVE_ACTION);
                byte[] extra = intent.getByteArrayExtra(MTK_NFC_WFA_SIGMA_TAG_EXTRA_DEV_INFO);
                if (mCurrentSession != null) {
                    mCurrentSession.sendMessageToDut(WPS_PWD_TOK, extra);
                }
            }
        }
    };

    static private String castToString(byte[] data, int len) {
        byte[] tmp = new byte[len];
        System.arraycopy(data, 0, tmp, 0, len);
        return new String(tmp);
    }

    static private String castToProtocolString(byte[] data) {
        String msg = ":";
        if (data.length <= 0xf) {
            msg += "000";
        } else if (data.length <= 0xff) {
            msg += "00";
        }
        msg += Integer.toHexString(data.length);
        msg += ":";
        for (byte b : data) {
            if (b < 0) {
                msg += Integer.toHexString(b).substring(6, 8);
            } else if (b <= 0xf) {
                msg += "0" + Integer.toHexString(b);
            } else {
                msg += Integer.toHexString(b);
            }
        }
        return msg;
    }

    static private byte[] castToByteArray(String payload) {
        byte[] out = new byte[payload.length() >>> 1];
        String dbgMsg = "";

        int length = payload.length();
        debugPrint("castToByteArray  payload.length():" + length);
        if (length % 2 != 0)
            debugPrint("castToByteArray  will exception.. payload length error");

        for (int i = 0; i < payload.length(); i += 2) {
            int value = 0;
            value = Integer.valueOf(payload.substring(i, i + 2), 16);
            out[i >>> 1] = (byte) value;
            dbgMsg += out[i >>> 1] + " ";
        }

        //debugPrint("castToByteArray   bytesToString(out):"+bytesToString(out));

        return out;
    }

    static private void debugPrint(String msg) {
        //System.out.println(msg);
        Log.d(TAG, msg);
    }

    public static String bytesToString(byte[] bytes) {
        if (bytes == null)
            return "";
        StringBuffer sb = new StringBuffer();
        for (byte b : bytes) {
            sb.append(String.format("%02x ", b & 0xFF));
        }
        String str = sb.toString();
        if (str.length() > 0) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }


}
