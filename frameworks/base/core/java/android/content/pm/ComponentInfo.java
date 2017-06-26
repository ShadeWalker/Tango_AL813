/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.content.pm;

import java.util.Locale;

import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.util.Printer;
/* < DTS2014102001272 zhangxinming/00181848 20140926 begin */
import android.hwtheme.HwThemeManager;
/*   DTS2014102001272 zhangxinming/00181848 20140926 end > */
import android.os.SystemProperties;
import android.util.Log;
import android.telephony.PhoneNumberUtils;//add by lipeng 
import java.util.Locale;//add by lipeng 
/**
 * Base class containing information common to all application components
 * ({@link ActivityInfo}, {@link ServiceInfo}).  This class is not intended
 * to be used by itself; it is simply here to share common definitions
 * between all application components.  As such, it does not itself
 * implement Parcelable, but does provide convenience methods to assist
 * in the implementation of Parcelable in subclasses.
 */
public class ComponentInfo extends PackageItemInfo {
    /**
     * Global information about the application/package this component is a
     * part of.
     */
    public ApplicationInfo applicationInfo;
    
    /**
     * The name of the process this component should run in.
     * From the "android:process" attribute or, if not set, the same
     * as <var>applicationInfo.processName</var>.
     */
    public String processName;

    /**
     * A string resource identifier (in the package's resources) containing
     * a user-readable description of the component.  From the "description"
     * attribute or, if not set, 0.
     */
    public int descriptionRes;
    
    /**
     * Indicates whether or not this component may be instantiated.  Note that this value can be
     * overriden by the one in its parent {@link ApplicationInfo}.
     */
    public boolean enabled = true;

    /**
     * Set to true if this component is available for use by other applications.
     * Comes from {@link android.R.attr#exported android:exported} of the
     * &lt;activity&gt;, &lt;receiver&gt;, &lt;service&gt;, or
     * &lt;provider&gt; tag.
     */
    public boolean exported = false;
    
    public ComponentInfo() {
    }

    public ComponentInfo(ComponentInfo orig) {
        super(orig);
        applicationInfo = orig.applicationInfo;
        processName = orig.processName;
        descriptionRes = orig.descriptionRes;
        enabled = orig.enabled;
        exported = orig.exported;
    }

    @Override public CharSequence loadLabel(PackageManager pm) {
		
        if (nonLocalizedLabel != null) {
            return nonLocalizedLabel;
        }
        ApplicationInfo ai = applicationInfo;
        CharSequence label;
        if (labelRes != 0) {
            label = pm.getText(packageName, labelRes, ai);
            if (label != null) {
                return label;
            }
        }
        if (ai.nonLocalizedLabel != null) {
            return ai.nonLocalizedLabel;
        }
        if (ai.labelRes != 0) {
            label = pm.getText(packageName, ai.labelRes, ai);
            if (label != null) {
                return label;
            }
        }
        return name;
    }

