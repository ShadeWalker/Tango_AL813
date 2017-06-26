/*
 * Copyright (C) 2006-2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.internal.telephony.cdma.utk;

import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.uicc.IccUtils;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
//import com.google.wireless.gdata2.contacts.data.Language;

import android.location.Location;

abstract class ResponseData {
    /**
     * Format the data appropriate for TERMINAL RESPONSE and write it into
     * the ByteArrayOutputStream object.
     */
    public abstract void format(ByteArrayOutputStream buf);
}

class LocalInformationResponseData extends ResponseData {
    private int mLocalInfoType;
    private LocalInfo mInfo;
    private Date mDate = new Date(System.currentTimeMillis());
    private int year, month, day, hour, minute, second, zone, tempzone;
    private int mMCC, mIMSI, mSID, mNID, mBaseID,mBaseLAT,mBaseLong;
    private String languageCode = Locale.getDefault().getLanguage();

    public LocalInformationResponseData(int type, LocalInfo info) {
        super();
        this.mLocalInfoType = type;
        this.mInfo = info;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
    if (buf == null){
        return;
    }

    switch(mLocalInfoType){
        case 0://local info
        {
            UtkLog.d(this, "LocalInformationResponseData local info ");
            mInfo.localInfoFormat(buf);
        }
        break;
        case 3://data and time
        {
            UtkLog.d(this, "LocalInformationResponseData format DateTime " + "Year:"+mDate.getYear()+ "Month:" + mDate.getMonth() + "Day:" + mDate.getDate());
            UtkLog.d(this, "Hour:"+ mDate.getHours() + "Minutes:" + mDate.getMinutes() + "Seconds:"+ mDate.getSeconds());


            year = UtkConvTimeToTPTStamp((mDate.getYear() + 1900)%100);
            month = UtkConvTimeToTPTStamp(mDate.getMonth() + 1);
            day  = UtkConvTimeToTPTStamp(mDate.getDate());
            hour = UtkConvTimeToTPTStamp(mDate.getHours());
            minute = UtkConvTimeToTPTStamp(mDate.getMinutes());
            second = UtkConvTimeToTPTStamp(mDate.getSeconds());

            TimeZone defaultZone = TimeZone. getDefault();
            tempzone = defaultZone.getRawOffset()/3600/1000;
            zone = (tempzone < 0 ) ?
                UtkConvTimeToTPTStamp(-tempzone* 4) | 0x80 :
                UtkConvTimeToTPTStamp(tempzone* 4);


            UtkLog.d(this, "TimeZone:"+ "rawzone:" + defaultZone.getRawOffset()+"tempzone" +tempzone +"zone" + zone);

            int tag = 0x26;
            buf.write(tag);
            buf.write(0x07);
            buf.write(year);
            buf.write(month);
            buf.write(day);
            buf.write(hour);
            buf.write(minute);
            buf.write(second);
            buf.write(zone);
        }
        break;
        case 4://language
        {
            UtkLog.d(this, "LocalInformationResponseData format Language: "+ languageCode);
            int tag = 0x2d;
            buf.write(tag);
            buf.write(0x02);
            byte[] data = languageCode.getBytes();
            for(byte b : data)
            {
                buf.write(b);
            }
        }
        break;
        case 6://access technology
        {
            UtkLog.d(this, "LocalInformationResponseData technology = " + mInfo.Technology);

            mInfo.technologyFormat(buf);
        }
        break;
      }

    }

    public int UtkConvTimeToTPTStamp(int TimeDate){
        return ((TimeDate%10)<<4) + TimeDate/10;
    }

}

class SelectItemResponseData extends ResponseData {
    // members
    private int id;

    public SelectItemResponseData(int id) {
        super();
        this.id = id;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        // Item identifier object
        int tag = ComprehensionTlvTag.ITEM_ID.value();//0x80 | ComprehensionTlvTag.ITEM_ID.value();
        buf.write(tag); // tag
        buf.write(1); // length
        buf.write(id); // identifier of item chosen
    }
}

class GetInkeyInputResponseData extends ResponseData {
    // members
    private boolean mIsUcs2;
    private boolean mIsPacked;
    private boolean mIsYesNo;
    private boolean mYesNoResponse;
    public String mInData;

    // GetInKey Yes/No response characters constants.
    protected static final byte GET_INKEY_YES = 0x01;
    protected static final byte GET_INKEY_NO = 0x00;

