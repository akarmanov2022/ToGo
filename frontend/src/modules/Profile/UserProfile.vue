<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ApiError } from '@/common/api/client'
import AppButton from '@/common/ui/AppButton.vue'
import AppCard from '@/common/ui/AppCard.vue'
import AppChip from '@/common/ui/AppChip.vue'
import AppField from '@/common/ui/AppField.vue'
import ChipGroup from '@/common/ui/ChipGroup.vue'
import LoadingText from '@/common/ui/LoadingText.vue'
import { currentUserKey, useAuthStore } from '@/modules/Auth'
import {
  EventCard,
  fetchCategories,
  fetchMyEvents,
  fetchOrganizerEvents,
  metaLine,
  participantsLine,
  stateLabel,
  isVisibleInFeed,
  type EventDto,
} from '@/modules/Events'
import { avatarLetter, updateCurrentUser } from './api'
import { useProfile } from './useProfile'

const props = defineProps<{ userId?: string }>()
const auth = useAuthStore()
const queryClient = useQueryClient()
const isOwn = computed(() => !props.userId || props.userId === auth.user?.id)

const tabs = computed(() =>
  isOwn.value ? (['Я иду', 'Мои события', 'Архив'] as const) : (['События'] as const),
)
const tab = ref<(typeof tabs.value)[number]>('Я иду')
watch(isOwn, () => {
  tab.value = tabs.value[0]
})

const profileQuery = useProfile(() => (isOwn.value ? undefined : props.userId))
const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: fetchCategories })
const categoryNames = computed(
  () => new Map((categoriesQuery.data.value ?? []).map((item) => [item.code, item.name])),
)

const profile = profileQuery.data
const rating = computed(() => (profile.value?.organizerRating?.average ?? 0).toFixed(1))
const hasRatings = computed(() => (profile.value?.organizerRating?.count ?? 0) > 0)
const ratingCaption = computed(() => {
  const count = profile.value?.organizerRating?.count ?? 0
  if (!count) return 'Пока нет оценок'
  const last = count % 10
  const lastTwo = count % 100
  const word = last === 1 && lastTwo !== 11 ? 'оценки' : 'оценок'
  return `На основе ${count} ${word}`
})

const editing = ref(false)
const name = ref('')
const city = ref('')
const saveError = ref('')
const saving = ref(false)

watch(
  () => profileQuery.data.value,
  (value) => {
    if (!value || !isOwn.value) return
    name.value = value.name
    city.value = value.city ?? ''
  },
  { immediate: true },
)

async function saveProfile() {
  if (saving.value || !isOwn.value || !profile.value) return
  saveError.value = ''
  saving.value = true
  try {
    const saved = await updateCurrentUser({
      name: name.value.trim(),
      city: city.value.trim() ? city.value.trim() : null,
      categories: [...profile.value.categories],
    })
    if (auth.status !== 'authenticated' || auth.user?.id !== saved.id) return
    queryClient.setQueryData(currentUserKey(saved.id), saved)
    editing.value = false
    await queryClient.invalidateQueries({ queryKey: ['users', saved.id] })
  } catch (error) {
    saveError.value = error instanceof ApiError ? error.message : 'Не удалось сохранить профиль'
  } finally {
    saving.value = false
  }
}

const meetingsQuery = useQuery({
  queryKey: ['events', 'mine', 'all'],
  queryFn: async () => {
    const boundary = new Date().toISOString()
    const [future, past] = await Promise.all([
      fetchMyEvents({ startsFrom: boundary, order: 'asc' }),
      fetchMyEvents({ startsTo: boundary, order: 'desc' }),
    ])
    return [...new Map([...future.items, ...past.items].map((event) => [event.id, event])).values()]
  },
  enabled: isOwn,
})
const archiveStates = ['finished', 'cancelled_by_organizer', 'cancelled_by_system'] as const
const organizerQuery = useQuery({
  queryKey: computed(() => ['events', 'organizer', props.userId]),
  queryFn: ({ signal }) => fetchOrganizerEvents(props.userId!, signal),
  enabled: computed(() => !isOwn.value && Boolean(props.userId)),
})
const archiveQuery = useQuery({
  queryKey: ['events', 'mine', 'archive'],
  queryFn: async () => {
    const pages = await Promise.all(
      archiveStates.map((state) => fetchMyEvents({ state, order: 'desc' })),
    )
    const items = pages
      .flatMap((page) => page.items)
      .sort((a, b) => Date.parse(b.startsAt) - Date.parse(a.startsAt))
    return { items, total: items.length }
  },
  enabled: computed(() => isOwn.value && tab.value === 'Архив'),
})

