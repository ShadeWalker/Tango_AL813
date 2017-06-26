package com.android.settings.accessibility.networklimit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ChildMode;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.android.settings.R;

import com.android.settings.childsecurity.ChooseChildLockHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Administrator on 15-2-5.
 */
public class WebSiteListActivity extends Activity implements AlertDialog.OnClickListener {
    private static final int DIALOG_ITEM_BOOKMARK_INDEX = 0;
    private static final int DIALOG_ITEM_HISTORY_INDEX = 1;
    private static final int DIALOG_ITEM_CUSTOM_INDEX = 2;
    private static final int FLAG_NEW_WEBSITE = 0;
    private static final int FLAG_EDIT_WEBSITE = 1;
    private static final int WEBSITE_FORM_BOOKMARK = 0;
    private static final int WEBSITE_FORM_HISTORY = 1;
    private static final int EVENT_REFRESH_LISTVIEW = 0;

    private static final int INVALID = -1;
    private static final int ALL_CHECKBOX_DISPLAY = -2;
    private static final boolean DEBUG = true;
    
    private ChooseChildLockHelper mChooseChildLockHelper;
    private static final int CONFIRM_EXISTING_FROM_RESUME = 131;
    boolean needToConfirm = false;

    private ArrayList<WebSiteBean> list = new ArrayList<WebSiteBean>();
    private int itemClickIndex = INVALID;
    private WhiteListAdapter adapter;
    private ListView listview;
    private int count = 0;
    
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
        setContentView(R.layout.website_list);
        
        mChooseChildLockHelper = new ChooseChildLockHelper(this);

