import test from 'node:test';
import assert from 'node:assert/strict';

import { createMissionNotificationConnection } from '../src/hooks/useMissionNotifications.js';

test('network error schedules reconnection and REST resynchronizes state', async () => {
  const eventSources = [];
  const scheduledCallbacks = [];
  const notifications = [];
  let refreshCount = 0;
  let connectedCount = 0;
  let disconnectedCount = 0;

  const connection = createMissionNotificationConnection({
    backendUrl: 'http://backend.test',
    refreshMissions: async () => {
      refreshCount += 1;
    },
    onConnected: () => {
      connectedCount += 1;
    },
    onDisconnected: () => {
      disconnectedCount += 1;
    },
    onNotification: (notification) => {
      notifications.push(notification);
    },
    eventSourceFactory: (url, options) => {
      const source = new FakeEventSource(url, options);
      eventSources.push(source);
      return source;
    },
    schedule: (callback, delay) => {
      scheduledCallbacks.push({ callback, delay });
      return scheduledCallbacks.length;
    },
    cancelSchedule: () => {},
    reconnectDelayMs: 25,
    logError: () => {},
  });

  await flushAsyncWork();

  assert.equal(refreshCount, 1);
  assert.equal(eventSources.length, 1);
  assert.equal(eventSources[0].url, 'http://backend.test/api/notifications/stream');
  assert.equal(eventSources[0].options.withCredentials, true);

  eventSources[0].onopen();
  assert.equal(connectedCount, 1);

  eventSources[0].onerror();
  assert.equal(disconnectedCount, 1);
  assert.equal(scheduledCallbacks.length, 1);
  assert.equal(scheduledCallbacks[0].delay, 25);

  scheduledCallbacks[0].callback();
  await flushAsyncWork();

  assert.equal(refreshCount, 2);
  assert.equal(eventSources.length, 2);

  eventSources[1].onopen();
  const newMissionHandler = eventSources[1].listeners.get('NEW_MISSION');
  newMissionHandler({
    data: JSON.stringify({ type: 'NEW_MISSION', missionId: 42 }),
  });
  await flushAsyncWork();

  assert.deepEqual(notifications, [{ type: 'NEW_MISSION', missionId: 42 }]);
  assert.equal(refreshCount, 3);

  connection.disconnect();
  assert.equal(eventSources[1].closed, true);
});

class FakeEventSource {
  constructor(url, options) {
    this.url = url;
    this.options = options;
    this.listeners = new Map();
    this.closed = false;
    this.onopen = null;
    this.onerror = null;
  }

  addEventListener(eventName, handler) {
    this.listeners.set(eventName, handler);
  }

  close() {
    this.closed = true;
  }
}

async function flushAsyncWork() {
  await Promise.resolve();
  await Promise.resolve();
}

test('initial REST failure schedules retry before opening SSE', async () => {
  const scheduledCallbacks = [];
  let attempts = 0;
  let eventSourceCreated = false;

  createMissionNotificationConnection({
    refreshMissions: async () => {
      attempts += 1;
      if (attempts === 1) {
        throw new Error('temporary network failure');
      }
    },
    onConnected: () => {},
    onDisconnected: () => {},
    onNotification: () => {},
    eventSourceFactory: () => {
      eventSourceCreated = true;
      return new FakeEventSource('', {});
    },
    schedule: (callback, delay) => {
      scheduledCallbacks.push({ callback, delay });
      return scheduledCallbacks.length;
    },
    cancelSchedule: () => {},
    reconnectDelayMs: 25,
    logError: () => {},
  });

  await flushAsyncWork();

  assert.equal(eventSourceCreated, false);
  assert.equal(scheduledCallbacks.length, 1);

  scheduledCallbacks[0].callback();
  await flushAsyncWork();

  assert.equal(attempts, 2);
  assert.equal(eventSourceCreated, true);
});

test('mission change events refresh the list', async () => {
  const sources = [];
  const notifications = [];
  let refreshCount = 0;

  const connection = createMissionNotificationConnection({
    refreshMissions: async () => {
      refreshCount += 1;
    },
    onConnected: () => {},
    onDisconnected: () => {},
    onNotification: (notification) => notifications.push(notification),
    eventSourceFactory: () => {
      const source = new FakeEventSource('', {});
      sources.push(source);
      return source;
    },
    logError: () => {},
  });

  await flushAsyncWork();
  assert.equal(refreshCount, 1);

  const types = ['MISSION_UPDATED', 'MISSION_CANCELLED', 'MISSION_REASSIGNED'];
  for (const type of types) {
    const handler = sources[0].listeners.get(type);
    assert.equal(typeof handler, 'function');

    handler({
      data: JSON.stringify({ type, missionId: 42, title: 'Livraison' }),
    });
    await flushAsyncWork();
  }

  assert.equal(refreshCount, 4);
  assert.deepEqual(
    notifications.map((item) => item.type),
    types,
  );
  connection.disconnect();
});
