//Add By chaoyongan to compose message ui (QL1700) SW00070146 2014-8-8
package com.android.mms.ui;

import android.widget.TextView;
import android.content.Context;
import android.util.AttributeSet;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
//HQ_zhangjing 2015-10-10 modified for CQ HQ01435191
import android.util.Log;

public class DrawableCenterTextView extends TextView {

    public DrawableCenterTextView(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
    }

    public DrawableCenterTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DrawableCenterTextView(Context context) {
        super(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable[] drawables = getCompoundDrawables();
        if (drawables != null) {
            Drawable drawableLeft = drawables[0];
            if (drawableLeft != null) {
                float textWidth = getPaint().measureText(getText().toString());
                int drawablePadding = getCompoundDrawablePadding();
                int drawableWidth = 0;
                drawableWidth = drawableLeft.getIntrinsicWidth();
                float bodyWidth = textWidth + drawableWidth + drawablePadding;
				/*HQ_zhangjing 2015-10-10 modified for CQ HQ01435191 begin */
				if( bodyWidth > getWidth() ){
					Log.d("onDraw","change the bodyWidth to getWidth()");
					bodyWidth = getWidth();
				}
                //canvas.translate((getWidth() - bodyWidth) / 2 - 6, 0);
				/*HQ_zhangjing 2015-10-10 modified for CQ HQ01435191 end */
				canvas.translate((getWidth() - bodyWidth) / 2 - 8, 0);
            }
        }
        super.onDraw(canvas);
    }
}