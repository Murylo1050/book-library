<template>
  <main class="home">
    <header class="home__header">
      <h1>Minha Biblioteca</h1>
      <button type="button" class="btn btn-primary" @click="dialogOpen = true">
        + Novo livro
      </button>
    </header>

    <p v-if="error">Erro ao carregar livros.</p>
    <p v-else-if="!books">Carregando livros...</p>
    <p v-else-if="books.length === 0">Nenhum livro encontrado.</p>

    <div v-else class="book-grid">
      <BookCard v-for="book in books" :key="book.id" :book="book" />
    </div>

    <BookFormDialog v-model:open="dialogOpen" @created="onBookCreated" />
  </main>
</template>

<script setup lang="ts">
import type { Book } from "~/types/book";

const { api } = useApiBase();

const {
  data: books,
  error,
  refresh,
} = await useFetch<Book[]>(`${api}/books`, {
  // Busca só no cliente: em Docker o frontend não alcança o backend via localhost.
  server: false,
});

const dialogOpen = ref(false);

function onBookCreated() {
  refresh();
}
</script>

<style scoped>
.home {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px 16px;
}

.home__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}

.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 20px;
}
</style>
