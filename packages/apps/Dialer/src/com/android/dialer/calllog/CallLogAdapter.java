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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteFullException;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.text.TextUtils;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.common.widget.GroupingListAdapter;
import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.ContactLoader;
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.PhoneCallDetails;
import com.android.dialer.PhoneCallDetailsHelper;
import com.android.dialer.R;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.ExpirableCache;
import com.android.dialer.calllog.PhoneAccountUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.mediatek.contacts.util.SimContactPhotoUtils;
import com.mediatek.dialer.ext.ExtensionManager;
import com.mediatek.dialer.util.DialerFeatureOptions;
import com.mediatek.dialer.util.DialerVolteUtils;


//add by zhangjinqiang for al812--start
import huawei.android.widget.TimeAxisWidget;

import com.android.incallui.InCallApp;

import java.lang.Exception;
import java.lang.Integer;
import java.lang.Override;
import java.lang.String;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedList;

import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.widget.Button;
import android.app.AlertDialog;
import android.view.Window;

import com.android.contacts.activities.PeopleActivity;

import android.provider.Settings;
//add by zhangjinqiang for al812--end

import android.graphics.drawable.Drawable;

// / Added by guofeiyao
import android.os.Bundle;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.provider.ContactsContract.QuickContact;
import android.content.ContentResolver;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.graphics.Rect;
import android.content.DialogInterface;
import android.content.ClipData;
import android.content.ClipboardManager;

import com.android.contacts.quickcontact.QuickContactActivity;
import com.android.dialer.dialpad.DialpadFragment;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.huawei.harassmentinterception.service.IHarassmentInterceptionService;
import com.huawei.harassmentinterception.service.IHarassmentInterceptionService.Stub;

import java.util.Arrays;
import android.telephony.TelephonyManager;

// / End

/**
 * Adapter class to fill in data for the Call Log.
 */
