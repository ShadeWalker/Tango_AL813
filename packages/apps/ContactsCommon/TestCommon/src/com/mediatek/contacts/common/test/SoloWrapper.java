package com.mediatek.contacts.common.test;

import android.app.Activity;
import android.app.Instrumentation;

import com.jayway.android.robotium.solo.Solo;

public class SoloWrapper extends Solo {

    private static final int FIVE_SECONDS = 5000;

    public SoloWrapper(Instrumentation instrumentation, Activity activity) {
        super(instrumentation, activity);
    }

    @Override
    public boolean waitForText(String text) {
        return super.waitForText(text, 1, FIVE_SECONDS, false);
    }

    @Override
    public void clickOnMenuItem(String text) {
        super.clickOnMenuItem(text);
    }

    @Override
    public void clickOnText(String text) {
        super.clickOnText(text, 1, false);
    }

}
