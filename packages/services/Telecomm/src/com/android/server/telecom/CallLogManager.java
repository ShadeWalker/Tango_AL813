/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.Manifest.permission;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.CallLog.Calls;
import android.provider.CallLog.ConferenceCalls;
import android.telecom.CallState;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.widget.Toast;
import android.text.TextUtils;
import android.telecom.PhoneAccount;

// TODO: Needed for move to system service: import com.android.internal.R;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.PhoneConstants;
import com.mediatek.telecom.volte.TelecomVolteUtils;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Helper class that provides functionality to write information about calls and their associated
 * caller details to the call log. All logging activity will be performed asynchronously in a
 * background thread to avoid blocking on the main thread.
 */
final class CallLogManager extends CallsManagerListenerBase {
    /**
     * Parameter object to hold the arguments to add a call in the call log DB.
     */
    private static class AddCallArgs {
        /**
         * @param callerInfo Caller details.
         * @param number The phone number to be logged.
         * @param presentation Number presentation of the phone number to be logged.
         * @param callType The type of call (e.g INCOMING_TYPE). @see
         *     {@link android.provider.CallLog} for the list of values.
         * @param features The features of the call (e.g. FEATURES_VIDEO). @see
         *     {@link android.provider.CallLog} for the list of values.
         * @param creationDate Time when the call was created (milliseconds since epoch).
         * @param durationInMillis Duration of the call (milliseconds).
         * @param dataUsage Data usage in bytes, or null if not applicable.
         * @param conferenceCallLogId The conference call callLog id.
         */
        public AddCallArgs(Context context, CallerInfo callerInfo, String number,
                int presentation, int callType, int features, PhoneAccountHandle accountHandle,
                long creationDate, long durationInMillis, Long dataUsage,
                long conferenceCallLogId /* M: For Conference call */) {
            this.context = context;
            this.callerInfo = callerInfo;
            this.number = number;
            this.presentation = presentation;
            this.callType = callType;
            this.features = features;
            this.accountHandle = accountHandle;
            this.timestamp = creationDate;
            this.durationInSec = (int)(durationInMillis / 1000);
            this.dataUsage = dataUsage;
            /// M: For Volte conference call calllog
            this.conferenceCallLogId = conferenceCallLogId;
        }
        // Since the members are accessed directly, we don't use the
        // mXxxx notation.
        public final Context context;
        public final CallerInfo callerInfo;
        public final String number;
        public final int presentation;
        public final int callType;
        public final int features;
        public final PhoneAccountHandle accountHandle;
        public final long timestamp;
        public final int durationInSec;
        public final Long dataUsage;
        /// M: For Volte conference call calllog
        public final long conferenceCallLogId;
    }

    private static final String TAG = CallLogManager.class.getSimpleName();

    private final Context mContext;
    private static final String ACTION_CALLS_TABLE_ADD_ENTRY =
                "com.android.server.telecom.intent.action.CALLS_ADD_ENTRY";
    private static final String PERMISSION_PROCESS_CALLLOG_INFO =
                "android.permission.PROCESS_CALLLOG_INFO";
    private static final String CALL_TYPE = "callType";
    private static final String CALL_DURATION = "duration";

