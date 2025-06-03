package com.cgluWxh.notificator;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity{

    private Intent myIntent;
    private TextView mCurrentStateTextView;
    private IANCSInterface mService;
    private boolean mBound = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCurrentStateTextView = findViewById(R.id.current_state);
        mCurrentStateTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Android 8.0 (Oreo) 及更高版本
                    intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                } else {
                    // Android 5.0 (Lollipop) 到 7.1 (Nougat)
                    intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                    intent.putExtra("app_package", getPackageName());
                    intent.putExtra("app_uid", getApplicationInfo().uid);
                }

                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            }
        });

        myIntent = new Intent(this, ANCSService.class);
        startService(myIntent);
        bindService(myIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IANCSInterface.Stub.asInterface(service);
            mBound = true;

            try {
                // 注册回调
                mService.registerCallback(mCallback);
                // 获取初始值
                updateValue(mService.getCurrentState());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    private final IANCSCallback mCallback = new IANCSCallback.Stub() {
        @Override
        public void onValueChanged(final UIState newValue) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateValue(newValue);
                }
            });
        }
    };

    private void updateValue(UIState value) {
        String text = "";
        switch(value.state) {
            case ANCSService.STATE_NOT_INITIALIZED:
                text = getString(R.string.to_be_initialized);
                break;
            case ANCSService.STATE_SERVICE_CREATED:
                text = getString(R.string.service_created);
                break;
            case ANCSService.STATE_IPHONE_CONNECTED:
                text = getString(R.string.connected_to, value.extraInfo);
                break;
            case ANCSService.STATE_ADVERTISING:
                text = getString(R.string.advertising);
                break;
            case ANCSService.STATE_ERROR:
                text = getString(R.string.error, value.extraInfo);
                break;
        }
        mCurrentStateTextView.setText(text);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBound) {
            try {
                // 取消注册回调
                mService.unregisterCallback(mCallback);
            } catch (RemoteException ignored) {}
            unbindService(mConnection);
            mBound = false;
        }
    }


}

