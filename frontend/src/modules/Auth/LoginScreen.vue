<template>
  <main class="login">
    <section class="login-hero">
      <div class="login-hero-content">
        <div class="animated-logo" aria-label="ToGo">
          <span>To</span><span class="together">gether</span><span class="go">Go</span>
        </div>
        <h1>Просто пойти вместе</h1>
        <p>Находи компанию для кафе, выставок и спорта по интересам, месту и времени.</p>
      </div>
    </section>
    <section class="login-panel">
      <div class="login-panel-content">
        <h2>Войти</h2>
        <p>Together go</p>
        <button class="button telegram-button" disabled>Войти через Telegram</button
        ><small>Вход через Telegram будет доступен после подключения сервера.</small
        ><RouterLink class="preview-link" to="/">Посмотреть демо интерфейса →</RouterLink>
      </div>
    </section>
  </main>
</template>
