<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ApiError } from '@/common/api/client'
import AppButton from '@/common/ui/AppButton.vue'
import AppChip from '@/common/ui/AppChip.vue'
import AppField from '@/common/ui/AppField.vue'
import ChipGroup from '@/common/ui/ChipGroup.vue'
import LoadingText from '@/common/ui/LoadingText.vue'
import {
  cancelEvent,
  fetchCategories,
  fetchFeedPage,
  joinEvent,
  leaveEvent,
  type EventCategory,
  type EventDto,
} from './api'
import { cardAction, isVisibleInFeed } from './actions'
import { stateLabel } from './rules'
import { metaLine, participantsLine } from './format'
import EventCard from './EventCard.vue'

const queryClient = useQueryClient()
const category = ref<EventCategory | null>(null)
const search = ref('')
const query = ref('')
const now = ref(new Date())
const startsFrom = ref(new Date())
const clock = setInterval(() => {
  now.value = new Date()
}, 1000)
let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(search, (value) => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    query.value = value
  }, 300)
})
watch(
  [category, query],
  () => {
    startsFrom.value = new Date()
  },
  { flush: 'sync' },
)
onUnmounted(() => {
  clearInterval(clock)
  clearTimeout(searchTimer)
})

const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: fetchCategories })
const categoryNames = computed(
  () => new Map((categoriesQuery.data.value ?? []).map((item) => [item.code, item.name])),
)
const feed = useInfiniteQuery({
  queryKey: ['events', 'feed', category, query],
  queryFn: ({ pageParam, signal }) =>
    fetchFeedPage(
      { category: category.value, query: query.value },
      pageParam,
      startsFrom.value,
      signal,
    ),
  initialPageParam: 0,
  getNextPageParam: (last) => {
    const next = last.offset + last.limit
    return next < last.total ? next : undefined
  },
})
const events = computed(() =>
  (feed.data.value?.pages.flatMap((page) => page.items) ?? []).filter(
    (event) => isVisibleInFeed(event) && Date.parse(event.startsAt) > now.value.getTime(),
  ),
)
const actionError = ref('')
const confirmingId = ref<string | null>(null)
function report(error: unknown, fallback: string) {
  actionError.value = error instanceof ApiError ? error.message : fallback
}
async function refreshEvents() {
  actionError.value = ''
  await queryClient.invalidateQueries({ queryKey: ['events'] })
  await queryClient.invalidateQueries({ queryKey: ['current-user'] })
  await queryClient.invalidateQueries({ queryKey: ['users'] })
}
const join = useMutation({
  mutationFn: joinEvent,
  onSuccess: refreshEvents,
  onError: (error) => report(error, 'Не удалось присоединиться. Проверьте подключение.'),
})
const leave = useMutation({
  mutationFn: leaveEvent,
  onSuccess: refreshEvents,
  onError: (error) => report(error, 'Не удалось отписаться. Проверьте подключение.'),
})
const cancel = useMutation({
  mutationFn: cancelEvent,
  onSuccess: async () => {
    confirmingId.value = null
    await refreshEvents()
  },
  onError: (error) => report(error, 'Не удалось отменить событие. Проверьте подключение.'),
})
const busy = computed(() => join.isPending.value || leave.isPending.value || cancel.isPending.value)
function run(event: EventDto) {
  const action = cardAction(event, now.value)
  if (action.kind === 'join') join.mutate(event.id)
  else if (action.kind === 'leave') leave.mutate(event.id)
  else if (action.kind === 'cancel') confirmingId.value = event.id
}
</script>
<template>
  <section>
    <div class="heading">
      <h1>Куда отправляемся?</h1>
      <div class="filters">
        <ChipGroup scrollable class="categories" aria-label="Категории">
          <AppChip :active="category === null" @click="category = null">Все</AppChip>
          <AppChip
            v-for="item in categoriesQuery.data.value ?? []"
            :key="item.code"
            :active="category === item.code"
            @click="category = item.code"
            >{{ item.name }}</AppChip
          >
        </ChipGroup>
        <p v-if="categoriesQuery.isError.value" class="hint">
          Не удалось загрузить категории. Фильтр «Все» всё ещё доступен.
        </p>
      </div>
    </div>
    <AppField v-model="search" class="search" label="Поиск по названию" type="search" />
    <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
    <p v-if="feed.isPending.value" class="state"><LoadingText label="Загружаем события" /></p>
    <div v-else-if="feed.isError.value && !feed.data.value" class="state">
      <p>Не удалось загрузить события.</p>
      <AppButton variant="secondary" @click="feed.refetch()">Повторить</AppButton>
    </div>
    <template v-else>
      <p v-if="events.length === 0" class="state">Событий не найдено.</p>
      <div class="grid">
        <EventCard
          v-for="(event, i) in events"
          :key="event.id"
          :to="`/events/${event.id}`"
          :title="event.title"
          :state="stateLabel(event.state)"
          :meta="metaLine(event, categoryNames.get(event.category) ?? event.category)"
          :place="`${event.location} · ${participantsLine(event)}`"
          :pink="i % 2 === 1"
        >
          <div
            v-if="confirmingId === event.id"
            class="confirm"
            role="group"
            aria-label="Подтверждение отмены события"
            @keydown.esc="confirmingId = null"
          >
            <p>Отменить событие? Участники получат уведомление.</p>
            <div class="confirm-actions">
              <AppButton
                :disabled="busy || cardAction(event, now).kind !== 'cancel'"
                @click="cancel.mutate(event.id)"
                >Да, отменить</AppButton
              >
              <AppButton variant="secondary" :disabled="busy" @click="confirmingId = null"
                >Нет</AppButton
              >
            </div>
          </div>
          <AppButton
            v-else
            class="join-button"
            block
            :variant="cardAction(event, now).kind === 'join' ? 'primary' : 'secondary'"
            :disabled="busy || cardAction(event, now).kind === 'unavailable'"
            @click="run(event)"
            >{{ cardAction(event, now).label }}</AppButton
          >
        </EventCard>
      </div>
      <div v-if="feed.isFetchNextPageError.value" class="error" role="alert">
        Не удалось загрузить следующую страницу.
      </div>
      <div v-if="feed.hasNextPage.value" class="more">
        <AppButton
          variant="secondary"
          :disabled="feed.isFetchingNextPage.value"
          @click="feed.fetchNextPage()"
          ><LoadingText v-if="feed.isFetchingNextPage.value" label="Загружаем" /><template v-else
            >Показать ещё</template
          ></AppButton
        >
      </div>
    </template>
  </section>
</template>
<style scoped>
.heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}
.filters {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  min-width: 0;
}
.search {
  max-width: 360px;
  margin-bottom: 20px;
}
.hint {
  font-size: 12px;
  color: var(--color-text-subtle);
  text-align: right;
}
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 0;
  text-align: center;
  color: var(--color-text-muted);
}
.error {
  color: #b42318;
  font-size: 13px;
  margin: 12px 0;
}
.more {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
}
.confirm {
  font-size: 13px;
}
.confirm-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
@media (max-width: 700px) {
  .heading {
    display: block;
    margin-bottom: 18px;
  }
  h1 {
    font-size: 25px;
  }
  .filters {
    align-items: stretch;
    margin-top: 14px;
  }
  .hint {
    text-align: left;
  }
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
