package com.mediatek.galleryframework.base;

public interface Work<T> {
    public boolean isCanceled();
    public T run();
}