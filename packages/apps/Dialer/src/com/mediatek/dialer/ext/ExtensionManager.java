/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
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
package com.mediatek.dialer.ext;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.mediatek.common.MPlugin;

public class ExtensionManager {
    private static final String TAG = "DialerExtensionManager";

    private static ExtensionManager sInstance;
    private Context mContext;

    private ICallDetailExtension mCallDetailExtension;
    private ISelectAccountExtension mSelectAccountExtension;
    private IDialPadExtension mDialPadExtension;
    private ICallLogExtension mCallLogExtension;
    private IDialerSearchExtension mDialerSearchExtension;
    private IRCSeCallLogExtension mRCSeCallLogExtension;

    private ExtensionManager() {
    }

    public static ExtensionManager getInstance() {
        if (sInstance == null) {
            createInstanceSynchronized();
        }
        return sInstance;
    }

    private static synchronized void createInstanceSynchronized() {
        if (sInstance == null) {
            sInstance = new ExtensionManager();
        }
    }

    public void init(Application application) {
        mContext = application.getApplicationContext();

        /// M: Workaround for CR:ALPS01827843, not init MPlugin in Application @{
        /*
        mCallDetailExtension = getCallDetailExtension();
        mSimIndicatorExtension = getSimIndicatorExtension();
        mDialPadExtension = getDialPadExtension();
        mCallLogExtension = getCallLogExtension();
        mDialerSearchExtension = getDialerSearchExtension();
        mRCSeCallLogExtension = getRCSeCallLogExtension();
        */
        /// @}
    }

    public ICallDetailExtension getCallDetailExtension() {
        if (mCallDetailExtension == null) {
            synchronized (ICallDetailExtension.class) {
                if (mCallDetailExtension == null) {
                    mCallDetailExtension = (ICallDetailExtension) MPlugin.createInstance(
                            ICallDetailExtension.class.getName(), mContext);
                    if (mCallDetailExtension == null) {
                        mCallDetailExtension = new DefaultCallDetailExtension();
                    }
                    Log.i(TAG, "[getCallDetailExtension]create ext instance: " + mCallDetailExtension);
                }
            }
        }
        return mCallDetailExtension;
    }

    public ISelectAccountExtension getSelectAccountExtension() {
        if (mSelectAccountExtension == null) {
            synchronized (ISelectAccountExtension.class) {
                if (mSelectAccountExtension == null) {
                    mSelectAccountExtension = (ISelectAccountExtension) MPlugin.createInstance(
                            ISelectAccountExtension.class.getName(), mContext);
                    if (mSelectAccountExtension == null) {
                        mSelectAccountExtension = new DefaultSelectAccountExtension();
                    }
                    Log.i(TAG, "[getSelectAccountExtension]create ext instance: " + mSelectAccountExtension);
                }
            }
        }
        return mSelectAccountExtension;
    }

    public IDialPadExtension getDialPadExtension() {
        if (mDialPadExtension == null) {
            synchronized (IDialPadExtension.class) {
                if (mDialPadExtension == null) {
                    mDialPadExtension = (IDialPadExtension) MPlugin.createInstance(
                            IDialPadExtension.class.getName(), mContext);
                    if (mDialPadExtension == null) {
                        mDialPadExtension = new DefaultDialPadExtension();
                    }
                    Log.i(TAG, "[getDialPadExtension]create ext instance: " + mDialPadExtension);
                }
            }
        }
        return mDialPadExtension;
    }

    public ICallLogExtension getCallLogExtension() {
        if (mCallLogExtension == null) {
            synchronized (ICallLogExtension.class) {
                if (mCallLogExtension == null) {
                    mCallLogExtension = (ICallLogExtension) MPlugin.createInstance(
                            ICallLogExtension.class.getName(), mContext);
                    if (mCallLogExtension == null) {
                        mCallLogExtension = new DefaultCallLogExtension();
                    }
                    Log.i(TAG, "[getCallLogAdapterExtension]create ext instance: " + mCallLogExtension);
                }
            }
        }
        return mCallLogExtension;
    }

    public IDialerSearchExtension getDialerSearchExtension() {
        if (mDialerSearchExtension == null) {
            synchronized (IDialerSearchExtension.class) {
                if (mDialerSearchExtension == null) {
                    mDialerSearchExtension = (IDialerSearchExtension) MPlugin.createInstance(
                            IDialerSearchExtension.class.getName(), mContext);
                    if (mDialerSearchExtension == null) {
                        mDialerSearchExtension = new DefaultDialerSearchExtension();
                    }
                    Log.i(TAG, "[getDialerSearchExtension]create ext instance: " + mDialerSearchExtension);
                }
            }
        }
        return mDialerSearchExtension;
    }

    public IRCSeCallLogExtension getRCSeCallLogExtension() {
        if (mRCSeCallLogExtension == null) {
            synchronized (IRCSeCallLogExtension.class) {
                if (mRCSeCallLogExtension == null) {
                    mRCSeCallLogExtension = (IRCSeCallLogExtension) MPlugin.createInstance(
                            IRCSeCallLogExtension.class.getName(), mContext);
                    if (mRCSeCallLogExtension == null) {
                        mRCSeCallLogExtension = new DefaultRCSeCallLogExtension();
                    }
                    Log.i(TAG, "[getRCSeCallLogExtension]create ext instance: " + mRCSeCallLogExtension);
                }
            }
        }
        return mRCSeCallLogExtension;
    }
}
