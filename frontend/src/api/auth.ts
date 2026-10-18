import { apiFetch } from './client'
import type { TokenResponse } from './types'

export function login(email: string, password: string) {
  return apiFetch<TokenResponse>('/auth/login', { method: 'POST', body: { email, password } })
}

export function cambiarPassword(passwordActual: string, passwordNueva: string) {
  return apiFetch<void>('/usuarios/me/password', { method: 'PUT', body: { passwordActual, passwordNueva } })
}

export function register(nombre: string, email: string, password: string) {
  return apiFetch<TokenResponse>('/auth/register', { method: 'POST', body: { nombre, email, password } })
}
