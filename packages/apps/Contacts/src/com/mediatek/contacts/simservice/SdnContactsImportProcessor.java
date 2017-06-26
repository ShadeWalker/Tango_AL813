package com.mediatek.contacts.simservice;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.RawContacts;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.ContactsApplication;
import com.android.contacts.common.model.account.AccountType;
import com.mediatek.contacts.simservice.SIMProcessorManager.ProcessorCompleteListener;
import com.mediatek.contacts.util.LogUtils;
/**
 * 随卡预置联系人 runnable 类，MyBootCmpReceiver中调用
 * @author niubi tang
 *
 */
public class SdnContactsImportProcessor extends SIMProcessorBase  implements  SdnListener{
	private static final String TAG = "tang";

	private static boolean sIsRunningNumberCheck = false;
	// 阿根廷 Personal (MCCMNC：72234/722341)
	// 813 Personal
	// 843 Personal Video
	// 810 Club Personal
	// 3372 Descuentos Club Personal
	// 444 Personal Musica
	// *833 Personal Backtones
	// 7373 Personal Messenger
	// *111 At. al cliente
	// *555 Correo de Voz
	// 112 Número de Emergencia
	private static final String AGENTINA_MCCMNC1 = "72234";
	private static final String AGENTINA_MCCMNC2 = "722341";
	private static final String AGENTINA_INSERT_PRESET_NUMBER[] = { "813", "843",
			"810", "3372", "444", "*833", "7373", "*111", "*555", "112" }; // 阿根廷预置联系人的姓名，10个
	private static final String AGENTINA_INSERT_PRESET_NAME[] = { "Personal",
			"Personal Video", "Club Personal", "Descuentos Club Personal",
			"Personal Musica", "Personal Backtones", "Personal Messenger",
			"At al cliente", "Buzón de voz", "Número de Emergencia" };
//------------------------------------------------------------------------------------------------------------
	// 巴西 OI (MCCMNC:72431/72424/72416)
	// *100 Caixa Postal
	// *144 Atendimento Oi
	// *880 Promoções Oi
	// Ambulancia：192
	// Bombeiros：193
	private static final String BRAZIL_OI_MCCMNC1 = "72431";
	private static final String BRAZIL_OI_MCCMNC2 = "72424";
	private static final String BRAZIL_OI_MCCMNC3 = "72416";
	private static final String BRAZIL_OI_INSERT_PRESET_NUMBER[] = { "*100",
			"*144", "*880", "192", "193" };
	private static final String BRAZIL_OI_INSERT_PRESET_NAME[] = {
			"Caixa Postal ", "Atendimento Oi ", "Promoções Oi ", "Ambulancia ",
			"Bombeiros " };

	// 除“巴西OI以外”的其他巴西运营商(MCCMNC:724)
	// Ambulancia：192
	// Bombeiros：193
	private static final String BRAZIL_NORMAL_INSERT_PRESET_NUMBER[] = { "192",
			"193" };
	private static final String BRAZIL_NORMAL_INSERT_PRESET_NAME[] = {
			"Ambulancia", "Bombeiros" };
//------------------------------------------------------------------------------------------------------------

	// 智利 Entel (MCCMNC:73001/73010)
	// Asistencia Total ： *727
	// Servicio al Cliente ： 103
	// Recarga ： 301
	// Zona EPCS ：*110#
	private static final String CHILE_Entel_MCCMNC1 = "73001";
//	private static final String CHILE_Entel_MCCMNC1 = "46001";  //for test
	private static final String CHILE_Entel_MCCMNC2 = "73010";
	private static final String CHILE_Entel_INSERT_PRESET_NUMBER[] = { "*727","301",
			"103",  "*110#" };
	private static final String CHILE_Entel_INSERT_PRESET_NAME[] = {
			"Asistencia Total","Recarga",  "Servicio al Cliente ", "Zona EPCS" };

