package com.android.support;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * SecurityNative — Anti-tamper, native login verification,
 * DEX integrity check, HMAC session tokens.
 *
 * All critical logic lives in libModXLab.so (Security.cpp).
 */
public final class SecurityNative {

    static {
        try { System.loadLibrary("ModXLab"); } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------
    // Native methods
    // ------------------------------------------------------------------
    public static native boolean checkSignatureHash(String sha256Hex);
    public static native boolean isEnvironmentValid();
    public static native boolean verifySessionToken(String token, String user, String pass, String expiry);
    public static native String  verifyLogin(String inputUser, String inputPass, String userJson);
    public static native String  decryptString(String encoded, int key);
    public static native String  getQueryUrl(String username);
    public static native String  getUpdateUrl();
    public static native String  getSelfHash(String input);
    public static native String  hmacSign(String message);
    public static native boolean isRooted();
    public static native boolean isVpnActive();

    /** DEX integrity verification — native side compares against embedded hash. */
    public static native boolean verifyDexHash(String sha256Hex);

    // ------------------------------------------------------------------
    // APK signature verification
    // ------------------------------------------------------------------
    public static boolean verifyApkSignatureOrDebug(Context ctx) {
        if (com.android.support.BuildConfig.DEBUG) return true;
        return verifyApkSignature(ctx);
    }

    public static boolean verifyApkSignature(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            String pkg = ctx.getPackageName();
            PackageInfo pi;
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
                if (pi == null || pi.signingInfo == null) return false;
                Signature[] sigs = pi.signingInfo.hasMultipleSigners()
                        ? pi.signingInfo.getApkContentsSigners()
                        : pi.signingInfo.getSigningCertificateHistory();
                if (sigs == null || sigs.length == 0) return false;
                return checkSignatureHash(sha256Hex(sigs[0].toByteArray()));
            } else {
                pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                if (pi == null || pi.signatures == null || pi.signatures.length == 0) return false;
                return checkSignatureHash(sha256Hex(pi.signatures[0].toByteArray()));
            }
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // DEX hash computation (Java side)
    // ------------------------------------------------------------------
    /**
     * Computes SHA-256 over all *.dex entries inside the APK,
     * sorted by name for deterministic ordering.
     *
     * Returns 64-char lowercase hex string, or "" on error.
     */
    public static String computeDexHash(Context ctx) {
        ZipFile zip = null;
        java.io.InputStream is = null;
        try {
            String apkPath = ctx.getApplicationInfo().sourceDir;
            zip = new ZipFile(apkPath);

            MessageDigest md = MessageDigest.getInstance("SHA-256");

            // Collect all .dex names, sort for determinism
            List<String> dexNames = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                String name = en.nextElement().getName();
                if (name.endsWith(".dex")) {
                    dexNames.add(name);
                }
            }
            Collections.sort(dexNames);

            if (dexNames.isEmpty()) {
                android.util.Log.e("Mod_security", "No .dex files found in APK");
                return "";
            }

            // Hash each dex in sorted order
            byte[] buf = new byte[16384];
            for (String name : dexNames) {
                ZipEntry entry = zip.getEntry(name);
                if (entry == null) continue;
                is = zip.getInputStream(entry);
                int n;
                while ((n = is.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
                is.close();
                is = null;
            }

            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (Throwable t) {
            android.util.Log.e("Mod_security", "computeDexHash failed: " + t.getMessage());
            return "";
        } finally {
            try { if (is != null) is.close(); } catch (Throwable ignored) { }
            try { if (zip != null) zip.close(); } catch (Throwable ignored) { }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(data);
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private SecurityNative() { }
}