const { Client, Databases, ID, Query } = require('node-appwrite');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');

// ================================================================
// 🔑 Environment Variables (set in Appwrite Function Settings)
// ================================================================
//   JWT_SECRET              — signs the JWT returned to client
//   NATIVE_SHARED_KEY       — HMAC key shared with Android native code
//                             MUST match deriveNativeKey() in Security.cpp
//   ALLOWED_APK_SIGS        — comma-separated list of allowed APK signature SHA-256
//   DATABASE_ID             — Appwrite database ID
//   COLLECTION_ID           — Users collection ID
//   ATTEMPTS_COLLECTION_ID  — login_attempts collection ID (create this!)
//   APPWRITE_API_KEY        — server API key
// ================================================================

const JWT_SECRET = process.env.JWT_SECRET;
const NATIVE_SHARED_KEY = process.env.NATIVE_SHARED_KEY;
const DATABASE_ID = process.env.DATABASE_ID;
const COLLECTION_ID = process.env.COLLECTION_ID;
const ATTEMPTS_COLLECTION_ID = process.env.ATTEMPTS_COLLECTION_ID;

const ALLOWED_APK_SIGS = (process.env.ALLOWED_APK_SIGS || '')
  .split(',')
  .map(s => s.trim().toLowerCase())
  .filter(s => s.length > 0);

const RATE_LIMIT_WINDOW_MS = 5 * 60 * 1000;   // 5 minutes
const RATE_LIMIT_MAX        = 5;              // max attempts per window

// ================================================================
// Helpers
// ================================================================
function hmacNative(payload) {
  return crypto.createHmac('sha256', NATIVE_SHARED_KEY).update(payload).digest('hex');
}

function hmacLogin(user, pass) {
  return crypto.createHmac('sha256', NATIVE_SHARED_KEY).update(user + '|' + pass).digest('hex');
}

function tryParse(val) {
  if (!val) return null;
  if (typeof val === 'object') return val;
  if (typeof val === 'string') {
    try { return JSON.parse(val); } catch (e) { return null; }
  }
  return null;
}

