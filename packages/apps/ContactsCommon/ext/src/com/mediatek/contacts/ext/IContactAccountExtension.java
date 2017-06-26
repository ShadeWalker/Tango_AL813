package com.mediatek.contacts.ext;

public interface IContactAccountExtension {

    public interface FeatureName {
        /**
         * if disable,there is no voip in every accouts
         */
        String VOIP = "voip";
        /**
         * if disable,if will always show mtk_raw_sim_contact_editor_view_blue in
         * ContactEditorFragment.java for SIM/USIM/UIM accouts
         */
        String SIM_COLOR_IN_EDITOR = "show_sim_color_in_editor";
    }

    /**
     * for op09
     * Plugin can diable feature by return false
     * @param accountType Account Type,may be null if not special account type.
     * @param featureName {@link }
     * @return true to enable featureName,false to disable
     */
    public boolean enableFeature(String accountType, String featureName);
}
