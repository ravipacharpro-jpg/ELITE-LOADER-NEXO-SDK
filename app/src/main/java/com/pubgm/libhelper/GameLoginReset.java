package com.pubgm.libhelper;

import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;

/**
 * Scoped login cleanup for one installed virtual game (user 0).
 * Does NOT use clearPackage(): that would delete all game data and settings.
 * Never touches installed APKs, OBBs, databases, or unrelated preferences.
 * This does not change any server-side account status or ban.
 */
public final class GameLoginReset {
    private static final String TAG = "GameLoginReset";
    private static final int USER_ID = 0;

    public static final class Result {
        public int preferencesRemoved;
        public int cacheRemoved;
        public int errors;
    }

    private GameLoginReset() { }

    public static Result clearSelectedGame(String packageName) {
        Result result = new Result();
        if (!isValidPackage(packageName)) {
            result.errors++;
            return result;
        }
        try {
            if (!BlackBoxCore.get().isInstalled(packageName, USER_ID)) {
                result.errors++;
                return result;
            }
            // Stop only this virtual game before touching its on-disk preferences.
            BlackBoxCore.get().stopPackage(packageName, USER_ID);

            File internal = BEnvironment.getDataDir(packageName, USER_ID);
            File protectedData = BEnvironment.getDeDataDir(packageName, USER_ID);
            File externalCache = BEnvironment.getExternalDataCacheDir(packageName, USER_ID);
            resetPreferences(new File(internal, "shared_prefs"), result);
            resetPreferences(new File(protectedData, "shared_prefs"), result);

            // Restrict cache cleanup to auth/login-related filenames or folders.
            // Preserves unrelated game resources and downloaded assets.
            resetAuthCache(new File(internal, "cache"), result);
            resetAuthCache(new File(protectedData, "cache"), result);
            resetAuthCache(externalCache, result);
        } catch (Throwable t) {
            Log.e(TAG, "Unable to reset selected game's login state", t);
            result.errors++;
        }
        return result;
    }

    private static boolean isValidPackage(String value) {
        return value != null && value.length() <= 200
                && value.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");
    }

    // Case-insensitive and handles camelCase, snake_case and dashed auth keys.
    private static boolean isAuthRelated(String value) {
        if (value == null) return false;
        String normalized = value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.US);
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        String surrounded = "_" + normalized + "_";
        String[] authMarkers = {
                "_token_", "_auth_", "_oauth_", "_session_", "_login_",
                "_signin_", "_sign_in_", "_credential_", "_cookie_",
                "_openid_", "_account_", "_accesskey_", "_refreshkey_",
                "_sso_"
        };
        for (String marker : authMarkers) {
            if (surrounded.contains(marker)) return true;
        }
        // Common camelCase or compact spellings: accessToken/refreshToken etc.
        return normalized.equals("accesstoken") || normalized.equals("refreshtoken")
                || normalized.equals("idtoken") || normalized.equals("sessionid")
                || normalized.equals("authtoken") || normalized.equals("logintoken");
    }

    /** Removes only matching XML pref keys; preserves all other settings. */
    private static void resetPreferences(File prefsDir, Result result) {
        if (!prefsDir.isDirectory()) return;
        File[] prefFiles = prefsDir.listFiles();
        if (prefFiles == null) { result.errors++; return; }
        for (File file : prefFiles) {
            String filename = file.getName();
            if (!file.isFile() || !(filename.endsWith(".xml") || filename.endsWith(".xml.bak"))) continue;
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setExpandEntityReferences(false);
                // Android versions may differ in supported XML security features.
                try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); }
                catch (Exception unsupported) { Log.w(TAG, "XML parser feature unavailable", unsupported); }
                Document document = factory.newDocumentBuilder().parse(file);
                Element root = document.getDocumentElement();
                if (root == null || !"map".equals(root.getTagName())) continue;
                NodeList children = root.getChildNodes();
                int removed = 0;
                for (int i = children.getLength() - 1; i >= 0; i--) {
                    Node child = children.item(i);
                    if (child instanceof Element) {
                        Element element = (Element) child;
                        if (isAuthRelated(element.getAttribute("name"))) {
                            root.removeChild(child);
                            removed++;
                        }
                    }
                }
                if (removed == 0) continue;

                File tmp = new File(prefsDir, filename + ".loginreset.tmp");
                Transformer writer = TransformerFactory.newInstance().newTransformer();
                writer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
                writer.setOutputProperty(OutputKeys.INDENT, "no");
                try {
                    writer.transform(new DOMSource(document), new StreamResult(tmp));
                    if (!tmp.renameTo(file)) throw new IOException("Could not replace preferences file");
                    result.preferencesRemoved += removed;
                } finally {
                    if (tmp.exists() && !tmp.delete()) Log.w(TAG, "Temp cleanup failed");
                }
            } catch (Exception e) {
                result.errors++;
                Log.e(TAG, "Unable to edit login preferences: " + filename, e);
            }
        }
    }

    private static void resetAuthCache(File cacheRoot, Result result) {
        if (!cacheRoot.isDirectory()) return;
        removeMatchingCacheEntries(cacheRoot, cacheRoot, 0, result);
    }

    private static void removeMatchingCacheEntries(File dir, File root, int depth, Result result) {
        if (depth > 8) return;
        File[] entries = dir.listFiles();
        if (entries == null) { result.errors++; return; }
        for (File entry : entries) {
            if (!insideRoot(root, entry)) { result.errors++; continue; }
            if (isAuthRelated(entry.getName())) {
                deleteTree(entry, root, 0, result);
            } else if (entry.isDirectory()) {
                removeMatchingCacheEntries(entry, root, depth + 1, result);
            }
        }
    }

    private static void deleteTree(File entry, File root, int depth, Result result) {
        if (depth > 32 || !insideRoot(root, entry)) { result.errors++; return; }
        if (entry.isDirectory()) {
            File[] children = entry.listFiles();
            if (children == null) { result.errors++; return; }
            for (File child : children) deleteTree(child, root, depth + 1, result);
        }
        if (entry.delete()) result.cacheRemoved++;
        else result.errors++;
    }

    private static boolean insideRoot(File root, File entry) {
        try {
            String base = root.getCanonicalPath() + File.separator;
            return entry.getCanonicalPath().startsWith(base);
        } catch (IOException e) {
            return false;
        }
    }
}
