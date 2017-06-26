package com.mediatek.contacts;

import android.app.Application;
import android.content.Context;

import com.mediatek.common.MPlugin;

import com.mediatek.contacts.ext.*;
import com.mediatek.contacts.util.LogUtils;

import java.util.HashMap;

public final class ExtensionManager {
    private static final String TAG = ExtensionManager.class.getSimpleName();
    private static ExtensionManager sInstance = null;
    private static Context sContext = null;

    /**
     * Map the plugin instance by ExtensionManager
     */
    private static HashMap<Class, Object> sPluginMap = new HashMap<Class, Object>();

    /**
     * Map the default plugin implements object
     */
    private static HashMap<Class, Object> sDefaulthPluginMap = new HashMap<Class, Object>();
    static {
        sDefaulthPluginMap.put(IContactAccountExtension.class, new DefaultContactAccountExtension());
        sDefaulthPluginMap.put(ICtExtension.class, new DefaultCtExtension());
        sDefaulthPluginMap.put(IOp01Extension.class, new DefaultOp01Extension());
        sDefaulthPluginMap.put(IContactListExtension.class, new DefaultContactListExtension());
        sDefaulthPluginMap.put(IIccCardExtension.class, new DefaultIccCardExtension());
        sDefaulthPluginMap.put(IImportExportExtension.class, new DefaultImportExportExtension());
        sDefaulthPluginMap.put(IViewCustomExtension.class, new DefaultViewCustomExtension());
        sDefaulthPluginMap.put(ISimServiceExtension.class, new DefaultSimServiceExtension());
        sDefaulthPluginMap.put(IAasExtension.class, new DefaultAasExtension());
        sDefaulthPluginMap.put(ISneExtension.class, new DefaultSneExtension());
    }

    private ExtensionManager() {
        //do-nothing
    }

    public static ExtensionManager getInstance() {
        if (sInstance == null) {
            createInstanceSynchronized();
        }
        return sInstance;
    }

    private static synchronized void createInstanceSynchronized() {
        if (sInstance == null) {
            sInstance = new ExtensionManager();
        }
    }

    /**
     *
     * @param iclass interface class
     * @return plugin object created
     */
    private static <I> Object getExtension(Class<I> iclass) {
        if (sPluginMap.get(iclass) != null) {
            return sPluginMap.get(iclass);
        }
        synchronized (iclass) {
            if (sPluginMap.get(iclass) != null) {
                return sPluginMap.get(iclass);
            }
            //check if the object create is instance of type I.
            I instance = (I) createPluginObject(iclass, sDefaulthPluginMap.get(iclass));
            sPluginMap.put(iclass, instance);
        }
        return sPluginMap.get(iclass);
    }

    private static <T> T createPluginObject(Class cls, Object defaultObject) {
        T pluginObject = (T) MPlugin.createInstance(cls.getName(), sContext);
        return pluginObject != null ? pluginObject : (T) defaultObject;
    }

    public static void registerApplicationContext(Application application) {
        sContext = application.getApplicationContext();
    }

    /**
     * used for test case.
     * reset all plugins,only use default plugin
     */
    public static void resetPlugins() {
        for (Class key : sDefaulthPluginMap.keySet()) {
            sPluginMap.put(key, sDefaulthPluginMap.get(key));
        }
    }

   //-------------------below are getXxxExtension() methonds-----------------//

    public static IContactAccountExtension getContactAccountExtension() {
        IContactAccountExtension extension = (IContactAccountExtension) getExtension(IContactAccountExtension.class);
        return extension;
    }

    public static ICtExtension getCtExtension() {
        ICtExtension extension = (ICtExtension) getExtension(ICtExtension.class);
        return extension;
    }

    public static IOp01Extension getOp01Extension() {
        IOp01Extension extension = (IOp01Extension) getExtension(IOp01Extension.class);
        return extension;
    }

    public static IContactListExtension getContactListExtension() {
        IContactListExtension extension = (IContactListExtension) getExtension(IContactListExtension.class);
        return extension;
    }

    public static IIccCardExtension getIccCardExtension() {
        IIccCardExtension extension = (IIccCardExtension) getExtension(IIccCardExtension.class);
        return extension;
    }

    public static IImportExportExtension getImportExportExtension() {
        IImportExportExtension extension = (IImportExportExtension) getExtension(IImportExportExtension.class);
        return extension;
    }

    public static IViewCustomExtension getViewCustomExtension() {
        IViewCustomExtension extension = (IViewCustomExtension) getExtension(IViewCustomExtension.class);
        return extension;
    }

    public static ISimServiceExtension getSimServiceExtension() {
        ISimServiceExtension extension = (ISimServiceExtension) getExtension(ISimServiceExtension.class);
        return extension;
    }

    public static IAasExtension getAasExtension() {
        IAasExtension extension = (IAasExtension) getExtension(IAasExtension.class);
        return extension;
    }

    public static ISneExtension getSneExtension() {
        ISneExtension extension = (ISneExtension) getExtension(ISneExtension.class);
        return extension;
    }

}
