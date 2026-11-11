<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ApiError } from '@/common/api/client'
import AppButton from '@/common/ui/AppButton.vue'
import LoadingText from '@/common/ui/LoadingText.vue'
import { fetchComments, publishComment } from './api'
import { formatStart } from './format'

const props = defineProps<{ eventId: string; canComment: boolean }>()
const queryClient = useQueryClient()
const draft = ref('')
const failure = ref('')

const commentsQuery = useQuery({
  queryKey: computed(() => ['events', props.eventId, 'comments']),
  queryFn: () => fetchComments(props.eventId),
})

const publishMutation = useMutation({
  mutationFn: () => publishComment(props.eventId, draft.value.trim()),
  onSuccess: async () => {
    draft.value = ''
    failure.value = ''
    await queryClient.invalidateQueries({ queryKey: ['events', props.eventId, 'comments'] })
  },
  onError: (error) => {
    failure.value =
      error instanceof ApiError && error.message
        ? error.message
        : 'Не удалось опубликовать комментарий'
  },
})

const blank = computed(() => draft.value.trim().length === 0)
</script>
<template>
  <section class="comments">
    <h2>Комментарии</h2>
    <p v-if="commentsQuery.isPending.value" class="state">
      <LoadingText label="Загружаем" />
    </p>
    <div v-else-if="commentsQuery.isError.value" class="state">
      <p>Не удалось загрузить комментарии.</p>
      <AppButton variant="secondary" @click="commentsQuery.refetch()">Повторить</AppButton>
    </div>
    <p v-else-if="(commentsQuery.data.value?.items.length ?? 0) === 0" class="state">
      Комментариев пока нет.
    </p>
    <ul v-else class="list">
      <li
        v-for="comment in commentsQuery.data.value?.items ?? []"
        :key="comment.id"
        class="comment"
      >
        <p class="meta">
          <RouterLink :to="`/users/${comment.author.id}`">
            {{ comment.author.tgLogin ? `@${comment.author.tgLogin}` : 'участник' }}
          </RouterLink>
          <span>{{ formatStart(comment.createdAt) }}</span>
        </p>
        <p class="text">{{ comment.text }}</p>
      </li>
    </ul>

    <form v-if="canComment" class="form" @submit.prevent="blank || publishMutation.mutate()">
      <label>
        Ваш комментарий
        <textarea v-model="draft" maxlength="1000" rows="3" />
      </label>
      <AppButton type="submit" :disabled="blank || publishMutation.isPending.value">
        Отправить
      </AppButton>
      <p v-if="failure" class="error">{{ failure }}</p>
    </form>
    <p v-else class="hint">Комментировать могут только участники события.</p>
  </section>
</template>
<style scoped>
.comments {
  margin-top: 28px;
}
.comments h2 {
  margin-bottom: 12px;
  font-size: 18px;
}
.list {
  list-style: none;
  padding: 0;
  margin: 0 0 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.meta {
  display: flex;
  gap: 8px;
  align-items: baseline;
  font-size: 12px;
  color: var(--color-text-subtle);
}
.meta a {
  font-weight: 800;
}
.text {
  margin-top: 4px;
  font-size: 14px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.form {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}
.form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  font-size: 12px;
  font-weight: 800;
}
.form textarea {
  border: 1px solid var(--color-input-border);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  font: inherit;
  font-weight: 400;
  resize: vertical;
}
.error {
  color: #c62828;
  font-size: 13px;
}
.hint {
  font-size: 13px;
  color: var(--color-text-subtle);
}
.state {
  padding: 16px 0;
  color: var(--color-text-muted);
}
</style>
