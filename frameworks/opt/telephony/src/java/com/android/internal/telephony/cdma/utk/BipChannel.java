/*
* This Software is the property of VIA Telecom, Inc. and may only be used pursuant to a
license from VIA Telecom, Inc.
* Any unauthorized use inconsistent with the terms of such license is strictly prohibited.
* Copyright (c) 2013 -2015 VIA Telecom, Inc. All rights reserved.
*/

package com.android.internal.telephony.cdma.utk;

//import android.os.AsyncResult;
//import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.android.internal.telephony.IccUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Thread;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
//import java.util.List;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;




public abstract class BipChannel extends Handler {
    public static final int EVENT_RECEIVED_DATA         = 1;
    public static final int EVENT_SENT_DATA             = 2;
    public static final int EVENT_ACCEPT_REQUEST        = 3;
    public static final int EVENT_TIMER_TICK            = 4;

    private static final int CHANNEL_TIMEUPDATE_PERIOD = 5000;  // ms

    protected OpenChannelSettings mChannelParams = null;

    protected ChannelStatus mChannelStatus = null;
    protected BipService mBipService = null;

    protected Socket mSocket = null;

    //
    protected ByteArrayOutputStream mTxBuffer = new ByteArrayOutputStream();
    protected Object mTxLock = new Object();

    protected byte[] mRxBuffer = null;
    protected Object mRxLock = new Object();
    protected boolean mReceiveDone = true;

    protected LinkedList<Message> mReceiveMsgQ = new LinkedList<Message>();

    protected int mRxBufferSize = BipConstants.BUFFER_SIZE_MAX;
    protected int mRxBufferIndex = 0;

    protected int mTxAvaSize = 0;

    protected BipRunnable mReceiver = null;

    protected int mDataAvailableLength = 0;
    private Object mTimerLock = new Object();
    protected Timer mDataToReceiveTimer = null;

    protected boolean mIsSocketReady = false;

    BipChannel(BipService bs, OpenChannelSettings p, int id) {
        UtkLog.d("BipChannel", " BipChannel id:" + id);

        mBipService = bs;
        mChannelParams = p;

        mChannelStatus = new ChannelStatus(p.transportLevel.protocolType, id,
          BipConstants.CHANNEL_STATUS_NO_LINK, BipConstants.CHANNEL_STATUS_INFO_NO_INFO);

        if ((p.bufferSize < mRxBufferSize) && (p.bufferSize != 0)) {
            mRxBufferSize = p.bufferSize;
        }

        mTxAvaSize = mRxBufferSize;
    }

    @Override
    public String toString() {
        return "BipChannel: id=" + mChannelStatus.getId() +
               " Status=" + mChannelStatus.getStatus() +
               " mIsSocketReady = " + mIsSocketReady;
    }

    @Override
    public void handleMessage(Message msg) {
        switch(msg.what) {
            case EVENT_RECEIVED_DATA: {
                UtkLog.d("BipChannel", " handleMessage EVENT_RECEIVED_DATA");

                UtkLog.d("BipChannel", "CT case modify:  mReceiveDone=" + mReceiveDone);
                if (mReceiveDone) {
                    if (msg.arg1 != BipConstants.RESULT_SUCCESS) {
                        UtkLog.d("BipChannel", " EVENT_RECEIVED_DATA exception");
                        mBipService.sendResponseToUtk(UtkService.MSG_ID_RECEIVED_DATA,
                        BipConstants.RESULT_ERROR, BipConstants.RESULT_CODE_BIP_ERROR, null);
                        break;
                    }

                    byte[] data = (byte[]) msg.obj;
                    synchronized (mRxLock) {
                        if (mRxBuffer != null) {
                            UtkLog.d("BipChannel", " EVENT_RECEIVED_DATA will overwrite data");
                        }
                        mRxBuffer = data;
                        mRxBufferIndex = 0;
                    }

                    mDataAvailableLength = data.length;
                    dataAvailable(mDataAvailableLength);

                    startDataToReceiveTimer(this);
                    mReceiveDone = false;
                } else {
                    UtkLog.d("BipChannel", " delay msg EVENT_RECEIVED_DATA");
                    Message delayMsg = Message.obtain(msg);
                    mReceiveMsgQ.addLast(delayMsg);
                }
            }
            break;

            case EVENT_SENT_DATA: {
                UtkLog.d("BipChannel", " handleMessage EVENT_SENT_DATA");

                if (msg.arg1 != BipConstants.RESULT_SUCCESS) {
                    UtkLog.d("BipChannel", " EVENT_SENT_DATA exception");
                    mBipService.sendResponseToUtk(UtkService.MSG_ID_SENT_DATA,
                    BipConstants.RESULT_ERROR, BipConstants.RESULT_CODE_BIP_ERROR, null);
                    break;
                }

                int[] avaBufSize = new int[1];
                avaBufSize[0] = mTxAvaSize;
                UtkLog.d("BipChannel", " response avaBufSize:" + mTxAvaSize);
                mBipService.sendResponseToUtk(UtkService.MSG_ID_SENT_DATA,
                              BipConstants.RESULT_SUCCESS, BipConstants.RESULT_CODE_OK, avaBufSize);
            }
            break;

            case EVENT_ACCEPT_REQUEST: {
                UtkLog.d("BipChannel", " handleMessage EVENT_ACCEPT_REQUEST");

                mSocket = (Socket) msg.obj;

                mReceiver = new TcpReceiver(mSocket, mRxBufferSize, this);
                Thread thd = new Thread(mReceiver);
                thd.start();

                mChannelStatus.setStatus(BipConstants.CHANNEL_STATUS_LINKED);
                notifyChannelStatusChanged();
            }
                break;
            case EVENT_TIMER_TICK:
                UtkLog.d("BipChannel", " handleMessage EVENT_TIMER_TICK");

                if (mDataAvailableLength != 0) {
                    dataAvailable(mDataAvailableLength);
                }
                break;
            default: {
                break;
            }
        }
    }

