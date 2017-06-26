package com.mediatek.galleryfeature.video;

class VideoConfig {
    static final int TRANSCODING_BIT_RATE = 512 * 1024;
    static final int TRANSCODING_FRAME_RATE = 10;
    static final int ENCODE_WIDTH = 320;
    static final int ENCODE_HEIGHT = 240;
    static final int MAX_THUMBNAIL_DURATION = 8 * 1000; // 8 seconds at present
    // too short video
    static final int VIDEO_DURATION_THRESHOLD = 99;
}
