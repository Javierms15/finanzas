import { apiFetch } from './client'
import type { Categoria, ReglaCategorizacion, TipoCategoria } from './types'

export function listarCategorias() {
  return apiFetch<Categoria[]>('/categorias')
}

export function crearCategoria(nombre: string, tipo: TipoCategoria, color?: string) {
  return apiFetch<Categoria>('/categorias', { method: 'POST', body: { nombre, tipo, color } })
}

export function recategorizar() {
  return apiFetch<number>('/categorias/recategorizar', { method: 'POST' })
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
