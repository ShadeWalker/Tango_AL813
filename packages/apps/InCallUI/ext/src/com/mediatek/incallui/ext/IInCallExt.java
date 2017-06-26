package com.mediatek.incallui.ext;

public interface IInCallExt {

    /**
     * Hint. When show the "No SIM or SIM error" alert dialog, plugin can replace
     * the error string.
     */
    public static final String HINT_ERROR_MSG_SIM_ERROR = "hint_error_sim_error";
    /**
     * Hint. When show account dialog, whether to show the "set account as default"
     * check box or not. If plugin decide to show the check box, replace default value
     * with true, otherwise replace with false.
     */
    public static final String HINT_BOOLEAN_SHOW_ACCOUNT_DIALOG
            = "hint_boolean_show_account_dialog";

    /**
     * Called when need to replace error message by hint string.
     * FIXME: should replaced by #replaceValue
     * @deprecated use #replaceValue instead.
     * 
     * @param defaultString
     * @param hint
     * @return new String.
     */
    String replaceString(String defaultString, String hint);

    /**
     * Called when any value need be changed by the plugin side.
     * This is a common API, the plugin can check the caller by the hint,
     * and decide to replace default value or not.
     *
     * @param defaultObject The host default value to be replaced.
     *                      by default, the value will be returned directly.
     * @param hint          The hint to indicate from where the API is called in host.
     * @return Make sure the return value is the #defaultObject itself, or the
     * replaced value who is the same class as #defaultObject.
     */
    Object replaceValue(Object defaultObject, String hint);
}
