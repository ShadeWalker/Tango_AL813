/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

#ifndef MTK_RENDERNODEPROPERTIES_H
#define MTK_RENDERNODEPROPERTIES_H

#include <algorithm>
#include <stddef.h>
#include <vector>
#include <cutils/compiler.h>
#include <androidfw/ResourceTypes.h>
#include <utils/Log.h>

#include <SkCamera.h>
#include <SkMatrix.h>
#include <SkRegion.h>

#include "Animator.h"
#include "Rect.h"
#include "RevealClip.h"
#include "Outline.h"
#include "utils/MathUtils.h"

class SkBitmap;
class SkColorFilter;
class SkPaint;

namespace android {
namespace uirenderer {

class Matrix4;
class RenderNode;
class RenderProperties;

#ifdef MTK_DEBUG_RENDERER
    #define RENDER_PROPERTIES_LOGD(...) \
        if (CC_UNLIKELY(g_HWUI_debug_render_properties)) ALOGD(__VA_ARGS__)
#else
    #define RENDER_PROPERTIES_LOGD(...)
#endif

// The __VA_ARGS__ will be executed if a & b are not equal
#define RP_SET(a, b, ...) (a != b ? (a = b, ##__VA_ARGS__, true) : false)
#define RP_SET_AND_DIRTY(a, b) RP_SET(a, b, mPrimitiveFields.mMatrixOrPivotDirty = true)

// Keep in sync with View.java:LAYER_TYPE_*
enum LayerType {
    kLayerTypeNone = 0,
    // Although we cannot build the software layer directly (must be done at
    // record time), this information is used when applying alpha.
    kLayerTypeSoftware = 1,
    kLayerTypeRenderLayer = 2,
    // TODO: LayerTypeSurfaceTexture? Maybe?
};

enum ClippingFlags {
    CLIP_TO_BOUNDS =      0x1 << 0,
    CLIP_TO_CLIP_BOUNDS = 0x1 << 1,
};

class ANDROID_API LayerProperties {
public:
    LayerProperties();
    ~LayerProperties();

    bool setType(LayerType type) {
        if (RP_SET(mType, type)) {
            reset();
            return true;
        }
        return false;
    }

    LayerType type() const {
        return mType;
    }

    bool setOpaque(bool opaque) {
        return RP_SET(mOpaque, opaque);
    }

    bool opaque() const {
        return mOpaque;
    }

    bool setAlpha(uint8_t alpha) {
        return RP_SET(mAlpha, alpha);
    }

    uint8_t alpha() const {
        return mAlpha;
    }

    bool setXferMode(SkXfermode::Mode mode) {
        return RP_SET(mMode, mode);
    }

    SkXfermode::Mode xferMode() const {
        return mMode;
    }

    bool setColorFilter(SkColorFilter* filter);

    SkColorFilter* colorFilter() const {
        return mColorFilter;
    }

    // Sets alpha, xfermode, and colorfilter from an SkPaint
    // paint may be NULL, in which case defaults will be set
    bool setFromPaint(const SkPaint* paint);

    bool needsBlending() const {
        return !opaque() || alpha() < 255;
    }

    LayerProperties& operator=(const LayerProperties& other);

private:
    void reset();

    friend class RenderProperties;

