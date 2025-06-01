package com.cgluWxh.notificator;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

public class PersistAppDataStorage {
    private LocalStorage localStorage;
    public PersistAppDataStorage(Context context) {
        localStorage = new LocalStorage(context.getSharedPreferences("persistAppName", Context.MODE_PRIVATE));
        setData("com.tencent.mqq", "QQ", 1);
        setData("com.tencent.xin", "微信", 1);
    }

    public static JSONObject getObject(String bundleID) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("appName", bundleID);
            jsonObject.put("bundleID", bundleID);
            jsonObject.put("lastNotification", 0);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }
    public static JSONObject getObject(String bundleID, String appName, int lastNotification) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("appName", appName);
            jsonObject.put("bundleID", bundleID);
            jsonObject.put("lastNotification", lastNotification);
            return jsonObject;
        } catch (JSONException e) {
            return null;
        }
    }
    public JSONObject getData(String bundleID) {
        String result = localStorage.getValue(bundleID, null);
        if (result == null) return getObject(bundleID);
        try {
            return new JSONObject(result);
        } catch (JSONException e) {
            return getObject(bundleID);
        }
    }

    public void setData(String bundleID, String appName, int lastNotification) {
        localStorage.setValue(bundleID,
                getObject(bundleID,
                        appName,
                        lastNotification).toString()
        );
    }

    public void updateData(String bundleID, String key, String value) {
        JSONObject original = getData(bundleID);
        try {
            original.put(key, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    public void updateData(String bundleID, String key, int value) {
        JSONObject original = getData(bundleID);
        try {
            original.put(key, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

}
