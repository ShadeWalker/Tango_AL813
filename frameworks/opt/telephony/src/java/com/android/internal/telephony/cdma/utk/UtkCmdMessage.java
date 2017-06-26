/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.internal.telephony.cdma.utk;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Class used to pass UTK messages from telephony to application. Application
 * should call getXXX() to get commands's specific values.
 *
 */
public class UtkCmdMessage implements Parcelable {
    // members
    CommandDetails mCmdDet;
    private TextMessage mTextMsg;
    private Menu mMenu;
    private Input mInput;
    private BrowserSettings mBrowserSettings = null;
    private ToneSettings mToneSettings = null;
    private CallSettings mCallSettings = null;
    private byte[] mSmsPdu = null;

    //bip start
    private OpenChannelSettings mOpenChannelSettings;
    public OpenChannelSettings getOpenChannelSettings() {
        return mOpenChannelSettings;
    }
    //bip end

    /**
     * Class for BrowserSettings.
     * {@hide}
     */
    public class BrowserSettings {
        public String url;
        public LaunchBrowserMode mode;
    }

    /**
     * Class for CallSettings.
     * {@hide}
     */
    public class CallSettings {
        public TextMessage confirmMsg;
        public TextMessage callMsg;
        public TextMessage setupMsg;
    }

    UtkCmdMessage(CommandParams cmdParams) {
        mCmdDet = cmdParams.cmdDet;
        switch(getCmdType()) {
        case SET_UP_MENU:
        case SELECT_ITEM:
            mMenu = ((SelectItemParams) cmdParams).menu;
            break;
        case DISPLAY_TEXT:
        case SET_UP_IDLE_MODE_TEXT:
        case SEND_DTMF:
        case SEND_SS:
        case SEND_USSD:
            mTextMsg = ((DisplayTextParams) cmdParams).textMsg;
            break;
        case SEND_SMS:
            mTextMsg = ((SendSmsParams) cmdParams).textMsg;
            mSmsPdu = ((SendSmsParams) cmdParams).smsPdu;
            break;
        case GET_INPUT:
        case GET_INKEY:
            mInput = ((GetInputParams) cmdParams).input;
            break;
        case LAUNCH_BROWSER:
            mTextMsg = ((LaunchBrowserParams) cmdParams).confirmMsg;
            mBrowserSettings = new BrowserSettings();
            mBrowserSettings.url = ((LaunchBrowserParams) cmdParams).url;
            mBrowserSettings.mode = ((LaunchBrowserParams) cmdParams).mode;
            break;
        case PLAY_TONE:
            PlayToneParams params = (PlayToneParams) cmdParams;
            mToneSettings = params.settings;
            mTextMsg = params.textMsg;
            break;
        case SET_UP_CALL:
            mCallSettings = new CallSettings();
            mCallSettings.confirmMsg = ((CallSetupParams) cmdParams).confirmMsg;
            mCallSettings.callMsg = ((CallSetupParams) cmdParams).callMsg;
            mCallSettings.setupMsg = ((CallSetupParams) cmdParams).setupMsg;
            break;
        //bip start
        case OPEN_CHANNEL:
            mOpenChannelSettings = new OpenChannelSettings();

            mTextMsg = ((OpenChannelParams) cmdParams).textMsg;

            mOpenChannelSettings.bearerDesc = ((OpenChannelParams) cmdParams).bearerDesc;
            mOpenChannelSettings.bufferSize = ((OpenChannelParams) cmdParams).bufferSize;
            mOpenChannelSettings.localAddress = ((OpenChannelParams) cmdParams).localAddress;
            mOpenChannelSettings.networkAccessName =
                ((OpenChannelParams) cmdParams).networkAccessName;
            mOpenChannelSettings.userName = ((OpenChannelParams) cmdParams).userName;
            mOpenChannelSettings.userPwd = ((OpenChannelParams) cmdParams).userPwd;
            mOpenChannelSettings.transportLevel = ((OpenChannelParams) cmdParams).transportLevel;
            mOpenChannelSettings.destAddress = ((OpenChannelParams) cmdParams).destAddress;
            mOpenChannelSettings.immediateLink = ((OpenChannelParams) cmdParams).immediateLink;
            mOpenChannelSettings.autoReconnect = ((OpenChannelParams) cmdParams).autoReconnect;
            mOpenChannelSettings.backgrountMode = ((OpenChannelParams) cmdParams).backgrountMode;
            break;
        case CLOSE_CHANNEL:
            mTextMsg = ((CloseChannelParams) cmdParams).textMsg;
            break;
         case RECEIVE_DATA:
            mTextMsg = ((ReceiveDataParams) cmdParams).textMsg;
            break;
         case SEND_DATA:
            mTextMsg = ((SendDataParams) cmdParams).textMsg;
            break;
//bip end
        }
    }

