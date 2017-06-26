package com.mediatek.phone.ext;

import android.os.Message;
import android.widget.EditText;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;

public interface IMmiCodeExt {
    /**
     * use the cancel message will dismiss the MMI/USSD dialog
     * @param cancelMessage
     */
    void onMmiDailogShow(Message cancelMessage);

    /**
     * called when need to response to Ussd dialog operation.
     *
     * @param phone
     * @param editText
     *            User input String on Ussd Dialog.
     * @return true if response operation success.
     */
    boolean showUssdInteractionDialog(Phone phone, EditText editText);

    /**
     * Called when display mmi code dialog for MMI dial.
     *
     * @param mmiCode MMI code.
     */
    void configBeforeMmiDialogShow(MmiCode mmiCode);

    /**
     * Called when need to judge whether to play USSD tone.
     *
     * @return true if it is User initiated, then not to play USSD tone.
     */
    boolean skipPlayingUssdTone();
}
