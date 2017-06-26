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

package com.android.settings.sim;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.text.Selection;
import android.text.Spannable;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;
import com.android.settings.RestrictedSettingsFragment;
import com.android.settings.Utils;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.mediatek.internal.telephony.CellConnMgr;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.settings.FeatureOption;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.cdma.CdmaUtils;
import com.mediatek.settings.ext.ISettingsMiscExt;
import com.mediatek.settings.ext.ISimManagementExt;
import com.mediatek.settings.sim.Log;
import com.mediatek.settings.sim.PhoneServiceStateHandler;
import com.mediatek.settings.sim.RadioPowerManager;
import com.mediatek.settings.sim.RadioPowerPreference;
import com.mediatek.settings.sim.SimHotSwapHandler;
import com.mediatek.settings.sim.TelephonyUtils;
import com.mediatek.telecom.TelecomManagerEx;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public class SimSettings extends RestrictedSettingsFragment
        implements Indexable,
        PhoneServiceStateHandler.Listener {
    private static final String TAG = "SimSettings";
    private static final boolean DBG = true;

    private static final String DISALLOW_CONFIG_SIM = "no_config_sim";
    private static final String SIM_CARD_CATEGORY = "sim_cards";
    private static final String KEY_CELLULAR_DATA = "sim_cellular_data";
    private static final String KEY_SIM_ACTIVITIES = "sim_activities";
    private static final String KEY_CALLS = "sim_calls";
    private static final String KEY_SMS = "sim_sms";
    private static final String KEY_ACTIVITIES = "activities";
    private static final int ID_INDEX = 0;
    private static final int NAME_INDEX = 1;
    private static final int APN_INDEX = 2;
    private static final int PROXY_INDEX = 3;
    private static final int PORT_INDEX = 4;
    private static final int USER_INDEX = 5;
    private static final int SERVER_INDEX = 6;
    private static final int PASSWORD_INDEX = 7;
    private static final int MMSC_INDEX = 8;
    private static final int MCC_INDEX = 9;
    private static final int MNC_INDEX = 10;
    private static final int NUMERIC_INDEX = 11;
    private static final int MMSPROXY_INDEX = 12;
    private static final int MMSPORT_INDEX = 13;
    private static final int AUTH_TYPE_INDEX = 14;
    private static final int TYPE_INDEX = 15;
    private static final int PROTOCOL_INDEX = 16;
    private static final int CARRIER_ENABLED_INDEX = 17;
    private static final int BEARER_INDEX = 18;
    private static final int ROAMING_PROTOCOL_INDEX = 19;
    private static final int MVNO_TYPE_INDEX = 20;
    private static final int MVNO_MATCH_DATA_INDEX = 21;
    private static final int DATA_PICK = 0;
    private static final int CALLS_PICK = 1;
    private static final int SMS_PICK = 2;

    /**
     * By UX design we use only one Subscription Information(SubInfo) record per SIM slot.
     * mAvalableSubInfos is the list of SubInfos we present to the user.
     * mSubInfoList is the list of all SubInfos.
     * mSelectableSubInfos is the list of SubInfos that a user can select for data, calls, and SMS.
     */
    private List<SubscriptionInfo> mAvailableSubInfos = null;
    private List<SubscriptionInfo> mSubInfoList = null;
    private List<SubscriptionInfo> mSelectableSubInfos = null;

    private SubscriptionInfo mCellularData = null;
    private SubscriptionInfo mCalls = null;
    private SubscriptionInfo mSMS = null;

    private PreferenceScreen mSimCards = null;

    private SubscriptionManager mSubscriptionManager;
    private Utils mUtils;
    private ISettingsMiscExt mMiscExt;


    public SimSettings() {
        super(DISALLOW_CONFIG_SIM);
    }

    @Override
    public void onCreate(final Bundle bundle) {
        super.onCreate(bundle);

        mSubscriptionManager = SubscriptionManager.from(getActivity());

        if (mSubInfoList == null) {
            mSubInfoList = mSubscriptionManager.getActiveSubscriptionInfoList();
            // FIXME: b/18385348, needs to handle null from getActiveSubscriptionInfoList
        }
        if (DBG) log("[onCreate] mSubInfoList=" + mSubInfoList);

        /// M: @{
        init();
        /// @}
        createPreferences();
        updateAllOptions();

        SimBootReceiver.cancelNotification(getActivity());
    }

    private void createPreferences() {
        final TelephonyManager tm =
            (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);

        addPreferencesFromResource(R.xml.sim_settings);

        mSimCards = (PreferenceScreen)findPreference(SIM_CARD_CATEGORY);
        /// M: only for OP09 UIM/SIM changes.
        changeSimActivityTitle();

        final int numSlots = tm.getSimCount();
        mAvailableSubInfos = new ArrayList<SubscriptionInfo>(numSlots);
        mSelectableSubInfos = new ArrayList<SubscriptionInfo>();
        for (int i = 0; i < numSlots; ++i) {
            final SubscriptionInfo sir = Utils.findRecordBySlotId(getActivity(), i);
            SimPreference simPreference = new SimPreference(getActivity(), sir, i);
            simPreference.setOrder(i-numSlots);
            /// M: add for Radio on/off feature @{
            bindWithRadioPowerManager(simPreference, sir);
            /// @}
            mSimCards.addPreference(simPreference);
            mAvailableSubInfos.add(sir);
            if (sir != null) {
                mSelectableSubInfos.add(sir);
            }
        }

        updateActivitesCategory();
    }

    private void updateAvailableSubInfos(){
        final TelephonyManager tm =
            (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        final int numSlots = tm.getSimCount();

        mAvailableSubInfos = new ArrayList<SubscriptionInfo>(numSlots);
        for (int i = 0; i < numSlots; ++i) {
            final SubscriptionInfo sir = Utils.findRecordBySlotId(getActivity(), i);
            mAvailableSubInfos.add(sir);
            if (sir != null) {
            }
        }
    }

    private void updateSimName() {
        //log("[updateSimName] mAvailableSubInfos=" + mAvailableSubInfos);
        final int prefSize = mSimCards.getPreferenceCount();
        for (int i = 0; i < prefSize; ++i) {
            Preference pref = mSimCards.getPreference(i);
            if (pref instanceof SimPreference) {
                ((SimPreference)pref).update();
            }
        }
    }

    private void updateAllOptions() {
        updateSimSlotValues();
        updateActivitesCategory();
    }

    private void updateSimSlotValues() {
        mSubscriptionManager.getAllSubscriptionInfoList();

        final int prefSize = mSimCards.getPreferenceCount();
        //add HQ_xuqian4_HQ01319702 start
        updateCallValues();
        updateSmsValues();
        //add HQ_xuqian4_HQ01319702 end
        for (int i = 0; i < prefSize; ++i) {
            Preference pref = mSimCards.getPreference(i);
            if (pref instanceof SimPreference) {
                ((SimPreference)pref).update();
            }
        }
    }

    private void updateActivitesCategory() {
        updateCellularDataValues();
        updateCallValues();
        updateSmsValues();
        updateSimPref();
    }

    private void updateSmsValues() {
        /* begin: change by donghongjing for sim settings Emui */
        final PreferenceSimSelect simPref = (PreferenceSimSelect) findPreference(KEY_SMS);
        if (simPref != null) {
            SubscriptionInfo sir = Utils.findRecordBySubId(getActivity(),
                    mSubscriptionManager.getDefaultSmsSubId());
            simPref.setTitle(R.string.default_message);
            if (DBG) log("[updateSmsValues] mSubInfoList=" + mSubInfoList);

            sir = mExt.setDefaultSubId(getActivity(), sir, 1);
            if (sir != null) {
                simPref.updateSlotSelectState(sir.getSimSlotIndex());
            } else if (sir == null) {
                int slotId = -1;
                int defaultSmsSubId = SubscriptionManager.getDefaultSmsSubId();
                if (defaultSmsSubId == Settings.System.SMS_SIM_SETTING_AUTO) {
                    slotId = -1;
                } else {
                    slotId = SubscriptionManager.getSlotId(defaultSmsSubId);
                }
                simPref.updateSlotSelectState(slotId);
                android.util.Log.d(TAG, "updateSmsValues sir == null, defaultSmsSubId " + defaultSmsSubId
                        + ", slotId = " + slotId);
            }
            simPref.setPickId(PreferenceSimSelect.SMS_PICK);

            //add HQ_xuqian4_HQ01319702 start
            if((mSelectableSubInfos.size() > 1)&&(getRadioStateForSlotId(0)==true)&&(getRadioStateForSlotId(1)==true)){
                simPref.updateSlotEnabledState(0, !mIsAirplaneModeOn && !isCapabilitySwitching());
                simPref.updateSlotEnabledState(1, !mIsAirplaneModeOn && !isCapabilitySwitching());
                simPref.setEnabled(true);
            }else{
                simPref.updateSlotEnabledState(0, false);
                simPref.updateSlotEnabledState(1, false);
                simPref.setEnabled(false);
            }
            //add HQ_xuqian4_HQ01319702 end
            /* end: change by donghongjing for sim settings Emui */
            if (!shouldDisableActivitesCategory(getActivity())) {
                simPref.setEnabled(mSelectableSubInfos.size() >= 1); 
            }
        }
    }

    private void updateCellularDataValues() {
        /* begin: change by donghongjing for sim settings Emui */
        final PreferenceSimSelect simPref = (PreferenceSimSelect) findPreference(KEY_CELLULAR_DATA);
        if (simPref != null) {
            SubscriptionInfo sir = Utils.findRecordBySubId(getActivity(),
                    mSubscriptionManager.getDefaultDataSubId());                    
            simPref.setTitle(R.string.switch_dual_card_slots_with_data);
            if (DBG) log("[updateCellularDataValues] mSubInfoList=" + mSubInfoList);

            sir = mExt.setDefaultSubId(getActivity(), sir, 2);
            int selectedSlotId = -1;
            if (sir != null) {
                selectedSlotId = sir.getSimSlotIndex();
            }
            simPref.updateSlotSelectState(selectedSlotId);

            simPref.setPickId(PreferenceSimSelect.DATA_PICK);
            if (mSelectableSubInfos.size() > 1 &&(getRadioStateForSlotId(0)==true)&&(getRadioStateForSlotId(1)==true)) {
                simPref.updateSlotEnabledState(0, getRadioStateForSlotId(0) && !mIsAirplaneModeOn && !isCapabilitySwitching());
                simPref.updateSlotEnabledState(1, getRadioStateForSlotId(1) && !mIsAirplaneModeOn && !isCapabilitySwitching());
            } else if (mSelectableSubInfos.size() == 1) {
                int insertedSlotId = mSelectableSubInfos.get(0).getSimSlotIndex();
                int noneInsertedSlotId = (insertedSlotId == 0) ? 1 : 0;
                if (selectedSlotId != insertedSlotId) {
                    simPref.updateSlotEnabledState(insertedSlotId,
                            getRadioStateForSlotId(insertedSlotId) && !mIsAirplaneModeOn && !isCapabilitySwitching());
                } else {
                    simPref.updateSlotEnabledState(insertedSlotId, false);
                }
                simPref.updateSlotEnabledState(noneInsertedSlotId, false);
            } else {
                simPref.updateSlotEnabledState(0, false);
                simPref.updateSlotEnabledState(1, false);
            }
            /// M: @{
            ///HQ_xionghaifeng 20151021 add for HQ01453022 start @{
			int [] sim1SubId = SubscriptionManager.getSubId(0);
			int [] sim2SubId = SubscriptionManager.getSubId(1);

			if (sim1SubId != null && sim2SubId != null)
			{
				boolean phone1IsIdle = (TelephonyManager.getDefault().getCallState(sim1SubId[0])
	                == TelephonyManager.CALL_STATE_IDLE);
				boolean phone2IsIdle = (TelephonyManager.getDefault().getCallState(sim2SubId[0])
	                == TelephonyManager.CALL_STATE_IDLE);
	                
				Log.d("xionghaifeng", "phone1IsIdle : " + phone1IsIdle + " phone2IsIdle : " + phone2IsIdle);

				if (!phone1IsIdle || !phone2IsIdle)
				{
					simPref.updateSlotEnabledState(0, false);
	                simPref.updateSlotEnabledState(1, false);
				}
			}
			///HQ_xionghaifeng 20151021 add for HQ01453022 end @}
            simPref.setEnabled(mSelectableSubInfos.size() >= 1 &&
                    (!mIsAirplaneModeOn) &&
                    (!isCapabilitySwitching()));
            /// @}    
        }
        /* end: change by donghongjing for sim settings Emui */
    }

    /* begin: add by donghongjing for sim settings Emui */
    private int getSlotIdFromPhoneAccount(PhoneAccountHandle phoneAccount) {
        int slotId = 0;
        final String phoneAccountId = phoneAccount.getId();
        final int phoneAccountSubId = Integer.parseInt(phoneAccountId);

        if (mAvailableSubInfos != null) {
            for (SubscriptionInfo sir : mAvailableSubInfos) {
                if (sir != null && phoneAccountSubId == sir.getSubscriptionId()) {
                    slotId = sir.getSimSlotIndex();
                    break;
                }
            }
        }

        return slotId;
    }
    /* end: add by donghongjing for sim settings Emui */

    private void updateCallValues() {
        /* begin: change by donghongjing for sim settings Emui */
        final PreferenceSimSelect simPref = (PreferenceSimSelect) findPreference(KEY_CALLS);

        if (simPref != null) {
            final TelecomManager telecomManager = TelecomManager.from(getActivity());
            PhoneAccountHandle phoneAccount =
                telecomManager.getUserSelectedOutgoingPhoneAccount();

            phoneAccount = mExt.setDefaultCallValue(phoneAccount);
            Log.d(TAG, "updateCallValues phoneAccount=" + phoneAccount);
            simPref.setTitle(R.string.default_call);
            if (phoneAccount == null) {
                simPref.updateSlotSelectState(-1);
            } else {
                simPref.updateSlotSelectState(getSlotIdFromPhoneAccount(phoneAccount));
            }
            simPref.setPickId(PreferenceSimSelect.CALLS_PICK);
            int accoutSum = telecomManager.getCallCapablePhoneAccounts().size();
            Log.d(TAG, "accountSum: " + accoutSum + "PhoneAccount: " + phoneAccount);
            //add HQ_xuqian4_HQ01319702 start
            if((accoutSum > 1)&&(getRadioStateForSlotId(0)==true)&&(getRadioStateForSlotId(1)==true)){
                simPref.updateSlotEnabledState(0, !mIsAirplaneModeOn && !isCapabilitySwitching());
                simPref.updateSlotEnabledState(1, !mIsAirplaneModeOn && !isCapabilitySwitching());
                simPref.setEnabled(true);
            }else{
                simPref.updateSlotEnabledState(0, false);
                simPref.updateSlotEnabledState(1, false);
                simPref.setEnabled(false);
            }
            //add HQ_xuqian4_HQ01319702 end
        }
        /* end: change by donghongjing for sim settings Emui */
    }

    @Override
    public void onResume() {
        super.onResume();
        /// M: add for SIM hot swap @{
        mSimHotSwapHandler.registerOnSubscriptionsChangedListener();
        /// @}
        mSubInfoList = mSubscriptionManager.getActiveSubscriptionInfoList();
        // FIXME: b/18385348, needs to handle null from getActiveSubscriptionInfoList
        if (DBG) log("[onResme] mSubInfoList=" + mSubInfoList);

        removeItem();

        updateAvailableSubInfos();
        updateAllOptions();
        /// M: Auto open the other card's data connection. when current card is radio off@{
        mExt.registerObserver();
        mExt.dealWithDataConnChanged(null, isResumed());
        /// @}
        // M: for CT to replace the SIM to UIM
        replaceSIMString();
    }

    @Override
    public boolean onPreferenceTreeClick(final PreferenceScreen preferenceScreen,
            final Preference preference) {
        final Context context = getActivity();
        Intent intent = new Intent(context, SimDialogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        if (preference instanceof SimPreference) {
            ((SimPreference)preference).createEditDialog((SimPreference)preference);
        } else if (findPreference(KEY_CELLULAR_DATA) == preference) {
            /* begin: delete by donghongjing for sim settings Emui */
            //intent.putExtra(SimDialogActivity.DIALOG_TYPE_KEY, SimDialogActivity.DATA_PICK);
            //context.startActivity(intent);
            /* end: delete by donghongjing for sim settings Emui */
        } else if (findPreference(KEY_CALLS) == preference) {
            /* begin: delete by donghongjing for sim settings Emui */
            //intent.putExtra(SimDialogActivity.DIALOG_TYPE_KEY, SimDialogActivity.CALLS_PICK);
            //context.startActivity(intent);
            /* end: delete by donghongjing for sim settings Emui */
        } else if (findPreference(KEY_SMS) == preference) {
            /* begin: delete by donghongjing for sim settings Emui */
            //intent.putExtra(SimDialogActivity.DIALOG_TYPE_KEY, SimDialogActivity.SMS_PICK);
            //context.startActivity(intent);
            /* end: delete by donghongjing for sim settings Emui */
        }

        return true;
    }

    private class SimPreference extends RadioPowerPreference{
        private SubscriptionInfo mSubInfoRecord;
        private int mSlotId;
        private int[] mTintArr;
        Context mContext;
        private String[] mColorStrings;
        private int mTintSelectorPos;
        private TextView mSimNameText;
        private TextView mSimSlotNumberText;
        private TextView mSimNumberText;
        
        public SimPreference(Context context, SubscriptionInfo subInfoRecord, int slotId) {
            super(context);
            /* begin: add by donghongjing for sim settings Emui */
            setLayoutResource(R.layout.preference_sim);
            /* end: add by donghongjing for sim settings Emui */
            mContext = context;
            mSubInfoRecord = subInfoRecord;
            mSlotId = slotId;
            setKey("sim" + mSlotId);
            update();
            mTintArr = context.getResources().getIntArray(com.android.internal.R.array.sim_colors);
            mColorStrings = context.getResources().getStringArray(R.array.color_picker);
            mTintSelectorPos = 0;
        }

        /* begin: add by donghongjing for sim settings Emui */
        @Override
        protected void onBindView(View view) {
            super.onBindView(view);
            mSimNameText = (TextView) view.findViewById(R.id.sim_name);
            mSimSlotNumberText = (TextView) view.findViewById(R.id.sim_slot_number);
            //mSimNumberText = (TextView) view.findViewById(R.id.sim_number);//modified by maolikui
            update();
        }
        /* end: add by donghongjing for sim settings Emui */

        /* begin: change by donghongjing for sim settings Emui */
        public void update() {
            final Resources res = getResources();

            SubscriptionInfo defDataSub = Utils.findRecordBySubId(getActivity(),
                    mSubscriptionManager.getDefaultDataSubId());
            defDataSub = mExt.setDefaultSubId(getActivity(), defDataSub, 2);
        	String langage = Locale.getDefault().getLanguage();//add by lipeng for number display
           if (mSimNameText != null) {
                mSimNameText.setBackgroundResource((mSlotId == 0) ? R.drawable.sim_slot_1 : R.drawable.sim_slot_1);//modified by maolikui
                if (mSubInfoRecord != null) {
                    String slotStr = res.getString((mSlotId == 0) ? R.string.slot_1 : R.string.slot_2);
                    if (defDataSub != null && defDataSub.getSimSlotIndex() == mSlotId) {
                        mSimNameText.setText("    " + slotStr + "\n" + "    " + res.getString(R.string.cardstatus1));
                    } else {
                        mSimNameText.setText("    " + slotStr + "\n" + "    " + res.getString(R.string.cardstatus2));
                    }
                    //the width of big sim card background icon "sim_slot_1" is 207
                    mSimNameText.setMaxWidth(207); 

                    setRadioOn(TelephonyUtils.isRadioOn(mSubInfoRecord.getSubscriptionId())
                            && (UnLockSubDialog.getCurrentStateForSubId(mContext, mSubInfoRecord.getSubscriptionId())
                                     != CellConnMgr.STATE_SIM_LOCKED));
                    mSimSlotNumberText.setText(mSubInfoRecord.getDisplayName());
                    if (TextUtils.isEmpty(getPhoneNumber(mSubInfoRecord))) {
                        //mSimNumberText.setText("");//modified by maolikui
                    } else {
                        //mSimNumberText.setText(getPhoneNumber(mSubInfoRecord));
                    	if(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw")){///RTL language
                    		//mSimNumberText.setText("\u202D"+getPhoneNumber(mSubInfoRecord)+"\u202C"); //change by wangxiumei for sim settings layout
                    	}else{
                        	//mSimNumberText.setText(getPhoneNumber(mSubInfoRecord));//modified by maolikui
                    	}

                        setEnabled(true);
                    }
                    setRadioEnabled(!mIsAirplaneModeOn
                            && isRadioSwitchComplete(mSubInfoRecord.getSubscriptionId()));
                } else {
                    String empty = res.getString(R.string.empty);
                    if(empty.length() > 10) {
                        //do not add space
                    }
                    else {
                        empty = "    " + empty;
                    }
                    mSimNameText.setText(empty);
                    mSimNameText.setEnabled(false);
                    //the width of big sim card background icon "sim_slot_1" is 207
                    mSimNameText.setMaxWidth(207);
                    mSimSlotNumberText.setText(R.string.empty);
                    mSimSlotNumberText.setEnabled(false);
                    //mSimNumberText.setText("");modified by maolikui
                    setRadioEnabled(false);
                    setRadioOn(false);
                    setFragment(null);
                    setEnabled(false);
                }
            }
        }
        /* end: change by donghongjing for sim settings Emui */

        public SubscriptionInfo getSubInfoRecord() {
            return mSubInfoRecord;
        }

        public void createEditDialog(SimPreference simPref) {
            final Resources res = getResources();

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            final View dialogLayout = getActivity().getLayoutInflater().inflate(
                    R.layout.multi_sim_dialog, null);
            builder.setView(dialogLayout);

            EditText nameText = (EditText) dialogLayout.findViewById(R.id.sim_name);
            nameText.setText(mSubInfoRecord.getDisplayName());
           /* HQ_sunli HQ01309483 20150810 begin*/
            CharSequence text = nameText.getText();
                                  if (text instanceof Spannable){
                                           Spannable spanabletext = (Spannable) text;
                                           Selection.setSelection(spanabletext, spanabletext.length());
                                   }
            /* HQ_sunli HQ01309483 20150810 end*/
            /// M: only for OP09 UIM/SIM changes.
            changeSimNameTitle(dialogLayout);

            /* begin: delete by donghongjing for sim settings Emui */
            /*final Spinner tintSpinner = (Spinner) dialogLayout.findViewById(R.id.spinner);
            SelectColorAdapter adapter = new SelectColorAdapter(getContext(),
                     R.layout.settings_color_picker_item, mColorStrings);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            tintSpinner.setAdapter(adapter);

            for (int i = 0; i < mTintArr.length; i++) {
                if (mTintArr[i] == mSubInfoRecord.getIconTint()) {
                    tintSpinner.setSelection(i);
                    mTintSelectorPos = i;
                    break;
                }
            }

            tintSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view,
                    int pos, long id){
                    tintSpinner.setSelection(pos);
                    mTintSelectorPos = pos;
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

            TextView numberView = (TextView)dialogLayout.findViewById(R.id.number);
            final String rawNumber = getPhoneNumber(mSubInfoRecord);
            if (TextUtils.isEmpty(rawNumber)) {
                numberView.setText(res.getString(com.android.internal.R.string.unknownName));
            } else {
                numberView.setText(PhoneNumberUtils.formatNumber(rawNumber));
            }

            final TelephonyManager tm =
                    (TelephonyManager) getActivity().getSystemService(
                            Context.TELEPHONY_SERVICE);
            String simCarrierName = tm.getSimOperatorNameForSubscription(mSubInfoRecord
                    .getSubscriptionId());
            TextView carrierView = (TextView) dialogLayout.findViewById(R.id.carrier);
            carrierView.setText(!TextUtils.isEmpty(simCarrierName) ? simCarrierName :
                    getContext().getString(com.android.internal.R.string.unknownName));*/
            /* end: delete by donghongjing for sim settings Emui */

            builder.setTitle(String.format(res.getString(R.string.sim_editor_title),
                                           (mSubInfoRecord.getSimSlotIndex() + 1)));

            /// M: only for OP09 UIM/SIM changes.
            changeEditorTitle(builder);
            builder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    final EditText nameText = (EditText)dialogLayout.findViewById(R.id.sim_name);

                    String displayName = nameText.getText().toString();
                    int subId = mSubInfoRecord.getSubscriptionId();
                    mSubInfoRecord.setDisplayName(displayName);
                    mSubscriptionManager.setDisplayName(displayName, subId,
                            SubscriptionManager.NAME_SOURCE_USER_INPUT);
                    //Utils.findRecordBySubId(getActivity(), subId).setDisplayName(displayName);

                    /* begin: delete by donghongjing for sim settings Emui */
                    /*final int tintSelected = tintSpinner.getSelectedItemPosition();
                    int subscriptionId = mSubInfoRecord.getSubscriptionId();
                    int tint = mTintArr[tintSelected];
                    mSubInfoRecord.setIconTint(tint);
                    mSubscriptionManager.setIconTint(tint, subscriptionId);*/
                    /* end: delete by donghongjing for sim settings Emui */
                    //Utils.findRecordBySubId(getActivity(), subscriptionId).setIconTint(tint);

                    updateAllOptions();
                    update();
                }
            });

            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            });
            /// M:  @{
            mExt.hideSimEditorView(dialogLayout, mContext);
            /// @}
            builder.create().show();
        }

        private class SelectColorAdapter extends ArrayAdapter<CharSequence> {
            private Context mContext;
            private int mResId;

            public SelectColorAdapter(
                Context context, int resource, String[] arr) {
                super(context, resource, arr);
                mContext = context;
                mResId = resource;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LayoutInflater inflater = (LayoutInflater)
                    mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                View rowView;
                final ViewHolder holder;
                Resources res = getResources();
                int iconSize = res.getDimensionPixelSize(R.dimen.color_swatch_size);
                int strokeWidth = res.getDimensionPixelSize(R.dimen.color_swatch_stroke_width);

                /// M: for ALPS01972022 this is workaround solution. the icon show wrong for
                /// landscape
                //if (convertView == null) {
                    // Cache views for faster scrolling
                    rowView = inflater.inflate(mResId, null);
                    holder = new ViewHolder();
                    ShapeDrawable drawable = new ShapeDrawable(new OvalShape());
                    drawable.setIntrinsicHeight(iconSize);
                    drawable.setIntrinsicWidth(iconSize);
                    drawable.getPaint().setStrokeWidth(strokeWidth);
                    holder.label = (TextView) rowView.findViewById(R.id.color_text);
                    holder.icon = (ImageView) rowView.findViewById(R.id.color_icon);
                    holder.swatch = drawable;
                    rowView.setTag(holder);
                //} else {
                //    rowView = convertView;
                //    holder = (ViewHolder) rowView.getTag();
                //}

                holder.label.setText(getItem(position));
                holder.swatch.getPaint().setColor(mTintArr[position]);
                holder.swatch.getPaint().setStyle(Paint.Style.FILL_AND_STROKE);
                holder.icon.setVisibility(View.VISIBLE);
                holder.icon.setImageDrawable(holder.swatch);
                return rowView;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View rowView = getView(position, convertView, parent);
                final ViewHolder holder = (ViewHolder) rowView.getTag();

                if (mTintSelectorPos == position) {
                    holder.swatch.getPaint().setStyle(Paint.Style.FILL_AND_STROKE);
                } else {
                    holder.swatch.getPaint().setStyle(Paint.Style.STROKE);
                }
                holder.icon.setVisibility(View.VISIBLE);
                return rowView;
            }

            private class ViewHolder {
                TextView label;
                ImageView icon;
                ShapeDrawable swatch;
            }
        }

        /**
         * only for OP09 UIM/SIM changes.
         */
        private void changeTitle() {
            int subId = 0;

            if (mSubInfoRecord != null) {
                subId = mSubInfoRecord.getSubscriptionId();
            } else {
                subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            }
            setTitle(String.format(mMiscExt.customizeSimDisplayString(
                                                getResources().getString(R.string.sim_editor_title),
                                                subId),
                                   (mSlotId + 1)));
        }

        /**
         * only for OP09 UIM/SIM changes.
         *
         * @param dialogLayout the layout of the dialog view.
         */
        private void changeSimNameTitle(View dialogLayout) {
            int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            if (mSubInfoRecord != null) {
                subId = mSubInfoRecord.getSubscriptionId();
            }

            TextView nameTitle = (TextView) dialogLayout.findViewById(R.id.sim_name_title);
            nameTitle.setText(mMiscExt.customizeSimDisplayString(
                                nameTitle.getText().toString(), subId));
            EditText nameText = (EditText) dialogLayout.findViewById(R.id.sim_name);
            nameText.setHint(mMiscExt.customizeSimDisplayString(
                                getResources().getString(R.string.sim_name_hint), subId));
        }

        /**
         * only for OP09 UIM/SIM changes.
         *
         * @param builder the AlertDialog builder.
         */
        private void changeEditorTitle(AlertDialog.Builder builder) {
            int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            if (mSubInfoRecord != null) {
                subId = mSubInfoRecord.getSubscriptionId();
            }
            builder.setTitle(String.format(mMiscExt.customizeSimDisplayString(
                                              getResources().getString(R.string.sim_editor_title),
                                              subId),
                                           (mSubInfoRecord.getSimSlotIndex() + 1)));
        }
    }

    // Returns the line1Number. Line1number should always be read from TelephonyManager since it can
    // be overridden for display purposes.
    private String getPhoneNumber(SubscriptionInfo info) {
        final TelephonyManager tm =
            (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        return tm.getLine1NumberForSubscriber(info.getSubscriptionId());
    }

    private void log(String s) {
        Log.d(TAG, s);
    }

    /**
     * For search
     */
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();

                    if (Utils.showSimCardTile(context)) {
                        SearchIndexableResource sir = new SearchIndexableResource(context);
                        sir.xmlResId = R.xml.sim_settings;
                        result.add(sir);
                    }

                    return result;
                }
            };
            

    /*
     * M: New method please add below
     ************************************************************************
     */
    private ITelephonyEx mTelephonyEx;
    private SimHotSwapHandler mSimHotSwapHandler;
    private boolean mIsAirplaneModeOn = false;
    private IntentFilter mIntentFilter;
    private ISimManagementExt mExt;
    private static final boolean RADIO_POWER_OFF = false;
    private static final boolean RADIO_POWER_ON = true;
    private static final int MODE_PHONE1_ONLY = 1;
    private PhoneServiceStateHandler mStateHandler;

    // Receiver to handle different actions
    private BroadcastReceiver mSubReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "mSubReceiver action = " + action);
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                handleAirplaneModeBroadcast(intent);
            } else if (action.equals(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                handleDataConnectionStateChanged(intent);
            } else if (action.equals(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)) {
                updateCellularDataValues();
            } else if (isPhoneAccountAction(action)) {
                updateCallValues();
            } else if (isSimSwitchAction(action)) {
                updateSimPref();
            } else if (action.equals(TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE)) {
                updateSimName();
            }
        }
    };

    private boolean isPhoneAccountAction(String action) {
        return action.equals(TelecomManagerEx.ACTION_DEFAULT_ACCOUNT_CHANGED) ||
                action.equals(TelecomManagerEx.ACTION_PHONE_ACCOUNT_CHANGED);
    }
    private void handleDataConnectionStateChanged(Intent intent) {
        String apnTypeList = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
        /// M: just process default type data change, avoid unnecessary
        /// change broadcast
        if ((PhoneConstants.APN_TYPE_DEFAULT.equals(apnTypeList))) {
            /// M: Auto open the other card's data connection.
            // when current card is radio off
            mExt.dealWithDataConnChanged(intent, isResumed());
            /// @}
        }
    }

    /**
     * When airplane mode is on, some parts need to be disabled for prevent some telephony issues
     * when airplane on.
     * Default data is not able to switch as may cause modem switch
     * SIM radio power switch need to disable, also this action need operate modem
     * @param airplaneOn airplane mode state true on, false off
     */
    private void handleAirplaneModeBroadcast(Intent intent) {
        mIsAirplaneModeOn = intent.getBooleanExtra("state", false);
        Log.d(TAG, "air plane mode is = " + mIsAirplaneModeOn);
        updateSimSlotValues();
        /* begin: change by donghongjing for sim settings Emui from updateCellularDataValues() */
        updateActivitesCategory();
        /* end: change by donghongjing for sim settings Emui */
        updateCellularDataValues();
        updateSimPref();
        removeItem();
    }

    private void init() {
        mTelephonyEx = ITelephonyEx.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
        mSimHotSwapHandler = SimHotSwapHandler.newInstance(getActivity());
        mIsAirplaneModeOn = TelephonyUtils.isAirplaneModeOn(getActivity());
        Log.d(TAG, "init()... air plane mode is: " + mIsAirplaneModeOn);
        initIntentFilter();
        getActivity().registerReceiver(mSubReceiver, mIntentFilter);
        mExt = UtilsExt.getSimManagmentExtPlugin(getActivity());
        mMiscExt = UtilsExt.getMiscPlugin(getActivity());
        mStateHandler = new PhoneServiceStateHandler(getActivity());
        mStateHandler.addPhoneServiceStateListener(this);
    }

    /// Get whether sim switch still under operating, if no, need to keep data switching dialog
    private boolean isCapabilitySwitching() {
        boolean isSwitching = false;
        try {
            if (mTelephonyEx != null) {
                isSwitching = mTelephonyEx.isCapabilitySwitching();
            } else {
                Log.d(TAG, "mTelephonyEx is null, returen false");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException = " + e);
        }
        Log.d(TAG, "isSwitching = " + isSwitching);
        return isSwitching;
    }

    private boolean isSimSwitchAction(String action) {
        return action.equals(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE) ||
               action.equals(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_FAILED);
    }

    private void initIntentFilter() {
        mIntentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mIntentFilter.addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        mIntentFilter.addAction(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        // For PhoneAccount
        mIntentFilter.addAction(TelecomManagerEx.ACTION_DEFAULT_ACCOUNT_CHANGED);
        mIntentFilter.addAction(TelecomManagerEx.ACTION_PHONE_ACCOUNT_CHANGED);
        // For SIM Switch
        mIntentFilter.addAction(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_FAILED);

        //yanqing for SIM name change HQ01504177
        mIntentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE);
    }

    private void bindWithRadioPowerManager(SimPreference simPreference, SubscriptionInfo subInfo) {
        int subId = subInfo == null ? SubscriptionManager.INVALID_SUBSCRIPTION_ID : 
                                      subInfo.getSubscriptionId();
        RadioPowerManager radioMgr = new RadioPowerManager(getActivity());
        radioMgr.bindPreference(simPreference, subId);
    }

    @Override
    public void onPause() {
        super.onPause();
        mSimHotSwapHandler.unregisterOnSubscriptionsChangedListener();
        mExt.unregisterObserver();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        getActivity().unregisterReceiver(mSubReceiver);
        mStateHandler.removePhoneServiceSateListener();
        super.onDestroy();
    }

    /**
     * whether radio switch finish about subId.
     * @param subId subId
     * @return true finish
     */
    private boolean isRadioSwitchComplete(int subId) {
        boolean isComplete = true;
        int slotId = SubscriptionManager.getSlotId(subId);
        if (SubscriptionManager.isValidSlotId(slotId)) {
            Bundle bundle = null;
            try {
                if (mTelephonyEx != null) {
                    bundle = mTelephonyEx.getServiceState(subId);
                } else {
                    Log.d(TAG, "mTelephonyEx is null, returen false");
                }
            } catch (RemoteException e) {
                isComplete = false;
                Log.d(TAG, "getServiceState() error, subId: " + subId);
                e.printStackTrace();
            }
            if (bundle != null) {
                ServiceState serviceState = ServiceState.newFromBundle(bundle);
                isComplete = isRadioSwitchComplete(subId, serviceState);
            }
        }
        Log.d(TAG, "isRadioSwitchComplete(" + subId + ")" + ", slotId: " + slotId
                + ", isComplete: " + isComplete);
        return isComplete;
    }

    private boolean isRadioSwitchComplete(final int subId, ServiceState state) {
        int slotId = SubscriptionManager.getSlotId(subId);
        boolean radiosState = getRadioStateForSlotId(slotId);
        Log.d(TAG, "soltId: " + slotId + ", radiosState is : " + radiosState);
        if (radiosState && (state.getState() != ServiceState.STATE_POWER_OFF)) {
            return true;
        } else if (state.getState() == ServiceState.STATE_POWER_OFF) {
            return true;
        }
        return false;
    }

    private boolean getRadioStateForSlotId(final int slotId) {
        if (getActivity() == null) {
            Log.d(TAG, "getRadioStateForSlotId()... activity is null");
            return false;
        }
        int currentSimMode = Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.MSIM_MODE_SETTING, -1);
        boolean radiosState = ((currentSimMode & (MODE_PHONE1_ONLY << slotId)) == 0) ?
                RADIO_POWER_OFF : RADIO_POWER_ON;
        Log.d(TAG, "soltId: " + slotId + ", radiosState : " + radiosState);
        return radiosState;
    }

    private void handleRadioPowerSwitchComplete() {
        updateSimSlotValues();
        /* begin: add by donghongjing for sim settings Emui*/
        updateActivitesCategory();
        /* end: add by donghongjing for sim settings Emui */

        // M Auto open the other card's data connection. when current card is radio off
        mExt.showChangeDataConnDialog(this, isResumed());
    }

    private void updateSmsSummary(final Preference simPref) {
        int defaultSmsSubId = SubscriptionManager.getDefaultSmsSubId();
        if (defaultSmsSubId == Settings.System.SMS_SIM_SETTING_AUTO) {
            mExt.updateDefaultSIMSummary(simPref, defaultSmsSubId);
        } else {
            simPref.setSummary(R.string.sim_calls_ask_first_prefs_title);
        }
    }

    @Override
    public void onServiceStateChanged(ServiceState state, int subId) {
        Log.d(TAG, "PhoneStateListener:onServiceStateChanged: subId: " + subId
                + ", state: " + state);
        if (isRadioSwitchComplete(subId, state)) {
            handleRadioPowerSwitchComplete();
        }
    }

    private void removeItem() {
        //remove some item when in 4gds wifi-only 
        if(FeatureOption.MTK_PRODUCT_IS_TABLET){
            Preference sim_call_Pref = findPreference(KEY_CALLS);
            Preference sim_sms_Pref = findPreference(KEY_SMS);
            Preference sim_data_Pref = findPreference(KEY_CELLULAR_DATA);
            PreferenceCategory mPreferenceCategoryActivities = (PreferenceCategory) findPreference(KEY_SIM_ACTIVITIES);
            TelephonyManager mTelephonyManager = TelephonyManager.from(getActivity());
            boolean mIsVoiceCapable = mTelephonyManager.isVoiceCapable();
            boolean mIsSmsCapable = mTelephonyManager.isSmsCapable();
            if (!mIsSmsCapable && sim_sms_Pref!=null) {
                mPreferenceCategoryActivities.removePreference(sim_sms_Pref);
            }
            if (!mTelephonyManager.isMultiSimEnabled() && sim_data_Pref!=null && sim_sms_Pref!=null) {
                mPreferenceCategoryActivities.removePreference(sim_data_Pref);
                mPreferenceCategoryActivities.removePreference(sim_sms_Pref);
            }
            if (!mIsVoiceCapable && sim_call_Pref!=null) {
                mPreferenceCategoryActivities.removePreference(sim_call_Pref);
            }
            mExt.updateDefaultSettingsItem(mPreferenceCategoryActivities);
        }
		/*HQ_xionghaifeng remove sms_Pref and call_Pref item for huawei start*/
		/*
		else
		{
			Preference sim_sms_Pref = findPreference(KEY_SMS);
		    Preference sim_call_Pref = findPreference(KEY_CALLS);
		    PreferenceCategory mPreferenceCategoryActivities = (PreferenceCategory) findPreference(KEY_SIM_ACTIVITIES);

			if (sim_sms_Pref!=null)
			{
				mPreferenceCategoryActivities.removePreference(sim_sms_Pref);
			}
			if (sim_call_Pref!=null)
			{
				mPreferenceCategoryActivities.removePreference(sim_call_Pref);
			}

			mExt.updateDefaultSettingsItem(mPreferenceCategoryActivities);
		}*/
		/*HQ_xionghaifeng remove sms_Pref and call_Pref item for huawei end*/
    }

    /**
     * only for OP09 UIM/SIM changes.
     */
    private void changeSimActivityTitle() {
        PreferenceCategory preferenceCategoryActivities =
                (PreferenceCategory) findPreference(KEY_SIM_ACTIVITIES);
        preferenceCategoryActivities.setTitle(
                mMiscExt.customizeSimDisplayString(
                        preferenceCategoryActivities.getTitle().toString(),
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID));
    }

    /**
     * M: Replace SIM to SIM/UIM.
     */
    private void replaceSIMString() {
        if (mSimCards != null) {
            mSimCards.setTitle(mMiscExt.customizeSimDisplayString(
                    getString(R.string.sim_settings_title),
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID));
        }
        getActivity().setTitle(
                mMiscExt.customizeSimDisplayString(getString(R.string.sim_settings_title),
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID));
    }

    private void updateSimPref() {
        final Preference simPref = findPreference(KEY_CELLULAR_DATA);

        if (simPref != null) {
            /// M: @{
             simPref.setEnabled(mSelectableSubInfos.size() >= 1 &&
                    (!mIsAirplaneModeOn) &&
                    (!isCapabilitySwitching()) &&
                    (!TelephonyUtils.isCapabilitySwitching()));
            /// @}    
        }
        if (shouldDisableActivitesCategory(getActivity())) {
            final Preference simCallsPref = findPreference(KEY_CALLS);
            if (simCallsPref != null) {
                final TelecomManager telecomManager = TelecomManager.from(getActivity());
                int accoutSum = telecomManager.getCallCapablePhoneAccounts().size();
                Log.d(TAG, "accountSum: " + accoutSum);
                simCallsPref.setEnabled(!TelephonyUtils.isCapabilitySwitching()
                        && (accoutSum >= 1)
                        && (!mIsAirplaneModeOn));
            }
            final Preference simSmsPref = findPreference(KEY_SMS);
            if (simSmsPref != null) {
                simSmsPref.setEnabled(mSelectableSubInfos.size() >= 1
                        && (!TelephonyUtils.isCapabilitySwitching())
                        && (!mIsAirplaneModeOn));
            }
        }
    }

    private static boolean shouldDisableActivitesCategory(Context context) {
        boolean shouldDisable = false;
        shouldDisable = CdmaUtils.isCdmaCardCompetion(context);
        Log.d(TAG, "shouldDisableActivitesCategory() .. shouldDisable :" + shouldDisable);
        return shouldDisable;
    }
}
