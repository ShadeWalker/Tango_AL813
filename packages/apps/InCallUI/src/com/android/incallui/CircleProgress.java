package com.android.incallui;
import java.util.Timer;
import java.util.TimerTask;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Paint.Style;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * 圆形ImageView，可设置最多两个宽度不同且颜色不同的圆形边框。
 * 设置颜色在xml布局文件中由自定义属性配置参数指定
 */
public class CircleProgress extends ImageView {
	private int mBorderThickness = 0;
	private Context mContext;
	private int defaultColor = 0xFFFFFFFF;
	// 如果只有其中一个有值，则只画一个圆形边框
	private int mBorderOutsideColor = 0;
	private int mBorderInsideColor = 0;
	// 控件默认长、宽
	private int defaultWidth = 0;
	private int defaultHeight = 0;
	
	private int maxProgress = 100;
	private int progress = 15;
	private int progressStrokeWidth = 1;
	private int marxArcStorkeWidth = 6;
	
	 private int mMaxProgress;							// 进度条最大值	 	 																												
	 private int mMainCurProgress;						// 主进度条当前值 
	
	 private CartoomEngine mCartoomEngine;				// 动画引擎
	// 画圆所在的距形区域
	RectF oval;
	RectF circle;
	
	Paint paint;

	public CircleProgress(Context context) {
		super(context);
		defaultParam();
		mContext = context;
	}

	public CircleProgress(Context context, AttributeSet attrs) {
		super(context, attrs);
		defaultParam();
		mContext = context;
		oval = new RectF();
		circle = new RectF();
		paint = new Paint();
		setCustomAttributes(attrs);
	}

	public CircleProgress(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		defaultParam();
		mContext = context;
		setCustomAttributes(attrs);
	}
	
	private void defaultParam()
	{ 
		mCartoomEngine = new CartoomEngine();
		
		mMaxProgress = 60;								 																												
		mMainCurProgress = 0;										
		 
	}

	private void setCustomAttributes(AttributeSet attrs) {
		TypedArray a = mContext.obtainStyledAttributes(attrs,R.styleable.roundedimageview);
		mBorderThickness = a.getDimensionPixelSize(R.styleable.roundedimageview_border_thickness, 0);
		mBorderOutsideColor = a.getColor(R.styleable.roundedimageview_border_outside_color,defaultColor);
		mBorderInsideColor = a.getColor(R.styleable.roundedimageview_border_inside_color, defaultColor);
		
	}

	@Override
	protected void onDraw(Canvas canvas) {
		Drawable drawable = getDrawable() ; 
		
		int width = this.getWidth();
		int height = this.getHeight();

		width = (width > height) ? height : width;
		height = (width > height) ? height : width;

		paint.setAntiAlias(true); // 设置画笔为抗锯齿
		paint.setColor(Color.WHITE); // 设置画笔颜色
		canvas.drawColor(Color.TRANSPARENT); // 白色背景
		paint.setStrokeWidth(progressStrokeWidth); // 线宽
		paint.setStyle(Style.STROKE);
		
		circle.left = 30;
		circle.top = 30;
		circle.right=width-30;
		circle.bottom=height-30;

		canvas.drawArc(circle, -70, 320, false, paint); // 绘制白色圆圈，即进度条背景
		paint.setColor(Color.rgb(0x57, 0x87, 0xb6));
		paint.setStrokeWidth(marxArcStorkeWidth);
		
		canvas.drawArc(circle, -70, ((float) mMainCurProgress / mMaxProgress) * 320,
				false, paint); // 绘制进度圆弧，这里是蓝色
	}
	
	public int getMaxProgress() {
		return maxProgress;
	}

	public void setMaxProgress(int maxProgress) {
		this.maxProgress = maxProgress;
	}
	
	/*
	 * 设置主进度值
	 */
	 public synchronized void setMainProgress (int progress)
	 {
	    	mMainCurProgress = progress;
	    	if (mMainCurProgress < 0)
	    	{
	    		mMainCurProgress = 0;
	    	}
	    	
	    	if (mMainCurProgress > mMaxProgress)
	    	{
	    		mMainCurProgress = mMaxProgress;
	    	}
	    	
	    	invalidate();
	}
	    
    public synchronized int getMainProgress()
    {
    	return mMainCurProgress;
    }
    
    /*
     * 开启动画
     */
	public  void  startCartoom(int time)
	{
		mCartoomEngine.startCartoom(time);

	}
	/*
	 * 结束动画
	 */
	public  void  stopCartoom()
	{
		mCartoomEngine.stopCartoom();
	}
	
	
	class CartoomEngine
	{
		public Handler mHandler; 
		public boolean mBCartoom;					// 是否正在作动画 
		public Timer   mTimer;						// 用于作动画的TIMER 
		public		 MyTimerTask	mTimerTask;			// 动画任务
//		public int 	 mSaveMax;						// 在作动画时会临时改变MAX值，该变量用于保存值以便恢复	 
		public int     mTimerInterval;				// 定时器触发间隔时间(ms)	 
		public int   mCurFloatProcess;			// 作动画时当前进度值 

		private long timeMil;
		
		public CartoomEngine()
		{
			mHandler = new Handler()
			{

				@Override
				public void handleMessage(Message msg) {
					// TODO Auto-generated method stub
					switch(msg.what)
					{
						case TIMER_ID:
						{
							if (mBCartoom == false)
							{
								return ;
							}
							
						
							mCurFloatProcess += 1;
							setMainProgress(mCurFloatProcess);
							
							long curtimeMil = System.currentTimeMillis();
									
							
							timeMil = curtimeMil;
							
							if (mCurFloatProcess >= mMaxProgress)
							{
								//stopCartoom();
								mCurFloatProcess=0;
							}
						}
						break;
					}
				}
				
			};
			
			mBCartoom = false;
			mTimerInterval = 1000;
			mCurFloatProcess = 0;
			
		}
		
		public synchronized void  startCartoom(int time)
		{
			int setTime = time%mMaxProgress;
			if (mBCartoom == true)
			{ 
			    setTime = time%mMaxProgress;
			    mCurFloatProcess = setTime;
				return ;
			}
			
			timeMil = 0;
			
			mBCartoom = true;

			//setMainProgress(setTime);

			mMaxProgress = 60;
			mCurFloatProcess = setTime;
			mTimer = new Timer();
			mTimerTask = new MyTimerTask();
			mTimer.schedule(mTimerTask, 0, mTimerInterval);
		}
		
		public synchronized void  stopCartoom()
		{

			if (mBCartoom == false)
			{
				return ;
			}
			
			mBCartoom = false;
			
			setMainProgress(0);
			if (mTimer != null)
			{
				mTimer.cancel();
				mTimer = null;
			}
			if (mTimerTask != null)
			{
				mTimerTask.cancel();
				mTimerTask = null;
			}
		}
		
		private final static int TIMER_ID = 0x0010;
		
		class MyTimerTask extends TimerTask{

			@Override
			public void run() {
				// TODO Auto-generated method stub
				Message msg = mHandler.obtainMessage(TIMER_ID);
				msg.sendToTarget();
			}
		}
	}
}

