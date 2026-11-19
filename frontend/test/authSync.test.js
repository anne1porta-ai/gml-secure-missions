import test from 'node:test';
import assert from 'node:assert/strict';

import { createAuthSync } from '../src/utils/authSync.js';

// Canal factice : enregistre les messages émis et simule ceux d'un autre onglet.
function createFakeChannel() {
  const listeners = new Set();
  const sentMessages = [];

  return {
    sentMessages,
    postMessage(message) {
      sentMessages.push(message);
    },
    addEventListener(type, listener) {
      if (type === 'message') {
        listeners.add(listener);
      }
    },
    removeEventListener(type, listener) {
      if (type === 'message') {
        listeners.delete(listener);
      }
    },
    receiveFromAnotherTab(data) {
      listeners.forEach((listener) => listener({ data }));
    },
  };
}

test('a login or logout is announced to the other tabs', () => {
  const channel = createFakeChannel();

  createAuthSync(channel).broadcastAuthChange();

  assert.deepEqual(channel.sentMessages, ['auth-changed']);
});

test('a session change in another tab triggers the callback', () => {
  const channel = createFakeChannel();
  let calls = 0;

  createAuthSync(channel).subscribeToAuthChanges(() => {
    calls += 1;
  });
  channel.receiveFromAnotherTab('auth-changed');

  assert.equal(calls, 1);
});

test('unrelated messages are ignored', () => {
  const channel = createFakeChannel();
  let calls = 0;

  createAuthSync(channel).subscribeToAuthChanges(() => {
    calls += 1;
  });
  channel.receiveFromAnotherTab('something-else');

  assert.equal(calls, 0);
});

test('unsubscribing stops the notifications', () => {
  const channel = createFakeChannel();
  let calls = 0;

  const unsubscribe = createAuthSync(channel).subscribeToAuthChanges(() => {
    calls += 1;
  });
  unsubscribe();
  channel.receiveFromAnotherTab('auth-changed');

  assert.equal(calls, 0);
});

test('without BroadcastChannel support nothing breaks', () => {
  const authSync = createAuthSync(null);

  assert.doesNotThrow(() => authSync.broadcastAuthChange());
  assert.equal(typeof authSync.subscribeToAuthChanges(() => {}), 'function');
});
