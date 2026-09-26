const { Client, Databases, Query } = require('node-appwrite');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET;
const DATABASE_ID = process.env.DATABASE_ID;
const COLLECTION_ID = 'Users';

module.exports = async function (req, res) {
  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY);
  
  const databases = new Databases(client);

  try {
    const { user, pass } = JSON.parse(req.body || '{}');
    if (!user || !pass) {
      return res.json({ ok: false, reason: 'bad_input' }, 400);
    }

    const result = await databases.listDocuments(DATABASE_ID, COLLECTION_ID, [
      Query.equal('user', user)
    ]);

    if (result.total === 0) {
      return res.json({ ok: false, reason: 'invalid_credentials' });
    }

    const dbUser = result.documents[0];

    let passwordMatched = false;
    if (dbUser.passHash && dbUser.passHash.startsWith('$2')) {
      passwordMatched = await bcrypt.compare(pass, dbUser.passHash);
    } else if (dbUser.passHash === pass) {
      passwordMatched = true;
      const newHash = await bcrypt.hash(pass, 12);
      await databases.updateDocument(DATABASE_ID, COLLECTION_ID, dbUser.$id, {
        passHash: newHash
      });
    }

    if (!passwordMatched) {
      return res.json({ ok: false, reason: 'invalid_credentials' });
    }

    if (dbUser.status !== 'true') {
      return res.json({ ok: false, reason: 'blocked' });
    }

    const now = Date.now();
    if (dbUser.expireAt && now > dbUser.expireAt) {
      return res.json({ ok: false, reason: 'expired' });
    }

    const token = jwt.sign(
      { user: dbUser.user, exp: Math.floor((dbUser.expireAt || (now + 86400000)) / 1000) },
      JWT_SECRET,
      { algorithm: 'HS256' }
    );

    return res.json({
      ok: true,
      token: token,
      user: dbUser.user,
      status: 'true',
      expiry: String(dbUser.expireAt)
    });

  } catch (error) {
    console.error('Error:', error);
    return res.json({ ok: false, reason: 'server_error' }, 500);
  }
};
