package com.mediatek.galleryfeature.panorama;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;



public class PanoramaMesh {
    private static final String TAG = "MtkGallery2/PanoramaMesh";

    private int mVertexCount;
    private float mRadius;
    private int mFragAngle = 4;
    private int mHeightAngle;
    private FloatBuffer mVertexBuffer;
    private FloatBuffer mNormalBuffer;
    private float[] mTexCoordAry = null;

    //static int numGet = 0;
    //static int numNew = 0;
    static private int MAP_SIZE = 8;
    static private LinkedHashMap<Integer, PanoramaMesh> mMeshMap = new LinkedHashMap<Integer, PanoramaMesh>() {
        private static final long serialVersionUID = 1L;
        protected boolean removeEldestEntry(java.util.Map.Entry<Integer, PanoramaMesh> eldest) {
            return size() > MAP_SIZE;
        }
    };

    public static PanoramaMesh getInstance(int width, int height) {
        //long start = SystemClock.currentThreadTimeMillis();
        synchronized (PanoramaMesh.class) {
            PanoramaMesh mesh = null;
            int scale = width / height;
            if (mMeshMap.containsKey(scale)) {
                mesh = mMeshMap.get(scale);
                mMeshMap.remove(scale);
                mMeshMap.put(scale, mesh);
                //long end = SystemClock.currentThreadTimeMillis();
                //numGet++;
                //MtkLog.i(TAG, "<getInstance> got it, " + (float)numGet/(float)(numNew + numGet) + ", scale = " + scale
                //        + ", mesh map size = " + mMeshMap.size() + ", cost " + (end-start) + " ms");
                return mesh;
            } else {
                mesh = new PanoramaMesh(width, height);
                mMeshMap.put(scale, mesh);
                //long end = SystemClock.currentThreadTimeMillis();
                //numNew++;
                //MtkLog.i(TAG, "<getInstance> new it, " + (float)numNew/(float)(numNew + numGet) + ", scale = " + scale
                //        + ", mesh map size = " + mMeshMap.size() + ", cost " + (end-start) + " ms");
                return mesh;
            }
        }
    }

    private PanoramaMesh(int width, int height) {
        mRadius = PanoramaHelper.MESH_RADIUS;
        mHeightAngle = (int) (360.f * height / width);
        mHeightAngle = (int) (((float) mHeightAngle / mFragAngle / 2.f + 1.f) * mFragAngle * 2.f);
        initMesh();
    }

    public FloatBuffer getVertexBuffer() {
        return mVertexBuffer;
    }

    public FloatBuffer getNormalBuffer() {
        return mNormalBuffer;
    }

    public FloatBuffer getTexCoordsBuffer(float scale) {
        FloatBuffer texCoordsBuffer;
        float[] texCoordAry = new float[mTexCoordAry.length];
        for (int i = 0; i < mTexCoordAry.length / 2; i += 1) {
            texCoordAry[2 * i] = mTexCoordAry[2 * i] / scale;
            texCoordAry[2 * i + 1] = 1 - mTexCoordAry[2 * i + 1];
        }
        ByteBuffer bytes = ByteBuffer.allocateDirect(texCoordAry.length * 4);
        bytes.order(ByteOrder.nativeOrder());
        texCoordsBuffer = bytes.asFloatBuffer();
        texCoordsBuffer.put(texCoordAry);
        texCoordsBuffer.position(0);
        return texCoordsBuffer;
    }

    public int getVertexCount() {
        return mVertexCount;
    }

    public int getTriangleCount() {
        return mVertexCount / 3;
    }

