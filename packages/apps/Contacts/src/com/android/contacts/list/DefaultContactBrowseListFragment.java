/*
 * Copyright (C) 2014 MediaTek Inc.
 * Modification based on code covered by the mentioned copyright
 * and/or permission notice(s).
 */
/*
 * Copyright (C) 2010 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.ContactsContract.Contacts;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.common.list.ContactListAdapter;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListFilterController;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.common.list.ProfileAndContactsLoader;
import com.android.contacts.common.util.AccountFilterUtil;
import com.android.contacts.editor.ContactEditorFragment;
import com.android.contacts.common.widget.SideBar;
import com.android.contacts.common.widget.SideBar.OnTouchingLetterChangedListener;
import com.mediatek.contacts.util.ContactsListUtils;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.vcs.VcsController;
import com.mediatek.contacts.widget.WaitCursorView;

import android.app.Activity;

import com.android.contacts.activities.PeopleActivity;

import android.widget.AdapterView;
import android.app.AlertDialog;
import android.content.DialogInterface;

import com.android.contacts.common.list.ShortcutIntentBuilder;
import com.android.contacts.common.list.ShortcutIntentBuilder.OnShortcutIntentCreatedListener;
import com.android.contacts.ContactSaveService;
import com.android.contacts.common.vcard.VCardCommonArguments;

import android.os.Build;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.RemoteException;

import com.huawei.harassmentinterception.service.IHarassmentInterceptionService;
import com.huawei.harassmentinterception.service.IHarassmentInterceptionService.Stub;

/**
 * Fragment containing a contact list used for browsing (as compared to picking
 * a contact with one of the PICK intents).
 */
public class DefaultContactBrowseListFragment extends ContactBrowseListFragment {
	private static final String TAG = DefaultContactBrowseListFragment.class
			.getSimpleName();

	private static final int REQUEST_CODE_ACCOUNT_FILTER = 1;

	private View mSearchHeaderView;
	private View mAccountFilterHeader;
	private FrameLayout mProfileHeaderContainer;
	private View mProfileHeader;
	private Button mProfileMessage;
	private TextView mProfileTitle;
	private View mSearchProgress;
	private TextView mSearchProgressText;
	private View DefaultContactBrowseListFragmentView;
	private TextView contactsToDisplay_tv;
	private final Map mArLangNummap = new HashMap() {{
        put("0", "۰");
        put("1", "١");
        put("2", "٢");
        put("3", "٣");
        put("4", "٤");
        put("5", "٥");
        put("6", "٦");
        put("7", "٧");
        put("8", "٨");
        put("9", "٩");
    }};
    private final Map mFaLangNummap = new HashMap() {{
        put("0", "۰");
        put("1", "١");
        put("2", "٢");
        put("3", "٣");
        put("4", "۴");
        put("5", "۵");
        put("6", "۶");
        put("7", "٧");
        put("8", "٨");
        put("9", "٩");
    }};
	private class FilterHeaderClickListener implements OnClickListener {
		@Override
		public void onClick(View view) {
			AccountFilterUtil.startAccountFilterActivityForResult(
					DefaultContactBrowseListFragment.this,
					REQUEST_CODE_ACCOUNT_FILTER, getFilter());
		}
	}

	private OnClickListener mFilterHeaderClickListener = new FilterHeaderClickListener();
	
    // /Annotated by guofeiyao
//	private RelativeLayout MyDock;
    // /End
    
	private LinearLayout MyDock_layout;

	private EditText mSearchView;

	private static SideBar sideBar;

	private TextView dialog;

	private View contactlistcontent_headview;
	private RelativeLayout MyGroup;
//	private RelativeLayout LifeService;

	private static final String ACTION_INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";
	private static final String HARASSMENT_INTERCEPTION_SERVICE = "com.huawei.harassmentinterception.service.HarassmentInterceptionService";        
	private IBinder harassmentIntercepterService;
	private IHarassmentInterceptionService hisStub;
	//HQ_fengsimin add for HQ01811908
    private boolean showdialog=false;
	public View getContactlistcontent_headview() {
		return contactlistcontent_headview;
	}

    //HQ_wuruijun add for HQ01435294 start
    private PackageManager mPm;
    private final static String LAUNCHER_PACKAGE_NAME = "com.huawei.android.launcher";
    private final static String LAUNCHER_CLASS_SIMPLEUI = "com.huawei.android.launcher.simpleui.SimpleUILauncher";
    static ComponentName mSimpleui = new ComponentName(
            LAUNCHER_PACKAGE_NAME, LAUNCHER_CLASS_SIMPLEUI);
    //HQ_wuruijun add end

	public DefaultContactBrowseListFragment() {
		setPhotoLoaderEnabled(true);
		// Don't use a QuickContactBadge. Just use a regular ImageView. Using a
		// QuickContactBadge
		// inside the ListView prevents us from using MODE_FULLY_EXPANDED and
		// messes up ripples.
		setQuickContactEnabled(false);
		setSectionHeaderDisplayEnabled(true);
		setVisibleScrollbarEnabled(true);
	}

	@Override
	public CursorLoader createCursorLoader(Context context) {
		/** M: Bug Fix for ALPS00115673 Descriptions: add wait cursor. @{ */
		Log.d(TAG, "createCursorLoader");
		if (mLoadingContainer != null) {
			mLoadingContainer.setVisibility(View.GONE);
		}
		// qinglei comment out this for L migration bug: people list will stop
		// on wait dialog when enter it.
		// mWaitCursorView.startWaitCursor();
		/** @} */

		return new ProfileAndContactsLoader(context);
	}

	@Override
	protected void onItemClick(int position, long id) {
		LogUtils.d(TAG, "[onItemClick][launch]start");
		final Uri uri = getAdapter().getContactUri(position);
		if (uri == null) {
			return;
		}
		viewContact(uri);
		LogUtils.d(TAG, "[onItemClick][launch]end");
	}

