package com.android.settings.accessibility.networklimit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Browser;
import android.provider.BrowserContract;
import android.provider.ChildMode;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.android.settings.R;

import com.android.settings.childsecurity.ChooseChildLockHelper;

import java.util.ArrayList;

/**
 * Created by Administrator on 15-2-5.
 */
public class BookmarkAndHistoryListActivity extends Activity
        implements View.OnClickListener, AdapterView.OnItemClickListener {
    private static final boolean DEBUG = true;
    private static final int WEBSITE_FORM_BOOKMARK = 0;
    private static final int WEBSITE_FORM_HISTORY = 1;
    private ArrayList<WebSiteBean> list = new ArrayList<WebSiteBean>();
    private ArrayList<WebSiteBean> listSelected = new ArrayList<WebSiteBean>();
    private MyAdatper adapter;
    private Button ok;
    private int formType = WEBSITE_FORM_BOOKMARK;
    
    private ChooseChildLockHelper mChooseChildLockHelper;
    private static final int CONFIRM_EXISTING_FROM_RESUME = 131;
    boolean needToConfirm = false;
    
    private BroadcastReceiver mHomeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                String reason = intent.getStringExtra("reason");
                if ("homekey".equals(reason)) {
                    log("home key pressed");
                    needToConfirm = true;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.website_bookmark_history_list);
        formType = getIntent().getIntExtra("website_add_from_type", WEBSITE_FORM_BOOKMARK);
        log("onCreate formType=" + ((formType == 0) ? "bookmark" : "history"));
        ListView listView = (ListView) findViewById(R.id.list);
        adapter = new MyAdatper();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);
        Button cancel = (Button) findViewById(R.id.cancel);
        ok = (Button) findViewById(R.id.ok);
        cancel.setOnClickListener(this);
        ok.setOnClickListener(this);

        loadData();
        
        mChooseChildLockHelper = new ChooseChildLockHelper(this);
    }
    
    //modified by machao 20150430 check lock state when starting this activity from recently apps -begin-
    @Override
    protected void onResume() {
        super.onResume();
        if (needToConfirm) {
            log("onResume show lock");
            mChooseChildLockHelper.launchConfirmationActivity(CONFIRM_EXISTING_FROM_RESUME, null, null);  
        }
    }
    
    @Override
    public void onStart() {
        super.onStart();
        IntentFilter mHomeIntentFilter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomeReceiver, mHomeIntentFilter);
    }
    
    @Override
    public void onStop() {
        unregisterReceiver(mHomeReceiver);
        super.onStop();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode==Activity.RESULT_OK) {
            needToConfirm = false;
        }
    }
    //modified by machao 20150430 check lock state when starting this activity from recently apps -end-

    private void loadData() {
        if (WEBSITE_FORM_BOOKMARK == formType) {
            //bookmark
            log("bookmark load data");
            Cursor cursor = getContentResolver().query(Browser.BOOKMARKS_URI,
                    null,  Browser.BookmarkColumns.BOOKMARK + " = 1", null, Browser.BookmarkColumns.VISITS + " DESC");
            if (null != cursor) {
                list.clear();
                log("cursor.count=" + cursor.getCount());
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    WebSiteBean bean = new WebSiteBean();
                    String name = cursor.getString(cursor.getColumnIndex(Browser.BookmarkColumns.TITLE));
                    String website = cursor.getString(cursor.getColumnIndex(Browser.BookmarkColumns.URL));

                    //begin:modifid by chensuyu for display url when name and website is not null [SW00126117] 20150327
                    if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(website)) {
                        bean.setName(name);
                        bean.setWebsite(website);
                        list.add(bean);
                    }
                    //end:modifid by chensuyu for display url when name and website is not null [SW00126117] 20150327
                    cursor.moveToNext();
                    //mHandler.sendEmptyMessage(EVENT_REFRESH_LISTVIEW);
                }
                cursor.close();
            } else {
                log("query data fail!");
            }
        } else {
            //history
            log("history load data");
            Cursor cursor = getContentResolver().query(BrowserContract.Combined.CONTENT_URI,
                    null, BrowserContract.Combined.VISITS + " > 0", null, null);
            if (null != cursor) {
                list.clear();
                log("cursor.count=" + cursor.getCount());
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    WebSiteBean bean = new WebSiteBean();
                    String name = cursor.getString(cursor.getColumnIndex(BrowserContract.Combined.TITLE));
                    String website = cursor.getString(cursor.getColumnIndex(BrowserContract.Combined.URL));

                    //begin:modifid by chensuyu for display url when name and website is not null [SW00126117] 20150327
                    if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(website)) {
                        bean.setName(name);
                        bean.setWebsite(website);
                        list.add(bean);
                    }
                    //end:modifid by chensuyu for display url when name and website is not null [SW00126117] 20150327
                    cursor.moveToNext();
                    //mHandler.sendEmptyMessage(EVENT_REFRESH_LISTVIEW);
                }
                cursor.close();
            } else {
                log("query data fail!");
            }
            
        }

        if (list.size() > 0) {
            ((TextView) findViewById(R.id.empty)).setText("");
        } else {
            if (formType == WEBSITE_FORM_BOOKMARK) {
                ((TextView) findViewById(R.id.empty)).setText(R.string.website_bookmark_empty);
            } else {
                ((TextView) findViewById(R.id.empty)).setText(R.string.website_history_empty);
            }
        }

        adapter.notifyDataSetChanged();
    }

    //implements View.OnClickListener
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.cancel:
                log("click cancel button, finish activity.");
                BookmarkAndHistoryListActivity.this.finish();
                break;
            case R.id.ok:
                listSelected.clear();
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).isChecked()) {
                        listSelected.add(list.get(i));
                    }
                }
                if (listSelected.size() > 0) {
                    showAddDialog();
                }
                break;
            default:
                break;
        }
    }

    private void showAddDialog() {
        int themeId = getResources().getIdentifier("androidhwext:style/Theme.Emui.Dialog.Alert", null, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this, themeId);
        if (WEBSITE_FORM_BOOKMARK == formType) {
            builder.setTitle(R.string.website_bookmark_dialog_title);
        } else {
            builder.setTitle(R.string.website_history_dialog_title);
        }

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                for (int i = 0; i < listSelected.size(); i++) {
                    String name = listSelected.get(i).getName();
                    String website = listSelected.get(i).getWebsite();
                    log("user click ok, name=" + name + ", website=" + website);
                    if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(website)) {
                        Uri uri = ChildMode.addUrlList(getContentResolver(), name,
                                website);
                        log("add data to db, name=" + name + ", website=" + website);
                    }

                }
                BookmarkAndHistoryListActivity.this.finish();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builder.create().show();
    }

    //implements AdapterView.OnItemClickListener
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (list.get(position).isChecked()) {
            list.get(position).setChecked(false);
        } else {
            list.get(position).setChecked(true);
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).isChecked()) {
                //ok button enabled
                ok.setEnabled(true);
                break;
            } else {
                //ok button disabled
                ok.setEnabled(false);
            }
        }
        adapter.notifyDataSetChanged();

    }

    private class MyAdatper extends BaseAdapter {

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            if (list.size() > 0) {
                return list.get(position);
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (null == convertView) {
                holder = new ViewHolder();
                convertView = getLayoutInflater().inflate(R.layout.website_bookmark_history_item, null);
                holder.index = (TextView) convertView.findViewById(R.id.index);
                holder.name = (TextView) convertView.findViewById(R.id.title);
                holder.website = (TextView) convertView.findViewById(R.id.url);
                holder.checkBox = (CheckBox) convertView.findViewById(R.id.checkbox);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.index.setText("" + (1 + position));
            holder.name.setText(list.get(position).getName());
            holder.website.setText(list.get(position).getWebsite());
            holder.checkBox.setChecked(list.get(position).isChecked());
            return convertView;
        }

        private class ViewHolder {
            TextView index;
            TextView name;
            TextView website;
            CheckBox checkBox;
        }
    }

    private void log(String msg) {
        if (DEBUG) {
            Log.d("BookmarkAndHistoryListActivity", msg);
        }
    }
}
