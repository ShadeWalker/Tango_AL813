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

package com.android.dialer.dialpad;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ChildMode;//add by lihaizhou for forbid dia under ChildMode at 2015-07-28
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.PhonesColumns;
import android.provider.CallLog;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.util.Constants;
import com.android.contacts.common.util.PhoneNumberFormatter;
import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.contacts.common.util.StopWatch;
import com.android.contacts.common.widget.FloatingActionButtonController;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.NeededForReflection;
import com.android.dialer.R;
import com.android.dialer.SpecialCharSequenceMgr;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.calllog.PhoneAccountUtils;
import com.android.ims.ImsManager;
import com.android.internal.telephony.PhoneConstants;
import com.android.phone.common.CallLogAsync;
import com.android.phone.common.HapticFeedback;
import com.android.phone.common.animation.AnimUtils;
import com.android.phone.common.dialpad.DialpadKeyButton;
import com.android.phone.common.dialpad.DialpadView;

import com.google.common.annotations.VisibleForTesting;
import com.mediatek.common.audioprofile.AudioProfileListener;
import com.mediatek.dialer.ext.ExtensionManager;
import com.mediatek.dialer.ext.IDialPadExtension.DialpadExtensionAction;
import com.mediatek.dialer.util.DialerVolteUtils;
import com.mediatek.ims.WfcReasonInfo;
import com.mediatek.telecom.TelecomManagerEx;
import com.mediatek.internal.telephony.ITelephonyEx;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

// / Added by guofeiyao
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.view.Window;
import android.widget.Button;

import com.android.internal.telephony.ITelephony;
import com.android.incallui.Call;
import com.android.incallui.CallList;
import com.android.contacts.activities.PeopleActivity;

import static com.android.internal.telephony.PhoneConstants.SUBSCRIPTION_KEY;

import java.util.ArrayList;
import com.cootek.smartdialer_plugin_oem.CooTekSmartdialerOemModule;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.speeddial.SpeedDialPlugin;
// / End

//hq_hxb modified by	for  HQ01308688 begin
import com.mediatek.contacts.simcontact.SlotUtils;
//hq_hxb modified by	for  HQ01308688 end
import java.util.Arrays;


/**
 * Fragment that displays a twelve-key phone dialpad.
 */
public class DialpadFragment extends Fragment
        implements View.OnClickListener,
        View.OnLongClickListener, View.OnKeyListener,
        AdapterView.OnItemClickListener, TextWatcher,
        PopupMenu.OnMenuItemClickListener, PopupMenu.OnDismissListener,
        DialpadKeyButton.OnPressedListener, DialpadExtensionAction {
    private static final String TAG = DialpadFragment.class.getSimpleName();

    /**
     * LinearLayout with getter and setter methods for the translationY property using floats,
     * for animation purposes.
     */
    public static class DialpadSlidingRelativeLayout extends RelativeLayout {

        public DialpadSlidingRelativeLayout(Context context) {
            super(context);
        }

        public DialpadSlidingRelativeLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public DialpadSlidingRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @NeededForReflection
        public float getYFraction() {
            final int height = getHeight();
            if (height == 0) return 0;
            return getTranslationY() / height;
        }

        @NeededForReflection
        public void setYFraction(float yFraction) {
            setTranslationY(yFraction * getHeight());
        }
    }

    public interface OnDialpadQueryChangedListener {
        void onDialpadQueryChanged(String query);
    }

    private static final boolean DEBUG = DialtactsActivity.DEBUG;

    // This is the amount of screen the dialpad fragment takes up when fully displayed
    private static final float DIALPAD_SLIDE_FRACTION = 0.67f;

    private static final String EMPTY_NUMBER = "";
    private static final char PAUSE = ',';
    private static final char WAIT = ';';

    /** The length of DTMF tones in milliseconds */
    private static final int TONE_LENGTH_MS = 150;
    private static final int TONE_LENGTH_INFINITE = -1;

    /** The DTMF tone volume relative to other sounds in the stream */
    private static final int TONE_RELATIVE_VOLUME = 80;

    /** Stream type used to play the DTMF tones off call, and mapped to the volume control keys */
    private static final int DIAL_TONE_STREAM_TYPE = AudioManager.STREAM_DTMF;

    private OnDialpadQueryChangedListener mDialpadQueryListener;

    private DialpadView mDialpadView;
    private EditText mDigits;
    private int mDialpadSlideInDuration;

    /** Remembers if we need to clear digits field when the screen is completely gone. */
    private boolean mClearDigitsOnStop;

    private View mOverflowMenuButton;
    private PopupMenu mOverflowPopupMenu;
    private View mDelete;
    private ToneGenerator mToneGenerator;
    private final Object mToneGeneratorLock = new Object();
    private View mSpacer;

    private FloatingActionButtonController mFloatingActionButtonController;

    ///M:  WFC @{
    private static final String SCHEME_TEL = PhoneAccount.SCHEME_TEL;
    private static final int DIALPAD_WFC_NOTIFICATION_ID = 2;
    private Context mContext;
    private int mNotiCount;
    private Timer mNotifTimer;
    private NotificationManager mNotificationManager;
    /// @}
    /**
     * Set of dialpad keys that are currently being pressed
     */
    private final HashSet<View> mPressedDialpadKeys = new HashSet<View>(12);
    //HQ_hushunli 2015-10-22 add for HQ01303116 begin
    private String mTextStr = "";
    private int index;
    private final Map mArLangNummap = new HashMap() {{
        put(0, "۰");
        put(1, "١");
        put(2, "٢");
        put(3, "٣");
        put(4, "٤");
        put(5, "٥");
        put(6, "٦");
        put(7, "٧");
        put(8, "٨");
        put(9, "٩");
    }};

    private final Map mFaLangNummap = new HashMap() {{
        put(0, "۰");
        put(1, "١");
        put(2, "٢");
        put(3, "٣");
        put(4, "۴");
        put(5, "۵");
        put(6, "۶");
        put(7, "٧");
        put(8, "٨");
        put(9, "٩");
    }};
    //HQ_hushunli 2015-10-22 add for HQ01303116 end
    private ListView mDialpadChooser;
    private DialpadChooserAdapter mDialpadChooserAdapter;

    /**
     * Regular expression prohibiting manual phone call. Can be empty, which means "no rule".
     */
    private String mProhibitedPhoneNumberRegexp;


    // Last number dialed, retrieved asynchronously from the call DB
    // in onCreate. This number is displayed when the user hits the
    // send key and cleared in onPause.
    private final CallLogAsync mCallLog = new CallLogAsync();
    private String mLastNumberDialed = EMPTY_NUMBER;

    // determines if we want to playback local DTMF tones.
    private boolean mDTMFToneEnabled;

    // Vibration (haptic feedback) for dialer key presses.
    private final HapticFeedback mHaptic = new HapticFeedback();

    /** Identifier for the "Add Call" intent extra. */
    private static final String ADD_CALL_MODE_KEY = "add_call_mode";

    /**
     * Identifier for intent extra for sending an empty Flash message for
     * CDMA networks. This message is used by the network to simulate a
     * press/depress of the "hookswitch" of a landline phone. Aka "empty flash".
     *
     * TODO: Using an intent extra to tell the phone to send this flash is a
     * temporary measure. To be replaced with an Telephony/TelecomManager call in the future.
     * TODO: Keep in sync with the string defined in OutgoingCallBroadcaster.java
     * in Phone app until this is replaced with the Telephony/Telecom API.
     */
    private static final String EXTRA_SEND_EMPTY_FLASH
            = "com.android.phone.extra.SEND_EMPTY_FLASH";

    private String mCurrentCountryIso;

    private CallStateReceiver mCallStateReceiver;

	//add by zhangjinqiang for if ipcall
	private  CooTekSmartdialerOemModule csom;
	//add by zjq end

	//add by zhangjinqiang for HQ01508475 at 20151207 -start
	String[] operatorName = new String[]{"70601","70401","71021","71073","71403","70604","706040","70403","704030","33403","334030","71030","710300","714020","71402","72207"};
	String[] operatorNameAT = new String[]{"334050","334090","33450"};
	//add by zhangjinqiang for HQ01508475 at 20151207 -end

    private class CallStateReceiver extends BroadcastReceiver {
        /**
         * Receive call state changes so that we can take down the
         * "dialpad chooser" if the phone becomes idle while the
         * chooser UI is visible.
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            // Log.i(TAG, "CallStateReceiver.onReceive");
            /** M: [ALPS01767303] make sure that hint was removed once call idle @{ */
            if (mDigits != null && (getActivity() == null || !isPhoneInUse())) {
                mDigits.setHint(null);
            }
            /** @} */

            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if ((TextUtils.equals(state, TelephonyManager.EXTRA_STATE_IDLE) ||
                    TextUtils.equals(state, TelephonyManager.EXTRA_STATE_OFFHOOK))
                    && isDialpadChooserVisible()) {
                // Log.i(TAG, "Call ended with dialpad chooser visible!  Taking it down...");
                // Note there's a race condition in the UI here: the
                // dialpad chooser could conceivably disappear (on its
                // own) at the exact moment the user was trying to select
                // one of the choices, which would be confusing.  (But at
                // least that's better than leaving the dialpad chooser
                // onscreen, but useless...)
                showDialpadChooser(false);
            }
        }
    }

    private boolean mWasEmptyBeforeTextChange;

    /**
     * This field is set to true while processing an incoming DIAL intent, in order to make sure
     * that SpecialCharSequenceMgr actions can be triggered by user input but *not* by a
     * tel: URI passed by some other app.  It will be set to false when all digits are cleared.
     */
    private boolean mDigitsFilledByIntent;

    private boolean mStartedFromNewIntent = false;
    private boolean mFirstLaunch = false;
    private boolean mAnimate = false;

    private ComponentName mSmsPackageComponentName;

    private static final String PREF_DIGITS_FILLED_BY_INTENT = "pref_digits_filled_by_intent";

    private TelephonyManager getTelephonyManager() {
        return (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
    }

    private TelecomManager getTelecomManager() {
        return (TelecomManager) getActivity().getSystemService(Context.TELECOM_SERVICE);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        mWasEmptyBeforeTextChange = TextUtils.isEmpty(s);
    }

    @Override
    public void onTextChanged(CharSequence input, int start, int before, int changeCount) {
        if (mWasEmptyBeforeTextChange != TextUtils.isEmpty(input)) {
            final Activity activity = getActivity();
            if (activity != null) {
                activity.invalidateOptionsMenu();
//                updateMenuOverflowButton(mWasEmptyBeforeTextChange);
            }
        }

        // DTMF Tones do not need to be played here any longer -
        // the DTMF dialer handles that functionality now.
    }

    @Override
    public void afterTextChanged(Editable input) {
        // When DTMF dialpad buttons are being pressed, we delay SpecialCharSequenceMgr sequence,
        // since some of SpecialCharSequenceMgr's behavior is too abrupt for the "touch-down"
        // behavior.
        if (!mDigitsFilledByIntent &&
                SpecialCharSequenceMgr.handleChars(getActivity(), input.toString(), mDigits)) {
            // A special sequence was entered, clear the digits
            mDigits.getText().clear();
            mTextStr = "";
        }

        if (isDigitsEmpty()) {
            mDigitsFilledByIntent = false;
            mDigits.setCursorVisible(false);
        }

        if (mDialpadQueryListener != null) {
            mDialpadQueryListener.onDialpadQueryChanged(mDigits.getText().toString());
        }
        updateDeleteButtonEnabledState();
		// / Added by guofeiyao for HQ01207444
		updateDialMenuButton();

		updateDigitsContainer();
		// / End
    }
	
	// / Added by guofeiyao for HQ01207444 begin
	public DialpadFragment(){
        super();
	}

	public DialpadFragment(View container, EditText digits){
        super();
		buildDigits(container,digits);
	}
	
	private ImageButton mMenu;
    private View mDigitsContainer;
	private View call;

    private SpeedDialPlugin speedDialPlugin;
    private final String[] idArr = {"two", "three", "four", "five", "six", "seven", "eight", "nine"};
	
	private void updateDialMenuButton() {
        if (getActivity() == null) {
            return;
        }
		if ( null == mMenu ) {
			Log.e("guofeiyao_mMenu:","mMenu is null!!!");
            return;
		}
        final boolean digitsNotEmpty = !isDigitsEmpty();
		if (digitsNotEmpty){
            mMenu.setImageResource(R.drawable.ic_delete_em);
		} else {
            mMenu.setImageResource(R.drawable.ic_menu_em);
		}
	}

    public void updateDigitsContainer(){
        if ( mDigitsContainer == null || mDigits == null ) return;
		
        final boolean digitsNotEmpty = !isDigitsEmpty();
		if (digitsNotEmpty){
            mDigitsContainer.setVisibility(View.VISIBLE);
			//Log.e("guofeiyao_Digits:",mDigits.getText().toString());
		} else {
            mDigitsContainer.setVisibility(View.GONE);
		}
	}
	// / End

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        mFirstLaunch = true;
        mCurrentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());

        try {
            mHaptic.init(getActivity(),
                         getResources().getBoolean(R.bool.config_enable_dialer_key_vibration));
        } catch (Resources.NotFoundException nfe) {
             Log.e(TAG, "Vibrate control bool missing.", nfe);
        }

        mProhibitedPhoneNumberRegexp = getResources().getString(
                R.string.config_prohibited_phone_number_regexp);

        if (state != null) {
            mDigitsFilledByIntent = state.getBoolean(PREF_DIGITS_FILLED_BY_INTENT);
        }

        mDialpadSlideInDuration = getResources().getInteger(R.integer.dialpad_slide_in_duration);

        if (mCallStateReceiver == null) {
            IntentFilter callStateIntentFilter = new IntentFilter(
                    TelephonyManager.ACTION_PHONE_STATE_CHANGED);
            mCallStateReceiver = new CallStateReceiver();
            ((Context) getActivity()).registerReceiver(mCallStateReceiver, callStateIntentFilter);
        }

        // / Commented out by guofeiyao,for All version support SpeedDial
        /// M: for Plug-in @{
        //ExtensionManager.getInstance().getDialPadExtension().onCreate(getActivity().getApplicationContext(), this, this);
        /// @}

        // / Added by guofeiyao for All version support SpeedDial

        speedDialPlugin = new SpeedDialPlugin(getActivity().getApplicationContext());
        // / End

		//add by zhangjinqiang for if ipcall-start
		csom = PeopleActivity.getCsom();
		//add by zjq end
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // / Commented out by guofeiyao,for All version support SpeedDial

