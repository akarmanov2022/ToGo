import { apiRequest } from '@/common/api/client'
import type { components } from '@/common/api/schema'

export type UserProfile = components['schemas']['UserProfile']
export type UpdateProfileBody = components['schemas']['UpdateProfileRequest']

export function updateCurrentUser(
  body: UpdateProfileBody,
): Promise<components['schemas']['CurrentUser']> {
  return apiRequest<components['schemas']['CurrentUser']>('/api/v1/users/me', {
    method: 'PUT',
    body,
  })
}

export function fetchUserProfile(userId: string): Promise<UserProfile> {
  return apiRequest<UserProfile>(`/api/v1/users/${userId}`)
}

export function avatarLetter(name: string): string {
  const trimmed = name.trim()
  return trimmed ? trimmed[0]!.toUpperCase() : '?'
}
