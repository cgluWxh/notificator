package com.cgluWxh.notificator;

public class PersistAppData {
    public String bundleID;
    public String name;
    public PersistAppData(String b, String n) {
        bundleID = b;
        name = n;
    }
    public PersistAppData(String b) {
        bundleID = b;
        name = null;
    }
}
