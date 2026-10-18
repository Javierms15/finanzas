import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useAuth } from '../auth/AuthContext'
import { listarCuentas } from '../api/cuentas'
import { agregacionMensualPorCuenta, cambiarCategoria, listarMovimientos } from '../api/movimientos'
import { listarCategorias } from '../api/categorias'
import { simularAjusteCategoria } from '../api/patrimonio'
import { ChartCard } from '../components/ChartCard'
import { StatCard } from '../components/StatCard'
import {
  COLOR_BAJA,
  COLOR_SUBE,
  colorParaIndice,
  estiloEje,
  estiloTooltip,
  formatearFecha,
  formatearMes,
  formatearMoneda,
  formatoTooltipMes,
  formatoTooltipMoneda,
} from '../lib/format'
import type { SimulacionAjusteCategoria } from '../api/types'

export function FinanzasDiariasPage() {
  const { usuario } = useAuth()
  const usuarioId = usuario!.id
  const queryClient = useQueryClient()
  const [soloSinCategoria, setSoloSinCategoria] = useState(false)

  const cuentasQuery = useQuery({ queryKey: ['cuentas', usuarioId], queryFn: () => listarCuentas(usuarioId) })
  const [cuentaId, setCuentaId] = useState<number | null>(null)
  const cuentaSeleccionada = cuentaId ?? cuentasQuery.data?.[0]?.id ?? null

  const movimientosQuery = useQuery({
    queryKey: ['movimientos', cuentaSeleccionada],
    queryFn: () => listarMovimientos(cuentaSeleccionada!),
    enabled: cuentaSeleccionada !== null,
  })
  const agregacionQuery = useQuery({
    queryKey: ['agregacion-cuenta', cuentaSeleccionada],
    queryFn: () => agregacionMensualPorCuenta(cuentaSeleccionada!),
    enabled: cuentaSeleccionada !== null,
  })
  const categoriasQuery = useQuery({ queryKey: ['categorias'], queryFn: listarCategorias })

  const gastoPorCategoria = useMemo(() => {
    const movimientos = movimientosQuery.data ?? []
    const totales = new Map<string, number>()
    for (const m of movimientos) {
      if (m.importe >= 0 || m.categoria?.tipo === 'NEUTRO') continue
      const nombre = m.categoria?.nombre ?? 'Sin categorizar'
      totales.set(nombre, (totales.get(nombre) ?? 0) + Math.abs(m.importe))
    }
    return [...totales.entries()]
      .map(([nombre, valor]) => ({ nombre, valor }))
      .sort((a, b) => b.valor - a.valor)
  }, [movimientosQuery.data])

  const [categoriaSimulada, setCategoriaSimulada] = useState<number | null>(null)
  const [ajuste, setAjuste] = useState(50)
  const [simulacion, setSimulacion] = useState<SimulacionAjusteCategoria | null>(null)
  const [simulando, setSimulando] = useState(false)

  const ejecutarSimulacion = async () => {
    if (!categoriaSimulada) return
    setSimulando(true)
    try {
      setSimulacion(await simularAjusteCategoria(usuarioId, 3, categoriaSimulada, ajuste))
    } finally {
      setSimulando(false)
    }
  }

  if (cuentasQuery.isLoading) {
    return <p className="text-sm text-muted">Cargando cuentas...</p>
  }

  if (!cuentasQuery.data || cuentasQuery.data.length === 0) {
    return <p className="text-sm text-muted">Todavía no tienes ninguna cuenta. Crea una para empezar a importar movimientos.</p>
  }

  const movimientos = movimientosQuery.data ?? []
  const agregacion = agregacionQuery.data ?? []
  const reales = movimientos.filter((m) => m.categoria?.tipo !== 'NEUTRO')
  const neutros = movimientos.length - reales.length
  const sinCategoria = movimientos.filter((m) => m.categoria === null).length
  const visibles = soloSinCategoria ? movimientos.filter((m) => m.categoria === null) : movimientos

  const onCambiarCategoria = async (movimientoId: number, valor: string) => {
    await cambiarCategoria(cuentaSeleccionada!, movimientoId, valor === '' ? null : Number(valor))
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['movimientos', cuentaSeleccionada] }),
      queryClient.invalidateQueries({ queryKey: ['agregacion-cuenta', cuentaSeleccionada] }),
      queryClient.invalidateQueries({ queryKey: ['agregacion-usuario', usuarioId] }),
      queryClient.invalidateQueries({ queryKey: ['proyeccion-efectivo', usuarioId] }),
    ])
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-ink">Finanzas diarias</h1>
          <p className="text-sm text-muted">Movimientos, gasto por categoría y proyección lineal</p>
        </div>
        <select
          className="rounded-xl border border-line px-3 py-2 text-sm"
          value={cuentaSeleccionada ?? ''}
          onChange={(e) => setCuentaId(Number(e.target.value))}
        >
          {cuentasQuery.data.map((c) => (
            <option key={c.id} value={c.id}>
              {c.alias} ({c.banco})
            </option>
          ))}
        </select>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard
          etiqueta="Movimientos"
          valor={String(movimientos.length)}
          detalle={`${sinCategoria} sin categoría · ${neutros} neutros (no cuentan como ingreso/gasto)`}
        />
        <StatCard
          etiqueta="Ingresos (histórico)"
          valor={formatearMoneda(reales.filter((m) => m.importe > 0).reduce((s, m) => s + m.importe, 0))}
          tono="positivo"
        />
        <StatCard
          etiqueta="Gastos (histórico)"
          valor={formatearMoneda(Math.abs(reales.filter((m) => m.importe < 0).reduce((s, m) => s + m.importe, 0)))}
          tono="negativo"
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <ChartCard titulo="Ingresos vs. gastos por mes">
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={agregacion.map((a) => ({ ...a, gastosAbs: Math.abs(a.totalGastos) }))}>
              <CartesianGrid vertical={false} stroke="var(--grid)" />
              <XAxis dataKey="mes" tickFormatter={formatearMes} {...estiloEje} />
              <YAxis tickFormatter={(v) => formatearMoneda(v)} width={90} {...estiloEje} />
              <Tooltip
                labelFormatter={formatoTooltipMes}
                formatter={formatoTooltipMoneda}
                {...estiloTooltip}
                cursor={{ fill: 'var(--surface-2)' }}
              />
              <Legend />
              <Bar dataKey="totalIngresos" name="Ingresos" fill={COLOR_SUBE} radius={[6, 6, 0, 0]} />
              <Bar dataKey="gastosAbs" name="Gastos" fill={COLOR_BAJA} radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard titulo="Gasto por categoría" subtitulo="Sobre todo el histórico de la cuenta">
          {gastoPorCategoria.length === 0 ? (
            <p className="text-sm text-muted">Todavía no hay gastos categorizados.</p>
          ) : (
            <ResponsiveContainer width="100%" height={280}>
              <PieChart>
                <Pie
                  data={gastoPorCategoria}
                  dataKey="valor"
                  nameKey="nombre"
                  innerRadius={60}
                  outerRadius={100}
                  paddingAngle={2}
                  stroke="var(--surface)"
                  strokeWidth={2}
                >
                  {gastoPorCategoria.map((_, i) => (
                    <Cell key={i} fill={colorParaIndice(i)} />
                  ))}
                </Pie>
                <Tooltip formatter={formatoTooltipMoneda} {...estiloTooltip} />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          )}
        </ChartCard>
      </div>

      <ChartCard titulo="Simulador: ¿qué pasaría si ajusto una categoría?" subtitulo="Proyección lineal a 3 meses">
        <div className="mb-4 flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-ink-2">Categoría</span>
            <select
              className="rounded-xl border border-line px-3 py-2 text-sm"
              value={categoriaSimulada ?? ''}
              onChange={(e) => setCategoriaSimulada(Number(e.target.value))}
            >
              <option value="">Selecciona...</option>
              {(categoriasQuery.data ?? []).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nombre}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-ink-2">Reducir gasto medio (€/mes)</span>
            <input
              type="number"
              className="w-32 rounded-xl border border-line px-3 py-2 text-sm"
              value={ajuste}
              onChange={(e) => setAjuste(Number(e.target.value))}
            />
          </label>
          <button
            onClick={ejecutarSimulacion}
            disabled={!categoriaSimulada || simulando}
            className="rounded-xl bg-accent px-4 py-2 text-sm font-medium text-on-accent hover:opacity-90 disabled:opacity-50"
          >
            {simulando ? 'Calculando...' : 'Simular'}
          </button>
        </div>

        {simulacion && (
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard etiqueta="Gasto medio categoría (actual)" valor={formatearMoneda(simulacion.gastoMedioCategoriaActual)} />
            <StatCard etiqueta="Gasto medio categoría (simulado)" valor={formatearMoneda(simulacion.gastoMedioCategoriaSimulado)} />
            <StatCard etiqueta="Proyección actual" valor={formatearMoneda(simulacion.patrimonioProyectadoActual)} />
            <StatCard
              etiqueta="Proyección simulada"
              valor={formatearMoneda(simulacion.patrimonioProyectadoSimulado)}
              tono={simulacion.patrimonioProyectadoSimulado >= simulacion.patrimonioProyectadoActual ? 'positivo' : 'negativo'}
            />
          </div>
        )}
      </ChartCard>

      <ChartCard titulo="Movimientos" subtitulo="Cambia la categoría de un movimiento desde su desplegable; lo que elijas a mano no lo toca la recategorización automática">
        <label className="mb-3 flex items-center gap-2 text-sm text-ink-2">
          <input type="checkbox" checked={soloSinCategoria} onChange={(e) => setSoloSinCategoria(e.target.checked)} />
          Solo sin categoría ({sinCategoria})
        </label>
        <div className="max-h-96 overflow-auto">
          <table className="w-full min-w-[480px] text-left text-sm">
            <thead className="sticky top-0 bg-surface text-xs uppercase text-muted">
              <tr>
                <th className="py-2">Fecha</th>
                <th className="py-2">Descripción</th>
                <th className="py-2">Categoría</th>
                <th className="py-2 text-right">Importe</th>
              </tr>
            </thead>
            <tbody>
              {[...visibles]
                .sort((a, b) => b.fecha.localeCompare(a.fecha))
                .map((m) => (
                  <tr key={m.id} className="border-t border-line">
                    <td className="py-2 text-muted">{formatearFecha(m.fecha)}</td>
                    <td className="max-w-md truncate py-2">{m.descripcionOriginal}</td>
                    <td className="py-2">
                      <select
                        className="max-w-[11rem] rounded-xl border border-line px-2 py-1 text-xs"
                        value={m.categoria?.id ?? ''}
                        onChange={(e) => onCambiarCategoria(m.id, e.target.value)}
                        title={m.categoriaManual ? 'Elegida a mano' : 'Automática'}
                      >
                        <option value="">Sin categoría</option>
                        {(categoriasQuery.data ?? []).map((c) => (
                          <option key={c.id} value={c.id}>
                            {c.nombre}
                          </option>
                        ))}
                      </select>
                      {m.categoriaManual && <span className="ml-1 text-xs text-muted">✎</span>}
                    </td>
                    <td className={`py-2 text-right font-medium ${m.importe >= 0 ? 'text-up' : 'text-down'}`}>
                      {formatearMoneda(m.importe)}
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      </ChartCard>
    </div>
  )
}
