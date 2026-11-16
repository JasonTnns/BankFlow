'use strict';

const { Errors } = require('../errors');
const { nowIso } = require('../utils/ids');

function toDto(row) {
  return {
    id: row.id,
    email: row.email,
    fullName: row.full_name,
    themePreference: row.theme_preference,
    dailyTransferLimitMinor: row.daily_transfer_limit_minor,
    createdAt: row.created_at,
  };
}

function createProfileService(db) {
  const read = db.prepare('SELECT * FROM users WHERE id = ?');

  function getProfile(userId) {
    const row = read.get(userId);
    if (!row) throw Errors.notFound('User not found');
    return toDto(row);
  }

  function updateProfile(userId, changes) {
    const sets = [];
    const params = [];
    if (changes.fullName !== undefined) {
      sets.push('full_name = ?');
      params.push(changes.fullName);
    }
    if (changes.themePreference !== undefined) {
      sets.push('theme_preference = ?');
      params.push(changes.themePreference);
    }
    sets.push('updated_at = ?');
    params.push(nowIso());

    const result = db.prepare(`UPDATE users SET ${sets.join(', ')} WHERE id = ?`).run(...params, userId);
    if (result.changes === 0) throw Errors.notFound('User not found');

    return getProfile(userId);
  }

  return { getProfile, updateProfile };
}

module.exports = { createProfileService };