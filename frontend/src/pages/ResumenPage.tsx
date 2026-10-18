import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useAuth } from '../auth/AuthContext'
import { historicoSnapshots, proyeccionEfectivo } from '../api/patrimonio'
import { listarPosiciones } from '../api/inversion'
import { listarCuentasConSaldo } from '../api/cuentas'
import { crearPrevisto, eliminarPrevisto, listarPrevistos, marcarPrevistoCumplido } from '../api/previstos'
import { ApiError } from '../api/client'
import { StatCard } from '../components/StatCard'
import { ChartCard } from '../components/ChartCard'
import {
  COLOR_BAJA,
  COLOR_SUBE,
  estiloEje,
  estiloTooltip,
  formatearFecha,
  formatearMoneda,
  formatearMonedaConSigno,
  formatearPorcentajeConSigno,
  formatoTooltipFecha,
  formatoTooltipMoneda,
} from '../lib/format'
import type { TipoActivo } from '../api/types'

const PRESETS_MESES = [6, 12, 36, 60, 120]

const ETIQUETA_TIPO: Record<TipoActivo, string> = {
  ACCION: 'Acciones',
  ETF: 'ETFs',
  FONDO: 'Fondos',
  CRIPTO: 'Cripto',
}

const RETORNOS_DEFECTO: Record<TipoActivo, number> = { ACCION: 8, ETF: 7, FONDO: 5, CRIPTO: 15 }

function etiquetaMeses(meses: number) {
  if (meses % 12 === 0) {
    const anios = meses / 12
    return `${anios} ${anios === 1 ? 'año' : 'años'}`
  }
  return `${meses} meses`
}

function pasoSerie(meses: number) {
  if (meses <= 24) return 1
  if (meses <= 60) return 3
  return 6
}

/** Mes (>= 1) a partir del cual cuenta un movimiento previsto; los ya vencidos cuentan desde el mes 1. */
function mesDeEfecto(fechaIso: string) {
  const dias = (new Date(fechaIso).getTime() - Date.now()) / 86_400_000
  return Math.max(1, Math.ceil(dias / 30.4375))
}

function fechaPorDefecto() {
  const f = new Date()
  f.setMonth(f.getMonth() + 1)
  return f.toISOString().slice(0, 10)
}

/** Tasa mensual equivalente a una tasa anual compuesta (no tasa/12 lineal). */
function tasaMensualEquivalente(tasaAnual: number) {
  return Math.pow(1 + tasaAnual, 1 / 12) - 1
}

