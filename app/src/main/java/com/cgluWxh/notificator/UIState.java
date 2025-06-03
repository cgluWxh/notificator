package com.cgluWxh.notificator;

import android.os.Parcel;
import android.os.Parcelable;

public class UIState implements Parcelable {
    public int state = -1;
    public String extraInfo = "";

    // 默认构造函数
    public UIState() {
    }

    // 从Parcel构造的构造函数
    protected UIState(Parcel in) {
        state = in.readInt();
        extraInfo = in.readString();
    }

    // 实现describeContents方法
    @Override
    public int describeContents() {
        return 0;
    }

    // 实现writeToParcel方法
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(state);
        dest.writeString(extraInfo);
    }

    // 创建CREATOR常量
    public static final Creator<UIState> CREATOR = new Creator<UIState>() {
        @Override
        public UIState createFromParcel(Parcel in) {
            return new UIState(in);
        }

        @Override
        public UIState[] newArray(int size) {
            return new UIState[size];
        }
    };
}