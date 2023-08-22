package com.woosh.wifiautoauth.helpers;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.woosh.wifiautoauth.background.BackgroundTaskCallback;
import com.woosh.wifiautoauth.response.LoginLogoutResponse;
import com.woosh.wifiautoauth.utils.Constants;
import com.woosh.wifiautoauth.utils.NetworkTools;
import com.woosh.wifiautoauth.utils.ResultHolder;
import com.woosh.wifiautoauth.utils.Util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class LoginLogoutTask {

    private final HashMap<String, String> params;
    private final Executor executor;
    private final Handler resultHandler;
    private final WeakReference<Context> weakContext;

    public LoginLogoutTask(Context ctx, Executor executor, Handler resultHandler, HashMap<String, String> params) {
        this.weakContext = new WeakReference<>(ctx);
        this.executor = executor;
        this.resultHandler = resultHandler;
        this.params = params;
    }

    private static void disableSSLCertificateChecking() {
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
            }
        }};

        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public void doLoginLogout(final BackgroundTaskCallback<LoginLogoutResponse> callback) {
        executor.execute(() -> {

            toggleMobileData(false);
            ResultHolder<LoginLogoutResponse> loginLogoutResultHolder = doLoginLogoutSync();
            toggleMobileData(true);
            notifyResult(loginLogoutResultHolder, callback);
        });
    }

    private ResultHolder<LoginLogoutResponse> doLoginLogoutSync() {
        String path = "", content;
        int resp = 0;
        Uri.Builder builder = new Uri.Builder();
        if ("login".equals(params.get("type"))) {
            path = "login.html";
            builder.appendQueryParameter("buttonClicked", "4")
                    .appendQueryParameter("err_flag", "0")
                    .appendQueryParameter("err_msg", "")
                    .appendQueryParameter("info_flag", "0")
                    .appendQueryParameter("info_msg", "")
                    .appendQueryParameter("redirect_url", "")
                    .appendQueryParameter("username", params.get("username"))
                    .appendQueryParameter("password", params.get("password"));
        } else if ("logout".equals(params.get("type"))) {
            path = "logout.html";
            builder.appendQueryParameter("userStatus", "1")
                    .appendQueryParameter("err_flag", "0")
                    .appendQueryParameter("err_msg", "");
        }
        if (path.length() > 0 && Constants.PREF_PORTAL.length() > 0) {
            try {
                URL url = new URL(Constants.PREF_PORTAL + path);
                if (url.getProtocol().equals("https")) {
                    disableSSLCertificateChecking();
                    HttpsURLConnection urlConnection = null;
                    try {
                        urlConnection = (HttpsURLConnection) url.openConnection();
                        urlConnection.setDoOutput(true);
                        urlConnection.setDoInput(true);
                        urlConnection.setConnectTimeout(Constants.NET_TIMEOUT);
                        urlConnection.setReadTimeout(Constants.NET_TIMEOUT);
                        urlConnection.setRequestMethod("POST");
                        String query = builder.build().getEncodedQuery();
                        OutputStream os = urlConnection.getOutputStream();
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
                        writer.write(query);
                        writer.flush();
                        writer.close();
                        os.close();
                        InputStream in = urlConnection.getInputStream();
                        content = Util.streamToString(in);
                        resp = urlConnection.getResponseCode();
                    } catch (IOException e) {
                        Log.e(Constants.DEBUG_TAG, "LoginLogoutTask.doLoginLogoutSync()", e);
                        return new ResultHolder.Error<>(e);
                    } finally {
                        if (urlConnection != null) {
                            urlConnection.disconnect();
                        }
                    }
                } else {
                    HttpURLConnection urlConnection = null;
                    try {
                        urlConnection = (HttpURLConnection) url.openConnection();
                        urlConnection.setDoOutput(true);
                        urlConnection.setDoInput(true);
                        urlConnection.setConnectTimeout(Constants.NET_TIMEOUT);
                        urlConnection.setReadTimeout(Constants.NET_TIMEOUT);
                        urlConnection.setRequestMethod("POST");
                        String query = builder.build().getEncodedQuery();
                        OutputStream os = urlConnection.getOutputStream();
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
                        writer.write(query);
                        writer.flush();
                        writer.close();
                        os.close();
                        InputStream in = urlConnection.getInputStream();
                        content = Util.streamToString(in);
                        resp = urlConnection.getResponseCode();
                    } catch (IOException e) {
                        Log.e(Constants.DEBUG_TAG, "LoginLogoutTask.doLoginLogoutSync()", e);
                        return new ResultHolder.Error<>(e);
                    } finally {
                        if (urlConnection != null) {
                            urlConnection.disconnect();
                        }
                    }
                }
                if (!content.isEmpty()) {
                    if (failedLoginDetected(content)) {
                        resp = -3;
                    }
                }
            } catch (MalformedURLException e) {
                Log.e(Constants.DEBUG_TAG, "LoginLogoutTask.doLoginLogoutSync()", e);
                return new ResultHolder.Error<>(e);
            }
        }
        return new ResultHolder.Success<>(new LoginLogoutResponse(resp));
    }

    private void notifyResult(final ResultHolder<LoginLogoutResponse> resultHolder, final BackgroundTaskCallback<LoginLogoutResponse> callback) {
        resultHandler.post(() -> callback.onComplete(resultHolder));
    }

    private void toggleMobileData(boolean enable) {
        final Context context = weakContext.get();
        if (context != null) {
            boolean dataEnabled = NetworkTools.getMobileDataState(context);
            boolean isRooted = Util.isDeviceRooted();
            if (isRooted) {
                if (dataEnabled && !enable) {
                    Util.addToDebugLog("LoginLogoutTask.toggleMobileData() - Turning MobileData off");
                    NetworkTools.setMobileDataState(context, false, false);
                }
                if (!dataEnabled && enable) {
                    Util.addToDebugLog("LoginLogoutTask.toggleMobileData() - Turning MobileData on");
                    NetworkTools.setMobileDataState(context, true, false);
                }
            }
        }
    }

    private boolean failedLoginDetected(String content) {
        int value = 0;
        if (content.contains("Login Error")) {
            Pattern p = Pattern.compile("\"err_flag\".+?VALUE=\"([0-9]+)\">");
            Matcher m = p.matcher(content);
            if (m.find() && m.groupCount() > 0) { // Find each match in turn; String can't do this.
                value = Integer.parseInt(Objects.requireNonNull(m.group(1))); // Access a submatch group; String can't do this.
            }
        }
        return value == 1;
    }
}
