package com.mediatek.incallui.ext;

public class DefaultInCallExt implements IInCallExt {

    @Override
    public String replaceString(String defaultString, String hint) {
        return defaultString;
    }

    @Override
    public Object replaceValue(Object defaultObject, String hint) {
        return defaultObject;
    }

}
