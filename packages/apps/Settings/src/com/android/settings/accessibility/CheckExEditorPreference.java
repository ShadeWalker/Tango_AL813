package com.android.settings.accessibility;

import android.content.Context;
import android.content.res.Resources;
import android.preference.Preference;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import com.android.settings.R;
import android.provider.ChildMode;
import java.lang.RuntimeException;

import com.android.settings.NetTrafficUtils;
public class CheckExEditorPreference extends Preference
 implements View.OnClickListener,CompoundButton.OnCheckedChangeListener
{
    private static final String TAG = "CheckExSettingPreference";
    private boolean mChecked = false;
    protected boolean mHasFocus = false;
    private View.OnClickListener mListener = null;
    private View.OnFocusChangeListener mOnFocusChangeListener = new OnFocusChangeListener(){
        public void onFocusChange(View paramView, boolean paramBoolean){
            mHasFocus = paramBoolean;
        }
    };
    private EditText mPreferenceEditor;
    private TextView mPreferenceSummary = null;
    private Switch mPreferenceSwitch;
    private TextView mPreferenceTitle = null;
    private TextView mPreferenceUnit;
    private CharSequence mSummaryValue = "";
    private String mSwitchKey = null;
    private boolean istimelimitpreference = false;
    private boolean istrafficlimitpreference = false;
    private TextWatcher mTextWatcher = new TextWatcher(){
        public void afterTextChanged(Editable paramEditable){
            String str = paramEditable.toString();
            Log.d("lhz", "key:"+getKey());
            Log.d("lhz", "INTERNET_LIMIT_TIME_UP:"+ChildMode.getString(getContext().getContentResolver(),ChildMode.INTERNET_LIMIT_TIME_UP));
            Log.d("lhz", "INTERNET_LIMIT_TRAFFIC_UP:"+ChildMode.getString(getContext().getContentResolver(),ChildMode.INTERNET_LIMIT_TRAFFIC_UP));
            ChildMode.putString(getContext().getContentResolver(),ChildMode.INTERNET_LIMIT_TIME_UP ,"0");
            ChildMode.putString(getContext().getContentResolver(),ChildMode.INTERNET_LIMIT_TRAFFIC_UP ,"0");
            if (!TextUtils.isEmpty(str)){
                if (getKey().contains("time")) {
                    ChildMode.putString(getContext().getContentResolver(),ChildMode.INTERNET_TIME_RESTRICTION,str);
                    ChildMode.putString(getContext().getContentResolver(),"internet_time_restriction_limit" ,"0");
                    NetTrafficUtils.setTimeLimit(getContext(), false);
                    NetTrafficUtils.setTimeLimit(getContext(), true);
                }else if(getKey().contains("traffic")){
                    ChildMode.putString(getContext().getContentResolver(),ChildMode.INTERNET_TRAFFIC_RESTRICTION,str);
                    ChildMode.putString(getContext().getContentResolver(),"internet_traffic_restriction_limit" ,"0");
                    NetTrafficUtils.settraffic(getContext(),false);
                    NetTrafficUtils.settraffic(getContext(), true);
                }
            }
            String str11 = ChildMode.getString(getContext().getContentResolver(),ChildMode.INTERNET_TIME_RESTRICTION);
            if (istimelimitpreference) {
                mPreferenceSummary.setText(getContext().getString(R.string.internet_time_settings_summary,str11));
            }
        }

        public void beforeTextChanged(CharSequence paramCharSequence, int paramInt1, int paramInt2, int paramInt3){
        }

        public void onTextChanged(CharSequence paramCharSequence, int paramInt1, int paramInt2, int paramInt3){
        }
    };
    private CharSequence mTitleValue = "";
    private String mUnitKey = null;
    private String[] mUnitNames = null;
    private int[] mUnits;
    private String mValueKey = null;

    public CheckExEditorPreference(Context paramContext){
        this(paramContext, null);
    }

    public CheckExEditorPreference(Context paramContext, AttributeSet paramAttributeSet){
        this(paramContext, paramAttributeSet, 0);
    }

    public CheckExEditorPreference(Context paramContext, AttributeSet paramAttributeSet, int paramInt){
        super(paramContext, paramAttributeSet, paramInt);
        setLayoutResource(R.layout.preference_checkexsetting);
        if (super.getTitle() != null)
            this.mTitleValue = super.getTitle().toString();
        if (super.getSummary() != null)
            this.mSummaryValue = super.getSummary().toString();
    }

    public CharSequence getSummary(){
        return super.getSummary();
    }

    public CharSequence getTitle(){
        return this.mTitleValue;
    }

    public View getView(View paramView, ViewGroup paramViewGroup){
        View localView = super.getView(paramView, paramViewGroup);
        mPreferenceEditor = ((EditText)localView.findViewById(R.id.edit_time));
        mPreferenceEditor.setLongClickable(false);
        mPreferenceEditor.setTextIsSelectable(false); //
        mPreferenceUnit = ((TextView)localView.findViewById(R.id.unit));
        mPreferenceTitle = ((TextView)localView.findViewById(R.id.preference_title));
        mPreferenceTitle.setText(mTitleValue);
        mPreferenceSwitch = ((Switch)localView.findViewById(R.id.pref_switch));
        mPreferenceSummary = ((TextView)localView.findViewById(R.id.preference_summary));
        mPreferenceSummary.setText(mSummaryValue);
        refreshSettings();
        mPreferenceSwitch.setOnClickListener(this);
        mPreferenceSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                    Log.d("zsp","onCheckedChanged=========" + isChecked );
                    if (isChecked) {
                        String strtime = ChildMode.getString(getContext().getContentResolver(),ChildMode.INTERNET_TIME_RESTRICTION);
                        String strtranfic = ChildMode.getString(getContext().getContentResolver(),ChildMode.INTERNET_TRAFFIC_RESTRICTION);
                        mPreferenceEditor.setVisibility(0);
                        mPreferenceUnit.setVisibility(0);
                        if (istimelimitpreference) {
                            ChildMode.putString(getContext().getContentResolver(),ChildMode.INTERNET_TIME_RESTRICTION_ON ,"1");
                            Log.d("byj","setTimeLimit true" );
                            mPreferenceEditor.setText(strtime);
                            NetTrafficUtils.setTimeLimit(getContext(),true);
                            ChildMode.putString(getContext().getContentResolver(),"internet_time_restriction_limit" ,"0");
                        }else{
                            mPreferenceUnit.setText(R.string.tranfic);
                            ChildMode.putString(getContext().getContentResolver(),ChildMode.INTERNET_TRAFFIC_RESTRICTION_ON ,"1");
                            mPreferenceEditor.setText(strtranfic);
                            Log.d("byj","new tracfficuit" );
                            ChildMode.putString(getContext().getContentResolver(),"internet_traffic_restriction_limit" ,"0");
                            NetTrafficUtils.settraffic(getContext(),true);
                        }
                        mPreferenceEditor.addTextChangedListener(mTextWatcher);
                      } else {
                          mPreferenceEditor.setVisibility(8);
                          mPreferenceEditor.removeTextChangedListener(mTextWatcher);
                          mPreferenceUnit.setVisibility(8);
                          if (istimelimitpreference) {
                              ChildMode.putString(getContext().getContentResolver(),ChildMode.INTERNET_TIME_RESTRICTION_ON ,"0");
                              
                              NetTrafficUtils.setTimeLimit(getContext(),false);
                              ChildMode.putString(getContext().getContentResolver(),"internet_limit_time_up","0");
                              ChildMode.putString(getContext().getContentResolver(),"internet_time_restriction_limit" ,"0");
                              Log.d("lihaizhou","internet_limit_time_up"+""+ChildMode.getString(getContext().getContentResolver(),ChildMode.INTERNET_LIMIT_TIME_UP));
                            }else{
                                mPreferenceUnit.setText(R.string.tranfic);
                                ChildMode.putString(getContext().getContentResolver(),ChildMode.INTERNET_TRAFFIC_RESTRICTION_ON ,"0");
                                Log.d("byj","new tracfficuit" );
                                ChildMode.putString(getContext().getContentResolver(),"internet_traffic_restriction_limit" ,"0");
                                NetTrafficUtils.settraffic(getContext(),false);
                            }
                        }
                    }
        });//Add By Zhangshuiping  20150327


    //    mPreferenceEditor.removeTextChangedListener(mTextWatcher);
    //    mPreferenceEditor.addTextChangedListener(mTextWatcher);
        mPreferenceEditor.setOnFocusChangeListener(mOnFocusChangeListener);
        /*if (this.mListener != null)
          this.mPreferenceUnit.setOnClickListener(this.mListener);*/

        if (mHasFocus)
            mPreferenceEditor.requestFocus();
        return localView;
    }

    public void onCheckedChanged(CompoundButton paramCompoundButton, boolean paramBoolean)
    {
    	Log.i("ZSP", "onCheckedChanged(" + paramCompoundButton + ") ");
        

    }
    public boolean isChecked(){
        return this.mChecked;
    }
    public void onClick(View paramView){
        if(paramView == mPreferenceSwitch) {
        	//del By Zhangshuiping  20150327
        }else {
            return;
          }
    }

    public void refreshSettings(){
        String isTimeOn = ChildMode.getString(getContext().getContentResolver(),ChildMode.INTERNET_TIME_RESTRICTION_ON);
        String isTranficOn = ChildMode.getString(getContext().getContentResolver(),ChildMode.INTERNET_TRAFFIC_RESTRICTION_ON);
        String strtime = ChildMode.getString(getContext().getContentResolver(),ChildMode.INTERNET_TIME_RESTRICTION);
        String strtranfic = ChildMode.getString(getContext().getContentResolver(),ChildMode.INTERNET_TRAFFIC_RESTRICTION);
        if (mPreferenceEditor != null){
          if (istimelimitpreference) {
            mPreferenceEditor.setText(strtime);
        }else {
          mPreferenceEditor.setText(strtranfic);
        }
          if (istrafficlimitpreference) {
            mPreferenceUnit.setText(R.string.tranfic);
        }    
        }
        if (istimelimitpreference) {
            mPreferenceSwitch.setChecked(isTimeOn != null && "1".equals(isTimeOn));
            mPreferenceSummary.setText(getContext().getString(R.string.internet_time_settings_summary,strtime));
      }else{
            mPreferenceSwitch.setChecked(isTranficOn != null && "1".equals(isTranficOn));
      }
    
        if (mPreferenceSwitch.isChecked()) {
          mPreferenceEditor.setVisibility(0);
              mPreferenceEditor.addTextChangedListener(mTextWatcher);
          mPreferenceUnit.setVisibility(0);
      } else {
        
        mPreferenceEditor.setVisibility(8);
            mPreferenceEditor.removeTextChangedListener(mTextWatcher);
         mPreferenceUnit.setVisibility(8);
      }
      }

    public boolean setChecked(boolean paramBoolean){
        this.mChecked = paramBoolean;
        return true;
    }

    public void setUnitListener(View.OnClickListener paramOnClickListener){
        this.mListener = paramOnClickListener;
    }

    public void setupEditor(String paramString){
        // ChildMode.putString(getContext().getContentResolver(),paramString,str);
        Log.d("zsp", "setupEditor" + paramString);
        Log.d("zsp", "this" + this);
        if ("is_network_limit_time".equals(paramString)) {
            istimelimitpreference = true;
        }
        if ("is_network_traffic_traffic".equals(paramString)) {
            istrafficlimitpreference = true;
        }
    }
}
