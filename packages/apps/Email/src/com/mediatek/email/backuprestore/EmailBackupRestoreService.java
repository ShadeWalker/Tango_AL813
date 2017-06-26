package com.mediatek.email.backuprestore;



import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

public class EmailBackupRestoreService extends IntentService {

    private static final String TAG = "EmailBackupRestoreService";

    private static final String BACKUP = "com.mediatek.apst.target.START_EMAIL_BACKUP";
    private static final String BACKUP_END = "com.mediatek.email.END_EMAIL_BACKUP";

    private static final String RESTORE = "com.mediatek.apst.target.START_EMAIL_RESTORE";
    private static final String RESTORE_END = "com.mediatek.email.END_EMAIL_RESTORE";

    private static final String PATH = "path";
    private static final String FILENAME = "fileName";
    private static final String IS_SUCCESSFUL = "isSuccessful";

    private String mAction;
    private String mPath;
    private String mFileName;

    public EmailBackupRestoreService() {
        super(EmailBackupRestoreService.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mAction = intent.getAction();
        mPath = intent.getStringExtra(PATH);
        mFileName = intent.getStringExtra(FILENAME);

        if (TextUtils.isEmpty(mAction) || TextUtils.isEmpty(mPath) || TextUtils.isEmpty(mFileName)) {
            Log.i(TAG, "Something null, Path: " + mPath
                    + " ,fileName: " + mFileName + " ,Action: " + mAction);
            reportResult(false);
            return;
        }

        Log.i(TAG, "Begin with action: " + mAction);
        if (BACKUP.equals(mAction)) {
            EmailComposer composer = new EmailComposer(getApplicationContext(), mPath, mFileName);
            boolean composeSuccessful = composer.startComposer();
            composer.releaseSource();
            reportResult(composeSuccessful);
        } else if (RESTORE.equals(mAction)) {
            // TODO restore
        }
    }

    public static void processEmailBackupRestoreIntent(final Context context, Intent intent) {
        Intent i = new Intent(intent.getAction());
        i.putExtra(PATH, intent.getStringExtra(PATH));
        i.putExtra(FILENAME, intent.getStringExtra(FILENAME));
        i.setClass(context, EmailBackupRestoreService.class);
        context.startService(i);
    }

    /**
     * Report the result to the sender whether we have successfully backup email message.
     */
    private void reportResult(boolean successful) {
        Intent intent = new Intent();
        intent.putExtra(PATH, mPath);
        intent.putExtra(FILENAME, mFileName);
        intent.putExtra(IS_SUCCESSFUL, successful);
        if (BACKUP.equals(mAction)) {
            intent.setAction(BACKUP_END);
        } else if (RESTORE.equals(mAction)) {
            intent.setAction(RESTORE_END);
        }
        sendBroadcast(intent);
    }
}
