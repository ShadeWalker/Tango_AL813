package com.mediatek.phone.ext;

import android.os.Message;
import android.widget.EditText;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;

public class DefaultMmiCodeExt implements IMmiCodeExt {
    @Override
    public void onMmiDailogShow(Message cancelMessage) {
        // do nothing
    }

    @Override
    public boolean showUssdInteractionDialog(Phone phone, EditText editText) {
        return false;
    }

    @Override
    public void configBeforeMmiDialogShow(MmiCode mmiCode) {
        // do nothing
    }

    @Override
    public boolean skipPlayingUssdTone() {
        return false;
    }
}
