import test from 'node:test';
import assert from 'node:assert/strict';

import { filterAuditLogs, formatAuditDate, getStatusTone } from '../src/utils/audit.js';

const logs = [
  {
    id: 1,
    email: 'driver1@gml.local',
    action: 'GET /api/missions/5/secret-code',
    ipAddress: '10.0.0.1',
    statusCode: 200,
  },
  { id: 2, email: null, action: 'POST /api/auth/login', ipAddress: '10.0.0.2', statusCode: 401 },
];

test('HTTP status families get distinct tones', () => {
  assert.equal(getStatusTone(201), 'success');
  assert.equal(getStatusTone(409), 'warning');
  assert.equal(getStatusTone(503), 'error');
});

test('the filter matches action, email or status without case sensitivity', () => {
  assert.deepEqual(
    filterAuditLogs(logs, 'SECRET').map((log) => log.id),
    [1],
  );
  assert.deepEqual(
    filterAuditLogs(logs, '401').map((log) => log.id),
    [2],
  );
  assert.equal(filterAuditLogs(logs, '  ').length, 2);
});

test('an invalid date is displayed as a dash', () => {
  assert.equal(formatAuditDate('not-a-date'), '—');
  assert.match(formatAuditDate('2026-10-28T09:12:00Z'), /2026|26/);
});
