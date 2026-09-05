const formatoMoneda = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' })
const formatoNumero = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 })
const formatoFecha = new Intl.DateTimeFormat('es-ES', { day: '2-digit', month: 'short', year: 'numeric' })
const formatoMes = new Intl.DateTimeFormat('es-ES', { month: 'short', year: '2-digit' })

export function formatearMoneda(valor: number) {
  return formatoMoneda.format(valor)
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

const PALETA = ['#2563eb', '#16a34a', '#f59e0b', '#dc2626', '#7c3aed', '#0891b2', '#db2777', '#65a30d', '#ea580c', '#4338ca']

export function colorParaIndice(indice: number) {
  return PALETA[indice % PALETA.length]
}

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
