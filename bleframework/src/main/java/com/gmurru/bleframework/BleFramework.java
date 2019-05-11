package com.gmurru.bleframework;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import android.annotation.TargetApi;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.pm.PackageManager;

import android.os.Build;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Handler;

import android.support.annotation.RequiresApi;
import android.util.Log;
import com.unity3d.player.UnityPlayer;

import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import android.app.Activity;
import android.widget.Toast;

/**
 * Created by Fenix on 24/09/2017.
 */

public class BleFramework
{
    private Activity _unityActivity;
    /*
    Singleton instance.
    */
    private static volatile BleFramework _instance;

    /*
    Definition of the BLE Unity message methods used to communicate back with Unity.
    */
    public static final String BLEUnityMessageName_OnBleDidInitialize = "OnBleDidInitialize";
    public static final String BLEUnityMessageName_OnBleDidConnect = "OnBleDidConnect";
    public static final String BLEUnityMessageName_OnBleDidCompletePeripheralScan = "OnBleDidCompletePeripheralScan";
    public static final String BLEUnityMessageName_OnBleDidDisconnect = "OnBleDidDisconnect";
    public static final String BLEUnityMessageName_OnBleDidReceiveData = "OnBleDidReceiveData";

    /*
    Static variables
    */
    private static final String TAG = BleFramework.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 30000;
    public static final int REQUEST_CODE = 30;
    private Handler mHandler;

    /*
    List containing all the discovered bluetooth devices
    */
    private List<BluetoothDevice> _mDevice = new ArrayList<BluetoothDevice>();

    /*
    The latest received data
    */
    private byte[] _dataRx = new byte[3];

    private BroadcastReceiver  mReceiver;
    /*
    Bluetooth service
    */
    private RBLService _mBluetoothLeService;

    private Map<UUID, BluetoothGattCharacteristic> _map = new HashMap<UUID, BluetoothGattCharacteristic>();

    /*
    Bluetooth adapter
    */
    private BluetoothAdapter _mBluetoothAdapter;

    /*
    Bluetooth device address and name to which the app is currently connected
    */
    private BluetoothDevice _device;
    private String _mDeviceAddress  ;
    private String _mDeviceName="FETEL";

    /*
    Boolean variables used to estabilish the status of the connection
    */
    private boolean _connState = false;
    private boolean _flag = true;
    private boolean _searchingDevice = false;

