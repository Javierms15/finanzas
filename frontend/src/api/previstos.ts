import { apiFetch } from './client'
import type { MovimientoPrevisto } from './types'

export function listarPrevistos(usuarioId: number) {
  return apiFetch<MovimientoPrevisto[]>(`/usuarios/${usuarioId}/previstos`)
}

export interface CrearPrevistoInput {
  fecha: string
  importe: number
  descripcion: string
}

export function crearPrevisto(usuarioId: number, datos: CrearPrevistoInput) {
  return apiFetch<MovimientoPrevisto>(`/usuarios/${usuarioId}/previstos`, { method: 'POST', body: datos })
}

export function marcarPrevistoCumplido(usuarioId: number, previstoId: number, cumplido: boolean) {
  return apiFetch<MovimientoPrevisto>(`/usuarios/${usuarioId}/previstos/${previstoId}`, {
    method: 'PATCH',
    body: { cumplido },
  })
}

export function eliminarPrevisto(usuarioId: number, previstoId: number) {
  return apiFetch<void>(`/usuarios/${usuarioId}/previstos/${previstoId}`, { method: 'DELETE' })
}
