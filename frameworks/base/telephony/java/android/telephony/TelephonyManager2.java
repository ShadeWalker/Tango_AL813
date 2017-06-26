/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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

package android.telephony;

import android.content.Intent;
import android.provider.Settings;
import android.os.Bundle;
import android.util.Log;


import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.telephony.TelephonyManager;

public class TelephonyManager2 {
   
    /**
     * Returns the numeric name (MCC+MNC) of the current registered operator.
     * Please call TelephonyManager.getNetworkOperator(int subId) instead
     * <p>
     * Availability: Only when the user is registered to a network. Result may be
     * unreliable on CDMA networks (use getPhoneType(int simId)) to determine if
     * it is on a CDMA network).
     *
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * @return numeric name (MCC+MNC) of current registered operator, e.g. "46000".
     * 
     * 
     */
	/** @hide */
    public String getSubscriberId(String simId) {
        try {
            return TelephonyManager.getDefault().getSubscriberId(1);
        } catch (NullPointerException ex) {
            ex.printStackTrace();
            return "";
        }
    }
    
}
