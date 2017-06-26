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

import com.android.internal.telephony.uicc.IccUtils;

import java.io.ByteArrayOutputStream;
//import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;


abstract class ValueObject {
    abstract ComprehensionTlvTag getTag();
}

/**
 * Class for Command Detailes object of proactive commands from SIM.
 * {@hide}
 */
class CommandDetails extends ValueObject implements Parcelable {
    public boolean compRequired;
    public int commandNumber;
    public int typeOfCommand;
    public int commandQualifier;

    public ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.COMMAND_DETAILS;
    }

    CommandDetails() {
    }

    public boolean compareTo(CommandDetails other) {
        return (this.compRequired == other.compRequired &&
                this.commandNumber == other.commandNumber &&
                this.commandQualifier == other.commandQualifier &&
                this.typeOfCommand == other.typeOfCommand);
    }

    public CommandDetails(Parcel in) {
        compRequired = (in.readInt() == 1) ? true : false;
        commandNumber = in.readInt();
        typeOfCommand = in.readInt();
        commandQualifier = in.readInt();
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(compRequired ? 1 : 0);
        dest.writeInt(commandNumber);
        dest.writeInt(typeOfCommand);
        dest.writeInt(commandQualifier);
    }

    public static final Parcelable.Creator<CommandDetails> CREATOR =
                                new Parcelable.Creator<CommandDetails>() {
        public CommandDetails createFromParcel(Parcel in) {
            return new CommandDetails(in);
        }

        public CommandDetails[] newArray(int size) {
            return new CommandDetails[size];
        }
    };

    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "CmdDetails: compRequired=" + compRequired +
                " commandNumber=" + commandNumber +
                " typeOfCommand=" + typeOfCommand +
                " commandQualifier=" + commandQualifier;
    }
}

class DeviceIdentities extends ValueObject {
    public int sourceId;
    public int destinationId;

    ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.DEVICE_IDENTITIES;
    }

    @Override
    public String toString() {
        return "DeviceIdentities: sourceId=" + sourceId +
                " destinationId=" + destinationId;
    }
}

// Container class to hold icon identifier value.
class IconId extends ValueObject {
    int recordNumber;
    boolean selfExplanatory;

    ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.ICON_ID;
    }
}

// Container class to hold item icon identifier list value.
class ItemsIconId extends ValueObject {
    int [] recordNumbers;
    boolean selfExplanatory;

    ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.ITEM_ICON_ID_LIST;
    }
}

//bip start
/**
 * The VIA BearerDescription.
 * @hide
 */
class BearerDescription extends ValueObject {
    public int dataLen = 0;
    public int bearerType = 0;
    public byte[] bearerParams = null;

    ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.BEARER_DESCRIPTION;
    }

    BearerDescription(int len, int type, byte[] bparams) {
        dataLen = len;
        bearerType = type;
        bearerParams = bparams;
    }

    public void writeToBuffer(ByteArrayOutputStream buf) {
        if (buf == null) {
            return;
        }

        //bearer description tag
        //length 1+x
        //bearer type 1
        //bearer parameters x
        buf.write(ComprehensionTlvTag.BEARER_DESCRIPTION.value());
        buf.write(dataLen); //length
        buf.write(bearerType);
        if (bearerParams != null) {
            buf.write(bearerParams, 0, bearerParams.length);
        }
    }

    @Override
    public String toString() {
        return "BearerDescription: dataLen=" + dataLen +
               " bearerType=" + bearerType +
               " bearerParams=" + IccUtils.bytesToHexString(bearerParams);
    }
}

/**
 * The VIA TransportLevel.
 * @hide
 */
class TransportLevel extends ValueObject {
    public int protocolType = 0;
    public int port = 0;

    ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.TRANSPORT_LEVEL;
    }

    @Override
    public String toString() {
        return "TransportLevel: protocolType=" + protocolType +
               " port=" + port;
    }
}

/**
 * The VIA OtherAddress.
 * @hide
 */
class OtherAddress extends ValueObject {
    public int addressType;
    public InetAddress address = null;

