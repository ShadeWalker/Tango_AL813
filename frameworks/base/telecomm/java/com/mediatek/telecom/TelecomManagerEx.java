package com.mediatek.telecom;

/**
 * @hide
 */
public class TelecomManagerEx {
    //-------------For VoLTE SS------------------
    /// M: CC022: Error message due to VoLTE SS checking @{
    /**
     * Here defines a special disconnect reason to distinguish that the disconnected call is
     * a VoLTE SS request without data connection open. (Telephony -> Telecomm)
     * see android.telecom.DisconnectCause.mDisconnectReason
     * @hide
     */
    public static final String DISCONNECT_REASON_VOLTE_SS_DATA_OFF =
            "disconnect.reason.volte.ss.data.off";
    /// @}

    /// M: CC073: Broadcast phone account changes @{
    /**
     * The action used to broadcast phone account have changed.
     * @hide
     */
    public static final String ACTION_PHONE_ACCOUNT_CHANGED =
            "android.telecom.action.PHONE_ACCOUNT_CHANGED";
    /**
     * The action used to broadcast default phone account has changed.
     * @hide
     */
    public static final String ACTION_DEFAULT_ACCOUNT_CHANGED =
            "android.telecom.action.DEFAULT_ACCOUNT_CHANGED";
    /// @}

    /// M: CC070: Suggested phone account @{
    /**
     * The extra used with an {@link android.content.Intent#ACTION_CALL} and
     * {@link android.content.Intent#ACTION_DIAL} {@code Intent} to specify a
     * {@link PhoneAccountHandle} which is suggested to use when making the call.
     * <p class="note">
     * Retrieve with {@link android.content.Intent#getParcelableExtra(String)}.
     * @hide
     */
    public static final String EXTRA_SUGGESTED_PHONE_ACCOUNT_HANDLE =
            "android.telecom.extra.SUGGESTED_PHONE_ACCOUNT_HANDLE";
    /// @}

    //-------------For VoLTE normal call switch to ECC------------------
    /**
     * Here defines a special key to distinguish the call is marked as Ecc by NW.
     * Its value is should be Boolean. see IConnectionServiceAdapter.updateExtras()
     * @hide
     */
    public static final String EXTRA_VOLTE_MARKED_AS_EMERGENCY = "com.mediatek.volte.isMergency";

    //-------------For VoLTE PAU field------------------
    /**
     * Here defines a special key to pass "pau" information of the call.
     * Its value should be String. see IConnectionServiceAdapter.updateExtras()
     * @hide
     */
    public static final String EXTRA_VOLTE_PAU_FIELD = "com.mediatek.volte.pau";

    //-------------For VoLTE Conference Call
    /**
     * Optional extra for {@link android.content.Intent#ACTION_CALL} and
     * {@link android.content.Intent#ACTION_CALL_PRIVILEGED} containing a phone
     * number {@link ArrayList} that used to launch the volte conference call.
     * The phone number in the list may be normal phone number, sip phone
     * address or IMS call phone number. This extra takes effect only when the
     * {@link #EXTRA_VOLTE_CONF_CALL_DIAL} is true.
     * @hide
     */
    public static final String EXTRA_VOLTE_CONF_CALL_NUMBERS =
            "com.mediatek.volte.ConfCallNumbers";

    /**
     * Optional extra for {@link android.content.Intent#ACTION_CALL} and
     * {@link android.content.Intent#ACTION_CALL_PRIVILEGED} containing an
     * boolean value that determines if it should launch a volte conference
     * call.
     * @hide
     */
    public static final String EXTRA_VOLTE_CONF_CALL_DIAL = "com.mediatek.volte.ConfCallDial";

    /**
     * extra Boolean-key info in {@link android.telecom.TelecomManager#EXTRA_INCOMING_CALL_EXTRAS}
     * to indicate the incoming call is VoLTE conference invite.
     * @hide
     */
    public static final String EXTRA_VOLTE_CONF_CALL_INCOMING = "com.mediatek.volte.conference.invite";
}
