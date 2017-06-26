package com.mediatek.internal.telephony.gsm;

import android.util.Log;

import android.telecom.VideoProfile;
import android.view.Surface;
import android.os.SystemProperties;



public class GsmVTProvider extends GsmVideoCallProvider {

    static {
        if (SystemProperties.get("ro.mtk_vt3g324m_support").equals("1")) {
            System.loadLibrary("mtk_vt_client");
        }
    }

    public enum State {
        CLOSE, OPEN, READY, CONNECTED
    }

    /*****
     * VT Manager's State
     */
    public static final int SET_VT_CLOSE = 0;
    public static final int SET_VT_OPEN = 1;
    public static final int SET_VT_READY = 2;
    public static final int SET_VT_CONNECTED = 3;
    public static final int QUIT_THREAD = 0x8000000;


    public static final int VT_PROVIDER_INVALIDE_ID                             = -10000;

    public static final int MSG_NOTIFY_RECEIVE_FIRSTFRAME                       = 0x0001;
    public static final int MSG_NOTIFY_PEER_CAMERA_OPEN                         = 0x0002;
    public static final int MSG_NOTIFY_PEER_CAMERA_CLOSE                        = 0x0003;
    public static final int MSG_NOTIFY_SNAPSHOT_DONE                            = 0x0004;
    public static final int MSG_NOTIFY_RECORDER_EVENT_INFO_UNKNOWN              = 0x0005;
    public static final int MSG_NOTIFY_RECORDER_EVENT_INFO_REACH_MAX_DURATION   = 0x0006;
    public static final int MSG_NOTIFY_RECORDER_EVENT_INFO_REACH_MAX_FILESIZE   = 0x0007;
    public static final int MSG_NOTIFY_RECORDER_EVENT_INFO_NO_I_FRAME           = 0x0008;
    public static final int MSG_NOTIFY_RECORDER_EVENT_INFO_COMPLETE             = 0x0009;
    public static final int MSG_NOTIFY_CALL_END                                 = 0x000A;

    public static final int MSG_NOTIFY_CLOSE                                    = 0x1001;
    public static final int MSG_NOTIFY_OPEN                                     = 0x1002;
    public static final int MSG_NOTIFY_READY                                    = 0x1003;
    public static final int MSG_NOTIFY_CONNECTED                                = 0x1004;
    public static final int MSG_NOTIFY_DISCONNECTED                             = 0x1005;
    public static final int MSG_NOTIFY_START_COUNTER                            = 0x1007;

    public static final int MSG_NOTIFY_RECV_SESSION_CONFIG_REQ                  = 0x4001;
    public static final int MSG_NOTIFY_RECV_SESSION_CONFIG_RSP                  = 0x4002;
    public static final int MSG_NOTIFY_HANDLE_CALL_SESSION_EVT                  = 0x4003;
    public static final int MSG_NOTIFY_PEER_SIZE_CHANGED                        = 0x4004;
    public static final int MSG_NOTIFY_LOCAL_SIZE_CHANGED                       = 0x4005;
    public static final int MSG_NOTIFY_DATA_USAGE_CHANGED                       = 0x4006;
    public static final int MSG_NOTIFY_CAM_CAP_CHANGED                          = 0x4007;

    public static final int MSG_ERROR_SERVICE                                   = 0x8001;
    public static final int MSG_ERROR_SERVER_DIED                               = 0x8002;
    public static final int MSG_ERROR_CAMERA                                    = 0x8003;
    public static final int MSG_ERROR_CODEC                                     = 0x8004;

    static final String                         TAG = "GsmVTProvider";

    private int                                 mId = 1;
    private GsmVTProviderUtil                   mUtil;
    private static int                          mDefaultId = VT_PROVIDER_INVALIDE_ID;

    State mState = State.CLOSE;

    VTSettings mSettings;

    private Integer mEndCallLock = new Integer(0);

    private boolean mClosingVTService = false;
    private boolean mStartVTSMALFail = false;

    public GsmVTProvider(int id) {
        super();

        Log.d(TAG, "New GsmVTProvider id = " + id);

        mState = State.CLOSE;
        mSettings = new VTSettings();

        mId = id;
        mUtil = new GsmVTProviderUtil();
        GsmVTProviderUtil.recordAdd(mId, this);
        //nInitialization(mId);
        onSetVTOpen();

        if (mDefaultId == VT_PROVIDER_INVALIDE_ID) {
            mDefaultId = mId;
        }
    }

    public GsmVTProvider() {
        super();
        Log.d(TAG, "New GsmVTProvider without id");
        mId = VT_PROVIDER_INVALIDE_ID;

        mState = State.CLOSE;
        mSettings = new VTSettings();
    }

    public void setId(int id) {
        Log.d(TAG, "setId id = " + id);
        Log.d(TAG, "setId mId = " + mId);

        if (mId == VT_PROVIDER_INVALIDE_ID) {
            mId = id;
            mUtil = new GsmVTProviderUtil();
            GsmVTProviderUtil.recordAdd(mId, this);
            //nInitialization(mId);
            onSetVTOpen();

            if (mDefaultId == VT_PROVIDER_INVALIDE_ID) {
                mDefaultId = mId;
            }
        }
    }

