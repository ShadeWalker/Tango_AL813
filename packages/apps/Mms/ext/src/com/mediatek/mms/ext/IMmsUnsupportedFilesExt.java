package com.mediatek.mms.ext;

import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * M: For OP09 Feature: show info for unsupported file when play mms.
 */
public interface IMmsUnsupportedFilesExt {

    /**
     * M: Get Icon for unsupported image.
     * @return the icon bitmap.
     */
    Bitmap getUnsupportedImageIcon();

    /**
     * M: Get Icon for unsupported audio.
     * @return the icon bitmap.
     */
    Bitmap getUnsupportedAudioIcon();

    /**
     * M: Get Icon for unsupported video.
     * @return the icon bitmap.
     */
    Bitmap getUnsupportedVideoIcon();

    /**
     * M: Judge whether support or not by contentType.
     * @param contentType the media's contentType.
     * @param fileName the file's name.
     * @return true: support; false: not.
     */
    boolean isSupportedFile(String contentType, String fileName);

    /**
     * M: set the unsupported icon for image.
     * @param imageView the imageView which will be set new icon for unsupported image.
     * @param contentType the image media's content-type.
     * @param fileName the file's name.
     */
    void setImageUnsupportedIcon(ImageView imageView, String contentType, String fileName);

    /**
     * M: set the unsupported icon for audio.<br>
     * For OP09 Feature: Unsupported files.
     *
     * @param audioView the audio's LinearLayout.
     */
    void setAudioUnsupportedIcon(LinearLayout audioView);

    /**
     * M: set audio Uri.
     * @param uri audio's Uri.
     */
    void setAudioUri(Uri uri);

    /**
     * M: set the unsupported icon for image.
     * @param videoView the imageView which will be set new icon for unsupported video.
     * @param contentType the video media's content-type.
     * @param fileName the file's name.
     */
    void setVideoUnsupportedIcon(ImageView videoView, String contentType, String fileName);

    /**
     * M: set the unsupported icon for image.
     * @param audioView the imageView which will be set new icon for unsupported audio.
     * @param contentType the audio media's content-type.
     * @param fileName the file's name.
     */
    void setAudioUnsupportedIcon(ImageView audioView, String contentType, String fileName);

    /**
     * M: init text view for show the unsupported info for image.
     * @param viewGroup the textView's parent's ViewGroup.
     */
    void initUnsupportedViewForImage(ViewGroup viewGroup);

    /**
     * M: init text view for show the unsupported info for audio.
     * @param viewGroup the textView's parent's ViewGroup.
     */
    void initUnsupportedViewForAudio(ViewGroup viewGroup);

    /**
     * M: set unsupported image view visible.
     * @param show true: show, false: not show.
     */
    void setUnsupportedViewVisibilityForImage(boolean show);

    /**
     * M: set unsupported audio view visible.
     * @param show true: show, false: not show.
     */
    void setUnsupportedViewVisibilityForAudio(boolean show);

    /**
     * M: set unsupported msg textView.
     * @param linearLayout  the root linearLayout.
     * @param srcView the imageView/audioView/VedioView.
     * @param show true: show View; false: remove the view.
     */
    void setUnsupportedMsg(LinearLayout linearLayout, View srcView, boolean show);

}