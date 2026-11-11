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

const props = withDefaults(
  defineProps<{ points: readonly EventMapPoint[]; selectable?: boolean; label?: string }>(),
  { selectable: true, label: 'События на карте' },
)
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
let fittedGeometry = ''
const geometry = computed(() =>
  JSON.stringify(points.value.map((point) => [point.id, point.latitude, point.longitude]).sort()),
)
const content = computed(() => JSON.stringify(points.value))
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
function showAll() {
  if (!points.value.length) return
  instance?.fitBounds(
    points.value.map((point) => [point.latitude!, point.longitude!] as [number, number]),
    { padding: [24, 24], maxZoom: 14 },
  )
}
function render() {
  if (!instance) return
  clear()
  const groups = new Map<string, EventMapPoint[]>()
  points.value.forEach((point) => {
    const key = `${point.latitude},${point.longitude}`
    groups.set(key, [...(groups.get(key) ?? []), point])
  })
  groups.forEach((group) => {
    const point = group[0]!
    const marker = circleMarker([point.latitude!, point.longitude!], {
      radius: 10,
      color: '#292657',
      fillColor: '#f58cba',
      fillOpacity: 1,
      weight: 3,
    })
    const label = document.createElement('span')
    label.textContent = group.length > 1 ? String(group.length) : point.title
    marker.bindTooltip(label, {
      permanent: group.length > 1,
      direction: group.length > 1 ? 'center' : 'top',
      className: group.length > 1 ? 'event-map-count' : '',
    })
    if (props.selectable && group.length > 1) {
      const list = document.createElement('div')
      list.className = 'event-map-popup__list'
      const content = document.createElement('div')
      const heading = document.createElement('p')
      heading.className = 'event-map-popup__heading'
      heading.textContent = `События в этом месте · ${group.length}`
      content.append(heading, list)
      group.forEach((event) => {
        const button = document.createElement('button')
        button.type = 'button'
        button.textContent = event.title
        button.className = 'event-map-popup__event'
        button.addEventListener('click', () => emit('select', event.id))
        list.append(button)
      })
      marker.bindPopup(content, { className: 'event-map-popup', minWidth: 220, maxWidth: 300 })
    } else if (props.selectable) marker.on('click', () => emit('select', point.id))
    marker.addTo(instance!)
    markers.push(marker)
  })
  if (geometry.value !== fittedGeometry) {
    showAll()
    fittedGeometry = geometry.value
  }
}
watch(content, render)
onMounted(() => {
  if (!container.value) return
  instance = map(container.value, { scrollWheelZoom: true }).setView([59.9343, 30.3351], 11)
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
  <section class="event-map" :aria-label="label">
    <div ref="container" class="map" :aria-label="label" :aria-busy="status === 'loading'" />
    <p v-if="status === 'loading'" role="status">Загружаем карту…</p>
    <div v-if="status === 'error'" class="map-fallback" role="status">
      <p>Не удалось загрузить карту. Список событий доступен ниже.</p>
      <AppButton size="sm" variant="secondary" @click="retry">Повторить</AppButton>
    </div>
    <p v-if="!points.length">У найденных событий нет координат для отображения на карте.</p>
    <p v-else-if="props.points.length > points.length">
      На карте {{ points.length }} из {{ props.points.length }} событий. У остальных не указаны
      корректные координаты.
    </p>
    <AppButton
      v-if="points.length"
      size="sm"
      variant="secondary"
      class="show-all"
      @click="showAll"
      >{{ selectable ? 'Показать все события' : 'Показать место' }}</AppButton
    >
    <ul v-if="selectable && points.length" class="map-list">
      <li v-for="point in points" :key="point.id">
        <button type="button" @click="emit('select', point.id)">{{ point.title }}</button>
      </li>
    </ul>
  </section>
</template>
<style scoped>
:global(.event-map-popup .leaflet-popup-content-wrapper) {
  border: 1px solid var(--color-border, #dcc8ee);
  border-radius: 16px;
  background: var(--color-surface, #fff);
  box-shadow: 0 8px 28px #29265726;
}
:global(.event-map-popup .leaflet-popup-content) { margin: 16px; font-family: inherit; }
:global(.event-map-popup .event-map-popup__heading) { margin: 0 24px 12px 0; color: var(--color-text-subtle, #47456d); font-size: 11px; font-weight: 800; line-height: 1.5; }
:global(.event-map-popup__list) { display: grid; gap: 6px; max-height: 240px; overflow-y: auto; overscroll-behavior: contain; }
:global(.event-map-popup__event) { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; border: 0; border-radius: 10px; padding: 11px 12px; color: var(--color-text, #292657); background: var(--color-blue-soft, #f0f1ff); text-align: left; font-family: inherit; font-size: 13px; font-weight: 800; line-height: 1.4; cursor: pointer; }
:global(.event-map-popup__event::after) { content: '›'; color: var(--color-blue, #6861b1); font-size: 20px; font-weight: 500; }
:global(.event-map-popup__event:hover) { background: #fbd5e6; }
:global(.event-map-popup__event:focus-visible) { outline: 2px solid var(--color-blue, #6861b1); outline-offset: -2px; }
:global(.event-map-popup .leaflet-popup-close-button) { top: 8px; right: 8px; width: 28px; height: 28px; border-radius: 8px; color: var(--color-text-subtle, #47456d); font: 22px/28px sans-serif; }
:global(.event-map-popup .leaflet-popup-close-button:hover) { background: var(--color-blue-soft, #f0f1ff); color: var(--color-text, #292657); }
:global(.event-map-popup .leaflet-popup-tip) { background: var(--color-surface, #fff); }
:global(.event-map-count.leaflet-tooltip) { border: 0; border-radius: 50%; width: 22px; height: 22px; padding: 0; background: #292657; color: #fff; font: 800 12px/22px sans-serif; text-align: center; box-shadow: 0 2px 8px #29265733; }
.show-all {
  margin-top: 12px;
}
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
