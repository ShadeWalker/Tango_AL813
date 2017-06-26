package com.mediatek.settings.deviceinfo;
/* HQ_yulisuo 2015-06-10 modified for HQ01176489 */
import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
   
import com.android.settings.R;
import com.mediatek.xlog.Xlog;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.widget.CompoundButton;
import android.os.SystemProperties;

public class UsbDebugPreference extends Preference implements View.OnClickListener,
           CompoundButton.OnCheckedChangeListener{
       private static final int TITLE_ID = R.id.usbdebug_preference_title;
       private static final int SUMMARY_ID = R.id.usbdebug_preference_summary;
       private static final int BUTTON_ID = R.id.usbdebug_preference_radiobutton;
       private TextView mPreferenceTitle = null;
       private TextView mPreferenceSummary = null;
       private CheckBox mPreferenceButton = null;
       private CharSequence mTitleValue = "";
       private CharSequence mSummaryValue = "";
       private boolean mChecked = false;
       private Dialog mAdbDialog;
       private Dialog mDetailDialog;
       private boolean mDialogClicked;
       private static final String TAG = "UsbDebugPreference";
   	   private String mCountryCode = null;//zhuchao add
       /**
        * UsbPreference construct
        * 
        * @param context
        *            the preference associated with
        */
       public UsbDebugPreference(Context context) {
           this(context, null);
       }
   
       /**
        * UsbPreference construct
        * 
        * @param context
        *            the preference associated with
        * @param attrs
        *            the attribute the xml will be inflated to
        */
       public UsbDebugPreference(Context context, AttributeSet attrs) {
           this(context, attrs, 0);
       }
  
      /**
       * UsbPreference construct
      * 
       * @param context
       *            the preference associated with
        * @param attrs
       *            the attribute the xml will be inflated to
       * @param defStyle
       *            the style which will be apply to the preference
       */
      public UsbDebugPreference(Context context, AttributeSet attrs, int defStyle) {
          super(context, attrs, defStyle);
          setLayoutResource(R.layout.preference_usb_debug);
  
         // get the title from audioprofile_settings.xml
          if (super.getTitle() != null) {
              mTitleValue = super.getTitle().toString();
          }
 
          // get the summary from audioprofile_settings.xml
          if (super.getSummary() != null) {
              mSummaryValue = super.getSummary().toString();
          }
      }
  
      @Override
     public View getView(View convertView, ViewGroup parent) {
         View view = super.getView(convertView, parent);
          Xlog.d(TAG, "getview");
         mPreferenceTitle = (TextView) view.findViewById(TITLE_ID);
          mPreferenceTitle.setText(mTitleValue);
          mPreferenceSummary = (TextView) view.findViewById(SUMMARY_ID);
          mPreferenceSummary.setText(mSummaryValue);
          mPreferenceSummary.setMovementMethod(LinkMovementMethod.getInstance());
          //mPreferenceSummary.setOnClickListener(this);
          mPreferenceButton = (CheckBox) view.findViewById(BUTTON_ID);
          mPreferenceButton.setOnClickListener(this);
          mPreferenceButton.setOnCheckedChangeListener(this);
          mPreferenceButton.setChecked(
                          Settings.Global.getInt(getContext().getContentResolver(), Settings.Global.ADB_ENABLED, 0) != 0);
          return view;
      }
  
      @Override
      public void setTitle(CharSequence title) {
          if (null == mPreferenceTitle) {
              mTitleValue = title;
          }
          if (!title.equals(mTitleValue)) {
              mTitleValue = title;
              mPreferenceTitle.setText(mTitleValue);
          }
      }
  
     @Override
      public CharSequence getTitle() {
          return mTitleValue;
      }
  
      public TextView getSummaryView() {
          return mPreferenceSummary;
      }
  
      /**
       * set the preference summary
       * 
       * @param summary
       *            the preference summary
       */
      public void setSummary(CharSequence summary) {
          if (null == mPreferenceSummary) {
              mSummaryValue = summary;
          }
          if (!summary.equals(mSummaryValue)) {
              mSummaryValue = summary;
              mPreferenceSummary.setText(mSummaryValue);
          }
      }
  
      /**
       * get the preference summary
       * 
       * @return the preference summary
       */
      public CharSequence getSummary() {
          return mSummaryValue;
      }
  
      /**
       * get the preference checked status
       * 
       * @return the checked status
       */
      public boolean isChecked() {
          Xlog.i(TAG, "isChecked  mPreferenceButton="+mPreferenceButton);
          if (mPreferenceButton != null) {
              Xlog.i(TAG, "isChecked  isChecked="+mPreferenceButton.isChecked());
              return mPreferenceButton.isChecked();
          }
          return false;
      }
  
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          Xlog.i(TAG, "onCheckedChanged  isChecked="+isChecked);
		ShowAdbDebugDialog();
      }
  
      @Override
      public void onClick(View v) {
      	Xlog.i(TAG, "View onclick");
          if (v.getId() == BUTTON_ID) {
                  //ShowAdbDebugDialog();
          }else if(v.getId() == SUMMARY_ID) {
                  Xlog.e(TAG+"-","onclick");
				  mCountryCode =  SystemProperties.get("ro.product.countrycode");
	   			  /*  if(SystemProperties.get("ro.hq_ut_function_modify","0").equals("1")){	
						if(!mCountryCode.equals("RU")){
                  			ShowDetailDialog();
					     }
	   				}else{
						ShowDetailDialog();
					} */
          }
  
      }
  
  
      /**
       * set the preference checked status
       * 
       * @param checked
       *            the checked status
       * @return set success or fail
       */
      public boolean setChecked(boolean checked) {
          Xlog.i(TAG, "setChecked  mPreferenceButton="+mPreferenceButton);
          if (mPreferenceButton != null) {
              Xlog.i(TAG, "setChecked  checked="+checked);
              mPreferenceButton.setChecked(checked);
              return true;
          }
          return false;
      }
  
      private void ShowAdbDebugDialog() {
          if (isChecked()) {
		  		Xlog.i(TAG, "ShowAdbDebugDialog 1 ");
				if(Settings.Global.getInt(getContext().getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 0){
					mDialogClicked = false;
					mAdbDialog = new AlertDialog.Builder(getContext()).setMessage(
							getContext().getResources().getString(R.string.adb_warning_message))
							.setCancelable(false)
							.setTitle(R.string.adb_warning_title)
							.setIconAttribute(android.R.attr.alertDialogIcon)
							.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener(){
									 @Override
								 public void onClick(DialogInterface dialog, int which){
											 mDialogClicked = true;
											 Settings.Global.putInt(getContext().getContentResolver(),
											 Settings.Global.ADB_ENABLED, 1);
											setChecked(true);
									 }		
									
							})
							.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener(){
									@Override
								 public void onClick(DialogInterface dialog, int which){
											setChecked(false);
									}
							})
							.show();
					mAdbDialog.setOnDismissListener(new OnDismissListener() {
					
						public void onDismiss(DialogInterface dlgi) {
							if(!mDialogClicked)
							setChecked(false);
						}
					});

				}
                  

             } else {
	              if(Settings.Global.getInt(getContext().getContentResolver(), Settings.Global.ADB_ENABLED, 0) != 0){
					  Xlog.i(TAG, "ShowAdbDebugDialog 2 ");
										Settings.Global.putInt(getContext().getContentResolver(),
												Settings.Global.ADB_ENABLED, 0);
										setChecked(false);

				  }
              	
              }
      }


		/*
      private void ShowDetailDialog() {
          mDetailDialog = new AlertDialog.Builder(getContext())
                          .setMessage(getContext().getResources().getString(R.string.huawei_softwore))
                          .setTitle(R.string.huawei_softwore_title)
                         .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener(){
                                  @Override
                               public void onClick(DialogInterface dialog, int which){
                                   }      
                                  
                          }).show();
      } */
  }

