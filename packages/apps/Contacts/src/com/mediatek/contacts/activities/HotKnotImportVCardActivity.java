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
 * limitations under the License.
 */

package com.mediatek.contacts.activities;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.vcard.CancelRequest;
import com.android.contacts.common.vcard.ExportRequest;
import com.android.contacts.common.vcard.ImportRequest;
import com.android.contacts.common.vcard.SelectAccountActivity;
import com.android.contacts.common.vcard.VCardImportExportListener;
import com.android.contacts.common.vcard.VCardService;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntryCounter;
import com.android.vcard.VCardParser;
import com.android.vcard.VCardParser_V21;
import com.android.vcard.VCardParser_V30;
import com.android.vcard.VCardSourceDetector;
import com.android.vcard.exception.VCardException;
import com.android.vcard.exception.VCardNestedException;
import com.android.vcard.exception.VCardVersionException;
import com.mediatek.hotknot.HotKnotAdapter;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

public class HotKnotImportVCardActivity extends Activity implements
        ServiceConnection, VCardImportExportListener {
    private static final String TAG = "HotKnotImportVCardActivity";

    private static final String HOTKNOT_VCARD_ACTION = "com.mediatek.hotknot.action.VCARD_DISCOVERD";

    private static final int VCARD_VERSION_V21 = 1;
    private static final int VCARD_VERSION_V30 = 2;

    private static final int SELECT_ACCOUNT = 1;

    private AccountWithDataSet mAccount;
    private byte[] mData;

    /* package */ class ImportTask extends AsyncTask<VCardService, Void, ImportRequest> {
        @Override
        public ImportRequest doInBackground(VCardService... services) {
            ImportRequest request = createImportRequest();
            if (request == null) {
                return null;
            }

            ArrayList<ImportRequest> requests = new ArrayList<ImportRequest>();
            requests.add(request);
            services[0].handleImportRequest(requests, HotKnotImportVCardActivity.this);
            return request;
        }

        @Override
        public void onCancelled() {
            unbindService(HotKnotImportVCardActivity.this);
        }

        @Override
        public void onPostExecute(ImportRequest request) {
            unbindService(HotKnotImportVCardActivity.this);
        }
    }

    /* package */ ImportRequest createImportRequest() {
        VCardParser parser;
        VCardEntryCounter counter = null;
        VCardSourceDetector detector = null;
        int vcardVersion = VCARD_VERSION_V21;
        try {
            ByteArrayInputStream is = new ByteArrayInputStream(mData);
            is.mark(0);
            parser = new VCardParser_V21();
            try {
                counter = new VCardEntryCounter();
                detector = new VCardSourceDetector();
                parser.addInterpreter(counter);
                parser.addInterpreter(detector);
                parser.parse(is);
            } catch (VCardVersionException e1) {
                is.reset();
                vcardVersion = VCARD_VERSION_V30;
                parser = new VCardParser_V30();
                try {
                    counter = new VCardEntryCounter();
                    detector = new VCardSourceDetector();
                    parser.addInterpreter(counter);
                    parser.addInterpreter(detector);
                    parser.parse(is);
                } catch (VCardVersionException e2) {
                    return null;
                }
            } finally {
                try {
                    if (is != null) is.close();
                } catch (IOException e) {
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed reading vcard data", e);
            return null;
        } catch (VCardNestedException e) {
            Log.w(TAG, "Nested Exception is found (it may be false-positive).");
            // Go through without throwing the Exception, as we may be able to detect the
            // version before it
        } catch (VCardException e) {
            Log.e(TAG, "Error parsing vcard", e);
            return null;
        }

        Log.d(TAG, "Could create ImportRequest.");
        return new ImportRequest(mAccount, mData, null,
                null, detector.getEstimatedType(),
                detector.getEstimatedCharset(), vcardVersion, counter.getCount());
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        VCardService service = ((VCardService.MyBinder) binder).getService();
        new ImportTask().execute(service);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Do nothing
    }

    @Override
    protected void onCreate(Bundle bundle) {
        Log.d(TAG, "onCreate");
        super.onCreate(bundle);

        Intent intent = getIntent();
        if (!HOTKNOT_VCARD_ACTION.equals(intent.getAction())) {
            Log.w(TAG, "Unknown intent " + intent);
            finish();
            return;
        }

        mData = intent.getByteArrayExtra(HotKnotAdapter.EXTRA_DATA);

        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(this);
        final List<AccountWithDataSet> accountList = accountTypes.getAccounts(true);
        if (accountList.size() == 0) {
            mAccount = null;
        } else if (accountList.size() == 1) {
            mAccount = accountList.get(0);
        } else {
            startActivityForResult(new Intent(this, SelectAccountActivity.class), SELECT_ACCOUNT);
            return;
        }

        startImport();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == SELECT_ACCOUNT) {
            if (resultCode == RESULT_OK) {
                mAccount = new AccountWithDataSet(
                        intent.getStringExtra(SelectAccountActivity.ACCOUNT_NAME),
                        intent.getStringExtra(SelectAccountActivity.ACCOUNT_TYPE),
                        intent.getStringExtra(SelectAccountActivity.DATA_SET));
                startImport();
            } else {
                finish();
            }
        }
    }

    private void startImport() {
        Log.d(TAG, "Start import");
        // We don't want the service finishes itself just after this connection.
        Intent intent = new Intent(this, VCardService.class);
        startService(intent);
        bindService(intent, this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onImportProcessed(ImportRequest request, int jobId, int sequence) {
        // do nothing
    }

    @Override
    public void onImportParsed(ImportRequest request, int jobId, VCardEntry entry, int currentCount,
            int totalCount) {
        // do nothing
    }

    @Override
    public void onImportFinished(ImportRequest request, int jobId, Uri uri) {
        if (isFinishing()) {
            Log.i(TAG, "Late import -- ignoring");
            return;
        }

        if (uri != null) {
            Uri contactUri = RawContacts.getContactLookupUri(getContentResolver(), uri);
            Intent intent = new Intent(Intent.ACTION_VIEW, contactUri);
            startActivity(intent);
        }
        finish();
    }

    @Override
    public void onImportFailed(ImportRequest request) {
        if (isFinishing()) {
            Log.i(TAG, "Late import failure -- ignoring");
            return;
        }
    }

    @Override
    public void onImportCanceled(ImportRequest request, int jobId) {
        // do nothing
    }

    @Override
    public void onExportProcessed(ExportRequest request, int jobId) {
        // do nothing
    }

    @Override
    public void onExportFailed(ExportRequest request) {
        // do nothing
    }

    @Override
    public void onCancelRequest(CancelRequest request, int type) {
        // do nothing
    }

    @Override
    public void onComplete() {
        // do nothing
    }
}
