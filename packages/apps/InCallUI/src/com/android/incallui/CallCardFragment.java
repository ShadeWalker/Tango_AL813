/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2013 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.incallui;

import android.app.Fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.telecom.DisconnectCause;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;
import com.android.contacts.common.widget.FloatingActionButtonController;
import com.android.incallui.service.PhoneNumberService;
import com.android.phone.common.animation.AnimUtils;
import com.mediatek.incallui.ext.ExtensionManager;

import java.util.List;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.graphics.PorterDuff.Mode;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.RectF;
import android.graphics.Color;

import android.content.res.Resources;
import android.widget.Toast;

import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
/*
import com.cootek.smartdialer_oem_module.sdk.CooTekPhoneService;
import com.cootek.smartdialer_oem_module.sdk.element.CallerIdDetail;
import com.cootek.smartdialer_oem_module_demo.CooTekSDKUtils;
*/
import android.content.res.Resources;
import java.util.ArrayList;
import java.util.List;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.provider.Settings;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import java.util.Locale;
import android.os.SystemProperties;

// / Added by guofeiyao
import android.telephony.TelephonyManager;
// / End

/**
 * Fragment for call card.
 */
public class CallCardFragment extends BaseFragment<CallCardPresenter, CallCardPresenter.CallCardUi>
        implements CallCardPresenter.CallCardUi {

    private AnimatorSet mAnimatorSet;
    private int mRevealAnimationDuration;
    private int mShrinkAnimationDuration;
    private int mFabNormalDiameter;
    private int mFabSmallDiameter;
    private boolean mIsLandscape;
    private boolean mIsDialpadShowing;

    // Primary caller info
    private TextView mPhoneNumber;
    private TextView mNumberLabel;
    private TextView mPrimaryName;
    private View mCallStateButton;
    private ImageView mCallStateIcon;
    private ImageView mCallStateVideoCallIcon;
    private TextView mCallStateLabel;
    private TextView mCallTypeLabel;
    private View mCallNumberAndLabel;
    private ImageView mPhoto;
    private TextView mElapsedTime;
    private Drawable mPrimaryPhotoDrawable;

    // Container view that houses the entire primary call card, including the call buttons
    private View mPrimaryCallCardContainer;
    // Container view that houses the primary call information
    private ViewGroup mPrimaryCallInfo;
    private View mCallButtonsContainer;

    private View mOtherCallInfo;
    // Secondary caller info
    private CallInfoView mSecondaryCallInfo;
    private TextView mSecondaryCallName;
    private View mSecondaryCallProviderInfo;
    private TextView mSecondaryCallProviderLabel;
    private View mSecondaryCallConferenceCallIcon;
    private View mProgressSpinner;

    // Third caller info
    private CallInfoView mThirdCallInfo;
    private View mManageConferenceCallButton;

    // Dark number info bar
    private TextView mInCallMessageLabel;

    private FloatingActionButtonController mFloatingActionButtonController;
    private View mFloatingActionButtonContainer;
    private ImageButton mFloatingActionButton;
    private int mFloatingActionButtonVerticalOffset;

    // Cached DisplayMetrics density.
    private float mDensity;

    private float mTranslationOffset;
    private Animation mPulseAnimation;

    private int mVideoAnimationDuration;

    private MaterialPalette mCurrentThemeColors;
    /// M: For second/third call color @{
    private int mCurrentSecondCallColor;
    private int mCurrentThirdCallColor;
    /// @}

    /// M: recording indication icon
    private ImageView mVoiceRecorderIcon;


//add by zhangjinqiang for al812 circlePhoto --start
private TextView mFirstCallName;
private TextView mFirstElapsedTime;
private TextView mFirstCallStateLabel;
private TextView mLocator;
private View callCardHW;

private CirclePhoto circlePhoto;
private ImageView circle;
private CircleProgress circleProgress;
private CallButtonFragment scallButtonFragment;
private ImageView dialpd,speakerOn,simView;
private View keyContainerHW;
private View imageInfoHW;

private TextView firstName,callTime,firstStatus,secondName,secondStatus,thirdName,thirdStatus,simText,digitsNumber;
private View textInfoHW,primayInfoHW,secondInfo,thirdInfo;
private boolean flag=true;
private String TAG="zhangjinqiang";
private	List<String> ECC = new ArrayList<String>();
private int simNum=-1;
private List<SubscriptionInfo> mSubInfoList;
private int mSubCount;
private boolean isNumber=true;
private String eccNumber=null;
private Activity activity;
private String chinaMobile,chinaUnicom,chinaTelecom,strangeOperator,locatorProvince,locatorCity;
private boolean isDisplayDialpad;
//add by zhangjinqiang for al812 circlePhoto --end

//private boolean firstShow=false;

    @Override
    CallCardPresenter.CallCardUi getUi() {
        return this;
    }

    @Override
    CallCardPresenter createPresenter() {
        return new CallCardPresenter();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		getSubInfoList();
        mRevealAnimationDuration = getResources().getInteger(R.integer.reveal_animation_duration);
        mShrinkAnimationDuration = getResources().getInteger(R.integer.shrink_animation_duration_incall);
        mVideoAnimationDuration = getResources().getInteger(R.integer.video_animation_duration);
        mFloatingActionButtonVerticalOffset = getResources().getDimensionPixelOffset(
                R.dimen.floating_action_bar_vertical_offset);
        mFabNormalDiameter = getResources().getDimensionPixelOffset(
                R.dimen.end_call_floating_action_button_diameter);
        mFabSmallDiameter = getResources().getDimensionPixelOffset(
                R.dimen.end_call_floating_action_button_small_diameter);
		//add by zhangjinqiang for al812 start
		chinaMobile = getResources().getString(R.string.china_mobile);
		chinaUnicom = getResources().getString(R.string.china_unicom);
		chinaTelecom = getResources().getString(R.string.china_telecom);
		strangeOperator = getResources().getString(R.string.strange_operator);
		locatorProvince = getResources().getString(R.string.locator_province);
		locatorCity =getResources().getString(R.string.locator_city);
 		//add by zhangjinqiang for al812 end
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final CallList calls = CallList.getInstance();
        final Call call = calls.getFirstCall();
        getPresenter().init(getActivity(), call);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        mDensity = getResources().getDisplayMetrics().density;
        mTranslationOffset =
                getResources().getDimensionPixelSize(R.dimen.call_card_anim_translate_y_offset);

				//add by zhangjinqiang for al812 ui --start
		if(InCallApp.gIsHwUi){
			final View parent = inflater.inflate(R.layout.call_card_content_hw,container,false);
			return parent;
			
		}else{
			return inflater.inflate(R.layout.call_card_content, container, false);
		}
				//add by zhangjinqiang for al812 ui--end
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPulseAnimation =
                AnimationUtils.loadAnimation(view.getContext(), R.anim.call_status_pulse);

        mPhoneNumber = (TextView) view.findViewById(R.id.phoneNumber);
        mPrimaryName = (TextView) view.findViewById(R.id.name);
        mCallStateLabel = (TextView) view.findViewById(R.id.callStateLabel);
	mFloatingActionButtonContainer = view.findViewById(R.id.floating_end_call_action_button_container);
		//add by zhangjinqiang for al812 ui --start
		Resources resource = getResources();
		final Drawable dialpad = resource.getDrawable(R.drawable.ic_dialpad);
		final Drawable dialpadOn = resource.getDrawable(R.drawable.ic_dialpad_on);
		
	if(InCallApp.gIsHwUi){
		circle = (ImageView)view.findViewById(R.id.circle);
		circlePhoto = (CirclePhoto)view.findViewById(R.id.photo);
		circleProgress = (CircleProgress)view.findViewById(R.id.circleProgress);
		callCardHW=view.findViewById(R.id.call_card_content_hw);
		imageInfoHW= view.findViewById(R.id.image_info_hw);
		primayInfoHW=view.findViewById(R.id.primayinfo_hw);
		textInfoHW = view.findViewById(R.id.text_info_hw);
		firstName=(TextView)view.findViewById(R.id.firstname);
		callTime=(TextView)view.findViewById(R.id.time);
		firstStatus=(TextView)view.findViewById(R.id.firststatus);
		secondName=(TextView)view.findViewById(R.id.secondname);
		secondStatus=(TextView)view.findViewById(R.id.secondstatus);
		mLocator = (TextView)view.findViewById(R.id.locator);
		simText=(TextView)view.findViewById(R.id.sim_txt);
		simView=(ImageView)view.findViewById(R.id.sim_img);

		digitsNumber=(TextView)view.findViewById(R.id.digits_number);

		secondInfo = (View)view.findViewById(R.id.secondcallinfo);
		secondInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().secondaryInfoClicked();
                //updateFabPositionForSecondaryCallInfo();
            }
        });

		thirdName=(TextView)view.findViewById(R.id.thirdname);
		thirdStatus=(TextView)view.findViewById(R.id.thirdstatus);
		thirdInfo=(View)view.findViewById(R.id.thirdcallinfo);
		thirdInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().thirdInfoClicked();
                //updateFabPositionForSecondaryCallInfo();
            }
        });
		
		keyContainerHW = view.findViewById(R.id.keyContainer);

		 Animation operatingAnim = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.rotate);  
	   	 AccelerateDecelerateInterpolator acce = new AccelerateDecelerateInterpolator();
	   	 operatingAnim.setInterpolator(acce);
	  	  circle.startAnimation(operatingAnim);

		scallButtonFragment =CallButtonFragment.getInstance();
		speakerOn=(ImageView)view.findViewById(R.id.speakerOn);
		speakerOn.setOnClickListener(new View.OnClickListener() {
	            @Override
	            public void onClick(View v) {
	                scallButtonFragment.onAudioButtonClicked();
	            }
	        });
		
		dialpd=(ImageView)view.findViewById(R.id.dialpd);
		dialpd.setOnClickListener(new View.OnClickListener() {
	            @Override
	            public void onClick(View v) {
			 if (getActivity() != null && getActivity() instanceof InCallActivity) {
			 	if(!v.isSelected()){
					v.setSelected(true);
					dialpd.setImageDrawable(dialpadOn);
					keyContainerHW.setVisibility(View.GONE);
					/*
					if (firstShow) {
						mPrimaryName.setVisibility(View.GONE);
						digitsNumber.setVisibility(View.VISIBLE);
					}
					*/
					mFloatingActionButtonContainer.setBackgroundResource(R.color.end_call_container_hw_dialpad_background);
		            	((InCallActivity) getActivity()).displayDialpad(true, true);
					isDisplayDialpad=true;
			 	}else{
			 		v.setSelected(false);
					dialpd.setImageDrawable(dialpad);
					keyContainerHW.setVisibility(View.VISIBLE);
					/*
					digitsNumber.setVisibility(View.GONE);
					mPrimaryName.setVisibility(View.VISIBLE);
					firstShow = true;
					*/
					mFloatingActionButtonContainer.setBackgroundResource(R.color.end_call_container_hw_background);
					((InCallActivity) getActivity()).displayDialpad(false, true);
					isDisplayDialpad=false;
				}
		          }
				
	            }
	        });
	}
	//add by zhangjinqiang for al812 ui --end
	mNumberLabel = (TextView) view.findViewById(R.id.label);
        mOtherCallInfo = view.findViewById(R.id.other_call_info_container);
        mSecondaryCallInfo = (CallInfoView) view.findViewById(R.id.secondary_call_info);
        mSecondaryCallProviderInfo = view.findViewById(R.id.secondary_call_provider_info);
        mThirdCallInfo = (CallInfoView) view.findViewById(R.id.third_call_info);
        mPhoto = (ImageView) view.findViewById(R.id.photo);
        mCallStateIcon = (ImageView) view.findViewById(R.id.callStateIcon);
        mCallStateVideoCallIcon = (ImageView) view.findViewById(R.id.videoCallIcon);
        mCallNumberAndLabel = view.findViewById(R.id.labelAndNumber);
        mCallTypeLabel = (TextView) view.findViewById(R.id.callTypeLabel);
        mElapsedTime = (TextView) view.findViewById(R.id.elapsedTime);
        mPrimaryCallCardContainer = view.findViewById(R.id.primary_call_info_container);
        mPrimaryCallInfo = (ViewGroup) view.findViewById(R.id.primary_call_banner);
        mInCallMessageLabel = (TextView) view.findViewById(R.id.connectionServiceMessage);
        mProgressSpinner = view.findViewById(R.id.progressSpinner);

        
        mFloatingActionButton = (ImageButton) view.findViewById(
                R.id.floating_end_call_action_button);
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().endCallClicked();
            }
        });
        mFloatingActionButtonController = new FloatingActionButtonController(getActivity(),
                mFloatingActionButtonContainer, mFloatingActionButton);

        mSecondaryCallInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().secondaryInfoClicked();
                updateFabPositionForSecondaryCallInfo();
            }
        });

        mThirdCallInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().thirdInfoClicked();
            }
        });

        mCallStateButton = view.findViewById(R.id.callStateButton);
        mCallStateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().onCallStateButtonTouched();
            }
        });

        mManageConferenceCallButton = view.findViewById(R.id.manage_conference_call_button);
        mManageConferenceCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(this, "[onClick] mManageConferenceCallButton.onclick func.");
                InCallActivity activity = (InCallActivity) getActivity();
                activity.showConferenceCallManager(true);
            }
        });

        mPrimaryName.setElegantTextHeight(false);
        mCallStateLabel.setElegantTextHeight(false);

        //add for plug in. @{
        ExtensionManager.getCallCardExt().onViewCreated(InCallPresenter.getInstance().getContext(), view);
        ExtensionManager.getRCSeCallCardExt().onViewCreated(InCallPresenter.getInstance().getContext(), view);
        //add for plug in. @}

        /// M: Add for recording.
        initVoiceRecorderIcon(view);
    }

    @Override
    public void setVisible(boolean on) {
        if (on) {
            getView().setVisibility(View.VISIBLE);
        } else {
            getView().setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Hides or shows the progress spinner.
     *
     * @param visible {@code True} if the progress spinner should be visible.
     */
    @Override
    public void setProgressSpinnerVisible(boolean visible) {
        mProgressSpinner.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Sets the visibility of the primary call card.
     * Ensures that when the primary call card is hidden, the video surface slides over to fill the
     * entire screen.
     *
     * @param visible {@code True} if the primary call card should be visible.
     */
    @Override
    public void setCallCardVisible(final boolean visible) {
        // When animating the hide/show of the views in a landscape layout, we need to take into
        // account whether we are in a left-to-right locale or a right-to-left locale and adjust
        // the animations accordingly.
        final boolean isLayoutRtl = InCallPresenter.isRtl();

        // Retrieve here since at fragment creation time the incoming video view is not inflated.
        final View videoView = getView().findViewById(R.id.incomingVideo);

        // Determine how much space there is below or to the side of the call card.
        final float spaceBesideCallCard = getSpaceBesideCallCard();

        // We need to translate the video surface, but we need to know its position after the layout
        // has occurred so use a {@code ViewTreeObserver}.
        final ViewTreeObserver observer = getView().getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                // We don't want to continue getting called.
                if (observer.isAlive()) {
                    observer.removeOnPreDrawListener(this);
                }

                float videoViewTranslation = 0f;

                // Translate the call card to its pre-animation state.
                if (mIsLandscape) {
                    float translationX = mPrimaryCallCardContainer.getWidth();
                    translationX *= isLayoutRtl ? 1 : -1;

                    mPrimaryCallCardContainer.setTranslationX(visible ? translationX : 0);

                    if (visible) {
                        videoViewTranslation = videoView.getWidth() / 2 - spaceBesideCallCard / 2;
                        videoViewTranslation *= isLayoutRtl ? -1 : 1;
                    }
                } else {
                    mPrimaryCallCardContainer.setTranslationY(visible ?
                            -mPrimaryCallCardContainer.getHeight() : 0);

                    if (visible) {
                        videoViewTranslation = videoView.getHeight() / 2 - spaceBesideCallCard / 2;
                    }
                }

                // Perform animation of video view.
                ViewPropertyAnimator videoViewAnimator = videoView.animate()
                        .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                        .setDuration(mVideoAnimationDuration);
                if (mIsLandscape) {
                    videoViewAnimator
                            .translationX(videoViewTranslation)
                            .start();
                } else {
                    videoViewAnimator
                            .translationY(videoViewTranslation)
                            .start();
                }
                videoViewAnimator.start();

                // Animate the call card sliding.
                ViewPropertyAnimator callCardAnimator = mPrimaryCallCardContainer.animate()
                        .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                        .setDuration(mVideoAnimationDuration)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                if (!visible) {
                                    mPrimaryCallCardContainer.setVisibility(View.GONE);
                                }
                            }

                            @Override
                            public void onAnimationStart(Animator animation) {
                                super.onAnimationStart(animation);
                                if (visible) {
                                    mPrimaryCallCardContainer.setVisibility(View.VISIBLE);
                                }
                            }
                        });

                if (mIsLandscape) {
                    float translationX = mPrimaryCallCardContainer.getWidth();
                    translationX *= isLayoutRtl ? 1 : -1;
                    callCardAnimator
                            .translationX(visible ? 0 : translationX)
                            .start();
                } else {
                    callCardAnimator
                            .translationY(visible ? 0 : -mPrimaryCallCardContainer.getHeight())
                            .start();
                }

                return true;
            }
        });
    }

    /**
     * Determines the amount of space below the call card for portrait layouts), or beside the
     * call card for landscape layouts.
     *
     * @return The amount of space below or beside the call card.
     */
    public float getSpaceBesideCallCard() {
        if (mIsLandscape) {
            return getView().getWidth() - mPrimaryCallCardContainer.getWidth();
        } else {
            return getView().getHeight() - mPrimaryCallCardContainer.getHeight();
        }
    }

    @Override
    public void setPrimaryName(String name, boolean nameIsNumber) {
        if (TextUtils.isEmpty(name)) {
            mPrimaryName.setText(null);
     	   firstName.setText(null);
        } else {
            mPrimaryName.setText(nameIsNumber
                    ? PhoneNumberUtils.ttsSpanAsPhoneNumber(name)
                    : name);
		firstName.setText(nameIsNumber
	                    ? PhoneNumberUtils.ttsSpanAsPhoneNumber(name)
	                    : name);
	
            // Set direction of the name field
            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mPrimaryName.setTextDirection(nameDirection);
        }
    }

    @Override
    public void setPrimaryImage(Drawable image) {

        if (image != null) {
    /* HQ_tanyali 2015-8-6 modified for al812 contact photo in InCall UI can not update */
    /*  if(InCallApp.gIsCustomerUi){
			System.out.println("CallCardFragment -- setPrimaryImage--if--zjq");
			// showImageYep(mPhoto, image);
		}else{*/
			System.out.println("CallCardFragment -- setPrimaryImage--else--zjq");
			 setDrawableToImageView(mPhoto, image);
	//	}/* HQ_tanyali 2015-8-6 modified end */
        }
    }

	public void setPrimaryPhoneNumber(String location,String name,String number){
        		//modify by zhangjinqiang for al812 start
        		mLocator.setText(location);
        		if(!ECC.contains(name)||TextUtils.isEmpty(name)){
				mPrimaryName.setText(number);
				mPhoneNumber.setText(R.string.strange_number);
            		mPhoneNumber.setVisibility(View.VISIBLE);
            		mPhoneNumber.setTextDirection(View.TEXT_DIRECTION_LTR);
        		}else{
        			mPrimaryName.setText(name);
				mPhoneNumber.setText(number);
            		mPhoneNumber.setVisibility(View.VISIBLE);
            		mPhoneNumber.setTextDirection(View.TEXT_DIRECTION_LTR);
			}
		   //modify by zhangjinqiang for al812 end
	}

    @Override
    public void setPrimaryPhoneNumber(String number) {
        // Set the number
        if (TextUtils.isEmpty(number)) {
            mPhoneNumber.setText(null);
            mPhoneNumber.setVisibility(View.GONE);
        } else {
            mPhoneNumber.setText(PhoneNumberUtils.ttsSpanAsPhoneNumber(number));
            mPhoneNumber.setVisibility(View.VISIBLE);
            mPhoneNumber.setTextDirection(View.TEXT_DIRECTION_LTR);
        }
    }

    @Override
    public void setPrimaryLabel(String label) {
        if (!TextUtils.isEmpty(label)) {
            mNumberLabel.setText(label);
            mNumberLabel.setVisibility(View.VISIBLE);
        } else {
            mNumberLabel.setVisibility(View.GONE);
        }

    }

	public void setSimLable(int num){
		Resources resource = getActivity().getResources();
		final Drawable simOne = resource.getDrawable(R.drawable.sim_card_one);
		final Drawable simTwo = resource.getDrawable(R.drawable.sim_card_two);

		if(mSubInfoList != null&&!mSubInfoList.isEmpty()){
			if( mSubCount > 1 ){
				if(num==0){
					CharSequence simName = mSubInfoList.get(0).getCarrierName();
					if (simName == null || "".equals(simName)) {
						simName = mSubInfoList.get(0).getDisplayName();
					}
					String mSimName = checkSimName(simName.toString());
					simView.setImageDrawable(simOne);
					simText.setText(mSimName);
				}else if(num==1){
					CharSequence simName = mSubInfoList.get(1).getCarrierName();
					if (simName == null || "".equals(simName)) {
						simName = mSubInfoList.get(1).getDisplayName();
					}
					String mSimName = checkSimName(simName.toString());
					simView.setImageDrawable(simTwo);
					simText.setText(mSimName);
				}
			}else if(mSubCount==1&&num==1){//only exist SIM2
					CharSequence simName = mSubInfoList.get(0).getCarrierName();
					if (simName == null || "".equals(simName)) {
						simName = mSubInfoList.get(0).getDisplayName();
					}
					String mSimName = checkSimName(simName.toString());
					simView.setImageDrawable(simTwo);
					simText.setText(mSimName);
			}else if( mSubCount > 0 ){
				CharSequence simName = mSubInfoList.get(0).getCarrierName();
				if (simName == null || "".equals(simName)) {
					simName = mSubInfoList.get(0).getDisplayName();
				}
				String mSimName = checkSimName(simName.toString());
				simView.setImageDrawable(simOne);
				simText.setText(mSimName);
			}else{
				simView.setImageDrawable(simOne);
				simText.setText(R.string.only_call_ecc);
			}
		}else{
				simView.setImageDrawable(simOne);
				simText.setText(R.string.only_call_ecc);
		}

		// / Added by guofeiyao 2015/11/27
		// For single sim version
		if( !((TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE)).isMultiSimEnabled() ) {
            simView.setImageDrawable(null);
		}
		// / End
	}

	public void setPrimaryPhoneNumberAndName(String name,String number){
			//add by zhangjinqiang for HQ01369615--start
			digitsNumber.setVisibility(View.GONE);
			mPrimaryName.setVisibility(View.VISIBLE);			//add by zjq end
			String langage = Locale.getDefault().getLanguage();//add by lipeng for number display
			if(TextUtils.isEmpty(name)){
				 if(false){
				 	/*
					String[] numArray = {number};
					callDetail=sdk.queryCallerIdDetail(numArray, CooTekPhoneService.SCENE_INOUT_CALL, callerIdType);
					if(callDetail!=null && callDetail.length>0 && callDetail[0]!=null&&callDetail[0].getName()!=null){
						Log.d("setPrimaryPhoneNumberAndName",callDetail[0].getName());
						mPrimaryName.setText(callDetail[0].getName());
						mPhoneNumber.setText(number);
						firstName.setText(callDetail[0].getName());
					}else{
						firstName.setText(number);
						mPrimaryName.setText(number);
						mPhoneNumber.setText(R.string.strange_number);
		            		mPhoneNumber.setVisibility(View.VISIBLE);
		            		mPhoneNumber.setTextDirection(View.TEXT_DIRECTION_LTR);
					}
					*/
				}else{
					if(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw")){///RTL language{
					    firstName.setText("\u202D"+number+"\u202C"); //modify by yulifeng 20151020
						mPrimaryName.setText("\u202D"+number+"\u202C");//modify by yulifeng 20151020
					}else{
						firstName.setText(number);
						mPrimaryName.setText(number);
					}

			        mPhoneNumber.setText(R.string.strange_number);
	            		mPhoneNumber.setVisibility(View.VISIBLE);
	            		mPhoneNumber.setTextDirection(View.TEXT_DIRECTION_LTR);
					}
        		}else{
				firstName.setText(name);
				//add by zhaizhanfeng for HQ01308455 & HQ01571921 at 151020 start
				if("voicemail".equals(name) || "Claro Mensajes".equals(name)){
					String namevoicemail = getString(R.string.voicemail_call_dialog_number_for_display);
        			mPrimaryName.setText(namevoicemail);
					}else{
					mPrimaryName.setText(name);
					}
				//add by zhaizhanfeng for HQ01308455 at 151020 end
				if(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw")){///RTL language{
					mPhoneNumber.setText("\u202D"+number+"\u202C");//modify by yulifeng 20151020
				}else{
					mPhoneNumber.setText(number);
				}

            		mPhoneNumber.setVisibility(View.VISIBLE);
            		mPhoneNumber.setTextDirection(View.TEXT_DIRECTION_LTR);
			}
            /*HQ_guomiao add for HQ01488424 begin*/
	if(number!=null){
           if (PhoneNumberUtils.isEmergencyNumber(number)||number.equals("171")) {
	     String mccmnc=PhoneNumberUtils.getSimMccMnc();
	     String operatorMccmnc=PhoneNumberUtils.getOperatorMccmnc();//modify by wangmingyue for HQ01617491
	     if(Build.TYPE.equals("eng")){
	    	 Log.i("tang", "mccmnc"+mccmnc);
	    	 Log.i("tang", "operatorMccmnc"+operatorMccmnc);
	     }
            if (SystemProperties.get("ro.hq.emergency.display.latin").equals("1")) {
		String language = Locale.getDefault().getLanguage();
                if (("911".equalsIgnoreCase(number) ||"112".equalsIgnoreCase(number)) && ("73401".equalsIgnoreCase(mccmnc) || "73402".equalsIgnoreCase(mccmnc) ||"73403".equalsIgnoreCase(mccmnc))){
                           mPrimaryName.setText("Emergencia");
		} else if ("171".equalsIgnoreCase(number)&& ("73404".equalsIgnoreCase(mccmnc) )) {
			if (langage.startsWith("en") || langage.startsWith("es")) {
			      mPrimaryName.setText("Policia");
			} else {
			       mPrimaryName.setText(R.string.emergency_call_dialog_number_for_display);
			}
		   } else if ("171".equalsIgnoreCase(number) &&( "73401".equalsIgnoreCase(mccmnc) ||"73402".equalsIgnoreCase(mccmnc) ||"73403".equalsIgnoreCase(mccmnc))) {
                            if (langage.startsWith("en") || langage.startsWith("es")) {
                                mPrimaryName.setText( "Emergencia Nacional");   
                             }else{
                                     mPrimaryName.setText(R.string.emergency_call_dialog_number_for_display);
                             }		
                     } else if ("*767".equalsIgnoreCase(number) && ("73401".equalsIgnoreCase(mccmnc) ||"73402".equalsIgnoreCase(mccmnc) ||"73403".equalsIgnoreCase(mccmnc))) {
                                  name = "Emergencia 412";
                     } else if ("190".equalsIgnoreCase(number) && "724".equalsIgnoreCase(mccmnc.substring(0, 3))) {
                                  mPrimaryName.setText("Polícia");
                      } else {
                    	  //modify by wangmingyue for HQ01617491 start
                    	  if (("911".equalsIgnoreCase(number) ||"112".equalsIgnoreCase(number)) && ("73401".equalsIgnoreCase(operatorMccmnc) || "73402".equalsIgnoreCase(operatorMccmnc) ||"73403".equalsIgnoreCase(operatorMccmnc))){
                              mPrimaryName.setText("Emergencia");
   		                  } else {
   		                	  mPrimaryName.setText(R.string.emergency_call_dialog_number_for_display);
   		                  }
                          //modify by wangmingyue for HQ01617491 end
                      }
		     if(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw")){///RTL language{
                              mPhoneNumber.setText("\u202D"+number+"\u202C");
                      }else{
                              mPhoneNumber.setText(number);
                      }
                }
            /*HQ_guomiao add for HQ01488424 end*/
            
            // [add for HQ01545860 by lizhao at 20151207 begain
	    if(SystemProperties.get("ro.hq.emergency.display.latin").equals("1")){
		    int slotId = PhoneNumberUtils.getSlotId();
		    if (slotId != -1) {
		    	mccmnc = PhoneNumberUtils.getSimMccMnc(slotId);
		    	String language = Locale.getDefault().getLanguage();
		    	if (Build.TYPE.equals("eng")) {
		    		Log.i(this, "mccmnc=" + mccmnc + ",number=" + number
		    				+ ",language=" + language);
		    	}
		    	if (mccmnc != null && !"".equals(mccmnc)) {
		    		if (mccmnc.startsWith("734") && "*767".equals(number)
		    				&& ("en".equals(language) || "es".equals(language))) {
		    			mPrimaryName
		    			.setText(R.string.emergency_call_dialog_number_for_display_734);
		    		}
		    	}
		    }
	}
            // add for HQ01545860 by lizhao at 20151207 end]
           }
			}
	}

	private void setPrimaryPhoneLocator(String locator){
		/*
		String operator = validateMobile(number);
		String formatLocator = validateLocator(locator);
		mLocator.setText(formatLocator+"	"+operator);
		*/
		mLocator.setText(locator);

		// / Modified by guofeiyao 2015/12/08
		// For latin tigo
		if ( android.os.SystemProperties.get("ro.hq.use.location").equals("1") ) {
		     mLocator.setVisibility(View.VISIBLE);
		}
		// / End
	}

	private void getSubInfoList() {
        mSubInfoList = SubscriptionManager.from(getActivity().getApplicationContext()).getActiveSubscriptionInfoList();
        mSubCount = (mSubInfoList != null && !mSubInfoList.isEmpty()) ? mSubInfoList.size() : 0;
    }

    @Override
    public void setPrimary(String number, String name, boolean nameIsNumber, String label,
            Drawable photo, boolean isSipCall) {
        Log.d(this, "Setting primary call");
		//Log.d(TAG,"number "+number);
		//Log.d(TAG,"name "+name);
		//Log.d(TAG,"nameIsNumber "+nameIsNumber);
		//Log.d(TAG,"location "+label);

		/*
		if(isNumber){
			eccNumber = number;
			isNumber=false;
		}	
		*/
        // set the name field.
        //modify by zhangjinqiang for al812--start
		simNum = Settings.System.getInt(getActivity().getApplicationContext().getContentResolver(), "slot_id", -1);

		if(!checkSimPosition()){
			simNum=1;
		}
		Log.d("simNum",""+simNum);
        setSimLable(simNum);
        setPrimaryPhoneNumberAndName(name,number);


		setPrimaryPhoneLocator(label);
		//modify by zhangjinqiang for al812--end

	//if(!InCallApp.gIsCustomerUi){
	        if (TextUtils.isEmpty(number) && TextUtils.isEmpty(label)) {
	            mCallNumberAndLabel.setVisibility(View.GONE);
	            mElapsedTime.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
	        } else {
	            mCallNumberAndLabel.setVisibility(View.VISIBLE);
	            mElapsedTime.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
	        }
	//}
		/*
		if(eccNumber!=null&&ECC.contains(eccNumber)){
			setPrimaryPhoneNumber(eccNumber);
		}
		*/

		//add by zhangjinqiang for al812 ui --start
		/*
        if(mFirstCallName != null){
            if(TextUtils.isEmpty(name)){
                 mFirstCallName.setText(number);
            }else{
                mFirstCallName.setText(name);
            }
        }
        */
        //add by zhangjinqiang for al812 ui --end


        // Set the label (Mobile, Work, etc)
		if(!InCallApp.gIsCustomerUi){
        setPrimaryLabel(label);
		showInternetCallLabel(isSipCall);
		}
        
		
		/*
		if(InCallApp.gIsCustomerUi){
			//showImageYep(mPhoto, photo);
			roundPhoto.setImageDrawable(photo);
		}else{
			setDrawableToImageView(mPhoto, photo);
		}
		*/
		setDrawableToImageView(mPhoto, photo);
		//add by zhangjinqiang for HQ01369615--start
		if(isDisplayDialpad){
			keyContainerHW.setVisibility(View.GONE);
			mFloatingActionButtonContainer.setBackgroundResource(R.color.end_call_container_hw_dialpad_background);
			((InCallActivity) getActivity()).displayDialpad(true, true);
		}
		//add by zjq end
    }

    /// M: For MTK DSDA Feature. @{
    /* Google code:
    @Override
    public void setSecondary(boolean show, String name, boolean nameIsNumber, String label,
            String providerLabel, boolean isConference) {

        if (show != mSecondaryCallInfo.isShown()) {
            updateFabPositionForSecondaryCallInfo();
        }

        if (show) {
            boolean hasProvider = !TextUtils.isEmpty(providerLabel);
            showAndInitializeSecondaryCallInfo(hasProvider);

            mSecondaryCallConferenceCallIcon.setVisibility(isConference ? View.VISIBLE : View.GONE);

            mSecondaryCallName.setText(nameIsNumber
                    ? PhoneNumberUtils.ttsSpanAsPhoneNumber(name)
                    : name);
            if (hasProvider) {
                mSecondaryCallProviderLabel.setText(providerLabel);
            }

            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mSecondaryCallName.setTextDirection(nameDirection);
        } else {
            mSecondaryCallInfo.setVisibility(View.GONE);
        }
    }
    */

	private void setSecondName(String name,String number){
		String langage = Locale.getDefault().getLanguage();//add by lipeng for number display
		if(TextUtils.isEmpty(name)){
				 if(false){
				 	/*
					String[] numArray = {number};
					callDetail=sdk.queryCallerIdDetail(numArray, CooTekPhoneService.SCENE_INOUT_CALL, callerIdType);
					if(callDetail!=null && callDetail.length>0 && callDetail[0]!=null&&callDetail[0].getName()!=null){
						Log.d("setPrimaryPhoneNumberAndName",callDetail[0].getName());
						secondName.setText(callDetail[0].getName());
					}else{
						secondName.setText(number);
		            		secondName.setVisibility(View.VISIBLE);
					}*/
				}else{
					if(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw")){///RTL language
						secondName.setText("\u202D"+number+"\u202C");//modify for HQ01340732 by lijianxin
					}else{
						secondName.setText(number);
					}
		            		secondName.setVisibility(View.VISIBLE);
					}
					
        		}else{
        			secondName.setText(name);
				secondName.setVisibility(View.VISIBLE);
			}
	}

    @Override
    public void setSecondary(boolean show, String name, boolean nameIsNumber, String label,
            String providerLabel, boolean isConference, boolean isIncoming) {

        if (show != mSecondaryCallInfo.isShown()) {
            updateFabPositionForSecondaryCallInfo();
        }

	 int resId = isIncoming ? R.string.card_title_incoming_call : R.string.onHold;

        if (show) {
		if(InCallApp.gIsHwUi){
			imageInfoHW.setVisibility(View.GONE);
			primayInfoHW.setVisibility(View.GONE);
			textInfoHW.setVisibility(View.VISIBLE);
			setSecondName(name,label);
			secondStatus.setText(getView().getResources().getString(resId));
		}
            mOtherCallInfo.setVisibility(View.VISIBLE);
            int providerColor = getPresenter().getSecondCallColor();
            // M: add for OP09 plug in @{
            if (ExtensionManager.getCallCardExt().shouldShowCallAccountIcon()) {
                if (null == providerLabel) {
                    providerLabel = ExtensionManager.getCallCardExt().getSecondCallProviderLabel();
                }
                mSecondaryCallInfo.mCallProviderIcon.setVisibility(View.VISIBLE);
                mSecondaryCallInfo.mCallProviderIcon.setImageBitmap(ExtensionManager.getCallCardExt()
                        .getSecondCallPhoneAccountBitmap());
            }
            // add for OP09 plug in @}
            updateCallInfoView(mSecondaryCallInfo, name, nameIsNumber, label, providerLabel,
                    providerColor, isConference, isIncoming);
            mCurrentSecondCallColor = providerColor;
        } else {
		if(InCallApp.gIsHwUi){
			imageInfoHW.setVisibility(View.VISIBLE);
			primayInfoHW.setVisibility(View.VISIBLE);
			textInfoHW.setVisibility(View.GONE);
		}
	   mSecondaryCallInfo.setVisibility(View.GONE);
            mOtherCallInfo.setVisibility(View.GONE);
        }

        // Need update AnswerFragment bottom padding when there
        // has another incoming call.
        updateAnswerFragmentBottomPadding();
    }

	//add by zhangjinqiang for HQ01340645 --start
	private void setThirdName(String name,String number){
		String langage = Locale.getDefault().getLanguage();//add by lipeng for number display
		if(TextUtils.isEmpty(name)){
			if(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw")){///RTL language
				thirdName.setText("\u202D"+number+"\u202C"); //modify by yulifeng 20151020 ,HQ01439126
			}else{
				thirdName.setText(number); 
			}
            		thirdName.setVisibility(View.VISIBLE);
        		}else{
        			thirdName.setText(name);
				thirdName.setVisibility(View.VISIBLE);
			}
	}
	//add by zjq end

    @Override
    public void setThird(boolean show, String name, boolean nameIsNumber, String label,
            String providerLabel, boolean isConference) {

        if (show != mThirdCallInfo.isShown()) {
            updateFabPositionForSecondaryCallInfo();
        }

       	 int resId = false ? R.string.card_title_incoming_call : R.string.onHold;

        if (show) {
			if(InCallApp.gIsHwUi){
				setThirdName(name,label);
				thirdStatus.setText(getView().getResources().getString(resId));
				thirdInfo.setVisibility(View.VISIBLE);
			}
            int providerColor = getPresenter().getThirdCallColor();
            // M: add for OP09 plug in @{
            if (ExtensionManager.getCallCardExt().shouldShowCallAccountIcon()) {
                if (null == providerLabel) {
                    providerLabel = ExtensionManager.getCallCardExt().getThirdCallProviderLabel();
                }
                mThirdCallInfo.mCallProviderIcon.setVisibility(View.VISIBLE);
                mThirdCallInfo.mCallProviderIcon.setImageBitmap(ExtensionManager.getCallCardExt()
                        .getThirdCallPhoneAccountBitmap());
            }
            // add for OP09 plug in @}
            updateCallInfoView(mThirdCallInfo, name, nameIsNumber, label, providerLabel,
                    providerColor, isConference, false);
            mCurrentThirdCallColor = providerColor;
        } else {
			if(InCallApp.gIsHwUi){
				thirdInfo.setVisibility(View.GONE);
			}else{
				mThirdCallInfo.setVisibility(View.GONE);
			}
        }
		mThirdCallInfo.setVisibility(View.GONE);
    }

    private void updateCallInfoView(CallInfoView callInfoView, String name, boolean nameIsNumber,
            String label, String providerLabel, int providerColor, boolean isConference, boolean isIncoming) {
        // Initialize CallInfo view.
        callInfoView.setVisibility(View.VISIBLE);

        if (!TextUtils.isEmpty(providerLabel)) {
            callInfoView.mCallProviderInfo.setVisibility(View.VISIBLE);
            callInfoView.mCallProviderLabel.setText(providerLabel);
            callInfoView.mCallProviderLabel.setTextColor(providerColor);
        }

        callInfoView.mCallConferenceCallIcon.setVisibility(isConference ? View.VISIBLE : View.GONE);

        callInfoView.mCallName.setText(nameIsNumber ? PhoneNumberUtils.ttsSpanAsPhoneNumber(name)
                : name);
        int nameDirection = View.TEXT_DIRECTION_INHERIT;
        if (nameIsNumber) {
            nameDirection = View.TEXT_DIRECTION_LTR;
        }
        callInfoView.mCallName.setTextDirection(nameDirection);

        int resId = isIncoming ? R.string.card_title_incoming_call : R.string.onHold;
        callInfoView.mCallStatus.setText(getView().getResources().getString(resId));
    }
    /// @}

    @Override
    public void setCallState(
            int state,
            int videoState,
            int sessionModificationState,
            DisconnectCause disconnectCause,
            String connectionLabel,
            String shortDescription,//add by liruihong for HQ01440479
            Drawable callStateIcon,
            String gatewayNumber) {
        boolean isGatewayCall = !TextUtils.isEmpty(gatewayNumber);
        CharSequence callStateLabel = getCallStateLabelFromState(state, videoState,
                sessionModificationState, disconnectCause, connectionLabel, isGatewayCall);

        Log.v(this, "setCallState " + callStateLabel);
        //Log.v(this, "DisconnectCause " + disconnectCause.toString());
        Log.v(this, "gateway " + connectionLabel + gatewayNumber);

	/*add by liruihong for HQ01440479 begin*/
        String num = shortDescription;
	 if(num != null){
		num = num.substring(num.length()-1,num.length());
		setSimLable(Integer.parseInt(num));
	 }
        Log.i(this,"setCallState:num = "+num);
	/*add by liruihong for HQ01440479 end*/

        if ((TextUtils.equals(callStateLabel, mCallStateLabel.getText()))
                /// M: For ALPS02036232, add this filter then can update
                // callstateIcon if icon changed. @{
                && isCallStateIconUnChanged(callStateIcon)) {
                /// @}
            // Nothing to do if the labels are the same
            return;
        }

	firstStatus.setText(callStateLabel);
	
        // Update the call state label and icon.
        if (!TextUtils.isEmpty(callStateLabel)) {
			Locale locale = getResources().getConfiguration().locale;
			String language = locale.getLanguage();
			//[add for HQ01535509 by lizhao at 20151203 begain
			if ("en".equals(language) || "ru".equals(language)) {
				if (state == Call.State.ACTIVE || state == Call.State.DIALING || state == Call.State.ONHOLD ||state == Call.State.CONNECTING) {
					callStateLabel = callStateLabel.toString().toUpperCase(Locale.ENGLISH);
				} 
				if (CallList.getInstance().getActiveAndHoldCallsCount() >= 2) {
					callStateLabel = getView().getContext().getString(R.string.multiparty_call);
				}
			}
			//add for HQ01535509 by lizhao at 20151203 end]
            mCallStateLabel.setText(callStateLabel);
            mCallStateLabel.setAlpha(1);
            mCallStateLabel.setVisibility(View.VISIBLE);

            if (state == Call.State.ACTIVE || state == Call.State.CONFERENCED) {
                mCallStateLabel.clearAnimation();
            } else {
                //mCallStateLabel.startAnimation(mPulseAnimation);
            }
        } else {
            Animation callStateLabelAnimation = mCallStateLabel.getAnimation();
            if (callStateLabelAnimation != null) {
                callStateLabelAnimation.cancel();
            }
            mCallStateLabel.setText(null);
            mCallStateLabel.setAlpha(0);
            //mCallStateLabel.setVisibility(View.GONE);
        }

        if (callStateIcon != null) {
            mCallStateIcon.setVisibility(View.VISIBLE);
            // Invoke setAlpha(float) instead of setAlpha(int) to set the view's alpha. This is
            // needed because the pulse animation operates on the view alpha.
            mCallStateIcon.setAlpha(1.0f);
            mCallStateIcon.setImageDrawable(callStateIcon);

            if (state == Call.State.ACTIVE || state == Call.State.CONFERENCED
                    || TextUtils.isEmpty(callStateLabel)) {
                mCallStateIcon.clearAnimation();
            } else {
                mCallStateIcon.startAnimation(mPulseAnimation);
            }

            if (callStateIcon instanceof AnimationDrawable) {
                ((AnimationDrawable) callStateIcon).start();
            }
        } else {
            Animation callStateIconAnimation = mCallStateIcon.getAnimation();
            if (callStateIconAnimation != null) {
                callStateIconAnimation.cancel();
            }

            // Invoke setAlpha(float) instead of setAlpha(int) to set the view's alpha. This is
            // needed because the pulse animation operates on the view alpha.
            mCallStateIcon.setAlpha(0.0f);
            mCallStateIcon.setVisibility(View.GONE);
            /**
             * M: [ALPS01841247]Once the ImageView was shown, it would show again even when
             * setVisibility(GONE). This is caused by View system, when complex interaction
             * combined by Visibility/Animation/Alpha. This root cause need further discussion.
             * As a solution, set the drawable to null can fix this specific problem of
             * ALPS01841247 directly.
             */
            mCallStateIcon.setImageDrawable(null);
        }

        if (VideoProfile.VideoState.isBidirectional(videoState)
                || (state == Call.State.ACTIVE && sessionModificationState
                        == Call.SessionModificationState.WAITING_FOR_RESPONSE)) {
            mCallStateVideoCallIcon.setVisibility(View.VISIBLE);
        } else {
            mCallStateVideoCallIcon.setVisibility(View.GONE);
        }

        if (state == Call.State.INCOMING) {
            if (callStateLabel != null) {
                getView().announceForAccessibility(callStateLabel);
            }
            if (mPrimaryName.getText() != null) {
                getView().announceForAccessibility(mPrimaryName.getText());
            }
        }
    }

    @Override
    public void setCallbackNumber(String callbackNumber, boolean isEmergencyCall) {
        if (mInCallMessageLabel == null) {
            return;
        }
        /// M: for ALPS02044888, remove the show callback number feature.
        // PhoneAccount sets sub number as address in MTK Turnkey solution for
        // bug fix ALPS01804842, and causes callbackNumber and simNumber may be
        // different when dial ECC before SIM LOADED. We need never show
        // callback number in L1, so remove it and waiting for M release. @{
        callbackNumber = null;
        /// @}
        if (TextUtils.isEmpty(callbackNumber)) {
            mInCallMessageLabel.setVisibility(View.GONE);
            return;
        }

        // TODO: The new Locale-specific methods don't seem to be working. Revisit this.
        callbackNumber = PhoneNumberUtils.formatNumber(callbackNumber);

        int stringResourceId = isEmergencyCall ? R.string.card_title_callback_number_emergency
                : R.string.card_title_callback_number;

        String text = getString(stringResourceId, callbackNumber);
        mInCallMessageLabel.setText(text);

        mInCallMessageLabel.setVisibility(View.VISIBLE);
    }

    private void showInternetCallLabel(boolean show) {
        if (show) {
            final String label = getView().getContext().getString(
                    R.string.incall_call_type_label_sip);
            mCallTypeLabel.setVisibility(View.VISIBLE);
            mCallTypeLabel.setText(label);
        } else {
            mCallTypeLabel.setVisibility(View.GONE);
        }
    }

    @Override
    public void setPrimaryCallElapsedTime(boolean show, long duration) {
        String callTimeElapsed = DateUtils.formatElapsedTime(duration / 1000);
        if (show) {
            if (mElapsedTime.getVisibility() != View.VISIBLE || circleProgress.getVisibility() != View.VISIBLE) {
                AnimUtils.fadeIn(mElapsedTime, AnimUtils.DEFAULT_DURATION);
			   circleProgress.setVisibility(View.VISIBLE);
			   circleProgress.startCartoom((int)(duration / 1000));
			   circle.setAlpha(0.0f);
            }

		  	//add by zhangjinqiang for al812 ui --start
			  //circleProgress.setAlpha(1.0f);
			  /*
			  if(isSetCallTime){
					circleProgress.startCartoom((int)(duration / 1000));
					isSetCallTime=false;
			  }
			  */
			  //circle.setAlpha(0.0f);
			//add by zhangjinqiang for al812 ui --end
				
            String durationDescription = InCallDateUtils.formatDetailedDuration(duration);
            mElapsedTime.setText(callTimeElapsed);
	        callTime.setText(callTimeElapsed);
            mElapsedTime.setContentDescription(durationDescription);
        } else {
            // hide() animation has no effect if it is already hidden.
            //add by zhangjinqiang for modify MTBF TESTS Contacts ANR
            if(circleProgress!=null){
				circleProgress.stopCartoom();
			}
		   // add by zjq end
            AnimUtils.fadeOut(mElapsedTime, AnimUtils.DEFAULT_DURATION);
        }
    }

    private void setDrawableToImageView(ImageView view, Drawable photo) {
        if (photo == null) {
            photo = ContactInfoCache.getInstance(
                    view.getContext()).getDefaultContactPhotoDrawable();
        }

        if (mPrimaryPhotoDrawable == photo) {
            return;
        }
        mPrimaryPhotoDrawable = photo;

        final Drawable current = view.getDrawable();
        if (current == null) {
            view.setImageDrawable(photo);
            AnimUtils.fadeIn(mElapsedTime, AnimUtils.DEFAULT_DURATION);
        } else {
            // Cross fading is buggy and not noticable due to the multiple calls to this method
            // that switch drawables in the middle of the cross-fade animations. Just set the
            // photo directly instead.
            view.setImageDrawable(photo);
            view.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Gets the call state label based on the state of the call or cause of disconnect.
     *
     * Additional labels are applied as follows:
     *         1. All outgoing calls with display "Calling via [Provider]".
     *         2. Ongoing calls will display the name of the provider.
     *         3. Incoming calls will only display "Incoming via..." for accounts.
     *         4. Video calls, and session modification states (eg. requesting video).
     */
    private CharSequence getCallStateLabelFromState(int state, int videoState,
            int sessionModificationState, DisconnectCause disconnectCause, String label,
            boolean isGatewayCall) {
        final Context context = getView().getContext();
        CharSequence callStateLabel = null;  // Label to display as part of the call banner

        boolean isSpecialCall = label != null;
        boolean isAccount = isSpecialCall && !isGatewayCall;

        switch  (state) {
            case Call.State.IDLE:
                // "Call state" is meaningless in this state.
                break;
            case Call.State.ACTIVE:
                // We normally don't show a "call state label" at all in this state
                // (but we can use the call state label to display the provider name).
                //modiby by zhangjinqiang for display call state-start
                /*
                if (isAccount) {
                    callStateLabel = label;
                } else */
                //modiby by zjq end
                if (sessionModificationState
                        == Call.SessionModificationState.REQUEST_FAILED) {
                    callStateLabel = context.getString(R.string.card_title_video_call_error);
                } else if (sessionModificationState
                        == Call.SessionModificationState.WAITING_FOR_RESPONSE) {
                    callStateLabel = context.getString(R.string.card_title_video_call_requesting);
                } else if (VideoProfile.VideoState.isBidirectional(videoState)) {
                    callStateLabel = context.getString(R.string.card_title_video_call);
                }else{
		   callStateLabel = context.getString(R.string.card_title_communicate);
	        }
                break;
            case Call.State.ONHOLD:
                callStateLabel = context.getString(R.string.card_title_on_hold);
                break;
            case Call.State.CONNECTING:
            case Call.State.DIALING:
                if (isSpecialCall) {
                    callStateLabel = context.getString(R.string.calling_via_template, label);
                } else {
                    callStateLabel = context.getString(R.string.card_title_dialing);
                }
                break;
            case Call.State.REDIALING:
                callStateLabel = context.getString(R.string.card_title_redialing);
                break;
            case Call.State.INCOMING:
            case Call.State.CALL_WAITING:
                /// M: [VoLTE conference]incoming volte conference @{
                if (isIncomingVolteConferenceCall()) {
                    callStateLabel = context.getString(R.string.card_title_incoming_conference);
                    break;
                }
                /// @}
                if (isAccount) {
                    callStateLabel = context.getString(R.string.incoming_via_template, label);
                } else if (VideoProfile.VideoState.isBidirectional(videoState)) {
                    callStateLabel = context.getString(R.string.notification_incoming_video_call);
                } else {
                    callStateLabel = context.getString(R.string.card_title_incoming_call);
                }
                break;
            case Call.State.DISCONNECTING:
                // While in the DISCONNECTING state we display a "Hanging up"
                // message in order to make the UI feel more responsive.  (In
                // GSM it's normal to see a delay of a couple of seconds while
                // negotiating the disconnect with the network, so the "Hanging
                // up" state at least lets the user know that we're doing
                // something.  This state is currently not used with CDMA.)
                callStateLabel = context.getString(R.string.card_title_hanging_up);
                break;
            case Call.State.DISCONNECTED:
                callStateLabel = disconnectCause.getLabel();
                if (TextUtils.isEmpty(callStateLabel)) {
                    callStateLabel = context.getString(R.string.card_title_call_ended);
                }
                break;
            case Call.State.CONFERENCED:
                callStateLabel = context.getString(R.string.card_title_conf_call);
                break;
            default:
                Log.wtf(this, "updateCallStateWidgets: unexpected call: " + state);
        }
        return callStateLabel;
    }

    private void showAndInitializeSecondaryCallInfo(boolean hasProvider) {
	//modify by zhangjinqiang for al812--start
        //mSecondaryCallInfo.setVisibility(View.VISIBLE);
	//modify by zhangjinqiang for al812--end
        // mSecondaryCallName is initialized here (vs. onViewCreated) because it is inaccessible
        // until mSecondaryCallInfo is inflated in the call above.
        if (mSecondaryCallName == null) {
            mSecondaryCallName = (TextView) getView().findViewById(R.id.secondaryCallName);
            mSecondaryCallConferenceCallIcon =
                    getView().findViewById(R.id.secondaryCallConferenceCallIcon);
        }

        if (mSecondaryCallProviderLabel == null && hasProvider) {
            mSecondaryCallProviderInfo.setVisibility(View.VISIBLE);
            mSecondaryCallProviderLabel = (TextView) getView()
                    .findViewById(R.id.secondaryCallProviderLabel);
        }
    }

    public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            dispatchPopulateAccessibilityEvent(event, mCallStateLabel);
            dispatchPopulateAccessibilityEvent(event, mPrimaryName);
            dispatchPopulateAccessibilityEvent(event, mPhoneNumber);
            return;
        }
        dispatchPopulateAccessibilityEvent(event, mCallStateLabel);
        dispatchPopulateAccessibilityEvent(event, mPrimaryName);
        dispatchPopulateAccessibilityEvent(event, mPhoneNumber);
        dispatchPopulateAccessibilityEvent(event, mCallTypeLabel);
        dispatchPopulateAccessibilityEvent(event, mSecondaryCallInfo.mCallName);
        dispatchPopulateAccessibilityEvent(event, mSecondaryCallInfo.mCallProviderLabel);

        return;
    }

    @Override
    public void setEndCallButtonEnabled(boolean enabled, boolean animate) {
        /// MTK add this log. @{
        Log.d(this, "setEndCallButtonEnabled, old state is %s", mFloatingActionButton.isEnabled());
        Log.d(this, "mFloatingActionButtonContainer visible is %s",
                mFloatingActionButtonContainer.getVisibility() != View.VISIBLE);
        Log.d(this, "enabled = " + enabled + "; animate = ", animate);
        /// @}
        if (enabled != mFloatingActionButton.isEnabled()) {
            if (animate) {
                if (enabled) {
                    mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
                } else {
                    mFloatingActionButtonController.scaleOut();
                }
            } else {
                if (enabled) {
                    mFloatingActionButtonContainer.setScaleX(1);
                    mFloatingActionButtonContainer.setScaleY(1);
                    mFloatingActionButtonContainer.setVisibility(View.VISIBLE);
                } else {
                    mFloatingActionButtonContainer.setVisibility(View.GONE);
                }
            }
            mFloatingActionButton.setEnabled(enabled);
            updateFabPosition();
        }
    }

    /**
     * Changes the visibility of the contact photo.
     *
     * @param isVisible {@code True} if the UI should show the contact photo.
     */
    @Override
    public void setPhotoVisible(boolean isVisible) {
        mPhoto.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    /**
     * Changes the visibility of the "manage conference call" button.
     *
     * @param visible Whether to set the button to be visible or not.
     */
    @Override
    public void showManageConferenceCallButton(boolean visible) {
        mManageConferenceCallButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Determines the current visibility of the manage conference button.
     *
     * @return {@code true} if the button is visible.
     */
    @Override
    public boolean isManageConferenceVisible() {
        return mManageConferenceCallButton.getVisibility() == View.VISIBLE;
    }

    /**
     * Get the overall InCallUI background colors and apply to call card.
     */
    @Override
    public void updateColors() {
        MaterialPalette themeColors = InCallPresenter.getInstance().getThemeColors();

        if (mCurrentThemeColors != null && mCurrentThemeColors.equals(themeColors)) {
            return;
        }
        if (themeColors == null) {
            return;
        }

       
//add by zhangjinqiang for al812 --start
/*
	mPrimaryCallCardContainer.setBackgroundColor(themeColors.mPrimaryColor);
		   mCallButtonsContainer.setBackgroundColor(themeColors.mPrimaryColor);
	
		   mCurrentThemeColors = themeColors;
*/
	if(InCallApp.gIsHwUi){
		//callCardHW.setBackgroundColor(themeColors.mPrimaryColor);
	}else{
		mPrimaryCallCardContainer.setBackgroundColor(themeColors.mPrimaryColor);
        		//mCallButtonsContainer.setBackgroundColor(themeColors.mPrimaryColor);
	}
//add by zhangjinqiang for al812 --end
	mCurrentThemeColors = themeColors;

    }

    private void dispatchPopulateAccessibilityEvent(AccessibilityEvent event, View view) {
        if (view == null) return;
        final List<CharSequence> eventText = event.getText();
        int size = eventText.size();
        view.dispatchPopulateAccessibilityEvent(event);
        // if no text added write null to keep relative position
        if (size == eventText.size()) {
            eventText.add(null);
        }
    }

    public void animateForNewOutgoingCall(final Point touchPoint,
            final boolean showCircularReveal) {
        final ViewGroup parent = (ViewGroup) mPrimaryCallCardContainer.getParent();

        final ViewTreeObserver observer = getView().getViewTreeObserver();

        mPrimaryCallInfo.getLayoutTransition().disableTransitionType(LayoutTransition.CHANGING);

        observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                final ViewTreeObserver observer = getView().getViewTreeObserver();
                if (!observer.isAlive()) {
                    return;
                }
                observer.removeOnGlobalLayoutListener(this);

                final LayoutIgnoringListener listener = new LayoutIgnoringListener();
                mPrimaryCallCardContainer.addOnLayoutChangeListener(listener);

                // Prepare the state of views before the circular reveal animation
                final int originalHeight = mPrimaryCallCardContainer.getHeight();
                mPrimaryCallCardContainer.setBottom(parent.getHeight());

                // Set up FAB.
                mFloatingActionButtonContainer.setVisibility(View.GONE);
                mFloatingActionButtonController.setScreenWidth(parent.getWidth());
                //mCallButtonsContainer.setAlpha(0);
                mCallStateLabel.setAlpha(0);
                mPrimaryName.setAlpha(0);
                mCallTypeLabel.setAlpha(0);
                mCallNumberAndLabel.setAlpha(0);

                final Animator animator = getOutgoingCallAnimator(touchPoint,
                        parent.getHeight(), originalHeight, showCircularReveal);

                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setViewStatePostAnimation(listener);
                    }
                });
                animator.start();
            }
        });
    }

    public void onDialpadVisiblityChange(boolean isShown) {
        mIsDialpadShowing = isShown;
        /* begin: add by donghongjing for HQ01431679 */
        if (dialpd != null && mIsDialpadShowing != dialpd.isSelected()) {
	        Resources resource = getResources();
	        final Drawable dialpad = resource.getDrawable(R.drawable.ic_dialpad);
	        final Drawable dialpadOn = resource.getDrawable(R.drawable.ic_dialpad_on);
            if(mIsDialpadShowing){
			    dialpd.setSelected(true);
			    dialpd.setImageDrawable(dialpadOn);
			    keyContainerHW.setVisibility(View.GONE);
			    mFloatingActionButtonContainer.setBackgroundResource(R.color.end_call_container_hw_dialpad_background);
                	((InCallActivity) getActivity()).displayDialpad(true, true);
			    isDisplayDialpad=true;
	     	}else{
	     		dialpd.setSelected(false);
			    dialpd.setImageDrawable(dialpad);
			    keyContainerHW.setVisibility(View.VISIBLE);
			    mFloatingActionButtonContainer.setBackgroundResource(R.color.end_call_container_hw_background);
			    ((InCallActivity) getActivity()).displayDialpad(false, true);
			    isDisplayDialpad=false;
		    }
        }
        /* end: add by donghongjing for HQ01431679 */
        updateFabPosition();
    }

    private void updateFabPosition() {
		if(InCallApp.gIsCustomerUi){
			return;
		}
        int offsetY = 0;
        if (!mIsDialpadShowing) {
            offsetY = mFloatingActionButtonVerticalOffset;
            if (mOtherCallInfo.isShown()) {
                offsetY = -1 * mOtherCallInfo.getHeight();
            }
        }

        mFloatingActionButtonController.align(
                mIsLandscape ? FloatingActionButtonController.ALIGN_QUARTER_END
                        : FloatingActionButtonController.ALIGN_MIDDLE,
                0 /* offsetX */,
                offsetY,
                true);

        mFloatingActionButtonController.resize(
                mIsDialpadShowing ? mFabSmallDiameter : mFabNormalDiameter, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        // If the previous launch animation is still running, cancel it so that we don't get
        // stuck in an intermediate animation state.
        if (mAnimatorSet != null && mAnimatorSet.isRunning()) {
            mAnimatorSet.cancel();
        }

        mIsLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

		if(!InCallApp.gIsCustomerUi){
        final ViewGroup parent = ((ViewGroup) mPrimaryCallCardContainer.getParent());
        final ViewTreeObserver observer = parent.getViewTreeObserver();
        parent.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ViewTreeObserver viewTreeObserver = observer;
                if (!viewTreeObserver.isAlive()) {
                    viewTreeObserver = parent.getViewTreeObserver();
                }
                viewTreeObserver.removeOnGlobalLayoutListener(this);
                mFloatingActionButtonController.setScreenWidth(parent.getWidth());
                updateFabPosition();
            }
        });
		}
        updateColors();
        /// M: For second/third call color @{
        updateSecondCallColor();
        updateThirdCallColor();
        /// @}
        
		int displayCallButton = Settings.System.getInt(getActivity().getApplicationContext().getContentResolver(), "displayCallButton", 1);
		if(displayCallButton==0){
			keyContainerHW.setVisibility(View.GONE);
			Settings.System.putInt(getActivity().getApplicationContext().getContentResolver(), "displayCallButton", 1);
		}
    }

    /**
     * Adds a global layout listener to update the FAB's positioning on the next layout. This allows
     * us to position the FAB after the secondary call info's height has been calculated.
     */
    private void updateFabPositionForSecondaryCallInfo() {
        mOtherCallInfo.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        final ViewTreeObserver observer = mOtherCallInfo.getViewTreeObserver();
                        if (!observer.isAlive()) {
                            return;
                        }
                        observer.removeOnGlobalLayoutListener(this);

                        onDialpadVisiblityChange(mIsDialpadShowing);

                        // Need update AnswerFragment bottom padding when there
                        // has another incoming call.
                        updateAnswerFragmentBottomPadding();
                    }
                });
    }

    /**
     * Animator that performs the upwards shrinking animation of the blue call card scrim.
     * At the start of the animation, each child view is moved downwards by a pre-specified amount
     * and then translated upwards together with the scrim.
     */
    private Animator getShrinkAnimator(int startHeight, int endHeight) {
        final Animator shrinkAnimator =
                ObjectAnimator.ofInt(mPrimaryCallCardContainer, "bottom", startHeight, endHeight);
        shrinkAnimator.setDuration(mShrinkAnimationDuration);
        shrinkAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                assignTranslateAnimation(mCallStateLabel, 1);
                assignTranslateAnimation(mCallStateIcon, 1);
                assignTranslateAnimation(mPrimaryName, 2);
                assignTranslateAnimation(mCallNumberAndLabel, 3);
                assignTranslateAnimation(mCallTypeLabel, 4);
                //assignTranslateAnimation(mCallButtonsContainer, 5);

                mFloatingActionButton.setEnabled(true);
            }
        });
        shrinkAnimator.setInterpolator(AnimUtils.EASE_IN);
        return shrinkAnimator;
    }

    private Animator getRevealAnimator(Point touchPoint) {
        final Activity activity = getActivity();
        final View view  = activity.getWindow().getDecorView();
        final Display display = activity.getWindowManager().getDefaultDisplay();
        final Point size = new Point();
        display.getSize(size);

        int startX = size.x / 2;
        int startY = size.y / 2;
        if (touchPoint != null) {
            startX = touchPoint.x;
            startY = touchPoint.y;
        }

        final Animator valueAnimator = ViewAnimationUtils.createCircularReveal(view,
                startX, startY, 0, Math.max(size.x, size.y));
        valueAnimator.setDuration(mRevealAnimationDuration);
        return valueAnimator;
    }

    private Animator getOutgoingCallAnimator(Point touchPoint, int startHeight, int endHeight,
            boolean showCircularReveal) {

        final Animator shrinkAnimator = getShrinkAnimator(startHeight, endHeight);

        if (!showCircularReveal) {
            return shrinkAnimator;
        }

        final Animator revealAnimator = getRevealAnimator(touchPoint);
        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playSequentially(revealAnimator, shrinkAnimator);
        return animatorSet;
    }

    private void assignTranslateAnimation(View view, int offset) {
        view.setTranslationY(mTranslationOffset * offset);
        view.animate().translationY(0).alpha(1).withLayer()
                .setDuration(mShrinkAnimationDuration).setInterpolator(AnimUtils.EASE_IN);
    }

    private void setViewStatePostAnimation(View view) {
        view.setTranslationY(0);
        view.setAlpha(1);
    }

    private void setViewStatePostAnimation(OnLayoutChangeListener layoutChangeListener) {
        //setViewStatePostAnimation(mCallButtonsContainer);
        setViewStatePostAnimation(mCallStateLabel);
        setViewStatePostAnimation(mPrimaryName);
        setViewStatePostAnimation(mCallTypeLabel);
        setViewStatePostAnimation(mCallNumberAndLabel);
        setViewStatePostAnimation(mCallStateIcon);

        mPrimaryCallCardContainer.removeOnLayoutChangeListener(layoutChangeListener);
        mPrimaryCallInfo.getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);
        /// M: For ALPS01761179 & ALPS01794859, don't show end button if state
        // is incoming or disconnected. @{
        final Call call = CallList.getInstance().getFirstCall();
        if (call != null) {
            int state = call.getState();
            if (!Call.State.isIncoming(state) && Call.State.isConnectingOrConnected(state)) {
                mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
                Log.d(this, "setViewStatePostAnimation end.");
            /// M: For ALPS01828090 disable end call button when end button do not show under call state is disconnected.
            // in order to setEndCallButtonEnabled() can get right mFloatingActionButton state 
            // to show end button to other connecting or connected calls @{
            } else if (mFloatingActionButton.isEnabled()) {
                Log.i(this, "mFloatingActionButton.setEnabled(false) when end button do not show");
                mFloatingActionButton.setEnabled(false);
            }
            /// @}
        }
        /// @}
    }

    private final class LayoutIgnoringListener implements View.OnLayoutChangeListener {
        @Override
        public void onLayoutChange(View v,
                int left,
                int top,
                int right,
                int bottom,
                int oldLeft,
                int oldTop,
                int oldRight,
                int oldBottom) {
            v.setLeft(oldLeft);
            v.setRight(oldRight);
            v.setTop(oldTop);
            v.setBottom(oldBottom);
        }
    }

    /**
     * Need update AnswerFragment bottom padding when there has another incoming call.
     */
    public void updateAnswerFragmentBottomPadding() {
        int bottomPadding = 0;
        if (mSecondaryCallInfo != null && CallList.getInstance().getSecondaryIncomingCall() != null) {
            bottomPadding = mSecondaryCallInfo.getHeight();
        }

        View answerView = getView().findViewById(R.id.answerFragment);
        if (answerView == null) {
            return;
        }

        int oldBottomPadding = answerView.getPaddingBottom();
        if (bottomPadding != oldBottomPadding) {
            answerView.setPadding(answerView.getPaddingLeft(), answerView.getPaddingTop(),
                    answerView.getPaddingRight(), bottomPadding);
            answerView.invalidate();
        }
    }

    /**
     * Make dim effect for secondary CallInfoView if needed.
     * @param dim true indicates that needs dim and false otherwise.
     */
    public void updateDimEffectForSecondaryCallInfo(boolean dim) {
        View view = getView().findViewById(R.id.dim_effect_for_secondary);
        if (dim) {
            mSecondaryCallInfo.setEnabled(false);
            view.setVisibility(View.VISIBLE);
        } else {
            mSecondaryCallInfo.setEnabled(true);
            view.setVisibility(View.GONE);
        }
    }

    // -----------------------------Medaitek---------------------------------------

    private void initVoiceRecorderIcon(View view) {
        mVoiceRecorderIcon = (ImageView) view.findViewById(R.id.voiceRecorderIcon);
        mVoiceRecorderIcon.setImageResource(R.drawable.voice_record);
        mVoiceRecorderIcon.setVisibility(View.INVISIBLE);
    }

    @Override
    public void updateVoiceRecordIcon(boolean show) {
        mVoiceRecorderIcon.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        AnimationDrawable ad = (AnimationDrawable) mVoiceRecorderIcon.getDrawable();
        if (ad != null) {
            if (show && !ad.isRunning()) {
                ad.start();
            } else if (!show && ad.isRunning()) {
                ad.stop();
            }
        }
    }

    // Fix ALPS01759672. @{
    @Override
    public void setSecondaryEnabled(boolean enabled) {
        if (mSecondaryCallInfo != null) {
            mSecondaryCallInfo.setEnabled(enabled);
        }
    }

    @Override
    public void setThirdEnabled(boolean enabled) {
        if (mThirdCallInfo != null) {
            mThirdCallInfo.setEnabled(enabled);
        }
    }
    // @}


    /// M: For second/third call color @{
    /**
     * Get the second call color and apply to second call provider label.
     */
    public void updateSecondCallColor() {
        int secondCallColor = getPresenter().getSecondCallColor();
        if (mCurrentSecondCallColor == secondCallColor) {
            return;
        }
        mSecondaryCallInfo.mCallProviderLabel.setTextColor(secondCallColor);
        mCurrentSecondCallColor = secondCallColor;
    }

    /**
     * Get the third call color and apply to third call provider label.
     */
    public void updateThirdCallColor() {
        int thirdCallColor = getPresenter().getThirdCallColor();
        if (mCurrentThirdCallColor == thirdCallColor) {
            return;
        }
        mThirdCallInfo.mCallProviderLabel.setTextColor(thirdCallColor);
        mCurrentThirdCallColor = thirdCallColor;
    }
    /**
     * check whether the callStateIcon has no change.
     * @param callStateIcon call state icon
     * @return true if no change
     */
    public boolean isCallStateIconUnChanged(Drawable callStateIcon) {
        return (mCallStateIcon.getDrawable() == null && callStateIcon == null)
                || (mCallStateIcon.getDrawable() != null && callStateIcon != null);
    }
    /// @}

    private boolean isIncomingVolteConferenceCall() {
        Call call = CallList.getInstance().getIncomingCall();
        return call != null
                && call.isConferenceCall()
                && call.can(android.telecom.Call.Details.CAPABILITY_VOLTE);
    }

	//add by zhangjinqiang for display SIM2 only exist SIM2 --start
	public boolean checkSimPosition(){
		if (1 == mSubCount) {
			int subsriptionId = mSubInfoList.get(0).getSubscriptionId();
			int slotId = SubscriptionManager.getSlotId(subsriptionId);
            if (slotId == 1) {
                 return false;//only exitst SIM2
			}else{
				return true;
			}
		}else{
			return true;
		}
	}

	private String validateMobile(String mobile){
		mobile = mobile.replace(" ","");
		String returnString="";
		if(mobile==null || mobile.trim().length()!=11){
			return "";		//illegal phone number
		}
		if(mobile.trim().substring(0,3).equals("134") ||  mobile.trim().substring(0,3).equals("135") || 
				mobile.trim().substring(0,3).equals("136") || mobile.trim().substring(0,3).equals("137")  
				|| mobile.trim().substring(0,3).equals("138")  || mobile.trim().substring(0,3).equals("139") 
				||  mobile.trim().substring(0,3).equals("150") || mobile.trim().substring(0,3).equals("151") 
				|| mobile.trim().substring(0,3).equals("152")  || mobile.trim().substring(0,3).equals("157") 
				|| mobile.trim().substring(0,3).equals("158") || mobile.trim().substring(0,3).equals("159") 
				|| mobile.trim().substring(0,3).equals("182")|| mobile.trim().substring(0,3).equals("183")
				|| mobile.trim().substring(0,3).equals("184")|| mobile.trim().substring(0,3).equals("178")
				|| mobile.trim().substring(0,3).equals("147")|| mobile.trim().substring(0,3).equals("187") 
				|| mobile.trim().substring(0,3).equals("188")){
			returnString=chinaMobile;	///china mobile
		}
		if(mobile.trim().substring(0,3).equals("130") ||  mobile.trim().substring(0,3).equals("131") || 
				mobile.trim().substring(0,3).equals("132")||  mobile.trim().substring(0,3).equals("145") ||  
				mobile.trim().substring(0,3).equals("155")||mobile.trim().substring(0,3).equals("156")  ||  
				mobile.trim().substring(0,3).equals("176")|| mobile.trim().substring(0,3).equals("185")  ||
				mobile.trim().substring(0,3).equals("186")){
			returnString=chinaUnicom;	///China Unicom
		}
		if(mobile.trim().substring(0,3).equals("133") ||  mobile.trim().substring(0,3).equals("153")
			||  mobile.trim().substring(0,3).equals("177") || mobile.trim().substring(0,3).equals("180") 
			||  mobile.trim().substring(0,3).equals("181")|| mobile.trim().substring(0,3).equals("189")){
			returnString=chinaTelecom;	//China Telecom
		}
		return returnString;
	}

	private String validateLocator(String location){
		String replaceStr = "";
		if(location.contains(locatorProvince)){
			location =location.replace(locatorProvince,replaceStr);
		}
		if(location.contains(locatorCity)){
			location = location.replace(locatorCity, replaceStr);
		}
		return location;
	}
	
	@Override
	public void showCallTime(){
		callTime.setVisibility(View.VISIBLE);
		if(circleProgress.getVisibility() == View.GONE){
			circleProgress.setVisibility(View.VISIBLE);
		}
	}

	@Override
	public void showCircle(){
		circle.setAlpha(1.0f);
		circleProgress.setVisibility(View.INVISIBLE);
		mElapsedTime.setVisibility(View.INVISIBLE);
	    //circle.setVisibility(View.VISIBLE);
	}

	@Override
	public void updateSimLable(int num){
		Log.d("simNum",""+num);
        setSimLable(num);
	}

	private String checkSimName(String simName){
		String language = Locale.getDefault().getLanguage();
		Resources resource = getActivity().getResources();
		if(simName==null){
			return null;
		}
		
		if(language.equals("zh")&&simName.equals("CMCC")){
			return resource.getString(R.string.china_mobile_incall);
		}else{
			return simName;
		}
	}
	//add by zhangjinqiang end
}
