package com.cgluWxh.notificator;

import android.util.Log;

public class TagScanner {
    /*
        CommandID：为0；
        NotificationUID：对应之前请求的UID；
        AttributeList：查询结果列表，每一项的格式都是：ID/16bit  Length/Value，每个attribute都是一个字符串，其长度由Length指定，但是此字符串不是以NULL结尾。若找不到对应的Attribute，则Length为0；
    */
    static public final int TAG_ATTR_BUNDLEID = 0x00;
    static public final int TAG_ATTR_TITLE = 0x01;
    static public final int TAG_ATTR_SUBTITLE = 0x02;
    static public final int TAG_ATTR_CONTENT = 0x03;
    static public final int TAG_ATTR_DATE = 0x05;
    static public final int TAG_ATTR_APPNAME = 0x00;
    static public final int TAG_TYPE_NOTIFICATION_ATTRIBUTE = 0x00;
    static public final int TAG_TYPE_APP_ATTRIBUTE = 0x01;
    private int mTagIndex = 0;
    private final byte[] mData;

    private int mNotificationUID = 0;

    public int mType;

    public String mAppBundleID = "";

    public static int parseNotificationIDFromBytes(byte[] idData) {
        int i = 0;
        i += (idData[0] & 0x000000ff);
        i += (idData[1] & 0x000000ff) << 8;
        i += (idData[2] & 0x000000ff) << 16;
        i += (idData[3] & 0x000000ff) << 24;
        return i;
    }

    public int getNotificationUID() {
        if (mType != TAG_TYPE_NOTIFICATION_ATTRIBUTE) return -1;
        if (mNotificationUID != 0) return mNotificationUID;
        byte[] idData = new byte[4];
        System.arraycopy(mData,1 , idData,0 ,4);
        mNotificationUID = parseNotificationIDFromBytes(idData);
        return mNotificationUID;
    }

    public TagScanner(byte[] data) {
        mData = data;
        mType = data[0];
        switch (mType) {
            case TAG_TYPE_APP_ATTRIBUTE:
                mTagIndex = 1;
                while (mData[mTagIndex] != 0x00) mTagIndex++;
                mAppBundleID = new String(mData, 1, mTagIndex - 1);
                Log.w(GlobalDefine.LOG_TAG, "Received Bundle ID: "+mAppBundleID);
                mTagIndex++;
                break;
            case TAG_TYPE_NOTIFICATION_ATTRIBUTE:
                getNotificationUID();
                mTagIndex = 5;
                break;
        }
    }

    public BleTag nextTag() {
        if (mTagIndex < mData.length) {
            int type = mData[mTagIndex];
            int len = mData[mTagIndex+1] + mData[mTagIndex+2]*256;
            if (len == 0) return new BleTag(type, null);
            String value = new String(mData, mTagIndex+3, len);
            mTagIndex = mTagIndex + 3 + len;
            return new BleTag(type, value);
        }
        return null;
    }
}

