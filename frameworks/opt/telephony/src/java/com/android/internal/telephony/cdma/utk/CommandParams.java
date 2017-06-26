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

import android.graphics.Bitmap;

/**
 * Container class for proactive command parameters.
 *
 */
class CommandParams {
    CommandDetails cmdDet;

    CommandParams(CommandDetails cmdDet) {
        this.cmdDet = cmdDet;
    }

    AppInterface.CommandType getCommandType() {
        return AppInterface.CommandType.fromInt(cmdDet.typeOfCommand);
    }

    boolean setIcon(Bitmap icon) { return true; }

    @Override
    public String toString() {
        return cmdDet.toString();
    }
}

class DisplayTextParams extends CommandParams {
    TextMessage textMsg;

    DisplayTextParams(CommandDetails cmdDet, TextMessage textMsg) {
        super(cmdDet);
        this.textMsg = textMsg;
    }

    boolean setIcon(Bitmap icon) {
        if (icon != null && textMsg != null) {
            textMsg.icon = icon;
            return true;
        }
        return false;
    }
}

class LaunchBrowserParams extends CommandParams {
    TextMessage confirmMsg;
    LaunchBrowserMode mode;
    String url;

    LaunchBrowserParams(CommandDetails cmdDet, TextMessage confirmMsg,
            String url, LaunchBrowserMode mode) {
        super(cmdDet);
        this.confirmMsg = confirmMsg;
        this.mode = mode;
        this.url = url;
    }

    boolean setIcon(Bitmap icon) {
        if (icon != null && confirmMsg != null) {
            confirmMsg.icon = icon;
            return true;
        }
        return false;
    }
}

class PlayToneParams extends CommandParams {
    TextMessage textMsg;
    ToneSettings settings;

    PlayToneParams(CommandDetails cmdDet, TextMessage textMsg,
            Tone tone, Duration duration, boolean vibrate) {
        super(cmdDet);
        this.textMsg = textMsg;
        this.settings = new ToneSettings(duration, tone, vibrate);
    }

    boolean setIcon(Bitmap icon) {
        if (icon != null && textMsg != null) {
            textMsg.icon = icon;
            return true;
        }
        return false;
    }
}

class CallSetupParams extends CommandParams {
    TextMessage confirmMsg;
    TextMessage callMsg;
    TextMessage setupMsg;

    CallSetupParams(CommandDetails cmdDet, TextMessage confirmMsg,
            TextMessage callMsg, TextMessage setupMsg) {
        super(cmdDet);
        this.confirmMsg = confirmMsg;
        this.callMsg = callMsg;
        this.setupMsg = setupMsg;
    }

    boolean setIcon(Bitmap icon) {
        if (icon == null) {
            return false;
        }
        if (confirmMsg != null && confirmMsg.icon == null) {
            confirmMsg.icon = icon;
            return true;
        } else if (callMsg != null && callMsg.icon == null) {
            callMsg.icon = icon;
            return true;
        }
        return false;
    }
}

class SelectItemParams extends CommandParams {
    Menu menu = null;
    boolean loadTitleIcon = false;

    SelectItemParams(CommandDetails cmdDet, Menu menu, boolean loadTitleIcon) {
        super(cmdDet);
        this.menu = menu;
        this.loadTitleIcon = loadTitleIcon;
    }

    boolean setIcon(Bitmap icon) {
        if (icon != null && menu != null) {
            if (loadTitleIcon && menu.titleIcon == null) {
                menu.titleIcon = icon;
            } else {
                for (Item item : menu.items) {
                    if (item.icon != null) {
                        continue;
                    }
                    item.icon = icon;
                    break;
                }
            }
            return true;
        }
        return false;
    }
}

class GetInputParams extends CommandParams {
    Input input = null;

    GetInputParams(CommandDetails cmdDet, Input input) {
        super(cmdDet);
        this.input = input;
    }

    boolean setIcon(Bitmap icon) {
        if (icon != null && input != null) {
            input.icon = icon;
        }
        return true;
    }
}

class SendSmsParams extends CommandParams {
    TextMessage textMsg;
    byte[] smsPdu;

    SendSmsParams(CommandDetails cmdDet, TextMessage textMsg, byte[] smsPdu) {
        super(cmdDet);
        this.textMsg = textMsg;
        this.smsPdu = smsPdu;
    }