    public GetInkeyInputResponseData(String inData, boolean ucs2, boolean packed) {
        super();
        this.mIsUcs2 = ucs2;
        this.mIsPacked = packed;
        this.mInData = inData;
        this.mIsYesNo = false;
    }

    public GetInkeyInputResponseData(boolean yesNoResponse) {
        super();
        this.mIsUcs2 = false;
        this.mIsPacked = false;
        this.mInData = "";
        this.mIsYesNo = true;
        this.mYesNoResponse = yesNoResponse;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null) {
            return;
        }

        // Text string object
        int tag = 0x80 | ComprehensionTlvTag.TEXT_STRING.value();
        buf.write(tag); // tag

        byte[] data;

        if (mIsYesNo) {
            data = new byte[1];
            data[0] = mYesNoResponse ? GET_INKEY_YES : GET_INKEY_NO;
        } else if (mInData != null && mInData.length() > 0) {
            try {
                if (mIsUcs2) {
                    data = mInData.getBytes("UTF-16BE");
                } else if (mIsPacked) {
                    //int size = mInData.length();modified by maolikui at 2015-09-28
	            
                    byte[] tempData = GsmAlphabet
                            .stringToGsm7BitPacked(mInData, 0, 0);
                   //modified by maolikui at 2015-09-28 start
                   //int size = tempData[0]; modified by maolikui at 2015-10-01
                    int size = tempData.length-1;
                    UtkLog.d(this, "[ALPS02332111] fixed size="+size); 
                    UtkLog.d(this, "[ALPS02332111] tempData.length="+tempData.length+" mInData.length()= "+mInData.length()); 

                    UtkLog.d(this, "[ALPS02332111] fixed size="+size); 
                   //modified by maolikui at 2015-09-28 end
                    data = new byte[size];
                    // Since stringToGsm7BitPacked() set byte 0 in the
                    // returned byte array to the count of septets used...
                    // copy to a new array without byte 0.
                    System.arraycopy(tempData, 1, data, 0, size);
                } else {
                    data = GsmAlphabet.stringToGsm8BitPacked(mInData);
                }
            } catch (UnsupportedEncodingException e) {
                data = new byte[0];
            } catch (EncodeException e) {
                data = new byte[0];
            }
        } else {
            data = new byte[0];
        }

        UtkLog.d(this, "input data length="+data.length);
        if(data.length >= 127){
          UtkLog.d(this, "add 0x81");
          buf.write(0x81);
        }

        // length - one more for data coding scheme.
        buf.write(data.length + 1);

        // data coding scheme
        if (mIsUcs2) {
            buf.write(0x08); // UCS2
        } else if (mIsPacked) {
            buf.write(0x00); // 7 bit packed
        } else {
            buf.write(0x04); // 8 bit unpacked
        }

        for (byte b : data) {
            buf.write(b);
        }
    }
}

//bip start
class PollIntervalResponseData extends ResponseData {
    int timeUinit = 0;
    int timeInterval = 0;

    PollIntervalResponseData(int u, int ti) {
        UtkLog.d(this, " PollIntervalResponseData timeUinit=" + u + " timeInterval=" + ti);

        timeUinit = u;
        timeInterval = ti;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null) {
            return;
        }

        //tag
        buf.write(ComprehensionTlvTag.DURATION.value());
        buf.write(0x02); //length
        buf.write(timeUinit);
        buf.write(timeInterval);
    }
}

class TimerManagementResponseData extends ResponseData {
    int timerId;
    byte[] timerValue = null;

    TimerManagementResponseData(int id, long remain) {
        UtkLog.d(this, " TimerManagementResponseData timer id=" + id + " remain=" + remain);

        timerId = id;

        byte[] digit = new byte[3];
        digit[0] = (byte) (remain / 3600); //h
        remain = remain % 3600;
        digit[1] = (byte) (remain / 60); //m
        digit[2] = (byte) (remain % 60); //s

        timerValue = UtkService.digitTobcd(digit);
        UtkLog.d(this, " digit=" + digit[0] + digit[1] + digit[2]);
        UtkLog.d(this, " convert to bcd=" + IccUtils.bytesToHexString(timerValue));
    }

