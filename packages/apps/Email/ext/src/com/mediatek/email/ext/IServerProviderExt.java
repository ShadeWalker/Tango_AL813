package com.mediatek.email.ext;


import android.content.Context;

public interface IServerProviderExt {

    /**
     * M: check if need to support provider List function.
     *
     * @return true if support, the value is set in in plugin.
     */
    public boolean isSupportProviderList();

    /**
     * M: get the extension providers domains.
     *
     * @return extension provider domains.
     */
    public String[] getProviderDomains();

    /**
     * M: get the extension providers' names.
     *
     * @return extension provider names.
     */
    public String[] getProviderNames();

    /**
     * M: get the plugin context.
     *
     * @return the current context
     */
    public Context getContext();

    /**
     * M: get the acount description, used in account setting step.
     *
     * @return the account name description
     */
    public String getAccountNameDescription();

    /**
     * M: get the extension provider xml, use this to get the provider host.
     *
     * @return the provider xml
     */
    public int getProviderXml();

    /**
     * M: get the provider icons, used to show AccountSetupChooseESP listview.
     *
     * @return the provider icon
     */
    public int[] getProviderIcons();

    /**
     * M: get the provider number to display in chooseESP activity.
     *
     * @return the display ESP num
     */
    public int getDisplayESPNum();

    /**
     * M: get the account signature, use to display in send mail content.
     *
     * @return the account's signature
     */
    public String getAccountSignature();

    /** M: get the default provider domain, use to check the account whether is default.
     *
     * @return the default provider domain
     */
    public String getDefaultProviderDomain();
}