    public CallLogManager(Context context) {
        mContext = context;
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
        int disconnectCause = call.getDisconnectCause().getCode();
        boolean isNewlyDisconnected =
                newState == CallState.DISCONNECTED || newState == CallState.ABORTED;
        boolean isCallCanceled = isNewlyDisconnected && disconnectCause == DisconnectCause.CANCELED;
        /// M: Ignore Conference child call, because its DisconnectCause always is CANCELED @{
        if (call.getConferenceCallLogId() > 0) {
            isCallCanceled = false;
        }
        /*Log.d(TAG, "onCallStateChanged [" + System.identityHashCode(call)
                + ", " + Log.piiHandle(call.getHandle())
                + "] isNewlyDisconnected:" + isNewlyDisconnected
                + ", oldState:" + CallState.toString(oldState)
                + ", newState:" + CallState.toString(newState)
                + ", call.isConference():" + call.isConference()
                + ", isCallCanceled:" + isCallCanceled
                + ", hasParent:" + (call.getParentCall() != null)
                + ", ConferenceCallLogId:" + call.getConferenceCallLogId());
                */
        /// @}

        // Log newly disconnected calls only if:
        // 1) It was not in the "choose account" phase when disconnected
        // 2) It is a conference call
        // 3) Call was not explicitly canceled
        if (isNewlyDisconnected &&
                 (oldState != CallState.PRE_DIAL_WAIT &&
                 !call.isConference() &&
                 !isCallCanceled)) {
            int type;
            if (!call.isIncoming()) {
                type = Calls.OUTGOING_TYPE;
            } else if (disconnectCause == DisconnectCause.MISSED) {
                type = Calls.MISSED_TYPE;
            } else {
                type = Calls.INCOMING_TYPE;
            }
            logCall(call, type);

            /// M: Show call duration @{
            if (oldState != CallState.DIALING
                    && oldState != CallState.RINGING
                    && (call.getConferenceCallLogId() <= 0 || (call.getConferenceCallLogId() > 0 && (call
                            .getConnectionCapabilities() & Connection.CAPABILITY_VOLTE) == 0))) {
                showCallDuration(call);
            }
            /// @}
        }
    }

    /**
     * Logs a call to the call log based on the {@link Call} object passed in.
     *
     * @param call The call object being logged
     * @param callLogType The type of call log entry to log this call as. See:
     *     {@link android.provider.CallLog.Calls#INCOMING_TYPE}
     *     {@link android.provider.CallLog.Calls#OUTGOING_TYPE}
     *     {@link android.provider.CallLog.Calls#MISSED_TYPE}
     */
    void logCall(Call call, int callLogType) {
        //final long creationTime = call.getCreationTimeMillis();
        final long creationTime = System.currentTimeMillis() - call.getAgeMillis();//HQ_wuruijun add for HQ01622569
        final long age = call.getAgeMillis();

        final String logNumber = getLogNumber(call);
        /// M: ALPS01899538, when dial a empty voice mail number fail, should not log this @{
        if (PhoneAccount.SCHEME_VOICEMAIL.equals(getLogScheme(call))
                && TextUtils.isEmpty(logNumber)) {
            Log.d(TAG, "Empty voice mail logNumber");
            return;
        }
        /// @}

        /// M: Update the CS call into IMS call callLog for conference SRVCC case @{
        if (handleConferenceSrvccCallLog(call, logNumber)) {
            return;
        }
        /// @}

        //Log.d(TAG, "logNumber set to: %s", Log.pii(logNumber));

        final PhoneAccountHandle accountHandle = call.getTargetPhoneAccount();

        // TODO(vt): Once data usage is available, wire it up here.
        int callFeatures = getCallFeatures(call.getVideoStateHistory());
        logCall(call.getCallerInfo(), logNumber, call.getHandlePresentation(),
                callLogType, callFeatures, accountHandle, creationTime, age, null,
                call.getConferenceCallLogId() /* M: For Volte Conference call */);
    }

