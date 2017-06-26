package com.mediatek.galleryfeature.pq.adapter;

import java.util.ArrayList;
import java.util.HashMap;

import com.android.gallery3d.R;
import com.mediatek.galleryfeature.pq.PictureQualityActivity;
import com.mediatek.galleryfeature.pq.Representation;
import com.mediatek.galleryfeature.pq.dcfilter.DCFilter;
import com.mediatek.galleryfeature.pq.filter.Filter;
import com.mediatek.galleryfeature.pq.filter.FilterInterface;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.mediatek.galleryframework.util.MtkLog;

public class PQDataAdapter extends BaseAdapter {
    private static final String TAG = "MtkGallery2/PQDataAdapter";
    private ArrayList<FilterInterface> mData;
    private FilterInterface mFilters = null;
    private LayoutInflater mInflater;
    private HashMap<ViewHolder, Representation> mAllPresentation = new HashMap<ViewHolder, Representation>();
    private Context mContext;
    private ListView mListView;
    private String mUri;
    private FilterInterface mFilter;
    public final class ViewHolder {
        public RelativeLayout layout;
        public TextView left;
        public SeekBar seekbar;
        public TextView blow;
        public TextView right;
    }
    public PQDataAdapter(Context context, String uri) {
        mUri = uri;
        this.mInflater = LayoutInflater.from(context);
        if (uri != null) {
            mFilter = new Filter();
        } else {
            mFilter = new DCFilter();
        }

        mData = mFilter.getFilterList();
        mContext = context;
    }
    public int getCount() {
        // TODO Auto-generated method stub
        return mData.size();
    }

    public void setListView(ListView listView) {
        mListView = listView;
    }
    public Object getItem(int position) {
        // TODO Auto-generated method stub
        return null;
    }

    public long getItemId(int position) {
        // TODO Auto-generated method stub
        return 0;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
        RelativeLayout layout = null;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = mInflater.inflate(R.layout.m_pq_seekbar, null);
            holder.left = (TextView) convertView.findViewById(R.id.m_textViewMinValue);
            holder.seekbar = (SeekBar) convertView.findViewById(R.id.m_seekbar);
            holder.blow = (TextView) convertView.findViewById(R.id.m_textViewCurrentIndex);
            holder.right = (TextView) convertView.findViewById(R.id.m_textViewMaxValue);
            holder.layout = (RelativeLayout) convertView.findViewById(R.id.m_listitem);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        Representation presentaion = mAllPresentation.get(holder);
        if (presentaion == null) {
            presentaion = new Representation(mUri);
            mAllPresentation.put(holder, presentaion);
        }
        presentaion.init(holder, mData.get(position));
        setItemHeight(holder, mData.size());
        return convertView;
    }

    private void setItemHeight(ViewHolder holder, int count) {
        MtkLog.d(TAG, "<setItemHeight >setItemHeight~~~~~~~~~~~~~~~~~~~~~~~");
        /*if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {*/
        int height = ((Activity) mContext).getWindowManager().getDefaultDisplay().getHeight();
        int sceenHeigh = mListView.getHeight();
        if (sceenHeigh == 0) {
            sceenHeigh = ((Activity) mContext).getWindowManager().getDefaultDisplay().getHeight()
                            - ((PictureQualityActivity) mContext).getActionBarHeight();
        }
        int defaultItemH = ((PictureQualityActivity) mContext).getDefaultItemHeight();
        int itemHeight ;
        if (count * defaultItemH < sceenHeigh) {
            itemHeight = (sceenHeigh - ((PictureQualityActivity) mContext).getActionBarHeight()) / count;
        } else {
            itemHeight = defaultItemH - ((PictureQualityActivity) mContext).getActionBarHeight() / count;
        }
        holder.layout.setMinimumHeight(itemHeight);
        MtkLog.d(TAG, "<setItemHeight>params.height===" + height + "  itemHeight==" + itemHeight + "  sceenHeigh=" + sceenHeigh + " actionBarHeight="
                + ((PictureQualityActivity) mContext).getActionBarHeight());
    }

    public void restoreIndex() {
        int size = mData.size();
        for (int i = 0; i < size; i++) {
            FilterInterface data = mData.get(i);
            if (data != null) data.setIndex(data.getDefaultIndex());
        }
    }

    public void onResume() {
        mFilter.onResume();
    }

    public void onDestroy() {
        mFilter.onDestroy();
    }
}
