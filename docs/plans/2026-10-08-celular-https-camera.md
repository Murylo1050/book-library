# Plano: Uso no celular (CORS + câmera com HTTPS)

> **Status:** Planejado — **não implementado** (registrado em 2026-10-08)
> **Projeto:** book-library
> **Contexto:** acessar o app pelo iPhone pela LAN (`http://192.168.4.146:3000`)

## Problema

Ao abrir o app no celular (iPhone, mesma rede, IP `192.168.4.146`), três bloqueios:

| # | Sintoma | Causa |
|---|---------|-------|
| 1 | "Erro ao carregar livros" | **CORS** — o backend só permite origem `http://localhost:3000` (`backend/src/main/java/dev/murylo/backend/SecurityConfig.java`, `setAllowedOrigins`) |
| 2 | "Necessário HTTPS ou localhost" (câmera) | **Contexto inseguro** — `getUserMedia` exige HTTPS ou `localhost`; `http://IP-da-LAN` não vale |
| 3 | (apareceria após resolver o 2) Chamadas à API falhariam | **Mixed content** — página HTTPS chamando `http://IP:8080` é bloqueada pelo navegador |

### Decisões já tomadas

- **Flag do Chrome** (`chrome://flags/#unsafely-treat-insecure-origin-as-secure`) **não serve**: no iOS todos os navegadores rodam sobre WebKit (Apple exige), e essa flag é recurso do Blink/Chromium (só Android/desktop). Safari iOS não tem equivalente. Além disso a flag não resolve o CORS (item 1).
- Ordem aprovada: **Etapa A primeiro, depois Etapa B**.

---

## Etapa A — Proxy de mesma origem (resolve CORS + mixed content)

Fazer o navegador falar **apenas com `:3000`**, repassando `/api` e `/uploads` ao backend pelo servidor (Nuxt/Nitro). Assim não existe cross-origin nem mixed content — vale para dev, celular e produção.

### Passos

1. **Criar proxy no Nuxt** (arquivos `server/` do Nuxt, ex.: `frontend/server/routes/...` ou plugin Nitro):
   - Rotas catch-all: `/api/**` e `/uploads/**` → `proxyRequest` (h3)
   - Alvo via runtimeConfig **não-público** (ex.: `apiProxyTarget`):
     - default `http://localhost:8080`
     - compose: `http://backend:8080` (env no serviço frontend)
   - Deve ser configurável em runtime (não bake no build)
2. **`useApiBase`** (`frontend/app/composables/useApiBase.ts`): passar a gerar caminhos **relativos** — `api = "/api"`, `origin = ""` — quando `apiBase` estiver vazio
   - `frontend/nuxt.config.ts`: default `apiBase: ""`
   - `compose.dev.yml`: `NUXT_PUBLIC_API_BASE: ""`
   - Imagens de capa: `${origin}/${coverImage}` → `/uploads/...` (relativo, same-origin ✓)
3. **CORS do backend**: pode permanecer como está (requisições same-origin não passam por CORS). Opcionalmente aliviar depois.
4. Upload multipart via `$fetch` passa pelo proxy — confirmar que o `proxyRequest` do h3 faz stream do multipart corretamente (testar criação de livro com capa).

### Critérios de aceite

- [ ] `http://192.168.4.146:3000` no iPhone: lista de livros carrega (sem "Erro ao carregar livros")
- [ ] `npm run build` passa
- [ ] Criação de livro com capa funciona pelo proxy
- [ ] localhost:3000 (desktop) continua funcionando igual

---

## Etapa B — HTTPS no dev com mkcert (libera a câmera)

CA local confiável no PC + no iPhone. `mkcert` está no repo `extra` do Arch (`1.4.4-3`).

### Passos (PC)

1. `pacman -S mkcert` e `mkcert -install` (instala a CA raiz local)
2. Gerar certs para o IP da LAN: `mkcert 192.168.4.146 localhost 127.0.0.1` → gravar em `frontend/certs/` (adicionar ao `.gitignore`)
3. `nuxt.config.ts`: habilitar `devServer.https` **condicionalmente** — só se os arquivos de cert existirem (dev em localhost continua HTTP como sempre)
4. `compose.dev.yml`: montar `./frontend/certs:/app/certs` no serviço `frontend`

### Passos (iPhone — uma vez só)

1. Servir o `rootCA.pem` (AirDrop ou `python3 -m http.server` temporário no PC)
2. No iPhone: abrir o link → instalar o perfil (*Ajustes → Perfil Baixado*)
3. *Ajustes → Geral → Sobre → Configuração de Confiança de Certificados* → ativar o **mkcert Root CA**
4. Acessar `https://192.168.4.146:3000` → contexto seguro ✓ → câmera pede permissão e funciona

### Critérios de aceite

- [ ] `https://192.168.4.146:3000` abre sem aviso de certificado no iPhone
- [ ] Botão 📊 do leitor abre a câmera e lê um ISBN
- [ ] Lista + busca OpenLibrary + capa funcionam via HTTPS (Etapa A)
- [ ] `npm run dev` local (sem cert) continua em HTTP

---

## Etapa C — Produção (futura, ao montar o `compose.prod`)

- Reaproveitar a mesma CA interna (mkcert ou CA própria) no reverse proxy (Caddy/nginx) do deploy
- Registrar a decisão no `ARCHITECTURE_DECISIONS.md` (arquitetura sem domínio → TLS com CA interna + confiança manual por dispositivo)
- Staging / ZeroTier: incluir os IPs (LAN e ZeroTier) nos SANs do cert

## Avisos e dicas

- **DHCP reservation:** reservar `192.168.4.146` no roteador — se o IP mudar, cert e favoritos quebram
- **Bind mount do `node_modules`:** o container (root) escreve como root no host e quebra `npm install` local. Ao retomar este trabalho, escolher uma das opções:
  - voltar ao volume anônimo `- /app/node_modules` no compose, ou
  - adicionar `user: "${UID:-1000}:${GID:-1000}"` no serviço `frontend`
- **Legado:** se `node_modules` voltar a ter arquivos root, limpar com `docker run --rm -v <frontend>:/app alpine:3 rm -rf /app/node_modules` (sem sudo)
- **Bug menor observado:** livro sem dados gera `coverImage: "uploads/null"` no backend (`BookService.findAll` concatena `null`) — considerar corrigir quando retomar

## Arquivos que serão tocados na implementação

- `frontend/nuxt.config.ts` (apiBase, devServer.https)
- `frontend/app/composables/useApiBase.ts` (caminhos relativos)
- `frontend/server/...` (novo: proxy Nitro)
- `compose.dev.yml` (env do proxy, volume de certs, possivelmente `user:`)
- `frontend/.gitignore` (certs/)
- (opcional) `backend/src/main/java/dev/murylo/backend/SecurityConfig.java` (CORS)
- (fase C) `ARCHITECTURE_DECISIONS.md`
