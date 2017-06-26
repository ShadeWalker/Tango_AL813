package com.mediatek.mms.ext;


import android.content.Context;
import android.content.ContextWrapper;

public class DefaultMmsSettingsExt extends ContextWrapper implements IMmsSettingsExt {
    private IMmsSettingsHost mHost = null;
    public DefaultMmsSettingsExt(Context context) {
        super(context);
    }

    public void init(IMmsSettingsHost host) {
        mHost = host;
        return;
    }

    public String getSmsServiceCenter() {
        return "";
    }
}
