package com.android.support;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.security.MessageDigest;

/**
 * SecurityNative — anti-tamper, native login verification.
 * Critical logic lives in libModXLab.so (Security.cpp).
 */
public final class SecurityNative {

    static {
        try {
            System.loadLibrary("ModXLab");
        } catch (Throwable t) {
            // already loaded by Main
        }
    }

    // ------------------------------------------------------------------
    // Native methods
    // ------------------------------------------------------------------
    public static native boolean checkSignatureHash(String sha256Hex);
    public static native String  verifyLogin(String inputUser, String inputPass, String userJson);
    public static native String  decryptString(String encoded, int key);
    public static native boolean isEnvironmentValid();

    // ------------------------------------------------------------------
    // Signature verify — returns true in DEBUG builds (so you can test),
    // enforces the real hash in RELEASE builds.
    // ------------------------------------------------------------------
    public static boolean verifyApkSignatureOrDebug(Context ctx) {
        if (com.android.support.BuildConfig.DEBUG) {
            return true;
        }
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