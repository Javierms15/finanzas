import { screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { RendimientoMensual } from '../api/types'
import { normalizar, renderConProveedores } from '../test/utils'
import { ChartCard } from './ChartCard'
import { RendimientosMensuales } from './RendimientosMensuales'
import { StatCard } from './StatCard'

vi.mock('../api/patrimonio', () => ({ rendimientosMensuales: vi.fn() }))
import { rendimientosMensuales } from '../api/patrimonio'

const fila = (mes: string, banco: string, intereses: number, retenciones: number, cashback: number): RendimientoMensual => ({
  mes,
  banco,
  intereses,
  retenciones,
  cashback,
})

describe('StatCard', () => {
  it('muestra etiqueta, valor y detalle opcional', () => {
    renderConProveedores(<StatCard etiqueta="Saldo" valor="10,00 €" detalle="Algo más" />)

    expect(screen.getByText('Saldo')).toBeInTheDocument()
    expect(screen.getByText('10,00 €')).toBeInTheDocument()
    expect(screen.getByText('Algo más')).toBeInTheDocument()
  })

  it('sin detalle no pinta el apartado', () => {
    renderConProveedores(<StatCard etiqueta="Saldo" valor="1" />)
    expect(screen.queryByText('Algo más')).not.toBeInTheDocument()
  })

  it('el tono cambia el color del valor', () => {
    renderConProveedores(
      <>
        <StatCard etiqueta="A" valor="pos" tono="positivo" />
        <StatCard etiqueta="B" valor="neg" tono="negativo" />
        <StatCard etiqueta="C" valor="neu" />
      </>,
    )

    expect(screen.getByText('pos')).toHaveClass('text-up')
    expect(screen.getByText('neg')).toHaveClass('text-down')
    expect(screen.getByText('neu')).toHaveClass('text-ink')
  })
})

describe('ChartCard', () => {
  it('muestra titulo, subtitulo y contenido', () => {
    renderConProveedores(
      <ChartCard titulo="Mi gráfica" subtitulo="Explicación">
        <p>Contenido</p>
      </ChartCard>,
    )

    expect(screen.getByRole('heading', { name: 'Mi gráfica' })).toBeInTheDocument()
    expect(screen.getByText('Explicación')).toBeInTheDocument()
    expect(screen.getByText('Contenido')).toBeInTheDocument()
  })
})

describe('RendimientosMensuales', () => {
  beforeEach(() => {
    vi.mocked(rendimientosMensuales).mockReset()
  })

  it('sin datos explica como conseguirlos', async () => {
    vi.mocked(rendimientosMensuales).mockResolvedValue([])
    renderConProveedores(<RendimientosMensuales usuarioId={1} />)

    expect(await screen.findByText(/Todavía no hay intereses ni cashback/)).toBeInTheDocument()
    expect(rendimientosMensuales).toHaveBeenCalledWith(1)
  })

  it('destaca el ultimo mes con el interes neto de cada banco, el cashback y el total', async () => {
    vi.mocked(rendimientosMensuales).mockResolvedValue([
      fila('2026-09', 'ING', 4.67, -0.89, 0),
      fila('2026-09', 'Trade Republic', 16.65, 0, 8.44),
    ])
    renderConProveedores(<RendimientosMensuales usuarioId={1} />)

    expect(await screen.findByText('Intereses y cashback · Septiembre de 2026')).toBeInTheDocument()
    expect(screen.getByText('Intereses · ING').nextElementSibling).toHaveTextContent(/^3,78/)
    expect(normalizar(screen.getByText('Intereses · ING').nextElementSibling?.nextElementSibling?.textContent)).toBe(
      '4,67 € brutos − 0,89 € de retención',
    )
    expect(normalizar(screen.getByText('Intereses · Trade Republic').nextElementSibling?.textContent)).toBe('16,65 €')
    expect(normalizar(screen.getByText('Cashback').nextElementSibling?.textContent)).toBe('8,44 €')
    expect(screen.getByText('De Trade Republic')).toBeInTheDocument()
    expect(normalizar(screen.getByText('Total del mes').nextElementSibling?.textContent)).toBe('28,87 €')
  })

  it('con un solo mes no pinta la tabla historica', async () => {
    vi.mocked(rendimientosMensuales).mockResolvedValue([fila('2026-09', 'ING', 4, 0, 0)])
    renderConProveedores(<RendimientosMensuales usuarioId={1} />)

    await screen.findByText(/Intereses y cashback ·/)
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('con varios meses pinta la tabla del mas reciente al mas antiguo y marca lo que falta', async () => {
    vi.mocked(rendimientosMensuales).mockResolvedValue([
      fila('2026-09', 'ING', 4, 0, 0),
      fila('2026-09', 'Trade Republic', 10, 0, 2),
      fila('2026-10', 'ING', 5, -1, 0),
    ])
    renderConProveedores(<RendimientosMensuales usuarioId={1} />)

    expect(await screen.findByText('Intereses y cashback · Octubre de 2026')).toBeInTheDocument()
    expect(screen.getByText('Sin abono este mes')).toBeInTheDocument()

    const filas = screen.getAllByRole('row')
    expect(filas).toHaveLength(3)
    expect(filas[1]).toHaveTextContent('Octubre de 2026')
    expect(filas[2]).toHaveTextContent('Septiembre de 2026')
    expect(screen.getByRole('columnheader', { name: 'Intereses ING' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Intereses Trade Republic' })).toBeInTheDocument()
  })

  it('mientras carga no pinta nada', async () => {
    vi.mocked(rendimientosMensuales).mockReturnValue(new Promise(() => {}))
    const { container } = renderConProveedores(<RendimientosMensuales usuarioId={1} />)

    await waitFor(() => expect(rendimientosMensuales).toHaveBeenCalled())
    expect(container).toBeEmptyDOMElement()
  })
})
