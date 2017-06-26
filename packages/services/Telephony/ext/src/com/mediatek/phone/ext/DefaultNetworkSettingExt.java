package com.mediatek.phone.ext;

import com.android.internal.telephony.OperatorInfo;

import java.util.List;

public class DefaultNetworkSettingExt implements INetworkSettingExt {

    @Override
    public List<OperatorInfo> customizeNetworkList(List<OperatorInfo> operatorInfoList, int subId) {
        return operatorInfoList;
    }

    @Override
    public boolean onPreferenceTreeClick(OperatorInfo operatorInfo, int subId) {
        return false;
    }

}
