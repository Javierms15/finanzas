import { describe, expect, it } from 'vitest'
import {
  RETORNOS_DEFECTO,
  crecimientoEn,
  detalleReparto,
  etiquetaMeses,
  mesDeEfecto,
  pasoSerie,
  pasosDeSerie,
  sumaPositivos,
  tasaMensualEquivalente,
  type EntradasCrecimiento,
} from './proyeccion'

const vacio: EntradasCrecimiento = {
  ahorroNetoMensual: 0,
  previstosPendientes: [],
  cuentasRemuneradas: [],
  valorPorTipo: {},
  retornosPct: RETORNOS_DEFECTO,
}

describe('etiquetaMeses', () => {
  it('expresa los multiplos de 12 en años', () => {
    expect(etiquetaMeses(12)).toBe('1 año')
    expect(etiquetaMeses(36)).toBe('3 años')
    expect(etiquetaMeses(120)).toBe('10 años')
    expect(etiquetaMeses(18)).toBe('18 meses')
  })
})

describe('serie de la grafica', () => {
  it('el paso crece con el horizonte', () => {
    expect([1, 24].map(pasoSerie)).toEqual([1, 1])
    expect([25, 60].map(pasoSerie)).toEqual([3, 3])
    expect([61, 360].map(pasoSerie)).toEqual([6, 6])
  })

  it('siempre empieza en 0 y termina exactamente en el horizonte', () => {
    expect(pasosDeSerie(3)).toEqual([0, 1, 2, 3])
    expect(pasosDeSerie(36)).toEqual([0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36])
    expect(pasosDeSerie(26).at(-1)).toBe(26)
    expect(pasosDeSerie(26)[0]).toBe(0)
    expect(pasosDeSerie(120).at(-2)).toBe(114)
  })

  it('nunca repite el ultimo punto', () => {
    for (const meses of [1, 6, 12, 24, 25, 36, 60, 61, 120, 360]) {
      const pasos = pasosDeSerie(meses)
      expect(new Set(pasos).size).toBe(pasos.length)
    }
  })
})

describe('mesDeEfecto', () => {
  const ahora = Date.parse('2026-01-01T00:00:00Z')

  it('los previstos de esta semana cuentan desde el primer mes', () => {
    expect(mesDeEfecto('2026-01-20', ahora)).toBe(1)
  })

  it('los ya vencidos tambien cuentan desde el primer mes', () => {
    expect(mesDeEfecto('2025-06-01', ahora)).toBe(1)
    expect(mesDeEfecto('2026-01-01', ahora)).toBe(1)
  })

  it('uno futuro cuenta a partir del mes en que ocurre', () => {
    expect(mesDeEfecto('2026-03-01', ahora)).toBe(2)
    expect(mesDeEfecto('2027-01-01', ahora)).toBe(12)
  })
})

describe('tasaMensualEquivalente', () => {
  it('compuesta doce veces devuelve exactamente la tasa anual', () => {
    for (const anual of [0.01, 0.0378, 0.07, 0.15]) {
      expect(Math.pow(1 + tasaMensualEquivalente(anual), 12) - 1).toBeCloseTo(anual, 10)
    }
  })

  it('es menor que dividir entre doce', () => {
    expect(tasaMensualEquivalente(0.12)).toBeLessThan(0.01)
    expect(tasaMensualEquivalente(0)).toBe(0)
  })
})

