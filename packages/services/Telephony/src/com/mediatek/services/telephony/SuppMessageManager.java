package com.mediatek.services.telephony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.gsm.SuppCrssNotification;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.services.telephony.TelephonyConnection;
import com.mediatek.telecom.TelecomManagerEx;

import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telecom.Connection;
import android.telecom.ConnectionService;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

// / Added by guofeiyao 11.18.2015
import android.os.SystemProperties;
// / End

public class SuppMessageManager {

    public static final String LOG_TAG = "SuppMessageManager";
    private static final int EVENT_SUPP_SERVICE_FAILED              = 250;
    private static final int EVENT_CRSS_SUPP_SERVICE_NOTIFICATION   = EVENT_SUPP_SERVICE_FAILED + 1;
    private static final int EVENT_SUPP_SERVICE_NOTIFICATION        = EVENT_SUPP_SERVICE_FAILED + 2;

    private ConnectionService mConnectionService;
    private List<SuppMessageHandler> mSuppMessageHandlerList = new ArrayList<SuppMessageHandler>();

    private HashMap<Phone, ArrayList<SuppServiceNotification>> mCachedSsnsMap =
            new HashMap<Phone, ArrayList<SuppServiceNotification>>();
    private HashMap<Phone, ArrayList<SuppCrssNotification>> mCachedCrssnsMap =
            new HashMap<Phone, ArrayList<SuppCrssNotification>>();


    public SuppMessageManager(ConnectionService connectionService) {
        mConnectionService = connectionService;
    }

    /**
     * register handlers to Phone when TelephonyConnectionService raise.
     */
    public void registerSuppMessageForPhones() {
        mSuppMessageHandlerList.clear();
        Phone[] phones = PhoneFactory.getPhones();
        for (Phone phone : phones) {
            if (phone != null) {
                Log.v(LOG_TAG, "registerSuppMessageForPhones()... for service" + mConnectionService
                        + " for phone " + phone.getPhoneId());
                SuppMessageHandler handler = new SuppMessageHandler(phone);
                mSuppMessageHandlerList.add(handler);
                phone.registerForSuppServiceFailed(handler, EVENT_SUPP_SERVICE_FAILED, null);
                phone.registerForCrssSuppServiceNotification(handler,
                        EVENT_CRSS_SUPP_SERVICE_NOTIFICATION, null);
                phone.registerForSuppServiceNotification(handler, EVENT_SUPP_SERVICE_NOTIFICATION,
                        null);
            }
        }
    }

    /**
     * unregister handlers from Phone when TelephonyConnectionService down.
     */
    public void unregisterSuppMessageForPhones() {
        for (SuppMessageHandler handler : mSuppMessageHandlerList) {
            if (handler.getPhone() != null) {
                Log.v(LOG_TAG, "unregisterSuppMessageForPhones()..."
                        + " for phone " + handler.getPhone().getPhoneId());
                handler.getPhone().unregisterForSuppServiceFailed(handler);
                handler.getPhone().unregisterForSuppServiceNotification(handler);
                handler.getPhone().unregisterForCrssSuppServiceNotification(handler);
            }
        }
        mSuppMessageHandlerList.clear();
        mCachedSsnsMap.clear();
        mCachedCrssnsMap.clear();
    }

    /**
     * Force Supplementary Message update once TelephonyConnection is created.
     * @param c The connection to update supplementary messages.
     * @param p The phone to update supplementary messages.
     */
    public void forceSuppMessageUpdate(TelephonyConnection c, Phone p) {
        ArrayList<SuppServiceNotification> ssnList = mCachedSsnsMap.get(p);
        if (ssnList != null) {
            Log.v(LOG_TAG, "forceSuppMessageUpdate()... for SuppServiceNotification for " + c
                    + " for phone " + p.getPhoneId());
            for (SuppServiceNotification ssn : ssnList) {
                onSuppServiceNotification(ssn, p, c);
            }
            mCachedSsnsMap.remove(p);
         }

        ArrayList<SuppCrssNotification> crssnList = mCachedCrssnsMap.get(p);
        if (crssnList != null) {
            Log.v(LOG_TAG, "forceSuppMessageUpdate()... for SuppCrssNotification for " + c
                    + " for phone " + p.getPhoneId());
            for (SuppCrssNotification crssn : crssnList) {
                onCrssSuppServiceNotification(crssn, p, c);
            }
            mCachedCrssnsMap.remove(p);
         }
    }

