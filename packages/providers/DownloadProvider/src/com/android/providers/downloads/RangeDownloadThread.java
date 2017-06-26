package com.android.providers.downloads;

import android.content.ContentValues;
import android.content.Context;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Downloads;
import android.util.Pair;

import static android.provider.Downloads.Impl.STATUS_CANNOT_RESUME;
import static android.provider.Downloads.Impl.STATUS_FILE_ERROR;
import static android.provider.Downloads.Impl.STATUS_HTTP_DATA_ERROR;
import static android.provider.Downloads.Impl.STATUS_RUNNING;
import static android.provider.Downloads.Impl.STATUS_SUCCESS;
import static android.provider.Downloads.Impl.STATUS_TOO_MANY_REDIRECTS;
import static android.provider.Downloads.Impl.STATUS_UNHANDLED_HTTP_CODE;
import static android.text.format.DateUtils.SECOND_IN_MILLIS;

import com.mediatek.xlog.Xlog;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRoute;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PARTIAL;
import static java.net.HttpURLConnection.HTTP_PRECON_FAILED;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

import libcore.io.IoUtils;

/**
 * download thread for sub downloads.
 */
public class RangeDownloadThread implements Runnable {
    private static final int DEFAULT_TIMEOUT = (int) (15 * SECOND_IN_MILLIS);
    private static final int HTTP_REQUESTED_RANGE_NOT_SATISFIABLE = 416;
    private static final int HTTP_TEMP_REDIRECT = 307;

    private static final int EVENT_UPDATE_DB = 1;

    private long mBlockId;
    private long mStartPos;
    private long mEndPos;
    private long mCurrentWriteBytes;
    private long mTotalLength;
    private String mFileName;

    private Handler mHandler;
    private long mLastCurrentWriteBytes;

    private DownloadInfo mInfo;
    private Context mContext;
    private URL mUrl;

    /**
     * Initialize for range download thread.
     * @param context the context.
     * @param url the download url.
     * @param blockId the download block id number.
     * @param startPos the download block start position.
     * @param endPos the download block end position.
     * @param currentWriteLength the download block current download length.
     * @param totalLength the download block content total length.
     * @param fileName the download file name.
     * @param info the download info.
     */
    public RangeDownloadThread(Context context, URL url, long blockId, long startPos, long endPos,
            long currentWriteLength, long totalLength, String fileName, DownloadInfo info) {
        mBlockId = blockId;
        mStartPos = startPos;
        mEndPos = endPos;
        mCurrentWriteBytes = currentWriteLength;
        mLastCurrentWriteBytes = mCurrentWriteBytes;
        mTotalLength = totalLength;
        mFileName = fileName;
        mInfo = info;
        mContext = context;
        mUrl = url;
    }

    @Override
    public void run() {
        Xlog.v(Constants.TAG, "Range download thread run, downloadId: " + mInfo.mId
                + ", blockId: " + mBlockId);

        String errorMsg = null;
        int status = STATUS_RUNNING;

        updateDownloadStatus(status);

        try {
            // Network traffic on this thread should be counted against the
            // requesting UID, and is tagged with well-known value.
            TrafficStats.setThreadStatsTag(TrafficStats.TAG_SYSTEM_DOWNLOAD);
            TrafficStats.setThreadStatsUid(mInfo.mUid);

            executeRangeDownload();
            status = STATUS_SUCCESS;
            TrafficStats.incrementOperationCount(1);
        } catch (StopRequestException error) {
            status = error.getFinalStatus();
            errorMsg = error.getMessage();
            Xlog.e(Constants.TAG, "Range download thread abort, downloadId: " + mInfo.mId
                    + ", blockId: " + mBlockId + ",currentThread id: "
                    + Thread.currentThread().getId() + ",  :" + errorMsg);
        } finally {
            updateDownloadStatus(status);

            TrafficStats.clearThreadStatsTag();
            TrafficStats.clearThreadStatsUid();
        }
        Xlog.v(Constants.TAG, "Range download thread done, downloadId: "
                + mInfo.mId + ", blockId: " + mBlockId);
    }

