package com.mediatek.systemui.qs.tiles;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.android.systemui.qs.QSTile.Icon;
import com.mediatek.systemui.ext.IconIdWrapper;

/**
 * M: Customize the QS Icon Wrapper.
 */
public class QsIconWrapper extends Icon {
    private final IconIdWrapper mIconWrapper;

    /**
     * Constructs a new QsIconWrapper with IconIdWrapper.
     *
     * @param iconWrapper A IconIdWrapper object
     */
    public QsIconWrapper(final IconIdWrapper iconWrapper) {
        this.mIconWrapper = iconWrapper;
    }

    @Override
    public Drawable getDrawable(Context context) {
        return mIconWrapper.getDrawable();
    }

    @Override
    public int hashCode() {
        return mIconWrapper.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return mIconWrapper.equals(o);
    }
}