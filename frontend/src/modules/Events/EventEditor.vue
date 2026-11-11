<script setup lang="ts">
import { computed, onUnmounted, reactive, ref } from 'vue'
import { useMutation } from '@tanstack/vue-query'
import { ApiError } from '@/common/api/client'
import {
  CalendarPicker,
  TimePicker,
  localDate,
  toLocalDateTime,
  toScheduleTimestamp,
} from '@/modules/EventSchedule'
import AppButton from '@/common/ui/AppButton.vue'
import AppField from '@/common/ui/AppField.vue'
import CategorySelect from './CategorySelect.vue'
import { updateEvent, type Category, type EventDto } from './api'
import { editDraft, editPatch, validateEdit } from './editing'
import { availableActions } from './rules'

const props = defineProps<{ event: EventDto; categories: readonly Category[] }>()
const emit = defineEmits<{ saved: []; cancel: [] }>()
const draft = reactive(editDraft(props.event))
const deadline = reactive(toLocalDateTime(props.event.registrationClosesAt))
const now = ref(new Date())
const submitted = ref(false)
const clock = setInterval(() => {
  now.value = new Date()
}, 1000)
onUnmounted(() => clearInterval(clock))
const errors = computed(() => validateEdit(draft, props.event, now.value))
const patch = computed(() => editPatch(draft, props.event))
const serverError = ref('')
const details = ref<Record<string, string>>({})
const mutation = useMutation({
  mutationFn: () => updateEvent(props.event.id, patch.value),
  onSuccess: () => emit('saved'),
  onError: (error) => {
    serverError.value = error instanceof ApiError ? error.message : 'Не удалось сохранить изменения'
    details.value =
      error instanceof ApiError
        ? Object.fromEntries(
            (error.body?.details ?? []).map((detail) => [detail.field, detail.message]),
          )
        : {}
  },
})
const canSave = computed(
  () =>
    availableActions(props.event, now.value).canEdit &&
    !Object.keys(errors.value).length &&
    Object.keys(patch.value).length > 0 &&
    !mutation.isPending.value,
)
function submit() {
  submitted.value = true
  now.value = new Date()
  if (canSave.value) {
    serverError.value = ''
    details.value = {}
    mutation.mutate()
  }
}
function changeDate(value: string) {
  deadline.date = value
  draft.registrationClosesAt = toScheduleTimestamp(deadline)
}
function changeTime(value: string) {
  deadline.time = value
  draft.registrationClosesAt = toScheduleTimestamp(deadline)
}
function digits(field: 'minUsers' | 'maxUsers', event: Event) {
  const input = event.target as HTMLInputElement
  draft[field] = input.value.replace(/\D/g, '')
  input.value = draft[field]
}
</script>
<template>
  <form class="editor" novalidate @submit.prevent="submit">
    <h2>Изменить событие</h2>
    <p class="hint">Начало, окончание и место встречи не изменяются.</p>
    <AppField v-model="draft.title" label="Название" maxlength="100" required />
    <label class="field"
      >Описание<textarea v-model="draft.description" maxlength="500" rows="4" required />
    </label>
    <CategorySelect v-model="draft.category" :categories="categories" />
    <AppField
      :model-value="draft.minUsers"
      label="Минимум участников"
      inputmode="numeric"
      maxlength="2"
      @input="digits('minUsers', $event)"
    />
    <AppField
      :model-value="draft.maxUsers"
      label="Максимум участников"
      inputmode="numeric"
      maxlength="2"
      @input="digits('maxUsers', $event)"
    />
    <fieldset>
      <legend>Закрытие регистрации</legend>
      <CalendarPicker
        :model-value="deadline.date"
        :min="localDate(now)"
        :max="localDate(new Date(event.startsAt))"
        label="Новая дата закрытия регистрации"
        @update:model-value="changeDate"
      />
      <TimePicker
        :model-value="deadline.time"
        label="Новое время закрытия регистрации"
        @update:model-value="changeTime"
      />
    </fieldset>
    <p v-for="(error, field) in submitted ? errors : {}" :key="field" class="error" role="status">
      {{ error }}
    </p>
    <p v-if="serverError" class="error" role="alert">{{ serverError }}</p>
    <p v-for="(error, field) in details" :key="field" class="error">{{ error }}</p>
    <div class="actions">
      <AppButton
        type="submit"
        :disabled="mutation.isPending.value || !availableActions(event, now).canEdit"
        >{{ mutation.isPending.value ? 'Сохраняем…' : 'Сохранить' }}</AppButton
      ><AppButton variant="secondary" :disabled="mutation.isPending.value" @click="emit('cancel')"
        >Отмена</AppButton
      >
    </div>
  </form>
</template>
<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 20px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
}
h2 {
  font-size: 18px;
}
.hint {
  color: var(--color-text-subtle);
  font-size: 12px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 7px;
  font-size: 12px;
  font-weight: 800;
}
textarea {
  border: 1px solid var(--color-input-border);
  border-radius: 10px;
  padding: 10px;
  font: inherit;
  resize: vertical;
  color: var(--color-text);
}
fieldset {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 12px;
}
legend {
  font-size: 12px;
  font-weight: 800;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.error {
  font-size: 12px;
  color: #b42318;
}
</style>