//        ExtensionManager.getInstance().getDialPadExtension().onViewCreated(getActivity(), view);

        // / Added by guofeiyao for All version support SpeedDial
        if (SystemProperties.get("ro.hq.call.emergency.number").equals("1")) {
            speedDialPlugin.onViewCreated(getActivity(), view, idArr, mDigits, R.xml.mena_special_key);
        } else {
            speedDialPlugin.onViewCreated(getActivity(), view, idArr, mDigits, -1);
        }
        // / End
    }

    // / Modified by guofeiyao for HQ01180250 begin
    private View fragmentView;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        fragmentView = inflater.inflate(R.layout.dialpad_fragment, container,
                false);
    // / End
        fragmentView.buildLayer();
        ///M: WFC @{
        mContext = getActivity();
        if (ImsManager.isWfcEnabledByPlatform(mContext)) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(TelecomManagerEx.ACTION_PHONE_ACCOUNT_CHANGED);
            filter.addAction(TelecomManagerEx.ACTION_DEFAULT_ACCOUNT_CHANGED);
            mContext.registerReceiver(mReceiver, filter);
        }
        ///@}

        /// M: for plug-in
        ExtensionManager.getInstance().getDialPadExtension().onCreateView(inflater, container, savedState, fragmentView);

        Resources r = getResources();

        mDialpadView = (DialpadView) fragmentView.findViewById(R.id.dialpad_view);
        mDialpadView.setCanDigitsBeEdited(true);
        

        //added by guofeiyao begin
        /*
        PeopleActivity ac = (PeopleActivity)getActivity();
		mDigits.addTextChangedListener(ac.getTextWatcher());
		*/
		//end
		
        PhoneNumberFormatter.setPhoneNumberFormattingTextWatcher(getActivity(), mDigits);
        // Check for the presence of the keypad
        View oneButton = fragmentView.findViewById(R.id.one);
        if (oneButton != null) {
            configureKeypadListeners(fragmentView);
        }

        mDelete = mDialpadView.getDeleteButton();
        mDelete.setVisibility(View.GONE);
	//add by HQ_caoxuhao for red line start
	BitmapDrawable m = null;
	try {
		m = ((PeopleActivity)mContext).getPagerTabs().getStrip().getSpecialDrawable();
	} catch (Exception e) {
		e.printStackTrace();
		getActivity().finish();
		return fragmentView;
	}
	
	//add by HQ_caoxuhao for red line end
		if (m != null){
		mDigits.setBackgroundDrawable(m);
		}
/*
        if (mDelete != null) {
            mDelete.setOnClickListener(this);
            mDelete.setOnLongClickListener(this);
        }
        */
