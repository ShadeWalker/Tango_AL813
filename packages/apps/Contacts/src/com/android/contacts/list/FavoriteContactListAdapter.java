package com.android.contacts.list;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.common.R;

public class FavoriteContactListAdapter extends DefaultContactListAdapter {

	Context mContext;
	public FavoriteContactListAdapter(Context context) {
		super(context);
		mContext=context;
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void bindHeaderView(View view, int partitionIndex, Cursor cursor) {
		// TODO Auto-generated method stub
		super.bindHeaderView(view, partitionIndex, cursor);
		TextView labelTextView = (TextView) view.findViewById(R.id.label);
		TextView displayNameTextView = (TextView) view
				.findViewById(R.id.display_name);
		labelTextView.setText( mContext.getResources().getString(R.string.contactsFavoritesLabel) +": "+getStartedContactsNum());
		labelTextView.setGravity(Gravity.CENTER_VERTICAL);
		view.setBackgroundColor(Color.parseColor("#EAEAEA"));
        view.setPaddingRelative(view.getPaddingStart(), 3, 3,
                view.getPaddingBottom());
	}
	
	
	/**
	 * 获取收藏联系人的人数
	 * @return num
	 */
	private int getStartedContactsNum() {
		Cursor cur = mContext.getContentResolver().query(
				ContactsContract.Contacts.CONTENT_URI, null,
				ContactsContract.Contacts.STARRED + " =  1 ", null, null);
		int num = 0;
		if (cur != null) {
			num = cur.getCount();
			cur.close();

		}
		return num;
	}

}
