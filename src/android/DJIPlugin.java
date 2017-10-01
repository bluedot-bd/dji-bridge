package io.cordova.hellocordova;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;
import java.util.Date;


import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Looper;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;

import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import dji.sdk.products.Aircraft;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.battery.Battery;
import dji.common.battery.AggregationState;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.gimbal.GimbalState;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.products.HandHeld;
import dji.sdk.sdkmanager.DJISDKManager;



/**
* This class echoes a string called from JavaScript.
*/
public class DJIPlugin extends CordovaPlugin {

    private static final String TAG = DJIPlugin.class.getName();

    Context appContext;
    private static BaseProduct mProduct;

    boolean productConnected;

    FlightController fc = null;
    Gimbal gc = null;
    Battery b = null;

    private double lat = 0;
    private double lon = 0;
    private float alt = 0;

    private double yaw = 0;
    private double pitch = 0;
    private double roll = 0;
    private double gimbalYaw = 0;
    private double gimbalPitch = 0;
    private double gimbalRoll = 0;

    private int battery = 0;
    private boolean isFlying = false;
    private int gpsSatellites = 0;



    public static CallbackContext attitudeCallback = null;
    public static CallbackContext locationCallback = null;


    public void initialize(CordovaInterface cordova, CordovaWebView webView){
        super.initialize(cordova,webView);
        Log.d(TAG, "Intializing DJIPlugin");



        appContext = this.cordova.getActivity().getApplicationContext();
        DJISDKManager.getInstance().registerApp(appContext, mDJISDKManagerCallback);


//maybe need something for permissions

//one for SDK Manager
        String [] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.READ_PHONE_STATE,
        };

        cordova.requestPermissions(this, 0, permissions);

    }



    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("echo")) {
            String message = args.getString(0);
            this.echo(message, callbackContext);
            return true;
        } else if(action.equals("getDate")) {
            final PluginResult result = new PluginResult(PluginResult.Status.OK, (new Date()).toString());
           callbackContext.sendPluginResult(result);
           return true;
        } else if(action.equals("attachToDevice")){
            Log.d(TAG, "Checking Device");
            if(productConnected == true){
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, ("Product Connected")));
            } else {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, ("Product Disconnected")));
            }
            
        } else if(action.equals("getAttitude")){
            JSONObject attJson = new JSONObject();
            attJson.put("yaw", String.valueOf(yaw));
            attJson.put("pitch", String.valueOf(pitch));
            attJson.put("roll", String.valueOf(roll));
            attJson.put("gimbalYaw", String.valueOf(gimbalYaw));
            attJson.put("gimbalPitch", String.valueOf(gimbalPitch));
            attJson.put("gimbalRoll", String.valueOf(gimbalRoll));
            PluginResult att = new PluginResult(PluginResult.Status.OK, attJson.toString());
            //att.setKeepCallback(true);
            callbackContext.sendPluginResult(att);

        } else if(action.equals("getLocation")){
            JSONObject locJson = new JSONObject();
            locJson.put("alt", String.valueOf(alt));
            locJson.put("lat", String.valueOf(lat));
            locJson.put("lon", String.valueOf(lon));
            PluginResult loc = new PluginResult(PluginResult.Status.OK, locJson.toString());
            //loc.setKeepCallback(true);
            callbackContext.sendPluginResult(loc);

        } else if(action.equals("getStatus")){
            JSONObject statJson = new JSONObject();
            statJson.put("battery", String.valueOf(battery));
            statJson.put("gpsSatellites", String.valueOf(gpsSatellites));
            statJson.put("isFlying", String.valueOf(isFlying));
            PluginResult stat = new PluginResult(PluginResult.Status.OK, statJson.toString());
            //stat.setKeepCallback(true);
            callbackContext.sendPluginResult(stat);
            
        }else if(action.equals("jurg")){
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, ("schwing")));

        } else {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, ("other")));
        }
        return false;
    }


    private void echo(String message, CallbackContext callbackContext) {
        if (message != null && message.length() > 0) {
            Log.d(TAG, message);
            callbackContext.success(message + "win");
        } else {
            callbackContext.error("Expected one non-empty string argument.");
        }
    }





    private DJISDKManager.SDKManagerCallback mDJISDKManagerCallback = new DJISDKManager.SDKManagerCallback() {

        //Listens to the SDK registration result
        @Override
        public void onRegister(DJIError error) {

            if(error == DJISDKError.REGISTRATION_SUCCESS) {
                Log.d(TAG, "Register Success");
                productConnected = true;
                DJISDKManager.getInstance().startConnectionToProduct();

                fc = DJIProduct.getFlightControllerInstance();
                if (fc != null) {
                    fc.setStateCallback(new FlightControllerState.Callback() {
                        @Override
                        public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                            alt = flightControllerState.getAircraftLocation().getAltitude();
                            lat = flightControllerState.getAircraftLocation().getLatitude();
                            lon = flightControllerState.getAircraftLocation().getLongitude();
                            yaw = flightControllerState.getAttitude().yaw;
                            pitch = flightControllerState.getAttitude().pitch;
                            roll = flightControllerState.getAttitude().roll;
                            isFlying = flightControllerState.isFlying();
                            gpsSatellites = flightControllerState.getSatelliteCount();

                        }
                    });
                }

                gc = DJIProduct.getGimbalInstance();
                if (gc != null) {
                    gc.setStateCallback(new GimbalState.Callback() {
                        @Override
                        public void onUpdate(@NonNull GimbalState gimbalState) {
                            gimbalYaw = gimbalState.getAttitudeInDegrees().getYaw();
                            gimbalPitch = gimbalState.getAttitudeInDegrees().getPitch();
                            gimbalRoll = gimbalState.getAttitudeInDegrees().getRoll();

                        }
                    });
                }

                b = DJIProduct.getBatteryInstance();
                if (b != null) {
                    b.setAggregationStateCallback(new AggregationState.Callback() {
                        @Override
                        public void onUpdate(@NonNull AggregationState batteryState) {
                            //MAVIC specific, only one battery
                            battery = batteryState.getBatteryOverviews()[0].getChargeRemainingInPercent();
                        }
                    });
                }

            } else {
                Log.d(TAG, "Register sdk fails, check network is available");
                productConnected = false;
            }
            Log.e("TAG", error.toString());
        }

        //Listens to the connected product changing, including two parts, component changing or product connection changing.
        @Override
        public void onProductChange(BaseProduct oldProduct, BaseProduct newProduct) {

            mProduct = newProduct;
            if(mProduct != null) {
                mProduct.setBaseProductListener(mDJIBaseProductListener);
            }

            Log.d(TAG, "Product Change");
        }
    };

    private BaseProduct.BaseProductListener mDJIBaseProductListener = new BaseProduct.BaseProductListener() {

        @Override
        public void onComponentChange(BaseProduct.ComponentKey key, BaseComponent oldComponent, BaseComponent newComponent) {

            if(newComponent != null) {
                newComponent.setComponentListener(mDJIComponentListener);
            }
             Log.d(TAG, "Component Change");
        }

        @Override
        public void onConnectivityChange(boolean isConnected) {

            Log.d(TAG, "BaseProductListener: Connection Change: " + String.valueOf(isConnected));
        }

    };

    private BaseComponent.ComponentListener mDJIComponentListener = new BaseComponent.ComponentListener() {

        @Override
        public void onConnectivityChange(boolean isConnected) {
            Log.d(TAG, "ComponentListener: Connection Change: " + String.valueOf(isConnected));
        }

    };


}




