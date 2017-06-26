package com.gsma.services.nfc;


import java.nio.ByteBuffer;

import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Iterator;
import java.lang.Exception;
import java.lang.String;

import android.content.Context;
import android.util.Log;

import android.graphics.drawable.Drawable;
import com.gsma.services.utils.InsufficientResourcesException;
import android.nfc.INfcAdapterGsmaExtras;
import android.nfc.NfcAdapter; 

public class OffHostService {

    static final String TAG = "OffHostService";
    private String mDescription;
    private String mSeName;
    private Drawable mBanner;
    private Context mContext;
    private List<AidGroup> mAidGroups = new LinkedList<AidGroup>();
    private INfcAdapterGsmaExtras mGsmaEx;

    public static final String SERVICE_INTERFACE =
            "com.gsma.services.nfc.action.OFF_HOST_SERVICE";

    public static final String SERVICE_META_DATA =
            "com.gsma.services.nfc.off_host_service";
    
    public String getLocation(){
    	return mSeName;
    }
    
    public String getDescription(){
        return mDescription;
    }
    
    public void setBanner(Drawable banner){
        mBanner = banner;
    }
    
    public Drawable getBanner(){
        return mBanner;
    }

    public AidGroup defineAidGroup(String description, String category){
        AidGroup aidGroup = AidGroup.createInstance(description, category);
        mAidGroups.add(aidGroup);
        return aidGroup;
    }
    
    public void deleteAidGroup(AidGroup group){
        mAidGroups.remove(group);
    }
    
    public AidGroup[] getAidGroups(){
         return mAidGroups.toArray(new AidGroup[mAidGroups.size()]); 
    }
    
    public void commit() throws InsufficientResourcesException {
              
        NfcAdapter adapter =  NfcAdapter.getDefaultAdapter(mContext);
        if(adapter == null) {
            throw new InsufficientResourcesException("Cannot get NFC Default Adapter");
        }
        
        mGsmaEx = adapter.getNfcAdapterGsmaExtrasInterface();
        if(mGsmaEx == null)  {
            throw new InsufficientResourcesException("Cannot get NFC Gsma Extra interface");
        }


        Set<String> aidSet = new HashSet<String>();
        List<String> aidList;

        ListIterator<AidGroup> iter = mAidGroups.listIterator();
        ListIterator<String> aidListIter;
        
        while (iter.hasNext()) {
            AidGroup aidGroup = iter.next();
            aidList = aidGroup.getGroupAidList();
            aidListIter = aidList.listIterator();
            
            while (aidListIter.hasNext()) {
                String aid = aidListIter.next();
                aidSet.add(aid);
            }
        } 

        Iterator<String> setIterator = aidSet.iterator();
        
        while (setIterator.hasNext()) {
            String aid = setIterator.next();

            try {
                mGsmaEx.routeAids(aid);
            } catch (Exception e) {
                throw new InsufficientResourcesException("Cannot do routeAids");
            }   
        }

        try {
            mGsmaEx.commitRouting();
        } catch (Exception e) {
            throw new InsufficientResourcesException("Cannot do commitRouting");
        }               
    }


    public OffHostService(String description, String SeName, Context context) {
        mDescription = description;
        mSeName = SeName;
        mContext = context;
    }
}
