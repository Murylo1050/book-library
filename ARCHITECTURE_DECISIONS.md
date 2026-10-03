# Arquitetura e Decisões do Projeto

## 1. Stack principal

O projeto será uma aplicação web composta por:

- **Frontend:** Nuxt.js / Nuxt 4
- **Backend:** Java + Spring Boot
- **Banco de dados:** PostgreSQL
- **Containers:** Docker + Docker Compose
- **Servidor:** servidor doméstico, sem IP público e sem domínio
- **Acesso administrativo ao servidor:** rede local e ZeroTier
- **Versionamento:** Git + GitHub
- **Deploy:** automático através de `systemd` no servidor

---

## 2. Arquitetura geral

A arquitetura de produção deve ser separada do ambiente de desenvolvimento.

```text
                         GitHub
                            │
                            │ push na main
                            ▼
                 ┌─────────────────────┐
                 │   systemd timer     │
                 │                     │
                 │ deploy.service      │
                 └──────────┬──────────┘
                            │
                            ▼
                       deploy.sh
                            │
                            ▼
                    Git fetch / update
                            │
                            ▼
                  Docker Compose PROD
                            │
             ┌──────────────┼──────────────┐
             ▼              ▼              ▼
          Nuxt.js       Spring Boot    PostgreSQL
          PROD            PROD           PROD
             │              │              │
             └──────────────┘              │
                                           ▼
                                    Docker Volume
```

O servidor não precisa ser acessível diretamente pela Internet para o deploy.

O servidor deve fazer conexões **de saída** para o GitHub e verificar periodicamente se a branch `main` foi alterada.

---

# 3. Deploy automático

## Decisão

Não utilizar GitHub Actions via SSH diretamente no servidor, pois o servidor não possui IP público nem domínio.

O deploy será iniciado pelo próprio servidor através de um `systemd timer`.

Fluxo:

```text
GitHub
   │
   │ nova alteração na main
   ▼
Servidor
   │
   ├── systemd timer
   │
   ├── git fetch origin main
   │
   ├── compara HEAD local com origin/main
   │
   ├── se não mudou → encerra
   │
   └── se mudou:
         ├── atualiza código
         ├── build das imagens
         └── atualiza containers
```

O intervalo inicial sugerido para o timer é de aproximadamente **2 minutos**.

---

# 4. systemd

Devem existir dois arquivos:

```text
/etc/systemd/system/meu-projeto-deploy.service
/etc/systemd/system/meu-projeto-deploy.timer
```

## Service

O service será `Type=oneshot` e executará o script de deploy.

Características:

- executar como usuário dedicado `deploy`;
- depender do Docker;
- utilizar o diretório do projeto como `WorkingDirectory`;
- enviar stdout/stderr para o journal do systemd.

Estrutura conceitual:

```ini
[Unit]
Description=Deploy do projeto
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
User=deploy
WorkingDirectory=/opt/projects/meu-projeto
ExecStart=/opt/projects/meu-projeto/deploy/deploy.sh
```

## Timer

O timer deve iniciar aproximadamente 1 minuto após o boot e executar novamente a cada aproximadamente 2 minutos.

Estrutura conceitual:

```ini
[Unit]
Description=Verifica atualizações do projeto

[Timer]
OnBootSec=1min
OnUnitActiveSec=2min
Unit=meu-projeto-deploy.service

[Install]
WantedBy=timers.target
```

Comandos úteis:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now meu-projeto-deploy.timer
systemctl status meu-projeto-deploy.timer
systemctl list-timers
sudo systemctl start meu-projeto-deploy.service
journalctl -u meu-projeto-deploy.service
journalctl -u meu-projeto-deploy.service -f
```

---

# 5. Script de deploy

O script deve usar:

```bash
set -e
```

Isso faz com que o script interrompa a execução quando um comando relevante retornar erro, evitando continuar o deploy após uma etapa crítica falhar.

O script deve:

1. entrar no diretório do projeto;
2. executar `git fetch origin main`;
3. obter o commit local;
4. obter o commit remoto;
5. comparar os dois;
6. encerrar se não houver alteração;
7. atualizar o código se houver alteração;
8. construir as imagens de produção;
9. atualizar os containers;
10. limpar imagens Docker antigas quando apropriado.

Os logs devem usar `echo`, pois o systemd captura stdout/stderr no `journald`.

Exemplo conceitual:

```bash
#!/bin/bash

