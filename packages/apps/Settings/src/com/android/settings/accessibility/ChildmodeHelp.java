package com.android.settings.accessibility; 
  
import java.util.ArrayList;  
import java.util.List;  


import android.app.Activity;  
import android.graphics.BitmapFactory;  
import android.graphics.Matrix;  
import android.os.Bundle;  
import android.support.v4.view.PagerAdapter;  
import android.support.v4.view.ViewPager;  
import android.support.v4.view.ViewPager.OnPageChangeListener;  
import android.util.DisplayMetrics;  
import android.view.LayoutInflater;  
import android.view.View;  
import android.view.View.OnClickListener;  
import android.view.animation.Animation;  
import android.view.animation.TranslateAnimation;  
import android.view.ViewGroup;  
import android.widget.ImageView;  
import android.widget.TextView;  
import android.widget.Toast;  
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.TextUtils;

import android.widget.Button;
import com.android.settings.R;
import com.android.settings.Utils;
import android.util.Log;
  
public class ChildmodeHelp extends Activity {  
  
    private ViewPager viewPager;  
    private ImageView imageView;   
    private TextView textView1,textView2,textView3;  
    private List<View> views; 
    private int offset = 0; 
    private int currIndex = 0;   
    private int bmpW; 
    private Button mFinalButton;
    private View view1,view2,view3;  
    
    private Button.OnClickListener mFinalClickListener = new Button.OnClickListener() {

        public void onClick(View v) {
        	finish();
        }
    };
    @Override  
    protected void onCreate(Bundle savedInstanceState) {  
        super.onCreate(savedInstanceState);  
        setContentView(R.layout.weibochildmode);  
        InitViewPager();  
    }  
  
    private void InitViewPager() {  
        viewPager=(ViewPager) findViewById(R.id.vPager);  
        views=new ArrayList<View>();  
        LayoutInflater inflater=getLayoutInflater();  
        view1=inflater.inflate(R.layout.childmod1, null);  
        view2=inflater.inflate(R.layout.childmod2, null);  
        view3=inflater.inflate(R.layout.childmod3, null);  
        views.add(view1);  
        views.add(view2);  
        views.add(view3);  
        viewPager.setAdapter(new MyViewPagerAdapter(views));  
        viewPager.setCurrentItem(0);  
        viewPager.setOnPageChangeListener(new MyOnPageChangeListener());  
        mFinalButton = (Button) view3.findViewById(R.id.finish_button);
        mFinalButton.setOnClickListener(mFinalClickListener);
    }  

    private class MyOnClickListener implements OnClickListener{  
        private int index=0;  
        public MyOnClickListener(int i){  
            index=i;  
        }  
        public void onClick(View v) {  
            viewPager.setCurrentItem(index);              
        }  
          
    }  
      
    public class MyViewPagerAdapter extends PagerAdapter{  
        private List<View> mListViews;  
          
        public MyViewPagerAdapter(List<View> mListViews) {  
            this.mListViews = mListViews;  
        }  
  
        @Override  
        public void destroyItem(ViewGroup container, int position, Object object)   {     
            container.removeView(mListViews.get(position));  
        }  
  
  
        @Override  
        public Object instantiateItem(ViewGroup container, int position) {            
             container.addView(mListViews.get(position), 0);  
             return mListViews.get(position);  
        }  
  
        @Override  
        public int getCount() {           
            return  mListViews.size();  
        }  
          
        @Override  
        public boolean isViewFromObject(View arg0, Object arg1) {             
            return arg0==arg1;  
        }  
    }  
  
    public class MyOnPageChangeListener implements OnPageChangeListener{  
  
        int one = offset * 2 + bmpW;
        int two = one * 2;
        public void onPageScrollStateChanged(int arg0) {  
              
              
        }  
  
        public void onPageScrolled(int arg0, float arg1, int arg2) {  
              
              
        }  
  
        public void onPageSelected(int arg0) {  
            Animation animation = new TranslateAnimation(one*currIndex, one*arg0, 0, 0);   
            currIndex = arg0;  
            animation.setFillAfter(true);  
            animation.setDuration(300);  
        }  
          
    }  
}  
