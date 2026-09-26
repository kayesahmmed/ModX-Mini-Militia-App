const { Client, Databases, Query } = require('node-appwrite');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET;
const DATABASE_ID = process.env.DATABASE_ID;
const COLLECTION_ID = process.env.COLLECTION_ID;

module.exports = async function ({ req, res, log, error }) {
  try {
    // ============================================================
    // 🔍 DIAGNOSTIC LOG — remove after debugging
    // ============================================================
    if (log) {
      log('=== REQUEST DEBUG ===');
      log('req keys: ' + Object.keys(req).join(','));
      log('body type: ' + typeof req.body);
      log('body value: ' + JSON.stringify(req.body));
      log('bodyRaw: ' + (req.bodyRaw || 'undefined'));
      log('bodyJson: ' + JSON.stringify(req.bodyJson));
      log('payload: ' + JSON.stringify(req.payload));
      log('=== END DEBUG ===');
    }

    // ============================================================
    // 🔧 ROBUST BODY EXTRACTION — tries every possible location
    // ============================================================
    let payload = null;

    const tryParse = (val) => {
      if (!val) return null;
      if (typeof val === 'object') return val;
      if (typeof val === 'string') {
        try { return JSON.parse(val); } catch (e) { return null; }
      }
      return null;
    };

    // Try all possible body locations
    payload = tryParse(req.bodyJson)
           || tryParse(req.payload)
           || tryParse(req.body)
           || tryParse(req.bodyRaw);

    if (!payload) {
      if (log) log('ERROR: Could not extract body from request');
      return res.json({ ok: false, reason: 'bad_input' }, 400);
    }

    const user = payload.user;
    const pass = payload.pass;

    if (log) log(`Extracted: user=${user}, pass=${pass ? '***' : 'undefined'}`);

    if (!user || !pass) {
      if (log) log('ERROR: user or pass missing from payload');
      return res.json({ ok: false, reason: 'bad_input' }, 400);
    }

    // ============================================================
    // 🔌 Appwrite Client
    // ============================================================
    const client = new Client()
      .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
      .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
      .setKey(process.env.APPWRITE_API_KEY);

    const databases = new Databases(client);

    // ============================================================
    // 🔍 Query user
    // ============================================================
    if (log) log(`Querying DB: ${DATABASE_ID}/${COLLECTION_ID} for user=${user}`);

    const result = await databases.listDocuments(DATABASE_ID, COLLECTION_ID, [
      Query.equal('user', user)
    ]);

    if (result.total === 0) {
      if (log) log(`User not found: ${user}`);
      return res.json({ ok: false, reason: 'invalid_credentials' });
    }

    const dbUser = result.documents[0];
    if (log) log(`Found user: ${dbUser.$id}`);

    // ============================================================
    // 🔐 Password verify
    // ============================================================
    let passwordMatched = false;
    if (dbUser.passHash && dbUser.passHash.startsWith('$2')) {
      passwordMatched = await bcrypt.compare(pass, dbUser.passHash);
    } else if (dbUser.passHash === pass) {
      passwordMatched = true;
      const newHash = await bcrypt.hash(pass, 12);
      await databases.updateDocument(DATABASE_ID, COLLECTION_ID, dbUser.$id, {
        passHash: newHash
      });
      if (log) log(`Password migrated to bcrypt for: ${user}`);
    }

    if (!passwordMatched) {
      if (log) log(`Password mismatch for: ${user}`);
      return res.json({ ok: false, reason: 'invalid_credentials' });
    }

    if (dbUser.status !== 'true') {
      if (log) log(`Blocked: ${user}`);
      return res.json({ ok: false, reason: 'blocked' });
    }

    const now = Date.now();
    if (dbUser.expireAt && now > dbUser.expireAt) {
      if (log) log(`Expired: ${user}`);
      return res.json({ ok: false, reason: 'expired' });
    }

    // ============================================================
    // 🎫 JWT Token
    // ============================================================
    const token = jwt.sign(
      { user: dbUser.user, exp: Math.floor((dbUser.expireAt || (now + 86400000)) / 1000) },
      JWT_SECRET,
      { algorithm: 'HS256' }
    );

    if (log) log(`Login success: ${user}`);

    return res.json({
      ok: true,
      token: token,
      user: dbUser.user,
      status: 'true',
      expiry: String(dbUser.expireAt)
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
