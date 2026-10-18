import { apiFetch } from './client'
import type { CuentaBancaria, CuentaConSaldoActual, TipoCuenta } from './types'

export function listarCuentas(usuarioId: number) {
  return apiFetch<CuentaBancaria[]>(`/usuarios/${usuarioId}/cuentas`)
}

export function listarCuentasConSaldo(usuarioId: number) {
  return apiFetch<CuentaConSaldoActual[]>(`/usuarios/${usuarioId}/cuentas/saldos`)
}

export interface CrearCuentaInput {
  banco: string
  tipo: TipoCuenta
  alias: string
  moneda: string
  saldoInicial?: number
  tasaInteresAnual?: number | null
}

export function crearCuenta(usuarioId: number, datos: CrearCuentaInput) {
  return apiFetch<CuentaBancaria>(`/usuarios/${usuarioId}/cuentas`, { method: 'POST', body: datos })
}

export function actualizarSaldoInicial(usuarioId: number, cuentaId: number, saldoInicial: number) {
  return apiFetch<CuentaBancaria>(`/usuarios/${usuarioId}/cuentas/${cuentaId}/saldo-inicial`, {
    method: 'PATCH',
    body: { saldoInicial },
  })
}

export function actualizarTasaInteres(usuarioId: number, cuentaId: number, tasaInteresAnual: number | null) {
  return apiFetch<CuentaBancaria>(`/usuarios/${usuarioId}/cuentas/${cuentaId}/tasa-interes`, {
    method: 'PATCH',
    body: { tasaInteresAnual },
  })
}