    boolean setIcon(Bitmap icon) {
        if (icon != null && textMsg != null) {
            textMsg.icon = icon;
            return true;
        }
        return false;
    }
}

//bip start
class SetupEventListParams extends CommandParams {
    byte[] eventList = null;

    SetupEventListParams(CommandDetails cmdDet, byte[] eList) {
        super(cmdDet);

        if (eList != null) {
            eventList = new byte[eList.length];
            System.arraycopy(eList, 0, eventList, 0, eList.length);
        }
    }
}

class pollIntervalParams extends CommandParams {
    int timeUnit = 0;
    int timeInterval = 0;

    pollIntervalParams(CommandDetails cmdDet, int u, int ti) {
        super(cmdDet);

        timeUnit = u;
        timeInterval = ti;
    }
}

class TimerManagementParams extends CommandParams {
    int timerId = 0;
    int timerAction = 0;
    byte[] dataRaw = null;

    TimerManagementParams(CommandDetails cmdDet, int id, byte[] data) {
        super(cmdDet);

        timerAction = (cmdDet.commandQualifier & 0x03);
        timerId = id;

        if (data != null && data.length == 3) {
            dataRaw = new byte[3];
            System.arraycopy(data, 0, dataRaw, 0, data.length);
        }
    }
}

class OpenChannelParams extends CommandParams {
    public TextMessage textMsg = null;
    public IconId iconId = null;
    public BearerDescription bearerDesc = null;
    public int bufferSize = 0;
    public OtherAddress localAddress = null;
    public String networkAccessName = null;
    public String userName = null;
    public String userPwd = null;
    public TransportLevel transportLevel = null;
    public OtherAddress destAddress = null;

    //for CS, packet data service, local and Default (network) bearer
    //cmdDet.commandQualifier & 0x01
    ////0 = on demand link establishment;
    ////1 = immediate link establishment.
    boolean immediateLink = true;
    //cmdDet.commandQualifier & 0x02
    ////0 = no automatic reconnection;
    ////1 = automatic reconnection.
    boolean autoReconnect = false;
    //cmdDet.commandQualifier & 0x04,
    ////0 = no background mode;
    ////1 = immediate link establishment in background mode (bit 1 is ignored).
    boolean backgrountMode = false;


    OpenChannelParams(CommandDetails cmdDet) {
        super(cmdDet);

        immediateLink = (cmdDet.commandQualifier & 0x01) == 0x01 ? true : false;
        autoReconnect = (cmdDet.commandQualifier & 0x02) == 0x01 ? true : false;
        backgrountMode = (cmdDet.commandQualifier & 0x04) == 0x01 ? true : false;
    }
}

class CloseChannelParams extends CommandParams {
    TextMessage textMsg = null;
    int chId;

    //for UICC Server Mode:
    //cmdDet.commandQualifier & 0x01
    ////0 = close the TCP connection and go to "TCP in CLOSED state";
    ////1 = close the TCP connection and go to "TCP in LISTEN state".
    boolean isListen;

    CloseChannelParams(CommandDetails cmdDet, TextMessage msg, int destinationId) {
        super(cmdDet);
        textMsg = msg;
        chId = destinationId & 0xf;

        isListen = (cmdDet.commandQualifier & 0x01) == 0x01 ? true : false;
    }
}

class ReceiveDataParams extends CommandParams {
    int reqDataLength = 0;
    TextMessage textMsg = null;
    int chId;

    ReceiveDataParams(CommandDetails cmdDet, int len, TextMessage msg, int destinationId) {
        super(cmdDet);
        reqDataLength = len;
        textMsg = msg;
        chId = destinationId & 0xf;
    }
}

class SendDataParams extends CommandParams {
    byte[] channelData = null;
    TextMessage textMsg = null;
    int chId;

    //cmdDet.commandQualifier & 0x01
    ////0 = store data in Tx buffer;
    ////1 = send data immediately.
    boolean sendImmediately;

    SendDataParams(CommandDetails cmdDet, byte[] data, TextMessage msg, int destinationId) {
        super(cmdDet);

        channelData = data;
        textMsg = msg;
        chId = destinationId & 0xf;

        sendImmediately = (cmdDet.commandQualifier & 0x01) == 0x01 ? true : false;
    }
}

class GetChannelStatusParams extends CommandParams {
    int chId;

    GetChannelStatusParams(CommandDetails cmdDet, int destinationId) {
        super(cmdDet);
        chId = destinationId & 0xf;
    }
}
//bip end
