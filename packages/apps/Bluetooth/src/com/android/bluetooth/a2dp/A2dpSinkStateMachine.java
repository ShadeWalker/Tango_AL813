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

/**
 * Bluetooth A2dp StateMachine
 *                      (Disconnected)
 *                           |    ^
 *                   CONNECT |    | DISCONNECTED
 *                           V    |
 *                         (Pending)
 *                           |    ^
 *                 CONNECTED |    | CONNECT
 *                           V    |
 *                        (Connected)
 */
package com.android.bluetooth.a2dp;

import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAudioConfig;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetooth;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.content.Intent;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ParcelUuid;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Set;

/// M: a2dp sink and source switch. {@
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.SystemProperties;
/// @}

final class A2dpSinkStateMachine extends StateMachine {
    private static final boolean DBG = true;
    
    /// M: a2dp sink and source switch. {@    
    private static final String ROLE_CHANGE_ACTION = "android.bluetooth.a2dp.role_change";
    private static final String ROLE_CHANGE_CANCEL_ACTION = "android.bluetooth.a2dp.role_change_canceled";
    private static final String REQUEST_ROLE = "request_role";
    private static final String DEVICE_ADDRESS = "device_address_to_connect";
    private static final String REQUEST_ROLE_SINK = "sink";
    /// @}

    static final int CONNECT = 1;
    static final int DISCONNECT = 2;
    private static final int STACK_EVENT = 101;
    private static final int CONNECT_TIMEOUT = 201;
    
    /// M: a2dp sink and source switch. {@
	private static final int REQUEST_CONFIRM = 102;
    private static final int FOCUS_REQUEST = 501;
    private static final int FOCUS_REQUEST_TIK = 1000;
    /// accept or reject the remote device connection request
    private static final int CONFIRM_REQUEST_ACCEPT = 1;
    private static final int CONFRIM_REQUEST_REJECT = 0;
    /// @}

    private Disconnected mDisconnected;
    private Pending mPending;
    private Connected mConnected;

    private A2dpSinkService mService;
    private Context mContext;
    private BluetoothAdapter mAdapter;
    private final AudioManager mAudioManager;
    private IntentBroadcastHandler mIntentBroadcastHandler;
    private final WakeLock mWakeLock;

    private static final int MSG_CONNECTION_STATE_CHANGED = 0;

    // mCurrentDevice is the device connected before the state changes
    // mTargetDevice is the device to be connected
    // mIncomingDevice is the device connecting to us, valid only in Pending state
    //                when mIncomingDevice is not null, both mCurrentDevice
    //                  and mTargetDevice are null
    //                when either mCurrentDevice or mTargetDevice is not null,
    //                  mIncomingDevice is null
    // Stable states
    //   No connection, Disconnected state
    //                  both mCurrentDevice and mTargetDevice are null
    //   Connected, Connected state
    //              mCurrentDevice is not null, mTargetDevice is null
    // Interim states
    //   Connecting to a device, Pending
    //                           mCurrentDevice is null, mTargetDevice is not null
    //   Disconnecting device, Connecting to new device
    //     Pending
    //     Both mCurrentDevice and mTargetDevice are not null
    //   Disconnecting device Pending
    //                        mCurrentDevice is not null, mTargetDevice is null
    //   Incoming connections Pending
    //                        Both mCurrentDevice and mTargetDevice are null
    private BluetoothDevice mCurrentDevice = null;
    private BluetoothDevice mTargetDevice = null;
    private BluetoothDevice mIncomingDevice = null;
    
    /// M: a2dp sink and source switch. {@
    private BluetoothDevice mRequestDevice = null;
    private A2dpSinkFocus mFocus = null;
    private boolean mVolAdjustedLower = false;
    /// @}

    private final HashMap<BluetoothDevice,BluetoothAudioConfig> mAudioConfigs
            = new HashMap<BluetoothDevice,BluetoothAudioConfig>();

    static {
        classInitNative();
    }

    private A2dpSinkStateMachine(A2dpSinkService svc, Context context) {
        super("A2dpSinkStateMachine");
        mService = svc;
        mContext = context;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        
        /// M: a2dp sink and source switch. {@
        if (SystemProperties.getBoolean("ro.mtk_a2dp_sink_src_switch", false)) {
        	mFocus = new A2dpSinkFocus(this);
		}
        /// @}

        initNative();

        mDisconnected = new Disconnected();
        mPending = new Pending();
        mConnected = new Connected();

        addState(mDisconnected);
        addState(mPending);
        addState(mConnected);

        setInitialState(mDisconnected);

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BluetoothA2dpSinkService");

        mIntentBroadcastHandler = new IntentBroadcastHandler();

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        
        /// M: a2dp sink and source switch. {@
        IntentFilter filter = new IntentFilter();
        if (SystemProperties.getBoolean("ro.mtk_a2dp_sink_src_switch", false)) {
			filter.addAction("android.bluetooth.a2dp_sink.connect_confirm");
        	mContext.registerReceiver(mReceiver, filter);
		}
        /// @}
    }

