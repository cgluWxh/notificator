package com.cgluWxh.notificator;

import android.content.SharedPreferences;

public class LocalStorage {
    private final SharedPreferences mSharedPreferences;
    public LocalStorage(SharedPreferences sharedPreferences) {
        mSharedPreferences = sharedPreferences;
    }
    public String getValue(String key) {
        return getValue(key, "");
    }

    public String getValue(String key, String defaultVal) {
        return mSharedPreferences.getString(key, defaultVal);
    }
    public void setValue(String key, String value) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }
}
