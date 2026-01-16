package com.eleybourn.bookcatalogue.utils;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public class LocaleManager {
    public static void setAppLocale(String languageTag) {
        // Use LocaleListCompat to create a list of locales
        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(languageTag);
        // Set the app's locale
        AppCompatDelegate.setApplicationLocales(appLocale);
    }
}