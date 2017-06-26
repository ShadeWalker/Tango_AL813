package com.android.settings;

/**
 *HQ_xupeixin at 2015-09-21 modified about optimize searching feature in Settings application begin
 */
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.view.inputmethod.InputMethodManager;
import android.widget.SearchView;
import android.widget.SearchView.SearchAutoComplete;

import com.android.settings.AllSettings;
import com.android.settings.Settings;
import com.android.settings.Utils;
import com.android.settings.search.Index;
import com.android.settings.R;

import java.util.HashMap;

public class SearchSettingActivity extends Activity implements SearchView.OnQueryTextListener, SearchView.OnCloseListener {
    private static final String TAG = "SearchSettingActivity";
    private static Context mContext;
    private static SearchResultsAdapter mResultsAdapter;
    private static UpdateSearchResultsTask mUpdateSearchResultsTask;
    private static SuggestionsAdapter mSuggestionsAdapter;
    private static UpdateSuggestionsTask mUpdateSuggestionsTask;
    private static SearchView mSearchView;
    private static SearchAutoComplete searchTextView;

    private static ListView mResultsListView;
    private static ListView mSuggestionsListView;

    private static ViewGroup mLayoutSuggestions;
    private static ViewGroup mLayoutResults;

    private static String mQuery;
    private static TextView mEmpty;
    private static TextView mSearchHeaderText;
    private static boolean mShowResult = false;
    private static final String EMPTY_QUERY = "";
    private static char ELLIPSIS = '\u2026';

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "in onCreate");
        mContext = this;
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.search_panel);
        mLayoutSuggestions = (ViewGroup) this.findViewById(R.id.layout_suggestions);
        mLayoutResults = (ViewGroup) this.findViewById(R.id.layout_results);

        mEmpty = (TextView) this.findViewById(R.id.txt_search_empty);
        mSearchView = (SearchView) this.findViewById(R.id.search_view);

        mResultsAdapter = new SearchResultsAdapter(mContext);
        mSuggestionsAdapter = new SuggestionsAdapter(mContext);

        mResultsListView = (ListView) this.findViewById(R.id.list_results);
        mResultsListView.setAdapter(mResultsAdapter);
        mResultsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // We have a header, so we need to decrement the position by one
                position--;

                // Some Monkeys could create a case where they were probably clicking on the
                // List Header and thus the position passed was "0" and then by decrement was "-1"
                if (position < 0) {
                    return;
                }

                final Cursor cursor = mResultsAdapter.mCursor;
                cursor.moveToPosition(position);

                final String className = cursor.getString(Index.COLUMN_INDEX_CLASS_NAME);
                final String screenTitle = cursor.getString(Index.COLUMN_INDEX_SCREEN_TITLE);
                final String action = cursor.getString(Index.COLUMN_INDEX_INTENT_ACTION);
                final String key = cursor.getString(Index.COLUMN_INDEX_KEY);

                SettingsActivity.mNeedToRevertToInitialFragment = true;
                AllSettings.mNeedToRevertToInitialFragment = true;
                if (TextUtils.isEmpty(action)) {
                    Bundle args = new Bundle();
                    args.putString(SettingsActivity.EXTRA_FRAGMENT_ARG_KEY, key);

                    Utils.startWithFragment(mContext, className, args, null, 0, -1, screenTitle);
                } else {
                    boolean trdappAction = false;
                    if ("android.intent.action.NO_DISTURB".equals(action) ||
                        "android.intent.action.NOTIFICATION_CENTER".equals(action) ||
                        "android.intent.action.PROTECTED_APPS".equals(action) ||
                        "android.intent.action.STARTUP_MANAGER".equals(action) ||
                        "android.intent.action.PERMISSION_MANAGER".equals(action) ||
                        "android.intent.action.CLOUD_SERVICE".equals(action) ||
                        "android.intent.action.NET_APP_MANAGEMENT".equals(action) ||
                        "android.intent.action.DATA_USAGE".equals(action) ||
                        "android.intent.action.GESTURE_SETTINGS".equals(action) ||
                        "android.intent.action.BATTERY_SAVING".equals(action) ||
                        "android.settings.SYSTEM_UPDATE_SETTINGS".equals(action)) {
                        trdappAction = true;
                    }
                    Intent intent;
                    if (trdappAction) {
                        intent = new Intent();
                    } else {
                        intent = new Intent(action);
                    }
                    Log.d(TAG, "in onItemClick intent: " + intent);
                    final String targetPackage = cursor.getString(
                            Index.COLUMN_INDEX_INTENT_ACTION_TARGET_PACKAGE);
                    final String targetClass = cursor.getString(
                            Index.COLUMN_INDEX_INTENT_ACTION_TARGET_CLASS);
                    if (!TextUtils.isEmpty(targetPackage) && !TextUtils.isEmpty(targetClass)) {
                        final ComponentName component =
                            new ComponentName(targetPackage, targetClass);
                        intent.setComponent(component);
                    }
                    intent.putExtra(SettingsActivity.EXTRA_FRAGMENT_ARG_KEY, key);
                    mContext.startActivity(intent);
                }
                mShowResult = true;
                saveQueryToDatabase();
           }
        });

        View resultsHeaderView = LayoutInflater.from(mContext).inflate(R.layout.search_panel_results_header, mResultsListView, false);
        mSearchHeaderText = (TextView) resultsHeaderView.findViewById(R.id.search_results_id);
        mResultsListView.addHeaderView(resultsHeaderView, null, false);

        mSuggestionsListView = (ListView) this.findViewById(R.id.list_suggestions);
        mSuggestionsListView.setAdapter(mSuggestionsAdapter);
        mSuggestionsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // We have a header, so we need to decrement the position by one
                position--;
                // Some Monkeys could create a case where they were probably clicking on the
                // List Header and thus the position passed was "0" and then by decrement was "-1"
                if (position < 0) {
                    return;
                }
                final Cursor cursor = mSuggestionsAdapter.mCursor;
                cursor.moveToPosition(position);

                mQuery = cursor.getString(0);
                mSearchView.setQuery(mQuery, false);
            }
        });

        mSuggestionsListView.addHeaderView(LayoutInflater.from(mContext).inflate(R.layout.search_panel_suggestions_header, mSuggestionsListView, false),
            null, false);

        setSuggestionsVisibility(true);
        setResultsVisibility(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "in onResume mShowResult: " + mShowResult + " mSearchView: " + mSearchView);
        if (mSearchView != null) {
            mSearchView.setOnQueryTextListener(this);
            mSearchView.setOnCloseListener(this);

            searchTextView = (SearchAutoComplete) mSearchView.getSearchSrcTextView();
            if (searchTextView != null) {
                searchTextView.setHint(mContext.getResources().getString(R.string.search_settings_hint));
                searchTextView.setFocusable(true);
                searchTextView.requestFocus();
            }
        }
        //if no click the result item,so when resume to the activity,we will show the suggestion.
        if (!mShowResult)
            showSomeSuggestions();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //double clear
        clearSuggestions();
        clearResults();
        Log.d(TAG, "in onDestroy");
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Log.d(TAG, "in onBackPressed");
        finish();

        /// M: ALPS01763116 ,move to here from onStop() {@
        clearSuggestions();
        clearResults();
        /// @}
        mResultsListView = null;
        mResultsAdapter = null;
        mUpdateSearchResultsTask = null;

        mSuggestionsListView = null;
        mSuggestionsAdapter = null;
        mUpdateSuggestionsTask = null;
        mShowResult = false;
        mSearchView = null;
    }

    private static class SuggestionItem {
        public String query;

        public SuggestionItem(String query) {
            this.query = query;
        }
    }

    private static class SuggestionsAdapter extends BaseAdapter {

        private static final int COLUMN_SUGGESTION_QUERY = 0;
        private static final int COLUMN_SUGGESTION_TIMESTAMP = 1;

        private Context mContext;
        private Cursor mCursor;
        private LayoutInflater mInflater;
        private boolean mDataValid = false;

        public SuggestionsAdapter(Context context) {
            mContext = context;
            mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mDataValid = false;
        }

        public Cursor swapCursor(Cursor newCursor) {
            if (newCursor == mCursor) {
                return null;
            }
            Cursor oldCursor = mCursor;
            mCursor = newCursor;
            if (newCursor != null) {
                mDataValid = true;
                notifyDataSetChanged();
            } else {
                mDataValid = false;
                notifyDataSetInvalidated();
            }
            return oldCursor;
        }

        @Override
        public int getCount() {
            if (!mDataValid || mCursor == null || mCursor.isClosed()) return 0;
            return mCursor.getCount();
        }

        @Override
        public Object getItem(int position) {
            if (mDataValid && mCursor.moveToPosition(position)) {
                final String query = mCursor.getString(COLUMN_SUGGESTION_QUERY);

                return new SuggestionItem(query);
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (!mDataValid && convertView == null) {
                throw new IllegalStateException(
                        "this should only be called when the cursor is valid");
            }
            if (!mCursor.moveToPosition(position)) {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }

            View view;

            if (convertView == null) {
                view = mInflater.inflate(R.layout.search_suggestion_item, parent, false);
            } else {
                view = convertView;
            }

            TextView query = (TextView) view.findViewById(R.id.title);

            SuggestionItem item = (SuggestionItem) getItem(position);
            query.setText(item.query);

            return view;
        }
    }

    private static class SearchResult {
        public Context context;
        public String title;
        public String summaryOn;
        public String summaryOff;
        public String entries;
        public int iconResId;
        public String key;

        public SearchResult(Context context, String title, String summaryOn, String summaryOff,
                            String entries, int iconResId, String key) {
            this.context = context;
            this.title = title;
            this.summaryOn = summaryOn;
            this.summaryOff = summaryOff;
            this.entries = entries;
            this.iconResId = iconResId;
            this.key = key;
        }
    }

    private static class SearchResultsAdapter extends BaseAdapter {

        private Context mContext;
        private Cursor mCursor;
        private LayoutInflater mInflater;
        private boolean mDataValid;
        private HashMap<String, Context> mContextMap = new HashMap<String, Context>();

        private static final String PERCENT_RECLACE = "%s";
        private static final String DOLLAR_REPLACE = "$s";

        public SearchResultsAdapter(Context context) {
            mContext = context;
            mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mDataValid = false;
        }

        public Cursor swapCursor(Cursor newCursor) {
            if (newCursor == mCursor) {
                return null;
            }
            Cursor oldCursor = mCursor;
            mCursor = newCursor;
            if (newCursor != null) {
                mDataValid = true;
                notifyDataSetChanged();
            } else {
                mDataValid = false;
                notifyDataSetInvalidated();
            }
            return oldCursor;
        }

        @Override
        public int getCount() {
            if (!mDataValid || mCursor == null || mCursor.isClosed()) return 0;
            return mCursor.getCount();
        }

        @Override
        public Object getItem(int position) {
            if (mDataValid && mCursor.moveToPosition(position)) {
                final String title = mCursor.getString(Index.COLUMN_INDEX_TITLE);
                final String summaryOn = mCursor.getString(Index.COLUMN_INDEX_SUMMARY_ON);
                final String summaryOff = mCursor.getString(Index.COLUMN_INDEX_SUMMARY_OFF);
                final String entries = mCursor.getString(Index.COLUMN_INDEX_ENTRIES);
                final String iconResStr = mCursor.getString(Index.COLUMN_INDEX_ICON);
                final String className = mCursor.getString(
                        Index.COLUMN_INDEX_CLASS_NAME);
                final String packageName = mCursor.getString(
                        Index.COLUMN_INDEX_INTENT_ACTION_TARGET_PACKAGE);
                final String key = mCursor.getString(
                        Index.COLUMN_INDEX_KEY);

                Context packageContext;
                if (TextUtils.isEmpty(className) && !TextUtils.isEmpty(packageName)) {
                    packageContext = mContextMap.get(packageName);
                    if (packageContext == null) {
                        try {
                            packageContext = mContext.createPackageContext(packageName, 0);
                        } catch (PackageManager.NameNotFoundException e) {
                            Log.e(TAG, "Cannot create Context for package: " + packageName);
                            return null;
                        }
                        mContextMap.put(packageName, packageContext);
                    }
                } else {
                    packageContext = mContext;
                }

                final int iconResId = TextUtils.isEmpty(iconResStr) ?
                        R.drawable.empty_icon : Integer.parseInt(iconResStr);

                return new SearchResult(packageContext, title, summaryOn, summaryOff,
                        entries, iconResId, key);
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (!mDataValid && convertView == null) {
                throw new IllegalStateException(
                        "getActivity() should only be called when the cursor is valid");
            }
            if (!mCursor.moveToPosition(position)) {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }
            View view;
            TextView textTitle;
            ImageView imageView;

            if (convertView == null) {
                view = mInflater.inflate(R.layout.search_result_item, parent, false);
            } else {
                view = convertView;
            }

            textTitle = (TextView) view.findViewById(R.id.title);
            imageView = (ImageView) view.findViewById(R.id.icon);

            final SearchResult result = (SearchResult) getItem(position);
            textTitle.setText(result.title);

            if (result.iconResId != R.drawable.empty_icon) {
                final Context packageContext = result.context;
                final Drawable drawable;
                try {
                    drawable = packageContext.getDrawable(result.iconResId);
                    imageView.setImageDrawable(drawable);
                } catch (Resources.NotFoundException nfe) {
                    // Not much we can do except logging
                    Log.e(TAG, "Cannot load Drawable for " + result.title);
                }
            } else {
                imageView.setImageDrawable(null);
                imageView.setBackgroundResource(R.drawable.empty_icon);
            }
            return view;
        }
    }

     /**
     * A basic AsyncTask for updating the query results cursor
     */
    private static class UpdateSearchResultsTask extends AsyncTask<String, Void, Cursor> {
        @Override
        protected Cursor doInBackground(String... params) {
            return Index.getInstance(mContext).search(params[0]);
        }

        @Override
        protected void onPostExecute(Cursor cursor) {
            Log.d(TAG, "in UpdateSearchResultsTask onPostExecute cursor.count:" + cursor.getCount() + " isCancel: " + isCancelled());
            if (!isCancelled()) {
                setResultsCursor(cursor);
                setResultsVisibility(cursor.getCount() > 0);
                if(cursor.getCount() > 0) {
                   mShowResult = true;
                   mEmpty.setVisibility(View.GONE);
                   Log.d(TAG, "mEmpty will be gone");
                } else {
                   mEmpty.setVisibility(View.VISIBLE);
                   Log.d(TAG, "mEmpty will be visible");
                }
            } else if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * A basic AsyncTask for updating the suggestions cursor
     */
    private static class UpdateSuggestionsTask extends AsyncTask<String, Void, Cursor> {
        @Override
        protected Cursor doInBackground(String... params) {
            Log.d(TAG, "in UpdateSuggestionsTask doInBackground");
            return Index.getInstance(mContext).getSuggestions(params[0]);
        }

        @Override
        protected void onPostExecute(Cursor cursor) {
            Log.d(TAG, "in UpdateSuggestionsTask onPostExecute cursor: " + cursor.getCount());
            if (!isCancelled()) {
                setSuggestionsCursor(cursor);
                setSuggestionsVisibility(cursor.getCount() > 0);
                if(cursor.getCount() > 0) {
                   mEmpty.setVisibility( View.GONE);
                   Log.d(TAG, "mEmpty will be gone");
                } else {
                   mEmpty.setVisibility(View.VISIBLE);
                   Log.d(TAG, "mEmpty will be visible");
                }
            } else if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static void showSomeSuggestions() {
        setResultsVisibility(false);
        mQuery = EMPTY_QUERY;
        updateSuggestions();
    }

    private static void clearSuggestions() {
        if (mUpdateSuggestionsTask != null) {
            mUpdateSuggestionsTask.cancel(false);
            mUpdateSuggestionsTask = null;
        }
        setSuggestionsCursor(null);
    }

    private static void setSuggestionsCursor(Cursor cursor) {
        if (mSuggestionsAdapter == null) {
            return;
        }
        Cursor oldCursor = mSuggestionsAdapter.swapCursor(cursor);
        if (oldCursor != null) {
            oldCursor.close();
        }
    }

    private static void clearResults() {
        if (mUpdateSearchResultsTask != null) {
            mUpdateSearchResultsTask.cancel(false);
            mUpdateSearchResultsTask = null;
        }
        setResultsCursor(null);
    }

    private static void setResultsCursor(Cursor cursor) {
        if (mResultsAdapter == null) {
            return;
        }
        Cursor oldCursor = mResultsAdapter.swapCursor(cursor);
        if (oldCursor != null) {
            oldCursor.close();
        }
    }

    private static String getFilteredQueryString(CharSequence query) {
        if (query == null) {
            return null;
        }
        final StringBuilder filtered = new StringBuilder();
        for (int n = 0; n < query.length(); n++) {
            char c = query.charAt(n);
            if (!Character.isLetterOrDigit(c) && !Character.isSpaceChar(c)) {
                continue;
            }
            filtered.append(c);
        }
        return filtered.toString();
    }

    private static void clearAllTasks() {
        if (mUpdateSearchResultsTask != null) {
            mUpdateSearchResultsTask.cancel(false);
            mUpdateSearchResultsTask = null;
        }
        if (mUpdateSuggestionsTask != null) {
            mUpdateSuggestionsTask.cancel(false);
            mUpdateSuggestionsTask = null;
        }
    }

    private static void updateSuggestions() {
        clearAllTasks();
        if (mQuery == null) {
            setSuggestionsCursor(null);
        }
        mUpdateSuggestionsTask = new UpdateSuggestionsTask();
        mUpdateSuggestionsTask.execute(mQuery);

    }

    private static void updateSearchResults() {
        clearAllTasks();
        if (TextUtils.isEmpty(mQuery)) {
            setResultsVisibility(false);
            setResultsCursor(null);
        } else {
            mUpdateSearchResultsTask = new UpdateSearchResultsTask();
            mUpdateSearchResultsTask.execute(mQuery);
        }
    }

    public static  void setSearchView(SearchView searchView) {
        mSearchView = searchView;
    }

    private static  void setSuggestionsVisibility(boolean visible) {
        if (mLayoutSuggestions != null) {
            mLayoutSuggestions.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private static  void setResultsVisibility(boolean visible) {
        if (mLayoutResults != null) {
            mLayoutResults.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private static  void saveQueryToDatabase() {
        Index.getInstance(mContext).addSavedQuery(mQuery);
    }

    @Override
    public boolean onClose() {
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        mQuery = getFilteredQueryString(query);
        setSuggestionsVisibility(false);
        updateSearchResults();
        saveQueryToDatabase();
        return false;
    }

    @Override
    public boolean onQueryTextChange(String query) {
        final String newQuery = getFilteredQueryString(query);
        mQuery = newQuery;

        if (TextUtils.isEmpty(mQuery)) {
            setResultsVisibility(false);
            updateSuggestions();
        } else {
            setSuggestionsVisibility(false);
            updateSearchResults();
        }
        return true;
    }
}
/**
 *HQ_xupeixin at 2015-09-21 modified end
 */
