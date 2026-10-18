import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CuentaConSaldoActual, MovimientoPrevisto, PosicionValorada } from '../api/types'
import { normalizar, renderConProveedores } from '../test/utils'
import { ResumenPage } from './ResumenPage'

vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ usuario: { id: 1, nombre: 'Ana', email: 'ana@correo.es' } }) }))
vi.mock('../api/patrimonio', () => ({
  historicoSnapshots: vi.fn(),
  proyeccionEfectivo: vi.fn(),
  rendimientosMensuales: vi.fn(),
}))
vi.mock('../api/inversion', () => ({ listarPosiciones: vi.fn() }))
vi.mock('../api/cuentas', () => ({ listarCuentasConSaldo: vi.fn() }))
vi.mock('../api/previstos', () => ({
  listarPrevistos: vi.fn(),
  crearPrevisto: vi.fn(),
  eliminarPrevisto: vi.fn(),
  marcarPrevistoCumplido: vi.fn(),
}))

import { listarCuentasConSaldo } from '../api/cuentas'
import { listarPosiciones } from '../api/inversion'
import { historicoSnapshots, proyeccionEfectivo, rendimientosMensuales } from '../api/patrimonio'
import { crearPrevisto, eliminarPrevisto, listarPrevistos, marcarPrevistoCumplido } from '../api/previstos'

const cuenta: CuentaConSaldoActual = {
  id: 1, banco: 'ING', tipo: 'NOMINA', alias: 'Principal', moneda: 'EUR', saldoInicial: 0, tasaInteresAnual: 0.05, saldoActual: 1000,
  ultimoMovimiento: '2026-09-30',
}

const etf: PosicionValorada = {
  posicionId: 1, ticker: 'VWCE', nombreActivo: 'Vanguard FTSE All-World', tipo: 'ETF', cantidad: 20, precioMedioCompra: 100,
  precioActual: 100, valorActual: 2000, rendimiento: 0,
}

function prepararDatos(opciones: { cuentas?: CuentaConSaldoActual[]; posiciones?: PosicionValorada[]; previstos?: MovimientoPrevisto[] } = {}) {
  vi.mocked(historicoSnapshots).mockResolvedValue([])
  vi.mocked(rendimientosMensuales).mockResolvedValue([])
  vi.mocked(proyeccionEfectivo).mockImplementation(async (_usuario, meses) => ({
    patrimonioActual: 1000, ingresoMedio: 2000, gastoMedio: 1500, meses, patrimonioProyectado: 1000 + 500 * meses,
  }))
  vi.mocked(listarCuentasConSaldo).mockResolvedValue(opciones.cuentas ?? [cuenta])
  vi.mocked(listarPosiciones).mockResolvedValue(opciones.posiciones ?? [etf])
  vi.mocked(listarPrevistos).mockResolvedValue(opciones.previstos ?? [])
}

