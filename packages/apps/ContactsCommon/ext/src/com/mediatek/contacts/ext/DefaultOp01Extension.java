package com.mediatek.contacts.ext;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.widget.EditText;

public class DefaultOp01Extension implements IOp01Extension {
    //---------------------for Editor-------------------//
    /**
     * OP01 will filter phone number.
     * let the op01 set its own listener for phone number input.
     * set the key listener
     * @param fieldView the view to set listener
     */
    @Override
    public void setViewKeyListener(EditText fieldView){
        //do-nothing
    }

    //-----------------------------for Multi Choise------------------//
    /**
     * for op01 Host max count is 3500,OP01 will custom to 5000
     * ContactsMultiDeletionFragment.java
     * @return the max count in multi choice list
     */
    @Override
    public int getMultiChoiceLimitCount(int defaultCount){
      //default return defaultCount
        return defaultCount;
    }

    //--------------for PeopleActivity----------------//
    /**
     * for op01,add for "show sim Capacity" in people list
     * @param menu The menu to be add options, context Host context
     */
    @Override
    public void addOptionsMenu(Context context, Menu menu){
      //do-nothing
    }

    //--------------for SIMImportProcessor----------------//
    /**
     * Op01 will format Number, filter some char
     * @param number to be filter
     * @param bundle is intent data
     */
    @Override
    public String formatNumber(String defaultNumber, Bundle bundle){
        //default return defaultNumber
        return defaultNumber;
    }
}