	// 智利 Falabella (MCCMNC:73001，spn:FALABELLA)
	// Servicio al Cliente:103
	private static final String Falabella_Entel_INSERT_PRESET_NUMBER[] = { "103"};
	private static final String Falabella_Entel_INSERT_PRESET_NAME[] = {
		"Servicio al Cliente"};
	
//	智利 Entel (MCCMNC:73001/73010)
//	Asistencia Total ： *727
//	Servicio al Cliente ： 103
//	Recarga ： 301
//	Zona EPCS ：*110#
//	智利 Falabella (MCCMNC:73001，spn:FALABELLA)
//	Servicio al Cliente:103
	
	
	

	private static String CurrentName[] = {};
	private static String CurrentNumber[] = {};

	private int mSlotId;
	private Context mContext;

	private TelephonyManager telMgr;
	private String MccMnc;

	public SdnContactsImportProcessor(Context context, int slotId,
			Intent intent, ProcessorCompleteListener listener) {
		super(intent, listener);
		mContext = context;
		mSlotId = slotId;
		telMgr = (TelephonyManager) context
				.getSystemService(Context.TELEPHONY_SERVICE);
		MccMnc = getNetworkOperator();
	}

	/**
	 * MCC+MNC(mobile country code + mobile network code)<br/>
	 * 注意：仅当用户已在网络注册时有效。<br/>
	 * 在CDMA网络中结果也许不可靠。<br/>
	 * 
	 * @return
	 */
	private String getNetworkOperator() {
		return telMgr.getNetworkOperator();
	}

	/**
	 * 按照字母次序的current registered operator(当前已注册的用户)的名字<br/>
	 * 注意：仅当用户已在网络注册时有效。<br/>
	 * 在CDMA网络中结果也许不可靠。
	 * 
	 * @return
	 */
	private String getNetworkOperatorName() {
		return telMgr.getNetworkOperatorName();
	}

	@Override
	public int getType() {
		return SIMServiceUtils.SERVICE_WORK_IMPORT_PRESET_CONTACTS;
	}

	@Override
	public void doWork() {
		if (isCancelled()) {
			LogUtils.d(TAG,
					"[doWork]cancel import preset contacts work. Thread id="
							+ Thread.currentThread().getId());
			return;
		}
		Log.i(TAG, "the mccMnc is " + MccMnc);
		if(MccMnc==null||MccMnc.length()==0){
			MccMnc=PhoneNumberUtils.getSimMccMnc();
			Log.i(TAG, "the mccMnc is getSimMccMnc:" + MccMnc);
			if(MccMnc==null||MccMnc.length()==0){
				return;
			}
		}
		Log.i(TAG, "getNetworkOperatorName()"+PhoneNumberUtils.getDefaultEfspn(PhoneNumberUtils.getSlotId()).toUpperCase());

		importPortugalSdnContact();
		importAttSdnContact();
		importSdnContact();
	}
	
