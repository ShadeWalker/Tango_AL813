package com.android.contacts.activities;

import android.net.Uri;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.telecom.PhoneAccount;
import android.text.TextUtils;

import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewParent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import android.app.Activity;

import com.android.phone.common.animation.AnimUtils;
import com.android.phone.common.animation.AnimationListenerAdapter;

import com.android.dialer.dialpad.DialpadFragment;
import com.android.dialer.util.DialerUtils;

import com.android.contacts.R;
import com.android.contacts.common.widget.FloatingActionButtonControllerEM;


//add by zhangjinqiang for al812--start
import android.app.AlertDialog;
import android.content.DialogInterface;
import com.android.dialer.calllog.CallLogQueryHandler;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.graphics.drawable.Drawable;

import com.android.dialer.calllog.CallLogFragment;
import com.mediatek.dialer.activities.CallLogMultipleDeleteActivity;

import java.lang.String;
import java.util.List;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.Display;

//add by zhangjinqiang for al812--end


import com.speeddial.SpeedDialActivity;

import com.mediatek.dialer.util.DialerFeatureOptions;

import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ClipData.Item;
import java.util.regex.Pattern;

/*
 * Created by guofeiyao for HQ01207444 AL812
 */
public class DialerPlugIn implements ViewPager.OnPageChangeListener,
		OnClickListener ,View.OnLongClickListener {
	public static final String TAG = "DialerPlugIn";

	/** @see #getCallOrigin() */
	private static final String CALL_ORIGIN_DIALTACTS = "com.android.dialer.DialtactsActivity";
	/**
	 * Just for backward compatibility. Should behave as same as
	 * {@link Intent#ACTION_DIAL}.
	 */
	private static final String ACTION_TOUCH_DIALER = "com.android.phone.action.TOUCH_DIALER";

	private Context mContext;
	private FloatingActionButtonControllerEM mFloatingActionButtonController;
	private FloatingActionButtonControllerEM dialpadContainer;
	private boolean mIsLandscape;
	private static int mCurrentTabPosition = 0;//modify by zhaizhanfeng for HQ01856116 at 160413
	
	//add by zhangjinqiang for al812--start
	private CallLogFragment mCallLogFragment=null;
	private int choiceItemId=0;
	public static final int CALL_TYPE_ALL=-1;
	public static final int CALL_TYPE_INCOMING=1;
	public static final int CALL_TYPE_OUTGOING=2;
	public static final int CALL_TYPE_MISS=3;
	private List<SubscriptionInfo> mSubInfoList;
	private int mSubCount;
	private AlertDialog dialog;
	private AlertDialog menuDialogForDialer;//HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked
	private Button cancel,confirm;
	private RadioGroup simGroup,callGroup;
	private RadioButton simAll,simOne,simTwo;
	private RadioButton allCall,missCall,answerCall,dialerCall;
	private static String mSimType="all_account";
	private static int mCallLog=-1;
	public static final String SIM_ALL = "all_account";
	public static final String SIM_ONE = "0";
	public static final String SIM_TWO = "1";

	public static boolean sSimAll = true;
	public static boolean sSimOne = false;
	public static boolean sSimTwo =false;
	public static boolean sCallAll=true;
	public static boolean sCallIncoming = false;
	public static boolean sCallOutgoing = false;
	public static boolean sCallMiss = false;
	
	//add by zhangjinqiang for al812--end
	public void setChosenType(int id) {
		choiceItemId = id;
	}

	public int getChosenType(){
		return choiceItemId;
	}

	public static int mSingleCallLog = CALL_TYPE_ALL;//HQ_wuruijun add for HQ01432449

	public boolean isR2L = DialerUtils.isRtl(); //add by gaoyuhao
	

	/**
	 * Animation that slides in.
	 */
	private Animation mSlideIn;

	/**
	 * Animation that slides out.
	 */
	private Animation mSlideOut;

	/**
	 * Listener for after slide out animation completes on dialer fragment.
	 */
	AnimationListenerAdapter mSlideOutListener = new AnimationListenerAdapter() {
		@Override
		public void onAnimationEnd(Animation animation) {
			commitDialpadFragmentHide();
		}
	};

	private DialpadFragment mDialpadFragment;
	private View menu;
	private ImageView menuTop;
	private TextView menuBottom;

	public DialerPlugIn(Context context,
			FloatingActionButtonControllerEM floatingActionButtonContainer,
			boolean isLandscape, DialpadFragment dialpadF) {
		this.mContext = context;
		mFloatingActionButtonController = floatingActionButtonContainer;
		mIsLandscape = isLandscape;
		mDialpadFragment = dialpadF;

		final View container = mFloatingActionButtonController.getContainer();
		final View dialpad = container.findViewById(R.id.ib_dialpad);
		dialpad.setOnClickListener(this);
		menu = container.findViewById(R.id.ib_menu);
		menuTop = (ImageView)container.findViewById(R.id.iv_menu_top);
		menuBottom = (TextView)container.findViewById(R.id.tv_menu_bottom);
		menu.setOnClickListener(this);
		menu.setOnLongClickListener(this);
		
		// something about animation:(
		final boolean isLayoutRtl = DialerUtils.isRtl();
		if (mIsLandscape) {
			mSlideIn = AnimationUtils.loadAnimation(mContext,
					isLayoutRtl ? R.anim.dialpad_slide_in_left
							: R.anim.dialpad_slide_in_right);
			mSlideOut = AnimationUtils.loadAnimation(mContext,
					isLayoutRtl ? R.anim.dialpad_slide_out_left
							: R.anim.dialpad_slide_out_right);
		} else {
			mSlideIn = AnimationUtils.loadAnimation(mContext,
					R.anim.dialpad_slide_in_bottom);
			mSlideOut = AnimationUtils.loadAnimation(mContext,
					R.anim.dialpad_slide_out_bottom);
		}

		mSlideIn.setInterpolator(AnimUtils.EASE_IN);
		mSlideOut.setInterpolator(AnimUtils.EASE_OUT);

		mSlideOut.setAnimationListener(mSlideOutListener);
	}

	public DialerPlugIn(Context context,
			FloatingActionButtonControllerEM floatingActionButtonContainer,
			boolean isLandscape, int fragmentPosition, DialpadFragment dialpad) {
		this(context, floatingActionButtonContainer, isLandscape, dialpad);
		mCurrentTabPosition = fragmentPosition;

	}

	//add by zhangjinqiang for al812--start
	public DialerPlugIn(Context context,
			FloatingActionButtonControllerEM floatingActionButtonContainer,
			boolean isLandscape, DialpadFragment dialpad,
			CallLogFragment callLogFragment) {
		this(context, floatingActionButtonContainer, isLandscape, dialpad);
		mCallLogFragment = callLogFragment;

	}

	// add by zhangjinqiang for al812--end
	
	public DialerPlugIn(Context context,
			FloatingActionButtonControllerEM floatingActionButtonContainer,
			FloatingActionButtonControllerEM dialpadC,
			boolean isLandscape, DialpadFragment dialpad,
			CallLogFragment callLogFragment) {
		this(context, floatingActionButtonContainer, isLandscape, dialpad,callLogFragment);
		dialpadContainer = dialpadC;

	}
	
	public static void setPosition(int p){//modify by zhaizhanfeng for HQ01856116 at 160413
		mCurrentTabPosition = p;
	}

	public static int getPosition(){//modify by zhaizhanfeng for HQ01856116 at 160413
        return mCurrentTabPosition;
	}

	private boolean queryEmpty = true;

	public void updateMenuButton(boolean empty) {
		queryEmpty = empty;
		if (empty) {
			/*
			Drawable drawableTop = mContext.getResources().getDrawable(
					R.drawable.dial_menu);
			drawableTop.setBounds(0, 0, drawableTop.getMinimumWidth(),
					drawableTop.getMinimumHeight());
			menu.setCompoundDrawables(null, drawableTop, null, null);
			menu.setText(R.string.dial_menu);
			*/
			menuTop.setImageResource(R.drawable.ic_menu_em);
			menuBottom.setText(R.string.dial_menu);
		} else {
		/*
			Drawable drawableTop = mContext.getResources().getDrawable(
					R.drawable.dial_delete);
			drawableTop.setBounds(0, 0, drawableTop.getMinimumWidth(),
					drawableTop.getMinimumHeight());
			menu.setCompoundDrawables(null, drawableTop, null, null);
			menu.setText(R.string.dial_delete);
			*/
			menuTop.setImageResource(R.drawable.ic_delete_em);
			menuBottom.setText(R.string.dial_delete);
		}
	}

	/**
	 * Callback from child DialpadFragment when the dialpad is shown.
	 */
	public void onDialpadShown() {
		if (mDialpadFragment.getAnimate()) {
			if(null != mDialpadFragment.getView()) {
				mDialpadFragment.getView().startAnimation(mSlideIn);
			}
		} else {
			mDialpadFragment.setYFraction(0);
		}

		PeopleActivity ac = ((PeopleActivity) mContext);

		/*
		// / M: Support MTK-DialerSearch @[
		if (DialerFeatureOptions.isDialerSearchEnabled()) {
			ac.updateSearchFragmentExPosition();
		} else {
			// / @}
			ac.updateSearchFragmentPosition();
		}
		*/
	}

	/**
	 * Initiates a fragment transaction to show the dialpad fragment. Animations
	 * and other visual updates are handled by a callback which is invoked after
	 * the dialpad fragment is shown.
	 * 
	 * @see #onDialpadShown
	 */
	private void showDialpadFragment(boolean animate) {
		PeopleActivity ac = ((PeopleActivity) mContext);
		if (ac.isDialpadShown() || ac.isStateSaved())
			return;

		ac.setIsDialpadShown(true);
		mDialpadFragment.setAnimate(animate);

		final FragmentTransaction ft = ac.getFragmentManager()
				.beginTransaction();
		ft.show(mDialpadFragment);
		// / M: fix CR: ALPS01608178, avoid commit JE @{
		/*
		 * ft.commit();
		 */
		ft.commitAllowingStateLoss();
		// / @}

		// modified by guofeiyao begin
		if (animate) {
			// mFloatingActionButtonController.scaleOut();
			mFloatingActionButtonController.setVisible(false);
			// end
		} else {
			mFloatingActionButtonController.setVisible(false);
		}

		if (!ac.isInSearchUi()) {
			ac.enterSearchUi(true /* isSmartDial */, ac.getSearchQuery());
		}
	}

	/**
	 * Initiates animations and other visual updates to hide the dialpad. The
	 * fragment is hidden in a callback after the hide animation ends.
	 * 
	 * @see #commitDialpadFragmentHide
	 */
	public void hideDialpadFragment(boolean animate, boolean clearDialpad) {
		if (mDialpadFragment == null) {
			return;
		}
		if (clearDialpad) {
			mDialpadFragment.clearDialpad();
		}

		PeopleActivity ac = ((PeopleActivity) mContext);

		if (!ac.isDialpadShown()) {
			return;
		}
		ac.setIsDialpadShown(false);
		mDialpadFragment.setAnimate(animate);

		/*
		// / M: Support MTK-DialerSearch @{
		if (DialerFeatureOptions.isDialerSearchEnabled()) {
			ac.updateSearchFragmentExPosition();
			// / @}
		} else {
			ac.updateSearchFragmentPosition();
		}
		*/

		updateFloatingActionButtonControllerAlignment(animate);
		if (animate) {
			mDialpadFragment.getView().startAnimation(mSlideOut);
		} else {
			commitDialpadFragmentHide();
		}

		if (ac.isInSearchUi()) {
			if (TextUtils.isEmpty(ac.getSearchQuery())) {
				ac.exitSearchUi();
			}
		}
	}

	/**
	 * Finishes hiding the dialpad fragment after any animations are completed.
	 */
	private void commitDialpadFragmentHide() {
		// modified by guofeiyao
		if (!((PeopleActivity) mContext).isStateSaved()
				&& !mDialpadFragment.isHidden()) {
			final FragmentTransaction ft = ((Activity) mContext)
					.getFragmentManager().beginTransaction();
			ft.hide(mDialpadFragment);
			// / M: Fix CR ALPS01821946. @{
			/*
			 * original code: ft.commit();
			 */
			ft.commitAllowingStateLoss();
			// / @}
		}
		// mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
		mFloatingActionButtonController.setVisible(true);
	}

	/**
	 * Updates controller based on currently known information.
	 * 
	 * @param animate
	 *            Whether or not to animate the transition.
	 */
	public void updateFloatingActionButtonControllerAlignment(boolean animate) {
		//the dialpad is shown,nothing to do.
		PeopleActivity ac = (PeopleActivity)mContext;
		if (ac.isDialpadShown()) return;
		
		// modified by guofeiyao begin
		int align = (!mIsLandscape && mCurrentTabPosition == 0) ? FloatingActionButtonControllerEM.ALIGN_MIDDLE
				: FloatingActionButtonControllerEM.ALIGN_HIDE;
		// end
		//add by gaoyuhao begin
		if(isR2L&&mCurrentTabPosition==2){
			align = FloatingActionButtonControllerEM.ALIGN_MIDDLE;
		} else if(isR2L){
            align = FloatingActionButtonControllerEM.ALIGN_HIDE;
        }
        // end lipeng
		mFloatingActionButtonController.align(align, 0 /* offsetX */,
				0 /* offsetY */, animate);
	}
	
	public void updateDialpadContainerAlignment(boolean animate) {
		//the dialpad is shown,nothing to do.
		PeopleActivity ac = (PeopleActivity)mContext;
		if (!ac.isDialpadShown()) return;
		
		// modified by guofeiyao begin
		int align = (!mIsLandscape && mCurrentTabPosition == 0) ? FloatingActionButtonControllerEM.ALIGN_MIDDLE
				: FloatingActionButtonControllerEM.ALIGN_HIDE;
		// end
		//add by gaoyuhao begin	
		if(isR2L&&mCurrentTabPosition==2){
			align = FloatingActionButtonControllerEM.ALIGN_MIDDLE;
		} else if(isR2L) {
            align = FloatingActionButtonControllerEM.ALIGN_HIDE;
        }
        // end lipeng
		dialpadContainer.align(align, 0 /* offsetX */,
				0 /* offsetY */, animate);
	}

	@Override
	public void onPageScrolled(int position, float positionOffset,
			int positionOffsetPixels) {
		// // added by guofeiyao
		position = ((PeopleActivity)mContext).getRtlPosition(position);
		// Only scroll the button when the first tab is selected. The button
		// should scroll from
		// the middle to right position only on the transition from the
		// first tab to the second
		// tab.
		// If the app is in RTL mode, we need to check against the second
		// tab, rather than the
		// first. This is because if we are scrolling between the first and
		// second tabs, the
		// viewpager will report that the starting tab position is 1 rather
		// than 0, due to the
		// reversal of the order of the tabs.
		final boolean isLayoutRtl = DialerUtils.isRtl();
		final boolean shouldScrollButton = position == (isLayoutRtl ? 1 : 0);
		if (shouldScrollButton && !mIsLandscape) {
			mFloatingActionButtonController
					.onPageScrolled(isLayoutRtl ? 1 - positionOffset
							: positionOffset);
		} else if (position != 0) {
			mFloatingActionButtonController.onPageScrolled(1);
		}
		
		PeopleActivity ac = (PeopleActivity)mContext;
		if(!ac.isDialpadShown()){
			return;
		}
		
		if (shouldScrollButton && !mIsLandscape) {
			dialpadContainer
					.onPageScrolled(isLayoutRtl ? 1 - positionOffset
							: positionOffset);
		} else if (position != 0) {
			dialpadContainer.onPageScrolled(1);
		}
		// end
	}

	@Override
	public void onPageSelected(int position) {
		mCurrentTabPosition = position;
		// Toast.makeText(mContext, "positon" + position, Toast.LENGTH_SHORT)
		// .show();
		// if (position == 1)
		// updateFloatingActionButtonControllerAlignment(false);
		// else
		// updateFloatingActionButtonControllerAlignment(true);

		//HQ_wuruijun add for HQ01701193 start
		updateFloatingActionButtonControllerAlignment(false);
		updateDialpadContainerAlignment(false);
		//HQ_wuruijun add for HQ01701193 end
	}

	@Override
	public void onPageScrollStateChanged(int state) {
	}

	@Override
    public boolean onLongClick(View view) {
        switch (view.getId()) {
        case R.id.ib_menu:
            mDialpadFragment.deleteButtonLongPressed();
			return true;
		default:
			return false;
		}
	}

	@Override
	public void onClick(View view) {
		// TODO Auto-generated method stub
		switch (view.getId()) {
		// modified by guofeiyao for HQ01207444 begin
		// case R.id.floating_action_button:
		case R.id.ib_dialpad:
			PeopleActivity ac = (PeopleActivity) mContext;
			// modified by guofeiyao for HQ01207444 end
			if (!ac.isDialpadShown()) {
				ac.setInCallDialpadUp(false);
				showDialpadFragment(true);
			}
			break;
		// added by guofeiyao for HQ01207444 begin
		case R.id.ib_menu:
			if (queryEmpty) {
				openMenu();
			} else {
                mDialpadFragment.deleteButtonPressed();
			}
			break;

		// end

		// case R.id.voice_search_button:
		// try {
		// startActivityForResult(new
		// Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
		// ACTIVITY_REQUEST_CODE_VOICE_SEARCH);
		// } catch (ActivityNotFoundException e) {
		// Toast.makeText(DialtactsActivity.this,
		// R.string.voice_search_not_available,
		// Toast.LENGTH_SHORT).show();
		// }
		// break;
		// case R.id.dialtacts_options_menu_button:
		// mOverflowMenu.show();
		// break;
		default: {
			Log.wtf(TAG, "Unexpected onClick event from " + view);
			break;
		}
		}
	}

	private boolean menuOpened = false;
	private static final String DZ = "_duanze";

	private void launchSpeedDial(){
		SpeedDialActivity.actionStart(mContext);
	}

	private void pasteNumber() {
ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
							String str = "";
							if (clipboard.hasPrimaryClip()) {
								ClipData clipData = clipboard.getPrimaryClip();
								ClipData.Item item = clipData.getItemAt(0);
								str = item.coerceToText(mContext).toString();

								str = str.replaceAll(" ", "");
							}
							mDialpadFragment.getDigitsWidget().setText(str);

							// / Maybe there are some spaces generated by the special widget.
							int len = mDialpadFragment.getDigitsWidget().getText().toString().length();
							mDialpadFragment.getDigitsWidget().setSelection(len);
	}

	public void openMenu() {
		// add by zhangjinqiang for al812--start
        // /added by guofeiyao
        if (menuOpened) return;
        menuOpened = true;
		// /end

		Log.d("zjq", "menu");

		//caohaolin begin
		ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
		String str = "";
		if (clipboard.hasPrimaryClip()) {
			ClipData clipData = clipboard.getPrimaryClip();
			ClipData.Item item = clipData.getItemAt(0);
			str = item.coerceToText(mContext).toString();

			str = str.replaceAll(" ", "");
		}

		Pattern pattern = Pattern.compile("([0-9]|\\+|\\*|#)+");

		if (0 == str.length() || !pattern.matcher(str).matches()) {
			if (mCallLogFragment != null && mCallLogFragment.getView() != null 
						&& mCallLogFragment.getListView().getAdapter().getCount() == 0) {
				final String[] arrayMenuItems = mContext.getResources().getStringArray(
						R.array.items_menu_four);
				AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
				builder.setItems(arrayMenuItems, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						switch (id) {
							case 0:
								// / Modified by guofeiyao
								/*
								int num = getSubInfoList();
								if (num == 2) {
									multiCardChangeCallType();
								} else {
									changeCallType();
								}
								*/
								changeCallType();
								// / End
								break;
							case 1:
							launchSpeedDial();
							break;
							
							case 2:
								blackListManager();
								break;
							case 3:
								((PeopleActivity) mContext).handleMenuSettings();
								break;
						}
					}
				}).setOnDismissListener(new OnDismissListener() {
					public void onDismiss(DialogInterface arg0) {
						menuOpened = false;
						//HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked
						menuDialogForDialer = null;
					}
				/*HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked begin*/
				//}).create().show();
				});
				menuDialogForDialer = builder.create();
				menuDialogForDialer.show();
				/*HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked end*/
			}
			else {
				final String[] arrayMenuItems = mContext.getResources().getStringArray(
						R.array.items_menu_two);
				AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
				builder.setItems(arrayMenuItems, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						switch (id) {
						case 0:
							Intent delCallLogIntent = new Intent(mContext,
									CallLogMultipleDeleteActivity.class);
							delCallLogIntent.putExtra(
									CallLogQueryHandler.CALL_LOG_TYPE_FILTER,
									mSingleCallLog);
							mContext.startActivity(delCallLogIntent);
							break;
						case 1:
							// / Modified by guofeiyao
							/*
							int num = getSubInfoList();
							if (num == 2) {
								multiCardChangeCallType();
							} else {
								changeCallType();
							}
							*/
							changeCallType();							
							// / End
							break;
						case 2:
						launchSpeedDial();
							break;
							
							case 3:
							blackListManager();
							break;
						case 4:
							((PeopleActivity) mContext).handleMenuSettings();
							break;
						}
					}
				}).setOnDismissListener(new OnDismissListener() {
					public void onDismiss(DialogInterface arg0) {
						menuOpened = false;
						//HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked
						menuDialogForDialer = null;
					}
				/*HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked begin*/
				//}).create().show();
				});
				menuDialogForDialer = builder.create();
				menuDialogForDialer.show();
				/*HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked end*/
			}

		} else {
			if (mCallLogFragment != null && mCallLogFragment.getView() != null
					    && mCallLogFragment.getListView().getAdapter().getCount() == 0) {
				final String[] arrayMenuItems = mContext.getResources().getStringArray(
						R.array.items_menu_three);
				AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
				builder.setItems(arrayMenuItems, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						switch (id) {
						case 0:
							pasteNumber();
							break;
						case 1:
							// / Modified by guofeiyao
							/*
							int num = getSubInfoList();
							if (num == 2) {
								multiCardChangeCallType();
							} else {
								changeCallType();
							}
							*/
							changeCallType();
							// / End
							break;
						case 2:
						
						launchSpeedDial();
							break;
							
							case 3:
							blackListManager();
							break;
						case 4:
							((PeopleActivity) mContext).handleMenuSettings();
							break;
						}
					}
				}).setOnDismissListener(new OnDismissListener() {
					public void onDismiss(DialogInterface arg0) {
						menuOpened = false;
						//HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked
						menuDialogForDialer = null;
					}
				/*HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked begin*/
				//}).create().show();
				});
				menuDialogForDialer = builder.create();
				menuDialogForDialer.show();
				/*HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked end*/
			}
			else {
				final String[] arrayMenuItems = mContext.getResources().getStringArray(
						R.array.items_menu_one);
				AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
				builder.setItems(arrayMenuItems, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						switch (id) {
						case 0:
							//caohaolin begin
							pasteNumber();
							//caohaolin end
							break;
						case 1:
							Intent delCallLogIntent = new Intent(mContext,
									CallLogMultipleDeleteActivity.class);
							delCallLogIntent.putExtra(
									CallLogQueryHandler.CALL_LOG_TYPE_FILTER,
									mSingleCallLog);
							mContext.startActivity(delCallLogIntent);
							break;
						case 2:
							// / Modified by guofeiyao
							/*
							int num = getSubInfoList();
							if (num == 2) {
								multiCardChangeCallType();
							} else {
								changeCallType();
							}
							*/
							changeCallType();
							// / End
							break;
						case 3:
						launchSpeedDial();
							break;
							
							case 4:
							blackListManager();
							break;
						case 5:
							((PeopleActivity) mContext).handleMenuSettings();
							break;
						}
					}
				}).setOnDismissListener(new OnDismissListener() {
					public void onDismiss(DialogInterface arg0) {
						menuOpened = false;
						//HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked
						menuDialogForDialer = null;
					}
				/*HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked begin*/
				//}).create().show();
				});
				menuDialogForDialer = builder.create();
				menuDialogForDialer.show();
				/*HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked end*/
			}
		}
		// add by zhangjinqiang for al812--end
	}

	// add by zhangjinqiang for al812-start
	private AlertDialog callTypeDialog;

	public void changeCallType() {
		final String[] callTypeItems = mContext.getResources().getStringArray(
				R.array.items_call_type_menu);
		AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
		builder.setTitle(R.string.call_type_hw)
				.setSingleChoiceItems(callTypeItems, choiceItemId, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int itemId) {
						// TODO Auto-generated method stub
						switch (itemId) {
							case 0:
								if (itemId != choiceItemId && mCallLogFragment != null) {
									mSingleCallLog = CALL_TYPE_ALL;
									// / Modified by guofeiyao
									mCallLogFragment.updateCallList(CALL_TYPE_ALL, 0, SIM_ALL);
									// / End
								} else {
									dialog.cancel();
									return;
								}
								break;
							case 1:
								if (itemId != choiceItemId && mCallLogFragment != null) {
									mSingleCallLog = CALL_TYPE_MISS;
									// / Modified by guofeiyao
									mCallLogFragment.updateCallList(CALL_TYPE_MISS, 0, SIM_ALL);
									// / End
								} else {
									dialog.cancel();
									return;
								}
								break;
							case 2:
								if (itemId != choiceItemId && mCallLogFragment != null) {
									mSingleCallLog = CALL_TYPE_OUTGOING;
									// / Modified by guofeiyao
									mCallLogFragment.updateCallList(CALL_TYPE_OUTGOING, 0, SIM_ALL);
									// / End
								} else {
									dialog.cancel();
									return;
								}
								break;
							case 3:
								if (itemId != choiceItemId && mCallLogFragment != null) {
									mSingleCallLog = CALL_TYPE_INCOMING;
									// / Modified by guofeiyao
									mCallLogFragment.updateCallList(CALL_TYPE_INCOMING, 0, SIM_ALL);
									// / End
								} else {
									dialog.cancel();
									return;
								}
								break;
						}
						choiceItemId = itemId;
						dialog.cancel();
					}
				});

		callTypeDialog = builder.create();
		callTypeDialog.show();
	}

	private void multiCardChangeCallType(){
		dialog = new AlertDialog.Builder(mContext).create();
		dialog.show();
		Window window = dialog.getWindow(); 
		WindowManager wm = ((Activity)mContext).getWindowManager();
		Display d = wm .getDefaultDisplay();
		WindowManager.LayoutParams dialogparams = window.getAttributes();  
		dialogparams.height = (int) (d.getHeight() * 0.5);
		dialogparams.width = (int) (d.getWidth() * 0.85);
		window.setAttributes(dialogparams);
		
		window.setContentView(R.layout.view_call_type_hw);  
		simAll = (RadioButton)window.findViewById(R.id.sim_all);
		simOne = (RadioButton)window.findViewById(R.id.sim_one);
		simTwo = (RadioButton)window.findViewById(R.id.sim_two);
		
		allCall = (RadioButton)window.findViewById(R.id.all_call);
		missCall = (RadioButton)window.findViewById(R.id.miss_call);
		answerCall = (RadioButton)window.findViewById(R.id.answer_call);
		dialerCall = (RadioButton)window.findViewById(R.id.dialer_call);
		updateSelectedRadio();
		
		simGroup = (RadioGroup)window.findViewById(R.id.sim_group);
		simGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(RadioGroup group, int checkId) {
				// TODO Auto-generated method stub
				if(checkId == simAll.getId()){
					mSimType=SIM_ALL;
					sSimAll = true;
					sSimOne=false;
					sSimTwo=false;
				}else if(checkId==simOne.getId()){
					mSimType = SIM_ONE;
					sSimAll = false;
					sSimOne=true;
					sSimTwo=false;
				}else if(checkId == simTwo.getId()){
					mSimType = SIM_TWO;
					sSimAll = false;
					sSimOne=false;
					sSimTwo=true;
				}
			}
		});
		
		callGroup = (RadioGroup)window.findViewById(R.id.call_type_group);
		callGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(RadioGroup group, int checkId) {
				// TODO Auto-generated method stub
				if(checkId == allCall.getId()){
					mCallLog=CALL_TYPE_ALL;
					sCallAll = true;
					sCallIncoming=false;
					sCallMiss=false;
					sCallOutgoing=false;
				}else if (checkId == missCall.getId()){
					mCallLog = CALL_TYPE_MISS;
					sCallAll=false;
					sCallIncoming=false;
					sCallMiss=true;
					sCallOutgoing=false;
				}else if (checkId == answerCall.getId()){
					mCallLog = CALL_TYPE_INCOMING;
					sCallAll=false;
					sCallIncoming=true;
					sCallMiss=false;
					sCallOutgoing=false;
				}else if(checkId == dialerCall.getId()){
					mCallLog = CALL_TYPE_OUTGOING;
					sCallAll=false;
					sCallIncoming=false;
					sCallMiss=false;
					sCallOutgoing=true;
				}
			}
		});
		
		cancel = (Button)window.findViewById(R.id.cancel);
		cancel.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				dialog.dismiss();
			}
		});
		
		confirm=(Button)window.findViewById(R.id.confirm);
		confirm.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				if(mCallLogFragment !=null){
					saveCallLogType();
					mCallLogFragment.updateCallList(mCallLog,0,mSimType);
					mCallLogFragment.refreshData();
				}
				dialog.dismiss();
			}
		});
	
	}

	private void saveCallLogType() {
		SharedPreferences sharedPreferences = mContext.getSharedPreferences("CallLog_type", Activity.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt("CallLog", mCallLog);
		editor.putString("SimType", mSimType);
		editor.commit();
	}
	
	private void updateSelectedRadio(){
		if(sSimAll){
			simAll.setChecked(true);
		}else if(sSimOne){
			simOne.setChecked(true);
		}else{
			simTwo.setChecked(true);
		}

		if(sCallAll)
			allCall.setChecked(true);
		else if(sCallIncoming)
			answerCall.setChecked(true);
		else if(sCallMiss)
			missCall.setChecked(true);
		else if(sCallOutgoing)
			dialerCall.setChecked(true);
	}

	private int getSubInfoList() {
        mSubInfoList = SubscriptionManager.from(mContext).getActiveSubscriptionInfoList();
        mSubCount = (mSubInfoList != null && !mSubInfoList.isEmpty()) ? mSubInfoList.size() : 0;
		return mSubCount;
    }

	public void blackListManager(){
		try {
				ComponentName componetName = new ComponentName("com.huawei.systemmanager","com.huawei.harassmentinterception.ui.InterceptionActivity"); 
				Intent LifeServiceIntent = new Intent();
				LifeServiceIntent.setComponent(componetName);
				mContext.startActivity(LifeServiceIntent);
			} catch (Exception e) {
				// TODO: handle exception
				Toast.makeText(mContext, R.string.black_list_manager_exception,
						Toast.LENGTH_SHORT).show();
			}
	}
	
	//add by zhangjinqiang for al812-end


	/**
	 * Returns an appropriate call origin for this Activity. May return null
	 * when no call origin should be used (e.g. when some 3rd party application
	 * launched the screen. Call origin is for remembering the tab in which the
	 * user made a phone call, so the external app's DIAL request should not be
	 * counted.)
	 */
	public String getCallOrigin() {
		return !isDialIntent(((Activity) mContext).getIntent()) ? CALL_ORIGIN_DIALTACTS
				: null;
	}

	/**
	 * Returns true if the given intent contains a phone number to populate the
	 * dialer with
	 */
	private boolean isDialIntent(Intent intent) {
		final String action = intent.getAction();
		if (Intent.ACTION_DIAL.equals(action)
				|| ACTION_TOUCH_DIALER.equals(action)) {
			return true;
		}
		if (Intent.ACTION_VIEW.equals(action)) {
			final Uri data = intent.getData();
			if (data != null
					&& PhoneAccount.SCHEME_TEL.equals(data.getScheme())) {
				return true;
			}
		}
		// if (){
		// return true;
		// }
		return false;
	}
	/*HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked begin*/
	public void dismissDialog(){
		if( menuDialogForDialer != null && menuDialogForDialer.isShowing()){
			Log.d("dismissdialog","dismiss dialer menu dialog");
			menuDialogForDialer.dismiss();
			menuDialogForDialer = null;
		}

		if (callTypeDialog != null && callTypeDialog.isShowing()) {
			callTypeDialog.dismiss();
			callTypeDialog = null;
		} else {
			Log.i("dismissdialog", "dismiss callTypeDialog fail.");
		}
	}
	/*HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked end*/
}

