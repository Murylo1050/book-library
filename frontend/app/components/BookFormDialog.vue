<template>
  <dialog
    ref="dialogEl"
    class="dialog"
    @click="onOverlayClick"
    @close="onClose"
  >
    <div class="dialog__header">
      <h2>Cadastrar livro</h2>
      <button
        type="button"
        class="dialog__close"
        aria-label="Fechar"
        @click="close"
      >
        &times;
      </button>
    </div>

    <form class="dialog__body" @submit.prevent="submit">
      <div class="field">
        <label for="isbn">ISBN</label>
        <div class="isbn-row">
          <input
            id="isbn"
            v-model.trim="form.isbn"
            inputmode="numeric"
            placeholder="9788576082699"
            @keydown.enter.prevent="lookupIsbn"
          />
          <button
            type="button"
            class="btn btn-secondary"
            :disabled="lookingUp"
            @click="lookupIsbn"
          >
            {{ lookingUp ? "Buscando..." : "Buscar na OpenLibrary" }}
          </button>
        </div>
        <p
          v-if="lookupMessage"
          class="hint"
          :class="{ 'hint--error': lookupError }"
        >
          {{ lookupMessage }}
        </p>
      </div>

      <div class="field">
        <label for="bookName">Título *</label>
        <input id="bookName" v-model.trim="form.bookName" required />
      </div>

      <div class="field">
        <label for="author">Autor</label>
        <input id="author" v-model.trim="form.author" />
      </div>

      <div class="row">
        <div class="field">
          <label for="publisher">Editora</label>
          <input id="publisher" v-model.trim="form.publisher" />
        </div>
        <div class="field field--narrow">
          <label for="numPages">Páginas</label>
          <input
            id="numPages"
            v-model.number="form.numPages"
            type="number"
            min="1"
          />
        </div>
      </div>

      <div class="row">
        <div class="field">
          <label for="status">Status</label>
          <select id="status" v-model="form.status">
            <option value="NAO_LIDO">Não lido</option>
            <option value="LIDO">Lido</option>
          </select>
        </div>
        <div class="field">
          <label for="coverImage">Capa</label>
          <input
            id="coverImage"
            type="file"
            accept="image/*"
            @change="onCoverChange"
          />
        </div>
      </div>

      <div v-if="coverPreview" class="cover-preview">
        <img :src="coverPreview" alt="Pré-visualização da capa" />
      </div>

      <p v-if="submitError" class="hint hint--error">{{ submitError }}</p>

      <div class="dialog__footer">
        <button type="button" class="btn btn-secondary" @click="close">
          Cancelar
        </button>
        <button type="submit" class="btn btn-primary" :disabled="submitting">
          {{ submitting ? "Salvando..." : "Salvar livro" }}
        </button>
      </div>
    </form>
  </dialog>
</template>

<script setup lang="ts">
import type { BookStatus } from "~/types/book";

interface OpenLibraryBook {
  title?: string;
  authors?: { name: string }[];
  publishers?: { name: string }[];
  number_of_pages?: number;
  cover?: { small?: string; medium?: string; large?: string };
}

const open = defineModel<boolean>("open", { default: false });
const emit = defineEmits<{ created: [] }>();

const { api } = useApiBase();

const dialogEl = ref<HTMLDialogElement | null>(null);

const form = reactive({
  isbn: "",
  bookName: "",
  author: "",
  publisher: "",
  numPages: "" as number | "",
  status: "NAO_LIDO" as BookStatus,
});

const coverFile = ref<File | null>(null);
const coverPreview = ref("");
const lookingUp = ref(false);
const lookupMessage = ref("");
const lookupError = ref(false);
const submitting = ref(false);
const submitError = ref("");

watch(open, (value) => {
  const el = dialogEl.value;
  if (!el) return;

  if (value) {
    lookupMessage.value = "";
    lookupError.value = false;
    submitError.value = "";
    if (!el.open) el.showModal();
  } else if (el.open) {
    el.close();
  }
});

function onClose() {
  open.value = false;
}

function onOverlayClick(event: MouseEvent) {
  if (event.target === dialogEl.value) close();
}

function close() {
  if (submitting.value) return;
  open.value = false;
}

/* ---------- Capa ---------- */

function onCoverChange(event: Event) {
  const input = event.target as HTMLInputElement;
  setCover(input.files?.[0] ?? null);
}

function setCover(file: File | null) {
  if (coverPreview.value.startsWith("blob:")) {
    URL.revokeObjectURL(coverPreview.value);
  }
  coverFile.value = file;
  coverPreview.value = file ? URL.createObjectURL(file) : "";
}

async function downloadCover(url: string) {
  try {
    const blob = await $fetch<Blob>(url, { responseType: "blob" });
    if (!blob.size) return false;

    const type = blob.type || "image/jpeg";
    const ext = type.includes("png") ? "png" : "jpg";
    setCover(new File([blob], `cover.${ext}`, { type }));
    return true;
  } catch {
    return false;
  }
}

/* ---------- Busca por ISBN (OpenLibrary) ---------- */

