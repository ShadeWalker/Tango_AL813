/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2012. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.mms.ext;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.telephony.SmsMessage;
import android.util.Log;
import android.provider.Telephony.Sms.Inbox;

/// M: Code analyze 001, For bug ALPS00352897, Stitching error when continuously receiving long SMS. @{
public class DefaultSmsReceiverExt extends ContextWrapper implements ISmsReceiverExt {
    private static final String TAG = "Mms/SmsReceiverImpl";

    public DefaultSmsReceiverExt(Context context) {
        super(context);
    }

    public void extractSmsBody(SmsMessage[] msgs, SmsMessage sms, ContentValues values) {
        int pduCount = msgs.length;

        Log.d(TAG, "SmsReceiverImpl.extractSmsBody, pduCount=" + pduCount);

        if (pduCount == 1) {
            // There is only one part, so grab the body directly.
            values.put(Inbox.BODY, replaceFormFeeds(sms.getDisplayMessageBody()));
        } else {
            // Build up the body from the parts.
            int i = 0;
            StringBuilder body = new StringBuilder();
            while (i < pduCount) {
                Log.d(TAG, "SmsReceiverImpl.extractSmsBody, i = " + i);
                sms = msgs[i];
                if (sms.mWrappedSmsMessage == null) {
                    i++;
                    continue;
                }
                int encodingType = sms.getEncodingType();
                if (encodingType == SmsMessage.ENCODING_16BIT) {
                    int length = 0;
                    for (int j = i; j < pduCount; j++) {
                        Log.d(TAG, "SmsReceiverImpl.extractSmsBody, j = " + j);
                        SmsMessage tempSms = msgs[j];
                        if (tempSms.mWrappedSmsMessage == null) {
                            break;
                        }
                        int tempEncodingType = tempSms.getEncodingType();
                        if (tempEncodingType == SmsMessage.ENCODING_16BIT) {
                            length += tempSms.getUserData().length;
                        } else {
                            break;
                        }
                    }
                    Log.d(TAG, "SmsReceiverImpl.extractSmsBody, length = " + length);
                    byte newbuf[] = new byte[length];
                    int pos = 0;
                    int k;
                    for (k = i; k < pduCount; k++) {
                        Log.d(TAG, "SmsReceiverImpl.extractSmsBody, k = " + k);
                        SmsMessage tempSms = msgs[k];
                        if (tempSms.mWrappedSmsMessage == null) {
                            k++;
                            break;
                        }
                        int tempEncodingType = tempSms.getEncodingType();
                        if (tempEncodingType == SmsMessage.ENCODING_16BIT) {
                            byte[] userData = tempSms.getUserData();
                            int l = userData.length;
                            System.arraycopy(userData, 0, newbuf, pos, l);
                            pos += l;
                        } else {
                            break;
                        }
                    }
                    String tempBody = null;
                    try {
                        tempBody = new String(newbuf,"utf-16");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    body.append(tempBody);
                    i = k;
                } else {
                    Log.d(TAG, "SmsReceiverImpl.extractSmsBody, utf-8 body = " + sms.getDisplayMessageBody());
                    body.append(sms.getDisplayMessageBody());
                    i++;
                }
            }
            values.put(Inbox.BODY, replaceFormFeeds(body.toString()));
        }
    }

    private static String replaceFormFeeds(String s) {
        // Some providers send formfeeds in their messages. Convert those formfeeds to newlines.
        /** M: process null @{ */
        if (s == null) {
            return "";
        }
        /** @} */
        return s.replace('\f', '\n');
    }
}
/// @}
