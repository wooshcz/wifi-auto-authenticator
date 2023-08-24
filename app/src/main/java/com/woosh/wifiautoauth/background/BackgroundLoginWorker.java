package com.woosh.wifiautoauth.background;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.os.HandlerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.woosh.wifiautoauth.helpers.CaptivityCheckTask;
import com.woosh.wifiautoauth.helpers.LoginLogoutTask;
import com.woosh.wifiautoauth.response.LoginLogoutResponse;
import com.woosh.wifiautoauth.response.RedirectCheckResponse;
import com.woosh.wifiautoauth.utils.Constants;
import com.woosh.wifiautoauth.utils.NetworkTools;
import com.woosh.wifiautoauth.utils.ResultHolder;
import com.woosh.wifiautoauth.utils.Util;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackgroundLoginWorker extends Worker {

    private final Context ctx;
    ExecutorService executorService;
    private final Handler mainThreadHandler;

    public BackgroundLoginWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        ctx = context;
        executorService = Executors.newFixedThreadPool(1);
        mainThreadHandler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    @NonNull
    @Override
    public Result doWork() {
        Util.addToDebugLog("BackgroundLoginWorker.doWork() called!");
        backgroundLogin();
        return Result.success();
    }

    private void backgroundLogin() {
        Util.addToDebugLog("BackgroundLoginWorker.backgroundLogin()");
        Constants.loadPrefs(ctx);
        if (!Constants.PREF_AUTOLOGIN) {
            Util.addToDebugLog("BackgroundLoginWorker.backgroundLogin() - autologin is switched off");
            return;
        }
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (!NetworkTools.isOnWifi(wm)) {
            Util.addToDebugLog("BackgroundLoginWorker.backgroundLogin() - WiFi is not active");
            return;
        }
        CaptivityCheckTask captivityCheckTask = new CaptivityCheckTask(ctx.getApplicationContext(), executorService, mainThreadHandler);
        captivityCheckTask.doRedirectCheck(checkResult -> {
            if (checkResult instanceof ResultHolder.Success) {
                boolean isCaptivePortal = ((ResultHolder.Success<RedirectCheckResponse>) checkResult).data.isCaptivePortalDetected();
                String detectedUrl = ((ResultHolder.Success<RedirectCheckResponse>) checkResult).data.getDetectedUrl();
                Util.addToDebugLog("BackgroundLoginWorker:backgroundLogin() - CaptivityCheck completed");
                Util.addToDebugLog("BackgroundLoginWorker:backgroundLogin() - Captivity detected = " + isCaptivePortal);
                if (isCaptivePortal && null != detectedUrl) {
                    HashMap<String, String> params = new HashMap<>();
                    params.put("username", Constants.PREF_USERNAME);
                    params.put("password", Constants.PREF_PASSWD);
                    params.put("type", "login");
                    LoginLogoutTask loginLogoutTask = new LoginLogoutTask(getApplicationContext(), executorService, mainThreadHandler, params);
                    loginLogoutTask.doLoginLogout(loginResult -> {
                        if (loginResult instanceof ResultHolder.Success) {
                            int respCode = ((ResultHolder.Success<LoginLogoutResponse>) loginResult).data.getResponse();
                            Util.addToDebugLog("BackgroundLoginWorker:backgroundLogin() - Login completed");
                            Util.addToDebugLog("BackgroundLoginWorker:backgroundLogin() - Response code = " + respCode);
                        } else {
                            Util.addToDebugLog("BackgroundLoginWorker:backgroundLogin() - Error!");
                        }
                    });
                }
            } else {
                Util.addToDebugLog("BackgroundLoginWorker:backgroundLogin() - Captivity was not detected or detectedUrl is null");
            }
        });
    }
}