//end

        //annotated by guofeiyao begin
        /*
        mSpacer = fragmentView.findViewById(R.id.spacer);
        mSpacer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isDigitsEmpty()) {
                    hideAndClearDialpad(true);
                    return true;
                }
                return false;
            }
        });
        */
        //end

        

        // Set up the "dialpad chooser" UI; see showDialpadChooser().
        mDialpadChooser = (ListView) fragmentView.findViewById(R.id.dialpadChooser);
        mDialpadChooser.setOnItemClickListener(this);

	    // / Modified by guofeiyoa for HQ01207444 begin
		
		final View dialpad =
                (ImageButton) fragmentView.findViewById(R.id.ib_dialpad);
        if (null != dialpad) {
            dialpad.setOnClickListener(this);
        }

		mMenu = (ImageButton)fragmentView.findViewById(R.id.ib_menu);
        if (mMenu != null) {
            mMenu.setOnClickListener(this);
            mMenu.setOnLongClickListener(this);
        }

        // / Just for the "dialpad chooser" UI
		final View floatingActionButtonContainer =
                fragmentView.findViewById(R.id.dialpad_floating_action_button_container0);
        final View floatingActionButton =
                (ImageButton) fragmentView.findViewById(R.id.ib_dialpad);
        if (null != floatingActionButton) {
            floatingActionButton.setOnClickListener(this);
            mFloatingActionButtonController = new FloatingActionButtonController(getActivity(),
                    floatingActionButtonContainer, floatingActionButton);
        }
        // / End

		// / For a demand,also see updateAboveBtn()
		aboveBtnContainer = fragmentView.findViewById(R.id.ll_above_btn_container);
		aboveBtnLine = fragmentView.findViewById(R.id.v_above_btn_line);
        fragmentView.findViewById(R.id.rl_new).setOnClickListener(this);
        fragmentView.findViewById(R.id.rl_msg).setOnClickListener(this);
		
		updateAboveBtn(false);
		// / End

		/// M: Fix CR ALPS01863413. Update text field view for ADN query.
        SpecialCharSequenceMgr.updateTextFieldView(mDigits);
        return fragmentView;
    }


    ///M: WFC @{
 
   /**
    * Update the dialer icon based on WFC is registered or not. 
    *
    */   
    
    private void updateWfcUI() {
        final View floatingActionButton =
                (ImageButton) getView().findViewById(R.id.dialpad_floating_action_button);
        if (floatingActionButton != null) {
            ImageView dialIcon = (ImageView) floatingActionButton;
            PhoneAccountHandle defaultAccountHandle =
                   getTelecomManager().getDefaultOutgoingPhoneAccount(SCHEME_TEL);
            if (defaultAccountHandle != null) {
                PhoneAccount phoneAccount = getTelecomManager().getPhoneAccount(defaultAccountHandle);
                if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_WIFI_CALLING)) {
                    dialIcon.setImageDrawable(getResources().getDrawable(R.drawable.mtk_fab_ic_wfc));
                    Log.i(TAG, "[WFC] Dial Icon is of WFC");
                } else {
                    dialIcon.setImageDrawable(getResources().getDrawable(R.drawable.fab_ic_call));
                    Log.i(TAG, "[WFC] WFC Icon replaced");
                }
            } else {
                dialIcon.setImageDrawable(getResources().getDrawable(R.drawable.fab_ic_call));
                Log.i(TAG, "[WFC] Icon replaced");
            }
        }
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelecomManagerEx.ACTION_PHONE_ACCOUNT_CHANGED.equals(action)
                    || TelecomManagerEx.ACTION_DEFAULT_ACCOUNT_CHANGED.equals(action)) {
                Log.i(TAG, "[WFC] Intent recived is " + intent.getAction());
                updateWfcUI();
            }
        }
    };
    ///@}

    // / Added by guofeiyao
    // For HQ01180250 begin
    // Revision of adapting to right-hand mode on 2015/12/11
    public static void setRightDrawable(TextView tv, int resId){
        tv.setCompoundDrawablesWithIntrinsicBounds(0,0,resId,0);
	}
	
	public void buildDigits(View container,EditText digits){
        mDigits = digits;
        mDigits.setKeyListener(UnicodeDialerKeyListener.INSTANCE);
        mDigits.setOnClickListener(this);
        mDigits.setOnKeyListener(this);
        mDigits.setOnLongClickListener(this);
        mDigits.addTextChangedListener(this);
        mDigits.setElegantTextHeight(false);
        /// M: Make sure the hint adapter to the width of the mDigits. @{
        mDigits.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                /// make the hint do not exceed the width of the mDigits
                CharSequence oldHint = mDigits.getHint();
                if (isDigitsEmpty() && oldHint != null && oldHint.length() > 0) {
                    final SpannableString hint = new SpannableString(oldHint.toString());
                    final Paint paint = mDigits.getPaint();
                    final int width = mDigits.getWidth();
                    float ratio = width / paint.measureText(hint.toString());
                    /// We use max ratio 0.8 and min 0.6 of the original text size
                    if (ratio > 0.8f) {
                        ratio = 0.8f;
                    } else if (ratio < 0.6f) {
                        ratio =0.6f;
                    }
                    hint.setSpan(new RelativeSizeSpan(ratio), 0, hint.length(), 0);
                    mDigits.setHint(hint);
                }
            }
        });
        /// @}

		mDigits.setCursorVisible(false);
		mDigitsContainer = container;
		//mDigitsContainer.setVisibility(View.GONE);
		updateDigitsContainer();
	}

	
    private List<SubscriptionInfo> mSubInfoList;
	private int mSubCount;

	private void getSubInfoList() {
        mSubInfoList = SubscriptionManager.from(getActivity()).getActiveSubscriptionInfoList();
        mSubCount = (mSubInfoList != null && !mSubInfoList.isEmpty()) ? mSubInfoList.size() : 0;
    }

    public void refreshBottom() {
		//0 sim
		final View call = fragmentView.findViewById(R.id.dctv_call_none);
		//add by zhaizhanfeng for HQ01533309&HQ01533600 at 151128 start
		if(SystemProperties.get("ro.hq.Atick.Authenticate").equals("1")){
            if ( call instanceof TextView ) {
			
			final TextView tvCall = (TextView)call;
			if (null != tvCall) {
				tvCall.setOnClickListener(this);
				tvCall.setVisibility(View.VISIBLE);

				if ( tvCall.isRtlLocale() ) {
                    setRightDrawable(tvCall, R.drawable.contact_dial_call_single);
				} else {
					tvCall.setCompoundDrawablesWithIntrinsicBounds(R.drawable.contact_dial_call_single,0,0,0);
				}
			}

			}
		}else {
		//add by zhaizhanfeng for HQ01533309&HQ01533600 at 151128 end
	        
			if (null != call) {
	            call.setOnClickListener(this);
				call.setVisibility(View.VISIBLE);
			}
		}
        //1 sim
		final TextView dctvCall = (TextView)fragmentView.findViewById(R.id.dctv_call);
		if (null != dctvCall) {
            dctvCall.setOnClickListener(this);
			dctvCall.setVisibility(View.GONE);
		}
		//2 sims
		final View llCalls = fragmentView.findViewById(R.id.ll_calls);
		//add by mingyue.wang for HQ01352182 begin
		if (null != llCalls) {
		    llCalls.setVisibility(View.GONE);
		}
		//add by mingyue.wang for HQ01352182 end
		final TextView dctvCall1 = (TextView)fragmentView.findViewById(R.id.dctv_call_1);
   		if (null != dctvCall1) {
            dctvCall1.setOnClickListener(this);
    	}
	   	final TextView dctvCall2 = (TextView)fragmentView.findViewById(R.id.dctv_call_2);
	    if (null != dctvCall2) {
            dctvCall2.setOnClickListener(this);
	   	}	

        //handle it 
        boolean isAirplaneModeOn = android.provider.Settings.System.getInt(mContext.getContentResolver(),  
                  android.provider.Settings.System.AIRPLANE_MODE_ON, 0) != 0;

        getSubInfoList();
		
        if (1 == mSubCount && !isAirplaneModeOn) {
			int subsriptionId = mSubInfoList.get(0).getSubscriptionId();
			int slotId = SubscriptionManager.getSlotId(subsriptionId);
			//add by mingyue.wang for HQ01352182 begin
			if (null != dctvCall) {
				if ( 1 == slotId ) {
					if ( dctvCall.isRtlLocale() ) {
                         setRightDrawable(dctvCall, R.drawable.contact_dial_call_2);
				    } else {
					     dctvCall.setCompoundDrawablesWithIntrinsicBounds(R.drawable.contact_dial_call_2,0,0,0);
				    }
				} else if ( 0 == slotId ) {
                    if ( dctvCall.isRtlLocale() ) {
                         setRightDrawable(dctvCall, R.drawable.contact_dial_call_1);
				    } else {
					     dctvCall.setCompoundDrawablesWithIntrinsicBounds(R.drawable.contact_dial_call_1,0,0,0);
				    }
				}

				if ( !getTelephonyManager().isMultiSimEnabled() ) {
					if ( dctvCall.isRtlLocale() ) {
                         setRightDrawable(dctvCall, R.drawable.contact_dial_call_single);
					} else {
					     dctvCall.setCompoundDrawablesWithIntrinsicBounds(R.drawable.contact_dial_call_single,0,0,0);
					}
				}
				
				dctvCall.setVisibility(View.VISIBLE);
				//add by zhaizhanfeng for dialer not diaplay spn HQ01597845 at 151229 start
				if(!SystemProperties.get("ro.hq.dialer.call.att").equals("1")){
					String sim1 = mSubInfoList.get(0).getDisplayName().toString();
				    dctvCall.setText(sim1);
					//dctvCall.setTextColor(Color.parseColor("#FFFFFF"));
	                                Log.e("guofeiyao","mSubInfoList.get(0).getDisplayName()"+sim1);
				}
				//add by zhaizhanfeng for dialer not diaplay spn HQ01597845 at 151229 end
			}
			if(null != call) {
			   call.setVisibility(View.GONE);
			}
			
            //call.setVisibility(View.GONE);
			//dctvCall.setVisibility(View.VISIBLE);

			/*
			boolean radioOn = isRadioOn(subsriptionId);
			if ( radioOn ) {
				Log.e("guofeiyao","1 sim, radioOn");
				*/
			    //String sim1 = mSubInfoList.get(0).getDisplayName().toString();
			    //dctvCall.setText(sim1);
				//dctvCall.setTextColor(Color.parseColor("#FFFFFF"));
				//dctvCall.setFocusable(true);
				
				/*
			} else {
			
			    Log.e("guofeiyao","1 sim,radioOff");
			    dctvCall.setText(R.string.no_service);
				dctvCall.setEnabled(false);
				dctvCall.setTextColor(Color.parseColor("#90918f"));
				//dctvCall.setFocusable(false);
			}
			*/
			
		} else if (2 == mSubCount && !isAirplaneModeOn) {
			if(null != call) {
			   call.setVisibility(View.GONE);
			}
			if(null != llCalls) {
			   llCalls.setVisibility(View.VISIBLE);
			}
            /*
            int subsriptionId1 = mSubInfoList.get(0).getSubscriptionId();
			boolean radioOn1 = isRadioOn(subsriptionId1);
			if ( radioOn1 ) {
                Log.e("guofeiyao","2 sim,1st: radioOn");
                */
			  if(null != dctvCall1) {
                if ( dctvCall1.isRtlLocale() ) {
                     setRightDrawable(dctvCall1, R.drawable.contact_dial_call_1);
				} else {
                     dctvCall1.setCompoundDrawablesWithIntrinsicBounds(R.drawable.contact_dial_call_1,0,0,0);
				}
				
				String sim1 = mSubInfoList.get(0).getDisplayName().toString();
			    dctvCall1.setText(sim1);
				//dctvCall1.setTextColor(Color.parseColor("#FFFFFF"));
                                Log.e("guofeiyao","SIM 1:mSubInfoList.get(0).getDisplayName()"+sim1);
			  }
				/*
			} else {
			
			    Log.e("guofeiyao","2 sim,1st: radioOff");
                dctvCall1.setText(R.string.no_service);
				dctvCall1.setEnabled(false);
				dctvCall1.setTextColor(Color.parseColor("#90918f"));
			}
			*/

            /*
            int subsriptionId2 = mSubInfoList.get(1).getSubscriptionId();
			boolean radioOn2 = isRadioOn(subsriptionId2);
            if ( radioOn2 ) {
                Log.e("guofeiyao","2 sim,2nd: radioOn");
                */
			  if(null != dctvCall2) {
                if ( dctvCall2.isRtlLocale() ) {
                     setRightDrawable(dctvCall2, R.drawable.contact_dial_call_2);
				} else {
                     dctvCall2.setCompoundDrawablesWithIntrinsicBounds(R.drawable.contact_dial_call_2,0,0,0);
				}
				
			    String sim2 = mSubInfoList.get(1).getDisplayName().toString();
			    dctvCall2.setText(sim2);
				//dctvCall2.setTextColor(Color.parseColor("#FFFFFF"));
                                Log.e("guofeiyao","SIM 2:mSubInfoList.get(1).getDisplayName()"+sim2);
			  }
				/*
            } else {
            
                Log.e("guofeiyao","2 sim,2nd: radioOff");
                dctvCall2.setText(R.string.no_service);
				dctvCall2.setEnabled(false);
				dctvCall2.setTextColor(Color.parseColor("#90918f"));
			}
			*/
		}
		
	}

	/**
     * Calling API to get subId is in on.
     * @param subId Subscribers ID.
     * @return {@code true} if radio on
     */
    public static boolean isRadioOn(int subId) {
        ITelephony itele = ITelephony.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE));
        boolean isOn = false;
        try {
            if (itele != null) {
                isOn = subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID ? false :
                    itele.isRadioOnForSubscriber(subId);
            } else {
                Log.d(TAG, "telephony service is null");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "isOn = " + isOn + ", subId: " + subId);
        return isOn;
    }
	// / End

    private boolean isLayoutReady() {
        return mDigits != null;
    }

    public EditText getDigitsWidget() {
        return mDigits;
    }

    /**
     * @return true when {@link #mDigits} is actually filled by the Intent.
     */
    private boolean fillDigitsIfNecessary(Intent intent) {
        // Only fills digits from an intent if it is a new intent.
        // Otherwise falls back to the previously used number.
        if (!mFirstLaunch && !mStartedFromNewIntent) {
            return false;
        }

        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null) {
                if (PhoneAccount.SCHEME_TEL.equals(uri.getScheme())
                        /// M: for ALPS01682003
                        // support dial voicemail number
                        || PhoneAccount.SCHEME_VOICEMAIL.equals(uri.getScheme())) {
                    // Put the requested number into the input area
                    String data = uri.getSchemeSpecificPart();
                    // Remember it is filled via Intent.
                    mDigitsFilledByIntent = true;
                    final String converted = PhoneNumberUtils.convertKeypadLettersToDigits(
                            PhoneNumberUtils.replaceUnicodeDigits(data));
                    setFormattedDigits(converted, null);
                    return true;
                } else {
                    String type = intent.getType();
                    if (People.CONTENT_ITEM_TYPE.equals(type)
                            || Phones.CONTENT_ITEM_TYPE.equals(type)) {
                        // Query the phone number
                        Cursor c = getActivity().getContentResolver().query(intent.getData(),
                                new String[] {PhonesColumns.NUMBER, PhonesColumns.NUMBER_KEY},
                                null, null, null);
                        if (c != null) {
                            try {
                                if (c.moveToFirst()) {
                                    // Remember it is filled via Intent.
                                    mDigitsFilledByIntent = true;
                                    // Put the number into the input area
                                    setFormattedDigits(c.getString(0), c.getString(1));
                                    return true;
                                }
                            } finally {
                                c.close();
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Determines whether an add call operation is requested.
     *
     * @param intent The intent.
     * @return {@literal true} if add call operation was requested.  {@literal false} otherwise.
     */
    private static boolean isAddCallMode(Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            // see if we are "adding a call" from the InCallScreen; false by default.
            return intent.getBooleanExtra(ADD_CALL_MODE_KEY, false);
        } else {
            return false;
        }
    }

    /**
     * Checks the given Intent and changes dialpad's UI state. For example, if the Intent requires
     * the screen to enter "Add Call" mode, this method will show correct UI for the mode.
     */
    private void configureScreenFromIntent(Activity parent) {
        // If we were not invoked with a DIAL intent,

		// / Modified by guofeiyao orz
        Log.i(TAG + "_duanze", "configureScreenFromIntent(Activity parent) start.");
/*
        if (!(parent instanceof DialtactsActivity)) {
            setStartedFromNewIntent(false);
            return;
        }
		*/
        if (!(parent instanceof PeopleActivity)) {
            setStartedFromNewIntent(false);
            return;
        }

		// / End
		
        // See if we were invoked with a DIAL intent. If we were, fill in the appropriate
        // digits in the dialer field.
        Intent intent = parent.getIntent();

        if (!isLayoutReady()) {
            // This happens typically when parent's Activity#onNewIntent() is called while
            // Fragment#onCreateView() isn't called yet, and thus we cannot configure Views at
            // this point. onViewCreate() should call this method after preparing layouts, so
            // just ignore this call now.
            Log.i(TAG,
                    "Screen configuration is requested before onCreateView() is called. Ignored");
            return;
        }

        boolean needToShowDialpadChooser = false;

        // Be sure *not* to show the dialpad chooser if this is an
        // explicit "Add call" action, though.
        final boolean isAddCallMode = isAddCallMode(intent);
        if (!isAddCallMode) {
            Log.i(TAG + "_duanze", "!isAddCallMode start.");

            // Don't show the chooser when called via onNewIntent() and phone number is present.
            // i.e. User clicks a telephone link from gmail for example.
            // In this case, we want to show the dialpad with the phone number.
            final boolean digitsFilled = fillDigitsIfNecessary(intent);
            if (!(mStartedFromNewIntent && digitsFilled)) {

                final String action = intent.getAction();
                // / Modified by guofeiyao for Three merge to one.
                if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)
                        || Intent.ACTION_MAIN.equals(action)
                        || "com.android.contacts.action.LIST_DEFAULT".equals(action)) {

                    Log.i(TAG + "_duanze", "before set needToShowDialpadChooser");
                    // If there's already an active call, bring up an intermediate UI to
                    // make the user confirm what they really want to do.
                    if (isPhoneInUse()) {
                        needToShowDialpadChooser = true;
                    }
                }

            }
        }
        showDialpadChooser(needToShowDialpadChooser);
        setStartedFromNewIntent(false);
    }

    public void setStartedFromNewIntent(boolean value) {
        mStartedFromNewIntent = value;
    }

    public void clearCallRateInformation() {
        setCallRateInformation(null, null);
    }

    public void setCallRateInformation(String countryName, String displayRate) {
        mDialpadView.setCallRateInformation(countryName, displayRate);
    }

    /**
     * Sets formatted digits to digits field.
     */
    private void setFormattedDigits(String data, String normalizedNumber) {
        // strip the non-dialable numbers out of the data string.
        String dialString = PhoneNumberUtils.extractNetworkPortion(data);
        final String formatDialString =
                PhoneNumberUtils.formatNumber(dialString, normalizedNumber, mCurrentCountryIso);
        if (!TextUtils.isEmpty(formatDialString)) {
           final Editable digits = mDigits.getText();
           digits.replace(0, digits.length(), formatDialString);
           // for some reason this isn't getting called in the digits.replace call above..
           // but in any case, this will make sure the background drawable looks right
           afterTextChanged(digits);

           /** M: If user pressed the dialpad key, the data in the intent would be
            *  out of date. So, clear it. Otherwise the data would be re-write
            *  into the digit view after restart the activity. @{ */
           Activity activity = getActivity();
           if (activity != null) {
               activity.getIntent().setData(null);
           }
           /** @} */
        }
    }

    private void configureKeypadListeners(View fragmentView) {
        final int[] buttonIds = new int[] {R.id.one, R.id.two, R.id.three, R.id.four, R.id.five,
                R.id.six, R.id.seven, R.id.eight, R.id.nine, R.id.star, R.id.zero, R.id.pound};

        DialpadKeyButton dialpadKey;

        for (int i = 0; i < buttonIds.length; i++) {
            dialpadKey = (DialpadKeyButton) fragmentView.findViewById(buttonIds[i]);
            dialpadKey.setOnPressedListener(this);
        }

        // Long-pressing one button will initiate Voicemail.
        final DialpadKeyButton one = (DialpadKeyButton) fragmentView.findViewById(R.id.one);
        one.setOnLongClickListener(this);

		//add by zhangjinqiang for HQ01508475 at 20151207 -start
        if (android.os.SystemProperties.get("ro.hq.speed.three").equals("1")) {
            TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            String operatorNumeric = tm.getSimOperator();
            if (Arrays.asList(operatorName).contains(operatorNumeric) || Arrays.asList(operatorNameAT).contains(operatorNumeric)) {

                final DialpadKeyButton three = (DialpadKeyButton) fragmentView.findViewById(R.id.three);
                three.setOnLongClickListener(this);

                if (null != speedDialPlugin) {
                    SpeedDialPlugin.consumedThree = true;
                }
            }
        }
        //add by zhangjinqiang for HQ01508475 at 20151207 -end

        //add by zhaizhanfeng for HQ01311701 at 151101 start
        final DialpadKeyButton nine = (DialpadKeyButton) fragmentView.findViewById(R.id.nine);
        nine.setOnLongClickListener(this);
        //add by zhaizhanfeng for HQ01311701 at 151101 end

        // Long-pressing zero button will enter '+' instead.
        final DialpadKeyButton zero = (DialpadKeyButton) fragmentView.findViewById(R.id.zero);
        zero.setOnLongClickListener(this);

        // / Added by guofeiyao for HQ01207444
        final DialpadKeyButton star = (DialpadKeyButton) fragmentView.findViewById(R.id.star);
        star.setOnLongClickListener(this);
		star.setOnClickListener(this);

		final DialpadKeyButton pound = (DialpadKeyButton) fragmentView.findViewById(R.id.pound);
        pound.setOnLongClickListener(this);
		pound.setOnClickListener(this);
		// / End
    }

    @Override
    public void onStart() {
        super.onStart();
        // if the mToneGenerator creation fails, just continue without it.  It is
        // a local audio signal, and is not as important as the dtmf tone itself.
        final long start = System.currentTimeMillis();
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                try {
                    mToneGenerator = new ToneGenerator(DIAL_TONE_STREAM_TYPE, TONE_RELATIVE_VOLUME);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Exception caught while creating local tone generator: " + e);
                    mToneGenerator = null;
                }
            }
        }
        final long total = System.currentTimeMillis() - start;
        if (total > 50) {
            Log.i(TAG, "Time for ToneGenerator creation: " + total);
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        // / Modified by guofeiyao begin
//        final DialtactsActivity activity = (DialtactsActivity) getActivity();
        final PeopleActivity activity = (PeopleActivity) getActivity();
	// / End

        mDialpadQueryListener = activity;

        final StopWatch stopWatch = StopWatch.start("Dialpad.onResume");

        // Query the last dialed number. Do it first because hitting
        // the DB is 'slow'. This call is asynchronous.
        queryLastOutgoingCall();

        stopWatch.lap("qloc");

        final ContentResolver contentResolver = activity.getContentResolver();

        /// M: [ALPS01858019] add listener to observer CallLog changes.
        contentResolver.registerContentObserver(CallLog.CONTENT_URI, true,
                mCallLogObserver);

        /// M: [ALPS01841736] add listener for RingerMode and DTMF
        setupAndRegisterListener(activity);
        stopWatch.lap("dtwd");

        // Retrieve the haptic feedback setting.
        mHaptic.checkSystemSetting();

        stopWatch.lap("hptc");

        mPressedDialpadKeys.clear();

        configureScreenFromIntent(getActivity());

        stopWatch.lap("fdin");

        if (!isPhoneInUse()) {
            // A sanity-check: the "dialpad chooser" UI should not be visible if the phone is idle.
            showDialpadChooser(false);
        }

        ///M: WFC @{
        if(ImsManager.isWfcEnabledByPlatform(mContext)) {
            updateWfcUI();
        }
        ///@}

        mFirstLaunch = false;

        stopWatch.lap("hnt");

        updateDeleteButtonEnabledState();

        stopWatch.lap("bes");

        stopWatch.stopAndLog(TAG, 50);

        mSmsPackageComponentName = DialerUtils.getSmsComponent(activity);

        // Populate the overflow menu in onResume instead of onCreate, so that if the SMS activity
        // is disabled while Dialer is paused, the "Send a text message" option can be correctly
        // removed when resumed.
        mOverflowMenuButton = mDialpadView.getOverflowMenuButton();
		//
		mOverflowMenuButton.setVisibility(View.GONE);
		//
        mOverflowPopupMenu = buildOptionsMenu(mOverflowMenuButton);
        mOverflowMenuButton.setOnTouchListener(mOverflowPopupMenu.getDragToOpenListener());
//        mOverflowMenuButton.setOnClickListener(this);
//        mOverflowMenuButton.setVisibility(isDigitsEmpty() ? View.INVISIBLE : View.VISIBLE);
        /** M: [VoLTE ConfCall] Always show overflow menu button for conf call. @{ */
        if (DialerVolteUtils.isVoLTEConfCallEnable(getActivity())) {

            //mOverflowMenuButton.setVisibility(View.VISIBLE);
            mOverflowMenuButton.setAlpha(1);

        }
        /** @} */
        
        // / Added by guofeiyao
        refreshBottom();
        // / End
    }

    @Override
    public void onPause() {
        super.onPause();

        // Make sure we don't leave this activity with a tone still playing.
        stopTone();
        mPressedDialpadKeys.clear();
        mTextStr = "";

        // TODO: I wonder if we should not check if the AsyncTask that
        // lookup the last dialed number has completed.
        mLastNumberDialed = EMPTY_NUMBER;  // Since we are going to query again, free stale number.

        SpecialCharSequenceMgr.cleanup();
        /// M: [ALPS01841736] stop the RingerMode and DTMF listener
        Activity activity = getActivity();
        removeListener(activity);

        /// M: [ALPS01858019] add unregister the call log observer.
        activity.getContentResolver().unregisterContentObserver(mCallLogObserver);

        // / Added by guofeiyao

        speedDialPlugin.onPause();


		if ( null != voicemailDialog && voicemailDialog.isShowing() ) { 
             voicemailDialog.dismiss();
			 voicemailDialog = null;
		}
        // / End
    }

    @Override
    public void onStop() {
        super.onStop();

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator != null) {
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }

        if (mClearDigitsOnStop) {
            mClearDigitsOnStop = false;
            clearDialpad();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(PREF_DIGITS_FILLED_BY_INTENT, mDigitsFilledByIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
	try {
	        ((Context) getActivity()).unregisterReceiver(mCallStateReceiver);
	} catch (Exception e) {
		e.printStackTrace();
	}
        /// M: for plug-in
        ExtensionManager.getInstance().getDialPadExtension().onDestroy();

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ///M: WFC @{
        if (ImsManager.isWfcEnabledByPlatform(mContext)) {
            mContext.unregisterReceiver(mReceiver);
        }
        ///@}
    }

    private void keyPressed(int keyCode) {
        if (getView().getTranslationY() != 0) {
            return;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_1:
                playTone(ToneGenerator.TONE_DTMF_1, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_2:
                playTone(ToneGenerator.TONE_DTMF_2, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_3:
                playTone(ToneGenerator.TONE_DTMF_3, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_4:
                playTone(ToneGenerator.TONE_DTMF_4, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_5:
                playTone(ToneGenerator.TONE_DTMF_5, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_6:
                playTone(ToneGenerator.TONE_DTMF_6, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_7:
                playTone(ToneGenerator.TONE_DTMF_7, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_8:
                playTone(ToneGenerator.TONE_DTMF_8, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_9:
                playTone(ToneGenerator.TONE_DTMF_9, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_0:
                playTone(ToneGenerator.TONE_DTMF_0, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_POUND:
                playTone(ToneGenerator.TONE_DTMF_P, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_STAR:
                playTone(ToneGenerator.TONE_DTMF_S, TONE_LENGTH_INFINITE);
                break;
            default:
                break;
        }

        mHaptic.vibrate();
//HQ_hushunli 2015-10-22 add for HQ01303116 begin

		try{//avoid null point exception 
		    String curLanguageString = getResources().getConfiguration().locale.getLanguage();
		    Log.d(TAG, "curLanguageString is " + curLanguageString + ", persist.sys.hq.arabic.numerals is " + SystemProperties.get("persist.sys.hq.arabic.numerals"));
		    if ((curLanguageString.equals("ar") || curLanguageString.equals("fa"))
		            && SystemProperties.get("persist.sys.hq.arabic.numerals").equals("1")) {
		        if (TextUtils.isEmpty(mTextStr)) {
		            index = 0;
		        }
		        Log.d(TAG, "start selection is " + index + ", mTextStr.length is " + mTextStr.length() + ", mTextStr is " + mTextStr);
		        if (((keyCode >= KeyEvent.KEYCODE_0) && (keyCode <= KeyEvent.KEYCODE_9)) || keyCode == KeyEvent.KEYCODE_POUND || keyCode == KeyEvent.KEYCODE_STAR) {
		            String addNum = "";
		            if (keyCode == KeyEvent.KEYCODE_POUND) {
		                addNum = "#";
		            } else if (keyCode == KeyEvent.KEYCODE_STAR) {
		                addNum = "*";
		            } else {
		                if (curLanguageString.equals("ar")) {
	                        addNum = mArLangNummap.get(keyCode - 7).toString();
	                    } else if (curLanguageString.equals("fa")) {
	                        addNum = mFaLangNummap.get(keyCode - 7).toString();
	                    }
		            }
		            if (index > 0 && index < mTextStr.length()) {
		                mTextStr = mTextStr.substring(0, index) + addNum + mTextStr.substring(index);
		                mDigits.setText("\u202D" + mTextStr + "\u202C");
		                index++;
		                mDigits.setSelection(index + 1);
		                Log.d(TAG, "add 1, " + "addNum is " + addNum + ", mTextStr is " + mTextStr + ", the selection is " + mDigits.getSelectionStart());
                    } else if (index == mTextStr.length()) {
                        mTextStr = mTextStr + addNum;
                        mDigits.setText("\u202D" + mTextStr + "\u202C");
                        index++;
                        mDigits.setSelection(index + 1);
                        Log.d(TAG, "add 2, " + "addNum is " + addNum + ", mTextStr is " + mTextStr + ", the selection is " + mDigits.getSelectionStart());
                    } else if (index == 0 && !mTextStr.equals("")) {
                        mTextStr = addNum + mTextStr;
                        mDigits.setText("\u202D" + mTextStr + "\u202C");
                        index++;
                        mDigits.setSelection(2);
                        Log.d(TAG, "add 3, " + "addNum is " + addNum + ", mTextStr is " + mTextStr + ", the selection is " + mDigits.getSelectionStart());
                    }
		        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
		            if (index > 0 && index < mTextStr.length()) {
		                mTextStr = mTextStr.substring(0, index - 1) + mTextStr.substring(index);
		                mDigits.setText("\u202D" + mTextStr + "\u202C");
		                index--;
		                mDigits.setSelection(index + 1);
		                Log.d(TAG, "delete 1" + ", mTextStr is " + mTextStr + ", the selection is " + mDigits.getSelectionStart());
		            } else if (index == mTextStr.length() && !mTextStr.equals("")) {
		                mTextStr = mTextStr.substring(0,mTextStr.length()-1);
		                mDigits.setText("\u202D" + mTextStr + "\u202C");
		                index--;
		                mDigits.setSelection(index + 1);
		                Log.d(TAG, "delete 2" + ", mTextStr is " + mTextStr + ", the selection is " + mDigits.getSelectionStart());
		            } else if (index == 0) {
		                Log.d(TAG, "delete 3" + ", mTextStr is " + mTextStr + ", the selection is " + mDigits.getSelectionStart());
		            }
		        }
		        Log.d(TAG, "mDigits.getText is " + mDigits.getText().toString() + ", length is " + mDigits.length() + ", index is " + index);
		    } else {
		       // / Added by guofeiyao for HQ01207444
		        if (KeyEvent.KEYCODE_POUND == keyCode || KeyEvent.KEYCODE_STAR == keyCode) return;
		       // / End
		        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
		        mDigits.onKeyDown(keyCode, event);
		    }
  
          // If the cursor is at the end of the text we hide it.
          final int length = mDigits.length();
          if (length == mDigits.getSelectionStart() && length == mDigits.getSelectionEnd()) {
              mDigits.setCursorVisible(false);
          }
		}catch(NullPointerException e){
			e.printStackTrace();
		}catch(IndexOutOfBoundsException e){
            		e.printStackTrace();
        	}
		//HQ_hushunli 2015-10-22 add for HQ01303116 end
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent event) {
	String curLanguageString = getResources().getConfiguration().locale.getLanguage();
        switch (view.getId()) {
            case R.id.digits:
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    handleDialButtonPressed();
                    return true;
                }
                break;
        }
        return false;
    }

    /**
     * When a key is pressed, we start playing DTMF tone, do vibration, and enter the digit
     * immediately. When a key is released, we stop the tone. Note that the "key press" event will
     * be delivered by the system with certain amount of delay, it won't be synced with user's
     * actual "touch-down" behavior.
     */
    @Override
    public void onPressed(View view, boolean pressed) {
        if (DEBUG) Log.d(TAG, "onPressed(). view: " + view + ", pressed: " + pressed);
        if (pressed) {
            switch (view.getId()) {
                case R.id.one: {
                    keyPressed(KeyEvent.KEYCODE_1);
                    break;
                }
                case R.id.two: {
                    keyPressed(KeyEvent.KEYCODE_2);
                    break;
                }
                case R.id.three: {
                    keyPressed(KeyEvent.KEYCODE_3);
                    break;
                }
                case R.id.four: {
                    keyPressed(KeyEvent.KEYCODE_4);
                    break;
                }
                case R.id.five: {
                    keyPressed(KeyEvent.KEYCODE_5);
                    break;
                }
                case R.id.six: {
                    keyPressed(KeyEvent.KEYCODE_6);
                    break;
                }
                case R.id.seven: {
                    keyPressed(KeyEvent.KEYCODE_7);
                    break;
                }
                case R.id.eight: {
                    keyPressed(KeyEvent.KEYCODE_8);
                    break;
                }
                case R.id.nine: {
                    keyPressed(KeyEvent.KEYCODE_9);
                    break;
                }
                case R.id.zero: {
                    keyPressed(KeyEvent.KEYCODE_0);
                    break;
                }
                case R.id.pound: {
                    keyPressed(KeyEvent.KEYCODE_POUND);
                    break;
                }
                case R.id.star: {
                    keyPressed(KeyEvent.KEYCODE_STAR);
                    break;
                }
                default: {
                    Log.wtf(TAG, "Unexpected onTouch(ACTION_DOWN) event from: " + view);
                    break;
                }
            }
            mPressedDialpadKeys.add(view);
        } else {
            mPressedDialpadKeys.remove(view);
            if (mPressedDialpadKeys.isEmpty()) {
                stopTone();
            }
        }
    }

    /**
     * Called by the containing Activity to tell this Fragment to build an overflow options
     * menu for display by the container when appropriate.
     *
     * @param invoker the View that invoked the options menu, to act as an anchor location.
     */
    private PopupMenu buildOptionsMenu(View invoker) {
        final PopupMenu popupMenu = new PopupMenu(getActivity(), invoker) {
            @Override
            public void show() {
                final Menu menu = getMenu();
                final MenuItem sendMessage = menu.findItem(R.id.menu_send_message);
                sendMessage.setVisible(mSmsPackageComponentName != null);

                boolean enable = !isDigitsEmpty();
                for (int i = 0; i < menu.size(); i++) {
                    menu.getItem(i).setVisible(enable);
                }
                /** M: [Ip Dial] Check whether to show button @{ */
                menu.findItem(R.id.menu_ip_dial).setVisible(enable &&
                        !PhoneNumberHelper.isUriNumber(mDigits.getText().toString()));
                /** @} */
                /** M: [VoLTE ConfCall] Show conference call menu for volte. @{ */
                boolean visible = DialerVolteUtils.isVoLTEConfCallEnable(getActivity());
                menu.findItem(R.id.menu_volte_conf_call).setVisible(visible);
                /** @} */
                super.show();
            }
        };
        popupMenu.inflate(R.menu.dialpad_options);
        popupMenu.setOnMenuItemClickListener(this);
        popupMenu.setOnDismissListener(this);
        /// M: for plug-in
        ExtensionManager.getInstance().getDialPadExtension().constructPopupMenu(popupMenu, invoker,
                popupMenu.getMenu());
        return popupMenu;
    }

    // / Added by guofeiyao begin
    private static final String G = "_guofeiyao";
	private static final String IP_SWITCH_KEY = "button_ip_switch_key";
    public static final String IP_RADIO = "ip_radio";

	private AlertDialog voicemailDialog;

    public void deleteButtonPressed() {
        if (!isDigitsEmpty()) {
            keyPressed(KeyEvent.KEYCODE_DEL);
        }
    }

    public void deleteButtonLongPressed() {
        if (!isDigitsEmpty()) {
            final Editable digits = mDigits.getText();
            digits.clear();
            mTextStr = "";
        }
    }

	public static boolean getIpSwitchValue(Context context, int subId) {
        String key = IP_SWITCH_KEY + subId;
        Log.d(TAG + G, "IP_SWITCH_KEY~~~: " + key);
        return 1 == Settings.System.getInt(context.getContentResolver(), key, 0);
    }
	
	public void callVoicemail(Intent intent) {
        DialerUtils.startActivityWithErrorToast(getActivity(), intent);
        hideAndClearDialpad(false);
    }

	private void showDialog(){	
		final AlertDialog alertDialog = new AlertDialog.Builder(mContext).create();
        voicemailDialog = alertDialog;
		
		alertDialog.show();
		Window window = alertDialog.getWindow(); 
		window.setContentView(R.layout.call_alert_dialog);
		String sim1 = mSubInfoList.get(0).getDisplayName().toString();
		Button btnOne = (Button)window.findViewById(R.id.btn_sim_one);
		btnOne.setText(sim1);
		btnOne.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				/*
				 Intent intent = CallUtil.getCallIntent(number,
                            (mContext instanceof PeopleActivity?
                                    ((PeopleActivity) mContext).getDialerPlugIn().getCallOrigin() : null));
                                intent.putExtra("slot_id",0);
                 DialerUtils.startActivityWithErrorToast(mContext, intent);
                 */
				//add by zhaizhanfeng for HQ01764150 at 160223 start
				List<PhoneAccountHandle> allUserSelectedAccountList = new ArrayList<PhoneAccountHandle>();
				allUserSelectedAccountList = getTelecomManager().getAllPhoneAccountHandles();

				if(getTelecomManager().hasVoiceMailNumber(allUserSelectedAccountList.get(0))){
		            Intent intent = CallUtil.getVoicemailIntent();
					intent.putExtra("slot_id",0);
					callVoicemail(intent);
					alertDialog.dismiss();
				}else{
					DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
														R.string.dialog_voicemail_not_ready_message);
					dialogFragment.show(getFragmentManager(), "voicemail_not_ready");
				}
				//add by zhaizhanfeng for HQ01764150 at 160223 end
			}
		});
		
		String sim2 = mSubInfoList.get(1).getDisplayName().toString();
		Button btnTwo = (Button)window.findViewById(R.id.btn_sim_two);
		btnTwo.setText(sim2);
		btnTwo.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				//add by zhaizhanfeng for HQ01764150 at 160223 start
				List<PhoneAccountHandle> allUserSelectedAccountList = new ArrayList<PhoneAccountHandle>();
				allUserSelectedAccountList = getTelecomManager().getAllPhoneAccountHandles();

				if(getTelecomManager().hasVoiceMailNumber(allUserSelectedAccountList.get(1))){
					Intent intent = CallUtil.getVoicemailIntent();
					intent.putExtra("slot_id",1);
					callVoicemail(intent);
					alertDialog.dismiss();
				}else{
					DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
														R.string.dialog_voicemail_not_ready_message);
					dialogFragment.show(getFragmentManager(), "voicemail_not_ready");
				}
				//add by zhaizhanfeng for HQ01764150 at 160223 end
			}
		});
		
	}
	// / End
      /*add by lihaizhou for ChildMode at 2015-07-28 by begin*/
      private boolean isChildModeOn() 
	{
	        String isOn = ChildMode.getString(mContext.getContentResolver(),
	       		ChildMode.CHILD_MODE_ON);
	        if(isOn != null && "1".equals(isOn)){
	       	 return true;
	        }else{
	       	 return false;
		 }
		       	 
	}
       private boolean isProhibitCall() {
		     String isOn = ChildMode.getString(mContext.getContentResolver(),
		    		ChildMode.FORBID_CALL );
		     if(isOn != null && "1".equals(isOn)){
		    	 return true;
		     }else {
		    	 return false;
		     }	 
		}
/*add by lihaizhou for ChildMode at 2015-07-28 by end*/ 
    @Override
    public void onClick(View view) {
	String curLanguageString = getResources().getConfiguration().locale.getLanguage();
        switch (view.getId()) {
			// / Modified by guofeiyoa for HQ01207444 begin
			//case R.id.dialpad_floating_action_button:
            case R.id.dctv_call_none:
			//modified by guofeiyoa for HQ01207444 end
                /*add by lihaizhou for ChildMode at 2015-07-28 by begin*/
                //if (isChildModeOn() && isProhibitCall())
                //{
                //break;
               // }
                /*add by lihaizhou for ChildMode at 2015-07-28 by begin*/	
                mHaptic.vibrate();
                handleDialButtonPressed();
                break;
			// Added by guofeiyoa for HQ01180250 begin
            case R.id.dctv_call:
				if ( mSubInfoList.size() < 1 ) {
					 Log.e(TAG+G,"error!!!mSubInfoList.size() < 1");
                     return;
				}
				mHaptic.vibrate();
				
				int subId = mSubInfoList.get(0).getSubscriptionId();
				if ( !getIpSwitchValue(mContext, subId )||isLocalCall(subId) ){
                    handleDialButtonPressed();
				} else {
				    Log.d(TAG+G,"ip dial");
				    onIPDialMenuItemSelected();
				}
                break;
			case R.id.dctv_call_1:
				if ( mSubInfoList.size() < 2 ) {
					 Log.e(TAG+G,"error!!!dial0:mSubInfoList.size() < 2");
                     return;
				}
				mHaptic.vibrate();
				
                int subId0 = mSubInfoList.get(0).getSubscriptionId();
				if ( !getIpSwitchValue(mContext, subId0) ||isLocalCall(subId0)){
                    handleDialButtonPressed(Constants.DIAL_NUMBER_INTENT_NORMAL,0);
				} else {
				    Log.d(TAG+G,"ip dial sim0");
				    handleDialButtonPressed(Constants.DIAL_NUMBER_INTENT_IP,0);
				}
				break;
		    case R.id.dctv_call_2:
				if ( mSubInfoList.size() < 2 ) {
					 Log.e(TAG+G,"error!!!dial1:mSubInfoList.size() < 2");
                     return;
				}
				mHaptic.vibrate();
				int subId1 = mSubInfoList.get(1).getSubscriptionId();
				if ( !getIpSwitchValue(mContext, subId1 ) ||isLocalCall(subId1)){
                    handleDialButtonPressed(Constants.DIAL_NUMBER_INTENT_NORMAL,1);
				} else {
				    Log.d(TAG+G,"ip dial sim1");
				    handleDialButtonPressed(Constants.DIAL_NUMBER_INTENT_IP,1);
				}
                break;
				
			case R.id.ib_dialpad:
				hideAndClearDialpad(true);
				break;
			case R.id.ib_menu:
				if (!isDigitsEmpty()) {
                    keyPressed(KeyEvent.KEYCODE_DEL);
				} else {
				
                    final PeopleActivity activity= (PeopleActivity)getActivity();
					activity.getDialerPlugIn().openMenu();
					
				}
				break;

			case R.id.pound:
                if ((curLanguageString.equals("ar") || curLanguageString.equals("fa"))
                        && SystemProperties.get("persist.sys.hq.arabic.numerals").equals("1")) {
                    Log.d(TAG, "onClick pound");
                } else {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_POUND);
                    mDigits.onKeyDown(KeyEvent.KEYCODE_POUND, event);
                }
				break;
			case R.id.star:
		if ((curLanguageString.equals("ar") || curLanguageString.equals("fa"))
	                    && SystemProperties.get("persist.sys.hq.arabic.numerals").equals("1")) {
			        Log.d(TAG, "onClick star");
			    } else {
			        KeyEvent event2 = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_STAR);
                  		mDigits.onKeyDown(KeyEvent.KEYCODE_STAR, event2);
			    }
				break;
			// End
            case R.id.deleteButton: {
                keyPressed(KeyEvent.KEYCODE_DEL);
                break;
            }
            case R.id.digits:
			// Added by guofeiyao for the new digits
            case R.id.det_digits:
            // / End
			{
	            if ((curLanguageString.equals("ar") || curLanguageString.equals("fa"))
	                    && SystemProperties.get("persist.sys.hq.arabic.numerals").equals("1")) {//HQ_hushunli 2015-11-02 add for HQ01303116
	                int selection = mDigits.getSelectionStart();
	                Log.d(TAG, "onClick, mDigits.getSelectionStart is " + selection);
	                if (selection != 0 && selection == mDigits.length()) {
	                    index = selection - 2;
	                } else if ((selection > 0) && (selection < mDigits.length())) {
	                    index = selection - 1;
	                } else if (selection == 0) {
	                    index = 0;
	                }
	            }
                if (!isDigitsEmpty()) {
                    mDigits.setCursorVisible(true);
                }
                break;
            }
            case R.id.dialpad_overflow: {
                /// M: for ALPS01964105, avoid user having the chance to
                 // instantly click this overflow button twice.
                mOverflowMenuButton.setEnabled(false);
                mOverflowPopupMenu.show();
                break;
            }
			// / Added by guofeiyao
			case R.id.rl_new:
				final CharSequence digitsNew = mDigits.getText();
                DialerUtils.startActivityWithErrorToast(getActivity(),
                        DialtactsActivity.getAddNumberToContactIntent(digitsNew));
				break;
			case R.id.rl_msg:
				final CharSequence digits = mDigits.getText();
                final Intent smsIntent = new Intent(Intent.ACTION_SENDTO,
                        Uri.fromParts(ContactsUtils.SCHEME_SMSTO, digits.toString(), null));
                smsIntent.setComponent(mSmsPackageComponentName);
                DialerUtils.startActivityWithErrorToast(getActivity(), smsIntent);
				break;
			// / End
            default: {
                Log.wtf(TAG, "Unexpected onClick() event from: " + view);
                return;
            }
        }
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

    @Override
    public boolean onLongClick(View view) {
        final Editable digits = mDigits.getText();
        final int id = view.getId();
        int key = -1;
        switch (id) {
            case R.id.deleteButton: {
                digits.clear();
                mTextStr = "";
                return true;
            }
			// / Added by guofeiyao for HQ01207444 begin
			case R.id.ib_menu: {
				if (!isDigitsEmpty()) {
                    digits.clear();
                    mTextStr = "";
				}
                return true;
            }
			// / Added by guofeiyao for HQ01207444 end
            case R.id.one: {
                // '1' may be already entered since we rely on onTouch() event for numeric buttons.
                // Just for safety we also check if the digits field is empty or not.
                if (isDigitsEmpty() || TextUtils.equals(mDigits.getText(), "1")) {
                    // We'll try to initiate voicemail and thus we want to remove irrelevant string.
                    removePreviousDigitIfPossible();

                    List<PhoneAccountHandle> subscriptionAccountHandles =
                            PhoneAccountUtils.getSubscriptionPhoneAccounts(getActivity());
                    boolean hasUserSelectedDefault = subscriptionAccountHandles.contains(
                            getTelecomManager().getUserSelectedOutgoingPhoneAccount());
                    boolean needsAccountDisambiguation = subscriptionAccountHandles.size() > 1
                            && !hasUserSelectedDefault;
                    if (needsAccountDisambiguation || isVoicemailAvailable()) {
                        // / Modified by guofeiyao 2016/1/5
                        // For select account to call voicemail
						
                        // On a multi-SIM phone, if the user has not selected a default
                        // subscription, initiate a call to voicemail so they can select an account
                        // from the "Call with" dialog.
                        //callVoicemail();
						if ( 2==mSubCount) {
						    showDialog();
						} else {
                            callVoicemail();
						}
						// / End
                    } else if (getActivity() != null) {
                        // Voicemail is unavailable maybe because Airplane mode is turned on.
                        // Check the current status and show the most appropriate error message.
                        final boolean isAirplaneModeOn =
                                Settings.System.getInt(getActivity().getContentResolver(),
                                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
                        if (isAirplaneModeOn) {
                            DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                                    R.string.dialog_voicemail_airplane_mode_message);
                            dialogFragment.show(getFragmentManager(),
                                    "voicemail_request_during_airplane_mode");
                        } else {
                            DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                                    R.string.dialog_voicemail_not_ready_message);
                            dialogFragment.show(getFragmentManager(), "voicemail_not_ready");
                        }
                    }
                    return true;
                }
                return false;
            }

			//add by zhangjinqiang for HQ01508475 at 20151207 -start
			case R.id.three:{
                callSpecialNumber();
                removePreviousDigitIfPossible();
				return true;
            }
			//add by zhangjinqiang for HQ01508475 at 20151207 -end
						
            //add by zhaizhanfeng for HQ01311701 at 151101 start
            case R.id.nine: {
                 if (SystemProperties.get("ro.hq.call.emergency.number").equals("1")){
                       callEmergencyNumber();
                       removePreviousDigitIfPossible();
                       return true;
                 }else {
                       return false;
                 }
            }
            //add by zhaizhanfeng for HQ01311701 at 151101 end
            case R.id.zero: {
                // Remove tentative input ('0') done by onTouch().
                removePreviousDigitIfPossible();
                keyPressed(KeyEvent.KEYCODE_PLUS);

                // Stop tone immediately
                stopTone();
                mPressedDialpadKeys.remove(view);

                return true;
            }
			// / Added by guofeiyao for HQ01207444 begin
			case R.id.star: {
				if (mDigits.getSelectionStart() == 0) return false;
				// Remove tentative input ('*') done by onTouch().
                //removePreviousDigitIfPossible();
                keyPressed(KeyEvent.KEYCODE_COMMA);

                // Stop tone immediately
                stopTone();
                mPressedDialpadKeys.remove(view);
                return true;
            }
			case R.id.pound: {
				if (mDigits.getSelectionStart() == 0) return false;
				// Remove tentative input ('#') done by onTouch().
                //removePreviousDigitIfPossible();
                keyPressed(KeyEvent.KEYCODE_SEMICOLON);

                // Stop tone immediately
                stopTone();
                mPressedDialpadKeys.remove(view);
                return true;
            }
			
            case R.id.digits:
			case R.id.det_digits:
            // / Added by guofeiyao for HQ01207444 end
			{
                // Right now EditText does not show the "paste" option when cursor is not visible.
                // To show that, make the cursor visible, and return false, letting the EditText
                // show the option by itself.
                mDigits.setCursorVisible(true);
                return false;
            }
        }

        return false;
    }

    /**
     * Remove the digit just before the current position. This can be used if we want to replace
     * the previous digit or cancel previously entered character.
     */
    private void removePreviousDigitIfPossible() {
        final int currentPosition = mDigits.getSelectionStart();
        if (currentPosition > 0) {
            mDigits.setSelection(currentPosition);
            mDigits.getText().delete(currentPosition - 1, currentPosition);
        }
    }

    public void callVoicemail() {
        DialerUtils.startActivityWithErrorToast(getActivity(), CallUtil.getVoicemailIntent());
        hideAndClearDialpad(false);
    }

    //add by zhaizhanfeng for HQ01311701 at 151101 start
    public void callEmergencyNumber(){
        /*Intent intent = new Intent(Intent.ACTION_CALL,Uri.parse("tel:158"));  
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);     
        startActivity(intent); */
        Intent intent = new Intent("android.intent.action.CALL_PRIVILEGED",Uri.parse("tel:"+112));    
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);     
        startActivity(intent); 
        hideAndClearDialpad(false);
    }
    //add by zhaizhanfeng for HQ01311701 at 151101 end

	//add by zhangjinqiang for HQ01508475 at 20151207 -start
    public void callSpecialNumber(){  
        Intent intent = new Intent(Intent.ACTION_CALL,Uri.parse("tel:*611"));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);     
        startActivity(intent); 
        hideAndClearDialpad(false);
    }
    //add by zhangjinqiang for HQ01508475 at 20151207 -end

    private void hideAndClearDialpad(boolean animate) {
	// / Modified by guofeiyao begin
//        ((DialtactsActivity) getActivity()).hideDialpadFragment(animate, true);
		((PeopleActivity) getActivity()).getDialerPlugIn().hideDialpadFragment(animate, false);
    }

	// Added by guofeiyao for HQ01257948
	private void launchVoiceSetting(int a,int b){
        if (a == b){
           Intent phoneAccountSettingsIntent =
                 new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
           phoneAccountSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		   mContext.startActivity(phoneAccountSettingsIntent);						
		}
	}
	// / End

    public static class ErrorDialogFragment extends DialogFragment {
        private int mTitleResId;
        private int mMessageResId;

        private static final String ARG_TITLE_RES_ID = "argTitleResId";
        private static final String ARG_MESSAGE_RES_ID = "argMessageResId";

        public static ErrorDialogFragment newInstance(int messageResId) {
            return newInstance(0, messageResId);
        }

        public static ErrorDialogFragment newInstance(int titleResId, int messageResId) {
            final ErrorDialogFragment fragment = new ErrorDialogFragment();
            final Bundle args = new Bundle();
            args.putInt(ARG_TITLE_RES_ID, titleResId);
            args.putInt(ARG_MESSAGE_RES_ID, messageResId);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mTitleResId = getArguments().getInt(ARG_TITLE_RES_ID);
            mMessageResId = getArguments().getInt(ARG_MESSAGE_RES_ID);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            if (mTitleResId != 0) {
                builder.setTitle(mTitleResId);
            }
            if (mMessageResId != 0) {
                builder.setMessage(mMessageResId);
            }
            builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dismissAllowingStateLoss();

								// / Added by guofeiyao for HQ01257948
								
			if (mMessageResId == R.string.dialog_voicemail_not_ready_message){
				//add by zhaizhanfeng for HQ01701052 about single card call settings at 160128 start
				if ( !((TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE)).isMultiSimEnabled() ) {
					Intent callSettingsIntent = new Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS);
					callSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(callSettingsIntent);
				} else {
				//add by zhaizhanfeng for HQ01701052 about single card call settings at 160128 end
					Intent phoneAccountSettingsIntent =
					new Intent(android.telecom.TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
					phoneAccountSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(phoneAccountSettingsIntent);
				}
			}

								// / End
                            }
                    });
            return builder.create();
        }
    }

    /**
     * In most cases, when the dial button is pressed, there is a
     * number in digits area. Pack it in the intent, start the
     * outgoing call broadcast as a separate task and finish this
     * activity.
     *
     * When there is no digit and the phone is CDMA and off hook,
     * we're sending a blank flash for CDMA. CDMA networks use Flash
     * messages when special processing needs to be done, mainly for
     * 3-way or call waiting scenarios. Presumably, here we're in a
     * special 3-way scenario where the network needs a blank flash
     * before being able to add the new participant.  (This is not the
     * case with all 3-way calls, just certain CDMA infrastructures.)
     *
     * Otherwise, there is no digit, display the last dialed
     * number. Don't finish since the user may want to edit it. The
     * user needs to press the dial button again, to dial it (general
     * case described above).
     */
    private void handleDialButtonPressed() {
        /// M: [Ip Dial] add ip dial
        handleDialButtonPressed(Constants.DIAL_NUMBER_INTENT_NORMAL);
    }
    
    private void handleDialButtonPressed(int type) {
        if (isDigitsEmpty()) { // No number entered.
            handleDialButtonClickWithEmptyDigits();
        } else {
            String number = mDigits.getText().toString();
            
            
            number =CallUtil.claroSpecialOperator(number,getActivity());
            
//            hq_hxb modified by  for  HQ01308688 begin
		if(SystemProperties.get("ro.config.emergency_call_taiwan").equals("1")){
		// xujunyong modified start
//		final String number;
//		checkIfSimSert();
		String mMccMncOp=PhoneNumberUtils.getOperatorMccmnc();
		String sim1_State = PhoneNumberUtils.getSimStatusForSlot(0);
		String sim2_State = PhoneNumberUtils.getSimStatusForSlot(1);		
		String sim1_MccMnc = PhoneNumberUtils.getSimMccMnc(0);
		String sim2_MccMnc = PhoneNumberUtils.getSimMccMnc(1);
		boolean is_110_dial_as_112=false;
		
		Log.d(TAG+" hxb", "mMccMncOp::"+mMccMncOp);
		Log.d(TAG+" hxb", "sim1_MccMnc::"+sim1_MccMnc);
		Log.d(TAG+" hxb", "sim2_MccMnc::"+sim2_MccMnc);
		Log.d(TAG+" hxb", "sim1_State::"+sim1_State);
		Log.d(TAG+" hxb", "sim2_State::"+sim2_State);

		if(sim1_State.equals("ABSENT")&&sim2_State.equals("ABSENT")&&mMccMncOp.startsWith("466")){
			is_110_dial_as_112=true;
		}else if((!sim1_State.equals("READY"))&&(!sim2_State.equals("READY"))&&Settings.System.getInt(mContext.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) == 1){
			is_110_dial_as_112=true;
		}

//		String tmpNumber = mDigits.getText().toString();
		String tmpNumber =number;
		if (tmpNumber != null) {
			tmpNumber = tmpNumber.replaceAll(" ", "").replaceAll("-", "");
		}
		
		if(is_110_dial_as_112
		&& ("119".equals(tmpNumber) || "110".equals(tmpNumber))) {
			number = "112";
			} else {
				number = tmpNumber;
			} //end xujunyong					
		}else{
		}
//            hq_hxb modified by  for  HQ01308688 end
            
            // "persist.radio.otaspdial" is a temporary hack needed for one carrier's automated
            // test equipment.
            // TODO: clean it up.
            if (number != null
                    && !TextUtils.isEmpty(mProhibitedPhoneNumberRegexp)
                    && number.matches(mProhibitedPhoneNumberRegexp)) {
                Log.i(TAG, "The phone number is prohibited explicitly by a rule.");
                if (getActivity() != null) {
                    DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                            R.string.dialog_phone_call_prohibited_message);
                    dialogFragment.show(getFragmentManager(), "phone_prohibited_dialog");
                }

                // Clear the digits just in case.
                clearDialpad();
            } else {
                final Intent intent;

				// / Annotated by guofeiyao begin
				
                /** M: [Ip Dial] check the type of call @{ */
				/*
                if (type != Constants.DIAL_NUMBER_INTENT_NORMAL) {
                    intent = CallUtil.getCallIntent(CallUtil.getCallUri(number),
                            (getActivity() instanceof DialtactsActivity ?
                                    ((DialtactsActivity) getActivity()).getCallOrigin() : null),
                            type);
                } else {
                    intent = CallUtil.getCallIntent(number,
                            (getActivity() instanceof DialtactsActivity ?
                                    ((DialtactsActivity) getActivity()).getCallOrigin() : null));
                }
                */
                /** @} */

				if (type != Constants.DIAL_NUMBER_INTENT_NORMAL) {
                    intent = CallUtil.getCallIntent(CallUtil.getCallUri(number),
                            (getActivity() instanceof PeopleActivity ?
                                    ((PeopleActivity) getActivity()).getDialerPlugIn().getCallOrigin() : null),
                            type);
                } else {
                    intent = CallUtil.getCallIntent(number,
                            (getActivity() instanceof PeopleActivity?
                                    ((PeopleActivity) getActivity()).getDialerPlugIn().getCallOrigin() : null));
                }

				
                DialerUtils.startActivityWithErrorToast(getActivity(), intent);
                //hideAndClearDialpad(false);
                
                // / End

                clearDialpad();//HQ_wuruijun add for HQ01310536 & HQ01310561
            }
        }
    }

    // / Added by guofeiyao for HQ01180250 begin
    private void handleDialButtonPressed(int type,int id) {
        if (isDigitsEmpty()) { // No number entered.
            handleDialButtonClickWithEmptyDigits();
        } else {
            String number = mDigits.getText().toString();

//            add by hq_tanghuaizhe for  ip call start
            if(type==Constants.DIAL_NUMBER_INTENT_IP){
            	number=number.replace(" ", "");
            	
            	if(number.startsWith("0086")){
            	
            		number=number.substring(4);
            	
            	}
            		if(number.startsWith("+86")){
            			number=number.replace("+86", "");
            		}
            		
            		if(number.startsWith("+")){
            			number=number.replace("+", "00");
            		}
            	}
//            end
            
            // "persist.radio.otaspdial" is a temporary hack needed for one carrier's automated
            // test equipment.
            // TODO: clean it up.
            if (number != null
                    && !TextUtils.isEmpty(mProhibitedPhoneNumberRegexp)
                    && number.matches(mProhibitedPhoneNumberRegexp)) {
                Log.i(TAG, "The phone number is prohibited explicitly by a rule.");
                if (getActivity() != null) {
                    DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                            R.string.dialog_phone_call_prohibited_message);
                    dialogFragment.show(getFragmentManager(), "phone_prohibited_dialog");
                }

                // Clear the digits just in case.
                clearDialpad();
            } else {
                    Intent intent = CallUtil.getCallIntent(number,
                          (getActivity() instanceof PeopleActivity?((PeopleActivity) getActivity()).getDialerPlugIn().getCallOrigin() : null));
                    if ((type & Constants.DIAL_NUMBER_INTENT_IP) != 0) {
                        intent.putExtra(Constants.EXTRA_IS_IP_DIAL, true);
                    }
                    intent.putExtra("slot_id", id);
                    DialerUtils.startActivityWithErrorToast(getActivity(), intent);
                    clearDialpad();
            }
        }
    }

