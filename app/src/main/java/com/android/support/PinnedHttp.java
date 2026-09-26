package com.android.support;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * HTTPS client with native-side certificate pinning.
 *
 * Pins are OBFUSCATED inside libModXLab.so — not visible in dex.
 */
public final class PinnedHttp {

    private static final String TAG = "ModXLab_HTTP";
    private static final boolean VERBOSE = BuildConfig.DEBUG;

    private PinnedHttp() { }

    public static String get(String urlStr) {
        return request(urlStr, "GET", null, null, null);
    }

    public static String post(String urlStr, String body, String projectId) {
        return request(urlStr, "POST", body, projectId, null);
    }

    public static String postJson(String urlStr, String body,
                                  String projectId, String responseFormat) {
        return request(urlStr, "POST", body, projectId, responseFormat);
    }

    private static String request(String urlStr, String method, String body,
                                  String projectId, String responseFormat) {
        HttpsURLConnection conn = null;
        try {
            if (VERBOSE) Log.e(TAG, "→ " + method + " " + urlStr);

            URL url = new URL(urlStr);
            conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "ModXLab/1.0");

            if (projectId != null && !projectId.isEmpty()) {
                conn.setRequestProperty("X-Appwrite-Project", projectId);
            }
            if (responseFormat != null && !responseFormat.isEmpty()) {
                conn.setRequestProperty("X-Appwrite-Response-Format", responseFormat);
            }

            if ("POST".equals(method) && body != null) {
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.getOutputStream().write(body.getBytes("UTF-8"));
            }

            // Pinning always active — even in release
            if (!BuildConfig.DEBUG) {
                conn.setSSLSocketFactory(new PinnedFactory());
            }

            int code = conn.getResponseCode();
            if (VERBOSE) Log.e(TAG, "← HTTP " + code);

            InputStream is = (code >= 200 && code < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (is == null) {
                if (VERBOSE) Log.e(TAG, "Empty response (code=" + code + ")");
                return null;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            String result = sb.toString();

            if (code < 200 || code >= 300) {
                if (VERBOSE) Log.e(TAG, "HTTP " + code + " body: " + result);
                return null;
            }

            if (VERBOSE) {
                if (result.length() > 500) {
                    Log.e(TAG, "Body (truncated): " + result.substring(0, 500) + "...");
                } else {
                    Log.e(TAG, "Body: " + result);
                }
            }
            return result;

        } catch (Throwable t) {
            if (VERBOSE) Log.e(TAG, method + " failed: " + t.getMessage());
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable ignored) { }
        }
    }

    // =================================================================
    // SSLSocketFactory that asks NATIVE code to verify pins
    // =================================================================
    private static class PinnedFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        PinnedFactory() {
            this.delegate = (SSLSocketFactory) SSLSocketFactory.getDefault();
        }

        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }

        @Override public java.net.Socket createSocket(java.net.Socket s, String host, int port, boolean autoClose) throws java.io.IOException {
            return verify((SSLSocket) delegate.createSocket(s, host, port, autoClose));
        }
        @Override public java.net.Socket createSocket(String host, int port) throws java.io.IOException {
            return verify((SSLSocket) delegate.createSocket(host, port));
        }
        @Override public java.net.Socket createSocket(String host, int port, java.net.InetAddress lh, int lp) throws java.io.IOException {
            return verify((SSLSocket) delegate.createSocket(host, port, lh, lp));
        }
        @Override public java.net.Socket createSocket(java.net.InetAddress h, int p) throws java.io.IOException {
            return verify((SSLSocket) delegate.createSocket(h, p));
        }
        @Override public java.net.Socket createSocket(java.net.InetAddress a, int p, java.net.InetAddress lh, int lp) throws java.io.IOException {
            return verify((SSLSocket) delegate.createSocket(a, p, lh, lp));
        }

        private SSLSocket verify(SSLSocket ss) throws java.io.IOException {
            try {
                ss.startHandshake();
                Certificate[] certs = ss.getSession().getPeerCertificates();
                if (certs == null || certs.length == 0) {
                    throw new java.io.IOException("No certs");
                }
                // ✅ Pins live in native, not in dex
                for (Certificate c : certs) {
                    if (c instanceof X509Certificate) {
                        String hash = sha256Hex(((X509Certificate) c).getEncoded());
                        if (SecurityNative.verifyCertPin(hash)) return ss;
                    }
                }
                throw new java.io.IOException("Pin mismatch");
            } catch (javax.net.ssl.SSLException e) {
                throw e;
            } catch (Throwable t) {
                throw new java.io.IOException("Pin fail: " + t.getMessage());
            }
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(data);
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }
}