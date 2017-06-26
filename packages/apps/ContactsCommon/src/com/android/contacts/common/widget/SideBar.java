package com.android.contacts.common.widget;

import java.util.Locale;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
/**
 * 联系人自定义侧边栏  for  f*g huawei UI
 * @author tanghuaizhe
 *
 */
public class SideBar extends View {
	// 监听器
	private OnTouchingLetterChangedListener onTouchingLetterChangedListener;
	// 侧边栏的字母
	public static String[] b = { "#", "A", "B", "C", "D", "E", "F", "G",
		"H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T",
		"U", "V", "W", "X", "Y", "Z"};
	
	
	public static String[] L21C328_b = { "#", "A", "B", "C", "D", "E", "F", "G",
			"H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T",
			"U", "V", "W", "X", "Y", "Z","ء" };
	
	public static String[] fa_b = { "#", "آ", "ا", "ب", "پ", "ث", "ث", "ج",
		"چ", "ح", "خ", "د", "ذ", "ر", "ز", "ژ", "س", "ش", "ص", "ض", "ط",
		"ظ", "ع", "غ", "ف", "ق", "ک", "گ", "ل", "م", "ن","و", "ه", "ی"};
	
	public static String[] ar_b = { "#", "ا", "ب", "ت", "ث", "ج", "ح", "خ",
		"د", "ذ", "ر", "ز", "س", "ش", "ص", "ض", "ط", "ظ", "ع", "غ", "ف",
		"ق", "ك", "ل", "م", "ن", "ه","و","ي","ء"};

	
	public static String[] L21C328_ar_b = { "#", "ا", "ب", "ت", "ث", "ج", "ح", "خ",
		"د", "ذ", "ر", "ز", "س", "ش", "ص", "ض", "ط", "ظ", "ع", "غ", "ف",
		"ق", "ك", "ل", "م", "ن", "ه","و","ي","ء","Z" };
	
	public static String[] Ru_b = {  "#","а", "б", "в", "г", "д", "е", "ё", "ж",
			"з", "и", "й", "к", "л", "м", "н", "о", "п", "р", "с", "т", "у",
			"ф", "х", "ц", "ч", "ш", "щ", "ъ", "ы", "ь", "э", "ю", "я" };
	private int choose = -1;// ­
	private Paint paint = new Paint();
	private static String[] index;

	private TextView mTextDialog;

	public TextView getTextDialog() {
		return mTextDialog;
	}

	public void setTextView(TextView mTextDialog) {
		this.mTextDialog = mTextDialog;
	}

	public SideBar(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public SideBar(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public SideBar(Context context) {
		super(context);
	}
	private String langage;
	private boolean forceEnBar;
	private boolean forceArBar;
	private String version;
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		//modify by wangmingyue for contacts ui beign
		langage = Locale.getDefault().getLanguage();
		Log.i("tang", "the language is "+langage);
		version=android.os.SystemProperties.get("ro.build.version");
		if (langage.startsWith("ar")) {
			if (version.contains("L21C328")) {
				index = L21C328_ar_b;
			} else {
				index = ar_b;
			}
		} else if (langage.startsWith("fa")) {
			index = fa_b;
		} else if(langage.startsWith("ru")){
			index=Ru_b;
		}else{
			if (version.contains("L21C328")) {
				index = L21C328_b;
			} else {
				index = b;
			}
		}
		 //强制模式
		 if(forceEnBar){
				if (version.contains("L21C328")) {
					index = L21C328_b;
				} else {
					index = b;
				}
		} else if (forceArBar) {
			if (version.contains("L21C328")) {
				index = L21C328_ar_b;
			} else {
				index = ar_b;
			}
		}
		
		 
		int height = getHeight()-20;
		int width = getWidth();
		int singleHeight = height / index.length;

		for (int i = 0; i < index.length; i++) {
			paint.setColor(android.graphics.Color.parseColor("#b6b6b6"));
			paint.setTypeface(Typeface.DEFAULT_BOLD);
			paint.setAntiAlias(true);
			paint.setTextSize(20);
			if (i == choose) {
				paint.setColor(Color.parseColor("#28c0c6"));
				paint.setFakeBoldText(true);
			}
			float xPos = width / 2 - paint.measureText(index[i]) / 2;
			float yPos = singleHeight * i + singleHeight;
			canvas.drawText(index[i], xPos, yPos, paint);
			paint.reset();
		}
		//modify by wangmingyue for contacts ui end

	}

	/**
	 * 根据sectionIndex设置siderbar高亮字母
	 * @param postion
	 */
	public void  setPosition(int   sectionIndex){
		choose=sectionIndex;
		invalidate();
	}
	
	
	/**
	 * 根据section字符设置siderbar高亮字母
	 * @param section
	 */
	public void setPosition(String section) {
		if (section != null&&mTextDialog != null) {
			if(section.equals(" ")){
				mTextDialog.setText("#");
			}else{
				mTextDialog.setText(section);
			}
//			mTextDialog.setVisibility(View.VISIBLE);
		}
	}
	
	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		final int action = event.getAction();
		final float y = event.getY();
		final int oldChoose = choose;
		final OnTouchingLetterChangedListener listener = onTouchingLetterChangedListener;
		final int c = (int) (y / getHeight() * index.length);
		if (event.getX() > getWidth()) {
			setBackgroundDrawable(new ColorDrawable(0x00000000));
			choose = -1;
			invalidate();
			if (mTextDialog != null) {
				mTextDialog.setVisibility(View.INVISIBLE);
			}
			return true;
		}
		switch (action) {
		case MotionEvent.ACTION_UP:
			setBackgroundDrawable(new ColorDrawable(0x00000000));
			choose = -1;//
			invalidate();
			if (mTextDialog != null) {
				mTextDialog.setVisibility(View.INVISIBLE);
			}
			break;

		default:
//			setBackgroundResource(R.drawable.sidebar_background);
			if (oldChoose != c) {
				if (c >= 0 && c < index.length) {
					if (version.contains("L21C328")) {
					if(index[c].equals("Z")){
						forceEnBar=true;
						forceArBar=false;
						invalidate();
					}else if (index[c].equals("ء")) {
						forceArBar=true;
						forceEnBar=false;
						invalidate();
					}
					}
					if (listener != null) {
						listener.onTouchingLetterChanged(index[c]);
					}
					if (mTextDialog != null) {
						mTextDialog.setText(index[c]);
						mTextDialog.setVisibility(View.VISIBLE);
					}

					choose = c;
					invalidate();
				}
			}

			break;
		}
		return true;
	}

	public void setOnTouchingLetterChangedListener(
			OnTouchingLetterChangedListener onTouchingLetterChangedListener) {
		this.onTouchingLetterChangedListener = onTouchingLetterChangedListener;
	}

	public interface OnTouchingLetterChangedListener {
		public void onTouchingLetterChanged(String s);
	}

}