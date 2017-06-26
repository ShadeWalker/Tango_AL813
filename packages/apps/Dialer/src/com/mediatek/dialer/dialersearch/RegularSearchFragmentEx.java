/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.mediatek.dialer.dialersearch;

import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.PinnedHeaderListView;
// /added by guofeiyao
import android.util.Log;
import com.mediatek.dialer.dialersearch.SearchCallback;
// /end

public class RegularSearchFragmentEx extends SearchFragmentEx {

    private static final int SEARCH_DIRECTORY_RESULT_LIMIT = 5;

    public RegularSearchFragmentEx() {
        configureDirectorySearch();
    }

    public void configureDirectorySearch() {
        setDirectorySearchEnabled(false);
        setDirectoryResultLimit(SEARCH_DIRECTORY_RESULT_LIMIT);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Smart dialing does not support Directory Load, falls back to normal search instead.
        if (id == getDirectoryLoaderId()) {
            return super.onCreateLoader(id, args);
        } else {
            final RegularSearchListAdapterEx adapter = (RegularSearchListAdapterEx) getAdapter();
            DialerSearchCursorLoader loader = new DialerSearchCursorLoader(super.getContext(), usesCallableUri());
            adapter.configureLoader(loader);
            return loader;
        }
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);
        ((PinnedHeaderListView) getListView()).setScrollToSectionOnHeaderTouch(true);
    }

    protected ContactEntryListAdapter createListAdapter() {
        RegularSearchListAdapterEx adapter = new RegularSearchListAdapterEx(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(usesCallableUri());
        return adapter;
    }

    // /Created by guofeiyao
	// / Extend Loader operator that in ContactEntryListFragment.
	private static final String TAG = "RegularSearchFragmentEx";
	
    private SearchCallback searchCallback;

	public void setSearchCallback( SearchCallback s ) {
         searchCallback = s;
	}
    
	/*
	* For the weird situation that PeopleActivity re-create caused by 2 sim cards,
	* mayby we have better method to fix it,but I really dont kown now :(
	*/
	private boolean isSearchEmpty = false;

	public boolean getIsSearchEmpty() {
         return isSearchEmpty;
	}
	
	@Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        super.onLoadFinished( loader, data );
		Log.e("guofeiyao_" + TAG,"onLoadFinished");
		if ( null != searchCallback ) {
			 // / super method in ContactEntryListFragment.
             if ( null != getAdapter()) {
                  if ( 0 == getAdapter().getCount()
				  	   && null != getQueryString()
				  	   && getQueryString().length() > 0 ) {
				  	   isSearchEmpty = true;
                       searchCallback.maybeShowBtn();
				  } else {
				       isSearchEmpty = false;
                       searchCallback.maybeHideBtn();
				  }
			 } else {
                  Log.e("guofeiyao_" + TAG,"onLoadFinished_getAdapter() is null!!!");
			 }
		} else {
             Log.e("guofeiyao_" + TAG,"onLoadFinished_searchCallback is null!!!");
		}
		
	}
}
