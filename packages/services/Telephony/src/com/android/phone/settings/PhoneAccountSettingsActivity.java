/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.phone.settings;

import android.os.Bundle;
import android.os.UserHandle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;
import android.widget.Toast;
import android.util.Log;

import com.android.phone.R;

public class PhoneAccountSettingsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        /* add by shanlan for HQ01839428 begin */
        if (UserHandle.myUserId() != UserHandle.USER_OWNER) {
            Toast.makeText(this, R.string.call_settings_primary_user_only,
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        /* add by shanlan for HQ01839428 end */

        getActionBar().setTitle(R.string.phone_accounts_settings_header);
        getFragmentManager().beginTransaction().replace(
                android.R.id.content, new PhoneAccountSettingsFragment()).commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
