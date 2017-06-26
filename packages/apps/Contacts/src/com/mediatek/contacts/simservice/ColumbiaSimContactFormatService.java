package com.mediatek.contacts.simservice;

import java.util.ArrayList;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Contacts.People;
import android.util.Log;

import com.mediatek.contacts.util.LogUtils;

/**
 * 哥伦比亚sim卡，漫游的时候需要对号码进行格式化，并且将变化的信息写入xml文件中进行备份。在回到哥伦比亚，ManYouReceiver类中，进行回复操作
 * 。
 * 
 * A情况 1. 哥伦比亚卡漫游到第4列表格所列的国家（与哥伦比亚存在漫游协议）的时候，对+57的处理如下： a)
 * 对SIM卡的联系人号码是10位，如果是3打头的（移动号码），做 +57处理，如果是0打头的（一般是固定号码），将头两位替换成+57，
 * b)对sim卡的联系人号码不是10位的，不做任何处理，保留原样。
 * 
 * 
 * B情况 2. 哥伦比亚卡漫游到不在上面表格里国家的时候，对+57的处理如下： a) 对SIM卡的联系人号码是10位， 如果是3打头的（移动号码），做
 * +57处理，如果是0打头的（一般是固定号码），将头两位03替换成+57。 b) 对SIM卡的联系人号码是 *123，*611号码做号码转换成
 * +573103333333处理。（3103333333为哥伦比亚国家服务号码） c) 除上面两点之外的号码一律保持不变。
 * 
 * 
 * @author niubi tang
 * 
 */
public class ColumbiaSimContactFormatService extends IntentService {

	public ColumbiaSimContactFormatService() {
		super("ColumbiaSimContactFormatService");
	}

