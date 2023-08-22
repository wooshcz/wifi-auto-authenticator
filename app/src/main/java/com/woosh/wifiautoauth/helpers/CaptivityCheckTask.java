package com.woosh.wifiautoauth.helpers;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.woosh.wifiautoauth.BuildConfig;
import com.woosh.wifiautoauth.background.BackgroundTaskCallback;
import com.woosh.wifiautoauth.response.RedirectCheckResponse;
import com.woosh.wifiautoauth.utils.Constants;
import com.woosh.wifiautoauth.utils.NetworkTools;
import com.woosh.wifiautoauth.utils.ResultHolder;
import com.woosh.wifiautoauth.utils.Util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CaptivityCheckTask {

    private final Executor executor;
    private final Handler resultHandler;
    private final WeakReference<Context> weakContext;

    public CaptivityCheckTask(Context ctx, Executor executor, Handler resultHandler) {
        this.weakContext = new WeakReference<>(ctx);
        this.executor = executor;
        this.resultHandler = resultHandler;
    }

    public void doRedirectCheck(final BackgroundTaskCallback<RedirectCheckResponse> callback) {
        executor.execute(() -> {
            toggleMobileData(false);
            ResultHolder<RedirectCheckResponse> redirectCheckResultHolder = doRedirectCheckSync();
            toggleMobileData(true);
            notifyResult(redirectCheckResultHolder, callback);
        });
    }

    public ResultHolder<RedirectCheckResponse> doRedirectCheckSync() {
        HttpURLConnection urlConnection = null;
        try {
            URL url;
            if (BuildConfig.MOCK_REMOTE_ENDPOINTS) {
                url = new URL(Constants.MOCK_TEST_URL);
            } else {
                url = new URL(Constants.TEST_URL);
            }
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setInstanceFollowRedirects(false);
            urlConnection.setConnectTimeout(Constants.NET_TIMEOUT);
            urlConnection.setReadTimeout(Constants.NET_TIMEOUT);
            urlConnection.setUseCaches(false);
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            String content = Util.streamToString(in);
            String detectedUrl = parseForUrl(content);
            Util.addToDebugLog("CaptivityCheckTask.doRedirectCheckSync() - detectedURL: " + detectedUrl);
            if (urlConnection.getResponseCode() != 204) {
                // We ARE redirected - reponse should be 204
                // We will record the portalUrl
                Constants.PREF_PORTAL_NEW = detectedUrl;
                Constants.CAPTIVITY_DETECTED = true;
                return new ResultHolder.Success<>(new RedirectCheckResponse(detectedUrl, true));
            } else {
                // We are not redirected
                Constants.CAPTIVITY_DETECTED = false;
                return new ResultHolder.Success<>(new RedirectCheckResponse(null, false));
            }
        } catch (IOException e) {
            Log.e(Constants.DEBUG_TAG, "CaptivityCheckTask.doRedirectCheckSync()", e);
            e.printStackTrace();
            return null;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private void notifyResult(final ResultHolder<RedirectCheckResponse> resultHolder, final BackgroundTaskCallback<RedirectCheckResponse> callback) {
        resultHandler.post(() -> callback.onComplete(resultHolder));
    }

    private void toggleMobileData(boolean enable) {
        final Context context = weakContext.get();
        if (context != null) {
            boolean dataEnabled = NetworkTools.getMobileDataState(context);
            boolean isRooted = Util.isDeviceRooted();
            if (isRooted) {
                if (dataEnabled && !enable) {
                    Util.addToDebugLog("CaptivityCheckTask.toggleMobileData() - Turning MobileData off");
                    NetworkTools.setMobileDataState(context, false, false);
                }
                if (!dataEnabled && enable) {
                    Util.addToDebugLog("CaptivityCheckTask.toggleMobileData() - Turning MobileData on");
                    NetworkTools.setMobileDataState(context, true, false);
                }
            }
        }
    }

    private String parseForUrl(String content) {
        if (content.contains("URL=")) {
            Pattern p = Pattern.compile("([htps]+://[a-zA-Z0-9\\-.]+/)");
            Matcher m = p.matcher(content);
            if (m.find() && m.groupCount() > 0) { // Find each match in turn; String can't do this.
                return m.group(1); // Access a submatch group; String can't do this.
            }
        }
        return null;
    }
}