	//caohaolin added for long click contact item begin
	@Override
	public void onCreate(Bundle savedState) {
		super.onCreate(savedState);
		harassmentIntercepterService= ServiceManager.getService(HARASSMENT_INTERCEPTION_SERVICE);
		hisStub= IHarassmentInterceptionService.Stub.asInterface(harassmentIntercepterService);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onActivityCreated(savedInstanceState);
	}
	@Override
	protected boolean onItemLongClick(int position, long id) {
		LogUtils.d(TAG, "position = " + position);
		buildContextMenu(position);
		return true;
	}

	//根据uri得到联系人号码（默认第一个）或者Email地址
	private String getContactNumberOrMail(Uri uri,String type) {
		if (uri == null) {
			return "";
		}
		int mimetype_id = 5;
		if ("vnd.android.cursor.item/email_v2".equals(type)) {
			mimetype_id = 1;
		}
                String info = "";
                Cursor cursor = null;
                Cursor dataCursor = null;
                Cursor dataCursor2 = null;
                try {
			cursor = getContext().getContentResolver().query(uri, null, null, null, null);
			cursor.moveToFirst();
			String contactId = cursor.getString(cursor.getColumnIndexOrThrow(Contacts._ID));

		        //放到子线程里运行
			uri = Uri.parse("content://com.android.contacts/contacts/" + contactId + "/data");
			dataCursor = getContext().getContentResolver().query(uri, new String[] { "data1", "mimetype", "is_primary"}, 
                                        "is_primary = 1 AND mimetype_id =" + mimetype_id, null, null);
			dataCursor2 = getContext().getContentResolver().query(uri, new String[] { "data1", "mimetype"}, 
                                        null, null, null);
			LogUtils.d(TAG, "Count ----------------" + dataCursor.getCount());
			if(dataCursor.getCount() > 0) { //说明号码或者邮件地址有默认值
				while (dataCursor.moveToNext()) {
					String data = dataCursor.getString(0);
					String compareType = dataCursor.getString(1);
					String isPrimary = dataCursor.getString(2);
					if (Build.TYPE.equals("eng")) {
						LogUtils.d(TAG, "data1 = " + data);
						LogUtils.d(TAG, "type1 = " + type);
						LogUtils.d(TAG, "compareType1 = " + compareType);
						LogUtils.d(TAG, "isPrimary1 = " + isPrimary);
					}
					if (type.equals(compareType) && isPrimary.equals("1")) {
				    		info = data;
		                    		break;
					}
				}
			} else {
				while (dataCursor2.moveToNext()) {
					String data = dataCursor2.getString(0);
					String compareType = dataCursor2.getString(1);
					if (Build.TYPE.equals("eng")) {
						LogUtils.d(TAG, "data2 = " + data);
						LogUtils.d(TAG, "type2 = " + type);
						LogUtils.d(TAG, "compareType2 = " + compareType);
					}
					if (type.equals(compareType)) {
				    		info = data;
		                    		break;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (cursor != null) {
				cursor.close();
			}
			if (dataCursor != null) {
				dataCursor.close();
			}
			if (dataCursor2 != null) {
				dataCursor2.close();
			}
			if (Build.TYPE.equals("eng")) {
				LogUtils.d(TAG, "info = " + info);
			}
			return info;
		}
	}

	//呼叫
	private void callNumber(Uri uri) {
		if (uri == null) {
			return;
		}
		String number = getContactNumberOrMail(uri, "vnd.android.cursor.item/phone_v2");
		Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
		startActivity(intent);
	}

	//发送信息
	private void sendMessage(Uri uri) {
		if (uri == null) {
			return;
		}
		String number = getContactNumberOrMail(uri, "vnd.android.cursor.item/phone_v2");
		Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number));
		startActivity(intent);
	}

	//发送邮件
	private void sendEMail(Uri uri) {
		if (uri == null) {
			return;
		}
		String email = getContactNumberOrMail(uri, "vnd.android.cursor.item/email_v2");
		Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email));
		startActivity(intent);
	}

	
	//编辑
	private void editContact(Uri uri) {
		if (uri == null) {
			return;
		}
		Intent intent = new Intent(Intent.ACTION_EDIT, uri);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                startActivity(intent);
	}

	//删除
	public void deleteContact(Uri uri) {
		if (uri == null) {
			return;
		}
		super.deleteContact(uri);
	}

	//分享名片
	private void shareContact(Uri uri) {
		if (uri == null) {
			return;
		}
		String lookupKey = "";
		Cursor cursor = null;
                Cursor dataCursor = null;
		try {
			cursor = getContext().getContentResolver().query(uri, null, null, null, null);
			cursor.moveToFirst();
			String contactId = cursor.getString(cursor.getColumnIndexOrThrow(Contacts._ID));

		        //放到子线程里运行
			uri = Uri.parse("content://com.android.contacts/contacts/" + contactId + "/data");
			dataCursor = getContext().getContentResolver().query(uri, new String[] { "lookup" }, null, null, null);
			while (dataCursor.moveToNext()) {
				lookupKey = dataCursor.getString(0);
				LogUtils.d(TAG, "lookupKey = " + lookupKey);
			}
			Uri shareUri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKey);
			final Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType(Contacts.CONTENT_VCARD_TYPE);
			intent.putExtra(Intent.EXTRA_STREAM, shareUri);
			intent.putExtra(VCardCommonArguments.ARG_CALLING_ACTIVITY,PeopleActivity.class.getName());
			final CharSequence chooseTitle = getContext().getText(R.string.share_via);
			final Intent chooseIntent = Intent.createChooser(intent, chooseTitle);
			startActivity(chooseIntent);
		} catch (final ActivityNotFoundException ex) {
			Toast.makeText(getContext(), R.string.share_error, Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (cursor != null) {
				cursor.close();
			}
			if (dataCursor != null) {
				dataCursor.close();
			}
		}
	}

	//是否收藏
	private boolean isStarred(Uri uri) {
		if (uri == null) {
			return false;
		}
		String star = "";
                Cursor cursor = null;
                Cursor dataCursor = null;
		try {
			cursor = getContext().getContentResolver().query(uri, null, null, null, null);
			cursor.moveToFirst();
			String contactId = cursor.getString(cursor.getColumnIndexOrThrow(Contacts._ID));

		        //放到子线程里运行
			uri = Uri.parse("content://com.android.contacts/contacts/" + contactId + "/data");
			dataCursor = getContext().getContentResolver().query(uri, new String[] { "starred" }, null, null, null);
			while (dataCursor.moveToNext()) {
				star = dataCursor.getString(0);
				LogUtils.d(TAG, "star = " + star);
			}
			if (star.equals("1")) {
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
			if (dataCursor != null) {
				dataCursor.close();
			}
		}
	}

	//收藏 或 从收藏中移除
	private void toggleStar(Uri uri) {
		if (uri == null) {
			return;
		}
		boolean isStarred = isStarred(uri);
		LogUtils.d(TAG, "isStarred = " + isStarred);
		Intent intent = ContactSaveService.createSetStarredIntent(getContext(), uri, !isStarred);
		getContext().startService(intent);
	}

	//加入黑名单
	private void addToBlackList(Uri uri) {
		if (uri == null) {
			return;
		}
                Cursor cursor = null;
                Cursor dataCursor = null;
		try {
			int isBlock = -1;
			cursor = getContext().getContentResolver().query(uri, null, null, null, null);
			cursor.moveToFirst();
			String contactId = cursor.getString(cursor.getColumnIndexOrThrow(Contacts._ID));
			String name = cursor.getString(cursor.getColumnIndexOrThrow("display_name"));
			LogUtils.d(TAG, "name = " + name);

		        //放到子线程里运行
			uri = Uri.parse("content://com.android.contacts/contacts/" + contactId + "/data");
			dataCursor = getContext().getContentResolver().query(uri, new String[] { "data1", "mimetype" }, null, null, null);
			while (dataCursor.moveToNext()) {
				String data = dataCursor.getString(0);
				String compareType = dataCursor.getString(1);
				LogUtils.d(TAG, "data = " + data);
				LogUtils.d(TAG, "compareType = " + compareType);
				if ("vnd.android.cursor.item/phone_v2".equals(compareType)) {
					
					if(data.equals("")){
						return;
					}
					
				    Bundle localBundle = new Bundle();
				    localBundle.putString("BLOCK_CONTACTNAME", name);
				    localBundle.putString("BLOCK_PHONENUMBER", data);
				    isBlock = hisStub.addPhoneNumberBlockItem(localBundle, 0, 0);
				}
			}
			if (isBlock == 0) {
				Toast.makeText(getContext(),getString(R.string.add_to_black_list),Toast.LENGTH_SHORT).show();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (cursor != null) {
				cursor.close();
			}
			if (dataCursor != null) {
				dataCursor.close();
			}
		}
	}

	//从黑名单中移除
	private void removeFromBlacklist(Uri uri) {
		if (uri == null) {
			return;
		}
                Cursor cursor = null;
                Cursor dataCursor = null;
		try {
			int isRemove  = -1;
			cursor = getContext().getContentResolver().query(uri, null, null, null, null);
			cursor.moveToFirst();
			String contactId = cursor.getString(cursor.getColumnIndexOrThrow(Contacts._ID));

		        //放到子线程里运行
			uri = Uri.parse("content://com.android.contacts/contacts/" + contactId + "/data");
			dataCursor = getContext().getContentResolver().query(uri, new String[] { "data1", "mimetype" }, null, null, null);
			while (dataCursor.moveToNext()) {
				String data = dataCursor.getString(0);
				String compareType = dataCursor.getString(1);
				LogUtils.d(TAG, "data = " + data);
				LogUtils.d(TAG, "compareType = " + compareType);
				if ("vnd.android.cursor.item/phone_v2".equals(compareType)) {
				    Bundle localBundle = new Bundle();
				    localBundle.putString("BLOCK_PHONENUMBER", data);
				    isRemove = hisStub.removePhoneNumberBlockItem(localBundle, 0, 0);
				}
			}
			if (isRemove == 0) {
				Toast.makeText(getContext(),getString(R.string.remove_from_black_list),Toast.LENGTH_SHORT).show();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (cursor != null) {
				cursor.close();
			}
			if (dataCursor != null) {
				dataCursor.close();
			}
		}
	}

	//是否所有号码都在黑名单中
	private boolean allNumberInBlackList(Uri uri) {
		if (uri == null) {
			return false;
		}
                Cursor cursor = null;
                Cursor dataCursor = null;
                boolean bHaveNumber = false; //联系人是否保存号码
		try {
			cursor = getContext().getContentResolver().query(uri, null, null, null, null);
			cursor.moveToFirst();
			String contactId = cursor.getString(cursor.getColumnIndexOrThrow(Contacts._ID));

		        //放到子线程里运行
			uri = Uri.parse("content://com.android.contacts/contacts/" + contactId + "/data");
			dataCursor = getContext().getContentResolver().query(uri, new String[] { "data1", "mimetype" }, null, null, null);
			while (dataCursor.moveToNext()) {
				String data = dataCursor.getString(0);
				String compareType = dataCursor.getString(1);
				if (Build.TYPE.equals("eng")) {
					LogUtils.d(TAG, "data = " + data);
					LogUtils.d(TAG, "compareType = " + compareType);
				}
				if ("vnd.android.cursor.item/phone_v2".equals(compareType)) {
				    bHaveNumber = true; //说明有号码
				    LogUtils.d(TAG, "bHaveNumber = " + bHaveNumber);
				    if (!checkNumberBlocked(data)) { //有号码不在黑名单中
					LogUtils.d(TAG, "not all numbers in black list");
					return false;
				    }
				}
			}
			if (bHaveNumber) { //有号码且都在黑名单中
				LogUtils.d(TAG, "all numbers in black list");
				return true;
			}
			LogUtils.d(TAG, "no number");
			return false; //无号码
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
			if (dataCursor != null) {
				dataCursor.close();
			}
		}
	}

	private boolean checkNumberBlocked(String number) {
		try {
			Bundle localBundle = new Bundle();
			localBundle.putString("CHECK_PHONENUMBER", number);
			int isBlock = hisStub.checkPhoneNumberFromBlockItem(localBundle, 0); 
			if (isBlock == 0) {
			    return true;
			} else {
			    return false;
			}                      
		} catch (Exception e) {
			e.printStackTrace();
			return false;     
		}
	}

	//创建快捷方式
	private void createLauncherShortcutWithContact(Uri uri) {
		if (uri == null) {
			return;
		}
		final ShortcutIntentBuilder builder = new ShortcutIntentBuilder(getContext(),
                    new OnShortcutIntentCreatedListener() {

                        @Override
                        public void onShortcutIntentCreated(Uri uri, Intent shortcutIntent) {
                            shortcutIntent.setAction(ACTION_INSTALL_SHORTCUT);
                            getContext().sendBroadcast(shortcutIntent);
                            Toast.makeText(getContext(), R.string.createContactShortcutSuccessful, Toast.LENGTH_SHORT).show();
                        }
                    });
		builder.createContactShortcutIntent(uri);
	}

	//是否保存在SIM卡中的联系人
 	private boolean isSimContact(Uri uri) {
		if (uri == null) {
			return false;
		}
		int indicate=0;
                Cursor cursor = null;
                Cursor dataCursor = null;
		try {
			cursor = getContext().getContentResolver().query(uri, null, null, null, null);
			cursor.moveToFirst();
			String contactId = cursor.getString(cursor.getColumnIndexOrThrow(Contacts._ID));
			LogUtils.d(TAG, "contactId = " + contactId);

		        //放到子线程里运行
			uri = Uri.parse("content://com.android.contacts/contacts/" + contactId + "/data");
			dataCursor = getContext().getContentResolver().query(uri, new String[] { "indicate_phone_or_sim_contact" }, null, null, null);
			while (dataCursor.moveToNext()) {
				indicate = dataCursor.getInt(0);
				LogUtils.d(TAG, "indicate = " + indicate);
			}
			if (indicate>0) {
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
			if (dataCursor != null) {
				dataCursor.close();
			}
		}
 	} 
 	
 	
	//是否是葡萄牙sdn联系人，不可删除编辑
 	private boolean isPortugalContact(Uri uri) {
		if (uri == null) {
			return false;
		}
		int is_sdn_contact=0;
                Cursor cursor = null;
                Cursor dataCursor = null;
		try {
			cursor = getContext().getContentResolver().query(uri, null, null, null, null);
			cursor.moveToFirst();
			String contactId = cursor.getString(cursor.getColumnIndexOrThrow(Contacts._ID));
			LogUtils.d(TAG, "contactId = " + contactId);

		        //放到子线程里运行
			uri = Uri.parse("content://com.android.contacts/contacts/" + contactId + "/data");
			dataCursor = getContext().getContentResolver().query(uri, new String[] { "is_sdn_contact" }, null, null, null);
			while (dataCursor.moveToNext()) {
				is_sdn_contact = dataCursor.getInt(0);
				LogUtils.d(TAG, "is_sdn_contact = " + is_sdn_contact);
			}
			if (is_sdn_contact==-2) {
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
			if (dataCursor != null) {
				dataCursor.close();
			}
		}
 	} 

	private void buildContextMenu(final int position) {
		final Uri uri = getAdapter().getContactUri(position);
		LogUtils.d(TAG, "uri = " + uri);
		final String displayName = getAdapter().getContactDisplayName(position);
		if (uri != null && !isSimContact(uri)&&!isPortugalContact(uri)) {
			String[] arrayMenuItems = getResources().getStringArray(R.array.phone_contact_menu);
			ArrayList<String>  arrayMenuItemsList=new ArrayList<>();
			
			for (String string : arrayMenuItems) {
				arrayMenuItemsList.add(string);
			}
			final boolean numIsNull=NumberIsNull(uri);
			if(numIsNull){
				if(arrayMenuItemsList.size()>=7){
					arrayMenuItemsList.remove(7);
					arrayMenuItems=(String[]) arrayMenuItemsList.toArray(new String[arrayMenuItemsList.size()]);
				}
			}
			if (isStarred(uri)) {
				arrayMenuItems[6] = getContext().getString(R.string.menu_removeStar);
			}
			if (allNumberInBlackList(uri)) {
				arrayMenuItems[7] = getContext().getString(R.string.menu_remove_from_black_list);
			}
		
			AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
			builder.setTitle(displayName).setItems(arrayMenuItems, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					switch (id) {
					    case 0:
		                                callNumber(uri);
						break;
					    case 1:
						sendMessage(uri);
						break;
					    case 2:
						sendEMail(uri);
						break;
					    case 3:
						editContact(uri);
						break;
					    case 4:
						deleteContact(uri);
						break;
					    case 5:
						shareContact(uri);
						break;
					    case 6:
						toggleStar(uri);
						break;
					    case 7:
					    	if(numIsNull){
					    	}else {
					    		if (!allNumberInBlackList(uri)) {
					    			addToBlackList(uri);
					    		} else {
					    			removeFromBlacklist(uri);
					    		}
					    		break;
							}
					    case 8:
						createLauncherShortcutWithContact(uri);
						break;
					    default:
						break;
					}
				}
			}).create().show();
		}else if (uri != null &&isPortugalContact(uri)) {
			String[] arrayMenuItems = getResources().getStringArray(R.array.Portugal_Sdn_contact_menu);
			ArrayList<String>  arrayMenuItemsList=new ArrayList<>();
			
			for (String string : arrayMenuItems) {
				arrayMenuItemsList.add(string);
			}
			final boolean numIsNull=NumberIsNull(uri);
			if(numIsNull){
				if(arrayMenuItemsList.size()>=7){
					arrayMenuItemsList.remove(5);
					arrayMenuItems=(String[]) arrayMenuItemsList.toArray(new String[arrayMenuItemsList.size()]);
				}
			}
			if (isStarred(uri)) {
				arrayMenuItems[4] = getContext().getString(R.string.menu_removeStar);
			}
			if (allNumberInBlackList(uri)) {
				arrayMenuItems[5] = getContext().getString(R.string.menu_remove_from_black_list);
			}
		
			AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
			builder.setTitle(displayName).setItems(arrayMenuItems, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					switch (id) {
					    case 0:
		                callNumber(uri);
						break;
					    case 1:
						sendMessage(uri);
						break;
					    case 2:
						sendEMail(uri);
						break;
					    case 3:
						shareContact(uri);
						break;
					    case 4:
						toggleStar(uri);
						break;
					    case 5:
					    	if(numIsNull){
					    	}else {
					    		if (!allNumberInBlackList(uri)) {
					    			addToBlackList(uri);
					    		} else {
					    			removeFromBlacklist(uri);
					    		}
					    		break;
							}
					    case 6:
						createLauncherShortcutWithContact(uri);
						break;
					    default:
						break;
					}
				}
			}).create().show();
		}else if (uri != null) {
			final String[] arrayMenuItems = getResources().getStringArray(R.array.sim_contact_menu);
			if (isStarred(uri)) {
				arrayMenuItems[5] = getContext().getString(R.string.menu_removeStar);
			}
			if (allNumberInBlackList(uri)) {
				//arrayMenuItems[5] = getContext().getString(R.string.menu_remove_from_black_list);
                                arrayMenuItems[6] = getContext().getString(R.string.menu_remove_from_black_list);
			}
		
			AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
			builder.setTitle(displayName).setItems(arrayMenuItems, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int id) {
					switch (id) {
					    case 0:
		                                callNumber(uri);
						break;
					    case 1:
						sendMessage(uri);
						break;
					    case 2:
						editContact(uri);
						break;
					    case 3:
						deleteContact(uri);
						break;
					    case 4:
						shareContact(uri);
						break;
					    case 5:
						toggleStar(uri);
						break;
					    case 6:
						if (!allNumberInBlackList(uri)) {
						    addToBlackList(uri);
						} else {
						    removeFromBlacklist(uri);
						}
						break;
					    case 7:
						createLauncherShortcutWithContact(uri);
						break;
					    default:
						break;
					}
				}
			}).create().show();
		}
	}
	//caohaolin added for long click contact item end

	
	private boolean NumberIsNull(Uri uri) {
		if (uri == null) {
			return true;
		}
		Cursor cursor = null;
		Cursor dataCursor = null;
		try {
			cursor = getContext().getContentResolver().query(uri, null, null,
					null, null);
			cursor.moveToFirst();
			String contactId = cursor.getString(cursor
					.getColumnIndexOrThrow(Contacts._ID));

			// 放到子线程里运行
			uri = Uri.parse("content://com.android.contacts/contacts/"
					+ contactId + "/data");
			dataCursor = getContext().getContentResolver().query(uri,
					new String[] { "data1", "mimetype" }, null, null, null);
			while (dataCursor.moveToNext()) {
				String data = dataCursor.getString(0);
				String compareType = dataCursor.getString(1);
				if (Build.TYPE.equals("eng")) {
					LogUtils.d(TAG, "data = " + data);
					LogUtils.d(TAG, "compareType = " + compareType);
				}
				if ("vnd.android.cursor.item/phone_v2".equals(compareType)) {
					if (data.equals("")) {

						return true;

					} else {
						return false;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (cursor != null) {
				cursor.close();
			}

			if (dataCursor != null) {
				dataCursor.close();
			}
		}
		return true;
	}

	@Override
	protected ContactListAdapter createListAdapter() {
		DefaultContactListAdapter adapter = new DefaultContactListAdapter(
				getContext());
		adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
		adapter.setDisplayPhotos(true);
		adapter.setPhotoPosition(ContactListItemView
				.getDefaultPhotoPosition(/* opposite = */false));
		return adapter;
	}

	@Override
	protected View inflateView(LayoutInflater inflater, ViewGroup container) {
		// return inflater.inflate(R.layout.contact_list_content, null);
		DefaultContactBrowseListFragmentView = inflater.inflate(
				R.layout.contact_list_content, null);
		return DefaultContactBrowseListFragmentView;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		// TODO Auto-generated method stub
		super.onLoadFinished(loader, data);

		//modify by caohaolin begin
		int count = getAdapter().getCount();
		if(isSearchMode()) {
			count --;
		}
		/* HQ_fengsimin 2016-3-22 modified for HQ01811908 */
		if(count>=10){
			showdialog=true;
		}else{
			showdialog=false;
		}
		//modify by wanghui for al813 
		String contactstoDisplay = "";
		if( SystemProperties.get("ro.product.name").equals("TAG-L03")&&(getContactsCount().equals("0"))){
            contactstoDisplay = getResources().getString(R.string.HQ_ContactsDefault);
		}else{
		      contactstoDisplay = getResources().getString(
				R.string.HQ_ContactsToDisplay)
				+ "  " + getContactsCount();//HQ_hushunli 2015-10-15 modify for HQ01354358
		//modify by caohaolin end		
         }
		contactsToDisplay_tv.setText(contactstoDisplay);
		if (getListView().getHeaderViewsCount() < 3) {
			getListView().addHeaderView(contactlistcontent_headview);
		}
	}

	private String getContactsCount() {//HQ_hushunli 2015-10-15 add for HQ01354358
        String countStr = getAdapter().getCount() + "";
        String returnCountStr= "";
        String curLanguageString = getResources().getConfiguration().locale.getLanguage();
        Log.d(TAG, "curLanguageString is " + curLanguageString + ", persist.sys.hq.arabic.numerals is " + SystemProperties.get("persist.sys.hq.arabic.numerals"));
        if (!(curLanguageString.equals("ar") || curLanguageString.equals("fa"))) {
            return countStr;
        }
        if (SystemProperties.get("persist.sys.hq.arabic.numerals").equals("1")) {
            for (int i = 0; i < countStr.length(); i++) {
                if (curLanguageString.equals("ar")) {
                    returnCountStr += mArLangNummap.get(countStr.charAt(i) + "");
                } else if (curLanguageString.equals("fa")) {
                    returnCountStr += mFaLangNummap.get(countStr.charAt(i) + "");
                }
            }
            return returnCountStr;
        } else {
            return countStr;
        }
    }

	public View getDefaultContactBrowseListFragmentView() {
		return DefaultContactBrowseListFragmentView;
	}

	/**
	 * 
	 * @param DefaultContactBrowseListFragmentView
	 */
	public void setupHqView(View DefaultContactBrowseListFragmentView) {
		// TODO Auto-generated method stub
		View SearchView = DefaultContactBrowseListFragmentView
				.findViewById(R.id.SearchView);
		View newContatcsView = DefaultContactBrowseListFragmentView
				.findViewById(R.id.newContatcsView);
		View menuView = DefaultContactBrowseListFragmentView
				.findViewById(R.id.menuView);
		SearchView.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				mSearchView.requestFocus();
				showInputMethod(mSearchView);
			}
		});
		MyGroup = (RelativeLayout) contactlistcontent_headview
				.findViewById(R.id.MyGroup);
/*		if (isSimpleModeOn()) {
			MyGroup.setVisibility(View.GONE);
		}*/
		/*LifeService = (RelativeLayout) contactlistcontent_headview
				.findViewById(R.id.LifeService);*/

		final TextView contactsToDisplay_tv = (TextView) contactlistcontent_headview
				.findViewById(R.id.contactsToDisplay_tv);

		// /Annotated by guofeiyao
		/*
		MyDock = (RelativeLayout) DefaultContactBrowseListFragmentView
				.findViewById(R.id.MyDock);
				*/
		// /End
		MyDock_layout = (LinearLayout) DefaultContactBrowseListFragmentView
				.findViewById(R.id.MyDock_layout);
		mSearchView = (EditText) DefaultContactBrowseListFragmentView
				.findViewById(R.id.search_view);
		mSearchView.setOnFocusChangeListener(new View.OnFocusChangeListener() {

			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				// Toast.makeText(getApplicationContext(),
				// "searchView获得焦点！",Toast.LENGTH_LONG).show();
				/* begin: delete by donghongjing for HQ01410676 */
				/*if (hasFocus) {
					MyDock_layout.setVisibility(View.GONE);
					MyGroup.setVisibility(View.GONE);
					LifeService.setVisibility(View.GONE);
					contactsToDisplay_tv.setVisibility(View.GONE);
					sideBar.setVisibility(View.GONE);

				} else {
					MyDock_layout.setVisibility(View.VISIBLE);
					MyGroup.setVisibility(View.VISIBLE);
					LifeService.setVisibility(View.VISIBLE);
					contactsToDisplay_tv.setVisibility(View.VISIBLE);
					sideBar.setVisibility(View.VISIBLE);
				}*/
				/* end: delete by donghongjing for HQ01410676 */
				
				//[add by lizhao for HQ01711206 at 20160201 begain
				if (mSearchView != null && !hasFocus) {
					InputMethodManager imm = (InputMethodManager) mSearchView
							.getContext().getSystemService(
									Context.INPUT_METHOD_SERVICE);
					boolean isOpen = imm.isActive();// isOpen若返回true，则表示输入法打开
					if (isOpen && v != null) {
						imm.hideSoftInputFromWindow(v.getWindowToken(), 0); // 强制隐藏键盘
					}
				}
				//add by lizhao for HQ01711206 at 20160201 end]
				}
		});
		mSearchView.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				String ss = mSearchView.getText().toString();
				/* HQ_fengsimin 2016-4-6 modified for HQ01850074 */
				ss=cutStarWhenSearch(ss);
				setQueryString(ss, false);
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
				// TODO Auto-generated method stub

			}

			@Override
			public void afterTextChanged(Editable s) {
				// TODO Auto-generated method stub

			}
		});
		getListView().setOnTouchListener(new View.OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// TODO Auto-generated method stub
//				final int action = event.getAction();
//				switch (action) {
//				case MotionEvent.ACTION_UP:
//					if (sideBar != null) {
//						TextView tv = sideBar.getTextDialog();
//						if(tv!=null){
//							tv.setVisibility(View.INVISIBLE);
//						}
//					}
//					break;

					if (mSearchView != null && mSearchView.hasFocus()) {
						mSearchView.clearFocus();
						
					}
					
				return false;
				
			}
		});
		
		getListView().setOnScrollListener(new AbsListView.OnScrollListener() {
			
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				/* HQ_fengsimin 2016-3-15 modified for HQ01811965 */
				if(!isSearchMode()&&showdialog){
					SideBar bar = getSideBar();
					TextView tv=null;
					if (bar != null) {
						tv = bar.getTextDialog();
					}
					switch (scrollState) {
					case AbsListView.OnScrollListener.SCROLL_STATE_IDLE:
						if (tv != null) {
							tv.setVisibility(View.INVISIBLE);
						}
						break;
					case AbsListView.OnScrollListener.SCROLL_STATE_FLING:
						if (tv != null) {
							tv.setVisibility(View.VISIBLE);
						}
						break;
					default:
						if (tv != null) {
							tv.setVisibility(View.VISIBLE);
						}
						break;
					}
				}
			}
			@Override
			public void onScroll(AbsListView view, int firstVisibleItem,
					int visibleItemCount, int totalItemCount) {
				/* HQ_fengsimin 2016-2-20 modified for HQ01751889 */
				if(!isSearchMode()&&showdialog&&getListView()!=null&&getAdapter()!=null){
					int firstpositon = getListView().getFirstVisiblePosition();
					if(firstpositon-3>=0){
						setSideBarDialog(getAdapter(),firstpositon-3);
						}else{
							setSideBarDialog(getAdapter(),0);
						}
				}
			}
		});
		sideBar = (SideBar) DefaultContactBrowseListFragmentView
				.findViewById(R.id.sideBar);
		dialog = (TextView) DefaultContactBrowseListFragmentView
				.findViewById(R.id.dialog);
		sideBar.setTextView(dialog);

		// 设置右侧触摸监听
		sideBar.setOnTouchingLetterChangedListener(new OnTouchingLetterChangedListener() {

			@Override
			public void onTouchingLetterChanged(String s) {
				/*
				 * //该字母首次出现的位置,获取联系人的位置！！ int position =
				 * adapter.getPositionForSection(s.charAt(0)); if(position !=
				 * -1){ //设置listviewItem的位置！！！
				 * sortListView.setSelection(position); }
				 */

				int TouchCharNum = s.toUpperCase().charAt(0);
				String[] Sections = (String[]) getAdapter().getSections();
				// for (String string : Sections) {
				// Log.i(TAG, "the sections is "+string);
				// }
				for (int i = 0; i < Sections.length; i++) {

					if (TouchCharNum == Sections[i].charAt(0)) {
						int position = getAdapter().getPositionForSection(i);
						/* HQ_fengsimin 2016-2-20 modified for HQ01751889 */
						if(getAdapter()!=null&&getListView()!=null){
							int count = getAdapter().getCount();
							if (position < count) {
								getListView().setSelection(position + 3);
							}
						}
						dialog.setText(Sections[i]);
					}
					// Log.i(TAG,
					// "the position is "+i+"the string is "+Sections[i]);
				}
			}
		});

		// / Modified by guofeiyao
		/*
		//add by jinlibo for performance
		if(getActivity() instanceof PeopleActivity){
		    ((PeopleActivity)getActivity()).setupHqView(DefaultContactBrowseListFragmentView);
		}
		*/
        
        Activity ac = getActivity();
		if ( ac instanceof PeopleActivity ) {
             MyGroup.setOnClickListener((PeopleActivity)ac);
//		     LifeService.setOnClickListener((PeopleActivity)ac);
       		 newContatcsView.setOnClickListener((PeopleActivity)ac);
      		 menuView.setOnClickListener((PeopleActivity)ac);
		}
		
		// / End
	}
	public static  SideBar getSideBar() {
		return sideBar;
	}
	private void showInputMethod(View view) {
		final InputMethodManager imm = (InputMethodManager) this.getContext()
				.getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null) {
			imm.showSoftInput(view, 0);
		}
	}

	@Override
	protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
		super.onCreateView(inflater, container);

		mAccountFilterHeader = getView().findViewById(
				R.id.account_filter_header_container);
		mAccountFilterHeader.setOnClickListener(mFilterHeaderClickListener);
		contactlistcontent_headview = inflater.inflate(
				R.layout.contactlistcontent_headview, null);
		contactsToDisplay_tv = (TextView) contactlistcontent_headview
				.findViewById(R.id.contactsToDisplay_tv);
		setupHqView(DefaultContactBrowseListFragmentView);
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
		} else {
			if (MyDock_layout != null) {
				MyDock_layout.setVisibility(View.GONE);
			}
		}
		// Create an empty user profile header and hide it for now (it will be
		// visible if the
		// contacts list will have no user profile).
		addEmptyUserProfileHeader(inflater);
		showEmptyUserProfile(false);
		/** M: Bug Fix for ALPS00115673 Descriptions: add wait cursor */
		mWaitCursorView = ContactsListUtils.initLoadingView(this.getContext(),
				getView(), mLoadingContainer, mLoadingContact, mProgress);

		// Putting the header view inside a container will allow us to make
		// it invisible later. See checkHeaderViewVisibility()
		// 搜索界面
		FrameLayout headerContainer = new FrameLayout(inflater.getContext());
		mSearchHeaderView = inflater.inflate(R.layout.search_header, null,
				false);
		headerContainer.addView(mSearchHeaderView);
		getListView().addHeaderView(headerContainer, null, false);
		checkHeaderViewVisibility();

		mSearchProgress = getView().findViewById(R.id.search_progress);
		mSearchProgressText = (TextView) mSearchHeaderView
				.findViewById(R.id.totalContactsText);

	}

	@Override
	protected void setSearchMode(boolean flag) {
		super.setSearchMode(flag);
		checkHeaderViewVisibility();
		/* begin: add by donghongjing for HQ01410676 */
        if (MyDock_layout != null) {
		    if (flag) {
			    MyDock_layout.setVisibility(View.GONE);
			    MyGroup.setVisibility(View.GONE);
			    //LifeService.setVisibility(View.GONE);
			    contactsToDisplay_tv.setVisibility(View.GONE);
			    sideBar.setVisibility(View.GONE);

		    } else {
			    MyDock_layout.setVisibility(View.VISIBLE);
/*			    if (isSimpleModeOn()) {
			        MyGroup.setVisibility(View.GONE);
			    } else {
			    }*/
			    MyGroup.setVisibility(View.VISIBLE);
			    //LifeService.setVisibility(View.VISIBLE);
			    contactsToDisplay_tv.setVisibility(View.VISIBLE);
			    sideBar.setVisibility(View.VISIBLE);
		    }
        }
		/* end: add by donghongjing for HQ01410676 */
		if (!flag)
			showSearchProgress(false);
	}

	//HQ_wuruijun add for HQ01435294 start
	public boolean isSimpleModeOn(){
		if (null == mPm) {
			mPm = getContext().getPackageManager();
		}
		ArrayList<ResolveInfo> homeActivities = new ArrayList<ResolveInfo>();
		ComponentName currentDefaultHome = mPm.getHomeActivities(homeActivities);
		final ResolveInfo candidate = homeActivities.get(0);
		final ActivityInfo info = candidate.activityInfo;
		ComponentName activityName = new ComponentName(info.packageName, info.name);
		int flag = activityName.compareTo(mSimpleui);
		if(flag == 0){
			Log.v(TAG, "activityName == mSimpleui==" + true);
			return true;
		}
		return false;
	}
	//HQ_wuruijun add end

	/** Show or hide the directory-search progress spinner. */
	private void showSearchProgress(boolean show) {
		if (mSearchProgress != null) {
			mSearchProgress.setVisibility(show ? View.VISIBLE : View.GONE);
		}
	}

	private void checkHeaderViewVisibility() {
		updateFilterHeaderView();

		// Hide the search header by default.
		if (mSearchHeaderView != null) {
			mSearchHeaderView.setVisibility(View.GONE);
		}
	}

	@Override
	public void setFilter(ContactListFilter filter) {
		super.setFilter(filter);
		updateFilterHeaderView();
	}

	private void updateFilterHeaderView() {
		if (mAccountFilterHeader == null) {
			return; // Before onCreateView -- just ignore it.
		}
		final ContactListFilter filter = getFilter();
		if (filter != null && !isSearchMode()) {
			final boolean shouldShowHeader = AccountFilterUtil
					.updateAccountFilterTitleForPeople(mAccountFilterHeader,
							filter, false);
			mAccountFilterHeader.setVisibility(shouldShowHeader ? View.VISIBLE
					: View.GONE);
		} else {
			mAccountFilterHeader.setVisibility(View.GONE);
		}
	}

	@Override
	public void setProfileHeader() {
		if (getAdapter() == null) {
			return;
		}
		mUserProfileExists = getAdapter().hasProfile();
		showEmptyUserProfile(!mUserProfileExists && !isSearchMode());

		if (isSearchMode()) {
			ContactListAdapter adapter = getAdapter();
			if (adapter == null) {
				return;
			}

			// In search mode we only display the header if there is nothing
			// found
			if (TextUtils.isEmpty(getQueryString())
					|| !adapter.areAllPartitionsEmpty()) {
				mSearchHeaderView.setVisibility(View.GONE);
				showSearchProgress(false);
			} else {
				mSearchHeaderView.setVisibility(View.VISIBLE);
				if (adapter.isLoading()) {
					mSearchProgressText
							.setText(R.string.search_results_searching);
					showSearchProgress(true);
				} else {
					mSearchProgressText
							.setText(R.string.listFoundAllContactsZero);
					mSearchProgressText
							.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
					showSearchProgress(false);
				}
			}
			showEmptyUserProfile(false);
		}

		// / M: [VCS] @{
		int count = getContactCount();
		if (mContactsLoadListener != null) {
			mContactsLoadListener.onContactsLoad(count);
		}
		// / @}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CODE_ACCOUNT_FILTER) {
			if (getActivity() != null) {
				AccountFilterUtil.handleAccountFilterResult(
						ContactListFilterController.getInstance(getActivity()),
						resultCode, data);
			} else {
				Log.e(TAG,
						"getActivity() returns null during Fragment#onActivityResult()");
			}
		}
	}

	private void showEmptyUserProfile(boolean show) {
		// Changing visibility of just the mProfileHeader doesn't do anything
		// unless
		// you change visibility of its children, hence the call to
		// mCounterHeaderView
		// and mProfileTitle
		show = false;// add bt HQ tanghuaizhe for conceal the userProfile!!
		Log.d(TAG, "showEmptyUserProfile show : " + show);
		mProfileHeaderContainer.setVisibility(show ? View.VISIBLE : View.GONE);
		mProfileHeader.setVisibility(show ? View.VISIBLE : View.GONE);
		mProfileTitle.setVisibility(show ? View.VISIBLE : View.GONE);
		mProfileMessage.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	/**
	 * This method creates a pseudo user profile contact. When the returned
	 * query doesn't have a profile, this methods creates 2 views that are
	 * inserted as headers to the listview: 1. A header view with the "ME" title
	 * and the contacts count. 2. A button that prompts the user to create a
	 * local profile
	 */
	private void addEmptyUserProfileHeader(LayoutInflater inflater) {
		ListView list = getListView();
		// Add a header with the "ME" name. The view is embedded in a frame view
		// since you cannot
		// change the visibility of a view in a ListView without having a parent
		// view.
		mProfileHeader = inflater.inflate(R.layout.user_profile_header, null,
				false);
		mProfileTitle = (TextView) mProfileHeader
				.findViewById(R.id.profile_title);
		mProfileHeaderContainer = new FrameLayout(inflater.getContext());
		mProfileHeaderContainer.addView(mProfileHeader);
		list.addHeaderView(mProfileHeaderContainer, null, false);

		// Add a button with a message inviting the user to create a local
		// profile
		mProfileMessage = (Button) mProfileHeader
				.findViewById(R.id.user_profile_button);
		mProfileMessage.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(Intent.ACTION_INSERT,
						Contacts.CONTENT_URI);
				intent.putExtra(
						ContactEditorFragment.INTENT_EXTRA_NEW_LOCAL_PROFILE,
						true);
				startActivity(intent);
			}
		});
	}

	/** M: Bug Fix For ALPS00115673. @{ */
	private ProgressBar mProgress;
	private View mLoadingContainer;
	private WaitCursorView mWaitCursorView;
	private TextView mLoadingContact;
	/** @} */
	// / M: for vcs
	private VcsController.ContactsLoadListener mContactsLoadListener = null;

	/**
	 * M: Bug Fix CR ID: ALPS00279111.
	 */
	public void closeWaitCursor() {
		// TODO Auto-generated method stub

        // / Modified by guofeiyao for Monkey test.
		if ( null != mWaitCursorView ) {
		     Log.d(TAG, "closeWaitCursor   DefaultContactBrowseListFragment");
       		 mWaitCursorView.stopWaitCursor();
		}
	}

	/**
	 * M: [vcs] for vcs.
	 */
	public void setContactsLoadListener(
			VcsController.ContactsLoadListener listener) {
		mContactsLoadListener = listener;
	}

	/**
	 * M: for ALPS01766595.
	 */
	private int getContactCount() {
		int count = isSearchMode() ? 0 : getAdapter().getCount();
		if (mUserProfileExists) {
			count -= PROFILE_NUM;
		}
		return count;
	}
	/**
     * HQ_fengsimin 2016-2-20 add for HQ01751889
     * @param position
     */
    public void setSideBarDialog(ContactListAdapter adapter,int position){
    	 int sectionIndex=adapter.getSectionForPosition(position);
         int section = -1;
         int partition1 = adapter.getPartitionForPosition(position);
         if (partition1 == adapter.getIndexedPartition()) {
             int offset = adapter.getOffsetInPartition(position);
             if (offset != -1) {
                 section = adapter.getSectionForPosition(offset);
             }
         }
         if(section!=-1&&section<=adapter.getSections().length){
         	String secString=(String)adapter.getSections()[section];
         	SideBar sidebar=DefaultContactBrowseListFragment.getSideBar();
         	
         	if(sidebar!=null){
         		sidebar.setPosition(secString);
         	}
         }
    }
	/**
	 * HQ_fengsimin 2016-4-6 add for HQ01850074
	 * @param ss
	 * 
	 */
	private String cutStarWhenSearch(String ss){
		if(ss!=null){
			if(ss.length()==1 && ss.charAt(0)=='*'){
				return " ";
			}else if(ss.length()> 1 && ss.charAt(0)=='*'){
				ss=cutStarWhenSearch(ss.substring(1));
			}
		}
		return ss;
	}

}