describe('crecimientoEn', () => {
  it('sin nada que crecer todo es cero', () => {
    expect(crecimientoEn(24, vacio)).toEqual({ ahorro: 0, interes: 0, inversion: 0, previsto: 0, total: 0 })
  })

  it('en el mes cero no hay crecimiento aunque haya de todo', () => {
    const e: EntradasCrecimiento = {
      ahorroNetoMensual: 500,
      previstosPendientes: [],
      cuentasRemuneradas: [{ saldoActual: 1000, tasaInteresAnual: 0.05 }],
      valorPorTipo: { ETF: 2000 },
      retornosPct: RETORNOS_DEFECTO,
    }
    expect(crecimientoEn(0, e).total).toBe(0)
  })

  it('el ahorro es lineal', () => {
    expect(crecimientoEn(6, { ...vacio, ahorroNetoMensual: 100 }).ahorro).toBe(600)
    expect(crecimientoEn(6, { ...vacio, ahorroNetoMensual: -50 }).ahorro).toBe(-300)
  })

  it('el interes de una cuenta es compuesto y a un año da la tasa anual', () => {
    const cuentas = [{ saldoActual: 1000, tasaInteresAnual: 0.05 }]
    expect(crecimientoEn(12, { ...vacio, cuentasRemuneradas: cuentas }).interes).toBeCloseTo(50, 8)
    expect(crecimientoEn(36, { ...vacio, cuentasRemuneradas: cuentas }).interes).toBeCloseTo(1000 * (1.05 ** 3 - 1), 8)
  })

  it('la inversion usa la rentabilidad de cada tipo de activo', () => {
    const e: EntradasCrecimiento = {
      ...vacio,
      valorPorTipo: { ETF: 1000, CRIPTO: 100 },
      retornosPct: { ACCION: 8, ETF: 7, FONDO: 5, CRIPTO: 15 },
    }
    expect(crecimientoEn(12, e).inversion).toBeCloseTo(70 + 15, 8)
  })

  it('una rentabilidad editada por el usuario se aplica', () => {
    const e: EntradasCrecimiento = { ...vacio, valorPorTipo: { ETF: 1000 }, retornosPct: { ...RETORNOS_DEFECTO, ETF: 0 } }
    expect(crecimientoEn(12, e).inversion).toBe(0)
  })

  it('los previstos cuentan solo desde su mes', () => {
    const e: EntradasCrecimiento = {
      ...vacio,
      previstosPendientes: [{ importe: -500, mes: 3 }, { importe: 200, mes: 10 }],
    }
    expect(crecimientoEn(2, e).previsto).toBe(0)
    expect(crecimientoEn(3, e).previsto).toBe(-500)
    expect(crecimientoEn(12, e).previsto).toBe(-300)
  })

  it('el total es la suma de los cuatro origenes', () => {
    const e: EntradasCrecimiento = {
      ahorroNetoMensual: 500,
      previstosPendientes: [{ importe: -100, mes: 1 }],
      cuentasRemuneradas: [{ saldoActual: 1000, tasaInteresAnual: 0.05 }],
      valorPorTipo: { ETF: 2000 },
      retornosPct: RETORNOS_DEFECTO,
    }
    const c = crecimientoEn(12, e)
    expect(c.total).toBeCloseTo(c.ahorro + c.interes + c.inversion + c.previsto, 10)
    expect(c.total).toBeCloseTo(6000 + 50 + 140 - 100, 6)
  })
})

describe('reparto porcentual', () => {
  it('suma solo los componentes positivos', () => {
    expect(sumaPositivos({ ahorro: 300, interes: 100, inversion: -50, previsto: 0, total: 350 })).toBe(400)
  })

  it('cada componente positivo es su parte de lo que suma', () => {
    expect(detalleReparto(300, 400)).toBe('75% de lo que suma')
    expect(detalleReparto(100, 400)).toBe('25% de lo que suma')
  })

  it('un componente negativo no recibe porcentaje', () => {
    expect(detalleReparto(-50, 400)).toBe('Resta del crecimiento')
  })

  it('sin nada que repartir no hay texto', () => {
    expect(detalleReparto(0, 400)).toBeUndefined()
    expect(detalleReparto(10, 0)).toBeUndefined()
  })

  it('los porcentajes de los positivos suman 100', () => {
    const c = { ahorro: 333, interes: 333, inversion: 334, previsto: 0, total: 1000 }
    const suma = sumaPositivos(c)
    const total = [c.ahorro, c.interes, c.inversion].reduce((s, v) => s + (v / suma) * 100, 0)
    expect(total).toBeCloseTo(100, 8)
  })
})
