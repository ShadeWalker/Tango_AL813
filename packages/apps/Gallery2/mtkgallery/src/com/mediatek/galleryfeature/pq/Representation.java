package com.mediatek.galleryfeature.pq;

import com.mediatek.galleryfeature.pq.adapter.PQDataAdapter.ViewHolder;
import com.mediatek.galleryfeature.pq.filter.FilterInterface;
import com.mediatek.galleryframework.util.MtkLog;
import android.widget.SeekBar;
import android.widget.TextView;

public class Representation implements SeekBar.OnSeekBarChangeListener {

    private static final String TAG = "MtkGallery2/Representation";
    TextView mMinValue;
    TextView mMaxValue;
    TextView mCurrentValue;
    SeekBar mController;
    private ViewHolder mHolder;
    private FilterInterface mFilter;
    private String mUri;

    public Representation(String uri) {
        mUri = uri;
    }

    public void init(ViewHolder holder, FilterInterface enhancement) {
        mHolder = holder;
        mFilter = enhancement;
        holder.left.setText(mFilter.getMinValue());
        holder.right.setText(mFilter.getMaxValue());
        holder.blow.setText(mFilter.getCurrentValue());
        holder.seekbar.setMax(mFilter.getRange() - 1);
        holder.seekbar.setProgress(Integer.parseInt(mFilter.getSeekbarProgressValue()));
        MtkLog.d(TAG, "<init>:: mFilter.getCurrentValue() = " + mFilter.getCurrentValue()
                + "  Integer.parseInt(mFilter.getSeekbarProgressValue())=" + Integer.parseInt(mFilter.getSeekbarProgressValue())
                + "  holder.seekbar Max===" + holder.seekbar.getMax());
        holder.seekbar.setOnSeekBarChangeListener(this);
    }

    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromUser) {
        // TODO Auto-generated method stub
        if (fromUser) {
            mFilter.setCurrentIndex(progress);
            mHolder.blow.setText(mFilter.getCurrentValue());
            if (mUri != null) {
                PresentImage.getPresentImage().loadBitmap(mUri);
            }
        }
    }
    public void onStartTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub

    }
    public void onStopTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub
        mFilter.setIndex(mFilter.getCurrentIndex());
        mHolder.blow.setText(mFilter.getCurrentValue());
    }



}