    public boolean isBackgroudModOrImmediate() {
        return (mChannelParams.backgrountMode || mChannelParams.immediateLink);
    }

    public boolean isLinked() {
        return (mChannelStatus.getStatus() == BipConstants.CHANNEL_STATUS_LINKED);
    }

    public OpenChannelSettings getBipChannelParams() {
      return mChannelParams;
    }

    protected void dataAvailable(int dataLength) {
        UtkLog.d("BipChannel", " dataAvailable:" + dataLength);
        /*
        Channel status 8.56
          -this data object shall contain the status and identifier of
          the channel on which the event occurred
        Channel data length 8.54
          -this data object shall contain the number of bytes received,
          e.g. available in the channel buffer. If
           more than 255 bytes are available, "FF" is used
        */
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // Channel status
        mChannelStatus.writeToBuffer(buf);

        // Channel data length
        /*
        Channel data length: this data object shall contain the number of bytes received,
        e.g. available in the channel buffer. If
        more than 255 bytes are available, "FF" is used.
        */
        buf.write(ComprehensionTlvTag.CHANNEL_DATA_LENGTH.value());
        buf.write(0x01);
        int len = (dataLength > 0xff) ? (byte) 0xff : (byte) dataLength;
        buf.write(len);

        byte[] additionalInfo = buf.toByteArray();
        UtkResponseMessage resMsg = new UtkResponseMessage(
          UtkService.EVENT_LIST_DATA_AVAILABLE, 0x82, 0x81, additionalInfo, false);

        mBipService.sendResponseToUtk(UtkService.MSG_ID_EVENT_DOWNLOAD, 0, 0, resMsg);
    }

    protected void startDataToReceiveTimer(final Handler caller) {
        UtkLog.d("BipChannel", "startDataToReceiveTimer");

        synchronized (mTimerLock) {
            if (mDataToReceiveTimer != null) {
                mDataToReceiveTimer.cancel();
                mDataToReceiveTimer.purge();
            }

            mDataToReceiveTimer = new Timer();

            mDataToReceiveTimer.schedule(new TimerTask() {
                public void run() {
                    Message.obtain(caller, EVENT_TIMER_TICK, null).sendToTarget();
                }
            }, CHANNEL_TIMEUPDATE_PERIOD, CHANNEL_TIMEUPDATE_PERIOD);
        }
    }

    protected void stopDataToReceiveTimer() {
        UtkLog.d("BipChannel", "stopDataToReceiveTimer");

        synchronized (mTimerLock) {
            if (mDataToReceiveTimer != null) {
              mDataToReceiveTimer.cancel();
              mDataToReceiveTimer.purge();
              mDataToReceiveTimer = null;
            }
        }
    }

