package com.android.incallui;
import java.util.Timer;
import java.util.TimerTask;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
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
import android.util.Log;
import android.widget.ImageView;

/**
 * 圆形ImageView，可设置最多两个宽度不同且颜色不同的圆形边框。
 * 设置颜色在xml布局文件中由自定义属性配置参数指定
 */
public class CircleView extends ImageView {
	private int mBorderThickness = 0;
	private Context mContext;
	private int defaultColor = 0xFFFFFFFF;
	// 如果只有其中一个有值，则只画一个圆形边框
	private int mBorderOutsideColor = 0;
	private int mBorderInsideColor = 0;
	// 控件默认长、宽
	private int defaultWidth = 0;
	private int defaultHeight = 0;
	
	private int width=0;
	private int height=0;
	
	private int maxProgress = 100;
	private int progress = 15;
	private int progressStrokeWidth = 1;
	private int marxArcStorkeWidth = 2;
	
	 private int mMaxProgress;							// 进度条最大值	 	 																												
	 private int mMainCurProgress;						// 主进度条当前值 
	
	 private CartoomEngine mCartoomEngine;				// 动画引擎
	// 画圆所在的距形区域
	RectF oval,locator;
	Paint paint;

	public CircleView(Context context) {
		super(context);
		defaultParam();
		mContext = context;
		mCartoomEngine.startCartoom(0);
	}

	public CircleView(Context context, AttributeSet attrs) {
		super(context, attrs);
		defaultParam();
		mContext = context;
		oval = new RectF();
		locator = new RectF();
		paint = new Paint();
		setCustomAttributes(attrs);
		mCartoomEngine.startCartoom(0);
	}

	public CircleView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		defaultParam();
		mContext = context;
		setCustomAttributes(attrs);
		mCartoomEngine.startCartoom(0);
	}
	
	private void defaultParam()
	{ 
		mCartoomEngine = new CartoomEngine();
		mMaxProgress = 1000;								 																												
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
		if (drawable == null) {
			return;
		}
		if (getWidth() == 0 || getHeight() == 0) {
			return;
		}
		this.measure(0, 0);
		if (drawable.getClass() == NinePatchDrawable.class)
			return;
		Bitmap b = ((BitmapDrawable) drawable).getBitmap();
		Bitmap bitmap = b.copy(Bitmap.Config.ARGB_8888, true);
		if (defaultWidth == 0) {
			defaultWidth = getWidth();
		}
		if (defaultHeight == 0) {
			defaultHeight = getHeight();
		}
		int radius = 0;
		
		radius = (defaultWidth < defaultHeight ? defaultWidth : defaultHeight) / 3;

		Bitmap roundBitmap = getCroppedRoundBitmap(bitmap, radius);
		canvas.drawBitmap(roundBitmap, defaultWidth / 2 - radius, defaultHeight / 2 - radius, null);
		
		
		int width = this.getWidth();
		int height = this.getHeight();

		width = (width > height) ? height : width;
		height = (width > height) ? height : width;

		paint.setAntiAlias(true); // 设置画笔为抗锯齿
		paint.setColor(Color.WHITE); // 设置画笔颜色
		canvas.drawColor(Color.TRANSPARENT); // 白色背景
		paint.setStrokeWidth(progressStrokeWidth); // 线宽
		paint.setStyle(Style.STROKE);

		oval.left = marxArcStorkeWidth / 2; // 左上角x
		oval.top = marxArcStorkeWidth / 2; // 左上角y
		oval.right = width - marxArcStorkeWidth / 2; // 左下角x
		oval.bottom = height - marxArcStorkeWidth / 2; // 右下角y
		
		float xCircle = (oval.left+oval.right)/2;
		float yCircle = (oval.top+oval.bottom)/2;
		float radiusCircle = xCircle-oval.left;

		canvas.drawArc(oval, -90, 360, false, paint); // 绘制白色圆圈，即进度条背景
		
		float angle = (float)mMainCurProgress/mMaxProgress*360-90;//对应的角度
		
		float pointX = (float) (xCircle+radiusCircle*Math.sin(angle));
		float pointY = (float) (yCircle-radiusCircle*Math.cos(angle));
		
		paint.setColor(Color.WHITE);
		paint.setStyle(Paint.Style.FILL);
		paint.setStrokeWidth(marxArcStorkeWidth);
		canvas.drawCircle(pointX, pointY, 5, paint);
		
	}

	/**
	 * 获取裁剪后的圆形图片
	 * @param radius半径
	 */
	public Bitmap getCroppedRoundBitmap(Bitmap bmp, int radius) {
		Bitmap scaledSrcBmp;
		int diameter = radius * 2;
		// 为了防止宽高不相等，造成圆形图片变形，因此截取长方形中处于中间位置最大的正方形图片
		int bmpWidth = bmp.getWidth();
		int bmpHeight = bmp.getHeight();
		int squareWidth = 0, squareHeight = 0;
		int x = 0, y = 0;
		Bitmap squareBitmap;
		if (bmpHeight > bmpWidth) {// 高大于宽
			squareWidth = squareHeight = bmpWidth;
			x = 0;
			y = (bmpHeight - bmpWidth) / 2;
			// 截取正方形图片
			squareBitmap = Bitmap.createBitmap(bmp, x, y, squareWidth, squareHeight);
		} else if (bmpHeight < bmpWidth) {// 宽大于高
			squareWidth = squareHeight = bmpHeight;
			x = (bmpWidth - bmpHeight) / 2;
			y = 0;
			squareBitmap = Bitmap.createBitmap(bmp, x, y, squareWidth,squareHeight);
		} else {
			squareBitmap = bmp;
		}
		if (squareBitmap.getWidth() != diameter || squareBitmap.getHeight() != diameter) {
			scaledSrcBmp = Bitmap.createScaledBitmap(squareBitmap, diameter,diameter, true);
		} else {
			scaledSrcBmp = squareBitmap;
		}
		Bitmap output = Bitmap.createBitmap(scaledSrcBmp.getWidth(),
				scaledSrcBmp.getHeight(), 
				Config.ARGB_8888);
		Canvas canvas = new Canvas(output);

		Paint paint = new Paint();
		Rect rect = new Rect(0, 0, scaledSrcBmp.getWidth(),scaledSrcBmp.getHeight());

		paint.setAntiAlias(true);
		paint.setFilterBitmap(true);
		paint.setDither(true);
		canvas.drawARGB(0, 0, 0, 0);
		canvas.drawCircle(scaledSrcBmp.getWidth() / 2,
				scaledSrcBmp.getHeight() / 2, 
				scaledSrcBmp.getWidth() / 2,
				paint);
		paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
		canvas.drawBitmap(scaledSrcBmp, rect, rect, paint);
		bmp = null;
		squareBitmap = null;
		scaledSrcBmp = null;
		return output;
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
		public float   mCurFloatProcess;			// 作动画时当前进度值 

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
							setMainProgress((int) mCurFloatProcess);
							
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
			mTimer = new Timer();
			mTimerInterval = 100;
			mCurFloatProcess = 0;
			
		}
		
		public synchronized void  startCartoom(int time)
		{
			if (time < 0 || mBCartoom == true)
			{
				return ;
			}
			
			timeMil = 0;
			
			mBCartoom = true;

			setMainProgress(time);

			mMaxProgress = 1000;
			mCurFloatProcess = time;
			
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


