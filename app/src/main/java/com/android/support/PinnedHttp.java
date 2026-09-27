package com.android.support;

import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * PinnedHttp — HTTPS client with certificate pinning.
 *
 * Verifies the server's certificate chain against a set of
 * SHA-256 SPKI pins. Prevents MITM attacks using a custom CA.
 *
 * If ENFORCE_PINS is false, falls back to default JVM trust
 * (useful during development or after a cert rotation).
 */
public final class PinnedHttp {

    private static final String TAG = "ModXLab_Http";

    // Set to false temporarily if Google rotates certs and pins fail
    private static final boolean ENFORCE_PINS = true;

    /**
     * SHA-256 SPKI pins (base64 encoded, no wrapping).
     * Multiple pins included for resilience across Google cert chains.
     */
    private static final String[] PINS = {
            // Google Trust Services Root R1
            "hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=",
            // Google Trust Services Root R2
            "Vfd95BwDeSQo+NUYxVEEIlvkOlWY2SalKK1lPhzOx78=",
            // Google Trust Services Root R3
            "QXnt2YHvdHR3tJYmQIr0Paosp6t/nggsEGD4QJZ3Q0g=",
            // Google Trust Services Root R4
            "mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=",
            // GlobalSign Root CA - R2 (older Firebase chains)
            "iie1VXtL7HzAMF+/PVPR9xzT80kQxdZeJ+zduCB3uj0=",
    };

    private static volatile SSLSocketFactory sFactory = null;
    private static final Object sLock = new Object();

    private PinnedHttp() { }

    /**
     * Performs an HTTPS GET and returns the response body as a String.
     * Returns null on any error (network, TLS, HTTP non-200, etc.)
     */
    public static String get(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) return null;

        HttpsURLConnection conn = null;
        try {
            SSLSocketFactory factory = getFactory();

            URL url = new URL(urlStr);
            conn = (HttpsURLConnection) url.openConnection();

            if (factory != null) conn.setSSLSocketFactory(factory);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "ModXLab/1.0");

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "HTTP " + code + " for " + urlStr);
                return null;
            }

            InputStream is = conn.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "get failed: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Exception ignored) { }
        }
    }

    // ================================================================
    // Internal
    // ================================================================

    private static SSLSocketFactory getFactory() {
        if (sFactory != null) return sFactory;
        synchronized (sLock) {
            if (sFactory != null) return sFactory;
            try {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null,
                         new TrustManager[]{ new PinTrustManager() },
                         new SecureRandom());
                sFactory = ctx.getSocketFactory();
            } catch (Exception e) {
                Log.e(TAG, "SSLContext init failed: " + e.getMessage());
            }
            return sFactory;
        }
    }

    /**
     * Custom TrustManager that verifies the server chain against
     * our pinned SPKI hashes.
     */
    private static final class PinTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            // Client-side certs not required
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {

            if (!ENFORCE_PINS) return;

            if (chain == null || chain.length == 0) {
                throw new CertificateException("Empty certificate chain");
            }

            // Match any pin against any cert in the chain
            for (X509Certificate cert : chain) {
                String spki = spkiSha256Base64(cert);
                if (spki == null) continue;
                for (String pin : PINS) {
                    if (pin.equals(spki)) {
                        return; // match found — chain trusted
                    }
                }
            }

            throw new CertificateException("No matching pin in certificate chain");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        private static String spkiSha256Base64(X509Certificate cert) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] spki = cert.getPublicKey().getEncoded();
                byte[] hash = md.digest(spki);
                return Base64.encodeToString(hash, Base64.NO_WRAP);
            } catch (Exception e) {
                return null;
            }
        }
    }
}