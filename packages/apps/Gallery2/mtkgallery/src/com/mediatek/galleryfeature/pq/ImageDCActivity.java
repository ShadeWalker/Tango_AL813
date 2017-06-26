package com.mediatek.galleryfeature.pq;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;

import com.android.gallery3d.R;
import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.galleryframework.util.MtkLog;

public class ImageDCActivity extends Activity {
    private final static String TAG = "MtkGallery2/ImageDC";
    private RadioGroup mGroup;
    private boolean mSupportDC;
    private Context mContext;
    private boolean mCurrentState;
    
    //added for image DC
    public static final int REQUEST_DC = 9;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        SharedPreferences sp = mContext.getSharedPreferences(ImageDC.DC,
                Context.MODE_PRIVATE);
        mCurrentState = sp.getBoolean(ImageDC.DCNAME, false);
        final Editor editor = sp.edit();
        setContentView(R.layout.m_image_dc);

        mGroup = (RadioGroup) findViewById(R.id.radioGroup1);
        mGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (R.id.m_dc_open == checkedId) {
                    ImageDC.setStatus(true);
                } else if (R.id.m_dc_close == checkedId) {
                    ImageDC.setStatus(false);
                }
                editor.putBoolean(ImageDC.DCNAME, ImageDC.getStatus());
                editor.commit();
                MtkLog.d(TAG, "R.id.m_dc_open="+R.id.m_dc_open+" R.id.m_dc_close="+R.id.m_dc_close+" checkedId="+checkedId
                        +" FeatureConfig.isOpenImageDC="+ImageDC.getStatus());
            }
        });
        RadioButton open = (RadioButton) findViewById(R.id.m_dc_open);
        RadioButton close = (RadioButton) findViewById(R.id.m_dc_close);

        if (ImageDC.getStatus()) {
            open.setChecked(true);
        } else {
            close.setChecked(true);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.m_pq_actionbar, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
        } else if (item.getItemId() == R.id.cancel) {
            ImageDC.setStatus(mCurrentState);
        } else if (item.getItemId() == R.id.save) {
        }
        SharedPreferences sp = mContext.getSharedPreferences(ImageDC.DC,
                Context.MODE_PRIVATE);
        final Editor editor = sp.edit();
        editor.putBoolean(ImageDC.DCNAME, ImageDC.getStatus());
        editor.commit();
        if (mCurrentState != ImageDC.getStatus()) {
            setResult(RESULT_OK);
        }
        finish();
        return true;
    }
    
    @Override 
    public void onResume() {
        super.onResume();
    }
    
    @Override 
    public void onPause() {
        super.onPause();
    }
    
    @Override 
    public void onDestroy() {
        super.onDestroy();
        if (mCurrentState != ImageDC.getStatus()) {
            setResult(RESULT_OK);
        }
    }
}