    public void notifyChannelStatusChanged() {
        UtkLog.d("BipChannel", " notifyChannelStatusChanged");
        /*
        detects one of the following changes:
          - a TCP connection is closed for Terminal Server Mode;
          - a state change in a TCP connection for UICC Server Mode
            (i.e. a transition to any of these states: TCP in
            LISTEN state, TCP in CLOSED state, TCP in ESTABLISHED state);
          - a link enters an error condition;
          - the user cancels the ongoing session, or
          - any other error,
        which is not resulting from the execution of a proactive command, or
          - the link was established or link establishing failed

        after an OPEN CHANNEL in background mode, the terminal shall inform the UICC
        that this has occurred, by using the
        ENVELOPE (EVENT DOWNLOAD - Channel status) command as defined in clause 7.5.11.2
        */
        /*if( !(
            ((mChannelStatus.getType() == BipConstants.BIP_CHANNEL_TYPE_UE_SERVER_MODE)&&
            (newStatus == BipConstants.CHANNEL_STATUS_NO_LINK))||
            (mChannelStatus.getType() == BipConstants.BIP_CHANNEL_TYPE_UICC_SERVER_MODE)||
            (newStatus == BipConstants.CHANNEL_STATUS_ERROR)
            )){
            UtkLog.d("BipChannel", " need not notify");
            return;
        }*/

        /*
        Channel status 8.56
          - this data object shall contain the status and identifier of the channel
            on which the event occurred.
        Bearer Description 8.52
          - this data object shall only be present after an OPEN CHANNEL in background mode.
        Other address (local address)
          - this data object shall only be present after an OPEN CHANNEL in background mode
            with dynamic local address request.
        */

        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // Channel status
        mChannelStatus.writeToBuffer(buf);

        if (mChannelParams.backgrountMode) {
            UtkLog.d("BipChannel", " add bearerDesc & local address");

            //Bearer Description
            if (mChannelParams.bearerDesc != null) {
                mChannelParams.bearerDesc.writeToBuffer(buf);
            }
            //Other address
            if (mChannelParams.localAddress != null) {
                mChannelParams.localAddress.writeToBuffer(buf);
            }
        }
        byte[] additionalInfo = buf.toByteArray();
        UtkResponseMessage resMsg = new UtkResponseMessage(
          UtkService.EVENT_LIST_CHANNEL_STATUS, 0x82, 0x81, additionalInfo, false);
        mBipService.sendResponseToUtk(UtkService.MSG_ID_EVENT_DOWNLOAD, 0, 0, resMsg);
    }

    public abstract int linkEstablish();
    public abstract int linkDisconnect(boolean listen);

    public void receiveData(int reqDataLen)
    {
        UtkLog.d("BipChannel", " receiveData:" + reqDataLen);

        int remaining = 0;
        mDataAvailableLength = 0;
        boolean reqChanged = false;

        stopDataToReceiveTimer();

        synchronized (mRxLock) {
            if (mRxBuffer == null || mRxBuffer.length == 0) {
                UtkLog.d("BipChannel", " mRxBuffer is null");

                mBipService.sendResponseToUtk(UtkService.MSG_ID_RECEIVED_DATA,
                            BipConstants.RESULT_ERROR, BipConstants.RESULT_CODE_BIP_ERROR, null);
                return;
            }

            if ((mRxBufferIndex + reqDataLen) > mRxBuffer.length) {
                reqDataLen = mRxBuffer.length - mRxBufferIndex;
                reqChanged = true;

                UtkLog.d("BipChannel", " reqDataLen>mRxBuffer.length, changed to: " + reqDataLen);
            }

            mReceiveDone = false;

            byte[] buffer = new byte[reqDataLen];
            System.arraycopy(mRxBuffer, mRxBufferIndex, buffer, 0, reqDataLen);
            mRxBufferIndex += reqDataLen;
            remaining = mRxBuffer.length - mRxBufferIndex;

            UtkLog.d("BipChannel", " receiveData:" + reqDataLen + " remaining:" + remaining);

            if (remaining > 0xff) {
                remaining = 0xff;
            }
            if (reqChanged) {
                mBipService.sendResponseToUtk(UtkService.MSG_ID_RECEIVED_DATA,
                            BipConstants.RESULT_ERROR, 
                            BipConstants.RESULT_CODE_PRFRMD_WITH_MISSING_INFO, buffer);
            } else {
                mBipService.sendResponseToUtk(UtkService.MSG_ID_RECEIVED_DATA,
                             BipConstants.RESULT_SUCCESS, remaining, buffer);
            }
        }
    }

    public void sendData(byte[] data, boolean sendImmediately) {
        UtkLog.d("BipChannel", " sendData sendImmediately:" + sendImmediately);

        mDataAvailableLength = 0;
        stopDataToReceiveTimer();

        synchronized (mTxLock) {
            mTxBuffer.write(data, 0, data.length);
            mTxAvaSize -= data.length;
        }

        UtkLog.d("BipChannel", " sendData mTxAvaSize:" + mTxAvaSize);

        if (sendImmediately) {
            //
        } else {
            //respons now
            if (mTxAvaSize < 0) {
                mBipService.sendResponseToUtk(UtkService.MSG_ID_SENT_DATA,
                          BipConstants.RESULT_ERROR, BipConstants.RESULT_CODE_BIP_ERROR, null);
            } else {
                int[] avaBufSize = new int[1];
                avaBufSize[0] = mTxAvaSize;
                mBipService.sendResponseToUtk(UtkService.MSG_ID_SENT_DATA,
                          BipConstants.RESULT_SUCCESS, BipConstants.RESULT_CODE_OK, avaBufSize);
            }
        }
    }

