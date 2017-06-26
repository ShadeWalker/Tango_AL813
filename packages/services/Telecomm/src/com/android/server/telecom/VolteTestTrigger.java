package com.android.server.telecom;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.mediatek.telecom.TelecomManagerEx;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.telecom.Connection;
import android.text.TextUtils;
import android.util.Log;

public class VolteTestTrigger {

    private static final String LOG_TAG = "VolteTestTrigger";

    private static final String ACTION_VOLTE_TEST_TRIGGER = "com.mediatek.volte.test.trigger";
    private static final String EXTRA_VOLTE_TEST_TYPE = "com.mediatek.volte.test.type";

    private static final int TYPE_NOT_SET = 0;
    private static final int TYPE_VOLTE_MARKED_AS_EMERGENCY = 1;
    private static final int TYPE_VOLTE_SET_PAU_FIELD = 3;
    private static final int TYPE_VOLTE_CONF_HOST = 4;
    private static final int TYPE_VOLTE_CONF_PARTICIPANT = 5;
    private static final int TYPE_VOLTE_CONF_SRVCC = 6;

    public static final int MSG_UPDATE_EXTRAS = 180;

    private static VolteTestTrigger sInstance = new VolteTestTrigger();
    private ConnectionServiceWrapper mConnectionServiceWrapper;

    public static synchronized VolteTestTrigger getInstance() {
        if (sInstance == null) {
            sInstance = new VolteTestTrigger();
        }
        return sInstance;
    }

    public void setServiceInterface(IBinder binder, ConnectionServiceWrapper wrapper) {
        if (binder != null) {
            init(wrapper);
        } else {
            tearDown();
        }
    }

