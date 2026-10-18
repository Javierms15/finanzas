import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { rendimientosMensuales } from '../api/patrimonio'
import { formatearMesLargo, formatearMoneda } from '../lib/format'
import { agruparRendimientos } from '../lib/rendimientos'
import { ChartCard } from './ChartCard'
import { StatCard } from './StatCard'

const MESES_EN_TABLA = 12

/**
 * Lo que el dinero parado ha rendido cada mes en cada banco: intereses (netos de retencion) y cashback.
 * Son importes reales de los movimientos importados, no una estimacion.
 */
export function RendimientosMensuales({ usuarioId }: { usuarioId: number }) {
  const consulta = useQuery({ queryKey: ['rendimientos', usuarioId], queryFn: () => rendimientosMensuales(usuarioId) })

  const { meses, bancos } = useMemo(() => agruparRendimientos(consulta.data ?? []), [consulta.data])

  if (consulta.isLoading) return null

  if (meses.length === 0) {
    return (
      <ChartCard titulo="Intereses y cashback" subtitulo="Lo que te abonan los bancos cada mes">
        <p className="text-sm text-muted">
          Todavía no hay intereses ni cashback en tus movimientos. Importa tus extractos y, en Categorías, pulsa «Cargar
          categorías y reglas sugeridas» para que se detecten.
        </p>
      </ChartCard>
    )
  }

  const ultimo = meses[0]
  const nombresCashback = Object.keys(ultimo.cashbackPorBanco).join(', ')

  return (
    <ChartCard
      titulo={`Intereses y cashback · ${formatearMesLargo(ultimo.mes)}`}
      subtitulo="Importes reales abonados en cada mes, según la fecha del movimiento en tu extracto (intereses ya con la retención descontada)"
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {bancos.map((banco) => {
          const neto = ultimo.netoPorBanco[banco]
          const retencion = ultimo.retencionPorBanco[banco] ?? 0
          return (
            <StatCard
              key={banco}
              etiqueta={`Intereses · ${banco}`}
              valor={neto === undefined ? '—' : formatearMoneda(neto)}
              detalle={
                neto === undefined
                  ? 'Sin abono este mes'
                  : retencion !== 0
                    ? `${formatearMoneda(ultimo.brutoPorBanco[banco])} brutos − ${formatearMoneda(Math.abs(retencion))} de retención`
                    : undefined
              }
              tono={neto === undefined ? 'neutro' : 'positivo'}
            />
          )
        })}
        <StatCard
          etiqueta="Cashback"
          valor={ultimo.cashback === 0 ? '—' : formatearMoneda(ultimo.cashback)}
          detalle={nombresCashback ? `De ${nombresCashback}` : 'Sin cashback este mes'}
          tono={ultimo.cashback === 0 ? 'neutro' : 'positivo'}
        />
        <StatCard etiqueta="Total del mes" valor={formatearMoneda(ultimo.total)} tono="positivo" />
      </div>

      {meses.length > 1 && (
        <div className="mt-5 overflow-x-auto">
          <table className="w-full min-w-[520px] text-left text-sm">
            <thead className="text-xs uppercase text-muted">
              <tr>
                <th className="py-2">Mes</th>
                {bancos.map((banco) => (
                  <th key={banco} className="py-2 text-right">
                    Intereses {banco}
                  </th>
                ))}
                <th className="py-2 text-right">Cashback</th>
                <th className="py-2 text-right">Total</th>
              </tr>
            </thead>
            <tbody>
              {meses.slice(0, MESES_EN_TABLA).map((m) => (
                <tr key={m.mes} className="border-t border-line">
                  <td className="py-2">{formatearMesLargo(m.mes)}</td>
                  {bancos.map((banco) => (
                    <td key={banco} className="py-2 text-right text-ink-2">
                      {m.netoPorBanco[banco] === undefined ? '—' : formatearMoneda(m.netoPorBanco[banco])}
                    </td>
                  ))}
                  <td className="py-2 text-right text-ink-2">{m.cashback === 0 ? '—' : formatearMoneda(m.cashback)}</td>
                  <td className="py-2 text-right font-medium text-up">{formatearMoneda(m.total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </ChartCard>
  )
}
