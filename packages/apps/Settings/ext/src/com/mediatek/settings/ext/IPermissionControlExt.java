package com.mediatek.settings.ext;


import android.preference.PreferenceGroup;
import android.provider.SearchIndexableData;

import java.util.List;

public interface IPermissionControlExt {
    /**
     * to add a permission contorl button
     * @return
     */

    public void addPermSwitchPrf(PreferenceGroup prefGroup);

    public void enablerResume();

    public void enablerPause();

    public void addAutoBootPrf(PreferenceGroup prefGroup);

    public  List<SearchIndexableData> getRawDataToIndex(boolean enabled);

}