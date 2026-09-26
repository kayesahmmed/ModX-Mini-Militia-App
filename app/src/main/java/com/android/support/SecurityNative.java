package com.android.support;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * SecurityNative — silent security layer.
 *
 * Design:
 *   - NO user-visible strings
 *   - NO descriptive logs
 *   - All verification in native code
 *   - Single entry point: preload()
 */
public final class SecurityNative {

    static {
        try { System.loadLibrary("ModXLab"); } catch (Throwable ignored) { }
    }

    // =================================================================
    // SILENT ENTRY POINT
    // =================================================================
    public static boolean preload(Context ctx, boolean isDebug) {
        try {
            String s = sigHash(ctx);
            String d = dexHash(ctx);
            if (s == null || s.isEmpty() || d == null || d.isEmpty()) return false;
            return verifyHashes(s, d, isDebug);
        } catch (Throwable t) {
            return false;
        }
    }

    // =================================================================
    // Native methods (all registered via JNI_OnLoad)
    // =================================================================
    private static native boolean verifyHashes(String sigHash, String dexHash, boolean isDebug);
    public static native boolean verifyLibHash(String libHash);
    public  static native String  verifyLogin(String inputUser, String inputPass, String userJson);
    public  static native String  verifyLoginWithTime(String inputUser, String inputPass, String userJson, long nowMs);
    public  static native boolean verifySessionToken(String token, String user, String pass, String expiry);
    public  static native String  getQueryUrl(String username);
    public  static native String  getUpdateUrl();
    public  static native String  getCloudFnUrl();

    // =================================================================
    // APK signature hash
    // =================================================================
    private static String sigHash(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            String pkg = ctx.getPackageName();
            PackageInfo pi;
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
                if (pi == null || pi.signingInfo == null) return null;
                Signature[] sigs = pi.signingInfo.hasMultipleSigners()
                        ? pi.signingInfo.getApkContentsSigners()
                        : pi.signingInfo.getSigningCertificateHistory();
                if (sigs == null || sigs.length == 0) return null;
                return h(sigs[0].toByteArray());
            } else {
                pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                if (pi == null || pi.signatures == null || pi.signatures.length == 0) return null;
                return h(pi.signatures[0].toByteArray());
            }
        } catch (Throwable t) {
            return null;
        }
    }

    // =================================================================
    // DEX integrity hash
    // =================================================================
    private static String dexHash(Context ctx) {
        ZipFile z = null;
        InputStream is = null;
        try {
            String apk = ctx.getApplicationInfo().sourceDir;
            z = new ZipFile(apk);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            List<String> names = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.endsWith(".dex")) names.add(n);
            }
            Collections.sort(names);
            if (names.isEmpty()) return null;
            byte[] buf = new byte[16384];
            for (String n : names) {
                ZipEntry e = z.getEntry(n);
                if (e == null) continue;
                is = z.getInputStream(e);
                int k;
                while ((k = is.read(buf)) > 0) md.update(buf, 0, k);
                is.close();
                is = null;
            }
            byte[] hh = md.digest();
            StringBuilder sb = new StringBuilder(hh.length * 2);
            for (byte x : hh) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (is != null) is.close(); } catch (Throwable ignored) { }
            try { if (z != null) z.close(); } catch (Throwable ignored) { }
        }
    }

    // =================================================================
    // Native library integrity hash
    // =================================================================
    public static String computeNativeLibHash(Context ctx) {
        FileInputStream fis = null;
        try {
            File libDir = new File(ctx.getApplicationInfo().nativeLibraryDir);
            File libFile = new File(libDir, "libModXLab.so");
            if (!libFile.exists()) return "";

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            fis = new FileInputStream(libFile);
            byte[] buf = new byte[16384];
            int n;
            while ((n = fis.read(buf)) > 0) md.update(buf, 0, n);

            byte[] hh = md.digest();
            StringBuilder sb = new StringBuilder(hh.length * 2);
            for (byte b : hh) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            return "";
        } finally {
            try { if (fis != null) fis.close(); } catch (Throwable ignored) { }
        }
    }

    // =================================================================
    private static String h(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hh = md.digest(data);
            StringBuilder sb = new StringBuilder(hh.length * 2);
            for (byte b : hh) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) { return null; }
    }

    private SecurityNative() { }
}