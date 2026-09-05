import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Area, AreaChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useAuth } from '../auth/AuthContext'
import { agregacionMensualUsuario, historicoSnapshots } from '../api/patrimonio'
import { ChartCard } from '../components/ChartCard'
import { formatearFecha, formatearMes, formatearMoneda, formatoTooltipFecha, formatoTooltipMes, formatoTooltipMoneda } from '../lib/format'

const RANGOS = [
  { etiqueta: '3 meses', meses: 3 },
  { etiqueta: '6 meses', meses: 6 },
  { etiqueta: '1 año', meses: 12 },
  { etiqueta: 'Todo', meses: null },
]

const VISTAS = [
  { etiqueta: 'Total', clave: 'total' as const },
  { etiqueta: 'Efectivo', clave: 'efectivo' as const },
  { etiqueta: 'Inversión', clave: 'inversion' as const },
]

export function EvolucionHistoricaPage() {
  const { usuario } = useAuth()
  const usuarioId = usuario!.id
  const [rango, setRango] = useState(RANGOS[3])
  const [vista, setVista] = useState(VISTAS[0])

  const snapshotsQuery = useQuery({ queryKey: ['snapshots', usuarioId], queryFn: () => historicoSnapshots(usuarioId) })
  const agregacionQuery = useQuery({
    queryKey: ['agregacion-usuario', usuarioId],
    queryFn: () => agregacionMensualUsuario(usuarioId),
  })

  const datos = useMemo(() => {
    const snapshots = snapshotsQuery.data ?? []
    if (snapshots.length > 0) {
      return snapshots
        .map((s) => ({
          fecha: s.fecha,
          total: s.patrimonioTotal,
          efectivo: s.saldoEfectivoTotal,
          inversion: s.valorCarteraInversion,
        }))
        .sort((a, b) => a.fecha.localeCompare(b.fecha))
    }
    // Sin snapshots todavia: se reconstruye una aproximacion del efectivo a
    // partir del balance mensual acumulado (no incluye inversion, que no tiene
    // historico sin snapshots).
    const agregacion = agregacionQuery.data ?? []
    let acumulado = 0
    return [...agregacion]
      .sort((a, b) => a.mes.localeCompare(b.mes))
      .map((a) => {
        acumulado += a.balance
        return { fecha: `${a.mes}-01`, total: acumulado, efectivo: acumulado, inversion: 0 }
      })
  }, [snapshotsQuery.data, agregacionQuery.data])

  const datosFiltrados = useMemo(() => {
    if (rango.meses === null) return datos
    const limite = new Date()
    limite.setMonth(limite.getMonth() - rango.meses)
    return datos.filter((d) => new Date(d.fecha) >= limite)
  }, [datos, rango])

  const usandoAproximacion = (snapshotsQuery.data ?? []).length === 0

  if (snapshotsQuery.isLoading || agregacionQuery.isLoading) {
    return <p className="text-sm text-slate-500">Cargando evolución histórica...</p>
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Evolución histórica</h1>
        <p className="text-sm text-slate-500">Patrimonio a lo largo del tiempo, por rango y por vista</p>
      </div>

      <ChartCard titulo="Evolución">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex gap-2">
            {RANGOS.map((r) => (
              <button
                key={r.etiqueta}
                onClick={() => setRango(r)}
                className={`rounded-full px-3 py-1 text-xs font-medium ${
                  rango.etiqueta === r.etiqueta ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                {r.etiqueta}
              </button>
            ))}
          </div>
          <div className="flex gap-2">
            {VISTAS.map((v) => (
              <button
                key={v.clave}
                onClick={() => setVista(v)}
                className={`rounded-full px-3 py-1 text-xs font-medium ${
                  vista.clave === v.clave ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                {v.etiqueta}
              </button>
            ))}
          </div>
        </div>

        {datosFiltrados.length === 0 ? (
          <p className="text-sm text-slate-400">No hay datos suficientes todavía para este rango.</p>
        ) : (
          <ResponsiveContainer width="100%" height={340}>
            <AreaChart data={datosFiltrados}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="fecha" tickFormatter={usandoAproximacion ? formatearMes : formatearFecha} tick={{ fontSize: 12 }} />
              <YAxis tickFormatter={(v) => formatearMoneda(v)} width={90} tick={{ fontSize: 12 }} />
              <Tooltip
                labelFormatter={usandoAproximacion ? formatoTooltipMes : formatoTooltipFecha}
                formatter={formatoTooltipMoneda}
              />
              <Legend />
              <Area type="monotone" dataKey={vista.clave} name={vista.etiqueta} stroke="#2563eb" fill="#2563eb" fillOpacity={0.15} />
            </AreaChart>
          </ResponsiveContainer>
        )}

        {usandoAproximacion && (
          <p className="mt-2 text-xs text-amber-600">
            Todavía no hay snapshots de patrimonio guardados: esta gráfica es una aproximación reconstruida a partir del balance
            mensual de movimientos (solo efectivo, no incluye inversión). Genera snapshots periódicamente para un histórico real.
          </p>
        )}
      </ChartCard>
    </div>
  )
}