    public ChannelStatus getChannelStatus() {
        ChannelStatus s = null;
        synchronized (mChannelStatus) {
            s = new ChannelStatus(mChannelStatus);
        }

        UtkLog.d("BipChannel", " getChannelStatus:" + s);

        return s;
    }

    public int getBipChannelId() {
        return mChannelStatus.getId();
    }
}

///////////////////////////////////////////////////////////////////////////////
class TcpClientChannel extends BipChannel {

    TcpClientChannel(BipService bs, OpenChannelSettings p, int id) {
        super(bs, p, id);
    }

    public int linkEstablish() {
        UtkLog.d("TcpClientChannel", " linkEstablish");

        int ret = BipConstants.RESULT_ERROR;
        InetAddress address = mChannelParams.destAddress.address;
        int port = mChannelParams.transportLevel.port;
        InetSocketAddress socketAddress = null;
        try {
            mSocket = new Socket();
            socketAddress = new InetSocketAddress(address, port);

            UtkLog.d("TcpClientChannel", " mSocket.connect...");
            try {
                mSocket.connect(socketAddress);
            } catch (SocketTimeoutException e) {
                //
            }

            if (mSocket.isConnected()) {
                UtkLog.d("TcpClientChannel", " mSocket.connect OK");
                mChannelStatus.setStatus(BipConstants.CHANNEL_STATUS_LINKED);
                if (mChannelParams.backgrountMode) {
                    notifyChannelStatusChanged();
                }
                ret = BipConstants.RESULT_SUCCESS;
            } else {
                UtkLog.d("TcpClientChannel", " mSocket.connect fail");
                //notifyChannelStatusChanged();
                mSocket.close();
            }
        } catch (IOException e) {
            //
        } catch (NullPointerException e) {
            //
        }

        UtkLog.d("TcpClientChannel", " ret:" + ret);

        if (ret == BipConstants.RESULT_SUCCESS) {
            mReceiver = new TcpReceiver(mSocket, mRxBufferSize, this);
            Thread thd = new Thread(mReceiver);
            thd.start();
        }

        return ret;
    }

    public int linkDisconnect(boolean listen) {
        UtkLog.d("TcpClientChannel", " linkDisconnect");

        mChannelStatus.setStatus(BipConstants.CHANNEL_STATUS_NO_LINK);
        //notifyChannelStatusChanged();

        if (mReceiver != null) {
            mReceiver.stop();
            mReceiver = null;
        }

        try {
            if (null != mSocket) {
                //will interrupt receiver thread!!
                mSocket.close();
                mSocket = null;
            }
        } catch (IOException e) {
            //
        }

        mRxBuffer = null;
        mDataAvailableLength = 0;

        stopDataToReceiveTimer();

        return BipConstants.RESULT_SUCCESS;
    }

    @Override
    public void receiveData(int reqDataLen) {

        super.receiveData(reqDataLen);

        synchronized (mRxLock) {
            if (mRxBuffer != null && mRxBuffer.length == mRxBufferIndex) {
                UtkLog.d("TcpClientChannel", " mRxBuffer data receive done");
                mRxBuffer = null;
                mRxBufferIndex = 0;

                mReceiveDone = true;
            }
        }

        UtkLog.d("TcpClientChannel", " mReceiveDone=" + mReceiveDone);
        if (mReceiveDone) {
          //get next data packet
          if (mReceiveMsgQ.size() > 0) {
              UtkLog.d("TcpClientChannel", " send delay msg");
              Message msg = mReceiveMsgQ.poll();
              sendMessageDelayed(msg, 100); //delay
          }
        }
    }

    public void sendData(byte[] data, boolean sendImmediately) {
        UtkLog.d("TcpClientChannel", " sendData sendImmediately:" + sendImmediately);

        super.sendData(data, sendImmediately);

        if (sendImmediately) {
            if (mTxAvaSize < 0) {
                mBipService.sendResponseToUtk(UtkService.MSG_ID_SENT_DATA,
                              BipConstants.RESULT_ERROR, BipConstants.RESULT_CODE_BIP_ERROR, null);
            } else {
                byte[] dataToSend;
                synchronized (mTxLock) {
                    byte[]tmp = mTxBuffer.toByteArray();
                    dataToSend = new byte[tmp.length];
                    System.arraycopy(tmp, 0, dataToSend, 0, tmp.length);

                    mTxBuffer.reset();
                    mTxAvaSize = mRxBufferSize;
                }

                Thread thd = new Thread(new TcpSender(mSocket, dataToSend, this));
                thd.start();
            }
        }
    }
}

