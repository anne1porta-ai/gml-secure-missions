import test from 'node:test';
import assert from 'node:assert/strict';

import { generateTemporaryPassword } from '../src/utils/password.js';

const ALPHABET = /^[ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789]+$/;

test('a temporary password has 16 characters by default', () => {
  const password = generateTemporaryPassword(16, (values) => values.fill(7));

  assert.equal(password.length, 16);
});

test('ambiguous characters are never used', () => {
  const password = generateTemporaryPassword(64, (values) => {
    values.forEach((_, index) => {
      values[index] = index * 7919;
    });
  });

  assert.match(password, ALPHABET);
});

test('the random source drives the generated characters', () => {
  const password = generateTemporaryPassword(3, (values) => values.set([0, 1, 2]));

  assert.equal(password, 'ABC');
});
