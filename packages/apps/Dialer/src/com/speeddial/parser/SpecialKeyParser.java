package com.speeddial.parser;

import android.content.res.XmlResourceParser;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Created by guofeiyao 2016/1/6
 */
public class SpecialKeyParser {
    private static final String TAG_ROOT = "keys";
    private static final String TAG_CHILD = "key";
    private static final String TAG_KEY = "name";
    private static final String TAG_VALUE = "number";

    public static Map<String, String> parse(XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        Map<String, String> map = new HashMap<>();
        String name = null;
        String number = null;

        int event = parser.getEventType();
        Stack<String> tagStack = new Stack<>();
        while (event != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.START_TAG) {
                switch (parser.getName()) {
                    case TAG_ROOT:
                        tagStack.push(TAG_ROOT);
                        break;
                    case TAG_CHILD:
                        tagStack.push(TAG_CHILD);
                        break;
                    case TAG_KEY:
                        tagStack.push(TAG_KEY);
                        break;
                    case TAG_VALUE:
                        tagStack.push(TAG_VALUE);
                        break;
// default:
// throw new XmlPullParserException(
// "Error in xml: tag isn't '"
// + TAG_ROOT
// + "' or '"
// + TAG_CHILD
// + "' or '"
// + TAG_KEY
// + "' or '"
// + TAG_VALUE
// + "' at line:"
// + parser.getLineNumber());
                }
            } else if (event == XmlResourceParser.TEXT) {
                switch (tagStack.peek()) {
                    case TAG_KEY:
                        name = parser.getText();
                        break;
                    case TAG_VALUE:
                        number = parser.getText();
                        break;
                }
            } else if (event == XmlResourceParser.END_TAG) {
                boolean mismatch = false;
                switch (parser.getName()) {
                    case TAG_ROOT:
                        if (!TAG_ROOT.equals(tagStack.pop())) {
                            mismatch = true;
                        }
                        break;
                    case TAG_CHILD:
                        if (!TAG_CHILD.equals(tagStack.pop())) {
                            mismatch = true;
                        }
                        map.put(name, number);
                        break;
                    case TAG_KEY:
                        if (!TAG_KEY.equals(tagStack.pop())) {
                            mismatch = true;
                        }
                        break;
                    case TAG_VALUE:
                        if (!TAG_VALUE.equals(tagStack.pop())) {
                            mismatch = true;
                        }
                        break;
                }
                if (mismatch) {
                    throw new XmlPullParserException(
                            "Error in xml: mismatch end tag at line:"
                                    + parser.getLineNumber());
                }
            }
            event = parser.next();
        }
        parser.close();
        return map;
    }
}