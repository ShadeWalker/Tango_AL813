/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.mediatek.dialer.activities;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ListFragment;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.Toast;

import com.android.dialer.R;
import com.mediatek.dialer.calllog.CallLogMultipleDeleteFragment;
import com.mediatek.dialer.list.DropMenu;
import com.mediatek.dialer.list.DropMenu.DropDownMenu;
import com.mediatek.dialer.util.LogUtils;
import com.mediatek.dialer.util.SmartBookUtils;

import com.cootek.smartdialer_plugin_oem.CooTekSmartdialerOemModule;
import com.cootek.smartdialer_plugin_oem.IServiceStateCallback;

/**
 * Displays a list of call log entries.
 */
public class CallLogMultipleDeleteActivity extends Activity implements IServiceStateCallback{
    private static final String TAG = "CallLogMultipleDeleteActivity";

    protected CallLogMultipleDeleteFragment mFragment;

    public StatusBarManager mStatusBarMgr;

    //the dropdown menu with "Select all" and "Deselect all"
    private DropDownMenu mSelectionMenu;
    private boolean mIsSelectedAll = false;
    private boolean mIsSelectedNone = true;
	private static CooTekSmartdialerOemModule csom;
    private static int ItemCount = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        log("onCreate()");
		//add by zhangjinqiang --start
		csom= new CooTekSmartdialerOemModule(getApplicationContext(),this);
		csom.setNetworkAccessible(true);
		//add by zjq end
        super.onCreate(savedInstanceState);
        /// M: ALPS01423405
        SmartBookUtils.setOrientationPortait(this);

        setContentView(R.layout.mtk_call_log_multiple_delete_activity);

        // Typing here goes to the dialer
        //setDefaultKeyMode(DEFAULT_KEYS_DIALER);

        mFragment = (CallLogMultipleDeleteFragment) getFragmentManager().findFragmentById(
                R.id.call_log_fragment);
        configureActionBar();
        updateSelectedItemsView(0);

    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // This action deletes all elements in the group from the call log.
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onResume() {
        super.onResume();

        // TODO check this method
        //SetIndicatorUtils.getInstance().showIndicator(true, this);
    }

    @Override
    protected void onPause() {
        // TODO check this method
        //SetIndicatorUtils.getInstance().showIndicator(false, this);
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    protected void onStopForSubClass() {
        super.onStop();
    }

    private void configureActionBar() {
        log("configureActionBar()");
        // Inflate a custom action bar that contains the "done" button for
        // multi-choice
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View customActionBarView = inflater.inflate(R.layout.mtk_call_log_multiple_delete_custom_action_bar, null);

        Button selectView = (Button) customActionBarView
                .findViewById(R.id.select_items);
        //HQ_wuruijun modify for HQ01391001 start
        /*selectView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSelectionMenu == null || !mSelectionMenu.isShown()) {
                    View parent = (View) v.getParent();
                    mSelectionMenu = updateSelectionMenu(parent);
                    mSelectionMenu.show();
                } else {
                    LogUtils.w(TAG, "mSelectionMenu is already showing, ignore this click");
                }
                return;
            }
        });*/
        //HQ_wuruijun modify end

        //dispaly the "OK" button.
        Button deleteView = (Button) customActionBarView
                .findViewById(R.id.delete);
        //display the "confirm" button
        Button confirmView = (Button) customActionBarView.findViewById(R.id.confirm);
        if (mIsSelectedNone) {
            // if there is no item selected, the "OK" button is disable.
            deleteView.setEnabled(false);
            confirmView.setEnabled(false);
            confirmView.setTextColor(Color.GRAY);
        } else {
            deleteView.setEnabled(true);
            confirmView.setEnabled(true);
            confirmView.setTextColor(Color.WHITE);
        }
        deleteView.setOnClickListener(getClickListenerOfActionBarOKButton());

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME
                            | ActionBar.DISPLAY_SHOW_TITLE);
            actionBar.setCustomView(customActionBarView);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        setActionBarView(customActionBarView);
    }

    public void updateSelectedItemsView(final int checkedItemsCount) {
        Button selectedItemsView = (Button) getActionBar().getCustomView().findViewById(R.id.select_items);
        if (selectedItemsView == null) {
            log("Load view resource error!");
            return;
        }
        //maheling 2016.3.11 LT测试问题单/意大利语/意大利_第五轮回归_C440B128_20160309
        String language = getResources().getConfiguration().locale.getLanguage();
        if ("it".equals(language) && checkedItemsCount >= 2){
            selectedItemsView.setText(getString(R.string.selected_item_more, checkedItemsCount));
        } else {
            selectedItemsView.setText(getString(R.string.selected_item_count, checkedItemsCount));
        }
        //maheling 2016.3.11 LT测试问题单/意大利语/意大利_第五轮回归_C440B128_20160309
        //if no item selected, the "OK" button is disable.
        Button optionView = (Button) getActionBar().getCustomView()
        .findViewById(R.id.delete);
        Button confirmView = (Button) getActionBar().getCustomView().findViewById(R.id.confirm);
        if (checkedItemsCount == 0) {
            optionView.setEnabled(false);
            confirmView.setEnabled(false);
            confirmView.setTextColor(Color.GRAY);
        } else {
            optionView.setEnabled(true);
            confirmView.setEnabled(true);
            confirmView.setTextColor(Color.WHITE);
        }
        /** M: Fix CR ALPS01677733. Disable the selected view if it has no data. @{ */
        if (mFragment.getItemCount() > 0) {
            selectedItemsView.setEnabled(true);
        } else {
            selectedItemsView.setEnabled(false);
        }
        /** @} */
    }

    private void log(final String log) {
        Log.i(TAG, log);
    }