set -e

PROJECT="/opt/projects/meu-projeto"

cd "$PROJECT"

echo "Verificando alterações..."

git fetch origin main

LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse origin/main)

if [ "$LOCAL" = "$REMOTE" ]; then
    echo "Nenhuma alteração encontrada."
    exit 0
fi

echo "Nova versão encontrada."

git reset --hard origin/main

echo "Construindo imagens..."
docker compose -f compose.prod.yml build

echo "Atualizando containers..."
docker compose -f compose.prod.yml up -d

echo "Limpando imagens antigas..."
docker image prune -f

echo "Deploy concluído."
```

Não executar `docker compose down` antes do build. O objetivo é evitar derrubar a aplicação caso o build falhe.

---

# 6. Estrutura do projeto

Estrutura recomendada:

```text
meu-projeto/
│
├── frontend/
│   ├── Dockerfile
│   ├── Dockerfile.dev
│   ├── package.json
│   ├── nuxt.config.ts
│   └── ...
│
├── backend/
│   ├── Dockerfile
│   ├── Dockerfile.dev
│   ├── pom.xml
│   └── src/
│
├── database/
│   └── migrations/
│
├── deploy/
│   └── deploy.sh
│
├── compose.dev.yml
├── compose.prod.yml
├── .env
└── .gitignore
```

A configuração de desenvolvimento e produção deve ser separada.

---

# 7. Ambiente de desenvolvimento

O ambiente de desenvolvimento será executado em containers, mas com características próprias de desenvolvimento.

Deve oferecer:

- hot reload;
- Nuxt HMR;
- Spring Boot DevTools;
- debug remoto do Spring Boot;
- source code montado como volume;
- dependências de desenvolvimento;
- logs detalhados;
- PostgreSQL acessível pelo host para ferramentas como DBeaver/DataGrip;
- facilidade para alteração de código sem rebuild da imagem.

Executar:

```bash
docker compose -f compose.dev.yml up
```

ou:

```bash
docker compose -f compose.dev.yml up --build
```

Portas esperadas em desenvolvimento:

```text
Nuxt:        localhost:3000
Spring Boot: localhost:8080
PostgreSQL:  localhost:5432
Java Debug:  localhost:5005
```

---

# 8. Ambiente de produção

O ambiente de produção deve ser otimizado e não deve conter ferramentas desnecessárias de desenvolvimento.

Não devem ser necessários:

- hot reload;
- HMR;
- Spring Boot DevTools em runtime;
- debug remoto;
- source mounts;
- ferramentas de desenvolvimento;
- dependências de desenvolvimento no runtime;
- Maven no container final;
- código fonte desnecessário no runtime do Nuxt.

A produção deve utilizar Dockerfiles multi-stage quando apropriado.

---

# 9. Nuxt em produção

Fluxo esperado:

```text
Node/build environment
       │
       ├── npm ci
       ├── npm run build
       │
       ▼
.output/
       │
       ▼
runtime image
       │
       ▼
Nuxt production server
```

O container final deve conter somente os artefatos necessários para executar a aplicação.

Não utilizar hot reload em produção.

---

# 10. Spring Boot em produção

Fluxo esperado:

```text
JDK + Maven
      │
      ├── mvn package
      │
      ▼
    app.jar
      │
      ▼
Java runtime image
      │
      ▼
java -jar app.jar
```

O container final não deve precisar do Maven.

DevTools deve ser utilizado apenas para desenvolvimento.

---

# 11. PostgreSQL

PostgreSQL deve ser executado em container.

Os dados devem estar em um **Docker volume persistente**.

Nunca depender do filesystem efêmero do container.

Conceito:

```text
PostgreSQL container
       │
       ▼
Docker Volume
       │
       ▼
dados persistentes
```

Um restart/recriação do container não deve apagar os dados.

No ambiente de desenvolvimento pode-se expor:

```text
5432:5432
```

para facilitar o acesso por ferramentas externas.

Em produção, preferencialmente não expor PostgreSQL diretamente ao host.

---

# 12. Redes Docker

Separar as redes conforme a necessidade.

Conceito:

```text
                app network
             ┌───────────────┐
             │               │
          Nuxt ───────► Spring
                         │
                         │
                    database network
                         │
                         ▼
                     PostgreSQL
