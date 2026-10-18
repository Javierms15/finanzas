import { describe, expect, it } from 'vitest'
import type { PosicionValorada } from '../api/types'
import { agruparPorProducto, nombreCorto } from './producto'

function posicion(nombreActivo: string, valorActual: number, rendimiento = 0, tipo: PosicionValorada['tipo'] = 'ACCION'): PosicionValorada {
  return { posicionId: 1, ticker: 'X', nombreActivo, tipo, cantidad: 1, precioMedioCompra: valorActual, precioActual: valorActual, valorActual, rendimiento }
}

describe('nombreCorto', () => {
  it('quita el ruido de mercado que viene detras del nombre', () => {
    expect(nombreCorto('Banco de Sabadell S.A. Acciones Nom. Serie A EO -,125')).toBe('Banco de Sabadell')
    expect(nombreCorto('Nvidia Corp. Registered Shares DL -,001')).toBe('Nvidia')
  })

  it('aplica los alias conocidos', () => {
    expect(nombreCorto('Banco Bilbao Vizcaya Argent.')).toBe('BBVA')
  })

  it('normaliza los fondos de iShares', () => {
    expect(nombreCorto('iShs VII-Core S&P 500 UCITS ETF USD (Acc)')).toBe('iShares Core S&P 500')
  })

  it('quita la marca de ISIN sin resolver', () => {
    expect(nombreCorto('Producto Raro (ISIN sin resolver)')).toBe('Producto Raro')
  })

  it('un nombre sin ruido se queda como esta', () => {
    expect(nombreCorto('Tesla')).toBe('Tesla')
  })

  it('no devuelve una cadena vacia', () => {
    expect(nombreCorto('MUF-')).toBe('MUF-')
  })
})

describe('agruparPorProducto', () => {
  it('suma las posiciones del mismo producto y las ordena de mayor a menor valor', () => {
    const filas = agruparPorProducto([
      posicion('Apple Inc. Registered Shares', 100, 10),
      posicion('Tesla Inc. Registered Shares', 500, -20),
      posicion('Apple Inc. Acciones', 300, 30),
    ])

    expect(filas.map((f) => f.producto)).toEqual(['Tesla', 'Apple'])
    expect(filas[1]).toMatchObject({ valor: 400, rendimiento: 40, posiciones: 2 })
  })

  it('el peso de cada producto suma 100', () => {
    const filas = agruparPorProducto([posicion('A Corp.', 250), posicion('B Corp.', 750)])
    expect(filas.map((f) => f.pesoPct)).toEqual([75, 25])
  })

  it('una cartera vacia o a cero no divide por cero', () => {
    expect(agruparPorProducto([])).toEqual([])
    expect(agruparPorProducto([posicion('Cero', 0)])[0].pesoPct).toBe(0)
  })
})