public static void makeDial(Context mContext,String number) {
        if ( number == null || number.length() == 0 ) { // No number entered.
            return;
        }

	    int type = Constants.DIAL_NUMBER_INTENT_NORMAL;
		/*
		int b = Settings.System.getInt(mContext.getApplicationContext().getContentResolver(), "ip_switch", 0);
        if ( b == 1) {
             type = Constants.DIAL_NUMBER_INTENT_IP;
		}
		*/
		
		
//            add by hq_tanghuaizhe for  ip call start
            if(type==Constants.DIAL_NUMBER_INTENT_IP){
            	number=number.replace(" ", "");
            	
            	if(number.startsWith("0086")){
            	
            		number=number.substring(4);
            	
            	}
            		if(number.startsWith("+86")){
            			number=number.replace("+86", "");
            		}
            		
            		if(number.startsWith("+")){
            			number=number.replace("+", "00");
            		}
            	}
//            end


            String mProhibitedPhoneNumberRegexp = mContext.getResources().getString(
                R.string.config_prohibited_phone_number_regexp);
            // "persist.radio.otaspdial" is a temporary hack needed for one carrier's automated
            // test equipment.
            // TODO: clean it up.
            if (number != null
                    && !TextUtils.isEmpty(mProhibitedPhoneNumberRegexp)
                    && number.matches(mProhibitedPhoneNumberRegexp)) {
                Log.i(TAG, "The phone number is prohibited explicitly by a rule.");
                
            } else {

				Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED);
			    
                intent.setData(Uri.parse("tel:" + number));

/*
		    	if ((type & Constants.DIAL_NUMBER_INTENT_IP) != 0) {
                    intent.putExtra(Constants.EXTRA_IS_IP_DIAL, true);
                }
                */
			
			    mContext.startActivity(intent);

            }
        
    }

