import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useAuth } from '../auth/AuthContext'
import { historicoSnapshots, proyeccionEfectivo } from '../api/patrimonio'
import { proyeccionInversion } from '../api/inversion'
import { StatCard } from '../components/StatCard'
import { ChartCard } from '../components/ChartCard'
import { formatearFecha, formatearMoneda, formatoTooltipFecha, formatoTooltipMoneda } from '../lib/format'

const HORIZONTES = [1, 3, 5]

export function ResumenPage() {
  const { usuario } = useAuth()
  const usuarioId = usuario!.id
  const [anios, setAnios] = useState(3)

  const snapshotsQuery = useQuery({
    queryKey: ['snapshots', usuarioId],
    queryFn: () => historicoSnapshots(usuarioId),
  })
  const efectivoQuery = useQuery({
    queryKey: ['proyeccion-efectivo', usuarioId, anios],
    queryFn: () => proyeccionEfectivo(usuarioId, anios * 12),
  })
  const inversionQuery = useQuery({
    queryKey: ['proyeccion-inversion', usuarioId, anios],
    queryFn: () => proyeccionInversion(usuarioId, anios, 0.07, 0),
  })

  const cargando = snapshotsQuery.isLoading || efectivoQuery.isLoading || inversionQuery.isLoading

  if (cargando) {
    return <p className="text-sm text-slate-500">Cargando resumen...</p>
  }

  const efectivo = efectivoQuery.data
  const inversion = inversionQuery.data
  const snapshots = snapshotsQuery.data ?? []

  const patrimonioActual = (efectivo?.patrimonioActual ?? 0) + (inversion?.valorActual ?? 0)
  const patrimonioProyectado = (efectivo?.patrimonioProyectado ?? 0) + (inversion?.valorProyectado ?? 0)

  const hoy = new Date()
  const fechaFutura = new Date(hoy)
  fechaFutura.setFullYear(fechaFutura.getFullYear() + anios)

  const datosGrafica = [
    ...[...snapshots]
      .sort((a, b) => a.fecha.localeCompare(b.fecha))
      .map((s) => ({ fecha: s.fecha, historico: s.patrimonioTotal, proyectado: undefined as number | undefined })),
    { fecha: hoy.toISOString().slice(0, 10), historico: patrimonioActual, proyectado: patrimonioActual },
    { fecha: fechaFutura.toISOString().slice(0, 10), historico: undefined, proyectado: patrimonioProyectado },
  ]

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Resumen</h1>
        <p className="text-sm text-slate-500">Patrimonio total y proyección combinada (efectivo + inversión)</p>
      </div>

      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard etiqueta="Patrimonio actual" valor={formatearMoneda(patrimonioActual)} />
        <StatCard etiqueta="Saldo efectivo" valor={formatearMoneda(efectivo?.patrimonioActual ?? 0)} />
        <StatCard etiqueta="Valor cartera" valor={formatearMoneda(inversion?.valorActual ?? 0)} />
        <StatCard
          etiqueta={`Proyectado a ${anios} ${anios === 1 ? 'año' : 'años'}`}
          valor={formatearMoneda(patrimonioProyectado)}
          tono={patrimonioProyectado >= patrimonioActual ? 'positivo' : 'negativo'}
        />
      </div>

      <ChartCard
        titulo="Evolución del patrimonio"
        subtitulo="Tramo histórico (snapshots reales) y tramo proyectado (efectivo lineal + inversión compuesta)"
      >
        <div className="mb-3 flex gap-2">
          {HORIZONTES.map((h) => (
            <button
              key={h}
              onClick={() => setAnios(h)}
              className={`rounded-full px-3 py-1 text-xs font-medium ${
                anios === h ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {h} {h === 1 ? 'año' : 'años'}
            </button>
          ))}
        </div>
        <ResponsiveContainer width="100%" height={320}>
          <LineChart data={datosGrafica}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
            <XAxis dataKey="fecha" tickFormatter={formatearFecha} tick={{ fontSize: 12 }} />
            <YAxis tickFormatter={(v) => formatearMoneda(v)} width={90} tick={{ fontSize: 12 }} />
            <Tooltip labelFormatter={formatoTooltipFecha} formatter={formatoTooltipMoneda} />
            <Legend />
            <Line
              type="monotone"
              dataKey="historico"
              name="Histórico"
              stroke="#2563eb"
              strokeWidth={2}
              dot={{ r: 3 }}
              connectNulls
            />
            <Line
              type="monotone"
              dataKey="proyectado"
              name="Proyectado"
              stroke="#2563eb"
              strokeWidth={2}
              strokeDasharray="6 4"
              dot={{ r: 3 }}
              connectNulls
            />
          </LineChart>
        </ResponsiveContainer>
        {snapshots.length === 0 && (
          <p className="mt-2 text-xs text-slate-400">
            Todavía no hay snapshots guardados: el tramo histórico solo mostrará el punto actual.
          </p>
        )}
      </ChartCard>
    </div>
  )
}
