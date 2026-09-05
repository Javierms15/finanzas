import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useAuth } from '../auth/AuthContext'
import { listarCuentas } from '../api/cuentas'
import { agregacionMensualPorCuenta, listarMovimientos } from '../api/movimientos'
import { listarCategorias } from '../api/categorias'
import { simularAjusteCategoria } from '../api/patrimonio'
import { ChartCard } from '../components/ChartCard'
import { StatCard } from '../components/StatCard'
import {
  colorParaIndice,
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
      if (m.importe >= 0) continue
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
    return <p className="text-sm text-slate-500">Cargando cuentas...</p>
  }

  if (!cuentasQuery.data || cuentasQuery.data.length === 0) {
    return <p className="text-sm text-slate-500">Todavía no tienes ninguna cuenta. Crea una para empezar a importar movimientos.</p>
  }

  const movimientos = movimientosQuery.data ?? []
  const agregacion = agregacionQuery.data ?? []

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Finanzas diarias</h1>
          <p className="text-sm text-slate-500">Movimientos, gasto por categoría y proyección lineal</p>
        </div>
        <select
          className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
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

      <div className="grid grid-cols-3 gap-4">
        <StatCard etiqueta="Movimientos" valor={String(movimientos.length)} />
        <StatCard
          etiqueta="Ingresos (histórico)"
          valor={formatearMoneda(movimientos.filter((m) => m.importe > 0).reduce((s, m) => s + m.importe, 0))}
          tono="positivo"
        />
        <StatCard
          etiqueta="Gastos (histórico)"
          valor={formatearMoneda(Math.abs(movimientos.filter((m) => m.importe < 0).reduce((s, m) => s + m.importe, 0)))}
          tono="negativo"
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <ChartCard titulo="Ingresos vs. gastos por mes">
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={agregacion.map((a) => ({ ...a, gastosAbs: Math.abs(a.totalGastos) }))}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="mes" tickFormatter={formatearMes} tick={{ fontSize: 12 }} />
              <YAxis tickFormatter={(v) => formatearMoneda(v)} width={90} tick={{ fontSize: 12 }} />
              <Tooltip labelFormatter={formatoTooltipMes} formatter={formatoTooltipMoneda} />
              <Legend />
              <Bar dataKey="totalIngresos" name="Ingresos" fill="#16a34a" radius={[4, 4, 0, 0]} />
              <Bar dataKey="gastosAbs" name="Gastos" fill="#dc2626" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard titulo="Gasto por categoría" subtitulo="Sobre todo el histórico de la cuenta">
          {gastoPorCategoria.length === 0 ? (
            <p className="text-sm text-slate-400">Todavía no hay gastos categorizados.</p>
          ) : (
            <ResponsiveContainer width="100%" height={280}>
              <PieChart>
                <Pie data={gastoPorCategoria} dataKey="valor" nameKey="nombre" innerRadius={60} outerRadius={100} paddingAngle={2}>
                  {gastoPorCategoria.map((_, i) => (
                    <Cell key={i} fill={colorParaIndice(i)} />
                  ))}
                </Pie>
                <Tooltip formatter={formatoTooltipMoneda} />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          )}
        </ChartCard>
      </div>

      <ChartCard titulo="Simulador: ¿qué pasaría si ajusto una categoría?" subtitulo="Proyección lineal a 3 meses">
        <div className="mb-4 flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-slate-600">Categoría</span>
            <select
              className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
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
            <span className="text-slate-600">Reducir gasto medio (€/mes)</span>
            <input
              type="number"
              className="w-32 rounded-lg border border-slate-300 px-3 py-2 text-sm"
              value={ajuste}
              onChange={(e) => setAjuste(Number(e.target.value))}
            />
          </label>
          <button
            onClick={ejecutarSimulacion}
            disabled={!categoriaSimulada || simulando}
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
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

      <ChartCard titulo="Movimientos">
        <div className="max-h-96 overflow-y-auto">
          <table className="w-full text-left text-sm">
            <thead className="sticky top-0 bg-white text-xs uppercase text-slate-400">
              <tr>
                <th className="py-2">Fecha</th>
                <th className="py-2">Descripción</th>
                <th className="py-2">Categoría</th>
                <th className="py-2 text-right">Importe</th>
              </tr>
            </thead>
            <tbody>
              {[...movimientos]
                .sort((a, b) => b.fecha.localeCompare(a.fecha))
                .map((m) => (
                  <tr key={m.id} className="border-t border-slate-100">
                    <td className="py-2 text-slate-500">{formatearFecha(m.fecha)}</td>
                    <td className="max-w-md truncate py-2">{m.descripcionOriginal}</td>
                    <td className="py-2 text-slate-500">{m.categoria?.nombre ?? '—'}</td>
                    <td className={`py-2 text-right font-medium ${m.importe >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
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
