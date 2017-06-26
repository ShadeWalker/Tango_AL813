package com.mediatek.galleryfeature.pq;


import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryfeature.pq.PresentImage.RenderingRequestListener;
import com.mediatek.galleryfeature.pq.adapter.PQDataAdapter;

public class PictureQualityActivity extends Activity implements RenderingRequestListener {

    public static final String ACTION_PQ = "android.media.action.PQ";
    private static final String TAG = "MtkGallery2/PictureQualityActivity";
    private ImageView mImageView;
    public static final int ITEM_HEIGHT = 85;
    public static float mDensity = 1.0f;
    ListView mListView;
    PQDataAdapter mAdapter;
    private int mHeight;
    String mUri = null;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

        Bundle bundle = ((Activity) this).getIntent().getExtras();
        if (bundle != null) {
            mUri = bundle.getString("PQUri");
        }
        mHeight = getViewHeight();
        setContentView(R.layout.m_pq_main);

        mImageView = (ImageView) findViewById(R.id.m_imageview);
        mListView = (ListView) findViewById(R.id.m_getInfo);
        try {
            mAdapter = new PQDataAdapter((Context) this, mUri);
        } catch (java.lang.UnsatisfiedLinkError e) {
            MtkLog.d(TAG, "<onCreate> PictureQualityActivity onCreate issue!");
            Toast toast = Toast.makeText(this, "UnsatisfiedLinkError Please Check!!", 2000);
            toast.show();
            e.printStackTrace();
            finish();
        } catch (java.lang.NoClassDefFoundError e) {
            MtkLog.d(TAG, "<onCreate>PictureQualityActivity onCreate issue!");
            Toast toast = Toast.makeText(this, "NoClassDefFoundError Please Check!!", 2000);
            toast.show();
            e.printStackTrace();
            finish();
        }

        DisplayMetrics displayMetrics = new DisplayMetrics();
        this.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        mDensity = displayMetrics.density;
        MtkLog.d(TAG, "<onCreate>mDensity=" + mDensity);

    }
    int home ;
    int cancel;
    int save;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.m_pq_actionbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        } else if (item.getItemId() == R.id.cancel) {
            recoverIndex();
            finish();
        } else if (item.getItemId() == R.id.save) {
            finish();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        // send the back event to the top sub-state
        super.onBackPressed();
        recoverIndex();
    }

    @Override
    public void onResume() {
        super.onResume();
        mListView.setAdapter(mAdapter);
        if (mAdapter != null) {
            mAdapter.setListView(mListView);
            mAdapter.onResume();
        }
        setListViewHeightBasedOnChildren(mListView);
        PresentImage.getPresentImage().setListener(this, this);
        PresentImage.getPresentImage().loadBitmap(mUri);
    }

    @Override
    public void onPause() {
        super.onPause();
        PresentImage.getPresentImage().stopLoadBitmap();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAdapter != null) {
            mAdapter.onDestroy();
        }
        releseResource();
    }

    private void recoverIndex() {
        if (mAdapter != null) {
            mAdapter.restoreIndex();
        }
    }

    private void releseResource() {
        mImageView.setImageBitmap(null);
        PresentImage.getPresentImage().free();
    }
    public void setListViewHeightBasedOnChildren(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();

        if (listAdapter == null) {
            return;
        }
        int totalHeight = 0;

        for (int i = 0; i < listAdapter.getCount(); i++) {
            View list = listAdapter.getView(i, null, null);
            list.measure(0, 0);
            MtkLog.d(TAG, "<setListViewHeightBasedOnChildren>list.getMeasuredHeight()=" + list.getMeasuredHeight());
            totalHeight += list.getMeasuredHeight() + listView.getDividerHeight();
        }
        int start  = 0;
        int height = mHeight;

        start = getActionBarHeight();
        height = mHeight - 2 * getActionBarHeight();
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        ((MarginLayoutParams) params).setMargins(0, start, 0, 0);
        ((MarginLayoutParams) params).height = height;
        listView.setLayoutParams(params);
        MtkLog.d(TAG, "<setListViewHeightBasedOnChildren>mHeight=" + mHeight + " mActionBar.getHeight()=" + getActionBarHeight() + " start=" + start
                + "  height=" + height);

    }

    public boolean available(Bitmap bitmap, String uri) {
        // TODO Auto-generated method stub
        if (mUri != null && mUri == uri) {
            mImageView.setImageBitmap(bitmap);
            return true;
        } else {
            bitmap.recycle();
            bitmap = null;
            return false;
        }

    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        mHeight = getViewHeight();
        MtkLog.d(TAG, "<onConfigurationChanged>onConfigurationChanged  height=" + mHeight);
        if (mListView != null) {
            setListViewHeightBasedOnChildren(mListView);
        }

/*        if (mView != null) {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                mView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            } else {
                mView.setSystemUiVisibility(View.INVISIBLE);
            }
        }*/
        toggleStatusBarByOrientation();
    }
    public int getActionBarHeight() {
        int actionBarHeight = this.getActionBar().getHeight();
        if (actionBarHeight != 0)
            return actionBarHeight;
        final TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true))
            actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
        return actionBarHeight;
    }

    public int getViewHeight() {
        int height = 0;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            Rect rect = new Rect();
            Window mWindows = this.getWindow();
            if (mWindows != null) {
                mWindows.getDecorView().getWindowVisibleDisplayFrame(rect);
            }
            MtkLog.d(TAG, "<getViewHeight>rect.top==" + rect.top);
            height = this.getWindowManager().getDefaultDisplay().getHeight()
            - rect.top;
        } else {
            height = this.getWindowManager().getDefaultDisplay().getHeight();
        }
        return height;
    }
    private void toggleStatusBarByOrientation() {

        Window win = getWindow();
        if (null == win) return;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            win.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }
    public int getDefaultItemHeight() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            return (int) (ITEM_HEIGHT * mDensity);
        } else {
            return (int) (ITEM_HEIGHT * mDensity - 10);
        }
    }

}