    /**
     * Used to construct UtkCmdMessage.
     * @param in Input obj.
     * @return null.
     */    
    public UtkCmdMessage(Parcel in) {
        mCmdDet = in.readParcelable(null);
        mTextMsg = in.readParcelable(null);
        mMenu = in.readParcelable(null);
        mInput = in.readParcelable(null);
        switch (getCmdType()) {
        case LAUNCH_BROWSER:
            mBrowserSettings = new BrowserSettings();
            mBrowserSettings.url = in.readString();
            mBrowserSettings.mode = LaunchBrowserMode.values()[in.readInt()];
            break;
        case PLAY_TONE:
            mToneSettings = in.readParcelable(null);
            break;
        case SET_UP_CALL:
            mCallSettings = new CallSettings();
            mCallSettings.confirmMsg = in.readParcelable(null);
            mCallSettings.callMsg = in.readParcelable(null);
            mCallSettings.setupMsg = in.readParcelable(null);
            break;
        }
    }

    /**
     * Used to writeToParcel.
     * @param dest Parcel obj.
     * @param flags int value.
     * @return null.
     */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mCmdDet, 0);
        dest.writeParcelable(mTextMsg, 0);
        dest.writeParcelable(mMenu, 0);
        dest.writeParcelable(mInput, 0);
        switch(getCmdType()) {
        case LAUNCH_BROWSER:
            dest.writeString(mBrowserSettings.url);
            dest.writeInt(mBrowserSettings.mode.ordinal());
            break;
        case PLAY_TONE:
            dest.writeParcelable(mToneSettings, 0);
            break;
        case SET_UP_CALL:
            dest.writeParcelable(mCallSettings.confirmMsg, 0);
            dest.writeParcelable(mCallSettings.callMsg, 0);
            dest.writeParcelable(mCallSettings.setupMsg, 0);
            break;
        }
    }

    public static final Parcelable.Creator<UtkCmdMessage> CREATOR = new Parcelable.Creator<UtkCmdMessage>() {
        public UtkCmdMessage createFromParcel(Parcel in) {
            return new UtkCmdMessage(in);
        }

        public UtkCmdMessage[] newArray(int size) {
            return new UtkCmdMessage[size];
        }
    };

    /**
     * Used to return describeContents.
     * @return 0.
     */
    public int describeContents() {
        return 0;
    }

    /* external API to be used by application */
    public AppInterface.CommandType getCmdType() {
        return AppInterface.CommandType.fromInt(mCmdDet.typeOfCommand);
    }

    public Menu getMenu() {
        return mMenu;
    }

    /**
     * Used to return getInput value.
     * @return Input obj.
     */
    public Input geInput() {
        return mInput;
    }

    /**
     * Used to return text message.
     * @return TextMessage obj.
     */
    public TextMessage geTextMessage() {
        return mTextMsg;
    }

    public void setTextMessage(String text) {

        mTextMsg.text = text;
    }

    public BrowserSettings getBrowserSettings() {
        return mBrowserSettings;
    }

    public ToneSettings getToneSettings() {
        return mToneSettings;
    }

    public CallSettings getCallSettings() {
        return mCallSettings;
    }
}

//bip start
class OpenChannelSettings {
    BearerDescription bearerDesc = null;
    int bufferSize = 0;
    OtherAddress localAddress;
    String networkAccessName = null;
    String userName = null;
    String userPwd = null;
    TransportLevel transportLevel;
    OtherAddress destAddress;

    //for CS, packet data service, local and Default (network) bearer
    ////0 = on demand link establishment;
    ////1 = immediate link establishment.
    boolean immediateLink;

    ////0 = no automatic reconnection;
    ////1 = automatic reconnection.
    boolean autoReconnect;

    ////0 = no background mode;
    ////1 = immediate link establishment in background mode (bit 1 is ignored).
    boolean backgrountMode;

    @Override
    public String toString() {
        return "OpenChannelSettings: bearerDesc=" + bearerDesc +
               " bufferSize=" + bufferSize +
               " localAddress=" + localAddress +
               " destAddress=" + destAddress +
               " networkAccessName=" + networkAccessName +
               " userName=" + userName +
               " userPwd=" + userPwd +
               " transportLevel=" + transportLevel +
               " immediateLink=" + immediateLink +
               " autoReconnect=" + autoReconnect +
               " backgrountMode=" + backgrountMode;
    }
}

class OpenChannelResult {
    public ChannelStatus channelStatus = null;
    public BearerDescription bearerDesc = null;
    public int bufferSize = 0;
    public OtherAddress localAddress = null;

    @Override
    public String toString() {
        return "OpenChannelResult: channelStatus=" + channelStatus +
               " bearerDesc=" + bearerDesc +
               " bufferSize=" + bufferSize +
               " localAddress=" + localAddress;
    }
}
//bip end
