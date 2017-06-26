/*
 * Copyright (C) 2007 The Android Open Source Project
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
 * limitations under the License.
 */

package com.mediatek.dialer.activities;

import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;

import com.android.dialer.R;

import com.mediatek.dialer.calllog.CallLogMultipleDeleteFragment;

/**
 * Displays a list of call log entries.
 */
public class CallLogMultipleChoiceActivity extends CallLogMultipleDeleteActivity {
    protected CallLogMultipleDeleteFragment mFragment;
    private static final String TAG = "CallLogMultipleChoiceActivity";

    @Override
    public void onStart() {
        super.onStart();
        mFragment = (CallLogMultipleDeleteFragment) getMultipleDeleteFragment();
    }

    protected OnClickListener onClickListenerOfActionBarOKButton() {
        return new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                String ids = mFragment.getSelections();
                intent.putExtra("calllogids", ids);
                setResult(RESULT_OK, intent);
                finish();
                return;
            }
        };
    }

    @Override
    protected void onStop() {
        super.onStopForSubClass();
        //this.finish();
    }

    @Override
    protected void setActionBarView(View view) {
        //display the "confirm" button.
        Button confirmView = (Button) view.findViewById(R.id.confirm);
        confirmView.setOnClickListener(onClickListenerOfActionBarOKButton());
        confirmView.setVisibility(View.VISIBLE);
        ImageView divider2View = (ImageView) view.findViewById(R.id.ic_divider2);
        divider2View.setVisibility(View.VISIBLE);

        //hidden the "delete" button
        Button deleteView = (Button) view.findViewById(R.id.delete);
        deleteView.setVisibility(View.INVISIBLE);
    }

    private void log(final String log) {
        Log.i(TAG, log);
    }
}
