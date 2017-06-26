/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.contacts.list;

import com.mediatek.contacts.util.ContactsListUtils;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.widget.WaitCursorView;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.QuickContact;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView.FindListener;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactTileLoaderFactory;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.ContactTileAdapter;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.common.list.ContactTileAdapter.DisplayType;
import com.android.contacts.common.list.ContactTileView;
import com.android.contacts.common.util.ContactListViewUtils;
import com.android.contacts.common.util.SchedulingUtils;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.preference.ContactsPreferences.ChangeListener;
import com.android.contacts.quickcontact.QuickContactActivity;

/**
 * Fragment containing a list of starred contacts followed by a list of frequently contacted.
 *
 * TODO: Make this an abstract class so that the favorites, frequent, and group list functionality
 * can be separated out. This will make it easier to customize any of those lists if necessary
 * (i.e. adding header views to the ListViews in the fragment). This work was started
 * by creating {@link ContactTileFrequentFragment}.
 */
public class ContactTileListFragment extends Fragment implements OnItemClickListener, OnScrollListener {
    private static final String TAG = ContactTileListFragment.class.getSimpleName();

    public interface Listener {
        void onContactSelected(Uri contactUri, Rect targetRect);
        void onCallNumberDirectly(String phoneNumber);
    }

    private Listener mListener;
    private FavoriteContactListAdapter mAdapter;
    private DisplayType mDisplayType;
    private TextView mEmptyView;
    private ListView mListView;
    
    public  int FavoritesNum=0;

