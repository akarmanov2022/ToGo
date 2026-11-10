export { default as Profile } from './UserProfile.vue'
export {
  avatarLetter,
  fetchCurrentUser,
  fetchUserProfile,
  updateCurrentUser,
} from './api'
export type { CurrentUser, UpdateProfileBody, UserProfile } from './api'
