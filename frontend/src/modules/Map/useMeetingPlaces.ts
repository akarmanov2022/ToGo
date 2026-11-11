import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { fetchPlaces } from './places'
import type { MeetingPlace, PlaceBounds } from './types'

export function useMeetingPlaces(
  category: MaybeRefOrGetter<string>,
  enabled: MaybeRefOrGetter<boolean> = true,
  bounds: MaybeRefOrGetter<PlaceBounds | undefined> = undefined,
) {
  return useQuery<MeetingPlace[]>({
    queryKey: computed(() => [
      'osm-places',
      'viewport-world-v5',
      toValue(category),
      toValue(bounds),
    ]),
    queryFn: ({ signal }) => fetchPlaces(toValue(category), signal, toValue(bounds)),
    enabled: computed(() => Boolean(toValue(category)) && toValue(enabled)),
    placeholderData: computed(() => {
      const code = toValue(category)
      return (previous: MeetingPlace[] | undefined) => previous?.every(place => place.category === code) ? previous : undefined
    }),
    staleTime: 30 * 60_000,
    gcTime: 60 * 60_000,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })
}