async function lookupIsbn() {
  const isbn = form.isbn.replace(/[^0-9Xx]/g, "");

  if (isbn.length !== 10 && isbn.length !== 13) {
    lookupError.value = true;
    lookupMessage.value = "Informe um ISBN com 10 ou 13 dígitos.";
    return;
  }

  lookingUp.value = true;
  lookupError.value = false;
  lookupMessage.value = "";

  try {
    let entry = await fetchByIsbn(isbn);

    // ISBN-13 (978...) pode não resolver direto: tenta o equivalente ISBN-10.
    if (!entry && isbn.length === 13 && isbn.startsWith("978")) {
      entry = await fetchByIsbn(toIsbn10(isbn));
    }

    if (!entry) {
      lookupError.value = true;
      lookupMessage.value = "Livro não encontrado na OpenLibrary.";
      return;
    }

    if (!form.bookName && entry.title) form.bookName = entry.title;
    if (!form.author && entry.authors?.length) {
      form.author = entry.authors.map((a) => a.name).join(", ");
    }
    if (!form.publisher && entry.publishers?.length) {
      form.publisher = entry.publishers[0].name;
    }
    if (!form.numPages && entry.number_of_pages) {
      form.numPages = entry.number_of_pages;
    }

    lookupMessage.value = "Dados preenchidos a partir da OpenLibrary.";

    const coverUrl =
      entry.cover?.large || entry.cover?.medium || entry.cover?.small;
    if (coverUrl && !(await downloadCover(coverUrl))) {
      lookupMessage.value =
        "Dados preenchidos, mas não foi possível baixar a capa. Selecione um arquivo manualmente.";
    }
  } catch {
    lookupError.value = true;
    lookupMessage.value =
      "Falha ao consultar a OpenLibrary. Verifique sua conexão.";
  } finally {
    lookingUp.value = false;
  }
}

async function fetchByIsbn(isbn: string): Promise<OpenLibraryBook | null> {
  const res = await $fetch<Record<string, OpenLibraryBook>>(
    "https://openlibrary.org/api/books",
    {
      query: { bibkeys: `ISBN:${isbn}`, jscmd: "data", format: "json" },
    },
  );
  return res[`ISBN:${isbn}`] ?? null;
}

function toIsbn10(isbn13: string) {
  const core = isbn13.slice(3, 12);
  let sum = 0;
  for (let i = 0; i < 9; i += 1) {
    sum += (10 - i) * Number(core[i]);
  }
  const check = (11 - (sum % 11)) % 11;
  return core + (check === 10 ? "X" : String(check));
}

/* ---------- Envio ---------- */

async function submit() {
  submitError.value = "";

  if (!form.bookName) {
    submitError.value = "Informe o título do livro.";
    return;
  }

  submitting.value = true;
  try {
    const body = new FormData();
    body.append("isbn", form.isbn);
    body.append("bookName", form.bookName);
    if (form.author) body.append("author", form.author);
    if (form.publisher) body.append("publisher", form.publisher);
    if (form.numPages !== "" && Number(form.numPages) > 0) {
      body.append("numPages", String(form.numPages));
    }
    body.append("status", form.status);
    if (coverFile.value) body.append("coverImage", coverFile.value);

    await $fetch(`${api}/books`, { method: "POST", body });

    emit("created");
    resetForm();
    open.value = false;
  } catch {
    submitError.value = "Não foi possível salvar o livro. Tente novamente.";
  } finally {
    submitting.value = false;
  }
}

function resetForm() {
  form.isbn = "";
  form.bookName = "";
  form.author = "";
  form.publisher = "";
  form.numPages = "";
  form.status = "NAO_LIDO";
  setCover(null);
}
</script>

<style scoped>
.dialog {
  border: none;
  border-radius: 12px;
  padding: 0;
  width: min(560px, calc(100vw - 32px));
  max-height: calc(100vh - 48px);
  overflow: auto;
  color: #1f2937;
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.3);
}

.dialog::backdrop {
  background: rgba(15, 23, 42, 0.55);
}

.dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #e5e7eb;
}

.dialog__header h2 {
  margin: 0;
  font-size: 1.1rem;
}

.dialog__close {
  border: none;
  background: transparent;
  font-size: 1.5rem;
  line-height: 1;
  cursor: pointer;
  color: #6b7280;
}

.dialog__close:hover {
  color: #111827;
}

.dialog__body {
  padding: 16px 20px 20px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
  flex: 1;
  min-width: 0;
}

label {
  font-size: 0.85rem;
  font-weight: 600;
  color: #374151;
}

input,
select {
  padding: 8px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.95rem;
  font-family: inherit;
}

input:focus,
select:focus {
  outline: 2px solid #93c5fd;
  outline-offset: 1px;
  border-color: #2563eb;
}

.isbn-row {
  display: flex;
  gap: 8px;
}

.isbn-row input {
  flex: 1;
  min-width: 0;
}

.row {
  display: flex;
  gap: 12px;
}

.field--narrow {
  max-width: 120px;
}

.hint {
  margin: 4px 0 0;
  font-size: 0.82rem;
  color: #047857;
}

.hint--error {
  color: #b91c1c;
}

.cover-preview {
  margin-bottom: 14px;
}

.cover-preview img {
  max-height: 180px;
  max-width: 100%;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}

.dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}
</style>
