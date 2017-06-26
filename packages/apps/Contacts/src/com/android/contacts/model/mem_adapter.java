package com.android.contacts.model;

import java.util.HashMap;
import java.util.List;
import java.util.zip.Inflater;

import com.android.contacts.ContactsApplication;
import com.android.contacts.R;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import java.util.Locale;

public class mem_adapter extends BaseAdapter implements ListAdapter {
	private List<HashMap<String, String>> accountMapList;
	private Context context;
	private TextView textView1;
	private TextView textView2;
//	private static SQLiteDatabase db;
	public mem_adapter(List<HashMap<String, String>> accountMap, Context context) {
		// TODO Auto-generated constructor stub

		this.accountMapList = accountMap;
		this.context = context;
//		this.db = ContactsApplication.getContactsDb();
	}

	@Override
	public int getCount() {
		// TODO Auto-generated method stub
		return accountMapList.size();
	}

	@Override
	public Object getItem(int position) {
		// TODO Auto-generated method stub
		return accountMapList.get(position);
	}

	@Override
	public long getItemId(int position) {
		// TODO Auto-generated method stub
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		// TODO Auto-generated method stub

		View view;
		if (convertView == null) {
			view = View
					.inflate(context, R.layout.mem_status_account_item, null);

		} else {
			view = convertView;
		}

		textView1 = (TextView) view.findViewById(R.id.account_name);
		textView2 = (TextView) view.findViewById(R.id.contacts_num);
		final String account_name = accountMapList.get(position).get(
				"account_name");
//		String account_type = accountMapList.get(position).get("account_type");
		String  account_name_display=accountMapList.get(position).get("account_name_display");
		textView1.setText(account_name_display);

//		to do 异步读取  数据库管理
//		new Thread() {
		int i=0;
//		public void run() {
		/*add by liruihong for HQ01459925 begin*/
//		if(db==null||!db.isOpen()){
//			db = ContactsApplication.getContactsDb();
//	    }
		/*add by liruihong for HQ01459925 end*/


		Cursor cursor;
		if (account_name.equals("Phone")) {
//			cursor = db
//				    .rawQuery(
//							"select distinct contact_id  from view_raw_contacts  where deleted =0  and (account_name=? or account_name is null) ",
//							new String[] { account_name });
			
		cursor = context.getContentResolver().query(
					Uri.parse("content://com.android.contacts/raw_contacts"),
					new String[] { "contact_id" },
					" deleted = 0  and ( account_name =  ?  or account_name is null )", new String[]{account_name}, "contact_id");
		} else {
//			cursor = db
//				    .rawQuery(
//							"select distinct contact_id  from view_raw_contacts  where account_name=? and deleted =0",
//							new String[] { account_name });
			
			cursor = context.getContentResolver().query(
					Uri.parse("content://com.android.contacts/raw_contacts"),
					new String[] { "contact_id" },
					" deleted = 0  and   account_name =  ? ", new String[]{account_name}, "contact_id");
			
		}
//		if (cursor != null) {
//			i = cursor.getCount();
//			cursor.close();
//		} else {
//			i = 0;
//		}
		
		if (cursor != null) {
			int lastcontact_id=-1;//代替distinct
			while(cursor.moveToNext()){
				int index = cursor.getColumnIndex("contact_id");
				if( lastcontact_id !=cursor.getInt(index)){
					i++;
				}
				lastcontact_id = cursor.getInt(index);
			}
			cursor.close();
		} else {
			i = 0;
		}
        String locale = Locale.getDefault().getLanguage();
        if(locale.equals("ru")){
		//if(getResources().getConfiguration().locale.getCountry().equals("RU")){
  			textView2.setText(context.getResources().getString(R.string.howManyContacts)+" "+i);
		}
		else {
			textView2.setText(i+" "+context.getResources().getString(R.string.howManyContacts));
		}
		return view;

	}

}