```

O frontend não precisa ter acesso direto ao PostgreSQL.

O Spring Boot deve ser o responsável pelo acesso ao banco.

---

# 13. Compose de produção

O `compose.prod.yml` deve conter:

```text
frontend
backend
postgres
```

Características:

- `restart: unless-stopped`;
- sem source mounts;
- imagens de produção;
- PostgreSQL com volume persistente;
- redes separadas;
- healthcheck para PostgreSQL;
- Spring dependendo da saúde do PostgreSQL;
- nenhuma porta desnecessária exposta.

Conceito:

```yaml
services:

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    restart: unless-stopped

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: prod

  postgres:
    image: postgres
    restart: unless-stopped
    volumes:
      - postgres_data:/var/lib/postgresql
```

---

# 14. Compose de desenvolvimento

O `compose.dev.yml` deve ser independente da produção.

Características:

```text
frontend
├── Dockerfile.dev
├── source volume
├── node_modules volume
└── hot reload

backend
├── Dockerfile.dev
├── source volume
├── Maven cache volume
├── Spring DevTools
└── debug :5005

postgres
├── volume de desenvolvimento
└── porta 5432 exposta
```

O banco de desenvolvimento deve ser separado do banco de produção.

Nunca apontar o ambiente de desenvolvimento diretamente para o banco de produção.

---

# 15. Autenticação

A autenticação deve utilizar **cookies HTTP-only**, preferencialmente com sessão no servidor, e não JWT armazenado em `localStorage`.

Fluxo:

```text
Nuxt
   │
   │ POST /api/auth/login
   ▼
Spring Security
   │
   ├── valida credenciais
   ├── cria sessão
   └── Set-Cookie
             │
             ▼
          Browser
```

Nas requisições seguintes:

```http
Cookie: SESSION=...
```

O JavaScript do frontend não deve conseguir acessar o cookie.

---

# 16. Configuração do cookie

Produção:

```text
HttpOnly = true
Secure = true
SameSite = Lax
Path = /
```

O `Secure` deve ser utilizado quando a aplicação estiver atrás de HTTPS.

O frontend não deve armazenar a sessão em:

```text
localStorage
sessionStorage
Zustand
```

O browser deve gerenciar o cookie.

---

# 17. Spring Security

O backend deve utilizar:

```text
Spring Security
Spring Session JDBC
Spring Data JPA
PostgreSQL
```

A sessão pode ser persistida no PostgreSQL para que reinicializações do container Spring Boot não necessariamente invalidem todas as sessões.

Arquitetura:

```text
Browser
   │
   │ SESSION cookie
   ▼
Spring Security
   │
   ▼
Spring Session JDBC
   │
   ▼
PostgreSQL
```

CSRF não deve ser simplesmente desabilitado em uma implementação final baseada em cookies. Deve ser definida uma estratégia adequada de proteção CSRF.

---

# 18. Endpoints de autenticação

Estrutura inicial:

```text
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me
```

`/api/auth/login` e `/api/auth/logout` devem poder ser acessados sem autenticação prévia quando apropriado.

As demais rotas devem exigir autenticação conforme as regras de autorização.

---

# 19. Nuxt e autenticação

O frontend deve fazer requisições com credenciais/cookies habilitados quando necessário:

```typescript
await $fetch('/api/auth/login', {
  method: 'POST',
  body: {
    email,
    password
  },
  credentials: 'include'
})
```

Para descobrir o usuário autenticado:

```typescript
await $fetch('/api/auth/me', {
  credentials: 'include'
})
```

O estado do usuário no frontend pode ser mantido em memória/store para conveniência de UI, mas a **fonte de verdade da autenticação é a sessão do backend**, não o estado do store.

---

# 20. Reverse proxy

Quando a aplicação for disponibilizada externamente, utilizar um reverse proxy como Caddy ou Nginx.

Arquitetura:

```text
Internet
   │
   ▼
Caddy / Nginx
   │
   ├── /       → Nuxt
   │
   └── /api/   → Spring Boot
```

Idealmente frontend e backend devem ficar sob a mesma origem:

```text
https://dominio/
https://dominio/api/
```

Isso simplifica cookies, CORS e autenticação.

No ambiente atual, o servidor não possui IP público/domínio. O reverse proxy pode ser adicionado posteriormente caso o site seja publicado externamente via uma solução como Cloudflare Tunnel, Tailscale/Funnel ou outra infraestrutura adequada.

---

# 21. Docker x ambiente do servidor

O servidor deve executar somente o ambiente de produção.

O ambiente de desenvolvimento deve ser utilizado na máquina de desenvolvimento.

```text
DESENVOLVIMENTO
    │
    └── compose.dev.yml

