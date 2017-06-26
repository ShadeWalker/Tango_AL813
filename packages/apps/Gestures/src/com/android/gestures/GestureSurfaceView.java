package com.android.gestures;

import java.util.Random;

import com.goodix.gestures.jni.GesturesJni;
import com.android.gestures.service.GestureService;
import com.android.gestures.util.Utils;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Paint.Style;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.WindowManager;

public class GestureSurfaceView extends SurfaceView implements Callback, Runnable {
	
	private static final String TAG = "GestureSurfaceView";
    private SurfaceHolder sfh;
    private Paint paint;      
    private Canvas canvas;
    private Thread th;
    public static boolean isRuning = true;
    public boolean closeSV = false;

    private float gesStartX, gesStartY, gesEndX, gesEndY, gesXmax, gesYmax, gesXmin, gesYmin, centerX, centerY, polar1X, polar1Y, polar2X, polar2Y, polar3X, polar3Y, polar4X,polar4Y;
    private float gesXlen, gesYlen;
    private Path mPath;
    private Paint paintQ;
    private Random random;
    private boolean refresh = false;
    private boolean readyTodraw = false;
    private FloatPoint[] drawPoints;
    private int pathPointNum;
    private int drawNumbers = 0;
    private int disappearNums = 0;
    private static final int animotionTime = 10;
    private Paint drawPaint;
    private Paint blurPaint;
    protected OnEndOfAnimationInterface mOnEndOfAnimation; 
    private boolean drawGesturesTest = false;
    private int gesType = 0;
    public static PointF[] point;
    private byte[] buffer = new byte[1000];
    private Paint mTextPaint;
    
    float[] polarSortX;
    float[] polarSortY;
    
    public GestureSurfaceView(Context context) {
        super(context);
        // TODO Auto-generated constructor stub
        sfh = getHolder();
        sfh.addCallback(this);

        mPath = new Path();
        drawPaint = new Paint();
        drawPaint.setAntiAlias(true);
        drawPaint.setStyle(Style.STROKE);
        drawPaint.setStrokeWidth(10f);
        drawPaint.setDither(true);
        drawPaint.setColor(Color.argb(255, 255, 255, 255));
        //drawPaint.setColor(Color.argb(235, 74, 138, 255));
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);
        
        blurPaint = new Paint();
        blurPaint.setAntiAlias(true);
        blurPaint.setStyle(Style.STROKE);
        //blurPaint.setColor(Color.argb(235, 74, 138, 255));
        blurPaint.setColor(Color.argb(248, 255, 255, 255));
        blurPaint.setStrokeWidth(10f);
        blurPaint.setMaskFilter(new BlurMaskFilter(20, BlurMaskFilter.Blur.SOLID));
        //blurPaint.setColorFilter(new LightingColorFilter(Color.BLUE, Color.RED));
        
        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setStyle(Style.STROKE);
        mTextPaint.setStrokeWidth(0);
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(30);
        
        drawPoints = new FloatPoint[150] ;
        