    private void initMesh() {
        ArrayList<Float> alVertix = new ArrayList<Float>();

        for (int rowAngle = -mHeightAngle / 2; rowAngle <= mHeightAngle / 2; rowAngle += mFragAngle) {
            for (int colAngleAngle = 0; colAngleAngle < 360; colAngleAngle += mFragAngle) {
                //double xozLength = mRadius * Math.cos(Math.toRadians(rowAngle));
                //alVertix.add((float) (xozLength * Math.cos(Math.toRadians(colAngleAngle)))); // x
                //alVertix.add((float) (xozLength * Math.sin(Math.toRadians(colAngleAngle)))); // y
                //alVertix.add((float) (mRadius * Math.sin(Math.toRadians(rowAngle)))); // z

                alVertix.add((float) (mRadius * Math.cos(Math.toRadians(colAngleAngle)))); // x
                alVertix.add((float) (mRadius * Math.sin(Math.toRadians(colAngleAngle)))); // y
                alVertix.add((float) (mRadius * Math.tan(Math.toRadians(rowAngle)))); // z
            }
        }
        mVertexCount = alVertix.size() / 3;

        float vertices[] = new float[mVertexCount * 3];
        for (int i = 0; i < alVertix.size(); i++) {
            vertices[i] = alVertix.get(i);
        }
        alVertix.clear();
        ArrayList<Float> alTexture = new ArrayList<Float>();

        int row = (mHeightAngle / mFragAngle) + 1;
        int col = 360 / mFragAngle;

        float splitRow = row - 1;
        float splitCol = col;

        for (int i = 0; i < row; i++) {
            if (i != row - 1) {
                for (int j = 0; j < col; j++) {
                    int k = i * col + j;
                    alVertix.add(vertices[(k + col) * 3]);
                    alVertix.add(vertices[(k + col) * 3 + 1]);
                    alVertix.add(vertices[(k + col) * 3 + 2]);

                    alTexture.add(j / splitCol);
                    alTexture.add((i + 1) / splitRow);

                    int tmp = k + 1;
                    if (j == col - 1) {
                        tmp = (i) * col;
                    }
                    alVertix.add(vertices[(tmp) * 3]);
                    alVertix.add(vertices[(tmp) * 3 + 1]);
                    alVertix.add(vertices[(tmp) * 3 + 2]);

                    alTexture.add((j + 1) / splitCol);
                    alTexture.add(i / splitRow);

                    alVertix.add(vertices[k * 3]);
                    alVertix.add(vertices[k * 3 + 1]);
                    alVertix.add(vertices[k * 3 + 2]);

                    alTexture.add(j / splitCol);
                    alTexture.add(i / splitRow);
                }
            }
            if (i != 0) {
                for (int j = 0; j < col; j++) {
                    int k = i * col + j;
                    alVertix.add(vertices[(k - col) * 3]);
                    alVertix.add(vertices[(k - col) * 3 + 1]);
                    alVertix.add(vertices[(k - col) * 3 + 2]);
                    if (j == 0) {
                        alTexture.add(1.0f);
                    } else {
                        alTexture.add(j / splitCol);
                    }
                    alTexture.add((i - 1) / splitRow);

                    int tmp = k - 1;
                    if (j == 0) {
                        tmp = i * col + col - 1;
                    }
                    alVertix.add(vertices[(tmp) * 3]);
                    alVertix.add(vertices[(tmp) * 3 + 1]);
                    alVertix.add(vertices[(tmp) * 3 + 2]);
                    if (j == 0) {
                        alTexture.add(1 - 1 / splitCol);
                    } else {
                        alTexture.add((j - 1) / splitCol);
                    }
                    alTexture.add(i / splitRow);

                    alVertix.add(vertices[k * 3]);
                    alVertix.add(vertices[k * 3 + 1]);
                    alVertix.add(vertices[k * 3 + 2]);
                    if (j == 0) {
                        alTexture.add(1.0f);
                    } else {
                        alTexture.add(j / splitCol);
                    }
                    alTexture.add(i / splitRow);
                }
            }
        }

        mVertexCount = alVertix.size() / 3;

        float[] vertexAry = new float[mVertexCount * 3];
        float[] normalAry = new float[mVertexCount * 3];
        for (int i = 0; i < alVertix.size(); i++) {
            vertexAry[i] = alVertix.get(i);
            normalAry[i] = -vertexAry[i];
        }

        mTexCoordAry = new float[mVertexCount * 2];
        for (int i = 0; i < alTexture.size(); i++) {
            mTexCoordAry[i] = alTexture.get(i);
        }

        ByteBuffer bytes = ByteBuffer.allocateDirect(vertexAry.length * 4);
        bytes.order(ByteOrder.nativeOrder());
        mVertexBuffer = bytes.asFloatBuffer();
        mVertexBuffer.put(vertexAry);
        mVertexBuffer.position(0);

        ByteBuffer bytes2 = ByteBuffer.allocateDirect(normalAry.length * 4);
        bytes2.order(ByteOrder.nativeOrder());
        mNormalBuffer = bytes2.asFloatBuffer();
        mNormalBuffer.put(normalAry);
        mNormalBuffer.position(0);
    }
}