	/**
	 * 小随卡，不可编不可删，拔卡消失 portugal preset number
	 * 
	 * 在5个地方屏蔽对预置联系人进行编辑处理：
	 * 联系人详情界面、联系人多选界面、新建联系人选择合并联系人时、长按联系人选择删除、合并同名联系人）。
	 * 
	 * 葡萄牙MEO（MCCMNC：26806）PRELOADED CONTACTS: 
	 * Apoio MEO：1696 
	 * Apoio MEO no estrangeiro：+351961696000 
	 * Ativações Serviços：12045 
	 * Saldo Visor：*#123#
	 * Saldo no estrangeiro：12044 1820 
	 * Informações：1820 
	 * Waiting Ring：12700
	 * Pontos Telemovel：12096 
	 * Internet Móvel：12300 
	 * Plafond Internet：*#123*99#
	 * Tarifas Roaming：+351961000083
	 * 
	 */
	private void importPortugalSdnContact() {
		// TODO Auto-generated method stub
		String	MccMnc=PhoneNumberUtils.getSimMccMnc();
		LogUtils.w(TAG,
				"是否是Portuagal版本？ " + android.os.SystemProperties.get("ro.hq.por.sdn"));
		LogUtils.w(TAG,
				"MCCMNC IS？ " + MccMnc);
		if (!android.os.SystemProperties.get("ro.hq.por.sdn").equals("1")) {
			return;
		}
		
		String AttName[] = { "Apoio MEO", "Apoio MEO no estrangeiro",
				"Ativações Serviços", "Saldo Visor",
				"Saldo no estrangeiro", "1820 Informações", "Waiting Ring",
				"Pontos Telemovel", "Internet Móvel", "Plafond Internet",
		"Tarifas Roaming" };
		String AttNumber[] = { "1696", "+351961696000", "12045", "*#123#",
				"12044", "1820", "12700", "12096", "12300", "*#123*99#",
		"+351961000083" };
		if (MccMnc.equals("26806")) {


			Log.i(TAG, "importPortugalSdnContact");
			for (int i = 0; i < AttName.length; i++) {
				Log.i(TAG, "current att name is " + AttName[i]);
				// 测试使用code---------------------
				/*
				 * Cursor contactCursor = ContactsApplication .getContactsDb()
				 * .rawQuery(
				 * "select  display_name from view_raw_contacts where deleted = 0  and  display_name = '"
				 * + AttName[i] + "'", null);
				 */
				// 如果用户删除了就应该不再预置了---------------------

				Cursor contactCursor = ContactsApplication.getContactsDb()
						.rawQuery(
								"select  display_name from view_raw_contacts where  display_name = '"
										+ AttName[i] + "'", null);

				try {
					if (contactCursor != null && contactCursor.getCount() > 0) {
						while (contactCursor.moveToNext()) {
							String name = contactCursor.getString(0);
							Log.i(TAG, "查询到的联系人是" + name);

						}
						Log.i(TAG, "Por cursor is null ! 存在需要与之的联系人  直接返回");
						continue;
					} else {
						Log.i(TAG, "ATT开始写入联系人" + AttName[i]);
						final ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
						ContentProviderOperation.Builder builder = ContentProviderOperation
								.newInsert(RawContacts.CONTENT_URI);
						ContentValues contactvalues = new ContentValues();
						contactvalues.put(RawContacts.ACCOUNT_NAME,
								AccountType.ACCOUNT_NAME_LOCAL_PHONE);
						contactvalues.put(RawContacts.ACCOUNT_TYPE,
								AccountType.ACCOUNT_TYPE_LOCAL_PHONE);
						contactvalues.put(RawContacts.INDICATE_PHONE_SIM,
								ContactsContract.RawContacts.INDICATE_PHONE);
		                contactvalues.put(RawContacts.IS_SDN_CONTACT, -2);
						
						builder.withValues(contactvalues);
						builder.withValue(RawContacts.AGGREGATION_MODE,
								RawContacts.AGGREGATION_MODE_DISABLED);
						operationList.add(builder.build());

						builder = ContentProviderOperation
								.newInsert(Data.CONTENT_URI);
						builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
						builder.withValue(Data.MIMETYPE,
								Phone.CONTENT_ITEM_TYPE);
						builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
						builder.withValue(Phone.NUMBER, AttNumber[i]);
						builder.withValue(Data.IS_PRIMARY, 1);
						operationList.add(builder.build());

						builder = ContentProviderOperation
								.newInsert(Data.CONTENT_URI);
						builder.withValueBackReference(
								StructuredName.RAW_CONTACT_ID, 0);
						builder.withValue(Data.MIMETYPE,
								StructuredName.CONTENT_ITEM_TYPE);
						builder.withValue(StructuredName.DISPLAY_NAME,
								AttName[i]);
						operationList.add(builder.build());

						try {
							mContext.getContentResolver().applyBatch(
									ContactsContract.AUTHORITY, operationList);
						} catch (RemoteException e) {
							Log.e(TAG,
									String.format("%s: %s", e.toString(),
											e.getMessage()));
						} catch (OperationApplicationException e) {
							Log.e(TAG,
									String.format("%s: %s", e.toString(),
											e.getMessage()));
						}

					}
				} finally {
					if (contactCursor != null) {
						contactCursor.close();
					}
				}
			}
		}else {//删除
			deleteNoUseNumber(AttName);
		}
	
	}

