import { useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { useAuth } from '../auth/AuthContext'
import { importarPosiciones, listarPosiciones, proyeccionInversion } from '../api/inversion'
import { ChartCard } from '../components/ChartCard'
import { StatCard } from '../components/StatCard'
import { COLOR_BAJA, COLOR_SUBE, colorParaIndice, estiloEje, estiloTooltip, formatearMoneda, formatoTooltipMoneda } from '../lib/format'
import { ApiError } from '../api/client'
import type { ResultadoImportacionPosiciones, TipoActivo } from '../api/types'
import { agruparPorProducto, nombreCorto } from '../lib/producto'

const ETIQUETA_TIPO: Record<TipoActivo, string> = {
  ACCION: 'Acciones',
  ETF: 'ETFs',
  FONDO: 'Fondos',
  CRIPTO: 'Cripto',
}

export function InversionPage() {
  const { usuario } = useAuth()
  const usuarioId = usuario!.id
  const queryClient = useQueryClient()

  const posicionesQuery = useQuery({ queryKey: ['posiciones', usuarioId], queryFn: () => listarPosiciones(usuarioId) })

  const [anios, setAnios] = useState(5)
  const [retorno, setRetorno] = useState(0.07)
  const [aportacion, setAportacion] = useState(1000)

  const proyeccionQuery = useQuery({
    queryKey: ['proyeccion-inversion-detalle', usuarioId, anios, retorno, aportacion],
    queryFn: () => proyeccionInversion(usuarioId, anios, retorno, aportacion),
  })

  const [subiendo, setSubiendo] = useState(false)
  const [resultadoImportacion, setResultadoImportacion] = useState<ResultadoImportacionPosiciones | null>(null)
  const [errorImportacion, setErrorImportacion] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const onFileChange = async (file: File | null) => {
    if (!file) return
    setSubiendo(true)
    setErrorImportacion(null)
    setResultadoImportacion(null)
    try {
      const respuesta = await importarPosiciones(usuarioId, file)
      setResultadoImportacion(respuesta)
      await queryClient.invalidateQueries({ queryKey: ['posiciones', usuarioId] })
    } catch (e) {
      setErrorImportacion(e instanceof ApiError ? e.message : 'No se ha podido importar el extracto')
    } finally {
      setSubiendo(false)
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  const posiciones = posicionesQuery.data ?? []
  const valorTotal = posiciones.reduce((s, p) => s + p.valorActual, 0)
  const rendimientoTotal = posiciones.reduce((s, p) => s + p.rendimiento, 0)
  const valorPorTipo = (['ETF', 'ACCION', 'FONDO', 'CRIPTO'] as TipoActivo[])
    .map((tipo) => ({
      tipo: ETIQUETA_TIPO[tipo],
      valor: posiciones.filter((p) => p.tipo === tipo).reduce((s, p) => s + p.valorActual, 0),
    }))
    .filter((t) => t.valor > 0)
  const productos = agruparPorProducto(posiciones)
  const hayRendimiento = productos.some((p) => Math.abs(p.rendimiento) > 0.005)
  const posicionesOrdenadas = [...posiciones].sort((a, b) => b.valorActual - a.valorActual)
  const recortar = (nombre: string) => (nombre.length > 26 ? `${nombre.slice(0, 25)}…` : nombre)

  const proyeccion = proyeccionQuery.data
  const datosProyeccion =
    proyeccion &&
    Array.from({ length: anios + 1 }, (_, i) => {
      const factor = Math.pow(1 + retorno, i)
      const valor = proyeccion.valorActual * factor + aportacion * i
      return { anio: i, valor }
    })

  if (posicionesQuery.isLoading) {
    return <p className="text-sm text-muted">Cargando cartera...</p>
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight text-ink">Inversión</h1>
        <p className="text-sm text-muted">Cartera valorada con precio de mercado real y proyección compuesta</p>
      </div>

      <ChartCard titulo="Actualizar cartera" subtitulo="Sube un extracto de la cuenta de valores (foto de tus posiciones actuales)">
        <div
          className="flex h-28 cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed border-muted/40 bg-surface-2 text-center text-sm text-muted hover:border-ink-2"
          onClick={() => inputRef.current?.click()}
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault()
            onFileChange(e.dataTransfer.files[0] ?? null)
          }}
        >
          {subiendo ? (
            <span>Importando...</span>
          ) : (
            <>
              <span className="font-medium">Arrastra el extracto de la cuenta de valores (PDF) o haz clic</span>
              <span className="mt-1 text-xs text-muted">Reemplaza cantidad y precio de cada posición por los del extracto</span>
            </>
          )}
        </div>
        <input
          ref={inputRef}
          type="file"
          accept=".pdf"
          className="hidden"
          onChange={(e) => onFileChange(e.target.files?.[0] ?? null)}
        />

        {errorImportacion && <p className="mt-3 text-sm text-down">{errorImportacion}</p>}
        {resultadoImportacion && (
          <p className="mt-3 text-sm text-up">
            {resultadoImportacion.actualizadas} posiciones actualizadas de {resultadoImportacion.totalEncontradas} encontradas.
          </p>
        )}
      </ChartCard>

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
        <p className="text-sm text-muted">Todavía no hay posiciones en la cartera.</p>
      ) : (
        <>
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <ChartCard titulo="Distribución por tipo de activo" className="lg:col-span-1">
              <ResponsiveContainer width="100%" height={220}>
                <PieChart>
                  <Pie
                    data={valorPorTipo}
                    dataKey="valor"
                    nameKey="tipo"
                    innerRadius={55}
                    outerRadius={90}
                    paddingAngle={2}
                    stroke="var(--surface)"
                    strokeWidth={2}
                  >
                    {valorPorTipo.map((_, i) => (
                      <Cell key={i} fill={colorParaIndice(i)} />
                    ))}
                  </Pie>
                  <Tooltip formatter={formatoTooltipMoneda} {...estiloTooltip} />
                </PieChart>
              </ResponsiveContainer>
              <ul className="mt-2 flex flex-col gap-2 text-sm">
                {valorPorTipo.map((t, i) => (
                  <li key={t.tipo} className="flex items-center justify-between gap-3">
                    <span className="flex items-center gap-2 text-ink-2">
                      <span className="h-2.5 w-2.5 rounded-full" style={{ background: colorParaIndice(i) }} />
                      {t.tipo}
                    </span>
                    <span className="text-ink">
                      {formatearMoneda(t.valor)}{' '}
                      <span className="text-muted">· {valorTotal > 0 ? ((t.valor / valorTotal) * 100).toFixed(0) : 0} %</span>
                    </span>
                  </li>
                ))}
              </ul>
            </ChartCard>

            <ChartCard
              titulo={hayRendimiento ? 'Rendimiento por producto' : 'Valor por producto'}
              subtitulo={
                hayRendimiento
                  ? 'Ganancia o pérdida de cada producto, sumando todas sus posiciones'
                  : 'Tu extracto de la cuenta de valores no incluye el precio de compra, así que el rendimiento sale 0 €: se muestra cuánto vale cada producto'
              }
              className="lg:col-span-2"
            >
              <ResponsiveContainer width="100%" height={Math.max(280, productos.length * 30)}>
                <BarChart data={productos} layout="vertical" margin={{ left: 0, right: 16 }}>
                  <CartesianGrid horizontal={false} stroke="var(--grid)" />
                  <XAxis type="number" tickFormatter={(v) => formatearMoneda(v)} {...estiloEje} />
                  <YAxis type="category" dataKey="producto" width={175} tickFormatter={recortar} interval={0} {...estiloEje} />
                  <Tooltip formatter={formatoTooltipMoneda} {...estiloTooltip} cursor={{ fill: 'var(--surface-2)' }} />
                  <Bar
                    dataKey={hayRendimiento ? 'rendimiento' : 'valor'}
                    name={hayRendimiento ? 'Rendimiento' : 'Valor'}
                    radius={[0, 6, 6, 0]}
                  >
                    {productos.map((p, i) => (
                      <Cell key={i} fill={hayRendimiento ? (p.rendimiento >= 0 ? COLOR_SUBE : COLOR_BAJA) : 'var(--series-1)'} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>
          </div>

          <ChartCard titulo="Posiciones">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] text-left text-sm">
                <thead className="text-xs uppercase text-muted">
                  <tr>
                    <th className="py-2">Producto</th>
                    <th className="py-2 text-right">Cantidad</th>
                    <th className="py-2 text-right">Precio actual</th>
                    <th className="py-2 text-right">Valor</th>
                    <th className="py-2 text-right">% cartera</th>
                    <th className="py-2 text-right">Rendimiento</th>
                  </tr>
                </thead>
                <tbody>
                  {posicionesOrdenadas.map((p) => (
                    <tr key={p.posicionId} className="border-t border-line">
                      <td className="py-2">
                        <div className="font-medium text-ink" title={p.nombreActivo}>
                          {nombreCorto(p.nombreActivo)}
                        </div>
                        <div className="text-xs text-muted">{p.ticker}</div>
                      </td>
                      <td className="py-2 text-right">{p.cantidad}</td>
                      <td className="py-2 text-right">{formatearMoneda(p.precioActual)}</td>
                      <td className="py-2 text-right font-medium">{formatearMoneda(p.valorActual)}</td>
                      <td className="py-2 text-right text-muted">
                        {valorTotal > 0 ? ((p.valorActual / valorTotal) * 100).toFixed(1).replace('.', ',') : '0'} %
                      </td>
                      <td className={`py-2 text-right font-medium ${p.rendimiento >= 0 ? 'text-up' : 'text-down'}`}>
                        {formatearMoneda(p.rendimiento)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </ChartCard>
        </>
      )}

      <ChartCard titulo="Proyección compuesta" subtitulo="valor_actual × (1 + retorno)^años + aportaciones periódicas">
        <div className="mb-4 flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-ink-2">Años</span>
            <input
              type="number"
              min={1}
              className="w-24 rounded-xl border border-line px-3 py-2 text-sm"
              value={anios}
              onChange={(e) => setAnios(Math.max(1, Number(e.target.value)))}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-ink-2">Retorno esperado anual</span>
            <input
              type="number"
              step="0.01"
              className="w-28 rounded-xl border border-line px-3 py-2 text-sm"
              value={retorno}
              onChange={(e) => setRetorno(Number(e.target.value))}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-ink-2">Aportación anual (€)</span>
            <input
              type="number"
              className="w-32 rounded-xl border border-line px-3 py-2 text-sm"
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
                <defs>
                  <linearGradient id="relleno-proyeccion" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={COLOR_SUBE} stopOpacity={0.28} />
                    <stop offset="100%" stopColor={COLOR_SUBE} stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid vertical={false} stroke="var(--grid)" />
                <XAxis dataKey="anio" tickFormatter={(v) => `Año ${v}`} {...estiloEje} />
                <YAxis tickFormatter={(v) => formatearMoneda(v)} width={90} {...estiloEje} />
                <Tooltip labelFormatter={(v) => `Año ${v}`} formatter={formatoTooltipMoneda} {...estiloTooltip} />
                <Area
                  type="monotone"
                  dataKey="valor"
                  name="Valor proyectado"
                  stroke={COLOR_SUBE}
                  strokeWidth={2}
                  fill="url(#relleno-proyeccion)"
                  dot={false}
                  activeDot={{ r: 5, stroke: 'var(--surface)', strokeWidth: 2 }}
                />
              </AreaChart>
            </ResponsiveContainer>
          </>
        )}
      </ChartCard>
    </div>
  )
}
