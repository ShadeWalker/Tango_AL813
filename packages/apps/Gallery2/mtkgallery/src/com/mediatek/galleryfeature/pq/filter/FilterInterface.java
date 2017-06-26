package com.mediatek.galleryfeature.pq.filter;

import java.util.ArrayList;

public interface FilterInterface {

     public void init();
     public String getMinValue();
     public String getMaxValue();
     public String getCurrentValue();
     public String getSeekbarProgressValue();
     public void setIndex(int index);
     public void setCurrentIndex(int progress);
     public ArrayList<FilterInterface> getFilterList();
     public void onResume();
     public void onDestroy();
     public int getDefaultIndex();
     public int getCurrentIndex();
     public int getRange();
}