    /**
     * Inserts a call into the call log, based on the parameters passed in.
     *
     * @param callerInfo Caller details.
     * @param number The number the call was made to or from.
     * @param presentation
     * @param callType The type of call.
     * @param features The features of the call.
     * @param start The start time of the call, in milliseconds.
     * @param duration The duration of the call, in milliseconds.
     * @param dataUsage The data usage for the call, null if not applicable.
     * @param conferenceCallLogId The conference call callLog id.
     */
    private void logCall(
            CallerInfo callerInfo,
            String number,
            int presentation,
            int callType,
            int features,
            PhoneAccountHandle accountHandle,
            long start,
            long duration,
            Long dataUsage,
            long conferenceCallLogId /* M: For Volte Conference call */) {
        boolean isEmergencyNumber = PhoneNumberUtils.isLocalEmergencyNumber(mContext, number);

        // On some devices, to avoid accidental redialing of emergency numbers, we *never* log
        // emergency calls to the Call Log.  (This behavior is set on a per-product basis, based
        // on carrier requirements.)
        final boolean okToLogEmergencyNumber =
                mContext.getResources().getBoolean(R.bool.allow_emergency_numbers_in_call_log);

        // Don't log emergency numbers if the device doesn't allow it.
        final boolean isOkToLogThisCall = !isEmergencyNumber || okToLogEmergencyNumber;

        sendAddCallBroadcast(callType, duration);

        if (isOkToLogThisCall) {
           // Log.d(TAG, "Logging Calllog entry: " + callerInfo + ", "
           //         + Log.pii(number) + "," + presentation + ", " + callType
           //         + ", " + start + ", " + duration + ", " + conferenceCallLogId);
            AddCallArgs args = new AddCallArgs(mContext, callerInfo, number, presentation,
                    callType, features, accountHandle, start, duration, dataUsage,
                    conferenceCallLogId /* M: For Conference call */);
            logCallAsync(args);
        } else {
          Log.d(TAG, "Not adding emergency call to call log.");
        }
    }

    /**
     * Based on the video state of the call, determines the call features applicable for the call.
     *
     * @param videoState The video state.
     * @return The call features.
     */
    private static int getCallFeatures(int videoState) {
        if ((videoState & VideoProfile.VideoState.TX_ENABLED)
                == VideoProfile.VideoState.TX_ENABLED) {
            return Calls.FEATURES_VIDEO;
        }
        return 0;
    }

    /**
     * Retrieve the phone number from the call, and then process it before returning the
     * actual number that is to be logged.
     *
     * @param call The phone connection.
     * @return the phone number to be logged.
     */
    private String getLogNumber(Call call) {
        Uri handle = call.getOriginalHandle();

        if (handle == null) {
            return null;
        }

        String handleString = handle.getSchemeSpecificPart();
        if (!PhoneNumberUtils.isUriNumber(handleString)) {
            handleString = PhoneNumberUtils.stripSeparators(handleString);
        }
        return handleString;
    }

    /**
     * Adds the call defined by the parameters in the provided AddCallArgs to the CallLogProvider
     * using an AsyncTask to avoid blocking the main thread.
     *
     * @param args Prepopulated call details.
     * @return A handle to the AsyncTask that will add the call to the call log asynchronously.
     */
    public AsyncTask<AddCallArgs, Void, Uri[]> logCallAsync(AddCallArgs args) {
        return new LogCallAsyncTask().execute(args);
    }

    /**
     * Helper AsyncTask to access the call logs database asynchronously since database operations
     * can take a long time depending on the system's load. Since it extends AsyncTask, it uses
     * its own thread pool.
     */
    private class LogCallAsyncTask extends AsyncTask<AddCallArgs, Void, Uri[]> {
        /// M: For conference SRVCC
        private AddCallArgs[] mAddCallArgs = null;

        @Override
        protected Uri[] doInBackground(AddCallArgs... callList) {
            mAddCallArgs = callList;
            int count = callList.length;
            Uri[] result = new Uri[count];
            for (int i = 0; i < count; i++) {
                AddCallArgs c = callList[i];

                try {
                    // May block.
                    result[i] = Calls.addCall(c.callerInfo, c.context, c.number, c.presentation,
                            c.callType, c.features, c.accountHandle, c.timestamp, c.durationInSec,
                            c.dataUsage, true /* addForAllUsers */,
                            c.conferenceCallLogId/* M: For Volte conference call calllog */);
                } catch (Exception e) {
                    // This is very rare but may happen in legitimate cases.
                    // E.g. If the phone is encrypted and thus write request fails, it may cause
                    // some kind of Exception (right now it is IllegalArgumentException, but this
                    // might change).
                    //
                    // We don't want to crash the whole process just because of that, so just log
                    // it instead.
                    Log.e(TAG, e, "Exception raised during adding CallLog entry.");
                    result[i] = null;
                }
            }
            return result;
        }

