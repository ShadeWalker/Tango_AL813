/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2008 The Android Open Source Project
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
 */

package com.android.phone;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DialerKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.telephony.Phone;

import android.app.Dialog;
import android.view.WindowManager;
import android.os.CountDownTimer;
import android.content.Intent;
import android.provider.Settings;
import com.android.internal.telephony.PhoneConstants;
import com.mediatek.telephony.TelephonyManagerEx;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneFactory;
import android.os.SystemProperties;
import android.os.ServiceManager;
import com.mediatek.internal.telephony.ITelephonyEx;
import android.os.RemoteException;
import android.telephony.SubscriptionManager;
import java.security.MessageDigest;
import android.view.inputmethod.InputMethodManager;
import android.text.InputType;




/**
 * "SIM network unlock" PIN entry screen.
 *
 * @see PhoneGlobals.EVENT_SIM_NETWORK_LOCKED
 *
 * TODO: This UI should be part of the lock screen, not the
 * phone app (see bug 1804111).
 */
public class IccNetworkDepersonalizationPanel extends IccPanel {

    //debug constants
    private static final boolean DBG = false;

    //events
    private static final int EVENT_ICC_NTWRK_DEPERSONALIZATION_RESULT = 100;

    private Phone mPhone;

	//add by zhangjinqiang for HW_SIMLock -start
	private TextView mTimerText;
	private TextView mLeftTimes;
	private  long timeDelay = 0;
	private  int timeCount =1000;
	private  int leftCounts = 0;
	private int needPopTop = 0;
	  // Intent action for launching the Emergency Dialer activity.
    static final String ACTION_EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL";
	private static final String ACTION_RESET_MODEM = "android.intent.action.sim.ACTION_RESET_MODEM";
	private int mPhoneID=-1;
	private TextView mTitle;
	private int mSIMNum=-1;
	private String pwd =null;
	//add by zhangjinqiang end

    //UI elements
    private EditText     mPinEntry;
    private LinearLayout mEntryPanel;
    private LinearLayout mStatusPanel;
    private TextView     mStatusText;

    private Button       mUnlockButton;
    private Button       mDismissButton;
	private Button      mEmergencyCall;
	private Button		 mPanelDismissButton;


    //private textwatcher to control text entry.
    private TextWatcher mPinEntryWatcher = new TextWatcher() {
        public void beforeTextChanged(CharSequence buffer, int start, int olen, int nlen) {
        }

        public void onTextChanged(CharSequence buffer, int start, int olen, int nlen) {
        }

        public void afterTextChanged(Editable buffer) {
            if (SpecialCharSequenceMgr.handleChars(
                    getContext(), buffer.toString())) {
                mPinEntry.getText().clear();
			   mUnlockButton.setClickable(false);
				mUnlockButton.setEnabled(false);
            }
        }
    };

