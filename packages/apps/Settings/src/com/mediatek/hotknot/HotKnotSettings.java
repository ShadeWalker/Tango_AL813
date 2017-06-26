package com.mediatek.settings.hotknot;

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.SettingsActivity;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.SearchIndexableRaw;
import com.android.settings.widget.SwitchBar;

import com.mediatek.hotknot.HotKnotAdapter;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.List;

public class HotKnotSettings extends SettingsPreferenceFragment implements Indexable  {
    private static final String TAG = "HotKnotSettings";
    private HotKnotEnabler mHotKnotEnabler;
    private IntentFilter mIntentFilter;
    private HotKnotAdapter mAdapter;
    
    private SwitchBar mSwitchBar;

    /**
     * The broadcast receiver is used to handle the nfc adapter state changed
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SettingsActivity activity = (SettingsActivity) getActivity();
        mAdapter = HotKnotAdapter.getDefaultAdapter(activity);
        if(mAdapter == null) {
            Xlog.d(TAG, "Hotknot adapter is null, finish Hotknot settings");
            getActivity().finish();
        }
          
        mIntentFilter = new IntentFilter(HotKnotAdapter.ACTION_ADAPTER_STATE_CHANGED);        
    }

    @Override
    public void onStart() {
        super.onStart();

        // On/off switch 
        final SettingsActivity activity = (SettingsActivity) getActivity();
        mSwitchBar = activity.getSwitchBar();
        Xlog.d(TAG, "onCreate, mSwitchBar = " + mSwitchBar);        
        mHotKnotEnabler = new HotKnotEnabler(activity, mSwitchBar);            
    }
        
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.hotknot_settings, container, false);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if(mHotKnotEnabler != null) {
            mHotKnotEnabler.teardownSwitchBar();
        }
    }

    public void onResume() {
        super.onResume();
        if (mHotKnotEnabler != null) {
        	mHotKnotEnabler.resume();
        }
        getActivity().registerReceiver(mReceiver, mIntentFilter);
    }

    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mReceiver);
        if (mHotKnotEnabler != null) {
        	mHotKnotEnabler.pause();
        }
    }
    
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
    new BaseSearchIndexProvider() {
        @Override
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            final List<SearchIndexableRaw> result = new ArrayList<SearchIndexableRaw>();
            final Resources res = context.getResources();

            HotKnotAdapter adapter = HotKnotAdapter.getDefaultAdapter(context);
            if (adapter != null) {
                // Add fragment title
                SearchIndexableRaw data = new SearchIndexableRaw(context);
                data.title = res.getString(R.string.hotknot_settings_title);
                data.screenTitle = res.getString(R.string.hotknot_settings_title);
                data.keywords = res.getString(R.string.hotknot_settings_title);
                result.add(data);
            }
            return result;
        }
    };
        
        
}

class HotKnotDescriptionPref extends Preference {
    public HotKnotDescriptionPref(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
    }

    public HotKnotDescriptionPref(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        TextView title = (TextView) view.findViewById(android.R.id.title);
        if (title != null) {
            title.setSingleLine(false);
            title.setMaxLines(3);
        }
    }
   
}