public static void makeDialWithPhonehandle(Context mContext,String number,PhoneAccountHandle handle) {
        if ( number == null || number.length() == 0 ) { // No number entered.
            return;
        }


	    int type = Constants.DIAL_NUMBER_INTENT_NORMAL;
		/*
		int b = Settings.System.getInt(mContext.getApplicationContext().getContentResolver(), "ip_switch", 0);
        if ( b == 1) {
             type = Constants.DIAL_NUMBER_INTENT_IP;
		}
		*/
		
//            add by hq_tanghuaizhe for  ip call start
            if(type==Constants.DIAL_NUMBER_INTENT_IP){
            	number=number.replace(" ", "");
            	
            	if(number.startsWith("0086")){
            	
            		number=number.substring(4);
            	
            	}
            		if(number.startsWith("+86")){
            			number=number.replace("+86", "");
            		}
            		
            		if(number.startsWith("+")){
            			number=number.replace("+", "00");
            		}
            	}
//            end


            String mProhibitedPhoneNumberRegexp = mContext.getResources().getString(
                R.string.config_prohibited_phone_number_regexp);
            // "persist.radio.otaspdial" is a temporary hack needed for one carrier's automated
            // test equipment.
            // TODO: clean it up.
            if (number != null
                    && !TextUtils.isEmpty(mProhibitedPhoneNumberRegexp)
                    && number.matches(mProhibitedPhoneNumberRegexp)) {
                Log.i(TAG, "The phone number is prohibited explicitly by a rule.");
                
            } else {

				Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED);
			    intent.putExtra("phone_handle", handle);
                intent.setData(Uri.parse("tel:" + number));

/*
		    	if ((type & Constants.DIAL_NUMBER_INTENT_IP) != 0) {
                    intent.putExtra(Constants.EXTRA_IS_IP_DIAL, true);
                }
                */
			
			    mContext.startActivity(intent);

            }
        
    }

    // / Created by guofeiyao
    private View aboveBtnContainer;
	private View aboveBtnLine;
    public void updateAboveBtn(boolean isShow) {
        if ( null == aboveBtnContainer ) {
            Log.i("guofeiyao_updateAboveBtn(boolean isShow)","aboveBtnContainer is null !!!");
			return;
        }
		if ( isShow ) {
             aboveBtnContainer.setVisibility(View.VISIBLE);
			 aboveBtnLine.setVisibility(View.VISIBLE);
		} else {
             aboveBtnContainer.setVisibility(View.GONE);
			 aboveBtnLine.setVisibility(View.GONE);
		}
	}
    // / End

    public void clearDialpad() {
        if (mDigits != null){
            mDigits.getText().clear();
        }
    }

    public void handleDialButtonClickWithEmptyDigits() {
        /// M: Remove the cdma check @{
        // MTK Support the fake hold, so remove the flash.
        //if (phoneIsCdma() && isPhoneInUse()) {
            // TODO: Move this logic into services/Telephony
            //
            // This is really CDMA specific. On GSM is it possible
            // to be off hook and wanted to add a 3rd party using
            // the redial feature.
            //startActivity(newFlashIntent());
        //} else {
        /// @}

			/*HQ_xionghaifeng Add for contact crash 20150817*/
            if (!TextUtils.isEmpty(mLastNumberDialed) && mDigits != null) {
                // Recall the last number dialed.
                mDigits.setText(mLastNumberDialed);

                // ...and move the cursor to the end of the digits string,
                // so you'll be able to delete digits using the Delete
                // button (just as if you had typed the number manually.)
                //
                // Note we use mDigits.getText().length() here, not
                // mLastNumberDialed.length(), since the EditText widget now
                // contains a *formatted* version of mLastNumberDialed (due to
                // mTextWatcher) and its length may have changed.
                mDigits.setSelection(mDigits.getText().length());
            } else {
                // There's no "last number dialed" or the
                // background query is still running. There's
                // nothing useful for the Dial button to do in
                // this case.  Note: with a soft dial button, this
                // can never happens since the dial button is
                // disabled under these conditons.
                playTone(ToneGenerator.TONE_PROP_NACK);
            }
        //}
    }

    /**
     * Plays the specified tone for TONE_LENGTH_MS milliseconds.
     */
    private void playTone(int tone) {
        playTone(tone, TONE_LENGTH_MS);
    }

    /**
     * Play the specified tone for the specified milliseconds
     *
     * The tone is played locally, using the audio stream for phone calls.
     * Tones are played only if the "Audible touch tones" user preference
     * is checked, and are NOT played if the device is in silent mode.
     *
     * The tone length can be -1, meaning "keep playing the tone." If the caller does so, it should
     * call stopTone() afterward.
     *
     * @param tone a tone code from {@link ToneGenerator}
     * @param durationMs tone length.
     */
    private void playTone(int tone, int durationMs) {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }

        // Also do nothing if the phone is in silent mode.
        // We need to re-check the ringer mode for *every* playTone()
        // call, rather than keeping a local flag that's updated in
        // onResume(), since it's possible to toggle silent mode without
        // leaving the current activity (via the ENDCALL-longpress menu.)
        if ((mRingerMode == AudioManager.RINGER_MODE_SILENT)
            || (mRingerMode == AudioManager.RINGER_MODE_VIBRATE)) {
            return;
        }

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(TAG, "playTone: mToneGenerator == null, tone: " + tone);
                return;
            }

            // Start the new tone (will stop any playing tone)
            mToneGenerator.startTone(tone, durationMs);
        }
    }

    /**
     * Stop the tone if it is played.
     */
    private void stopTone() {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(TAG, "stopTone: mToneGenerator == null");
                return;
            }
            mToneGenerator.stopTone();
        }
    }

    /**
     * Brings up the "dialpad chooser" UI in place of the usual Dialer
     * elements (the textfield/button and the dialpad underneath).
     *
     * We show this UI if the user brings up the Dialer while a call is
     * already in progress, since there's a good chance we got here
     * accidentally (and the user really wanted the in-call dialpad instead).
     * So in this situation we display an intermediate UI that lets the user
     * explicitly choose between the in-call dialpad ("Use touch tone
     * keypad") and the regular Dialer ("Add call").  (Or, the option "Return
     * to call in progress" just goes back to the in-call UI with no dialpad
     * at all.)
     *
     * @param enabled If true, show the "dialpad chooser" instead
     *                of the regular Dialer UI
     */
    private void showDialpadChooser(boolean enabled) {
        if (getActivity() == null) {
            return;
        }
        // Check if onCreateView() is already called by checking one of View objects.
        if (!isLayoutReady()) {
            return;
        }

        if (enabled) {
            Log.d(TAG, "Showing dialpad chooser!");
            if (mDialpadView != null) {
                mDialpadView.setVisibility(View.GONE);
            }

            if (null != mFloatingActionButtonController) {
                mFloatingActionButtonController.setVisible(false);
				Log.e("guofeiyao","mFloatingActionButtonController.setVisible(false);");
            }
            mDialpadChooser.setVisibility(View.VISIBLE);

            // Instantiate the DialpadChooserAdapter and hook it up to the
            // ListView.  We do this only once.
            if (mDialpadChooserAdapter == null) {
                mDialpadChooserAdapter = new DialpadChooserAdapter(getActivity());
            }
            mDialpadChooser.setAdapter(mDialpadChooserAdapter);
        } else {
            Log.d(TAG, "Displaying normal Dialer UI.");
            if (mDialpadView != null) {
                mDialpadView.setVisibility(View.VISIBLE);
            } else {
                mDigits.setVisibility(View.VISIBLE);
            }

            /** M: If the scaleOut() of FloatingActionButtonController be
             *  called at previous, the floating button and container would
             *  all be set to GONE. But the setVisible() method only set the
             *  floating container to visible. So that the floating button is
             *  GONE yet. So, it should call the scaleIn() to make sure all of
             *  them be set to visible. @{*/
            /*
            mFloatingActionButtonController.setVisible(true);
            */
            if (null != mFloatingActionButtonController) {
				// / Modified by guofeiyao
//                mFloatingActionButtonController.scaleIn(0);
                  mFloatingActionButtonController.setVisible(true);
                // / End
            }
            /** @} */
            mDialpadChooser.setVisibility(View.GONE);
        }

        /// M: for plug-in @{
        ExtensionManager.getInstance().getDialPadExtension().showDialpadChooser(enabled);
        /// @}
    }

    /**
     * @return true if we're currently showing the "dialpad chooser" UI.
     */
    private boolean isDialpadChooserVisible() {
        return mDialpadChooser.getVisibility() == View.VISIBLE;
    }

    /**
     * Simple list adapter, binding to an icon + text label
     * for each item in the "dialpad chooser" list.
     */
    private static class DialpadChooserAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        // Simple struct for a single "choice" item.
        static class ChoiceItem {
            String text;
            Bitmap icon;
            int id;

            public ChoiceItem(String s, Bitmap b, int i) {
                text = s;
                icon = b;
                id = i;
            }
        }

        // IDs for the possible "choices":
        static final int DIALPAD_CHOICE_USE_DTMF_DIALPAD = 101;
        static final int DIALPAD_CHOICE_RETURN_TO_CALL = 102;
        static final int DIALPAD_CHOICE_ADD_NEW_CALL = 103;

        private static final int NUM_ITEMS = 3;
        private ChoiceItem mChoiceItems[] = new ChoiceItem[NUM_ITEMS];

        public DialpadChooserAdapter(Context context) {
            // Cache the LayoutInflate to avoid asking for a new one each time.
            mInflater = LayoutInflater.from(context);

            // Initialize the possible choices.
            // TODO: could this be specified entirely in XML?

            // - "Use touch tone keypad"
            mChoiceItems[0] = new ChoiceItem(
                    context.getString(R.string.dialer_useDtmfDialpad),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_tt_keypad),
                    DIALPAD_CHOICE_USE_DTMF_DIALPAD);

            // - "Return to call in progress"
            mChoiceItems[1] = new ChoiceItem(
                    context.getString(R.string.dialer_returnToInCallScreen),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_current_call),
                    DIALPAD_CHOICE_RETURN_TO_CALL);

            // - "Add call"
            mChoiceItems[2] = new ChoiceItem(
                    context.getString(R.string.dialer_addAnotherCall),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_add_call),
                    DIALPAD_CHOICE_ADD_NEW_CALL);
        }

        @Override
        public int getCount() {
            return NUM_ITEMS;
        }

        /**
         * Return the ChoiceItem for a given position.
         */
        @Override
        public Object getItem(int position) {
            return mChoiceItems[position];
        }

        /**
         * Return a unique ID for each possible choice.
         */
        @Override
        public long getItemId(int position) {
            return position;
        }

        /**
         * Make a view for each row.
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // When convertView is non-null, we can reuse it (there's no need
            // to reinflate it.)
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.dialpad_chooser_list_item, null);
            }

            TextView text = (TextView) convertView.findViewById(R.id.text);
            text.setText(mChoiceItems[position].text);

            ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
            icon.setImageBitmap(mChoiceItems[position].icon);

            return convertView;
        }
    }

    /**
     * Handle clicks from the dialpad chooser.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        DialpadChooserAdapter.ChoiceItem item =
                (DialpadChooserAdapter.ChoiceItem) parent.getItemAtPosition(position);
        int itemId = item.id;
        switch (itemId) {
            case DialpadChooserAdapter.DIALPAD_CHOICE_USE_DTMF_DIALPAD:
                // Log.i(TAG, "DIALPAD_CHOICE_USE_DTMF_DIALPAD");
                // Fire off an intent to go back to the in-call UI
                // with the dialpad visible.
                //add by zhangjinqiang for dismiss callbuttonfragment
                Settings.System.putInt(getActivity().getApplicationContext().getContentResolver(), "displayCallButton", 0);
				//add by zhangjinqiang end
                returnToInCallScreen(true);
                break;

            case DialpadChooserAdapter.DIALPAD_CHOICE_RETURN_TO_CALL:
                // Log.i(TAG, "DIALPAD_CHOICE_RETURN_TO_CALL");
                // Fire off an intent to go back to the in-call UI
                // (with the dialpad hidden).
                returnToInCallScreen(false);
                break;

            case DialpadChooserAdapter.DIALPAD_CHOICE_ADD_NEW_CALL:
                // Log.i(TAG, "DIALPAD_CHOICE_ADD_NEW_CALL");
                // Ok, guess the user really did want to be here (in the
                // regular Dialer) after all.  Bring back the normal Dialer UI.
                showDialpadChooser(false);
                break;

            default:
                Log.w(TAG, "onItemClick: unexpected itemId: " + itemId);
                break;
        }
    }

    /**
     * Returns to the in-call UI (where there's presumably a call in
     * progress) in response to the user selecting "use touch tone keypad"
     * or "return to call" from the dialpad chooser.
     */
    private void returnToInCallScreen(boolean showDialpad) {
        getTelecomManager().showInCallScreen(showDialpad);

        // Finally, finish() ourselves so that we don't stay on the
        // activity stack.
        // Note that we do this whether or not the showCallScreenWithDialpad()
        // call above had any effect or not!  (That call is a no-op if the
        // phone is idle, which can happen if the current call ends while
        // the dialpad chooser is up.  In this case we can't show the
        // InCallScreen, and there's no point staying here in the Dialer,
        // so we just take the user back where he came from...)
        getActivity().finish();
    }

    /**
     * @return true if the phone is "in use", meaning that at least one line
     *              is active (ie. off hook or ringing or dialing, or on hold).
     */
    public boolean isPhoneInUse() {
        return getTelecomManager().isInCall();
    }

    /**
     * @return true if the phone is a CDMA phone type
     */
    private boolean phoneIsCdma() {
        return getTelephonyManager().getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_add_contact: {
                final CharSequence digits = mDigits.getText();
                DialerUtils.startActivityWithErrorToast(getActivity(),
                        DialtactsActivity.getAddNumberToContactIntent(digits));
                return true;
            }
            case R.id.menu_2s_pause:
                updateDialString(PAUSE);
                return true;
            case R.id.menu_add_wait:
                updateDialString(WAIT);
                return true;
            case R.id.menu_send_message: {
                final CharSequence digits = mDigits.getText();
                final Intent smsIntent = new Intent(Intent.ACTION_SENDTO,
                        Uri.fromParts(ContactsUtils.SCHEME_SMSTO, digits.toString(), null));
                smsIntent.setComponent(mSmsPackageComponentName);
                DialerUtils.startActivityWithErrorToast(getActivity(), smsIntent);
                return true;
            }
            /** M: [Ip Dial] click ip dial on popup menu @{ */
            case R.id.menu_ip_dial:
                return onIPDialMenuItemSelected();
            /** @} */
            /** M: [VoLTE ConfCall] handle conference call menu. @{ */
            case R.id.menu_volte_conf_call:
                Activity activity = getActivity();
                if (activity != null) {
                    DialerVolteUtils.handleMenuVolteConfCall(activity);
                }
                return true;
            /** @} */
            default:
                return false;
        }
    }

    @Override
    public void onDismiss(PopupMenu popupMenu) {
        mOverflowMenuButton.setEnabled(true);
    }

    /**
     * Updates the dial string (mDigits) after inserting a Pause character (,)
     * or Wait character (;).
     */
    private void updateDialString(char newDigit) {
        if (newDigit != WAIT && newDigit != PAUSE) {
            throw new IllegalArgumentException(
                    "Not expected for anything other than PAUSE & WAIT");
        }

        int selectionStart;
        int selectionEnd;

        // SpannableStringBuilder editable_text = new SpannableStringBuilder(mDigits.getText());
        int anchor = mDigits.getSelectionStart();
        int point = mDigits.getSelectionEnd();

        selectionStart = Math.min(anchor, point);
        selectionEnd = Math.max(anchor, point);

        if (selectionStart == -1) {
            selectionStart = selectionEnd = mDigits.length();
        }

        Editable digits = mDigits.getText();

        if (canAddDigit(digits, selectionStart, selectionEnd, newDigit)) {
            digits.replace(selectionStart, selectionEnd, Character.toString(newDigit));

            if (selectionStart != selectionEnd) {
              // Unselect: back to a regular cursor, just pass the character inserted.
              mDigits.setSelection(selectionStart + 1);
            }
        }
    }

    /**
     * Update the enabledness of the "Dial" and "Backspace" buttons if applicable.
     */
    private void updateDeleteButtonEnabledState() {
        if (getActivity() == null) {
            return;
        }
        final boolean digitsNotEmpty = !isDigitsEmpty();
		
// / Annotated by guofeiyao for HQ01207444
//        mDelete.setEnabled(digitsNotEmpty);
    }

    /**
     * Handle transitions for the menu button depending on the state of the digits edit text.
     * Transition out when going from digits to no digits and transition in when the first digit
     * is pressed.
     * @param transitionIn True if transitioning in, False if transitioning out
     */
    private void updateMenuOverflowButton(boolean transitionIn) {
        /** M: [VoLTE ConfCall] Always show overflow menu button for conf call. @{ */
        if (DialerVolteUtils.isVoLTEConfCallEnable(getActivity())) {
            return;
        }
        /** @} */
        mOverflowMenuButton = mDialpadView.getOverflowMenuButton();
        if (transitionIn) {
            AnimUtils.fadeIn(mOverflowMenuButton, AnimUtils.DEFAULT_DURATION);
        } else {
            AnimUtils.fadeOut(mOverflowMenuButton, AnimUtils.DEFAULT_DURATION);
        }
    }

    /**
     * Check if voicemail is enabled/accessible.
     *
     * @return true if voicemail is enabled and accessible. Note that this can be false
     * "temporarily" after the app boot.
     * @see TelecomManager#hasVoiceMailNumber(PhoneAccountHandle)
     */
    private boolean isVoicemailAvailable() {
        try {
            PhoneAccountHandle defaultUserSelectedAccount =
                    getTelecomManager().getUserSelectedOutgoingPhoneAccount();
			//add by zhaizhanfeng for HQ01764150 at 160223 start
			List<PhoneAccountHandle> allUserSelectedAccountList = new ArrayList<PhoneAccountHandle>();
			allUserSelectedAccountList = getTelecomManager().getAllPhoneAccountHandles();
            int allUserSelectedAccountSize = getTelecomManager().getAllPhoneAccountsCount();
			Log.d("zhaizhanfeng","allUserSelectedAccountSize = "+ allUserSelectedAccountSize);
			//add by zhaizhanfeng for HQ01764150 at 160223 end
			if (defaultUserSelectedAccount == null) {
                // In a single-SIM phone, there is no default outgoing phone account selected by
                // the user, so just call TelephonyManager#getVoicemailNumber directly.
                //modify by zhaizhanfeng for HQ01701052 about voicemail from global xml at 160128
                return ((getTelephonyManager().getVoiceMailNumber() != null) && (!"".equals(getTelephonyManager().getVoiceMailNumber())));
                //return getTelephonyManager().getVoiceMailNumber() != null;
            } else {
				//add by zhaizhanfeng for HQ01764150 at 160223 start
				for(int i = 0; i< allUserSelectedAccountSize ; i++){
					Log.d("zhaizhanfeng","i = "+ i +";flag = "+ getTelecomManager().hasVoiceMailNumber(allUserSelectedAccountList.get(i)));
				    if(getTelecomManager().hasVoiceMailNumber(allUserSelectedAccountList.get(i))){
					    return true;
				    }
			    }
				Log.d("zhaizhanfeng","isVoicemailAvailable.double card="+ getTelecomManager().hasVoiceMailNumber(defaultUserSelectedAccount));
				//add by zhaizhanfeng for HQ01764150 at 160223 end
				return getTelecomManager().hasVoiceMailNumber(defaultUserSelectedAccount);
            }
        } catch (SecurityException se) {
            // Possibly no READ_PHONE_STATE privilege.
            Log.w(TAG, "SecurityException is thrown. Maybe privilege isn't sufficient.");
        }
        return false;
    }

    /**
     * Returns true of the newDigit parameter can be added at the current selection
     * point, otherwise returns false.
     * Only prevents input of WAIT and PAUSE digits at an unsupported position.
     * Fails early if start == -1 or start is larger than end.
     */
    @VisibleForTesting
    /* package */ static boolean canAddDigit(CharSequence digits, int start, int end,
                                             char newDigit) {
        if(newDigit != WAIT && newDigit != PAUSE) {
            throw new IllegalArgumentException(
                    "Should not be called for anything other than PAUSE & WAIT");
        }

        // False if no selection, or selection is reversed (end < start)
        if (start == -1 || end < start) {
            return false;
        }

        // unsupported selection-out-of-bounds state
        if (start > digits.length() || end > digits.length()) return false;

        // Special digit cannot be the first digit
        if (start == 0) return false;

        if (newDigit == WAIT) {
            // preceding char is ';' (WAIT)
            if (digits.charAt(start - 1) == WAIT) return false;

            // next char is ';' (WAIT)
            if ((digits.length() > end) && (digits.charAt(end) == WAIT)) return false;
        }

        return true;
    }

    /**
     * @return true if the widget with the phone number digits is empty.
     */
    private boolean isDigitsEmpty() {
	/*HQ_zhangjing modified for dialer error begin*/
        //return mDigits.length() == 0;
		//return (mDigits != null && mDigits.length() == 0);

		// / Modified by guofeiyao
		return mDigits == null || mDigits.length() == 0;
		// / End

	/*HQ_zhangjing modified for dialer error end*/
    }

    /**
     * Starts the asyn query to get the last dialed/outgoing
     * number. When the background query finishes, mLastNumberDialed
     * is set to the last dialed number or an empty string if none
     * exists yet.
     */
    private void queryLastOutgoingCall() {
        mLastNumberDialed = EMPTY_NUMBER;
        CallLogAsync.GetLastOutgoingCallArgs lastCallArgs =
                new CallLogAsync.GetLastOutgoingCallArgs(
                    getActivity(),
                    new CallLogAsync.OnLastOutgoingCallComplete() {
                        @Override
                        public void lastOutgoingCall(String number) {
                            // TODO: Filter out emergency numbers if
                            // the carrier does not want redial for
                            // these.
                            // If the fragment has already been detached since the last time
                            // we called queryLastOutgoingCall in onResume there is no point
                            // doing anything here.
                            if (getActivity() == null) return;
                            mLastNumberDialed = number;
                            updateDeleteButtonEnabledState();
                        }
                    });
        mCallLog.getLastOutgoingCall(lastCallArgs);
    }

    private Intent newFlashIntent() {
        final Intent intent = CallUtil.getCallIntent(EMPTY_NUMBER);
        intent.putExtra(EXTRA_SEND_EMPTY_FLASH, true);
        return intent;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
		// / Modified by guofeiyao
	PeopleActivity activity = null;
	try {
		activity = (PeopleActivity) getActivity();	
	} catch (Exception e) {
		e.printStackTrace();
		return;
	}
			
//        final DialpadView dialpadView = (DialpadView) getView().findViewById(R.id.dialpad_view);
        View tmp = getView();
        if (tmp == null) return;
        final DialpadView dialpadView = (DialpadView) tmp.findViewById(R.id.dialpad_view);
		// / End
		
        if (activity == null) return;
        if (!hidden && !isDialpadChooserVisible()) {
            if (mAnimate) {
                dialpadView.animateShow();
            }

            if (null != mFloatingActionButtonController) {
				// / Modified by guofeiyao
//                mFloatingActionButtonController.scaleIn(mAnimate ? mDialpadSlideInDuration : 0);
                  mFloatingActionButtonController.setVisible(true);
				// / End
            }
            /// M: for Plug-in @{
            ExtensionManager.getInstance().
                    getDialPadExtension().onHiddenChanged(
                            true, mAnimate ? mDialpadSlideInDuration : 0);
            /// @}

            activity.getDialerPlugIn().onDialpadShown();
            mDigits.requestFocus();
        }
        if (hidden && mAnimate && (null != mFloatingActionButtonController)) {
			// / Modified by guofeiyao
//            mFloatingActionButtonController.scaleOut();
              mFloatingActionButtonController.setVisible(false);
            // / End
        }
        /// M: for Plug-in @{
        if (hidden && mAnimate) {
            ExtensionManager.getInstance().
                    getDialPadExtension().onHiddenChanged(false, 0);
        }
        /// @}

    }

    public void setAnimate(boolean value) {
        mAnimate = value;
    }

    public boolean getAnimate() {
        return mAnimate;
    }

    public void setYFraction(float yFraction) {
        ((DialpadSlidingRelativeLayout) getView()).setYFraction(yFraction);
    }