    private void executeRangeDownload() throws StopRequestException {
        if (mCurrentWriteBytes == mTotalLength) {
            Xlog.v(Constants.TAG, "Range download aleady done, will return, downloadId: "
                    + mInfo.mId + ", blockId: " + mBlockId);
            return;
        }
        int redirectionCount = 0;

        HandlerThread thread = new HandlerThread(Constants.TAG);
        thread.start();
        mHandler = new DbUpdateHandler(thread.getLooper());
        mHandler.obtainMessage(EVENT_UPDATE_DB).sendToTarget();

        while (redirectionCount++ < Constants.MAX_REDIRECTS) {
            // Open connection and follow any redirects until we have a useful
            // response with body.
            DefaultHttpClient httpClient = null;
            try {
                // checkConnectivity();
                httpClient = makeDefaultHttpClient(calcuateBufferSize());
                final HttpGet request = new HttpGet(mUrl.toString());
                addHttpRequestHeader(request);
                HttpResponse response = httpClient.execute(request);
                final int responseCode = response.getStatusLine().getStatusCode();

                switch (responseCode) {
                case HTTP_OK:
                    throw new StopRequestException(STATUS_HTTP_DATA_ERROR,
                            "Expected partial, but received OK, info: " + mInfo.mId + ", blockId: "
                                    + mBlockId);

                case HTTP_PARTIAL:
                    transferData(response);
                    return;

                case HTTP_MOVED_PERM:
                case HTTP_MOVED_TEMP:
                case HTTP_SEE_OTHER:
                case HTTP_TEMP_REDIRECT:
                    Header locationHeader = response.getFirstHeader("location");
                    if (locationHeader == null) {
                        throw new StopRequestException(STATUS_CANNOT_RESUME,
                            "Null location header");
                    }
                    String location = locationHeader.getValue();
                    mUrl = new URL(location);
                    if (responseCode == HTTP_MOVED_PERM) {
                        // Push updated URL back to database
                        ContentValues values = new ContentValues();
                        values.put(Constants.SubDownloads.COLUMN_URI, mUrl.toString());
                        Uri subDownload = Uri.withAppendedPath(mInfo.getMyDownloadsUri(),
                                Constants.SubDownloads.URI_SEGMENT + "/" + mBlockId);
                        mContext.getContentResolver().update(subDownload, values, null, null);
                    }
                    continue;

                case HTTP_PRECON_FAILED:
                    throw new StopRequestException(STATUS_CANNOT_RESUME, "Precondition failed");

                case HTTP_REQUESTED_RANGE_NOT_SATISFIABLE:
                    throw new StopRequestException(STATUS_CANNOT_RESUME,
                            "Requested range not satisfiable");

                case HTTP_UNAVAILABLE:
                    throw new StopRequestException(HTTP_UNAVAILABLE,
                        response.getStatusLine().getReasonPhrase());

                case HTTP_INTERNAL_ERROR:
                    throw new StopRequestException(HTTP_INTERNAL_ERROR,
                        response.getStatusLine().getReasonPhrase());

                case HTTP_UNAUTHORIZED:
                    Xlog.w(Constants.DL_ENHANCE, "DownloadThread: handleExceptionalStatus:"
                            + " 401, need Authenticate ");
                    throw new StopRequestException(Downloads.Impl.STATUS_NEED_HTTP_AUTH,
                            "http error " + HTTP_UNAUTHORIZED);

                default:
                    StopRequestException.throwUnhandledHttpError(responseCode,
                            response.getStatusLine().getReasonPhrase());
                }

            } catch (IOException e) {
                if (e instanceof ProtocolException
                        && e.getMessage().startsWith("Unexpected status line")) {
                    throw new StopRequestException(STATUS_UNHANDLED_HTTP_CODE, e);
                } else {
                    // Trouble with low-level sockets
                    throw new StopRequestException(STATUS_HTTP_DATA_ERROR, e);
                }

            } finally {
                if (httpClient != null) {
                    if (httpClient.getConnectionManager() != null) {
                        httpClient.getConnectionManager().shutdown();
                    }
                    httpClient = null;
                }
                if (mHandler.getLooper() != null) {
                    mHandler.getLooper().quit();
                }
            }
        }

        throw new StopRequestException(STATUS_TOO_MANY_REDIRECTS, "Too many redirects, info: "
                + mInfo.mId + ", blockId: " + mBlockId);
    }