    public int getId() {
        return mId;
    }

    public static native int openVTService(int id);
    public static native int initVTService(Surface local, Surface peer);
    public static native int startVTService();
    public static native int stopVTService();
    public static native int closeVTService();

    public static native void setEndCallFlag();

    public static native void setLocalView(int type, String path);
    public static native int setPeerView(int enableFlag, String filePath);

    public static native int switchCamera();

    public static native int setVTVisible(int isOn, Surface local, Surface peer);
    public static native void onUserInput(String input);

    public static native void setEM(int item, int arg1, int arg2);

    //
    public static native int nInitialization(int id);
    public static native int nFinalization(int id);
    public static native int nSetCamera(int id, int cam);
    public static native int nSetPreviewSurface(int id, Surface surface);
    public static native int nSetDisplaySurface(int id, Surface surface);
    public static native int nSetCameraParameters(int id, String config);
    public static native int nSetDeviceOrientation(int id, int rotation);
    public static native String nGetCameraParameters(int id);
    public static native int nGetCameraSensorCount(int id);
    public static native int nRequestPeerConfig(int id, String config);
    public static native int nResponseLocalConfig(int id, String config);
    public static native int nRequestCameraCapabilities(int id);
    public static native int nRequestCallDataUsage(int id);
    public static native int nSnapshot(int id, int type, String uri);
    public static native int nStartRecording(int id, int type, String url, long maxSize);
    public static native int nStopRecording(int id);
    public static native int nSetUIMode(int id, int mode);

    /* AOSP interface */
    public void onSetCamera(String cameraId) {
    }

    public void onSetPreviewSurface(Surface surface) {
    }

    public void onSetDisplaySurface(Surface surface) {
    }

    public void onSetDeviceOrientation(int rotation) {
    }

    public void onSetZoom(float value) {
    }

    public void onSendSessionModifyRequest(VideoProfile requestProfile) {
    }

    public void onSendSessionModifyResponse(VideoProfile responseProfile) {
    }

    public void onRequestCameraCapabilities() {
    }

    public void onRequestCallDataUsage() {
    }

    public void onSetPauseImage(String uri) {
    }

    public void onSetUIMode(int mode) {
    }

    public void onSetVTOpen() {
        Log.w(TAG, "setVTOpen start");
        setEM(0, 0, 0);
        setEM(1, 0, 0);
        setEM(1, 1, 0);
        setEM(1, 2, 0);
        setEM(1, 3, 0);
        setEM(1, 4, 0);
        setEM(1, 5, 0);
        setEM(1, 6, 0);
        setEM(3, 0, 0);
        setEM(3, 1, 0);
        setEM(4, 0, 0);
        setEM(4, 1, 0);
        setEM(5, 0, 0);
        setEM(6, 0, 0);
        setEM(7, 1, 0);
        setEM(8, 0, 28);
        setEM(8, 1, 28);
        setEM(8, 2, 28);
        setEM(8, 3, 28);
        setEM(9, 0, 0);

        if (mState != State.CLOSE) {
            Log.e(TAG, "setVTOpen, mState != State.CLOSE");
            return;
        }

        mSettings.init(this);
        mClosingVTService = false;

        int ret = openVTService(mId);
        if (0 != ret) {
            Log.e(TAG, "setVTOpenImpl, error");
            return;
        }
        mState = State.OPEN;
        Log.i(TAG, mState.toString());

        handleCallSessionEvent(MSG_NOTIFY_OPEN);
        Log.w(TAG, "setVTOpen finish");
    }

    public void onSetVTReady() {
        Log.w(TAG, "setVTReady start");

        if ((State.OPEN != mState) && (State.CLOSE != mState)) {
            Log.e(TAG, "setVTReadyImpl, error");
            return;
        }

        int ret = initVTService(mSettings.getLocalSurface(), mSettings.getPeerSurface());

        if (0 != ret) {
            mStartVTSMALFail = true;
            Log.e(TAG, "setVTReadyImpl, error");
            return;
        }

        mState = State.READY;
        Log.i(TAG, mState.toString());

        mSettings.getCameraSettings();

        handleCallSessionEvent(MSG_NOTIFY_READY);
        Log.w(TAG, "setVTReady finish");
    }

    public void onSetVTConnected() {
        Log.w(TAG, "setVTConnected start");

        if (State.CONNECTED == mState) {
            return;
        }
        if (State.CLOSE == mState) {
            Log.e(TAG, "setVTConnected, error");
            return;
        }

        int ret = startVTService();
        if (0 != ret) {
            Log.e(TAG, "setVTConnected, error");
            return;
        }
        mState = State.CONNECTED;
        Log.i(TAG, mState.toString());

        handleCallSessionEvent(MSG_NOTIFY_CONNECTED);

        Log.w(TAG, "setVTConnected finish");
    }

