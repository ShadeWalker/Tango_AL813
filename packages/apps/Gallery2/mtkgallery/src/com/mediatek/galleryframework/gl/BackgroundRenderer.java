/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.mediatek.galleryframework.gl;

import java.util.ArrayDeque;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL11;

import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.galleryframework.util.Utils;
import com.mediatek.galleryframework.util.MtkLog;

public class BackgroundRenderer extends Thread {
    private static final String TAG = "MtkGallery2/BackgroundRenderer";
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    private final ArrayDeque<BackgroundGLTask> mGLTasks = new ArrayDeque<BackgroundGLTask>();

    private EGLConfig mEglConfig;
    private EGLDisplay mEglDisplay;
    private EGLContext mEglContext;
    private EGLSurface mEglSurface;
    private EGL10 mEgl;
    private MGLCanvas mCanvas;
    private int mVersion;

    private static BackgroundRenderer sInstance;

    public static synchronized BackgroundRenderer getInstance() {
        if (sInstance == null) {
            sInstance = new BackgroundRenderer("Thread-BackgroundRenderer",
                    Utils.HAS_GLES20_REQUIRED && !FeatureConfig.supportEmulator ? 2 : 1);
            sInstance.start();
        }
        return sInstance;
    }

    public static synchronized void destroyInstance() {
        if (sInstance != null) {
            MtkLog.i(TAG, "<destroyInstance>");
            sInstance.interrupt();
            synchronized (sInstance) {
                sInstance.notifyAll();
            }
            sInstance = null;
        }
    }

    private BackgroundRenderer(String name, int version) {
        super(name);
        Utils.assertTrue(version == 1 || version == 2);
        mVersion = version;
    }

    @Override
    public void run() {
        MtkLog.i(TAG, "<run> begin");
        initialize();
        while (!isInterrupted()) {
            mCanvas.clearBuffer();
            mCanvas.deleteRecycledResources();
            BackgroundGLTask task = null;
            synchronized (BackgroundRenderer.this) {
                if (!mGLTasks.isEmpty())
                    task = mGLTasks.removeFirst();
                if (task == null && !isInterrupted()) {
                    Utils.waitWithoutInterrupt(BackgroundRenderer.this);
                }
            }
            if (task != null) {
                MtkLog.i(TAG, "<run> run task: " + task);
                task.run(mCanvas);
            }
        }
        release();
        MtkLog.i(TAG, "<run> end");
    }

    public static interface BackgroundGLTask {
        public boolean run(MGLCanvas canvas);
    }

    public void addGLTask(BackgroundGLTask task) {
        synchronized (BackgroundRenderer.this) {
            mGLTasks.addLast(task);
        }
    }

    public void requestRender() {
        synchronized (BackgroundRenderer.this) {
            notifyAll();
        }
    }

    private void initialize() {
        mEgl = (EGL10) EGLContext.getEGL();
        mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (mEglDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed");
        }
        int[] version = new int[2];
        if (!mEgl.eglInitialize(mEglDisplay, version)) {
            throw new RuntimeException("eglInitialize failed");
        } else {
            MtkLog.v(TAG, "<initialize> EGL version: " + version[0] + '.' + version[1]);
        }
        int[] attribList = { EGL_CONTEXT_CLIENT_VERSION, mVersion, EGL10.EGL_NONE };
        mEglConfig = chooseConfig(mEgl, mEglDisplay);
        mEglContext = mEgl.eglCreateContext(mEglDisplay, mEglConfig, EGL10.EGL_NO_CONTEXT,
                attribList);
        if (mEglContext == null || mEglContext == EGL10.EGL_NO_CONTEXT) {
            throw new RuntimeException("failed to createContext");
        } else {
            MtkLog.v(TAG, "<initialize> EGL context: create success");
        }
        int[] attribSurfaceList = { EGL10.EGL_WIDTH, 1, EGL10.EGL_HEIGHT, 1, EGL10.EGL_NONE };
        mEglSurface = mEgl.eglCreatePbufferSurface(mEglDisplay, mEglConfig, attribSurfaceList);
        if (mEglSurface == null || mEglSurface == EGL10.EGL_NO_SURFACE) {
            throw new RuntimeException("failed to createPbufferSurface");
        } else {
            MtkLog.v(TAG, "<initialize> EGL surface: create success");
        }
        if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            throw new RuntimeException("failed to eglMakeCurrent");
        } else {
            MtkLog.v(TAG, "<initialize> EGL make current: success");
        }
        if (mVersion == 1) {
            mCanvas = new MGLES11Canvas((GL11) mEglContext.getGL(), true);
        } else {
            mCanvas = new MGLES20Canvas();
        }
        // set a non-zero size
        mCanvas.setSize(360, 480);
    }

    private void release() {
        MtkLog.i(TAG, "<release> begin");
        mEgl.eglDestroySurface(mEglDisplay, mEglSurface);
        mEgl.eglDestroyContext(mEglDisplay, mEglContext);
        mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_CONTEXT);
        mEgl.eglTerminate(mEglDisplay);
        mEglSurface = null;
        mEglContext = null;
        mEglDisplay = null;
        MtkLog.i(TAG, "<release> end");
    }

    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final int[] CONFIG_SPEC = new int[] { EGL10.EGL_RENDERABLE_TYPE,
            EGL_OPENGL_ES2_BIT, EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 0, EGL10.EGL_DEPTH_SIZE, 0,
            EGL10.EGL_STENCIL_SIZE, 0, EGL10.EGL_NONE };

    private static EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
        int[] numConfig = new int[1];
        if (!egl.eglChooseConfig(display, CONFIG_SPEC, null, 0, numConfig)) {
            throw new IllegalArgumentException("eglChooseConfig failed");
        }

        int numConfigs = numConfig[0];
        if (numConfigs <= 0) {
            throw new IllegalArgumentException("No configs match configSpec");
        }

        EGLConfig[] configs = new EGLConfig[numConfigs];
        if (!egl.eglChooseConfig(display, CONFIG_SPEC, configs, numConfigs, numConfig)) {
            throw new IllegalArgumentException("eglChooseConfig#2 failed");
        }

        return configs[0];
    }
}