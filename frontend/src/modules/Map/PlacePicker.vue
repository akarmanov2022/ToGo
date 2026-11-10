<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import {
  circleMarker,
  map,
  tileLayer,
  type CircleMarker,
  type Map as LeafletMap,
  type TileLayer,
} from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { MeetingPlace } from './types'

const props = defineProps<{ modelValue: MeetingPlace | null; places: MeetingPlace[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: MeetingPlace | null] }>()
const labelId = useId()
const container = ref<HTMLElement | null>(null)
const selected = computed(() => props.places.find((place) => place.id === props.modelValue?.id))
const tileStatus = ref<'loading' | 'ready' | 'error'>('loading')
let instance: LeafletMap | undefined
let tiles: TileLayer | undefined
const markers = new Map<string, CircleMarker>()
let failedInCycle = false
let resizeObserver: ResizeObserver | undefined
let resizeFrame: number | undefined

function startLoading() {
  failedInCycle = false
  tileStatus.value = 'loading'
}

function finishLoading() {
  // Leaflet emits load even after partial tile failures.
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

function selectPlace(id: string) {
  const place = props.places.find((place) => place.id === id)
  if (place) emit('update:modelValue', place)
}

function highlightSelection() {
  markers.forEach((marker, id) => {
    const active = id === selected.value?.id
    marker.setStyle({
      radius: active ? 12 : 8,
      color: '#292657',
      weight: active ? 4 : 2,
      fillColor: active ? '#f58cba' : '#fff',
      fillOpacity: 1,
    })
    if (active) marker.bringToFront()
  })
}

function removeMarkers() {
  markers.forEach((marker) => {
    marker.off('click')
    marker.remove()
  })
  markers.clear()
}

function renderPlaces() {
  const target = instance
  if (!target) return
  removeMarkers()
  props.places.forEach((place) => {
    const marker = circleMarker([place.latitude, place.longitude], { bubblingMouseEvents: false })
    marker.on('click', () => selectPlace(place.id))
    marker.addTo(target)
    markers.set(place.id, marker)
  })
  highlightSelection()
  if (props.places.length) {
    target.fitBounds(
      props.places.map((place) => [place.latitude, place.longitude] as [number, number]),
      { padding: [24, 24], maxZoom: 14 },
    )
  }
}

watch(
  () => [props.places, props.modelValue] as const,
  () => {
    const value = props.modelValue
    const place = selected.value
    if (value && !place) emit('update:modelValue', null)
    else if (
      value &&
      place &&
      (value.name !== place.name ||
        value.address !== place.address ||
        value.latitude !== place.latitude ||
        value.longitude !== place.longitude ||
        value.category !== place.category)
    ) {
      emit('update:modelValue', place)
    }
    highlightSelection()
  },
  { deep: true, immediate: true },
)
watch(() => props.places, renderPlaces, { deep: true })

onMounted(() => {
  if (!container.value) return
  instance = map(container.value, { scrollWheelZoom: false }).setView([59.9343, 30.3351], 11)
  tiles = tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution:
      '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  })
  tiles.on('loading', startLoading)
  tiles.on('load', finishLoading)
  tiles.on('tileerror', failLoading)
  tiles.addTo(instance)
  renderPlaces()
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(resizeMap)
    resizeObserver.observe(container.value)
  }
  window.addEventListener('resize', resizeMap)
  window.addEventListener('orientationchange', resizeMap)
  window.addEventListener('online', recoverOnline)
  resizeMap()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  window.removeEventListener('resize', resizeMap)
  window.removeEventListener('orientationchange', resizeMap)
  window.removeEventListener('online', recoverOnline)
  if (resizeFrame !== undefined) window.cancelAnimationFrame(resizeFrame)
  tiles?.off('loading', startLoading)
  tiles?.off('load', finishLoading)
  tiles?.off('tileerror', failLoading)
  removeMarkers()
  instance?.remove()
  tiles = undefined
  instance = undefined
})
</script>

<template>
  <section class="place-picker" :aria-labelledby="labelId">
    <h3 :id="labelId">Место встречи</h3>
    <p class="place-picker__hint">Выберите предложенное место на карте или в списке.</p>
    <div
      ref="container"
      class="place-picker__map"
      aria-label="Карта предложенных мест встречи"
      :aria-busy="tileStatus === 'loading'"
    />
    <p v-if="tileStatus === 'loading'" class="place-picker__hint" role="status">Загружаем карту…</p>
    <p v-else-if="tileStatus === 'error'" class="place-picker__hint" role="status">
      Не удалось загрузить карту. Выберите место в списке ниже.
      <button type="button" class="place-picker__retry" @click="retryTiles">
        Повторить загрузку
      </button>
    </p>
    <div class="place-picker__summary" role="status">
      <template v-if="selected">
        <strong>{{ selected.name }}</strong>
        <p>{{ selected.address }}</p>
      </template>
      <p v-else>Место не выбрано</p>
    </div>
    <ul v-if="places.length" class="place-picker__list" aria-label="Предложенные места встречи">
      <li v-for="place in places" :key="place.id">
        <button
          type="button"
          class="place-picker__option"
          :aria-pressed="selected?.id === place.id"
          @click="selectPlace(place.id)"
        >
          <strong>{{ place.name }}</strong>
          <span>{{ place.address }}</span>
        </button>
      </li>
    </ul>
    <p v-else class="place-picker__hint">Нет доступных мест встречи.</p>
  </section>
</template>

<style scoped>
.place-picker h3 {
  margin: 0;
  font-size: 12px;
  font-weight: 800;
}
.place-picker__hint {
  margin: 7px 0;
  font-size: 12px;
  color: #47456d;
}
.place-picker__map {
  height: 280px;
  width: 100%;
  border: 1px solid #dcc8ee;
  border-radius: 12px;
  overflow: hidden;
  z-index: 0;
}
.place-picker__summary,
.place-picker__option {
  padding: 12px;
  border: 1px solid #dcc8ee;
  border-radius: 12px;
  background: #fff;
  color: #292657;
  font-size: 14px;
}
.place-picker__summary {
  margin-top: 10px;
  border-color: #f58cba;
}
.place-picker__summary p {
  margin: 3px 0 0;
}
.place-picker__list {
  display: grid;
  gap: 8px;
  padding: 0;
  list-style: none;
}
.place-picker__option {
  display: grid;
  gap: 3px;
  width: 100%;
  text-align: left;
  font-family: inherit;
  cursor: pointer;
}
.place-picker__option[aria-pressed='true'] {
  border-color: #a63872;
  background: #fbd5e6;
}
.place-picker__option:focus-visible,
.place-picker__retry:focus-visible {
  outline: 3px solid #292657;
  outline-offset: 2px;
}
.place-picker__retry {
  border: 0;
  background: transparent;
  color: #a63872;
  font: inherit;
  font-weight: 800;
  text-decoration: underline;
  cursor: pointer;
}
</style>
