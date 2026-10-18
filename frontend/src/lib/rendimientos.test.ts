import { describe, expect, it } from 'vitest'
import type { RendimientoMensual } from '../api/types'
import { agruparRendimientos } from './rendimientos'

const fila = (mes: string, banco: string, intereses: number, retenciones: number, cashback: number): RendimientoMensual => ({
  mes,
  banco,
  intereses,
  retenciones,
  cashback,
})

describe('agruparRendimientos', () => {
  it('sin datos no hay meses ni bancos', () => {
    expect(agruparRendimientos([])).toEqual({ meses: [], bancos: [] })
  })

  it('el interes neto descuenta la retencion y el total suma interes neto y cashback', () => {
    const { meses } = agruparRendimientos([
      fila('2026-09', 'ING', 4.67, -0.89, 0),
      fila('2026-09', 'Trade Republic', 16.65, 0, 8.44),
    ])

    expect(meses).toHaveLength(1)
    expect(meses[0].netoPorBanco.ING).toBeCloseTo(3.78, 8)
    expect(meses[0].brutoPorBanco.ING).toBe(4.67)
    expect(meses[0].retencionPorBanco.ING).toBe(-0.89)
    expect(meses[0].cashback).toBeCloseTo(8.44, 8)
    expect(meses[0].cashbackPorBanco).toEqual({ 'Trade Republic': 8.44 })
    expect(meses[0].total).toBeCloseTo(28.87, 8)
  })

  it('ordena los meses del mas reciente al mas antiguo y los bancos alfabeticamente', () => {
    const { meses, bancos } = agruparRendimientos([
      fila('2026-08', 'Trade Republic', 1, 0, 0),
      fila('2026-10', 'ING', 2, 0, 0),
      fila('2026-09', 'ING', 3, 0, 0),
    ])

    expect(meses.map((m) => m.mes)).toEqual(['2026-10', '2026-09', '2026-08'])
    expect(bancos).toEqual(['ING', 'Trade Republic'])
  })

  it('un banco que solo da cashback no cuenta como banco con intereses', () => {
    const { bancos, meses } = agruparRendimientos([fila('2026-09', 'Trade Republic', 0, 0, 8.44)])
    expect(bancos).toEqual([])
    expect(meses[0].netoPorBanco).toEqual({})
    expect(meses[0].total).toBeCloseTo(8.44, 8)
  })
})
