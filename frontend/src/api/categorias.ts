import { apiFetch } from './client'
import type { Categoria, ReglaCategorizacion, ResultadoSugeridas, TipoCategoria } from './types'

export function listarCategorias() {
  return apiFetch<Categoria[]>('/categorias')
}

export function crearCategoria(nombre: string, tipo: TipoCategoria, color?: string) {
  return apiFetch<Categoria>('/categorias', { method: 'POST', body: { nombre, tipo, color } })
}

/** todas=false solo categoriza los que no tienen categoria; true recalcula tambien los automaticos (nunca los manuales). */
export function recategorizar(todas = false) {
  return apiFetch<number>(`/categorias/recategorizar?todas=${todas}`, { method: 'POST' })
}

export function cargarSugeridas(usuarioId: number) {
  return apiFetch<ResultadoSugeridas>(`/categorias/sugeridas?usuarioId=${usuarioId}`, { method: 'POST' })
}

export function eliminarRegla(reglaId: number) {
  return apiFetch<void>(`/reglas-categorizacion/${reglaId}`, { method: 'DELETE' })
}

export function listarReglas() {
  return apiFetch<ReglaCategorizacion[]>('/reglas-categorizacion')
}

export function crearRegla(categoriaId: number, patron: string, prioridad: number) {
  return apiFetch<ReglaCategorizacion>('/reglas-categorizacion', {
    method: 'POST',
    body: { categoriaId, patron, prioridad },
  })
}
