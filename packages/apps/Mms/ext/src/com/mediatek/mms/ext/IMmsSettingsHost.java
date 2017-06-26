package com.mediatek.mms.ext;

import java.util.HashMap;

public interface IMmsSettingsHost {

    void setSmsValues(HashMap<String, String> values);

    void registerSmsStateReceiver();
}
