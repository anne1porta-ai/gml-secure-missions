import test from 'node:test';
import assert from 'node:assert/strict';

import { getErrorMessage } from '../src/utils/errorMessage.js';

const FALLBACK = 'Action impossible.';

test('the business message returned by the API is displayed', () => {
  const error = { response: { status: 400, data: { error: 'Chauffeur introuvable' } } };

  assert.equal(getErrorMessage(error, FALLBACK), 'Chauffeur introuvable');
});

test('validation details are appended to the message', () => {
  const error = {
    response: {
      status: 400,
      data: { error: 'Requête invalide', details: ['title : ne doit pas être vide'] },
    },
  };

  assert.equal(
    getErrorMessage(error, FALLBACK),
    'Requête invalide (title : ne doit pas être vide)',
  );
});

test('a response without a readable body falls back to the action message', () => {
  const error = { response: { status: 500, data: undefined } };

  assert.equal(getErrorMessage(error, FALLBACK), FALLBACK);
});

test('a request without any response reports an unreachable server', () => {
  const error = { request: {} };

  assert.match(getErrorMessage(error, FALLBACK), /Serveur injoignable/);
});

test('an unexpected error falls back to the action message', () => {
  assert.equal(getErrorMessage(new Error('boom'), FALLBACK), FALLBACK);
});
