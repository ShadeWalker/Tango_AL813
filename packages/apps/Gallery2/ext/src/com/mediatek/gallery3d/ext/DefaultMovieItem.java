package com.mediatek.gallery3d.ext;

import android.net.Uri;

public class DefaultMovieItem implements IMovieItem {
    private static final String TAG = "Gallery2/DefaultMovieItem";
    private static final boolean LOG = true;

    private Uri mUri;
    private String mMimeType;
    private String mTitle;
    private boolean mError;
    private Uri mOriginal;

    public DefaultMovieItem(Uri uri, String mimeType, String title) {
        mUri = uri;
        mMimeType = mimeType;
        mTitle = title;
        mOriginal = uri;
    }
    public DefaultMovieItem(String uri, String mimeType, String title) {
        this(Uri.parse(uri), mimeType, title);
    }

    @Override
    public Uri getUri() {
        return mUri;
    }

    @Override
    public String getMimeType() {
        return mMimeType;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    @Override
    public void setUri(Uri uri) {
        mUri = uri;
    }

    @Override
    public void setMimeType(String mimeType) {
        mMimeType = mimeType;
    }

    @Override
    public Uri getOriginalUri() {
        return mOriginal;
    }

    @Override
    public void setOriginalUri(Uri uri) {
        mOriginal = uri;
    }

    @Override
    public String toString() {
        return new StringBuilder().append("MovieItem(uri=")
        .append(mUri)
        .append(", mime=")
        .append(mMimeType)
        .append(", title=")
        .append(mTitle)
        .append(", error=")
        .append(mError)
        .append(", mOriginal=")
        .append(mOriginal)
        .append(")")
        .toString();
    }
}