import test from 'node:test';
import assert from 'node:assert/strict';

import { buildScheduledAt, formatScheduledAt } from '../src/utils/missionDetails.js';

test('a planned date is displayed in French', () => {
  const label = formatScheduledAt('2026-11-02T08:30:00');

  assert.match(label, /2026/);
  assert.match(label, /08:30/);
});

test('a missing or invalid date is not displayed', () => {
  assert.equal(formatScheduledAt(null), null);
  assert.equal(formatScheduledAt('pas-une-date'), null);
});

test('a planned date needs both the day and the time, or neither', () => {
  assert.deepEqual(buildScheduledAt('', ''), { value: null, error: null });
  assert.deepEqual(buildScheduledAt('2026-10-10', '08:30'), {
    value: '2026-10-10T08:30',
    error: null,
  });
  assert.match(buildScheduledAt('2026-10-10', '').error, /date et l'heure/);
  assert.match(buildScheduledAt('', '08:30').error, /date et l'heure/);
});
