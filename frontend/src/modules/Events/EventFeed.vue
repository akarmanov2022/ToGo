<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useInfiniteQuery, useQuery } from '@tanstack/vue-query'
import AppButton from '@/common/ui/AppButton.vue'
import AppChip from '@/common/ui/AppChip.vue'
import AppField from '@/common/ui/AppField.vue'
import ChipGroup from '@/common/ui/ChipGroup.vue'
import LoadingText from '@/common/ui/LoadingText.vue'
import { PAGE_SIZE, fetchCategories, fetchFeedPage, type EventCategory } from './api'
import EventCard from './EventCard.vue'
import { metaLine, participantsLine } from './format'
import { stateLabel } from './rules'

const SEARCH_DELAY_MS = 300

const category = ref<EventCategory | null>(null)
const search = ref('')
const query = ref('')

let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(search, (value) => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => (query.value = value), SEARCH_DELAY_MS)
})

const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: fetchCategories })
const categoryNames = computed(
  () => new Map((categoriesQuery.data.value ?? []).map((item) => [item.code, item.name])),
)

const feed = useInfiniteQuery({
  queryKey: ['events', 'feed', category, query],
  queryFn: ({ pageParam }) =>
    fetchFeedPage({ category: category.value, query: query.value }, pageParam),
  initialPageParam: 0,
  getNextPageParam: (last) => {
    const next = last.offset + PAGE_SIZE
    return next < last.total ? next : undefined
  },
})
const events = computed(() => feed.data.value?.pages.flatMap((page) => page.items) ?? [])
</script>
<template>
  <section>
    <div class="heading">
      <div>
        <h1>Куда отправляемся?</h1>
      </div>
      <div class="filters">
        <ChipGroup scrollable class="categories" aria-label="Категории">
          <AppChip :active="category === null" @click="category = null">Все</AppChip>
          <AppChip
            v-for="item in categoriesQuery.data.value ?? []"
            :key="item.code"
            :active="category === item.code"
            @click="category = item.code"
          >
            {{ item.name }}
          </AppChip>
        </ChipGroup>
        <p v-if="categoriesQuery.isError.value" class="hint">
          Не удалось загрузить категории. Фильтр «Все» всё ещё доступен.
        </p>
      </div>
    </div>
    <AppField v-model="search" class="search" label="Поиск по названию" type="search" />
    <p v-if="feed.isPending.value" class="state">
      <LoadingText label="Загружаем события" />
    </p>
    <div v-else-if="feed.isError.value" class="state">
      <p>Не удалось загрузить события.</p>
      <AppButton variant="secondary" @click="feed.refetch()">Повторить</AppButton>
    </div>
    <p v-else-if="events.length === 0" class="state">Событий не найдено.</p>
    <template v-else>
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
        />
      </div>
      <div v-if="feed.hasNextPage.value" class="more">
        <AppButton variant="secondary" @click="feed.fetchNextPage()">
          <LoadingText v-if="feed.isFetchingNextPage.value" label="Загружаем" />
          <template v-else>Показать ещё</template>
        </AppButton>
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
  margin: 0;
  max-width: 280px;
  font-size: 12px;
  line-height: 1.35;
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
    max-width: none;
    text-align: left;
  }
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
