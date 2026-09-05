import { IdempotencyAttempt, idempotencyHeaders, newIdempotencyKey } from './idempotency.util';

describe('newIdempotencyKey', () => {
  it('returns a fresh UUID each call', () => {
    const a = newIdempotencyKey();
    const b = newIdempotencyKey();
    expect(a).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
    expect(a).not.toBe(b);
  });
});

describe('idempotencyHeaders', () => {
  it('carries the key when present', () => {
    expect(idempotencyHeaders('k1')?.get('Idempotency-Key')).toBe('k1');
  });

  it('is undefined when no key', () => {
    expect(idempotencyHeaders(null)).toBeUndefined();
    expect(idempotencyHeaders(undefined)).toBeUndefined();
  });
});

describe('IdempotencyAttempt', () => {
  it('keeps the same key while the payload is unchanged (retry of the same action)', () => {
    const attempt = new IdempotencyAttempt();
    const payload = { amountXof: '100000', supplierId: 's1' };
    const k1 = attempt.keyFor(payload);
    const k2 = attempt.keyFor({ ...payload });
    expect(k2).toBe(k1);
  });

  it('is insensitive to property order', () => {
    const attempt = new IdempotencyAttempt();
    const k1 = attempt.keyFor({ a: 1, b: 2, nested: { x: 1, y: 2 } });
    const k2 = attempt.keyFor({ nested: { y: 2, x: 1 }, b: 2, a: 1 });
    expect(k2).toBe(k1);
  });

  it('mints a NEW key when the payload changes (new user intent)', () => {
    const attempt = new IdempotencyAttempt();
    const k1 = attempt.keyFor({ amountXof: '100000' });
    const k2 = attempt.keyFor({ amountXof: '150000' });
    expect(k2).not.toBe(k1);
  });

  it('mints a new key for the next action after complete()', () => {
    const attempt = new IdempotencyAttempt();
    const payload = { amountXof: '100000' };
    const k1 = attempt.keyFor(payload);
    attempt.complete();
    const k2 = attempt.keyFor(payload);
    expect(k2).not.toBe(k1);
  });
});
