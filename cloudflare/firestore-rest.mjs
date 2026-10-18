// Minimal Firestore REST transaction adapter for Workers. Integer values stay BigInt.
export class FirestoreError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

export const SERVER_TIMESTAMP = Symbol('server-timestamp');
const BASE = 'https://firestore.googleapis.com/v1';
const STATUS = new Set(['ABORTED', 'ALREADY_EXISTS', 'FAILED_PRECONDITION', 'NOT_FOUND', 'PERMISSION_DENIED', 'UNAVAILABLE']);

function segments(path) {
  if (typeof path !== 'string' || !path || path.startsWith('/') || path.endsWith('/')) throw new FirestoreError('INVALID_ARGUMENT', 'Invalid Firestore path');
  const parts = path.split('/');
  if (parts.some(part => !part || part === '.' || part === '..' || part.length > 512)) throw new FirestoreError('INVALID_ARGUMENT', 'Invalid Firestore path');
  return parts;
}
function encodePath(path) { return segments(path).map(encodeURIComponent).join('/'); }
function valueOf(value) {
  if (value === null) return null;
  if (!value || typeof value !== 'object') throw new FirestoreError('FAILED_PRECONDITION', 'Invalid Firestore value');
  if ('integerValue' in value) return BigInt(value.integerValue);
  if ('doubleValue' in value) return Number(value.doubleValue);
  if ('stringValue' in value) return value.stringValue;
  if ('booleanValue' in value) return value.booleanValue;
  if ('nullValue' in value) return null;
  if ('timestampValue' in value) return value.timestampValue;
  if ('arrayValue' in value) return (value.arrayValue.values || []).map(valueOf);
  if ('mapValue' in value) return fieldsOf(value.mapValue.fields || {});
  throw new FirestoreError('FAILED_PRECONDITION', 'Unsupported Firestore value');
}
function fieldsOf(fields) { return Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, valueOf(value)])); }
function toValue(value) {
  if (value === null) return { nullValue: null };
  if (typeof value === 'bigint') return { integerValue: value.toString() };
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) throw new FirestoreError('INVALID_ARGUMENT', 'Non-finite number');
    if (Number.isInteger(value)) {
      if (!Number.isSafeInteger(value)) throw new FirestoreError('INVALID_ARGUMENT', 'Unsafe integer');
      return { integerValue: String(value) };
    }
    return { doubleValue: value };
  }
  if (typeof value === 'string') return { stringValue: value };
  if (typeof value === 'boolean') return { booleanValue: value };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(toValue) } };
  if (value && typeof value === 'object' && Object.getPrototypeOf(value) === Object.prototype)
    return { mapValue: { fields: Object.fromEntries(Object.entries(value).map(([key, item]) => [key, toValue(item)])) } };
  throw new FirestoreError('INVALID_ARGUMENT', 'Unsupported Firestore field');
}
function toFields(data) { return Object.fromEntries(Object.entries(data).map(([key, value]) => [key, toValue(value)])); }
function stream(text) {
  const trimmed = text.trim();
  if (!trimmed) return [];
  if (trimmed.startsWith('[')) return JSON.parse(trimmed);
  return trimmed.split(/\r?\n/).filter(Boolean).map(line => JSON.parse(line));
}
function snapshot(document, name) {
  return document ? { exists: true, id: document.name.split('/').at(-1), name: document.name, data: fieldsOf(document.fields || {}), get(key) { return this.data[key]; } }
    : { exists: false, id: name.split('/').at(-1), name, data: null, get() { return undefined; } };
}

export class FirestoreRest {
  constructor(project, token, fetcher = fetch, origin = BASE) {
    if (!/^[a-z][a-z0-9-]{4,61}[a-z0-9]$/.test(project)) throw new FirestoreError('INVALID_ARGUMENT', 'Invalid Firebase project');
    this.project = project;
    this.token = token;
    this.fetcher = fetcher;
    this.root = `${origin}/projects/${project}/databases/(default)/documents`;
    this.database = `${origin}/projects/${project}/databases/(default)`;
    this.documentRoot = `projects/${project}/databases/(default)/documents`;
  }
  name(path) { return `${this.documentRoot}/${segments(path).join('/')}`; }
  async request(url, body) {
    // Cloudflare's global fetch must not be invoked with this adapter as its receiver.
    const fetcher = this.fetcher;
    const response = await fetcher(url, { method: 'POST', headers: { Authorization: `Bearer ${await this.token()}`, 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    const text = await response.text();
    if (!response.ok) {
      let parsed;
      try { parsed = JSON.parse(text); } catch { /* Google can return plain text. */ }
      const status = parsed?.error?.status;
      throw new FirestoreError(STATUS.has(status) ? status : 'UNAVAILABLE', `Firestore ${status || response.status}`);
    }
    return text;
  }
  async transaction(action) {
    for (let attempt = 0; attempt < 3; attempt++) {
      const began = JSON.parse(await this.request(`${this.database}/documents:beginTransaction`, {}));
      const tx = new RestTransaction(this, began.transaction);
      try {
        const result = await action(tx);
        if (tx.writes.length) await this.request(`${this.database}/documents:commit`, { transaction: began.transaction, writes: tx.writes });
        return result;
      } catch (error) {
        if (error?.status !== 'ABORTED' || attempt === 2) throw error;
      }
    }
    throw new FirestoreError('ABORTED', 'Firestore transaction could not complete');
  }
}

class RestTransaction {
  constructor(db, transaction) { this.db = db; this.id = transaction; this.writes = []; }
  async get(path) {
    const name = this.db.name(path);
    const rows = stream(await this.db.request(`${this.db.database}/documents:batchGet`, { documents: [name], transaction: this.id }));
    const row = rows.find(item => item.found?.name === name || item.missing === name);
    if (!row) throw new FirestoreError('UNAVAILABLE', 'Firestore document response missing');
    return snapshot(row.found, name);
  }
  async list(collection, field, equalTo) {
    const parts = segments(collection);
    if (parts.length % 2 !== 1 || parts.length < 3) throw new FirestoreError('INVALID_ARGUMENT', 'Invalid collection path');
    const parent = parts.slice(0, -1).join('/');
    const query = { from: [{ collectionId: parts.at(-1) }] };
    if (field !== undefined) query.where = { fieldFilter: { field: { fieldPath: field }, op: 'EQUAL', value: toValue(equalTo) } };
    const rows = stream(await this.db.request(`${this.db.root}/${encodePath(parent)}:runQuery`, { structuredQuery: query, transaction: this.id }));
    return rows.filter(row => row.document).map(row => snapshot(row.document));
  }
  create(path, data) { this.write(path, data, { exists: false }); }
  set(path, data) { this.writes.push({ update: { name: this.db.name(path), fields: toFields(data) } }); }
  update(path, data) { this.write(path, data, { exists: true }, true); }
  delete(path) { this.writes.push({ delete: this.db.name(path), currentDocument: { exists: true } }); }
  write(path, data, currentDocument, partial = false) {
    const fields = {}, updateTransforms = [];
    for (const [key, value] of Object.entries(data)) {
      if (value === SERVER_TIMESTAMP) updateTransforms.push({ fieldPath: key, setToServerValue: 'REQUEST_TIME' });
      else fields[key] = toValue(value);
    }
    const write = { update: { name: this.db.name(path), fields }, currentDocument };
    if (partial) write.updateMask = { fieldPaths: Object.keys(data) };
    if (updateTransforms.length) write.updateTransforms = updateTransforms;
    this.writes.push(write);
  }
}
