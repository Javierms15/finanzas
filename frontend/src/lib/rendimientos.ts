import type { RendimientoMensual } from '../api/types'

export interface MesAgrupado {
  mes: string
  /** Interes neto (ya descontada la retencion) por banco */
  netoPorBanco: Record<string, number>
  brutoPorBanco: Record<string, number>
  retencionPorBanco: Record<string, number>
  cashbackPorBanco: Record<string, number>
  cashback: number
  total: number
}

/**
 * Agrupa las filas (mes, banco) del servidor en una fila por mes, del mas reciente al mas antiguo.
 * `bancos` son solo los que han cobrado intereses alguna vez (el cashback no crea columna de banco).
 */
export function agruparRendimientos(filas: RendimientoMensual[]): { meses: MesAgrupado[]; bancos: string[] } {
  const porMes = new Map<string, MesAgrupado>()
  const conInteres = new Set<string>()
  for (const r of filas) {
    const mes =
      porMes.get(r.mes) ??
      { mes: r.mes, netoPorBanco: {}, brutoPorBanco: {}, retencionPorBanco: {}, cashbackPorBanco: {}, cashback: 0, total: 0 }
    if (r.intereses !== 0 || r.retenciones !== 0) {
      conInteres.add(r.banco)
      mes.brutoPorBanco[r.banco] = r.intereses
      mes.retencionPorBanco[r.banco] = r.retenciones
      mes.netoPorBanco[r.banco] = r.intereses + r.retenciones
    }
    if (r.cashback !== 0) mes.cashbackPorBanco[r.banco] = r.cashback
    mes.cashback += r.cashback
    mes.total += r.intereses + r.retenciones + r.cashback
    porMes.set(r.mes, mes)
  }
  return {
    meses: [...porMes.values()].sort((a, b) => b.mes.localeCompare(a.mes)),
    bancos: [...conInteres].sort(),
  }
}
