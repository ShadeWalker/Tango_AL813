package com.mediatek.epdg;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Slog;

import com.android.internal.util.HexDump;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


/**
 *
 * Constant class for EPDG SW module.
 * @hide
 */

class EpdgConnector {
    private static final String TAG = "EpdgConnector";

    static final String SOCKET_ACTION_EPDG = "wod_action";
    static final String SOCKET_SIM_EPDG = "wod_sim";
    static final int SOCKET_OPEN_RETRY_MILLIS = 5 * 1000;
    static final int MAX_RETRY_COUNT = 10;
    static final int BUFFER_SIZE = 4096;

    private LocalSocket mSocket;
    private LocalSocket mSimSocket;
    private OutputStream mOutputStream;
    private OutputStream mSimOutputStream;

    Thread mReceiverThread;
    Thread mSimReceiverThread;

    //***** Instance Variables
    EpdgCommandReceiver mEpdgCommandReceiver;
    EpdgSimReceiver mEpdgSimReceiver;

    /** Lock held whenever communicating with native daemon. */
    private final Object mDaemonLock = new Object();
    private final Object mSimDaemonLock = new Object();
    private AtomicInteger mSequenceNumber;

    /*  Create static instance for EpdgConnector */
    private static EpdgConnector sInstance = new EpdgConnector();
    private final Map<Integer, EpdgCallback> mCbClents;


    private EpdgConnector() {
        mEpdgCommandReceiver = new EpdgCommandReceiver();
        mReceiverThread = new Thread(mEpdgCommandReceiver, "EPDGReceiver1");
        mReceiverThread.start();

        mEpdgSimReceiver = new EpdgSimReceiver();
        mSimReceiverThread = new Thread(mEpdgSimReceiver, "EPDGReceiver2");
        mSimReceiverThread.start();

        mCbClents = new LinkedHashMap<Integer, EpdgCallback>();

    }

    static EpdgConnector getInstance() {
        return sInstance;
    }

    void registerEpdgCallback(int networkType, EpdgCallback cb) {
        mCbClents.put(networkType, cb);
    }

    void unregisterEpdgCallback(int networkType, EpdgCallback cb) {
        mCbClents.remove(networkType);
    }

    private void notifySimAction(String apn, byte[] rand, byte[] autn) {
        Slog.d(TAG, "notifySimAction:" + apn);

        Collection<EpdgCallback> epdgCbs = mCbClents.values();

        for (EpdgCallback cb : epdgCbs) {
            cb.onEpdgSimAuthenticate(apn, rand, autn);
        }
    }

    private void notifyEpdgAttach(String apn, int statusCode, String nwInterface,
            String tunnelIpv4, String tunnelIpv6, String pcscfIpv4Addr, String pcscfIpv6Addr,
            String dnsIpv4Addr, String dnsIpv6Addr) {
        Collection<EpdgCallback> epdgCbs = mCbClents.values();

        Slog.d(TAG, "notifyEpdgAttach:" + apn + ":" + epdgCbs.size());

        for (EpdgCallback cb : epdgCbs) {
            cb.onEpdgConnected(apn, statusCode, nwInterface, tunnelIpv4, tunnelIpv6,
                            pcscfIpv4Addr, pcscfIpv6Addr, dnsIpv4Addr, dnsIpv6Addr);
        }
    }

    private void notifyEpdgDetach(String apn, int statusCode) {
        Slog.d(TAG, "notifyEpdgDetach:" + apn);

        Collection<EpdgCallback> epdgCbs = mCbClents.values();

        for (EpdgCallback cb : epdgCbs) {
            if (statusCode == EpdgConstants.EPDG_RESPONSE_OK) {
                cb.onEpdgDisconnected(apn);
            } else {
                cb.onEpdgConnectFailed(apn, statusCode);
            }
        }
    }

