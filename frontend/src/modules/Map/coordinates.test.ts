import { describe, expect, it } from 'vitest'
import { toCoordinateFields, wrapLongitude } from './coordinates'

describe('coordinate request fields', () => {
  it('omits both fields for an absent point', () => {
    expect(toCoordinateFields(null)).toEqual({})
  })
  it('includes both numeric fields, preserving zero', () => {
    expect(toCoordinateFields({ latitude: 0, longitude: 0 })).toEqual({ latitude: 0, longitude: 0 })
  })
  it.each([
    { latitude: NaN, longitude: 30 },
    { latitude: 91, longitude: 30 },
    { latitude: 59, longitude: Infinity },
    { latitude: 59, longitude: 181 },
  ])('rejects a point outside the API range: %o', (point) => {
    expect(() => toCoordinateFields(point)).toThrow(RangeError)
  })
  it('wraps clicks in repeated map worlds into the longitude API range', () => {
    expect(wrapLongitude(390)).toBe(30)
    expect(wrapLongitude(-330)).toBe(30)
    expect(wrapLongitude(30)).toBe(30)
  })
})
