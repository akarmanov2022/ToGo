import { nextTick, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

export function usePopover(
  open: Ref<boolean>,
  root: Ref<HTMLElement | null>,
  panel: Ref<HTMLElement | null>,
) {
  const offset = ref(0)

  watch(open, async (value) => {
    if (!value) return
    offset.value = 0
    await nextTick()
    const rect = panel.value?.getBoundingClientRect()
    if (!rect) return
    const overflow = rect.right - (window.innerWidth - 8)
    if (overflow > 0) offset.value = Math.max(8 - rect.left, -overflow)
  })

  function onPointerDown(event: PointerEvent) {
    if (open.value && !root.value?.contains(event.target as Node | null)) open.value = false
  }

  onMounted(() => document.addEventListener('pointerdown', onPointerDown))
  onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerDown))

  return { offset }
}
