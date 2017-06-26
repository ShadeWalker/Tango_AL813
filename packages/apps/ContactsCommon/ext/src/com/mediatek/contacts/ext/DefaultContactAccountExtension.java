package com.mediatek.contacts.ext;

public class DefaultContactAccountExtension implements IContactAccountExtension {

    @Override
    public boolean enableFeature(String accountType, String featureName) {
        //default need all features
        return true;
    }

}
