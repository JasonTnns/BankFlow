'use strict';

const {
  MAX_TRANSFER_MINOR,
  isValidPositiveMinor,
  parseDecimalToMinor,
  formatMinor,
} = require('../src/utils/money');

describe('isValidPositiveMinor', () => {
  test.each([1, 100, 250000, MAX_TRANSFER_MINOR])('accepts %p', (value) => {
    expect(isValidPositiveMinor(value)).toBe(true);
  });

  test.each([0, -1, 12.5, NaN, Infinity, '100', null, undefined, MAX_TRANSFER_MINOR + 1])(
    'rejects %p',
    (value) => {
      expect(isValidPositiveMinor(value)).toBe(false);
    }
  );
});

describe('parseDecimalToMinor', () => {
  test.each([
    ['12.34', 1234],
    ['7', 700],
    ['0.5', 50],
    ['0.05', 5],
    ['1000', 100000],
    [' 3.10 ', 310],
  ])('parses %p as %p', (text, expected) => {
    expect(parseDecimalToMinor(text)).toBe(expected);
  });

  test.each(['12.345', '-5', 'abc', '', '1,000', '.5', '5.', 12, null])('rejects %p', (text) => {
    expect(parseDecimalToMinor(text)).toBeNull();
  });

  test('is exact where floating point is not (0.1 + 0.2 style cases)', () => {
    expect(parseDecimalToMinor('0.10') + parseDecimalToMinor('0.20')).toBe(30);
    expect(parseDecimalToMinor('19.99')).toBe(1999);
  });
});

describe('formatMinor', () => {
  test('formats cents as currency', () => {
    expect(formatMinor(123456)).toBe('$1,234.56');
    expect(formatMinor(5)).toBe('$0.05');
    expect(formatMinor(0)).toBe('$0.00');
  });
});