        point = new PointF[250];
        for(int i=0; i<250; i++) {
        	point[i] = new PointF();
        	point[i].x = 0;
        	point[i].y = 0;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // TODO Auto-generated method stub
        paint = new Paint();
        paint.setAntiAlias(true);
        closeSV = false;
        pathPointNum = 0;
        Log.d(TAG, "surfaceCreated");
        
        gesStartX = GestureFullscreenActivity.gesStartX;
        gesStartY = GestureFullscreenActivity.gesStartY;
        gesEndX = GestureFullscreenActivity.gesEndX;
        gesEndY = GestureFullscreenActivity.gesEndY;
        gesXlen = GestureFullscreenActivity.gesXlen;
        gesYlen = GestureFullscreenActivity.gesYlen;
        gesType = GestureFullscreenActivity.gesType;
        
        centerX = GestureFullscreenActivity.centerX;
        centerY = GestureFullscreenActivity.centerY;
        polar1X = GestureFullscreenActivity.polar1X;
        polar1Y = GestureFullscreenActivity.polar1Y;
        polar2X = GestureFullscreenActivity.polar2X;
        polar2Y = GestureFullscreenActivity.polar2Y;
        polar3X = GestureFullscreenActivity.polar3X;
        polar3Y = GestureFullscreenActivity.polar3Y;
        polar4X = GestureFullscreenActivity.polar4X;
        polar4Y = GestureFullscreenActivity.polar4Y;
        
        sortPolarPoint();
        
        Log.d(TAG, "MySurfaceView:gesType="+Integer.toHexString(gesType)+","+
        		"gesStartX="+Float.toString(gesStartX)+","+
        		"gesStartY="+Float.toString(gesStartY)+","+
        		"gesEndX="+Float.toString(gesEndX)+","+
        		"gesEndY="+Float.toString(gesEndY)+","+
        		"gesXlen="+Float.toString(gesXlen)+","+
        		"gesYlen="+Float.toString(gesYlen)+",");
        	{
        	switch(gesType) {
        	case 'a':
        	    Log.d(TAG, "begin draw a");
        	    if(!gesADraw())return;
        		break;
            case 'b':
        	    Log.d(TAG, "begin draw b");
        	    if(!gesBDraw())return;
        		break;
        	case 'c':
        		Log.d(TAG, "begin draw c");
        		if(!gesCDraw())return;
        		break;
        	case 'd':
        	    Log.d(TAG, "begin draw d");
        	    if(!gesDDraw())return;
        		break;
        	case 'e':
        		Log.d(TAG, "begin draw e");
        		if(!gesEDraw())return;
        		break;
        	case 'g':
        	    Log.d(TAG, "begin draw g");
        	    if(!gesGDraw())return;
        		break;
        	case 'h':
        	    Log.d(TAG, "begin draw h");
        	    if(!gesHDraw())return;
        		break;
        	case 'm':
        	    Log.d(TAG, "begin draw m");
        	    if(!gesMDraw())return;
        		break;
        	case 'o':
        		Log.d(TAG, "begin draw o");
        		if(!gesODraw())return;
        		break;
        	case 'q':
        	    Log.d(TAG, "begin draw q");
        	    if(!gesQDraw())return;
        		break;
        	case 's':
        	    Log.d(TAG, "begin draw s");
        	    if(!gesSDraw())return;
        		break;
        	case 'w':
        		Log.d(TAG, "begin draw w");
        		if(!gesWDraw())return;
        		break;
        	case '^':
        		Log.d(TAG, "begin draw ^");
        		if(!gesNDraw())return;
        		break;
        	case 'v':
        	case 'u':
        	    Log.d(TAG, "begin draw v");
        	    if(!gesVDraw())return;
        		break;
        	case 'z':
        	    Log.d(TAG, "begin draw z");
        	    if(!gesZDraw())return;
        		break;
            case 0xbb:
            case 0xab:
            case 0xba:
            case 0xaa:
                Log.d(TAG, "begin draw direction line");
                if(!gesLineDraw())return;
                break;
            case 'y':
                Log.d(TAG, "begin draw y");
            	if(!gesYDraw())return;
            	break;
        	default:
        		return;
        	}
        	GestureService.screenON.acquire();
                //wuhuihui modified to change unlock method
        	//GestureService.mKeylock.disableKeyguard();
        	drawNumbers = 0;
            disappearNums = 0;
            readyTodraw = true;
            refresh = true;
            th = new Thread(this);
            isRuning = true;
            th.start();
        }
        
    }
    
    private boolean gesNDraw() {
    	if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
        	return false;
        }
        
        gesType = 'v';
    	
    	Path path1 = new Path();
    	path1.reset();
    	path1.moveTo(gesStartX, gesStartY);
    	path1.lineTo(gesStartX + gesXlen/2, gesStartY - gesYlen);
    	getPoints(path1, 75);
    	Path path2 = new Path();
    	path2.reset();
    	path2.moveTo(gesStartX + gesXlen/2, gesStartY - gesYlen);
    	path2.lineTo(gesEndX, gesEndY);
    	getPoints(path2, 75);
    	
