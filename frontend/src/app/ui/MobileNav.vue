<template>
  <nav class="nav" aria-label="Нижняя навигация">
    <RouterLink to="/">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <rect x="3" y="5" width="18" height="16" rx="3" />
        <path d="M8 3v4M16 3v4M3 10h18" />
      </svg>
      События
    </RouterLink>
    <span class="disabled" aria-disabled="true">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="9" cy="8" r="3.5" />
        <path
          d="M2.5 20c0-3.5 3-6 6.5-6s6.5 2.5 6.5 6M16 4.5a3.5 3.5 0 010 7M18 14.5c2.2.6 3.5 2.6 3.5 5.5"
        />
      </svg>
      Друзья
    </span>
    <RouterLink to="/profile">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21c0-4 3.6-6.5 8-6.5s8 2.5 8 6.5" />
      </svg>
      Профиль
    </RouterLink>
  </nav>
</template>
<style scoped>
.nav {
  display: none;
}
@media (max-width: 700px) {
  .nav {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    background: var(--color-surface);
    border-top: 1px solid #f3d9e5;
    display: flex;
    justify-content: space-around;
    z-index: 3;
    padding: 8px 0 calc(8px + env(safe-area-inset-bottom));
    font-size: 10px;
    font-weight: 800;
    color: var(--color-text-subtle);
  }
  .nav a,
  .disabled {
    display: flex;
    align-items: center;
    flex-direction: column;
    gap: 2px;
  }
  .nav a.router-link-exact-active {
    color: var(--color-text);
  }
  .disabled {
    color: #aaa4b5;
  }
  svg {
    width: 24px;
    height: 24px;
    fill: none;
    stroke: currentColor;
    stroke-width: 2;
    stroke-linecap: round;
    stroke-linejoin: round;
  }
}
</style>
