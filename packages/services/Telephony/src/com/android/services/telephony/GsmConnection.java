/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.services.telephony;

/// M: for VoLTE @{
import android.os.SystemProperties;
/// @}

import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
/// M: IMS feature. @{
import com.android.internal.telephony.PhoneConstants;
/// @}

/**
 * Manages a single phone call handled by GSM.
 */
final class GsmConnection extends TelephonyConnection {
    GsmConnection(Connection connection) {
        super(connection);
    }

    /**
     * Clones the current {@link GsmConnection}.
     * <p>
     * Listeners are not copied to the new instance.
     *
     * @return The cloned connection.
     */
    @Override
    public TelephonyConnection cloneConnection() {
        GsmConnection gsmConnection = new GsmConnection(getOriginalConnection());
        return gsmConnection;
    }

    /** {@inheritDoc} */
    @Override
    public void onPlayDtmfTone(char digit) {
        if (getPhone() != null) {
            getPhone().startDtmf(digit);
            /// M: CC034: Stop DTMF when TelephonyConnection is disconnected @{
            mDtmfRequestIsStarted = true;
            /// @}
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onStopDtmfTone() {
        if (getPhone() != null) {
            getPhone().stopDtmf();
            /// M: CC034: Stop DTMF when TelephonyConnection is disconnected @{
            mDtmfRequestIsStarted = false;
            /// @}
        }
    }

    @Override
    protected int buildConnectionCapabilities() {
        int capabilities = super.buildConnectionCapabilities();
        capabilities |= CAPABILITY_MUTE;
        capabilities |= CAPABILITY_SUPPORT_HOLD;
        /// M: CC027: Proprietary scheme to build Connection Capabilities @{
        if (getConnectionService() != null) {
            if (getConnectionService().canHold(this)) {
                capabilities |= CAPABILITY_HOLD;
            }
            if (getConnectionService().canUnHold(this)) {
                capabilities |= CAPABILITY_UNHOLD;
            }
            if (getConnectionService().canAdd(this)) {
                capabilities |= CAPABILITY_ADD_CALL;
            }
            if (getConnectionService().canTransfer(this)) {
                capabilities |= CAPABILITY_ECT;
            }
            /// M: CC029: DSDA conference @{
            /**
             * Use canSeparate() to update call capability CAPABILITY_SEPARATE_FROM_CONFERENCE.
             */
            if (getConnectionService().canSeparate(this)) {
                capabilities |= CAPABILITY_SEPARATE_FROM_CONFERENCE;
            }
            /// @}
        }

        if (SystemProperties.get("ro.mtk_volte_support").equals("1")) {
            if (getPhone() != null) {
                int curPhoneType = getPhone().getPhoneType();
                if (curPhoneType == PhoneConstants.PHONE_TYPE_IMS) {
                    capabilities |= CAPABILITY_VOLTE;
                }
            }
        }

        Log.d(this, "buildConnectionCapabilities: %s", capabilitiesToString(capabilities));
        /// @}
        return capabilities;
    }

    @Override
    void onRemovedFromCallService() {
        super.onRemovedFromCallService();
    }

    public void onStateChanged(int state) {
        super.onStateChanged(state);
        Phone phone = getPhone();
        if (phone == null) {
            return;
        }

        if (phone.getState() == PhoneConstants.State.OFFHOOK) {
            phone.switchAntenna(PhoneConstants.STATE_CONNECTED,
                                PhoneConstants.RAT_MODE_GSM);
            Log.d(this, "switchAntenna: enable to gsm with (1, 0)");
        } else if (phone.getState() == PhoneConstants.State.IDLE) {
            phone.switchAntenna(PhoneConstants.STATE_DISCONNECTED,
                                PhoneConstants.RAT_MODE_GSM);
            Log.d(this, "switchAntenna: disable to gsm with (0, 0)");
        } else {
            Log.d(this, "switchAntenna: do nothing");
        }
    }
}