	/**
	 * 彻底删除数据库中的数据，否则会与用户删除的操作混淆（deleted=1）
	 * @param nameStringArry
	 */
	private void deleteNoUseNumber(String[] nameStringArry) {
		// TODO Auto-generated method stub
		for (String nameString : nameStringArry) {
			Log.i("tang", "deleteNoUseNumber "+nameString);
			 Uri uri = Uri.parse("content://com.android.contacts/raw_contacts");  
		        ContentResolver resolver = mContext.getContentResolver();
		        Cursor cursor = resolver.query(uri, new String[]{Data._ID},"display_name=?", new String[]{nameString}, null);  
		        if(cursor.moveToFirst()){
		            int id = cursor.getInt(0);  
		            //根据id删除data中的相应数据  
//		            resolver.delete(uri, "display_name=?", new String[]{nameString});  
//		            uri = Uri.parse("content://com.android.contacts/data");  
//		            resolver.delete(uri, "raw_contact_id=?", new String[]{id+""}); 
		            ContactsApplication
		            .getContactsDb().delete("raw_contacts", "display_name=?", new String[]{nameString});
		            ContactsApplication
		            .getContactsDb().delete("data", "raw_contact_id=?", new String[]{nameString});
		            }
		        if(cursor!=null){
		        	cursor.close();
		        }  
		}
	}

