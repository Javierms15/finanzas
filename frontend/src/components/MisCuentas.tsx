import type { CuentaConSaldoActual, TipoCuenta } from '../api/types'
import { formatearFecha, formatearMoneda, formatearTasa } from '../lib/format'
import { ChartCard } from './ChartCard'

const ETIQUETA_TIPO: Record<TipoCuenta, string> = {
  NOMINA: 'Nómina',
  TARJETA: 'Tarjeta',
  AHORRO: 'Ahorro',
}

/** Saldo actual de cada cuenta: lo que se ve aqui es saldo inicial + movimientos importados hasta la fecha indicada. */
export function MisCuentas({ cuentas }: { cuentas: CuentaConSaldoActual[] }) {
  if (cuentas.length === 0) return null

  const total = cuentas.reduce((suma, c) => suma + c.saldoActual, 0)

  return (
    <ChartCard
      titulo="Mis cuentas"
      subtitulo="Saldo inicial más los movimientos importados hasta la fecha indicada. Si no coincide con tu banco, importa el extracto más reciente o revisa el saldo inicial."
    >
      <div className="overflow-x-auto">
        <table className="w-full min-w-[560px] text-left text-sm">
          <thead className="text-xs uppercase text-muted">
            <tr>
              <th className="py-2">Cuenta</th>
              <th className="py-2">Tipo</th>
              <th className="py-2 text-right">Interés</th>
              <th className="py-2 text-right">Datos hasta</th>
              <th className="py-2 text-right">Saldo</th>
            </tr>
          </thead>
          <tbody>
            {cuentas.map((c) => (
              <tr key={c.id} className="border-t border-line">
                <td className="py-2">
                  <div className="font-medium text-ink">{c.alias}</div>
                  <div className="text-xs text-muted">{c.banco}</div>
                </td>
                <td className="py-2 text-ink-2">{ETIQUETA_TIPO[c.tipo]}</td>
                <td className="py-2 text-right text-ink-2">
                  {c.tasaInteresAnual != null && c.tasaInteresAnual > 0 ? formatearTasa(c.tasaInteresAnual) : '—'}
                </td>
                <td className="py-2 text-right text-ink-2">{c.ultimoMovimiento ? formatearFecha(c.ultimoMovimiento) : 'Sin movimientos'}</td>
                <td className="py-2 text-right font-semibold text-ink">{formatearMoneda(c.saldoActual)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t border-line">
              <td className="py-2 font-medium text-ink" colSpan={4}>
                Total en cuentas
              </td>
              <td className="py-2 text-right font-semibold text-ink">{formatearMoneda(total)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </ChartCard>
  )
}
