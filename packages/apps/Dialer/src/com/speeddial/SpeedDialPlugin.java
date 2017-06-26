package com.speeddial;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.speeddial.parser.SpecialKeyParser;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.Exception;
import java.lang.String;
import java.util.Map;
import java.util.Set;


/**
 * Created by guofeiyao on 15-9-29
 * For SpeedDial all version support
 */
public class SpeedDialPlugin implements View.OnLongClickListener {
    public static final String TAG = SpeedDialPlugin.class.getSimpleName();
    public static final String DZ = "duanze_";

    private Activity mHostActivity;
    private String mHostPackage;
    private Resources mHostResources;
    private EditText mEditText;

    public static boolean consumedThree = false;

    private Context mContext;

    public static String[] identifierNameArr;

    public static Map<String, String> specialKeyMap;
    public static Set<String> specialKeySet;

    public SpeedDialPlugin(Context context) {
        mContext = context;
    }

    public Set<String> getSpecialKeySet() {
        return specialKeySet;
    }

    private boolean isInNameArr(String name) {
        if (null == identifierNameArr || 0 == identifierNameArr.length) {
            return false;
        }

        for (String str : identifierNameArr) {
            if (str.equals(name)) {
                return true;
            }
        }

        return false;
    }

    public void onViewCreated(Activity activity, View view, String[] idArr, EditText editText, int xmlId) {
        Log.i(TAG, "onViewCreated.");
        mHostActivity = activity;

        mHostPackage = activity.getPackageName();
        mHostResources = activity.getResources();

        identifierNameArr = idArr;
        mEditText = editText;

        if (null == identifierNameArr || 8 != identifierNameArr.length) {
            Log.e(DZ + TAG, "Error when test identifierNameArr!");
            return;
        }

        if (-1 != xmlId) {
            try {
                Log.e(DZ + TAG, "begin parse. ");
                specialKeyMap = SpecialKeyParser.parse(mHostResources.getXml(xmlId));
                Set<String> specialKeySet = specialKeyMap.keySet();
                for (String key : specialKeySet) {
                    Log.e(DZ + TAG, "name:" + key + " number:" + specialKeyMap.get(key));
                }

            } catch (IOException | XmlPullParserException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            specialKeyMap = null;
        }

        if (null != specialKeyMap && 0 < specialKeyMap.size()) {
            specialKeySet = specialKeyMap.keySet();
        } else {
            specialKeySet = null;
        }

        for (String name : identifierNameArr) {
            if (null != specialKeySet) {
                if (specialKeySet.contains(name)) {
                    Log.e(DZ + TAG, "we have " + name + " in Set,so continue");
                    continue;
                }
            }

            if (consumedThree && "three".equals(name)){
                continue;
            }

            view.findViewById(mHostResources.getIdentifier(name, "id", mHostPackage)).setOnLongClickListener(this);
        }
    }


    @Override
    public boolean onLongClick(View v) {
// We have tested it in the above method.
//        if (null == identifierNameArr || 8 != identifierNameArr.length) {
//            Log.e(DZ + TAG, "Error when test identifierNameArr!");
//            return false;
//        }

        if (null == mEditText) {
            Log.e(DZ + TAG, "Error when test mEditText!");
            return false;
        }

        int id = v.getId();

        int key = 0;
        if (id == mHostResources.getIdentifier(identifierNameArr[0], "id", mHostPackage)) {
            key = 2;
        } else if (id == mHostResources.getIdentifier(identifierNameArr[1], "id", mHostPackage)) {
            key = 3;
        } else if (id == mHostResources.getIdentifier(identifierNameArr[2], "id", mHostPackage)) {
            key = 4;
        } else if (id == mHostResources.getIdentifier(identifierNameArr[3], "id", mHostPackage)) {
            key = 5;
        } else if (id == mHostResources.getIdentifier(identifierNameArr[4], "id", mHostPackage)) {
            key = 6;
        } else if (id == mHostResources.getIdentifier(identifierNameArr[5], "id", mHostPackage)) {
            key = 7;
        } else if (id == mHostResources.getIdentifier(identifierNameArr[6], "id", mHostPackage)) {
            key = 8;
        } else if (id == mHostResources.getIdentifier(identifierNameArr[7], "id", mHostPackage)) {
            key = 9;
        }

        if (key > 0 && mEditText.getText().length() <= 1) {
            SpeedDialController.getInstance().handleKeyLongProcess(mHostActivity, mContext, key);
            mEditText.getText().clear();
            return true;
        }
        return false;
    }

    public void onPause(){
        SpeedDialController.getInstance().onPause();
    }
}
