package com.cgluWxh.notificator;

import android.util.Log;

public class TagScanner {
    /*
        CommandID：为0；
        NotificationUID：对应之前请求的UID；
        AttributeList：查询结果列表，每一项的格式都是：ID/16bit  Length/Value，每个attribute都是一个字符串，其长度由Length指定，但是此字符串不是以NULL结尾。若找不到对应的Attribute，则Length为0；
    */
    static public final int TAG_ATTR_TITLE = 0x01;
    static public final int TAG_ATTR_CONTENT = 0x03;
    static public final int TAG_ATTR_BUNDLEID = 0x00;
    private int mTagIndex = 0;
    private final byte[] mData;

    private int mNotificationUID = 0;

    public int getNotificationUID() {
        if (mNotificationUID != 0) return mNotificationUID;
        mNotificationUID += (mData[1] & 0x000000ff);
        mNotificationUID += (mData[2] & 0x000000ff) << 8;
        mNotificationUID += (mData[3] & 0x000000ff) << 16;
        mNotificationUID += (mData[4] & 0x000000ff) << 24;
        return mNotificationUID;
    }

    public TagScanner(byte[] data) {
        mData = data;
        getNotificationUID();
        mTagIndex = 5;
    }

    public BleTag nextTag() {
        if (mTagIndex < mData.length) {
            int type = mData[mTagIndex];
            int len = mData[mTagIndex+1] + mData[mTagIndex+2]*256;
            String value = new String(mData, mTagIndex+3, len);
            Log.i(GlobalDefine.LOG_TAG,"Get Tag =" + type + " and " + value);
            mTagIndex = mTagIndex + 3 + len;
            return new BleTag(type, value);
        }
        return null;
    }
}

