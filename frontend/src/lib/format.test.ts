import { describe, expect, it } from 'vitest'
import {
  colorParaIndice,
  colorParaTipo,
  formatearFecha,
  formatearMes,
  formatearMesLargo,
  formatearMoneda,
  formatearMonedaConSigno,
  formatearNumero,
  formatearPorcentajeConSigno,
  formatoTooltipFecha,
  formatoTooltipMes,
  formatoTooltipMoneda,
} from './format'
import { normalizar } from '../test/utils'

describe('formatearMoneda', () => {
  it('usa coma decimal, punto de millares y el simbolo del euro', () => {
    expect(normalizar(formatearMoneda(1234.5))).toBe('1.234,50 €')
    expect(normalizar(formatearMoneda(0))).toBe('0,00 €')
    expect(normalizar(formatearMoneda(-23.456))).toBe('-23,46 €')
  })

  it('agrupa los millares tambien en cifras de cuatro digitos', () => {
    expect(normalizar(formatearMoneda(1000))).toBe('1.000,00 €')
    expect(normalizar(formatearMoneda(1234567.89))).toBe('1.234.567,89 €')
  })

  it('anade el signo mas solo a los positivos', () => {
    expect(normalizar(formatearMonedaConSigno(10))).toBe('+10,00 €')
    expect(normalizar(formatearMonedaConSigno(-10))).toBe('-10,00 €')
    expect(normalizar(formatearMonedaConSigno(0))).toBe('0,00 €')
  })

  it('el formateador del tooltip acepta cualquier tipo que le pase Recharts', () => {
    expect(normalizar(formatoTooltipMoneda('12.5'))).toBe('12,50 €')
    expect(normalizar(formatoTooltipMoneda(3))).toBe('3,00 €')
  })
})

describe('formatearPorcentajeConSigno', () => {
  it('usa una coma decimal y un decimal', () => {
    expect(formatearPorcentajeConSigno(12.5)).toBe('+12,5 %')
    expect(formatearPorcentajeConSigno(-3)).toBe('-3,0 %')
    expect(formatearPorcentajeConSigno(0)).toBe('0,0 %')
  })
})

describe('formatearNumero', () => {
  it('limita a dos decimales', () => {
    expect(formatearNumero(1.23456)).toBe('1,23')
    expect(formatearNumero(10)).toBe('10')
  })
})

describe('fechas y meses', () => {
  it('formatea una fecha ISO en castellano', () => {
    expect(normalizar(formatearFecha('2026-03-05'))).toMatch(/^05 mar\.? 2026$/)
    expect(formatoTooltipFecha('2026-03-05')).toBe(formatearFecha('2026-03-05'))
  })

  it('el mes largo lleva mayuscula inicial', () => {
    expect(formatearMesLargo('2026-09')).toBe('Septiembre de 2026')
    expect(formatearMesLargo('2026-01')).toBe('Enero de 2026')
  })

  it('el mes corto incluye el año con dos cifras', () => {
    expect(formatearMes('2026-09')).toMatch(/^sep/i)
    expect(formatearMes('2026-09')).toMatch(/26$/)
    expect(formatoTooltipMes('2026-09')).toBe(formatearMes('2026-09'))
  })
})

describe('colores', () => {
  it('los indices recorren los 8 tonos y vuelven a empezar', () => {
    expect(colorParaIndice(0)).toBe('var(--series-1)')
    expect(colorParaIndice(7)).toBe('var(--series-8)')
    expect(colorParaIndice(8)).toBe('var(--series-1)')
  })

  it('cada tipo de activo tiene un color fijo y distinto', () => {
    const colores = (['ETF', 'ACCION', 'FONDO', 'CRIPTO'] as const).map(colorParaTipo)
    expect(new Set(colores).size).toBe(4)
    expect(colorParaTipo('ETF')).toBe('var(--series-1)')
  })
})