public class CallLogAdapter extends GroupingListAdapter
        implements ViewTreeObserver.OnPreDrawListener, CallLogGroupBuilder.GroupCreator
        // / Added by guofeiyao for ContextMenu when item is LongClicked
        , CallLogQueryHandler.Listener
        // / End
        {
    private static final String TAG = CallLogAdapter.class.getSimpleName();

    private static final int VOICEMAIL_TRANSCRIPTION_MAX_LINES = 10;
    private	List<String> CN = new ArrayList<String>();//HQ_wuruijun add for HQ01359274
    //modified by jinlibo for call history list scroll performance
    private Drawable callLabelOne;
    private Drawable callLabelTwo;
    //jinlibo add end

    /** The enumeration of {@link android.os.AsyncTask} objects used in this class. */
    public enum Tasks {
        REMOVE_CALL_LOG_ENTRIES,
    }

    /** Interface used to inform a parent UI element that a list item has been expanded. */
    public interface CallItemExpandedListener {
        /**
         * @param view The {@link View} that represents the item that was clicked
         *         on.
         */
        public void onItemExpanded(View view);

        /**
         * Retrieves the call log view for the specified call Id.  If the view is not currently
         * visible, returns null.
         *
         * @param callId The call Id.
         * @return The call log view.
         */
        public View getViewForCallId(long callId);
    }

    /** Interface used to initiate a refresh of the content. */
    public interface CallFetcher {
        public void fetchCalls();
    }

    /** Implements onClickListener for the report button. */
    public interface OnReportButtonClickListener {
        public void onReportButtonClick(String number);
    }

    /**
     * Stores a phone number of a call with the country code where it originally occurred.
     * <p>
     * Note the country does not necessarily specifies the country of the phone number itself, but
     * it is the country in which the user was in when the call was placed or received.
     */
    private static final class NumberWithCountryIso {
        public final String number;
        public final String countryIso;

        public NumberWithCountryIso(String number, String countryIso) {
            this.number = number;
            this.countryIso = countryIso;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) return false;
            if (!(o instanceof NumberWithCountryIso)) return false;
            NumberWithCountryIso other = (NumberWithCountryIso) o;
            return TextUtils.equals(number, other.number)
                    && TextUtils.equals(countryIso, other.countryIso);
        }

        @Override
        public int hashCode() {
            return (number == null ? 0 : number.hashCode())
                    ^ (countryIso == null ? 0 : countryIso.hashCode());
        }
    }

    /** The time in millis to delay starting the thread processing requests. */
    private static final int START_PROCESSING_REQUESTS_DELAY_MILLIS = 1000;

    /** The size of the cache of contact info. */
    private static final int CONTACT_INFO_CACHE_SIZE = 100;

    /** Constant used to indicate no row is expanded. */
    private static final long NONE_EXPANDED = -1;

    protected final Context mContext;
    private final ContactInfoHelper mContactInfoHelper;
    private final CallFetcher mCallFetcher;
    private final Toast mReportedToast;
    private final OnReportButtonClickListener mOnReportButtonClickListener;
    private ViewTreeObserver mViewTreeObserver = null;

    /**
     * A cache of the contact details for the phone numbers in the call log.
     * <p>
     * The content of the cache is expired (but not purged) whenever the application comes to
     * the foreground.
     * <p>
     * The key is number with the country in which the call was placed or received.
     */
    private ExpirableCache<NumberWithCountryIso, ContactInfo> mContactInfoCache;

    /**
     * Tracks the call log row which was previously expanded.  Used so that the closure of a
     * previously expanded call log entry can be animated on rebind.
     */
    private long mPreviouslyExpanded = NONE_EXPANDED;

    /**
     * Tracks the currently expanded call log row.
     */
    private long mCurrentlyExpanded = NONE_EXPANDED;

    /**
     *  Hashmap, keyed by call Id, used to track the day group for a call.  As call log entries are
     *  put into the primary call groups in {@link com.android.dialer.calllog.CallLogGroupBuilder},
     *  they are also assigned a secondary "day group".  This hashmap tracks the day group assigned
     *  to all calls in the call log.  This information is used to trigger the display of a day
     *  group header above the call log entry at the start of a day group.
     *  Note: Multiple calls are grouped into a single primary "call group" in the call log, and
     *  the cursor used to bind rows includes all of these calls.  When determining if a day group
     *  change has occurred it is necessary to look at the last entry in the call log to determine
     *  its day group.  This hashmap provides a means of determining the previous day group without
     *  having to reverse the cursor to the start of the previous day call log entry.
     */
    private HashMap<Long,Integer> mDayGroups = new HashMap<Long, Integer>();

	//add by zhangjinqiang for HQ01508475 at 20151207 -start
	String[] operatorName = new String[]{"70601","70401","71021","71073","71403","70604","706040","70403","704030","33403","334030","71030","710300","714020","71402","72207"};
	String[] operatorNameAT = new String[]{"334050","334090","33450"};
	//add by zhangjinqiang for HQ01508475 at 20151207 -end

    /**
     * A request for contact details for the given number.
     */
    private static final class ContactInfoRequest {
        /** The number to look-up. */
        public final String number;
        /** The country in which a call to or from this number was placed or received. */
        public final String countryIso;
        /** The cached contact information stored in the call log. */
        public final ContactInfo callLogInfo;

        public ContactInfoRequest(String number, String countryIso, ContactInfo callLogInfo) {
            this.number = number;
            this.countryIso = countryIso;
            this.callLogInfo = callLogInfo;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (!(obj instanceof ContactInfoRequest)) return false;

            ContactInfoRequest other = (ContactInfoRequest) obj;

            if (!TextUtils.equals(number, other.number)) return false;
            if (!TextUtils.equals(countryIso, other.countryIso)) return false;
            if (!Objects.equal(callLogInfo, other.callLogInfo)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((callLogInfo == null) ? 0 : callLogInfo.hashCode());
            result = prime * result + ((countryIso == null) ? 0 : countryIso.hashCode());
            result = prime * result + ((number == null) ? 0 : number.hashCode());
            return result;
        }
    }

    /**
     * List of requests to update contact details.
     * <p>
     * Each request is made of a phone number to look up, and the contact info currently stored in
     * the call log for this number.
     * <p>
     * The requests are added when displaying the contacts and are processed by a background
     * thread.
     */
    private final LinkedList<ContactInfoRequest> mRequests;

    private boolean mLoading = true;
    private static final int REDRAW = 1;
    private static final int START_THREAD = 2;

    private QueryThread mCallerIdThread;

    /** Instance of helper class for managing views. */
    private final CallLogListItemHelper mCallLogViewsHelper;

    /** Helper to set up contact photos. */
    private final ContactPhotoManager mContactPhotoManager;
    /** Helper to parse and process phone numbers. */
    private PhoneNumberDisplayHelper mPhoneNumberHelper;
    /** Helper to access Telephony phone number utils class */
    protected final PhoneNumberUtilsWrapper mPhoneNumberUtilsWrapper;
    /** Helper to group call log entries. */
    private final CallLogGroupBuilder mCallLogGroupBuilder;

    private CallItemExpandedListener mCallItemExpandedListener;

    /** Can be set to true by tests to disable processing of requests. */
    private volatile boolean mRequestProcessingDisabled = false;

    private boolean mIsCallLog = true;

    private View mBadgeContainer;
    private ImageView mBadgeImageView;
    private TextView mBadgeText;

    private int mCallLogBackgroundColor;
    private int mExpandedBackgroundColor;
    private float mExpandedTranslationZ;
    private int mPhotoSize;

    /** Listener for the primary or secondary actions in the list.
     *  Primary opens the call details.
     *  Secondary calls or plays.
     **/
     //modify by zhangjinqiang for al812--start
     private String phoneNumber;
    private final View.OnClickListener mActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
        		getSubInfoList();
			startActivityForAction(view);
        }
    };
	//modify by zhangjinqiang for al812--end

            // / Added by guofeiyao

            /** Called by the CallLogQueryHandler when the list of calls has been fetched or updated. */
            @Override
            public boolean onCallsFetched(Cursor cursor) {
                return true;
            }

            /** Called by the CallLogQueryHandler when the list of calls has been fetched or updated. */
            @Override
            public void onCallsDeleted() {

            }

            /** Called when {@link CallLogQueryHandler#fetchVoicemailStatus()} completes. */
            @Override
            public void onVoicemailStatusFetched(Cursor statusCursor){

            }
            public void onPause() {
                if (null != ctxMenuDialog && ctxMenuDialog.isShowing()) {
                    ctxMenuDialog.dismiss();
                    ctxMenuDialog = null;
                }

				if (null != dialDialog && dialDialog.isShowing()) {
                    dialDialog.dismiss();
                    dialDialog = null;
                }
            }

            private static final String G = "guofeiyao_";
            private CallLogQueryHandler mCallLogQueryHandler;
            private static final String HARASSMENT_INTERCEPTION_SERVICE =
                    "com.huawei.harassmentinterception.service.HarassmentInterceptionService";
            private IBinder harassmentIntercepterService;
            private IHarassmentInterceptionService hisStub;
            private AlertDialog ctxMenuDialog;
			private AlertDialog dialDialog;

            class DialCallback implements View.OnClickListener {
                public String getNumber() {
					return number;
				}

				public void setNumber(String number) {
					this.number = number;
				}

				private String number;
                private String name;
                private Uri contactUri;
                private long id;
                private boolean isSaved = false;

                public DialCallback(String number) {
                    this.number = number;
                }

                public DialCallback(String number, boolean isSaved) {
                    this(number);
                    this.isSaved = isSaved;
                }

                public DialCallback(String number, boolean isSaved, long id) {
                    this(number, isSaved);
                    this.id = id;
                }

                public void setIsSaved(boolean b) {
                    isSaved = b;
                }

                public void setName(String n) {
                    name = n;
                }

                public void setContactUri(Uri u) {
                    this.contactUri = u;
                }

                public void setId(long id) {
                    this.id = id;
                }

                @Override
                public void onClick(View view) {
                    if (null == number || number.length() == 0) {
                        Log.e(G + TAG, "Error in DialCallback onClick,null==number||number.length()==0");
                        return;
                    }
                     Log.i("zhenghao","click...............");
                     //added by zhenghao;
                     getSubInfoList();
                     if(mSubCount == 2){
                         showDialog(number);
		         return;
		     }
	            //added end ! 
                    //add by zhangjinqiang for display SIM2 only exist SIM2 --start
                    if (!checkSimPosition()) {
                        Settings.System.putInt(mContext.getContentResolver(), "slot_id", 1);
                    }
                    //add by zhangjinqiang end

                    PhoneAccountHandle h = ((TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE))
                            .getUserSelectedOutgoingPhoneAccount();
                    if (null == h) {
						Log.e(G + TAG, "Error in DialCallback onClick,PhoneAccountHandle... h==null!!!");
                        startActivityForAction(view);
                    } else {
                        Log.e(G + TAG, "DialCallback onClick,makeDialWithPhonehandle(mContext, number, h)");
                        DialpadFragment.makeDialWithPhonehandle(mContext, number, h);
                    }
                }

                public boolean onLongClick(View v,int pos) {
                    if (null == number || number.length() == 0) {
                        Log.e(G + TAG, "Error in DialCallback onLongClick,null==number||number.length()==0");
                        return false;
                    }
                    Log.d(G + TAG, "DialCallback onLongClick isSaved:" + isSaved);
                    if (isSaved) {
                        if (null == contactUri) {
                            Log.d(G + TAG, "DialCallback :null == contactUri!!!");
                            return false;
                        }
                    }
                    buildContextMenu(pos);
                    return true;
                }

                private void buildContextMenu(final int pos) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                    if (isSaved) {
                        if (!checkNumberBlocked(number)) {
                            final String[] arrayMenuItems = mContext.getResources().getStringArray(
                                    R.array.ctx_menu_saved);
                            builder.setTitle(name)
                                    .setItems(arrayMenuItems, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id) {
                                            switch (id) {
                                                case 0:
                                                    sendMessage();
                                                    break;
                                                case 1:
                                                    viewContact();
                                                    break;
                                                case 2:
                                                    editBeforeCall();
                                                    break;
                                                case 3:
                                                    deleteEntry(pos);
                                                    break;
                                                case 4:
                                                    sendNumber();
                                                    break;
                                                case 5:
                                                    copyNumber();
                                                    break;
                                                case 6:
                                                    addBlacklist(isSaved, number, name);
                                                    break;
                                                default:
                                                    Log.e(G + TAG, "DialCallback,ctx_menu_saved, Error item is clicked");
                                                    break;
                                            }
                                        }
                                    });
                        } else {
                            final String[] arrayMenuItems = mContext.getResources().getStringArray(
                                    R.array.ctx_menu_saved_blocked);
                            builder.setTitle(name)
                                    .setItems(arrayMenuItems, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id) {
                                            switch (id) {
                                                case 0:
                                                    sendMessage();
                                                    break;
                                                case 1:
                                                    viewContact();
                                                    break;
                                                case 2:
                                                    editBeforeCall();
                                                    break;
                                                case 3:
                                                    deleteEntry(pos);
                                                    break;
                                                case 4:
                                                    sendNumber();
                                                    break;
                                                case 5:
                                                    copyNumber();
                                                    break;
                                                case 6:
                                                    removeBlacklist(number);
                                                    break;
                                                default:
                                                    Log.e(G + TAG, "DialCallback,ctx_menu_saved, Error item is clicked");
                                                    break;
                                            }
                                        }
                                    });
                        }
                    } else {
                        if (!checkNumberBlocked(number)) {
                            final String[] arrayMenuItems = mContext.getResources().getStringArray(
                                    R.array.ctx_menu);
                            builder.setTitle(number)
                                    .setItems(arrayMenuItems, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id) {
                                            switch (id) {
                                                case 0:
                                                    sendMessage();
                                                    break;
                                                case 1:
                                                    createContact();
                                                    break;
                                                case 2:
                                                    editBeforeCall();
                                                    break;
                                                case 3:
                                                    deleteEntry(pos);
                                                    break;
                                                case 4:
                                                    sendNumber();
                                                    break;
                                                case 5:
                                                    copyNumber();
                                                    break;
                                                case 6:
                                                    addBlacklist(isSaved, number, name);
                                                    break;
                                                default:
                                                    Log.e(G + TAG, "DialCallback,ctx_menu, Error item is clicked");
                                                    break;
                                            }
                                        }
                                    });
                        }else {
                            final String[] arrayMenuItems = mContext.getResources().getStringArray(
                                    R.array.ctx_menu_blocked);
                            builder.setTitle(number)
                                    .setItems(arrayMenuItems, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id) {
                                            switch (id) {
                                                case 0:
                                                    sendMessage();
                                                    break;
                                                case 1:
                                                    createContact();
                                                    break;
                                                case 2:
                                                    editBeforeCall();
                                                    break;
                                                case 3:
                                                    deleteEntry(pos);
                                                    break;
                                                case 4:
                                                    sendNumber();
                                                    break;
                                                case 5:
                                                    copyNumber();
                                                    break;
                                                case 6:
                                                    removeBlacklist(number);
                                                    break;
                                                default:
                                                    Log.e(G + TAG, "DialCallback,ctx_menu, Error item is clicked");
                                                    break;
                                            }
                                        }
                                    });
                        }
                    }

                    ctxMenuDialog = builder.create();
                    ctxMenuDialog.show();
                }

                private void sendMessage() {
                    /* modify by shanlan for HQ02052267 begin */
                    Uri uri = null;
                    if (number.startsWith("#")) {
                        uri = Uri.parse("smsto:%23" + number.substring(1, number.length()));
                    } else if (number.endsWith("#")){
                        uri = Uri.parse("smsto:" + number.substring(0, number.length()-1) + "%23");
                    } else {
                        uri = Uri.parse("smsto:" + number);
                    }
                    //Uri uri = Uri.parse("smsto:" + number);
                    /* modify by shanlan for HQ02052267 end */
                    Intent it = new Intent(Intent.ACTION_SENDTO, uri);
                    mContext.startActivity(it);
                }

                private void viewContact() {
                    Intent intent = QuickContact.composeQuickContactsIntent(
                            mContext, (Rect) null, contactUri,
                            QuickContactActivity.MODE_FULLY_EXPANDED, null);
                    mContext.startActivity(intent);
                }

                private void createContact() {
                    DialerUtils.startActivityWithErrorToast(mContext,
                            DialtactsActivity.getAddNumberToContactIntent(number));
                }

                private void editBeforeCall() {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + number));
                    mContext.startActivity(intent);
                }

                private void deleteEntry(final int pos) {
//                    if (0 == id) {
//                        Log.e(G + TAG, "deleteEntry(),Error,id==0");
//                        return;
//                    }
                    if (null == mCallLogQueryHandler) {
                        Log.e(G + TAG, "deleteEntry(),Error,null == mCallLogQueryHandler");
                        return;
                    }
//                    Log.d(G + TAG, "deleteEntry(),delete id:" + id);


//                    mCallLogQueryHandler.deleteSpecifiedCalls("_id in (\'" + id + "\')");
//                    mCallLogQueryHandler.deleteSpecifiedCalls(getDeleteFilter(pos));

                    // / Just delete all......
//                    mCallLogQueryHandler.deleteSpecifiedCalls("number = \'" + number + "\'");

                    String title = mContext.getString(R.string.deleteCallLogConfirmation_title);
                    String message = mContext.getString(R.string.deleteCallLogConfirmation_message);

                    AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                            .setTitle(title)
                            .setIconAttribute(android.R.attr.alertDialogIcon)
                            .setMessage(message)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.dial_delete,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                               mCallLogQueryHandler.deleteSpecifiedCalls(getDeleteFilter(pos));
                                        }
                                    });
                    dialDialog = builder.create();
                    dialDialog.show();
                }

                private void sendNumber() {
                    Uri smsToUri = Uri.parse("smsto:");
                    Intent intent = new Intent(Intent.ACTION_SENDTO, smsToUri);
                    intent.putExtra("sms_body", number);
                    mContext.startActivity(intent);
                }

                private void copyNumber() {
                    ClipData cd = ClipData.newPlainText("label", number);
                    ClipboardManager clipboardManager = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboardManager.setPrimaryClip(cd);
                    Toast.makeText(mContext, R.string.copied_to_clip, Toast.LENGTH_SHORT).show();
                }

                private String getDeleteFilter(int pos) {
                    StringBuilder where = new StringBuilder("_id in ");
                    where.append("(");

                    ArrayList<Integer> mSelectedCallLogIdList = getCallLogList(pos);

                    /** M: Fix CR ALPS01569024. Use the call log id to identify the select item. @{ */
                    if (mSelectedCallLogIdList.size() > 0) {
                        boolean isFirst = true;
                        for (int id : mSelectedCallLogIdList) {
                            if (isFirst) {
                                isFirst = false;
                            } else {
                                where.append(",");
                            }
                            where.append("\'");
                            where.append(id);
                            where.append("\'");
                        }
                    } else {
                        where.append(-1);
                    }
                    /** @} */

                    where.append(")");
                    return where.toString();
                }
            }

            private ArrayList<Integer> getCallLogList(final int listPosition) {

                ArrayList<Integer> mSelectedCallLogIdList = new ArrayList<Integer>();

                int count = 0;
                if (isGroupHeader(listPosition)) {
                    count = getGroupSize(listPosition);
                } else {
                    count = 1;
                }

                /** M: Fix CR ALPS01569024. Use the call log id to identify the select item. @{ */
                Cursor cursor = (Cursor) getItem(listPosition);
                if (cursor == null) {
                    return null;
                }
                int position = cursor.getPosition();
                int firstId = cursor.getInt(CallLogQuery.ID);
                for (int i = 0; i < count; i++) {
                    if (!cursor.moveToPosition(position + i)) {
                        continue;
                    }
                    int id = cursor.getInt(CallLogQuery.ID);
                    mSelectedCallLogIdList.add(id);
                }
                cursor.moveToPosition(position);
                return mSelectedCallLogIdList;
                /** @} */
            }

            class CheatListener implements View.OnClickListener {
                private Uri u;

                public CheatListener(Uri uri) {
                    u = uri;
                }

                @Override
                public void onClick(View view) {
                    Log.d(G + TAG, "cheat click in Dialer....Ex");
                    if (null == u) {
                        Log.d(G + TAG, "cheat click,Uri is null!!!");
                        return;
                    }
                    Intent intent = QuickContact.composeQuickContactsIntent(
                            mContext, (Rect) null, u,
                            QuickContactActivity.MODE_FULLY_EXPANDED, null);
                    mContext.startActivity(intent);
                }
            }

            private void addBlacklist(boolean b,String number,String name) {
                try {
                    Bundle localBundle = new Bundle();
                    localBundle.putString("BLOCK_PHONENUMBER", number);
                    if (b) {
                        localBundle.putString("BLOCK_CONTACTNAME", name);
                    } else {
                        localBundle.putString("BLOCK_CONTACTNAME", "");
                    }
                    if (null == hisStub) {
                        Log.e(G + TAG, "addBlacklist(),null==hisStub");
                        throw new Exception();
                    }
                    int isBlock = hisStub.addPhoneNumberBlockItem(localBundle, 0, 0);

                    if (isBlock == 0) {
                        Toast.makeText(mContext, R.string.add_to_black_list, Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(G + TAG, "addBlacklist(),isBlock != 0添加黑名单失败！！！");
                    }
                } catch (RemoteException paramContext) {
                    paramContext.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

            private void removeBlacklist(String number) {
                try {
                    Bundle localBundle = new Bundle();
                    localBundle.putString("BLOCK_PHONENUMBER", number);
                    if (null == hisStub) {
                        Log.e(G + TAG, "removeBlacklist(String number),null==hisStub");
                        throw new Exception();
                    }
                    int isRemove = hisStub.removePhoneNumberBlockItem(localBundle, 0, 0);
                    if (isRemove == 0) {
                        Toast.makeText(mContext, R.string.remove_from_black_list, Toast.LENGTH_SHORT).show();
                    }
                    else {
                        Log.e(G + TAG, "removeBlacklist(String number),isRemove != 0移除黑名单失败！！！");
                    }
                } catch (Exception paramContext) {
                    paramContext.printStackTrace();
                }
            }

            private boolean checkNumberBlocked(String Number) {
                try {
                    Bundle localBundle = new Bundle();
                    localBundle.putString("CHECK_PHONENUMBER", Number);
                    if (null == hisStub) {
                        Log.e(G + TAG, "checkNumberBlocked(String Number),null==hisStub");
                        throw new Exception();
                    }
                    int isBlock = hisStub.checkPhoneNumberFromBlockItem(localBundle, 0);
                    if (isBlock == 0) {
                        return true;
                    } else {
                        return false;
                    }

                } catch (RemoteException paramContext) {
                    paramContext.printStackTrace();
                    return false;

                } catch (Exception e){
                    e.printStackTrace();
                    return false;
                }
            }
            // / End

    /**
     * The onClickListener used to expand or collapse the action buttons section for a call log
     * entry.
     */
    private final View.OnClickListener mExpandCollapseListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final View callLogItem = (View) v.getParent().getParent();
            handleRowExpanded(callLogItem, true /* animate */, false /* forceExpand */);
        }
    };

    private AccessibilityDelegate mAccessibilityDelegate = new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(ViewGroup host, View child,
                AccessibilityEvent event) {
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                handleRowExpanded(host, false /* animate */,
                        true /* forceExpand */);
            }
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }
    };

    private void startActivityForAction(View view) {
        final IntentProvider intentProvider = (IntentProvider) view.getTag();
        if (intentProvider != null) {
            final Intent intent = intentProvider.getIntent(mContext);
            // See IntentProvider.getCallDetailIntentProvider() for why this may be null.
            if (intent != null) {
//            	处理拨出的号码 +57
            	String mData=intent.getDataString();
            	if(mData.startsWith("tel:")){
            		String number=mData.substring(4);
            		number=CallUtil.claroSpecialOperator(number, mContext);
            		intent.setData(Uri.parse("tel:"+number));
            	}
                DialerUtils.startActivityWithErrorToast(mContext, intent);
            }
        }
    }

    @Override
    public boolean onPreDraw() {
        // We only wanted to listen for the first draw (and this is it).
        unregisterPreDrawListener();

        // Only schedule a thread-creation message if the thread hasn't been
        // created yet. This is purely an optimization, to queue fewer messages.

        /// M:[Call Log Union Query] for ALPS01932653
        // avoid unnecessary messages enqueue
        if (!DialerFeatureOptions.CALL_LOG_UNION_QUERY && mCallerIdThread == null) {
            mHandler.sendEmptyMessageDelayed(START_THREAD, START_PROCESSING_REQUESTS_DELAY_MILLIS);
        }

        return true;
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REDRAW:
                    notifyDataSetChanged();
                    break;
                case START_THREAD:
                    startRequestProcessing();
                    break;
            }
        }
    };

    public CallLogAdapter(Context context, CallFetcher callFetcher,
            ContactInfoHelper contactInfoHelper, CallItemExpandedListener callItemExpandedListener,
            OnReportButtonClickListener onReportButtonClickListener, boolean isCallLog) {
        super(context);

        mContext = context;
        mCallFetcher = callFetcher;
        mContactInfoHelper = contactInfoHelper;
        mIsCallLog = isCallLog;
        mCallItemExpandedListener = callItemExpandedListener;

        mOnReportButtonClickListener = onReportButtonClickListener;
        mReportedToast = Toast.makeText(mContext, R.string.toast_caller_id_reported,
                Toast.LENGTH_SHORT);

        mContactInfoCache = ExpirableCache.create(CONTACT_INFO_CACHE_SIZE);
        mRequests = new LinkedList<ContactInfoRequest>();

        Resources resources = mContext.getResources();
        CallTypeHelper callTypeHelper = new CallTypeHelper(resources);
        mCallLogBackgroundColor = resources.getColor(R.color.background_dialer_list_items);
        mExpandedBackgroundColor = resources.getColor(R.color.call_log_expanded_background_color);
        mExpandedTranslationZ = resources.getDimension(R.dimen.call_log_expanded_translation_z);
        mPhotoSize = resources.getDimensionPixelSize(R.dimen.contact_photo_size);

        mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
        mPhoneNumberHelper = new PhoneNumberDisplayHelper(mContext, resources);
        mPhoneNumberUtilsWrapper = new PhoneNumberUtilsWrapper(mContext);
        PhoneCallDetailsHelper phoneCallDetailsHelper =
                new PhoneCallDetailsHelper(mContext, resources, mPhoneNumberUtilsWrapper);
        mCallLogViewsHelper =
                new CallLogListItemHelper(
                        phoneCallDetailsHelper, mPhoneNumberHelper, resources);
        mCallLogGroupBuilder = new CallLogGroupBuilder(this);

        //HQ_wuruijun add for HQ01359274 start
        CN.add("10086");
        CN.add("10010");
        CN.add("10000");
        //HQ_wuruijun add for HQ01359274 end


        // / Added by guofeiyao
        ContentResolver cr = mContext.getContentResolver();
        mCallLogQueryHandler = new CallLogQueryHandler(cr, this);

        harassmentIntercepterService = ServiceManager.getService(HARASSMENT_INTERCEPTION_SERVICE);
        hisStub = IHarassmentInterceptionService.Stub.asInterface(harassmentIntercepterService);
        // / End
        
        //added by modified by jinlibo for call history list scroll performance
        Resources resource = mContext.getResources();
        callLabelOne = resource.getDrawable(R.drawable.call_label_one);
        callLabelTwo = resource.getDrawable(R.drawable.call_label_two);
        //jinlibo add end

    }

    /**
     * Requery on background thread when {@link Cursor} changes.
     */
    @Override
    protected void onContentChanged() {
        mCallFetcher.fetchCalls();
    }

    public void setLoading(boolean loading) {
        mLoading = loading;
    }

    @Override
    public boolean isEmpty() {
        if (mLoading) {
            // We don't want the empty state to show when loading.
            return false;
        } else {
            return super.isEmpty();
        }
    }

    /**
     * Starts a background thread to process contact-lookup requests, unless one
     * has already been started.
     */
    private synchronized void startRequestProcessing() {
        // For unit-testing.
        if (mRequestProcessingDisabled) return;

        // Idempotence... if a thread is already started, don't start another.
        if (mCallerIdThread != null) return;

        mCallerIdThread = new QueryThread();
        mCallerIdThread.setPriority(Thread.MIN_PRIORITY);
        mCallerIdThread.start();
    }

    /**
     * Stops the background thread that processes updates and cancels any
     * pending requests to start it.
     */
    public synchronized void stopRequestProcessing() {
        // Remove any pending requests to start the processing thread.
        mHandler.removeMessages(START_THREAD);
        if (mCallerIdThread != null) {
            // Stop the thread; we are finished with it.
            mCallerIdThread.stopProcessing();
            mCallerIdThread.interrupt();
            mCallerIdThread = null;
        }
    }

    /**
     * Stop receiving onPreDraw() notifications.
     */
    private void unregisterPreDrawListener() {
        if (mViewTreeObserver != null && mViewTreeObserver.isAlive()) {
            mViewTreeObserver.removeOnPreDrawListener(this);
        }
        mViewTreeObserver = null;
    }

    public void invalidateCache() {
        mContactInfoCache.expireAll();

        // Restart the request-processing thread after the next draw.
        stopRequestProcessing();
        unregisterPreDrawListener();
    }

    /**
     * Enqueues a request to look up the contact details for the given phone number.
     * <p>
     * It also provides the current contact info stored in the call log for this number.
     * <p>
     * If the {@code immediate} parameter is true, it will start immediately the thread that looks
     * up the contact information (if it has not been already started). Otherwise, it will be
     * started with a delay. See {@link #START_PROCESSING_REQUESTS_DELAY_MILLIS}.
     */
    protected void enqueueRequest(String number, String countryIso, ContactInfo callLogInfo,
            boolean immediate) {
        ContactInfoRequest request = new ContactInfoRequest(number, countryIso, callLogInfo);
        synchronized (mRequests) {
            if (!mRequests.contains(request)) {
                mRequests.add(request);
                mRequests.notifyAll();
            }
        }
        if (immediate) startRequestProcessing();
    }

    /**
     * Queries the appropriate content provider for the contact associated with the number.
     * <p>
     * Upon completion it also updates the cache in the call log, if it is different from
     * {@code callLogInfo}.
     * <p>
     * The number might be either a SIP address or a phone number.
     * <p>
     * It returns true if it updated the content of the cache and we should therefore tell the
     * view to update its content.
     */
    private boolean queryContactInfo(String number, String countryIso, ContactInfo callLogInfo) {
        final ContactInfo info = mContactInfoHelper.lookupNumber(number, countryIso);

        if (info == null) {
            // The lookup failed, just return without requesting to update the view.
            return false;
        }

        // Check the existing entry in the cache: only if it has changed we should update the
        // view.
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
        ContactInfo existingInfo = mContactInfoCache.getPossiblyExpired(numberCountryIso);

        final boolean isRemoteSource = info.sourceType != 0;

        // Don't force redraw if existing info in the cache is equal to {@link ContactInfo#EMPTY}
        // to avoid updating the data set for every new row that is scrolled into view.
        // see (https://googleplex-android-review.git.corp.google.com/#/c/166680/)

        // Exception: Photo uris for contacts from remote sources are not cached in the call log
        // cache, so we have to force a redraw for these contacts regardless.
        boolean updated = (existingInfo != ContactInfo.EMPTY || isRemoteSource) &&
                !info.equals(existingInfo);

        // Store the data in the cache so that the UI thread can use to display it. Store it
        // even if it has not changed so that it is marked as not expired.
        mContactInfoCache.put(numberCountryIso, info);
        // Update the call log even if the cache it is up-to-date: it is possible that the cache
        // contains the value from a different call log entry.
        updateCallLogContactInfoCache(number, countryIso, info, callLogInfo);
        return updated;
    }

    /*
     * Handles requests for contact name and number type.
     */
    private class QueryThread extends Thread {
        private volatile boolean mDone = false;

        public QueryThread() {
            super("CallLogAdapter.QueryThread");
        }

        public void stopProcessing() {
            mDone = true;
        }

        @Override
        public void run() {
            boolean needRedraw = false;
            while (true) {
                // Check if thread is finished, and if so return immediately.
                if (mDone) return;

                // Obtain next request, if any is available.
                // Keep synchronized section small.
                ContactInfoRequest req = null;
                synchronized (mRequests) {
                    if (!mRequests.isEmpty()) {
                        req = mRequests.removeFirst();
                    }
                }

                if (req != null) {
                    // Process the request. If the lookup succeeds, schedule a
                    // redraw.
                    needRedraw |= queryContactInfo(req.number, req.countryIso, req.callLogInfo);
                } else {
                    // Throttle redraw rate by only sending them when there are
                    // more requests.
                    if (needRedraw) {
                        needRedraw = false;
                        mHandler.sendEmptyMessage(REDRAW);
                    }

                    // Wait until another request is available, or until this
                    // thread is no longer needed (as indicated by being
                    // interrupted).
                    try {
                        synchronized (mRequests) {
                            mRequests.wait(1000);
                        }
                    } catch (InterruptedException ie) {
                        // Ignore, and attempt to continue processing requests.
                    }
                }
            }
        }
    }

    @Override
    protected void addGroups(Cursor cursor) {
        mCallLogGroupBuilder.addGroups(cursor);
    }

    @Override
    protected View newStandAloneView(Context context, ViewGroup parent) {
        return newChildView(context, parent);
    }

    @Override
    protected View newGroupView(Context context, ViewGroup parent) {
        return newChildView(context, parent);
    }

    @Override
    protected View newChildView(Context context, ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.call_log_list_item, parent, false);

        // Get the views to bind to and cache them.
        
        // / Modified by guofeiyao
