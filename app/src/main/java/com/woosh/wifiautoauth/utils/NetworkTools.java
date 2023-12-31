package com.woosh.wifiautoauth.utils;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.woosh.wifiautoauth.R;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Created by woosh on 12.6.16.
 * Utilities
 */

public class NetworkTools {

    public static boolean isOnWifi(Context ctx) {
        ConnectivityManager connectivityManager = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network nw = connectivityManager.getActiveNetwork();
        if (nw == null) return false;
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
        return (actNw != null && actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
    }

    public static void setMobileDataState(Context ctx, boolean setMobileData, boolean makeToast) {

        String command;
        int toaststr;

        if (setMobileData) {
            command = Constants.COMMAND_DATA_ENABLE;
            toaststr = R.string.toast_data_switch_on;
        } else {
            command = Constants.COMMAND_DATA_DISABLE;
            toaststr = R.string.toast_data_switch_off;
        }

        try {
            Process su = Runtime.getRuntime().exec(Constants.COMMAND_SU);
            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());
            outputStream.writeBytes(command);
            outputStream.flush();
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            try {
                su.waitFor();
                if (makeToast) {
                    Toast.makeText(ctx, toaststr, Toast.LENGTH_SHORT).show();
                }
            } catch (InterruptedException e) {
                Log.e(Constants.DEBUG_TAG, "NetworkTools.setMobileDataState()", e);
                if (makeToast) {
                    Toast.makeText(ctx, e.toString(), Toast.LENGTH_SHORT).show();
                }
            }
            outputStream.close();
        } catch (IOException e) {
            Log.e(Constants.DEBUG_TAG, "NetworkTools.setMobileDataState()", e);
            if (makeToast) {
                Toast.makeText(ctx, e.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static boolean getMobileDataState(Context ctx) {
        try {
            TelephonyManager telephonyService = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            Method getMobileDataEnabledMethod = telephonyService.getClass().getDeclaredMethod("getDataEnabled");
            if (null != getMobileDataEnabledMethod) {
                return (Boolean) getMobileDataEnabledMethod.invoke(telephonyService);
            }
        } catch (Exception e) {
            Log.e(Constants.DEBUG_TAG, "NetworkTools.getMobileDataState() - Error getting mobile data state", e);
        }
        return false;
    }

    public static boolean isNetworkAvailable(Application application) {
        ConnectivityManager connectivityManager = (ConnectivityManager) application.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network nw = connectivityManager.getActiveNetwork();
        if (nw == null) return false;
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
        return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH));
    }
}
