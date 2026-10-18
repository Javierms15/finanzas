export const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

let token: string | null = localStorage.getItem('finanzas.token')
let onUnauthorized: (() => void) | null = null

export function setToken(nuevoToken: string | null) {
  token = nuevoToken
  if (nuevoToken) {
    localStorage.setItem('finanzas.token', nuevoToken)
  } else {
    localStorage.removeItem('finanzas.token')
  }
}

export function getToken() {
  return token
}

export function onUnauthorizedResponse(callback: () => void) {
  onUnauthorized = callback
}

/** El backend responde {"message": "..."} en los errores de validacion; si no es JSON se devuelve el texto tal cual. */
function extraerMensaje(texto: string): string {
  try {
    const cuerpo = JSON.parse(texto)
    if (cuerpo && typeof cuerpo.message === 'string' && cuerpo.message) return cuerpo.message
  } catch {
    // no era JSON
  }
  return texto
}

interface RequestOptions {
  method?: string
  body?: unknown
  isFormData?: boolean
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  if (!options.isFormData && options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.isFormData ? (options.body as FormData) : options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  if (response.status === 401 && onUnauthorized) {
    onUnauthorized()
  }

  if (!response.ok) {
    const texto = await response.text().catch(() => '')
    throw new ApiError(extraerMensaje(texto) || `Error ${response.status}`, response.status)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}
