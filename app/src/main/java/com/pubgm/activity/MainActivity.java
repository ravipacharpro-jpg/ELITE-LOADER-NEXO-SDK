package com.pubgm.activity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.widget.ImageView;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.pubgm.libhelper.GoogleIdentitySignIn;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.util.Date;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.pubgm.Login;
import com.pubgm.R;
import com.pubgm.adapter.RecyclerViewAdapter;
import com.pubgm.libhelper.DownloadZipAdapter;
import com.pubgm.floating.FloatLogo;
import com.pubgm.libhelper.ApkEnv;
import com.pubgm.libhelper.FileHelper;
import com.pubgm.libhelper.GameInstallManager;
import com.pubgm.utils.ActivityCompat;
import com.pubgm.utils.FLog;
import com.pubgm.utils.FPrefs;
import com.pubgm.utils.Shell;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static com.pubgm.Config.GAME_LIST_ICON;

import com.google.android.material.button.MaterialButton;

import android.os.Vibrator;

// BlackBoxCore Imports

@Obfuscate
public class MainActivity extends ActivityCompat {
    
    public static MainActivity instance;
    private Vibrator vibrator;
    private String daemonPath;
    public static String socket;
    public String CURRENT_PACKAGE = "";
    private LinearProgressIndicator progress;
    private RecyclerView recyclerView;
    private RecyclerViewAdapter adapter;
    String sFixCrash = Login.FixCrash();

    private Dialog verifyDialog;
    private View progressBarView;
    private TextView tvStatusText, tvStatusDetail, tvKeyText, tvErrorMessage;
    private LinearLayout progressContainer, statusContainer, deviceInfoContainer, keyContainer, successScreen, errorScreen;
    private TextView tvDeviceInfo, tvAndroidInfo, tvSDKInfo;
    private boolean verified = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    // ===== NEW XML VIEWS =====
    private TextView days, hours, minutes, seconds;
    private TextView deviceInfoTextView;
    private SwitchMaterial hideEsp;
    private TextView hideEspText;
    private Button startGame;
    private Button installGame, obbFixButton;
    private ImageView previousGameIcon, currentGameIcon, nextGameIcon;
    private LinearLayout carouselDots;
    private TextView mainGameVersion, licenseKeyText;
    private ImageButton licenseEye;
    private boolean licenseKeyVisible = false;
    private Button clearData;
    private ImageButton googleSignInButton;
    private View googleAccountChip;
    private ImageView googleAccountAvatar;
    private TextView googleAccountName, googleAccountEmail;
    private ProgressBar googleLoginProgress;
    private boolean googleAuthBusy = false;
    private String lastGoogleAvatarUrl = "";
    private Bitmap lastGoogleAvatarBitmap;
    private final FirebaseAuth.AuthStateListener googleAuthStateListener = auth -> {
        if (!googleAuthBusy) renderGoogleAccount(auth.getCurrentUser());
    };
    private MaterialButton btnSwitchMode;
    private boolean isAutoMode = false;
    private boolean modeSelected = false;
    private ImageView mainGameIcon;
    private TextView mainGameTitle, mainGameStatus;
    private boolean carouselAnimating = false;
    private String pendingObbPackage;
    private static final int REQUEST_SELECT_OBB = 6206;
    
    // ===== GAME DATA LIST =====
    private ArrayList<String> gameTitles = new ArrayList<>();
    private ArrayList<String> gamePackages = new ArrayList<>();
    private ArrayList<String> gameVersions = new ArrayList<>();
    private ArrayList<Integer> gameIcons = new ArrayList<>();
    private ArrayList<String> gameStatuses = new ArrayList<>();
    private int selectedGameIndex = 0;
    
    // ===== TIMER =====
    private long expiryTimeMillis = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private boolean isServiceRunning = false;
    
    // ===== GAME PACKAGE FROM JSON =====
    private String gamePackage = "com.pubg.imobile"; // Default
    
