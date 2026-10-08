<template>
  <div class="scanner">
    <video
      ref="videoEl"
      class="scanner__video"
      autoplay
      muted
      playsinline
    ></video>

    <p class="hint" :class="{ 'hint--error': hasError }">{{ statusMessage }}</p>

    <div class="scanner__actions">
      <button type="button" class="btn btn-secondary" @click="emit('close')">
        {{ hasError ? "Fechar" : "Parar câmera" }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { IScannerControls } from "@zxing/browser";

const emit = defineEmits<{
  scan: [isbn: string];
  close: [];
}>();

const videoEl = ref<HTMLVideoElement | null>(null);
const statusMessage = ref(
  "Aponte a câmera para o código de barras do livro...",
);
const hasError = ref(false);

let controls: IScannerControls | null = null;
let finished = false;

onMounted(start);

onBeforeUnmount(stop);

async function start() {
  if (!globalThis.navigator?.mediaDevices?.getUserMedia) {
    fail(
      "Câmera indisponível: é necessário HTTPS (ou localhost) para acessar a câmera.",
    );
    return;
  }

  try {
    const { BrowserMultiFormatReader } = await import("@zxing/browser");
    const { BarcodeFormat, DecodeHintType } = await import("@zxing/library");

    const hints = new Map();
    hints.set(DecodeHintType.POSSIBLE_FORMATS, [
      BarcodeFormat.EAN_13,
      BarcodeFormat.EAN_8,
      BarcodeFormat.UPC_A,
      BarcodeFormat.UPC_E,
    ]);

    const reader = new BrowserMultiFormatReader(hints, {
      delayBetweenScanAttempts: 250,
    });

    controls = await reader.decodeFromVideoDevice(
      undefined,
      videoEl.value ?? undefined,
      (result) => {
        if (!result || finished) return;

        const text = result.getText().replace(/\s/g, "");
        if (!/^\d{8,14}$/.test(text)) return;

        finished = true;
        emit("scan", text);
        stop();
      },
    );
  } catch (err) {
    fail(cameraErrorMessage(err));
  }
}

function stop() {
  controls?.stop();
  controls = null;
}

function fail(message: string) {
  statusMessage.value = message;
  hasError.value = true;
}

function cameraErrorMessage(err: unknown) {
  const name = (err as DOMException)?.name;

  switch (name) {
    case "NotAllowedError":
    case "PermissionDeniedError":
      return "Permissão da câmera negada. Libere o acesso nas configurações do navegador.";
    case "NotFoundError":
    case "DevicesNotFoundError":
      return "Nenhuma câmera encontrada.";
    case "NotReadableError":
    case "TrackStartError":
      return "Câmera em uso por outro aplicativo.";
    default:
      return "Não foi possível iniciar a câmera.";
  }
}
</script>

<style scoped>
.scanner {
  margin-bottom: 14px;
  padding: 10px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #0f172a;
}

.scanner__video {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: 6px;
  object-fit: cover;
  background: #000;
}

.hint {
  margin: 8px 0 0;
  font-size: 0.82rem;
  color: #93c5fd;
}

.hint--error {
  color: #fca5a5;
}

.scanner__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
