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
 * limitations under the License.
 */

package com.android.settings.brightness;

import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
import android.provider.Settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.android.settings.R;

/** A dialog that provides controls for adjusting the screen brightness. */
public class BrightnessDialog extends Activity {

    protected BrightnessController mBrightnessController;
    protected boolean mBrightnessControllerFlag = false;
    private CheckBox mAutoBrightnessCB = null;
    private Button CancelButton = null;
    private Button OKButton = null;
    private boolean CheckBoxDefault = false;
    private int valueDefault = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Window window = getWindow();
		 //HQ_jiangchao modify for HQ01368037 at 20150917
        window.setGravity(Gravity.CENTER);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.requestFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.quick_settings_brightness_dialog);

        mAutoBrightnessCB = (CheckBox)findViewById(R.id.brightness_auto_checkbox);
        mAutoBrightnessCB.setOnCheckedChangeListener(new AutoBrightnessOnCheckedChangeListener(BrightnessDialog.this));
        
        OKButton = (Button)findViewById(R.id.brightness_sure_button);
        CancelButton = (Button)findViewById(R.id.brightness_cancel_button);
        		
        final ImageView icon = (ImageView) findViewById(R.id.brightness_icon);
        final ToggleSlider slider = (ToggleSlider) findViewById(R.id.brightness_slider);
        mBrightnessController = new BrightnessController(this, icon, slider);
        
        OKButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				finish();
			}
		});
        
        CancelButton.setOnClickListener(new OnClickListener() {
        	
			@Override
			public void onClick(View v) {
				mAutoBrightnessCB.setChecked(CheckBoxDefault);
				Settings.System.putInt(getContentResolver(), SCREEN_BRIGHTNESS_MODE,
						CheckBoxDefault ? SCREEN_BRIGHTNESS_MODE_AUTOMATIC : SCREEN_BRIGHTNESS_MODE_MANUAL);
				if (valueDefault != -1) {
					mBrightnessController.setSliderValue(valueDefault);
				}
				finish();
			}
		});
    }

    @Override
    protected void onStart() {
        super.onStart();
//        if (!mBrightnessControllerFlag) {
        	mBrightnessController.registerCallbacks();
        	mBrightnessControllerFlag = true;
//        }
        
        int brightnessMode = Settings.System.getInt(getContentResolver(),
                SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL);
        
        CheckBoxDefault = (brightnessMode != SCREEN_BRIGHTNESS_MODE_MANUAL);
        mAutoBrightnessCB.setChecked(CheckBoxDefault);
        
        valueDefault = mBrightnessController.getUpdateSliderValue();
    }

    @Override
    protected void onStop() {
        super.onStop();
//        if (mBrightnessControllerFlag) {
        	mBrightnessController.unregisterCallbacks();
            mBrightnessControllerFlag = false;
//		}
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            finish();
        }

        return super.onKeyDown(keyCode, event);
    }
}

class AutoBrightnessOnCheckedChangeListener implements OnCheckedChangeListener{
	
	BrightnessDialog context = null;
	int updateSliderValue = 0;
	
	public AutoBrightnessOnCheckedChangeListener(BrightnessDialog context){
		this.context = context;
	}
	
	@Override
    public void onCheckedChanged(CompoundButton arg0, boolean isChecked) {
		
//		if (isChecked) {
//			updateSliderValue = context.mBrightnessController.getUpdateSliderValue();
//			if (context.mBrightnessControllerFlag) {
//				context.mBrightnessController.unregisterCallbacks();
//				context.mBrightnessControllerFlag = false;
//			}
//		}else {
//			context.mBrightnessController.setSliderValue(updateSliderValue);
//			if (!context.mBrightnessControllerFlag) {
//				context.mBrightnessController.registerCallbacks();
//				context.mBrightnessControllerFlag = true;
//			}
//		}
		
		Settings.System.putInt(context.getContentResolver(), SCREEN_BRIGHTNESS_MODE,
				isChecked ? SCREEN_BRIGHTNESS_MODE_AUTOMATIC : SCREEN_BRIGHTNESS_MODE_MANUAL);
		
    }
}