    public void onSetVTClose() {
        Log.w(TAG, "setVTClose start");

        if (State.CLOSE == mState) {
            Log.e(TAG, "setVTCloseImpl, error");
            return;
        }
        mSettings.deinit();

        handleCallSessionEvent(MSG_NOTIFY_CLOSE);

        // added for closing VT service
        mClosingVTService = true;

        synchronized (mEndCallLock) {
            int ret = closeVTService();
            if (0 != ret) {
                Log.e(TAG, "setVTCloseImpl, error");
                return;
            }
            mState = State.CLOSE;
            mStartVTSMALFail = false;
            Log.i(TAG, mState.toString());
        }

        Log.w(TAG, "setVTClose finish, mState = " + mState.toString());
    }

    public void onOnDisconnected() {
        Log.w(TAG, "setVTDisconnected start");

        if (State.CLOSE == mState) {
            Log.e(TAG, "onDisconnected, VT Manager alreay closed");
            return;
        }
        setEndCallFlag();

        Log.i(TAG, "onDisconnected");
        if (State.CONNECTED != mState) {
            Log.e(TAG, "onDisconnected, VT Manager state error");
            return;
        }
        int ret = stopVTService();
        if (0 != ret) {
            Log.e(TAG, "onDisconnected, error");
            return;
        }

        mState = State.READY;
        Log.i(TAG, mState.toString());

        handleCallSessionEvent(MSG_NOTIFY_DISCONNECTED);
        Log.w(TAG, "setVTDisconnected finish");
    }

    public void onSetDisplay(Surface local, Surface peer) {
        Log.i(TAG, "setDisplay " + local + ", " + peer);
        mSettings.setLocalSurface(local);
        mSettings.setPeerSurface(peer);
    }

    public void onSwitchDisplaySurface() {
    }

    public void onSetLocalView(int videoType, String path) {
        Log.w(TAG, "onSetLocalView");

        synchronized (mEndCallLock) {
            if (State.CLOSE == mState) {
                return;
            }
            setLocalView(videoType, path);
        }
    }

    public void onSetPeerView(int bEnableReplacePeerVideo, String sReplacePeerVideoPicturePath) {
        Log.w(TAG, "setPeerView");
        if (State.CLOSE == mState) {
            return;
        }
        setPeerView(bEnableReplacePeerVideo, sReplacePeerVideoPicturePath);
    }

    public void onSwitchCamera() {
        int ret = 0;
        synchronized (mEndCallLock) {
            if (State.CLOSE == mState) {
                Log.e(TAG, "onSwitchCamera, VT Manager alreay closed");
                return;
            }
            ret = switchCamera();
            mSettings.getCameraSettings();
        }

        if (0 != ret) {
            Log.e(TAG, "onSwitchCamera, error");
        }
        return;
    }

    public void onSetVTVisible(boolean isVisible) {
        Log.w(TAG, " => setVTVisible()");
        if (State.CLOSE == mState) {
            return;
        }
        if (!isVisible) {
            Log.w(TAG, " => setVTVisible() - isVisible=" + isVisible + " localS=null, peerS= null");
            setVTVisible(0, (Surface) (null), (Surface) (null));
        } else {
            if ((null == mSettings)) {
                Log.e(TAG, "error setVTVisible, null == mSettings");
                return;
            }
            if ((null == mSettings.getPeerSurface())) {
                Log.e(TAG, "error setVTVisible, null == getPeerSurface");
                return;
            }
            if ((null == mSettings.getPeerSurface())) {
                Log.e(TAG, "error setVTVisible, null == getSurface");
                return;
            }
            setVTVisible(1, mSettings.getLocalSurface(), mSettings.getPeerSurface());
        }
        Log.w(TAG, " <= setVTVisible()");
    }

    public void onOnUserInput(String input) {
        if (State.CLOSE == mState) {
            Log.e(TAG, "onUserInput, vtmanager state error");
            return;
        }
        onUserInput(input);
    }

    public void setParameters(CameraParamters params) {
        if (State.CLOSE == mState) {
            Log.e(TAG, "setParameters, vtmanager state error");
            return;
        }

        if (null == params) {
            Log.e(TAG, "setParameters: params == null");
            return;
        }

        Log.i(TAG, params.flatten());
        nSetCameraParameters(mId, params.flatten());
        Log.i(TAG, "setParameters ok");
    }

    public CameraParamters getParameters() {
        if (State.CLOSE == mState) {
            Log.e(TAG, "getParameters, vtmanager state error");
            return null;
        }

        CameraParamters p = new CameraParamters();
        String s = nGetCameraParameters(mId);
        p.unflatten(s);
        p.dump();
        return p;
    }

    public CameraParamters updateParameters(CameraParamters p) {
        if (State.CLOSE == mState) {
            Log.e(TAG, "updateParameters, vtmanager state error");
            return null;
        }

        if (null == p) {
            Log.e(TAG, "updateParameters: p == null");
            return p;
        }

        String s = nGetCameraParameters(mId);
        p.unflatten(s);
        return p;
    }

    public static void postEventFromNative(int what, int id, int arg1, int arg2, Object obj) {
        Log.i(TAG, "postEventFromNative [" + what + "]");

        GsmVTProvider vp = GsmVTProviderUtil.recordGet(id);

        vp.handleCallSessionEvent(what);
    }
}
