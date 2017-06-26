package com.gsma.services.nfc;


import java.nio.ByteBuffer;
import java.util.List;
import java.util.LinkedList;
import java.util.List;
import java.lang.Exception;
import java.lang.String;
import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;
import android.util.Xml;
import android.util.Log;
import android.util.AttributeSet;
import android.provider.Settings;
import android.os.UserHandle;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.ComponentName;
import android.nfc.NfcAdapter;
import android.nfc.INfcAdapterGsmaExtras;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.cardemulation.OffHostApduService;

public class NfcController {

    public interface Callbacks {
        /* Card Emulation mode has been disabled */
        static final int CARD_EMULATION_DISABLED = 0;
        /* Card Emulation mode has been enabled */
        static final int CARD_EMULATION_ENABLED = 1;
        /* An error occurred when handset tried to enable/disable Card Emulation mode */
        static final int CARD_EMULATION_ERROR = -1;

        @Deprecated /* When Host Card Emulation (HCE) is supported */
        void onCardEmulationMode(int status);
        /* Called when process for enabling the NFC Controller is finished. */
        void onEnableNfcController(boolean success);
        /* Called when process for getting the default Controller is finished. */
        void onGetDefaultController(NfcController controller);
    }
    static final boolean DBG = true;
    static final String TAG = "NfcController";
    
    private static Context mContext;
    private static NfcController sSingleton;
    private static Callbacks mCallback;
    private NfcAdapter mNfcAdapter;
    private String mPackageName;
    private BroadcastReceiver mNfcReceiver;
    private List<OffHostService> mOffHostServices = new LinkedList<OffHostService>();


    public OffHostService defineOffHostService(String description, String SEName) {
        OffHostService service = new OffHostService(description, SEName, mContext);
        mOffHostServices.add(service);
        return service;
    }

    public void deleteOffHostService(OffHostService service) {
        if (service == null) {
            throw new IllegalArgumentException("Service is not enabled");
        }
        
        mOffHostServices.remove(service);
    }

    /* Asks the system to disable the Card Emulation mode.
     * Change is not persistent and SHALL be overridden by the following events:
     * Turning OFF and ON the NFC Controller
     * Full power cycle of the handset
     *
     * @param cb - Callback interface
     */
    @Deprecated /* When Host Card Emulation (HCE) is supported */
    public void disableCardEmulationMode(NfcController.Callbacks cb){  
        if (mContext == null)
            throw new IllegalStateException("NFC Controller is not enabled");

        // TODO: check app SE permission

        Log.i(TAG, "disableCardEmulationMode()");
        mCallback = cb;        
            
        PackageManager pm = mContext.getPackageManager();
        boolean isHceCapable = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
        if (isHceCapable) {
            mCallback.onCardEmulationMode(Callbacks.CARD_EMULATION_ERROR);
        } else {
            Settings.Global.putString(mContext.getContentResolver(), Settings.Global.NFC_MULTISE_ACTIVE, "Off");
            mCallback.onCardEmulationMode(Callbacks.CARD_EMULATION_DISABLED);
        }
    }
    
    /* Asks the system to enable the Card Emulation mode.
     * Change is not persistent and SHALL be overridden by the following events:
     * Turning OFF and ON the NFC Controller
     * Full power cycle of the handset
     *
     * @param cb - Callback interface
     */
    @Deprecated /* When Host Card Emulation (HCE) is supported */
    public void enableCardEmulationMode(NfcController.Callbacks cb){  
        if (mContext == null)
            throw new IllegalStateException("NFC Controller is not enabled");

        // TODO: check app SE permission

        Log.i(TAG, "enableCardEmulationMode()");
        mCallback = cb;
        int seNum = Settings.Global.getInt(
            mContext.getContentResolver(),  "nfc_multise_active_num", 0);
            
        PackageManager pm = mContext.getPackageManager();
        boolean isHceCapable = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
        if (isHceCapable) {
            mCallback.onCardEmulationMode(Callbacks.CARD_EMULATION_ENABLED);
        } else {
            if (seNum > 0) {
                mCallback.onCardEmulationMode(Callbacks.CARD_EMULATION_ENABLED);
            } else {
                mCallback.onCardEmulationMode(Callbacks.CARD_EMULATION_ERROR);
            }                
        }
    }

    /* Asks the system to enable the NFC Controller. User input is required to enable NFC.
     * A question will be asked if the user wants to enable NFC or not.
     *
     * @param cb - Callback interface
     */
    public void enableNfcController(NfcController.Callbacks cb) {
        final ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        String runningPackage = null;
        List<RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (tasks.size() > 0) {
            runningPackage = tasks.get(0).topActivity.getPackageName();
            mPackageName = runningPackage;
        }        

        if (!mNfcAdapter.isEnabled()) {

            Intent intent = new Intent("com.mediatek.nfc.gsmahandset.ENABLE_COMFIRM");
            intent.putExtra("com.mediatek.nfc.gsmahandset.packagename", runningPackage);
            intent.setFlags(intent.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);	
            
            registerEnableNfcEvent(mContext);
    		
        } else {
            mCallback.onEnableNfcController(true);
        }        
    }
    
