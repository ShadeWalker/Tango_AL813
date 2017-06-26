package com.android.contacts.util;

import java.util.ArrayList;

import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;

import android.content.Context;
import android.view.ViewParent;

import com.android.contacts.common.widget.FloatingActionButtonControllerEM;

/*
 * Created by guofeiyao for HQ01207444 AL812
 */
public class ViewPagerListenersUtil implements ViewPager.OnPageChangeListener {
	private ArrayList<OnPageChangeListener> mOnPageChangeListeners = new ArrayList<OnPageChangeListener>();
    
	
	
	@Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageScrolled(position, positionOffset,
                    positionOffsetPixels);
        }
    }

    @Override
    public void onPageSelected(int position) {
        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageSelected(position);
        }
//        sendScreenViewForPosition(position);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageScrollStateChanged(state);
        }
    }
	
    public void addOnPageChangeListener(OnPageChangeListener onPageChangeListener) {
        if (!mOnPageChangeListeners.contains(onPageChangeListener)) {
            mOnPageChangeListeners.add(onPageChangeListener);
        }
    }
}
