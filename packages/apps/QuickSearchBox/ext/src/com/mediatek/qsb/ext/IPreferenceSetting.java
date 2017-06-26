package com.mediatek.qsb.ext;

import android.content.Context;
import com.mediatek.common.search.SearchEngine;

public interface IPreferenceSetting {
    SearchEngine getDefaultSearchEngine(Context context);
}
