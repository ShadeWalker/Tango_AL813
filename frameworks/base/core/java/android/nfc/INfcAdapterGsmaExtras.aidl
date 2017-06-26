/*
 * Copyright (C) 2011, The Android Open Source Project
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


package android.nfc;

//import org.si;

/**
 * GSMA interface which dependens on other module
 */
/**
* @hide
*/
interface INfcAdapterGsmaExtras {

    /**
     * Get the active SE.
     */
    int getActiveSeValue();

    /**
     * Set the active SE.
     */
    void setActiveSeValue(int seValue);

    /**
     * CommitRouting.
     */
    void commitRouting();

    /**
     * route Aids.
     */
    void routeAids(String aid);

    /**
     * Query if system has HCE Feature
     */
    boolean isHceCapable();


    /**
     * Enable Multi Event Transaction Broadcast.
     */
    boolean enableMultiEvtTransaction();
    
    

    
    //SEAPI function
    /**
     * is aid allow in the SIM applet
     */
    boolean[] isNFCEventAllowed(String reader, 
        		in byte[] aid,
            in String[] packageNames);
            
    /**
     * Enable SWP.
     */
    boolean setNfcSwpActive(int simID);         
    
}