        /**
         * Performs a simple sanity check to make sure the call was written in the database.
         * Typically there is only one result per call so it is easy to identify which one failed.
         */
        @Override
        protected void onPostExecute(Uri[] result) {
            for (Uri uri : result) {
                if (uri == null) {
                    Log.w(TAG, "Failed to write call to the log.");
                }
            }
            /// M: If it was conference child, we record the Uri used for
            /// conference SRVCC case. @{
            for (int i = 0; i < mAddCallArgs.length; i++) {
                AddCallArgs c = mAddCallArgs[i];
                if (c.conferenceCallLogId > 0 && result[i] != null) {
                    updateSrvccConferenceCallLogs(c.conferenceCallLogId, c.number, result[i]);
                }
            }
            /// @}
        }
    }

    private void sendAddCallBroadcast(int callType, long duration) {
        Intent callAddIntent = new Intent(ACTION_CALLS_TABLE_ADD_ENTRY);
        callAddIntent.putExtra(CALL_TYPE, callType);
        callAddIntent.putExtra(CALL_DURATION, duration);
        mContext.sendBroadcast(callAddIntent, PERMISSION_PROCESS_CALLLOG_INFO);
    }

    /**
     * M: ALPS01899538, add for getting scheme from call.
     * @param call
     * @return the phone number scheme to be logged.
     */
    private String getLogScheme(Call call) {
        Uri handle = call.getOriginalHandle();

        if (handle == null) {
            return null;
        }
        String scheme = handle.getScheme();
        return scheme;
    }

    /// M: Show call duration @{
    private void showCallDuration(Call call) {
        //long callDuration = System.currentTimeMillis() - call.getConnectTimeMillis();
        long callDuration = call.getAgeMillis();//HQ_wuruijun add for HQ01622569

        Log.d(TAG, "showCallDuration: " + callDuration);

        if (callDuration / 1000 != 0 && call.getConnectTimeMillis() != 0) {
            Toast.makeText(mContext, getFormateDuration((int) (callDuration / 1000)), Toast.LENGTH_SHORT).show();
        }
    }

    private String getFormateDuration(long duration) {
        long hours = 0;
        long minutes = 0;
        long seconds = 0;

        if (duration >= 3600) {
            hours = duration / 3600;
            minutes = (duration - hours * 3600) / 60;
            seconds = duration - hours * 3600 - minutes * 60;
        } else if (duration >= 60) {
            minutes = duration / 60;
            seconds = duration - minutes * 60;
        } else {
            seconds = duration;
        }

        String duration_title = mContext.getResources().getString(R.string.call_duration_title);
        String duration_content = mContext.getResources().getString(R.string.call_duration_format, hours, minutes, seconds);
        return  duration_title + " (" + duration_content + ")";
    }
    /// @}

    /// M: For Volte conference call calllog @{
    private LogConferenceCallAsyncTask mLogConferenceCallAsyncTask = null;

    /**
     * M: In order to save the relationships between the conference and its
     * participants into callLog. We put the conference callLog id into the the
     * participant call while this call be changed to a participant of the
     * conference. And if the conference has no conference callLog id, we save
     * the conference into database first to get its callLog id.
     */
    @Override
    public void onIsConferencedChanged(Call call) {
        Log.d(TAG, "onIsConferencedChanged Call: " + call);
        // If support volte, and the call is volte conference call child
        Call confCall = call.getParentCall();
        if (confCall != null && TelecomVolteUtils.isVolteSupport()) {
            // It is Volte conference call child
            long confCallLogId = confCall.getConferenceCallLogId();
            if (confCallLogId > 0) {
                Log.d(TAG, "Conference Saved. Conference Call Id: " + confCallLogId);
                // The conference call has saved into database
                call.setConferenceCallLogId(confCallLogId);
            } else if (mLogConferenceCallAsyncTask == null) {
                Log.d(TAG, "excute LogConferenceCallAsyncTask.");
                // The conference call has not saved
                // Launch an AsyncTask to save the conference call first
                mLogConferenceCallAsyncTask = new LogConferenceCallAsyncTask(
                        mContext, confCall);
                mLogConferenceCallAsyncTask.addChildCall(call);
                mLogConferenceCallAsyncTask.execute();
            } else {
                Log.d(TAG, "mLogConferenceCallAsyncTask is running.");
                mLogConferenceCallAsyncTask.addChildCall(call);
            }
        }
    }

