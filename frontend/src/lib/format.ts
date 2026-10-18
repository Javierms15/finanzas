import type { TipoActivo } from '../api/types'

const formatoMoneda = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR', useGrouping: 'always' })
const formatoNumero = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 })
const formatoFecha = new Intl.DateTimeFormat('es-ES', { day: '2-digit', month: 'short', year: 'numeric' })
const formatoMes = new Intl.DateTimeFormat('es-ES', { month: 'short', year: '2-digit' })
const formatoMesLargo = new Intl.DateTimeFormat('es-ES', { month: 'long', year: 'numeric' })

export function formatearMoneda(valor: number) {
  return formatoMoneda.format(valor)
}

export function formatearMonedaConSigno(valor: number) {
  return `${valor > 0 ? '+' : ''}${formatoMoneda.format(valor)}`
}

export function formatearPorcentajeConSigno(valor: number) {
  return `${valor > 0 ? '+' : ''}${valor.toFixed(1).replace('.', ',')} %`
}

/** 0.0089 -> "0,89 %" */
export function formatearTasa(tasaAnual: number) {
  return `${(tasaAnual * 100).toFixed(2).replace('.', ',')} %`
}

export function formatearNumero(valor: number) {
  return formatoNumero.format(valor)
}

export function formatearFecha(iso: string) {
  return formatoFecha.format(new Date(iso))
}

/** "2026-09" -> "Septiembre de 2026" */
export function formatearMesLargo(mes: string) {
  const texto = formatoMesLargo.format(new Date(`${mes}-01`))
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}

export function formatearMes(mes: string) {
  return formatoMes.format(new Date(`${mes}-01`))
}

// Orden fijo de los 8 tonos categoricos definidos en index.css (--series-N), que
// cambian solos con el tema. Mas de 8 series no se debe pintar con colores
// repetidos: agrupar en "Otros" o dividir en varias graficas.
export function colorParaIndice(indice: number) {
  return `var(--series-${(indice % 8) + 1})`
}

/**
 * Color fijo por tipo de activo: el color sigue a la entidad, no a su posicion en la lista
 * (si no hay cripto, los ETF siguen siendo del mismo color). Lo comparten el donut y las barras.
 */
const COLOR_TIPO: Record<TipoActivo, string> = {
  ETF: 'var(--series-1)',
  ACCION: 'var(--series-2)',
  FONDO: 'var(--series-3)',
  CRIPTO: 'var(--series-4)',
}

export function colorParaTipo(tipo: TipoActivo) {
  return COLOR_TIPO[tipo]
}

export const COLOR_SUBE = 'var(--up)'
export const COLOR_BAJA = 'var(--down)'

/** Props comunes de <Tooltip> de Recharts para que encaje con el tema (tarjeta sobre superficie). */
export const estiloTooltip = {
  contentStyle: {
    background: 'var(--surface-2)',
    border: '1px solid var(--line)',
    borderRadius: 12,
    color: 'var(--ink)',
    boxShadow: '0 8px 24px rgba(0,0,0,0.35)',
    fontSize: 13,
  },
  labelStyle: { color: 'var(--muted)', marginBottom: 4 },
  itemStyle: { color: 'var(--ink)' },
  cursor: { stroke: 'var(--muted)', strokeDasharray: '3 3' },
}

/** Ejes discretos: solo etiquetas en gris, sin linea ni marcas. */
export const estiloEje = {
  tick: { fontSize: 12, fill: 'var(--muted)' },
  axisLine: false,
  tickLine: false,
} as const

// Recharts tipa formatter/labelFormatter de Tooltip de forma muy laxa (ReactNode,
// ValueType | undefined...); estos wrappers aceptan lo que llegue y lo formatean.
export function formatoTooltipMoneda(valor: unknown) {
  return formatearMoneda(Number(valor))
}

export function formatoTooltipFecha(etiqueta: unknown) {
  return formatearFecha(String(etiqueta))
}

export function formatoTooltipMes(etiqueta: unknown) {
  return formatearMes(String(etiqueta))
}