	/**
	 * 这种方式更好
	 * @param numberArray
	 */
	private void deleteNoUseNumberWithNum(String[] numberArray) {
		// TODO Auto-generated method stub
		for (String numberString : numberArray) {
			Log.i("tang", "deleteNoUseNumber "+numberString);
			
			 // 获得Uri
			  Uri uriNumber2Contacts = Uri.parse("content://com.android.contacts/"
			      + "data/phones/filter/" + numberString); 
			  // 查询Uri，返回数据集
			  Cursor cursorCantacts =mContext.getContentResolver().query(
			      uriNumber2Contacts, 
			      null, 
			      null,            
			      null, 
			      null);
			  // 如果该联系人存在
//			  if (cursorCantacts.getCount() > 0) { 
//			    // 移动到第一条数据
//			    　　　　　　cursorCantacts.moveToFirst();
//			    　　　　　　// 获得该联系人的contact_id
//			    　　　　　　 Long contactID = cursorCantacts.getLong(cursorCantacts.getColumnIndex("_id"));
//			    　　　　　　// 获得contact_id的Uri
//			  }
			 Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
		        ContentResolver resolver = mContext.getContentResolver();
//		        Cursor cursor = resolver.query(uri, new String[]{Data._ID},ContactsContract.CommonDataKinds.Phone.NUMBER, new String[]{numberString}, null);  
		        if(cursorCantacts.moveToFirst()){
		            int id = cursorCantacts.getInt(0);  
		            //根据id删除data中的相应数据  
		            resolver.delete(uri, "number=?", new String[]{numberString});  
		            uri = Uri.parse("content://com.android.contacts/data");  
		            resolver.delete(uri, "raw_contact_id=?", new String[]{id+""});  
		        }  
		}
	}
	
//	private void deleteAllSdnNumber(){
//		Uri uri=Uri.parse("contacts;com.android.contacts");
//		mContext.getContentResolver().delete(uri,ContactsContract.RawContacts.IS_SDN_CONTACT, new String[]{"1"});
//	}
	
	
	@SuppressLint("InlinedApi")
	private void importSdnContact() {
		 if (!SystemProperties.get("ro.hq.sdn").equals("1")) {
			 return;
		 }
		caculateNameAndNumber();
		Log.i(TAG, "isRunningNumberCheck before: " + sIsRunningNumberCheck);
		if (sIsRunningNumberCheck) {
			return;
		}
		sIsRunningNumberCheck = true;
		Log.i(TAG, "ALL current name is " + CurrentName);
		for (int i = 0; i < CurrentNumber.length; i++) {
			Log.i(TAG, "isRunningNumberCheck after: " + sIsRunningNumberCheck);
//			Uri uri1=Uri.parse("contacts;com.android.view_raw_contacts");
//			Uri uri = Uri.withAppendedPath(uri1,
//					Uri.encode(CurrentNumber[i]));
//			Log.i(TAG, "getContactInfoByPhoneNumbers(), uri = " + uri);
			Log.i(TAG, "current name is " + CurrentName[i]);
			/*				Cursor contactCursor = ContactsApplication
					.getContactsDb()
					.rawQuery(
							"select  display_name from view_raw_contacts where deleted = 0  and  display_name = '"+CurrentName[i]+"'",null);
			
			测试使用code  
			如果用户删除了就应该不再预置了
*/		
			Cursor contactCursor = ContactsApplication
			.getContactsDb()
			.rawQuery(
					"select  display_name from view_raw_contacts where  display_name = '"+CurrentName[i]+"'",null);

			
			try {
				if (contactCursor != null && contactCursor.getCount() > 0) {
					while(contactCursor.moveToNext()){
						String name =contactCursor.getString(0);
						Log.i(TAG, "查询到的联系人是"+name);
						
					}
					Log.i(TAG, "cursor is null ! 存在需要与之的联系人  直接返回");
					continue;
				} else {
					Log.i(TAG, "开始写入联系人"+CurrentName[i]);
					final ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
					ContentProviderOperation.Builder builder = ContentProviderOperation
							.newInsert(RawContacts.CONTENT_URI);
					ContentValues contactvalues = new ContentValues();
					contactvalues.put(RawContacts.ACCOUNT_NAME,
							AccountType.ACCOUNT_NAME_LOCAL_PHONE);
					contactvalues.put(RawContacts.ACCOUNT_TYPE,
							AccountType.ACCOUNT_TYPE_LOCAL_PHONE);
					contactvalues.put(RawContacts.INDICATE_PHONE_SIM,
							ContactsContract.RawContacts.INDICATE_PHONE);
//					contactvalues.put(RawContacts.IS_SDN_CONTACT, 1);//sdn
					// contactvalues.put(RawContacts.IS_SDN_CONTACT, -2);
					builder.withValues(contactvalues);
					builder.withValue(RawContacts.AGGREGATION_MODE,
							RawContacts.AGGREGATION_MODE_DISABLED);
					operationList.add(builder.build());

					builder = ContentProviderOperation
							.newInsert(Data.CONTENT_URI);
					builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
					builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
					builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
					builder.withValue(Phone.NUMBER, CurrentNumber[i]);
					builder.withValue(Data.IS_PRIMARY, 1);
					operationList.add(builder.build());

					builder = ContentProviderOperation
							.newInsert(Data.CONTENT_URI);
					builder.withValueBackReference(
							StructuredName.RAW_CONTACT_ID, 0);
					builder.withValue(Data.MIMETYPE,
							StructuredName.CONTENT_ITEM_TYPE);
					builder.withValue(StructuredName.DISPLAY_NAME, CurrentName[i]);
					operationList.add(builder.build());

					try {
						mContext.getContentResolver().applyBatch(
								ContactsContract.AUTHORITY, operationList);
					} catch (RemoteException e) {
						Log.e(TAG,
								String.format("%s: %s", e.toString(),
										e.getMessage()));
					} catch (OperationApplicationException e) {
						Log.e(TAG,
								String.format("%s: %s", e.toString(),
										e.getMessage()));
					}

				}
			} finally {
				// when this service start,but the contactsprovider has not been
				// started yet.
				// the contactCursor perhaps null, but not always.(first load
				// will weekup the provider)
				// so add null block to avoid nullpointerexception
				if (contactCursor != null) {
					contactCursor.close();
				}
			}// for
			Log.i(TAG, "isRunningNumberCheck insert: " + sIsRunningNumberCheck);
			sIsRunningNumberCheck = false;
		}
	}