    static A2dpSinkStateMachine make(A2dpSinkService svc, Context context) {
        Log.d("A2dpSinkStateMachine", "make");
        A2dpSinkStateMachine a2dpSm = new A2dpSinkStateMachine(svc, context);
        a2dpSm.start();
        return a2dpSm;
    }

    public void doQuit() {
		/// M: a2dp sink and source switch. {@
		if (SystemProperties.getBoolean("ro.mtk_a2dp_sink_src_switch", false)) {
			Log.d("A2dpSinkStateMachine", "[doQuit] enter");
			mContext.unregisterReceiver(mReceiver);
		}
        /// @}
        
        quitNow();
    }
    
    /// M: a2dp sink and source switch. {@
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context arg0, Intent intent) {
            String action = intent.getAction();
            Log.d("A2dpSinkStateMachine", "[onReceive] action : " + action);
            if (SystemProperties.getBoolean("ro.mtk_a2dp_sink_src_switch", false)) {
	            if (action.equals("android.bluetooth.a2dp_sink.connect_confirm")) {
	                int n = intent.getIntExtra("confirmed", -1);
	                String address = intent.getStringExtra("address");
	                Log.d("A2dpSinkStateMachine", "[onReceive] n : " + n + ", address : " + address);
	                if (n != -1) {
	                    //confirmRequestNative(getByteAddress(address), n);
						mRequestDevice = null;
						
						BluetoothDevice device = mAdapter.getRemoteDevice(address);
						RequestConfirmInformation infor = new RequestConfirmInformation();
						infor.device = device;
						infor.acceptOrReject = n;
						A2dpSinkStateMachine.this.sendMessage(REQUEST_CONFIRM, infor);
	                }
	            }
          	}
        }
        
    };
    /// @}
    
    public void cleanup() {
        cleanupNative();
        mAudioConfigs.clear();
    }

    public void dump(StringBuilder sb) {
        ProfileService.println(sb, "mCurrentDevice: " + mCurrentDevice);
        ProfileService.println(sb, "mTargetDevice: " + mTargetDevice);
        ProfileService.println(sb, "mIncomingDevice: " + mIncomingDevice);
        ProfileService.println(sb, "StateMachine: " + this.toString());
    }

    private class Disconnected extends State {
        @Override
        public void enter() {
            log("Enter Disconnected: " + getCurrentMessage().what);
        }

        @Override
        public boolean processMessage(Message message) {
            log("Disconnected process message: " + message.what);
            if (mCurrentDevice != null || mTargetDevice != null  || mIncomingDevice != null) {
                loge("ERROR: current, target, or mIncomingDevice not null in Disconnected");
                return NOT_HANDLED;
            }

            boolean retValue = HANDLED;
            switch(message.what) {
                case CONNECT:
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTING,
                                   BluetoothProfile.STATE_DISCONNECTED);

                    if (!connectA2dpNative(getByteAddress(device)) ) {
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED,
                                       BluetoothProfile.STATE_CONNECTING);
                        break;
                    }

                    synchronized (A2dpSinkStateMachine.this) {
                        mTargetDevice = device;
                        transitionTo(mPending);
                    }
                    // TODO(BT) remove CONNECT_TIMEOUT when the stack
                    //          sends back events consistently
                    sendMessageDelayed(CONNECT_TIMEOUT, 30000);
                    break;
                case DISCONNECT:
                    // ignore
                    break;
                case STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    switch (event.type) {
                        case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            processConnectionEvent(event.valueInt, event.device);
                            break;
                        case EVENT_TYPE_AUDIO_CONFIG_CHANGED:
                            processAudioConfigEvent(event.audioConfig, event.device);
                            break;
                        default:
                            loge("Unexpected stack event: " + event.type);
                            break;
                    }
                    break;
					
    			/// M: a2dp sink and source switch. {@
                case REQUEST_CONFIRM:
                    RequestConfirmInformation info = (RequestConfirmInformation)message.obj;
                    logd("[Disconnected][processMessage] info.device : " + info.device 
                               + ", info.acceptOrReject : " + info.acceptOrReject);
					
					if (SystemProperties.getBoolean("ro.mtk_a2dp_sink_src_switch", false)) {
						if (!confirmRequestNative(getByteAddress(info.device), info.acceptOrReject)) {
							logd("[Disconnected][processMessage] confirmRequestNative failed");
							if (info.acceptOrReject == CONFIRM_REQUEST_ACCEPT) {
							    A2dpSinkStateMachine.this.sendMessage(CONNECT, info.device);
							} else if (info.acceptOrReject == CONFRIM_REQUEST_REJECT) {
							    broadcastConnectionState(info.device, BluetoothProfile.STATE_DISCONNECTED,
							    		BluetoothProfile.STATE_CONNECTING);
							}
							break;
						}
						logd("[Disconnected][processMessage] confirmRequestNative success");
						if (info.acceptOrReject == CONFIRM_REQUEST_ACCEPT) {
							broadcastConnectionState(info.device, BluetoothProfile.STATE_CONNECTING,
						           	BluetoothProfile.STATE_DISCONNECTED);
							synchronized (A2dpSinkStateMachine.this) {
							    mTargetDevice = info.device;
							    transitionTo(mPending);
							}
						} else if (info.acceptOrReject == CONFRIM_REQUEST_REJECT) {
							broadcastConnectionState(info.device, BluetoothProfile.STATE_DISCONNECTED,
									BluetoothProfile.STATE_CONNECTING);
						}
					}
					
                    break;
    			/// @}
    			
                default:
                    return NOT_HANDLED;
            }
            return retValue;
        }

        @Override
        public void exit() {
            log("Exit Disconnected: " + getCurrentMessage().what);
        }

        // in Disconnected state
        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
            case CONNECTION_STATE_DISCONNECTED:
                logw("Ignore HF DISCONNECTED event, device: " + device);
				
    			/// M: a2dp sink and source switch. {@
				if (SystemProperties.getBoolean("ro.mtk_a2dp_sink_src_switch", false)) {
					if (mRequestDevice != null && device.equals(mRequestDevice)) {
	                    logi("[Disconnected][processConnectionEvent] request device equal to disconnected device");
	                    Intent intent = new Intent();
	                    intent.setAction(ROLE_CHANGE_CANCEL_ACTION);
	                    intent.putExtra(DEVICE_ADDRESS, device.getAddress());
	                    intent.putExtra(REQUEST_ROLE, REQUEST_ROLE_SINK);
	                    mContext.sendBroadcast(intent);
	                }
				}
    			/// @}
    			
                break;
            case CONNECTION_STATE_CONNECTING:
                if (okToConnect(device)){
                    logi("Incoming A2DP accepted");
                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTING,
                                             BluetoothProfile.STATE_DISCONNECTED);
                    synchronized (A2dpSinkStateMachine.this) {
                        mIncomingDevice = device;
                        transitionTo(mPending);
                    }
                } else {
                    //reject the connection and stay in Disconnected state itself
                    logi("Incoming A2DP rejected");
                    disconnectA2dpNative(getByteAddress(device));
                    // the other profile connection should be initiated
                    AdapterService adapterService = AdapterService.getAdapterService();
                    if (adapterService != null) {
                        adapterService.connectOtherProfile(device,
                                                           AdapterService.PROFILE_CONN_REJECTED);
                    }
                }
                break;
            case CONNECTION_STATE_CONNECTED:
                logw("A2DP Connected from Disconnected state");
                if (okToConnect(device)){
                    logi("Incoming A2DP accepted");
                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_DISCONNECTED);
                    synchronized (A2dpSinkStateMachine.this) {
                        mCurrentDevice = device;
                        transitionTo(mConnected);
                    }
                } else {
                    //reject the connection and stay in Disconnected state itself
                    logi("Incoming A2DP rejected");
                    disconnectA2dpNative(getByteAddress(device));
                    // the other profile connection should be initiated
                    AdapterService adapterService = AdapterService.getAdapterService();
                    if (adapterService != null) {
                        adapterService.connectOtherProfile(device,
                                                           AdapterService.PROFILE_CONN_REJECTED);
                    }
                }
                break;
            case CONNECTION_STATE_DISCONNECTING:
                logw("Ignore HF DISCONNECTING event, device: " + device);
                break;
            
            /// M: a2dp sink and source switch. {@
            case SINK_CONNECT_REQUEST:
                logi("SINK_CONNECT_REQUEST happen");
				if (SystemProperties.getBoolean("ro.mtk_a2dp_sink_src_switch", false)) {
					mRequestDevice = device;
	                Intent intent = new Intent();
	                intent.setAction(ROLE_CHANGE_ACTION);
	                intent.putExtra(DEVICE_ADDRESS, device.getAddress());
	                intent.putExtra(REQUEST_ROLE, REQUEST_ROLE_SINK);
	                mContext.sendBroadcast(intent);
				}
                break;
            /// @}
                
            default:
                loge("Incorrect state: " + state);
                break;
            }
        }
    }

    private class Pending extends State {
        @Override
        public void enter() {
            log("Enter Pending: " + getCurrentMessage().what);
        }

        @Override
        public boolean processMessage(Message message) {
            log("Pending process message: " + message.what);

            boolean retValue = HANDLED;
            switch(message.what) {
                case CONNECT:
                    deferMessage(message);
                    break;
                case CONNECT_TIMEOUT:
                    onConnectionStateChanged(CONNECTION_STATE_DISCONNECTED,
                                             getByteAddress(mTargetDevice));
                    break;
                case DISCONNECT:
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (mCurrentDevice != null && mTargetDevice != null &&
                        mTargetDevice.equals(device) ) {
                        // cancel connection to the mTargetDevice
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED,
                                       BluetoothProfile.STATE_CONNECTING);
                        synchronized (A2dpSinkStateMachine.this) {
                            mTargetDevice = null;
                        }
                    } else {
                        deferMessage(message);
                    }
                    break;
                case STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    switch (event.type) {
                        case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            removeMessages(CONNECT_TIMEOUT);
                            processConnectionEvent(event.valueInt, event.device);
                            break;
                        case EVENT_TYPE_AUDIO_CONFIG_CHANGED:
                            processAudioConfigEvent(event.audioConfig, event.device);
                            break;
                        default:
                            loge("Unexpected stack event: " + event.type);
                            break;
                    }
                    break;
				
    			/// M: a2dp sink and source switch. {@
                case REQUEST_CONFIRM:
                    log("Ignore the request confirm in pending state");
					break;
    			/// @}
    			
                default:
                    return NOT_HANDLED;
            }
            return retValue;
        }

        // in Pending state
        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
                case CONNECTION_STATE_DISCONNECTED:
                    mAudioConfigs.remove(device);
                    if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                        broadcastConnectionState(mCurrentDevice,
                                                 BluetoothProfile.STATE_DISCONNECTED,
                                                 BluetoothProfile.STATE_DISCONNECTING);
                        synchronized (A2dpSinkStateMachine.this) {
                            mCurrentDevice = null;
                        }

                        if (mTargetDevice != null) {
                            if (!connectA2dpNative(getByteAddress(mTargetDevice))) {
                                broadcastConnectionState(mTargetDevice,
                                                         BluetoothProfile.STATE_DISCONNECTED,
                                                         BluetoothProfile.STATE_CONNECTING);
                                synchronized (A2dpSinkStateMachine.this) {
                                    mTargetDevice = null;
                                    transitionTo(mDisconnected);
                                }
                            }
                        } else {
                            synchronized (A2dpSinkStateMachine.this) {
                                mIncomingDevice = null;
                                transitionTo(mDisconnected);
                            }
                        }
                    } else if (mTargetDevice != null && mTargetDevice.equals(device)) {
                        // outgoing connection failed
                        broadcastConnectionState(mTargetDevice, BluetoothProfile.STATE_DISCONNECTED,
                                                 BluetoothProfile.STATE_CONNECTING);
                        synchronized (A2dpSinkStateMachine.this) {
                            mTargetDevice = null;
                            transitionTo(mDisconnected);
                        }
                    } else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {
                        broadcastConnectionState(mIncomingDevice,
                                                 BluetoothProfile.STATE_DISCONNECTED,
                                                 BluetoothProfile.STATE_CONNECTING);
                        synchronized (A2dpSinkStateMachine.this) {
                            mIncomingDevice = null;
                            transitionTo(mDisconnected);
                        }
                    } else {
                        loge("Unknown device Disconnected: " + device);
                    }
                    break;
            case CONNECTION_STATE_CONNECTED:
                if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                    // disconnection failed
                    broadcastConnectionState(mCurrentDevice, BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_DISCONNECTING);
                    if (mTargetDevice != null) {
                        broadcastConnectionState(mTargetDevice, BluetoothProfile.STATE_DISCONNECTED,
                                                 BluetoothProfile.STATE_CONNECTING);
                    }
                    synchronized (A2dpSinkStateMachine.this) {
                        mTargetDevice = null;
                        transitionTo(mConnected);
                    }
                } else if (mTargetDevice != null && mTargetDevice.equals(device)) {
                    broadcastConnectionState(mTargetDevice, BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_CONNECTING);
                    synchronized (A2dpSinkStateMachine.this) {
                        mCurrentDevice = mTargetDevice;
                        mTargetDevice = null;
                        transitionTo(mConnected);
                    }
                } else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {
                    broadcastConnectionState(mIncomingDevice, BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_CONNECTING);
                    synchronized (A2dpSinkStateMachine.this) {
                        mCurrentDevice = mIncomingDevice;
                        mIncomingDevice = null;
                        transitionTo(mConnected);
                    }
                } else {
                    loge("Unknown device Connected: " + device);
                    // something is wrong here, but sync our state with stack
                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_DISCONNECTED);
                    synchronized (A2dpSinkStateMachine.this) {
                        mCurrentDevice = device;
                        mTargetDevice = null;
                        mIncomingDevice = null;
                        transitionTo(mConnected);
                    }
                }
                break;
            case CONNECTION_STATE_CONNECTING:
                if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                    log("current device tries to connect back");
                    // TODO(BT) ignore or reject
                } else if (mTargetDevice != null && mTargetDevice.equals(device)) {
                    // The stack is connecting to target device or
                    // there is an incoming connection from the target device at the same time
                    // we already broadcasted the intent, doing nothing here
                    log("Stack and target device are connecting");
                }
                else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {
                    loge("Another connecting event on the incoming device");
                } else {
                    // We get an incoming connecting request while Pending
                    // TODO(BT) is stack handing this case? let's ignore it for now
                    log("Incoming connection while pending, ignore");
                }
                break;
            case CONNECTION_STATE_DISCONNECTING:
                if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                    // we already broadcasted the intent, doing nothing here
                    if (DBG) {
                        log("stack is disconnecting mCurrentDevice");
                    }
                } else if (mTargetDevice != null && mTargetDevice.equals(device)) {
                    loge("TargetDevice is getting disconnected");
                } else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {
                    loge("IncomingDevice is getting disconnected");
                } else {
                    loge("Disconnecting unknown device: " + device);
                }
                break;
                
            /// M: a2dp sink and source switch. {@
            case SINK_CONNECT_REQUEST:
                logi("Source Connect Request While Pending, ignore it");
                break;
            /// @}
            
            default:
                loge("Incorrect state: " + state);
                break;
            }
        }

    }

    private class Connected extends State {
        @Override
        public void enter() {
            log("Enter Connected: " + getCurrentMessage().what);
            // Upon connected, the audio starts out as stopped
            broadcastAudioState(mCurrentDevice, BluetoothA2dpSink.STATE_NOT_PLAYING,
                                BluetoothA2dpSink.STATE_PLAYING);
        }

        @Override
        public boolean processMessage(Message message) {
            log("Connected process message: " + message.what);
            if (mCurrentDevice == null) {
                loge("ERROR: mCurrentDevice is null in Connected");
                return NOT_HANDLED;
            }

            boolean retValue = HANDLED;
            switch(message.what) {
                case CONNECT:
                {
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (mCurrentDevice.equals(device)) {
                        break;
                    }

                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTING,
                                   BluetoothProfile.STATE_DISCONNECTED);
                    if (!disconnectA2dpNative(getByteAddress(mCurrentDevice))) {
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED,
                                       BluetoothProfile.STATE_CONNECTING);
                        break;
                    }

                    synchronized (A2dpSinkStateMachine.this) {
                        mTargetDevice = device;
                        transitionTo(mPending);
                    }
                }
                    break;
                case DISCONNECT:
                {
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (!mCurrentDevice.equals(device)) {
                        break;
                    }
                    broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTING,
                                   BluetoothProfile.STATE_CONNECTED);
                    if (!disconnectA2dpNative(getByteAddress(device))) {
                        broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTED,
                                       BluetoothProfile.STATE_DISCONNECTED);
                        break;
                    }
                    
					/// M: a2dp sink and source switch. {@
					if (SystemProperties.getBoolean("ro.mtk_a2dp_sink_src_switch", false)) {
						mFocus.abandonAudioFocus();
                  	}
                  	/// @}
                  	
                    transitionTo(mPending);
                }
                    break;
                case STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    switch (event.type) {
                        case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            processConnectionEvent(event.valueInt, event.device);
                            break;
                        case EVENT_TYPE_AUDIO_STATE_CHANGED:
                            processAudioStateEvent(event.valueInt, event.device);
                            break;
                        case EVENT_TYPE_AUDIO_CONFIG_CHANGED:
                            processAudioConfigEvent(event.audioConfig, event.device);
                            break;
                        default:
                            loge("Unexpected stack event: " + event.type);
                            break;
                    }
                    break;
                    
                /// M: a2dp sink and source switch. {@
                case FOCUS_REQUEST:
					if (SystemProperties.getBoolean("ro.mtk_a2dp_sink_src_switch", false)) {
						mFocus.getAudioFocus();
					}
					break;

                case REQUEST_CONFIRM:
                    log("Ignore the request confirm in connected state");
					break;
				/// @}		

                default:
                    return NOT_HANDLED;
            }
            return retValue;
        }

        // in Connected state
        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
                case CONNECTION_STATE_DISCONNECTED:
                    mAudioConfigs.remove(device);
                    if (mCurrentDevice.equals(device)) {
                        broadcastConnectionState(mCurrentDevice, BluetoothProfile.STATE_DISCONNECTED,
                                                 BluetoothProfile.STATE_CONNECTED);
                        synchronized (A2dpSinkStateMachine.this) {
                            mCurrentDevice = null;
                            transitionTo(mDisconnected);
                        }
                    } else {
                        loge("Disconnected from unknown device: " + device);
                    }
                    
					/// M: a2dp sink and source switch. {@
					if (SystemProperties.getBoolean("ro.mtk_a2dp_sink_src_switch", false)) {
						mFocus.abandonAudioFocus();
					}
					/// @}
                  	
                    break;
                    
				/// M: a2dp sink and source switch. {@
                case SINK_CONNECT_REQUEST:
                    logi("Source Connect Request While Connected, ignore it");
                    break;
                /// @}
                
              	default:
                  loge("Connection State Device: " + device + " bad state: " + state);
                  break;
            }
        }
        private void processAudioStateEvent(int state, BluetoothDevice device) {
            if (!mCurrentDevice.equals(device)) {
                loge("Audio State Device:" + device + "is different from ConnectedDevice:" +
                                                           mCurrentDevice);
                return;
            }
            switch (state) {
                case AUDIO_STATE_STARTED:
                    broadcastAudioState(device, BluetoothA2dpSink.STATE_PLAYING,
                                        BluetoothA2dpSink.STATE_NOT_PLAYING);

					/// M: a2dp sink and source switch. {@
					if (SystemProperties.getBoolean("ro.mtk_a2dp_sink_src_switch", false)) {
						mFocus.getAudioFocus();
					}
					/// @}
                  	
                    break;
                case AUDIO_STATE_REMOTE_SUSPEND:
                case AUDIO_STATE_STOPPED:
                    broadcastAudioState(device, BluetoothA2dpSink.STATE_NOT_PLAYING,
                                        BluetoothA2dpSink.STATE_PLAYING);
                                        
					/// M: a2dp sink and source switch. {@
					if (SystemProperties.getBoolean("ro.mtk_a2dp_sink_src_switch", false)) {
						mFocus.abandonAudioFocus();
					}
					/// @}

                    break;
                default:
                  loge("Audio State Device: " + device + " bad state: " + state);
                  break;
            }
        }
    }

    private void processAudioConfigEvent(BluetoothAudioConfig audioConfig, BluetoothDevice device) {
        mAudioConfigs.put(device, audioConfig);
        broadcastAudioConfig(device, audioConfig);
    }
    
    /// M: a2dp sink and source switch. {@
    private void controlA2dpSinkPlay(int action){
        log("controlA2dpSinkPlay:  action " + action);
        if (action != 1 && action !=2){
            return;
        }

        if (mCurrentDevice != null){
            setAudioStateNative(getByteAddress(mCurrentDevice),action);
        } else {
            loge("[controlA2dpSinkPlay] mCurrentDevice is NULL");
        }
    }

    class A2dpSinkFocus {
        private static final String TAG = "A2dpSinkFocus";
        private static final int BTAV_AUDIO_STATE_STOPPED = 1;
        private static final int BTAV_AUDIO_STATE_STARTED = 2;
        private static final int A2DPSINK_START = BTAV_AUDIO_STATE_STARTED;
        private static final int A2DPSINK_STOP = BTAV_AUDIO_STATE_STOPPED;
        private static final int A2DPSINK_PAUSE = BTAV_AUDIO_STATE_STOPPED;
        private static final int A2DPSINK_RESUME = BTAV_AUDIO_STATE_STARTED;

        boolean isCaught; //mark whether to request focus

        A2dpSinkStateMachine mSinkStateMachine;

        AudioManager.OnAudioFocusChangeListener mAudioListener =
                new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                Log.d(TAG, "[onAudioFocusChange] focusChange :" + focusChange);
                switch (focusChange){
                    case AudioManager.AUDIOFOCUS_GAIN:
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                        if (mSinkStateMachine.mVolAdjustedLower == true) {
                            mSinkStateMachine.mAudioManager.adjustStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    AudioManager.ADJUST_RAISE,0);
                            mSinkStateMachine.mAudioManager.adjustStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    AudioManager.ADJUST_RAISE,0);
                            mSinkStateMachine.mVolAdjustedLower = false;                            
                        }
                        //continue to playing audio
                        mSinkStateMachine.controlA2dpSinkPlay(A2DPSINK_RESUME);
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                        mSinkStateMachine.mAudioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_RAISE,0);
                        mSinkStateMachine.mAudioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_RAISE,0);
                        mSinkStateMachine.mVolAdjustedLower = false;                        
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS:
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        //stop playing audio
                        mSinkStateMachine.controlA2dpSinkPlay(A2DPSINK_PAUSE);
                        mSinkStateMachine.mVolAdjustedLower = false;                        
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        //adjust down the volume
                        mSinkStateMachine.mAudioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_LOWER,0);
                        mSinkStateMachine.mAudioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_LOWER,0);
                        mSinkStateMachine.mVolAdjustedLower = true;         
                        break;
                    default:
                        Log.d(TAG, "onAudioFocusChange:  unhandle change " + focusChange);
                        break;
                            
                }
            }            
        };

        A2dpSinkFocus(A2dpSinkStateMachine stateMahcine){
            mSinkStateMachine = stateMahcine;
            isCaught = false;
        }

        public void getAudioFocus(){
            Log.d(TAG, "[getAudioFocus] isCaught " + isCaught);
            if (isCaught == true) {
                return;
            }

            if (mSinkStateMachine.getCurrentState() != mConnected) {
                Log.d(TAG, "getAudioFocus not mConnected return , " + 
                    mSinkStateMachine.getCurrentState());
                return;
            }

            int res = mSinkStateMachine.mAudioManager.requestAudioFocus(
                            mAudioListener, 
                            AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN);

            if (AudioManager.AUDIOFOCUS_REQUEST_FAILED == res ){
                mSinkStateMachine.sendMessageDelayed(FOCUS_REQUEST, FOCUS_REQUEST_TIK);
                return;
            }

            //firstly playing audio
            mSinkStateMachine.controlA2dpSinkPlay(A2DPSINK_START);

            Log.d(TAG, "getAudioFocus true");
            isCaught = true;
        }

        public void abandonAudioFocus(){
            Log.d(TAG, "[abandonAudioFocus] isCaught : " + isCaught);
            if (isCaught == false) {
                return;
            }
            mSinkStateMachine.controlA2dpSinkPlay(A2DPSINK_STOP);
            mAudioManager.abandonAudioFocus(mAudioListener);
            isCaught = false;
        }
            
    };
	  /// @}

    int getConnectionState(BluetoothDevice device) {
        if (getCurrentState() == mDisconnected) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }

        synchronized (this) {
            IState currentState = getCurrentState();
            if (currentState == mPending) {
                if ((mTargetDevice != null) && mTargetDevice.equals(device)) {
                    return BluetoothProfile.STATE_CONNECTING;
                }
                if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                    return BluetoothProfile.STATE_DISCONNECTING;
                }
                if ((mIncomingDevice != null) && mIncomingDevice.equals(device)) {
                    return BluetoothProfile.STATE_CONNECTING; // incoming connection
                }
                return BluetoothProfile.STATE_DISCONNECTED;
            }

            if (currentState == mConnected) {
				
				/// M: a2dp sink and source switch. {@
				if (mCurrentDevice != null && mCurrentDevice.equals(device)) { 
				/// @}
				
                //if (mCurrentDevice.equals(device)) {
                    return BluetoothProfile.STATE_CONNECTED;
                }
                return BluetoothProfile.STATE_DISCONNECTED;
            } else {
                loge("Bad currentState: " + currentState);
                return BluetoothProfile.STATE_DISCONNECTED;
            }
        }
    }

    BluetoothAudioConfig getAudioConfig(BluetoothDevice device) {
        return mAudioConfigs.get(device);
    }

    List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
        synchronized(this) {
            if (getCurrentState() == mConnected) {
                devices.add(mCurrentDevice);
            }
        }
        return devices;
    }

    boolean okToConnect(BluetoothDevice device) {
        AdapterService adapterService = AdapterService.getAdapterService();
        boolean ret = true;
        //check if this is an incoming connection in Quiet mode.
        if((adapterService == null) ||
           ((adapterService.isQuietModeEnabled() == true) &&
           (mTargetDevice == null))){
            ret = false;
        }
        return ret;
    }

    synchronized List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        List<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();
        Set<BluetoothDevice> bondedDevices = mAdapter.getBondedDevices();
        int connectionState;

        for (BluetoothDevice device : bondedDevices) {
            ParcelUuid[] featureUuids = device.getUuids();
            if (!BluetoothUuid.isUuidPresent(featureUuids, BluetoothUuid.AudioSource)) {
                continue;
            }
            connectionState = getConnectionState(device);
            for(int i = 0; i < states.length; i++) {
                if (connectionState == states[i]) {
                    deviceList.add(device);
                }
            }
        }
        return deviceList;
    }


    // This method does not check for error conditon (newState == prevState)
    private void broadcastConnectionState(BluetoothDevice device, int newState, int prevState) {

        int delay = mAudioManager.setBluetoothA2dpDeviceConnectionState(device, newState,
                BluetoothProfile.A2DP_SINK);

        mWakeLock.acquire();
        mIntentBroadcastHandler.sendMessageDelayed(mIntentBroadcastHandler.obtainMessage(
                                                        MSG_CONNECTION_STATE_CHANGED,
                                                        prevState,
                                                        newState,
                                                        device),
                                                        delay);
    }

    private void broadcastAudioState(BluetoothDevice device, int state, int prevState) {
        Intent intent = new Intent(BluetoothA2dpSink.ACTION_PLAYING_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, state);
//FIXME        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);

        log("A2DP Playing state : device: " + device + " State:" + prevState + "->" + state);
    }

    private void broadcastAudioConfig(BluetoothDevice device, BluetoothAudioConfig audioConfig) {
        Intent intent = new Intent(BluetoothA2dpSink.ACTION_AUDIO_CONFIG_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothA2dpSink.EXTRA_AUDIO_CONFIG, audioConfig);
//FIXME        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);

        log("A2DP Audio Config : device: " + device + " config: " + audioConfig);
    }
    
    /// M: a2dp sink and source switch. {@
    private byte[] getByteAddress(String address) {
        return Utils.getBytesFromAddress(address);
    }
    /// @}

    private byte[] getByteAddress(BluetoothDevice device) {
        return Utils.getBytesFromAddress(device.getAddress());
    }

    private void onConnectionStateChanged(int state, byte[] address) {
        StackEvent event = new StackEvent(EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt = state;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onAudioStateChanged(int state, byte[] address) {
        StackEvent event = new StackEvent(EVENT_TYPE_AUDIO_STATE_CHANGED);
        event.valueInt = state;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onAudioConfigChanged(byte[] address, int sampleRate, int channelCount) {
        StackEvent event = new StackEvent(EVENT_TYPE_AUDIO_CONFIG_CHANGED);
        int channelConfig = (channelCount == 1 ? AudioFormat.CHANNEL_IN_MONO
                                               : AudioFormat.CHANNEL_IN_STEREO);
        event.audioConfig = new BluetoothAudioConfig(sampleRate, channelConfig,
                AudioFormat.ENCODING_PCM_16BIT);
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private BluetoothDevice getDevice(byte[] address) {
        return mAdapter.getRemoteDevice(Utils.getAddressStringFromByte(address));
    }
	
    /// M: a2dp sink and source switch. {@
    private class RequestConfirmInformation {
        int acceptOrReject;
        BluetoothDevice device;
    }
	/// @}
	
    private class StackEvent {
        int type = EVENT_TYPE_NONE;
        int valueInt = 0;
        BluetoothDevice device = null;
        BluetoothAudioConfig audioConfig = null;

        private StackEvent(int type) {
            this.type = type;
        }
    }
    /** Handles A2DP connection state change intent broadcasts. */
    private class IntentBroadcastHandler extends Handler {

        private void onConnectionStateChanged(BluetoothDevice device, int prevState, int state) {
            Intent intent = new Intent(BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED);
            intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
            intent.putExtra(BluetoothProfile.EXTRA_STATE, state);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
//FIXME            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
            log("Connection state " + device + ": " + prevState + "->" + state);
            mService.notifyProfileConnectionStateChanged(device, BluetoothProfile.A2DP_SINK,
                    state, prevState);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CONNECTION_STATE_CHANGED:
                    onConnectionStateChanged((BluetoothDevice) msg.obj, msg.arg1, msg.arg2);
                    mWakeLock.release();
                    break;
            }
        }
    }


    // Event types for STACK_EVENT message
    final private static int EVENT_TYPE_NONE = 0;
    final private static int EVENT_TYPE_CONNECTION_STATE_CHANGED = 1;
    final private static int EVENT_TYPE_AUDIO_STATE_CHANGED = 2;
    final private static int EVENT_TYPE_AUDIO_CONFIG_CHANGED = 3;
    
    /// M: a2dp sink and source switch. {@
    //event types for request audioFocus
    final private static int EVENT_TYPE_AUDIO_FOCUS_REQUEST = 10;
    /// @}
	
   // Do not modify without updating the HAL bt_av.h files.

    // match up with btav_connection_state_t enum of bt_av.h
    final static int CONNECTION_STATE_DISCONNECTED = 0;
    final static int CONNECTION_STATE_CONNECTING = 1;
    final static int CONNECTION_STATE_CONNECTED = 2;
    final static int CONNECTION_STATE_DISCONNECTING = 3;
    
    /// M: a2dp sink and source switch. {@
    final static int SINK_CONNECT_REQUEST = 4;
    /// @}

    // match up with btav_audio_state_t enum of bt_av.h
    final static int AUDIO_STATE_REMOTE_SUSPEND = 0;
    final static int AUDIO_STATE_STOPPED = 1;
    final static int AUDIO_STATE_STARTED = 2;

    private native static void classInitNative();
    private native void initNative();
    private native void cleanupNative();
    private native boolean connectA2dpNative(byte[] address);
    private native boolean disconnectA2dpNative(byte[] address);
	
    /// M: a2dp sink and source switch. {@
    private native boolean setAudioStateNative(byte[] address,int state);
    private native boolean confirmRequestNative(byte[] address, int acceptOrReject);
    /// @}
}
