package com.cgluWxh.notificator;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity{

    private Intent myIntent;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myIntent = new Intent(this, ANCSService.class);
        startService(myIntent);
    }

    @Override
    protected void onDestroy() {
//        stopService(myIntent);
        super.onDestroy();
    }


}