    OtherAddress()throws UnknownHostException{}

    ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.OTHER_ADDRESS;
    }

    public void writeToBuffer(ByteArrayOutputStream buf) {
        if (buf == null || address == null) {
            return;
        }

        //other address tag
        //length 1+x
        //type of address 1
        //address x
        byte[] ipRaw = address.getAddress();
        if (ipRaw != null) {
            //tag
            buf.write(ComprehensionTlvTag.OTHER_ADDRESS.value());
            //length
            buf.write(ipRaw.length + 1);
            buf.write(BipConstants.BIP_OTHER_ADDRESS_TYPE_IPV4); //IPv4
            buf.write(ipRaw, 0, ipRaw.length);
        }
    }

    @Override
    public String toString() {
        return "OtherAddress: addressType=" + addressType +
               " address=" + address.getHostAddress();
    }
}

/**
 * The VIA ChannelStatus.
 * @hide
 */
class ChannelStatus {
    private int mType;
    private int mId;
    private int mStatus;
    private int mStatusInfo;

    public ChannelStatus(int type, int chId, int status, int info) {
        mType = type;
        mId = chId;
        mStatus = status;
        mStatusInfo = info;
    }

    public ChannelStatus(ChannelStatus s) {
        this.mType = s.mType;
        this.mId = s.mId;
        this.mStatus = s.mStatus;
        this.mStatusInfo = s.mStatusInfo;
    }

    public void setStatus(int s) {
        mStatus = s;
    }

    public int getStatus() {
        return mStatus;
    }

    public void setStatusInfo(int si) {
        mStatusInfo = si;
    }

    public int getStatusInfo() {
        return mStatusInfo;
    }

    public int getType() {
        return mType;
    }

    public int getId() {
        return mId;
    }

    /*
    Coding:
      - byte 3:
          bit 1 to 3: Channel identifier: 1 to 7;
          Channel identifier 0 means "No channel available".
        For CS, packet data service, local and Default (network) bearer:
          bit 4 to 7: RFU.
          bit 8: 0 = Link not established or Packet data service not activated;
                 1 = Link established or Packet data service activated.
        For UICC Server Mode:
          bit 4 to 6: RFU.
          bit 7, 8: 00 = TCP in CLOSED state;
                    01 = TCP in LISTEN state;
                    10 = TCP in ESTABLISHED state;
                    11 = reserved.
        For Terminal Server Mode and TCP:
          bit 4 to 6: RFU.
          bit 7, 8: 00 = TCP in CLOSED state;
                    01 = reserved;
                    10 = TCP in ESTABLISHED state;
                    11 = reserved.
        For Terminal Server Mode and UDP:
          bit 4 to 8: RFU.
      - byte 4:
        '00' = No further info can be given;
        '01' = Not used;
        '02' = Not used;
        '03' = Not used;
        '04' = Not used;
        '05' = Link dropped (network failure or user cancellation);
        all other values are reserved.
    */
    public void writeToBuffer(ByteArrayOutputStream buf) {
        byte[] data = new byte[4];
        int s = mId;
        switch (mType) {
            case BipConstants.TRANSPORT_TYPE_UDP_CLIENT_REMOTE:
            case BipConstants.TRANSPORT_TYPE_TCP_CLIENT_REMOTE:
                if (mStatus == BipConstants.CHANNEL_STATUS_NO_LINK) {
//
                } else if (mStatus == BipConstants.CHANNEL_STATUS_LINKED) {
                    s |= 0x80;
                } else {
                    data = null;
                }
                break;
            case BipConstants.TRANSPORT_TYPE_TCP_SERVER:
                if (mStatus == BipConstants.CHANNEL_STATUS_NO_LINK) {
//
                } else if (mStatus == BipConstants.CHANNEL_STATUS_LINKED) {
                    s |= 0x80;
                } else if (mStatus == BipConstants.CHANNEL_STATUS_LISTEN) {
                    s |= 0x40;
                } else {
                    data = null;
                }
                break;
            default:
                UtkLog.d("ChannelStatus", " not support channel type");
                return;
        }

        if (data != null) {
            data[0] = (byte) (ComprehensionTlvTag.CHANNEL_STATUS.value());
            data[1] = 0x02;
            data[2] = (byte) s;
            data[3] = (byte) mStatusInfo;

            buf.write(data, 0, data.length);
        }
    }

    @Override
    public String toString() {
        return "ChannelStatus: mType=" + mType +
               " mId=" + mId +
               " mStatus=" + mStatus +
               " mStatusInfo=" + mStatusInfo;
    }
}
//bip end
