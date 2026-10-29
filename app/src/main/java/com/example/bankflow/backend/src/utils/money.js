'use strict';


const MAX_TRANSFER_MINOR = 100_000_000;


function isValidPositiveMinor(value) {
  return (
    typeof value === 'number' &&
    Number.isSafeInteger(value) &&
    value > 0 &&
    value <= MAX_TRANSFER_MINOR
  );
}


function parseDecimalToMinor(text) {
  if (typeof text !== 'string') return null;
  const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(text.trim());
  if (!match) return null;
  const whole = Number.parseInt(match[1], 10);
  const cents = Number.parseInt((match[2] ?? '').padEnd(2, '0') || '0', 10);
  const minor = whole * 100 + cents;
  return Number.isSafeInteger(minor) ? minor : null;
}


function formatMinor(minor, currency = 'USD') {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(minor / 100);
}

module.exports = {
  MAX_TRANSFER_MINOR,
  isValidPositiveMinor,
  parseDecimalToMinor,
  formatMinor,
};
