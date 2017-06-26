/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.policy.impl;

import android.content.Context;
import android.util.Log;
import android.view.FallbackEventHandler;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManagerPolicy;

import com.android.internal.policy.IPolicy;
/* <DTS2014060505966 litao/185177 20140605 begin */
//Emui Location Stub
import com.android.internal.policy.impl.HwPolicyFactory;
/* DTS2014060505966 litao/185177 20140605 end> */
/// M: BMW.
import com.mediatek.multiwindow.MultiWindowProxy;

/**
 * {@hide}
 */

// Simple implementation of the policy interface that spawns the right
// set of objects
public class Policy implements IPolicy {
    private static final String TAG = "PhonePolicy";

    private static final String[] preload_classes = {
        "com.android.internal.policy.impl.PhoneLayoutInflater",
        "com.android.internal.policy.impl.PhoneWindow",
        "com.android.internal.policy.impl.PhoneWindow$1",
        "com.android.internal.policy.impl.PhoneWindow$DialogMenuCallback",
        "com.android.internal.policy.impl.PhoneWindow$DecorView",
        "com.android.internal.policy.impl.PhoneWindow$PanelFeatureState",
        "com.android.internal.policy.impl.PhoneWindow$PanelFeatureState$SavedState",
    };

    static {
        // For performance reasons, preload some policy specific classes when
        // the policy gets loaded.
        for (String s : preload_classes) {
            try {
                Class.forName(s);
            } catch (ClassNotFoundException ex) {
                Log.e(TAG, "Could not preload class for phone policy: " + s);
            }
        }
    }

    public Window makeNewWindow(Context context) {
        /* < DTS2014062001124 zhangxinming/00181848 20140620 begin */
        return HwPolicyFactory.getHwPhoneWindow(context);
        /*   DTS2014062001124 zhangxinming/00181848 20140620 end > */
    }

    public LayoutInflater makeNewLayoutInflater(Context context) {
        /* < DTS2014112606398  guyue/00295151 20141030 begin */
        return HwPolicyFactory.getHwPhoneLayoutInflater(context);
        /* DTS2014112606398  guyue/00295151 20141030 end > */
    }

    public WindowManagerPolicy makeNewWindowManager() {
        /// M: BMW. @{
        /* hhq fix*/
		/* <DTS2014060505966 litao/185177 20140605 begin */
        /* <DTS2014060505966 litao/185177 20140605 begin */
        //Emui Policy Stub
        return HwPolicyFactory.getHwPhoneWindowManager();
        /* DTS2014060505966 litao/185177 20140605 end> */		
		/*
		WindowManagerPolicy wmp =  HwPolicyFactory.getHwPhoneWindowManager();
		Log.e(TAG, "HwPolicyFactory.getHwPhoneWindowManager() is null : " +(wmp==null));
		if(wmp!=null){
			return wmp;
		}*/
		/*
        if (MultiWindowProxy.isFeatureSupport()){
            Log.e(TAG, "return MtkPhoneWindowManager ");
            return new MtkPhoneWindowManager();
        }else {
            Log.e(TAG, "return PhoneWindowManager ");
            return new PhoneWindowManager();
        }*/
        /// @}
    }

    public FallbackEventHandler makeNewFallbackEventHandler(Context context) {
        return new PhoneFallbackEventHandler(context);
    }
}
