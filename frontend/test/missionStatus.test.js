import test from 'node:test';
import assert from 'node:assert/strict';

import { getMissionStatusLabel, getNextMissionStatus } from '../src/utils/missionStatus.js';

test('PENDING advances to IN_PROGRESS', () => {
  assert.equal(getNextMissionStatus('PENDING'), 'IN_PROGRESS');
});

test('IN_PROGRESS advances to COMPLETED', () => {
  assert.equal(getNextMissionStatus('IN_PROGRESS'), 'COMPLETED');
});

test('COMPLETED has no next status', () => {
  assert.equal(getNextMissionStatus('COMPLETED'), null);
});

test('status codes are displayed with French labels', () => {
  assert.equal(getMissionStatusLabel('PENDING'), 'En attente');
  assert.equal(getMissionStatusLabel('IN_PROGRESS'), 'En cours');
  assert.equal(getMissionStatusLabel('COMPLETED'), 'Terminée');
});

test('cancelled missions have no next status', () => {
  assert.equal(getNextMissionStatus('CANCELLED'), null);
  assert.equal(getMissionStatusLabel('CANCELLED'), 'Annulée');
});

test('an unknown status is displayed as is rather than hidden', () => {
  assert.equal(getMissionStatusLabel('ARCHIVED'), 'ARCHIVED');
});
