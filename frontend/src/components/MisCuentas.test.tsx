import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { CuentaConSaldoActual } from '../api/types'
import { normalizar, renderConProveedores } from '../test/utils'
import { MisCuentas } from './MisCuentas'

const base: CuentaConSaldoActual = {
  id: 1, banco: 'ING', tipo: 'NOMINA', alias: 'ING Nomina', moneda: 'EUR', saldoInicial: 545.81, tasaInteresAnual: null,
  saldoActual: 672.49, ultimoMovimiento: '2026-09-30',
}
const naranja: CuentaConSaldoActual = {
  ...base, id: 2, tipo: 'AHORRO', alias: 'ING Naranja', saldoInicial: 5524.98, tasaInteresAnual: 0.0089, saldoActual: 5528.76,
}

function fila(alias: string) {
  return screen.getByText(alias).closest('tr')!
}

describe('MisCuentas', () => {
  it('no pinta nada si no hay cuentas', () => {
    const { container } = renderConProveedores(<MisCuentas cuentas={[]} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('muestra una fila por cuenta con banco, tipo y saldo actual', () => {
    renderConProveedores(<MisCuentas cuentas={[base, naranja]} />)

    expect(within(fila('ING Nomina')).getByText('Nómina')).toBeInTheDocument()
    expect(normalizar(fila('ING Nomina').lastElementChild?.textContent)).toBe('672,49 €')
    expect(within(fila('ING Naranja')).getByText('Ahorro')).toBeInTheDocument()
    expect(normalizar(fila('ING Naranja').lastElementChild?.textContent)).toBe('5.528,76 €')
  })

  it('el interes solo aparece en la cuenta remunerada', () => {
    renderConProveedores(<MisCuentas cuentas={[base, naranja]} />)

    expect(within(fila('ING Naranja')).getByText('0,89 %')).toBeInTheDocument()
    expect(within(fila('ING Nomina')).getByText('—')).toBeInTheDocument()
  })

  it('indica hasta que fecha llegan los datos de cada cuenta', () => {
    renderConProveedores(<MisCuentas cuentas={[base, { ...naranja, ultimoMovimiento: null }]} />)

    expect(normalizar(within(fila('ING Nomina')).getByText(/2026/).textContent)).toMatch(/^30 sept?\.? 2026$/)
    expect(within(fila('ING Naranja')).getByText('Sin movimientos')).toBeInTheDocument()
  })

  it('el total suma los saldos de todas las cuentas', () => {
    renderConProveedores(<MisCuentas cuentas={[base, naranja]} />)

    const total = screen.getByText('Total en cuentas').closest('tr')!
    expect(normalizar(total.lastElementChild?.textContent)).toBe('6.201,25 €')
  })
})