// ================================================================
// MAIN HANDLER
// ================================================================
module.exports = async function ({ req, res, log, error }) {
  try {
    // ── 1) Extract payload ──
    let payload = tryParse(req.bodyJson)
              || tryParse(req.payload)
              || tryParse(req.body)
              || tryParse(req.bodyRaw);

    if (!payload) {
      return res.json({ ok: false, reason: 'bad_input' }, 400);
    }

    const user = (payload.user || '').toString().toLowerCase().trim();
    const pass = (payload.pass || '').toString();
    const apkSig = (payload.apkSig || '').toString().toLowerCase().trim();

    if (!user || !pass) {
      return res.json({ ok: false, reason: 'bad_input' }, 400);
    }

    // ── 2) APK signature attestation ──
    if (ALLOWED_APK_SIGS.length > 0) {
      if (!apkSig || ALLOWED_APK_SIGS.indexOf(apkSig) === -1) {
        if (log) log(`APK sig rejected: ${apkSig}`);
        // Silent fail — don't reveal we're checking
        return res.json({ ok: false, reason: 'invalid_credentials' });
      }
    }

    // ── 3) Appwrite client ──
    const client = new Client()
      .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
      .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
      .setKey(process.env.APPWRITE_API_KEY);

    const databases = new Databases(client);

    // ── 4) Server-side rate limiting ──
    if (ATTEMPTS_COLLECTION_ID) {
      try {
        const windowStart = Date.now() - RATE_LIMIT_WINDOW_MS;
        const recent = await databases.listDocuments(
          DATABASE_ID, ATTEMPTS_COLLECTION_ID,
          [Query.equal('user', user), Query.greaterThan('ts', windowStart)]
        );

        if (recent.total >= RATE_LIMIT_MAX) {
          if (log) log(`Rate limited: ${user} (${recent.total} attempts)`);
          return res.json({ ok: false, reason: 'rate_limited' });
        }
      } catch (e) {
        // Collection may not exist — log and continue
        if (log) log(`Rate limit check skipped: ${e.message}`);
      }
    }

    // ── 5) Query user ──
    const result = await databases.listDocuments(DATABASE_ID, COLLECTION_ID, [
      Query.equal('user', user)
    ]);

    if (result.total === 0) {
      if (log) log(`User not found: ${user}`);
      await recordAttempt(databases, user, false);
      return res.json({ ok: false, reason: 'invalid_credentials' });
    }

    const dbUser = result.documents[0];

    // ── 6) Password verify (bcrypt or plaintext migrate) ──
    let passwordMatched = false;
    if (dbUser.passHash && dbUser.passHash.startsWith('$2')) {
      passwordMatched = await bcrypt.compare(pass, dbUser.passHash);
    } else if (dbUser.passHash === pass) {
      passwordMatched = true;
      const newHash = await bcrypt.hash(pass, 12);
      await databases.updateDocument(DATABASE_ID, COLLECTION_ID, dbUser.$id, {
        passHash: newHash
      });
      if (log) log(`Migrated password to bcrypt: ${user}`);
    }

    if (!passwordMatched) {
      if (log) log(`Password mismatch: ${user}`);
      await recordAttempt(databases, user, false);
      return res.json({ ok: false, reason: 'invalid_credentials' });
    }

    // ── 7) Status / expiry ──
    if (dbUser.status !== 'true') {
      if (log) log(`Blocked: ${user}`);
      return res.json({ ok: false, reason: 'blocked' });
    }

    const now = Date.now();
    if (dbUser.expireAt && now > dbUser.expireAt) {
      if (log) log(`Expired: ${user}`);
      return res.json({ ok: false, reason: 'expired' });
    }

    // ── 8) JWT token ──
    const token = jwt.sign(
      {
        user: dbUser.user,
        exp: Math.floor((dbUser.expireAt || (now + 86400000)) / 1000)
      },
      JWT_SECRET,
      { algorithm: 'HS256' }
    );

    // ── 9) Native HMAC signature ──
    //    MUST match: deriveNativeKey() in Security.cpp
    //    Payload format: user|expiry|true
    const expiry = String(dbUser.expireAt || (now + 86400000));
    const nativeSig = hmacNative(`${dbUser.user}|${expiry}|true`);

    // Successful attempt — clear rate limit record
    await recordAttempt(databases, user, true);

    if (log) log(`Login success: ${user}`);

    return res.json({
      ok: true,
      token: token,
      nativeSig: nativeSig,
      user: dbUser.user,
      status: 'true',
      expiry: expiry
    });

  } catch (e) {
    if (error) error(`Exception: ${e.message}\n${e.stack}`);
    return res.json({
      ok: false,
      reason: 'server_error',
      message: e.message
    }, 500);
  }
};

// ================================================================
// Rate-limit attempt recorder
// ================================================================
async function recordAttempt(databases, user, success) {
  if (!ATTEMPTS_COLLECTION_ID) return;
  try {
    if (success) {
      // On success, don't record a new failure. Optionally delete recent ones.
      const windowStart = Date.now() - RATE_LIMIT_WINDOW_MS;
      const existing = await databases.listDocuments(
        DATABASE_ID, ATTEMPTS_COLLECTION_ID,
        [Query.equal('user', user), Query.greaterThan('ts', windowStart)]
      );
      for (const doc of existing.documents) {
        await databases.deleteDocument(DATABASE_ID, ATTEMPTS_COLLECTION_ID, doc.$id);
      }
    } else {
      await databases.createDocument(
        DATABASE_ID, ATTEMPTS_COLLECTION_ID, ID.unique(),
        { user: user, ts: Date.now() }
      );
    }
  } catch (e) {
    // Non-critical — just skip
  }
}