export function ResumenPage() {
  const { usuario } = useAuth()
  const usuarioId = usuario!.id
  const [meses, setMeses] = useState(36)
  const [mostrarSupuestos, setMostrarSupuestos] = useState(false)
  const [retornosPct, setRetornosPct] = useState<Record<TipoActivo, number>>(RETORNOS_DEFECTO)
  const queryClient = useQueryClient()
  const [nuevoPrevisto, setNuevoPrevisto] = useState({
    descripcion: '',
    importe: '' as number | '',
    tipo: 'INGRESO' as 'INGRESO' | 'PAGO',
    fecha: fechaPorDefecto(),
  })
  const [guardandoPrevisto, setGuardandoPrevisto] = useState(false)
  const [errorPrevisto, setErrorPrevisto] = useState<string | null>(null)

  const snapshotsQuery = useQuery({
    queryKey: ['snapshots', usuarioId],
    queryFn: () => historicoSnapshots(usuarioId),
  })
  const efectivoQuery = useQuery({
    queryKey: ['proyeccion-efectivo', usuarioId, meses],
    queryFn: () => proyeccionEfectivo(usuarioId, meses),
  })
  const cuentasQuery = useQuery({
    queryKey: ['cuentas-saldo', usuarioId],
    queryFn: () => listarCuentasConSaldo(usuarioId),
  })
  const posicionesQuery = useQuery({
    queryKey: ['posiciones', usuarioId],
    queryFn: () => listarPosiciones(usuarioId),
  })

  const previstosQuery = useQuery({
    queryKey: ['previstos', usuarioId],
    queryFn: () => listarPrevistos(usuarioId),
  })

  const cargando =
    snapshotsQuery.isLoading ||
    efectivoQuery.isLoading ||
    cuentasQuery.isLoading ||
    posicionesQuery.isLoading ||
    previstosQuery.isLoading

  const efectivo = efectivoQuery.data
  const cuentas = useMemo(() => cuentasQuery.data ?? [], [cuentasQuery.data])
  const posiciones = useMemo(() => posicionesQuery.data ?? [], [posicionesQuery.data])
  const snapshots = snapshotsQuery.data ?? []

  const valorInversionActual = posiciones.reduce((s, p) => s + p.valorActual, 0)
  const patrimonioActual = (efectivo?.patrimonioActual ?? 0) + valorInversionActual
  const ahorroNetoMensual = (efectivo?.ingresoMedio ?? 0) - (efectivo?.gastoMedio ?? 0)

  const cuentasRemuneradas = useMemo(
    () => cuentas.filter((c) => c.tasaInteresAnual != null && c.tasaInteresAnual > 0),
    [cuentas],
  )

  const valorPorTipo = useMemo(() => {
    const acc: Record<string, number> = {}
    for (const p of posiciones) {
      acc[p.tipo] = (acc[p.tipo] ?? 0) + p.valorActual
    }
    return acc as Partial<Record<TipoActivo, number>>
  }, [posiciones])

  const previstos = useMemo(() => previstosQuery.data ?? [], [previstosQuery.data])
  const previstosPendientes = useMemo(
    () => previstos.filter((p) => !p.cumplido).map((p) => ({ importe: p.importe, mes: mesDeEfecto(p.fecha) })),
    [previstos],
  )

  const crecimiento = useMemo(
    () => (t: number) => {
      const ahorro = ahorroNetoMensual * t
      const previsto = previstosPendientes.reduce((s, p) => (p.mes <= t ? s + p.importe : s), 0)
      const interes = cuentasRemuneradas.reduce((s, c) => {
        const tasaMensual = tasaMensualEquivalente(c.tasaInteresAnual as number)
        return s + c.saldoActual * (Math.pow(1 + tasaMensual, t) - 1)
      }, 0)
      const inversion = (Object.keys(valorPorTipo) as TipoActivo[]).reduce((s, tipo) => {
        const valor = valorPorTipo[tipo] ?? 0
        const tasaMensual = tasaMensualEquivalente((retornosPct[tipo] ?? 0) / 100)
        return s + valor * (Math.pow(1 + tasaMensual, t) - 1)
      }, 0)
      return { ahorro, interes, inversion, previsto, total: ahorro + interes + inversion + previsto }
    },
    [ahorroNetoMensual, cuentasRemuneradas, valorPorTipo, retornosPct, previstosPendientes],
  )

  const onCrearPrevisto = async () => {
    if (!nuevoPrevisto.descripcion.trim() || nuevoPrevisto.importe === '' || nuevoPrevisto.importe <= 0) {
      setErrorPrevisto('Indica una descripción y un importe mayor que 0')
      return
    }
    setGuardandoPrevisto(true)
    setErrorPrevisto(null)
    try {
      await crearPrevisto(usuarioId, {
        descripcion: nuevoPrevisto.descripcion,
        fecha: nuevoPrevisto.fecha,
        importe: nuevoPrevisto.tipo === 'PAGO' ? -nuevoPrevisto.importe : nuevoPrevisto.importe,
      })
      await queryClient.invalidateQueries({ queryKey: ['previstos', usuarioId] })
      setNuevoPrevisto((n) => ({ ...n, descripcion: '', importe: '' }))
    } catch (e) {
      setErrorPrevisto(e instanceof ApiError ? e.message : 'No se ha podido guardar')
    } finally {
      setGuardandoPrevisto(false)
    }
  }

  const onCambiarCumplido = async (id: number, cumplido: boolean) => {
    await marcarPrevistoCumplido(usuarioId, id, cumplido)
    await queryClient.invalidateQueries({ queryKey: ['previstos', usuarioId] })
  }

  const onEliminarPrevisto = async (id: number) => {
    await eliminarPrevisto(usuarioId, id)
    await queryClient.invalidateQueries({ queryKey: ['previstos', usuarioId] })
  }

  const hoy = new Date()
  const paso = pasoSerie(meses)
  const pasos = useMemo(() => {
    const valores: number[] = []
    for (let t = 0; t < meses; t += paso) valores.push(t)
    valores.push(meses)
    return valores
  }, [meses, paso])

  const serieProyectada = pasos.map((t) => {
    const fecha = new Date(hoy)
    fecha.setMonth(fecha.getMonth() + t)
    const c = crecimiento(t)
    return { fecha: fecha.toISOString().slice(0, 10), proyectado: patrimonioActual + c.total }
  })

  const desgloseFinal = crecimiento(meses)
  const patrimonioProyectado = patrimonioActual + desgloseFinal.total
  const crecimientoTotal = desgloseFinal.total
  // El % se calcula solo sobre lo que suma: con un componente negativo, repartir sobre el total neto
  // daria porcentajes negativos o por encima de 100.
  const sumaPositivos = [desgloseFinal.ahorro, desgloseFinal.interes, desgloseFinal.inversion, desgloseFinal.previsto]
    .filter((v) => v > 0)
    .reduce((s, v) => s + v, 0)
  const detallePct = (valor: number) => {
    if (valor < 0) return 'Resta del crecimiento'
    if (valor > 0 && sumaPositivos > 0) return `${((valor / sumaPositivos) * 100).toFixed(0)}% de lo que suma`
    return undefined
  }

  const datosGrafica = [
    ...[...snapshots]
      .sort((a, b) => a.fecha.localeCompare(b.fecha))
      .map((s) => ({ fecha: s.fecha, historico: s.patrimonioTotal, proyectado: undefined as number | undefined })),
    { fecha: hoy.toISOString().slice(0, 10), historico: patrimonioActual, proyectado: patrimonioActual },
    ...serieProyectada.slice(1).map((p) => ({ fecha: p.fecha, historico: undefined as number | undefined, proyectado: p.proyectado })),
  ]

  if (cargando) {
    return <p className="text-sm text-muted">Cargando resumen...</p>
  }

  const variacion = patrimonioProyectado - patrimonioActual
  const variacionPct = patrimonioActual !== 0 ? (variacion / patrimonioActual) * 100 : 0
  const sube = variacion >= 0
  const colorLinea = sube ? COLOR_SUBE : COLOR_BAJA

  return (
    <div className="flex flex-col gap-6">
      <section>
        <h1 className="text-sm font-normal text-muted">Resumen · Patrimonio total</h1>
        <p className="mt-1 text-5xl font-bold tracking-tight text-ink sm:text-6xl">{formatearMoneda(patrimonioActual)}</p>
        <p className={`mt-2 text-sm font-medium ${sube ? 'text-up' : 'text-down'}`}>
          {sube ? '▲' : '▼'} {formatearMonedaConSigno(variacion)} ({formatearPorcentajeConSigno(variacionPct)}){' '}
          <span className="font-normal text-muted">proyectado a {etiquetaMeses(meses)}</span>
        </p>
      </section>

      <section>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={datosGrafica} margin={{ left: 0, right: 8 }}>
            <CartesianGrid vertical={false} stroke="var(--grid)" />
            <XAxis dataKey="fecha" tickFormatter={formatearFecha} minTickGap={48} {...estiloEje} />
            <YAxis hide domain={['auto', 'auto']} />
            <Tooltip labelFormatter={formatoTooltipFecha} formatter={formatoTooltipMoneda} {...estiloTooltip} />
            <Legend />
            <Line
              type="monotone"
              dataKey="historico"
              name="Histórico"
              stroke={colorLinea}
              strokeWidth={2.5}
              dot={false}
              activeDot={{ r: 5, stroke: 'var(--page)', strokeWidth: 2 }}
              connectNulls
            />
            <Line
              type="monotone"
              dataKey="proyectado"
              name="Proyectado"
              stroke={colorLinea}
              strokeWidth={2.5}
              strokeDasharray="6 5"
              dot={false}
              activeDot={{ r: 5, stroke: 'var(--page)', strokeWidth: 2 }}
              connectNulls
            />
          </LineChart>
        </ResponsiveContainer>

        <div className="mt-3 flex flex-wrap items-center justify-center gap-1.5">
          {PRESETS_MESES.map((m) => (
            <button
              key={m}
              onClick={() => setMeses(m)}
              className={`rounded-full px-3.5 py-1.5 text-xs font-semibold transition-colors ${
                meses === m ? 'bg-surface-2 text-ink' : 'text-muted hover:text-ink'
              }`}
            >
              {etiquetaMeses(m)}
            </button>
          ))}
          <label className="ml-2 flex items-center gap-1.5 text-xs text-muted">
            <input
              type="number"
              min={1}
              max={360}
              aria-label="Meses personalizados"
              className="w-16 rounded-full border border-line px-3 py-1.5 text-center text-xs"
              value={meses}
              onChange={(e) => setMeses(Math.min(360, Math.max(1, Number(e.target.value) || 1)))}
            />
            meses
          </label>
        </div>
        {snapshots.length === 0 && (
          <p className="mt-2 text-center text-xs text-muted">
            Todavía no hay snapshots guardados: el tramo histórico solo mostrará el punto actual.
          </p>
        )}
      </section>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard etiqueta="Saldo efectivo" valor={formatearMoneda(efectivo?.patrimonioActual ?? 0)} />
        <StatCard etiqueta="Valor cartera" valor={formatearMoneda(valorInversionActual)} />
        <StatCard
          etiqueta={`Proyectado a ${etiquetaMeses(meses)}`}
          valor={formatearMoneda(patrimonioProyectado)}
          tono={sube ? 'positivo' : 'negativo'}
        />
      </div>

      <ChartCard
        titulo="Pagos e ingresos previstos"
        subtitulo="Cosas que esperas y aún no están en ningún extracto (p. ej. dinero prestado que te van a devolver). Cuentan en la proyección desde su fecha hasta que las marques como cumplidas."
      >
        <div className="flex flex-wrap items-end gap-3 rounded-xl bg-surface-2 p-4">
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-ink-2">Descripción</span>
            <input
              className="w-56 rounded-xl border border-line px-3 py-2 text-sm"
              placeholder="Ej.: Me devuelve Juan"
              value={nuevoPrevisto.descripcion}
              onChange={(e) => setNuevoPrevisto((n) => ({ ...n, descripcion: e.target.value }))}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-ink-2">Tipo</span>
            <select
              className="rounded-xl border border-line px-3 py-2 text-sm"
              value={nuevoPrevisto.tipo}
              onChange={(e) => setNuevoPrevisto((n) => ({ ...n, tipo: e.target.value as 'INGRESO' | 'PAGO' }))}
            >
              <option value="INGRESO">Ingreso (me van a pagar)</option>
              <option value="PAGO">Pago (tengo que pagar)</option>
            </select>
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-ink-2">Importe (€)</span>
            <input
              type="number"
              min={0}
              step="0.01"
              className="w-28 rounded-xl border border-line px-3 py-2 text-sm"
              value={nuevoPrevisto.importe}
              onChange={(e) => setNuevoPrevisto((n) => ({ ...n, importe: e.target.value === '' ? '' : Number(e.target.value) }))}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-ink-2">Fecha prevista</span>
            <input
              type="date"
              className="rounded-xl border border-line px-3 py-2 text-sm"
              value={nuevoPrevisto.fecha}
              onChange={(e) => setNuevoPrevisto((n) => ({ ...n, fecha: e.target.value }))}
            />
          </label>
          <button
            onClick={onCrearPrevisto}
            disabled={guardandoPrevisto}
            className="rounded-xl bg-accent px-4 py-2 text-sm font-medium text-on-accent hover:opacity-90 disabled:opacity-50"
          >
            {guardandoPrevisto ? 'Guardando...' : 'Añadir'}
          </button>
        </div>
        {errorPrevisto && <p className="mt-2 text-sm text-down">{errorPrevisto}</p>}

        {previstos.length === 0 ? (
          <p className="mt-4 text-sm text-muted">Todavía no has añadido ningún movimiento previsto.</p>
        ) : (
          <table className="mt-4 w-full text-left text-sm">
            <thead className="text-xs uppercase text-muted">
              <tr>
                <th className="py-2">Fecha</th>
                <th className="py-2">Descripción</th>
                <th className="py-2 text-right">Importe</th>
                <th className="py-2" />
              </tr>
            </thead>
            <tbody>
              {previstos.map((p) => (
                <tr key={p.id} className="border-t border-line">
                  <td className={`py-2 ${p.cumplido ? 'text-muted line-through' : ''}`}>{formatearFecha(p.fecha)}</td>
                  <td className={`py-2 ${p.cumplido ? 'text-muted line-through' : ''}`}>{p.descripcion}</td>
                  <td
                    className={`py-2 text-right font-medium ${
                      p.cumplido ? 'text-muted line-through' : p.importe >= 0 ? 'text-up' : 'text-down'
                    }`}
                  >
                    {formatearMoneda(p.importe)}
                  </td>
                  <td className="py-2 text-right">
                    <button
                      className="mr-3 text-xs font-medium text-link hover:opacity-80"
                      onClick={() => onCambiarCumplido(p.id, !p.cumplido)}
                    >
                      {p.cumplido ? 'Reactivar' : 'Marcar cumplido'}
                    </button>
                    <button className="text-xs font-medium text-down hover:opacity-80" onClick={() => onEliminarPrevisto(p.id)}>
                      Eliminar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="mt-3 text-xs text-muted">
          Cuando el movimiento real aparezca en un extracto importado, márcalo como cumplido o elimínalo para no contarlo dos veces.
        </p>
      </ChartCard>

      <ChartCard
        titulo="De dónde viene el crecimiento proyectado"
        subtitulo={`Crecimiento neto estimado a ${etiquetaMeses(meses)}: ${formatearMoneda(crecimientoTotal)}. Los porcentajes reparten solo lo que suma; lo que resta se indica aparte.`}
      >
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
          <StatCard
            etiqueta="Ahorro (ingresos de trabajo)"
            valor={formatearMoneda(desgloseFinal.ahorro)}
            detalle={detallePct(desgloseFinal.ahorro)}
            tono={desgloseFinal.ahorro >= 0 ? 'positivo' : 'negativo'}
          />
          <StatCard
            etiqueta="Intereses / remuneración"
            valor={formatearMoneda(desgloseFinal.interes)}
            detalle={cuentasRemuneradas.length === 0 ? 'Sin cuentas remuneradas configuradas' : detallePct(desgloseFinal.interes)}
            tono="positivo"
          />
          <StatCard
            etiqueta="Crecimiento de inversión"
            valor={formatearMoneda(desgloseFinal.inversion)}
            detalle={detallePct(desgloseFinal.inversion)}
            tono={desgloseFinal.inversion >= 0 ? 'positivo' : 'negativo'}
          />
          <StatCard
            etiqueta="Previstos (manuales)"
            valor={formatearMoneda(desgloseFinal.previsto)}
            detalle={previstosPendientes.length === 0 ? 'Ninguno pendiente' : detallePct(desgloseFinal.previsto)}
            tono={desgloseFinal.previsto >= 0 ? 'positivo' : 'negativo'}
          />
        </div>
        {cuentasRemuneradas.length === 0 && (
          <p className="mt-3 text-xs text-muted">
            Para incluir intereses, marca la tasa de interés anual de una cuenta remunerada en "Importar archivos".
          </p>
        )}

        <button
          className="mt-4 text-sm font-medium text-link hover:opacity-80"
          onClick={() => setMostrarSupuestos((v) => !v)}
        >
          {mostrarSupuestos ? '▾ Ocultar supuestos de crecimiento' : '▸ Ajustar supuestos de crecimiento por tipo de inversión'}
        </button>
        {mostrarSupuestos && (
          <div className="mt-3 flex flex-wrap gap-3 rounded-xl bg-surface-2 p-4">
            {(Object.keys(ETIQUETA_TIPO) as TipoActivo[]).map((tipo) => (
              <label key={tipo} className="flex flex-col gap-1 text-sm">
                <span className="text-ink-2">
                  {ETIQUETA_TIPO[tipo]} {valorPorTipo[tipo] ? `(${formatearMoneda(valorPorTipo[tipo]!)})` : '(sin posiciones)'}
                </span>
                <span className="flex items-center gap-1">
                  <input
                    type="number"
                    step="0.5"
                    className="w-20 rounded-xl border border-line px-2 py-1 text-sm"
                    value={retornosPct[tipo]}
                    onChange={(e) => setRetornosPct((r) => ({ ...r, [tipo]: Number(e.target.value) }))}
                  />
                  <span className="text-xs text-muted">% anual</span>
                </span>
              </label>
            ))}
            <p className="w-full text-xs text-muted">
              Son supuestos editables para esta proyección, no una recomendación de inversión.
            </p>
          </div>
        )}
      </ChartCard>
    </div>
  )
}