	private void caculateNameAndNumber() {
		if (MccMnc.equals(AGENTINA_MCCMNC1) || MccMnc.equals(AGENTINA_MCCMNC2)) {// 阿根廷
		// if(true){
			CurrentName = AGENTINA_INSERT_PRESET_NAME;
			CurrentNumber = AGENTINA_INSERT_PRESET_NUMBER;
			// deleteNoUseNumber(BRAZIL_OI_INSERT_PRESET_NAME);
			// deleteNoUseNumber(BRAZIL_NORMAL_INSERT_PRESET_NAME);
			deleteNoUseNumber(Falabella_Entel_INSERT_PRESET_NAME);
			deleteNoUseNumber(CHILE_Entel_INSERT_PRESET_NAME);
		}/*
		 * else if (MccMnc.equals(BRAZIL_OI_MCCMNC1)// 巴西oi 不再关注 ||
		 * MccMnc.equals(BRAZIL_OI_MCCMNC2) || MccMnc.equals(BRAZIL_OI_MCCMNC3))
		 * { CurrentName = BRAZIL_OI_INSERT_PRESET_NAME; CurrentNumber =
		 * BRAZIL_OI_INSERT_PRESET_NUMBER;
		 * 
		 * deleteNoUseNumber(AGENTINA_INSERT_PRESET_NAME);
		 * deleteNoUseNumber(BRAZIL_NORMAL_INSERT_PRESET_NAME);
		 * deleteNoUseNumber(Falabella_Entel_INSERT_PRESET_NAME);
		 * deleteNoUseNumber(CHILE_Entel_INSERT_PRESET_NAME); } else if
		 * (MccMnc.length()>=2&&MccMnc.substring(0, 2).startsWith("724")) {//
		 * 非oi巴西 不再关注 CurrentName = BRAZIL_NORMAL_INSERT_PRESET_NAME;
		 * CurrentNumber = BRAZIL_NORMAL_INSERT_PRESET_NUMBER;
		 * 
		 * deleteNoUseNumber(AGENTINA_INSERT_PRESET_NAME);
		 * deleteNoUseNumber(BRAZIL_OI_INSERT_PRESET_NAME);
		 * deleteNoUseNumber(Falabella_Entel_INSERT_PRESET_NAME);
		 * deleteNoUseNumber(CHILE_Entel_INSERT_PRESET_NAME);
		 * 
		 * 
		 * }
		 */else if (PhoneNumberUtils
				.getDefaultEfspn(PhoneNumberUtils.getSlotId()).toUpperCase()
				.equals("FALABELLA")) {// 智利FALABELLA
			CurrentName = Falabella_Entel_INSERT_PRESET_NAME;
			CurrentNumber = Falabella_Entel_INSERT_PRESET_NUMBER;

			deleteNoUseNumber(AGENTINA_INSERT_PRESET_NAME);
			// deleteNoUseNumber(BRAZIL_OI_INSERT_PRESET_NAME);
			// deleteNoUseNumber(BRAZIL_NORMAL_INSERT_PRESET_NAME);
			deleteNoUseNumber(CHILE_Entel_INSERT_PRESET_NAME);

		} else if (MccMnc.equals(CHILE_Entel_MCCMNC1)// 智利entel
				|| MccMnc.equals(CHILE_Entel_MCCMNC2)) {
			CurrentName = CHILE_Entel_INSERT_PRESET_NAME;
			CurrentNumber = CHILE_Entel_INSERT_PRESET_NUMBER;

			deleteNoUseNumber(AGENTINA_INSERT_PRESET_NAME);
			// deleteNoUseNumber(BRAZIL_OI_INSERT_PRESET_NAME);
			// deleteNoUseNumber(BRAZIL_NORMAL_INSERT_PRESET_NAME);
			deleteNoUseNumber(Falabella_Entel_INSERT_PRESET_NAME);

		} else {
			Log.i(TAG, "the mccMnc is no match");
			deleteNoUseNumber(AGENTINA_INSERT_PRESET_NAME);
			// deleteNoUseNumber(BRAZIL_OI_INSERT_PRESET_NAME);
			// deleteNoUseNumber(BRAZIL_NORMAL_INSERT_PRESET_NAME);
			deleteNoUseNumber(Falabella_Entel_INSERT_PRESET_NAME);
			deleteNoUseNumber(CHILE_Entel_INSERT_PRESET_NAME);
		}
	}

