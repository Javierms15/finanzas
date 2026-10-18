const formatoMoneda = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR', useGrouping: 'always' })
const formatoNumero = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 })
const formatoFecha = new Intl.DateTimeFormat('es-ES', { day: '2-digit', month: 'short', year: 'numeric' })
const formatoMes = new Intl.DateTimeFormat('es-ES', { month: 'short', year: '2-digit' })

export function formatearMoneda(valor: number) {
  return formatoMoneda.format(valor)
}

export function formatearMonedaConSigno(valor: number) {
  return `${valor > 0 ? '+' : ''}${formatoMoneda.format(valor)}`
}

export function formatearPorcentajeConSigno(valor: number) {
  return `${valor > 0 ? '+' : ''}${valor.toFixed(1).replace('.', ',')} %`
}

export function formatearNumero(valor: number) {
  return formatoNumero.format(valor)
}

export function formatearFecha(iso: string) {
  return formatoFecha.format(new Date(iso))
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
