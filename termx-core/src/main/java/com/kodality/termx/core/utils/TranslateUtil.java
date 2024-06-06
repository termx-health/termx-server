package com.kodality.termx.core.utils;

import com.kodality.termx.core.auth.SessionInfo;
import com.kodality.termx.core.auth.SessionStore;
import java.util.Locale;
import java.util.Locale.Builder;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class TranslateUtil {

  public static String translate(String key, String baseName) {
    String lang = getLanguage();
    String translatedMessage;
    try {
      ResourceBundle bundle = getBundle(lang, baseName);
      translatedMessage = bundle.getString(key.toLowerCase());
    } catch (MissingResourceException e) {
      translatedMessage = key;
    }
    return translatedMessage;
  }

  private static ResourceBundle getBundle(String lang, String baseName) {
    Locale locale = new Builder().setLanguage(lang).build();
    return ResourceBundle.getBundle(baseName, locale);
  }

  private static String getLanguage() {
    return SessionStore.get().map(SessionInfo::getLang).orElse("en");
  }
}
