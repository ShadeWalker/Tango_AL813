package com.android.providers.downloads;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Downloads;
import android.system.ErrnoException;
import android.system.Os;

import static android.provider.Downloads.Impl.STATUS_BAD_REQUEST;
import static android.provider.Downloads.Impl.STATUS_FILE_ERROR;
import static android.provider.Downloads.Impl.STATUS_RUNNING;

import com.mediatek.xlog.Xlog;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * download handler for sub downloads.
 */
public class RangeDownloadHandler {

    private static final RangeDownloadHandler sRangeDownloadHandler = new RangeDownloadHandler();
    private final ExecutorService mExecutor = buildDownloadExecutor();

    private static ExecutorService buildDownloadExecutor() {
        final int maxConcurrent = Constants.MAX_DOWNLOAD_CONNECTION;

        final ThreadPoolExecutor executor = new ThreadPoolExecutor(maxConcurrent, maxConcurrent,
                10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public static RangeDownloadHandler getInstance() {
        return sRangeDownloadHandler;
    }

    /**
     * Process range download and put blocks to thread pool.
     * @param context the context.
     * @param url the download url.
     * @param contentLength the download file content length.
     * @param fileName the download file name.
     * @param info the download info.
     *
     * @throws StopRequestException the download thread exception.
     */
    public synchronized void processRangeDownload(Context context, URL url, long contentLength,
            String fileName, DownloadInfo info) throws StopRequestException {

        if (info.mAvgBlockLength == 0) {
            try {
                RandomAccessFile downloadFile = new RandomAccessFile(new File(fileName), "rw");
                // Pre-flight disk space requirements, when known contentLength
                FileDescriptor outFd = downloadFile.getFD();

                final long curSize = Os.fstat(outFd).st_size;
                final long newBytes = contentLength - curSize;

                StorageUtils.ensureAvailableSpace(context, outFd, newBytes);

                downloadFile.setLength(contentLength);
                downloadFile.close();
            } catch (ErrnoException e) {
                Xlog.v(Constants.TAG,
                        "RangeDownloadHandler:downloadFile ErrnoException: " + e.getMessage());
                throw new StopRequestException(STATUS_FILE_ERROR, e);
            } catch (IOException e) {
                Xlog.v(Constants.TAG,
                        "RangeDownloadHandler:downloadFile IOException: " + e.getMessage());
                throw new StopRequestException(STATUS_FILE_ERROR, e);
            }

            long blockLength = (contentLength % Constants.DEFAULT_BLOCKS) == 0 ? contentLength
                    / Constants.DEFAULT_BLOCKS : contentLength / Constants.DEFAULT_BLOCKS + 1;
            Xlog.v(Constants.TAG, "processRangeDownload cut file, avgBlockLength = " + blockLength);

            saveRangedownloadInfo(context, blockLength, contentLength, info);

            info.mAvgBlockLength = blockLength;
            ContentValues values = new ContentValues();
            values.put(Constants.DOWNLOADS_COLUMN_AVG_BLOCK_LENGTH, blockLength);
            context.getContentResolver().update(info.getAllDownloadsUri(), values, null, null);
        }

        // query old values
        Uri subDownloadsUri = Uri.withAppendedPath(info.getMyDownloadsUri(),
                Constants.SubDownloads.URI_SEGMENT);
        Cursor cursor = context.getContentResolver().query(subDownloadsUri, null, null, null, null);
        try {
            int downloadIdIndex = cursor
                    .getColumnIndexOrThrow(Constants.SubDownloads.COLUMN_DOWNLOAD_ID);
            int blockIdIndex = cursor
                    .getColumnIndexOrThrow(Constants.SubDownloads.COLUMN_SUB_BLOCK_ID);
            int uriIndex = cursor.getColumnIndexOrThrow(Constants.SubDownloads.COLUMN_URI);
            int startPosIndex = cursor
                    .getColumnIndexOrThrow(Constants.SubDownloads.COLUMN_START_POS);
            int endPosIndex = cursor.getColumnIndexOrThrow(Constants.SubDownloads.COLUMN_END_POS);
            int currentWriteIndex = cursor
                    .getColumnIndexOrThrow(Constants.SubDownloads.COLUMN_CURRENT_WRITE_BYTES);
            int totalLengthIndex = cursor
                    .getColumnIndexOrThrow(Constants.SubDownloads.COLUMN_TOTAL_LENGTH);
            int statusIndex = cursor.getColumnIndexOrThrow(Constants.SubDownloads.COLUMN_STATUS);

            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                long downloadId = cursor.getLong(downloadIdIndex);
                long blockId = cursor.getLong(blockIdIndex);
                String urlString = cursor.getString(uriIndex);
                long startPos = cursor.getLong(startPosIndex);
                long endPos = cursor.getLong(endPosIndex);
                long currentWriteBytes = cursor.getLong(currentWriteIndex);
                long totalLength = cursor.getLong(totalLengthIndex);
                int status = cursor.getInt(statusIndex);

                try {
                    url = new URL(urlString);
                } catch (MalformedURLException e) {
                    Xlog.v(Constants.TAG, "ProcessRangeDownload:parse urlString: " + urlString
                            + " happen error");
                    throw new StopRequestException(STATUS_BAD_REQUEST, e);
                }

                Xlog.v(Constants.TAG, "Query subDownload, downloadId: " + downloadId
                        + ",blockId: " + blockId + ",uri: " + urlString + ", startPos: " + startPos
                        + ", endPos: " + endPos + ", currentWriteBytes: " + currentWriteBytes
                        + ", totalLength: " + totalLength + ", status: " + status);

                if (!Downloads.Impl.isStatusSuccess(status)) {
                    addToThreadPool(context, url, blockId, startPos, endPos, currentWriteBytes,
                            totalLength, fileName, info);
                    Xlog.v(Constants.TAG, "Insert to thread pool, downloadId: "
                            + downloadId + ",blockId: " + blockId + "status: " + status);

                    ///put to thread pool, status is running, not wait for real
                    // schedule
                    ContentValues values = new ContentValues();
                    values.put(Constants.SubDownloads.COLUMN_STATUS, STATUS_RUNNING);
                    Uri subDownload = Uri.withAppendedPath(info.getMyDownloadsUri(),
                            Constants.SubDownloads.URI_SEGMENT + "/" + blockId);
                    context.getContentResolver().update(subDownload, values, null, null);
                }
            }
        } finally {
            cursor.close();
        }
    }

    private void saveRangedownloadInfo(Context context, long blockLength, long contentLength,
            DownloadInfo info) {
        for (int i = 0; i < Constants.DEFAULT_BLOCKS; i++) {
            long startPos = blockLength * i;
            long endPos;
            if (i == Constants.DEFAULT_BLOCKS - 1) {
                endPos = contentLength - 1;
            } else {
                endPos = blockLength * (i + 1) - 1;
            }

            ContentValues subDonwloadvalues = new ContentValues();
            subDonwloadvalues.put(Constants.SubDownloads.COLUMN_DOWNLOAD_ID, info.mId);
            subDonwloadvalues.put(Constants.SubDownloads.COLUMN_SUB_BLOCK_ID, i);
            subDonwloadvalues.put(Constants.SubDownloads.COLUMN_URI, info.mUri);
            subDonwloadvalues.put(Constants.SubDownloads.COLUMN_START_POS, startPos);
            subDonwloadvalues.put(Constants.SubDownloads.COLUMN_END_POS, endPos);
            subDonwloadvalues.put(Constants.SubDownloads.COLUMN_CURRENT_WRITE_BYTES, 0);
            subDonwloadvalues
                    .put(Constants.SubDownloads.COLUMN_TOTAL_LENGTH, endPos - startPos + 1);
            subDonwloadvalues.put(Constants.SubDownloads.COLUMN_STATUS, 0);
            context.getContentResolver().insert(info.getMyDownloadsUri(), subDonwloadvalues);
            Xlog.v(Constants.TAG, "Save RangeDownload info, "
                    + Constants.SubDownloads.COLUMN_DOWNLOAD_ID + ":" + info.mId + ", "
                    + Constants.SubDownloads.COLUMN_SUB_BLOCK_ID + ":" + i + ", "
                    + Constants.SubDownloads.COLUMN_URI + ":" + info.mUri + ", "
                    + Constants.SubDownloads.COLUMN_START_POS + ":" + startPos + ", "
                    + Constants.SubDownloads.COLUMN_END_POS + ":" + endPos + ", "
                    + Constants.SubDownloads.COLUMN_CURRENT_WRITE_BYTES + ":" + 0 + ", "
                    + Constants.SubDownloads.COLUMN_TOTAL_LENGTH + ":" + (endPos - startPos + 1));
        }
    }

    private void addToThreadPool(Context context, URL url, long blockId, long startPos, long endPos,
            long currentWriteLength, long totalLength, String fileName, DownloadInfo info) {
        RangeDownloadThread rangeDownloadTask = new RangeDownloadThread(context, url, blockId,
                startPos, endPos, currentWriteLength, totalLength, fileName, info);
        mExecutor.submit(rangeDownloadTask);

    }

}
