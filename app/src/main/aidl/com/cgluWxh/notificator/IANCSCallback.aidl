// IANCSCallback.aidl
package com.cgluWxh.notificator;

// Declare any non-default types here with import statements
import com.cgluWxh.notificator.UIState;

interface IANCSCallback {
    void onValueChanged(in UIState newValue);
}