package com.pubgm.libhelper;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.pubgm.R;

import java.security.SecureRandom;

/** Host-app Google account chooser + Firebase Authentication.
 * Never handles a Google password, saves ID tokens, or bypasses an auth challenge.
 * This does not authenticate an installed/virtual game or bypass app licensing.
 */
public final class GoogleIdentitySignIn {
    private static final String TAG = "EliteToolFirebaseAuth";
    private static boolean inProgress;

    public interface Listener {
        void onLoading(boolean loading);
        void onSuccess(@NonNull FirebaseUser user);
        void onError(@NonNull String message);
        void onCancelled();
    }

    private GoogleIdentitySignIn() { }

    public static boolean isNetworkAvailable(@NonNull Context context) {
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        try {
            NetworkInfo active = manager.getActiveNetworkInfo();
            return active != null && active.isConnected();
        } catch (SecurityException ignored) {
            return false;
        }
    }

    public static void start(@NonNull Activity activity, @NonNull Listener listener) {
        if (inProgress) return;
        if (!isNetworkAvailable(activity)) {
            listener.onError("No internet connection. Connect and retry.");
            return;
        }
        if (FirebaseApp.getApps(activity).isEmpty()) {
            listener.onError("Firebase is not configured. Check google-services.json.");
            return;
        }
        String webClientId;
        try {
            webClientId = activity.getString(R.string.default_web_client_id).trim();
        } catch (Exception e) {
            listener.onError("Google Web Client ID is missing from Firebase setup.");
            return;
        }
        if (!webClientId.endsWith(".apps.googleusercontent.com")) {
            listener.onError("Invalid Google Web Client ID.");
            return;
        }
        final FirebaseAuth auth;
        try {
            auth = FirebaseAuth.getInstance();
        } catch (IllegalStateException problem) {
            listener.onError("Firebase Authentication is not initialized.");
            return;
        }

        inProgress = true;
        listener.onLoading(true);
        try {
            byte[] bytes = new byte[24];
            new SecureRandom().nextBytes(bytes);
            String nonce = Base64.encodeToString(bytes,
                    Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);
            GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(webClientId)
                    .setNonce(nonce).build();
            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(option).build();
            CredentialManager.create(activity).getCredentialAsync(activity, request,
                    null, command -> activity.runOnUiThread(command),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override public void onResult(@NonNull GetCredentialResponse response) {
                            Credential raw = response.getCredential();
                            if (!(raw instanceof CustomCredential)
                                    || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                    .equals(((CustomCredential) raw).getType())) {
                                failure(activity, listener, "Unsupported Google credential.");
                                return;
                            }
                            String idToken;
                            try {
                                idToken = GoogleIdTokenCredential.createFrom(
                                        ((CustomCredential) raw).getData()).getIdToken();
                            } catch (Exception e) {
                                Log.w(TAG, "Could not read Google credential", e);
                                failure(activity, listener, "Google credential could not be read.");
                                return;
                            }
                            if (idToken == null || idToken.isEmpty()) {
                                failure(activity, listener, "Google returned no ID token.");
                                return;
                            }
                            AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
                            auth.signInWithCredential(credential).addOnCompleteListener(task -> {
                                if (!task.isSuccessful()) {
                                    Log.w(TAG, "Firebase sign-in failed", task.getException());
                                    failure(activity, listener,
                                            "Firebase sign-in failed. Check network, SHA keys and Google provider.");
                                    return;
                                }
                                FirebaseUser user = auth.getCurrentUser();
                                if (user == null) {
                                    failure(activity, listener, "Signed in but Firebase user was unavailable.");
                                    return;
                                }
                                complete(activity, listener, () -> listener.onSuccess(user));
                            });
                        }
                        @Override public void onError(@NonNull GetCredentialException error) {
                            if (error instanceof GetCredentialCancellationException) {
                                complete(activity, listener, listener::onCancelled);
                            } else {
                                Log.w(TAG, "Google chooser failure", error);
                                failure(activity, listener,
                                        "Google account selection failed. Update Google Play services and retry.");
                            }
                        }
                    });
        } catch (Exception e) {
            Log.w(TAG, "Google chooser could not start", e);
            failure(activity, listener, "Google sign-in could not start. Please retry.");
        }
    }

    public static void clearProviderSession(@NonNull Activity activity, @NonNull Runnable done) {
        try {
            CredentialManager.create(activity).clearCredentialStateAsync(
                    new ClearCredentialStateRequest(), null,
                    command -> activity.runOnUiThread(command),
                    new CredentialManagerCallback<Void, ClearCredentialException>() {
                        @Override public void onResult(Void ignored) { done.run(); }
                        @Override public void onError(@NonNull ClearCredentialException e) {
                            Log.w(TAG, "Provider session could not be cleared", e);
                            done.run();
                        }
                    });
        } catch (Exception e) {
            Log.w(TAG, "Provider session clear failed", e);
            done.run();
        }
    }

    private static void failure(Activity activity, Listener listener, String message) {
        complete(activity, listener, () -> listener.onError(message));
    }

    private static void complete(Activity activity, Listener listener, Runnable callback) {
        activity.runOnUiThread(() -> {
            inProgress = false;
            if (activity.isFinishing() || activity.isDestroyed()) return;
            listener.onLoading(false);
            callback.run();
        });
    }
}