    	return true;
    }

    private boolean gesYDraw() {
    	if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
        	return false;
        }
    	
    	gesType = 'y';
    	
    	Path path1 = new Path();
    	float X1 = gesStartX - gesXlen;
    	float Y1 = gesStartY;
    	float X2 = gesStartX;
    	float Y2 = gesStartY + gesYlen/3;
    	RectF rect = new RectF(X1, Y1, X2, Y2);
    	path1.reset();
    	path1.addArc(rect, 180, -180);
    	getPoints(path1, 50);
    	Path path2 = new Path();
    	path2.reset();
    	path2.moveTo(gesStartX, gesStartY + gesYlen/6);
    	path2.lineTo(gesStartX, gesStartY + gesYlen*2/3);
    	getPoints(path2, 50);
    	Path path3 = new Path();
    	X1 = gesStartX - gesXlen;
    	Y1 = gesStartY + gesYlen*2/3;
    	X2 = gesStartX;
    	Y2 = gesStartY + gesYlen;
    	RectF rect1 = new RectF(X1, Y1, X2, Y2);
    	path3.reset();
    	path3.addArc(rect1, 0, 180);
    	getPoints(path3, 50);
    	return true;
    }
    
    private boolean gesLineDraw() {
        if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
        	return false;
        }
        Path path1 = new Path();
        path1.reset();
        path1.moveTo(gesStartX, gesStartY);
        path1.lineTo(gesEndX, gesEndY);
        getPoints(path1, 150);
		return true;
    }
    
    private boolean gesADraw() {
        if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
        	return false;
        }
        
    	float ovalX = gesXlen - (gesEndX - gesStartX);
    	float ovalY = gesYlen;
    	float X1 = gesStartX - ovalX;
    	float Y1 = gesEndY - ovalY;
    	float X2 = gesStartX;
    	float Y2 = gesEndY;
    	RectF oval1 = new RectF(X1, Y1, X2, Y2);
    	ovalX = gesEndX - gesStartX;
    	ovalY = gesEndY - gesStartY;
    	X1 = gesStartX;
    	Y1 = gesStartY - ovalY;
    	X2 = gesStartX + 2*ovalX;
    	Y2 = gesEndY;
    	RectF oval2 = new RectF(X1, Y1, X2, Y2);
    	Path path1 = new Path();
    	Path path2 = new Path();
    	
    	path1.reset();
    	path1.addArc(oval1, 0, -360);
    	getPoints(path1, 100);
    	path2.reset();
    	path2.addArc(oval2, 170, -90);
    	getPoints(path2, 50);
		return true;
    }
    
    private boolean gesBDraw() {
    	
    	if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
        	return false;
        }
        gesType = 'b';
    	Path path1 = new Path();
    	Path path2 = new Path();
    	path1.reset();
    	path1.moveTo(gesStartX, gesStartY);
    	path1.lineTo(gesStartX, gesStartY+gesYlen);
    	getPoints(path1, 50);
    	float X1 = gesEndX;
    	float Y1 = gesEndY - gesYlen/2;
    	float X2 = X1 + gesXlen;
    	float Y2 = gesEndY;
    	RectF oval = new RectF(X1, Y1, X2, Y2);
    	path2.reset();
    	//path2.moveTo(gesStartX, gesEndY);
    	path2.addArc(oval, 180, 350);
    	getPoints(path2, 100);
    	return true;
    }

    private boolean gesCDraw() {
    	Path path1 = new Path();
    	Path path2 = new Path();
    	RectF rect = new RectF();

        Log.d(TAG, "polarSortX length:" + polarSortX.length + ", polarSortY length:" + polarSortY.length);
		Log.d(TAG, "polarSortX:" + polarSortX[0] + "," + polarSortX[1] + "," + polarSortX[2] + "," + polarSortX[3]);
		Log.d(TAG, "polarSortY:" + polarSortY[0] + "," + polarSortY[1] + "," + polarSortY[2] + "," + polarSortY[3]);
    	if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
			Log.d(TAG, "returned in drawing C");
        	return false;
        }
    	
    	rect.top = polarSortY[0];
    	rect.left = polarSortX[0];
    	rect.right = polarSortX[3];
    	rect.bottom = polarSortY[3];

    	path2.reset();
    	path2.moveTo(polarSortX[0], polarSortY[0]);
    	path2.addArc(rect, -45, -270);
    	getPoints(path2, 150);
		Log.d(TAG, "drawing C successfully");
		return true;
    }
    
    private boolean gesDDraw() {
        if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
    		return false;
    	}
    	gesType = 'd';
    	Path path1 = new Path();
    	float X1 = gesStartX - gesXlen;
    	float Y1 = gesStartY;
    	float X2 = gesStartX;
    	float Y2 = gesStartY + gesYlen/2;
    	RectF rect = new RectF(X1, Y1, X2, Y2);
    	path1.reset();
    	path1.moveTo(gesStartX, gesStartY);
    	path1.addArc(rect, 0, -359);
    	getPoints(path1, 100);
    	Path path2 = new Path();
    	float controlX = gesStartX;
    	float controlY = gesStartY;
    	path2.reset();
    	path2.moveTo(gesStartX, gesEndY - gesYlen);
    	path2.quadTo(controlX, controlY, gesEndX, gesEndY);
    	getPoints(path2, 50);
    	return true;
    }
    
    private boolean gesEDraw() {
    	Path path1 = new Path();
    	Path path2 = new Path();
    	RectF rect1, rect2;
    	
    	if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
        	return false;
        }
    	
    	rect2 = new RectF(polarSortX[0], polarSortY[0], polarSortX[3], polarSortY[3]);
    	rect1 = new RectF(polarSortX[0], polarSortY[0], polarSortX[3], polarSortY[0]+(polarSortY[3] - polarSortY[0])/2.2f);
    	
    	path1.reset();
    	path1.addArc(rect1, 180, -160); 
    	getPoints(path1, 50);
    	
    	path2.reset();
    	path2.addArc(rect2, -22, -275);
    	getPoints(path2, 100);
    	return true;
    }
    
    private boolean gesGDraw() {
        if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
    		return false;
    	}
    	gesType = 'g';
    	
    	Path path1 = new Path();
    	float X1 = gesStartX - gesXlen;
    	float Y1 = gesStartY;
    	float X2 = gesStartX;
    	float Y2 = gesStartY + gesYlen/3;
    	RectF rect = new RectF(X1, Y1, X2, Y2);
    	path1.reset();
    	path1.addArc(rect, 0, -359);
    	getPoints(path1, 50);
    	Path path2 = new Path();
    	path2.reset();
    	path2.moveTo(gesStartX, gesStartY + gesYlen/6);
    	path2.lineTo(gesStartX, gesStartY + gesYlen*2/3);
    	getPoints(path2, 50);
    	Path path3 = new Path();
    	X1 = gesStartX - gesXlen;
    	Y1 = gesStartY + gesYlen*2/3;
    	X2 = gesStartX;
    	Y2 = gesStartY + gesYlen;
    	RectF rect1 = new RectF(X1, Y1, X2, Y2);
    	path3.reset();
    	path3.addArc(rect1, 0, 180);
    	getPoints(path3, 50);
    	return true;
    }
    
    private boolean gesHDraw() {
        if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
    		return false;
    	}
    	gesType = 'h';
    	Path path1 = new Path();
    	path1.reset();
    	path1.moveTo(gesStartX, gesStartY);
    	path1.lineTo(gesStartX, gesStartY + gesYlen);
    	getPoints(path1, 50);
    	Path path2 = new Path();
    	float X1 = gesStartX + gesXlen/2;
    	float Y1 = gesStartY + gesYlen/2;
    	path2.reset();
    	path2.moveTo(gesStartX, gesStartY + gesYlen*2/3);
    	path2.quadTo(X1, Y1, gesEndX, gesEndY - gesYlen/3);
    	getPoints(path2, 50);
    	Path path3 = new Path();
    	path3.reset();
    	path3.moveTo(gesEndX, gesEndY - gesYlen/3);
    	path3.lineTo(gesEndX, gesEndY);
    	getPoints(path3, 50);
    	return true;
    }
    
    private boolean gesMDraw() {
    	float centerX = (gesStartX + gesEndX)/2;
    	float centerY = (gesStartY + gesEndY)/2;
    	float slope = 0.5f;
    	float controlX1, controlY1, controlX2, controlY2;
    	Path path = new Path();
    	
    	if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
    		return false;
    	}
    	
    	if(gesEndX - gesStartX > 0.5f) {
    		slope = (gesEndY - gesStartY)/(gesEndX - gesStartX);
    	}
    	
    	float deltaY = gesYlen;
    	float deltaX = deltaY*slope;
    	if(deltaX > GestureFullscreenActivity.screenWidth) {
    		deltaX = GestureFullscreenActivity.screenWidth/2;
    	}
    	controlX1 = gesStartX + deltaX;
    	controlY1 = gesStartY - deltaY;
    	controlX2 = centerX + deltaX;
    	controlY2 = centerY - deltaY;
    	path.reset();
    	path.moveTo(gesStartX, gesStartY);
    	path.cubicTo(controlX1, controlY1, controlX2, controlY2, centerX, centerY);
    	controlX1 = controlX2;
    	controlY1 = controlY2;
    	controlX2 = gesEndX + deltaX;
    	controlY2 = gesEndY - deltaY;
    	path.cubicTo(controlX1, controlY1, controlX2, controlY2, gesEndX, gesEndY);
    	getPoints(path, 150);
    	
		return true;
    }

    private boolean gesWDraw() {
    	
        float centerX = (gesStartX + gesEndX)/2;
        float centerY = (gesStartY + gesEndY)/2;
        float slope = 0.5f;
        float controlX1, controlY1, controlX2, controlY2;
        Path path = new Path();
        
        if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
        	return false;
        }
        
        if(gesEndX - gesStartX > 0.5f) {
        	slope = (gesEndY - gesStartY)/(gesEndX - gesStartX);
        }
        
        float deltaY = gesYlen;
        float deltaX = deltaY*slope;
        
        if(deltaX > GestureFullscreenActivity.screenWidth) {
        	deltaX = GestureFullscreenActivity.screenWidth/2;
        }
        		
        controlX1 = gesStartX - deltaX;
        controlY1 = gesStartY + deltaY;
        controlX2 = centerX - deltaX;
        controlY2 = centerY + deltaY;
        path.reset();
        path.moveTo(gesStartX, gesStartY);
        path.cubicTo(controlX1, controlY1, controlX2, controlY2, centerX, centerY);
        controlX1 = controlX2;
        controlY1 = controlY2;
        controlX2 = gesEndX - deltaX;
        controlY2 = gesEndY + deltaY;
        path.cubicTo(controlX1, controlY1, controlX2, controlY2, gesEndX, gesEndY);
        getPoints(path, 150);
        return true;
    }
    
    private boolean gesODraw() {
    	Path path = new Path();
    	Path path1 = new Path();
    	RectF rect = new RectF();
    	
    	if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
        	return false;
        }
    	rect.top = polarSortY[0];
    	rect.left = polarSortX[0];
    	rect.right = polarSortX[3];
    	rect.bottom = polarSortY[3];
    	path.reset();