    /**
     * Helper AsyncTask to access the call logs database asynchronously since database operations
     * can take a long time depending on the system's load. Since it extends AsyncTask, it uses
     * its own thread pool.
     */
    private class LogConferenceCallAsyncTask extends AsyncTask<Void, Void, Uri> {
        private final ArrayList<Call> mChildCallList = new ArrayList<Call>();
        private final Call mConferenceCall;
        private final Context mContext;

        LogConferenceCallAsyncTask(Context context, Call conferenceCall) {
            mContext = context;
            mConferenceCall = conferenceCall;
        }

        void addChildCall(Call childCall) {
            mChildCallList.add(childCall);
        }

        @Override
        protected Uri doInBackground(Void... args) {
            ContentValues values = new ContentValues();
            values.put(ConferenceCalls.CONFERENCE_DATE,
                    mConferenceCall.getCreationTimeMillis());
            Uri result = null;
            try {
                result = mContext.getContentResolver().insert(
                        ConferenceCalls.CONTENT_URI, values);
            } catch (Exception e) {
                Log.e(TAG, e, "Exception raised during adding CallLog entry.");
                result = null;
            }
            return result;
        }

        /**
         * Performs a simple sanity check to make sure the call was written in the database.
         * Typically there is only one result per call so it is easy to identify which one failed.
         * And save the child call into database with conference call id.
         */
        @Override
        protected void onPostExecute(Uri result) {
            if (result == null) {
                Log.w(TAG, "Failed to write call to the log.");
            }
            long confCallId = 0;
            try {
                confCallId = ContentUris.parseId(result);
                mConferenceCall.setConferenceCallLogId(confCallId);
            } catch (Exception ex) {
                Log.e(TAG, ex, "Failed to write call to the log. Without id feedback.");
            }
            Log.d(TAG, "New Conference Call Id: " + confCallId);
            for (Call childCall : mChildCallList) {
                childCall.setConferenceCallLogId(confCallId);
            }
            mLogConferenceCallAsyncTask = null;
            /// M: For Volte conference SRVCC case. Now the phone only support
            /// one conference call existing simultaneously. So, Clear the
            /// previous residual info while new conference setup. @{
            Log.d(TAG, "Clear mSrvccConferenceCallLogs");
            mSrvccConferenceCallLogs.clear();
            /// @}
        }
    }
    /// @}

    /// M: For Volte conference SRVCC case. @{
    /**
     * The Volte conference would not be disconnected while SRVCC occurred. But
     * the children IMS calls would be disconnected and changed to new CS calls.
     * If use the normal logging method a child SRVCC call would be logged as
     * two callLogs. It is bad user experiences. So, the child IMS call and CS
     * call should merge into one callLog. Here is the logic to implement this
     * feature. First we record the IMS call numbers and their callLog Uris into
     * a memory map. Then find the corresponding Uri from the map according the
     * call number while the CS call disconnecting. Finally, use the Uri to
     * update the callLog info, such as duration.
     */
    private HashMap<Long, HashMap<String, Uri>> mSrvccConferenceCallLogs =
            new HashMap<Long, HashMap<String, Uri>>();

    private void addSrvccConferenceCallLogs(long conferenceCallLogId,
            String logNumber, Uri callLogUri) {
        Log.d(TAG, "addSrvccConferenceCallLogs confId: " + conferenceCallLogId
                + ", logNumber:" + logNumber + ", callLogUri:" + callLogUri);
        if (TextUtils.isEmpty(logNumber)) {
            return;
        }
        HashMap<String, Uri> callLogs = mSrvccConferenceCallLogs.get(conferenceCallLogId);
        if (callLogs == null) {
            callLogs = new HashMap<String, Uri>();
            mSrvccConferenceCallLogs.put(conferenceCallLogId, callLogs);
        }
        callLogs.put(logNumber, callLogUri);
    }

