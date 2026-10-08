<template>
  <NuxtLink :to="`/books/${book.id}`" class="book-card">
    <div class="book-card__cover">
      <img
        v-if="coverUrl"
        :src="coverUrl"
        :alt="`Capa de ${book.bookName}`"
        loading="lazy"
        class="book-card__image"
      />
      <div v-else class="book-card__placeholder">Sem capa</div>
    </div>

    <div class="book-card__info">
      <h2 class="book-card__title">{{ book.bookName }}</h2>
      <p class="book-card__author">{{ book.author ?? "Autor desconhecido" }}</p>
    </div>
  </NuxtLink>
</template>

<script setup lang="ts">
import type { Book } from "~/types/book";

const props = defineProps<{
  book: Book;
}>();

const { origin } = useApiBase();

const coverUrl = computed(() => {
  if (!props.book.coverImage) return null;
  return `${origin}/${props.book.coverImage}`;
});
</script>

<style scoped>
.book-card {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e2e2e2;
  border-radius: 8px;
  overflow: hidden;
  text-decoration: none;
  color: inherit;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.book-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
}

.book-card__cover {
  aspect-ratio: 2 / 3;
  background: #f4f4f4;
  display: flex;
  align-items: center;
  justify-content: center;
}

.book-card__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.book-card__placeholder {
  color: #999;
  font-size: 0.9rem;
}

.book-card__info {
  padding: 12px;
}

.book-card__title {
  margin: 0 0 4px;
  font-size: 1rem;
  line-height: 1.3;
}

.book-card__author {
  margin: 0;
  font-size: 0.85rem;
  color: #666;
}
</style>
