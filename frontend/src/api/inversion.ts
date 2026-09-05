import { apiFetch } from './client'
import type { Activo, PosicionValorada, ProyeccionInversion, TipoActivo, TipoMovimientoInversion } from './types'

export function listarActivos() {
  return apiFetch<Activo[]>('/activos')
}

export function crearActivo(ticker: string, nombre: string, tipo: TipoActivo) {
  return apiFetch<Activo>('/activos', { method: 'POST', body: { ticker, nombre, tipo } })
}

export function listarPosiciones(usuarioId: number) {
  return apiFetch<PosicionValorada[]>(`/usuarios/${usuarioId}/inversion/posiciones`)
}

export interface RegistrarMovimientoInversionInput {
  activoId: number
  tipo: TipoMovimientoInversion
  fecha: string
  cantidad: number
  precio: number
}

export function registrarMovimientoInversion(usuarioId: number, datos: RegistrarMovimientoInversionInput) {
  return apiFetch(`/usuarios/${usuarioId}/inversion/movimientos`, { method: 'POST', body: datos })
}

export function proyeccionInversion(usuarioId: number, anios: number, retornoEsperado: number, aportacionAnual: number) {
  return apiFetch<ProyeccionInversion>(
    `/usuarios/${usuarioId}/inversion/proyeccion?anios=${anios}&retornoEsperado=${retornoEsperado}&aportacionAnual=${aportacionAnual}`,
  )
}
