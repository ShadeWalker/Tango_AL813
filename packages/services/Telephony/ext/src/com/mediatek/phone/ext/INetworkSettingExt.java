package com.mediatek.phone.ext;

import com.android.internal.telephony.OperatorInfo;

import java.util.List;


public interface INetworkSettingExt {

    /**
     * Let plug-in customize the OperatorInfo list before display.
     *
     * @param operatorInfoList The OperatorInfo list get from framework
     * @param subId The sub id user selected
     * @return new OperatorInfo list
     */
    public List<OperatorInfo> customizeNetworkList(List<OperatorInfo> operatorInfoList, int subId);

    /**
     * CU feature, customize forbidden Preference click, pop up a toast.
     * @param operatorInfo Preference's operatorInfo
     * @param subId sub id
     * @return true It means the preference click will be done
     */
    public boolean onPreferenceTreeClick(OperatorInfo operatorInfo, int subId);
}
