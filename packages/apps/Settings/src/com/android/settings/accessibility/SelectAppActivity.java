package com.android.settings.accessibility;
import android.R.integer;
import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import android.content.IntentFilter;

import android.provider.ChildMode;
import android.provider.Settings;

import com.android.settings.R;
import com.android.settings.childsecurity.ChooseChildLockHelper;

public class SelectAppActivity extends Activity
  implements View.OnClickListener
{
  protected static final int MSG_ADD = 101;
  public static final int REFRESH = 1;
  private appAdapter adapter;
  private boolean isAnimation = false;
  public List<ApplicationInfo> listData = new ArrayList();
  private ListView listView;
  private AsyncTask<Void, Void, ArrayList<ApplicationInfo>> loadTask;
  private Handler mHandler = new Handler()
  {
    public void handleMessage(Message paramMessage)
    {
        SelectAppActivity.this.progressBar.setVisibility(8);
    }
  };
  private ProgressBar progressBar;

  private void loadData()
  {
    if (this.loadTask == null){
      this.loadTask = new AsyncTask<Void, Void, ArrayList<ApplicationInfo>>()
      {
          @Override
        protected ArrayList<ApplicationInfo> doInBackground(Void[] paramArrayOfVoid)
        {
          List localArrayList1 = new ArrayList<String>();
          //localArrayList1 = ChildMode.getAppBlackList(SelectAppActivity.this.getContentResolver());
          Cursor localCursor = SelectAppActivity.this.getContentResolver().query(ChildMode.APP_CONTENT_URI, null, null, null, null);
          if (localCursor != null)
            try
            {
              int k = localCursor.getColumnIndex("package_name");
              while (localCursor.moveToNext())
                localArrayList1.add(localCursor.getString(k));
            }
            finally
            {
              localCursor.close();
            }
          String[] arrayOfString = new String[]{"com.huawei.camera","com.android.systemui"};
          ArrayList localArrayList2 = new ArrayList();
          if (arrayOfString != null)
          {
            int i = arrayOfString.length;
            for (int j = 0; j < i; j++)
              localArrayList2.add(arrayOfString[j]);
          }
          
          Intent localIntent = new Intent("android.intent.action.MAIN", null);
          PackageManager localPackageManager = SelectAppActivity.this.getPackageManager();
          localIntent.addCategory("android.intent.category.LAUNCHER");
          List localList = localPackageManager.queryIntentActivities(localIntent, 0);
          HashSet localHashSet = new HashSet();
          Iterator localIterator1 = localList.iterator();
          while (localIterator1.hasNext()){
            localHashSet.add(((ResolveInfo)localIterator1.next()).activityInfo.packageName);
          }
          ArrayList localArrayList3 = new ArrayList();
          Iterator localIterator2 = localHashSet.iterator();
          while (localIterator2.hasNext()){
              String str = (String)localIterator2.next();
              if (((SelectAppActivity.this.getComponentName() != null)
                      && (SelectAppActivity.this.getComponentName().getPackageName() != null) 
                      && (SelectAppActivity.this.getComponentName().getPackageName().equals(str)))
                      || (localArrayList1.contains(str)) || ((localArrayList2 != null) && (localArrayList2.contains(str))))
                continue;
              Object localObject2 = null;
              try
              {
                ApplicationInfo localApplicationInfo2 = localPackageManager.getApplicationInfo(str, 0);
                localObject2 = localApplicationInfo2;
                if (localObject2 == null)
                  continue;
                localArrayList3.add(localObject2);
              }
              catch (PackageManager.NameNotFoundException localNameNotFoundException)
              {
                  localNameNotFoundException.printStackTrace();
              }
        }
//          if (SettingUtils.getChildModeApplimitSwitch(SelectAppActivity.this))
//          {
//            Object localObject1 = SelectAppActivity.this.getPackageManager().getInstalledApplications(512);
//            if (localObject1 == null)
//              localObject1 = new ArrayList();
//            Iterator localIterator3 = ((List)localObject1).iterator();
//            while (localIterator3.hasNext())
//            {
//              ApplicationInfo localApplicationInfo1 = (ApplicationInfo)localIterator3.next();
//              if (localApplicationInfo1.enabledSetting != 2)
//                continue;
//              localArrayList3.add(localApplicationInfo1);
//            }
//          }
          return (ArrayList<ApplicationInfo>)localArrayList3;
        }

        protected void onPostExecute(ArrayList<ApplicationInfo> paramArrayList)
        {
          SelectAppActivity.this.listData.clear();
          SelectAppActivity.this.listData.addAll(paramArrayList);
          if (SelectAppActivity.this.adapter != null)
            SelectAppActivity.this.adapter.notifyDataSetChanged();
          //SelectAppActivity.access$302(SelectAppActivity.this, null);
          SelectAppActivity.this.mHandler.sendEmptyMessage(1);
        }

      };
    }
    this.loadTask.execute(new Void[0]);

  }

  public void onClick(View paramView)
  {
    if (paramView.getId() == R.id.goback_butoon)
    {
      Intent localIntent = new Intent();
      localIntent.setClass(this, ManageAppsActivity.class);
      startActivity(localIntent);
      finish();
    }
  }

  protected void onCreate(Bundle paramBundle)
  {
    super.onCreate(paramBundle);
    setContentView(R.layout.selectapp_activity);
    mChooseChildLockHelper = new ChooseChildLockHelper(this);
    View localView = ((LayoutInflater)getSystemService("layout_inflater")).inflate(R.layout.apps_manager_actionbar, null);
    getActionBar().setDisplayOptions(16);
    getActionBar().setCustomView(localView, new ActionBar.LayoutParams(-2, -2, 17));
    ((TextView)localView.findViewById(R.id.title)).setText(R.string.add_app_title);
    localView.findViewById(R.id.goback_butoon).setVisibility(8);
    this.progressBar = ((ProgressBar)findViewById(R.id.widget196));
    this.progressBar.setVisibility(0);
    this.listView = ((ListView)findViewById(R.id.selectapp_listview));
    this.adapter = new appAdapter(this);
    this.listView.setAdapter(this.adapter);
    this.listView.setOnItemClickListener(new AdapterView.OnItemClickListener()
    {
      public void onItemClick(AdapterView<?> paramAdapterView, View paramView, int paramInt, long paramLong)
      {
        if (((paramView instanceof RelativeLayout)) && (!SelectAppActivity.this.isAnimation))
        {
          View localView = paramView.findViewById(R.id.appitem_name);
          if (localView != null)
          {
            Object localObject = localView.getTag();
            if ((localObject instanceof String))
            {
              String str = (String)localObject;
//              if (SettingUtils.getChildModeApplimitSwitch(SelectAppActivity.this))
//              {
//                SelectAppActivity.this.getPackageManager().setPackageEnabledSetting(str, false);
//                SettingUtils.killApp(str, SelectAppActivity.this);
//              }
              ContentValues localContentValues = new ContentValues();
              localContentValues.put("package_name", str);
              boolean isAppBlackListOn = ChildMode.isAppBlackListOn(getContentResolver());
              if (isAppBlackListOn && "com.huawei.flashlight".equals(str)) {
                  turnOffFlashLight();
              }
              SelectAppActivity.this.getContentResolver().insert(ChildMode.APP_CONTENT_URI, localContentValues);
              Intent i = new Intent("com.settings.childmode.appdisable.add");
              i.putExtra("package_name", str);
              sendBroadcast(i);
              ActivityManager am = (ActivityManager)getSystemService("activity");
              /*Modified By zhangjun kill app after add to black list(QL1701) SW00115017 2015-2-28*/

              if (isAppBlackListOn && !"com.android.systemui".equals(str)) {
                  am.forceStopPackage(str);
                  Log.d("selectAppActivity", "force stop " + str);
               }
            }
          }
          SelectAppActivity.this.removeListItem(paramView, paramInt);
        }
      }
    });
    loadData();
  }
  @Override
  protected void onResume() {
    super.onResume();
    if (needToConfirm) {
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
      protected void onActivityResult(int requestCode, int resultCode, Intent data) {
          super.onActivityResult(requestCode, resultCode, data);
          if (resultCode==Activity.RESULT_OK) {
              needToConfirm = false;
          }
      }
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
                    Log.i(ChooseChildLockHelper.TAG, "home key pressed");
                    needToConfirm = true;
                }
            }
        }
    };
