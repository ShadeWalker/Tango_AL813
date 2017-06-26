package com.mediatek.systemui.ext;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * M: the interface for Plug-in definition of Status bar.
 */
public interface IStatusBarPlmnPlugin {
   /**
    * Get Plmn TextView.
    * @param context A Context object.
    * @return Plmn TextView.
    */
   TextView getPlmnTextView(Context context);

    /**
     * Bind Setting Service.
     * @param context A Context object.
     */
    void bindSettingService(Context context);

    /**
     * Whether support customize PLMN carrier label.
     * @return Return whether support customize carrier label.
     */
    boolean supportCustomizeCarrierLabel();

    /**
     * Customize PLMN carrier label.
     * @param parentView parent view.
     * @param orgCarrierLabel org default carrier label.
     * @return Customize carrier label ViewGroup.
     */
    View customizeCarrierLabel(ViewGroup parentView, View orgCarrierLabel);

    /**
     * Update PLMN carrier label visibility.
     * @param force Whether force update.
     * @param makeVisible Whether Visible.
     */
    void updateCarrierLabelVisibility(boolean force, boolean makeVisible);

    /**
     * Update PLMN carrier label by slotId.
     * @param slotId The slot index.
     * @param isSimInserted Whether Sim Inserted.
     * @param isHasSimService Whether Sim service is available.
     * @param networkNames carrier plmn texts.
     */
    void updateCarrierLabel(int slotId, boolean isSimInserted, boolean isHasSimService,
            String[] networkNames);
}
