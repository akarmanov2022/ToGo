/** Один ключ серверного профиля для входа, шапки и редактора. */
export const currentUserKey = (userId: string | undefined) => ['current-user', userId] as const
