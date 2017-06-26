package com.mediatek.music.ext;
import android.content.Context;

public class PluginUtils {
    /**
     * Base MENU ITEM ID that is used to extend the menu
     */
    public static final int MENU_ITEM_BASE_ID = 0xF0000000;


    /**
     * Base Activity Request Code
     */

    public static final int ACTIVITY_REQUEST_CODE_BASE_ID = 1000;

    /**
     * The name of the activity whose menu will be customized
     */

    public static final String TRACK_BROWSER_ACTIVITY = "TrackBrowserActivity";
    public static final String MUSIC_BROWSER_ACTIVITY = "MusicBrowserActivity";
    public static final String PLAYLIST_BROWSER_ACTIVITY = "PlaylistBrowserActivity";


    /**
     * Tab Index value
     */
    public static final int ARTIST_TAB_INDEX = 0;
    public static final int ALBUM__TAB_INDEX = 1;
    public static final int SONG_TAB_INDEX = 2;
    public static final int PLAYLIST_TAB_INDEX = 3;


    /**
     * keys of Bundle which will be passed as the parameter when the following interface is called
     * by host when the TrackBrowserActivity is used to display the songs of a playlist
     *   onCreateOptionsMenuForPlugin
     *   onPrepareOptionsMenuForPlugin
     *   onOptionsItemSelectedForPlugin
     *   onActivityResultForPlugin
     *
     */

    public static final String PLAYLIST_NAME = "playlistname";
    public static final String PLAYLIST_LEN = "playlistlen";


    /**
     * keys of Bundle which will be passed as the parameter when the following interface is called
     * by host when MusicBrowserActivity is used to manager the artist/album/song/playlist
     *   onCreateOptionsMenuForPlugin
     *   onPrepareOptionsMenuForPlugin
     *   onOptionsItemSelectedForPlugin
     */
    public static final String TAB_INDEX = "tabindex";


    /**
     * pre defined playlist name
     */

    public static final String NOW_PLAYING = "nowplaying";
    public static final String RECENTLY_ADDED = "recentlyadded";
    public static final String PODCASTS = "podcasts";

    public interface IMusicListenter {
        public void onCallPlay(Context context, long [] list, int position);
        public void onCallAddToPlaylist(Context context, long [] ids, long playlistid);
        public void onCallAddToCurrentPlaylist(Context context, long [] list);
        public void onCallClearPlaylist(Context context, int playlistid);
   }
}