        listview = (ListView) findViewById(R.id.list);
        listview.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        adapter = new WhiteListAdapter();
        listview.setAdapter(adapter);
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                    //need display del and edit button.
                    itemClickIndex = position;
                    adapter.notifyDataSetChanged();
            }
        });

        listview.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            private TextView mTitleText;

            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
                log("onItemCheckedStateChanged position=" + position + ", checked=" + checked);
                list.get(position).setChecked(checked);
                adapter.notifyDataSetChanged();
                if (checked) {
                    mTitleText.setText(getString(R.string.website_select_count, ++count));
                } else {
                    mTitleText.setText(getString(R.string.website_select_count, --count));
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                itemClickIndex = ALL_CHECKBOX_DISPLAY;
                adapter.notifyDataSetChanged();
                mode.getMenuInflater().inflate(R.menu.website_list_actionmode_menu, menu);
                View localview = LayoutInflater.from(WebSiteListActivity.this).inflate(R.layout.website_actionmode, null);
                mTitleText = (TextView) localview.findViewById(R.id.am_title);
                mTitleText.setText(getString(R.string.website_select_count, count));
                mode.setCustomView(localview);

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.menu_del:
                        //ArrayList<String> delIDs = new ArrayList<String>();
                        log("click del menu");
                        int tmp = 0;

                        for (int i = 0; i < list.size(); i++) {
                            if (list.get(i).isChecked()) {
                                //delIDs.add(list.get(i).getId());
                                tmp += 1;
                                log("click del menu, and del " + list.get(i));
                                ChildMode.removeUrlList(getContentResolver(), list.get(i).getWebsite());
                            }
                        }
                        log("click del menu, and del count=" + tmp);
                        loadData();
                        mode.finish();
                        break;
                    case R.id.menu_selectAll:
                        log("click selectAll menu");
                        listview.clearChoices();
                        count = 0;
                        for (int i = 0; i < listview.getCount(); i++) {
                            listview.setItemChecked(i, true);
                        }
                        break;
                    //begin:add by chensuyu for add cancel menu [SW00115217] 20150212
                    case R.id.menu_cancel:
                        log("click cancel menu");
                        mode.finish();
                        break;
                    //end:add by chensuyu for add cancel menu [SW00115217] 20150212
                }
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                count = 0;
                mTitleText.setText(getString(R.string.website_select_count, count));
                resetView();
            }
        });
    }//end onCreate()

    private void loadData() {
        List<String[]> result = ChildMode.getWebWhiteList(getContentResolver());
        if (null != result) {
            list.clear();
            //begin:modified by chensuyu for resolve APR:ArrayIndexOutOfBoundsException[SW00118993] 20150309
            try {
                for (int i = 0; i < result.size(); i++) {
                    WebSiteBean bean = new WebSiteBean();
                    bean.setName(result.get(i)[0]);
                    bean.setWebsite(result.get(i)[1]);
                    bean.setId(result.get(i)[2]);
                    list.add(bean);
                }
            } catch (Exception e)  {
                e.printStackTrace();
                log("loadData: Exception:" + e.getMessage());
            }
            //end:modified by chensuyu for resolve APR:ArrayIndexOutOfBoundsException[SW00118993] 20150309

            adapter.notifyDataSetChanged();
        }
    }

    //modified by machao 20150430 check lock state when starting this activity from recently apps -begin-
    @Override
    protected void onResume() {
        super.onResume();
        loadData();
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

    private void resetView() {
        itemClickIndex = INVALID;
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setChecked(false);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.website_list_menu,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.addNew:
                int themeId = getResources().getIdentifier("androidhwext:style/Theme.Emui.Dialog.Alert", null, null);
                AlertDialog.Builder builder = new AlertDialog.Builder(this, themeId);
                builder.setTitle(R.string.add_url_dialog_title);
                builder.setItems(getResources().getStringArray(R.array.childmode_add_url), this);
                builder.create().show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    //implement AlertDialog.OnClickListener
    @Override
    public void onClick(DialogInterface dialog, int which) {
        //implements click event of Add URL dialog item
        switch (which) {
            case DIALOG_ITEM_BOOKMARK_INDEX:
                log("choice bookmark");
                Intent bookmark = new Intent(this, BookmarkAndHistoryListActivity.class);
                bookmark.putExtra("website_add_from_type", WEBSITE_FORM_BOOKMARK);
                startActivity(bookmark);
                break;
            case DIALOG_ITEM_HISTORY_INDEX:
                log("choice history");
                Intent history = new Intent(this, BookmarkAndHistoryListActivity.class);
                history.putExtra("website_add_from_type", WEBSITE_FORM_HISTORY);
                startActivity(history);
                break;
            case DIALOG_ITEM_CUSTOM_INDEX:
                log("choice custom");
                showEditURLdialog(FLAG_NEW_WEBSITE, -1);
                break;
            default:
                break;
        }
    }

    private void showEditURLdialog(final int flag, final int position) {
        String title = "";
        if (flag == FLAG_NEW_WEBSITE) {
            title = getString(R.string.addNew_website_dialog_title);
        } else {
            title = getString(R.string.edit_website_dialog_title);
        }

        int themeId = getResources().getIdentifier("androidhwext:style/Theme.Emui.Dialog.Alert", null, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this, themeId);
        builder.setTitle(title);
        View view = getLayoutInflater().inflate(R.layout.website_edit_dialog, null);
        final EditText name =  (EditText)view.findViewById(R.id.name);
        final EditText website = (EditText)view.findViewById(R.id.website);

        if (flag == FLAG_EDIT_WEBSITE) {
            name.setText(list.get(position).getName());
            website.setText(list.get(position).getWebsite());
        }

        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String nameValue = name.getText().toString();
                String websiteValue = website.getText().toString();
                log("user click ok, nameValue=" + nameValue + ", websiteValue=" + websiteValue);
                if (flag == FLAG_NEW_WEBSITE) {
                    //add new item
                    if (!TextUtils.isEmpty(nameValue) && !TextUtils.isEmpty(websiteValue)) {
                        Uri uri = ChildMode.addUrlList(getContentResolver(), nameValue, websiteValue);
                        if(uri==null)
                        {
                         log("uri=" + uri);
                         return;
                        }
                        else
                        {
                        String id = uri.getLastPathSegment();
                        WebSiteBean bean = new WebSiteBean();
                        bean.setName(nameValue);
                        bean.setWebsite(websiteValue);
                        bean.setId(id);
                        list.add(bean);
                        log("new website success, id=" + id);
                        }
                    } else {
                        //begin:add by chensuyu for add toast [SW00118691] 20150306
                        Toast.makeText(WebSiteListActivity.this, R.string.content_empty, Toast.LENGTH_SHORT).show();
                        //end:add by chensuyu for add toast [SW00118691] 20150306
                        log("new website, name or website is empty");
                    }
                } else {
                    //edit item
                    if (!TextUtils.isEmpty(nameValue) && !TextUtils.isEmpty(websiteValue)) {
                        list.get(position).setName(nameValue);
                        list.get(position).setWebsite(websiteValue);

                        int count = ChildMode.updateUrlList(getContentResolver(), nameValue, websiteValue,
                                list.get(position).getId());
                        log("update website success, count=" + count);
                    } else {
                        //begin:add by chensuyu for add toast [SW00118691] 20150306
                        Toast.makeText(WebSiteListActivity.this, R.string.content_empty, Toast.LENGTH_SHORT).show();
                        //end:add by chensuyu for add toast [SW00118691] 20150306
                        log("update website, name or website is empty");
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builder.create().show();
    }

    private class WhiteListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            if (list.size() > 0) {
                return list.get(position);
            } else {
                return null;
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (null == convertView) {
                holder = new ViewHolder();
                convertView = getLayoutInflater().inflate(R.layout.website_item, null);
                holder.index = (TextView) convertView.findViewById(R.id.index);
                holder.name = (TextView) convertView.findViewById(R.id.name);
                holder.website = (TextView) convertView.findViewById(R.id.website);
                holder.op = (LinearLayout) convertView.findViewById(R.id.op);
                holder.del = (Button) convertView.findViewById(R.id.del);
                holder.edit = (Button) convertView.findViewById(R.id.edit);
                holder.checkBox = (CheckBox) convertView.findViewById(R.id.checkbox);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
                //resolve convertview cache problem that many item is visible
                holder.op.setVisibility(View.GONE);
                holder.checkBox.setVisibility(View.GONE);
            }

            //resolve convertview cache with item not visible when this item go out of screen and back to in screen again.
            if (itemClickIndex == position) {
                //display del and edit button
                holder.op.setVisibility(View.VISIBLE);
                holder.checkBox.setVisibility(View.GONE);
            } else if (itemClickIndex == ALL_CHECKBOX_DISPLAY) {
                //display all checkbox button
                holder.op.setVisibility(View.GONE);
                holder.checkBox.setVisibility(View.VISIBLE);
                //holder.checkBox.requestFocus();
                //holder.checkBox.setClickable(true);
            } else {
                holder.op.setVisibility(View.GONE);
                holder.checkBox.setVisibility(View.GONE);
                holder.checkBox.setClickable(false);
            }

            holder.index.setText("" + (1 + position));
            holder.name.setText(list.get(position).getName());
            holder.website.setText(list.get(position).getWebsite());
            holder.checkBox.setChecked(list.get(position).isChecked());


            //del item
            holder.del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    itemClickIndex = INVALID;
                    int count = ChildMode.removeUrlList(getContentResolver(), list.get(position).getWebsite());
                    log("user click del button, del " + list.get(position));
                    list.remove(position);
                    notifyDataSetChanged();
                }
            });

            //edit item
            holder.edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    itemClickIndex = INVALID;
                    notifyDataSetChanged();
                    showEditURLdialog(FLAG_EDIT_WEBSITE, position);
                }
            });
            return convertView;
        }

        private class ViewHolder {
            TextView index;
            TextView name;
            TextView website;
            Button del;
            Button edit;
            LinearLayout op;
            CheckBox checkBox;
        }
    }//end WhitelistAdapter

    private void log(String msg) {
        if (DEBUG) {
            Log.d("WebSiteListActivity", msg);
        }
    }
}
