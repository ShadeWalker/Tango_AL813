package com.mediatek.contacts.activities;

import com.android.contacts.list.ContactTileListFragment;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;

import com.android.contacts.R;

/**
 * 收藏联系人aty
 * 
 * @author niubi tang
 * 
 */
public class StarContactBrowseActivity extends Activity {

	private ContactTileListFragment starFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.hq_starcontacts_browse_activity);
		getActionBar().setTitle(getResources().getString(R.string.contactsFavoritesLabel));
		starFragment = new ContactTileListFragment();
		FragmentManager fragmentManager = getFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager
				.beginTransaction();
		fragmentTransaction.add(R.id.star_contacts, starFragment);
		fragmentTransaction.commit();
		invalidateOptionsMenu();
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}

}
