package com.android.dialer.dialpad;

import android.widget.TextView;
import android.content.Context;
import android.util.AttributeSet;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

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
			Drawable drawableRight = drawables[2];
            if (drawableLeft != null) {
                float textWidth = getPaint().measureText(getText().toString());
                int drawablePadding = getCompoundDrawablePadding();
                int drawableWidth = 0;
                drawableWidth = drawableLeft.getIntrinsicWidth();
                float bodyWidth = textWidth + drawableWidth + drawablePadding;
				
				// / Modified by guofeiyao,there are so many bugs in the original codes,
				//   how terrible they are! :(
				//   Now,this is a hack for AL812
				//Log.e("DrawableCenterTextView","getWidth():"+getWidth()+" bodyWidth:"+bodyWidth);
				if ( 308 > bodyWidth ) {
				     if ( bodyWidth > getWidth() ) {
    				  	  bodyWidth = getWidth();
				     }
                //canvas.translate((getWidth() - bodyWidth) / 2 + 8, 0);
                
                float leftOffset = (getWidth() - bodyWidth) / 2 - 8;
				//Log.e("DrawableCenterTextView", "leftOffset:"+leftOffset);
				canvas.translate(leftOffset , 0);
				}
				// / End
            } else if ( null != drawableRight ) {
                // / Created by guofeiyao 2015/12/11
                // This is a hack for 813 right-hand mode
                float textWidth = getPaint().measureText(getText().toString());
                int drawablePadding = getCompoundDrawablePadding();
                int drawableWidth = 0;
                drawableWidth = drawableRight.getIntrinsicWidth();
                float bodyWidth = textWidth + drawableWidth + drawablePadding;
				
				Log.e("DrawableCenterTextView","getWidth():"+getWidth()+" bodyWidth:"+bodyWidth);
				if ( 308 > bodyWidth ) {
				     if ( bodyWidth > getWidth() ) {
    				  	  bodyWidth = getWidth();
				     }
                //canvas.translate((getWidth() - bodyWidth) / 2 + 8, 0);
                
                float rightOffset = -1*((getWidth() - bodyWidth) / 2 - 12);
				Log.e("DrawableCenterTextView", "rightOffset:"+rightOffset);
				canvas.translate(rightOffset , 0);
				}
			}
        }
        super.onDraw(canvas);
    }
}
