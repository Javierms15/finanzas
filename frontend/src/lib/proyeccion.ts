import type { CuentaConSaldoActual, TipoActivo } from '../api/types'

/** Rentabilidad anual esperada por defecto (%) de cada tipo de activo; el usuario puede ajustarla en el Resumen. */
export const RETORNOS_DEFECTO: Record<TipoActivo, number> = { ACCION: 8, ETF: 7, FONDO: 5, CRIPTO: 15 }

const DIAS_POR_MES = 30.4375

export function etiquetaMeses(meses: number) {
  if (meses % 12 === 0) {
    const anios = meses / 12
    return `${anios} ${anios === 1 ? 'año' : 'años'}`
  }
  return `${meses} meses`
}

/** Cada cuantos meses se dibuja un punto de la serie: mas espaciado cuanto mas largo es el horizonte. */
export function pasoSerie(meses: number) {
  if (meses <= 24) return 1
  if (meses <= 60) return 3
  return 6
}

/** Meses 0, paso, 2*paso... y siempre el ultimo, para que la serie termine exactamente en el horizonte. */
export function pasosDeSerie(meses: number) {
  const paso = pasoSerie(meses)
  const valores: number[] = []
  for (let t = 0; t < meses; t += paso) valores.push(t)
  valores.push(meses)
  return valores
}

/** Mes (>= 1) a partir del cual cuenta un movimiento previsto; los ya vencidos cuentan desde el mes 1. */
export function mesDeEfecto(fechaIso: string, ahora: number = Date.now()) {
  const dias = (new Date(fechaIso).getTime() - ahora) / 86_400_000
  return Math.max(1, Math.ceil(dias / DIAS_POR_MES))
}

/** Tasa mensual equivalente a una tasa anual compuesta (no tasa/12 lineal). */
export function tasaMensualEquivalente(tasaAnual: number) {
  return Math.pow(1 + tasaAnual, 1 / 12) - 1
}

export interface PrevistoPendiente {
  importe: number
  /** Mes (>= 1) en el que empieza a contar */
  mes: number
}

export interface EntradasCrecimiento {
  ahorroNetoMensual: number
  previstosPendientes: PrevistoPendiente[]
  /** Solo las cuentas remuneradas (tasa > 0) */
  cuentasRemuneradas: Array<Pick<CuentaConSaldoActual, 'saldoActual' | 'tasaInteresAnual'>>
  valorPorTipo: Partial<Record<TipoActivo, number>>
  /** Rentabilidad anual esperada por tipo, en % */
  retornosPct: Record<TipoActivo, number>
}

export interface Crecimiento {
  ahorro: number
  interes: number
  inversion: number
  previsto: number
  total: number
}

/** Cuanto crece el patrimonio en `t` meses, separado por origen: ahorro, intereses, inversion y previstos. */
export function crecimientoEn(t: number, e: EntradasCrecimiento): Crecimiento {
  const ahorro = e.ahorroNetoMensual * t
  const previsto = e.previstosPendientes.reduce((s, p) => (p.mes <= t ? s + p.importe : s), 0)
  const interes = e.cuentasRemuneradas.reduce((s, c) => {
    const tasaMensual = tasaMensualEquivalente(c.tasaInteresAnual as number)
    return s + c.saldoActual * (Math.pow(1 + tasaMensual, t) - 1)
  }, 0)
  const inversion = (Object.keys(e.valorPorTipo) as TipoActivo[]).reduce((s, tipo) => {
    const valor = e.valorPorTipo[tipo] ?? 0
    const tasaMensual = tasaMensualEquivalente((e.retornosPct[tipo] ?? 0) / 100)
    return s + valor * (Math.pow(1 + tasaMensual, t) - 1)
  }, 0)
  return { ahorro, interes, inversion, previsto, total: ahorro + interes + inversion + previsto }
}

/** Suma solo de los componentes positivos: el % se reparte sobre lo que suma, no sobre el neto. */
export function sumaPositivos(c: Crecimiento) {
  return [c.ahorro, c.interes, c.inversion, c.previsto].filter((v) => v > 0).reduce((s, v) => s + v, 0)
}

/** Texto bajo cada componente del desglose: su porcentaje, o "Resta" si es negativo. */
export function detalleReparto(valor: number, suma: number) {
  if (valor < 0) return 'Resta del crecimiento'
  if (valor > 0 && suma > 0) return `${((valor / suma) * 100).toFixed(0)}% de lo que suma`
  return undefined
}