    private void notifyEpdgDisconnected(String apn) {
        Slog.d(TAG, "notifyEpdgDisconnected:" + apn);

        Collection<EpdgCallback> epdgCbs = mCbClents.values();

        for (EpdgCallback cb : epdgCbs) {
            cb.onEpdgDisconnected(apn);
        }
    }

    void sendSimCommand(String rawCmd) {
        Slog.d(TAG, "[SIM] SND -> {" + rawCmd + ")");

        synchronized (mSimDaemonLock) {
            if (mSimOutputStream == null) {
                Slog.e(TAG, ("missing SIM output stream"));
            } else {
                try {
                    mSimOutputStream.write(rawCmd.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    void sendEpdgCommand(String rawCmd) throws SocketException {

        Slog.d(TAG, "SND -> {" + rawCmd + ")");

        synchronized (mDaemonLock) {
            if (mOutputStream == null) {
                throw new SocketException("missing output stream");
            } else {
                try {
                    mOutputStream.write(rawCmd.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String trimDoubleQuotes(String text) {
        int textLength = 0;
        text = text.trim();
        textLength = text.length();

        if (textLength >= 2 && text.charAt(0) == '"' && text.charAt(textLength - 1) == '"') {
            return text.substring(1, textLength - 1);
        }

        return text;
    }

    /**
     *
     * Socket receiver to handle SIM command from WO demand.
     *
     */
    private class EpdgSimReceiver implements Runnable {
        private static final String EAUTH_CMD = "eauth=";

        EpdgSimReceiver() {

        }

        @Override
        public void run() {
            int retryCount = 0;
            LocalSocketAddress.Namespace flag = LocalSocketAddress.Namespace.RESERVED;

            while (true) {
                mSimSocket = null;
                LocalSocketAddress address;

                try {
                    mSimSocket = new LocalSocket();
                    address = new LocalSocketAddress(SOCKET_SIM_EPDG,
                                                     flag);
                    mSimSocket.connect(address);
                } catch (IOException ex) {
                    ex.printStackTrace();

                    try {
                        if (mSimSocket != null) {
                            mSimSocket.close();
                        }
                    } catch (IOException ex2) {
                        //ignore failure to close after failure to connect
                    }

                    // don't print an error message after the the first time
                    // or after the 5th time
                    if (retryCount == MAX_RETRY_COUNT) {
                        flag = LocalSocketAddress.Namespace.ABSTRACT;
                        Slog.e(TAG, "[SIM]Error: can't connect native daemon:" + retryCount);
                        //break;
                    }

                    try {
                        Thread.sleep(SOCKET_OPEN_RETRY_MILLIS);
                    } catch (InterruptedException er) {
                        er.printStackTrace();
                    }

                    retryCount++;
                    continue;
                }

                retryCount = 0;

                Slog.e(TAG, "[SIM]Connect successfully");

                try {
                    InputStream inputStream = mSimSocket.getInputStream();

                    synchronized (mSimDaemonLock) {
                        mSimOutputStream = mSimSocket.getOutputStream();
                    }

                    byte[] buffer = new byte[BUFFER_SIZE];

                    while (true) {
                        int count = inputStream.read(buffer, 0, BUFFER_SIZE);

                        if (count < 0) {
                            // End-of-stream reached
                            Slog.e(TAG, "Hit EOS while reading message:" + count);
                            break;
                        }

                        if (count > 0) {
                            handleSimInput(new String(buffer, 0, count));
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    synchronized (mSimDaemonLock) {
                        if (mSimOutputStream != null) {
                            try {
                                Slog.d(TAG, "closing stream for " + mSocket);
                                mSimOutputStream.close();
                            } catch (IOException e) {
                                Slog.d(TAG, "Failed closing output stream: " + e);
                            }

                            mSimOutputStream = null;
                        }
                    }

                    try {
                        if (mSimSocket != null) {
                            mSimSocket.close();
                        }
                    } catch (IOException ex) {
                        Slog.d(TAG, "Failed closing socket: " + ex);
                    }
                }
            }
        }

        /* Process an incoming AT command line
        */
        protected void handleSimInput(String input) {
            Slog.i(TAG, "process input: RCV <-(" + input + ")");

            //eauth="tmus","8f4f6d42389034ff0aa9dc033bc9aad0","8a83df0537fc00003cbebf543bec88e8"
            //eauth="*",   "3EDD9C279CB1729E1B71D2080A1454C5","931106E56E5F0000ED7E814B620FCE11"
            if (input.toLowerCase().startsWith(EAUTH_CMD)) {
                String data = input.substring(EAUTH_CMD.length());
                String[] parsed = data.split(",");
                String apn = trimDoubleQuotes(parsed[0]);
                byte[] rand = HexDump.hexStringToByteArray(trimDoubleQuotes(parsed[1]));
                byte[] autn = null;

                if (parsed.length == 3) {
                    autn = HexDump.hexStringToByteArray(trimDoubleQuotes(parsed[2]));
                }

                notifySimAction(apn, rand, autn);
            }
        }
    }

    /**
     *
     * Handle EPDG command from WO demand.
     *
     */
    private class EpdgCommandReceiver implements Runnable {

        protected EpdgCommandReceiver() {

        }

        protected void handleEpdgCommand(String input) {
            Slog.i(TAG, "process epdg RCV <- {:" + input + ")");

            if (input.toLowerCase().startsWith(EpdgConstants.ATTACH_DATA)
                || input.toLowerCase().startsWith(EpdgConstants.HANDOVER_DATA)
                || input.toLowerCase().startsWith(EpdgConstants.HANDOVER_WIFI_DATA)) {
                try {
                    int offset = input.indexOf(":");
                    String data = input.substring(offset + 1);
                    String[] parsed = data.split(",");

                    if (parsed.length < 2) {
                        Slog.e(TAG, "Wrong response");
                        return;
                    } else if (parsed.length == 2) {
                        //wodetach:<APN>,<Status Code>
                        String apn = trimDoubleQuotes(parsed[0]);
                        int failCause = Integer.parseInt(parsed[1].trim());
                        notifyEpdgDetach(apn, failCause);
                        return;
                    } else if (parsed.length == 3) {
                        //wodetach:<APN>,<Status Code>, <sub error code>
                        String apn = trimDoubleQuotes(parsed[0]);
                        int failCause = Integer.parseInt(parsed[1].trim());
                        int subFailCause = Integer.parseInt(parsed[2].trim());
                        notifyEpdgDetach(apn, failCause);
                        return;
                    }

                    String apn = trimDoubleQuotes(parsed[0]);
                    int statusCode = Integer.parseInt(parsed[1].trim());
                    String nwInterface = "";
                    String addrV6 = "";
                    String pcscfV6 = "";
                    String dnsV6 = "";
                    String addrV4 = "";
                    String pcscfV4 = "";
                    String dnsV4 = "";

                    if (parsed.length >= 6) {
                        nwInterface = trimDoubleQuotes(parsed[2]);
                        addrV6 = trimDoubleQuotes(parsed[3]);
                        pcscfV6 = trimDoubleQuotes(parsed[4]);
                        dnsV6 = trimDoubleQuotes(parsed[5]);
                    }

                    Slog.i(TAG, "parsed.length:" + parsed.length);

                    if (parsed.length == 9) {
                        addrV4 = trimDoubleQuotes(parsed[6]);
                        pcscfV4 = trimDoubleQuotes(parsed[7]);
                        dnsV4 = trimDoubleQuotes(parsed[8]);
                        Slog.i(TAG, "addrV4:" + addrV4);
                        Slog.i(TAG, "pcscfV4:" + pcscfV4);
                        Slog.i(TAG, "dnsV4:" + dnsV4);
                    }

                    notifyEpdgAttach(apn, statusCode, nwInterface,
                            addrV4, addrV6, pcscfV4, pcscfV6, dnsV4, dnsV6);
                } catch (IndexOutOfBoundsException e) {
                    e.printStackTrace();
                }
            } else if (input.toLowerCase().startsWith(EpdgConstants.DETACH_DATA)) {
                try {
                    int offset = input.indexOf(":");
                    String data = input.substring(offset + 1);
                    String[] parsed = data.split(",");

                    if (parsed.length < 2) {
                        Slog.e(TAG, "Wrong response");
                        return;
                    }

                    String apn = trimDoubleQuotes(parsed[0]);
                    int statusCode = Integer.parseInt(parsed[1].trim());
                    int subStatueCode = 0;
                    if (parsed.length == 3) {
                        subStatueCode = Integer.parseInt(parsed[2].trim());
                    }
                    notifyEpdgDetach(apn, statusCode);
                } catch (IndexOutOfBoundsException e) {
                    e.printStackTrace();
                }
            } else if (input.toLowerCase().startsWith(EpdgConstants.DISCONNECT_DATA)) {
                try {
                    int offset = input.indexOf(":");
                    String data = input.substring(offset + 1);
                    String apn = trimDoubleQuotes(data);
                    notifyEpdgDisconnected(apn);
                } catch (IndexOutOfBoundsException e) {
                    e.printStackTrace();
                }
            } else {
                Slog.e(TAG, "No handle");
            }
        }

        @Override
        public void run() {
            int retryCount = 0;
            LocalSocketAddress.Namespace flag = LocalSocketAddress.Namespace.RESERVED;

            while (true) {
                mSocket = null;
                LocalSocketAddress address;

                try {
                    mSocket = new LocalSocket();
                    address = new LocalSocketAddress(SOCKET_ACTION_EPDG,
                                                     flag);
                    mSocket.connect(address);
                } catch (IOException ex) {
                    ex.printStackTrace();

                    try {
                        if (mSocket != null) {
                            mSocket.close();
                        }
                    } catch (IOException ex2) {
                        //ignore failure to close after failure to connect
                    }

                    // don't print an error message after the the first time
                    // or after the 5th time
                    if (retryCount == MAX_RETRY_COUNT) {
                        flag = LocalSocketAddress.Namespace.ABSTRACT;
                        Slog.e(TAG, "Fatal error: can't connect native daemon:" + retryCount);
                        //break;
                    }

                    try {
                        Thread.sleep(SOCKET_OPEN_RETRY_MILLIS);
                    } catch (InterruptedException er) {
                        er.printStackTrace();
                    }

                    retryCount++;
                    continue;
                }

                retryCount = 0;

                Slog.e(TAG, "Connect successfully");

                try {
                    InputStream inputStream = mSocket.getInputStream();

                    synchronized (mDaemonLock) {
                        mOutputStream = mSocket.getOutputStream();
                    }

                    byte[] buffer = new byte[BUFFER_SIZE];
                    int start = 0;

                    while (true) {
                        int count = inputStream.read(buffer, start, BUFFER_SIZE - start);

                        if (count < 0) {
                            // End-of-stream reached
                            Slog.e(TAG, "got " + count + " reading with start = " + start);
                            notifyEpdgDisconnected(EpdgConnection.ALL_MATCH_APN);
                            break;
                        } else if (count > 0) {
                            handleEpdgCommand(new String(buffer, 0, count));
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    synchronized (mDaemonLock) {
                        if (mOutputStream != null) {
                            try {
                                Slog.d(TAG, "closing stream for " + mSocket);
                                mOutputStream.close();
                            } catch (IOException e) {
                                Slog.d(TAG, "Failed closing output stream: " + e);
                            }

                            mOutputStream = null;
                        }
                    }

                    try {
                        if (mSocket != null) {
                            mSocket.close();
                        }
                    } catch (IOException ex) {
                        Slog.d(TAG, "Failed closing socket: " + ex);
                    }
                }
            }
        }
    }
}