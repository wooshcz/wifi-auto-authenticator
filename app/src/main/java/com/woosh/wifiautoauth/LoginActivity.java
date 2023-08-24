package com.woosh.wifiautoauth;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.os.HandlerCompat;
import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.snackbar.Snackbar;
import com.woosh.wifiautoauth.background.BackgroundLoginWorker;
import com.woosh.wifiautoauth.helpers.CaptivityCheckTask;
import com.woosh.wifiautoauth.helpers.LoginLogoutTask;
import com.woosh.wifiautoauth.response.LoginLogoutResponse;
import com.woosh.wifiautoauth.response.RedirectCheckResponse;
import com.woosh.wifiautoauth.utils.Constants;
import com.woosh.wifiautoauth.utils.NetworkTools;
import com.woosh.wifiautoauth.utils.ResultHolder;
import com.woosh.wifiautoauth.utils.Util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// login screen class for Web Login Screen
public class LoginActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    // Other references
    private static int mColorWarn, mColorGood;
    private static WifiManager wm;
    // Preferences, listener
    private SharedPreferences sp;
    private SharedPreferences.OnSharedPreferenceChangeListener spChanged;
    // UI references.
    private EditText mUsername;
    private EditText mPassword;
    private View mLoginFormView;
    private TextView mStateText;
    private ImageView mStateImg;
    private SwitchCompat mAutologinToggle;
    private CoordinatorLayout mParentView;
    private SwipeRefreshLayout mSwipeLayout;
    private Button mSignInButton;
    private Button mSignOutButton;
    private ObjectAnimator oa;
    private ExecutorService executorService;
    private Handler mainThreadHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle("");
            setSupportActionBar(toolbar);
        }

        mColorGood = ContextCompat.getColor(getApplicationContext(), R.color.colorAccentGood);
        mColorWarn = ContextCompat.getColor(getApplicationContext(), R.color.colorAccentWarn);

        mSignInButton = findViewById(R.id.login_button);
        mSignInButton.setOnClickListener(view -> attemptLogin());
        mSignOutButton = findViewById(R.id.logout_button);
        mSignOutButton.setOnClickListener(view -> attemptLogout());

        // Set up the login form.
        mParentView = findViewById(R.id.parent);
        mLoginFormView = findViewById(R.id.login_form);
        mStateText = findViewById(R.id.state_text);
        mStateImg = findViewById(R.id.state_img);
        mSwipeLayout = findViewById(R.id.swipe_refresh_layout);
        mSwipeLayout.setOnRefreshListener(this);
        mUsername = findViewById(R.id.username);
        mPassword = findViewById(R.id.password);

        mAutologinToggle = findViewById(R.id.autologin_toggle);
        mAutologinToggle.setOnCheckedChangeListener((compoundButton, b) -> {
            if (Constants.PREF_AUTOLOGIN != b) {
                Constants.PREF_AUTOLOGIN = b;
                SharedPreferences.Editor editor = sp.edit();
                editor.putBoolean("autologin", b);
                editor.apply();
                if (b) {
                    Snackbar.make(mParentView, getString(R.string.snack_autologin_switch_on), Snackbar.LENGTH_LONG).show();
                    mLoginFormView.setVisibility(View.GONE);
                    enqueueBackgroundWork();
                } else {
                    Snackbar.make(mParentView, getString(R.string.snack_autologin_switch_off), Snackbar.LENGTH_LONG).show();
                    mLoginFormView.setVisibility(View.VISIBLE);
                    WorkManager.getInstance(getApplicationContext()).cancelAllWork();
                }
            }
        });

        sp = PreferenceManager.getDefaultSharedPreferences(this);
        spChanged = (sharedPreferences, key) -> {
            Map<String, ?> prefs = sharedPreferences.getAll();
            Util.addToDebugLog("SharedPreferenceChangeListener: pref changed: " + key + ", new value: " + Objects.requireNonNull(prefs.get(key)));
        };
        sp.registerOnSharedPreferenceChangeListener(spChanged);

        wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        executorService = Executors.newFixedThreadPool(1);
        mainThreadHandler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    @Override
    public void onRequestPermissionsResult(int reqCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Util.addToDebugLog("onRequestPermissionsResult called");
        super.onRequestPermissionsResult(reqCode, permissions, grantResults);
        for (int i = 0; i < permissions.length; i++) {
            if (PackageManager.PERMISSION_GRANTED != grantResults[i]) {
                Util.addToDebugLog("onRequestPermissionsResult: some permissions have not been granted");
                break;
            }
        }
    }

    @Override
    public void onDestroy() {
        sp.unregisterOnSharedPreferenceChangeListener(spChanged);
        // Must always call the super method at the end.
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadUX();
        // reloadUX() calls loadPrefs() which loads actual preference values
        if (!Constants.PREF_EXPERT) {
            setButtonState(mSignInButton, false);
            setButtonState(mSignOutButton, false);
        }

        if (Constants.PREF_AUTOLOGIN) {
            enqueueBackgroundWork();
        }

        if (checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_WIFI_STATE}, 0x1000);
        }
    }

    @Override
    public void onRefresh() {
        manualChecker();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem dataSwitch = menu.findItem(R.id.action_data_switch);
        boolean isRooted = Util.isDeviceRooted();
        Util.addToDebugLog("LoginActivity.onPrepareOptionsMenu() - isRooted:" + isRooted);
        if (dataSwitch != null) {
            dataSwitch.setVisible(isRooted);
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        }
        if (id == R.id.action_data_switch) {
            boolean dataEnabled = NetworkTools.getMobileDataState(this);
            NetworkTools.setMobileDataState(this, !dataEnabled, true);
        }
        if (id == R.id.action_about) {
            AlertDialog.Builder builder = new AlertDialog.Builder(LoginActivity.this);
            builder.setTitle(R.string.app_name);
            builder.setMessage(String.format(Locale.getDefault(), getString(R.string.app_about), BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME));
            builder.setPositiveButton(R.string.action_close, (dialog, which) -> dialog.cancel());
            builder.show();
        }
        return super.onOptionsItemSelected(item);
    }

    // Enables/Disables the Buttons and bypasses the choice in ExpertMode
    private void setButtonState(Button but, boolean setenabled) {
        if (Constants.PREF_EXPERT) setenabled = true;
        if (but != null) {
            but.setEnabled(setenabled);
        }
    }

    // Reloads the GUI elements according to current conditions
    private void reloadUX() {
        Constants.loadPrefs(getApplicationContext());
        mUsername.setText(Constants.PREF_USERNAME);
        mPassword.setText(Constants.PREF_PASSWD);
        if (NetworkTools.isOnWifi(wm) && Constants.PREF_PORTAL.length() > 0 && Constants.CAPTIVITY_DETECTED != null) {
            if (Constants.CAPTIVITY_DETECTED) {
                setButtonState(mSignInButton, true);
                setButtonState(mSignOutButton, false);
                mStateImg.setImageResource(R.drawable.ic_state_redirect);
                mStateText.setText(R.string.message_signedout);
            } else {
                setButtonState(mSignInButton, false);
                setButtonState(mSignOutButton, true);
                mStateImg.setImageResource(R.drawable.ic_state_ok);
                mStateText.setText(R.string.message_signedin);
            }
        } else {
            setButtonState(mSignInButton, false);
            setButtonState(mSignOutButton, false);
        }

        if (Constants.PREF_AUTOLOGIN) {
            if (!mAutologinToggle.isChecked()) mAutologinToggle.setChecked(true);
            mLoginFormView.setVisibility(View.GONE);
        } else {
            if (mAutologinToggle.isChecked()) mAutologinToggle.setChecked(false);
            mLoginFormView.setVisibility(View.VISIBLE);
        }

        // Checks for filled-out settings
        if (Constants.PREF_USERNAME.equals("") || Constants.PREF_PASSWD.equals("")) {
            String snackMsg = getString(R.string.message_credentials_notset);
            Snackbar snackbar = Snackbar.make(mParentView, snackMsg, Snackbar.LENGTH_INDEFINITE);
            snackbar.setAction(getString(R.string.action_settings), view -> startActivity(new Intent(getApplicationContext(), SettingsActivity.class)));
            snackbar.setActionTextColor(mColorWarn);
            snackbar.show();
        }
    }

    // just a wrapper around the WorkManager().enqueue() call
    private void enqueueBackgroundWork() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build();
        PeriodicWorkRequest backgroundLoginRequest = new PeriodicWorkRequest.Builder(BackgroundLoginWorker.class, PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork("backgroundLoginReq", ExistingPeriodicWorkPolicy.KEEP, backgroundLoginRequest);
    }

    // callback handler for the LoginLogoutTask
    private void handleLoginLogoutCallback(LoginLogoutResponse response) {
        int resp = response.getResponse();
        if (mSwipeLayout != null) mSwipeLayout.setRefreshing(false);
        mStateImg.setAlpha(1.0f);
        mStateText.setAlpha(1.0f);
        // Response 200
        if (resp == 200) {
            setButtonState(mSignInButton, false);
            setButtonState(mSignOutButton, false);
            if (mParentView != null) {
                Snackbar snackbar = Snackbar.make(mParentView, getString(R.string.message_response_ok), Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        } else {
            String errMsg;
            if (resp == -3) {
                errMsg = getString(R.string.error_failed_login);
            } else if (resp == -2) {
                errMsg = getString(R.string.error_network_error);
            } else if (resp == -1) {
                errMsg = getString(R.string.error_cancelled);
            } else {
                errMsg = getString(R.string.error_unknown);
            }
            Util.addToDebugLog("LoginActivity.handleLoginLogoutCallback() - " + errMsg);
            if (mParentView != null) {
                Snackbar snackbar = Snackbar.make(mParentView, errMsg, Snackbar.LENGTH_INDEFINITE);
                snackbar.setAction(getString(R.string.action_retry), view -> {
                    mSwipeLayout.setRefreshing(true);
                    manualChecker();
                });
                snackbar.setActionTextColor(mColorWarn);
                snackbar.show();
            }
        }
        reloadUX();
    }

    // callback handler for the CaptivityCheckTask
    private void handleRedirectCheckerCallback(RedirectCheckResponse response) {
        if (mSwipeLayout != null) mSwipeLayout.setRefreshing(false);
        oa.cancel();
        mStateImg.clearAnimation();
        mStateImg.setRotation(0f);
        mStateImg.setAlpha(1.0f);
        mStateText.setAlpha(1.0f);
        boolean isRedirected = response.isCaptivePortalDetected();
        Util.addToDebugLog("LoginActivity.handleRedirectCheckerCallback() - redirect detected: " + isRedirected);
        if (isRedirected) {
            // We are behind walled connection
            if (mParentView != null) {
                // Show snackbar about the redirect
                Snackbar.make(mParentView, getString(R.string.message_signedout), Snackbar.LENGTH_LONG).show();
                mStateImg.setImageResource(R.drawable.ic_state_redirect);
                mStateText.setText(R.string.message_signedout);
                reloadUX();
            }

            // trigger autologin if it is turned on and the loginlogouttask is null
            if (Constants.PREF_AUTOLOGIN) {
                HashMap<String, String> params = new HashMap<>();
                params.put("username", Constants.PREF_USERNAME);
                params.put("password", Constants.PREF_PASSWD);
                params.put("type", "login");
                LoginLogoutTask loginLogoutTask = new LoginLogoutTask(getApplicationContext(), executorService, mainThreadHandler, params);
                loginLogoutTask.doLoginLogout(loginResult -> {
                    if (loginResult instanceof ResultHolder.Success) {
                        int respCode = ((ResultHolder.Success<LoginLogoutResponse>) loginResult).data.getResponse();
                        Util.addToDebugLog("LoginActivity:handleRedirectCheckerCallback() - Login completed");
                        Util.addToDebugLog("LoginActivity:handleRedirectCheckerCallback() - Response code = " + respCode);
                    } else {
                        Util.addToDebugLog("LoginActivity:handleRedirectCheckerCallback() - Error!");
                    }
                });
            }
        } else {
            // We are not walled.
            if (mParentView != null) {
                // Show snackbar - internet is working
                Snackbar snackbar = Snackbar.make(mParentView, getString(R.string.message_signedin), Snackbar.LENGTH_LONG);
                snackbar.setActionTextColor(mColorGood);
                snackbar.show();
                mStateImg.setImageResource(R.drawable.ic_state_ok);
                mStateText.setText(R.string.message_signedin);
                reloadUX();
            }
        }
    }

    private void manualChecker() {
        Constants.loadPrefs(getApplicationContext());
        if (Constants.PREF_USERNAME.equals("") || Constants.PREF_PASSWD.equals("")) {
            Snackbar.make(mParentView, getString(R.string.message_settings_notset), Snackbar.LENGTH_LONG).show();
            Util.addToDebugLog("LoginActivity:manualChecker() - Application settings have not been filled-out");
            mSwipeLayout.setRefreshing(false);
            return;
        }
        boolean isConnected = NetworkTools.isNetworkAvailable(getApplication());
        String snackMsg = "";
        boolean err = false;
        if (!isConnected) {
            snackMsg = getString(R.string.message_network_no);
            err = true;
        } else if (!NetworkTools.isOnWifi(wm)) {
            snackMsg = getString(R.string.message_network_wifi);
            err = true;
        }
        if (err) {
            Util.addToDebugLog("LoginActivity:manualChecker() - " + snackMsg);
            Snackbar snackbar = Snackbar.make(mParentView, snackMsg, Snackbar.LENGTH_INDEFINITE);
            snackbar.setAction(getString(R.string.action_retry), view -> {
                mSwipeLayout.setRefreshing(true);
                manualChecker();
            });
            snackbar.setActionTextColor(mColorWarn);
            snackbar.show();
            mSwipeLayout.setRefreshing(false);
        } else {
            mStateImg.setImageResource(R.drawable.ic_state_working);
            oa = ObjectAnimator.ofFloat(mStateImg, "rotation", 0f, 360f);
            oa.setDuration(1000);
            oa.setRepeatCount(ObjectAnimator.INFINITE);
            oa.start();
            mStateImg.setAlpha(1.0f);
            mStateText.setAlpha(1.0f);
            mStateText.setText(R.string.message_working);
            CaptivityCheckTask captivityCheckTask = new CaptivityCheckTask(getApplicationContext(), executorService, mainThreadHandler);
            captivityCheckTask.doRedirectCheck(checkResult -> {
                if (checkResult instanceof ResultHolder.Success) {
                    boolean isCaptivePortal = ((ResultHolder.Success<RedirectCheckResponse>) checkResult).data.isCaptivePortalDetected();
//                    String detectedUrl = ((ResultHolder.Success<RedirectCheckResponse>) checkResult).data.getDetectedUrl();
                    Util.addToDebugLog("LoginActivity:manualChecker() - CaptivityCheck complete");
                    Util.addToDebugLog("LoginActivity:manualChecker() - Captivity detected = " + isCaptivePortal);
                    handleRedirectCheckerCallback(((ResultHolder.Success<RedirectCheckResponse>) checkResult).data);
                } else {
                    Util.addToDebugLog("LoginActivity:manualChecker() - Error!");
                }
            });
        }
    }

    // Attempts to Sign Out
    private void attemptLogout() {
        mSwipeLayout.setRefreshing(true);
        mStateImg.setAlpha(0.5f);
        mStateText.setAlpha(0.5f);
        HashMap<String, String> params = new HashMap<>();
        params.put("type", "logout");
        LoginLogoutTask loginLogoutTask = new LoginLogoutTask(getApplicationContext(), executorService, mainThreadHandler, params);
        loginLogoutTask.doLoginLogout(logoutResult -> {
            if (logoutResult instanceof ResultHolder.Success) {
                int respCode = ((ResultHolder.Success<LoginLogoutResponse>) logoutResult).data.getResponse();
                Util.addToDebugLog("LoginActivity:attemptLogout() - Logout completed");
                Util.addToDebugLog("LoginActivity:attemptLogout() - Response code = " + respCode);
                handleLoginLogoutCallback(((ResultHolder.Success<LoginLogoutResponse>) logoutResult).data);
            } else {
                Util.addToDebugLog("LoginActivity:attemptLogout() - Error Happened");
            }
        });
    }

    // Attempts to Sign In
    private void attemptLogin() {

        // Reset errors.
        mUsername.setError(null);
        mPassword.setError(null);

        // Store values at the time of the login attempt.
        String username = mUsername.getText().toString();
        String password = mPassword.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a password.
        if (TextUtils.isEmpty(password)) {
            mPassword.setError(getString(R.string.error_field_required));
            focusView = mPassword;
            cancel = true;
        }

        // Check for a username.
        if (TextUtils.isEmpty(username)) {
            mUsername.setError(getString(R.string.error_field_required));
            focusView = mUsername;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login
            focusView.requestFocus();
        } else {
            mSwipeLayout.setRefreshing(true);
            mStateImg.setAlpha(0.5f);
            mStateText.setAlpha(0.5f);
            HashMap<String, String> params = new HashMap<>();
            params.put("username", username);
            params.put("password", password);
            params.put("type", "login");
            LoginLogoutTask loginLogoutTask = new LoginLogoutTask(getApplicationContext(), executorService, mainThreadHandler, params);
            loginLogoutTask.doLoginLogout(loginResult -> {
                if (loginResult instanceof ResultHolder.Success) {
                    int respCode = ((ResultHolder.Success<LoginLogoutResponse>) loginResult).data.getResponse();
                    Util.addToDebugLog("LoginActivity:attemptLogin() - Login completed");
                    Util.addToDebugLog("LoginActivity:attemptLogin() - Response code = " + respCode);
                    handleLoginLogoutCallback(((ResultHolder.Success<LoginLogoutResponse>) loginResult).data);
                } else {
                    Util.addToDebugLog("LoginActivity:attemptLogin() - Error!");
                }
            });
        }
    }
}
