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
import android.net.Uri;
import android.os.Bundle;
// /added by guofeiyao
import android.util.Log;
import android.view.View;
import android.view.MotionEvent;
import com.mediatek.dialer.dialersearch.SearchCallback;
// /end

import com.android.contacts.common.list.ContactEntryListAdapter;
import com.mediatek.dialer.util.LogUtils;

/**
 * Implements a fragment to load and display SmartDial search results.
 */
public class SmartDialSearchFragmentEx extends SearchFragmentEx {

    private static final String TAG = "SmartDialSearchFragmentEx";

    /**
     * Creates a SmartDialCursorLoader object to load query results.
     */
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Smart dialing does not support Directory Load, falls back to normal
        // search instead.
        if (id == getDirectoryLoaderId()) {
            return super.onCreateLoader(id, args);
        } else {
            LogUtils.d(TAG, "MTK-DialerSearch, Create loader");

            final SmartDialNumberListAdapterEx adapter = (SmartDialNumberListAdapterEx) getAdapter();
            DialerSearchCursorLoader loader = new DialerSearchCursorLoader(super.getContext(), usesCallableUri());
            adapter.configureLoader(loader);
            return loader;
        }
    }

    /**
     * Creates a SmartDialListAdapter to display and operate on search results.
     */
    @Override
    protected ContactEntryListAdapter createListAdapter() {
        SmartDialNumberListAdapterEx adapter = new SmartDialNumberListAdapterEx(getActivity());
        adapter.setUseCallableUri(super.usesCallableUri());
        adapter.setQuickContactEnabled(true);
        // Disable the direct call shortcut for the smart dial fragment, since the call button
        // will already be showing anyway.
        adapter.setShortcutEnabled(SmartDialNumberListAdapterEx.SHORTCUT_DIRECT_CALL, false);
        adapter.setShortcutEnabled(SmartDialNumberListAdapterEx.SHORTCUT_ADD_NUMBER_TO_CONTACTS, false);
        ///M: [VoLTE] Smart dial does not need volte call
        adapter.setShortcutEnabled(SmartDialNumberListAdapterEx.SHORTCUT_MAKE_VOLTE_CALL, false);
        return adapter;
    }

    /**
     * Gets the Phone Uri of an entry for calling.
     * @param position Location of the data of interest.
     * @return Phone Uri to establish a phone call.
     */
    @Override
    protected Uri getPhoneUri(int position) {
        final SmartDialNumberListAdapterEx adapter = (SmartDialNumberListAdapterEx) getAdapter();
        return adapter.getDataUri(position);
    }

	// /The following created by guofeiyao
	@Override
	public boolean onTouch(View view, MotionEvent event) {
        super.onTouch(view, event);
		Log.e("guofeiyao_" + TAG,"onTouch");
        if (hideDialpadCallback != null){
            hideDialpadCallback.hideDialpad();
		}
		
        return false;
    }

	private HideDialpadCallback hideDialpadCallback;

	public void setHideDialpadCallback(HideDialpadCallback h) {
            hideDialpadCallback = h;
	}

	public interface HideDialpadCallback {
         public void hideDialpad();
	}

    // / Extend Loader operator that in ContactEntryListFragment.
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