//  public boolean onKeyDown(int paramInt, KeyEvent paramKeyEvent)
//  {
////    if (paramInt == 4)
////    {
////      Intent localIntent = new Intent();
////      localIntent.setClass(this, ManageAppsActivity.class);
////      startActivity(localIntent);
////      finish();
////    }
////    for (boolean bool = true; ; bool = super.onKeyDown(paramInt, paramKeyEvent))
//      return fa;
//  }

  protected void removeListItem(View paramView, int paramInt)
  {
    final int pos = paramInt;
    Animation localAnimation = AnimationUtils.loadAnimation(paramView.getContext(), R.anim.movetoleft);
    localAnimation.setAnimationListener(new Animation.AnimationListener()
    {
      public void onAnimationEnd(Animation paramAnimation)
      {
         try {
              SelectAppActivity.this.listData.remove(pos);
        } catch (IndexOutOfBoundsException e) {
        }

        SelectAppActivity.this.adapter.notifyDataSetChanged();
        isAnimation=false;
        //SelectAppActivity.access$102(SelectAppActivity.this, false);
      }

      public void onAnimationRepeat(Animation paramAnimation)
      {
      }

      public void onAnimationStart(Animation paramAnimation)
      {
          isAnimation = true;
        //SelectAppActivity.access$102(SelectAppActivity.this, true);
      }
    });
    paramView.startAnimation(localAnimation);
  }
  public void turnOffFlashLight(){
      Intent intent = new Intent("com.huawei.flashlight.action.FlashLightService");
      Bundle bundle = new Bundle();
      int op  = 0;;
      try {
          Settings.System.putInt(getContentResolver(),"flashlight_current_state", op);
      } catch (Exception e) {
          Log.d("SelectAppActivity", "sendFlashlight putInt  flashlight_current_state exception:" + op);
      }
      bundle.putInt("status", op);
      intent.putExtras(bundle);
      Log.d("SelectAppActivity", "sendFlashlight intent :" + intent);
      startService(intent);
}
  class appAdapter extends BaseAdapter
  {
    private LayoutInflater mInflater;

    public appAdapter(Context arg2)
    {
      super();
      this.mInflater = LayoutInflater.from(arg2);
    }

    public int getCount()
    {
      return SelectAppActivity.this.listData.size();
    }

    public Object getItem(int paramInt)
    {
      return SelectAppActivity.this.listData.get(paramInt);
    }

    public long getItemId(int paramInt)
    {
      return paramInt;
    }

//    public View getView(int paramInt, View paramView, ViewGroup paramViewGroup)
//    {
//      ViewHolder localViewHolder = new ViewHolder();
//      if ((paramView == null) || (paramView.getTag() == null) || (!(paramView.getTag() instanceof ViewHolder)))
//      {
//        paramView = this.mInflater.inflate(R.layout.selectapp_item, null);
//        localViewHolder.icon = ((ImageView)paramView.findViewById(R.id.appitem_icon));
//        localViewHolder.label = ((TextView)paramView.findViewById(R.id.appitem_name));
//        localViewHolder.appitem_islock = ((ImageView)paramView.findViewById(R.id.appitem_islock));
//        localViewHolder.appitem_islock.setVisibility(8);
//      }
//        ApplicationInfo localApplicationInfo = (ApplicationInfo)SelectAppActivity.this.listData.get(paramInt);
//        localViewHolder.label.setText(localApplicationInfo.loadLabel(SelectAppActivity.this.getPackageManager()));
//        localViewHolder.label.setTag(localApplicationInfo.packageName);
//        localViewHolder.appitem_islock.setImageResource(R.drawable.nolock);
//        localViewHolder.icon.setImageDrawable(localApplicationInfo.loadIcon(SelectAppActivity.this.getPackageManager()));
//        localViewHolder = (ViewHolder)paramView.getTag();
//
//      return paramView;
//    }
    public View getView(int paramInt, View paramView, ViewGroup paramViewGroup){
      ViewHolder localViewHolder = null;
      if (paramView == null)
      {
        localViewHolder = new ViewHolder();
        paramView = this.mInflater.inflate(R.layout.selectapp_item, null);
        localViewHolder.icon = ((ImageView)paramView.findViewById(R.id.appitem_icon));
        localViewHolder.label = ((TextView)paramView.findViewById(R.id.appitem_name));
        localViewHolder.appitem_islock = ((ImageView)paramView.findViewById(R.id.appitem_islock));
        localViewHolder.appitem_islock.setVisibility(8);
        paramView.setTag(localViewHolder);
      }else {
          localViewHolder = (ViewHolder)paramView.getTag();
      }
        ApplicationInfo localApplicationInfo = (ApplicationInfo)SelectAppActivity.this.listData.get(paramInt);
        localViewHolder.label.setText(localApplicationInfo.loadLabel(SelectAppActivity.this.getPackageManager()));
        localViewHolder.label.setTag(localApplicationInfo.packageName);
        //localViewHolder.appitem_islock.setImageResource(R.drawable.nolock);//delete by lihaizhou
        localViewHolder.icon.setImageDrawable(localApplicationInfo.loadIcon(SelectAppActivity.this.getPackageManager()));
      return paramView;
    }

    class ViewHolder
    {
      ImageView appitem_islock;
      ImageView icon;
      TextView label;
      ViewHolder()
      {
      }
    }
  }
}
