/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.calllog;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.util.Constants;
import com.android.dialer.CallDetailActivity;
import com.mediatek.telecom.TelecomManagerEx;

import java.util.ArrayList;

/**
 * Used to create an intent to attach to an action in the call log.
 * <p>
 * The intent is constructed lazily with the given information.
 */
public abstract class IntentProvider {

    private static final String TAG = IntentProvider.class.getSimpleName();

    public abstract Intent getIntent(Context context);

    public static IntentProvider getReturnCallIntentProvider(final String number) {
        return getReturnCallIntentProvider(number, null);
    }

    public static IntentProvider getReturnCallIntentProvider(final String number,
            final PhoneAccountHandle accountHandle) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                return CallUtil.getCallIntent(number, accountHandle);
            }
        };
    }

    public static IntentProvider getReturnVideoCallIntentProvider(final String number) {
        return getReturnVideoCallIntentProvider(number, null);
    }

    public static IntentProvider getReturnVideoCallIntentProvider(final String number,
            final PhoneAccountHandle accountHandle) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                return CallUtil.getVideoCallIntent(number, accountHandle);
            }
        };
    }

    public static IntentProvider getReturnVoicemailCallIntentProvider() {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                return CallUtil.getVoicemailIntent();
            }
        };
    }

    public static IntentProvider getPlayVoicemailIntentProvider(final long rowId,
            final String voicemailUri) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                Intent intent = new Intent(context, CallDetailActivity.class);
                intent.setData(ContentUris.withAppendedId(
                        Calls.CONTENT_URI_WITH_VOICEMAIL, rowId));
                if (voicemailUri != null) {
                    intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI,
                            Uri.parse(voicemailUri));
                }
                intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_START_PLAYBACK, true);
                return intent;
            }
        };
    }

    /**
     * Retrieves the call details intent provider for an entry in the call log.
     *
     * @param id The call ID of the first call in the call group.
     * @param extraIds The call ID of the other calls grouped together with the call.
     * @param voicemailUri If call log entry is for a voicemail, the voicemail URI.
     * @return The call details intent provider.
     */
    public static IntentProvider getCallDetailIntentProvider(
            final long id, final long[] extraIds, final String voicemailUri) {
        return getCallDetailIntentProvider(id, extraIds, voicemailUri, false);
    }

    /**
     * M: [VoLTE ConfCall] For volte Conference call
     * Retrieves the call details intent provider for an entry in the call log.
     *
     * @param id The call ID of the first call in the call group.
     * @param extraIds The call ID of the other calls grouped together with the call.
     * @param voicemailUri If call log entry is for a voicemail, the voicemail URI.
     * @param isConferenceCall if it was conference call
     * @return The call details intent provider.
     */
    public static IntentProvider getCallDetailIntentProvider(
            final long id, final long[] extraIds, final String voicemailUri,
            final boolean isConferenceCall) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                Intent intent = new Intent(context, CallDetailActivity.class);
                // Check if the first item is a voicemail.
                if (voicemailUri != null) {
                    intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI,
                            Uri.parse(voicemailUri));
                }
                intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_START_PLAYBACK, false);

                if (extraIds != null && extraIds.length > 0) {
                    intent.putExtra(CallDetailActivity.EXTRA_CALL_LOG_IDS, extraIds);
                } else {
                    // If there is a single item, use the direct URI for it.
                    intent.setData(ContentUris.withAppendedId(
                            Calls.CONTENT_URI_WITH_VOICEMAIL, id));
                }
                ///M: [VoLTE ConfCall] For volte Conference call
                intent.putExtra(CallDetailActivity.EXTRA_IS_CONFERENCE_CALL, isConferenceCall);
                return intent;
            }
        };
    }

    /** M: [Ip Dial] get Call Intent for ip dial @{ */
    public static IntentProvider getIpDialCallIntentProvider(final String number) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                return CallUtil.getCallIntent(CallUtil.getCallUri(number), null,
                        Constants.DIAL_NUMBER_INTENT_IP);
            }
        };
    }
    /** @} */

    /**
     * M: [VoLTE ConfCall] For Volte Conference Call. @{
     * Get the IntentProvider which would return the volte conference call intent
     * @param numbers the volte conference call numbers
     * @return the IntentProvider
     */
    public static IntentProvider getReturnVolteConfCallIntentProvider(
            final ArrayList<String> numbers) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                Intent confCallIntent = CallUtil.getCallIntent(numbers.get(0), null, null);
                confCallIntent.putExtra(TelecomManagerEx.EXTRA_VOLTE_CONF_CALL_DIAL, true);
                confCallIntent.putStringArrayListExtra(
                        TelecomManagerEx.EXTRA_VOLTE_CONF_CALL_NUMBERS, numbers);
                return confCallIntent;
            }
        };
    }
    /** @} */

    /**
     * M: [VoLTE] For Volte IMS Call. @{
     * Get the IntentProvider which would return the volte IMS call intent
     * @param number the volte IMS call number
     * @return the IntentProvider
     */
    public static IntentProvider getReturnIMSCallIntentProvider(
            final String number) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                Intent confCallIntent = CallUtil.getCallIntent(
                        Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null),
                        null, Constants.DIAL_NUMBER_INTENT_IMS);
                return confCallIntent;
            }
        };
    }
    /** @} */

    /// M: Supporting suggested account @{
    public static IntentProvider getSuggestedReturnCallIntentProvider(final String number,
            final PhoneAccountHandle accountHandle) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                Intent intent = CallUtil.getCallIntent(number);
                if (accountHandle != null) {
                    intent.putExtra(TelecomManagerEx.EXTRA_SUGGESTED_PHONE_ACCOUNT_HANDLE, accountHandle);
                }
                return intent;
            }
        };
    }

    public static IntentProvider getSuggestedReturnVideoCallIntentProvider(final String number,
            final PhoneAccountHandle accountHandle) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                Intent intent = CallUtil.getVideoCallIntent(number, null, null);
                if (accountHandle != null) {
                    intent.putExtra(TelecomManagerEx.EXTRA_SUGGESTED_PHONE_ACCOUNT_HANDLE, accountHandle);
                }
                return intent;
            }
        };
    }

    public static IntentProvider getSuggestedIpDialCallIntentProvider(final String number, final PhoneAccountHandle accountHandle) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                Intent intent = CallUtil.getCallIntent(CallUtil.getCallUri(number), null,
                        Constants.DIAL_NUMBER_INTENT_IP);
                if (accountHandle != null) {
                    intent.putExtra(TelecomManagerEx.EXTRA_SUGGESTED_PHONE_ACCOUNT_HANDLE, accountHandle);
                }
                return intent;
            }
        };
    }
    /// @}
}