	public ColumbiaSimContactFormatService(String name) {
		super(name);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void onHandleIntent(Intent intent) {

		// 获取所有sim卡联系人的号码。。。
		ArrayList<SimContactEnrty> simContactsNumLArrayList = SimQuery(getApplicationContext());
		String kind = intent.getExtras().getString("ServiceKind");

		// 还原的流程
		if (kind.equals("Z")) {
			Log.i("tang", "还原流程");
			for (SimContactEnrty simcontact : simContactsNumLArrayList) {
				String phoneNum = simcontact.getPhoneNum();
				String name = simcontact.getName();
				String id = simcontact.getId();
				if (phoneNum != null) {
					String newNum = restoreSimContacts(phoneNum, name);
					if (newNum == null) {
						Log.w("tang",
								"the  newNUm is null,we will deal with next num");
						continue;
					}
					Log.i("tang", "the restorephoneNum is" + phoneNum);
					Log.i("tang", "the restorephoneName is" + name);
					updateContactDb(getApplicationContext(), name, phoneNum,
							newNum);
				}
			}
		} else {
			for (SimContactEnrty simcontact : simContactsNumLArrayList) {
				String phoneNum = simcontact.getPhoneNum();
				String name = simcontact.getName();
				String id = simcontact.getId();

				if ((phoneNum == null || phoneNum.length() != 10)
						&& !phoneNum.equals("*123") && !phoneNum.equals("*611")) {// 对sim卡的联系人号码不是10位的，不做任何处理，保留原样。
					Log.d("tang",
							">>>>>>" + name + ">>>phone number lengh不等于10，跳过, "
									+ phoneNum.length());
					continue;
				}

				Log.d("tang", "现在获取到的sim卡联系人number是 >>>>>>" + phoneNum);
				String newNum = dealWithSimContacts(phoneNum, kind);
				updateContactDb(getApplicationContext(), name, phoneNum, newNum);
			}
		}
		startSimService(getApplicationContext(),
				intent.getIntExtra("subID", -1),
				SIMServiceUtils.SERVICE_WORK_IMPORT);// 刷新

	}

	private String restoreSimContacts(String phoneNum, String name) {
		Log.i("tang", "the phoneNum is" + phoneNum);
		Log.i("tang", "the name is" + name);

		if (phoneNum.equals("+573103333333")) {
			if (name.toLowerCase().contains("mail")) {
				return "*123";
			} else {
				return "*611";
			}
		} else if (phoneNum.startsWith("+57") && phoneNum.length() == 13) {// 原来是+57来的，直接去掉
			return phoneNum.substring(3);
		} else if (phoneNum.startsWith("+57") && phoneNum.length() == 11) {// 原来是03开头替换为+57的
			return phoneNum.replace("+57", "03");
		}
		return phoneNum;// 不需要还原的号码
	}

	private static Uri uri = Uri.parse("content://icc/adn");
	private static ContentValues ContactValues = new ContentValues();

	private void startSimService(Context context, int subId, int workType) {
		Intent intent = null;
		intent = new Intent(context, SIMProcessorService.class);
		intent.putExtra(SIMServiceUtils.SERVICE_SUBSCRIPTION_KEY, subId);
		intent.putExtra(SIMServiceUtils.SERVICE_WORK_TYPE, workType);
		LogUtils.d("tang", "[startSimService]subId:" + subId + "|workType:"
				+ workType);
		context.startService(intent);
	}

	/**
	 * 更新数据库
	 * 
	 * @param context
	 * @param id
	 * @param NewNum
	 */
	public void updateContactDb(Context context, String name,
			String Oringinnumber, String NewNum) {
		if (NewNum == null) {
			Log.d("tang", "update>>>>>>  the new Num is  null");
			return;
		}
		// ContactValues.put(People.NUMBER, NewNum);
		Log.d("tang", "update>>>>>>  the new Num is" + NewNum);
		Log.d("tang", "update>>>>>>  the contact name is" + name);
		// Log.d("tang", "the uri is"+uri);

		// 更新sim卡联系人，需要原来的名字和号码
		ContactValues.put("tag", name);
		ContactValues.put("number", Oringinnumber);
		ContactValues.put("newTag", name);
		ContactValues.put("newNumber", NewNum);
		Log.d("tang",
				"update>>>>>>  the contactValues is" + ContactValues.toString());
		context.getContentResolver().update(uri, ContactValues, null, null);
	}

	/**
	 * 查询所有sim卡中联系人
	 * 
	 * @param context
	 * @return
	 */
	public ArrayList<SimContactEnrty> SimQuery(Context context) {

		// ArrayList<HashMap<String, String>> simContactsNumLArrayList = new
		// ArrayList<>();
		Cursor cursor = context.getContentResolver().query(uri,
				new String[] { People._ID, People.NAME, People.NUMBER },
				"deleted=0", null, null);
		Log.d("tang", ">>>>>>" + cursor.getCount());

		ArrayList<SimContactEnrty> simContactList = new ArrayList<>();
		while (cursor.moveToNext()) {
			SimContactEnrty simContact = new SimContactEnrty();
			String id = cursor.getString(cursor.getColumnIndex(People._ID));
			String name = cursor.getString(cursor.getColumnIndex(People.NAME));
			ArrayList<String> phoneNumList = new ArrayList<String>();
			@SuppressWarnings("deprecation")
			String phoneNum = cursor.getString(cursor
					.getColumnIndex(People.NUMBER));// 两个号码的情况？

			simContact.setId(id);
			simContact.setName(name);
			simContact.setPhoneNum(phoneNum);
			Log.d("tang", ">>>>>查询sim卡数据---start");
			Log.d("tang", ">>>>>>" + "id, " + simContact.getId());
			Log.d("tang", ">>>>>>" + "name, " + simContact.getName());
			Log.d("tang", ">>>>>>" + "phone number, " + phoneNum);
			Log.d("tang", ">>>>>查询sim卡数据---end");
			simContactList.add(simContact);
		}
		if (cursor != null) {
			cursor.close();
		}
		return simContactList;

	}

	public class SimContactEnrty {

		String name;
		String id;
		String phoneNum;
		ArrayList<String> phoneNumList;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public void addPhoneNumList(String phoneNum) {
			this.phoneNumList.add(phoneNum);
		}

		public ArrayList<String> getPhoneNumList() {
			return phoneNumList;
		}

		public void setPhoneNumList(ArrayList<String> phoneNumList) {
			this.phoneNumList = phoneNumList;
		}

		public String getPhoneNum() {
			return phoneNum;
		}

		public void setPhoneNum(String phoneNum) {
			this.phoneNum = phoneNum;
		}

		public SimContactEnrty() {
			// TODO Auto-generated constructor stub
		}

	}

	/**
	 * 返回处理过的号码
	 * 
	 * @param number
	 * @param kind
	 * @return
	 */
	private String dealWithSimContacts(String number, String kind) {
		String newNum = number;
		if (kind.equals("A")) {
			Log.d("tang", ">>>>>>" + "services A");
			// 如果是3打头的（移动号码），做 +57处理，如果是0打头的（一般是固定号码），将头两位替换成+57，
			if (number.startsWith("3")) {
				Log.d("tang", ">>>>>>" + "start with 3 ");
				newNum = "+57" + number;
			} else if (number.startsWith("0")) {
				Log.d("tang", ">>>>>>" + "start with 0 ");
				newNum = "+57" + number.substring(2);
			}

			return newNum;
		} else {
			Log.d("tang", ">>>>>>" + "services B");
			// * a) 对SIM卡的联系人号码是10位， 如果是3打头的（移动号码），做
			// * +57处理，如果是0打头的（一般是固定号码），将头两位03替换成+57。
			if (number.startsWith("3")) {
				Log.d("tang", ">>>>>>" + "start with 3 ");
				newNum = "+57" + number;
			} else if (number.startsWith("03")) {
				Log.d("tang", ">>>>>>" + "start with 03 ");
				newNum = "+57" + number.substring(2);
			} else if (number.equals("*123") || number.equals("*611")) {
				Log.d("tang", ">>>>>>" + "start with *123 ");
				newNum = "+573103333333";
			}
			// * b) 对SIM卡的联系人号码是 *123，*611号码做号码转换成
			// * +573103333333处理。（3103333333为哥伦比亚国家服务号码）

			return newNum;

		}
	}

}