    private void updateSrvccConferenceCallLogs(long conferenceCallLogId, String logNumber,
            Uri callLogUri) {
        Log.d(TAG, "updateSrvccConferenceCallLogs confId: " + conferenceCallLogId
                + ", logNumber:" + logNumber + ", callLogUri:" + callLogUri);
        if (TextUtils.isEmpty(logNumber)) {
            return;
        }
        HashMap<String, Uri> callLogs = mSrvccConferenceCallLogs.get(conferenceCallLogId);
        if (callLogs != null) {
            callLogs.put(logNumber, callLogUri);
        }
    }

    private Uri removeSrvccConferenceCallLogs(
            long conferenceCallLogId, String logNumber) {
        Log.d(TAG, "removeSrvccConferenceCallLogs confId: " + conferenceCallLogId
                + ", logNumber:" + logNumber);
        HashMap<String, Uri> callLogs = mSrvccConferenceCallLogs.get(conferenceCallLogId);
        if (callLogs == null || TextUtils.isEmpty(logNumber)) {
            return null;
        }
        String removedCallLogNumber = null;
        for (String number : callLogs.keySet()) {
            if (logNumber.equals(number)
                    || logNumber.equals(PhoneNumberUtils
                            .getUsernameFromUriNumber(number))) {
                removedCallLogNumber = number;
                break;
            }
        }
        if (removedCallLogNumber != null) {
            Log.d(TAG, "removeSrvccConferenceCallLogs"
                    + " removedCallLogNumber:" + removedCallLogNumber);
            return callLogs.remove(removedCallLogNumber);
        }
        return null;
    }

    /**
     * M: Handle the Volte conference SRVCC case.
     * @param childCall the conference child call.
     * @param childCall the call number to be logged.
     * @return true if the its conference SRVCC CS child call and be updated into callLog.
     */
    private boolean handleConferenceSrvccCallLog(Call childCall, String logNumber) {
        if (childCall.getConferenceCallLogId() <= 0) {
            return false;
        }
        if (childCall.can(Connection.CAPABILITY_VOLTE)) {
            // It is IMS call and it may be changed to be CS call at SRVCC case.
            // So, temporarily record it.
            addSrvccConferenceCallLogs(childCall.getConferenceCallLogId(),
                    logNumber, null);
            return false;
        }
        if (!childCall.can(Connection.CAPABILITY_VOLTE)) {
            // It is CS call. Find the previous relative IMS call, if we found
            // update the Call data into IMS call data.
            Uri callLogUri = removeSrvccConferenceCallLogs(
                    childCall.getConferenceCallLogId(), logNumber);
            if (callLogUri != null) {
                new UpdateConferenceCallLogAsyncTask(childCall.getContext(),
                        childCall, callLogUri).execute();
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    /**
     * The AsyncTask to update the conference callLog info. Now we only update the duration.
     */
    private class UpdateConferenceCallLogAsyncTask extends AsyncTask<Void, Void, Void> {
        private final Call mChildCall;
        private final Context mContext;
        private final Uri mCallLogUri;

        UpdateConferenceCallLogAsyncTask(Context context, Call newChildCall, Uri callLogUri) {
            mContext = context;
            mChildCall = newChildCall;
            mCallLogUri = callLogUri;
        }

        @Override
        protected Void doInBackground(Void... args) {
            Cursor c = null;
            try {
                c = mContext.getContentResolver().query(mCallLogUri,
                        new String[] { Calls.DURATION }, null, null, null);
                c.moveToFirst();
                long duration = c.getLong(0) + mChildCall.getAgeMillis();
                ContentValues values = new ContentValues();
                values.put(Calls.DURATION, duration);
                Log.d(TAG, "Update " + mCallLogUri + " with duration=" + duration);
                mContext.getContentResolver().update(mCallLogUri, values, null, null);
            } catch (Exception e) {
                Log.e(TAG, e, "Exception raised during update conference CallLog.");
            } finally {
                if (c != null) {
                    c.close();
                }
            }
            return null;
        }
    }
    /// @}
}
