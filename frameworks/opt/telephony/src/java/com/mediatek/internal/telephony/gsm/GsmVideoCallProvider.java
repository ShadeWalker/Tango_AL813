/*
* Copyright (C) 2011-2014 MediaTek Inc.
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

package com.mediatek.internal.telephony.gsm;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.telecom.CameraCapabilities;
import android.telecom.VideoProfile;
import android.view.Surface;

/// M: For 3G VT only @{
import com.android.internal.os.SomeArgs;
/// @}

public abstract class GsmVideoCallProvider {
    private static final int MSG_SET_CALLBACK = 1;
    private static final int MSG_SET_CAMERA = 2;
    private static final int MSG_SET_PREVIEW_SURFACE = 3;
    private static final int MSG_SET_DISPLAY_SURFACE = 4;
    private static final int MSG_SET_DEVICE_ORIENTATION = 5;
    private static final int MSG_SET_ZOOM = 6;
    private static final int MSG_SEND_SESSION_MODIFY_REQUEST = 7;
    private static final int MSG_SEND_SESSION_MODIFY_RESPONSE = 8;
    private static final int MSG_REQUEST_CAMERA_CAPABILITIES = 9;
    private static final int MSG_REQUEST_CALL_DATA_USAGE = 10;
    private static final int MSG_SET_PAUSE_IMAGE = 11;
    /// M: For 3G VT only @{
    private static final int MSG_MTK_BASE = 100;
    private static final int MSG_SET_UI_MODE = MSG_MTK_BASE;
    private static final int MSG_SET_VT_OPEN = MSG_MTK_BASE + 1;
    private static final int MSG_SET_VT_READY = MSG_MTK_BASE + 2;
    private static final int MSG_SET_VT_CONNECTED = MSG_MTK_BASE + 3;
    private static final int MSG_SET_VT_CLOSE = MSG_MTK_BASE + 4;
    private static final int MSG_ON_DISCONNECTED = MSG_MTK_BASE + 5;
    private static final int MSG_SET_DISPLAY = MSG_MTK_BASE + 6;
    private static final int MSG_SWITCH_DISPLAY_SURFACE = MSG_MTK_BASE + 7;
    private static final int MSG_SET_LOCAL_VIEW = MSG_MTK_BASE + 8;
    private static final int MSG_SET_PEER_VIEW = MSG_MTK_BASE + 9;
    private static final int MSG_SWITCH_CAMERA = MSG_MTK_BASE + 10;
    private static final int MSG_SET_VT_VISIBLE = MSG_MTK_BASE + 11;
    private static final int MSG_ON_USER_INPUT = MSG_MTK_BASE + 12;
    /// @}

    private final GsmVideoCallProviderBinder mBinder;

    private IGsmVideoCallCallback mCallback;

    /**
     * Default handler used to consolidate binder method calls onto a single thread.
     */
    private final Handler mProviderHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SET_CALLBACK:
                    mCallback = (IGsmVideoCallCallback) msg.obj;
                    break;
                case MSG_SET_CAMERA:
                    onSetCamera((String) msg.obj);
                    break;
                case MSG_SET_PREVIEW_SURFACE:
                    onSetPreviewSurface((Surface) msg.obj);
                    break;
                case MSG_SET_DISPLAY_SURFACE:
                    onSetDisplaySurface((Surface) msg.obj);
                    break;
                case MSG_SET_DEVICE_ORIENTATION:
                    onSetDeviceOrientation(msg.arg1);
                    break;
                case MSG_SET_ZOOM:
                    onSetZoom((Float) msg.obj);
                    break;
                case MSG_SEND_SESSION_MODIFY_REQUEST:
                    onSendSessionModifyRequest((VideoProfile) msg.obj);
                    break;
                case MSG_SEND_SESSION_MODIFY_RESPONSE:
                    onSendSessionModifyResponse((VideoProfile) msg.obj);
                    break;
                case MSG_REQUEST_CAMERA_CAPABILITIES:
                    onRequestCameraCapabilities();
                    break;
                case MSG_REQUEST_CALL_DATA_USAGE:
                    onRequestCallDataUsage();
                    break;
                case MSG_SET_PAUSE_IMAGE:
                    onSetPauseImage((String) msg.obj);
                    break;
                /// M: For 3G VT only @{
                case MSG_SET_UI_MODE:
                    onSetUIMode((int) msg.obj);
                    break;
                case MSG_SET_VT_OPEN: {
                    onSetVTOpen();
                    break;
                }
                case MSG_SET_VT_READY:
                    onSetVTReady();
                    break;
                case MSG_SET_VT_CONNECTED:
                    onSetVTConnected();
                    break;
                case MSG_SET_VT_CLOSE:
                    onSetVTClose();
                    break;
                case MSG_ON_DISCONNECTED:
                    onOnDisconnected();
                    break;
                case MSG_SET_DISPLAY: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    onSetDisplay((Surface) args.arg1, (Surface) args.arg2);
                    break;
                }
                case MSG_SWITCH_DISPLAY_SURFACE:
                    onSwitchDisplaySurface();
                    break;
                case MSG_SET_LOCAL_VIEW: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    onSetLocalView((int) args.arg1, (String) args.arg2);
                    break;
                }
                case MSG_SET_PEER_VIEW: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    onSetPeerView((int) args.arg1, (String) args.arg2);
                    break;
                }
                case MSG_SWITCH_CAMERA:
                    onSwitchCamera();
                    break;
                case MSG_SET_VT_VISIBLE:
                    onSetVTVisible((boolean) msg.obj);
                    break;
                case MSG_ON_USER_INPUT:
                    onOnUserInput((String) msg.obj);
                    break;
                /// @}
                default:
                    break;
            }
        }
    };

    /**
     * IGsmVideoCallProvider stub implementation.
     */
    private final class GsmVideoCallProviderBinder extends IGsmVideoCallProvider.Stub {
        public void setCallback(IGsmVideoCallCallback callback) {
            mProviderHandler.obtainMessage(MSG_SET_CALLBACK, callback).sendToTarget();
        }

        public void setCamera(String cameraId) {
            mProviderHandler.obtainMessage(MSG_SET_CAMERA, cameraId).sendToTarget();
        }

        public void setPreviewSurface(Surface surface) {
            mProviderHandler.obtainMessage(MSG_SET_PREVIEW_SURFACE, surface).sendToTarget();
        }

        public void setDisplaySurface(Surface surface) {
            mProviderHandler.obtainMessage(MSG_SET_DISPLAY_SURFACE, surface).sendToTarget();
        }

        public void setDeviceOrientation(int rotation) {
            mProviderHandler.obtainMessage(MSG_SET_DEVICE_ORIENTATION, rotation).sendToTarget();
        }

        public void setZoom(float value) {
            mProviderHandler.obtainMessage(MSG_SET_ZOOM, value).sendToTarget();
        }

        public void sendSessionModifyRequest(VideoProfile requestProfile) {
            mProviderHandler.obtainMessage(
                    MSG_SEND_SESSION_MODIFY_REQUEST, requestProfile).sendToTarget();
        }

        public void sendSessionModifyResponse(VideoProfile responseProfile) {
            mProviderHandler.obtainMessage(
                    MSG_SEND_SESSION_MODIFY_RESPONSE, responseProfile).sendToTarget();
        }

        public void requestCameraCapabilities() {
            mProviderHandler.obtainMessage(MSG_REQUEST_CAMERA_CAPABILITIES).sendToTarget();
        }

        public void requestCallDataUsage() {
            mProviderHandler.obtainMessage(MSG_REQUEST_CALL_DATA_USAGE).sendToTarget();
        }

        public void setPauseImage(String uri) {
            mProviderHandler.obtainMessage(MSG_SET_PAUSE_IMAGE, uri).sendToTarget();
        }

        /// M: For 3G VT only @{
        public void setUIMode(int mode) {
            mProviderHandler.obtainMessage(MSG_SET_UI_MODE, mode).sendToTarget();
        }

        public void setVTOpen() {
            mProviderHandler.obtainMessage(MSG_SET_VT_OPEN).sendToTarget();
        }

        public void setVTReady() {
            mProviderHandler.obtainMessage(MSG_SET_VT_READY).sendToTarget();
        }

        public void setVTConnected() {
            mProviderHandler.obtainMessage(MSG_SET_VT_CONNECTED).sendToTarget();
        }

        public void setVTClose() {
            mProviderHandler.obtainMessage(MSG_SET_VT_CLOSE).sendToTarget();
        }

        public void onDisconnected() {
            mProviderHandler.obtainMessage(MSG_ON_DISCONNECTED).sendToTarget();
        }

        public void setDisplay(Surface local, Surface peer) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = local;
            args.arg2 = peer;
            mProviderHandler.obtainMessage(MSG_SET_DISPLAY, args).sendToTarget();
        }

        public void switchDisplaySurface() {
            mProviderHandler.obtainMessage(MSG_SWITCH_DISPLAY_SURFACE).sendToTarget();
        }

        public void setLocalView(int videoType, String path) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = videoType;
            args.arg2 = path;
            mProviderHandler.obtainMessage(MSG_SET_LOCAL_VIEW, args).sendToTarget();
        }

        public void setPeerView(int bEnableReplacePeerVideo, String sReplacePeerVideoPicturePath) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = bEnableReplacePeerVideo;
            args.arg2 = sReplacePeerVideoPicturePath;
            mProviderHandler.obtainMessage(MSG_SET_PEER_VIEW, args).sendToTarget();
        }

        public void switchCamera() {
            mProviderHandler.obtainMessage(MSG_SWITCH_CAMERA).sendToTarget();
        }

        public void setVTVisible(boolean isVisible) {
            mProviderHandler.obtainMessage(MSG_SET_VT_VISIBLE, isVisible).sendToTarget();
        }

        public void onUserInput(String input) {
            mProviderHandler.obtainMessage(MSG_ON_USER_INPUT, input).sendToTarget();
        }
        /// @}
    }

    public GsmVideoCallProvider() {
        mBinder = new GsmVideoCallProviderBinder();
    }

    /**
     * Returns binder object which can be used across IPC methods.
     */
    public final IGsmVideoCallProvider getInterface() {
        return mBinder;
    }

    /** @see Connection.VideoProvider#onSetCamera */
    public abstract void onSetCamera(String cameraId);

    /** @see Connection.VideoProvider#onSetPreviewSurface */
    public abstract void onSetPreviewSurface(Surface surface);

    /** @see Connection.VideoProvider#onSetDisplaySurface */
    public abstract void onSetDisplaySurface(Surface surface);

    /** @see Connection.VideoProvider#onSetDeviceOrientation */
    public abstract void onSetDeviceOrientation(int rotation);

    /** @see Connection.VideoProvider#onSetZoom */
    public abstract void onSetZoom(float value);

    /** @see Connection.VideoProvider#onSendSessionModifyRequest */
    public abstract void onSendSessionModifyRequest(VideoProfile requestProfile);

    /** @see Connection.VideoProvider#onSendSessionModifyResponse */
    public abstract void onSendSessionModifyResponse(VideoProfile responseProfile);

    /** @see Connection.VideoProvider#onRequestCameraCapabilities */
    public abstract void onRequestCameraCapabilities();

    /** @see Connection.VideoProvider#onRequestCallDataUsage */
    public abstract void onRequestCallDataUsage();

    /** @see Connection.VideoProvider#onSetPauseImage */
    public abstract void onSetPauseImage(String uri);

    /// M: For 3G VT only @{
    /** @see Connection.VideoProvider#onSetUIMode */
    public abstract void onSetUIMode(int mode);

    /** @see Connection.VideoProvider#onSetVTOpen */
    public abstract void onSetVTOpen();

    /** @see Connection.VideoProvider#onSetVTReady */
    public abstract void onSetVTReady();

    /** @see Connection.VideoProvider#onSetVTConnected */
    public abstract void onSetVTConnected();

    /** @see Connection.VideoProvider#setVTClose */
    public abstract void onSetVTClose();

    /** @see Connection.VideoProvider#onOnDisconnected */
    public abstract void onOnDisconnected();

    /** @see Connection.VideoProvider#onSetDisplay */
    public abstract void onSetDisplay(Surface local, Surface peer);

    /** @see Connection.VideoProvider#onSwitchDisplaySurface */
    public abstract void onSwitchDisplaySurface();

    /** @see Connection.VideoProvider#onSetLocalView */
    public abstract void onSetLocalView(int videoType, String path);

    /** @see Connection.VideoProvider#onSetPeerView */
    public abstract void onSetPeerView(int bEnableReplacePeerVideo,
            String sReplacePeerVideoPicturePath);

    /** @see Connection.VideoProvider#onSwitchCamera */
    public abstract void onSwitchCamera();

    /** @see Connection.VideoProvider#onSetVTVisible */
    public abstract void onSetVTVisible(boolean isVisible);

    /** @see Connection.VideoProvider#onOnUserInput */
    public abstract void onOnUserInput(String input);
    /// @}

    /** @see Connection.VideoProvider#receiveSessionModifyRequest */
    public void receiveSessionModifyRequest(VideoProfile VideoProfile) {
        if (mCallback != null) {
            try {
                mCallback.receiveSessionModifyRequest(VideoProfile);
            } catch (RemoteException ignored) {
            }
        }
    }

    /** @see Connection.VideoProvider#receiveSessionModifyResponse */
    public void receiveSessionModifyResponse(
            int status, VideoProfile requestedProfile, VideoProfile responseProfile) {
        if (mCallback != null) {
            try {
                mCallback.receiveSessionModifyResponse(status, requestedProfile, responseProfile);
            } catch (RemoteException ignored) {
            }
        }
    }

    /** @see Connection.VideoProvider#handleCallSessionEvent */
    public void handleCallSessionEvent(int event) {
        if (mCallback != null) {
            try {
                mCallback.handleCallSessionEvent(event);
            } catch (RemoteException ignored) {
            }
        }
    }

    /** @see Connection.VideoProvider#changePeerDimensions */
    public void changePeerDimensions(int width, int height) {
        if (mCallback != null) {
            try {
                mCallback.changePeerDimensions(width, height);
            } catch (RemoteException ignored) {
            }
        }
    }

    /** @see Connection.VideoProvider#changeCallDataUsage */
    public void changeCallDataUsage(int dataUsage) {
        if (mCallback != null) {
            try {
                mCallback.changeCallDataUsage(dataUsage);
            } catch (RemoteException ignored) {
            }
        }
    }

    /** @see Connection.VideoProvider#changeCameraCapabilities */
    public void changeCameraCapabilities(CameraCapabilities CameraCapabilities) {
        if (mCallback != null) {
            try {
                mCallback.changeCameraCapabilities(CameraCapabilities);
            } catch (RemoteException ignored) {
            }
        }
    }
}
