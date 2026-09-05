import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { useAuth } from '../auth/AuthContext'
import { listarPosiciones, proyeccionInversion } from '../api/inversion'
import { ChartCard } from '../components/ChartCard'
import { StatCard } from '../components/StatCard'
import { colorParaIndice, formatearMoneda, formatoTooltipMoneda } from '../lib/format'

export function InversionPage() {
  const { usuario } = useAuth()
  const usuarioId = usuario!.id

  const posicionesQuery = useQuery({ queryKey: ['posiciones', usuarioId], queryFn: () => listarPosiciones(usuarioId) })

  const [anios, setAnios] = useState(5)
  const [retorno, setRetorno] = useState(0.07)
  const [aportacion, setAportacion] = useState(1000)

  const proyeccionQuery = useQuery({
    queryKey: ['proyeccion-inversion-detalle', usuarioId, anios, retorno, aportacion],
    queryFn: () => proyeccionInversion(usuarioId, anios, retorno, aportacion),
  })

  const posiciones = posicionesQuery.data ?? []
  const valorTotal = posiciones.reduce((s, p) => s + p.valorActual, 0)
  const rendimientoTotal = posiciones.reduce((s, p) => s + p.rendimiento, 0)

  const proyeccion = proyeccionQuery.data
  const datosProyeccion =
    proyeccion &&
    Array.from({ length: anios + 1 }, (_, i) => {
      const factor = Math.pow(1 + retorno, i)
      const valor = proyeccion.valorActual * factor + aportacion * i
      return { anio: i, valor }
    })

  if (posicionesQuery.isLoading) {
    return <p className="text-sm text-slate-500">Cargando cartera...</p>
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Inversión</h1>
        <p className="text-sm text-slate-500">Cartera valorada con precio de mercado real y proyección compuesta</p>
      </div>

      <div className="grid grid-cols-3 gap-4">
        <StatCard etiqueta="Valor de cartera" valor={formatearMoneda(valorTotal)} />
        <StatCard etiqueta="Posiciones" valor={String(posiciones.length)} />
        <StatCard
          etiqueta="Rendimiento total"
          valor={formatearMoneda(rendimientoTotal)}
          tono={rendimientoTotal >= 0 ? 'positivo' : 'negativo'}
        />
      </div>

      {posiciones.length === 0 ? (
        <p className="text-sm text-slate-500">Todavía no hay posiciones en la cartera.</p>
      ) : (
        <>
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <ChartCard titulo="Distribución de la cartera">
              <ResponsiveContainer width="100%" height={280}>
                <PieChart>
                  <Pie data={posiciones} dataKey="valorActual" nameKey="ticker" innerRadius={60} outerRadius={100} paddingAngle={2}>
                    {posiciones.map((_, i) => (
                      <Cell key={i} fill={colorParaIndice(i)} />
                    ))}
                  </Pie>
                  <Tooltip formatter={formatoTooltipMoneda} />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            </ChartCard>

            <ChartCard titulo="Rendimiento por posición">
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={posiciones} layout="vertical" margin={{ left: 20 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis type="number" tickFormatter={(v) => formatearMoneda(v)} tick={{ fontSize: 12 }} />
                  <YAxis type="category" dataKey="ticker" width={70} tick={{ fontSize: 12 }} />
                  <Tooltip formatter={formatoTooltipMoneda} />
                  <Bar dataKey="rendimiento" radius={[0, 4, 4, 0]}>
                    {posiciones.map((p, i) => (
                      <Cell key={i} fill={p.rendimiento >= 0 ? '#16a34a' : '#dc2626'} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>
          </div>

          <ChartCard titulo="Posiciones">
            <table className="w-full text-left text-sm">
              <thead className="text-xs uppercase text-slate-400">
                <tr>
                  <th className="py-2">Ticker</th>
                  <th className="py-2">Activo</th>
                  <th className="py-2 text-right">Cantidad</th>
                  <th className="py-2 text-right">Precio medio</th>
                  <th className="py-2 text-right">Precio actual</th>
                  <th className="py-2 text-right">Valor</th>
                  <th className="py-2 text-right">Rendimiento</th>
                </tr>
              </thead>
              <tbody>
                {posiciones.map((p) => (
                  <tr key={p.posicionId} className="border-t border-slate-100">
                    <td className="py-2 font-medium">{p.ticker}</td>
                    <td className="py-2 text-slate-500">{p.nombreActivo}</td>
                    <td className="py-2 text-right">{p.cantidad}</td>
                    <td className="py-2 text-right">{formatearMoneda(p.precioMedioCompra)}</td>
                    <td className="py-2 text-right">{formatearMoneda(p.precioActual)}</td>
                    <td className="py-2 text-right font-medium">{formatearMoneda(p.valorActual)}</td>
                    <td className={`py-2 text-right font-medium ${p.rendimiento >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                      {formatearMoneda(p.rendimiento)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </ChartCard>
        </>
      )}

      <ChartCard titulo="Proyección compuesta" subtitulo="valor_actual × (1 + retorno)^años + aportaciones periódicas">
        <div className="mb-4 flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-slate-600">Años</span>
            <input
              type="number"
              min={1}
              className="w-24 rounded-lg border border-slate-300 px-3 py-2 text-sm"
              value={anios}
              onChange={(e) => setAnios(Math.max(1, Number(e.target.value)))}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-slate-600">Retorno esperado anual</span>
            <input
              type="number"
              step="0.01"
              className="w-28 rounded-lg border border-slate-300 px-3 py-2 text-sm"
              value={retorno}
              onChange={(e) => setRetorno(Number(e.target.value))}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-slate-600">Aportación anual (€)</span>
            <input
              type="number"
              className="w-32 rounded-lg border border-slate-300 px-3 py-2 text-sm"
              value={aportacion}
              onChange={(e) => setAportacion(Number(e.target.value))}
            />
          </label>
        </div>

        {proyeccion && datosProyeccion && (
          <>
            <div className="mb-4 grid grid-cols-2 gap-4 md:grid-cols-2">
              <StatCard etiqueta="Valor actual" valor={formatearMoneda(proyeccion.valorActual)} />
              <StatCard etiqueta={`Valor proyectado a ${anios} años`} valor={formatearMoneda(proyeccion.valorProyectado)} tono="positivo" />
            </div>
            <ResponsiveContainer width="100%" height={260}>
              <AreaChart data={datosProyeccion}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="anio" tickFormatter={(v) => `Año ${v}`} tick={{ fontSize: 12 }} />
                <YAxis tickFormatter={(v) => formatearMoneda(v)} width={90} tick={{ fontSize: 12 }} />
                <Tooltip labelFormatter={(v) => `Año ${v}`} formatter={formatoTooltipMoneda} />
                <Area type="monotone" dataKey="valor" name="Valor proyectado" stroke="#7c3aed" fill="#7c3aed" fillOpacity={0.15} />
              </AreaChart>
            </ResponsiveContainer>
          </>
        )}
      </ChartCard>
    </div>
  )
}
