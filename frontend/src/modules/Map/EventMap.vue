<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  circleMarker,
  map,
  tileLayer,
  type CircleMarker,
  type Map as LeafletMap,
  type TileLayer,
} from 'leaflet'
import 'leaflet/dist/leaflet.css'
import AppButton from '@/common/ui/AppButton.vue'
import type { EventMapPoint } from './types'

const props = defineProps<{ points: readonly EventMapPoint[] }>()
const emit = defineEmits<{ select: [id: string] }>()
const points = computed(() =>
  props.points.filter(
    (point) =>
      typeof point.latitude === 'number' &&
      Number.isFinite(point.latitude) &&
      Math.abs(point.latitude) <= 90 &&
      typeof point.longitude === 'number' &&
      Number.isFinite(point.longitude) &&
      Math.abs(point.longitude) <= 180,
  ),
)
const container = ref<HTMLElement | null>(null)
const status = ref<'loading' | 'ready' | 'error'>('loading')
let instance: LeafletMap | undefined
let tiles: TileLayer | undefined
let markers: CircleMarker[] = []
let observer: ResizeObserver | undefined
let frame: number | undefined
let failed = false
function loading() {
  failed = false
  status.value = 'loading'
}
function loaded() {
  status.value = failed ? 'error' : 'ready'
}
function failure() {
  failed = true
  status.value = 'error'
}
function retry() {
  loading()
  tiles?.redraw()
}
function online() {
  if (status.value === 'error') retry()
}
function resize() {
  if (frame !== undefined) return
  frame = requestAnimationFrame(() => {
    frame = undefined
    instance?.invalidateSize({ pan: false })
  })
}
function clear() {
  markers.forEach((marker) => {
    marker.off('click')
    marker.remove()
  })
  markers = []
}
function render() {
  if (!instance) return
  clear()
  points.value.forEach((point) => {
    const marker = circleMarker([point.latitude!, point.longitude!], {
      radius: 10,
      color: '#292657',
      fillColor: '#f58cba',
      fillOpacity: 1,
      weight: 3,
    })
    marker.on('click', () => emit('select', point.id)).addTo(instance!)
    markers.push(marker)
  })
  if (points.value.length)
    instance.fitBounds(
      points.value.map((point) => [point.latitude!, point.longitude!] as [number, number]),
      { padding: [24, 24], maxZoom: 14 },
    )
}
watch(points, render)
onMounted(() => {
  if (!container.value) return
  instance = map(container.value, { scrollWheelZoom: false }).setView([59.9343, 30.3351], 11)
  instance.attributionControl.setPrefix(false)
  tiles = tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution:
      '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  })
  tiles.on('loading', loading).on('load', loaded).on('tileerror', failure).addTo(instance)
  render()
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(resize)
    observer.observe(container.value)
  }
  window.addEventListener('resize', resize)
  window.addEventListener('online', online)
  resize()
})
onBeforeUnmount(() => {
  observer?.disconnect()
  window.removeEventListener('resize', resize)
  window.removeEventListener('online', online)
  if (frame !== undefined) cancelAnimationFrame(frame)
  tiles?.off('loading', loading).off('load', loaded).off('tileerror', failure)
  clear()
  instance?.remove()
})
</script>
<template>
  <section class="event-map" aria-label="События на карте">
    <div
      ref="container"
      class="map"
      aria-label="Карта отфильтрованных событий"
      :aria-busy="status === 'loading'"
    />
    <p v-if="status === 'loading'" role="status">Загружаем карту…</p>
    <div v-if="status === 'error'" class="map-fallback" role="status">
      <p>Не удалось загрузить карту. Список событий доступен ниже.</p>
      <AppButton size="sm" variant="secondary" @click="retry">Повторить</AppButton>
    </div>
    <p v-if="!points.length">У найденных событий нет координат для отображения на карте.</p>
    <ul v-else class="map-list">
      <li v-for="point in points" :key="point.id">
        <button type="button" @click="emit('select', point.id)">{{ point.title }}</button>
      </li>
    </ul>
  </section>
</template>
<style scoped>
.map {
  height: 360px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  z-index: 0;
}
p {
  margin-top: 10px;
  color: var(--color-text-subtle);
  font-size: 13px;
}
.map-fallback {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}
.map-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0;
  list-style: none;
  margin-top: 14px;
}
.map-list button {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 8px 12px;
  background: var(--color-surface);
  color: var(--color-text);
  font-size: 12px;
}
.map-list button:hover {
  background: var(--color-blue-soft);
}
@media (max-width: 700px) {
  .map {
    height: 280px;
  }
}
</style>
