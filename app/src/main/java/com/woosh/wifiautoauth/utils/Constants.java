package com.woosh.wifiautoauth.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.woosh.wifiautoauth.R;

import java.util.ArrayList;

/**
 * Constants used by multiple classes in this package
 */
public final class Constants {

    public static final String DEBUG_TAG = "woosh";
    public static final boolean DEBUG = true;
    public static final String TEST_URL = "http://clients3.google.com/generate_204";
    public static final String MOCK_TEST_URL = "https://cb619371-32da-4ca6-9212-3e06b02921e3.mock.pstmn.io/generate_204";
    public static final int NET_TIMEOUT = 10000;
    public static final String COMMAND_DATA_ENABLE = "svc data enable\n ";
    public static final String COMMAND_DATA_DISABLE = "svc data disable\n ";
    public static final String COMMAND_SU = "su";

    public static Boolean CAPTIVITY_DETECTED = null;
    public static final ArrayList<String> DEBUG_LIST = new ArrayList<>();

    public static boolean PREF_AUTOLOGIN, PREF_EXPERT;
    public static String PREF_PORTAL, PREF_PORTAL_NEW, PREF_PASSWD, PREF_USERNAME;

    public static void loadPrefs(Context context) {
        Util.addToDebugLog("Constants.loadPrefs() - loading preferences");
        PreferenceManager.setDefaultValues(context, R.xml.pref_general, false);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        PREF_AUTOLOGIN = sharedPref.getBoolean("autologin", false);
        PREF_EXPERT = sharedPref.getBoolean("expert", false);
        PREF_USERNAME = sharedPref.getString("username", "");
        PREF_PASSWD = sharedPref.getString("password", "");
        if (PREF_PORTAL_NEW != null) {
            PREF_PORTAL = PREF_PORTAL_NEW;
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("portal_url", PREF_PORTAL_NEW);
            editor.apply();
            PREF_PORTAL_NEW = null;
        } else {
            PREF_PORTAL = sharedPref.getString("portal_url", "");
        }
    }
}