const list = computed(() => {
  if (!isOwn.value) return organizerQuery.data.value ?? ([] as EventDto[])
  if (tab.value === 'Я иду') return meetingsQuery.data.value?.filter(isVisibleInFeed) ?? []
  if (tab.value === 'Мои события')
    return meetingsQuery.data.value?.filter((event) => event.isOrganizer) ?? []
  return archiveQuery.data.value?.items ?? []
})

const listPending = computed(() => {
  if (!isOwn.value) return organizerQuery.isPending.value
  if (tab.value !== 'Архив') return meetingsQuery.isPending.value
  return archiveQuery.isPending.value
})
const listError = computed(() => {
  if (!isOwn.value) return organizerQuery.isError.value
  if (tab.value !== 'Архив') return meetingsQuery.isError.value
  return archiveQuery.isError.value
})
function refetchList() {
  if (!isOwn.value) return organizerQuery.refetch()
  if (tab.value !== 'Архив') return meetingsQuery.refetch()
  return archiveQuery.refetch()
}
const listFetching = computed(() =>
  !isOwn.value
    ? organizerQuery.isFetching.value
    : tab.value === 'Архив'
      ? archiveQuery.isFetching.value
      : meetingsQuery.isFetching.value,
)
function reloadProfile() {
  return profileQuery.refetch()
}
</script>
<template>
  <section :class="profile ? 'layout' : 'solo'">
    <p v-if="profileQuery.isPending.value" class="state">
      <LoadingText label="Загружаем профиль" />
    </p>
    <div v-else-if="profileQuery.isError.value" class="state">
      <p>Не удалось загрузить профиль.</p>
      <AppButton
        size="sm"
        variant="secondary"
        :disabled="profileQuery.isFetching.value"
        @click="reloadProfile"
        >{{ profileQuery.isFetching.value ? 'Загружаем…' : 'Повторить' }}</AppButton
      >
    </div>
    <template v-else-if="profile">
      <AppCard as="aside" class="summary">
        <img
          v-if="profile.photoUrl"
          class="avatar image"
          :src="profile.photoUrl"
          :alt="profile.name"
        />
        <div v-else class="avatar">{{ avatarLetter(profile.name) }}</div>
        <div class="rating-summary">
          <div
            class="organizer-rating"
            :class="{ 'organizer-rating--empty': !hasRatings }"
            :aria-label="
              hasRatings
                ? `Рейтинг организатора: ${rating}`
                : 'Рейтинг организатора: пока нет оценок'
            "
          >
            <span class="rating-stars" aria-hidden="true">
              <svg
                v-for="star in 5"
                :key="star"
                :class="{ filled: star <= Math.floor(Number(rating)) }"
                viewBox="0 0 24 24"
              >
                <path d="m12 2 3.1 6.3 6.9 1-5 4.9 1.2 6.8-6.2-3.2-6.2 3.2L7 14.2 2 9.3l6.9-1Z" />
              </svg>
            </span>
            <strong>{{ rating }}</strong>
          </div>
          <p class="rating-caption" :class="{ 'rating-empty': !hasRatings }">{{ ratingCaption }}</p>
        </div>
        <h2>{{ profile.name }}</h2>
        <p v-if="profile.tgLogin">@{{ profile.tgLogin }}</p>
        <p v-if="profile.city">{{ profile.city }}</p>
        <div class="stats">
          <div>
            <strong>{{ profile.createdEventsCount }}</strong
            >создано
          </div>
          <div>
            <strong>{{ profile.visitedEventsCount }}</strong
            >посещено
          </div>
        </div>
        <template v-if="isOwn">
          <AppButton v-if="!editing" block @click="editing = true">Редактировать профиль</AppButton>
          <form v-else class="edit" @submit.prevent="saveProfile">
            <AppField v-model="name" label="Имя" maxlength="100" required />
            <AppField v-model="city" label="Город" maxlength="100" />
            <AppButton type="submit" block :disabled="saving">
              {{ saving ? 'Сохраняем…' : 'Сохранить' }}
            </AppButton>
            <AppButton type="button" block variant="secondary" @click="editing = false">
              Отмена
            </AppButton>
            <p v-if="saveError" class="error">{{ saveError }}</p>
          </form>
        </template>
      </AppCard>

      <div>
        <h1>{{ isOwn ? 'Профиль' : profile.name }}</h1>
        <ChipGroup v-if="isOwn" class="tabs">
          <AppChip v-for="item in tabs" :key="item" :active="tab === item" @click="tab = item">
            {{ item }}
          </AppChip>
        </ChipGroup>

        <h2 v-if="!isOwn" class="events-heading">Созданные события</h2>
        <p v-if="listPending" class="state compact">
          <LoadingText label="Загружаем события" />
        </p>
        <div v-else-if="listError" class="state compact">
          <p>Не удалось загрузить список.</p>
          <AppButton size="sm" variant="secondary" :disabled="listFetching" @click="refetchList">{{
            listFetching ? 'Загружаем…' : 'Повторить'
          }}</AppButton>
        </div>
        <p v-else-if="list.length === 0" class="empty">
          {{ isOwn ? 'Здесь пока пусто.' : 'Пользователь пока не создал событий.' }}
        </p>
        <div v-else class="events">
          <EventCard
            v-for="(event, i) in list"
            :key="event.id"
            :to="`/events/${event.id}`"
            :title="event.title"
            :state="stateLabel(event.state)"
            :meta="metaLine(event, categoryNames.get(event.category) ?? event.category)"
            :place="`${event.location} · ${participantsLine(event)}${event.isOrganizer ? ' · вы организатор' : ''}`"
            :pink="i % 2 === 0"
            :organizer="event.organizer"
            :category="event.category"
          />
        </div>
      </div>
    </template>
  </section>