///////////////////////////////////////////////////////////////////////////////
class TcpServerChannel extends TcpClientChannel {
    private ListenPortManager mListen;
    private int mListenPort;

    TcpServerChannel(BipService bs, OpenChannelSettings p, int id) {
        super(bs, p, id);
        mListenPort = p.transportLevel.port; ////this port??????
    }

    @Override
    public int linkEstablish() {
        int ret = BipConstants.RESULT_SUCCESS;

        UtkLog.d("TcpServerChannel", " linkEstablish");

        mListen = ListenPortManager.getInstance();
        mListen.addListenPort(mListenPort, this);

        mChannelStatus.setStatus(BipConstants.CHANNEL_STATUS_LISTEN);
        //notifyChannelStatusChanged();
        if (!this.mIsSocketReady) {
            ret = BipConstants.RESULT_ERROR;
            UtkLog.d("TcpServerChannel", " linkEstablish, ret = BipConstants.RESULT_ERROR");
        }

        return ret;
    }

    @Override
    public int linkDisconnect(boolean listen) {
        UtkLog.d("TcpServerChannel", " linkDisconnect");

        if (listen) {
            UtkLog.d("TcpServerChannel", " keep listen:" + mListenPort);

            //mListen.addListenPort(mListenPort, this);

            mChannelStatus.setStatus(BipConstants.CHANNEL_STATUS_LISTEN);
            //notifyChannelStatusChanged();
        } else {
            mListen.removeListenPort(mListenPort, this);

            mChannelStatus.setStatus(BipConstants.CHANNEL_STATUS_NO_LINK);
            //notifyChannelStatusChanged();
        }

        if (mReceiver != null) {
            mReceiver.stop();
            mReceiver = null;
        }

        try {
            if (null != mSocket) {
                mSocket.close();
                mSocket = null;
            }
        } catch (IOException e) {
            //
        }

        mRxBuffer = null;
        mDataAvailableLength = 0;

       stopDataToReceiveTimer();

        return BipConstants.RESULT_SUCCESS;
    }
}

///////////////////////////////////////////////////////////////////////////////
class UdpClientChannel extends BipChannel {
    private DatagramSocket mDatagramSocket = null;

    private InetAddress mRemoteAddress = null;
    private int mRemotePort = 0;
    private InetAddress mLocalAddress = null;
    private int mLocalPort = 0;

    UdpClientChannel(BipService bs, OpenChannelSettings p, int id) {
        //
        super(bs, p, id);

        mRemoteAddress = mChannelParams.destAddress.address;
        mRemotePort = mChannelParams.transportLevel.port; ////this port ??????
        //mLocalAddress = //????
        //mLocalPort = //????
    }

    public int linkEstablish() {
        UtkLog.d("UdpClientChannel", " linkEstablish");

        try {
            mDatagramSocket = new DatagramSocket(); //local ip & port ??????
        } catch (SocketException e) {
            UtkLog.d("UdpClientChannel", " get datagram socket fail");
            return BipConstants.RESULT_ERROR;
        }

        mReceiver = new UdpReceiver(mDatagramSocket, mRxBufferSize, this);
        Thread thd = new Thread(mReceiver);
        thd.start();

        mChannelStatus.setStatus(BipConstants.CHANNEL_STATUS_LINKED);
        if (mChannelParams.backgrountMode) {
            notifyChannelStatusChanged();
        }

        //
        return BipConstants.RESULT_SUCCESS;
    }

    public int linkDisconnect(boolean listen) {
        UtkLog.d("UdpClientChannel", " linkDisconnect");

        if (mReceiver != null) {
            mReceiver.stop();
            mReceiver = null;
        }

        if (mDatagramSocket != null) {
            mDatagramSocket.close();
            mDatagramSocket = null;
            mRxBuffer = null;
        }

        mDataAvailableLength = 0;

        mChannelStatus.setStatus(BipConstants.CHANNEL_STATUS_NO_LINK);
        //notifyChannelStatusChanged();

       stopDataToReceiveTimer();

        return BipConstants.RESULT_SUCCESS;
    }

