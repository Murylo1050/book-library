<template>
  <div>
    <NuxtRouteAnnouncer />
    <h1>Book Library</h1>
    <p v-if="message">Backend diz: {{ message }}</p>
    <p v-else>Carregando mensagem do backend...</p>
  </div>
</template>

<script setup lang="ts">
const message = ref("");

onMounted(async () => {
  const config = useRuntimeConfig();
  try {
    const data = await $fetch<{ message: string }>(
      `${config.public.apiBase}/api/hello`,
    );
    message.value = data.message;
  } catch {
    message.value = "Backend indisponível";
  }
});
</script>
