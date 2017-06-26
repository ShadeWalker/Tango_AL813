package com.mediatek.gallery3d.adapter;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSource;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.PathMatcher;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryframework.util.Utils;

public class ContainerSource extends MediaSource {

    private static final String TAG = "MtkGallery2/ContainerSource";

    private static final int CONTAINER_BY_CONSHOT_ITEM = 0;
    private static final int CONTAINER_BY_MOTRACK_ITEM = 1;
    private static final int CONTAINER_BY_CONSHOT_SET = 2;
    private static final int CONTAINER_BY_MOTRACK_SET = 3;

    public static final String CONTAINER_CONSHOT_ITEM = "/container/conshot/item";
    public static final String CONTAINER_MOTRACK_ITEM = "/container/motrack/item";
    public static final String CONTAINER_CONSHOT_SET = "/container/conshot/set";
    public static final String CONTAINER_MOTRACK_SET = "/container/motrack/set";
    // motion set path - /container/motrack/set/xxx, xxx is id in media db
    // conshot set path - /container/conshot/set/xx1/xx2, xx1 is bucket id, xx2 is group id

    private GalleryApp mApplication;
    private PathMatcher mMatcher;

    public ContainerSource(GalleryApp application) {
        super("container");
        mApplication = application;
        mMatcher = new PathMatcher();
        mMatcher.add(CONTAINER_CONSHOT_ITEM + "/*", CONTAINER_BY_CONSHOT_ITEM);
        mMatcher.add(CONTAINER_MOTRACK_ITEM + "/*", CONTAINER_BY_MOTRACK_ITEM);
        mMatcher.add(CONTAINER_CONSHOT_SET + "/*/*", CONTAINER_BY_CONSHOT_SET);
        mMatcher.add(CONTAINER_MOTRACK_SET + "/*", CONTAINER_BY_MOTRACK_SET);
    }

    @Override
    public MediaObject createMediaObject(Path path) {
        MtkLog.w(TAG, "<createMediaObject> path = " + path);
        switch (mMatcher.match(path)) {
        case CONTAINER_BY_CONSHOT_ITEM:
            return new LocalImage(path, mApplication, Integer.parseInt(mMatcher.getVar(0)), true);
        case CONTAINER_BY_MOTRACK_ITEM:
            String filePath = Utils.decodePath(mMatcher.getVar(0));
            if (filePath == null) {
                MtkLog.w(TAG, "<createMediaObject> CONTAINER_BY_MOTRACK_ITEM, filePath is null");
                throw new RuntimeException("bad path: " + path);
            }
            return new LocalImage(path, mApplication, filePath, true);
        case CONTAINER_BY_CONSHOT_SET:
            return new ContainerSet(path, mApplication, Integer.parseInt(mMatcher.getVar(0)), Long
                    .parseLong(mMatcher.getVar(1)), true);
        case CONTAINER_BY_MOTRACK_SET:
            return new ContainerSet(path, mApplication, Integer.parseInt(mMatcher.getVar(0)), false);
        default:
            throw new RuntimeException("bad path: " + path);
        }
    }
}