    private void transferData(HttpResponse response) throws StopRequestException {
        RandomAccessFile downloadFile = null;
        InputStream in = null;
        FileDescriptor outFd = null;
        try {
            try {
                downloadFile = new RandomAccessFile(new File(mFileName), "rw");
                outFd = downloadFile.getFD();
                downloadFile.seek(mStartPos + mCurrentWriteBytes);
            } catch (IOException e) {
                Xlog.v(Constants.TAG, "transfer data, seek happen exception: " + e.toString());
                throw new StopRequestException(STATUS_FILE_ERROR, e);
            }

            try {
                in = response.getEntity().getContent();
            } catch (IOException e) {
                throw new StopRequestException(STATUS_HTTP_DATA_ERROR, e);
            }
            transferData(in, downloadFile, outFd);
        } finally {
            IoUtils.closeQuietly(in);
            try {
                downloadFile.close();
                if (outFd != null) {
                    outFd.sync();
                    outFd = null;
                }
            } catch (IOException e) {
                Xlog.v(Constants.TAG, "download file close happen exception: " + e.toString());
            }
        }

    }

    private void transferData(InputStream in, RandomAccessFile downloadFile, FileDescriptor outFd)
            throws StopRequestException {
        byte data[];
        int bytesRead = 0;
        int lengthRead = 0;

        int bufferSize = calcuateBufferSize();
        data = new byte[bufferSize];

        for (;; ) {
            checkPausedOrCanceled();

            try {
                bytesRead = in.read(data, lengthRead, bufferSize - lengthRead);
            } catch (IOException e) {
                throw new StopRequestException(STATUS_HTTP_DATA_ERROR, "mInfo.mId: " + mInfo.mId
                        + ",blockId: " + mBlockId + ",Failed reading response: " + e, e);
            }

            if (bytesRead == -1) {
                break;
            }

            lengthRead += bytesRead;

            if (lengthRead == bufferSize) {
                writeDatatoDestination(downloadFile, data, 0, lengthRead, outFd);
                // insert current bytes to db
                writeToDatabase(lengthRead, mInfo, mBlockId);
                lengthRead = 0;
            }
        }

        if (lengthRead != 0) {
            writeDatatoDestination(downloadFile, data, 0, lengthRead, outFd);
            // insert current bytes to db
            writeToDatabase(lengthRead, mInfo, mBlockId);
        }
    }

