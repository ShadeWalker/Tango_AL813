/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.mediatek.systemui.ext;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.ITelephony;

/**
 * M: [SystemUI] Phone Manager Utils, Support OP "dual SIM" and "Notification toolbar".
 */
public class PhoneHelper {
    private static ITelephony sPhone;

    /**
     * Private Construct to avoid new instance.
     */
    private PhoneHelper() {
    }

    /**
     * Check to see if the radio is on or not.
     *
     * @param simId sim slot
     * @return returns true if the radio is on.
     */
    public static final boolean isRadioOn(int simId) {
        boolean ret = false;
        try {
            // TODO
            final int[] subId = SubscriptionManager.getSubId(simId);
            if (subId != null && subId.length > 0) {
                final ITelephony iTelephony = ITelephony.Stub.asInterface(ServiceManager
                        .getService(Context.TELEPHONY_SERVICE));
                if (iTelephony != null) {
                    ret = iTelephony.isRadioOnForSubscriber(subId[0]);
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return ret;
    }

    /**
     * Returns a reference to a ITelephony instance.
     *
     * @return ITelephony
     */
    public static final ITelephony getITelephony() {
        if (sPhone == null) {
            sPhone = ITelephony.Stub.asInterface(ServiceManager
                    .getService(Context.TELEPHONY_SERVICE));
        }
        return sPhone;
    }

    /**
     * Whether Sim is Emergency Only.
     *
     * @param ss The service state.
     * @return true If Sim is Emergency Only.
     */
    public static final boolean isEmergencyOnly(ServiceState ss) {
        return ss != null && ss.getVoiceRegState() == ServiceState.STATE_EMERGENCY_ONLY;
    }

    /**
     * Whether Sim is Emergency Only in LTE.
     *
     * @param ss The service state.
     * @return true If Sim is Emergency Only.
     */
    public static final boolean isEmergencyOnlyForLte(ServiceState ss) {
        return ss != null
                && ss.getVoiceRegState() == ServiceState.STATE_EMERGENCY_ONLY
                && ss.getDataRegState() == ServiceState.STATE_EMERGENCY_ONLY;
    }
}
