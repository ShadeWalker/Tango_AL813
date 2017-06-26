package com.mediatek.contacts.vcs;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.android.contacts.R;

public class VoiceSearchRow {

    private View mView;
    private Uri mPhotoUri;
    private String mName;
    private Uri mContactUri;

    protected void startAnimation(Context context) {
        Animation animation = AnimationUtils.loadAnimation(context, R.anim.vcs_row_anim);
        mView.startAnimation(animation);
    }

    public View getView() {
        return mView;
    }

    public void setView(View mView) {
        this.mView = mView;
    }

    public Uri getIcon() {
        return mPhotoUri;
    }

    public void setIcon(Uri photoUri) {
        this.mPhotoUri = photoUri;
    }

    public String getName() {
        return mName;
    }

    public void setName(String mName) {
        this.mName = mName;
    }

    public Uri getContactUri() {
        return mContactUri;
    }

    public void setContactUri(Uri mContactUri) {
        this.mContactUri = mContactUri;
    }

//    public int describeContents() {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    public void writeToParcel(Parcel dest, int flags) {
//        // TODO Auto-generated method stub
//        LogUtils.d("VoiceSearchRow", "writeToParcel");
//        dest.writeString(mName);
//        dest.writeString(mContactUri != null ? mContactUri.toString() : null);
//        dest.writeString(mPhotoUri != null ? mPhotoUri.toString() : null);
//    }

}