///-----------------------------------------MediaTek------------------------------------------------------------------

    @Override
    public void doCallOptionHandle(Intent intent) {
        DialerUtils.startActivityWithErrorToast(getActivity(), intent);
        hideAndClearDialpad(false);
    }

    /** M: [Ip Dial] add IP dial @{ */
    protected boolean onIPDialMenuItemSelected() {
        handleDialButtonPressed(Constants.DIAL_NUMBER_INTENT_IP);
        return true;
    }
    /** @} */

    /** [ALPS01841736] add listener for RingerMode and DTMF @{ */
    private int mRingerMode;
    private DTMFObserver mDTMFObserver = new DTMFObserver();

    private void setupAndRegisterListener(Context context) {
        if (context == null) {
            return;
        }
        AudioManager audioMgr = (AudioManager) context
                .getSystemService(Context.AUDIO_SERVICE);
        mDTMFToneEnabled = Settings.System.getInt(context.getContentResolver(),
                Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1;
        mRingerMode = audioMgr.getRingerMode();
        audioMgr.listenRingerModeAndVolume(mRingerModeListener,
                AudioProfileListener.LISTEN_RINGERMODE_CHANGED);
        context.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                Settings.System.DTMF_TONE_WHEN_DIALING), false, mDTMFObserver);
    }

    private void removeListener(Context context) {
        if (context == null) {
            return;
        }
        AudioManager audioMgr = (AudioManager) context
                .getSystemService(Context.AUDIO_SERVICE);
        if (mRingerModeListener != null) {
            audioMgr.listenRingerModeAndVolume(mRingerModeListener,
                    AudioProfileListener.STOP_LISTEN);
        }
        if (mDTMFObserver != null) {
            context.getContentResolver().unregisterContentObserver(mDTMFObserver);
        }
    }

    private final AudioProfileListener mRingerModeListener = new AudioProfileListener() {
        @Override
        public void onRingerModeChanged(int newRingerMode) {
            if (mRingerMode != newRingerMode) {
                Log.d(TAG, "Ringer mode changed to " + newRingerMode);
                mRingerMode = newRingerMode;
            }
        }
    };

    private class DTMFObserver extends ContentObserver {

        public DTMFObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Activity activity = getActivity();
            if (activity != null) {
                mDTMFToneEnabled = Settings.System.getInt(
                        activity.getContentResolver(),
                        Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1;
                Log.d(TAG, "DTMF changed to " + mDTMFToneEnabled);
            }
        }
    }
    /** @} */

    /** M: [ALPS01858019] add listener observer CallLog changes. @{ */
    private ContentObserver mCallLogObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange) {
            if (DialpadFragment.this.isAdded()) {
                Log.d(TAG, "Observered the CallLog changes. queryLastOutgoingCall");
                queryLastOutgoingCall();
            }
        };
    };
    /** @} */


   /**
    * Shows WFC related notification on status bar when open DialpadFragment
    *
    */
    public void showWfcNotification() {
        Log.i(TAG, "[WFC]showWfcNotification ");
        String wfcText = null;
        String wfcTextSummary = null;
        int wfcIcon = 0;
        final int TIMER_COUNT = 1;
        PhoneAccountHandle defaultAccountHandle =
                getTelecomManager().getDefaultOutgoingPhoneAccount(SCHEME_TEL);
        if (defaultAccountHandle != null) {
            int subId = Integer.parseInt(defaultAccountHandle.getId());
            ImsManager imsManager =
                    ImsManager.getInstance(mContext, SubscriptionManager.getPhoneId(subId));
            if (imsManager != null) {
                Log.i(TAG, "[WFC]imsManager != null ");
                int wfcStatus = imsManager.getWfcStatusCode();
                switch (wfcStatus) {
                    case WfcReasonInfo.CODE_WFC_SUCCESS:
                        wfcText = mContext.getResources().getString(R.string.calls_over_wifi);
                        wfcIcon = com.mediatek.internal.R.drawable.wfc_notify_registration_success;
                        wfcTextSummary = mContext.getResources().getString(R.string.wfc_notification_summary);
                        break;
                    default:
                        break;
                }
            }
        }
        if (isSimPresent(mContext) && !isRatPresent(mContext)) {
            Log.i(TAG, "[WFC]!isRatPresent(mContext) ");
            wfcText = mContext.getResources().getString(R.string.connect_to_wifi);
            wfcIcon = com.mediatek.internal.R.drawable.wfc_notify_registration_error;
            wfcTextSummary = mContext.getResources().getString(R.string.wfc_notification_summary_fail);
        }
        if (wfcText != null) {
            Log.i(TAG, "[WFC]wfc_text " + wfcText);
            mNotifTimer = new Timer();
            mNotificationManager =
                    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotifTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mNotiCount ++;
                    Log.i(TAG, "[WFC]count:" + mNotiCount);
                    if (mNotiCount == TIMER_COUNT) {
                        Log.i(TAG, "[WFC]Canceling notification on time expire mNotiCount" + mNotiCount);
                        stopWfcNotification();
                    }
                 }
             }, 100, 100);
            Notification noti = new Notification.Builder(mContext)
                    .setContentTitle(wfcText)
                    .setContentText(mContext.getResources().getString(R.string.wfc_notification_summary))
                    .setSmallIcon(wfcIcon)
                    .setTicker(wfcText)
                    .setOngoing(true)
                    .build();
            Log.i(TAG, "[WFC]Showing WFC notification");
            mNotificationManager.notify(DIALPAD_WFC_NOTIFICATION_ID, noti);
        } else {
            return;
        }
    }


   /**
    * Removes the notification from status bar shown for WFC
    *
    */
    public void stopWfcNotification() {
        Log.i(TAG, "[WFC]canceling notification on stopNotification");
        if (mNotifTimer != null) {
            mNotifTimer.cancel();
        };
        mNotiCount = 0;
        if (mNotificationManager != null) {
            mNotificationManager.cancel(DIALPAD_WFC_NOTIFICATION_ID);
        }
    }

   /**
    * Checks whether SIM is present or not 
    *
    * @param context
    */
    public boolean isSimPresent(Context context) {
        boolean ret = false;
        int[] subs =
                SubscriptionManager.from(context).getActiveSubscriptionIdList();
        if (subs.length == 0) {
            ret =  false;
        } else {
             ret = true;
        }
        Log.i(TAG, "[WFC]isSimPresent ret " + ret);
        return ret;
    }

   /**
    * Checks whether any of RAT present: 2G/3G/LTE/Wi-Fi 
    *
    *@param context
    */
    public static boolean isRatPresent(Context context) {
        Log.i(TAG, "[WFC]isRatPresent");
        int cellularState = ServiceState.STATE_IN_SERVICE;
        ITelephonyEx telephonyEx = ITelephonyEx.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
        Bundle bundle = null;
        try {
            bundle = telephonyEx.getServiceState(SubscriptionManager.getDefaultVoiceSubId());
        } catch (RemoteException e) {
            Log.i(TAG, "[wfc]getServiceState() exception, subid: "
                    + SubscriptionManager.getDefaultVoiceSubId());
            e.printStackTrace();
        }
        if (bundle != null) {
            cellularState = ServiceState.newFromBundle(bundle).getState();
        }
        Log.i(TAG, "[wfc]cellularState:" + cellularState);
        WifiManager wifiManager =
                (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi =
                cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        Log.i(TAG, "[wfc]wifi state:" + wifiManager.getWifiState());
        Log.i(TAG, "[wfc]wifi connected:" + wifi.isConnected());
        if ((wifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED
                || (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED && !wifi.isConnected()))
                && cellularState != ServiceState.STATE_IN_SERVICE) {
            Log.i(TAG, "[wfc]No RAT present");
            return false;
        } else {
            Log.i(TAG, "[wfc]RAT present");
            return true;
        }
    }
    ///@}

	private boolean isLocalCall(int subId){
		TelephonyManager phoneMgr=(TelephonyManager)getActivity().getSystemService(Context.TELEPHONY_SERVICE);
		 String localNumber =	phoneMgr.getLine1NumberForSubscriber(subId);
		 String dialnumber = mDigits.getText().toString();
		 if(dialnumber!=null){
				dialnumber = dialnumber.replaceAll(" ","");
				if(dialnumber.length()<11){
					return true;
				}
				
				if(csom!=null){
				String localLocation = csom.getPhoneAttribute(localNumber);
				String dialLocation = csom.getPhoneAttribute(dialnumber);
				if(localLocation!=null&&dialLocation!=null&&localLocation.contains(" ")){
					String sublocalLocation = localLocation.substring(0, localLocation.indexOf(" "));
					if(dialLocation.contains(sublocalLocation)){
						return true;
					}
				}
		 	}
		 }
		 return false;
	}
		
//	hq_hxb modified by	for  HQ01308688 begin

	 private boolean mSimInserted = true;
	 private void checkIfSimSert() {
	 if(Settings.System.getInt(mContext.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0) 
	     {
	             mSimInserted = false;
	             return;
	     }

	   TelephonyManager mTm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
	     try {
	         mSimInserted = true;
	         if (SlotUtils.isGeminiEnabled()) {
	             if((mTm.hasIccCard(PhoneConstants.SIM_ID_1) == false 
	                                     && mTm.hasIccCard(PhoneConstants.SIM_ID_2) == false)
	                                     ||Settings.System.getInt(mContext.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) == 1) {
	                 mSimInserted = false;
	             }
	         } else {
	             mSimInserted = mTm.hasIccCard(PhoneConstants.SIM_ID_1);
	         }
	     
	     }catch (Exception ex) {
	         ex.printStackTrace();
	         mSimInserted = false;
	     }

	}	
	// 					 hq_hxb modified by  for	HQ01308688 end				 
}