    /* Helper for getting an instance of the NFC Controller.
     *
     * @param context - Calling application's context
     * @param cb - Callback interface
     */    
    public static void getDefaultController(Context context,
        NfcController.Callbacks cb) {
        Log.i(TAG, "getDefaultController()");
        mCallback = cb;
        mContext = context;

        NfcController controller = createSingleton(context);
        if (controller != null) {
            mCallback.onGetDefaultController(controller);
        } else {
            mCallback.onGetDefaultController(null);
        }
    }

    public OffHostService getDefaultOffHostService() {
        return null;
    }

    public OffHostService[] getOffHostServices() {
        return mOffHostServices.toArray(new OffHostService[mOffHostServices.size()]);        
    }

    public boolean isEnabled() {
        Log.i(TAG, "isEnabled()");
        if (mNfcAdapter != null) {
            return mNfcAdapter.isEnabled();
        }
        return false;
    }
    
    /* Check if the Card Emulation mode is enabled or disabled.
     *
     */ 
    @Deprecated /* When Host Card Emulation (HCE) is supported */   
    public boolean isCardEmulationEnabled() {
        Log.i(TAG, "isCardEmulationEnabled()");
            
        PackageManager pm = mContext.getPackageManager();
        boolean isHceCapable = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
        if (isHceCapable) {
            return true;
        } else {
            int seNum = Settings.Global.getInt(
                mContext.getContentResolver(),  "nfc_multise_active_num", 0);
            if (seNum > 0) {
                return true;
            }            
        }
        return false;
    }


    private NfcController(Context context) {
        mNfcAdapter = NfcAdapter.getDefaultAdapter(context);
        if (mNfcAdapter == null) {
            Log.e(TAG, "NFC adapter not present.");
        }     
    }

    private static NfcController createSingleton(Context context) {
        Log.i(TAG, "createSingleton()");
        if (sSingleton == null) {
            sSingleton = new NfcController(context);
        }

        return sSingleton;
    }