    // ORIGINAL BGMI METHOD - 100% UNCHANGED
    public void doInitRecycler() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> {

            ArrayList<Integer> imageValues = new ArrayList<>();
            ArrayList<String> titleValues = new ArrayList<>();
            ArrayList<String> versionValues = new ArrayList<>();
            ArrayList<String> statusValues = new ArrayList<>();
            ArrayList<String> packageValues = new ArrayList<>();
            ArrayList<String> genreValues = new ArrayList<>();
            ArrayList<String> regionValues = new ArrayList<>();

            String jsonString = loadJson("games.json");

            try {
                JSONArray jsonArray = new JSONArray(jsonString);
                gameTitles.clear();
                gamePackages.clear();
                gameVersions.clear();
                gameIcons.clear();
                gameStatuses.clear();

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject game = jsonArray.getJSONObject(i);
                    int icon = GAME_LIST_ICON[game.getInt("imageIndex")];
                    String title = game.getString("title");
                    String pkg = game.getString("package");
                    String status = game.getString("status");

                    imageValues.add(icon);
                    titleValues.add(title);
                    versionValues.add(game.getString("version"));
                    statusValues.add(status);
                    packageValues.add(pkg);
                    genreValues.add(game.getString("genre"));
                    regionValues.add(game.getString("region"));
                    
                    // Populate local lists for switching
                    gameTitles.add(title);
                    gameVersions.add(game.optString("version", "Version: unknown"));
                    gamePackages.add(pkg);
                    gameIcons.add(icon);
                    gameStatuses.add(status);
                }
                
                // Keep the exact selected package after Install / OBB Fix / UI refresh.
                if (!gamePackages.isEmpty()) {
                    String wanted = getPreferences(MODE_PRIVATE).getString("selected_game_package", gamePackage);
                    int found = gamePackages.indexOf(wanted);
                    selectVersion(found >= 0 ? found : Math.min(selectedGameIndex, gamePackages.size() - 1));
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
            adapter = new RecyclerViewAdapter(this, imageValues, titleValues, versionValues, statusValues, packageValues, genreValues, regionValues);
            if (recyclerView != null) {
                recyclerView.setAdapter(adapter);
            }
            if (progress != null) progress.setVisibility(View.GONE);
            
            updateMainGameUI();

        }, 300);
    }

    private void selectVersion(int index) {
        if (index < 0 || index >= gamePackages.size()) return;
        
        selectedGameIndex = index;
        gamePackage = gamePackages.get(index);
        getPreferences(MODE_PRIVATE).edit().putString("selected_game_package", gamePackage).apply();
        
        final String title = gameTitles.get(index);
        final String displayTitle = gamePackage.equals("com.pubg.imobile") ? "BGMI" : title;
        String configuredVersion = index < gameVersions.size() ? gameVersions.get(index) : "unknown";
        if (!configuredVersion.startsWith("Version:")) configuredVersion = "Version: " + configuredVersion;
        // Device-installed version is authoritative when available; JSON is only the fallback.
        try {
            android.content.pm.PackageInfo pkgInfo = getPackageManager().getPackageInfo(gamePackage, 0);
            if (pkgInfo.versionName != null && !pkgInfo.versionName.isEmpty()) configuredVersion = "Version: " + pkgInfo.versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        final String version = configuredVersion;
        final int iconRes = gameIcons.get(index);
        final String status = gameStatuses.get(index);

        runOnUiThread(() -> {
            // Force update UI elements
            if (mainGameTitle == null) mainGameTitle = findViewById(R.id.main_game_title);
            if (mainGameIcon == null) mainGameIcon = findViewById(R.id.main_game_icon);
            if (mainGameStatus == null) mainGameStatus = findViewById(R.id.main_game_status);

            if (mainGameTitle != null) {
                mainGameTitle.setText("Game: " + displayTitle);
                if (mainGameVersion != null) mainGameVersion.setText(version);
            }
            if (mainGameIcon != null) {
                mainGameIcon.setImageResource(iconRes);
            }
            if (mainGameStatus != null) {
                mainGameStatus.setText("Status: " + status);
                if (status != null && status.equalsIgnoreCase("Safe")) {
                    mainGameStatus.setTextColor(Color.parseColor("#10B981"));
                } else {
                    mainGameStatus.setTextColor(Color.parseColor("#EF4444"));
                }
            }
            updateMainGameUI();
            updateGameCarousel();
        });
    }
    
    public String loadJson(String fileName) {
        String json = null;

        try {
            File file = new File(getFilesDir(), fileName);
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                int size = fis.available();
                byte[] buffer = new byte[size];
                fis.read(buffer);
                fis.close();
                return new String(buffer, "UTF-8");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            File file = new File(getExternalFilesDir(null), fileName);
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                int size = fis.available();
                byte[] buffer = new byte[size];
                fis.read(buffer);
                fis.close();
                return new String(buffer, "UTF-8");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            InputStream is = getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }

        return json;
    }
    
    // Android 11+ protects /sdcard/Android/data from direct arbitrary writes.
    // This marker belongs to this host app, so keep it in private app storage.
    public void loadAssets() {
        File marker = new File(getFilesDir(), ".tyb");
        try (FileOutputStream fos = new FileOutputStream(marker)) {
            fos.write("DO NOT DELETE".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            android.util.Log.w("EliteToolLaunch", "Could not create app-private marker", e);
            // The marker is optional: do not abort all subsequent launcher setup.
        }

        daemonPath = getFilesDir().toString() + "/sock64";
        File f = new File(daemonPath);
        if (f.exists()) {
            f.setExecutable(true, false);
            f.setReadable(true, false);
            f.setWritable(true, false);
        }
        
        if (Shell.rootAccess()) {
            socket = "su -c " + daemonPath;
        } else {
            socket = daemonPath;
        }
    }

    public static MainActivity get() {
        return instance;
    }
    
    public static void goMain(Context context) {
        Intent i = new Intent(context, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(i);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(R.layout.activity_main);
        
        // ===== ORIGINAL CODE =====
        progress = findViewById(R.id.progress);
        recyclerView = findViewById(R.id.recyclerview);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setVisibility(View.GONE);
        }
        
        hideSystemUI();
        
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        
        // ===== NEW VIEWS =====
        days = findViewById(R.id.days);
        hours = findViewById(R.id.hours);
        minutes = findViewById(R.id.minutes);
        seconds = findViewById(R.id.seconds);
        deviceInfoTextView = findViewById(R.id.device_info);
        hideEsp = findViewById(R.id.hide_esp);
        hideEspText = findViewById(R.id.hide_esp_text);
        startGame = findViewById(R.id.start_game);
        installGame = findViewById(R.id.install_game);
                obbFixButton = findViewById(R.id.obb_fix);
        mainGameVersion = findViewById(R.id.game_version);
        licenseKeyText = findViewById(R.id.license_key_value);
        licenseEye = findViewById(R.id.license_eye);
        previousGameIcon = findViewById(R.id.game_prev_icon);
        currentGameIcon = findViewById(R.id.game_curr_icon);
        nextGameIcon = findViewById(R.id.game_next_icon);
        carouselDots = findViewById(R.id.game_dots);
                clearData = findViewById(R.id.clear_data);
        btnSwitchMode = findViewById(R.id.btn_switch_mode);
        googleSignInButton = findViewById(R.id.btn_google_sign_in);
        googleAccountChip = findViewById(R.id.google_profile_chip);
        googleAccountAvatar = findViewById(R.id.google_profile_avatar);
        googleAccountName = findViewById(R.id.google_profile_name);
        googleAccountEmail = findViewById(R.id.google_profile_email);
        googleLoginProgress = findViewById(R.id.google_signin_progress);
        if (googleSignInButton != null) {
            googleSignInButton.setOnClickListener(v -> beginGoogleSignIn());
        }
        if (googleAccountChip != null) {
            googleAccountChip.setOnClickListener(v -> showGoogleAccountMenu());
        }
        renderGoogleAccount(FirebaseAuth.getInstance().getCurrentUser());
        // Best effort virtual GMS provisioning; skips missing host GMS.
        com.pubgm.libhelper.GoogleServicesSetup.installIfAvailable();

        updateModeSelectionUI();
        
        mainGameIcon = findViewById(R.id.main_game_icon);
        mainGameTitle = findViewById(R.id.main_game_title);
        mainGameStatus = findViewById(R.id.main_game_status);
                        
        loadDeviceInfo();
        setupGameCarousel();
        // Fit all controls on short portrait displays without any vertical scrolling.
        View dash = findViewById(R.id.dashboard_root);
        if (dash != null) dash.post(this::fitDashboardToScreen);
        refreshLicenseKey();
        if (hideEsp != null) hideEsp.setChecked(FPrefs.with(this).readBoolean("hide_esp", false));
        startAnimations();
        
        // CHECK VERIFICATION
        SharedPreferences prefs = getSharedPreferences("auth_pref", MODE_PRIVATE);
        boolean isVerified = prefs.getBoolean("verified", false);
        long expiry = prefs.getLong("expiry", 0);
        
        // If we have EXP string but no timestamp, try parsing it now
        if (expiry == 0 && Login.EXP != null && !Login.EXP.isEmpty()) {
            expiry = Login.getExpiryTimestamp();
        }
        
        if (isVerified && expiry > (System.currentTimeMillis() / 1000) - 60) {
            verified = true;
            expiryTimeMillis = expiry * 1000;
            runOnUiThread(() -> {
                updateExpiryDisplay();
                startTimer();
            });
            doInitRecycler();
        } else {
            createVerifyDialog();
        }
        
        setupNewListeners();
        applyColorfulText();
    }

    // Google account UI is a separate Firebase session; it never changes the license or game login.
    private void beginGoogleSignIn() {
        if (googleAuthBusy) return;
        GoogleIdentitySignIn.start(this, new GoogleIdentitySignIn.Listener() {
            @Override public void onLoading(boolean loading) {
                googleAuthBusy = loading;
                if (googleLoginProgress != null) googleLoginProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
                if (googleSignInButton != null) {
                    googleSignInButton.setEnabled(!loading);
                    googleSignInButton.setAlpha(loading ? 0.22f : 1f);
                }
                if (googleAccountChip != null) googleAccountChip.setEnabled(!loading);
            }
            @Override public void onSuccess(@NonNull FirebaseUser user) {
                renderGoogleAccount(user);
                Toast.makeText(MainActivity.this, "Google Login Successful!", Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(@NonNull String message) {
                showGoogleError(message);
            }
            @Override public void onCancelled() {
                Toast.makeText(MainActivity.this, "Google login cancelled", Toast.LENGTH_SHORT).show();
                renderGoogleAccount(FirebaseAuth.getInstance().getCurrentUser());
            }
        });
    }

    private void showGoogleError(String message) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle("Google Login Failed")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Retry", (dialog, which) -> beginGoogleSignIn())
                .show();
    }

    private void renderGoogleAccount(FirebaseUser user) {
        if (googleAuthBusy || googleAccountChip == null || googleSignInButton == null) return;
        boolean connected = user != null;
        googleSignInButton.setVisibility(connected ? View.GONE : View.VISIBLE);
        googleAccountChip.setVisibility(connected ? View.VISIBLE : View.GONE);
        if (!connected) {
            lastGoogleAvatarUrl = "";
            lastGoogleAvatarBitmap = null;
            if (googleAccountAvatar != null) {
                googleAccountAvatar.setImageResource(R.drawable.ic_google_g);
                googleAccountAvatar.setPadding(dp(7), dp(7), dp(7), dp(7));
                googleAccountAvatar.setClipToOutline(false);
            }
            return;
        }
        String name = user.getDisplayName();
        String email = user.getEmail();
        googleAccountName.setText(name == null || name.trim().isEmpty() ? "Google Connected" : name);
        googleAccountEmail.setText(email == null ? "Firebase account connected" : email);
        googleAccountChip.setContentDescription("Google account: " +
                (email == null ? "connected" : email) + ". Tap for settings");
        loadGoogleAvatar(user);
    }

    // Fetch only the current signed-in user's HTTPS profile photo; no permission or disk cache.
    private void loadGoogleAvatar(FirebaseUser user) {
        if (googleAccountAvatar == null) return;
        Uri photo = user.getPhotoUrl();
        String url = photo == null ? "" : photo.toString();
        if (url.equals(lastGoogleAvatarUrl)) {
            if (lastGoogleAvatarBitmap != null) showAvatar(lastGoogleAvatarBitmap);
            return;
        }
        lastGoogleAvatarUrl = url;
        lastGoogleAvatarBitmap = null;
        googleAccountAvatar.setImageResource(R.drawable.ic_google_g);
        googleAccountAvatar.setPadding(dp(7), dp(7), dp(7), dp(7));
        if (!url.startsWith("https://")) return;
        final String expectedUid = user.getUid();
        new Thread(() -> {
            HttpURLConnection connection = null;
            Bitmap bitmap = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(true);
                try (InputStream in = connection.getInputStream();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int count, total = 0;
                    while ((count = in.read(buffer)) != -1 && total <= 512 * 1024) {
                        total += count;
                        if (total > 512 * 1024) break;
                        out.write(buffer, 0, count);
                    }
                    if (total <= 512 * 1024) {
                        byte[] bytes = out.toByteArray();
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inSampleSize = 2;
                        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                    }
                }
            } catch (Exception ignored) {
                // Keep the Google fallback icon when offline or avatar cannot load.
            } finally {
                if (connection != null) connection.disconnect();
            }
            final Bitmap result = bitmap;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || !url.equals(lastGoogleAvatarUrl)) return;
                FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                if (current == null || !expectedUid.equals(current.getUid())) return;
                if (result != null) {
                    lastGoogleAvatarBitmap = result;
                    showAvatar(result);
                }
            });
        }, "firebase-google-avatar").start();
    }

    private void showAvatar(Bitmap bitmap) {
        if (googleAccountAvatar == null) return;
        googleAccountAvatar.setImageBitmap(bitmap);
        googleAccountAvatar.setPadding(0, 0, 0, 0);
        googleAccountAvatar.setClipToOutline(true);
    }

    private void showGoogleAccountMenu() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            renderGoogleAccount(null);
            return;
        }
        String subtitle = user.getEmail() == null ? "Google Account" : user.getEmail();
        String[] options = {"Account Details", "Verify Firebase Session", "Switch Gmail Account",
                "Logout", "Delete Firebase Account"};
        new AlertDialog.Builder(this)
                .setTitle("Google Connected\n" + subtitle)
                .setItems(options, (dialog, selection) -> {
                    switch (selection) {
                        case 0: showGoogleAccountDetails(user); break;
                        case 1: verifyFirebaseSession(); break;
                        case 2: switchGoogleAccount(); break;
                        case 3: confirmGoogleLogout(); break;
                        case 4: confirmFirebaseAccountDeletion(); break;
                        default: break;
                    }
                }).setNegativeButton("Close", null).show();
    }

    private void showGoogleAccountDetails(FirebaseUser user) {
        String displayName = user.getDisplayName() == null ? "—" : user.getDisplayName();
        String email = user.getEmail() == null ? "—" : user.getEmail();
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String model = Build.MODEL == null ? "Android" : Build.MODEL;
        String created = "—", last = "—";
        if (user.getMetadata() != null) {
            DateFormat fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
            if (user.getMetadata().getCreationTimestamp() > 0)
                created = fmt.format(new Date(user.getMetadata().getCreationTimestamp()));
            if (user.getMetadata().getLastSignInTimestamp() > 0)
                last = fmt.format(new Date(user.getMetadata().getLastSignInTimestamp()));
        }
        String message = "Name: " + displayName
                + "\nEmail: " + email
                + "\nProvider: Google / Firebase"
                + "\nFirebase UID: " + user.getUid()
                + "\nEmail verified: " + (user.isEmailVerified() ? "Yes" : "Not yet")
                + "\nDevice: " + manufacturer + " " + model
                + "\nCreated: " + created
                + "\nLast sign-in: " + last;
        new AlertDialog.Builder(this).setTitle("Account Details")
                .setMessage(message)
                .setPositiveButton("OK", null).show();
    }

    // A fresh backend token request checks the active session; do not confuse this with email verification.
    private void verifyFirebaseSession() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { showGoogleError("No Firebase session. Please sign in."); return; }
        if (!GoogleIdentitySignIn.isNetworkAvailable(this)) {
            showGoogleError("Internet required to verify Firebase session."); return;
        }
        Toast.makeText(this, "Verifying Firebase session…", Toast.LENGTH_SHORT).show();
        user.getIdToken(true).addOnCompleteListener(this, task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().getToken() != null) {
                new AlertDialog.Builder(this).setTitle("Firebase Session Verified")
                        .setMessage("Your Google account is authenticated with Firebase.\n"
                                + (user.getEmail() == null ? "" : user.getEmail()))
                        .setPositiveButton("OK", null).show();
            } else {
                showGoogleError("Could not verify this session online. Please retry or sign in again.");
            }
        });
    }

    private void switchGoogleAccount() {
        if (googleAuthBusy) return;
        if (!GoogleIdentitySignIn.isNetworkAvailable(this)) {
            showGoogleError("Internet is required to switch Google accounts."); return;
        }
        // Keep the current Firebase user if the new account chooser is cancelled.
        GoogleIdentitySignIn.clearProviderSession(this, () -> {
            if (!isFinishing() && !isDestroyed()) beginGoogleSignIn();
        });
    }

    private void confirmGoogleLogout() {
        new AlertDialog.Builder(this).setTitle("Logout Google Account?")
                .setMessage("Sign out of Elite Tool? Your Gmail stays on your Android phone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Logout", (dialog, which) -> logoutGoogleAccount()).show();
    }

    private void logoutGoogleAccount() {
        FirebaseAuth.getInstance().signOut();
        renderGoogleAccount(null);
        GoogleIdentitySignIn.clearProviderSession(this, () -> {
            if (!isFinishing() && !isDestroyed())
                Toast.makeText(this, "Google account logged out", Toast.LENGTH_SHORT).show();
        });
    }

    private void confirmFirebaseAccountDeletion() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        new AlertDialog.Builder(this).setTitle("Delete Firebase Account?")
                .setMessage("This permanently deletes your Firebase Authentication user from Elite Tool. "
                        + "It does NOT delete your Google/Gmail account or remove it from this phone. "
                        + "You may need to sign in again to confirm your identity.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (!GoogleIdentitySignIn.isNetworkAvailable(this)) {
                        showGoogleError("Internet required to delete Firebase account."); return;
                    }
                    user.delete().addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseAuth.getInstance().signOut();
                            renderGoogleAccount(null);
                            GoogleIdentitySignIn.clearProviderSession(this,
                                    () -> Toast.makeText(this, "Firebase account deleted", Toast.LENGTH_SHORT).show());
                        } else if (task.getException() instanceof FirebaseAuthRecentLoginRequiredException) {
                            new AlertDialog.Builder(this).setTitle("Recent Login Required")
                                    .setMessage("For security, sign in to Google again, then retry account deletion.")
                                    .setPositiveButton("Sign In", (d, w) -> switchGoogleAccount())
                                    .setNegativeButton("Cancel", null).show();
                        } else {
                            showGoogleError("Account deletion failed. Check your connection and retry.");
                        }
                    });
                }).show();
    }

    @Override protected void onStart() {
        super.onStart();
        FirebaseAuth.getInstance().addAuthStateListener(googleAuthStateListener);
    }

    @Override protected void onStop() {
        FirebaseAuth.getInstance().removeAuthStateListener(googleAuthStateListener);
        super.onStop();
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = getPackageName();
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(android.net.Uri.parse("package:" + packageName));
                    startActivity(intent);
                } catch (Exception e) {
                    FLog.error("Battery Optimization Request Failed: " + e.getMessage());
                }
            }
        }
    }

    private int dashboardFitStage = 0;

    private void setDashboardHeight(int id, int dpHeight) {
        View v = findViewById(id);
        if (v == null) return;
        ViewGroup.LayoutParams params = v.getLayoutParams();
        params.height = dp(dpHeight);
        v.setLayoutParams(params);
    }

    private void fitDashboardToScreen() {
        View root = findViewById(R.id.dashboard_root);
        View card = findViewById(R.id.dashboard_card);
        if (root == null || card == null || root.getHeight() == 0) return;
        // The wrap-content card must not leave a blank panel below the actions.
        // Only compact further if the whole dashboard exceeds the usable screen.
        final int overflow = card.getBottom() + dp(9) - root.getHeight();
        if (overflow <= 0 || dashboardFitStage >= 2) return;
        dashboardFitStage++;
        if (dashboardFitStage == 1) {
            View subtitle = findViewById(R.id.dashboard_subtitle);
            if (subtitle != null) subtitle.setVisibility(View.GONE);
            setDashboardHeight(R.id.dashboard_header, 40);
            setDashboardHeight(R.id.game_carousel, 67);
            setDashboardHeight(R.id.game_curr_icon, 69);
            setDashboardHeight(R.id.game_prev_icon, 49);
            setDashboardHeight(R.id.game_next_icon, 49);
            setDashboardHeight(R.id.dashboard_game_info_row, 54);
            setDashboardHeight(R.id.device_card, 45);
            setDashboardHeight(R.id.start_game, 41);
        } else {
            setDashboardHeight(R.id.dashboard_header, 38);
            setDashboardHeight(R.id.game_carousel, 61);
            setDashboardHeight(R.id.game_curr_icon, 60);
            setDashboardHeight(R.id.game_prev_icon, 44);
            setDashboardHeight(R.id.game_next_icon, 44);
            setDashboardHeight(R.id.dashboard_game_info_row, 50);
            setDashboardHeight(R.id.dashboard_actions_row, 31);
            setDashboardHeight(R.id.license_card, 46);
            setDashboardHeight(R.id.dashboard_quick_row, 31);
            setDashboardHeight(R.id.device_card, 44);
            setDashboardHeight(R.id.start_game, 38);
            setDashboardHeight(R.id.btn_switch_mode, 34);
            card.setPadding(dp(9), dp(8), dp(9), dp(8));
        }
        root.post(this::fitDashboardToScreen);
    }

    private void applyColorfulText() {
        if (hideEspText != null) {
            hideEspText.post(() -> {
                float width = hideEspText.getPaint().measureText(hideEspText.getText().toString());
                Shader textShader = new LinearGradient(0, 0, width, 0,
                        new int[]{
                                Color.parseColor("#A855F7"),
                                Color.parseColor("#EC4899"),
                                Color.parseColor("#3B82F6")
                        }, null, Shader.TileMode.CLAMP);
                hideEspText.getPaint().setShader(textShader);
                hideEspText.invalidate();
            });
        }
    }

    private void startAnimations() {
        // The dashboard uses a static image background; no blob views are present.
    }

    private void animateBlob(View view, int duration, float tx, float ty) {
        if (view == null) return;
        ObjectAnimator animX = ObjectAnimator.ofFloat(view, "translationX", 0f, tx, 0f);
        ObjectAnimator animY = ObjectAnimator.ofFloat(view, "translationY", 0f, ty, 0f);
        animX.setDuration(duration);
        animY.setDuration(duration + 2000);
        animX.setRepeatCount(ValueAnimator.INFINITE);
        animY.setRepeatCount(ValueAnimator.INFINITE);
        animX.setRepeatMode(ValueAnimator.REVERSE);
        animY.setRepeatMode(ValueAnimator.REVERSE);
        animX.start();
        animY.start();
    }

    private void updateMainGameUI() {
        if (gamePackage == null || startGame == null) return;
        final boolean installed;
        try {
            installed = ApkEnv.getInstance().isInstalled(gamePackage);
        } catch (Throwable ex) {
            toast("Could not query selected game: " + ex.getMessage());
            return;
        }
        runOnUiThread(() -> {
            // One button changes its action based on the selected game's actual state.
            if (installGame != null) {
                installGame.setText(installed ? "UNINSTALL" : "INSTALL");
                installGame.setEnabled(true);
                installGame.setAlpha(1f);
            }
            if (obbFixButton != null) {
                obbFixButton.setEnabled(installed);
                obbFixButton.setAlpha(installed ? 1f : 0.5f);
            }
            startGame.setText(installed ? "Start Game   ➜" : "Install Game First   ➜");
        });
    }

    /** Swipe on any icon or blank carousel space; parent must not steal MOVE. */
    private void setupGameCarousel() {
        View carousel = findViewById(R.id.game_carousel);
        if (carousel != null) carousel.setOnTouchListener(gameSwipeListener(0));
        if (previousGameIcon != null) previousGameIcon.setOnTouchListener(gameSwipeListener(-1));
        if (currentGameIcon != null) currentGameIcon.setOnTouchListener(gameSwipeListener(0));
        if (nextGameIcon != null) nextGameIcon.setOnTouchListener(gameSwipeListener(1));
        updateGameCarousel();
    }

    /** Return true for DOWN to receive the full drag even across neighbouring icons. */
    private View.OnTouchListener gameSwipeListener(final int tapAction) {
        final float[] down = new float[2];
        return (view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawX();
                    down[1] = event.getRawY();
                    if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = event.getRawX() - down[0];
                    float dy = event.getRawY() - down[1];
                    if (Math.abs(dx) > dp(26) && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                        animateGameShift(dx < 0 ? 1 : -1);
                    } else if (Math.abs(dx) < dp(16) && Math.abs(dy) < dp(16)) {
                        if (tapAction != 0) animateGameShift(tapAction);
                        view.performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return true;
            }
        };
    }

    private void animateGameShift(int step) {
        if (carouselAnimating || gamePackages.isEmpty()) return;
        carouselAnimating = true;
        View row = findViewById(R.id.game_carousel);
        if (row == null) { carouselAnimating = false; shiftGame(step); return; }
        // Animate movement instead of requiring the user to tap neighbouring logos.
        row.animate().translationX(-step * dp(24)).alpha(0.40f)
            .setDuration(125).withEndAction(() -> {
                shiftGame(step);
                row.setTranslationX(step * dp(24));
                row.animate().translationX(0f).alpha(1f).setDuration(145)
                    .withEndAction(() -> carouselAnimating = false).start();
            }).start();
    }

    private void shiftGame(int delta) {
        if (gamePackages.isEmpty()) return;
        int index = (selectedGameIndex + delta + gamePackages.size()) % gamePackages.size();
        selectVersion(index);
    }

    private void updateGameCarousel() {
        if (gameIcons.isEmpty()) return;
        int count = gameIcons.size();
        int prev = (selectedGameIndex - 1 + count) % count;
        int next = (selectedGameIndex + 1) % count;
        if (previousGameIcon != null) {
            previousGameIcon.setImageResource(gameIcons.get(prev));
            previousGameIcon.setContentDescription("Previous game: " + gameTitles.get(prev));
        }
        if (currentGameIcon != null) currentGameIcon.setImageResource(gameIcons.get(selectedGameIndex));
        if (nextGameIcon != null) {
            nextGameIcon.setImageResource(gameIcons.get(next));
            nextGameIcon.setContentDescription("Next game: " + gameTitles.get(next));
        }
        if (carouselDots != null) {
            carouselDots.removeAllViews();
            for (int i = 0; i < count; i++) {
                final int index = i;
                View dot = new View(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(i == selectedGameIndex ? 22 : 8), dp(8));
                params.setMargins(dp(5), 0, dp(5), 0);
                dot.setLayoutParams(params);
                dot.setBackgroundResource(i == selectedGameIndex ? R.drawable.dash_dot_active : R.drawable.dash_dot_idle);
                dot.setOnClickListener(v -> selectVersion(index));
                carouselDots.addView(dot);
            }
        }
    }

    private void refreshLicenseKey() {
        if (licenseKeyText == null) return;
        String key = getUserKey();
        if (key == null || key.trim().isEmpty() || "INVALID".equals(key)) {
            licenseKeyText.setText("Key: not available");
        } else if (licenseKeyVisible) {
            licenseKeyText.setText("Key: " + key);
        } else {
            String last = key.length() > 4 ? key.substring(key.length() - 4) : "••••";
            licenseKeyText.setText("Key: ••••••••" + last + "  (tap eye)");
        }
    }

    private void startApkOnlyInstall() {
        final String pkg = gamePackage;
        doShowProgress(true);
        GameInstallManager.installApkOnly(this, pkg, (success, message) -> {
            doHideProgress();
            toast(message);
            if (pkg.equals(gamePackage)) updateMainGameUI();
        });
    }

    private void runObbFix() {
        final String pkg = gamePackage;  // Snapshot: switching icons cannot retarget a running copy.
        if (pkg == null) return;
        obbFixButton.setEnabled(false);
        obbFixButton.setText("CHECKING…");
        GameInstallManager.copySelectedGameObb(this, pkg, (success, message) -> {
            if (GameInstallManager.needsObbPicker(message)) {
                resetObbButton();
                showObbPickerChoice(pkg, GameInstallManager.humanMessage(message));
            } else {
                resetObbButton();
                toast(message);
                if (pkg.equals(gamePackage)) updateMainGameUI();
            }
        }, percent -> {
            if (pkg.equals(gamePackage) && obbFixButton != null)
                obbFixButton.setText("COPY " + percent + "%");
        });
    }

    private void resetObbButton() {
        if (obbFixButton == null) return;
        obbFixButton.setText("OBB FIX");
        if (gamePackage != null) updateMainGameUI();
    }

    private void showObbPickerChoice(final String pkg, String reason) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("OBB permission needed")
            .setMessage(reason + "\n\nOnly choose a main/patch OBB for " + pkg
                + ". On Android 11+, Android/obb may not be accessible to third-party apps."
                + " You can use the system picker if the file is available there, or select an authorized copy in Downloads.")
            .setPositiveButton("CHOOSE OBB", (dialog, which) -> openObbPicker(pkg))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void openObbPicker(String pkg) {
        pendingObbPackage = pkg;
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, REQUEST_SELECT_OBB);
        } catch (Exception ex) {
            toast("No compatible system file picker: " + ex.getMessage());
        }
    }

    private void confirmUninstall(final String pkg) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Uninstall selected game")
            .setMessage("Remove " + pkg + " from this loader? This will not uninstall the original game.")
            .setPositiveButton("Uninstall", (dialog, which) -> {
                if (installGame != null) installGame.setEnabled(false);
                new Thread(() -> {
                    boolean success;
                    try {
                        ApkEnv.getInstance().unInstallApp(pkg);
                        success = !ApkEnv.getInstance().isInstalled(pkg);
                    } catch (Throwable ex) { success = false; }
                    final boolean ok = success;
                    runOnUiThread(() -> {
                        toast(ok ? "Removed " + pkg + " from loader" : "Uninstall failed");
                        if (pkg.equals(gamePackage)) updateMainGameUI();
                        else if (installGame != null) installGame.setEnabled(true);
                    });
                }, "selected-game-uninstall").start();
            })
            .setNegativeButton("Cancel", null).show();
    }

    // ===== NEW LISTENERS =====
    private void setupNewListeners() {
        // Copy HWID on Device Info click (New)
        if (deviceInfoTextView != null) {
            deviceInfoTextView.setOnClickListener(v -> {
                String hwid = Login.getAndroidID(this);
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("HWID", hwid);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    toast("HWID Copied to Clipboard!");
                }
            });
        }

        // Real actions: install APK into container, then OBB separately for selected game.
        if (installGame != null) installGame.setOnClickListener(v -> {
            final String selected = gamePackage;
            if (selected == null) return;
            boolean installed;
            try { installed = ApkEnv.getInstance().isInstalled(selected); }
            catch (Exception ex) { toast("Could not check install status."); return; }
            if (installed) confirmUninstall(selected);
            else startApkOnlyInstall();
        });
        if (obbFixButton != null) obbFixButton.setOnClickListener(v -> runObbFix());
        if (licenseEye != null) licenseEye.setOnClickListener(v -> {
            licenseKeyVisible = !licenseKeyVisible;
            refreshLicenseKey();
        });
        if (startGame != null) startGame.setOnClickListener(v -> {
            if (!ApkEnv.getInstance().isInstalled(gamePackage)) {
                toast("Install the selected game first.");
                return;
            }
            handleMainAction();
        });

        // Game changes via swiping/tapping the three logos only. No More Games button.

        // Hide ESP
        if (hideEsp != null) {
            hideEsp.setOnCheckedChangeListener((buttonView, isChecked) -> {
                FPrefs.with(this).writeBoolean("hide_esp", isChecked);
                Toast.makeText(this, "Hide ESP: " + (isChecked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
            });
        }

        // Removed Exit button and its callback; Android Back/launcher still work.

        // Mode Switcher Button
        if (btnSwitchMode != null) {
            btnSwitchMode.setOnClickListener(v -> handleModeSwitchPopup());
        }

        // Reset selected virtual game's login-related SharedPreferences/cache only.
        // No confirmation dialog: the button shows its result as a Toast.
        if (clearData != null) {
            clearData.setOnClickListener(v -> resetSelectedGameLoginOnly());
        }
    }

    private void resetSelectedGameLoginOnly() {
        final String target = gamePackage;
        if (target == null || !gamePackages.contains(target) || target.equals(getPackageName())) {
            toast("Select a valid game first.");
            return;
        }
        if (!ApkEnv.getInstance().isInstalled2(target)) {
            toast("Selected game is not installed in Loader.");
            return;
        }
        if (clearData != null) clearData.setEnabled(false);

        new Thread(() -> {
            final com.pubgm.libhelper.GameLoginReset.Result result =
                    com.pubgm.libhelper.GameLoginReset.clearSelectedGame(target);
            runOnUiThread(() -> {
                if (clearData != null) clearData.setEnabled(true);
                if (result.errors > 0) {
                    toast("Login reset incomplete. Check logs.");
                } else if (result.preferencesRemoved > 0 || result.cacheRemoved > 0) {
                    toast("Login token/cache cleared. Open game to sign in.");
                } else {
                    toast("No matching local login token/cache found.");
                }
            });
        }, "login-data-reset").start();
    }

    // ===== START PATCHER =====
    private void startPatcherAndLaunch() {
        startPatcher();
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            launchGame();
        }, 500);
    }

    private void handleVersionSwitch() {
        if (gameTitles.isEmpty()) return;
        
        final com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_Translucent);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_version_selector, null);
        dialog.setContentView(dialogView);
        
        RecyclerView rv = dialogView.findViewById(R.id.version_recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        
        rv.setAdapter(new RecyclerView.Adapter<VersionViewHolder>() {
            @NonNull
            @Override
            public VersionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.item_version_selector, parent, false);
                return new VersionViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull VersionViewHolder holder, int position) {
                holder.title.setText(gameTitles.get(position));
                holder.pkg.setText(gamePackages.get(position));
                holder.icon.setImageResource(gameIcons.get(position));
                
                holder.itemView.setOnClickListener(v -> {
                    dialog.dismiss();
                    selectVersion(position);
                    toast("Switched to " + gameTitles.get(position));
                });
                
                // Add simple animation to items
                holder.itemView.setAlpha(0f);
                holder.itemView.setTranslationY(50f);
                holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .setStartDelay(position * 50L)
                    .start();
            }

            @Override
            public int getItemCount() {
                return gameTitles.size();
            }
        });
        
        View btnClose = dialogView.findViewById(R.id.btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }
        
        dialog.show();
    }
    
    private static class VersionViewHolder extends RecyclerView.ViewHolder {
        TextView title, pkg;
        ImageView icon;
        public VersionViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.version_title);
            pkg = itemView.findViewById(R.id.version_pkg);
            icon = itemView.findViewById(R.id.version_icon);
        }
    }

    private void handleModeSwitchPopup() {
        final BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_Translucent);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_mode_selector, null);
        dialog.setContentView(dialogView);

        View btnJava = dialogView.findViewById(R.id.btn_select_java);
        View btnAuto = dialogView.findViewById(R.id.btn_select_auto);
        View btnClose = dialogView.findViewById(R.id.btn_close_mode);

        if (btnJava != null) {
            btnJava.setOnClickListener(v -> {
                isAutoMode = false;
                modeSelected = true;
                updateModeSelectionUI();
                dialog.dismiss();
                toast("Switched to JAVA (FLOATING)");
            });
        }

        if (btnAuto != null) {
            btnAuto.setOnClickListener(v -> {
                isAutoMode = true;
                modeSelected = true;
                updateModeSelectionUI();
                dialog.dismiss();
                toast("Switched to AUTO MODE");
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void updateModeSelectionUI() {
        if (btnSwitchMode == null) return;
        
        if (!modeSelected) {
            btnSwitchMode.setText("SELECT GAME MODE");
            btnSwitchMode.setIconResource(R.drawable.animation_play);
            btnSwitchMode.setIconTint(android.content.res.ColorStateList.valueOf(Color.parseColor("#64748B")));
            btnSwitchMode.setTextColor(Color.parseColor("#64748B"));
            return;
        }

        if (!isAutoMode) {
            btnSwitchMode.setText("MODE: JAVA (FLOATING)");
            btnSwitchMode.setIconResource(R.drawable.animation_play);
            btnSwitchMode.setIconTint(android.content.res.ColorStateList.valueOf(Color.parseColor("#3B82F6")));
            btnSwitchMode.setTextColor(Color.parseColor("#3B82F6"));
        } else {
            btnSwitchMode.setText("MODE: AUTO (ADVANCED)");
            btnSwitchMode.setIconResource(R.drawable.bypass);
            btnSwitchMode.setIconTint(android.content.res.ColorStateList.valueOf(Color.parseColor("#EC4899")));
            btnSwitchMode.setTextColor(Color.parseColor("#EC4899"));
        }
    }

    private void handleMainAction() {
        if (gamePackage == null) return;

        if (ApkEnv.getInstance().isInstalled(gamePackage)) {
            if (ApkEnv.getInstance().isRunning(gamePackage)) {
                ApkEnv.getInstance().stopRunningApp(gamePackage);
                updateMainGameUI();
            } else {
                if (!modeSelected) {
                    toast("Please SELECT GAME MODE first!");
                    handleModeSwitchPopup();
                    return;
                }
                
                if (isAutoMode) {
                    handleAutoModeStart();
                } else {
                    handleJavaModeStart();
                }
            }
        } else {
            // APK only: never invoke the legacy blocking OBB copier from Start Game.
            startApkOnlyInstall();
        }
    }

    private void handleJavaModeStart() {
        // SECURITY: Remove any injected "Auto Mode" loaders to ensure clean Java Mode
        ApkEnv.getInstance().removeLoader(gamePackage);

        // Ensure daemon is downloaded if missing
        File dFile = new File(getFilesDir(), "sock64");
        if (!dFile.exists()) {
            toast("Downloading required files...");
            DownloadZipAdapter downloadZip = new DownloadZipAdapter(this);
            downloadZip.setZipFileName("assets.zip");
            downloadZip.startDownload(Login.FixCrash(), new DownloadZipAdapter.DownloadCallback() {
                @Override
                public void onDownloadComplete(boolean success, String message) {
                    if (success) {
                        startPatcherAndLaunch();
                    } else {
                        toast("Failed to download daemon: " + message);
                    }
                }
                @Override
                public void onProgress(int progress) {}
            });
        } else {
            startPatcherAndLaunch();
        }
    }

    private void handleAutoModeStart() {
        toast("Starting Auto Mode Injection...");
        doShowProgress(true);
        
        DownloadZipAdapter downloadZip = new DownloadZipAdapter(this);
        downloadZip.setZipFileName("Saved.zip");
        
        // Link: https://github.com/aaravmeh2345/Game_loader/releases/download/Bgmi1/Saved.zip
        downloadZip.startDownload("https://github.com/aaravmeh2345/Game_loader/releases/download/Bgmi1/Saved.zip", new DownloadZipAdapter.DownloadCallback() {
            @Override
            public void onDownloadComplete(boolean success, String message) {
                runOnUiThread(() -> {
                    doHideProgress();
                    if (success) {
                        // Inject the library into the game container
                        if (ApkEnv.getInstance().tryAddLoader(gamePackage)) {
                            toast("Injection Successful. Launching...");
                            launchGame();
                        } else {
                            toast("Injection Failed!");
                        }
                    } else {
                        toast("Auto Mode Download Failed: " + message);
                    }
                });
            }
            @Override
            public void onProgress(int progress) {
                runOnUiThread(() -> {
                    if (getProgresBar() != null) {
                        getProgresBar().setIndeterminate(false);
                        getProgresBar().setProgress(progress);
                    }
                });
            }
        });
    }

    public void startPatcher() {
        try {
            loadAssets();
            startService(new Intent(this, FloatLogo.class));
            isServiceRunning = true;
            FPrefs.with(this).writeBoolean("service_running", true);
            updateMainGameUI();
            Toast.makeText(this, "Service Started!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Service Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ===== GAME LAUNCH DIAGNOSTICS =====
    // A launch intent being submitted does NOT prove the game's process started.
    private void launchGame() {
        final String selectedPackage = gamePackage;
        if (selectedPackage == null || selectedPackage.trim().isEmpty()
                || !gamePackages.contains(selectedPackage)) {
            toast("Select a valid game first.");
            return;
        }
        try {
            if (!ApkEnv.getInstance().isInstalled(selectedPackage)) {
                android.util.Log.w("EliteToolLaunch", "Not installed in virtual user 0: " + selectedPackage);
                toast("Selected game is not installed in the virtual container. Use Install first.");
                updateMainGameUI();
                return;
            }
            final boolean requestAccepted = ApkEnv.getInstance().launchApk(selectedPackage);
            if (requestAccepted) {
                android.util.Log.i("EliteToolLaunch", "Virtual launch REQUEST sent: " + selectedPackage);
                toast("Game launch requested. Check whether the game opens.");
            } else {
                android.util.Log.e("EliteToolLaunch", "No launch intent / launch request rejected: " + selectedPackage);
                toast("Game launch request failed. Check virtual install and launch logs.");
            }
        } catch (Exception e) {
            android.util.Log.e("EliteToolLaunch", "Launch request exception for " + selectedPackage, e);
            toast("Game launch error: " + e.getClass().getSimpleName());
        }
    }
    
    // ===== TIMER =====
    private void updateExpiryDisplay() {
        long currentTime = System.currentTimeMillis();
        long diff = expiryTimeMillis - currentTime;

        if (diff <= 0) {
            if (days != null) days.setText("00");
            if (hours != null) hours.setText("00");
            if (minutes != null) minutes.setText("00");
            if (seconds != null) seconds.setText("00");
            return;
        }

        long d = TimeUnit.MILLISECONDS.toDays(diff);
        long h = TimeUnit.MILLISECONDS.toHours(diff) - TimeUnit.DAYS.toHours(d);
        long m = TimeUnit.MILLISECONDS.toMinutes(diff) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(diff));
        long s = TimeUnit.MILLISECONDS.toSeconds(diff) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(diff));

        if (days != null) days.setText(String.format("%02d", d));
        if (hours != null) hours.setText(String.format("%02d", h));
        if (minutes != null) minutes.setText(String.format("%02d", m));
        if (seconds != null) seconds.setText(String.format("%02d", s));
    }

    private void startTimer() {
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                updateExpiryDisplay();
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.postDelayed(timerRunnable, 0);
    }

    // ===== VERIFY DIALOG =====
    private void createVerifyDialog() {
        verifyDialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        verifyDialog.setCancelable(false);
        
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_verify, null);
        verifyDialog.setContentView(dialogView);
        
        progressBarView = dialogView.findViewById(R.id.progressBar);
        tvStatusText = dialogView.findViewById(R.id.tvStatusText);
        tvStatusDetail = dialogView.findViewById(R.id.tvStatusDetail);
        tvKeyText = dialogView.findViewById(R.id.tvKeyText);
        tvErrorMessage = dialogView.findViewById(R.id.tvErrorMessage);
        
        progressContainer = dialogView.findViewById(R.id.progressContainer);
        statusContainer = dialogView.findViewById(R.id.statusContainer);
        deviceInfoContainer = dialogView.findViewById(R.id.deviceInfoContainer);
        keyContainer = dialogView.findViewById(R.id.keyContainer);
        successScreen = dialogView.findViewById(R.id.successScreen);
        errorScreen = dialogView.findViewById(R.id.errorScreen);
        
        tvDeviceInfo = dialogView.findViewById(R.id.tvDeviceInfo);
        tvAndroidInfo = dialogView.findViewById(R.id.tvAndroidInfo);
        tvSDKInfo = dialogView.findViewById(R.id.tvSDKInfo);
        
        Window window = verifyDialog.getWindow();
        if (window != null) {
            window.setLayout(dp(180), dp(220));
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        verifyDialog.show();
        startVerification();
    }
    
    private void startVerification() {
        tvStatusText.setText("VERIFYING");
        tvStatusDetail.setText("Please wait...");
        loadDeviceInfo();
        
        String key = getUserKey();
        tvKeyText.setText(key);
        
        if (key == null || key.equals("INVALID") || key.equals("null") || key.trim().isEmpty()) {
            showError("INVALID LICENCE KEY");
            return;
        }
        
        new Thread(() -> {
            try {
                String result = Login.check(MainActivity.this, key);
                runOnUiThread(() -> {
                    if (result.equals("OK")) {
                        SharedPreferences prefs = getSharedPreferences("auth_pref", MODE_PRIVATE);
                        prefs.edit().putBoolean("verified", true).putLong("expiry", Login.getExpiryTimestamp()).apply();
                        tvStatusText.setText("VERIFIED");
                        tvStatusDetail.setText("Licence confirmed");
                        handler.postDelayed(() -> {
                            progressContainer.setVisibility(View.GONE);
                            statusContainer.setVisibility(View.GONE);
                            deviceInfoContainer.setVisibility(View.GONE);
                            keyContainer.setVisibility(View.GONE);
                            successScreen.setVisibility(View.VISIBLE);
                            handler.postDelayed(() -> {
                                verifyDialog.dismiss();
                                onVerified();
                            }, 800);
                        }, 500);
                    } else {
                        showError(result);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }
    
    private void onVerified() {
        if (verified) return;
        verified = true;
        
        // Fix: Update timer immediately after verification
        long expiry = Login.getExpiryTimestamp();
        if (expiry > 0) {
            expiryTimeMillis = expiry * 1000;
            runOnUiThread(() -> {
                updateExpiryDisplay();
                startTimer();
            });
        }

        doInitRecycler();
    }
    
    private void showError(String message) {
        runOnUiThread(() -> {
            progressContainer.setVisibility(View.GONE);
            statusContainer.setVisibility(View.GONE);
            deviceInfoContainer.setVisibility(View.GONE);
            keyContainer.setVisibility(View.GONE);
            tvErrorMessage.setText(message);
            errorScreen.setVisibility(View.VISIBLE);
            handler.postDelayed(() -> {
                verifyDialog.dismiss();
                MainActivity.this.toast("Licence Error: " + message);
                LoginActivity.goLogin(MainActivity.this);
                finish();
            }, 2000);
        });
    }
    
    private void loadDeviceInfo() {
        runOnUiThread(() -> {
            String deviceName = getDeviceName();
            String androidVer = getAndroidVersion();
            
            if (tvDeviceInfo != null) tvDeviceInfo.setText("Device: " + deviceName);
            if (tvAndroidInfo != null) tvAndroidInfo.setText("Android: " + androidVer);
            if (tvSDKInfo != null) tvSDKInfo.setText("SDK: " + getSDKVersion());
            
            // Update the main dashboard text view
            if (deviceInfoTextView != null) {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                long ramMb = 0;
                if (am != null) {
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    ramMb = mi.totalMem / (1024L * 1024L);
                }
                deviceInfoTextView.setText("Model: " + Build.MODEL + "  •  Android: " + androidVer + "\nRAM: " + ramMb + " MB  •  Tap to copy device ID");
            }
        });
    }
    
    private String getUserKey() {
        if (LoginActivity.USERKEY != null && !LoginActivity.USERKEY.isEmpty()) {
            return LoginActivity.USERKEY;
        }
        return FPrefs.with(this).read("USER", "INVALID");
    }

    private String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }

    private String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }
    
    private String getAndroidVersion() {
        return Build.VERSION.RELEASE;
    }
    
    private String getSDKVersion() {
        return String.valueOf(Build.VERSION.SDK_INT);
    }
    
    private int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }

    public LinearProgressIndicator getProgresBar() {
        return progress;
    }
    
    public void doShowProgress(boolean indeterminate) {
        if (progress == null) return;
        runOnUiThread(() -> {
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(indeterminate);
        });
    }
    
    public void doHideProgress() {
        if (progress == null) return;
        runOnUiThread(() -> {
            progress.setVisibility(View.GONE);
        });
    }
    
    public void toast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
    
    public void launchSplash(String packageName) {
        launchGame();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        if (verifyDialog != null && verifyDialog.isShowing()) {
            verifyDialog.dismiss();
        }
        instance = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_OBB) {
            final String pkg = pendingObbPackage;
            pendingObbPackage = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null || pkg == null) return;
            final Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getPreferences(MODE_PRIVATE).edit().putString("obb_uri_" + pkg, uri.toString()).apply();
            } catch (SecurityException ignored) {
                // The current read grant may still be valid; do not pretend it is permanent.
            }
            if (obbFixButton != null && pkg.equals(gamePackage)) {
                obbFixButton.setEnabled(false);
                obbFixButton.setText("COPYING…");
            }
            GameInstallManager.copyFromUserSelection(this, pkg, uri, (ok, message) -> {
                if (!ok) getPreferences(MODE_PRIVATE).edit().remove("obb_uri_" + pkg).apply();
                resetObbButton();
                toast(message);
            }, percent -> {
                if (pkg.equals(gamePackage) && obbFixButton != null)
                    obbFixButton.setText("COPY " + percent + "%");
            });
            return;
        }
        if (requestCode == 123) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startPatcherAndLaunch();
            } else {
                Toast.makeText(this, "Overlay permission required!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}