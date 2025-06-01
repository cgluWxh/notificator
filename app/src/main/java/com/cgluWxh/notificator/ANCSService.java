package com.cgluWxh.notificator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelUuid;
import android.util.Log;

import org.json.JSONException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

public class ANCSService extends Service implements Handler.Callback{

    private Messenger mMessenger;
    private Handler ServerHandler;
    private HandlerThread handlerThread;
    private SystemReceiver mBTReceiver;

    //bt
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mIphoneDevice;
    private final LocalBluetoothGattCallback mGattCallback = new LocalBluetoothGattCallback();
    private BluetoothGatt mConnectedGatt;
    private BluetoothGattService mANCSService;
    private BluetoothGattCharacteristic mNotificationSourceChar;
    private BluetoothGattCharacteristic mPointControlChar;
    private BluetoothGattCharacteristic mDataSourceChar;

    //BLE GATT Server
    private BluetoothGattServer bluetoothGattServer;
    private AdvertiseCallback advertiseCallback;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattServerCallback bluetoothGattServerCallback;

    public static final String CHANNEL_ID = "notification-iphone";

    private PersistAppDataStorage persistAppDataStorage;

    public static String Bytes2HexString(byte[] b, int offset, int count) {
        if (offset > b.length) {
            return null;
        }
        if (offset + count > b.length) {
            return null;
        }
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < count; i++) {
            String hex = Integer.toHexString(b[offset + i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            ret.append(hex.toUpperCase());
        }
        return ret.toString();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handlerThread = new HandlerThread("ServerHandlerThread");
        handlerThread.start();

        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        //server adv
        if (mBluetoothAdapter.isEnabled()){
            initGATTServer();
        }else{
            mBluetoothAdapter.enable();
        }

        ServerHandler = new Handler(handlerThread.getLooper(),this);
        mMessenger = new Messenger(ServerHandler);
        Log.e(GlobalDefine.LOG_TAG, "onCreate");

        mBTReceiver = new SystemReceiver(ServerHandler);
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBTReceiver,intentFilter);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        persistAppDataStorage = new PersistAppDataStorage(getApplicationContext());
    }

    @Override
    public void onDestroy() {
        handlerThread.getLooper().quit();
        handlerThread = null;
        //蓝牙线程终止
        ServerHandler = null;
        mMessenger = null;
        unregisterReceiver(mBTReceiver);
        super.onDestroy();
        Log.e(GlobalDefine.LOG_TAG, "onDestroy");
    }



    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public boolean handleMessage(Message message) {
        switch (message.what){
            case GlobalDefine.BLUETOOTH_BONDED:
                if (mIphoneDevice != null) {
                    Log.d(GlobalDefine.LOG_TAG, "connect gatt");
                    mIphoneDevice.connectGatt(getApplicationContext(), false, mGattCallback);
                }
                break;
            case GlobalDefine.BLUETOOTH_ON:
                initGATTServer();
                break;

            case GlobalDefine.BLUETOOTH_ACCEPT:
                positiveResponseToNotification((byte[]) message.obj);
                break;
            case GlobalDefine.BLUETOOTH_REJECT:
                negativeResponseToNotification((byte[]) message.obj);
                break;
            case GlobalDefine.BLUETOOTH_DISPLAY_INFO:
                // 发出一个通知
                TagScanner tagScanner = new TagScanner((byte[]) message.obj);
                NotificationData notificationData = new NotificationData();

                notificationData.uid = tagScanner.getNotificationUID();

                Log.i(GlobalDefine.LOG_TAG, "NotificationUID =" + notificationData.uid);

                BleTag bleTag = tagScanner.nextTag();
                while (bleTag != null) {
                    switch (bleTag.type) {
                        case TagScanner.TAG_ATTR_TITLE:
                            notificationData.title = bleTag.value;
                            Log.i(GlobalDefine.LOG_TAG, "通知标题："+bleTag.value);
                            break;
                        case TagScanner.TAG_ATTR_CONTENT:
                            notificationData.content = bleTag.value;
                            Log.i(GlobalDefine.LOG_TAG, "通知内容："+bleTag.value);
                            break;
                        case TagScanner.TAG_ATTR_BUNDLEID:
                            notificationData.from = persistAppDataStorage.getData(bleTag.value);
                            Log.i(GlobalDefine.LOG_TAG, "通知来源："+bleTag.value);
                            break;
                    }
                    bleTag = tagScanner.nextTag();
                }
//                showNotification(this, notificationUID, title.toString(), msg);
                break;
            case GlobalDefine.BLUETOOTH_GET_MORE_INFO:
                byte[] data2 = (byte[]) message.obj;
                retrieveMoreInfo(data2);
                break;
            default:
                break;
        }
        return false;
    }