	public static String getSimLable(int SlotId){
		StringBuilder suffix = new StringBuilder();
		suffix.append("gsm.stkapp.name");
		int mSlotId = SlotId;
		//int mSlotId = PhoneNumberUtils.getSlotId();		
		if( mSlotId == 1 ){
			suffix.append("2");//gsm.stkapp.name2
		}
		String stk = SystemProperties.get(suffix.toString());
		String pMccMnc = PhoneNumberUtils.getSimMccMnc(mSlotId);
		Log.d("stk_lipeng","pMccMnc==>"+"|"+pMccMnc+"|");
		String pImsi = PhoneNumberUtils.getDefaultImsi(mSlotId);
		String language = Locale.getDefault().getLanguage();
		
		if(SystemProperties.get("ro.hq.stk.name").equals("1")){
            //add by lipeng for stk
            if("52501".equals(pMccMnc)||"52502".equals(pMccMnc)||"52507".equals(pMccMnc)){
            	stk = "Singtel Menu";
            }
            else if("73406".equals(pMccMnc)){
            	stk = "tu chip";
            }
            else if("37001".equals(pMccMnc)){
         	   if("es".equals(language) ){
         		  stk = "Servicios Orange";
         	   }else{
         		  stk = "Orange Services";
         	   }
            }
            else if("33403".equals(pMccMnc)||"334030".equals(pMccMnc)||"70403".equals(pMccMnc)
				||"704030".equals(pMccMnc)||"70604".equals(pMccMnc)||"706040".equals(pMccMnc)
				||"71030".equals(pMccMnc)||"710300".equals(pMccMnc)||"714020".equals(pMccMnc)
				||"71402".equals(pMccMnc)||"73002".equals(pMccMnc)||"72207".equals(pMccMnc)
				||"732123".equals(pMccMnc)||"74000".equals(pMccMnc)||"74807".equals(pMccMnc)
				||"71606".equals(pMccMnc)||"73404".equals(pMccMnc)||"21407".equals(pMccMnc)
				||"21402".equals(pMccMnc)||"71204".equals(pMccMnc)){
            	stk = "Movistar";
            }
            else if("70402".equals(pMccMnc)||"70603".equals(pMccMnc)||"70802".equals(pMccMnc)
				||"70802".equals(pMccMnc)||"708020".equals(pMccMnc)){
            	stk = "Menu Tigo";
            }
            else if("732111".equals(pMccMnc)||"73603".equals(pMccMnc)||"74404".equals(pMccMnc)){
            	stk = "Mundo Tigo";
            }
			
            if("732103".equals(pMccMnc)){
            	stk = "Mundo Tigo";
            }
            if(pImsi!=null && pImsi.startsWith("7321030027")||pImsi.startsWith("7321030028")||pImsi.startsWith("7321030029")){
            	stk = "UNE";
            	Log.d("stk_lipeng","pMccMnc==7321030027>"+"|"+pMccMnc+"|");
            }else if(pImsi!=null && pImsi.startsWith("732103018")||pImsi.startsWith("732103019")||pImsi.startsWith("732103023713")||pImsi.startsWith("732103023714")||pImsi.startsWith("732103023715")||pImsi.startsWith("732103023716")||pImsi.startsWith("732103023717")||pImsi.startsWith("732103023718")||pImsi.startsWith("732103023719")||pImsi.startsWith("73210302372")||pImsi.startsWith("73210302373")||pImsi.startsWith("73210302374")||pImsi.startsWith("73210302375")||pImsi.startsWith("732103023760")||pImsi.startsWith("732103023761")){            	
            	stk = "Uff";
            }else if(pImsi!=null && pImsi.startsWith("732103017")){
            	stk = "ETB";
            }else if(pImsi!=null && pImsi.startsWith("732103024")){
            	stk = "UNE";
            }else if(pImsi!=null && pImsi.startsWith("732103054")||pImsi.startsWith("732103055")){
            	stk = "Exito";
            }
            
            if("70401".equals(pMccMnc)||"708001".equals(pMccMnc)||"70601".equals(pMccMnc)
				||"71021".equals(pMccMnc)||"71073".equals(pMccMnc)||"71203".equals(pMccMnc)
				||"71403".equals(pMccMnc)||"37002".equals(pMccMnc)||"74001".equals(pMccMnc)
				||"740010".equals(pMccMnc)||"330110".equals(pMccMnc)){
            	stk = "Claro";
            }
            else if("73401".equals(pMccMnc)||"73402".equals(pMccMnc)||"73403".equals(pMccMnc)){
            	stk = "SIM 412";
            }
            else if("334020".equals(pMccMnc)){
            	stk = "TELCEL";
            }
            else if("72234".equals(pMccMnc)||"722341".equals(pMccMnc)){
            	stk = "Mi Personal";
            }
            else if("72405".equals(pMccMnc)){ 
            	stk = "Menu Claro";
            }
            else if("74801".equals(pMccMnc)){
            	stk = "ZONA MOVIL";
            }
            else if("73005".equals(pMccMnc)||"73008".equals(pMccMnc)){
            	stk = "VTR";
            }
            else if("73003".equals(pMccMnc)){
            	stk = "ClaroChip";
            }
            //334/050,222/01,222/010,222/011,222/012,222/013,222/014
            //Read from SIM first,if no content, STK name should be "Mi SIM"
            if(stk == null && "334050".equals(pMccMnc)||"22201".equals(pMccMnc)
				||"222010".equals(pMccMnc)||"222011".equals(pMccMnc)||"222012".equals(pMccMnc)
				||"222013".equals(pMccMnc)||"222014".equals(pMccMnc)){
            	stk = "Mi SIM";
            }
            //Read Name From SIM firstly,if nothing,show Claro.  716/10:Claro
            if(stk == null && "71610".equals(pMccMnc)){
            	stk = "Claro";
             }
		
		}
		/* end by lipeng for stk name*/
		/* add by caohaolin for claro stk name   
		 * 71203 70601 70401 708001 71021 
		 * 71073 71403 37002 330110 74001 740010 732101 
		 * 74402 74810 73003 71610 722310    */
		if(SystemProperties.get("ro.hq.stk.name.claro").equals("1")) {
				stk = "SIM Claro";
		}
		

		if(SystemProperties.get("ro.hq.stk.name.movistar").equals("1")&& "72207".equals(pMccMnc)||"73002".equals(pMccMnc)
				||"732123".equals(pMccMnc)||"71204".equals(pMccMnc)||"74000".equals(pMccMnc)
				||"70604".equals(pMccMnc)||"706040".equals(pMccMnc)||"70403".equals(pMccMnc)||"704030".equals(pMccMnc)
				||"710300".equals(pMccMnc)||"71030".equals(pMccMnc)||"71402".equals(pMccMnc)
				||"714020".equals(pMccMnc)||"71606".equals(pMccMnc)||"73404".equals(pMccMnc)||"74807".equals(pMccMnc)) {
			stk = "Movistar";
	    }

		if(SystemProperties.get("ro.hq.stk.name.atandt").equals("1")) {
			String stkname = SystemProperties.get("gsm.stkapp.name");
			boolean isstkname = (stkname == null || stkname.equals("")) ? true : false;
			if (!isstkname) {
			    stk = stkname;
			}
			else {
			    stk = "Mi SIM";
			}
		}
		
		if(SystemProperties.get("ro.hq.stk.name.telcel").equals("1")) {
			stk = "SIM Telcel";
	    }

		return stk;
	}

