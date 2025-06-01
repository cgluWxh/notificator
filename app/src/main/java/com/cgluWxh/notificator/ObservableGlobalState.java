package com.cgluWxh.notificator;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;

public class ObservableGlobalState {
    public final MutableLiveData<Integer> state = new MutableLiveData<>();

    public ObservableGlobalState(Context ctx) {
        state.setValue(-1);
    }

}
