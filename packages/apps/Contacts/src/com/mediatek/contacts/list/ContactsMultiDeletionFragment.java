
package com.mediatek.contacts.list;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

import com.mediatek.contacts.ExtensionManager;
import com.android.contacts.R;

import com.mediatek.contacts.list.MultiContactsBasePickerAdapter.PickListItemCache;
import com.mediatek.contacts.list.MultiContactsBasePickerAdapter.PickListItemCache.PickListItemData;
import com.mediatek.contacts.list.service.MultiChoiceHandlerListener;
import com.mediatek.contacts.list.service.MultiChoiceRequest;
import com.mediatek.contacts.list.service.MultiChoiceService;
import com.mediatek.contacts.util.LogUtils;

import java.util.ArrayList;
import java.util.List;

public class ContactsMultiDeletionFragment extends MultiContactsPickerBaseFragment {
    private static final String TAG = "ContactsMultiDeletionFragment";

    public static final boolean DEBUG = true;
    private static final String DIALOG_FRAGMENT_TAG = "confirm";

    private SendRequestHandler mRequestHandler;
    private HandlerThread mHandlerThread;

    private DeleteRequestConnection mConnection;

    private int mRetryCount = 20;
    private static int itemsCount = 0;
    private static int listItemCount = 0;
    @Override
    public CursorLoader createCursorLoader(Context context) {
        return new CursorLoader(context, null, null, null, null, null);
    }

