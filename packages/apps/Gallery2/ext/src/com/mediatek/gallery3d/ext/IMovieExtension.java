package com.mediatek.gallery3d.ext;

import java.util.ArrayList;
import android.content.Context;

/**
 * MoviePlayer extension finder class.
 */
public interface IMovieExtension {
   /**
     * this is google default feature,
     * but some operator doesn't like it, need remove it.
     * op01, op02 for example.
     * Enable checking long sleep(>=180s) or not.
     * @return
     */
    boolean shouldEnableCheckLongSleep();
    /**
     * opreator will override this method.
     * all hookers is added by host app, including hookers created by operator.
     * @return hooker extension which will be used by host app.
     * @param context
     */
    ArrayList<IActivityHooker> getHookers(Context context);
    /**
     * opreator will override this method.
     * serverTimeout is operator feature, host app need use this api to open or close it.
     * @return ServerTimeoutExtension which will be used by host app.
     */
    IServerTimeoutExtension getServerTimeoutExtension();
    /**
     * opreator will override this method.
     * RewindAndForward is operator feature, host app need use this api to open or close it.
     * @return RewindAndForwardExtension which will be used by host app.
     */
    IRewindAndForwardExtension getRewindAndForwardExtension();
}
