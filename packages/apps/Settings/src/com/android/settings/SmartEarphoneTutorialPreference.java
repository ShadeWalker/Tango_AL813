package com.android.settings;

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class SmartEarphoneTutorialPreference extends Preference {

	public SmartEarphoneTutorialPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected View onCreateView(ViewGroup parent) {
		return LayoutInflater.from(getContext()).inflate(
				R.layout.preference_smart_earphone_control, parent, false);
	}

	@Override
	protected void onBindView(View view) {
		super.onBindView(view);
		ImageView icon = (ImageView) view.findViewById(R.id.img_app_icon);
		AnimationDrawable anim = (AnimationDrawable) icon.getBackground();
		anim.start();
	}

}

