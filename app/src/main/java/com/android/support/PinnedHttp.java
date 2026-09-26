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
 * HTTPS client with certificate pinning + diagnostic logging.
 *
 * Get the CURRENT pin hashes via Termux:
 *   echo | openssl s_client -servername sgp.cloud.appwrite.io \
 *     -connect sgp.cloud.appwrite.io:443 2>/dev/null \
 *     | openssl x509 -fingerprint -sha256 -noout -in /dev/stdin \
 *     | sed 's/.*=//' | tr -d ':' | tr 'A-Z' 'a-z'
 */
public final class PinnedHttp {

    private static final String TAG = "ModXLab_HTTP";

    // ⚠️ Update these if pin mismatch occurs (logcat shows actual hash).
    private static final Set<String> PINNED = new HashSet<>(Arrays.asList(
        // Appwrite Singapore — leaf cert
        "6bd255ea86d4cf05e8aed3d6e071895b8c29736ba83908dbcf409817aa8b03ed",
        // Google Trust Services — intermediate
        "fec41e32ca75c295a6240fa639d3abe3bfb5cb131d6690e2331a176bed2e5bd2",
        // Firebase
        "170b2def1e9c89c59970f25c62ffe64c0fba73989cd29a098dc0a2d405d87ed7"
    ));

    private PinnedHttp() { }

    /** GET request with cert pinning. */
    public static String get(String urlStr) {
        return request(urlStr, "GET", null, null, null);
    }

    /** POST request with Appwrite project header (legacy signature). */
    public static String post(String urlStr, String body, String projectId) {
        return request(urlStr, "POST", body, projectId, null);
    }

    /** POST with explicit response format header (recommended for Appwrite). */
    public static String postJson(String urlStr, String body,
                                  String projectId, String responseFormat) {
        return request(urlStr, "POST", body, projectId, responseFormat);
    }

    private static String request(String urlStr, String method, String body,
                                  String projectId, String responseFormat) {
        HttpsURLConnection conn = null;
        try {
            Log.d(TAG, "→ " + method + " " + urlStr);
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

            // Skip pinning in debug builds
            if (!com.android.support.BuildConfig.DEBUG) {
                conn.setSSLSocketFactory(new PinnedFactory());
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "← HTTP " + code);

            InputStream is = (code >= 200 && code < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (is == null) {
                Log.w(TAG, "Empty response stream");
                return null;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            String result = sb.toString();

            if (code < 200 || code >= 300) {
                Log.w(TAG, "HTTP " + code + " body: " + result);
                return null;
            }

            if (result.length() > 800) {
                Log.d(TAG, "Body: " + result.substring(0, 800) + "...");
            } else {
                Log.d(TAG, "Body: " + result);
            }
            return result;

        } catch (Throwable t) {
            Log.e(TAG, method + " failed: "
                    + t.getClass().getSimpleName() + " — " + t.getMessage(), t);
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable ignored) { }
        }
    }

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
                boolean matched = false;
                StringBuilder actual = new StringBuilder();
                for (Certificate c : certs) {
                    if (c instanceof X509Certificate) {
                        String hash = sha256Hex(((X509Certificate) c).getEncoded());
                        actual.append(hash).append(" ");
                        if (PINNED.contains(hash)) matched = true;
                    }
                }
                if (matched) return ss;
                // 🔍 Log actual hashes → copy to PINNED above
                Log.e(TAG, "PIN MISMATCH. Actual hashes: " + actual.toString().trim());
                throw new java.io.IOException(
                        "Pin mismatch. Update PINNED with: " + actual.toString().trim());
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