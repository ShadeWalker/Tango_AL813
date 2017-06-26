package com.gsma.services.nfc;



import java.util.List;
import java.util.ArrayList;

import java.util.Iterator;

import java.lang.IllegalArgumentException;
import java.lang.String;

//import android.content.Context;
import android.util.Log;


public class AidGroup {

    static final String TAG = "gsma.AidGroup";

    //private Context mContext;
    private String mCategory;
    private String mDescription;

    private List<String> mAidList = new ArrayList<String>();
        
    //constructor
    private AidGroup(String description,String category) {
        Log.d(TAG, "AidGroup()  description:"+description+"  category:"+category);

        //mContext = context;
        mCategory = category;
        mDescription = description;
    }

    public static AidGroup createInstance(String description,String category) {
        return new AidGroup(description,category);        
    }
    
    //public static GsmaProxy getInstance() {
    //    return mStaticInstance;
    //}


    /**
    *   getCategory()
    *
    *     Return the category of the group of AIDs.
    *
    *     Returns:
    *         Category of the group of AIDs:
    *            android.nfc.cardemulation.CardEmulation.CATEGORY_PAYMENT 
    *            android.nfc.cardemulation.CardEmulation.CATEGORY_OTHER
    *   
    * @param  null
    * @return   String   Return the category of the group of AIDs.
    * @see         null
    */
    public String getCategory() {
        Log.d(TAG, "getCategory()  mCategory:"+mCategory);
        return mCategory;//"android.nfc.cardemulation.CardEmulation.CATEGORY_PAYMENT";
    }

    /**
    *   getDescription()
    *
    *    Return the description of the group of AIDs.
    *    Returns:
    *     The description of the group of AIDs
    *   
    * @param  null
    * @return   String    Return the description of the group of AIDs.
    * @see         null
    */    
    public String getDescription(){
        Log.d(TAG, "getDescription()   mDescription:"+mDescription);
        return mDescription;
    }

    /**
    *   addNewAid()
    *
    *        Add a new AID to the current group.
    *
    *        Parameters:
    *           Aid - Application IDentifier to add to the current group 
    *
    *        Throws: 
    *           java.lang.IllegalArgumentException - 
    *           Indicate that a method has been passed an illegal or inappropriate argument.
    *   
    * @param  String    Application IDentifier to add to the current group 
    * @return   null  
    * @see         null
    */
    public void addNewAid(String Aid) {
        Log.d(TAG, "addNewAid()  Aid:"+Aid);
    
        Log.d(TAG, "    mAidList.size():"+mAidList.size());
        mAidList.add(Aid);

        // TODO:: throw IllegalArgumentException
    }

    /**
    *   removeAid()
    *
    *   Remove an AID from the current group.
    *
    *   Parameters:
    *            Aid - Application IDentifier to remove from the current group 
    *
    *   Throws: 
    *            java.lang.IllegalArgumentException - 
    *            Indicate that a method has been passed an illegal or inappropriate argument.
    *        
    *   
    * @param  String      AID to remove from the current group 
    * @return   null
    * @see         null
    */
    public void removeAid(String Aid){
        Log.d(TAG, "removeAid()  Aid:"+Aid);
        Log.d(TAG, "mAidList.size():"+mAidList.size());

        Iterator itr = mAidList.iterator();
        while (itr.hasNext()) {
            String aidListElement = (String) itr.next();
            if(aidListElement.equalsIgnoreCase(Aid)){
                Log.d(TAG, "remove mAidList Element:" + aidListElement);
                itr.remove(); 
                return;
            }
        }

        
        Log.d(TAG, "removeAid() not match throw IllegalArgumentException, aid:" + Aid);
        throw new IllegalArgumentException("aid not find :"+Aid);


       

    }
	
    public List<String> getGroupAidList(){
        Log.d(TAG, "getGroupAid()  mAidList.size():"+mAidList.size());

        int i = 0;
        Iterator itr = mAidList.iterator();
        while (itr.hasNext()) {
            String aidListElement = (String) itr.next();
            Log.d(TAG, " item:"+i+"  aidListElement :"+aidListElement);
            i++;
        }
        
        return mAidList;
    }


}