    /**
     * Handler to handle SS messages, one phone one handler.
     */
    private class SuppMessageHandler extends Handler {
        private Phone mPhone;   // This is used for some operation when we receive message.

        public SuppMessageHandler(Phone phone) {
            mPhone = phone;
        }

        private Phone getPhone() {
            return mPhone;
        }

        @Override
        public void handleMessage(Message msg) {
            Log.v(LOG_TAG, "handleMessage()... for service " + mConnectionService
                    + " for phone " + mPhone.getPhoneId());
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar == null || ar.result == null || mConnectionService == null) {
                Log.e(LOG_TAG, "handleMessage()...Wrong condition: ar / mConnectionService = "
                                + ar + " / " + mConnectionService);
                return;
            }
            switch (msg.what) {
                case EVENT_SUPP_SERVICE_FAILED:
                    if (!(ar.result instanceof Phone.SuppService)) {
                        Log.e(LOG_TAG, "handleMessage()...Wrong data for Phone.SuppService");
                        return;
                    }
                    Phone.SuppService service = (Phone.SuppService) ar.result;
                    onSuppServiceFailed(service, mPhone);
                    break;
                case EVENT_SUPP_SERVICE_NOTIFICATION: {
                    if (!(ar.result instanceof SuppServiceNotification)) {
                        Log.e(LOG_TAG, "handleMessage()...Wrong data for SuppServiceNotification");
                        return;
                    }
                    SuppServiceNotification noti = (SuppServiceNotification) ar.result;
                    onSuppServiceNotification(noti, mPhone, null);
                    break;
                }
                case EVENT_CRSS_SUPP_SERVICE_NOTIFICATION: {
                    if (!(ar.result instanceof SuppCrssNotification)) {
                        Log.e(LOG_TAG, "handleMessage()...Wrong data for SuppCrssNotification");
                        return;
                    }
                    SuppCrssNotification noti = (SuppCrssNotification) ar.result;
                    onCrssSuppServiceNotification(noti, mPhone, null);
                    break;
                }
                default:
                    break;
            }
        }
    };

    /**
     * handle "EVENT_SUPP_SERVICE_FAILED" case.
     * TODO: maybe we should leave toast-part just here, like mr1.
     * @param service SuppService of Phone
     * @param phone Target Phone
     */
    private void onSuppServiceFailed(Phone.SuppService service, Phone phone) {
        Log.v(LOG_TAG, "onSuppServiceFailed()... " + service);

        int actionCode = (int) getSuppServiceActionCode(service);

        com.android.internal.telephony.Connection originalConnection =
                getProperOriginalConnection(phone);
        Connection connection = findConnection(originalConnection);
        if (connection != null) {
            connection.notifyActionFailed(actionCode);
        } else {
            Log.v(LOG_TAG, "onSuppServiceFailed()...connection is null");
        }
    }

    /**
     * handle "EVENT_SUPP_SERVICE_NOTIFICATION" case.
     * TODO: maybe we should leave toast-part just here, like mr1.
     * @param noti SuppServiceNotification
     * @param phone Target Phone
     */
    private void onSuppServiceNotification(SuppServiceNotification noti, Phone phone,
            Connection forceConn) {
        Log.v(LOG_TAG, "onSuppServiceNotification()... " + noti);
        com.android.internal.telephony.Connection originalConnection = null;
        Connection connection = null;
        boolean normalCase = false;
        if (noti.notificationType == 0) {       // for MO cases
            switch (noti.code) {
            case SuppServiceNotification.MO_CODE_CALL_IS_EMERGENCY:
                connection = getConnectionWithState(Call.State.DIALING);
                if (connection == null) {
                    connection = getConnectionWithState(Call.State.ALERTING);
                }
                if (connection != null) {
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(TelecomManagerEx.EXTRA_VOLTE_MARKED_AS_EMERGENCY, true);
                    if (forceConn == null || forceConn == connection) {
                        connection.setCallInfo(bundle);
                    }
                } else {
                    if (forceConn == null) {
                        Log.v(LOG_TAG, "onSuppServiceNotification()...MO connection is null");
                        addSsnList(noti, phone);
                    }
                }
                break;

            case SuppServiceNotification.MO_CODE_CALL_FORWARDED_TO:
                Log.v(LOG_TAG, "onSuppServiceNotification(): MO_CODE_CALL_FORWARDED_TO");
                if (noti.number != null && !noti.number.isEmpty()) {
                    normalCase = true;
                } else {
                    // for ALPS01927571.
                    Log.v(LOG_TAG, "number is empty, ignore it.");
                }
                break;

            default:
                normalCase = true;
            }
        } else if (noti.notificationType == 1) {    // for MT cases
            normalCase = true;
        }
        if (normalCase) {
            // the upper layer just show a toast, so here we just bypass it to Telecomm,
            // and do not check which connection it belongs to (get anyone).
            originalConnection = getProperOriginalConnection(phone);
            connection = findConnection(originalConnection);
            if (connection != null) {
                if (forceConn == null || forceConn == connection) {
                    connection.notifySSNotificationToast(noti.notificationType,
                            noti.type, noti.code, noti.number, noti.index);
                }
            } else {
                if (forceConn == null) {
                    Log.v(LOG_TAG, "onSuppServiceNotification()...MT connection is null");
                    addSsnList(noti, phone);
                }
            }
        }
    }

    private void addSsnList(SuppServiceNotification noti, Phone phone) {
        ArrayList<SuppServiceNotification> ssnList = mCachedSsnsMap.get(phone);
        if (ssnList == null) {
            ssnList = new ArrayList<SuppServiceNotification>();
        } else {
            mCachedSsnsMap.remove(phone);
        }
        ssnList.add(noti);
        mCachedSsnsMap.put(phone, ssnList);
    }

    /**
     * handle "EVENT_CRSS_SUPP_SERVICE_NOTIFICATION" case.
     * TODO: maybe we can merge notifyNumberUpdate() and notifyIncomingInfoUpdate() to one.
     * just as IConnectionServiceAdapter.setAddress()
     * @param noti SuppCrssNotification
     * @param phone Target Phone
     */
    private void  onCrssSuppServiceNotification(SuppCrssNotification noti, Phone phone,
            Connection forceConn) {
        //Log.v(LOG_TAG, "onCrssSuppServiceNotification... " + noti);
        com.android.internal.telephony.Connection originalConnection = null;
        Connection connection = null;
        switch (noti.code) {
            case SuppCrssNotification.CRSS_CALL_WAITING:
                break;
            case SuppCrssNotification.CRSS_CALLED_LINE_ID_PREST:
                originalConnection = getOriginalConnectionWithState(phone, Call.State.ACTIVE);
                connection = findConnection(originalConnection);
                if (connection != null) {
                    if (forceConn == null || forceConn == connection) {
                        connection.notifyNumberUpdate(noti.number);
                    }
                } else {
                    if (forceConn == null) {
                        Log.v(LOG_TAG, "onCrssSuppServiceNotification()...connection is null");
                        addCrssnList(noti, phone);
                    }
                }
                break;
            case SuppCrssNotification.CRSS_CALLING_LINE_ID_PREST:
                originalConnection = getOriginalConnectionWithState(phone, Call.State.INCOMING);
                connection = findConnection(originalConnection);
                if (connection != null) {
                    if (forceConn == null || forceConn == connection) {
                        connection.notifyIncomingInfoUpdate(
                                noti.type, noti.alphaid, noti.cli_validity);
                    }
                } else {
                    if (forceConn == null) {
                        Log.v(LOG_TAG, "onCrssSuppServiceNotification()...connection is null");
                        addCrssnList(noti, phone);
                    }
                }
                break;
            case SuppCrssNotification.CRSS_CONNECTED_LINE_ID_PREST:

				// / Added by guofeiyao on 11.18.2015
				// Disable +COLP for specific location
				if ( !SystemProperties.get("ro.hq.disable.colp").equals("1") ) {
					
					
                originalConnection = getOriginalConnectionWithState(phone, Call.State.ACTIVE);
                connection = findConnection(originalConnection);
                if (connection != null) {
                    if (forceConn == null || forceConn == connection) {
                        connection.notifyNumberUpdate(PhoneNumberUtils.stringFromStringAndTOA(
                                noti.number, noti.type));
                    }
                } else {
                    if (forceConn == null) {
                        Log.v(LOG_TAG, "onCrssSuppServiceNotification()...connection is null");
                        addCrssnList(noti, phone);
                    }
                }


				}
				// / End
                break;
            default:
                break;
        }
    }

    private void addCrssnList(SuppCrssNotification noti, Phone phone) {
        ArrayList<SuppCrssNotification> crssnList = mCachedCrssnsMap.get(phone);
        if (crssnList == null) {
            crssnList = new ArrayList<SuppCrssNotification>();
        } else {
            mCachedCrssnsMap.remove(phone);
        }
        crssnList.add(noti);
        mCachedCrssnsMap.put(phone, crssnList);
    }

    /**
     * For some SS message, we can tell which connection it belongs to, use this function to find it.
     * Eg, "SuppServiceNotification.MO_CODE_CALL_IS_WAITING" is for connection with state of DIALING or ALERTING.
     * @param phone
     * @param state
     * @return
     */
    private com.android.internal.telephony.Connection getOriginalConnectionWithState(Phone phone,
            Call.State state) {
        com.android.internal.telephony.Connection originalConnection = null;
        Call call = null;
        if (state == Call.State.INCOMING) {
            call = phone.getRingingCall();
        } else if (state == Call.State.HOLDING) {
            call = phone.getBackgroundCall();
        } else if (state == Call.State.DIALING || state == Call.State.ALERTING
                || state == Call.State.ACTIVE) {
            call = phone.getForegroundCall();
        }
        if (call != null && call.getState() == state) {
            originalConnection = call.getLatestConnection();
        }
        return originalConnection;
    }

    /**
     * For some SS message, we can not judge which connection it belongs to,
     * so we scan all calls, and find the most-likely connection.
     * ringing > foreground > background
     * @param phone
     * @param state
     * @return
     */
    private com.android.internal.telephony.Connection getProperOriginalConnection(Phone phone) {
        if (phone == null) {
            Log.v(LOG_TAG, "getProperOriginalConnection: phone is null");
            return null;
        }

        com.android.internal.telephony.Connection originalConnection = null;
        // phone.getRingingCall() can get GsmCall or ImsPhoneCall.
        if (phone.getRingingCall().getState().isAlive()) {
            originalConnection = phone.getRingingCall().getLatestConnection();
        }

        if (originalConnection == null && phone.getForegroundCall().getState().isAlive()) {
            originalConnection = phone.getForegroundCall().getLatestConnection();
        }

        if (originalConnection == null && phone.getBackgroundCall().getState().isAlive()) {
            originalConnection = phone.getBackgroundCall().getLatestConnection();
        }

        // for foreground and background, need to use ImsPhone to get ImsPhoneCall.
        Phone imsPhone = phone.getImsPhone();
        if (imsPhone != null) {
            if (originalConnection == null && imsPhone.getForegroundCall().getState().isAlive()) {
                originalConnection = imsPhone.getForegroundCall().getLatestConnection();
            }

            if (originalConnection == null && imsPhone.getBackgroundCall().getState().isAlive()) {
                originalConnection = imsPhone.getBackgroundCall().getLatestConnection();
            }
        }

        return originalConnection;
    }

    /**
     * get all connections exist in ConnectionService
     * @return
     */
    private ArrayList<TelephonyConnection> getAllTelephonyConnectionsFromService() {
        ArrayList<TelephonyConnection> connectionList = new ArrayList<TelephonyConnection>();
        if (mConnectionService != null) {
            for (Connection connection : mConnectionService.getAllConnections()) {
                if (connection instanceof TelephonyConnection) {
                    connectionList.add((TelephonyConnection)connection);
                }
            }
        }
        return connectionList;
    }

    /**
     * find Connection in ConnectionService, which corresponding to certain original Connection.
     * @param originalConnection
     * @return
     */
    private Connection findConnection(com.android.internal.telephony.Connection originalConnection) {
        Connection connection = null;
        ArrayList<TelephonyConnection> telephonyConnections = getAllTelephonyConnectionsFromService();
        if (originalConnection != null && telephonyConnections != null) {
            for (TelephonyConnection telephonyConnection : telephonyConnections) {
                if (originalConnection == telephonyConnection.getOriginalConnection()) {
                    connection = telephonyConnection;
                }
            }
        }
        return connection;
    }

    /**
     * Get TelephonyConnection with special state.
     * @param state
     * @return
     */
    private Connection getConnectionWithState(Call.State state) {
        Connection connection = null;
        ArrayList<TelephonyConnection> telephonyConnections =
            getAllTelephonyConnectionsFromService();
        if (telephonyConnections != null) {
            for (TelephonyConnection telephonyConnection : telephonyConnections) {
                com.android.internal.telephony.Connection originalConnection =
                    telephonyConnection.getOriginalConnection();
                if (originalConnection != null) {
                    Call call = originalConnection.getCall();
                    if (call != null && call.getState() == state) {
                        connection = telephonyConnection;
                        break;
                    }
                }
            }
        }
        return connection;
    }

    private int getSuppServiceActionCode(Phone.SuppService service) {
        int actionCode = 0;
        switch (service) {
            case SWITCH:
                actionCode = 1;
                break;
            case SEPARATE:
                actionCode = 2;
                break;
            case TRANSFER:
                actionCode = 3;
                break;
            case CONFERENCE:
                actionCode = 4;
                break;
            case REJECT:
                actionCode = 5;
                break;
            case HANGUP:
                actionCode = 6;
                break;
            case UNKNOWN:
            default:
                break;
        }
        return actionCode;
    }

    // Based on current ConnectionService-based design, maybe should consider below items later.
    // TODO: 1/ maybe we can change path from "SuppMessageManager -> Connection
    //                               -> ConnectionService.mConnectionLisnter -> Adapter"
    //          to "SuppMessageManager -> ConnectionService -> Adapter"
    //      2/ do not scan connections in ConnectionService,
    //          instead, manage a list, like GsmConferenceController.
    //      3/ divide those messages into two parts: toast only + logic-related.
    //          for toast only, two ways to handle:
    //              1) show toast here (no interface needed);
    //              2) show toast in Telecomm or InCallUI; (current way)
    //                  modify interface with Telecomm (remove parameter of callId),
    //                  then here we can just bypass those messages directly,
    //                  no need try to find corresponding connection.
    //          for logic-related, maybe we can check interfaces with Telecomm again.
    //              Eg,merge notifyNumberUpdate() and notifyIncomingInfoUpdate() to be setAddress().
}
