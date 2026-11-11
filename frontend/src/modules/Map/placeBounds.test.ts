import { expect, it } from 'vitest'
import { isInPlaceBounds, placeBounds, placeBoundsParts } from './placeBounds'
import { poiTileCoordinates } from './vectorPlaces'

it.each([
  [48.85, 2.3, 48.87, 2.33],
  [55.75, 37.6, 55.77, 37.64],
  [40.70, -74.02, 40.72, -74.00],
  [-33.87, 151.2, -33.85, 151.22],
])('поиск работает в видимой области любого города: %j', (south, west, north, east) => {
  const bounds: [number, number, number, number] = [south, west, north, east]
  expect(placeBounds(bounds)).toEqual(bounds)
  expect(poiTileCoordinates(bounds).length).toBeGreaterThan(0)
  expect(isInPlaceBounds((south + north) / 2, (west + east) / 2, bounds)).toBe(true)
})

it('корректно обрабатывает область через линию смены дат и повторные копии мира', () => {
  const bounds: [number, number, number, number] = [0.001, 179.99, 0.009, -179.99]
  expect(placeBoundsParts(bounds)).toHaveLength(2)
  expect(isInPlaceBounds(0.005, -179.995, bounds)).toBe(true)
  expect(isInPlaceBounds(0.005, 179.995, bounds)).toBe(true)
  expect(isInPlaceBounds(0.005, 0, bounds)).toBe(false)
  expect(new Set(poiTileCoordinates(bounds).map(tile => tile.x))).toEqual(new Set([0, 16383]))
  expect(placeBounds([10, 540.01, 10.01, 540.02])[1]).toBeCloseTo(-179.99)
})

it('ограничивает широты проекцией карты и не допускает некорректные области', () => {
  expect(placeBounds([85.04, 0, 90, 0.01])[2]).toBeLessThan(86)
  expect(poiTileCoordinates([85.04, 0, 90, 0.01]).every(tile => tile.y >= 0)).toBe(true)
  expect(() => placeBounds([NaN, 0, 1, 1])).toThrow('Некорректная область')
  expect(() => placeBounds([20, 0, 10, 1])).toThrow('Некорректная область')
})
