<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { circleMarker, map, tileLayer, type CircleMarker, type Map, type TileLayer } from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { Coordinates } from './types'
import { wrapLongitude } from './coordinates'

const props = defineProps<{ modelValue: Coordinates | null; address: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: Coordinates | null] }>()

const container = ref<HTMLElement | null>(null)
const tileStatus = ref<'loading' | 'ready' | 'error'>('loading')
let instance: Map | undefined
let marker: CircleMarker | undefined
let tiles: TileLayer | undefined
let failedInCycle = false
let resizeObserver: ResizeObserver | undefined
let resizeFrame: number | undefined

function startLoading() {
  failedInCycle = false
  tileStatus.value = 'loading'
}

function finishLoading() {
  // Leaflet emits `load` even when some tiles failed. Do not hide partial failures.
  tileStatus.value = failedInCycle ? 'error' : 'ready'
}

function failLoading() {
  failedInCycle = true
  tileStatus.value = 'error'
}

function retryTiles() {
  if (!tiles) return
  startLoading()
  tiles.redraw()
}

function recoverOnline() {
  if (tileStatus.value === 'error') retryTiles()
}

function resizeMap() {
  if (resizeFrame !== undefined) return
  resizeFrame = window.requestAnimationFrame(() => {
    resizeFrame = undefined
    instance?.invalidateSize({ pan: false, debounceMoveend: true })
  })
}

function updateMarker(value: Coordinates | null) {
  if (!instance) return
  if (!value) {
    marker?.remove()
    marker = undefined
    return
  }
  const position: [number, number] = [value.latitude, value.longitude]
  if (marker) marker.setLatLng(position)
  else
    marker = circleMarker(position, {
      radius: 10,
      color: '#292657',
      weight: 3,
      fillColor: '#f58cba',
      fillOpacity: 1,
    }).addTo(instance)
}

onMounted(() => {
  if (!container.value) return
  const initial = props.modelValue
  instance = map(container.value, { scrollWheelZoom: false }).setView(
    initial ? [initial.latitude, initial.longitude] : [59.9343, 30.3351],
    11,
  )
  tiles = tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution:
      '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  })
  tiles.on('loading', startLoading)
  tiles.on('load', finishLoading)
  tiles.on('tileerror', failLoading)
  tiles.addTo(instance)
  instance.on('click', ({ latlng }) =>
    emit('update:modelValue', { latitude: latlng.lat, longitude: wrapLongitude(latlng.lng) }),
  )
  updateMarker(props.modelValue)
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(resizeMap)
    resizeObserver.observe(container.value)
  }
  window.addEventListener('resize', resizeMap)
  window.addEventListener('orientationchange', resizeMap)
  window.addEventListener('online', recoverOnline)
  resizeMap()
})

watch(() => props.modelValue, updateMarker, { deep: true })
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  window.removeEventListener('resize', resizeMap)
  window.removeEventListener('orientationchange', resizeMap)
  window.removeEventListener('online', recoverOnline)
  if (resizeFrame !== undefined) window.cancelAnimationFrame(resizeFrame)
  tiles?.off('loading', startLoading)
  tiles?.off('load', finishLoading)
  tiles?.off('tileerror', failLoading)
  instance?.off('click')
  instance?.remove()
  marker = undefined
  tiles = undefined
  instance = undefined
})
</script>

<template>
  <section class="location-picker" aria-labelledby="map-label">
    <div class="location-picker__heading">
      <span id="map-label">Точка на карте (необязательно)</span>
      <button
        v-if="modelValue"
        type="button"
        class="location-picker__clear"
        @click="emit('update:modelValue', null)"
      >
        Убрать точку
      </button>
    </div>
    <p class="location-picker__hint">Нажмите на карту, чтобы указать место встречи.</p>
    <div
      ref="container"
      class="location-picker__map"
      aria-label="Карта выбора места встречи"
      :aria-busy="tileStatus === 'loading'"
    />
    <p v-if="tileStatus === 'loading'" class="location-picker__hint" role="status">
      Загружаем карту…
    </p>
    <p v-else-if="tileStatus === 'error'" class="location-picker__hint" role="status">
      Не удалось загрузить карту. Можно продолжить без точки.
      <button type="button" class="location-picker__retry" @click="retryTiles">
        Повторить загрузку
      </button>
    </p>
    <div class="location-picker__summary" role="status">
      <span class="location-picker__icon" aria-hidden="true">⌖</span>
      <div>
        <strong>{{ address.trim() || 'Укажите адрес встречи' }}</strong>
        <p v-if="modelValue">
          Точка выбрана: {{ modelValue.latitude.toFixed(5) }}, {{ modelValue.longitude.toFixed(5) }}
        </p>
        <p v-else>Отметьте место на карте</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.location-picker__heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  font-weight: 800;
}
.location-picker__hint {
  margin: 7px 0;
  font-size: 12px;
  color: #47456d;
}
.location-picker__clear {
  border: 0;
  background: transparent;
  color: #a63872;
  font-size: 12px;
  font-weight: 800;
}
.location-picker__retry {
  margin-left: 8px;
  padding: 6px 0;
  border: 0;
  background: transparent;
  color: #a63872;
  font: inherit;
  font-weight: 800;
  text-decoration: underline;
  cursor: pointer;
}
.location-picker__map {
  height: 280px;
  width: 100%;
  border: 1px solid #dcc8ee;
  border-radius: 12px;
  overflow: hidden;
  z-index: 0;
}
.location-picker__summary {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  margin-top: 10px;
  border: 1px solid #f58cba;
  border-radius: 12px;
  background: #fff;
  font-size: 14px;
}
.location-picker__summary p {
  margin-top: 3px;
  color: #77718d;
  font-size: 12px;
}
.location-picker__icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  flex: none;
  border-radius: 12px;
  background: #fbd5e6;
  font-size: 22px;
}
</style>