    TimerManagementResponseData(int id, byte[] bcdData) {
        UtkLog.d(this, " TimerManagementResponseData timer id=" + id +
                       " bcd=" + IccUtils.bytesToHexString(bcdData));

        timerId = id;
        if (bcdData != null && bcdData.length == 3) {
            timerValue = new byte[3];
            System.arraycopy(bcdData, 0, timerValue, 0, 3);
        }
    }

    TimerManagementResponseData(int id) {
        UtkLog.d(this, " TimerManagementResponseData timer id=" + id);
        timerId = id;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null) {
            return;
        }

        //timer identifier
        buf.write(ComprehensionTlvTag.TIMER_IDENTIFIER.value());
        buf.write(0x01); //length
        buf.write(timerId);

        //timer value
        if (timerValue != null) {
            buf.write(ComprehensionTlvTag.TIMER_VALUE.value());
            buf.write(0x03); //length
            buf.write(timerValue, 0, 3);
        }
    }
}

class OpenChannelResponseData extends ResponseData {
    //Channel status 8.56, contain a Channel status data object for the opened channel
    //Bearer description 8.52
    //Buffer size 8.55
    /*Other address (local address) (only required
    in response to OPEN CHANNEL proactive
    command with dynamic local address request) 8.58*/

    private ChannelStatus channelStatus = null;
    private BearerDescription bearerDesc = null;
    private int bufferSize = 0;
    private OtherAddress localAddress = null;

    OpenChannelResponseData(OpenChannelResult result) {
        super();

        UtkLog.d(this, " result" + result);

        channelStatus = result.channelStatus;
        bearerDesc = result.bearerDesc;
        bufferSize = result.bufferSize;
        localAddress = result.localAddress;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null) {
            return;
        }

        //Channel status 8.56, contain a Channel status data object for the opened channel
        if (channelStatus != null) {
            channelStatus.writeToBuffer(buf);
        } else {
            //
        }

        //Bearer description 8.52
        if (bearerDesc != null) {
            //
            bearerDesc.writeToBuffer(buf);
        } else {
            //
        }

        //Buffer size 8.55
        if (bufferSize > 0) {
            buf.write(ComprehensionTlvTag.BUFFER_SIZE.value());
            buf.write(0x02); //length
            buf.write((bufferSize >> 8) & 0xff);
            buf.write(bufferSize & 0xff);
        } else {
            //
        }

        //Other address (local address)
        if (localAddress != null) {
            localAddress.writeToBuffer(buf);
        } else {
            //
        }
    }
}

class SendDataResponseData extends ResponseData {
    //Channel data length 8.54
    int availableTxSize = 0;

    SendDataResponseData(int len) {
        super();
        availableTxSize = len;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null) {
            return;
        }

        //
        buf.write(ComprehensionTlvTag.CHANNEL_DATA_LENGTH.value());
        buf.write(1);
        if (availableTxSize >= 0xff) {
            buf.write(0xff);
        } else {
            buf.write(availableTxSize);
        }
    }
}

class ReceiveDataResponseData extends ResponseData {
    //Channel data 8.53
    //Channel data length 8.54
    byte[] data = null;
    int remaining = 0;

    ReceiveDataResponseData(byte[] d, int remain) {
        super();

        if (d != null) {
            data = new byte[d.length];
            System.arraycopy(d, 0, data, 0, d.length);
        }

        remaining = remain;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null) {
            return;
        }

        //Channel data 8.53
        buf.write(ComprehensionTlvTag.CHANNEL_DATA.value());
        if (data != null) {
            if (data.length >= 0x80) {
                buf.write(0x81); //length > 0x7f, ETSI TS 102 223 annex C
            }
            buf.write(data.length);
            buf.write(data, 0, data.length);
        } else {
            buf.write(0);
        }

        //Channel data length 8.54
        buf.write(ComprehensionTlvTag.CHANNEL_DATA_LENGTH.value());
        buf.write(0x01);
        if (remaining > 0xff) {
            buf.write(0xff);
        } else {
            buf.write(remaining);
        }
    }
}

class GetChannelStatusResponseData extends ResponseData {
    //Channel status 8.56, as many Channel Status data objects as there are available channels
    ChannelStatus mChannelStatus;

    GetChannelStatusResponseData(ChannelStatus channelStatus) {
        mChannelStatus = new ChannelStatus(channelStatus);
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null || mChannelStatus == null) {
            return;
        }

        mChannelStatus.writeToBuffer(buf);
    }
}
//bip end