    private DefaultHttpClient makeDefaultHttpClient(int bufferSize) {
        HttpParams params = new BasicHttpParams();

        System.setProperty("socket.rx.buffer.size", Integer.toString(bufferSize));

        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setUseExpectContinue(params, false);

        HttpConnectionParams.setStaleCheckingEnabled(params, false);
        HttpConnectionParams.setConnectionTimeout(params, DEFAULT_TIMEOUT);
        HttpConnectionParams.setSoTimeout(params, DEFAULT_TIMEOUT);
        HttpConnectionParams.setSoSndTimeout(params, DEFAULT_TIMEOUT);
        HttpConnectionParams.setSocketBufferSize(params, bufferSize);
        HttpClientParams.setRedirecting(params, false);

        /** The default maximum number of connections allowed per host */
        ConnPerRoute connPerRoute = new ConnPerRoute() {
            public int getMaxForRoute(HttpRoute route) {
                return Constants.MAX_DOWNLOAD_CONNECTION;
            }
        };
        ConnManagerParams.setTimeout(params, 3 * 1000);
        ConnManagerParams.setMaxConnectionsPerRoute(params, connPerRoute);
        ConnManagerParams.setMaxTotalConnections(params, Constants.MAX_DOWNLOAD_CONNECTION);

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http",
                PlainSocketFactory.getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https",
                SSLSocketFactory.getSocketFactory(), 443));

        ClientConnectionManager manager = new ThreadSafeClientConnManager(params, schemeRegistry);
        return new DefaultHttpClient(manager, params);
    }

    private void addHttpRequestHeader(HttpGet request) {
        for (Pair<String, String> header : mInfo.getHeaders()) {
            request.setHeader(header.first, header.second);
        }
        if (request.containsHeader("User-Agent")) {
            request.setHeader("User-Agent", mInfo.getUserAgent());
        }

        request.setHeader("Accept-Encoding", "identity");
        request.setHeader("Range", "bytes=" + (mStartPos + mCurrentWriteBytes) + "-"
                    + mEndPos);
    }

    private void addRequestHeaders(HttpURLConnection conn, DownloadInfo mInfo) {
        for (Pair<String, String> header : mInfo.getHeaders()) {
            conn.addRequestProperty(header.first, header.second);
        }

        if (conn.getRequestProperty("User-Agent") == null) {
            conn.addRequestProperty("User-Agent", mInfo.getUserAgent());
        }

        conn.setRequestProperty("Accept-Encoding", "identity");
        conn.addRequestProperty("Range", "bytes=" + (mStartPos + mCurrentWriteBytes) + "-"
                    + mEndPos);
    }

    private void writeDatatoDestination(RandomAccessFile downloadFile, byte[] data, int off,
            int length, FileDescriptor outFd) throws StopRequestException {
        try {
            downloadFile.write(data, off, length);
            outFd.sync();
        } catch (IOException e) {
            Xlog.v(Constants.TAG, "writeDatatoDestination happen io exception, mInfo.mId: "
                    + mInfo.mId + ",blockId: " + mBlockId);
            throw new StopRequestException(STATUS_FILE_ERROR, e);
        }
    }

    private void updateDownloadStatus(int status) {
        ContentValues values = new ContentValues();
        values.put(Constants.SubDownloads.COLUMN_STATUS, status);
        Uri subDownload = Uri.withAppendedPath(mInfo.getMyDownloadsUri(),
                Constants.SubDownloads.URI_SEGMENT + "/" + mBlockId);
        mContext.getContentResolver().update(subDownload, values, null, null);
    }

    private void writeToDatabase(int length, DownloadInfo info, long blockId) {
        mCurrentWriteBytes += length;
    }

    private void checkPausedOrCanceled() throws StopRequestException {
        synchronized (mInfo) {
            if (mInfo.mControl == Downloads.Impl.CONTROL_PAUSED) {
                throw new StopRequestException(Downloads.Impl.STATUS_PAUSED_BY_APP,
                        "download paused by owner");
            }
            if (mInfo.mStatus == Downloads.Impl.STATUS_CANCELED || mInfo.mDeleted) {
                throw new StopRequestException(Downloads.Impl.STATUS_CANCELED,
                        "download canceled, blockId: " + mBlockId);
            }
        }
    }

    /** Handler to do the network accesses on.*/
    private class DbUpdateHandler extends Handler {

        public DbUpdateHandler(Looper l) {
            super(l);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_UPDATE_DB:
                    onUpdateDbByTime();
                    break;
                default:
                    break;
            }
        }
    }

    private void onUpdateDbByTime() {
        if (mLastCurrentWriteBytes != mCurrentWriteBytes) {
            ContentValues values = new ContentValues();
            values.put(Constants.SubDownloads.COLUMN_CURRENT_WRITE_BYTES, mCurrentWriteBytes);
            Uri subDownload = Uri.withAppendedPath(mInfo.getMyDownloadsUri(),
                    Constants.SubDownloads.URI_SEGMENT + "/" + mBlockId);
            int result = mContext.getContentResolver().update(subDownload, values, null, null);
            mLastCurrentWriteBytes = mCurrentWriteBytes;
        }

        mHandler.sendMessageDelayed(mHandler.obtainMessage(EVENT_UPDATE_DB),
                Constants.MAX_DB_UPDATE_TIMER);
    }

    private int calcuateBufferSize() {
        int defaultBufferSize = (int) ((mTotalLength - mCurrentWriteBytes)
                                / Constants.RANGE_MAX_WRITE_TIMES);
        Xlog.d(Constants.TAG, "Total Len:" + mTotalLength + ":"
                        + mCurrentWriteBytes + ":" + defaultBufferSize);
        if (defaultBufferSize < Constants.RANGE_BUFFER_SIZE) {
            defaultBufferSize = Constants.RANGE_BUFFER_SIZE;
        } else if (defaultBufferSize > Constants.RANGE_MAX_BUFFER_SIZE) {
            defaultBufferSize = Constants.RANGE_MAX_BUFFER_SIZE;
        }
        int bufferSize = SystemProperties.getInt("persist.sys.hetcomm.buffersize",
            defaultBufferSize);

        Xlog.d(Constants.TAG, "Buffer Size:" + bufferSize + ":" + defaultBufferSize);
        return bufferSize;
    }
}