    //handler for unlock function results
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == EVENT_ICC_NTWRK_DEPERSONALIZATION_RESULT) {
                AsyncResult res = (AsyncResult) msg.obj;
     			mSIMNum = Settings.System.getInt(getContext().getContentResolver(), "sml_sim", -1);
                if (res.exception != null) {
                    if (DBG) log("zhangjinqiang-network depersonalization request failure.");
                    	indicateError();
					//add by zhangjinqiang for HW_SIMLock-start
					timeDelay= Settings.System.getInt(getContext().getContentResolver(), "sml_unlock_time", 0)*1000;
					if(mPhoneID==0){
						leftCounts = Settings.System.getInt(getContext().getContentResolver(), "sml_retry_counts", 255);
					}else if(mPhoneID==1){
						leftCounts = Settings.System.getInt(getContext().getContentResolver(), "sml_sim2_retry_counts", 255);
					}
					//modify by yulifeng for HQ01987750,20160817
					//if(timeDelay!=0){
					if(timeDelay!=0 && leftCounts > 1){
						android.util.Log.d("zhangjinqiang","timeDelay:"+timeDelay + "leftCounts:"+leftCounts);
						updateSimLockTimerScreen();
					}
					if(leftCounts!=255){
						android.util.Log.d("zhangjinqiang","leftCounts:"+leftCounts);
						updateSIMLockLeftTimes();
					}
					long timeInter = timeDelay==0?3000:timeDelay;
                		postDelayed(new Runnable() {
                          public void run() {
							if(leftCounts<=1){
								needPopTop= Settings.System.getInt(getContext().getContentResolver(), "sml_need_pop_top", 0);
								if(needPopTop==0){
									//modify by yulifeng for HQ01987750,20160817
									//dismiss();
									if((mPhoneID==0&&mSIMNum==1)||(mPhoneID==1&&mSIMNum==0)){
										launchSIMIccPanel();
									}
								}
							}else{
                                    	hideAlert();
                                    	mPinEntry.getText().clear();
                                    	mPinEntry.requestFocus();
									mUnlockButton.setClickable(false);
									mUnlockButton.setEnabled(false);
							}
                               }
                       }, timeInter);
                    //add by zhangjinqiang end
                } else {
                    if (DBG) log("zhangjinqiang-network depersonalization success.");
                    indicateSuccess();
                    postDelayed(new Runnable() {
                                    public void run() {
                                        dismiss();
									int smlRelated = Settings.System.getInt(getContext().getContentResolver(), "sml_sim_relate", 0);
									if(((mPhoneID==0&&mSIMNum==1)||(mPhoneID==1&&mSIMNum==0))&&smlRelated==0){
										launchSIMIccPanel();
									}else if((mPhoneID==0||mPhoneID==1)&&smlRelated !=0){
										reSearchNetWork(mPhoneID);
									}else if(SystemProperties.get("ro.hq.telcel.simlock").equals("1")
											&&TelephonyManagerEx.getDefault().getSimState(0)==TelephonyManager.SIM_STATE_ABSENT){
											Intent intent = new Intent("com.android.huawei.simlock.getSIM1Status");
											getContext().sendBroadcast(intent);
									}
                                    }
                                }, 3000);
                }
            }
        }
    };

    //constructor
    public IccNetworkDepersonalizationPanel(Context context, Phone phone) {
        super(context);
        mPhone = phone;
    }

	public IccNetworkDepersonalizationPanel(Context context, Phone phone,int phoneId) {
        super(context);
        mPhone = phone;
	    mPhoneID = phoneId;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
		
        setContentView(R.layout.sim_ndp);

		mTitle =(TextView)findViewById(R.id.perso_subtype_text);

		//add by zhangjinqiang for HQ01629235-start
		if (SystemProperties.get("ro.mtk_gemini_support").equals("1")) {
			if(mPhoneID==0){
				mTitle.setText(mTitle.getText().toString()+"(SIM1)");
			}else if(mPhoneID==1){
				mTitle.setText(mTitle.getText().toString()+"(SIM2)");
			}
		}
		//add by zhangjinqiang end

        // PIN entry text field
        mPinEntry = (EditText) findViewById(R.id.pin_entry);
        mPinEntry.setKeyListener(DialerKeyListener.getInstance());
		/* modify by zhangjinqiang for HQ02060459-start */
        //mPinEntry.setOnClickListener(mUnlockListener); 
        /* modify by zhangjinqiang end */
	    mPinEntry.addTextChangedListener(watcher);

        // Attach the textwatcher
        CharSequence text = mPinEntry.getText();
        Spannable span = (Spannable) text;
        span.setSpan(mPinEntryWatcher, 0, text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);

        mEntryPanel = (LinearLayout) findViewById(R.id.entry_panel);

        mUnlockButton = (Button) findViewById(R.id.ndp_unlock);
        mUnlockButton.setOnClickListener(mUnlockListener);

        // The "Dismiss" button is present in some (but not all) products,
        // based on the "sim_network_unlock_allow_dismiss" resource.

		//add by zhangjinqiang for SIMLock-start
		int canCancelPop = Settings.System.getInt(getContext().getContentResolver(), "sml_can_cancel_pop", 1);
        mDismissButton = (Button) findViewById(R.id.ndp_dismiss);
        if (canCancelPop ==1) {
            if (DBG) log("Enabling 'Dismiss' button...");
            mDismissButton.setVisibility(View.VISIBLE);
            mDismissButton.setOnClickListener(mDismissListener);
        } else {
            if (DBG) log("Removing 'Dismiss' button...");
            mDismissButton.setVisibility(View.GONE);
        }
		//add by zhangjinqiang end

        //add by yulifeng for HQ01987750 20160817,start
        mPanelDismissButton = (Button)findViewById(R.id.panel_dismiss);
        mPanelDismissButton.setOnClickListener(mDismissListener);
        //add by yulifeng for HQ01987750 20160817,end


        //status panel is used since we're having problems with the alert dialog.
        mStatusPanel = (LinearLayout) findViewById(R.id.status_panel);
        mStatusText = (TextView) findViewById(R.id.status_text);

		//add by zhangjinqiang for HW_SIMLock-start
		mTimerText = (TextView)findViewById(R.id.timer_text);
		mLeftTimes = (TextView)findViewById(R.id.left_times_text);
		mEmergencyCall=(Button)findViewById(R.id.emergency_call);
		mEmergencyCall.setOnClickListener(mEmergencyCallListener);
		//add by zhangjinqiang end
    }

    @Override
    protected void onStart() {
        super.onStart();
        //add by yulifeng for simlock reboot show icc,HQ02051595,20160829,start
        Log.d("LevYu","onStart~");
        if(mPhoneID==0){
            leftCounts = Settings.System.getInt(getContext().getContentResolver(), "sml_retry_counts", 255);
        }else if(mPhoneID==1){
            leftCounts = Settings.System.getInt(getContext().getContentResolver(), "sml_sim2_retry_counts", 255);
        }
        Log.d("LevYu","onStart:leftCounts : "+leftCounts);
        if(leftCounts == 0){
            indicateError();
            mLeftTimes.setVisibility(View.VISIBLE);
            mLeftTimes.setText(R.string.sml_no_times);
            mPanelDismissButton.setVisibility(View.VISIBLE);
        }
        //add by yulifeng for simlock reboot show icc,HQ02051595,20160829,end
    }

    //Mirrors IccPinUnlockPanel.onKeyDown().
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    View.OnClickListener mUnlockListener = new View.OnClickListener() {
        public void onClick(View v) {
            String pin = mPinEntry.getText().toString();
			
            if (TextUtils.isEmpty(pin)) {
                return;
            }
			indicateBusy();
			String saltString = Settings.System.getString(getContext().getContentResolver(), "sml_sha");
			if(saltString!=null&&!saltString.equals("00000000")){
				//add by zhangjinqiang for SHA256 encryption
				String passWordSHA = encryptSHA(pin);
				pwd = getPWD(passWordSHA);
				//add by zhangjinqiang end
			}else{
				pwd = pin;
			}
			
		   //add by zhangjinqiang for unlock simlock without sim -start MX
		   //int simState = TelephonyManagerEx.getDefault().getSimState(0);
		   if(SystemProperties.get("ro.hq.telcel.simlock").equals("1")
			 	&&(TelephonyManagerEx.getDefault().getSimState(0) == TelephonyManager.SIM_STATE_ABSENT)){
				 String passWord = Settings.System.getString(getContext().getContentResolver(), "telcel_lock_password");
				 if(passWord!=null&&pwd!=null&&passWord.equals(pwd)){
						String[] unLockString = { "AT+SMLUN=1,", "+SMLUN" };
						setSimLockAction(unLockString, EVENT_ICC_NTWRK_DEPERSONALIZATION_RESULT);
				 }else{
				 		indicateError();
						v.postDelayed(new Runnable() {  
		                        public void run() {  
		         					hideAlert();
                                    	mPinEntry.getText().clear();
                                    	mPinEntry.requestFocus();
									mUnlockButton.setClickable(false);
									mUnlockButton.setEnabled(false);
		                        }  
		                   }, 3000);  
				 }
		   }else{
		   //add by zhangjinqiang end
            	if (DBG) log("zhangjinqiang-requesting network depersonalization with code " + pin);
			if(mPhone!=null){
            			mPhone.getIccCard().supplyNetworkDepersonalization(pwd,
                    Message.obtain(mHandler, EVENT_ICC_NTWRK_DEPERSONALIZATION_RESULT));
				}else{
					if (DBG) log("zhangjinqiang-mPhone is null ");
				}
		   	}
		   /*add by yulifeng for HQ02052248,simlock,20160825,start*/
		   InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		   if(imm != null ){
		       imm.hideSoftInputFromWindow(mPinEntry.getWindowToken(), 0);
		   }
		   /*add by yulifeng for HQ02052248,simlock,20160825,end*/
            
        }
    };

    private void indicateBusy() {
        mStatusText.setText(R.string.requesting_unlock);
        mEntryPanel.setVisibility(View.GONE);
        mStatusPanel.setVisibility(View.VISIBLE);
    }

    private void indicateError() {
        mStatusText.setText(R.string.unlock_failed);
        mEntryPanel.setVisibility(View.GONE);
        mStatusPanel.setVisibility(View.VISIBLE);
    }

    private void indicateSuccess() {
        mStatusText.setText(R.string.unlock_success);
        mEntryPanel.setVisibility(View.GONE);
        mStatusPanel.setVisibility(View.VISIBLE);
        //add by yulifeng for HQ02048418,20160801
        mLeftTimes.setVisibility(View.GONE);
    }

    private void hideAlert() {
        mEntryPanel.setVisibility(View.VISIBLE);
        mStatusPanel.setVisibility(View.GONE);
    }

    View.OnClickListener mDismissListener = new View.OnClickListener() {
            public void onClick(View v) {
                if (DBG) log("mDismissListener: skipping depersonalization...");
                dismiss();
				mSIMNum = Settings.System.getInt(getContext().getContentResolver(), "sml_sim", -1);
 			   android.util.Log.d("zhangjinqiang","mSIMNum:"+mSIMNum);
			   if((mPhoneID==0&&mSIMNum==1)||(mPhoneID==1&&mSIMNum==0)){
					launchSIMIccPanel();
			   }
            }
        };

	//add by zhangjinqiang for SIMLock emergencyCall --start
	 View.OnClickListener mEmergencyCallListener = new View.OnClickListener() {
            public void onClick(View v) {
			   dismiss();
                Intent emergencyIntent = new Intent();
			   emergencyIntent.setAction(ACTION_EMERGENCY_DIAL);
        		   emergencyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        		   getContext().startActivity(emergencyIntent);
            }
        };
	//add by zhangjinqiang for SIMLock emergencyCall end

    private void log(String msg) {
        Log.v(TAG, "[IccNetworkDepersonalizationPanel] " + msg);
    }

	//add by zhangjinqiang for HW_SIMLock -start
	private void updateSimLockTimerScreen(){
			mTimerText.setVisibility(View.VISIBLE);
			new CountDownTimer(timeDelay, timeCount){

				@Override
				public void onFinish() {
					// TODO Auto-generated method stub
					if(mTimerText!=null){
						mTimerText.setVisibility(View.GONE);
					}
					if(mEntryPanel!=null){
						mEntryPanel.setVisibility(View.VISIBLE);
					}
					if(mStatusPanel!=null){
						mStatusPanel.setVisibility(View.GONE);
					}
					if(mPinEntry!=null){
						mPinEntry.getText().clear();
						mPinEntry.requestFocus();
						mUnlockButton.setClickable(false);
						mUnlockButton.setEnabled(false);
					}
				}

				@Override
				public void onTick(long milliseconds) {
					// TODO Auto-generated method stub
					int seconds = (int)(milliseconds/1000);
					android.util.Log.d("zhangjinqiang","seconds:"+seconds);
					mTimerText.setText(getContext().getResources().getQuantityString(R.plurals.timer_update_string, seconds,seconds));
				}
				
			}.start();
		}

	private void updateSIMLockLeftTimes(){
		mLeftTimes.setVisibility(View.VISIBLE);
		if((leftCounts-1)>=1){
			mLeftTimes.setText(getContext().getResources().getString(R.string.left_times_string)+(leftCounts-1));
			if(mPhoneID==0){
				Settings.System.putInt(getContext().getContentResolver(), "sml_retry_counts", leftCounts-1);
			}else if(mPhoneID==1){
				Settings.System.putInt(getContext().getContentResolver(), "sml_sim2_retry_counts", leftCounts-1);
			}
		}else{
			mLeftTimes.setText(R.string.sml_no_times);
			//add by yulifeng for HQ01987750,20160817
			mPanelDismissButton.setVisibility(View.VISIBLE);
			/*HQ_yulifeng for simlock ,20160905,start*/
			if(mPhoneID==0){
			    Settings.System.putInt(getContext().getContentResolver(), "sml_retry_counts", 0);
			}else if(mPhoneID==1){
			    Settings.System.putInt(getContext().getContentResolver(), "sml_sim2_retry_counts", 0);
			}
			/*HQ_yulifeng for simlock ,20160905,end*/
		}
	}

	private void setSimLockAction(String[] str , int action){
		Phone nPhone =  (PhoneProxy) PhoneFactory.getDefaultPhone();
		nPhone.invokeOemRilRequestStrings(str, mHandler.obtainMessage(action));
	}

	private TextWatcher watcher = new TextWatcher() {
   
	    @Override
	    public void onTextChanged(CharSequence s, int start, int before, int count) {
	        // TODO Auto-generated method stub
	       
	    }
	   
	    @Override
	    public void beforeTextChanged(CharSequence s, int start, int count,
	            int after) {
	        // TODO Auto-generated method stub
	       
	    }
	   
	    @Override
	    public void afterTextChanged(Editable s) {
	        // TODO Auto-generated method stub
	        String str = mPinEntry.getText().toString();
		   if(str!=""){
	        		mUnlockButton.setClickable(true);
				mUnlockButton.setEnabled(true);
		   	}else {
				mUnlockButton.setClickable(false);
				mUnlockButton.setEnabled(false);
			}
	    }
	};

	private void launchSIMIccPanel(){
		Intent intent = new Intent("com.android.huawei.sim.iccpanel.show");
		getContext().sendBroadcast(intent);
	}

	private void reSearchNetWork(int mPhoneid){
		int phoneId = mPhoneid==0?1:0;
		try{
			int subId[] = SubscriptionManager.getSubId(phoneId);
			if(subId!=null){
				ITelephonyEx.Stub.asInterface(ServiceManager.getService("phoneEx"))
    					.repollIccStateForNetworkLock(subId[0], true);
			}
		}catch (RemoteException e) {
         	android.util.Log.d("zhangjinqiang", "repollIccStateForNetworkLock exception caught");
         }
	}
	
	private  String encryptSHA(String msg) {
		String salt = Settings.System.getString(getContext().getContentResolver(), "sml_sha")+msg;
		//modify by yulifeng for HQ02052109,20160819
		//android.util.Log.d("zhangjinqiang","salt:"+salt);
		StringBuilder sb = new StringBuilder();
		String tempStr = null;
		try{
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(salt.getBytes());
			byte[] bytes = md.digest();
			android.util.Log.d("zhangjinqiang","bytes.length:"+bytes.length);
			for(int i=0; i< bytes.length ;i++){
				tempStr = Integer.toHexString(bytes[i] & 0xff);
	         	if (tempStr.length() == 1) {
	             		sb.append("0");
	            	} 
				sb.append(tempStr);
			}
		}catch(Exception e){
			android.util.Log.d("zhangjinqiang","EXCEPTION");
		}
		return sb.toString();
	}

	private String getPWD(String pwd){
		pwd = pwd.trim();
		StringBuilder keyStr = new StringBuilder();
		if(pwd==null || "".equals(pwd)){
			return null;
		}
		for(int i=0;i<pwd.length();i++){
			if(pwd.charAt(i)>=48 && pwd.charAt(i)<=57){
 				keyStr.append(pwd.charAt(i));
				if(keyStr.length()==16){
					return keyStr.toString();
				}
			}
		}
 		return null;
	}
	//add by zhangjinqiang end
}
