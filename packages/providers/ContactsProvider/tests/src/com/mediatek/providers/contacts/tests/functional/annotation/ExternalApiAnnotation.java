package com.mediatek.providers.contacts.tests.functional.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


// use below command to run test case marked with annotation of "ExternalApiAnnotation"
// adb shell am instrument  -w -e annotation com.mediatek.providers.contacts.tests.functional
// .annotation.ExternalApiAnnotation com.android.providers.contacts.tests/com.mediatek.providers
// .contacts.ContactsProviderTestRunner

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE })
public @interface ExternalApiAnnotation {

}