//    	path.moveTo(polar3X, polar3Y);
    	path.addOval(rect, Path.Direction.CCW);
    	getPoints(path, 150);
    	return true;
    }
    
    private boolean gesQDraw() {
        if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
    		return false;
    	}
    	gesType = 'q';
    	
    	Path path1 = new Path();
    	float X1 = gesStartX - gesXlen;
    	float Y1 = gesStartY;
    	float X2 = gesStartX;
    	float Y2 = gesStartY + gesYlen/3;
    	RectF rect = new RectF(X1, Y1, X2, Y2);
    	path1.reset();
    	path1.addArc(rect, 0, -359);
    	getPoints(path1, 100);
    	Path path2 = new Path();
    	path2.reset();
    	path2.moveTo(gesStartX, gesStartY + gesYlen/6);
    	path2.lineTo(gesStartX, gesEndY);
    	getPoints(path2, 50);
    	return true;
    }
    
    private boolean gesSDraw() {
    	if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
        	return false;
        }
    	gesType = 's';
        
    	Path path1 = new Path();
    	float X1 = polarSortX[0];
    	float Y1 = polarSortY[0];
    	float X2 = polarSortX[3];
    	float Y2 = polarSortY[0] + (polarSortY[3] - polarSortY[0])/2;
    	
    	RectF rect1 = new RectF(X1, Y1, X2, Y2);
    	path1.reset();
    	path1.addArc(rect1, 0, -270);
    	getPoints(path1, 75);
    	
    	X1 = polarSortX[0];
    	Y1 = polarSortY[0] + (polarSortY[3] - polarSortY[0])/2;
    	X2 = polarSortX[3];
    	Y2 = polarSortY[3];
    	RectF rect2 = new RectF(X1, Y1, X2, Y2);
    	Path path2 = new Path();
    	path2.reset();
    	path2.addArc(rect2, -90, 270);
    	getPoints(path2, 75);
    	
    	return true;
    }
    
    private boolean gesVDraw() {
    	if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
        	return false;
        }
    	gesType = 'v';
    	Path path1 = new Path();
    	path1.reset();
    	path1.moveTo(polarSortX[0], polarSortY[1]);
    	path1.lineTo(polarSortX[0] + (polarSortX[3] - polarSortX[0])/2, polarSortY[3]);
    	getPoints(path1, 75);
    	Path path2 = new Path();
    	path2.reset();
    	path2.moveTo(polarSortX[0] + (polarSortX[3] - polarSortX[0])/2, polarSortY[3]);
    	path2.lineTo(polarSortX[3], polarSortY[0]);
    	getPoints(path2, 75);
    	return true;
    }
    
    private boolean gesUDraw() {
    	if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
    		return false;
    	}
    	Path path1 = new Path();
    	path1.reset();
    	path1.moveTo(gesStartX, gesStartY);
    	float controlX1, controlY1, controlX2, controlY2;
    	controlX1 = gesStartX;
    	controlY1 = gesStartY + gesYlen;
    	controlX2 = gesStartX + gesXlen;
    	controlY2 = controlY1;
    	path1.cubicTo(controlX1, controlY1, controlX2, controlY2, gesEndX, gesEndY);
    	getPoints(path1, 150);
    	return true;
    }
    
    private boolean gesZDraw() {
    	if((gesStartX == gesEndX)&&(gesStartY == gesEndY)&&(gesXlen == 0)&&(gesYlen == 0)) {
        	return false;
        }
    	gesType = 'z';
    	
    	Path path1 = new Path();
    	path1.reset();
    	path1.moveTo(polarSortX[0], polarSortY[0]);
    	path1.lineTo(polarSortX[3], polarSortY[0] - 20);
    	path1.lineTo(polarSortX[0], polarSortY[3]);
    	path1.lineTo(polarSortX[3], polarSortY[3] - 20);
    	
    	getPoints(path1, 150);
    	return true;
    }
    
    private void sortPolarPoint() {
    	float[] minX = {polar1X, polar2X, polar3X, polar4X};
    	float[] minY = {polar1Y, polar2Y, polar3Y, polar4Y};
    	bubbingSort(minX);
    	bubbingSort(minY);
    	polarSortX = minX;
    	polarSortY = minY;
    	
    }
    
    private void bubbingSort(float[] array){
		int len = array.length ;		
		for(int i=len-1;i>0;i--){
			boolean flag = false ;
			for(int j=0;j<i;j++){
				if(array[j]>array[j+1]){
					float temp = array[j] ;
					array[j] = array[j+1] ;
					array[j+1] = temp ;
					flag = true ;
				}				
			}			
			if(!flag){
				break ;
			}
		}
	}
    
    public void drawQpath(Canvas canvas, float startX, float startY, 
    		float controlX, float controlY, float endX, float endY) {
    	Path path = new Path();
        path.reset();
        path.moveTo(startX, startY);
        path.quadTo(controlX, controlY, endX, endY);
        canvas.drawPath(path, paintQ);
    }
    
    private void getPoints(Path path, int pNum) {

        PathMeasure pm = new PathMeasure(path, false);
        float length = pm.getLength();
        float distance = 0f;
        float speed = length / pNum;
        int counter = 0;
        float[] aCoordinates = new float[2];

        while ((distance < length) && (counter < pNum)) {
            // get point from the path
            pm.getPosTan(distance, aCoordinates, null);
            drawPoints[pathPointNum + counter] = new FloatPoint(aCoordinates[0], aCoordinates[1]);
            counter++;
            distance = distance + speed;
        }
        pathPointNum += counter;

    }
    
    class FloatPoint {
        float x, y;

        public FloatPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        
    	if(!drawGesturesTest)return false;
    	
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            gesStartX = (int) event.getX();
            gesStartY = (int) event.getY();
            gesXmax = gesStartX;
            gesYmax = gesStartY;
            gesXmin = gesStartX;
            gesYmin = gesStartY;
            refresh = false;
            readyTodraw = false;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
        	if(gesXmax <  event.getX()) {
        		gesXmax = event.getX();
        	}
        	if(gesXmin > event.getX()) {
        		gesXmin = event.getX();
        	}
        	if(gesYmax < event.getY()) {
        		gesYmax = event.getY();
        	}
        	if(gesYmin > event.getY()) {
        		gesYmin = event.getY();
        	}
        	
        }
        if(event.getAction() == MotionEvent.ACTION_UP) {
        	gesEndX = (int) event.getX();
            gesEndY = (int) event.getY();
            gesXlen = gesXmax - gesXmin;
            gesYlen = gesYmax - gesYmin;            
            
            if(!gesZDraw()) {
            	return true;
            }
            
            refresh = true;
            drawNumbers = 0;
            disappearNums = 0;
            pathPointNum = 0;
            readyTodraw = true;
        }

        return true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // TODO Auto-generated method stub
    	isRuning = false;

    	if(GestureService.screenON != null) {
    		GestureService.screenON.release();
    	}
    }
    
    public interface OnEndOfAnimationInterface {  
        public void onEndOfAnimation();  
          
    } 
         
    public void setOnEndOfAnimation(OnEndOfAnimationInterface xOnEndOfAnimation){  
        mOnEndOfAnimation = xOnEndOfAnimation;  
    }
    
    public void release() {
        getHolder().getSurface().release();
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        while (isRuning) {
		   Log.d(TAG, "Thread running");
           long startTime = System.currentTimeMillis();
           if(readyTodraw) {
        	    
	            if(drawPoints.length != 0 && drawNumbers < drawPoints.length) {
	            	drawNumbers+=5;
	            	disappearNums = 0;
	            }
	            else {
	            	disappearNums+=5;
	            }
	            
	            canvas = sfh.lockCanvas();
	            
	            if(canvas != null) {
                        //HQ_wuhuihui add for apr crash start
                        try {
                            canvas.drawColor(Color.BLACK);
                            appearDrawGesture();
                            disappearDrawGesture();
                            sfh.unlockCanvasAndPost(canvas);

                        } catch (IllegalStateException e) {
                            Log.d(TAG, "Check if surface has been released");
                        }
                        //HQ_wuhuihui add for apr crash end
	            }
	            
	            if(disappearNums >= 150) {
	            	closeSV = true;
	            	isRuning = false;
	            	disappearNums = 0;
	            	if(mOnEndOfAnimation != null) {
	            		mOnEndOfAnimation.onEndOfAnimation();
	            	}
	            }
	            
            }
            //[[HQ00502504]] wuhuihui modified draw line  delay 20ms
                try {
                    Thread.currentThread().sleep(20);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

        }

    }
    
    private void appearDrawGesture() {
    	float penWidth;
        FloatPoint lastPoint = drawPoints[0];
        if(disappearNums == 0) {
            for(int j=0; j<drawPoints.length && j<drawNumbers; j++) {
            	if(lastPoint != null && drawPoints[j] != null) {
            		canvas.drawLine(lastPoint.getX(), lastPoint.getY(), drawPoints[j].getX(), drawPoints[j].getY(), drawPaint);
	            	lastPoint = drawPoints[j];
	            	if(j<(drawPoints.length/2)) {
	            		penWidth = j/4;
	            	}
	            	else
	            	{
	            		penWidth = (drawPoints.length/2 - j/2)/2;
	            	}
	            	if((gesType == 'b')&&(j < 50)) {
	            		penWidth = 25;
	            	}
	            	if(gesType == 'h') {
	            		penWidth = 25;
	            	}
	            	
	            	if(GestureFullscreenActivity.isDebugMode) {
	            	    penWidth = 10;
	            	}
	            	drawPaint.setStrokeWidth(penWidth);
            	}
            }
        }
    }
    
    private void disappearDrawGesture() {
    	float penWidth;
        FloatPoint lastPoint = drawPoints[0];
    	if(disappearNums > 0 && disappearNums < (drawPoints.length+1)) {
        	lastPoint = drawPoints[disappearNums-1];
            for(int j=disappearNums; j<drawPoints.length; j++) {
            	if(lastPoint != null && drawPoints[j] != null) {
            		canvas.drawLine(lastPoint.getX(), lastPoint.getY(), drawPoints[j].getX(), drawPoints[j].getY(), drawPaint);
	            	lastPoint = drawPoints[j];
	            	if(j<(drawPoints.length/2)) {
	            		penWidth = j/4;
	            	}
	            	else
	            	{
	            		penWidth = (drawPoints.length/2 - j/2)/2;
	            	}
	            	if((gesType == 'b')&&(j < 50)) {
	            		penWidth = 25;
	            	}
	            	if(gesType == 'h') {
	            		penWidth = 25;
	            	}
	            	drawPaint.setStrokeWidth(penWidth);
            	}
            }
        }

    }

}
