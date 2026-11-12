<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
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
import { usePopover } from '@/common/composables/usePopover'
import { poiTileCoordinates } from './vectorPlaces'
import type { MeetingPlace } from './types'
import type { PlaceBounds } from './types'

const props = withDefaults(
  defineProps<{
    modelValue: MeetingPlace | null
    places: MeetingPlace[]
    showEmpty?: boolean
    searchable?: boolean
    searchedBounds?: PlaceBounds
    searching?: boolean
    searchEnabled?: boolean
  }>(),
  {
    showEmpty: true,
    searchable: false,
    searchedBounds: undefined,
    searching: false,
    searchEnabled: true,
  },
)
const emit = defineEmits<{
  'update:modelValue': [value: MeetingPlace | null]
  searchArea: [bounds: PlaceBounds]
  viewportChange: [bounds: PlaceBounds]
}>()
const labelId = useId()
const container = ref<HTMLElement | null>(null)
const selected = computed(
  () =>
    props.places.find((place) => place.id === props.modelValue?.id) ??
    (props.searchable ? props.modelValue : undefined),
)
const visibleBounds = ref<PlaceBounds>()
const listOpen = ref(false)
const listQuery = ref('')
const listId = useId()
const listRoot = ref<HTMLElement | null>(null)
const listPanel = ref<HTMLElement | null>(null)
const listTrigger = ref<HTMLButtonElement | null>(null)
const activeIndex = ref(0)
const panelAbove = ref(false)
const { offset: listOffset } = usePopover(listOpen, listRoot, listPanel)
const filteredPlaces = computed(() => {
  const query = listQuery.value.trim().toLocaleLowerCase('ru-RU')
  return props.places.filter((place) =>
    `${place.name} ${place.address}`.toLocaleLowerCase('ru-RU').includes(query),
  )
})
watch(listQuery, () => {
  activeIndex.value = 0
})
watch(activeIndex, async () => {
  await nextTick()
  listPanel.value?.querySelector('.is-active')?.scrollIntoView?.({ block: 'nearest' })
})
async function toggleList() {
  listOpen.value = !listOpen.value
  if (!listOpen.value) return
  listQuery.value = ''
  activeIndex.value = Math.max(
    0,
    props.places.findIndex((place) => place.id === selected.value?.id),
  )
  await nextTick()
  const rect = listTrigger.value?.getBoundingClientRect()
  if (rect)
    panelAbove.value =
      window.innerHeight - rect.bottom < 280 && rect.top > window.innerHeight - rect.bottom
  listPanel.value?.querySelector('input')?.focus()
}
function closeList() {
  listOpen.value = false
  listTrigger.value?.focus()
}
function listKey(event: KeyboardEvent) {
  if (!listOpen.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    closeList()
  } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    const count = filteredPlaces.value.length
    if (count)
      activeIndex.value = (activeIndex.value + (event.key === 'ArrowDown' ? 1 : -1) + count) % count
  } else if (event.key === 'Enter') {
    event.preventDefault()
    const place = filteredPlaces.value[activeIndex.value]
    if (place) selectPlace(place.id)
  }
}
function leaveList(event: FocusEvent) {
  if (event.relatedTarget && !listRoot.value?.contains(event.relatedTarget as Node))
    listOpen.value = false
}
const areaChanged = computed(
  () =>
    visibleBounds.value &&
    (!props.searchedBounds ||
      visibleBounds.value.some(
        (value, index) => Math.abs(value - props.searchedBounds![index]!) > 0.00001,
      )),
)
const areaError = computed(() => {
  if (!visibleBounds.value) return ''
  try {
    poiTileCoordinates(visibleBounds.value)
    return ''
  } catch (error) {
    return error instanceof Error ? error.message : 'Приблизьте карту'
  }
})
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
  if (place) {
    emit('update:modelValue', place)
    listOpen.value = false
    listQuery.value = ''
    listTrigger.value?.focus()
  }
}
function reportViewport() {
  const bounds = instance?.getBounds()
  if (!bounds) return
  visibleBounds.value = [bounds.getSouth(), bounds.getWest(), bounds.getNorth(), bounds.getEast()]
  emit('viewportChange', visibleBounds.value)
}
function searchArea() {
  if (visibleBounds.value && !areaError.value) emit('searchArea', [...visibleBounds.value])
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
  if (props.places.length && !props.searchable) {
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
watch(
  () => props.places,
  () => {
    listQuery.value = ''
    listOpen.value = false
  },
)

onMounted(() => {
  if (!container.value) return
  instance = map(container.value, { scrollWheelZoom: true }).setView(
    [59.9343, 30.3351],
    props.searchable ? 13 : 11,
  )
  instance.attributionControl.setPrefix(false)
  if (props.searchable) instance.on('moveend', reportViewport)
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
  if (props.searchable) reportViewport()
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
  instance?.off('moveend', reportViewport)
  instance?.remove()
  tiles = undefined
  instance = undefined
})
</script>

<template>
  <section class="place-picker" :aria-labelledby="labelId">
    <h3 :id="labelId">Место встречи</h3>
    <p class="place-picker__hint">Нажмите на точку карты, чтобы выбрать место.</p>
    <div
      ref="container"
      class="place-picker__map"
      aria-label="Карта предложенных мест встречи"
      :aria-busy="tileStatus === 'loading'"
    />
    <template v-if="searchable">
      <p v-if="areaError" class="place-picker__hint" role="status">{{ areaError }}</p>
      <AppButton
        v-if="(areaChanged || searching) && searchEnabled"
        size="sm"
        variant="secondary"
        :disabled="Boolean(areaError) || searching"
        :aria-busy="searching"
        @click="searchArea"
        ><span v-if="searching" class="place-picker__spinner" aria-hidden="true" />{{
          searching ? 'Ищем места…' : 'Искать в этой области'
        }}</AppButton
      >
      <p v-if="searching" class="place-picker__hint" role="status">
        Обновляем места в выбранной области…
      </p>
      <p v-else-if="!areaChanged && places.length" class="place-picker__hint" role="status">
        Найдено мест: {{ places.length }}{{ places.length === 300 ? ' (показаны первые 300)' : '' }}
      </p>
    </template>
    <p v-if="tileStatus === 'loading'" class="place-picker__hint" role="status">Загружаем карту…</p>
    <div v-else-if="tileStatus === 'error'" class="place-picker__error" role="status">
      <p class="place-picker__hint">Не удалось загрузить карту. Выберите место в списке ниже.</p>
      <AppButton class="place-picker__retry" size="sm" variant="secondary" @click="retryTiles">
        Повторить загрузку
      </AppButton>
    </div>
    <div ref="listRoot" class="place-picker__choices" @keydown="listKey" @focusout="leaveList">
      <div
        class="place-picker__summary"
        :class="{ 'place-picker__summary--retained': selected && !places.length }"
        role="status"
      >
        <button
          v-if="places.length"
          ref="listTrigger"
          type="button"
          class="place-picker__list-toggle"
          :class="{ 'is-placeholder': !selected, 'is-open': listOpen }"
          aria-haspopup="dialog"
          :aria-expanded="listOpen"
          :aria-controls="listId"
          @click="toggleList"
        >
          <svg
            class="place-picker__pin"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            aria-hidden="true"
          >
            <path d="M20 10c0 6-8 11-8 11S4 16 4 10a8 8 0 1 1 16 0Z" />
            <circle cx="12" cy="10" r="2.5" />
          </svg>
          <span class="place-picker__value">{{
            selected?.name ?? 'Выберите место из списка'
          }}</span>
          <span v-if="!selected" class="place-picker__count">{{ places.length }}</span>
          <svg
            class="place-picker__chevron"
            :class="{ flipped: listOpen }"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            aria-hidden="true"
          >
            <path d="m6 9 6 6 6-6" />
          </svg>
        </button>
        <strong v-else-if="selected">{{ selected.name }}</strong>
        <p v-else>Место не выбрано</p>
        <p v-if="selected && selected.address !== selected.name" class="place-picker__address">
          {{ selected.address }}
        </p>
        <p
          v-if="selected && searchable && !places.some((place) => place.id === selected?.id)"
          class="place-picker__hint"
        >
          Выбранное место сохранено. Новый поиск не меняет ваш выбор.
        </p>
      </div>
      <div
        v-if="listOpen && places.length"
        :id="listId"
        ref="listPanel"
        class="place-picker__panel"
        :class="{ 'is-above': panelAbove }"
        role="dialog"
        aria-label="Выбор места встречи"
        :style="listOffset ? { left: `${listOffset}px` } : undefined"
      >
        <label class="place-picker__search">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            aria-hidden="true"
          >
            <circle cx="10.5" cy="10.5" r="6.5" />
            <path d="m16 16 5 5" />
          </svg>
          <input
            v-model="listQuery"
            type="search"
            role="combobox"
            aria-autocomplete="list"
            aria-label="Поиск среди найденных мест"
            placeholder="Найти место по названию"
            :aria-controls="`${listId}-options`"
            aria-expanded="true"
            :aria-activedescendant="
              filteredPlaces.length ? `${listId}-option-${activeIndex}` : undefined
            "
            autocomplete="off"
          />
        </label>
        <p class="place-picker__list-caption">
          Места на карте <span>{{ filteredPlaces.length }}</span>
        </p>
        <ul
          :id="`${listId}-options`"
          class="place-picker__list"
          role="listbox"
          aria-label="Предложенные места встречи"
        >
          <li v-for="(place, index) in filteredPlaces" :key="place.id" role="presentation">
            <button
              type="button"
              class="place-picker__option"
              :class="{ 'is-active': index === activeIndex }"
              :id="`${listId}-option-${index}`"
              role="option"
              tabindex="-1"
              :aria-selected="selected?.id === place.id"
              :aria-pressed="selected?.id === place.id"
              @click="selectPlace(place.id)"
              @mousemove="activeIndex = index"
            >
              <strong>{{ place.name }}</strong>
              <span v-if="selected?.id === place.id" aria-label="Выбрано">✓</span>
            </button>
          </li>
        </ul>
        <p v-if="!filteredPlaces.length" class="place-picker__hint">
          Ничего не найдено по этому названию.
        </p>
      </div>
    </div>
    <p v-if="!places.length && showEmpty" class="place-picker__hint">
      В этой области не найдено мест этой категории. Переместите карту или выберите другую
      категорию.
    </p>
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
.place-picker__summary {
  color: var(--color-text);
  font-size: 13px;
}
.place-picker__summary--retained {
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface);
}
.place-picker__summary p {
  margin: 6px 0 0;
}
.place-picker__address {
  color: var(--color-text-subtle);
  font-size: 12px;
  line-height: 1.5;
  padding: 0 4px;
}
.place-picker__list {
  max-height: min(220px, 32dvh);
  overflow-y: auto;
  overscroll-behavior: contain;
  margin: 0;
  padding: 0;
  list-style: none;
}
.place-picker__choices {
  position: relative;
  min-width: 0;
  margin-top: 10px;
}
.place-picker__list-toggle {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 46px;
  border: 1px solid var(--color-input-border);
  border-radius: 12px;
  padding: 11px 14px;
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}
.place-picker__list-toggle.is-placeholder {
  font-weight: 400;
  color: var(--color-text-subtle);
}
.place-picker__list-toggle.is-open,
.place-picker__list-toggle:hover {
  border-color: #b79cd6;
}
.place-picker__pin,
.place-picker__chevron {
  width: 18px;
  height: 18px;
  flex: none;
  color: var(--color-text-subtle);
}
.place-picker__chevron {
  margin-left: auto;
  transition: transform 0.15s;
}
.place-picker__chevron.flipped {
  transform: rotate(180deg);
}
.place-picker__value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
}
.place-picker__count {
  flex: none;
  border-radius: 7px;
  background: var(--color-blue-soft);
  padding: 2px 6px;
  font-size: 11px;
  color: var(--color-text-subtle);
}
.place-picker__panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 45;
  width: 100%;
  max-width: calc(100vw - 32px);
  padding: 8px;
  border: 1px solid #edcfe2;
  border-radius: 14px;
  background: var(--color-surface);
  box-shadow: 0 18px 44px #29265729;
}
.place-picker__panel.is-above {
  top: auto;
  bottom: calc(100% + 6px);
}
.place-picker__search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  height: 40px;
  border: 1px solid var(--color-input-border);
  border-radius: 9px;
  background: var(--color-bg);
}
.place-picker__search svg {
  width: 17px;
  height: 17px;
  flex: none;
  color: var(--color-text-subtle);
}
.place-picker__search input {
  width: 100%;
  min-width: 0;
  height: 100%;
  padding: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-text);
  font: inherit;
  font-size: 13px;
}
.place-picker__search:focus-within {
  border-color: #b79cd6;
  box-shadow: 0 0 0 2px #edcfe255;
}
.place-picker__list-caption {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 10px 8px 5px;
  color: var(--color-text-subtle);
  font-size: 11px;
  font-weight: 700;
}
.place-picker__option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  width: 100%;
  border: 0;
  border-radius: 8px;
  padding: 10px;
  background: transparent;
  color: var(--color-text);
  font-size: 13px;
  text-align: left;
  font-family: inherit;
  cursor: pointer;
}
.place-picker__option:hover,
.place-picker__option.is-active {
  background: var(--color-blue-soft);
}
.place-picker__option strong {
  font-weight: 600;
}
.place-picker__option[aria-pressed='true'] {
  border-color: #a63872;
  background: #fbd5e6;
}
.place-picker__option:focus-visible,
.place-picker__list-toggle:focus-visible {
  outline: 3px solid #292657;
  outline-offset: 2px;
}
.place-picker__error {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}
.place-picker__spinner {
  width: 13px;
  height: 13px;
  margin-right: 7px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: place-spin 0.7s linear infinite;
}
@keyframes place-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .place-picker__spinner {
    animation: none;
  }
}
</style>