    @Override
    public void receiveData(int reqDataLen) {

        super.receiveData(reqDataLen);

        synchronized (mRxLock) {
            if (mRxBuffer != null && mRxBuffer.length == mRxBufferIndex) {
                UtkLog.d("UdpClientChannel", " mRxBuffer data receive done");

                mRxBuffer = null;
                mRxBufferIndex = 0;

                mReceiveDone = true;
            }
        }

        UtkLog.d("UdpClientChannel", " mReceiveDone=" + mReceiveDone);
        if (mReceiveDone) {
          //get next data packet
          if (mReceiveMsgQ.size() > 0) {
              UtkLog.d("UdpClientChannel", " send delay msg");
              Message msg = mReceiveMsgQ.poll();
              sendMessageDelayed(msg, 100); //delay
          }
        }
    }

    public void sendData(byte[] data, boolean sendImmediately) {
        UtkLog.d("UdpClientChannel", " sendData sendImmediately:" + sendImmediately);

        super.sendData(data, sendImmediately);

        if (sendImmediately) {
            if (mTxAvaSize < 0) {
                mBipService.sendResponseToUtk(UtkService.MSG_ID_SENT_DATA,
                              BipConstants.RESULT_ERROR, BipConstants.RESULT_CODE_BIP_ERROR, null);
            } else {
                byte[] dataToSend;
                synchronized (mTxLock) {
                    byte[]tmp = mTxBuffer.toByteArray();
                    dataToSend = new byte[tmp.length];
                    System.arraycopy(tmp, 0, dataToSend, 0, tmp.length);

                    mTxBuffer.reset();
                    mTxAvaSize = mRxBufferSize;
                }

                Thread thd = new Thread(new UdpSender(mDatagramSocket, mRemoteAddress,
                                                      mRemotePort, dataToSend, this));
                thd.start();
            }
        }
    }
}

///////////////////////////////////////////////////////////////////////////////
abstract class BipRunnable implements Runnable {
    protected boolean mRun = true;
    protected BipChannel mCaller = null;
    protected byte[] cacheBuf = null;

    public void sendMessageToChannel(int what, int arg1, int arg2, Object obj) {
        UtkLog.d("BipRunnable", " sendMessageToChannel:" + what);
        if (mCaller == null) {
            UtkLog.d("BipRunnable", " no caller");
            return;
        }

        Message m = mCaller.obtainMessage(what, arg1, arg2, obj);
        mCaller.sendMessage(m);
    }

    public boolean compAndCache(byte[] data) {
        UtkLog.d("BipRunnable", " compAndCache");
        String cb = IccUtils.bytesToHexString(cacheBuf);
        String cd = IccUtils.bytesToHexString(data);
        if (cb == null && cb == cd) {
            UtkLog.d("BipRunnable", " compAndCache: both = null");
            return true;
        }
        boolean isSame = cb.equals(cd);
        if (isSame) {
            UtkLog.d("BipRunnable", " compAndCache: cacheBuf = " + cb);
            UtkLog.d("BipRunnable", " compAndCache: data = " + cd);
            Arrays.fill(cacheBuf, (byte)0);
            System.arraycopy(data, 0, cacheBuf, 0, data.length);
        }
        return isSame;
    }

    public void stop() {
        UtkLog.d("BipRunnable", " stop");
        cacheBuf = null;
        mRun = false;
    }
}

class ListenPortManager {
    private static ListenPortManager sInstance = null;
    private HashMap<Integer, AcceptRunable> mAcceptRunableHash =
                                                    new HashMap<Integer, AcceptRunable>();

    public static ListenPortManager getInstance() {
        if (sInstance == null) {
            UtkLog.d("ListenPortManager", " new ListenPortManager");
            sInstance = new ListenPortManager();
        }

        return sInstance;
    }

    public void addListenPort(int port, BipChannel ch) {
        UtkLog.d("ListenPortManager", " addListenPort:" + port + " ch=" + ch);

        AcceptRunable r = null;
        synchronized (mAcceptRunableHash) {
            r = mAcceptRunableHash.get(port);
        }

        if (r == null) {
            if (ch != null) {
                UtkLog.d("ListenPortManager", " AcceptRunable = null, create it" +
                        "and set ch.mIsSocketReady = false by default.");
                ch.mIsSocketReady = false;
            }
            r = new AcceptRunable(port, ch);
            if (ch != null && !ch.mIsSocketReady) {
                UtkLog.d("ListenPortManager", " AcceptRunable = null, create it" +
                        "AcceptRunable owns a null ServerSocket instance, fail and return here.");
                return;
            }

            Thread thd = new Thread(r);
            thd.start();

            synchronized (mAcceptRunableHash) {
                mAcceptRunableHash.put(port, r);
            }
        } else {
            r.addRspHandler(ch);
        }
    }

