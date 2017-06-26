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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.DisplayMetrics;

import com.android.ims.ImsConfig;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.PhoneUtils;
import com.android.phone.R;
//add for plug in
import com.mediatek.internal.telephony.uicc.SvlteUiccUtils;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;
import com.mediatek.phone.ext.ExtensionManager;

import com.mediatek.telecom.TelecomManagerEx;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Owns all data we have registered with Telecom including handling dynamic addition and
 * removal of SIMs and SIP accounts.
 */
final class TelecomAccountRegistry {
    private static final boolean DBG = false; /* STOP SHIP if true */

    // This icon is the one that is used when the Slot ID that we have for a particular SIM
    // is not supported, i.e. SubscriptionManager.INVALID_SLOT_ID or the 5th SIM in a phone.
    private final static int defaultPhoneAccountIcon =  R.drawable.ic_multi_sim;

    /// M: WFC, True if phone is capable of wifi calling @{
    private boolean mWifiCallEnabled = false;
    /// @}

    private final class AccountEntry {
        private final Phone mPhone;
        private final PhoneAccount mAccount;
        private final PstnIncomingCallNotifier mIncomingCallNotifier;

        AccountEntry(Phone phone, boolean isEmergency, boolean isDummy) {
            mPhone = phone;
            mAccount = registerPstnPhoneAccount(isEmergency, isDummy);
            if (Build.TYPE.equals("eng")) {
                Log.d(this, "Registered phoneAccount: %s with handle: %s",
                        mAccount, mAccount.getAccountHandle());
            }
            mIncomingCallNotifier = new PstnIncomingCallNotifier((PhoneProxy) mPhone);
        }

        void teardown() {
            mIncomingCallNotifier.teardown();
        }

