package com.mediatek.contacts.simcontact;

import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.mediatek.contacts.model.AccountWithDataSetEx;
import com.mediatek.contacts.simservice.ColumbiaSimContactFormatService;
import com.mediatek.contacts.util.LogUtils;

/**
 * 监听漫游状态 漫游回来呢，todo
 * 
 * @author niubi tang
 * 
 */
public class ManYouReceiver extends BroadcastReceiver {
	private String MccMnc;
	private TelephonyManager telMgr;

	private boolean test;
	private static final String[] SpecialMccMnc = { "732101", "722310",
			"72405", "73003", "74001", "70601", "70401", "708001", "338070",
			"334020", "71021", "71073", "71403", "74402", "71610", "330110",
			"37002", "74810" };
	private static List<AccountWithDataSet> Allaccounts;

	@Override
	public void onReceive(Context context, Intent intent) {
		test = false;
		if (!test) {
			if (!SystemProperties.get("ro.hq.claro.columbia").equals("1")) {
				LogUtils.w("tang", "非拉美carlo版本，哥伦比亚漫游功能关闭");
				return;
			}
			MccMnc = PhoneNumberUtils.getSimMccMnc();
			LogUtils.d("tang", "MccMnc is " + MccMnc);
			if (!MccMnc.startsWith("732")) {
				return;
			}
		} else {
			MccMnc = "732310";
		}

		LogUtils.w("tang", intent.getAction() + "!!!!!");
		if (intent.getAction().equals(
				"android.provider.Telephony.SPN_STRINGS_UPDATED")) {
			LogUtils.w("tang", intent.getAction()
					+ "is android.provider.Telephony.SPN_STRINGS_UPDATED");

			final String spnIntent = intent.getStringExtra("spn");
			final String plmnIntent = intent.getStringExtra("plmn");
			// final String plmn = "748190";//出哥伦比亚
			// final String plmn = "73211";//哥伦比亚local plmn
			LogUtils.d("tang", "spnIntent is " + spnIntent);
			LogUtils.d("tang", "plmnIntent is " + plmnIntent);
			TelephonyManager telManager = (TelephonyManager) context
					.getSystemService(Context.TELEPHONY_SERVICE);
			String plmn;
			if (!test) {
				plmn = telManager.getNetworkOperator();
				if (plmn == null || plmn.equals("")) {
					plmn = plmnIntent;
				}
				if (plmn.equals(MccMnc)) {
					LogUtils.d("tang", "plmn =mcc,非漫游");
					return;
				}
			} else {
				// plmn="732100";
//				plmn = telManager.getNetworkOperator();
				plmn = plmnIntent;
			}
			// if (plmn == null || plmn.equals("Emergency calls only")
			// || plmn.equals("")) {
			// LogUtils.d("tang", "plmn =null !!!!!");
			// return;
			// }
			LogUtils.d("tang", "plmn is " + plmn);

			Intent ServiceIntent = new Intent(context,
					ColumbiaSimContactFormatService.class);
			Allaccounts = loadDeviceAllAccount(context);
			int subID = GetSimCardAccountSubID(0, context);
			ServiceIntent.putExtra("subID", subID);// subid sim卡的 刷新的时候要用 fuck

			Log.i("tang", "the subId is " + subID);
			if (subID == -1) {
				Toast.makeText(context, "subID获取失败", Toast.LENGTH_LONG).show();
			}

			// 1，回到哥伦比亚，则执行恢复操作
			if (!plmn.equals("732101") && plmn.startsWith("732")
					&& MccMnc.startsWith("732")) {// 哥伦比亚的卡，漫游回来
				Log.w("tang", "回到哥伦比亚，准备恢复sim卡联系人");
				ServiceIntent.putExtra("ServiceKind", "Z");
				context.startService(ServiceIntent);
			} else if (plmn.equals("732101") || !plmn.startsWith("732")) {
				// 2，漫游出了哥伦比亚
				// 2.1： 启动sim卡联系人服务,启动服务进行A操作……
				for (String Specialplmn : SpecialMccMnc) {
					if (Specialplmn.equals(plmn)) {
						ServiceIntent.putExtra("ServiceKind", "A");
						context.startService(ServiceIntent);

						Log.i("tang", "匹配的plmn是：" + Specialplmn);
						Log.i("tang", "启动了service A");
						return;
					}
				}
				// 2.2： 不在列表里面，启动服务执行B操作……
				ServiceIntent.putExtra("ServiceKind", "B");
				context.startService(ServiceIntent);
				Log.i("tang", "启动了service B");
			}
			// end
		}
	}

	/**
	 * 获取卡1或卡2的subId
	 * 
	 * @param SlotId
	 * @param context
	 * @return
	 */
	private static int GetSimCardAccountSubID(int SlotId, Context context) {
		List<AccountWithDataSet> accounts = Allaccounts;
		final AccountTypeManager accountTypes = AccountTypeManager
				.getInstance(context);
		for (AccountWithDataSet account : accounts) {
			AccountType accountType = accountTypes.getAccountType(account.type,
					account.dataSet);
			int subId = SubInfoUtils.getInvalidSubId();
			if (account instanceof AccountWithDataSetEx) {
				subId = ((AccountWithDataSetEx) account).getSubId();
				return subId;
			}
		}
		return -1;// 说明有问题啊
	}

	/**
	 * MCC+MNC(mobile country code + mobile network code)<br/>
	 * 注意：仅当用户已在网络注册时有效。<br/>
	 * 在CDMA网络中结果也许不可靠。<br/>
	 * 
	 * @return
	 */
	private String getNetworkOperator(Context context) {
		telMgr = (TelephonyManager) context
				.getSystemService(Context.TELEPHONY_SERVICE);
		return telMgr.getNetworkOperator();
	}

	/**
	 * 获取所有账户信息
	 * 
	 * @param context
	 * @return if not exisit,return null
	 * @author niubi tang
	 */
	private static List<AccountWithDataSet> loadDeviceAllAccount(Context context) {
		final AccountTypeManager accountTypes = AccountTypeManager
				.getInstance(context);
		List<AccountWithDataSet> accounts = accountTypes.getAccounts(true);
		return accounts;
	}
}