    private void showDeleteDialog() {
        DeleteComfigDialog.newInstance().show(getFragmentManager(), "DeleteComfigDialog");
    }

    /**
     * add dropDown menu on the selectItems.The menu is "Select all" or "Deselect all"
     * @param customActionBarView
     * @return The updated DropDownMenu instance
     */
    //HQ_wuruijun modify for HQ01391001 start

    /*private DropDownMenu updateSelectionMenu(View customActionBarView) {
        DropMenu dropMenu = new DropMenu(this);
        // new and add a menu.
        DropDownMenu selectionMenu = dropMenu.addDropDownMenu((Button) customActionBarView
                .findViewById(R.id.select_items), R.menu.mtk_selection);
        // new and add a menu.
        Button selectView = (Button) customActionBarView
                .findViewById(R.id.select_items);
        selectView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSelectionMenu == null || !mSelectionMenu.isShown()) {
                    View parent = (View) v.getParent();
                    mSelectionMenu = updateSelectionMenu(parent);
                    mSelectionMenu.show();
                } else {
                    LogUtils.w(TAG, "mSelectionMenu is already showing, ignore this click");
                }
                return;
            }
        });
        MenuItem item = selectionMenu.findItem(R.id.action_select_all);
        mIsSelectedAll = mFragment.isAllSelected();
        // if select all items, the menu is "Deselect all"; else the menu is "Select all".
        if (mIsSelectedAll) {
            item.setChecked(true);
            item.setTitle(R.string.menu_select_none);
            // click the menu, deselect all items
            dropMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    configureActionBar();
                    mFragment.unSelectAllItems();
                    updateSelectedItemsView(0);
                    return false;
                }
            });
        } else {
            item.setChecked(false);
            item.setTitle(R.string.menu_select_all);
            //click the menu, select all items.
            dropMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    configureActionBar();
                    updateSelectedItemsView(mFragment.selectAllItems());
                    return false;
                }
            });
        }
        return selectionMenu;
    }*/
    //HQ_wuruijun modify end

    protected OnClickListener getClickListenerOfActionBarOKButton() {
        return new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFragment.getSelectedItemCount() == 0) {
                    Toast.makeText(v.getContext(), R.string.multichoice_no_select_alert,
                                 Toast.LENGTH_SHORT).show();
                  return;
              }
              ItemCount = mFragment.getSelectedItemCount(); 
              showDeleteDialog();
              return;
            }
        };
    }

    //HQ_wuruijun add for HQ01391001 start

    public MenuItem selectAll;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mtk_calllog_list_multichoice, menu);
        selectAll = menu.findItem(R.id.action_select_all);
        mIsSelectedAll = mFragment.isAllSelected();
        if (mIsSelectedAll){
            selectAll.setTitle(R.string.menu_select_none);
        }else{
            selectAll.setTitle(R.string.menu_select_all);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        mIsSelectedAll = mFragment.isAllSelected();
        if(mIsSelectedAll) {
            selectAll.setTitle(R.string.menu_select_none);
        }
        // if select all items, the menu is "Deselect all"; else the menu is "Select all".
        if (item.getItemId() == R.id.action_select_all) {
            if (mIsSelectedAll) {
                item.setTitle(R.string.menu_select_all);
                // click the menu, deselect all items
                configureActionBar();
                mFragment.unSelectAllItems();
                updateSelectedItemsView(0);
            } else {
                item.setTitle(R.string.menu_select_none);
                //click the menu, select all items.
                configureActionBar();
                updateSelectedItemsView(mFragment.selectAllItems());
            }
        }
        return super.onOptionsItemSelected(item);
    }
    //HQ_wuruijun add end

    /// M: for ALPS01375185 @{
    // amend it for querying all CallLog on choice interface
    public ListFragment getMultipleDeleteFragment() {
        return mFragment;
    }
    /// @}

    // amend it for action bar view on CallLogMultipleChoiceActivity interface
    protected void setActionBarView(View view) {
    }

    private void deleteSelectedCallItems() {
        if (mFragment != null) {
            mFragment.deleteSelectedCallItems();
            updateSelectedItemsView(0);
        }
    }

    public static class DeleteComfigDialog extends DialogFragment {
        static DeleteComfigDialog newInstance() {
            return new DeleteComfigDialog();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
        	 String title = this.getActivity().getResources().getString(R.string.deleteCallLogConfirmation_title);
             String message = this.getActivity().getResources().getString(R.string.deleteCallLogConfirmation_message);

             if(getResources().getConfiguration().locale.getCountry().equals("RU")){
             	if (ItemCount <= 1) {
                     title = this.getActivity().getResources().getString(R.string.deleteCallLogConfirmation_title_singular);
                     message = this.getActivity().getResources().getString(R.string.deleteCallLogConfirmation_message_singular);
                 } else {
                 	 title = this.getActivity().getResources().getString(R.string.deleteCallLogConfirmation_title);
                      message = this.getActivity().getResources().getString(R.string.deleteCallLogConfirmation_message);
                 }
             }
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                        .setTitle(title)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setMessage(message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    if (getActivity() != null) {
                                        ((CallLogMultipleDeleteActivity) getActivity())
                                                .deleteSelectedCallItems();
                                    }
                                }
                            });
            return builder.create();
        }
    }

	//add by zhangjinqiang start
		@Override
	public void onServiceConnected() {
//		Toast.makeText(PeopleActivity.this, "号码助手Service通信成功！", Toast.LENGTH_SHORT).show();  
	}
	
	@Override
	public void onServiceDisconnected() {
		//Toast.makeText(PeopleActivity.this, "号码助手Service通信连接失败！！", Toast.LENGTH_LONG).show(); 
	}
	public static CooTekSmartdialerOemModule getCooTekSDK(){
		return csom;
	}
	//add by zjq end
}
