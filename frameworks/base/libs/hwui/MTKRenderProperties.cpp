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

#include "MTKRenderProperties.h"

#include <utils/Trace.h>

#include <SkCanvas.h>
#include <SkColorFilter.h>
#include <SkMatrix.h>
#include <SkPath.h>
#include <SkPathOps.h>

#include "Matrix.h"
#include "OpenGLRenderer.h"
#include "utils/MathUtils.h"

namespace android {
namespace uirenderer {

#define DROP_CASE(CHECKER, PROPERTY, TYPE)                                  \
        case AP_ID_##PROPERTY:                                              \
        {                                                                   \
           TYPE* other = reinterpret_cast<TYPE*>(otherBuffer[i].getData()); \
           if (CHECKER) {                                                   \
               otherBuffer[i].destroy();                                    \
               drop = true;                                                 \
           }                                                                \
        }                                                                   \
        break;

#define INIT_CASE(PROPERTY, TYPE) case AP_ID_##PROPERTY: dataPtr = new TYPE(); break;
#define DELETE_CASE(PROPERTY, TYPE) case AP_ID_##PROPERTY: delete reinterpret_cast<TYPE*>(getData()); break;
#define DATA_CASE(PROPERTY, TYPE) case AP_ID_##PROPERTY: *((TYPE*)getData()) = *((TYPE*)data); break;
#define PURE_DATA_CASE(SIZE, TYPE) \
        case SIZE: mData = static_cast<uint64_t>(*reinterpret_cast<TYPE*>(const_cast<void*>(data))); break;

LayerProperties::LayerProperties()
        : mType(kLayerTypeNone)
        , mColorFilter(NULL) {
    reset();
}

LayerProperties::~LayerProperties() {
    setType(kLayerTypeNone);
}

void LayerProperties::reset() {
    mOpaque = false;
    setFromPaint(NULL);
}

bool LayerProperties::setColorFilter(SkColorFilter* filter) {
   if (mColorFilter == filter) return false;
   SkRefCnt_SafeAssign(mColorFilter, filter);
   return true;
}

bool LayerProperties::setFromPaint(const SkPaint* paint) {
    bool changed = false;
    SkXfermode::Mode mode;
    int alpha;
    OpenGLRenderer::getAlphaAndModeDirect(paint, &alpha, &mode);
    changed |= setAlpha(static_cast<uint8_t>(alpha));
    changed |= setXferMode(mode);
    changed |= setColorFilter(paint ? paint->getColorFilter() : NULL);
    return changed;
}

LayerProperties& LayerProperties::operator=(const LayerProperties& other) {
    setType(other.type());
    setOpaque(other.opaque());
    setAlpha(other.alpha());
    setXferMode(other.xferMode());
    setColorFilter(other.colorFilter());
    return *this;
}

RenderProperties::PrimitiveFields::PrimitiveFields()
        : mPivotX(0), mPivotY(0)
        , mLeft(0), mTop(0), mRight(0), mBottom(0)
        , mClippingFlags(CLIP_TO_BOUNDS)
        , mPivotExplicitlySet(false)
        , mMatrixOrPivotDirty(false) {
}

RenderProperties::RenderProperties()
        : mBuffer(NULL)
        , mCount(0) {
}

RenderProperties::~RenderProperties() {
    Mutex::Autolock _l(mLock);
    destroyAll();
}

RenderProperties& RenderProperties::operator=(const RenderProperties& other) {
    if (this != &other) {
        copy(const_cast<RenderProperties*>(&other));

        mPrimitiveFields = other.mPrimitiveFields;

        // Force recalculation of the matrix, since other's dirty bit may be clear
        mPrimitiveFields.mMatrixOrPivotDirty = true;
        updateMatrix();
    }
    return *this;
}

void RenderProperties::debugOutputProperties(const int level) const {
    if (mPrimitiveFields.mLeft != 0 || mPrimitiveFields.mTop != 0) {
        ALOGD("%*sTranslate (left, top) %d, %d", level * 2, "", mPrimitiveFields.mLeft, mPrimitiveFields.mTop);
    }
    if (getStaticMatrix()) {
        ALOGD("%*sConcatMatrix (static) %p: " SK_MATRIX_STRING,
                level * 2, "", getStaticMatrix(), SK_MATRIX_ARGS(getStaticMatrix()));
    }
    if (getAnimationMatrix()) {
        ALOGD("%*sConcatMatrix (animation) %p: " SK_MATRIX_STRING,
                level * 2, "", getAnimationMatrix(), SK_MATRIX_ARGS(getAnimationMatrix()));
    }
    if (hasTransformMatrix()) {
        if (isTransformTranslateOnly()) {
            ALOGD("%*sTranslate %.2f, %.2f, %.2f",
                    level * 2, "", getTranslationX(), getTranslationY(), getZ());
        } else {
            ALOGD("%*sConcatMatrix %p: " SK_MATRIX_STRING,
                    level * 2, "", getTransformMatrix(), SK_MATRIX_ARGS(getTransformMatrix()));
        }
    }

    const bool isLayer = layerProperties().type() != kLayerTypeNone;
    int clipFlags = getClippingFlags();
    if (getAlpha() < 1) {
        if (isLayer) {
            clipFlags &= ~CLIP_TO_BOUNDS; // bounds clipping done by layer

            ALOGD("%*sSetOverrideLayerAlpha %.2f", level * 2, "", getAlpha());
        } else if (!getHasOverlappingRendering()) {
            ALOGD("%*sScaleAlpha %.2f", level * 2, "", getAlpha());
        } else {
            Rect layerBounds(0, 0, getWidth(), getHeight());
            int saveFlags = SkCanvas::kHasAlphaLayer_SaveFlag;
            if (clipFlags) {
                saveFlags |= SkCanvas::kClipToLayer_SaveFlag;
                getClippingRectForFlags(clipFlags, &layerBounds);
                clipFlags = 0; // all clipping done by saveLayer
            }

            ALOGD("%*sSaveLayerAlpha %d, %d, %d, %d, %d, 0x%x", level * 2, "",
                    (int)layerBounds.left, (int)layerBounds.top, (int)layerBounds.right, (int)layerBounds.bottom,
                    (int)(getAlpha() * 255), saveFlags);
        }
    }
    if (clipFlags) {
        Rect clipRect;
        getClippingRectForFlags(clipFlags, &clipRect);
        ALOGD("%*sClipRect %d, %d, %d, %d", level * 2, "",
                (int)clipRect.left, (int)clipRect.top, (int)clipRect.right, (int)clipRect.bottom);
    }
}

void RenderProperties::updateMatrix() {
    if (mPrimitiveFields.mMatrixOrPivotDirty) {
        if (!mPrimitiveFields.mPivotExplicitlySet) {
            mPrimitiveFields.mPivotX = getWidth() / 2.0f;
            mPrimitiveFields.mPivotY = getHeight() / 2.0f;
        }
        SkMatrix transform;
        transform.reset();
        if (MathUtils::isZero(getRotationX()) && MathUtils::isZero(getRotationY())) {
            transform.setTranslate(getTranslationX(), getTranslationY());
            transform.preRotate(getRotation(), getPivotX(), getPivotY());
            transform.preScale(getScaleX(), getScaleY(), getPivotX(), getPivotY());
        } else {
            SkMatrix transform3D;
            Sk3DView &camera = mutateTransformCamera();
            camera.save();
            transform.preScale(getScaleX(), getScaleY(), getPivotX(), getPivotY());
            camera.rotateX(getRotationX());
            camera.rotateY(getRotationY());
            camera.rotateZ(-getRotation());
            camera.getMatrix(&transform3D);
            transform3D.preTranslate(-getPivotX(), -getPivotY());
            transform3D.postTranslate(getPivotX() + getTranslationX(),
                    getPivotY() + getTranslationY());
            transform.postConcat(transform3D);
            camera.restore();
        }
        add(AP_ID_TRANSFORM_MATRIX, sizeof(SkMatrix), &transform);
        mPrimitiveFields.mMatrixOrPivotDirty = false;
    }
}

uint8_t RenderProperties::Property::getSize() {
    return static_cast<uint8_t>(mData >> 48);
}

uint8_t RenderProperties::Property::getId() {
    return static_cast<uint8_t>(mData >> 56);
}

void RenderProperties::Property::init(uint8_t id, uint8_t size) {
    void* dataPtr = NULL;
    if (size > 4) {
        switch (id) {
            INIT_CASE(OUTLINE, Outline)
            INIT_CASE(REVEAL_CLIP, RevealClip)
            INIT_CASE(LAYER_PROPERTIES, LayerProperties)
            INIT_CASE(TRANSFORM_CAMERA, Sk3DView)
        default:
            dataPtr = malloc(size);
        }
    }

    uint64_t id64 = id;
    uint64_t size64 = size;
    uint64_t ptr64 = 0xFFFFFFFFFFFF & reinterpret_cast<uint64_t>(dataPtr);

    // void* use max 40bits in 64bit platform
    // | id: 8bits | size: 8bits | data: 48bits |
    mData =  (id64 << 56) | (size64 << 48) | ptr64;
}

void RenderProperties::Property::destroy() {
    if (mData !=0 && !isPureData()) {
        switch (getId()) {
            DELETE_CASE(OUTLINE, Outline)
            DELETE_CASE(REVEAL_CLIP, RevealClip)
            DELETE_CASE(LAYER_PROPERTIES, LayerProperties)
            DELETE_CASE(TRANSFORM_CAMERA, Sk3DView)
        default:
            free(reinterpret_cast<void*>(getData()));
        }
    }
    mData = 0;
}

void RenderProperties::Property::setData(const void* data) {
    if (!data) return;
    uint64_t id64 = getId();
    uint64_t size64 = getSize();

    if (isPureData()) {
        switch(size64) {
            PURE_DATA_CASE(1, uint8_t)
            PURE_DATA_CASE(2, uint16_t)
            PURE_DATA_CASE(4, uint32_t)
        default:
            LOG_ALWAYS_FATAL_IF(true, "[RP] No this data type (%d, %d)!!", getId(), getSize());
        }
        mData = (id64 << 56) | (size64 << 48) | (0xFFFFFFFFFFFF & mData);
   } else {
        switch (id64) {
            DATA_CASE(OUTLINE, Outline)
            DATA_CASE(REVEAL_CLIP, RevealClip)
            DATA_CASE(LAYER_PROPERTIES, LayerProperties)
        case AP_ID_TRANSFORM_CAMERA:
            // Sk3DView is notcopyable, so just set camera location
            ((Sk3DView*)getData())->setCameraLocation(0, 0, ((Sk3DView*)data)->getCameraLocationZ());
            break;
        default:
            memcpy(getData(), data, getSize());
        }
    }
}

void* RenderProperties::Property::getData() {
    return isPureData() ? &mData : reinterpret_cast<void*>(0xFFFFFFFFFFFF & mData);
}

bool RenderProperties::Property::isPureData() {
    return getSize() <= 4;
}

void RenderProperties::Property::output(String8& string) {
    string.appendFormat("[%d, %d, %p, 0x%.8x%.8x]", getId(), getSize(), getData(),
        uint32_t(mData >> 32), uint32_t(mData & 0xffffffff));
}

void* RenderProperties::getAttachProperty(uint8_t propId) const {
    uint8_t index = findNearest(propId);
    return index != mCount ? mBuffer[index].getData() : NULL;
}

uint8_t RenderProperties::findNearest(uint8_t id) const {
    uint8_t i = 0;
    for (i = 0; i < mCount; ++i) {
        if (mBuffer[i].getId() == id)
            return i;
    }
    return i;
}

RenderProperties::Property* RenderProperties::add(uint8_t propId, int size, const void *data) {
    Mutex::Autolock _l(mLock);
    uint8_t index = findNearest(propId);
    uint8_t oldCount = mCount;
    if (index == oldCount) {
        int sizeOfProperty = sizeof(Property);

        // add new one
        // reallocation
        uint8_t newCount = oldCount + 1;
        Property *buf = reinterpret_cast<Property*>(malloc(newCount * sizeOfProperty));

        if (mBuffer) {
            // copy old propeties to new buffer, the new one will put in the last entry,
            // since this is a straight move, we don't need to destroy/init the properties
            memcpy(buf, mBuffer, oldCount * sizeOfProperty);
        }

        setBufferAndCount(buf, newCount);

        // init the new property entry, data will be set latter
        mBuffer[index].init(propId, size);
    }

    // set new data
    mBuffer[index].setData(data);
    RENDER_PROPERTIES_LOGD("[RP] add id %d, size %d at %d/%d [%d, %d, %p] <%p>",
        propId, size, index, mCount, mBuffer[index].getId(), mBuffer[index].getSize(), mBuffer[index].getData(), this);
    return &(mBuffer[index]);
}

bool RenderProperties::remove(uint32_t propId) {
    Mutex::Autolock _l(mLock);
    uint8_t index = findNearest(propId);
    uint8_t oldCount = mCount;
    if(index == oldCount) return false; // not found

    RENDER_PROPERTIES_LOGD("[RP] remove id %d at %d/%d <%p>", propId, index, mCount, this);
    if (oldCount == 1) {
        destroyAll();
    } else {
        int sizeOfProperty = sizeof(Property);

        // shrink one entry
        uint8_t newCount = oldCount - 1;
        Property *buf = reinterpret_cast<Property*>(malloc(newCount * sizeOfProperty));

        // copy old propeties to new buffer but skip the one which will be removed
        // since this is a straight move, we don't need to destroy/init the properties
        if (index == 0) {
            // fast path, skip the first entry
            memcpy(buf, mBuffer + 1, newCount * sizeof(Property));
        } else if (index == newCount) {
            // fast path, skip the last entry
            memcpy(buf, mBuffer, newCount * sizeOfProperty);
        } else {
            memcpy(buf, mBuffer, index * sizeOfProperty);
            memcpy(buf + index, mBuffer + index + 1 , (newCount - index) * sizeOfProperty);
        }

        // destroy the property, not needed anymore
        mBuffer[index].destroy();
        setBufferAndCount(buf, newCount);
    }
    return true;
}

void RenderProperties::destroyAll() {
    for (uint8_t i = 0; i < mCount; ++i) {
        mBuffer[i].destroy();
    }
    setBufferAndCount(NULL, 0);
}

void RenderProperties::setBufferAndCount(Property* newBuffer, uint8_t newCount) {
    if (mBuffer) free(mBuffer);
    mBuffer = newBuffer;
    mCount = newCount;
}

void RenderProperties::copy(RenderProperties* otherProperty) {
    Mutex::Autolock _l(mLock);
    Property* otherBuffer = otherProperty->mBuffer;
    uint8_t otherCount = otherProperty->mCount;

    // guarantees old properties are destroyed properly
    destroyAll();
    if (otherCount > 0) {
        int sizeOfProperty = sizeof(Property);
        Property* buffer = reinterpret_cast<Property*>(malloc(otherCount * sizeOfProperty));
        uint8_t count = 0;
        for (uint8_t i = 0; i < otherCount; ++i) {
            uint8_t id = otherBuffer[i].getId();
            bool drop = false;
            switch (id) {
                DROP_CASE(other->isNone(), OUTLINE, Outline)
                DROP_CASE(other->type() == kLayerTypeNone, LAYER_PROPERTIES, LayerProperties)
                DROP_CASE(other->willClip(), REVEAL_CLIP, RevealClip)
            default:
                ;
            }
            if (!drop) {
                // copy the data from other,
                // because it's not a straight move, property must init and set data
                buffer[count].init(otherBuffer[i].getId(), otherBuffer[i].getSize());
                buffer[count].setData(otherBuffer[i].getData());
                count++;
            }
        }

        if (count == otherCount) {
            setBufferAndCount(buffer, count);
        } else {
            // count changed means there are properties dropped
            // just copy propeties which are not dropped
            Property *buf = reinterpret_cast<Property*>(malloc(count * sizeOfProperty));
            memcpy(buf, buffer, count * sizeOfProperty);
            free(buffer);
            setBufferAndCount(buf, count);

            // the dropped property has destroyed in DROP_CASE,
            // so we just trim the src buffer to the correct size
            otherProperty->trim(count);
        }
    }

    output();
}

void RenderProperties::trim(int count) {
    Mutex::Autolock _l(mLock);
    int sizeOfProperty = sizeof(Property);
    Property* buffer = reinterpret_cast<Property*>(malloc(count * sizeOfProperty));
    int index = 0;
    for (uint8_t i = 0; i < mCount; ++i) {
        if (mBuffer[i].mData != 0) {
            // data not be destroyed, keep it!!
            // since this is a straight move, we don't need to destroy/init the properties
            buffer[index] = mBuffer[i];
            index++;
        }
    }
    LOG_ALWAYS_FATAL_IF(index != count, "[RP] trim size is not matched!! %d != %d", index, count);
    setBufferAndCount(buffer, count);
}

void RenderProperties::output() const {
    if (CC_UNLIKELY(g_HWUI_debug_render_properties)) {
        String8 string;
        for (uint8_t i = 0; i < mCount; ++i) {
            mBuffer[i].output(string);
        }
        ALOGD("[RP] -- %s -- #%d <%p>", string.string(), mCount, this);
    }
}

} /* namespace uirenderer */
} /* namespace android */