    @Override
    public void onOptionAction() {
        final MultiContactsBasePickerAdapter adapter = (MultiContactsBasePickerAdapter) getAdapter();
        int listItemSize = adapter == null ? -1 : adapter.getListItemCache() == null ? -1 : adapter.getListItemCache()
                .getCacheSize();
        listItemCount = listItemSize;
        if (getCheckedItemIds().length == 0) {
            Toast.makeText(this.getContext(), R.string.multichoice_no_select_alert,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        itemsCount = getCheckedItemIds().length;
        ConfirmDialog cDialog = new ConfirmDialog();
        cDialog.setTargetFragment(this, 0);
        cDialog.setArguments(this.getArguments());
        cDialog.show(this.getFragmentManager(), DIALOG_FRAGMENT_TAG);
    }

    public static class ConfirmDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            String title = this.getActivity().getResources().getString(R.string.multichoice_delete_confirm_title);
            String message = this.getActivity().getResources().getString(R.string.multichoice_delete_confirm_message);

            if(getResources().getConfiguration().locale.getCountry().equals("RU")){
            	if (itemsCount == listItemCount) {
                    title = this.getActivity().getResources().getString(R.string.multichoice_delete_confirm_title);
                    message = this.getActivity().getResources().getString(R.string.multichoice_delete_confirm_message_all);
                } else if(itemsCount <= 1){
                    title = this.getActivity().getResources().getString(R.string.multichoice_delete_confirm_title_singular);
                    message = this.getActivity().getResources().getString(R.string.multichoice_delete_confirm_message_singular);
                }else{
                	 title = this.getActivity().getResources().getString(R.string.multichoice_delete_confirm_title);
                     message = this.getActivity().getResources().getString(R.string.multichoice_delete_confirm_message);
                }
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity()).setTitle(title).setIconAttribute(
                    android.R.attr.alertDialogIcon).setMessage(message).setNegativeButton(
                    android.R.string.cancel, null).setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            final ContactsMultiDeletionFragment target = (ContactsMultiDeletionFragment) getTargetFragment();
                            if (target != null) {
                                target.handleDelete();
                            }
                        }
                    });
            return builder.create();
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            setTargetFragment(null, 0);
        }
    }

    private void handleDelete() {
        if (mConnection != null) {
            LogUtils.w(TAG, "[handleDelete]abort due to mConnection is not null,the delete service has been started.");
            return ;
        }

        //[fix ALPS01206556]
        //don't start delete if the data hasn't been loaded to the listview.
        final MultiContactsBasePickerAdapter adapter = (MultiContactsBasePickerAdapter) getAdapter();
        int listItemSize = adapter == null ? -1 : adapter.getListItemCache() == null ? -1 : adapter.getListItemCache()
                .getCacheSize();
        if (listItemSize <= 0) {
            LogUtils.w(TAG, "[handleDelete] there is no items in the listview.abort delete,listItemSize:" + listItemSize);
            return;
        }
        //[fix ALPS01206556] end

        startDeleteService();

        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread(TAG);
            mHandlerThread.start();
            mRequestHandler = new SendRequestHandler(mHandlerThread.getLooper());
        }

        List<MultiChoiceRequest> requests = new ArrayList<MultiChoiceRequest>();

        final PickListItemCache listItemCacher = adapter.getListItemCache();
        final long[] checkedIds = getCheckedItemIds();
        LogUtils.i(TAG, "[handleDelete] listItemSize:" + listItemSize + ",checkedItemSize:" + checkedIds.length);
        for (long id : checkedIds) {
            PickListItemData item = listItemCacher.getItemData(id);
            requests.add(new MultiChoiceRequest(item.contactIndicator, item.simIndex, (int) id, item.displayName));
        }

        /*
         * Bug Fix by Mediatek Begin.
         *
         * CR ID: ALPS00233127
         */
        if (requests.size() > 0) {
            mRequestHandler.sendMessage(mRequestHandler.obtainMessage(SendRequestHandler.MSG_REQUEST, requests));
        } else {
            mRequestHandler.sendMessage(mRequestHandler.obtainMessage(SendRequestHandler.MSG_END));
        }
        /*
         * Bug Fix by Mediatek End.
         */
    }

    private class DeleteRequestConnection implements ServiceConnection {
        private MultiChoiceService mService;

        public boolean sendDeleteRequest(final List<MultiChoiceRequest> requests) {
            LogUtils.d(TAG, "Send an delete request");
            if (mService == null) {
                LogUtils.i(TAG, "mService is not ready");
                return false;
            }
            mService.handleDeleteRequest(requests, new MultiChoiceHandlerListener(mService));
            return true;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            LogUtils.d(TAG, "onServiceConnected");
            mService = ((MultiChoiceService.MyBinder) binder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            LogUtils.d(TAG, "Disconnected from MultiChoiceService");
        }
    }

    private class SendRequestHandler extends Handler {

        public static final int MSG_REQUEST = 100;
        public static final int MSG_END = 200;

        public SendRequestHandler(Looper looper) {
            super(looper);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_REQUEST) {
                if (!mConnection.sendDeleteRequest((List<MultiChoiceRequest>) msg.obj)) {
                    if (mRetryCount-- > 0) {
                        sendMessageDelayed(obtainMessage(msg.what, msg.obj), 500);
                    } else {
                        sendMessage(obtainMessage(MSG_END));
                    }
                } else {
                    sendMessage(obtainMessage(MSG_END));
                }
                return;
            } else if (msg.what == MSG_END) {
                destroyMyself();
                return;
            }
            super.handleMessage(msg);
        }

    }

    void startDeleteService() {
        mConnection = new DeleteRequestConnection();

        LogUtils.i(TAG, "Bind to MultiChoiceService.");
        // We don't want the service finishes itself just after this connection.
        Intent intent = new Intent(this.getActivity(), MultiChoiceService.class);
        getContext().startService(intent);
        getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    void destroyMyself() {
        LogUtils.i(TAG, "[destroyMyself]mHandlerThread:" + mHandlerThread);
        getContext().unbindService(mConnection);
        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    /**
     * [ALPS01040180]the dialog of delete confirming, should be dismissed when
     * fragment start. this would lead to better user experience
     */
    private void dismissDialogIfNeeded() {
        DialogFragment dialog = (DialogFragment) getFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG);
        if (dialog != null) {
            LogUtils.i(TAG, "[dismissDialogIfNeeded]dismiss the dialog fragment: " + dialog);
            dialog.dismiss();
        } else {
            LogUtils.d(TAG, "[dismissDialogIfNeeded]no dialog found");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        dismissDialogIfNeeded();
    }

    @Override
    public int getMultiChoiceLimitCount() {
        //M:op01 max support 5000
        int defaultCount = super.getMultiChoiceLimitCount();
        return ExtensionManager.getInstance().getOp01Extension().getMultiChoiceLimitCount(defaultCount);
    }

    ///For CT New Feature @{
    //In contacts delete screen, don't show SDN number
    public boolean isShowCTInternationNumber() {
        return false;
    }
    ///@}
}
