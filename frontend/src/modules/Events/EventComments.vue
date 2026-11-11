<script setup lang="ts">
import { computed, ref } from 'vue'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { ApiError } from '@/common/api/client'
import AppButton from '@/common/ui/AppButton.vue'
import { fetchComments, postComment, type EventComment } from './api'
import { formatStart } from './format'

const props = defineProps<{ eventId: string; isParticipant: boolean }>()
const client = useQueryClient()
const text = ref('')
const errorMessage = ref('')
const published = ref<EventComment | null>(null)
const key = computed(() => ['events', props.eventId, 'comments'])
const comments = useInfiniteQuery({
  retry: false,
  queryKey: key,
  queryFn: ({ pageParam }) => fetchComments(props.eventId, pageParam),
  initialPageParam: 0,
  getNextPageParam: (last) =>
    last.items.length && last.offset + last.items.length < last.total
      ? last.offset + last.items.length
      : undefined,
})
const unavailable = computed(
  () => comments.error.value instanceof ApiError && comments.error.value.status === 501,
)
const items = computed(() => {
  const list = comments.data.value?.pages.flatMap((page) => page.items) ?? []
  return [
    ...new Map(
      [...list, ...(published.value ? [published.value] : [])].map((comment) => [
        comment.id,
        comment,
      ]),
    ).values(),
  ].sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt) || a.id.localeCompare(b.id))
})
const mutation = useMutation({
  mutationFn: () => postComment(props.eventId, text.value.trim()),
  onSuccess: async (comment) => {
    published.value = comment
    text.value = ''
    errorMessage.value = ''
    await client.invalidateQueries({ queryKey: key.value })
  },
  onError: (error) => {
    errorMessage.value =
      error instanceof ApiError
        ? error.status === 501
          ? 'Комментарии пока недоступны на сервере.'
          : error.message
        : 'Не удалось отправить комментарий. Проверьте подключение.'
  },
})
const canSend = computed(
  () =>
    props.isParticipant &&
    text.value.trim().length > 0 &&
    text.value.length <= 1000 &&
    !mutation.isPending.value &&
    !unavailable.value,
)
function submit() {
  if (canSend.value) mutation.mutate()
}
</script>
<template>
  <section id="comments" class="comments" aria-label="Комментарии к событию">
    <h2>Комментарии</h2>
    <p v-if="comments.isPending.value" role="status">Загружаем комментарии…</p>
    <p v-else-if="unavailable" role="status">Комментарии пока недоступны на сервере.</p>
    <div v-else-if="comments.isError.value && !comments.data.value" role="alert">
      <p>Не удалось загрузить комментарии.</p>
      <AppButton size="sm" variant="secondary" @click="comments.refetch()">Повторить</AppButton>
    </div>
    <template v-else>
      <p v-if="!items.length">Пока нет комментариев.</p>
      <ul class="comment-list">
        <li v-for="comment in items" :key="comment.id">
          <RouterLink :to="`/users/${comment.author.id}`">{{
            comment.author.tgLogin ? `@${comment.author.tgLogin}` : 'Профиль участника'
          }}</RouterLink
          ><time :datetime="comment.createdAt">{{ formatStart(comment.createdAt) }}</time>
          <p>{{ comment.text }}</p>
        </li>
      </ul>
      <p v-if="comments.isFetchNextPageError.value" class="error" role="alert">
        Не удалось загрузить следующие комментарии.
      </p>
      <AppButton
        v-if="comments.hasNextPage.value"
        variant="secondary"
        :disabled="comments.isFetchingNextPage.value"
        @click="comments.fetchNextPage()"
        >{{
          comments.isFetchingNextPage.value ? 'Загружаем…' : 'Показать ещё комментарии'
        }}</AppButton
      >
    </template>
    <form v-if="isParticipant && !unavailable" @submit.prevent="submit">
      <label>Ваш комментарий<textarea v-model="text" maxlength="1000" rows="3" required /></label>
      <span class="hint">{{ text.length }} / 1000</span>
      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      <p v-if="published" role="status">Комментарий опубликован.</p>
      <AppButton type="submit" :disabled="!canSend">{{
        mutation.isPending.value ? 'Отправляем…' : 'Отправить'
      }}</AppButton>
    </form>
    <p v-else-if="!isParticipant" class="hint">
      Писать комментарии могут только участники события.
    </p>
  </section>
</template>
<style scoped>
.comments {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--color-border);
}
h2 {
  font-size: 22px;
  margin-bottom: 16px;
}
.comment-list {
  list-style: none;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.comment-list li {
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 14px;
  background: var(--color-surface);
}
.comment-list a {
  font-size: 13px;
  font-weight: 800;
  color: var(--color-blue);
}
time {
  margin-left: 12px;
  font-size: 11px;
  color: var(--color-text-subtle);
}
.comment-list p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 14px;
  margin-top: 8px;
}
form {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  margin-top: 20px;
}
label {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  font-size: 13px;
  font-weight: 800;
}
textarea {
  border: 1px solid var(--color-input-border);
  border-radius: 10px;
  padding: 12px;
  font: inherit;
  color: var(--color-text);
  resize: vertical;
}
.hint {
  font-size: 12px;
  color: var(--color-text-subtle);
}
.error {
  color: #b42318;
  font-size: 13px;
}
</style>