    public void removeListenPort(int port, BipChannel ch) {
        UtkLog.d("ListenPortManager", " removeListenPort:" + port + " ch=" + ch);

        AcceptRunable r = null;
        synchronized (mAcceptRunableHash) {
            r = mAcceptRunableHash.get(port);
        }

        if (r == null) {
            UtkLog.d("ListenPortManager", " not found this ch");
        } else {
            if (r.removeRspHandler(ch)) {
                //should stop AcceptRunable r
                r.stop();

                synchronized (mAcceptRunableHash) {
                    mAcceptRunableHash.remove(r);
                }
            }
        }
    }
}

class AcceptRunable extends BipRunnable {
    private ServerSocket mServerSocket;
    private int mPort;
    private ArrayList<BipChannel> mBipChannelList = new ArrayList<BipChannel>();

    AcceptRunable(int port, BipChannel ch) {
        mPort = port;
        addRspHandler(ch);

        try {
            //
            mServerSocket = new ServerSocket(port, 0, Inet4Address.LOOPBACK);
        } catch (IOException e) {
            UtkLog.d("AcceptRunable", " get server socket fail");
        }

        if (mServerSocket != null & ch != null) {
            ch.mIsSocketReady = true;
        }
    }

    public void addRspHandler(BipChannel ch) {
        if (ch == null) {
            return;
        }
        UtkLog.d("AcceptRunable", " addRspHandler=" + ch);

        synchronized (mBipChannelList) {
            mBipChannelList.add(ch);
        }
    }

    public boolean removeRspHandler(BipChannel ch) {
        if (ch == null) {
            return false;
        }
        UtkLog.d("AcceptRunable", " removeRspHandler=" + ch);
        synchronized (mBipChannelList) {
            mBipChannelList.remove(ch);
            if (mBipChannelList.size() == 0) {
                UtkLog.d("AcceptRunable", " stop listen this port");

                //interrupt accept runable!!
                if (mServerSocket != null) {
                    try {
                        mServerSocket.close();
                    } catch (IOException e) {
                        //
                    }
                }

                return true;
            }
        }

        return false;
    }

    @Override
    public void run() {
        UtkLog.d("AcceptRunable", " enter AcceptRunable");

        while (mRun) {
            Socket socket = null;

            try {
                //wait client to connect
                UtkLog.d("AcceptRunable", "linsten...");
                socket = mServerSocket.accept();
                UtkLog.d("AcceptRunable", "accepted");
            } catch (IOException e) {
                UtkLog.d("AcceptRunable", "catch error, exit AcceptRunable");
            }

            if (socket != null) {
                synchronized (mBipChannelList) {
                    BipChannel ch = mBipChannelList.get(0);
                    if (ch != null) {
                        Message m = ch.obtainMessage(BipChannel.EVENT_ACCEPT_REQUEST, 0, 0, socket);
                        ch.sendMessage(m);
                    } else {
                        UtkLog.d("AcceptRunable", "no ch wait socket");

                        try {
                            socket.close();
                        } catch (IOException e) {
                            //
                        }
                    }
                }
            }
        }

        UtkLog.d("AcceptRunable", " exit AcceptRunable");
    }
}

///////////////////////////////////////////////////////////////////////////////
class TcpReceiver extends BipRunnable {
    private InputStream mInStream;
    private int mBufSize;

    TcpReceiver(Socket socket, int bufSize, BipChannel ch) {
        mCaller = ch;
        mBufSize = bufSize;

        try {
           //get input stream
           mInStream = socket.getInputStream();
        } catch (IOException e) {
           UtkLog.d("TcpReceiver", " get input stream fail");
        }
    }

    @Override
    public void run() {
        UtkLog.d("TcpReceiver", " enter TcpReceiver Thread");

        while (mRun) {
            byte[] tmp = new byte[mBufSize];
            int readLen = 0;

            try {
                 //get input data, block
                 UtkLog.d("TcpReceiver", " wait data comming...");
                 readLen = mInStream.read(tmp);
                 UtkLog.d("TcpReceiver", " read data len=" + readLen);

                 if (readLen > 0) {
                    byte[] buf;
                    if (readLen < mBufSize) {
                      buf = new byte[readLen];
                      System.arraycopy(tmp, 0, buf, 0, readLen);
                    } else {
                        buf = tmp;
                    }

                    sendMessageToChannel(BipChannel.EVENT_RECEIVED_DATA,
                                            BipConstants.RESULT_SUCCESS, 0, buf);
                }
            } catch (IOException e) {
                 UtkLog.d("TcpReceiver", " catch IOException");
            }
        }

        try {
            mInStream.close();
        } catch (IOException e) {
            //
        }

        UtkLog.d("TcpReceiver", " exit TcpReceiver Thread");
     }
  }