//        CallLogListItemViews views = CallLogListItemViews.fromView(view);
        CallLogListItemViews views = CallLogListItemViews.fromView(view, true);
		// / End
		
        view.setTag(views);

        // Set text height to false on the TextViews so they don't have extra padding.
        views.phoneCallDetailsViews.nameView.setElegantTextHeight(false);
        views.phoneCallDetailsViews.callLocationAndDate.setElegantTextHeight(false);
	//add by zhangjinqiang for al812 start
	
	if(InCallApp.gIsHwUi){
		View HW = inflater.inflate(R.layout.list_item_timeaxis, parent, false);
		 TimeAxisWidget taw = (TimeAxisWidget) HW.findViewById(R.id.item_date);
		 taw.setContent(view);
	 	return HW;
	}else{
		 return view;
	}
	//return view;
	//add by zhangjinqiang for al812 end 
    }

    @Override
    protected void bindStandAloneView(View view, Context context, Cursor cursor) {
        bindView(view, cursor, 1);
    }

    @Override
    protected void bindChildView(View view, Context context, Cursor cursor) {
        bindView(view, cursor, 1);
    }

    @Override
    protected void bindGroupView(View view, Context context, Cursor cursor, int groupSize,
            boolean expanded) {
        bindView(view, cursor, groupSize);
    }

    private void findAndCacheViews(View view) {
    }

    /**
     * Binds the views in the entry to the data in the call log.
     *
     * @param callLogItemView the view corresponding to this entry
     * @param c the cursor pointing to the entry in the call log
     * @param count the number of entries in the current item, greater than 1 if it is a group
     */
    protected void bindView(View callLogView, Cursor c, int count) {
	 //callLogItemView.setAccessibilityDelegate(mAccessibilityDelegate);

	 CallLogListItemViews views;
	 TimeAxisWidget taw=null;
	 View tView=null;

	if(InCallApp.gIsHwUi){
		 taw = (TimeAxisWidget)callLogView.findViewById(R.id.item_date);
		 tView = taw.getContent();
		 tView.setAccessibilityDelegate(mAccessibilityDelegate);
		  views = (CallLogListItemViews)tView.getTag();
	}
	else{
		callLogView.setAccessibilityDelegate(mAccessibilityDelegate);
		  views = (CallLogListItemViews) callLogView.getTag();
	}		 

        // Default case: an item in the call log.
        views.primaryActionView.setVisibility(View.VISIBLE);

        final String number = c.getString(CallLogQuery.NUMBER);
        final int numberPresentation = c.getInt(CallLogQuery.NUMBER_PRESENTATION);
        long date = c.getLong(CallLogQuery.DATE);
        final long duration = c.getLong(CallLogQuery.DURATION);
        final int callType = c.getInt(CallLogQuery.CALL_TYPE);
        final PhoneAccountHandle accountHandle = PhoneAccountUtils.getAccount(
                c.getString(CallLogQuery.ACCOUNT_COMPONENT_NAME),
                c.getString(CallLogQuery.ACCOUNT_ID));
        /// M: UI change
        //HQ_zhangjing modified for CQ HQ01400852
        //final String accountLabel = PhoneAccountUtils.getAccountLabel(mContext, accountHandle);

        final String countryIso = c.getString(CallLogQuery.COUNTRY_ISO);

        final long rowId = c.getLong(CallLogQuery.ID);
		final String location = c.getString(CallLogQuery.GEOCODED_LOCATION);
		final String subsriptionId = c.getString(CallLogQuery.ACCOUNT_ID);
        views.rowId = rowId;

        // For entries in the call log, check if the day group has changed and display a header
        // if necessary.
        if (mIsCallLog) {
			//xiatao add sim calllog label
			
			
			views.phoneCallDetailsViews.phoneLocation.setText(location);
			//removed by jinlibo for call history list scroll performance
//			Resources resource = mContext.getResources();

			/*HQ_xionghaifeng 20150815 modify for crash start*/
			int slotId = 0;
			if (subsriptionId != null && subsriptionId.length() != 0)
			{
				try	{
				    slotId = SubscriptionManager.getSlotId(Integer.parseInt(subsriptionId));
				} catch(NumberFormatException e) {
				    Log.i("xionghaifeng","CallLogAdapter NumberFormatException");
				}
			}
			/*HQ_xionghaifeng 20150815 modify for crash end*/
			
			//removed by jinlibo for call history list scroll performance
//			final Drawable callLabelOne = resource.getDrawable(R.drawable.call_label_one);
//			final Drawable callLabelTwo = resource.getDrawable(R.drawable.call_label_two);

            // / Modified by guofeiyao on 11.18.2015
            // For support single card phone
            if ( ((TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE)).isMultiSimEnabled() ) {
				
			
			getSubInfoList();
			if (mSubCount == 2){
			
			if(slotId == 0)
			  views.phoneCallDetailsViews.callLabelImage.setImageDrawable(callLabelOne);
		    else if (slotId == 1) 
			  views.phoneCallDetailsViews.callLabelImage.setImageDrawable(callLabelTwo);

			} else if (mSubCount == 1) {
			//HQ_wuruijun added start
			if(slotId == -1)
			  views.phoneCallDetailsViews.callLabelImage.setImageDrawable(null);
			//HQ_wuruijun added end
			if(slotId == 0)
			  views.phoneCallDetailsViews.callLabelImage.setImageDrawable(callLabelOne);
		    else if (slotId == 1) 
			  views.phoneCallDetailsViews.callLabelImage.setImageDrawable(callLabelTwo);
			//HQ_wuruijun added start
			} else if (mSubCount == 0) {
			if(slotId == -1)
			  views.phoneCallDetailsViews.callLabelImage.setImageDrawable(null);
			}
			//HQ_wuruijun added end
			// /end
			//-----xiatao end
			

            } else {
              views.phoneCallDetailsViews.callLabelImage.setImageDrawable(null);
			}
			// / End on 11.18.2015
			
            int currentGroup = getDayGroupForCall(rowId);
            int previousGroup = getPreviousDayGroup(c);
            if (currentGroup != previousGroup) {
		//modify by zhangjinqiang  for al812 start
                //views.dayGroupHeader.setVisibility(View.VISIBLE);
                //modify by zhangjinqiang for al812 end
                views.dayGroupHeader.setText(getGroupDescription(currentGroup));
            } else {
                views.dayGroupHeader.setVisibility(View.GONE);
            }
        } else {
            views.dayGroupHeader.setVisibility(View.GONE);
        }

        // Store some values used when the actions ViewStub is inflated on expansion of the actions
        // section.
        views.number = number;
        /// M: [VoLTE ConfCall] need reset confCallNumbers for new call log item
        views.confCallNumbers = null;
        views.numberPresentation = numberPresentation;
        views.callType = callType;
        views.accountHandle = accountHandle;
        views.voicemailUri = c.getString(CallLogQuery.VOICEMAIL_URI);
		
        // Stash away the Ids of the calls so that we can support deleting a row in the call log.
        views.callIds = getCallIds(c, count);

        final ContactInfo cachedContactInfo = getContactInfoFromCallLog(c);

        final boolean isVoicemailNumber =
                mPhoneNumberUtilsWrapper.isVoicemailNumber(accountHandle, number);

        // / Added by guofeiyao for ContextMenu when CallLogItem is LongClicked.
        DialCallback dialCallback = new DialCallback(views.number, false, rowId);
		
		// to make the listview which adapts to EMUI timeline show normal click effect
		/*
		if ( null != llContainer ) {
             llContainer.setTag(R.string.dialcallback_data, dialCallback);
		}
		*/
        // / End

        // Where binding and not in the call log, use default behaviour of invoking a call when
        // tapping the primary view.
        if (mIsCallLog) {
			
			// / Modified by guofeiyao
//            views.primaryActionView.setOnClickListener(this.mActionListener);

//            views.primaryActionView.setOnClickListener(dialCallback);
//            views.primaryActionView.setOnLongClickListener(dialCallback);
              views.primaryActionView.setTag(R.string.dialcallback_data, dialCallback);
			// / End
			
			phoneNumber = views.number;

            // Set return call intent, otherwise null.
            if (PhoneNumberUtilsWrapper.canPlaceCallsTo(number, numberPresentation)) {
                // Sets the primary action to call the number.
                if (isVoicemailNumber) {
                    views.primaryActionView.setTag(
                            IntentProvider.getReturnVoicemailCallIntentProvider());
                } else {
                    /// M: Supporting suggested account @{
                    if (DialerFeatureOptions.isSuggestedAccountSupport()) {
                        views.primaryActionView.setTag(IntentProvider.getSuggestedReturnCallIntentProvider(number, accountHandle));
                    /// @}
                    } else {
                        views.primaryActionView.setTag(
                                IntentProvider.getReturnCallIntentProvider(number));
                    }
                }
            } else {
                // Number is not callable, so hide button.
                views.primaryActionView.setTag(null);
            }
        } else {
            // In the call log, expand/collapse an actions section for the call log entry when
            // the primary view is tapped.

	//modify by zhangjinqiang for al812--start
           //views.primaryActionView.setOnClickListener(this.mExpandCollapseListener);
	//views.primaryActionView.setOnClickListener(this.mActionListener);
	//modify by zhangjinqiang for al812--end

            // Note: Binding of the action buttons is done as required in configureActionViews
            // when the user expands the actions ViewStub.
        }
        /// M: [Union Query] for MTK CallLog Query @{
        ContactInfo info = null;
        if (DialerFeatureOptions.CALL_LOG_UNION_QUERY) {
            info = ContactInfo.getContactInfofromCursor(c);
        } else {
            // Lookup contacts with this number
            NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
            ExpirableCache.CachedValue<ContactInfo> cachedInfo =
                    mContactInfoCache.getCachedValue(numberCountryIso);
            info = cachedInfo == null ? null : cachedInfo.getValue();
            if (!PhoneNumberUtilsWrapper.canPlaceCallsTo(number, numberPresentation)
                    || isVoicemailNumber) {
                // If this is a number that cannot be dialed, there is no point in looking up a contact
                // for it.
                info = ContactInfo.EMPTY;
            } else if (cachedInfo == null) {
                mContactInfoCache.put(numberCountryIso, ContactInfo.EMPTY);
                // Use the cached contact info from the call log.
                info = cachedContactInfo;
                // The db request should happen on a non-UI thread.
                // Request the contact details immediately since they are currently missing.
                enqueueRequest(number, countryIso, cachedContactInfo, true);
                // We will format the phone number when we make the background request.
            } else {
                if (cachedInfo.isExpired()) {
                    // The contact info is no longer up to date, we should request it. However, we
                    // do not need to request them immediately.
                    enqueueRequest(number, countryIso, cachedContactInfo, false);
                } else  if (!callLogInfoMatches(cachedContactInfo, info)) {
                    // The call log information does not match the one we have, look it up again.
                    // We could simply update the call log directly, but that needs to be done in a
                    // background thread, so it is easier to simply request a new lookup, which will, as
                    // a side-effect, update the call log.
                    enqueueRequest(number, countryIso, cachedContactInfo, false);
                }

                if (info == ContactInfo.EMPTY) {
                    // Use the cached contact info from the call log.
                    info = cachedContactInfo;
                }
            }
        }
        /// @}

        /// M: for plug-in @{
        ExtensionManager.getInstance().getCallLogExtension().bindViewPreForCallLogAdapter(mContext, info);
        /// @}

        final Uri lookupUri = info.lookupUri;
        String name = info.name;
        //modified by zhouyoukun for HQ01335481 begin
        /*
        if(null!=name || "122".equals(number)
        ||"110".equals(number)
		||"000".equals(number)
		||"08".equals(number)
		||"999".equals(number)
		||"118".equals(number)
		||"119".equals(number)
		||"122".equals(number)
		||"911".equals(number)
		|| CN.contains(number)
		){
        	views.phoneCallDetailsViews.phoneLocation.setText(number+"  "+location);
        }
        */
       // modified by zhouyoukun for HQ01335481 end

        // / Added by guofeiyao for ContextMenu when CallLogItem is LongClicked.
        if (null != name) {
            dialCallback.setName(name);
            dialCallback.setIsSaved(true);
            dialCallback.setContactUri(lookupUri);
        }
        // / End

        final int ntype = info.type;
        final String label = info.label;
        long photoId = info.photoId;
        final Uri photoUri = info.photoUri;
        CharSequence formattedNumber = info.formattedNumber == null
                ? null : PhoneNumberUtils.ttsSpanAsPhoneNumber(info.formattedNumber);
        int[] callTypes = getCallTypes(c, count);
        final String geocode = c.getString(CallLogQuery.GEOCODED_LOCATION);
        final int sourceType = info.sourceType;
        final int features = getCallFeatures(c, count);
        final String transcription = c.getString(CallLogQuery.TRANSCRIPTION);
        Long dataUsage = null;
        if (!c.isNull(CallLogQuery.DATA_USAGE)) {
            dataUsage = c.getLong(CallLogQuery.DATA_USAGE);
        }

        /// M: [VoLTE ConfCall] For Volte Conference Call @{
        long confCallId = -1;
        if (DialerFeatureOptions.isVolteEnhancedConfCallSupport()) {
            confCallId = c.getLong(CallLogQuery.CALLS_JOIN_DATA_VIEW_CONF_CALL_ID);
            Log.d(TAG, "Support VolteEnhancedConfCall and confCallId= " + confCallId);
        }
        if (confCallId > 0 && !mIsConfCallMemberList) {
            ArrayList<String> numbers = getConferenceCallNumbers(c, count);
            if (!mIsCallLog) {
                views.primaryActionView.setTag(IntentProvider
                        .getReturnVolteConfCallIntentProvider(numbers));
            }
            date = getConferenceCallDate(c, count);
            name = getConferenceCallName(c, count);
            views.confCallNumbers = numbers;
            photoId = R.drawable.ic_group_white_24dp;
            int firstCallType = callTypes[0];
            callTypes = new int[1];
            callTypes[0] = firstCallType;
            Log.d(TAG, "Volte ConfCall numbers= " + numbers + ", date=" + date + ", name=" + name);
        }
        /// @}

        /// M: [VoLTE] For Volte IMS Call @{
        else if (DialerFeatureOptions.isVolteCallSupport()
                && !mIsCallLog
                && PhoneNumberUtilsWrapper.isSipNumber(number)
                && PhoneAccountUtils.isSubScriptionAccount(mContext, accountHandle)) {
            views.primaryActionView.setTag(IntentProvider
                    .getReturnIMSCallIntentProvider(number));
        }
        /// @}

        final PhoneCallDetails details;

        views.reported = info.isBadData;

        // The entry can only be reported as invalid if it has a valid ID and the source of the
        // entry supports marking entries as invalid.
        views.canBeReportedAsInvalid = mContactInfoHelper.canReportAsInvalid(info.sourceType,
                info.objectId);

        // Restore expansion state of the row on rebind.  Inflate the actions ViewStub if required,
        // and set its visibility state accordingly.
        //add by zhangjinqiang for al812--start
        if(InCallApp.gIsHwUi){
		expandOrCollapseActions(tView, isExpanded(rowId));
	}else{
        		expandOrCollapseActions(callLogView, isExpanded(rowId));
		}
	//add by zhangjinqiang for al812 --end
        if (isVoicemailNumber) {
            name = mContext.getString(R.string.type_voicemail);
        }
	//add by zhangjinqiang for HQ01508475 at 20151207 -start
	if(number.equals("*611")
		&& android.os.SystemProperties.get("ro.hq.speed.three").equals("1")){
		TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
		String operatorNumeric = tm.getSimOperator();
		if (Arrays.asList(operatorName).contains(operatorNumeric)){
			name = mContext.getString(R.string.call_special_name);
		}else if(Arrays.asList(operatorNameAT).contains(operatorNumeric)){
			name = mContext.getString(R.string.call_special_name_at);
		}
	}
	//add by zhangjinqiang for HQ01508475 at 20151207 -end

		// / Revised by guofeiyao
		/*
		//add by HQ_xitao HQ01335986 start
        int[] mShowLastCallTypes = new int[1];
        mShowLastCallTypes[0] = callTypes[0];
       //add by HQ_xitao HQ01335986 end
       */
        if (TextUtils.isEmpty(name)) {
            details = new PhoneCallDetails(number, numberPresentation,
                    formattedNumber, countryIso, geocode, callTypes, date,
                    duration, accountHandle, features, dataUsage, transcription);
        } else {
            details = new PhoneCallDetails(number, numberPresentation,
                    formattedNumber, countryIso, geocode, callTypes, date,
                    duration, name, ntype, label, lookupUri, photoUri, sourceType,
                    accountHandle, features, dataUsage, transcription);
        }
		// / End

        /// M: [CallLog Search] Highlight the search text @{
        if (DialerFeatureOptions.CALL_LOG_SEARCH) {
            mCallLogViewsHelper.setHighlightedText(mUpperCaseQueryString);
        }
        /// @}

        mCallLogViewsHelper.setPhoneCallDetails(mContext, views, details);

        int contactType = ContactPhotoManager.TYPE_DEFAULT;

        if (isVoicemailNumber) {
            contactType = ContactPhotoManager.TYPE_VOICEMAIL;
        } else if (mContactInfoHelper.isBusiness(info.sourceType)) {
            contactType = ContactPhotoManager.TYPE_BUSINESS;
        }

        String lookupKey = lookupUri == null ? null
                : ContactInfoHelper.getLookupKeyFromUri(lookupUri);

        String nameForDefaultImage = null;
        if (TextUtils.isEmpty(name)) {
        	/// guoxiaolong for apr @{
            try {
				nameForDefaultImage = mPhoneNumberHelper.getDisplayNumber(details.accountHandle,
				        details.number, details.numberPresentation, details.formattedNumber).toString();
			} catch (NullPointerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            /// @}
        } else {
            nameForDefaultImage = name;
        }

        /** M: [Union Query] Just show a voicemail icon for voicemail contact photo. @{ */
        Uri contactLookupUri = lookupUri;
        String contactLookupkey = lookupKey;
        if (DialerFeatureOptions.CALL_LOG_UNION_QUERY) {
            /// M: [VoLTE ConfCall] Show Volte Conference Call contact icon @{
            if (confCallId > 0 && !mIsConfCallMemberList) {
                contactType = ContactPhotoManager.TYPE_CONFERENCE_CALL;
                contactLookupUri = null;
                contactLookupkey = null;
                photoId = 0;
            /// @}
            }
            /*HQ_sunli 20150824 HQ01331881 start*/
            /* else if (isVoicemailNumber) {
                contactLookupUri = null;
                contactLookupkey = null;
                photoId = 0;            
            }*/
            /*HQ_sunli 20150824 HQ01331881 end*/
              else if (contactLookupUri == null && !TextUtils.isEmpty(info.number)){
                //create a temp contact uri for quick contact view.
                contactLookupUri = ContactInfoHelper.createTemporaryContactUri(info.number);
            }
        }
        /** @} */
	//modify by zhangjinqiang for al812--start
	/*
        if (photoId == 0 && photoUri != null) {
            setPhoto(views, photoUri, contactLookupUri, nameForDefaultImage, contactLookupkey, contactType, info);
        } else {
            setPhoto(views, photoId, contactLookupUri, nameForDefaultImage, contactLookupkey, contactType, info);
        }
    */
     views.quickContactView.assignContactUri(contactLookupUri);
		
		// /added by guofeiyao
		if ( null != views.contactDetail && null != contactLookupUri) {
		     views.contactDetail.setOnClickListener(new CheatListener(contactLookupUri));
		} else {
             views.contactDetail.setOnClickListener(new CheatListener(null));
		}
        // /end
		
    views.quickContactView.setOverlay(null);
    //modify by zhangjinqiang for al812--end
        // Listen for the first draw
        if (mViewTreeObserver == null) {
			//add by zhangjinqiang for al812--start
			if(InCallApp.gIsHwUi){
				mViewTreeObserver = tView.getViewTreeObserver();
			}else{
				mViewTreeObserver = callLogView.getViewTreeObserver();
			}
			//add by zhangjinqiang for al812 --end
			
            //mViewTreeObserver = callLogView.getViewTreeObserver();
            mViewTreeObserver.addOnPreDrawListener(this);
        }

        /// M: for Plug-in @{
        //add by zhangjinqiang for al812--start
        ViewGroup actionView;
        if(InCallApp.gIsHwUi){
		actionView = (ViewGroup)tView.findViewById(R.id.primary_action_view);
	}else{
		actionView = (ViewGroup)callLogView.findViewById(R.id.primary_action_view);
	}
	//add by zhangjinqiang for al812--end
        //ViewGroup actionView = (ViewGroup)callLogView.findViewById(R.id.primary_action_view);
        ExtensionManager.getInstance().getRCSeCallLogExtension().bindPluginViewForCallLogList(
                mContext, actionView, number);

	if(InCallApp.gIsHwUi){
	ExtensionManager.getInstance().getCallLogExtension().setCallAccountForCallLogList(mContext, tView, accountHandle);
	}
	else{
        ExtensionManager.getInstance().getCallLogExtension().setCallAccountForCallLogList(mContext, callLogView, accountHandle);
		}
		/// @}

	if(InCallApp.gIsHwUi){
		Calendar cal = Calendar.getInstance();
	    	cal.setTimeInMillis(date);
	    	taw.setCalendar(cal);
			
		bindBadge(tView, info, details, callType);
	}else{
		bindBadge(callLogView, info, details, callType);
	}

        //bindBadge(callLogView, info, details, callType);
    }

    /**
     * Retrieves the day group of the previous call in the call log.  Used to determine if the day
     * group has changed and to trigger display of the day group text.
     *
     * @param cursor The call log cursor.
     * @return The previous day group, or DAY_GROUP_NONE if this is the first call.
     */
    private int getPreviousDayGroup(Cursor cursor) {
        // We want to restore the position in the cursor at the end.
        int startingPosition = cursor.getPosition();
        int dayGroup = CallLogGroupBuilder.DAY_GROUP_NONE;
        if (cursor.moveToPrevious()) {
            long previousRowId = cursor.getLong(CallLogQuery.ID);
            dayGroup = getDayGroupForCall(previousRowId);
        }
        cursor.moveToPosition(startingPosition);
        return dayGroup;
    }

    /**
     * Given a call Id, look up the day group that the call belongs to.  The day group data is
     * populated in {@link com.android.dialer.calllog.CallLogGroupBuilder}.
     *
     * @param callId The call to retrieve the day group for.
     * @return The day group for the call.
     */
    private int getDayGroupForCall(long callId) {
        if (mDayGroups.containsKey(callId)) {
            return mDayGroups.get(callId);
        }
        return CallLogGroupBuilder.DAY_GROUP_NONE;
    }
    /**
     * Determines if a call log row with the given Id is expanded.
     * @param rowId The row Id of the call.
     * @return True if the row should be expanded.
     */
    private boolean isExpanded(long rowId) {
        return mCurrentlyExpanded == rowId;
    }

    /**
     * Toggles the expansion state tracked for the call log row identified by rowId and returns
     * the new expansion state.  Assumes that only a single call log row will be expanded at any
     * one point and tracks the current and previous expanded item.
     *
     * @param rowId The row Id associated with the call log row to expand/collapse.
     * @return True where the row is now expanded, false otherwise.
     */
    private boolean toggleExpansion(long rowId) {
        if (rowId == mCurrentlyExpanded) {
            // Collapsing currently expanded row.
            mPreviouslyExpanded = NONE_EXPANDED;
            mCurrentlyExpanded = NONE_EXPANDED;

            return false;
        } else {
            // Expanding a row (collapsing current expanded one).

            mPreviouslyExpanded = mCurrentlyExpanded;
            mCurrentlyExpanded = rowId;
            return true;
        }
    }

    /**
     * Expands or collapses the view containing the CALLBACK/REDIAL, VOICEMAIL and DETAILS action
     * buttons.
     *
     * @param callLogItem The call log entry parent view.
     * @param isExpanded The new expansion state of the view.
     */
    private void expandOrCollapseActions(View callLogItem, boolean isExpanded) {
        final CallLogListItemViews views = (CallLogListItemViews)callLogItem.getTag();

        expandVoicemailTranscriptionView(views, isExpanded);
        if (isExpanded) {
            // Inflate the view stub if necessary, and wire up the event handlers.
            inflateActionViewStub(callLogItem);

            views.actionsView.setVisibility(View.VISIBLE);
            views.actionsView.setAlpha(1.0f);
			
			// / Annotated by guofeiyao
//            views.callLogEntryView.setBackgroundColor(mExpandedBackgroundColor);
            // / End

            views.callLogEntryView.setTranslationZ(mExpandedTranslationZ);
            callLogItem.setTranslationZ(mExpandedTranslationZ); // WAR
        } else {
            // When recycling a view, it is possible the actionsView ViewStub was previously
            // inflated so we should hide it in this case.
            if (views.actionsView != null) {
                views.actionsView.setVisibility(View.GONE);
            }

            // / Annotated by guofeiyao
//            views.callLogEntryView.setBackgroundColor(mCallLogBackgroundColor);
            // / End

            views.callLogEntryView.setTranslationZ(0);
            callLogItem.setTranslationZ(0); // WAR
        }
    }

    public static void expandVoicemailTranscriptionView(CallLogListItemViews views,
            boolean isExpanded) {
        if (views.callType != Calls.VOICEMAIL_TYPE) {
            return;
        }

        final TextView view = views.phoneCallDetailsViews.voicemailTranscriptionView;
        if (TextUtils.isEmpty(view.getText())) {
            return;
        }
        view.setMaxLines(isExpanded ? VOICEMAIL_TRANSCRIPTION_MAX_LINES : 1);
        view.setSingleLine(!isExpanded);
    }

    /**
     * Configures the action buttons in the expandable actions ViewStub.  The ViewStub is not
     * inflated during initial binding, so click handlers, tags and accessibility text must be set
     * here, if necessary.
     *
     * @param callLogItem The call log list item view.
     */
    private void inflateActionViewStub(final View callLogItem) {
        final CallLogListItemViews views = (CallLogListItemViews)callLogItem.getTag();

        ViewStub stub = (ViewStub)callLogItem.findViewById(R.id.call_log_entry_actions_stub);
        if (stub != null) {
            views.actionsView = (ViewGroup) stub.inflate();
        }

        if (views.callBackButtonView == null) {
            views.callBackButtonView = (TextView)views.actionsView.findViewById(
                    R.id.call_back_action);
        }

        /** M: [Ip Dial] add Ip Dial @{ */
        if (views.ipDialButtonView == null) {
            views.ipDialButtonView = (TextView)views.actionsView.findViewById(R.id.ipdial_action);
        }
        /** @} */

        if (views.videoCallButtonView == null) {
            views.videoCallButtonView = (TextView)views.actionsView.findViewById(
                    R.id.video_call_action);
        }

        if (views.voicemailButtonView == null) {
            views.voicemailButtonView = (TextView)views.actionsView.findViewById(
                    R.id.voicemail_action);
        }

        if (views.detailsButtonView == null) {
            views.detailsButtonView = (TextView)views.actionsView.findViewById(R.id.details_action);
        }

        if (views.reportButtonView == null) {
            views.reportButtonView = (TextView)views.actionsView.findViewById(R.id.report_action);
            views.reportButtonView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mOnReportButtonClickListener != null) {
                        mOnReportButtonClickListener.onReportButtonClick(views.number);
                    }
                }
            });
        }

        bindActionButtons(views);
    }

    /***
     * Binds text titles, click handlers and intents to the voicemail, details and callback action
     * buttons.
     *
     * @param views  The call log item views.
     */
    private void bindActionButtons(CallLogListItemViews views) {
        boolean canPlaceCallToNumber =
                PhoneNumberUtilsWrapper.canPlaceCallsTo(views.number, views.numberPresentation);
        // Set return call intent, otherwise null.
        if (canPlaceCallToNumber) {
            // Sets the primary action to call the number.
            boolean isVoicemailNumber =
                    mPhoneNumberUtilsWrapper.isVoicemailNumber(views.accountHandle, views.number);
            if (isVoicemailNumber) {
                // Make a general call to voicemail to ensure that if there are multiple accounts
                // it does not call the voicemail number of a specific phone account.
                views.callBackButtonView.setTag(
                        IntentProvider.getReturnVoicemailCallIntentProvider());
            } else {
                // Sets the primary action to call the number.
                /// M: Supporting suggested account @{
                if (DialerFeatureOptions.isSuggestedAccountSupport()) {
                    views.callBackButtonView.setTag(
                            IntentProvider.getSuggestedReturnCallIntentProvider(views.number, views.accountHandle));
                /// @}
                } else {
                    views.callBackButtonView.setTag(
                            IntentProvider.getReturnCallIntentProvider(views.number));
                }
            }
            views.callBackButtonView.setVisibility(View.VISIBLE);
            views.callBackButtonView.setOnClickListener(mActionListener);
            /** M: [Ip Dial] Add Ip Dial @{ */
            /// M: Support suggested account @{
            if (DialerFeatureOptions.isSuggestedAccountSupport()) {
                views.ipDialButtonView.setTag(
                        IntentProvider.getSuggestedIpDialCallIntentProvider(views.number, views.accountHandle));
            } else {
                views.ipDialButtonView.setTag(
                        IntentProvider.getIpDialCallIntentProvider(views.number));
            }
            /// @}

            if (!PhoneNumberHelper.isUriNumber(views.number)) {
                views.ipDialButtonView.setVisibility(View.VISIBLE);
            } else {
                views.ipDialButtonView.setVisibility(View.GONE);
            }
            views.ipDialButtonView.setOnClickListener(mActionListener);
            /** @} */
            /** M: [VoLTE ConfCall] For Volte Conference call @{ */
            if (views.confCallNumbers != null) {
                if (DialerVolteUtils.isVoLTEConfCallEnable(mContext)) {
                    views.callBackButtonView.setTag(IntentProvider
                            .getReturnVolteConfCallIntentProvider(views.confCallNumbers));
                } else {
                    views.callBackButtonView.setVisibility(View.GONE);
                }
                // Hide the ip dial button
                views.ipDialButtonView.setVisibility(View.GONE);
            }
            /** @} */
            /** M: [VoLTE] For Volte IMS call @{ */
            else if (PhoneNumberHelper.isUriNumber(views.number)
                    && PhoneAccountUtils.isSubScriptionAccount(mContext, views.accountHandle)) {
                views.callBackButtonView.setTag(
                        IntentProvider.getReturnIMSCallIntentProvider(views.number));
            }
            /** @} */

            final int titleId;
            if (views.callType == Calls.VOICEMAIL_TYPE || views.callType == Calls.OUTGOING_TYPE) {
                titleId = R.string.call_log_action_redial;
            } else {
                titleId = R.string.call_log_action_call_back;
            }
            views.callBackButtonView.setText(mContext.getString(titleId));
        } else {
            // Number is not callable, so hide button.
            views.callBackButtonView.setTag(null);
            views.callBackButtonView.setVisibility(View.GONE);
            /// M: [Ip Dial] add Ip Dial
            views.ipDialButtonView.setVisibility(View.GONE);
        }

        // If one of the calls had video capabilities, show the video call button.
        if (CallUtil.isVideoEnabled(mContext) && canPlaceCallToNumber &&
                views.phoneCallDetailsViews.callTypeIcons.isVideoShown()) {

            /// M: Supporting suggested account @{
            if (DialerFeatureOptions.isSuggestedAccountSupport()) {
                views.videoCallButtonView.setTag(
                        IntentProvider.getSuggestedReturnVideoCallIntentProvider(views.number, views.accountHandle));
            /// @}
            } else {
                views.videoCallButtonView.setTag(
                        IntentProvider.getReturnVideoCallIntentProvider(views.number));
            }

            views.videoCallButtonView.setVisibility(View.VISIBLE);
            views.videoCallButtonView.setOnClickListener(mActionListener);
        } else {
            views.videoCallButtonView.setTag(null);
            views.videoCallButtonView.setVisibility(View.GONE);
        }

        // For voicemail calls, show the "VOICEMAIL" action button; hide otherwise.
        if (views.callType == Calls.VOICEMAIL_TYPE) {
            views.voicemailButtonView.setOnClickListener(mActionListener);
            views.voicemailButtonView.setTag(
                    IntentProvider.getPlayVoicemailIntentProvider(
                            views.rowId, views.voicemailUri));
            views.voicemailButtonView.setVisibility(View.VISIBLE);

            views.detailsButtonView.setVisibility(View.GONE);
        } else {
            views.voicemailButtonView.setTag(null);
            views.voicemailButtonView.setVisibility(View.GONE);
            /// M: For ALPS01899718, explicitly set details button visibility
            views.detailsButtonView.setVisibility(View.VISIBLE);

            views.detailsButtonView.setOnClickListener(mActionListener);
            views.detailsButtonView.setTag(
                    IntentProvider.getCallDetailIntentProvider(
                            views.rowId, views.callIds, null)
            );
            /** M: [VoLTE ConfCall] For Volte Conference call @{ */
            if (views.confCallNumbers != null) {
                views.detailsButtonView.setTag(
                        IntentProvider.getCallDetailIntentProvider(
                                views.rowId, views.callIds, null, true));
            }
            /** @} */

            if (views.canBeReportedAsInvalid && !views.reported) {
                views.reportButtonView.setVisibility(View.VISIBLE);
            } else {
                views.reportButtonView.setVisibility(View.GONE);
            }
        }

        mCallLogViewsHelper.setActionContentDescriptions(views);
    }

    protected void bindBadge(
            View view, final ContactInfo info, final PhoneCallDetails details, int callType) {
        // Do not show badge in call log.
        if (!mIsCallLog) {
            final ViewStub stub = (ViewStub) view.findViewById(R.id.link_stub);
            if (UriUtils.isEncodedContactUri(info.lookupUri)) {
                if (stub != null) {
                    mBadgeContainer = stub.inflate();
                } else {
                    mBadgeContainer = view.findViewById(R.id.badge_container);
                }

                mBadgeContainer.setVisibility(View.VISIBLE);
                mBadgeImageView = (ImageView) mBadgeContainer.findViewById(R.id.badge_image);
                mBadgeText = (TextView) mBadgeContainer.findViewById(R.id.badge_text);

                final View clickableArea = mBadgeContainer.findViewById(R.id.badge_link_container);
                if (clickableArea != null) {
                    clickableArea.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // If no lookup uri is provided, we need to rely on what information
                            // we have available; namely the phone number and name.
                            if (info.lookupUri == null) {
                                final Intent intent =
                                        DialtactsActivity.getAddToContactIntent(details.name,
                                                details.number,
                                                details.numberType);
                                DialerUtils.startActivityWithErrorToast(mContext, intent,
                                        R.string.add_contact_not_available);
                            } else {
                                addContactFromLookupUri(info.lookupUri);
                            }
                        }
                    });
                }
                mBadgeImageView.setImageResource(R.drawable.ic_person_add_24dp);
                mBadgeText.setText(R.string.recentCalls_addToContact);
            } else {
                // Hide badge if it was previously shown.
                mBadgeContainer = view.findViewById(R.id.badge_container);
                if (mBadgeContainer != null) {
                    mBadgeContainer.setVisibility(View.GONE);
                }
            }
        }
    }

    /** Checks whether the contact info from the call log matches the one from the contacts db. */
    private boolean callLogInfoMatches(ContactInfo callLogInfo, ContactInfo info) {
        // The call log only contains a subset of the fields in the contacts db.
        // Only check those.
        return TextUtils.equals(callLogInfo.name, info.name)
                && callLogInfo.type == info.type
                && TextUtils.equals(callLogInfo.label, info.label);
    }

    /** Stores the updated contact info in the call log if it is different from the current one. */
    private void updateCallLogContactInfoCache(String number, String countryIso,
            ContactInfo updatedInfo, ContactInfo callLogInfo) {
        final ContentValues values = new ContentValues();
        boolean needsUpdate = false;

        if (callLogInfo != null) {
            if (!TextUtils.equals(updatedInfo.name, callLogInfo.name)) {
                values.put(Calls.CACHED_NAME, updatedInfo.name);
                needsUpdate = true;
            }

            if (updatedInfo.type != callLogInfo.type) {
                values.put(Calls.CACHED_NUMBER_TYPE, updatedInfo.type);
                needsUpdate = true;
            }

            if (!TextUtils.equals(updatedInfo.label, callLogInfo.label)) {
                values.put(Calls.CACHED_NUMBER_LABEL, updatedInfo.label);
                needsUpdate = true;
            }
            if (!UriUtils.areEqual(updatedInfo.lookupUri, callLogInfo.lookupUri)) {
                values.put(Calls.CACHED_LOOKUP_URI, UriUtils.uriToString(updatedInfo.lookupUri));
                needsUpdate = true;
            }
            // Only replace the normalized number if the new updated normalized number isn't empty.
            if (!TextUtils.isEmpty(updatedInfo.normalizedNumber) &&
                    !TextUtils.equals(updatedInfo.normalizedNumber, callLogInfo.normalizedNumber)) {
                values.put(Calls.CACHED_NORMALIZED_NUMBER, updatedInfo.normalizedNumber);
                needsUpdate = true;
            }
            if (!TextUtils.equals(updatedInfo.number, callLogInfo.number)) {
                values.put(Calls.CACHED_MATCHED_NUMBER, updatedInfo.number);
                needsUpdate = true;
            }
            if (updatedInfo.photoId != callLogInfo.photoId) {
                values.put(Calls.CACHED_PHOTO_ID, updatedInfo.photoId);
                needsUpdate = true;
            }
            if (!TextUtils.equals(updatedInfo.formattedNumber, callLogInfo.formattedNumber)) {
                values.put(Calls.CACHED_FORMATTED_NUMBER, updatedInfo.formattedNumber);
                needsUpdate = true;
            }
        } else {
            // No previous values, store all of them.
            values.put(Calls.CACHED_NAME, updatedInfo.name);
            values.put(Calls.CACHED_NUMBER_TYPE, updatedInfo.type);
            values.put(Calls.CACHED_NUMBER_LABEL, updatedInfo.label);
            values.put(Calls.CACHED_LOOKUP_URI, UriUtils.uriToString(updatedInfo.lookupUri));
            values.put(Calls.CACHED_MATCHED_NUMBER, updatedInfo.number);
            values.put(Calls.CACHED_NORMALIZED_NUMBER, updatedInfo.normalizedNumber);
            values.put(Calls.CACHED_PHOTO_ID, updatedInfo.photoId);
            values.put(Calls.CACHED_FORMATTED_NUMBER, updatedInfo.formattedNumber);
            needsUpdate = true;
        }

        if (!needsUpdate) return;

        try {
            if (countryIso == null) {
                mContext.getContentResolver().update(Calls.CONTENT_URI_WITH_VOICEMAIL, values,
                        Calls.NUMBER + " = ? AND " + Calls.COUNTRY_ISO + " IS NULL",
                        new String[]{ number });
            } else {
                mContext.getContentResolver().update(Calls.CONTENT_URI_WITH_VOICEMAIL, values,
                        Calls.NUMBER + " = ? AND " + Calls.COUNTRY_ISO + " = ?",
                        new String[]{ number, countryIso });
            }
        } catch (SQLiteFullException e) {
            Log.e(TAG, "Unable to update contact info in call log db", e);
        }
    }

    /** Returns the contact information as stored in the call log. */
    private ContactInfo getContactInfoFromCallLog(Cursor c) {
        ContactInfo info = new ContactInfo();
        info.lookupUri = UriUtils.parseUriOrNull(c.getString(CallLogQuery.CACHED_LOOKUP_URI));
        info.name = c.getString(CallLogQuery.CACHED_NAME);
        info.type = c.getInt(CallLogQuery.CACHED_NUMBER_TYPE);
        info.label = c.getString(CallLogQuery.CACHED_NUMBER_LABEL);
        String matchedNumber = c.getString(CallLogQuery.CACHED_MATCHED_NUMBER);
        info.number = matchedNumber == null ? c.getString(CallLogQuery.NUMBER) : matchedNumber;
        info.normalizedNumber = c.getString(CallLogQuery.CACHED_NORMALIZED_NUMBER);
        info.photoId = c.getLong(CallLogQuery.CACHED_PHOTO_ID);
        info.photoUri = null;  // We do not cache the photo URI.
        info.formattedNumber = c.getString(CallLogQuery.CACHED_FORMATTED_NUMBER);
        return info;
    }

    /**
     * Returns the call types for the given number of items in the cursor.
     * <p>
     * It uses the next {@code count} rows in the cursor to extract the types.
     * <p>
     * It position in the cursor is unchanged by this function.
     */
    private int[] getCallTypes(Cursor cursor, int count) {
        int position = cursor.getPosition();
        int[] callTypes = new int[count];
        for (int index = 0; index < count; ++index) {
            callTypes[index] = cursor.getInt(CallLogQuery.CALL_TYPE);
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return callTypes;
    }

    /**
     * Determine the features which were enabled for any of the calls that make up a call log
     * entry.
     *
     * @param cursor The cursor.
     * @param count The number of calls for the current call log entry.
     * @return The features.
     */
    private int getCallFeatures(Cursor cursor, int count) {
        int features = 0;
        int position = cursor.getPosition();
        for (int index = 0; index < count; ++index) {
            features |= cursor.getInt(CallLogQuery.FEATURES);
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return features;
    }

    private void setPhoto(CallLogListItemViews views, long photoId, Uri contactUri,
            String displayName, String identifier, int contactType, ContactInfo info) {
        views.quickContactView.assignContactUri(contactUri);
        views.quickContactView.setOverlay(null);
        DefaultImageRequest request = new DefaultImageRequest(displayName, identifier,
                contactType, true /* isCircular */);

        if (info.contactSimId > 0) {
            request.subId = info.contactSimId;
            request.photoId = info.isSdnContact;
        }
        mContactPhotoManager.loadThumbnail(views.quickContactView, photoId, false /* darkTheme */,
                true /* isCircular */, request);
    }

    private void setPhoto(CallLogListItemViews views, Uri photoUri, Uri contactUri,
            String displayName, String identifier, int contactType, ContactInfo info) {
        views.quickContactView.assignContactUri(contactUri);
        views.quickContactView.setOverlay(null);
        DefaultImageRequest request = new DefaultImageRequest(displayName, identifier,
                contactType, true /* isCircular */);

        if (info.contactSimId > 0) {
            request.subId = info.contactSimId;
            request.photoId = info.isSdnContact;
        }
        mContactPhotoManager.loadPhoto(views.quickContactView, photoUri, mPhotoSize,
                false /* darkTheme */, true /* isCircular */, request);
    }

    /**
     * Bind a call log entry view for testing purposes.  Also inflates the action view stub so
     * unit tests can access the buttons contained within.
     *
     * @param view The current call log row.
     * @param context The current context.
     * @param cursor The cursor to bind from.
     */
    @VisibleForTesting
    void bindViewForTest(View view, Context context, Cursor cursor) {
        bindStandAloneView(view, context, cursor);
        inflateActionViewStub(view);
    }

    /**
     * Sets whether processing of requests for contact details should be enabled.
     * <p>
     * This method should be called in tests to disable such processing of requests when not
     * needed.
     */
    @VisibleForTesting
    void disableRequestProcessingForTest() {
        mRequestProcessingDisabled = true;
    }

    @VisibleForTesting
    void injectContactInfoForTest(String number, String countryIso, ContactInfo contactInfo) {
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
        mContactInfoCache.put(numberCountryIso, contactInfo);
    }

    @Override
    public void addGroup(int cursorPosition, int size, boolean expanded) {
        super.addGroup(cursorPosition, size, expanded);
    }

    /**
     * Stores the day group associated with a call in the call log.
     *
     * @param rowId The row Id of the current call.
     * @param dayGroup The day group the call belongs in.
     */
    @Override
    public void setDayGroup(long rowId, int dayGroup) {
        if (!mDayGroups.containsKey(rowId)) {
            mDayGroups.put(rowId, dayGroup);
        }
    }

    /**
     * Clears the day group associations on re-bind of the call log.
     */
    @Override
    public void clearDayGroups() {
        mDayGroups.clear();
    }

    /*
     * Get the number from the Contacts, if available, since sometimes
     * the number provided by caller id may not be formatted properly
     * depending on the carrier (roaming) in use at the time of the
     * incoming call.
     * Logic : If the caller-id number starts with a "+", use it
     *         Else if the number in the contacts starts with a "+", use that one
     *         Else if the number in the contacts is longer, use that one
     */
    public String getBetterNumberFromContacts(String number, String countryIso) {
        String matchingNumber = null;
        // Look in the cache first. If it's not found then query the Phones db
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
        ContactInfo ci = mContactInfoCache.getPossiblyExpired(numberCountryIso);
        if (ci != null && ci != ContactInfo.EMPTY) {
            matchingNumber = ci.number;
        } else {
            try {
                Cursor phonesCursor = mContext.getContentResolver().query(
                        Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, number),
                        PhoneQuery._PROJECTION, null, null, null);
                if (phonesCursor != null) {
                    try {
                        if (phonesCursor.moveToFirst()) {
                            matchingNumber = phonesCursor.getString(PhoneQuery.MATCHED_NUMBER);
                        }
                    } finally {
                        phonesCursor.close();
                    }
                }
            } catch (Exception e) {
                // Use the number from the call log
            }
        }
        if (!TextUtils.isEmpty(matchingNumber) &&
                (matchingNumber.startsWith("+")
                        || matchingNumber.length() > number.length())) {
            number = matchingNumber;
        }
        return number;
    }

    /**
     * Retrieves the call Ids represented by the current call log row.
     *
     * @param cursor Call log cursor to retrieve call Ids from.
     * @param groupSize Number of calls associated with the current call log row.
     * @return Array of call Ids.
     */
    private long[] getCallIds(final Cursor cursor, final int groupSize) {
        // We want to restore the position in the cursor at the end.
        int startingPosition = cursor.getPosition();
        long[] ids = new long[groupSize];
        // Copy the ids of the rows in the group.
        for (int index = 0; index < groupSize; ++index) {
            ids[index] = cursor.getLong(CallLogQuery.ID);
            cursor.moveToNext();
        }
        cursor.moveToPosition(startingPosition);
        return ids;
    }

    /**
     * Determines the description for a day group.
     *
     * @param group The day group to retrieve the description for.
     * @return The day group description.
     */
    private CharSequence getGroupDescription(int group) {
       if (group == CallLogGroupBuilder.DAY_GROUP_TODAY) {
           return mContext.getResources().getString(R.string.call_log_header_today);
       } else if (group == CallLogGroupBuilder.DAY_GROUP_YESTERDAY) {
           return mContext.getResources().getString(R.string.call_log_header_yesterday);
       } else {
           return mContext.getResources().getString(R.string.call_log_header_other);
       }
    }

    public void onBadDataReported(String number) {
        mContactInfoCache.expireAll();
        mReportedToast.show();
    }

    /**
     * Manages the state changes for the UI interaction where a call log row is expanded.
     *
     * @param view The view that was tapped
     * @param animate Whether or not to animate the expansion/collapse
     * @param forceExpand Whether or not to force the call log row into an expanded state regardless
     *        of its previous state
     */
    private void handleRowExpanded(View view, boolean animate, boolean forceExpand) {
        final CallLogListItemViews views = (CallLogListItemViews) view.getTag();

        if (forceExpand && isExpanded(views.rowId)) {
            return;
        }

        // Hide or show the actions view.
        boolean expanded = toggleExpansion(views.rowId);

        // Trigger loading of the viewstub and visual expand or collapse.
        expandOrCollapseActions(view, expanded);

        // Animate the expansion or collapse.
        if (mCallItemExpandedListener != null) {
            if (animate) {
                mCallItemExpandedListener.onItemExpanded(view);
            }

            // Animate the collapse of the previous item if it is still visible on screen.
            if (mPreviouslyExpanded != NONE_EXPANDED) {
                View previousItem = mCallItemExpandedListener.getViewForCallId(
                        mPreviouslyExpanded);

                if (previousItem != null) {
                    expandOrCollapseActions(previousItem, false);
                    if (animate) {
                        mCallItemExpandedListener.onItemExpanded(previousItem);
                    }
                }
                mPreviouslyExpanded = NONE_EXPANDED;
            }
        }
    }

    /**
     * Invokes the "add contact" activity given the expanded contact information stored in a
     * lookup URI.  This can include, for example, address and website information.
     *
     * @param lookupUri The lookup URI.
     */
    private void addContactFromLookupUri(Uri lookupUri) {
        Contact contactToSave = ContactLoader.parseEncodedContactEntity(lookupUri);
        if (contactToSave == null) {
            return;
        }

        // Note: This code mirrors code in Contacts/QuickContactsActivity.
        final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);

        ArrayList<ContentValues> values = contactToSave.getContentValues();
        // Only pre-fill the name field if the provided display name is an nickname
        // or better (e.g. structured name, nickname)
        if (contactToSave.getDisplayNameSource()
                >= ContactsContract.DisplayNameSources.NICKNAME) {
            intent.putExtra(ContactsContract.Intents.Insert.NAME,
                    contactToSave.getDisplayName());
        } else if (contactToSave.getDisplayNameSource()
                == ContactsContract.DisplayNameSources.ORGANIZATION) {
            // This is probably an organization. Instead of copying the organization
            // name into a name entry, copy it into the organization entry. This
            // way we will still consider the contact an organization.
            final ContentValues organization = new ContentValues();
            organization.put(ContactsContract.CommonDataKinds.Organization.COMPANY,
                    contactToSave.getDisplayName());
            organization.put(ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE);
            values.add(organization);
        }

        // Last time used and times used are aggregated values from the usage stat
        // table. They need to be removed from data values so the SQL table can insert
        // properly
        for (ContentValues value : values) {
            value.remove(ContactsContract.Data.LAST_TIME_USED);
            value.remove(ContactsContract.Data.TIMES_USED);
        }
        intent.putExtra(ContactsContract.Intents.Insert.DATA, values);

        DialerUtils.startActivityWithErrorToast(mContext, intent,
                R.string.add_contact_not_available);
    }

    /// -----------------------------------Mediatek----------------------------

    /// M: [CallLog Search] New Feature CallLogSearch @{
    private char[] mUpperCaseQueryString;

    // Add for call log search feature
    public void setQueryString(String queryString) {
        if (TextUtils.isEmpty(queryString)) {
            mUpperCaseQueryString = null;
        } else {
            mUpperCaseQueryString = queryString.toUpperCase().toCharArray();
        }
    }
    /// @}

    /// M: [VoLTE ConfCall] For volte conference call @{
    private boolean mIsConfCallMemberList = false;
    /**
     * Is this adapter used to show the conference call member list
     */
    public void setIsConfCallMemberList(boolean isConfCallMemberList) {
        mIsConfCallMemberList = isConfCallMemberList;
    }

    private ArrayList<String> getConferenceCallNumbers(Cursor cursor, int count) {
        int position = cursor.getPosition();
        ArrayList<String> numbers = new ArrayList<String>(count);
        for (int index = 0; index < count; ++index) {
            numbers.add(cursor.getString(CallLogQuery.NUMBER));
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return numbers;
    }

    private long getConferenceCallDate(Cursor cursor, int count) {
        int position = cursor.getPosition();
        long minDate = cursor.getLong(CallLogQuery.DATE);
        for (int index = 1; index < count; ++index) {
            cursor.moveToNext();
            long date = cursor.getLong(CallLogQuery.DATE);
            if (minDate > date) {
                minDate = date;
            }
        }
        cursor.moveToPosition(position);
        return minDate;
    }

    private String getConferenceCallName(Cursor cursor, int count) {
        int position = cursor.getPosition();
        ArrayList<CharSequence> names = new ArrayList<CharSequence>(count);
        for (int index = 0; index < count; ++index) {
            String name = cursor.getString(CallLogQuery.CALLS_JOIN_DATA_VIEW_DISPLAY_NAME);
            if (TextUtils.isEmpty(name)) {
                names.add(cursor.getString(CallLogQuery.NUMBER));
            } else {
                names.add(name);
            }
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return DialerUtils.join(mContext.getResources(), names).toString();
    }
    /// @}

	//add by zhangjinqiang for al812--start

	private List<SubscriptionInfo> mSubInfoList;
	private int mSubCount;
	private void getSubInfoList() {
        mSubInfoList = SubscriptionManager.from(mContext).getActiveSubscriptionInfoList();
        mSubCount = (mSubInfoList != null && !mSubInfoList.isEmpty()) ? mSubInfoList.size() : 0;
    }

	private void showDialog(final String number){	
		final AlertDialog alertDialog = new AlertDialog.Builder(mContext).create();

        // / Added by guofeiyao
        dialDialog = alertDialog;
        // / End
		
		alertDialog.show();
		Window window = alertDialog.getWindow(); 
		window.setContentView(R.layout.call_alert_dialog);  
		String sim1 = mSubInfoList.get(0).getDisplayName().toString();
		Button btnOne = (Button)window.findViewById(R.id.btn_sim_one);
		btnOne.setText(sim1);
		btnOne.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				 Intent intent = CallUtil.getCallIntent(number,
                            (mContext instanceof PeopleActivity?
                                    ((PeopleActivity) mContext).getDialerPlugIn().getCallOrigin() : null));
				//Settings.System.putInt(mContext.getContentResolver(), "slot_id", 0);
                                intent.putExtra("slot_id",0);
                 DialerUtils.startActivityWithErrorToast(mContext, intent);
				alertDialog.dismiss();
			}
		});
		
		String sim2 = mSubInfoList.get(1).getDisplayName().toString();
		Button btnTwo = (Button)window.findViewById(R.id.btn_sim_two);
		btnTwo.setText(sim2);
		btnTwo.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				Intent intent = CallUtil.getCallIntent(number,
                            (mContext instanceof PeopleActivity?
                                    ((PeopleActivity) mContext).getDialerPlugIn().getCallOrigin() : null));
				//Settings.System.putInt(mContext.getContentResolver(), "slot_id", 1);
                                intent.putExtra("slot_id",1);
                DialerUtils.startActivityWithErrorToast(mContext, intent);
				alertDialog.dismiss();
			}
		});
		

	}

	//add by zhangjinqiang for display SIM2 only exist SIM2 --start
	public boolean checkSimPosition(){
	    boolean isAirplaneModeOn = android.provider.Settings.System.getInt(mContext.getContentResolver(),  
                  android.provider.Settings.System.AIRPLANE_MODE_ON, 0) != 0;
		if (1 == mSubCount && !isAirplaneModeOn) {
			int subsriptionId = mSubInfoList.get(0).getSubscriptionId();
			int slotId = SubscriptionManager.getSlotId(subsriptionId);
            if (slotId == 1) {
                 return false;//only exitst SIM2
			}else{
				return true;
			}
		}else{
			return true;
		}
	}
	//add by zhangjinqiang end

	//add by zhangjinqiang for al812--end
}
