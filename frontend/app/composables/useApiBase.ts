/**
 * Resolve as bases de URL da API a partir de runtimeConfig.public.apiBase.
 * Aceita tanto "http://localhost:8080" quanto "http://localhost:8080/api".
 *
 * - api:    base para endpoints REST (ex.: /books)
 * - origin: base do servidor (ex.: /uploads, que fica fora de /api)
 */
export function useApiBase() {
  const config = useRuntimeConfig()

  const raw = config.public.apiBase.replace(/\/+$/, "")
  const origin = raw.replace(/\/api$/, "")
  const api = `${origin}/api`

  return { api, origin }
}
