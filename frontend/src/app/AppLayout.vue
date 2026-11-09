<template>
  <header class="header">
    <div class="header-inner">
      <RouterLink class="logo logo-small" to="/">To<span>Go</span></RouterLink>
      <nav class="desktop-nav">
        <RouterLink to="/">События</RouterLink><RouterLink to="/profile">Профиль</RouterLink>
      </nav>
      <RouterLink class="button header-create" to="/create">+ Создать событие</RouterLink
      ><RouterLink class="header-avatar" to="/profile" aria-label="Профиль">С</RouterLink>
    </div>
  </header>
  <slot />
  <RouterLink class="floating-create" to="/create" aria-label="Создать событие"
    >＋ <span>Создать</span></RouterLink
  >
  <nav class="mobile-nav">
    <RouterLink to="/"><span>▦</span>События</RouterLink
    ><span class="nav-disabled"><span>♧</span>Друзья</span
    ><RouterLink to="/profile"><span>♙</span>Профиль</RouterLink>
  </nav>
</template>