</template>
<style scoped>
.layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 40px;
}
.solo {
  display: block;
}
.summary {
  --card-radius: 16px;
  --card-padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  text-align: center;
}
.summary h2 {
  font-size: 22px;
}
.organizer-rating {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--color-text);
}
.organizer-rating strong {
  font-size: 30px;
  line-height: 1.2;
  font-weight: 900;
  font-variant-numeric: tabular-nums;
}
.rating-stars {
  display: flex;
  gap: 3px;
}
.rating-summary {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.organizer-rating--empty {
  color: var(--color-text-subtle);
}
.rating-caption {
  color: var(--color-text-subtle);
}
.organizer-rating svg {
  width: 27px;
  height: 27px;
  fill: none;
  stroke: #df82b0;
  stroke-width: 1.5;
  stroke-linejoin: round;
}
.organizer-rating svg.filled {
  fill: #df82b0;
}
.events-heading {
  margin: 18px 0 12px;
  font-size: 18px;
}
.summary p {
  font-size: 12px;
}
.avatar {
  height: 90px;
  width: 90px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #fbcde3, #cddfff);
  font-size: 38px;
  font-weight: 900;
  color: var(--color-blue);
  object-fit: cover;
}
.avatar.image {
  padding: 0;
}
.stats {
  display: flex;
  gap: 30px;
}
.stats div {
  font-size: 11px;
  display: flex;
  flex-direction: column;
}
.stats strong {
  font-size: 23px;
  font-weight: 900;
}
.edit {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  text-align: left;
}
.error {
  color: #c62828;
  font-size: 12px;
}
h1 {
  margin-bottom: 4px;
}
.tabs {
  margin: 16px 0;
}
.events {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.empty,
.state {
  font-size: 14px;
  color: var(--color-text-subtle);
  padding: 24px 0;
}
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  text-align: center;
  min-height: 40vh;
}
.state.compact {
  min-height: 0;
  padding: 16px 0;
  align-items: flex-start;
  text-align: left;
}
@media (max-width: 700px) {
  .layout {
    display: flex;
    flex-direction: column;
    gap: 24px;
  }
  .summary {
    gap: 10px;
  }
  .avatar {
    width: 70px;
    height: 70px;
    font-size: 30px;
  }
  h1 {
    font-size: 25px;
  }
}
</style>