        /**
         * Registers the specified account with Telecom as a PhoneAccountHandle.
         * M: For ALPS01965388. Only create a PhoneAccount but not register it to Telecom.
         */
        private PhoneAccount registerPstnPhoneAccount(boolean isEmergency, boolean isDummyAccount) {
            String dummyPrefix = isDummyAccount ? "Dummy " : "";

            // Build the Phone account handle.
            PhoneAccountHandle phoneAccountHandle =
                    PhoneUtils.makePstnPhoneAccountHandleWithPrefix(
                            mPhone, dummyPrefix, isEmergency);

            // Populate the phone account data.
            int subId = mPhone.getSubId();
            int color = PhoneAccount.NO_HIGHLIGHT_COLOR;
            int slotId = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
            /// M: for ALPS01804842, set sub number as address, so no need line1Number. @{
            // Original code:
            // String line1Number = mTelephonyManager.getLine1NumberForSubscriber(subId);
            // if (line1Number == null) {
            //     line1Number = "";
            // }
            /// @}
            String subNumber = mPhone.getPhoneSubInfo().getLine1Number();

            String label;
            String description;
            Bitmap iconBitmap = null;
            SubscriptionInfo record =
                    mSubscriptionManager.getActiveSubscriptionInfo(subId);

            if (isEmergency) {
                label = mContext.getResources().getString(R.string.sim_label_emergency_calls);
                description =
                        mContext.getResources().getString(R.string.sim_description_emergency_calls);
            }
            /// M: for ALPS01774567, remove these code, make the account name always same. @{
            // Original code:
            // else if (mTelephonyManager.getPhoneCount() == 1) {
            //     // For single-SIM devices, we show the label and description as whatever the name of
            //     // the network is.
            //     description = label = mTelephonyManager.getNetworkOperatorName();
            // }
            /// @}
            else {
                // M: for ALPS01772299, don't change it if name is empty, init it as "".
                CharSequence subDisplayName = "";
                // We can only get the real slotId from the SubInfoRecord, we can't calculate the
                // slotId from the subId or the phoneId in all instances.
                if (record != null) {
                    subDisplayName = record.getDisplayName();
                    slotId = record.getSimSlotIndex();
                    color = record.getIconTint();
                    // M: for ALPS01804842, set sub number as address
                    subNumber = record.getNumber();
                }

                String slotIdString;
                if (SubscriptionManager.isValidSlotId(slotId)) {
                    slotIdString = Integer.toString(slotId);
                } else {
                    slotIdString = mContext.getResources().getString(R.string.unknown);
                }

                /// M: for ALPS01772299, don't change it if name is empty. @{
                // original code:
                // if (TextUtils.isEmpty(subDisplayName)) {
                //     // Either the sub record is not there or it has an empty display name.
                //     Log.w(this, "Could not get a display name for subid: %d", subId);
                //     subDisplayName = mContext.getResources().getString(
                //             R.string.sim_description_default, slotIdString);
                //     subDisplayName = "";
                // }
                /// @}

                // The label is user-visible so let's use the display name that the user may
                // have set in Settings->Sim cards.
                label = dummyPrefix + subDisplayName;
                description = dummyPrefix + mContext.getResources().getString(
                                R.string.sim_description_default, slotIdString);
            }
            if (subNumber == null) {
                subNumber = "";
            }

            // By default all SIM phone accounts can place emergency calls.
            int capabilities = PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION |
                    PhoneAccount.CAPABILITY_CALL_PROVIDER |
                    PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS |
                    PhoneAccount.CAPABILITY_MULTI_USER;

            // M: attach extended capabilities for the PhoneAccount
            capabilities |= getExtendedCapabilities();

            if ((capabilities & PhoneAccount.CAPABILITY_UNAVAILABLE_FOR_CALL)
                    == PhoneAccount.CAPABILITY_UNAVAILABLE_FOR_CALL) {
                iconBitmap = createIconBitmap(mContext, Color.GRAY, record);
            } else {
                iconBitmap = record.createIconBitmap(mContext);
            }

            if (iconBitmap == null) {
                iconBitmap = BitmapFactory.decodeResource(
                        mContext.getResources(),
                        defaultPhoneAccountIcon);
            }
            //add for plug in.@{
            ExtensionManager.getTelecomAccountRegistryExt().setPhoneAccountSubId(subId);
            iconBitmap = ExtensionManager.getTelecomAccountRegistryExt().getPhoneAccountIconBitmap(mContext, 
                    iconBitmap);
            //add for plug in.@}
            PhoneAccount account = PhoneAccount.builder(phoneAccountHandle, label)
                     // M: for ALPS01804842, set sub number as address
                    .setAddress(Uri.fromParts(PhoneAccount.SCHEME_TEL, subNumber, null))
                    .setSubscriptionAddress(
                            Uri.fromParts(PhoneAccount.SCHEME_TEL, subNumber, null))
                    .setCapabilities(capabilities)
                    .setIcon(iconBitmap)
                    .setHighlightColor(color)
                    .setShortDescription(description)
                    .setSupportedUriSchemes(Arrays.asList(
                            PhoneAccount.SCHEME_TEL, PhoneAccount.SCHEME_VOICEMAIL))
                    .build();

            /// M: For ALPS01965388.
            /** Original code:
            // Register with Telecom and put into the account entry.
            mTelecomManager.registerPhoneAccount(account);
            */
            updateAccountChangeStatus(account);
            /// @}
            return account;
        }

        /**
         * M:add to create a disable icon.
         * @param context
         * @param color
         * @param record
         * @return
         */
        private Bitmap createIconBitmap(Context context, int color, SubscriptionInfo record) {
            Bitmap bmp = BitmapFactory.decodeResource(context.getResources(),
                    com.android.internal.R.drawable.ic_sim_card_multi_24px_clr);
            int width = bmp.getWidth();
            int height = bmp.getHeight();
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();

            // Create a new bitmap of the same size because it will be modified.
            Bitmap workingBitmap = Bitmap.createBitmap(metrics, width, height, bmp.getConfig());

            Canvas canvas = new Canvas(workingBitmap);
            Paint paint = new Paint();

            // Tint the icon with the color.
            paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
            canvas.drawBitmap(bmp, 0, 0, paint);
            paint.setColorFilter(null);

            // Write the sim slot index.
            paint.setAntiAlias(true);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setColor(Color.WHITE);
            // Set text size scaled by density
            paint.setTextSize(16 * metrics.density);
            // Convert sim slot index to localized string
            final String index = String.format("%d", record.getSimSlotIndex() + 1);
            final Rect textBound = new Rect();
            paint.getTextBounds(index, 0, 1, textBound);
            final float xOffset = (width / 2.f) - textBound.centerX();
            final float yOffset = (height / 2.f) - textBound.centerY();
            canvas.drawText(index, xOffset, yOffset, paint);

            return workingBitmap;
        }

