<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { circleMarker, map, tileLayer, type CircleMarker, type Map } from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { Coordinates } from './types'

const props = defineProps<{ modelValue: Coordinates | null; address: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: Coordinates | null] }>()

const container = ref<HTMLElement | null>(null)
const tileError = ref(false)
let instance: Map | undefined
let marker: CircleMarker | undefined

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
  instance = map(container.value, { scrollWheelZoom: false }).setView([59.9343, 30.3351], 11)
  const tiles = tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution:
      '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  })
  tiles.on('tileerror', () => {
    tileError.value = true
  })
  tiles.addTo(instance)
  instance.on('click', ({ latlng }) =>
    emit('update:modelValue', { latitude: latlng.lat, longitude: latlng.lng }),
  )
  updateMarker(props.modelValue)
})

watch(() => props.modelValue, updateMarker, { deep: true })
onBeforeUnmount(() => {
  instance?.remove()
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
    <div ref="container" class="location-picker__map" aria-label="Карта выбора места встречи" />
    <p v-if="tileError" class="location-picker__hint" role="status">
      Не удалось загрузить карту. Можно продолжить без точки.
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
    <p class="location-picker__hint">
      Пока адрес и точка выбираются отдельно. Автоматический поиск будет подключён позже.
    </p>
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
