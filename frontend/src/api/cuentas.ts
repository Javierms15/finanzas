import { apiFetch } from './client'
import type { CuentaBancaria, TipoCuenta } from './types'

export function listarCuentas(usuarioId: number) {
  return apiFetch<CuentaBancaria[]>(`/usuarios/${usuarioId}/cuentas`)
}

export interface CrearCuentaInput {
  banco: string
  tipo: TipoCuenta
  alias: string
  moneda: string
  saldoInicial?: number
}

export function crearCuenta(usuarioId: number, datos: CrearCuentaInput) {
  return apiFetch<CuentaBancaria>(`/usuarios/${usuarioId}/cuentas`, { method: 'POST', body: datos })
}
