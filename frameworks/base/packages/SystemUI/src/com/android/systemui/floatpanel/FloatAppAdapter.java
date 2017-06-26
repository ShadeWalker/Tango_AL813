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
 * M:add for multi window
 */

package com.android.systemui.floatpanel;

import android.app.LauncherActivity.ListItem;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.android.systemui.R;

import com.mediatek.xlog.Xlog;

import java.util.List;

/**
 * M: The adapter for entrance bar and more area in multi-window entrance.
 */
public class FloatAppAdapter extends BaseAdapter {
    private static final String TAG = "FloatAppAdapter";
    private static final boolean DEBUG = false;

    private Context mContext;
    private final int mFloatContainer;

    private IconResizer mIconResizer;
    private LayoutInflater mInflater;

    private FloatModel mFloatModel;
    private List<FloatAppItem> mActivitiesList;

    public FloatAppAdapter(Context context, FloatModel model, int floatContainer) {
        mContext = context;
        mFloatModel = model;
        mIconResizer = new IconResizer(context);
        mInflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mFloatContainer = floatContainer;
        mActivitiesList = (floatContainer == FloatModel.RESIDENT_CONTAINER ? mFloatModel
                .getFloatApps() : mFloatModel.getEditApps());
        Xlog.d(TAG, "FloatAppAdapter construct: floatContainer = "
                + floatContainer + "mActivitiesList = " + mActivitiesList);
    }