    private boolean mOptionsMenuHasFrequents;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        Resources res = getResources();
        int columnCount = res.getInteger(R.integer.contact_tile_column_count_in_favorites);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
    	mDisplayType=DisplayType.STARRED_ONLY;
        mAdapter = new FavoriteContactListAdapter(getActivity());
        mAdapter.setDisplayPhotos(true);
        mAdapter.setPhotoPosition(ContactListItemView
				.getDefaultPhotoPosition(false));
        mAdapter.setSearchMode(false);
        mAdapter.setIncludeProfile(false);
        mAdapter.setSortOrder(mSortOrder);
        mPhotoManager=ContactPhotoManager.getInstance(getActivity());
        return inflateAndSetupView(inflater, container, savedInstanceState,
                R.layout.contact_tile_list);
    }

    protected View inflateAndSetupView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState, int layoutResourceId) {
        View listLayout = inflater.inflate(layoutResourceId, container, false);

        mEmptyView = (TextView) listLayout.findViewById(R.id.contact_tile_list_empty);
        mListView = (ListView) listLayout.findViewById(R.id.contact_tile_list);
        mListView.setDivider(getResources().getDrawable(R.drawable.linegray));
        mListView.setItemsCanFocus(true);
        mListView.setHeaderDividersEnabled(false);
        mListView.setAdapter(mAdapter);mListView.setOnItemClickListener(this);
        mListView.setOnScrollListener(this);
        mAdapter.setPhotoLoader(mPhotoManager);

        ContactListViewUtils.applyCardPaddingToView(getResources(), mListView, listLayout);

        /** M: Bug Fix For CR ALPS00115673 Descriptions: add wait cursor */
        mWaitCursorView = ContactsListUtils.initLoadingView(getActivity(), listLayout,
                mLoadingContainer, mLoadingContact, mProgress);
        return listLayout;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (getActivity() != null && getView() != null && !hidden) {
            // If the padding was last applied when in a hidden state, it may have been applied
            // incorrectly. Therefore we need to reapply it.
            ContactListViewUtils.applyCardPaddingToView(getResources(), mListView, getView());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        /// M:fix ALPS01013843
        mContactsPreferences.registerChangeListener(mPreferencesChangeListener);

        // initialize the loader for this display type and destroy all others
        final DisplayType[] loaderTypes = mDisplayType.values();
        for (int i = 0; i < loaderTypes.length; i++) {
            if (loaderTypes[i] == mDisplayType) {
                getLoaderManager().initLoader(mDisplayType.ordinal(), null,
                        mContactTileLoaderListener);
            } else {
                getLoaderManager().destroyLoader(loaderTypes[i].ordinal());
            }
        }
    }

    /**
     * Returns whether there are any frequents with the side effect of setting the
     * internal flag mOptionsMenuHasFrequents to the value.  This should be called externally
     * by the activity that is about to prepare the options menu with the clear frequents
     * menu item.
     */
    public boolean hasFrequents() {
        mOptionsMenuHasFrequents = internalHasFrequents();
        return mOptionsMenuHasFrequents;
    }

    /**
     * Returns whether there are any frequents.
     */
    private boolean internalHasFrequents() {
//        return mAdapter.getNumFrequents() > 0;
    	return false;
    }
    
    public void setColumnCount(int columnCount) {
//        mAdapter.setColumnCount(1);
    }

    public void setDisplayType(DisplayType displayType) {
        mDisplayType = displayType;
//        mAdapter.setDisplayType(DisplayType.STARRED_ONLY);
    }

    public void enableQuickContact(boolean enableQuickContact) {
//        mAdapter.enableQuickContact(enableQuickContact);
    }

    private final LoaderManager.LoaderCallbacks<Cursor> mContactTileLoaderListener =
            new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            Log.d(TAG, "onCreateLoader ContactTileListFragment");
            /** M: Bug Fix For CALPS00115673 Descriptions: add wait cursor. */
            mWaitCursorView.startWaitCursor();
            switch (mDisplayType) {
              case STARRED_ONLY:
                  return ContactTileLoaderFactory.createStarredLoader(getActivity());
              case STREQUENT:
                  return ContactTileLoaderFactory.createStrequentLoader(getActivity());
              case FREQUENT_ONLY:
                  return ContactTileLoaderFactory.createFrequentLoader(getActivity());
              default:
                  throw new IllegalStateException(
                      "Unrecognized DisplayType " + mDisplayType);
            }
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if (data == null || data.isClosed()) {
                Log.e(TAG, "Failed to load contacts");
                return;
            }
            /** M: add for ALPS01464088. check whether the fragment still in Activity @{ */
            if (!isAdded() || data == null) {
                Log.d(TAG, "onLoadFinished(),This Fragment is not add to the Activity now.data:" + data);
                return;
            }
            /** @} */
            Log.d(TAG, "onloadfinished");
            /** M: Bug Fix For ALPS00115673 */
            mWaitCursorView.stopWaitCursor();

            mAdapter.changeCursor(data);
            mEmptyView.setText(getEmptyStateText());
            mListView.setEmptyView(mEmptyView);

            FavoritesNum=data.getCount();
            
            // invalidate the menu options if needed
            invalidateOptionsMenuIfNeeded();
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    private boolean isOptionsMenuChanged() {
        return mOptionsMenuHasFrequents != internalHasFrequents();
    }

    private void invalidateOptionsMenuIfNeeded() {
        if (isOptionsMenuChanged()) {
            getActivity().invalidateOptionsMenu();
        }
    }

    private String getEmptyStateText() {
        String emptyText;
        switch (mDisplayType) {
            case STREQUENT:
            case STARRED_ONLY:
                emptyText = getString(R.string.listTotalAllContactsZeroStarred);
                break;
            case FREQUENT_ONLY:
            case GROUP_MEMBERS:
                emptyText = getString(R.string.noContacts);
                break;
            default:
                throw new IllegalArgumentException("Unrecognized DisplayType " + mDisplayType);
        }
        return emptyText;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    private ContactTileView.Listener mAdapterListener =
            new ContactTileView.Listener() {
        @Override
        public void onContactSelected(Uri contactUri, Rect targetRect) {
            if (mListener != null) {
                mListener.onContactSelected(contactUri, targetRect);
            }
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber) {
            if (mListener != null) {
                mListener.onCallNumberDirectly(phoneNumber);
            }
        }

        @Override
        public int getApproximateTileWidth() {
            return getView().getWidth();
        }
    };

    /** M: Bug Fix For ALPS00115673 Descriptions: add wait cursor. @{ */
    private View mLoadingContainer;
    private TextView mLoadingContact;
    private ProgressBar mProgress;
    private WaitCursorView mWaitCursorView;
    /** @} */

    /** M: fix ALPS01013843. refresh the list when change the sort preference. @{ */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContactsPreferences = new ContactsPreferences(getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (loadContactsPreferences()) {
            final DisplayType[] loaderTypes = mDisplayType.values();
            for (int i = 0; i < loaderTypes.length; i++) {
                if (loaderTypes[i] == mDisplayType) {
                    getLoaderManager().restartLoader(mDisplayType.ordinal(), null, mContactTileLoaderListener);
                }
            }
        }
    }

    @Override
    public void onStop() {
        if (mContactsPreferences != null) {
            LogUtils.d(TAG, "[onDestroy] unregisterChangeListener...");
            mContactsPreferences.unregisterChangeListener();
        }
        super.onStop();
    }

    /**
     * M: modify
     * @return true if the sort preference has changed,false else.
     */
    private boolean loadContactsPreferences() {
        if (mContactsPreferences == null || mPreferencesChangeListener == null) {
            return false;
        }
        boolean changed = false;
        final int currentDisplayOrder = mContactsPreferences.getDisplayOrder();
        if (mDisplayNameOrder != mContactsPreferences.getDisplayOrder()) {
            mDisplayNameOrder = mContactsPreferences.getDisplayOrder();
            changed = true;
        }
        if (mSortOrder != mContactsPreferences.getSortOrder()) {
            mSortOrder = mContactsPreferences.getSortOrder();
            changed = true;
        }

        return changed;
    }

    private ChangeListener mPreferencesChangeListener = new ChangeListener() {

        @Override
        public void onChange() {
            LogUtils.d(TAG, "[onChange] ContactsPreferences has changed,reload contacts...");
            if (loadContactsPreferences()) {
                final DisplayType[] loaderTypes = mDisplayType.values();
                for (int i = 0; i < loaderTypes.length; i++) {
                    if (loaderTypes[i] == mDisplayType) {
                        getLoaderManager().restartLoader(mDisplayType.ordinal(), null, mContactTileLoaderListener);
                    }
                }
            }
        }
    };

    private ContactsPreferences mContactsPreferences = null;
    private int mDisplayNameOrder;
    private int mSortOrder;
    private ContactPhotoManager mPhotoManager;
    /** @} */

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		// TODO Auto-generated method stub  getAdapter().getContactUri(position);
		final Uri uri = mAdapter.getContactUri(position);
		if (uri == null) {
			return;
		}
		Intent intent = QuickContact.composeQuickContactsIntent(
				getActivity(), (Rect) null, uri,
				QuickContactActivity.MODE_FULLY_EXPANDED, null);
		startActivity(intent);
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		// TODO Auto-generated method stub
        if (scrollState == OnScrollListener.SCROLL_STATE_FLING) {
            mPhotoManager.pause();
        } else {
            mPhotoManager.resume();
        }
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {
		// TODO Auto-generated method stub
		
	}
}
