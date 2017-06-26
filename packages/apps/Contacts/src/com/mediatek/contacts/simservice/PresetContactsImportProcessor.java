package com.mediatek.contacts.simservice;
 
import com.mediatek.contacts.simservice.SIMProcessorManager.ProcessorCompleteListener;
 
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
 
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;//for usim
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
 
 
import com.android.contacts.common.model.account.AccountType;
import android.os.RemoteException;
 
import java.util.ArrayList;

import com.mediatek.contacts.simservice.SIMProcessorManager.ProcessorCompleteListener;
import com.mediatek.contacts.simservice.SIMServiceUtils;
import com.mediatek.contacts.simservice.SIMServiceUtils.ServiceWorkData;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.util.LogUtils;
import android.provider.ContactsContract.PhoneLookup;
	
//add by HQ_xiatao preset contact start
import com.android.contacts.R;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import android.content.ContentUris;
import android.provider.ContactsContract.CommonDataKinds.Organization;
//add by HQ_xiatao preset contact end

 
public class PresetContactsImportProcessor extends SIMProcessorBase {
    private static final String TAG = "PresetContactsImportProcessor";
    private static boolean sIsRunningNumberCheck = false;
    private static final int INSERT_PRESET_NUMBER_COUNT = 1;           //预置联系人的个数
    private String  INSERT_PRESET_NAME ;   
    private String  INSERT_PRESET_NUMBER;  
    private String  INSERT_PRESET_EMAIL;
	private String  INSERT_PRESET_ORGANIZATION;
   
    private int mSlotId;
    private Context mContext;
 
    public PresetContactsImportProcessor(Context context, int slotId, Intent intent,
            ProcessorCompleteListener listener) {
        super(intent, listener);
        mContext = context;
        mSlotId = slotId;
    }
 
    @Override
    public int getType() {
        return SIMServiceUtils.SERVICE_WORK_IMPORT_PRESET_CONTACTS;
    }
 
    @Override
    public void doWork() {
        if (isCancelled()) {
            LogUtils.d(TAG, "[doWork]cancel import preset contacts work. Thread id=" + Thread.currentThread().getId());
            return;
        }
		//HQ_wuruijun modify for HQ01344793 start
		/*INSERT_PRESET_NAME = mContext.getResources().getString(R.string.hotline_name);
		INSERT_PRESET_NUMBER = mContext.getResources().getString(R.string.hotline_phone);
		INSERT_PRESET_EMAIL = mContext.getResources().getString(R.string.hotline_email);
		INSERT_PRESET_ORGANIZATION = mContext.getResources().getString(R.string.hotline_organization);
		importDefaultReadonlyContact();*/
		//HQ_wuruijun modify end
    }
   
    private void importDefaultReadonlyContact(){
         Log.i(TAG, "isRunningNumberCheck before: " + sIsRunningNumberCheck);
         if (sIsRunningNumberCheck) {
            return;
         }
         sIsRunningNumberCheck = true;
         for(int i = 0;i < INSERT_PRESET_NUMBER_COUNT; i++)
         {
             Log.i(TAG, "isRunningNumberCheck after: " + sIsRunningNumberCheck);
             Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri
                      .encode(INSERT_PRESET_NUMBER));
             Log.i(TAG, "getContactInfoByPhoneNumbers(), uri = " + uri);
 
             Cursor contactCursor = mContext.getContentResolver().query(uri, new String[] {
                      PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_ID
         }, null, null, null);
         try {
             if (contactCursor != null && contactCursor.getCount() > 0) {
                  return;
             } else {
                  ContentValues values = new ContentValues();
                  Uri rawContactUri = mContext.getContentResolver().insert(ContactsContract.RawContacts.CONTENT_URI, values);
              	  long rawContactId = ContentUris.parseId(rawContactUri);
                  values.clear();
              // customer display name
              values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
              values.put(ContactsContract.Data.MIMETYPE,
                 ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
              values.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, INSERT_PRESET_NAME);
              values.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                 INSERT_PRESET_NAME);
              mContext.getContentResolver().insert(ContactsContract.Data.CONTENT_URI, values);
              values.clear();
              // customer avatar
              Bitmap sourceBitmap = BitmapFactory.decodeResource(mContext.getResources(),
                  R.drawable.huawei_hotline);
              final ByteArrayOutputStream os = new ByteArrayOutputStream();
              // use Bitmap press avatar to PNG code,quality 100%
              sourceBitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
              byte[] avatar = os.toByteArray();
              values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
              values.put(ContactsContract.Data.MIMETYPE,
                  ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
              values.put(ContactsContract.Contacts.Photo.PHOTO, avatar);
              mContext.getContentResolver().insert(ContactsContract.Data.CONTENT_URI, values);
              values.clear();

		       // tel
              values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
              values.put(ContactsContract.Data.MIMETYPE,
                  ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
              values.put(ContactsContract.CommonDataKinds.Phone.TYPE,
                  ContactsContract.CommonDataKinds.Phone.TYPE_WORK);
              values.put(ContactsContract.CommonDataKinds.Phone.NUMBER, INSERT_PRESET_NUMBER);
              mContext.getContentResolver().insert(ContactsContract.Data.CONTENT_URI, values);
              values.clear();

               // email
              values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
              values.put(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);
              values.put(ContactsContract.CommonDataKinds.Email.TYPE,
                                 ContactsContract.CommonDataKinds.Email.TYPE_WORK);
              values.put(ContactsContract.CommonDataKinds.Email.ADDRESS, INSERT_PRESET_EMAIL);
              mContext.getContentResolver().insert(ContactsContract.Data.CONTENT_URI, values);
 			  //organization
              values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
              values.put(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE);
              values.put(ContactsContract.CommonDataKinds.Organization.TYPE,
                                 ContactsContract.CommonDataKinds.Organization.TYPE_WORK);
              values.put(ContactsContract.CommonDataKinds.Organization.COMPANY, INSERT_PRESET_ORGANIZATION);
              mContext.getContentResolver().insert(ContactsContract.Data.CONTENT_URI, values);		
 
             }
         } finally {
             // when this service start,but the contactsprovider has not been started yet.
             // the contactCursor perhaps null, but not always.(first load will weekup the provider)
             // so add null block to avoid nullpointerexception
             if (contactCursor != null) {
                  contactCursor.close();
             }
         }//for
         Log.i(TAG, "isRunningNumberCheck insert: " + sIsRunningNumberCheck);
         sIsRunningNumberCheck = false;
         }
    }
}