	/**
	 * ATT版本yuzhi联系人
【预置条件】
"SDN预置到电话本中:
Asistencia        *911
Atención a clientes      *611
Compra Accesorios  *311
Compra Tiempo Aire  *2473
Buzón de Voz        *86
Asist Ejecutiva    *73273
Info General        *8636
Mientras Contesto    *6262
Info Entretenimiento *386
Dominos Pizza      *3030
Soporte Serv 3G    *34
Compra TV Movil    *88
Seguridad Publica  060
Cruz Roja          065
PFP                088 
Bomberos          068
Policia Judicial    061
Buzón de Voz        186
Buzón de Voz        #9
Buzón de Voz        +528187619000
SDN号码可编辑，可删除"
	 */
	private void importAttSdnContact() {

		LogUtils.w(TAG,
				"是否是att版本？ " + android.os.SystemProperties.get("ro.hq.att.sdn"));
		if (!android.os.SystemProperties.get("ro.hq.att.sdn").equals("1")) {
			return;
		}

		String AttName[] = { "Asistencia", "Atn Clientes",
				"Compra Accesorios", "Compra Tiempo Aire", "Buzón de Voz",
				"Asist Ejecutiva", "Info General", "Mientras Contesto",
				"Info Entretenimiento", "Dominos Pizza", "Soporte Serv 3G",
				"Compra TV Movil", "Seguridad Publica", "Cruz Roja", "PFP",
				"Bomberos", "Policia Judicial", "Buzón de Voz", "Buzón de Voz", "Buzón de Voz" };/*modify by zhaizhanfeng for HQ01590895 at 151230*/
		String AttNumber[] = { "*911", "*611", "*311", "*2473", "*86",
				"*73273", "*8636", "*6262", "*386", "*3030", "*34", "*88",
				"060", "065", "088", "068", "061", "186", "#9", "+528187619000" };

		Log.i(TAG, "importAttSdnContact");
		for (int i = 0; i < AttName.length; i++) {
			Log.i(TAG, "current att name is " + AttName[i]);
			// 测试使用code---------------------
/*			Cursor contactCursor = ContactsApplication
					.getContactsDb()
					.rawQuery(
							"select  display_name from view_raw_contacts where deleted = 0  and  display_name = '"
									+ AttName[i] + "'", null);
*/
			// 如果用户删除了就应该不再预置了---------------------

			Cursor contactCursor = ContactsApplication.getContactsDb()
					.rawQuery(
							"select  display_name from view_raw_contacts where  display_name = '"
									+ AttName[i] + "'", null);

			try {
				/*if (contactCursor != null && contactCursor.getCount() > 0) {
					while (contactCursor.moveToNext()) {
						String name = contactCursor.getString(0);
						Log.i(TAG, "查询到的联系人是" + name);

					}
					Log.i(TAG, "ATT cursor is null ! 存在需要与之的联系人  直接返回");
					continue;
				} else {  modify by zhaizhanfeng for HQ01590895 at 151230 */
					Log.i(TAG, "ATT开始写入联系人" + AttName[i]);
					final ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
					ContentProviderOperation.Builder builder = ContentProviderOperation
							.newInsert(RawContacts.CONTENT_URI);
					ContentValues contactvalues = new ContentValues();
					contactvalues.put(RawContacts.ACCOUNT_NAME,
							AccountType.ACCOUNT_NAME_LOCAL_PHONE);
					contactvalues.put(RawContacts.ACCOUNT_TYPE,
							AccountType.ACCOUNT_TYPE_LOCAL_PHONE);
					contactvalues.put(RawContacts.INDICATE_PHONE_SIM,
							ContactsContract.RawContacts.INDICATE_PHONE);
					builder.withValues(contactvalues);
					builder.withValue(RawContacts.AGGREGATION_MODE,
							RawContacts.AGGREGATION_MODE_DISABLED);
					operationList.add(builder.build());

					builder = ContentProviderOperation
							.newInsert(Data.CONTENT_URI);
					builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
					builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
					builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
					builder.withValue(Phone.NUMBER, AttNumber[i]);
					builder.withValue(Data.IS_PRIMARY, 1);
					operationList.add(builder.build());
					/*if (AttName[i].equals("Buzón de Voz")) {
						String BuzónAttNumber[] = { "186", "#9",
								"+528187619000" };

						for (int n = 0; n < BuzónAttNumber.length; n++) {
							builder = ContentProviderOperation
									.newInsert(Data.CONTENT_URI);
							builder.withValueBackReference(
									Phone.RAW_CONTACT_ID, 0);
							builder.withValue(Data.MIMETYPE,
									Phone.CONTENT_ITEM_TYPE);
							builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
							builder.withValue(Phone.NUMBER, BuzónAttNumber[n]);
							// builder.withValue(Data.IS_PRIMARY, 1);
							operationList.add(builder.build());

						}
					}  modify by zhaizhanfeng for HQ01590895 at 151230 */

					builder = ContentProviderOperation
							.newInsert(Data.CONTENT_URI);
					builder.withValueBackReference(
							StructuredName.RAW_CONTACT_ID, 0);
					builder.withValue(Data.MIMETYPE,
							StructuredName.CONTENT_ITEM_TYPE);
					builder.withValue(StructuredName.DISPLAY_NAME, AttName[i]);
					operationList.add(builder.build());

					try {
						mContext.getContentResolver().applyBatch(
								ContactsContract.AUTHORITY, operationList);
					} catch (RemoteException e) {
						Log.e(TAG,
								String.format("%s: %s", e.toString(),
										e.getMessage()));
					} catch (OperationApplicationException e) {
						Log.e(TAG,
								String.format("%s: %s", e.toString(),
										e.getMessage()));
					}

				//}
			} finally {
				if (contactCursor != null) {
					contactCursor.close();
				}
			}
		}
	}

	private SharedPreferences SdnImportResultPreferences;
	private  SharedPreferences.Editor editor;
	public static final String SDN_IMPORT_PREF="SdnImportResultPreferences";
	public static final String SDN_IMPORT_PREF_RESULT_KEY="SdnImportResultPreferences_key";
	
	
	@Override
	public void success() {
		// TODO Auto-generated method stub
		SdnImportResultPreferences=mContext.getSharedPreferences(SDN_IMPORT_PREF, Context.MODE_PRIVATE);
		editor=SdnImportResultPreferences.edit();
		editor.putBoolean(SDN_IMPORT_PREF_RESULT_KEY, true);
		editor.commit();
		Toast.makeText(mContext, "预置联系人OK ！!!", Toast.LENGTH_LONG).show();
	}

	@Override
	public void fail() {
		// TODO Auto-generated method stub
		Toast.makeText(mContext, "预置联系人失败！。。。。", Toast.LENGTH_LONG).show();
	}

}