        public PhoneAccountHandle getPhoneAccountHandle() {
            return mAccount != null ? mAccount.getAccountHandle() : null;
        }

        /**
         * M: get extended PhoneAccount capabilities currently.
         * @return the extended capability bit mask.
         */
        private int getExtendedCapabilities() {
            int extendedCapabilities = 0;
            boolean isImsEnabled = (1 == Settings.Global.getInt(
                    mPhone.getContext().getContentResolver(), Settings.Global.IMS_SWITCH, 0));
            if (isImsEnabled) {
                /// @}
                boolean isImsReg = false;
                try {
                    ImsManager imsManager = ImsManager.getInstance(mContext, mPhone.getPhoneId());
                    isImsReg = imsManager.getImsRegInfo();
                } catch (ImsException e) {
                    Log.v(this, "Get IMS register info fail.");
                }
                if (isImsReg) {
                    /// M: add WFC capability @{
                    if (mWifiCallEnabled) {
                        Log.v(this, "[WFC] getExtendedCapabilities, mWifiCallEnabled true");
                        extendedCapabilities |= PhoneAccount.CAPABILITY_WIFI_CALLING;
                    }
                    /// @}
                    extendedCapabilities |= PhoneAccount.CAPABILITY_VOLTE_CALLING;
                    extendedCapabilities |= PhoneAccount.CAPABILITY_VIDEO_CALLING;
                    /// For Volte enhanced conference feature. @{
                    if (mPhone.isFeatureSupported(Phone.FeatureType.VOLTE_ENHANCED_CONFERENCE)) {
                        extendedCapabilities |= PhoneAccount.CAPABILITY_VOLTE_ENHANCED_CONFERENCE;
                    }
                    /// @}
                }
            }

            /// Added for EVDO world phone.@{
            if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                extendedCapabilities |= PhoneAccount.CAPABILITY_CDMA_CALL_PROVIDER;
            }
            if (isPhoneUnAvailableForCall(mPhone.getSubId())) {
                extendedCapabilities |= PhoneAccount.CAPABILITY_UNAVAILABLE_FOR_CALL;
            }
            /// @}
            return extendedCapabilities;
        }
    }

    private OnSubscriptionsChangedListener mOnSubscriptionsChangedListener =
            new OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            // Any time the SubscriptionInfo changes...rerun the setup
            tearDownAccounts();
            setupAccounts();
            // M: broadcast pstn accounts changed
            broadcastAccountChanged();
        }
    };

    /**
     * M: for multi-sub, all subs should be listen, not only the default one.
     * original code:
    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            int newState = serviceState.getState();
            if (newState == ServiceState.STATE_IN_SERVICE && mServiceState != newState) {
                tearDownAccounts();
                setupAccounts();
            }
            mServiceState = newState;
        }
    };
     */

    private static TelecomAccountRegistry sInstance;
    private final Context mContext;
    private final TelecomManager mTelecomManager;
    private final TelephonyManager mTelephonyManager;
    private final SubscriptionManager mSubscriptionManager;
    private List<AccountEntry> mAccounts = new LinkedList<AccountEntry>();
    /**
     * M: should keep service state for all subs.
     * original code:
    private int mServiceState = ServiceState.STATE_POWER_OFF;
     */
    private final Map<Integer, Integer> mServiceStates = new ArrayMap<Integer, Integer>();

    // TODO: Remove back-pointer from app singleton to Service, since this is not a preferred
    // pattern; redesign. This was added to fix a late release bug.
    private TelephonyConnectionService mTelephonyConnectionService;

    TelecomAccountRegistry(Context context) {
        mContext = context;
        mTelecomManager = TelecomManager.from(context);
        mTelephonyManager = TelephonyManager.from(context);
        mSubscriptionManager = SubscriptionManager.from(context);
    }

    static synchronized final TelecomAccountRegistry getInstance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new TelecomAccountRegistry(context);
        }
        return sInstance;
    }

    void setTelephonyConnectionService(TelephonyConnectionService telephonyConnectionService) {
        this.mTelephonyConnectionService = telephonyConnectionService;
    }

    TelephonyConnectionService getTelephonyConnectionService() {
        return mTelephonyConnectionService;
    }

    /**
     * Sets up all the phone accounts for SIMs on first boot.
     */
    void setupOnBoot() {
        // TODO: When this object "finishes" we should unregister by invoking
        // SubscriptionManager.getInstance(mContext).unregister(mOnSubscriptionsChangedListener);
        // This is not strictly necessary because it will be unregistered if the
        // notification fails but it is good form.

        // Register for SubscriptionInfo list changes which is guaranteed
        // to invoke onSubscriptionsChanged the first time.
        SubscriptionManager.from(mContext).addOnSubscriptionsChangedListener(
                mOnSubscriptionsChangedListener);

        // We also need to listen for changes to the service state (e.g. emergency -> in service)
        // because this could signal a removal or addition of a SIM in a single SIM phone.
        /**
         * M: for multi-sub, all subs has should be listen
         * Original code:
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
         */
        listenPhoneState();
        registerReceiver();
        /** @} */
    }

    /**
     * Determines if the list of {@link AccountEntry}(s) contains an {@link AccountEntry} with a
     * specified {@link PhoneAccountHandle}.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if an entry exists.
     */
    private boolean hasAccountEntryForPhoneAccount(PhoneAccountHandle handle) {
        for (AccountEntry entry : mAccounts) {
            if (entry.getPhoneAccountHandle().equals(handle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Un-registers any {@link PhoneAccount}s which are no longer present in the list
     * {@code AccountEntry}(s).
     */
    private void cleanupPhoneAccounts() {
        ComponentName telephonyComponentName =
                new ComponentName(mContext, TelephonyConnectionService.class);
        List<PhoneAccountHandle> accountHandles = mTelecomManager.getAllPhoneAccountHandles();
        for (PhoneAccountHandle handle : accountHandles) {
            if (telephonyComponentName.equals(handle.getComponentName()) &&
                    !hasAccountEntryForPhoneAccount(handle)) {
                Log.d(this, "Unregistering phone account %s.", handle);
                mTelecomManager.unregisterPhoneAccount(handle);
                /// M: for ALPS01965388.@{
                mPhoneAccountChanged = true;
                /// @}
            }
        }
    }

    private void setupAccounts() {
        // Go through SIM-based phones and register ourselves -- registering an existing account
        // will cause the existing entry to be replaced.
        Phone[] phones = PhoneFactory.getPhones();
        Log.d(this, "Found %d phones.  Attempting to register.", phones.length);
        for (Phone phone : phones) {
            long subscriptionId = phone.getSubId();
            Log.d(this, "Phone with subscription id %d", subscriptionId);
            if (subscriptionId >= 0) {
                mAccounts.add(new AccountEntry(phone, false /* emergency */, false /* isDummy */));
            }
        }

        /// M: for ALPS01809899, do not register emergency account.
        // because it just indicate emergency call show use TelephonyConnectionService, it has no
        // actually use in MO or MT, and UI never want see it. @{
        // original code:
        // // If we did not list ANY accounts, we need to provide a "default" SIM account
        // // for emergency numbers since no actual SIM is needed for dialing emergency
        // // numbers but a phone account is.
        // if (mAccounts.isEmpty()) {
        //     mAccounts.add(new AccountEntry(PhoneFactory.getDefaultPhone(), true /* emergency */,
        //             false /* isDummy */));
        // }
        /// @}

        // Add a fake account entry.
        if (DBG && phones.length > 0 && "TRUE".equals(System.getProperty("dummy_sim"))) {
            mAccounts.add(new AccountEntry(phones[0], false /* emergency */, true /* isDummy */));
        }

        /// M: Added for ALPS01965388. @{
        registerIfAccountChanged();
        /// @}

        // Clean up any PhoneAccounts that are no longer relevant
        cleanupPhoneAccounts();
    }

    private void tearDownAccounts() {
        for (AccountEntry entry : mAccounts) {
            entry.teardown();
        }
        mAccounts.clear();
    }


    // ---------------------------------------mtk --------------------------------------//

    // For ALPS01965388. Use to mark whether there have one or more PhoneAccounts changed.
    private boolean mPhoneAccountChanged = false;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean rebuildAccounts;
            String action = intent.getAction();
            // Check for extended broadcast to rebuild accounts
            rebuildAccounts = needRebuildAccounts(intent);
            Log.d(this, "onReceive, action is " + action + "; rebuildAccounts is "
                    + rebuildAccounts);
            if (rebuildAccounts) {
                tearDownAccounts();
                setupAccounts();
                 // Broadcast pstn accounts changed
                broadcastAccountChanged();
            }
        }

        /**
         * Check the extended broadcast to determine whether to rebuild
         * the PhoneAccounts.
         * @param intent the intent.
         * @return true if rebuild needed
         */
        private boolean needRebuildAccounts(Intent intent) {
            boolean rebuildAccounts = false;
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_IMS_STATE_CHANGED.equals(action)) {
                /**
                 * Keys within this Intent:
                 * TelephonyIntents.EXTRA_IMS_REG_STATE_KEY
                 * PhoneConstants.SUBSCRIPTION_KEY
                 * PhoneConstants.PHONE_KEY
                 * PhoneConstants.SLOT_KEY
                 */
                int reg = intent.getIntExtra(TelephonyIntents.EXTRA_IMS_REG_STATE_KEY, -1);
                long subId = intent.getLongExtra(PhoneConstants.SUBSCRIPTION_KEY, -1);
                Log.d(this, "ACTION_IMS_STATE_CHANGED, new state: %s, subId: %s", reg, subId);
                rebuildAccounts = true;
                /// M: contorll the switch of WFC @{
            } else if (ImsManager.ACTION_IMS_CAPABILITIES_CHANGED.equals(action)) {
                int[] enabledFeatures = intent.getIntArrayExtra(ImsManager.EXTRA_IMS_ENABLE_CAP_KEY);
                int[] disabledFeatures = intent.getIntArrayExtra(ImsManager.EXTRA_IMS_DISABLE_CAP_KEY);
                if (enabledFeatures != null && enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI] ==
                        ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI) {
                    mWifiCallEnabled = true;
                } else if (disabledFeatures != null && disabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI] ==
                        ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI) {
                    mWifiCallEnabled = false;
                }
                rebuildAccounts = true;

                Log.d(this, "needRebuildAccounts, mWifiCallEnabled: " + mWifiCallEnabled);
            }
            /// @}
            return rebuildAccounts;
        }
    };

    /**
     * Notify pstn account changed.
     * TODO: need refactory this part and broadcast account changed by PhoneAccountRegistrar.
     */
    private void broadcastAccountChanged() {
        /// Modified for ALPS01965388.@{
        if (mPhoneAccountChanged) {
            Log.d(this, "broadcastAccountChanged");
            mPhoneAccountChanged = false;
            Intent intent = new Intent(TelecomManagerEx.ACTION_PHONE_ACCOUNT_CHANGED);
            mContext.sendBroadcast(intent);
        }
        /// @}
    }

    /**
     * Register receiver to more broadcast, like IMS state changed.
     * @param intentFilter the target IntentFilter.
     */
    private void registerReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        // Receive extended broadcasts like IMS state changed
        intentFilter.addAction(TelephonyIntents.ACTION_IMS_STATE_CHANGED);
        /// M: WFC, register the IMS_CAPABILITIES_CHANGED @{
        intentFilter.addAction(ImsManager.ACTION_IMS_CAPABILITIES_CHANGED);
        /// @}
        mContext.registerReceiver(mReceiver, intentFilter);
    }

    /**
     * For multi-sub, all subs should be listen.
     */
    private void listenPhoneState() {
        Phone[] phones = PhoneFactory.getPhones();
        for (Phone phone : phones) {
            int subscriptionId = phone.getSubId();
            if (subscriptionId >= 0) {
                if (!mServiceStates.containsKey(subscriptionId)) {
                    mServiceStates.put(subscriptionId, ServiceState.STATE_POWER_OFF);
                }
                PhoneStateListener listener = new PhoneStateListener(subscriptionId) {
                    @Override
                    public void onServiceStateChanged(ServiceState serviceState) {
                        int newState = serviceState.getState();
                        if (newState == ServiceState.STATE_IN_SERVICE
                                && mServiceStates.get(mSubId) != newState) {
                            Log.d(this, "[PhoneStateListener]ServiceState of sub %s changed "
                                    + "%s -> IN_SERVICE, reset PhoneAccount", mSubId,
                                    mServiceStates.get(mSubId));
                            // TODO: each sub-account should be reset alone
                            tearDownAccounts();
                            setupAccounts();
                            broadcastAccountChanged();
                        }
                        mServiceStates.put(mSubId, newState);
                    }
                };
                mTelephonyManager.listen(listener, PhoneStateListener.LISTEN_SERVICE_STATE);
            }
        }
    }

    /**
     * Update the flag which is used to determine whether accounts does change.
     * @param account
     * For ALPS01965388.
     */
    private void updateAccountChangeStatus(PhoneAccount account) {
        if (account == null) {
            Log.d(this, "updateAccountChangeStatus, account is null!");
            return;
        }

        if (mPhoneAccountChanged) {
            return;
        }

        // Get old PhoneAccount if there has one.
        PhoneAccount oldAccount = mTelecomManager.getPhoneAccount(account.getAccountHandle());
        // Check whether the account does change.
        if (!account.equals(oldAccount)) {
            mPhoneAccountChanged = true;
            Log.d(this, "updateAccountChangeStatus, one account changed.");
        }
    }

    /**
     * Register all PSTN PhoneAccount if any one of them changed.
     * For ALPS01965388.
     */
    private void registerIfAccountChanged() {
        if (mPhoneAccountChanged) {
            for (AccountEntry accountEntry : mAccounts) {
                mTelecomManager.registerPhoneAccount(accountEntry.mAccount);
            }
        } else {
            Log.d(this, "registerIfAccountChanged, no PhoneAccount changed, so do nothing");
        }
    }

    /**
     * add for c2k svlte project.
     *
     * solution 2.0(ro.mtk.c2k.slot2.support = 1):
     *   C+C, disable not default data account
     * solution 1.5  (ro.mtk.c2k.slot2.support = 0):
     *   C+G: disable the C account not set as default 3/4G
     *
     * @param subId
     * @return
     */
    private boolean isPhoneUnAvailableForCall(int subId){
        int counter = 0;
        int defaultDataSubId = android.telephony.SubscriptionManager
                .getDefaultDataSubId();
        int phoneNum = mTelephonyManager.getPhoneCount();
        for (int i = 0; i < phoneNum; i++) {
            if (SvlteUiccUtils.SIM_TYPE_CDMA == SvlteUiccUtils
                    .getInstance().getSimType(i)) {
                counter++;
            }
        }
        int mainSlotId = -1;
        String currLteSim = SystemProperties.get("persist.radio.simswitch", "");
        Log.d(this, "current 3/4G Sim = " + currLteSim);
        if (!TextUtils.isEmpty(currLteSim)) {
            mainSlotId = Integer.parseInt(currLteSim) - 1;
        }

        Log.d(this, "isNetworkRoaming = " +
                mTelephonyManager.isNetworkRoaming(subId));
        if (counter == phoneNum
                && SubscriptionManager.getSlotId(subId) != mainSlotId) {
            // For ALPS02281291. @{
            // C + C, we should enable the phone account when it is CT 6M
            // Project and the network status is roaming
            if (FeatureOption.isMtkC2k6MSupport() &&
                mTelephonyManager.isNetworkRoaming(subId)) {
                return false;
            } else {
                return true;
            }
            // @}
        }
        if (!FeatureOption.isMtkSvlteSolution2Support()) {
            int soltId = SubscriptionManager.getSlotId(subId);
            //C+G, default LTE is on G
            if (counter == 1
                    && counter < phoneNum
                    && SubscriptionManager.isValidSlotId(mainSlotId)
                    && mainSlotId != soltId
                    && SvlteUiccUtils.getInstance().getSimType(mainSlotId)
                    != SvlteUiccUtils.SIM_TYPE_CDMA) {
                return true;
            }
        }
        return false;
    }
}
