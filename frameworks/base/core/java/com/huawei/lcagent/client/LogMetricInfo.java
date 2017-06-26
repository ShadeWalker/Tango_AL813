package com.huawei.lcagent.client;

import android.os.Parcel;
import android.os.Parcelable;

public class LogMetricInfo implements Parcelable {
    /**
     * the unique id for current, which is also used to delete the log
     */
    public long id;
    /**
     * path for current log stored in flash.
     */
    public String path;
    /**
     * description for current log
     */
    public String description;
    /**
     * Log file lists that included in this log package.
     */
    public String[] files;
    /**
     * pack logs time
     */
    public String zipTime;
    /**
     * details info of log file lists
     */
    public String logDetailedInfo;

    public LogMetricInfo() {
        id = 0;
        description = null;
        files = null;
        path = null;
        zipTime = null;
        logDetailedInfo = null;
    }

    public LogMetricInfo(long id, String path, String description, String[] files, String zipTime, String logDetailedInfo) {
        this.id = id;
        this.path = path;
        this.description = description;
        this.zipTime = zipTime;
        this.logDetailedInfo = logDetailedInfo;

        if (files == null || files.length == 0) {
            this.files = null;
        } else {
            this.files = new String[files.length];
            int length = files.length;

            for (int i = 0; i < length; i++) {
                this.files[i] = files[i];
            }
        }
    }

    private LogMetricInfo(Parcel in) {
        id = in.readLong();
        path = in.readString();
        description = in.readString();
        files = in.createStringArray();
        zipTime = in.readString();
        logDetailedInfo = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(path);
        dest.writeString(description);
        dest.writeStringArray(files);
        dest.writeString(zipTime);
        dest.writeString(logDetailedInfo);
    }

    public static final Parcelable.Creator<LogMetricInfo> CREATOR = new Parcelable.Creator<LogMetricInfo>() {
        @Override
        public LogMetricInfo createFromParcel(Parcel in) {
            return new LogMetricInfo(in);
        }

        @Override
        public LogMetricInfo[] newArray(int size) {
            return new LogMetricInfo[size];
        }
    };

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("id = " + id + "\n");
        sb.append("path = " + path + "\n");
        sb.append("description = " + description + "\n");
        if (files == null) {
            return sb.toString();
        }
        int length = files.length;

        for (int i = 0; i < length; i++) {
            sb.append("files[" + i + "]=" + files[i] + "\n");
        }
        sb.append("zipTime = " + zipTime + "\n");
        sb.append("logDetailedInfo = " + logDetailedInfo + "\n");
        return sb.toString();
    }
}
