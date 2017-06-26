/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2014. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.galleryfeature.SlideVideo;

import com.mediatek.gallery3d.ext.IActivityHooker;
import com.mediatek.galleryframework.base.MediaData;
/**
 * Interface used to interact with MovieControllerOverlay
 */
public interface IVideoController {
    /**
     * When the visibility of movie controller has changed,the gallery action
     * bar should be changed when necessary.
     * 
     */
    interface ControllerHideListener {
        /**
         * Notify gallery action bar movie controllser's visibility has
         * changed.When movie controller is shown,the gallery action bar should
         * be shown,when movie controller hide,the gallery action bar should be
         * notified to be hidden.
         * 
         * @param visibility
         *            True if movie controller is shown,false otherwise.
         */
        void onControllerVisibilityChanged(boolean visibility);
    }
    
    void setControllerHideListener(ControllerHideListener listener);
    
    /**
     * When user click the screen ,video controller should be shown when gallery
     * action bar is shown.
     */
    void showController();
    
    /**
     * When gallery action bar is going to hide,video controller should hide as
     * well.The opposite behavior to {@link #showController}
     */
    void hideController();
    void setData(MediaData data);
    
    /**
     * Hide audio only picture when necessary,when slide video enabled and in
     * video is played in file mode ,audio only picture should not be shown.
     * 
     * @param isFilmMode
     *            True if in file mode,false otherwise.
     */
    void hideAudioOnlyIcon(boolean isFilmMode);
    
    /**
     * Get RewindAndForward hooker from operator.
     * 
     * @return The RewindAndForward hooker.
     */
    IActivityHooker getRewindAndForwardHooker();
    
}