    @Override
    public int getCount() {
        return mActivitiesList != null ? mActivitiesList.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return mActivitiesList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = mInflater.inflate(R.layout.float_panel_item, parent, false);
        }
        if(view!= null)
          bindView(view, position);
        return view;
    }

    private void bindView(View view, int position) {
        final FloatAppItem item = mActivitiesList.get(position);
        TextView appName = (TextView) view.findViewById(R.id.app_name);
        appName.setText(item.label);
        if (DEBUG) {
            Xlog.d(TAG, "bindView: position = " + position + ",item.visible = "
                    + item.visible + ", item = " + item + ", view = " + view);
        }

        if (item.icon == null) {
            item.icon = mIconResizer.createIconThumbnail(item.resolveInfo
                    .loadIcon(mContext.getPackageManager()));
        }
        appName.setCompoundDrawablesWithIntrinsicBounds(null, item.icon, null,
                null);

        if (item.visible) {
            view.setVisibility(View.VISIBLE);
        } else {
            if (DEBUG) {
                Xlog.d(TAG, "bindView set view invisible: position = "
                        + position + ", item = " + item + ", view = " + view);
            }
            view.setVisibility(View.INVISIBLE);
        }
        view.setTag(item);
    }

    /**
     * Add the given float app item to the specified position, add the item to
     * the tail if position is -1.
     * 
     * @param listItem
     * @param position
     */
    public void addItem(FloatAppItem item, int position) {
        Xlog.d(TAG, "addItem: position = " + position + ", item = " + item
                + ", size = " + mActivitiesList.size());
        if (position == -1) {
            position = mActivitiesList.size();
            mActivitiesList.add(item);
        } else {
            mActivitiesList.add(position, item);
        }
        updateItemInfoBetween(position, mActivitiesList.size());
        notifyDataSetChanged();
    }

    /**
     * Remove the item of the specified position.
     * 
     * @param position
     */
    public void removeItem(int position) {
        Xlog.d(TAG,
                "removeItem: position = " + position + ", item = "
                        + mActivitiesList.get(position) + ", size = "
                        + mActivitiesList.size());
        mActivitiesList.remove(position);
        updateItemInfoBetween(position, mActivitiesList.size());
        notifyDataSetChanged();
    }

    /**
     * Reorder the item of the "from position" to the "to position", all items
     * between the from and to will be moved forward/backward for one unit.
     * 
     * @param fromPosition
     * @param toPosition
     */
    public void reorder(int fromPosition, int toPosition) {
        FloatAppItem appItem = mActivitiesList.get(fromPosition);
        Xlog.d(TAG, "Reorder item: fromPosition = " + fromPosition
                + ", toPosition = " + toPosition + ", appItem = " + appItem
                + ", size = " + mActivitiesList.size());
        mActivitiesList.remove(fromPosition);
        mActivitiesList.add(toPosition, appItem);

        if (fromPosition > toPosition) {
            updateItemInfoBetween(toPosition, fromPosition + 1);
        } else {
            updateItemInfoBetween(fromPosition, toPosition + 1);
        }
        notifyDataSetChanged();
    }

    /**
     * Retrive the intent from the information of the given position.
     * 
     * @param position
     * @return
     */
    public Intent intentForPosition(int position) {
        if (mActivitiesList == null || mActivitiesList.size() <= position) {
            Xlog.d(TAG, "No intent for position(" + position
                    + "), list size is " + mActivitiesList.size());
            return null;
        }

        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        FloatAppItem item = mActivitiesList.get(position);
        intent.setClassName(item.packageName, item.className);
        intent.setPackage(item.packageName);
        if (item.extras != null) {
            intent.putExtras(item.extras);
        }
        return intent;
    }

    /**
     * Update items between the two position, be awared that the bound is
     * [fromPosition, toPosition).
     * 
     * @param fromPosition
     * @param toPosition
     */
    private void updateItemInfoBetween(int fromPosition, int toPosition) {
        Xlog.d(TAG, "updateItemInfoBetween: fromPosition = " + fromPosition
                + ", toPosition = " + toPosition + ", size = "
                + mActivitiesList.size());
        for (int i = fromPosition; i < toPosition; i++) {
            FloatAppItem item = mActivitiesList.get(i);
            item.position = mActivitiesList.indexOf(item);
            item.container = mFloatContainer;
            mFloatModel.addItemToModifyListIfNeeded(item);
        }
    }

    /**
     * Utility class to resize icons to match default icon size.
     */
    public class IconResizer {
        private Context mContext;

        private int mIconWidth = -1;
        private int mIconHeight = -1;

        private final Rect mOldBounds = new Rect();
        private Canvas mCanvas = new Canvas();

        public IconResizer(Context context) {
            mContext = context;
            mCanvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG,
                    Paint.FILTER_BITMAP_FLAG));

            final Resources resources = mContext.getResources();
            mIconWidth = (int) resources
                    .getDimension(android.R.dimen.app_icon_size);
            mIconHeight = mIconWidth;
        }

        /**
         * Returns a Drawable representing the thumbnail of the specified
         * Drawable. The size of the thumbnail is defined by the dimension
         * android.R.dimen.launcher_application_icon_size. This method is not
         * thread-safe and should be invoked on the UI thread only.
         * 
         * @param icon
         *            The icon to get a thumbnail of.
         * @return A thumbnail for the specified icon or the icon itself if the
         *         thumbnail could not be created.
         */
        public Drawable createIconThumbnail(Drawable icon) {
            int width = mIconWidth;
            int height = mIconHeight;

            final int iconWidth = icon.getIntrinsicWidth();
            final int iconHeight = icon.getIntrinsicHeight();

            if (icon instanceof PaintDrawable) {
                PaintDrawable painter = (PaintDrawable) icon;
                painter.setIntrinsicWidth(width);
                painter.setIntrinsicHeight(height);
            }
            Xlog.d(TAG,
                    "createIconThumbnail: iconWidth = " + iconWidth
                            + ", iconHeight = " + iconHeight
                            + ", mIconWidth = " + mIconWidth
                            + ", mIconHeight = " + mIconHeight + ", icon = "
                            + icon + ",icon.getOpacity() = "
                            + icon.getOpacity());

            if (width > 0 && height > 0) {
                if (width < iconWidth || height < iconHeight) {
                    final float ratio = (float) iconWidth / iconHeight;

                    if (iconWidth > iconHeight) {
                        height = (int) (width / ratio);
                    } else if (iconHeight > iconWidth) {
                        width = (int) (height * ratio);
                    }

                    final Bitmap.Config c = icon.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
                            : Bitmap.Config.RGB_565;
                    final Bitmap thumb = Bitmap.createBitmap(mIconWidth,
                            mIconHeight, c);
                    final Canvas canvas = mCanvas;
                    canvas.setBitmap(thumb);
                    // Copy the old bounds to restore them later
                    // If we were to do oldBounds = icon.getBounds(),
                    // the call to setBounds() that follows would
                    // change the same instance and we would lose the
                    // old bounds
                    mOldBounds.set(icon.getBounds());
                    final int x = (mIconWidth - width) / 2;
                    final int y = (mIconHeight - height) / 2;
                    icon.setBounds(x, y, x + width, y + height);
                    icon.draw(canvas);
                    icon.setBounds(mOldBounds);
                    icon = new BitmapDrawable(mContext.getResources(), thumb);
                    canvas.setBitmap(null);
                } else if (iconWidth < width && iconHeight < height) {
                    final Bitmap.Config c = Bitmap.Config.ARGB_8888;
                    final Bitmap thumb = Bitmap.createBitmap(mIconWidth,
                            mIconHeight, c);
                    final Canvas canvas = mCanvas;
                    canvas.setBitmap(thumb);
                    mOldBounds.set(icon.getBounds());
                    final int x = (width - iconWidth) / 2;
                    final int y = (height - iconHeight) / 2;
                    icon.setBounds(x, y, x + iconWidth, y + iconHeight);
                    icon.draw(canvas);
                    icon.setBounds(mOldBounds);
                    icon = new BitmapDrawable(mContext.getResources(), thumb);
                    canvas.setBitmap(null);
                }
            }

            return icon;
        }
    }

    public List<FloatAppItem> getActivityList() {
        return mActivitiesList;
    }
}
