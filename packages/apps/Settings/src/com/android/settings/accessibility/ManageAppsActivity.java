package com.android.settings.accessibility;

import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import android.provider.ChildMode;
import com.android.settings.R;
import com.android.settings.childsecurity.ChooseChildLockHelper;
public class ManageAppsActivity extends Activity
  implements View.OnClickListener
{
  public static final int REFRESH = 1;
  protected static final int TOKEN_QUERY_DATA = 101;
  public static final int UNINSTALL_UPDATE = 2;
  private GridView gridView;
  private AsyncTask<Void, Void, Integer> mAppLimitTask;
  private CursorAdapter mCursorAdapter;
  private Button mDeleteSelectBtn;
  private ChooseChildLockHelper mChooseChildLockHelper;
  private static final int CONFIRM_EXISTING_FROM_RESUME = 131;
  boolean needToConfirm = false;
  private Handler mHandler = new Handler()
  {
    public void handleMessage(Message paramMessage)
    {
//      switch (paramMessage.what)
//      {
//      default:
//      case 1:
//      case 2:
//      }
//      while (true)
//      {
//        return;
//        ManageAppsActivity.this.refresh();
//        continue;
        ManageAppsActivity.this.mQueryhandler.startQuery(101, null, ChildMode.APP_CONTENT_URI, null, null, null, null);
        ManageAppsActivity.this.refresh();
//      }
    }
  };
 // private AppLimitProgressDialog mProgressDialog;
  private AsyncQueryHandler mQueryhandler;
  private final Map<Long, ApplicationInfo> mSelectedCursorItemStatusMap = new HashMap();
  private RelativeLayout top_title;

  private void deleteSelectApp()
  {
    ArrayList<String> removeList = new ArrayList<String>();
    StringBuilder localStringBuilder = new StringBuilder("_id in ");
    localStringBuilder.append("(");
    if (this.mSelectedCursorItemStatusMap.size() > 0)
    {
      Iterator localIterator = this.mSelectedCursorItemStatusMap.entrySet().iterator();
      Map.Entry localEntry1 = (Map.Entry)localIterator.next();
      Long localLong1 = (Long)localEntry1.getKey();
      localStringBuilder.append("'");
      localStringBuilder.append(String.valueOf(localLong1));
      localStringBuilder.append("'");
      //setAppEnableState((ApplicationInfo)localEntry1.getValue());
      removeList.add(((ApplicationInfo)localEntry1.getValue()).packageName);
      while (localIterator.hasNext())
      {
        Map.Entry localEntry2 = (Map.Entry)localIterator.next();
        Long localLong2 = (Long)localEntry2.getKey();
        localStringBuilder.append(",'");
        localStringBuilder.append(String.valueOf(localLong2));
        localStringBuilder.append("'");
       // setAppEnableState((ApplicationInfo)localEntry2.getValue());
        removeList.add(((ApplicationInfo)localEntry2.getValue()).packageName);
      }
    }
    localStringBuilder.append(")");
    getContentResolver().delete(ChildMode.APP_CONTENT_URI, localStringBuilder.toString(), null);
    Intent i = new Intent("com.settings.childmode.appdisable.remove");
    i.putStringArrayListExtra("package_list", removeList);
    sendBroadcast(i);
    this.mSelectedCursorItemStatusMap.clear();
    this.mQueryhandler.startQuery(101, null, ChildMode.APP_CONTENT_URI, null, null, null, null);
  }

  private void refresh()
  {
    if (this.mCursorAdapter.getCount() == 0)
      this.top_title.setVisibility(8);

      updateDelBtn();

      //this.top_title.setVisibility(0);

  }

//  private void setAppEnableState(ApplicationInfo paramApplicationInfo)
//  {
//    if (SettingUtils.getChildModeApplimitSwitch(this))
//      getPackageManager().setPackageEnabledSetting(paramApplicationInfo.packageName, true);
//  }

  private void updateDelBtn()
  {
    if (this.mSelectedCursorItemStatusMap.size() > 0)
      this.mDeleteSelectBtn.setEnabled(true);
    else
      this.mDeleteSelectBtn.setEnabled(false);
  }

  public void onClick(View paramView)
  {
    switch (paramView.getId())
    {
    case R.id.btn_AddNewapp:
      Intent localIntent = new Intent();
      localIntent.setClass(this, SelectAppActivity.class);
      startActivity(localIntent);
      //finish();
      break;
    case R.id.btn_Clearapp:
      AlertDialog.Builder localBuilder = new AlertDialog.Builder(this);
      localBuilder.setTitle(R.string.confirm_remove_from_blacklist);
      localBuilder.setNegativeButton(R.string.delete_cancel, null);
      localBuilder.setPositiveButton(R.string.delete_confirm, new DialogInterface.OnClickListener()
      {
        public void onClick(DialogInterface paramDialogInterface, int paramInt)
        {
//          ManageAppsActivity.access$502(ManageAppsActivity.this, new ManageAppsActivity.SetAppLimitTask(ManageAppsActivity.this));
//          ManageAppsActivity.this.mAppLimitTask.execute(new Void[0]);
           deleteSelectApp();
        }
      });
      localBuilder.create().show();
    }
  }

  protected void onCreate(Bundle paramBundle)
  {
    super.onCreate(paramBundle);
    setContentView(R.layout.apps_manager_activity);
    mChooseChildLockHelper = new ChooseChildLockHelper(this);
    View localView = ((LayoutInflater)getSystemService("layout_inflater")).inflate(R.layout.apps_manager_actionbar, null);
    getActionBar().setDisplayOptions(16);
    getActionBar().setCustomView(localView, new ActionBar.LayoutParams(-2, -2, 17));
    ((TextView)localView.findViewById(R.id.title)).setText(R.string.app_black_list_title);
    localView.findViewById(R.id.goback_butoon).setVisibility(8);
    findViewById(R.id.btn_AddNewapp).setOnClickListener(this);
    this.mDeleteSelectBtn = ((Button)findViewById(R.id.btn_Clearapp));
    this.mDeleteSelectBtn.setOnClickListener(this);
    this.mDeleteSelectBtn.setEnabled(false);
    this.top_title = ((RelativeLayout)findViewById(R.id.top_title));
    this.gridView = ((GridView)findViewById(R.id.gridview));
//    this.mProgressDialog = new AppLimitProgressDialog(this);
//    this.mProgressDialog.setTitle(2131361914);
    this.mQueryhandler = new AsyncQueryHandler(getContentResolver())
    {
      protected void onQueryComplete(int paramInt, Object paramObject, Cursor paramCursor)
      {
        if (paramInt == 101)
        {
          super.onQueryComplete(paramInt, paramObject, paramCursor);
          ManageAppsActivity.this.mCursorAdapter.changeCursor(paramCursor);
          ManageAppsActivity.this.refresh();
          ManageAppsActivity.this.mCursorAdapter.notifyDataSetChanged();
        }
      }
    };
    this.mCursorAdapter = new AppItemAdapter(this,null);
    this.gridView.setAdapter(this.mCursorAdapter);
    this.gridView.setOnItemClickListener(new AdapterView.OnItemClickListener()
    {
      public void onItemClick(AdapterView<?> paramAdapterView, View paramView, int paramInt, long paramLong)
      {
        new ManageAppsActivity.AppHolder();
        ManageAppsActivity.AppHolder localAppHolder = (ManageAppsActivity.AppHolder)paramView.getTag(R.id.applock_image);
        if (ManageAppsActivity.this.mSelectedCursorItemStatusMap.containsKey(Long.valueOf(paramLong)))
        {
          ManageAppsActivity.this.mSelectedCursorItemStatusMap.remove(Long.valueOf(paramLong));
          localAppHolder.mSelect.setVisibility(8);
        }else{
          ManageAppsActivity.this.updateDelBtn();
          ManageAppsActivity.this.mSelectedCursorItemStatusMap.put(Long.valueOf(paramLong), (ApplicationInfo)paramView.getTag(R.layout.priapp_item));
          localAppHolder.mSelect.setVisibility(0);
        }
        updateDelBtn();
      }
    });
    this.gridView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener()
    {
      public boolean onItemLongClick(AdapterView<?> paramAdapterView, View paramView, int paramInt, final long paramLong)
      {
        AlertDialog.Builder localBuilder = new AlertDialog.Builder(ManageAppsActivity.this);
        if ((paramView.getTag(R.layout.priapp_item) != null) && ((paramView.getTag(R.layout.priapp_item) instanceof ApplicationInfo)))
        {
          final ApplicationInfo localApplicationInfo = (ApplicationInfo)paramView.getTag(R.layout.priapp_item);
          localBuilder.setTitle(localApplicationInfo.loadLabel(ManageAppsActivity.this.getPackageManager()));
          localBuilder.setItems(new String[]{getString(R.string.remove_from_blacklist)}, new DialogInterface.OnClickListener(){
              @Override
              public void onClick(DialogInterface dialog, int which) {
                  //getPackageManager().setPackageEnabledSetting(localApplicationInfo.packageName, true);
				  ChildMode.removeAppList(getContentResolver(),localApplicationInfo.packageName);
	              Intent i = new Intent("com.settings.childmode.appdisable.remove");
	              i.putExtra("package_name", localApplicationInfo.packageName);
	              sendBroadcast(i);
                  if(ManageAppsActivity.this.mSelectedCursorItemStatusMap.containsKey(Long.valueOf(paramLong))) {
                      ManageAppsActivity.this.mSelectedCursorItemStatusMap.remove(Long.valueOf(paramLong));
                  }
                  ManageAppsActivity.this.refresh();
                  ManageAppsActivity.this.mQueryhandler.startQuery(101, null, ChildMode.APP_CONTENT_URI, null, null, null, null);
              }
          }).create().show();
        }
        return true;
      }
    });
  }
  @Override
    protected void onResume() {
      super.onResume();
      if (needToConfirm) {
          mChooseChildLockHelper.launchConfirmationActivity(CONFIRM_EXISTING_FROM_RESUME, null, null);  
      }
      mQueryhandler.startQuery(101, null, ChildMode.APP_CONTENT_URI, null, null, null, null);
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
  private BroadcastReceiver mHomeReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();
          if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
              String reason = intent.getStringExtra("reason");
              if ("homekey".equals(reason)) {
                  Log.i(ChooseChildLockHelper.TAG, "home key pressed");
                  needToConfirm = true;
              }
          }
      }
  };
  static class AppHolder
  {
    ImageView mIcon;
    TextView mLabel;
    ImageView mSelect;
  }

  class AppItemAdapter extends CursorAdapter
  {
    ManageAppsActivity.AppHolder mHolder;
    private LayoutInflater mInflater;
    private PackageManager mPackagemanager;

    public AppItemAdapter(Context context,Cursor c)
    {
      super(context,c,false);
      this.mInflater = LayoutInflater.from(context);
      this.mPackagemanager = context.getPackageManager();
    }

    public void bindView(View paramView, Context paramContext, Cursor paramCursor)
    {
      this.mHolder = ((ManageAppsActivity.AppHolder)paramView.getTag(R.id.applock_image));
      ApplicationInfo localObject = null;
      String str = paramCursor.getString(paramCursor.getColumnIndex("package_name"));
      try
      {
        ApplicationInfo localApplicationInfo = this.mPackagemanager.getApplicationInfo(str, 0);
        localObject = localApplicationInfo;
        if (localObject != null)
        {
          this.mHolder.mLabel.setText(localObject.loadLabel(ManageAppsActivity.this.getPackageManager()));
          this.mHolder.mIcon.setImageDrawable(localObject.loadIcon(ManageAppsActivity.this.getPackageManager()));
          this.mHolder.mSelect.setVisibility(View.GONE);
          if (ManageAppsActivity.this.mSelectedCursorItemStatusMap.containsKey(Long.valueOf(paramCursor.getLong(paramCursor.getColumnIndex("_id")))))
          {
            this.mHolder.mSelect.setVisibility(0);
          }
          paramView.setTag(R.layout.priapp_item, localObject);
          return;
        }
      }
      catch (PackageManager.NameNotFoundException localNameNotFoundException)
      {

          localNameNotFoundException.printStackTrace();
//          continue;
//          this.mHolder.mSelect.setVisibility(8);
//          continue;
          ChildMode.removeAppList(getContentResolver(),str);
          ManageAppsActivity.this.mHandler.sendEmptyMessage(2);
      }
    }

    public View newView(Context paramContext, Cursor paramCursor, ViewGroup paramViewGroup)
    {
      ManageAppsActivity.AppHolder localAppHolder = new ManageAppsActivity.AppHolder();
      View localView = this.mInflater.inflate(R.layout.priapp_item, null);
      localAppHolder.mIcon = ((ImageView)localView.findViewById(R.id.applock_image));
      localAppHolder.mLabel = ((TextView)localView.findViewById(R.id.applock_text));
      localAppHolder.mSelect = ((ImageView)localView.findViewById(R.id.applock_select));
      localView.setTag(R.id.applock_image, localAppHolder);
      return localView;
    }
  }

  class SetAppLimitTask extends AsyncTask<Void, Void, Integer>
  {
    SetAppLimitTask()
    {
    }

    protected Integer doInBackground(Void[] paramArrayOfVoid)
    {
      ManageAppsActivity.this.deleteSelectApp();
      return Integer.valueOf(0);
    }

    protected void onPostExecute(Integer paramInteger)
    {
      ManageAppsActivity.this.mHandler.sendEmptyMessage(1);
//      if ((ManageAppsActivity.this.mProgressDialog != null) && (ManageAppsActivity.this.mProgressDialog.isShowing()))
//          ManageAppsActivity.this.mProgressDialog.setDismiss();
    }
  }
}