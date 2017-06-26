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

import android.content.ContentResolver;
import android.net.Uri;
import java.util.ArrayList;
import android.app.PendingIntent;
import android.content.Context;

public interface IMmsSmsMessageSenderExt {

    /**
     * M: Save the send messages in database, only for mass text message in OP09 project.<br/>
     * OP09Feature: CDMA/GSM-01-025;
     * @param resolver the contentResolver.
     * @param uri the message uri.
     * @param address the message's address.
     * @param body the message body.
     * @param subject the message's subject.
     * @param date the date.
     * @param read true: read; false: not read.
     * @param deliveryReport true: request deliveryReport. false: not request deliveryReport.
     * @param threadId the threadId.
     * @param subId the subId.
     * @param ipmsgId the gourp message id for mass text message.
     * @return The Uri in database.
     */
    Uri addMessageToUri(ContentResolver resolver, Uri uri, String address,
            String body, String subject, Long date, boolean read, boolean deliveryReport,
            long threadId, long subId, long ipmsgId);

     /**
     * Send SMS with priority param in PDU.
     * @param context the Context.
     * @param destAddr the recipients.
     * @param scAddr the service center.
     * @param parts the message's parts.
     * @param subId the sub Id.
     * @param sentIntents the sent intent.
     * @param deliveryIntents the delivery intents.
     */
    void sendSMSWithPriority(Context context, String destAddr, String scAddr,
            ArrayList<String> parts, int subId, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents);

    /**
     * M: check the number of the queued messages for Optimization of sending multi smses.
     */
    void checkQueuedMsgNumber();

}