/** El valor de una tarjeta es el elemento que sigue a su etiqueta. */
function valorDe(etiqueta: string | RegExp) {
  return normalizar(screen.getByText(etiqueta).nextElementSibling?.textContent)
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('ResumenPage', () => {
  it('sin cuentas ni historial da la bienvenida con los primeros pasos', async () => {
    prepararDatos({ cuentas: [], posiciones: [] })
    renderConProveedores(<ResumenPage />)

    expect(await screen.findByText('Te damos la bienvenida, Ana')).toBeInTheDocument()
    expect(screen.getByText('Primeros pasos')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Importar archivos' })).toHaveAttribute('href', '/importar')
  })

  it('muestra el patrimonio actual: efectivo mas cartera', async () => {
    prepararDatos()
    renderConProveedores(<ResumenPage />)

    await screen.findByText('Resumen · Patrimonio total')
    expect(normalizar(screen.getByText('Resumen · Patrimonio total').nextElementSibling?.textContent)).toBe('3.000,00 €')
    expect(valorDe('Saldo efectivo')).toBe('1.000,00 €')
    expect(valorDe('Valor cartera')).toBe('2.000,00 €')
  })

  it('a 3 años proyecta ahorro lineal mas interes compuesto mas inversion compuesta', async () => {
    prepararDatos()
    renderConProveedores(<ResumenPage />)

    await screen.findByText('Resumen · Patrimonio total')
    // ahorro 500/mes x 36 = 18.000; interes 1.000 x (1,05^3 - 1) = 157,63; inversion 2.000 x (1,07^3 - 1) = 450,09
    expect(valorDe('Ahorro (ingresos de trabajo)')).toBe('18.000,00 €')
    expect(valorDe('Intereses / remuneración')).toBe('157,63 €')
    expect(valorDe('Crecimiento de inversión')).toBe('450,09 €')
    expect(valorDe('Proyectado a 3 años')).toBe('21.607,71 €')
  })

  it('el reparto porcentual solo cuenta lo que suma y los porcentajes cierran en 100', async () => {
    prepararDatos()
    renderConProveedores(<ResumenPage />)

    await screen.findByText('Resumen · Patrimonio total')
    const porcentajes = [
      screen.getByText('Ahorro (ingresos de trabajo)'),
      screen.getByText('Intereses / remuneración'),
      screen.getByText('Crecimiento de inversión'),
    ].map((e) => Number(/(\d+)%/.exec(e.parentElement!.textContent!)![1]))
    expect(porcentajes).toEqual([97, 1, 2])
    expect(screen.getByText('Ninguno pendiente')).toBeInTheDocument()
  })

  it('cambiar el horizonte recalcula la proyeccion', async () => {
    prepararDatos()
    renderConProveedores(<ResumenPage />)
    await screen.findByText('Resumen · Patrimonio total')

    await userEvent.click(screen.getByRole('button', { name: '1 año' }))

    expect(valorDe('Ahorro (ingresos de trabajo)')).toBe('6.000,00 €')
    expect(valorDe('Intereses / remuneración')).toBe('50,00 €')
    expect(valorDe('Crecimiento de inversión')).toBe('140,00 €')
    await waitFor(() => expect(proyeccionEfectivo).toHaveBeenCalledWith(1, 12))
  })

  it('el horizonte libre se limita entre 1 y 360 meses', async () => {
    prepararDatos()
    renderConProveedores(<ResumenPage />)
    await screen.findByText('Resumen · Patrimonio total')
    const campo = screen.getByLabelText('Meses personalizados')

    await userEvent.clear(campo)
    await userEvent.type(campo, '999')
    expect(campo).toHaveValue(360)

    await userEvent.clear(campo)
    expect(campo).toHaveValue(1)
  })

  it('los supuestos de rentabilidad se pueden editar y cambian la inversion', async () => {
    prepararDatos()
    renderConProveedores(<ResumenPage />)
    await screen.findByText('Resumen · Patrimonio total')
    await userEvent.click(screen.getByRole('button', { name: '1 año' }))

    await userEvent.click(screen.getByRole('button', { name: /Ajustar supuestos de crecimiento/ }))
    const campo = within(screen.getByText(/ETFs/).closest('label')!).getByRole('spinbutton')
    expect(campo).toHaveValue(7)
    await userEvent.clear(campo)
    await userEvent.type(campo, '10')

    expect(valorDe('Crecimiento de inversión')).toBe('200,00 €')
    expect(screen.getByText(/no una recomendación de inversión/)).toBeInTheDocument()
  })

  it('muestra el saldo de cada cuenta en «Mis cuentas»', async () => {
    prepararDatos({ cuentas: [cuenta, { ...cuenta, id: 2, alias: 'Segunda', saldoActual: 250, tasaInteresAnual: null }] })
    renderConProveedores(<ResumenPage />)

    await screen.findByText('Mis cuentas')
    expect(normalizar(screen.getByText('Principal').closest('tr')!.lastElementChild?.textContent)).toBe('1.000,00 €')
    expect(normalizar(screen.getByText('Segunda').closest('tr')!.lastElementChild?.textContent)).toBe('250,00 €')
    expect(normalizar(screen.getByText('Total en cuentas').closest('tr')!.lastElementChild?.textContent)).toBe('1.250,00 €')
  })

  it('sin cuentas remuneradas avisa de como configurarlas', async () => {
    prepararDatos({ cuentas: [{ ...cuenta, tasaInteresAnual: null }] })
    renderConProveedores(<ResumenPage />)

    await screen.findByText('Resumen · Patrimonio total')
    expect(screen.getByText('Sin cuentas remuneradas configuradas')).toBeInTheDocument()
    expect(valorDe('Intereses / remuneración')).toBe('0,00 €')
  })

  it('un gasto previsto pendiente resta del crecimiento y se indica aparte', async () => {
    prepararDatos({ previstos: [{ id: 5, fecha: '2020-01-01', importe: -300, descripcion: 'Multa', cumplido: false }] })
    renderConProveedores(<ResumenPage />)

    await screen.findByText('Resumen · Patrimonio total')
    expect(valorDe('Previstos (manuales)')).toBe('-300,00 €')
    expect(screen.getAllByText('Resta del crecimiento')).toHaveLength(1)
  })

  it('un previsto cumplido ya no cuenta en la proyeccion', async () => {
    prepararDatos({ previstos: [{ id: 5, fecha: '2020-01-01', importe: -300, descripcion: 'Multa', cumplido: true }] })
    renderConProveedores(<ResumenPage />)

    await screen.findByText('Resumen · Patrimonio total')
    expect(valorDe('Previstos (manuales)')).toBe('0,00 €')
    expect(screen.getByText('Reactivar')).toBeInTheDocument()
  })

  it('añadir un previsto valida los datos y llama a la API con el signo correcto', async () => {
    prepararDatos()
    vi.mocked(crearPrevisto).mockResolvedValue({ id: 9, fecha: '2030-01-01', importe: -50, descripcion: 'Seguro', cumplido: false })
    renderConProveedores(<ResumenPage />)
    await screen.findByText('Resumen · Patrimonio total')

    await userEvent.click(screen.getByRole('button', { name: 'Añadir' }))
    expect(screen.getByText('Indica una descripción y un importe mayor que 0')).toBeInTheDocument()
    expect(crearPrevisto).not.toHaveBeenCalled()

    await userEvent.type(screen.getByPlaceholderText('Ej.: Me devuelve Juan'), 'Seguro')
    await userEvent.selectOptions(screen.getByLabelText('Tipo'), 'PAGO')
    await userEvent.type(screen.getByLabelText('Importe (€)'), '50')
    await userEvent.click(screen.getByRole('button', { name: 'Añadir' }))

    await waitFor(() => expect(crearPrevisto).toHaveBeenCalledOnce())
    expect(vi.mocked(crearPrevisto).mock.calls[0][0]).toBe(1)
    expect(vi.mocked(crearPrevisto).mock.calls[0][1]).toMatchObject({ descripcion: 'Seguro', importe: -50 })
  })

  it('marcar como cumplido y eliminar llaman a la API', async () => {
    prepararDatos({ previstos: [{ id: 5, fecha: '2030-01-01', importe: 100, descripcion: 'Me devuelve Juan', cumplido: false }] })
    vi.mocked(marcarPrevistoCumplido).mockResolvedValue({ id: 5, fecha: '2030-01-01', importe: 100, descripcion: 'x', cumplido: true })
    vi.mocked(eliminarPrevisto).mockResolvedValue(undefined)
    renderConProveedores(<ResumenPage />)
    await screen.findByText('Me devuelve Juan')

    await userEvent.click(screen.getByRole('button', { name: 'Marcar cumplido' }))
    await waitFor(() => expect(marcarPrevistoCumplido).toHaveBeenCalledWith(1, 5, true))

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }))
    await waitFor(() => expect(eliminarPrevisto).toHaveBeenCalledWith(1, 5))
  })

  it('avisa cuando todavia no hay fotos guardadas del patrimonio', async () => {
    prepararDatos()
    renderConProveedores(<ResumenPage />)

    expect(await screen.findByText(/Todavía no hay snapshots guardados/)).toBeInTheDocument()
  })
})