SERVIDOR
    │
    └── compose.prod.yml
```

Não misturar volumes de desenvolvimento com produção.

---

# 22. Observabilidade

Utilizar:

```text
Spring Boot Actuator
```

principalmente para health checks.

Endpoint conceitual:

```text
/actuator/health
```

Isso poderá ser integrado posteriormente aos healthchecks do Docker e ao reverse proxy.

Logs dos serviços devem ser acessíveis através de:

```bash
docker compose logs
docker compose logs -f
```

Logs do processo de deploy:

```bash
journalctl -u meu-projeto-deploy.service
journalctl -u meu-projeto-deploy.service -f
```

---

# 23. Dependências iniciais do Spring Initializr

Selecionar:

```text
Spring Web
Spring Data JPA
PostgreSQL Driver
Validation
Spring Security
Spring Boot Actuator
Spring Boot DevTools
Lombok
```

Para autenticação persistente em sessão, adicionar também:

```text
Spring Session JDBC
```

Dependências adicionais devem ser adicionadas somente quando houver necessidade concreta.

Evitar inicialmente:

```text
Spring WebFlux
Spring Data REST
Spring HATEOAS
Spring Batch
Spring Cloud
Spring Data MongoDB
```

---

# 24. Princípios importantes para o agente

Ao modificar o projeto, preservar estas decisões:

1. Não misturar configuração de desenvolvimento e produção.
2. Não armazenar credenciais de autenticação em `localStorage`.
3. Utilizar cookies `HttpOnly` para a sessão.
4. Não expor PostgreSQL publicamente.
5. Manter os dados PostgreSQL em volume persistente.
6. Não executar `docker compose down` antes de um build de produção.
7. O deploy deve parar caso uma etapa crítica falhe.
8. O servidor não depende de conexões de entrada do GitHub.
9. O servidor verifica o GitHub através de conexões de saída.
10. Produção não deve utilizar hot reload ou ferramentas de debug.
11. Desenvolvimento deve ter hot reload e ferramentas de desenvolvimento.
12. Frontend não acessa PostgreSQL diretamente.
13. Spring Boot é responsável pelo acesso ao banco.
14. Usar healthchecks quando apropriado.
15. Manter logs do deploy no `journald`.
16. Não destruir o volume do PostgreSQL durante deploy.
17. Segredos não devem ser commitados no Git.
18. Configurações específicas de ambiente devem ser fornecidas por variáveis de ambiente/secrets.
19. O código deve continuar compatível com execução containerizada.
20. Mudanças na infraestrutura devem preservar a separação DEV/PROD.

---

# 25. Estado desejado da arquitetura

```text
                         GITHUB
                            │
                            │
                      branch: main
                            │
                            ▼
                   ┌─────────────────┐
                   │ SERVER          │
                   │                 │
                   │ systemd timer   │
                   │       │         │
                   │       ▼         │
                   │ deploy.service  │
                   │       │         │
                   │       ▼         │
                   │   deploy.sh     │
                   │       │         │
                   │       ▼         │
                   │ compose.prod    │
                   └───────┬─────────┘
                           │
             ┌─────────────┼──────────────┐
             ▼             ▼              ▼
        ┌─────────┐  ┌────────────┐  ┌────────────┐
        │  Nuxt   │  │   Spring   │  │ PostgreSQL │
        │  PROD   │  │    Boot    │  │    PROD    │
        │         │  │    PROD    │  │            │
        └─────────┘  └─────┬──────┘  └──────┬─────┘
                           │                │
                           └────────────────┘
                                    │
                              Docker Volume


DESENVOLVIMENTO:

        ┌─────────────────────────────────────┐
        │           compose.dev.yml           │
        │                                     │
        │  Nuxt DEV     Spring DEV    PG DEV  │
        │  HMR          DevTools      Volume   │
        │  :3000        :8080         :5432   │
        │                    │                │
        │                 Debug :5005         │
        └─────────────────────────────────────┘
```

Este documento representa as decisões arquiteturais atuais e deve ser tratado como referência ao implementar ou modificar o projeto.
