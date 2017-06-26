package com.mediatek.contacts.ext;

import android.content.Context;
import android.net.Uri;
import android.view.View;

/**
 * 
 * For RCS-e view custom.
 *
 */
public interface IViewCustomExtension {
    /**
     *
     * Used in RCS-e to custom ContactListItemView in People List
     */
    public interface ContactListItemViewCustom {
        /**
         * The view contains a icon
         * @return ImageView contains joyn icon
         */
        View createCustomView(long contactId);

        /**
         *
         * @param widthMeasureSpec
         * @param heightMeasureSpec
         */
        void onMeasure(int widthMeasureSpec, int heightMeasureSpec);

        /**
         *
         * @param changed
         * @param leftBound
         * @param topBound
         * @param rightBound
         * @param bottomBound
         */
        void onLayout(boolean changed, int leftBound, int topBound, int rightBound, int bottomBound);
    }

    /**
     * 
     * Used to add Joyn card view in QuickActivity
     *
     */
    public interface QuickContactCardViewCustom {
        /**
         * Plugin should create your Joyn Card view & add into it's LinearLayout parent id.card_container
         * @param lookupUri Contact lookup Uri
         * @return the instance of ExpandingEntryCardView that plugin add.
         */
        View createCardView(View container, View anchorView, Uri lookupUri, Context context);
    }

    /**
     * 
     * Used to add Joyn icon on the left top in QuickActivity
     *
     */
    public interface QuickContactScrollerCustom {
        /**
         * 
         * @param container A FrameLayout placed on the left top
         * @param anchorView the NameView
         * @param lookupUri Contact uri
         * @return ImageView contains joyn icon
         */
        View createJoynIconView(View container, View anchorView, Uri lookupUri);

        /**
         * update the ImageView visiblity when scroll.
         */
        void updateJoynIconView();
    }

    /**
     *
     * @return get the object for custom ContactListItemView
     */
    ContactListItemViewCustom getContactListItemViewCustom();

    /**
     * @return get the object for custom QuickActivity Card
     */
    QuickContactCardViewCustom getQuickContactCardViewCustom();

    /**
     * @return get the object for custom MultiShrinkScroller
     */
    QuickContactScrollerCustom getQuickContactScrollerCustom();
}
