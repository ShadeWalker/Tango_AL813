
package com.mediatek.datausage;

import static android.net.NetworkPolicyManager.computeLastCycleBoundary;
import static android.net.NetworkPolicyManager.computeNextCycleBoundary;
import static android.net.NetworkStatsHistory.FIELD_RX_BYTES;
import static android.net.NetworkStatsHistory.FIELD_TX_BYTES;
import static android.net.NetworkTemplate.buildTemplateMobileAll;
import static android.net.NetworkTemplate.buildTemplateWifiWildcard;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.INetworkStatsSession;
import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.net.NetworkStatsHistory;
import android.net.NetworkTemplate;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.net.ChartData;
import com.android.settings.net.NetworkPolicyEditor;
import com.mediatek.common.operamax.ILoaderService;
import com.mediatek.common.operamax.ILoaderStateListener;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.cdma.CdmaUtils;
import com.mediatek.settings.ext.IDataUsageSummaryExt;
import com.mediatek.settings.sim.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class OverViewTabAdapter extends BaseExpandableListAdapter {

    private static final String TAG = "OverViewTabAdapter";
    private static final String TEST_SUBSCRIBER_PROP = "test.subscriberid";
    private static final long DAY = 28800000L; // 60 * 60 * 8 * 1000;
    private static final int POLICY_NULL_FLAG = -2;

    // private static final int GROUP_ROAMING = 1;
    private int mWifiPos;
    private int mMobilePos;

    private List<SubscriptionInfo> mSublist;
    private Context mContext;
    private List<String> mGroup;
    private INetworkStatsSession mStatsSession;
    private int mSimNum;
    private long mWifiTotal;
    private long mWifiToday;
    private long[] mMobileTotal;
    private long[] mLimitBytes;
    private static final long REF_WIFI = 1048576000; // 1000 * MB_IN_BYTES
    private NetworkPolicyManager mPolicyManager;
    private NetworkPolicyEditor mPolicyEditor;
    private static final int PROGRESS_NONE = 0;
    private static final int PROGRESS_FULL = 100;
    private static final int PERCENT_NONE = 0;
    private static final int PERCENT_FULL = 1;
    private static final double PERCENT_LOW = 0.01;
    private static final int SIM_COLOR_TEAL = 0;
    private static final int SIM_COLOR_BLUE = 1;
    private static final int SIM_COLOR_INDIGO = 2;
    private static final int SIM_COLOR_PURPLE = 3;
    private static final int SIM_COLOR_PINK = 4;
    private static final int SIM_COLOR_RED = 5;
    private static final int OVER_COLOR_RED = 10;
    private static final int LOADER_CHART_DATA = 2;
    private static final int CYCLE_RANGE_OVER_WEEK = 4;
    private static final int THOUSAND = 1000;
    // for date string, such as 20070809T010000Z
    private static final int YEAR_START_INDEX = 0;
    private static final int YEAR_END_INDEX = 3; // 2007
    private static final int MONTH_START_INDEX = 4;
    private static final int MONTH_END_INDEX = 5; // 08
    private static final int DAY_START_INDEX = 6;
    private static final int DAY_END_INDEX = 7; // 09
    /** M: CMCC/CT spec for Modify data usage SIM name indicator @{ */
    private static IDataUsageSummaryExt mExt;
    /** @} */

    private LinearLayout mDataSavingZone;
    private Switch mDataSavingSwitch;
    private View mDataSavingView;

    // for Opera Max: data saving
    public static final String mSavingPkgName = "com.opera.max.loader";
    private static final String mSavingServiceName = "com.opera.max.loader.LoaderService";
    public Handler mUiHandler;

    private BidiFormatter mBidiFormatter;

    public OverViewTabAdapter(Context context, INetworkStatsSession statsSession) {
        Log.d(TAG, "OverViewTabAdapter()");
        mContext = context;
        mStatsSession = statsSession;
        // support RTL
        mBidiFormatter = BidiFormatter.getInstance();
        initPolicy();
        getMobileState();
        initData();
        /** M: CMCC/CT spec for Modify data usage SIM name indicator @{ */
        mExt = UtilsExt.getDataUsageSummaryPlugin(context);
        /** @} */
        mUiHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                int state = 0;
                try {
                    state = Settings.System.getInt(
                        mContext.getContentResolver(),
                        Settings.System.DATA_SAVING_KEY);
                } catch (SettingNotFoundException e) {
                    state = 0;
                }
                if (OverViewTabAdapter.this.mDataSavingSwitch != null) {
                    OverViewTabAdapter.this.mDataSavingSwitch.setChecked(state == 1 ? true : false);
                }
            }
        };
    }

    private void getMobileState() {
        Log.d(TAG, "getMobileState()");
        mSublist = SubscriptionManager.from(mContext).getActiveSubscriptionInfoList();
        mSimNum = mSublist == null ? 0 : mSublist.size();
        mMobileTotal = new long[mSimNum];
        mLimitBytes = new long[mSimNum];
    }

    private void getWifiData() {
        Log.d(TAG, "getWifiData()");
        ChartData data = new ChartData();
        try {
            data.network = mStatsSession.getHistoryForNetwork(
                    buildTemplateWifiWildcard(), FIELD_RX_BYTES
                            | FIELD_TX_BYTES);
        } catch (RemoteException e) {
            Log.d(TAG, "Remote Exception happens");
        }
        NetworkStatsHistory.Entry entry = null;
        long historyStart = data.network.getStart();
        long historyEnd = data.network.getEnd();
        final long now = System.currentTimeMillis();
        Log.d(TAG, "historyStart = " + historyStart + " historyEnd = " + historyEnd + " now = "
                + now);
        long cycleEnd = historyEnd > now ? historyEnd : now;
        long cycleEndBak = cycleEnd;
        long cycleStart = historyStart;
        while (cycleEnd > historyStart) {
            // no policy defined cycles; show entry for each four-week period
            cycleStart = cycleEnd - (DateUtils.WEEK_IN_MILLIS * CYCLE_RANGE_OVER_WEEK);
            if (cycleStart <= now && now <= cycleEnd) {
                // Once current time match a CycleItem,pick that CycleItem usage
                Log.d(TAG, "cycleStart <= now && now <= cycleEnd");
                break;
            }
            cycleEndBak = cycleEnd;
            cycleEnd = cycleStart;
        }

        entry = data.network.getValues(cycleStart, cycleEndBak, now, null);
        Log.d(TAG, "cycleStart = " + cycleStart + " cycleEndBak = " + cycleEndBak);
        mWifiTotal = entry != null ? entry.rxBytes + entry.txBytes : 0;
        entry = data.network.getValues(getUtcDateMillis(), now, now, null);
        mWifiToday = entry != null ? entry.rxBytes + entry.txBytes : 0;
        Log.d(TAG, "mWifiTotal = " + mWifiTotal + " mWifiToday = "
                + mWifiToday);
    }

    private int calcWifiTodayProgress(long todayUsage, long totalUsage) {
        Log.d(TAG, "calcWifiTodayProgress() todayUsage : " + todayUsage + " totalUsage : "
                + totalUsage);
        double per = PERCENT_NONE;
        if (todayUsage == 0) {
            per = PERCENT_NONE;
        } else {
            per = (double) todayUsage / totalUsage;
            if (per > PERCENT_NONE && per < PERCENT_LOW) {
                per = PERCENT_LOW;
            }
        }
        int value = (int) (per * PROGRESS_FULL);
        Log.d(TAG, "calcWifiTodayProgress() value : " + value);
        return value;
    }

    private int calcWifiTotalProgress(long todayUsage) {
        return (todayUsage == 0) ? PROGRESS_NONE : PROGRESS_FULL;
    }

    private int calcMobileProgress(long totalUsage, long limitUsage) {
        Log.d(TAG, "calcMobileProgress() totalUsage = " + totalUsage + " limitUsage = "
                + limitUsage);
        double per = PERCENT_NONE;
        if (limitUsage < 0) {
            per = (totalUsage == 0) ? PERCENT_NONE : PERCENT_FULL;
        } else if (totalUsage <= limitUsage) {
            per = (double) totalUsage / limitUsage;
            Log.d(TAG, "limitUsage >=  totalUsage  per = " + per);
        } else {
            per = PERCENT_FULL;
            Log.d(TAG, "limitUsage < totalUsage ,so set per = 1");
        }
        if (per > PERCENT_NONE && per < PERCENT_LOW) {
            per = PERCENT_LOW;
        }
        int value = (int) (per * PROGRESS_FULL);
        Log.d(TAG, "calcMobileProgress value " + value);
        return value;
    }

    private void getMobileData() {
        Log.d(TAG, "getMobileData()");
        if (mSublist == null) {
            return;
        }
        ChartData data = new ChartData();
        int count = 0;
        NetworkTemplate template;
        NetworkPolicy policy;

        for (SubscriptionInfo subInfo : mSublist) {
            String subscriberId = TelephonyManager.from(mContext).getSubscriberId(subInfo.getSubscriptionId());
            template = buildTemplateMobileAll(subscriberId);

            CdmaUtils.fillTemplateForCdmaLte(template, subInfo.getSubscriptionId());

            try {
                data.network = mStatsSession.getHistoryForNetwork(template,
                        FIELD_RX_BYTES | FIELD_TX_BYTES);
                mLimitBytes[count] = mPolicyEditor
                        .getPolicyLimitBytes(template);
            } catch (Exception e) {
                // TODO: handle exception
                mLimitBytes[count] = POLICY_NULL_FLAG;
                count++;
                continue;
            }

            long historyStart = data.network.getStart();
            long historyEnd = data.network.getEnd();
            final long now = System.currentTimeMillis();
            policy = mPolicyEditor.getPolicy(template);
            long cycleEnd = historyEnd;
            long cycleEndBak = historyEnd;
            long cycleStart = historyStart;
            if (policy != null) {
                // find the next cycle boundary
                cycleEnd = computeNextCycleBoundary(historyEnd, policy);
                // walk backwards, generating all valid cycle ranges
                while (cycleEnd > historyStart) {
                    cycleStart = computeLastCycleBoundary(cycleEnd, policy);
                    if (cycleStart <= now && now <= cycleEnd) {
                        Log.d(TAG, "cycleStart <= now && now <= cycleEnd");
                        // Once current time match a CycleItem,pick that
                        // CycleItem usage
                        break;
                    }
                    cycleEndBak = cycleEnd;
                    cycleEnd = cycleStart;
                }
            } else {
                // no policy defined cycles; show entry for each four-week
                // period
                while (cycleEnd > historyStart) {
                    cycleStart = cycleEnd - (DateUtils.WEEK_IN_MILLIS * CYCLE_RANGE_OVER_WEEK);
                    if (cycleStart <= now && now <= cycleEnd) {
                        break;
                    }
                    cycleEndBak = cycleEnd;
                    cycleEnd = cycleStart;
                }
            }
            Log.d(TAG, "cycleEndBak=" + cycleEndBak + "cycleStart=" + cycleStart);

            NetworkStatsHistory.Entry entry = null;
            entry = data.network.getValues(cycleStart, cycleEndBak, now, null);
            mMobileTotal[count] = entry != null ? entry.rxBytes
                    + entry.txBytes : 0;
            Log.d(TAG, "mMobileTotal" + "[" + count + "]="
                    + mMobileTotal[count] + "mLimitBytes" + "[" + count
                    + "]=" + mLimitBytes[count]);
            count++;
        }
    }

    private void initPolicy() {
        Log.d(TAG, "initPolicy()");
        if (mPolicyManager == null && mPolicyEditor == null) {
            mPolicyManager = NetworkPolicyManager.from(mContext);
            mPolicyEditor = new NetworkPolicyEditor(mPolicyManager);
            mPolicyEditor.read();
        }
    }

    private void initData() {
        Log.d(TAG, "initData()");
        mGroup = new ArrayList<String>();
        if (mSimNum != 0) {
            mGroup.add(mContext.getString(R.string.mtk_datausage_overview_mobile_title));
            mMobilePos = 0;
            mGroup.add(mBidiFormatter.unicodeWrap(mContext.getString(R.string.wifi_settings)));
            mWifiPos = 1;
        } else {
            mGroup.add(mBidiFormatter.unicodeWrap(mContext.getString(R.string.wifi_settings)));
            mWifiPos = 0;
        }

    }

    private static String getActiveSubscriberId(Context context) {
        final TelephonyManager tele = TelephonyManager.from(context);
        final String actualSubscriberId = tele.getSubscriberId();
        return SystemProperties.get(TEST_SUBSCRIBER_PROP, actualSubscriberId);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getChildId(int arg0, int arg1) {
        // TODO Auto-generated method stub
        return arg1;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition,
            boolean isLastChild, View convertView, ViewGroup parent) {
        TextView textTitle;
        TextView textUsage;
        ProgressBar progress;
        View view = null;
        String usage;
        if (groupPosition == mWifiPos) {
            LayoutInflater inflate = LayoutInflater.from(mContext);
            view = inflate
                    .inflate(R.layout.data_usage_exp_child_wifi, null);
            textTitle = (TextView) view.findViewById(R.id.wifi_title);
            textUsage = (TextView) view.findViewById(R.id.wifi_usage);
            progress = (ProgressBar) view.findViewById(R.id.wifi_progressbar);
            getWifiData();
            if (childPosition == 0) {
                textTitle.setText(mContext
                        .getString(R.string.mtk_datausage_overview_wifi_today));
                usage = Formatter.formatFileSize(mContext, mWifiToday);
                Log.d(TAG, "childPosition=" + childPosition + " and usage= "
                        + usage);
                textUsage.setText(usage);
                progress.setSecondaryProgress(calcWifiTodayProgress(mWifiToday,
                        mWifiTotal));
            } else {
                textTitle.setText(mContext
                        .getString(R.string.mtk_datausage_overview_wifi_total));
                usage = Formatter.formatFileSize(mContext, mWifiTotal);
                Log.d(TAG, "childPosition = " + childPosition + " and usage= "
                        + usage);
                textUsage.setText(usage);
                progress.setSecondaryProgress(calcWifiTotalProgress(mWifiTotal));
            }
        } else if (groupPosition == mMobilePos) {
            LayoutInflater inflate = LayoutInflater.from(mContext);
            int childCount = getChildrenCount(groupPosition);
            Log.d(TAG, "childCount: " + childCount + ", mSimSum: " + mSimNum);
            // the last item
            if (childCount == (mSimNum + 1) && childPosition == (childCount - 1)) {
                view = inflate.inflate(R.layout.data_saving_screen, null);
                mDataSavingZone = (LinearLayout) view.findViewById(R.id.data_saving_zone);
                inflateLockScreenView(inflate);
            } else {
                getMobileData();
                if (mSublist != null) {
                    int [] tintArr = mContext.getResources()
                            .getIntArray(com.android.internal.R.array.sim_colors);
                    int simColor = mSublist.get(childPosition).getIconTint();
                    Arrays.sort(tintArr);
                    int index = Arrays.binarySearch(tintArr, simColor);
                    Log.d(TAG, "usage : " + mMobileTotal[childPosition] + " limit : "
                            + mLimitBytes[childPosition] + ", index: " + index);
                    for (int i : tintArr) {
                        Log.d(TAG, "i: " + i);
                    }
                    Log.d(TAG, ",simColor: " + simColor);
                    if (mLimitBytes[childPosition] >= 0
                            && mMobileTotal[childPosition] > mLimitBytes[childPosition]) {
                        index = SIM_COLOR_RED; // add for OverUsage
                    }
                    switch (index) {
                        case SIM_COLOR_TEAL:
                            view = inflate.inflate(
                                    R.layout.data_usage_exp_child_mobile_color_teal, null);
                            break;
                        case SIM_COLOR_BLUE:
                            view = inflate.inflate(
                                    R.layout.data_usage_exp_child_mobile_color_blue, null);
                            break;
                        case SIM_COLOR_INDIGO:
                            view = inflate.inflate(
                                    R.layout.data_usage_exp_child_mobile_color_indigo, null);
                            break;
                        case SIM_COLOR_PURPLE:
                            view = inflate.inflate(
                                    R.layout.data_usage_exp_child_mobile_color_purple, null);
                            break;
                        case SIM_COLOR_PINK:
                            view = inflate.inflate(
                                    R.layout.data_usage_exp_child_mobile_color_pink, null);
                            break;
                        case SIM_COLOR_RED:
                            view = inflate.inflate(
                                    R.layout.data_usage_exp_child_mobile_color_red, null);
                            break;
                        default:
                            view = inflate.inflate(
                                    R.layout.data_usage_exp_child_mobile_color_teal, null);
                            break;
                    }
                    textTitle = (TextView) view.findViewById(R.id.mobile_title);
                    textUsage = (TextView) view.findViewById(R.id.mobile_usage);
                    int progressBarColor = mSublist.get(childPosition).getIconTint();
                    progress = (ProgressBar) view.findViewById(R.id.mobile_progressbar);
                    textTitle.setText(mSublist.get(childPosition).getDisplayName());
                    /**
                     * M: customize textview background resource, for example add
                     * SIM name indicator @{
                     */
                    mExt.customizeTextViewBackgroundResource(progressBarColor, textTitle);
                    /** @} */
                    // / M: customize MobileData Summary
                    mExt.customizeMobileDataSummary(view, textTitle,
                            mSublist.get(childPosition).getSimSlotIndex());
                    if (mLimitBytes[childPosition] > -1) {
                        if (mLimitBytes[childPosition] >= 0
                                && mMobileTotal[childPosition] > mLimitBytes[childPosition]) {
                            Log.d(TAG, "Usage bytes is bigger than the limit bytes , show warning");
                            textUsage.setText(mContext.getString(
                                    R.string.mtk_datausage_overview_mobile_usage_warning,
                                    Formatter
                                    .formatFileSize(mContext, mMobileTotal[childPosition]
                                            - mLimitBytes[childPosition])));
                        } else {
                            textUsage.setText(mContext.getString(
                                    R.string.mtk_datausage_overview_mobile_usage,
                                    Formatter.formatFileSize(mContext,
                                            mMobileTotal[childPosition]),
                                            Formatter.formatFileSize(mContext,
                                                    mLimitBytes[childPosition])));
                        }
                    } else {
                        textUsage.setText(Formatter.formatFileSize(mContext,
                                mMobileTotal[childPosition]));
                    }
                    progress.setProgress(calcMobileProgress(
                            mMobileTotal[childPosition], mLimitBytes[childPosition]));
                }
            }

        }
        return view;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        if (groupPosition == mWifiPos) {
            return 2;
        } else if (groupPosition == mMobilePos) {
            if (UtilsExt.isPackageExist(mContext, mSavingPkgName)) {
                Log.d(TAG, "add Data Saving item");
                return mSimNum + 1;
            } else {
                return mSimNum;
            }
        }
        return 0;
    }

    @Override
    public Object getGroup(int arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getGroupCount() {
        // TODO Auto-generated method stub
        return mGroup.size();
    }

    @Override
    public long getGroupId(int arg0) {
        // TODO Auto-generated method stub
        return arg0;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded,
            View convertView, ViewGroup parent) {
        TextView item;
        if (null == convertView || !(convertView instanceof TextView)) {
            LayoutInflater factory = LayoutInflater.from(mContext);
            item = (TextView) factory.inflate(R.layout.list_group_header, null);
        } else {
            item = (TextView) convertView;
        }
        item.setText(mGroup.get(groupPosition));
        return item;
    }

    @Override
    public boolean hasStableIds() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isChildSelectable(int arg0, int arg1) {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * Return the millisecond of the 00:00:00 of the given day.
     *
     * @return the millisecond
     */
    public long getUtcDateMillis() {
        SimpleDateFormat sdf =
                new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String date = sdf.format(new Date(System.currentTimeMillis()));
        int year = Integer.valueOf(
                date.substring(YEAR_START_INDEX, YEAR_END_INDEX + 1))
                .intValue();
        int month = Integer.valueOf(
                date.substring(MONTH_START_INDEX, MONTH_END_INDEX + 1))
                .intValue();
        int day = Integer.valueOf(
                date.substring(DAY_START_INDEX, DAY_END_INDEX + 1)).intValue();
        Calendar gc = Calendar.getInstance(TimeZone.getDefault());
        gc.set(year, month - 1, day, 0, 0, 0);
        return gc.getTimeInMillis() / THOUSAND * THOUSAND;
    }

    /**
     * Update the simInfoList and networkPolicy when switch TAB.
     */
    public void updateAdapter() {
        Log.d(TAG, "updateAdapter()");
        if (mPolicyEditor != null) {
            mPolicyEditor.read();
        }
        getMobileState();
        initData();
    }

    /**
     * M: Add "Data Saving" preference into Mobile Group
     */
    private void inflateLockScreenView(LayoutInflater inflater) {
        if (mDataSavingZone != null) {
            mDataSavingSwitch = new Switch(inflater.getContext());
            mDataSavingView = inflatePreference(inflater,
                    mDataSavingZone, mDataSavingSwitch);
            mDataSavingView.setClickable(true);
            mDataSavingView.setFocusable(true);
            // get state from settings provider
            int state = 0;
            try {
                state = Settings.System.getInt(
                        mContext.getContentResolver(),
                        Settings.System.DATA_SAVING_KEY);
                // "data_saving_key");
                Log.d(TAG, "get state from provider = " + state);
            } catch (SettingNotFoundException e) {
                state = 0;
                Log.d(TAG, "Get data from provider failure");
            }

            mDataSavingSwitch.setChecked(state == 1 ? true : false);

            mDataSavingSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    Log.d(TAG, "Data Saving isChecked = " + isChecked);
                    try {
                        if (mSavingService != null) {
                            if (isChecked) {
                                mSavingService.startSaving();
                            } else {
                                mSavingService.stopSaving();
                            }
                        }
                    } catch (RemoteException e) {
                        Log.d(TAG, "Exception happened! " + e.getMessage());
                    }
                }
            });
            // Set preference title
            TextView title = (TextView) mDataSavingView.findViewById(android.R.id.title);
            title.setText(R.string.data_saving_title);
            mDataSavingZone.addView(mDataSavingView);
            mDataSavingView.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Log.d(TAG, "launch data saving activity");
                    try {
                        if (mSavingService != null) {
                            mSavingService.launchOperaMAX();
                        }
                    } catch (RemoteException e) {
                        Log.d(TAG, "Exception happened! " + e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * Inflate a {@link Preference} style layout, adding the given {@link View}
     * widget into {@link android.R.id#widget_frame}.
     */
    private static View inflatePreference(LayoutInflater inflater, ViewGroup root, View widget) {
        final View view = inflater.inflate(R.layout.preference, root, false);
        final LinearLayout widgetFrame = (LinearLayout) view.findViewById(
                android.R.id.widget_frame);
        widgetFrame.addView(widget, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        return view;
    }

    private ILoaderStateListener.Stub stateListener = new ILoaderStateListener.Stub() {
        @Override
        public void onTunnelState(int state) throws RemoteException {
            Log.d(TAG, "Loader service onTunnelState");
            // tunnelState = state;
            // showTunnelState();
        }

        @Override
        public void onSavingState(int state) throws RemoteException {
            Log.d(TAG, "Loader service onSavingState");
            OverViewTabAdapter.this.mUiHandler.sendEmptyMessage(0);
        }
    };

    public ILoaderService mSavingService;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Loader service Connected");
            mSavingService = ILoaderService.Stub.asInterface(service);
            try {
                mSavingService.registerStateListener(stateListener);
            } catch (RemoteException e) {
                Log.d(TAG, "Exception happened! " + e.getMessage());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Loader service DisConnected");
            try {
                mSavingService.unregisterStateListener(stateListener);
            } catch (RemoteException e) {
                Log.d(TAG, "Exception happened! " + e.getMessage());
            }
            mSavingService = null;
        }
    };

    public void bindOperaMaxLoader() {
        if (UtilsExt.isPackageExist(mContext, mSavingPkgName)) {
            Log.d(TAG, "bindOperaMaxLoader");
            Intent intent = new Intent();
            intent.setClassName(mSavingPkgName, mSavingServiceName);
            mContext.startService(intent);
            mContext.bindService(intent, serviceConnection,
                    Context.BIND_AUTO_CREATE);
        }
    }

    public void unbindOperaMaxLoader() {
        if (mSavingService != null) {
            Log.d(TAG, "unbindOperaMaxLoader");
            try {
                mSavingService.unregisterStateListener(stateListener);
            } catch (RemoteException e) {
                Log.d(TAG, "unregisterStateListener error");
            }
            mContext.unbindService(serviceConnection);
        }
    }
}
