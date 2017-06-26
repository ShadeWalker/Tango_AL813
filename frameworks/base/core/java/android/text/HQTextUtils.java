package android.text;

import java.util.HashMap;

import android.text.SpannableString;
import android.text.SpannedString;
import android.util.Log;

public class HQTextUtils {

	private static boolean isSyrillic(String chs,int len){
		boolean sr = false;
		char c;
		for(int i =0;i< len;i++){
			c=chs.charAt(i);
			if(c>'\u0400'&& c<'\u0460'){
				sr = true;
				break;
			}
		}
		return sr;
	}
	
	private static StringBuilder getLatinString(String chs, int len){
		
		StringBuilder out = new StringBuilder();
		char c;
		for(int i =0;i<len;i++){
			c= chs.charAt(i);
			if(c>'\u0400'&&c<'\u0460'){
				out.append(SyrillicLatinMap.get(c));
			}else{
				out.append(c);
			}
		}
		return out;
	}
	
	private static int getLatinStringLen(CharSequence chs,int len){
		return getLatinString(chs.toString(), len).length();
	}
	
	public static String serbianSyrillic2Latin(String text){
		if(text == null){
			return null;
		}
		int len = text.length();
		if(!isSyrillic(text, len)){
			return text;
		}
		StringBuilder out = getLatinString(text,len);
		return out.toString();
	}
	
	public static CharSequence serbianSyrillic2Latin(CharSequence text){
		if(text == null){
			return null;
		}
		Log.i("HQTextUtilsgaoyuhao", "serbianSyrillic2Latin(CharSequence text)");
		if(text instanceof String){
			return serbianSyrillic2Latin((String)text);
		}else if(text instanceof SpannedString){
			int len = text.length();
			
		if(!isSyrillic(text.toString(),len)){
			return text;
		}
		
		StringBuilder out = getLatinString(text.toString(), len);
		SpannableString newText = new SpannableString(out);
		SpannedString sp = (SpannedString) text;
		
		int start = 0;
		int end =sp.length();
		Object[] spans = sp.getSpans(start, end, Object.class);
		
		for(int i = 0;i<spans.length;i++){
			int st =sp.getSpanStart(spans[i]);
			int en = sp.getSpanEnd(spans[i]);
			int fl = sp.getSpanFlags(spans[i]);
			
			if(st<start){
				st=start;
			}
			if(en > end){
				en =end;
			}
			
			st =getLatinStringLen(text.subSequence(0, st), st);
			en =getLatinStringLen(text.subSequence(0, en), en);
			
			((SpannableString)newText).setSpan(spans[i], st, en, fl);
		}
		return newText;
		}else{
			return text;
		}
	}
	
	
	private static HashMap<Character, CharSequence> SyrillicLatinMap = SyrillicToLatin();
	
	private static HashMap<Character, CharSequence> SyrillicToLatin(){
		HashMap<Character, CharSequence> map = new HashMap<Character, CharSequence>();
		map.put('\u0401', "\u0401");//the same
		map.put('\u0402', "\u0110");
		map.put('\u0403', "\u0403");
		map.put('\u0404', "\u0404");
		map.put('\u0405', "\u0405");
		map.put('\u0406', "\u0406");
		map.put('\u0407', "\u0407");
		map.put('\u0408', "J");
		map.put('\u0409', "Lj");
		map.put('\u040A', "Nj");
		map.put('\u040B', "\u0106");
		map.put('\u040C', "\u040C");
		map.put('\u040D', "\u040D");
		map.put('\u040E', "\u040E");
		map.put('\u040F', "D\u017E");
		
		map.put('\u0410', "A");
		map.put('\u0411', "B");
		map.put('\u0412', "V");
		map.put('\u0413', "G");
		map.put('\u0414', "D");
		map.put('\u0415', "E");
		map.put('\u0416', "\u017D");
		map.put('\u0417', "Z");
		map.put('\u0418', "I");
		map.put('\u0419', "\u0419");
		map.put('\u041A', "K");
		map.put('\u041B', "L");
		map.put('\u041C', "M");
		map.put('\u041D', "N");
		map.put('\u041E', "O");
		map.put('\u041F', "P");
		
		map.put('\u0420', "R");
		map.put('\u0421', "S");
		map.put('\u0422', "T");
		map.put('\u0423', "U");
		map.put('\u0424', "F");
		map.put('\u0425', "H");
		map.put('\u0426', "C");
		map.put('\u0427', "\u010C");
		map.put('\u0428', "\u0160");
		map.put('\u0429', "\u0429");
		map.put('\u042A', "\u042A");
		map.put('\u042B', "\u042B");
		map.put('\u042C', "\u042C");
		map.put('\u042D', "\u042D");
		map.put('\u042E', "\u042E");
		map.put('\u042F', "\u042F");
		
		map.put('\u0430', "a");
		map.put('\u0431', "b");
		map.put('\u0432', "v");
		map.put('\u0433', "g");
		map.put('\u0434', "d");
		map.put('\u0435', "e");
		map.put('\u0436', "\u017E");
		map.put('\u0437', "z");
		map.put('\u0438', "i");
		map.put('\u0439', "\u0439");
		map.put('\u043A', "k");
		map.put('\u043B', "l");
		map.put('\u043C', "m");
		map.put('\u043D', "n");
		map.put('\u043E', "o");
		map.put('\u043F', "p");
		
		map.put('\u0440', "r");
		map.put('\u0441', "s");
		map.put('\u0442', "t");
		map.put('\u0443', "u");
		map.put('\u0444', "f");
		map.put('\u0445', "h");
		map.put('\u0446', "c");
		map.put('\u0447', "\u010D");
		map.put('\u0448', "\u0161");
		map.put('\u0449', "\u0449");
		map.put('\u044A', "\u044A");
		map.put('\u044B', "\u044B");
		map.put('\u044C', "\u044C");
		map.put('\u044D', "\u044D");
		map.put('\u044E', "\u044E");
		map.put('\u044F', "\u040F");
		
		map.put('\u0450', "\u0450");
		map.put('\u0451', "\u0451");
		map.put('\u0452', "\u0111");
		map.put('\u0453', "\u0453");
		map.put('\u0454', "\u0454");
		map.put('\u0455', "\u0455");
		map.put('\u0456', "\u0456");
		map.put('\u0457', "\u0457");
		map.put('\u0458', "j");
		map.put('\u0459', "lj");
		map.put('\u045A', "nj");
		map.put('\u045B', "\u0107");
		map.put('\u045C', "\u045C");
		map.put('\u045D', "\u045D");
		map.put('\u045E', "\u045E");
		map.put('\u045F', "\u017E");
		
		
		return map;
	}
}