    private void addStaticOffHostService(PackageManager pm, ServiceInfo si) throws 
        XmlPullParserException, IOException {

        XmlResourceParser parser = null;
        try {
            parser = si.loadXmlMetaData(pm, OffHostApduService.SERVICE_META_DATA);
            if (parser == null) {
                throw new XmlPullParserException("No " + OffHostApduService.SERVICE_META_DATA +
                        " meta-data");
            }    
            
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_DOCUMENT) {
                eventType = parser.next();
            }

            String tagName = parser.getName();
            if (!"offhost-apdu-service".equals(tagName)) {
                throw new XmlPullParserException(
                        "Meta-data does not start with <offhost-apdu-service> tag");
            }

            Resources res = pm.getResourcesForApplication(si.applicationInfo);
            AttributeSet attrs = Xml.asAttributeSet(parser);

            TypedArray sa = res.obtainAttributes(attrs,
                    com.android.internal.R.styleable.OffHostApduService);
            String description = sa.getString(
                    com.android.internal.R.styleable.OffHostApduService_description);
            int bannerResourceId = sa.getResourceId(
                    com.android.internal.R.styleable.OffHostApduService_apduServiceBanner, -1);
            sa.recycle();

            OffHostService staticService = defineOffHostService(description, "UICC");

            final int depth = parser.getDepth();
            AidGroup currentGroup = null;

            // Parsed values for the current AID group
            while (((eventType = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                    && eventType != XmlPullParser.END_DOCUMENT) {
                tagName = parser.getName();
                if (eventType == XmlPullParser.START_TAG && "aid-group".equals(tagName) &&
                        currentGroup == null) {
                    final TypedArray groupAttrs = res.obtainAttributes(attrs,
                           com.android.internal.R.styleable.AidGroup);
                    // Get category of AID group
                    String groupCategory = groupAttrs.getString(
                            com.android.internal.R.styleable.AidGroup_category);
                    String groupDescription = groupAttrs.getString(
                            com.android.internal.R.styleable.AidGroup_description);
                    if (!CardEmulation.CATEGORY_PAYMENT.equals(groupCategory)) {
                        groupCategory = CardEmulation.CATEGORY_OTHER;
                    }
                    groupAttrs.recycle();
                    currentGroup = staticService.defineAidGroup(groupDescription, groupCategory);
                } else if (eventType == XmlPullParser.END_TAG && "aid-group".equals(tagName) &&
                        currentGroup != null) {
                    if (currentGroup.getGroupAidList().size() == 0) {
                        Log.e(TAG, "Not adding <aid-group> with empty or invalid AIDs");
                    }
                    currentGroup = null;                    
                } else if (eventType == XmlPullParser.START_TAG && "aid-filter".equals(tagName) &&
                        currentGroup != null) {
                    final TypedArray a = res.obtainAttributes(attrs,
                            com.android.internal.R.styleable.AidFilter);
                    String aid = a.getString(com.android.internal.R.styleable.AidFilter_name).
                            toUpperCase();
                    if (CardEmulation.isValidAid(aid) && !currentGroup.getGroupAidList().contains(aid)) {
                        currentGroup.addNewAid(aid);
                    } else {
                        Log.e(TAG, "Ignoring invalid or duplicate aid: " + aid);
                    }
                    a.recycle();
                } 
            }
            
            
        } catch (NameNotFoundException e) {
            throw new XmlPullParserException("Unable to create context for: " + si.packageName);
        } finally {
            if (parser != null) parser.close();
        }        
    }

    private void loadStaticOffHostService(Context context) {
        PackageManager pm;
        try {
            pm = context.createPackageContextAsUser("android", 0,
                    new UserHandle(ActivityManager.getCurrentUser())).getPackageManager();
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Could not create user package context");
            return;
        }

        List<ResolveInfo> resolvedOffHostServices = pm.queryIntentServicesAsUser(
                new Intent(OffHostService.SERVICE_INTERFACE),
                PackageManager.GET_META_DATA, ActivityManager.getCurrentUser()); 


        List<ResolveInfo> pkgOffHostServices = new LinkedList<ResolveInfo>();

        for (ResolveInfo resolvedOffHostService : resolvedOffHostServices) {
            try {
                    ServiceInfo si = resolvedOffHostService.serviceInfo;
                    if (si.packageName.equals(context.getPackageName())) {
                        
                        ComponentName componentName = new ComponentName(si.packageName, si.name);
                        // Check if the package holds the NFC permission
                        if (pm.checkPermission(android.Manifest.permission.NFC, si.packageName) !=
                                PackageManager.PERMISSION_GRANTED) {
                            Log.e(TAG, "Skipping APDU service " + componentName +
                                    ": it does not require the permission " +
                                    android.Manifest.permission.NFC);
                            continue;
                        }
                        if (!android.Manifest.permission.BIND_NFC_SERVICE.equals(
                                si.permission)) {
                            Log.e(TAG, "Skipping APDU service " + componentName +
                                    ": it does not require the permission " +
                                    android.Manifest.permission.BIND_NFC_SERVICE);
                            continue;
                        }
                        addStaticOffHostService(pm, si);
                    }
                    
                } catch (XmlPullParserException e) {
                    Log.w(TAG, "Unable to load component info " + resolvedOffHostService.toString(), e);
                } catch (IOException e) {
                    Log.w(TAG, "Unable to load component info " + resolvedOffHostService.toString(), e);
                }
            }
    }

    private void registerEnableNfcEvent(Context context) {
        Log.v(TAG, "registerEnableNfcEvent");

        IntentFilter intentFilter = new IntentFilter("com.gsma.services.nfc.REQUEST_ENABLE_NFC");
        mNfcReceiver = new BroadcastReceiver() {
    		@Override
    		public void onReceive(Context context, Intent intent) {
    		    unregisterEnableNfcEvent(mContext);
                
                final String action = intent.getAction();
                Log.d(TAG, "mNfcReceiver onReceive(), action =" + action);
                if (action.equals("com.gsma.services.nfc.REQUEST_ENABLE_NFC")) {
                    final String enable = intent.getStringExtra("com.mediatek.nfc.gsmahandset.enable");
                    if (enable.equals("YES")) {
                        Log.i(TAG, "GSMA : NFC enabled");                   
                        final String packageName = intent.getStringExtra("com.mediatek.nfc.gsmahandset.packagename");                    
                        if (mNfcAdapter != null && packageName.equals(mPackageName)) {
                            if(mNfcAdapter.enable() == false) {
                                throw new IllegalStateException("NFC can't be enabled");
                            } else {
                                mCallback.onEnableNfcController(true);
                            }                           
                        }
                    } else {
                        mCallback.onEnableNfcController(false);
                    }
                }        
            }
        };

        context.registerReceiver(mNfcReceiver, intentFilter);
    }    
    
    private void unregisterEnableNfcEvent(Context context) {
        if(mNfcReceiver!= null) {
            Log.v(TAG, "unregisterEnableNfcEvent");
            context.unregisterReceiver(mNfcReceiver);
            mNfcReceiver = null;
        }
     }    
}