///////////////////////////////////////////////////////////////////////////////
class TcpSender extends BipRunnable {
    private OutputStream mOutStream = null;
    private byte[] mDataToSend;

    TcpSender(Socket socket, byte[] data, BipChannel ch) {
        mCaller = ch;
        mDataToSend = data;

        try {
            //get output stream
            mOutStream = socket.getOutputStream();
        } catch (IOException e) {
            sendMessageToChannel(BipChannel.EVENT_SENT_DATA, BipConstants.RESULT_ERROR, 0, null);
            UtkLog.d("TcpSender", " get out stream fail");
        }
    }

    @Override
    public void run() {
        UtkLog.d("TcpSender", " enter TcpSend Thread");

        try {
            //send data
            UtkLog.d("TcpSender", " sending");
            mOutStream.write(mDataToSend);
            UtkLog.d("TcpSender", " sent length=" + mDataToSend.length);

            //no param!
            sendMessageToChannel(BipChannel.EVENT_SENT_DATA, BipConstants.RESULT_SUCCESS, 0, null);
        } catch (IOException e) {
            sendMessageToChannel(BipChannel.EVENT_SENT_DATA, BipConstants.RESULT_ERROR, 0, null);
            UtkLog.d("TcpSender", " catch IOException");
        }

        try {
            mOutStream.close();
        } catch (IOException e) {
            //
        }

        UtkLog.d("TcpSender", " exit TcpSend Thread");
    }
}

///////////////////////////////////////////////////////////////////////////////
class UdpReceiver extends BipRunnable {
    private DatagramSocket mDatagramSocket;
    private int mBufSize;

    UdpReceiver(DatagramSocket dataSocket, int bufSize, BipChannel ch) {
        mDatagramSocket = dataSocket;
        mCaller = ch;
        mBufSize = bufSize;
        cacheBuf = new byte[mBufSize];
    }

    @Override
    public void run() {
        UtkLog.d("UdpReceiver", " enter UdpReceiver Thread");

        while (mRun) {
            byte[] tmp = new byte[mBufSize];

            DatagramPacket datagramPacket = new DatagramPacket(tmp, tmp.length);

            int dataLen = 0;
            try {
                mDatagramSocket.receive(datagramPacket);
                dataLen = datagramPacket.getLength();

                UtkLog.d("UdpReceiver", " receive data len=" + dataLen);

                if (dataLen > 0) {
                    byte[] buf;
                    if (dataLen < mBufSize) {
                      buf = new byte[dataLen];
                      System.arraycopy(tmp, 0, buf, 0, dataLen);
                    } else {
                        buf = tmp;
                    }

                    if (!compAndCache(buf)) {
                        sendMessageToChannel(BipChannel.EVENT_RECEIVED_DATA,
                            BipConstants.RESULT_SUCCESS, 0, buf);
                    }
                }
            } catch (IOException e) {
                UtkLog.d("UdpReceiver", " catch  IOException");
            }
        }

        UtkLog.d("UdpReceiver", " exit UdpReceiver Thread");
    }
}

///////////////////////////////////////////////////////////////////////////////
class UdpSender extends BipRunnable {
    private DatagramSocket mDatagramSocket;
    private DatagramPacket mDatagramPacket;
    private byte[] mDataToSend;

    UdpSender(DatagramSocket dataSocket, InetAddress address, int port,
              byte[] data, BipChannel ch) {
        mDatagramSocket = dataSocket;
        mDataToSend = data;
        mDatagramPacket = new DatagramPacket(new byte[1], 1, address, port);
        mCaller = ch;
    }

    @Override
    public void run() {
        UtkLog.d("UdpSender", " enter UdpSender Thread");

        mDatagramPacket.setData(mDataToSend);

        try {
            UtkLog.d("UdpSender", " sending");
            mDatagramSocket.send(mDatagramPacket);
            UtkLog.d("UdpSender", " sent length=" + mDatagramPacket.getLength());

            //no param!
            sendMessageToChannel(BipChannel.EVENT_SENT_DATA, BipConstants.RESULT_SUCCESS, 0, null);
        } catch (Exception e) {
            sendMessageToChannel(BipChannel.EVENT_SENT_DATA, BipConstants.RESULT_ERROR, 0, null);
            UtkLog.d("UdpSender", " catch Exception");
        }

        UtkLog.d("UdpSender", " exit UdpSender Thread");
    }
}