    /*
    The service connection containing the actions definition onServiceConnected and onServiceDisconnected
    */
    private final ServiceConnection _mServiceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service)
        {
            _mBluetoothLeService = ((RBLService.LocalBinder) service).getService();
            if (!_mBluetoothLeService.initialize())
            {
                Log.e(TAG, "onServiceConnected: Unable to initialize Bluetooth");
                //finish();
            }
            else
            {
                Log.d(TAG, "onServiceConnected: Bluetooth initialized correctly");
                // do not connect automatically
                _mBluetoothLeService.connect(_mDeviceAddress);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName)
        {
            Log.d(TAG, "onServiceDisconnected: Bluetooth disconnected");
            _mBluetoothLeService = null;
        }
    };

    /*
    Callback called when the scan of bluetooth devices is finished
    */
    private BluetoothAdapter.LeScanCallback _mLeScanCallback = new BluetoothAdapter.LeScanCallback()
    {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord)
        {
            _unityActivity.runOnUiThread(new Runnable() {
                @Override
                public void run()
                {
//                    Log.d(TAG, "onLeScan: run()");
//                    if (device != null && device.getName() != null)
//                    {
//                        Log.d(TAG, "onLeScan: device is not null");
//                        if (_mDevice.indexOf(device) == -1)
//                        {
//                            Log.d(TAG, "onLeScan: add device to _mDevice");
//                            _mDevice.add(device);
//
//                        }
//                    }

                    Log.d(TAG, device.getAddress() + " " + device.getName() + "");
                    if(device == null) {
                        Log.d(TAG, "Device is null? stop?");
                    } else {
                        _mDevice.add(device);
                    }
                }
            });
        }
    };



    /*
    Callback called when the bluetooth device receive relevant updates about connection, disconnection, service discovery, data available, rssi update
    */
    private final BroadcastReceiver _mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (RBLService.ACTION_GATT_CONNECTED.equals(action))
            {
                _connState = true;
                _flag = true;
                Log.d(TAG, "Connection estabilished with: " + _mDeviceAddress);
                Log.d(TAG, "Send BLEUnityMessageName_OnBleDidConnect success signal to Unity");
                UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidConnect, "Success");

            }
            else if (RBLService.ACTION_GATT_DISCONNECTED.equals(action))
            {
                _connState = false;
                _flag = false;
                UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidDisconnect, "Success");

                Log.d(TAG, "Connection lost");
            }
            else if (RBLService.ACTION_GATT_SERVICES_DISCOVERED.equals(action))
            {
                Log.d(TAG, "Service discovered! Registering GattService ACTION_GATT_SERVICES_DISCOVERED");
                getGattService(_mBluetoothLeService.getSupportedGattService());
            }
            else if (RBLService.ACTION_DATA_AVAILABLE.equals(action))
            {
                Log.d(TAG, "New Data received by the server");
                _dataRx = intent.getByteArrayExtra(RBLService.EXTRA_DATA);
                UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidReceiveData, String.valueOf(_dataRx.length));
            }
            else if (RBLService.ACTION_GATT_RSSI.equals(action))
            {
                String rssiData = intent.getStringExtra(RBLService.EXTRA_DATA);
                Log.d(TAG, "RSSI: " + rssiData);
            }
        }
    };

    /*
    METHODS DEFINITION
    */

    public static BleFramework getInstance(Activity activity)
    {
        if (_instance == null )
        {
            synchronized (BleFramework.class)
            {
                if (_instance == null)
                {
                    Log.d(TAG, "BleFramework: Creation of _instance");
                    _instance = new BleFramework(activity);
                }
            }
        }

        return _instance;
    }

    public BleFramework(Activity activity)
    {
        Log.d(TAG, "BleFramework: saving unityActivity in private var.");
        this._unityActivity = activity;
    }

    /*
    Method used to create a filter for the bluetooth actions that you like to receive
    */
    private static IntentFilter makeGattUpdateIntentFilter()
    {
        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(RBLService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(RBLService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(RBLService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(RBLService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(RBLService.ACTION_GATT_RSSI);

        return intentFilter;
    }

    /*
    Start reading RSSI: information about bluetooth signal intensity
    */

    private void startReadRssi()
    {
        new Thread()
        {
            public void run()
            {
                while (_connState)
                {
                    _mBluetoothLeService.readRssi();
                    try
                    {
                        sleep(500);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            };
        }.start();
    }
    
    /*
    Method used to initialize the characteristic for data transmission
    */

    private void getGattService(BluetoothGattService gattService)
    {

        if (gattService == null)
            return;

        BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(RBLService.UUID_BLE_SHIELD_TX);
        _map.put(characteristic.getUuid(), characteristic);

        BluetoothGattCharacteristic characteristicRx = gattService.getCharacteristic(RBLService.UUID_BLE_SHIELD_RX);
        _mBluetoothLeService.setCharacteristicNotification(characteristicRx,
                true);
        _mBluetoothLeService.readCharacteristic(characteristicRx);
    }


    /*
    Method used to scan for available bluetooth low energy devices
    */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void scanLeDevice() {
            //Finding
            new Thread() {

                @Override
                public void run() {
                    _searchingDevice = true;
                    Log.d(TAG, "scanLeDevice: _mBluetoothAdapter StartLeScan");


                    try {
                        Log.d(TAG, "scanLeDevice: scan for 30 seconds then abort");
                        Thread.sleep(SCAN_PERIOD);


                        _mBluetoothAdapter.startDiscovery();

                        mReceiver = new BroadcastReceiver() {
                            public void onReceive(Context context, Intent intent) {
                                String action = intent.getAction();

                                //Finding devices
                                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                                    // Get the BluetoothDevice object from the Intent
                                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                                    // Add the name and address to an array adapter to show in a ListView
                                    _mDevice.add(_device);
                                    Log.d(TAG, "scanLeDevice: _mDevice size is " + _mDevice.size());
                                    Log.d(TAG, "scanLeDevice: Name " + device.getName());

                                }


                            }
                        };


                    } catch (InterruptedException e) {
                        Log.d(TAG, "scanLeDevice: InterruptedException");
                        e.printStackTrace();
                    }

                    Log.d(TAG, "scanLeDevice: _mBluetoothAdapter StopLeScan");
                    //_mBluetoothAdapter.stopLeScan(_mLeScanCallback);
                    _searchingDevice = false;



                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidCompletePeripheralScan, "Success");

                }
            }.start();

    }



    private void unregisterBleUpdatesReceiver()
    {
        Log.d(TAG,"unregisterBleUpdatesReceiver:");
        _unityActivity.unregisterReceiver(_mGattUpdateReceiver);
    }

    private void registerBleUpdatesReceiver()
    {
        Log.d(TAG,"registerBleUpdatesReceiver:");
        if (!_mBluetoothAdapter.isEnabled())
        {
            Log.d(TAG,"registerBleUpdatesReceiver: WARNING: _mBluetoothAdapter is not enabled!");
            /*
            Intent enableBtIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            */
        }
        Log.d(TAG,"registerBleUpdatesReceiver: registerReceiver");
        _unityActivity.registerReceiver(_mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    //I need a reference to the Unity activity in order to use UnityPlayer.UnitySendMessage

    /*
    Singleton initialization. Create an instance of BleFramework class only if it doesn't exist yet.
    */
    /*
    public static BleFramework getInstance()
    {
        if (_instance == null )
        {
            synchronized (BleFramework.class)
            {
                if (_instance == null)
                {
                    Log.d(TAG, "BleFramework: Creation of _instance");
                    _instance = new BleFramework();
                }
            }
        }

        return _instance;
    }
    */

    /*
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT
                && resultCode == Activity.RESULT_CANCELED) {
            //finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Log.d(TAG,"onCreate is being launched");


        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            Log.d(TAG,"onCreate: fail: missing FEATURE_BLUETOOTH_LE");
            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidInitialize, "Fail: missing FEATURE_BLUETOOTH_LE");
            //finish();
        }

        final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        _mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (_mBluetoothAdapter == null)
        {
            Log.d(TAG,"onCreate: fail: _mBluetoothAdapter is null");
            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidInitialize, "Fail: Context.BLUETOOTH_SERVICE");
            //finish();
            return;
        }

        Log.d(TAG,"onCreate: _mBluetoothAdapter correctly initialized");
        Intent gattServiceIntent = new Intent(BleFramework.this, RBLService.class);
        bindService(gattServiceIntent, _mServiceConnection, BIND_AUTO_CREATE);

        Log.d(TAG,"onCreate: sending BLEUnityMessageName_OnBleDidInitialize success");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG,"onStop: unregisterReceiver");
        _unityActivity.unregisterReceiver(_mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"onDestroy: unbindService");
        if (_mServiceConnection != null)
            unbindService(_mServiceConnection);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG,"onResume:");
        if (!_mBluetoothAdapter.isEnabled())
        {
            Log.d(TAG,"onResume: startActivityForResult: REQUEST_ENABLE_BT");
            Intent enableBtIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        Log.d(TAG,"onResume: registerReceiver");
        _unityActivity.registerReceiver(_mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }
    */

    /*
    Public methods that can be directly called by Unity
    */
    public void _InitBLEFramework()
    {
        System.out.println("Android Executing: _InitBLEFramework");

        if (!_unityActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            Log.d(TAG,"onCreate: fail: missing FEATURE_BLUETOOTH_LE");
            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidInitialize, "Fail: missing FEATURE_BLUETOOTH_LE");
            //finish();
            return;
        }

        final BluetoothManager mBluetoothManager = (BluetoothManager) _unityActivity.getSystemService(Context.BLUETOOTH_SERVICE);
        _mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (_mBluetoothAdapter == null)
        {
            Log.d(TAG,"onCreate: fail: _mBluetoothAdapter is null");
            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidInitialize, "Fail: Context.BLUETOOTH_SERVICE");
            //finish();
            return;
        }

        registerBleUpdatesReceiver();

        Log.d(TAG,"onCreate: _mBluetoothAdapter correctly initialized");
        UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidInitialize, "Success");

    }

    public void _ScanForPeripherals()
    {
        Log.d(TAG, "_ScanForPeripherals: Launching scanLeDevice");
        scanLeDevice();
    }

    public boolean _IsDeviceConnected()
    {
        Log.d(TAG,"_IsDeviceConnected");
        return _connState;
    }

    public boolean _SearchDeviceDidFinish()
    {
        Log.d(TAG,"_SearchDeviceDidFinish");
        return !_searchingDevice;
    }

    public String _GetListOfDevices()
    {
        String jsonListString;

        if (_mDevice.size() > 0)
        {
            Log.d(TAG,"_GetListOfDevices");
            String[] uuidsArray = new String[_mDevice.size()];

            for (int i = 0; i < _mDevice.size(); i++)
            {

                BluetoothDevice bd = _mDevice.get(i);
                /*
                ParcelUuid[] puiids = bd.getUuids();
                if (puuids!=null)
                {
                    String uuid = puiids[0].getUuid().toString();
                    Log.d(TAG, "scanLeDevice: Adding " +uuid+" to array");
                    uuidsArray[i] = uuid;
                }
                */

                uuidsArray[i] = bd.getAddress();
            }
            Log.d(TAG, "_GetListOfDevices: Building JSONArray");
            JSONArray uuidsJSON = new JSONArray(Arrays.asList(uuidsArray));
            Log.d(TAG, "_GetListOfDevices: Building JSONObject");
            JSONObject dataUuidsJSON = new JSONObject();

            try
            {
                Log.d(TAG, "_GetListOfDevices: Try inserting uuuidsJSON array in the JSONObject");
                dataUuidsJSON.put("data", uuidsJSON);
            }
            catch (JSONException e)
            {
                Log.e(TAG, "_GetListOfDevices: JSONException");
                e.printStackTrace();
            }

            jsonListString = dataUuidsJSON.toString();

            Log.d(TAG, "_GetListOfDevices: sending found devices in JSON: " + jsonListString);

        }
        else
        {
            jsonListString = "NO DEVICE FOUND";
            Log.d(TAG, "_GetListOfDevices: no device was found");
        }

        return jsonListString;
    }

    public boolean _ConnectPeripheralAtIndex(int peripheralIndex)
    {
        Log.d(TAG,"_ConnectPeripheralAtIndex: " + peripheralIndex);
        BluetoothDevice device = _mDevice.get(peripheralIndex);

        _mDeviceAddress = device.getAddress();
        _mDeviceName = device.getName();

        Intent gattServiceIntent = new Intent(_unityActivity, RBLService.class);
        _unityActivity.bindService(gattServiceIntent, _mServiceConnection, _unityActivity.BIND_AUTO_CREATE);

        return true;
    }

    public boolean _ConnectPeripheral(String peripheralID)
    {
        Log.d(TAG,"_ConnectPeripheral: " + peripheralID);

        for (BluetoothDevice device : _mDevice)
        {
            if (device.getAddress().equals(peripheralID))
            {
                _mDeviceAddress = device.getAddress();
                _mDeviceName = device.getName();

                Intent gattServiceIntent = new Intent(_unityActivity, RBLService.class);
                _unityActivity.bindService(gattServiceIntent, _mServiceConnection, _unityActivity.BIND_AUTO_CREATE);

                return true;
            }
        }

        return false;
    }

    public byte[] _GetData()
    {
        Log.d(TAG,"_GetData: "+ _dataRx);
        return _dataRx;
    }

    public void _SendData(byte[] data)
    {
        Log.d(TAG,"_SendData: ");

        BluetoothGattCharacteristic characteristic = _map.get(RBLService.UUID_BLE_SHIELD_TX);
        Log.d(TAG, "Set data in the _characteristicTx");
        byte[] tx = hexStringToByteArray("fefefe");
        characteristic.setValue(tx);

        Log.d(TAG, "Write _characteristicTx in the _mBluetoothLeService: " + tx[0] + " " + tx[1] + " " + tx[2]);
        if (_mBluetoothLeService==null)
        {
            Log.d(TAG, "_mBluetoothLeService is null");
        }
        _mBluetoothLeService.writeCharacteristic(characteristic);

    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