    LayerType mType;
    // Whether or not that Layer's content is opaque, doesn't include alpha
    bool mOpaque;
    uint8_t mAlpha;
    SkXfermode::Mode mMode;
    SkColorFilter* mColorFilter;
};

static const Outline g_ap_outline;
static const RevealClip g_ap_reveal_clip;
static const Rect g_ap_clip_bounds;
static const LayerProperties g_ap_layer_properties;
static const Sk3DView g_ap_transform_camera;

#define AP_ID_CLIPPING_FLAGS       0x01
#define AP_ID_PROJECT_BACKWARDS    0x02
#define AP_ID_PROJECT_RECEIVER     0x03
#define AP_ID_ALPHA                0x04
#define AP_ID_OVERLAPPING          0x05
#define AP_ID_ELEVATION            0x06
#define AP_ID_TRANSLATION_X        0x07
#define AP_ID_TRANSLATION_Y        0x08
#define AP_ID_TRANSLATION_Z        0x09
#define AP_ID_ROTATION             0x0A
#define AP_ID_ROTATION_X           0x0B
#define AP_ID_ROTATION_Y           0x0C
#define AP_ID_SCALE_X              0x0D
#define AP_ID_SCALE_Y              0x0E
#define AP_ID_OUTLINE              0x0F
#define AP_ID_REVEAL_CLIP          0x10
#define AP_ID_CLIP_BOUNDS          0x11
#define AP_ID_LAYER_PROPERTIES     0x12
#define AP_ID_STATIC_MATRIX        0x13
#define AP_ID_ANIMATION_MATRIX     0x14
#define AP_ID_TRANSFORM_MATRIX     0x15
#define AP_ID_TRANSFORM_CAMERA     0x16

#define AP_CLIPPING_FLAGS       CLIP_TO_BOUNDS
#define AP_PROJECT_BACKWARDS    false
#define AP_PROJECT_RECEIVER     false
#define AP_ALPHA                1.0f
#define AP_OVERLAPPING          true
#define AP_ELEVATION            0.0f
#define AP_TRANSLATION_X        0.0f
#define AP_TRANSLATION_Y        0.0f
#define AP_TRANSLATION_Z        0.0f
#define AP_ROTATION             0.0f
#define AP_ROTATION_X           0.0f
#define AP_ROTATION_Y           0.0f
#define AP_SCALE_X              1.0f
#define AP_SCALE_Y              1.0f

#define AP_OUTLINE              g_ap_outline
#define AP_REVEAL_CLIP          g_ap_reveal_clip
#define AP_CLIP_BOUNDS          g_ap_clip_bounds
#define AP_LAYER_PROPERTIES     g_ap_layer_properties
#define AP_STATIC_MATRIX        NULL
#define AP_ANIMATION_MATRIX     NULL
#define AP_TRANSFORM_MATRIX     NULL
#define AP_TRANSFORM_CAMERA     g_ap_transform_camera

#define AP_SET(GETTER, VALUE, PROPERTY, TYPE, ...)                                              \
        (GETTER() != VALUE ? (VALUE == AP_##PROPERTY ? remove(AP_ID_##PROPERTY) :               \
            add(AP_ID_##PROPERTY, sizeof(TYPE), &VALUE) != NULL, ##__VA_ARGS__, true) : false)

#define AP_SET_AND_DIRTY(GETTER, VALUE, PROPERTY, TYPE) \
        AP_SET(GETTER, VALUE, PROPERTY, TYPE, mPrimitiveFields.mMatrixOrPivotDirty = true)

#define AP_GET(PROPERTY, TYPE) {                                                     \
            TYPE* v = reinterpret_cast<TYPE*>(getAttachProperty(AP_ID_##PROPERTY));  \
            return v ? *v : AP_##PROPERTY;                                           \
        }

#define AP_MUTATE(GETTER, PROPERTY, TYPE) {                                                       \
            if (!getAttachProperty(AP_ID_##PROPERTY)) add(AP_ID_##PROPERTY, sizeof(TYPE), NULL);  \
            return const_cast<TYPE&>(GETTER());                                                   \
        }

/*
 * Data structure that holds the properties for a RenderNode
 */
class ANDROID_API RenderProperties {
public:
    RenderProperties();
    virtual ~RenderProperties();

    static bool setFlag(uint8_t flag, bool newValue, uint8_t* outFlags) {
        if (newValue) {
            if (!(flag & *outFlags)) {
                *outFlags |= flag;
                return true;
            }
            return false;
        } else {
            if (flag & *outFlags) {
                *outFlags &= ~flag;
                return true;
            }
            return false;
        }
    }

    RenderProperties& operator=(const RenderProperties& other);

    bool setClipToBounds(bool clipToBounds) {
        return setFlag(CLIP_TO_BOUNDS, clipToBounds, &mPrimitiveFields.mClippingFlags);
    }

    bool setClipBounds(const Rect& clipBounds) {
        bool ret = setFlag(CLIP_TO_CLIP_BOUNDS, true, &mPrimitiveFields.mClippingFlags);
        return AP_SET(getClipBounds, clipBounds, CLIP_BOUNDS, Rect) || ret;
    }

    bool setClipBoundsEmpty() {
        remove(AP_ID_CLIP_BOUNDS);
        return setFlag(CLIP_TO_CLIP_BOUNDS, false, &mPrimitiveFields.mClippingFlags);
    }

    bool setProjectBackwards(bool shouldProject) {
        return AP_SET(getProjectBackwards, shouldProject, PROJECT_BACKWARDS, bool);
    }

    bool setProjectionReceiver(bool shouldRecieve) {
        return AP_SET(isProjectionReceiver, shouldRecieve, PROJECT_RECEIVER, bool);
    }

    bool isProjectionReceiver() const {
        AP_GET(PROJECT_RECEIVER, bool);
    }

    bool setStaticMatrix(const SkMatrix* matrix) {
        if (matrix == AP_STATIC_MATRIX) {
            remove(AP_ID_STATIC_MATRIX);
        } else {
            add(AP_ID_STATIC_MATRIX, sizeof(SkMatrix), matrix);
        }
        return true;
    }

    // Can return NULL
    const SkMatrix* getStaticMatrix() const {
        return reinterpret_cast<SkMatrix*>(getAttachProperty(AP_ID_STATIC_MATRIX));
    }

    bool setAnimationMatrix(const SkMatrix* matrix) {
        if (matrix == AP_ANIMATION_MATRIX) {
            remove(AP_ID_ANIMATION_MATRIX);
        } else {
            add(AP_ID_ANIMATION_MATRIX, sizeof(SkMatrix), matrix);
        }
        return true;
    }

    bool setAlpha(float alpha) {
        alpha = MathUtils::clampAlpha(alpha);
        return AP_SET(getAlpha, alpha, ALPHA, float);
    }

    float getAlpha() const {
        AP_GET(ALPHA, float);
    }

    bool setHasOverlappingRendering(bool hasOverlappingRendering) {
        return AP_SET(getHasOverlappingRendering, hasOverlappingRendering, OVERLAPPING, bool);
    }

    bool hasOverlappingRendering() const {
        return getHasOverlappingRendering();
    }

    bool setElevation(float elevation) {
        return AP_SET(getElevation, elevation, ELEVATION, float);
        // Don't dirty matrix/pivot, since they don't respect Z
    }

    float getElevation() const {
        AP_GET(ELEVATION, float);
    }

    bool setTranslationX(float translationX) {
        return AP_SET_AND_DIRTY(getTranslationX, translationX, TRANSLATION_X, float);
    }

    float getTranslationX() const {
        AP_GET(TRANSLATION_X, float);
    }

    bool setTranslationY(float translationY) {
        return AP_SET_AND_DIRTY(getTranslationY, translationY, TRANSLATION_Y, float);
    }

    float getTranslationY() const {
        AP_GET(TRANSLATION_Y, float);
    }

    bool setTranslationZ(float translationZ) {
        return AP_SET(getTranslationZ, translationZ, TRANSLATION_Z, float);
        // mMatrixOrPivotDirty not set, since matrix doesn't respect Z
    }

    float getTranslationZ() const {
        AP_GET(TRANSLATION_Z, float);
    }

    // Animation helper
    bool setX(float value) {
        return setTranslationX(value - getLeft());
    }

    // Animation helper
    float getX() const {
        return getLeft() + getTranslationX();
    }

    // Animation helper
    bool setY(float value) {
        return setTranslationY(value - getTop());
    }

    // Animation helper
    float getY() const {
        return getTop() + getTranslationY();
    }

    // Animation helper
    bool setZ(float value) {
        return setTranslationZ(value - getElevation());
    }

    float getZ() const {
        return getElevation() + getTranslationZ();
    }

    bool setRotation(float rotation) {
        return AP_SET_AND_DIRTY(getRotation, rotation, ROTATION, float);
    }

    float getRotation() const {
        AP_GET(ROTATION, float);
    }

    bool setRotationX(float rotationX) {
        return AP_SET_AND_DIRTY(getRotationX, rotationX, ROTATION_X, float);
    }

    float getRotationX() const {
        AP_GET(ROTATION_X, float);
    }

    bool setRotationY(float rotationY) {
        return AP_SET_AND_DIRTY(getRotationY, rotationY, ROTATION_Y, float);
    }

    float getRotationY() const {
        AP_GET(ROTATION_Y, float);
    }

    bool setScaleX(float scaleX) {
        return AP_SET_AND_DIRTY(getScaleX, scaleX, SCALE_X, float);
    }

    float getScaleX() const {
        AP_GET(SCALE_X, float);
    }

    bool setScaleY(float scaleY) {
        return AP_SET_AND_DIRTY(getScaleY, scaleY, SCALE_Y, float);
    }

    float getScaleY() const {
        AP_GET(SCALE_Y, float);
    }

    bool setPivotX(float pivotX) {
        if (RP_SET(mPrimitiveFields.mPivotX, pivotX)
                || !mPrimitiveFields.mPivotExplicitlySet) {
            mPrimitiveFields.mMatrixOrPivotDirty = true;
            mPrimitiveFields.mPivotExplicitlySet = true;
            return true;
        }
        return false;
    }

    /* Note that getPivotX and getPivotY are adjusted by updateMatrix(),
     * so the value returned may be stale if the RenderProperties has been
     * modified since the last call to updateMatrix()
     */
    float getPivotX() const {
        return mPrimitiveFields.mPivotX;
    }

    bool setPivotY(float pivotY) {
        if (RP_SET(mPrimitiveFields.mPivotY, pivotY)
                || !mPrimitiveFields.mPivotExplicitlySet) {
            mPrimitiveFields.mMatrixOrPivotDirty = true;
            mPrimitiveFields.mPivotExplicitlySet = true;
            return true;
        }
        return false;
    }

    float getPivotY() const {
        return mPrimitiveFields.mPivotY;
    }

    bool isPivotExplicitlySet() const {
        return mPrimitiveFields.mPivotExplicitlySet;
    }

    const Sk3DView& getTransformCamera() const{
        AP_GET(TRANSFORM_CAMERA, Sk3DView);
    }

    Sk3DView& mutateTransformCamera() {
        AP_MUTATE(getTransformCamera, TRANSFORM_CAMERA, Sk3DView);
    }

    bool setCameraDistance(float distance) {
        bool ret = false;
        if (distance == const_cast<Sk3DView*>(&AP_TRANSFORM_CAMERA)->getCameraLocationZ()) {
            ret = remove(AP_ID_TRANSFORM_CAMERA);
            mPrimitiveFields.mMatrixOrPivotDirty |= ret;
        } else if (distance != getCameraDistance()) {
            mPrimitiveFields.mMatrixOrPivotDirty = true;
            mutateTransformCamera().setCameraLocation(0, 0, distance);
            ret = true;
        }
        return ret;
    }

    float getCameraDistance() const {
        // TODO: update getCameraLocationZ() to be const
        return const_cast<Sk3DView*>(&getTransformCamera())->getCameraLocationZ();
    }

    bool setLeft(int left) {
        if (RP_SET(mPrimitiveFields.mLeft, left)) {
            if (!mPrimitiveFields.mPivotExplicitlySet) {
                mPrimitiveFields.mMatrixOrPivotDirty = true;
            }
            return true;
        }
        return false;
    }

    float getLeft() const {
        return mPrimitiveFields.mLeft;
    }

    bool setTop(int top) {
        if (RP_SET(mPrimitiveFields.mTop, top)) {
            if (!mPrimitiveFields.mPivotExplicitlySet) {
                mPrimitiveFields.mMatrixOrPivotDirty = true;
            }
            return true;
        }
        return false;
    }

    float getTop() const {
        return mPrimitiveFields.mTop;
    }

    bool setRight(int right) {
        if (RP_SET(mPrimitiveFields.mRight, right)) {
            if (!mPrimitiveFields.mPivotExplicitlySet) {
                mPrimitiveFields.mMatrixOrPivotDirty = true;
            }
            return true;
        }
        return false;
    }

    float getRight() const {
        return mPrimitiveFields.mRight;
    }

    bool setBottom(int bottom) {
        if (RP_SET(mPrimitiveFields.mBottom, bottom)) {
            if (!mPrimitiveFields.mPivotExplicitlySet) {
                mPrimitiveFields.mMatrixOrPivotDirty = true;
            }
            return true;
        }
        return false;
    }

    float getBottom() const {
        return mPrimitiveFields.mBottom;
    }

    bool setLeftTop(int left, int top) {
        bool leftResult = setLeft(left);
        bool topResult = setTop(top);
        return leftResult || topResult;
    }

    bool setLeftTopRightBottom(int left, int top, int right, int bottom) {
        if (left != mPrimitiveFields.mLeft || top != mPrimitiveFields.mTop
                || right != mPrimitiveFields.mRight || bottom != mPrimitiveFields.mBottom) {
            mPrimitiveFields.mLeft = left;
            mPrimitiveFields.mTop = top;
            mPrimitiveFields.mRight = right;
            mPrimitiveFields.mBottom = bottom;
            if (!mPrimitiveFields.mPivotExplicitlySet) {
                mPrimitiveFields.mMatrixOrPivotDirty = true;
            }
            return true;
        }
        return false;
    }

    bool offsetLeftRight(int offset) {
        if (offset != 0) {
            mPrimitiveFields.mLeft += offset;
            mPrimitiveFields.mRight += offset;
            return true;
        }
        return false;
    }

    bool offsetTopBottom(int offset) {
        if (offset != 0) {
            mPrimitiveFields.mTop += offset;
            mPrimitiveFields.mBottom += offset;
            return true;
        }
        return false;
    }

    int getWidth() const {
        return getRight() - getLeft();
    }

    int getHeight() const {
        return getBottom() - getTop();
    }

    const SkMatrix* getAnimationMatrix() const {
        return reinterpret_cast<SkMatrix*>(getAttachProperty(AP_ID_ANIMATION_MATRIX));
    }

    bool hasTransformMatrix() const {
        return getTransformMatrix() && !getTransformMatrix()->isIdentity();
    }

    // May only call this if hasTransformMatrix() is true
    bool isTransformTranslateOnly() const {
        return getTransformMatrix()->getType() == SkMatrix::kTranslate_Mask;
    }

    const SkMatrix* getTransformMatrix() const {
        LOG_ALWAYS_FATAL_IF(mPrimitiveFields.mMatrixOrPivotDirty, "Cannot get a dirty matrix!");
        return reinterpret_cast<SkMatrix*>(getAttachProperty(AP_ID_TRANSFORM_MATRIX));
    }

    int getClippingFlags() const {
        return mPrimitiveFields.mClippingFlags;
    }

    const Rect& getClipBounds() const {
        AP_GET(CLIP_BOUNDS, Rect);
    }

    bool getClipToBounds() const {
        return mPrimitiveFields.mClippingFlags & CLIP_TO_BOUNDS;
    }

    void getClippingRectForFlags(uint32_t flags, Rect* outRect) const {
        if (flags & CLIP_TO_BOUNDS) {
            outRect->set(0, 0, getWidth(), getHeight());
            if (flags & CLIP_TO_CLIP_BOUNDS) {
                outRect->intersect(getClipBounds());
            }
        } else {
            outRect->set(getClipBounds());
        }
    }

    bool getHasOverlappingRendering() const {
        AP_GET(OVERLAPPING, bool);
    }

    const Outline& getOutline() const {
        AP_GET(OUTLINE, Outline);
    }

    const RevealClip& getRevealClip() const {
        AP_GET(REVEAL_CLIP, RevealClip);
    }

    bool getProjectBackwards() const {
        AP_GET(PROJECT_BACKWARDS, bool);
    }

    void debugOutputProperties(const int level) const;

    void updateMatrix();

    Outline& mutableOutline() {
        AP_MUTATE(getOutline, OUTLINE, Outline);
    }

    RevealClip& mutableRevealClip() {
        AP_MUTATE(getRevealClip, REVEAL_CLIP, RevealClip);
    }

    const LayerProperties& layerProperties() const {
        AP_GET(LAYER_PROPERTIES, LayerProperties);
    }

    LayerProperties& mutateLayerProperties() {
        AP_MUTATE(layerProperties, LAYER_PROPERTIES, LayerProperties);
    }

    // Returns true if damage calculations should be clipped to bounds
    // TODO: Figure out something better for getZ(), as children should still be
    // clipped to this RP's bounds. But as we will damage -INT_MAX to INT_MAX
    // for this RP's getZ() anyway, this can be optimized when we have a
    // Z damage estimate instead of INT_MAX
    bool getClipDamageToBounds() const {
        return getClipToBounds() && (getZ() <= 0 || getOutline().isEmpty());
    }

    bool hasShadow() const {
        return getZ() > 0.0f
                && getOutline().getPath() != NULL
                && getOutline().getAlpha() != 0.0f;
    }

    int getDebugSize() {
        int total = 0;
        int sizeofProperty = sizeof(Property);
        for (uint8_t i = 0; i < mCount; ++i) {
            total += mBuffer[i].getSize() <= 4 ? sizeofProperty : sizeofProperty + mBuffer[i].getSize();
        }
        return total;
    }

private:
    // Rendering properties
    struct PrimitiveFields {
        PrimitiveFields();
        float mPivotX, mPivotY;
        int mLeft, mTop, mRight, mBottom;
        uint8_t mClippingFlags;
        bool mPivotExplicitlySet;
        bool mMatrixOrPivotDirty;
    } mPrimitiveFields;

    struct Property {
        uint64_t mData;

        uint8_t getSize();
        uint8_t getId();

        void init(uint8_t id, uint8_t size);
        void destroy();
        void setData(const void* data);
        void* getData();

        // return true if real data are saved
        // this api is only avaliable after Propety initialized
        bool isPureData();
        void output(String8& string);
    };

    void* getAttachProperty(uint8_t propId) const;
    uint8_t findNearest(uint8_t id) const;
    Property* add(uint8_t propId, int size, const void *data);
    bool remove(uint32_t propId);
    void destroyAll();

    // must sync buffer size and count, do not set buffer and count separately
    // old buffer will free when new one coming
    void setBufferAndCount(Property* newBuffer, uint8_t newCount);
    void copy(RenderProperties* otherProperty);
    void trim(int count);
    void output() const;

    Property* mBuffer;
    uint8_t mCount;

    // Render property might get/update by not only main thread, render thread,
    // but also GC thread, MTBF thread..., add lock to protect for robustness
    mutable Mutex mLock;
};

} /* namespace uirenderer */
} /* namespace android */

#endif /* MTK_RENDERNODEPROPERTIES_H */
