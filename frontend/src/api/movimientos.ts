import { apiFetch } from './client'
import type { AgregacionMensual, Movimiento, ResultadoImportacion } from './types'

export function listarMovimientos(cuentaId: number) {
  return apiFetch<Movimiento[]>(`/cuentas/${cuentaId}/movimientos`)
}

export function agregacionMensualPorCuenta(cuentaId: number) {
  return apiFetch<AgregacionMensual[]>(`/cuentas/${cuentaId}/movimientos/agregacion-mensual`)
}

/** categoriaId null = quitar la categoria manual y volver a la automatica. */
export function cambiarCategoria(cuentaId: number, movimientoId: number, categoriaId: number | null) {
  return apiFetch<Movimiento>(`/cuentas/${cuentaId}/movimientos/${movimientoId}/categoria`, {
    method: 'PATCH',
    body: { categoriaId },
  })
}

export function importarFichero(cuentaId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiFetch<ResultadoImportacion>(`/cuentas/${cuentaId}/importar`, {
    method: 'POST',
    body: formData,
    isFormData: true,
  })
}