    @SuppressWarnings("unchecked")
    public boolean createBond(@SuppressWarnings("rawtypes") Class btClass, BluetoothDevice btDevice)
    {
        Method createBondMethod = null;
        Boolean returnValue = null;
        try {
            createBondMethod = btClass.getMethod("createBond");
            returnValue = (Boolean) createBondMethod.invoke(btDevice);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return Boolean.TRUE.equals(returnValue);
    }


    //BLE Server For Adv
    private boolean initServices(Context context) {
        bluetoothGattServerCallback = new LocalBluetoothGattServerCallback();
        bluetoothGattServer = mBluetoothManager.openGattServer(context, bluetoothGattServerCallback);
        BluetoothGattService service = new BluetoothGattService(UUID.fromString(GlobalDefine.service_hid), BluetoothGattService.SERVICE_TYPE_PRIMARY);
        if (bluetoothGattServer!=null && bluetoothGattServerCallback!=null){
            bluetoothGattServer.addService(service);
            Log.i(GlobalDefine.LOG_TAG, "2. initServices ok");
            return true;
        }else{
            closeGattServer();
            return false;
        }
    }


    public void closeGattServer(){
        if (bluetoothGattServer!=null)
        {
            bluetoothGattServer.clearServices();
            bluetoothGattServer.close();
        }

        if (advertiser!=null){
            advertiser.stopAdvertising(advertiseCallback);
        }
    }

    public void initGATTServer() {

        //先初始化好服务
        if(!initServices(getApplicationContext())){
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setConnectable(true)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .build();

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid.fromString(GlobalDefine.service_hid))
                .build();

        advertiseCallback = new AdvertiseCallback() {

            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.e(GlobalDefine.LOG_TAG, "#BLE advertisement successfully");
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e(GlobalDefine.LOG_TAG, "Failed to add BLE advertisement, reason: " + errorCode);
                if(errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE){
                    Log.e(GlobalDefine.LOG_TAG,"Failed to start advertising as the advertise data to be broadcasted is larger than 31 bytes.");
                }else if(errorCode == ADVERTISE_FAILED_TOO_MANY_ADVERTISERS){
                    Log.e(GlobalDefine.LOG_TAG,"Failed to start advertising because TOO_MANY_ADVERTISERS.");
                }else if(errorCode == ADVERTISE_FAILED_ALREADY_STARTED){
                    Log.e(GlobalDefine.LOG_TAG,"Failed to start advertising as the advertising is already started");
                }else if(errorCode == ADVERTISE_FAILED_INTERNAL_ERROR){
                    Log.e(GlobalDefine.LOG_TAG,"Operation failed due to an internal error");
                }else if(errorCode == ADVERTISE_FAILED_FEATURE_UNSUPPORTED){
                    Log.e(GlobalDefine.LOG_TAG,"This feature is not supported on this platform");
                }
            }
        };

        advertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        advertiser.stopAdvertising(advertiseCallback);
        if (advertiser!=null){
            advertiser.startAdvertising(settings, advertiseData,advertiseCallback);
        }
    }


