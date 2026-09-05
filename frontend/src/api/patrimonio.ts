import { apiFetch } from './client'
import type { AgregacionMensual, ProyeccionEfectivo, SimulacionAjusteCategoria, SnapshotPatrimonio } from './types'

export function historicoSnapshots(usuarioId: number) {
  return apiFetch<SnapshotPatrimonio[]>(`/usuarios/${usuarioId}/patrimonio/snapshots`)
}

export function generarSnapshot(usuarioId: number) {
  return apiFetch<SnapshotPatrimonio>(`/usuarios/${usuarioId}/patrimonio/snapshots`, { method: 'POST' })
}

export function agregacionMensualUsuario(usuarioId: number) {
  return apiFetch<AgregacionMensual[]>(`/usuarios/${usuarioId}/patrimonio/agregacion-mensual`)
}

export function proyeccionEfectivo(usuarioId: number, meses: number) {
  return apiFetch<ProyeccionEfectivo>(`/usuarios/${usuarioId}/patrimonio/proyeccion?meses=${meses}`)
}

export function simularAjusteCategoria(usuarioId: number, meses: number, categoriaId: number, ajuste: number) {
  return apiFetch<SimulacionAjusteCategoria>(
    `/usuarios/${usuarioId}/patrimonio/proyeccion/simular?meses=${meses}&categoriaId=${categoriaId}&ajuste=${ajuste}`,
  )
}
