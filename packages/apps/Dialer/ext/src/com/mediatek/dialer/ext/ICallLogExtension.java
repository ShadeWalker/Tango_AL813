package com.mediatek.dialer.ext;

import java.util.List;

import android.app.Activity;
import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.telecom.PhoneAccountHandle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.HorizontalScrollView;
import android.widget.ListView;

import com.android.dialer.calllog.ContactInfo;

public interface ICallLogExtension {

    /**
     * for op01 OP09
     * called when CallLogFragment Created, plug-in should do initializing work here
     * @param fragment CallLogFragment
     */
    void onCreateForCallLogFragment(Context context, ListFragment fragment);

    /**
     * for OP09
     * called when CallLogFragment onViewCreated
     * @param view
     * @param savedInstanceState
     */
    void onViewCreatedForCallLogFragment(View view, Bundle savedInstanceState);

    /**
     * for OP09 OP01
     * called when CallLogFragment onDestroy
     */
    void onDestroyForCallLogFragment();

    /**
     * for OP09
     * Called when CallLogFragment list item clicked, return true if click event is handled
     * by plug-in, otherwise return false
     * @param l ListView
     * @param v View
     * @param position clicked position
     * @param id row id
     * @return whether click event is handled
     */
    boolean onListItemClickForCallLogFragment(ListView l, View v, int position, long id);

    /**
     * for OP09
     * Called when CallLogFragment onCreateContextMenu
     * @param menu the ContextMenu
     * @param view the view contains th menu
     * @param menuInfo ContextMenuInfo
     * @return whether plug-in created own menu
     */
    boolean onCreateContextMenuForCallLogFragment(ContextMenu menu, View view, ContextMenuInfo menuInfo);

    /**
     * for OP09
     * called when CallLogFragment onContextItemSelected
     * @param item selected context menu item
     * @return whether the click event is handled by plug-in
     */
    boolean onContextItemSelectedForCallLogFragment(MenuItem item);

    /**
     * for OP09
     * called bind view in CallLogAdapter, plug-in should bind own Tag if needed
     * @param itemView the view binded
     * @param contactInfo  contact info for current item
     * @param callDetailIntent the intent host used when set tag
     */
    public void setListItemViewTagForCallLogAdapter(View itemView, ContactInfo contactInfo, Intent callDetailIntent);

    /**
     * for OP09
     * Called when CallLogAdapter bindview, before host bind view
     * @param context
     * @param contactInfo contact info for current item
     */
    public void bindViewPreForCallLogAdapter(Context context, ContactInfo contactInfo);

    /**
     *for OP09
     * get sim name by sim id
     *
     * @param simId from datebase
     * @param callDisplayName the default StringBuffer of display name plug-in should change it
     */
    public void updateSimDisplayNameById(int simId, StringBuffer callDisplayName);

    /**
     *for OP09
     * set account for call log list
     *
     * @param Context context
     * @param View view
     * @param PhoneAccountHandle phoneAccountHandle
     */
    public void setCallAccountForCallLogList(Context context, View view, PhoneAccountHandle phoneAccountHandle);

    /**
     * for OP09
     * get sim color drawable by sim id
     *
     * @param simId form datebases
     * @param simBackground simBackgroud[0] is the default value of sim color drawable, plugin should replace it
     */
    public void updateSimColorDrawable(int simId, Drawable[] simBackground);

    /**
     * for op01
     * called when host create menu, to add plug-in own menu here
     * @param menu
     * @param tabs the ViewPagerTabs used in activity
     * @param callLogAction callback plug-in need if things need to be done by host
     */
    void createCallLogMenu(Activity activity, Menu menu, HorizontalScrollView tabs,
            ICallLogAction callLogAction);

    /**
     * for op01
     * called when host prepare menu, prepare plug-in own menu here
     * @param menu the Menu Created
     */
    void prepareCallLogMenu(Menu menu);

    /**
     * for op01
     * called when call log query, plug-in should customize own query here
     * @param typeFiler current query type
     * @param builder the query selection Stringbuilder, modify to change query selection
     * @param selectionArgs the query selection args, modify to change query selection
     */
    void appendQuerySelection(int typeFiler, StringBuilder builder, List<String> selectionArgs);

    /**
     * for op01
     * called when home button in actionbar clicked
     * @param pagerAdapter the view pager adapter used in activity
     * @param menu the optionsmenu itmes
     * @return true if do not need further operation in host
     */
    boolean onHomeButtonClick(FragmentPagerAdapter pagerAdapter, MenuItem menu);

    /**
     * for op01
     * Called when calllog activity onBackPressed
     * @param pagerAdapter the view pager adapter used in activity
     * @param callLogAction callback plug-in need if things need to be done by host
     */
    void onBackPressed(FragmentPagerAdapter pagerAdapter, ICallLogAction callLogAction);

    /**
     * for op01
     * called when updating tab count
     * @param count
     * @return tab count
     */
    int getTabCount(int count);

    /**
     * for op01
     * @param savedInstanceState the save instance state
     * @param pagerAdapter the view pager adapter used in activity
     * @param tabs the ViewPagerTabs used in activity
     */
    void restoreFragments(Context context, Bundle savedInstanceState,
            FragmentPagerAdapter pagerAdapter, HorizontalScrollView tabs);

    /**
     * for op01
     * @param outState save state
     */
    void onSaveInstanceState(Bundle outState);

    /**
     * for op01
     * plug-in can callback to host through this interface to do specific things
     */
    public static interface ICallLogAction {
        void updateCallLogScreen();
        void processBackPressed();
    }
	/**
     * for op01
     * plug-in set position
     * @param position to set
     */
	public void setPosition(int position);

	/**
     * for op01
     * plug-in modify current position
     * @param position
     * @return get the position
     */
	public int getPosition(int position);

}
