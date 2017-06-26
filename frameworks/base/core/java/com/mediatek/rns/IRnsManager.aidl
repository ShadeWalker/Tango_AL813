package com.mediatek.rns;

import android.os.Messenger;

/** {@hide} */
interface IRnsManager
{
    int getAllowedRadioList(int capability);
    int getTryAnotherRadioType(int failedNetType);
    int getRnsState();
    boolean isNeedWifiConnected(int flag);
}