    /**
     * Return whether this component and its enclosing application are enabled.
     */
    public boolean isEnabled() {
        return enabled && applicationInfo.enabled;
    }
    
    /**
     * Return the icon resource identifier to use for this component.  If
     * the component defines an icon, that is used; else, the application
     * icon is used.
     * 
     * @return The icon associated with this component.
     */
    public final int getIconResource() {
        /* < DTS2014102001272 zhangxinming/00181848 20140926 begin */
        HwThemeManager.updateIconCache(this, name, packageName, icon, applicationInfo.icon);
        /*   DTS2014102001272 zhangxinming/00181848 20140926 end > */
        return icon != 0 ? icon : applicationInfo.icon;
    }

    /**
     * Return the logo resource identifier to use for this component.  If
     * the component defines a logo, that is used; else, the application
     * logo is used.
     *
     * @return The logo associated with this component.
     */
    public final int getLogoResource() {
        return logo != 0 ? logo : applicationInfo.logo;
    }
    
    /**
     * Return the banner resource identifier to use for this component. If the
     * component defines a banner, that is used; else, the application banner is
     * used.
     *
     * @return The banner associated with this component.
     */
    public final int getBannerResource() {
        return banner != 0 ? banner : applicationInfo.banner;
    }

    protected void dumpFront(Printer pw, String prefix) {
        super.dumpFront(pw, prefix);
        pw.println(prefix + "enabled=" + enabled + " exported=" + exported
                + " processName=" + processName);
        if (descriptionRes != 0) {
            pw.println(prefix + "description=" + descriptionRes);
        }
    }
    
    protected void dumpBack(Printer pw, String prefix) {
        if (applicationInfo != null) {
            pw.println(prefix + "ApplicationInfo:");
            applicationInfo.dump(pw, prefix + "  ");
        } else {
            pw.println(prefix + "ApplicationInfo: null");
        }
        super.dumpBack(pw, prefix);
    }
    
    public void writeToParcel(Parcel dest, int parcelableFlags) {
        super.writeToParcel(dest, parcelableFlags);
        applicationInfo.writeToParcel(dest, parcelableFlags);
        dest.writeString(processName);
        dest.writeInt(descriptionRes);
        dest.writeInt(enabled ? 1 : 0);
        dest.writeInt(exported ? 1 : 0);
    }
    
    protected ComponentInfo(Parcel source) {
        super(source);
        applicationInfo = ApplicationInfo.CREATOR.createFromParcel(source);
        processName = source.readString();
        descriptionRes = source.readInt();
        enabled = (source.readInt() != 0);
        exported = (source.readInt() != 0);
    }
    
    /**
     * @hide
     */
    @Override
    public Drawable loadDefaultIcon(PackageManager pm) {
        return applicationInfo.loadIcon(pm);
    }
    
    /**
     * @hide
     */
    @Override protected Drawable loadDefaultBanner(PackageManager pm) {
        return applicationInfo.loadBanner(pm);
    }

    /**
     * @hide
     */
    @Override
    protected Drawable loadDefaultLogo(PackageManager pm) {
        return applicationInfo.loadLogo(pm);
    }
    
    /**
     * @hide
     */
    @Override protected ApplicationInfo getApplicationInfo() {
        return applicationInfo;
    }
}
