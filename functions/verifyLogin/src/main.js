const { Client, Databases, Query } = require('node-appwrite');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET;
const DATABASE_ID = process.env.DATABASE_ID;
const COLLECTION_ID = process.env.COLLECTION_ID;

module.exports = async function ({ req, res, log, error }) {
  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY);
  
  const databases = new Databases(client);

  try {
    // Parse body — handles both string and object (different Appwrite versions)
    let payload = {};
    if (typeof req.body === 'string') {
      payload = JSON.parse(req.body || '{}');
    } else if (req.body && typeof req.body === 'object') {
      payload = req.body;
    }

    const { user, pass } = payload;
    if (!user || !pass) {
      if (log) log('Missing user or pass');
      return res.json({ ok: false, reason: 'bad_input' }, 400);
    }

    if (log) log(`Login attempt for: ${user}`);

    // Find user
    const result = await databases.listDocuments(DATABASE_ID, COLLECTION_ID, [
      Query.equal('user', user)
    ]);

    if (result.total === 0) {
      if (log) log(`User not found: ${user}`);
      return res.json({ ok: false, reason: 'invalid_credentials' });
    }

    const dbUser = result.documents[0];

    // Verify password
    let passwordMatched = false;
    if (dbUser.passHash && dbUser.passHash.startsWith('$2')) {
      // bcrypt hash
      passwordMatched = await bcrypt.compare(pass, dbUser.passHash);
    } else if (dbUser.passHash === pass) {
      // Plaintext (first login) — migrate to bcrypt
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

    // Check status
    if (dbUser.status !== 'true') {
      if (log) log(`User blocked: ${user}`);
      return res.json({ ok: false, reason: 'blocked' });
    }

    // Check expiry
    const now = Date.now();
    if (dbUser.expireAt && now > dbUser.expireAt) {
      if (log) log(`User expired: ${user}`);
      return res.json({ ok: false, reason: 'expired' });
    }

    // Generate JWT
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
    if (error) error(`Exception: ${e.message}`);
    return res.json({ 
      ok: false, 
      reason: 'server_error',
      message: e.message 
    }, 500);
  }
};
