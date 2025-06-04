// IANCSInterface.aidl
package com.cgluWxh.notificator;

// Declare any non-default types here with import statements
import com.cgluWxh.notificator.UIState;
import com.cgluWxh.notificator.IANCSCallback;

interface IANCSInterface {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    UIState getCurrentState();
//    void setForeground(boolean f);
    void registerCallback(IANCSCallback callback);
    void unregisterCallback(IANCSCallback callback);
}