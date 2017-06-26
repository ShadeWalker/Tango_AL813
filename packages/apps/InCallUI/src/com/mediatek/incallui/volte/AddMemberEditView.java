package com.mediatek.incallui.volte;

import android.content.Context;
import android.util.AttributeSet;
import com.android.mtkex.chips.MTKRecipientEditTextView;
import com.android.mtkex.chips.BaseRecipientAdapter;
//import com.android.mtkex.chips.R;

public class AddMemberEditView extends MTKRecipientEditTextView {

    private static final int AUTO_SEARCH_THRESHOLD_LENGTH = 1;

    public AddMemberEditView(Context context, AttributeSet attrs) {
        super(context, attrs);
        super.setValidator(new NumberValidator());
        //set search address threshold length as 1
        setThreshold(AUTO_SEARCH_THRESHOLD_LENGTH);
    }

    /** A noop validator that does not munge invalid texts and claims any number is valid */
    private class NumberValidator implements Validator {
        public CharSequence fixText(CharSequence invalidText) {
            return invalidText;
        }

        public boolean isValid(CharSequence text) {
            return true;
        }
    }

    public static class AddMemberEditViewAdatper extends BaseRecipientAdapter {
        private static final int DEFAULT_PREFERRED_MAX_RESULT_COUNT = 10;

        public AddMemberEditViewAdatper(Context context) {
            // The Chips UI is email-centric by default. By setting QUERY_TYPE_PHONE, the chips UI
            // will operate with phone numbers instead of emails.
            super(context, DEFAULT_PREFERRED_MAX_RESULT_COUNT, QUERY_TYPE_PHONE);
            setShowDuplicateResults(true);
        }

        /**
         * Returns a layout id for each item inside auto-complete list.
         *
         * Each View must contain two TextViews (for display name and destination) and one ImageView
         * (for photo). Ids for those should be available via {@link #getDisplayNameId()},
         * {@link #getDestinationId()}, and {@link #getPhotoId()}.
         */
        @Override
        protected int getItemLayout() {
            return com.android.incallui.R.layout.mtk_volte_add_conference_member_dropdown_item;
        }

        public void setShowEmailAddress(boolean showEmailAddress) {
            super.setShowPhoneAndEmail(showEmailAddress);
        }
    }
}
