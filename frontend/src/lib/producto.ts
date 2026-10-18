import type { PosicionValorada, TipoActivo } from '../api/types'

// A partir de la primera de estas marcas el nombre del extracto es ruido de
// mercado (clase de accion, divisa, forma juridica...), no el nombre del producto.
const RUIDO =
  /\s+(?:Acciones|Aandelen|Actions|Registered|Reg\.|Shs\b|UCITS|U\.ETF|OPEN END|S\.A\.|N\.V\.|PLC|Inc\.|Corp\.|Co\.,|\(Acc\)|EO\s|DL\s)/i

const ALIAS: Array<[RegExp, string]> = [
  [/^Banco Bilbao Vizcaya Argent\.?$/i, 'BBVA'],
  [/^MUF-/i, ''],
  [/^iShs\s?(?:VII|III|IV|II|I)?-/i, 'iShares '],
  [/Em\.Markts\./i, 'Emerging Markets'],
  [/\s*Softw\.Inc\b/i, ''],
]

/** Nombre legible de un producto ("Banco de Sabadell S.A. Acciones Nom. Serie A EO -,125" -> "Banco de Sabadell"). */
export function nombreCorto(nombreActivo: string): string {
  let base = nombreActivo.replace(/\s*\(ISIN sin resolver\)\s*$/i, '').trim()
  // Oro y plata comparten emisor y nombre: lo unico que los distingue va al final.
  const metal = /(Gold|Silver)\s*$/i.exec(base)?.[1]

  const corte = RUIDO.exec(base)
  if (corte && corte.index > 0) base = base.slice(0, corte.index).trim()
  for (const [patron, reemplazo] of ALIAS) base = base.replace(patron, reemplazo).trim()
  if (metal && !base.toLowerCase().endsWith(metal.toLowerCase())) base = `${base.replace(/\s+Metals$/i, '')} ${metal}`
  return base || nombreActivo
}

export interface ProductoAgrupado {
  producto: string
  tipo: TipoActivo
  valor: number
  rendimiento: number
  pesoPct: number
  posiciones: number
}

/** Una fila por producto (nombre corto), sumando las posiciones que coincidan, de mayor a menor valor. */
export function agruparPorProducto(posiciones: PosicionValorada[]): ProductoAgrupado[] {
  const porProducto = new Map<string, ProductoAgrupado>()
  for (const p of posiciones) {
    const clave = nombreCorto(p.nombreActivo)
    const actual = porProducto.get(clave) ?? { producto: clave, tipo: p.tipo, valor: 0, rendimiento: 0, pesoPct: 0, posiciones: 0 }
    actual.valor += p.valorActual
    actual.rendimiento += p.rendimiento
    actual.posiciones += 1
    porProducto.set(clave, actual)
  }
  const total = [...porProducto.values()].reduce((s, p) => s + p.valor, 0)
  return [...porProducto.values()]
    .map((p) => ({ ...p, pesoPct: total > 0 ? (p.valor / total) * 100 : 0 }))
    .sort((a, b) => b.valor - a.valor)
}
