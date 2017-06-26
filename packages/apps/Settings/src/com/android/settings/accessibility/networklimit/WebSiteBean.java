package com.android.settings.accessibility.networklimit;

/**
 * Created by Administrator on 15-2-5.
 */
public class WebSiteBean {
    private String name;
    private String website;
    private boolean isChecked;
    private String id;
    private String ip;

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }



    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean isChecked) {
        this.isChecked = isChecked;
    }

    @Override
    public String toString() {
        return "Website[name:" + name + ", website:"
                + website + ", isChecked:" + isChecked + ", id:" + id + ", ip:" + ip + "]";
    }
}