    public void negativeResponseToNotification(byte[] nid) {

        byte[] action = {
                (byte) 0x02,
                //UID
                nid[0], nid[1], nid[2], nid[3],
                //action id
                (byte) 0x01,

        };

        //如果已经绑定，而且此时未断开
        if (mConnectedGatt != null) {
            BluetoothGattService service = mConnectedGatt.getService(UUID.fromString(GlobalDefine.service_ancs));
            if (service == null) {
                Log.d(GlobalDefine.LOG_TAG, "cant find service");
            } else {
                Log.d(GlobalDefine.LOG_TAG, "find service");
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(GlobalDefine.characteristics_control_point));
                if (characteristic == null) {
                    Log.d(GlobalDefine.LOG_TAG, "cant find chara");
                } else {
                    Log.d(GlobalDefine.LOG_TAG, "find chara");
                    characteristic.setValue(action);
                    mConnectedGatt.writeCharacteristic(characteristic);
                }
            }
        }
    }

    public void positiveResponseToNotification(byte[] nid) {

        byte[] action = {
                (byte) 0x02,
                //UID
                nid[0], nid[1], nid[2], nid[3],
                //action id
                (byte) 0x00,

        };

        //如果已经绑定，而且此时未断开
        if (mConnectedGatt != null) {
            BluetoothGattService service = mConnectedGatt.getService(UUID.fromString(GlobalDefine.service_ancs));
            if (service == null) {
                Log.d(GlobalDefine.LOG_TAG, "cant find service");
            } else {
                Log.d(GlobalDefine.LOG_TAG, "find service");
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(GlobalDefine.characteristics_control_point));
                if (characteristic == null) {
                    Log.d(GlobalDefine.LOG_TAG, "cant find chara");
                } else {
                    Log.d(GlobalDefine.LOG_TAG, "find chara");
                    characteristic.setValue(action);
                    mConnectedGatt.writeCharacteristic(characteristic);
                }
            }
        }
    }

    public void retrieveMoreInfo(byte[] nid) {

        byte[] getNotificationAttribute = {
                (byte) 0x00,
                //UID
                nid[0], nid[1], nid[2], nid[3],

                //title
                (byte) 0x01, (byte) 0xff, (byte) 0xff,
                //subtitle
//                (byte) 0x02, (byte) 0xff, (byte) 0xff,
                //message
                (byte) 0x03, (byte) 0xff, (byte) 0xff,
                // app identifier
                (byte) 0x00
        };

        Log.i(GlobalDefine.LOG_TAG,"发送获取详细信息的指令="+Bytes2HexString(getNotificationAttribute,0,getNotificationAttribute.length));
        //如果已经绑定，而且此时未断开
        if (mConnectedGatt != null) {
            BluetoothGattService service = mConnectedGatt.getService(UUID.fromString(GlobalDefine.service_ancs));
            if (service == null) {
                Log.d(GlobalDefine.LOG_TAG, "cant find service");
            } else {
                Log.d(GlobalDefine.LOG_TAG, "find service");
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(GlobalDefine.characteristics_control_point));
                if (characteristic == null) {
                    Log.d(GlobalDefine.LOG_TAG, "cant find chara");
                } else {
                    Log.d(GlobalDefine.LOG_TAG, "find chara");
                    characteristic.setValue(getNotificationAttribute);
                    mConnectedGatt.writeCharacteristic(characteristic);
                }
            }
        }
    }

    private void setNotificationEnabled(BluetoothGattCharacteristic characteristic) {
        mConnectedGatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(GlobalDefine.descriptor_config));
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mConnectedGatt.writeDescriptor(descriptor);
        }
    }


    private class LocalBluetoothGattCallback extends BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(GlobalDefine.LOG_TAG, "connected");
                mConnectedGatt = gatt;
                gatt.discoverServices();
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                //外设主动断开
                Log.d(GlobalDefine.LOG_TAG, "disconnected");
                initGATTServer();
                mConnectedGatt = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService ancsService = gatt.getService(UUID.fromString(GlobalDefine.service_ancs));
                if (ancsService == null) {
                    Log.d(GlobalDefine.LOG_TAG, "ANCS cannot find");
                } else {
                    Log.d(GlobalDefine.LOG_TAG, "ANCS find");
                    mANCSService = ancsService;
                    mDataSourceChar = ancsService.getCharacteristic(UUID.fromString(GlobalDefine.characteristics_data_source));
                    mPointControlChar = ancsService.getCharacteristic(UUID.fromString(GlobalDefine.characteristics_control_point));
                    mNotificationSourceChar = ancsService.getCharacteristic(UUID.fromString(GlobalDefine.characteristics_notification_source));
                    setNotificationEnabled(mDataSourceChar);
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(GlobalDefine.LOG_TAG, " onDescriptorWrite:: " + status);
            // Notification source
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.getCharacteristic().getUuid().equals(UUID.fromString(GlobalDefine.characteristics_data_source))) {
                    setNotificationEnabled(mNotificationSourceChar);
                    Log.d(GlobalDefine.LOG_TAG, "data_source 订阅成功 ");
                }
                if (descriptor.getCharacteristic().getUuid().equals(UUID.fromString(GlobalDefine.characteristics_notification_source))) {
                    Log.d(GlobalDefine.LOG_TAG, "notification_source　订阅成功 ");
                }
            }

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(GlobalDefine.LOG_TAG, "onCharacteristicWrite");
            if (GlobalDefine.characteristics_control_point.equals(characteristic.getUuid().toString())) {
                Log.d(GlobalDefine.LOG_TAG, "control_point  Write successful");

            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (GlobalDefine.characteristics_notification_source.equals(characteristic.getUuid().toString())) {
                Log.d(GlobalDefine.LOG_TAG, "notification_source Changed");
                byte[] nsData = characteristic.getValue();
                Log.i(GlobalDefine.LOG_TAG,"通知数据："+Bytes2HexString(nsData,0,nsData.length));


                /*
                EventID：消息类型，添加(0)、修改(1)、删除(2)；

                EventFlags：消息优先级，静默(1)、重要(2)；

                CategoryID：消息类型；

                CategoryCount：消息计数；

                NotificationUID：通知ID，可以通过此ID获取详情；
                */
                //TODO getMoreAboutNotification(nsData);
                Log.i(GlobalDefine.LOG_TAG,"EventID ="+nsData[0]);
                Log.i(GlobalDefine.LOG_TAG,"EventFlags ="+nsData[1]);
                Log.i(GlobalDefine.LOG_TAG,"CategoryID ="+nsData[2]);
                Log.i(GlobalDefine.LOG_TAG,"CategoryCount ="+nsData[3]);
                Log.i(GlobalDefine.LOG_TAG,"NotificationUID ="+ Bytes2HexString(nsData,4,4));

                if (nsData[0]==0x02){
                    Log.i(GlobalDefine.LOG_TAG,"通知被iphone删除");
                }else{
                    byte[] NotificationUID = new byte[4];
                    System.arraycopy(nsData,4,NotificationUID,0 ,4);
                    Message msg = ServerHandler.obtainMessage();
                    msg.what = GlobalDefine.BLUETOOTH_GET_MORE_INFO;
                    msg.obj = NotificationUID;
                    ServerHandler.sendMessage(msg);
                }
            }
            if (GlobalDefine.characteristics_data_source.equals(characteristic.getUuid().toString())) {
                Log.d(GlobalDefine.LOG_TAG, "characteristics_data_source changed");
                byte[] get_data = characteristic.getValue();
                Log.i(GlobalDefine.LOG_TAG,"详细数据："+Bytes2HexString(get_data,0,get_data.length));

                //TODO 显示通知消息
                Message msg = ServerHandler.obtainMessage();
                msg.what = GlobalDefine.BLUETOOTH_DISPLAY_INFO;
                msg.obj = get_data;
                ServerHandler.sendMessage(msg);
            }

            if (GlobalDefine.characteristics_control_point.equals(characteristic.getUuid().toString())) {
                Log.d(GlobalDefine.LOG_TAG, "characteristics_control_point changed");
                byte[] cpData = characteristic.getValue();
                Log.i(GlobalDefine.LOG_TAG,"控制数据："+Bytes2HexString(cpData,0,cpData.length));
            }
        }

    }

    private class LocalBluetoothGattServerCallback extends BluetoothGattServerCallback{

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            mIphoneDevice = device;
            if (newState==2){
                closeGattServer();
                String MacAddress = mIphoneDevice.getAddress();
                Log.i(GlobalDefine.LOG_TAG,"连接到的 iPhone 的 MAC 地址："+ MacAddress);
                if (mIphoneDevice.getBondState()==BluetoothDevice.BOND_BONDED){
                    mIphoneDevice.connectGatt(getApplicationContext(), false, mGattCallback);
                }else{
                    createBond(device.getClass(),device);
                }
            }
        }
    }


    public static void showNotification(Context context,int id,String title,String message) {
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setSmallIcon(R.mipmap.ic_launcher)
//                .setTicker("通知来了")
                .setContentTitle(title)
                .setContentText(message)
                .setWhen(System.currentTimeMillis())
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOngoing(false)
                .setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND)
                .setContentIntent(PendingIntent.getActivity(context, 1, new Intent(context, MainActivity.class), PendingIntent.FLAG_CANCEL_CURRENT))
                .build();
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(id, notification);
    }
}