    private void init(ConnectionServiceWrapper wrapper) {
        mConnectionServiceWrapper = wrapper;
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_VOLTE_TEST_TRIGGER);
        TelecomGlobals.getInstance().getContext().registerReceiver(mVolteTestReceiver, filter);
    }

    private void tearDown() {
        mConnectionServiceWrapper = null;
        try {
            TelecomGlobals.getInstance().getContext().unregisterReceiver(mVolteTestReceiver);
        } catch (IllegalArgumentException e) {
            log("the Receiver has already unregistered, catch this error.");
        }
    }

    private BroadcastReceiver mVolteTestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                log("onReceive()... no intent at all, return directly.");
                return;
            }
            int testType = intent.getIntExtra(EXTRA_VOLTE_TEST_TYPE, TYPE_NOT_SET);
            log("onReceive()... testType: " + testType);
            switch (testType) {
                case TYPE_VOLTE_MARKED_AS_EMERGENCY:
                    handleVolteMarkAsEcc();
                    break;
                case TYPE_VOLTE_SET_PAU_FIELD:
                    handleVolteSetPauField();
                    break;
                case TYPE_VOLTE_CONF_HOST:
                    handleVolteConfHost();
                    break;
                case TYPE_VOLTE_CONF_PARTICIPANT:
                    handleVolteConfParticipant();
                    break;
                case TYPE_VOLTE_CONF_SRVCC:
                    handleVolteConfSRVCC();
                    break;
                default:
                    log("un-recognized type :" + testType);
                    break;
            }
        }
    };

    private void handleVolteMarkAsEcc() {
        // find corresponding call, dialing or active.
        Call call = CallsManager.getInstance().getDialingCall();
        if (call == null) {
            call = CallsManager.getInstance().getActiveCall();
        }
        if (call == null) {
            log("handleVolteMarkAsEcc()... can not find dialing or active call, skip!");
            return;
        }
        // generate bundle.
        Bundle bundle = new Bundle();
        bundle.putBoolean(TelecomManagerEx.EXTRA_VOLTE_MARKED_AS_EMERGENCY, true);
        // trigger it.
        mConnectionServiceWrapper.triggierVolteTest(call, MSG_UPDATE_EXTRAS , bundle);
    }

    private void handleVolteSetPauField() {
        // find corresponding call, dialing or active.
        Call call = CallsManager.getInstance().getDialingCall();
        if (call == null) {
            call = CallsManager.getInstance().getActiveCall();
        }
        if (call == null) {
            log("handleVolteSetPauField()... can not find dialing or active call, skip!");
            return;
        }
        // generate bundle.
        Bundle bundle = new Bundle();
        String pau = "<tel:123456789><sip:123456789@172.20.2.2><name:XXYYZZ>";
        bundle.putString(TelecomManagerEx.EXTRA_VOLTE_PAU_FIELD, pau);
        // trigger it.
        mConnectionServiceWrapper.triggierVolteTest(call, MSG_UPDATE_EXTRAS , bundle);
    }

    private void handleVolteConfHost() {
        // find corresponding call, active conference call.
        Call conferenceCall = CallsManager.getInstance().getActiveCall();
        if (conferenceCall == null || !conferenceCall.isConference()) {
            log("handleVolteConfHost()... can not find a conference call, skip!");
            return;
        }
        int conferenceCapabilities = conferenceCall.getConnectionCapabilities() | Connection.CAPABILITY_INVITE_PARTICIPANTS;
        conferenceCall.setConnectionCapabilities(conferenceCapabilities);
        List<Call> childCalls = conferenceCall.getChildCalls();
        for (Call call : childCalls) {
            int callCapabilities = call.getConnectionCapabilities() | Connection.CAPABILITY_VOLTE;
            callCapabilities = callCapabilities & ~Connection.CAPABILITY_SEPARATE_FROM_CONFERENCE;
            call.setConnectionCapabilities(callCapabilities);
        }
    }

    private void handleVolteConfParticipant() {
        // find corresponding call, active conference call.
        Call conferenceCall = CallsManager.getInstance().getActiveCall();
        if (conferenceCall == null || !conferenceCall.isConference()) {
            log("handleVolteConfParticipant()... can not find a conference call, skip!");
            return;
        }
        int conferenceCapabilities = conferenceCall.getConnectionCapabilities() & ~Connection.CAPABILITY_INVITE_PARTICIPANTS;
        conferenceCall.setConnectionCapabilities(conferenceCapabilities);
        List<Call> childCalls = conferenceCall.getChildCalls();
        for (Call call : childCalls) {
            int callCapabilities = call.getConnectionCapabilities() | Connection.CAPABILITY_VOLTE;
            callCapabilities = callCapabilities & ~Connection.CAPABILITY_SEPARATE_FROM_CONFERENCE;
            callCapabilities = callCapabilities & ~Connection.CAPABILITY_DISCONNECT_FROM_CONFERENCE;
            call.setConnectionCapabilities(callCapabilities);
        }
    }

    private void handleVolteConfSRVCC() {
        Call conferenceCall = CallsManager.getInstance().getActiveCall();
        if (conferenceCall == null || !conferenceCall.isConference()) {
            log("handleVolteConfSRVCC()... can not find a conference call, skip!");
            return;
        }
        boolean isHost = Connection.CAPABILITY_INVITE_PARTICIPANTS == (conferenceCall
                .getConnectionCapabilities() & Connection.CAPABILITY_INVITE_PARTICIPANTS);

        List<Call> childCalls = conferenceCall.getChildCalls();
        for (Call call : childCalls) {
            int callCapabilities = call.getConnectionCapabilities() & ~Connection.CAPABILITY_VOLTE;
            callCapabilities = callCapabilities | Connection.CAPABILITY_SEPARATE_FROM_CONFERENCE;
            callCapabilities = callCapabilities | Connection.CAPABILITY_DISCONNECT_FROM_CONFERENCE;
            call.setConnectionCapabilities(callCapabilities);
        }

        if (isHost) {
            int conferenceCapabilities = conferenceCall.getConnectionCapabilities() & ~Connection.CAPABILITY_INVITE_PARTICIPANTS;
            conferenceCall.setConnectionCapabilities(conferenceCapabilities);
        } else {
            log("handleVolteConfSRVCC()... participant is not support for now !");
        }
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, ">>>>>" + msg);
    }
}
