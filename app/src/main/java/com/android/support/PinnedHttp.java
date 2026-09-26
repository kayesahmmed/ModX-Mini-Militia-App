package com.android.support;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * HTTPS client with certificate pinning.
 *
 * Pinned certs are extracted via Termux:
 *   echo | openssl s_client -servername sgp.cloud.appwrite.io \
 *     -connect sgp.cloud.appwrite.io:443 2>/dev/null \
 *     | openssl x509 -fingerprint -sha256 -noout -in /dev/stdin \
 *     | sed 's/.*=//' | tr -d ':' | tr 'A-Z' 'a-z'
 */
public final class PinnedHttp {

    private static final String TAG = "ModXLab_Pin";

    // ⚠️ REPLACE THESE with actual hashes from Termux command above
    private static final Set<String> PINNED = new HashSet<>(Arrays.asList(
        // Appwrite Singapore - leaf cert
        "6bd255ea86d4cf05e8aed3d6e071895b8c29736ba83908dbcf409817aa8b03ed",
        // Google Trust Services - intermediate (same for all GCP properties)
        "fec41e32ca75c295a6240fa639d3abe3bfb5cb131d6690e2331a176bed2e5bd2",
        // Firebase - still needed for update checks
        "170b2def1e9c89c59970f25c62ffe64c0fba73989cd29a098dc0a2d405d87ed7"
    ));

    private PinnedHttp() { }

    /** GET request with cert pinning. */
    public static String get(String urlStr) {
        return request(urlStr, "GET", null, null);
    }

    /** POST request with Appwrite project header. */
    public static String post(String urlStr, String body, String projectId) {
        return request(urlStr, "POST", body, projectId);
    }

    private static String request(String urlStr, String method, String body, String projectId) {
        HttpsURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "application/json");

            if (projectId != null && !projectId.isEmpty()) {
                conn.setRequestProperty("X-Appwrite-Project", projectId);
            }

            if ("POST".equals(method) && body != null) {
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.getOutputStream().write(body.getBytes("UTF-8"));
            }

            // Skip pinning in debug builds for easier debugging
            if (!com.android.support.BuildConfig.DEBUG) {
                conn.setSSLSocketFactory(new PinnedFactory());
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                Log.w(TAG, method + " returned HTTP " + code);
                return null;
            }

            InputStream is = conn.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();

        } catch (Throwable t) {
            Log.e(TAG, method + " failed: " + t.getMessage());
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable ignored) { }
        }
    }

        private static class PinnedFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        PinnedFactory() {
            // ✅ Correct way — use SSLSocketFactory.getDefault()
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
                for (Certificate c : certs) {
                    if (c instanceof X509Certificate) {
                        String hash = sha256Hex(((X509Certificate) c).getEncoded());
                        if (PINNED.contains(hash)) return ss;
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