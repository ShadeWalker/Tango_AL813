package com.mediatek.galleryfeature.video;

import com.mediatek.galleryframework.util.MtkLog;

import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;

public class GLUtil {
    public static final String TAG = "MtkGallery2/GLUtil";
    public static float[] createIdentityMtx() {
        float[] matrix = new float[16];
        Matrix.setIdentityM(matrix, 0);
        return matrix;
    }

    public static float[] createFullSquareVtx(float width, float height) {
        float vertices[] = new float[] {
            0,     0,      0,
            0,     height, 0,
            width, height, 0,

            width, height, 0,
            width, 0,      0,
            0,     0,      0,
        };

        return vertices;
    }

    public static float[] createTopRightRect(int width, int height, int toTop) {
        float vertices[] = new float[] {
                (float) height / 4,                0 + toTop, 0,
                (float) height / 4, (float) height / 4 + toTop, 0,
                (float) height / 2, (float) height / 4 + toTop, 0,

                (float) height / 2, (float) height / 4 + toTop, 0,
                (float) height / 2,                0 + toTop, 0,
                (float) height / 4,                0 + toTop, 0,
          };
        return vertices;
    }
//
//    public static float[] createTopRightRect(AnimationRect rect) {
//        float vertices[]=new float[] {
//                rect.getLeftTop()[0],      rect.getLeftTop()[1], 0,
//                rect.getLeftBottom()[0],   rect.getLeftBottom()[1],0,
//                rect.getRightBottom()[0],  rect.getRightBottom()[1],0,
//
//                rect.getRightBottom()[0],  rect.getRightBottom()[1],0,
//                rect.getRightTop()[0],     rect.getRightTop()[1],0,
//                rect.getLeftTop()[0],      rect.getLeftTop()[1], 0,
//          };
//        return vertices;
//    }

    public static float[] createSquareVtxByCenterEdge(float centerX, float centerY, float edge) {
        float vertices[] = new float[] {
                (float) (centerX - (float) edge / 2), (float) (centerY - (float) edge / 2), 0,
                (float) (centerX - (float) edge / 2), (float) (centerY + (float) edge / 2), 0,
                (float) (centerX + (float) edge / 2), (float) (centerY + (float) edge / 2), 0,

                (float) (centerX + (float) edge / 2), (float) (centerY + (float) edge / 2), 0,
                (float) (centerX + (float) edge / 2), (float) (centerY - (float) edge / 2), 0,
                (float) (centerX - (float) edge / 2), (float) (centerY - (float) edge / 2), 0,
        };
        return vertices;
    }

    public static float[] createTexCoord() {
        float texCoor[] = new float[] {
                          0, 0,
                          0, 1f,
                          1f, 1f,

                          1f, 1f,
                          1f, 0,
                          0, 0
          };
          return texCoor;
    }

    public static float[] createTexCoord(float lowWidth, float highWidth, float lowHeight, float highHeight) {
        float texCoor[] = new float[] {
                          lowWidth, lowHeight,
                          lowWidth, highHeight,
                          highWidth, highHeight,

                          highWidth, highHeight,
                          highWidth, lowHeight,
                          lowWidth, lowHeight
          };
          return texCoor;
    }

    // create 0 degree texture coordinate
    public static float[] createStandTexCoord(float lowWidth, float highWidth, float lowHeight, float highHeight) {
        float texCoor[] = new float[] {
                          highWidth, lowHeight,
                          highWidth, highHeight,
                          lowWidth, highHeight,

                          lowWidth, highHeight,
                          lowWidth, lowHeight,
                          highWidth, lowHeight
          };
          return texCoor;
    }

    // create 180 degree texture coordinate
    public static float[] createReverseStandTexCoord(float lowWidth, float highWidth, float lowHeight, float highHeight) {
        float texCoor[] = new float[] {
                lowWidth, highHeight,
                lowWidth, lowHeight,
                highWidth, lowHeight,

                highWidth, lowHeight,
                highWidth, highHeight,
                lowWidth, highHeight
        };
        return texCoor;
    }

    // create 90 degree texture coordinate
    public static float[] createRightTexCoord(float lowWidth, float highWidth, float lowHeight, float highHeight) {
        float texCoor[] = new float[] {
                lowWidth, lowHeight,
                highWidth, lowHeight,
                highWidth, highHeight,

                highWidth, highHeight,
                lowWidth, highHeight,
                lowWidth, lowHeight
        };
        return texCoor;
    }

    // create 270 degree texture coordinate
    public static float[] createLeftTexCoord(float lowWidth, float highWidth, float lowHeight, float highHeight) {
        float texCoor[] = new float[] {
                highWidth, highHeight,
                lowWidth, highHeight,
                lowWidth, lowHeight,

                lowWidth, lowHeight,
                highWidth, lowHeight,
                highWidth, highHeight
        };
        return texCoor;
    }

    public static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER,   vertexSource);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            MtkLog.e(TAG, "<createProgram> Could not link program:");
            MtkLog.e(TAG, "<createProgram>" + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
            }
        return program;
    }

    public static int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            MtkLog.e(TAG, "<loadShader> Could not compile shader(TYPE=" + shaderType + "):");
            MtkLog.e(TAG, "<loadShader>" + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        return shader;
    }

    public static void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            MtkLog.i(TAG, "<checkGlError>" + op + ":glGetError:0x" + Integer.toHexString(error));
            throw new RuntimeException("glGetError encountered (see log)");
        }
    }

    public static void checkEglError(String op) {
        int error;
        while ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            MtkLog.e(TAG, "<checkEglError>" + op + ":eglGetError:0x" + Integer.toHexString(error));
            throw new RuntimeException("eglGetError encountered (see log)");
        }
    }

    public static int[] generateTextureIds(int num) {
        int[] textures = new int[num];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glGenTextures(num, textures, 0);
        int[] sizes = new int[2];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, sizes, 0);
        MtkLog.i(TAG, "<generateTextureIds> GL_MAX_TEXTURE_SIZE sizes[0] = " + sizes[0] + " size[1] = " + sizes[1]);
        return textures;
    }

    public static void deleteTextures(int[] textureIds) {
        GLES20.glDeleteTextures(textureIds.length, textureIds, 0);
    }

    public static void bindPreviewTexure(int texId) {
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    public static void bindTexture(int texId) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }
}
