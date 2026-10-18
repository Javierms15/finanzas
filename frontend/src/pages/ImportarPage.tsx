import { useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { actualizarSaldoInicial, actualizarTasaInteres, crearCuenta, listarCuentas } from '../api/cuentas'
import { importarFichero } from '../api/movimientos'
import { ChartCard } from '../components/ChartCard'
import type { ResultadoImportacion, TipoCuenta } from '../api/types'
import { ApiError } from '../api/client'

export function ImportarPage() {
  const { usuario } = useAuth()
  const usuarioId = usuario!.id
  const queryClient = useQueryClient()

  const cuentasQuery = useQuery({ queryKey: ['cuentas', usuarioId], queryFn: () => listarCuentas(usuarioId) })
  const [cuentaId, setCuentaId] = useState<number | null>(null)
  const [subiendo, setSubiendo] = useState(false)
  const [resultado, setResultado] = useState<ResultadoImportacion | null>(null)
  const [error, setError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const [mostrarFormCuenta, setMostrarFormCuenta] = useState(false)
  const [nuevaCuenta, setNuevaCuenta] = useState({
    banco: '',
    alias: '',
    tipo: 'NOMINA' as TipoCuenta,
    moneda: 'EUR',
    saldoInicial: 0,
    tasaInteresAnual: '' as number | '',
  })
  const [creandoCuenta, setCreandoCuenta] = useState(false)

  const [mostrarEdicionCuenta, setMostrarEdicionCuenta] = useState(false)
  const [edicionCuenta, setEdicionCuenta] = useState({ saldoInicial: 0, tasaInteresAnual: '' as number | '' })
  const [guardandoCuenta, setGuardandoCuenta] = useState(false)

  const cuentaSeleccionada = cuentaId ?? cuentasQuery.data?.[0]?.id ?? null
  const cuentaSeleccionadaDatos = cuentasQuery.data?.find((c) => c.id === cuentaSeleccionada) ?? null

  const onFileChange = async (file: File | null) => {
    if (!file || !cuentaSeleccionada) return
    setSubiendo(true)
    setError(null)
    setResultado(null)
    try {
      const respuesta = await importarFichero(cuentaSeleccionada, file)
      setResultado(respuesta)
      queryClient.invalidateQueries({ queryKey: ['movimientos', cuentaSeleccionada] })
      queryClient.invalidateQueries({ queryKey: ['agregacion-cuenta', cuentaSeleccionada] })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se ha podido importar el fichero')
    } finally {
      setSubiendo(false)
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  const onCrearCuenta = async () => {
    if (!nuevaCuenta.banco || !nuevaCuenta.alias) return
    setCreandoCuenta(true)
    try {
      const creada = await crearCuenta(usuarioId, {
        ...nuevaCuenta,
        tasaInteresAnual: nuevaCuenta.tasaInteresAnual === '' ? null : nuevaCuenta.tasaInteresAnual / 100,
      })
      await queryClient.invalidateQueries({ queryKey: ['cuentas', usuarioId] })
      setCuentaId(creada.id)
      setMostrarFormCuenta(false)
      setNuevaCuenta({ banco: '', alias: '', tipo: 'NOMINA', moneda: 'EUR', saldoInicial: 0, tasaInteresAnual: '' })
    } catch {
      setError('No se ha podido crear la cuenta')
    } finally {
      setCreandoCuenta(false)
    }
  }

  const onGuardarCuenta = async () => {
    if (!cuentaSeleccionada) return
    setGuardandoCuenta(true)
    try {
      await actualizarSaldoInicial(usuarioId, cuentaSeleccionada, edicionCuenta.saldoInicial)
      await actualizarTasaInteres(
        usuarioId,
        cuentaSeleccionada,
        edicionCuenta.tasaInteresAnual === '' ? null : edicionCuenta.tasaInteresAnual / 100,
      )
      await queryClient.invalidateQueries({ queryKey: ['cuentas', usuarioId] })
      await queryClient.invalidateQueries({ queryKey: ['cuentas-saldo', usuarioId] })
      setMostrarEdicionCuenta(false)
    } catch {
      setError('No se ha podido actualizar la cuenta')
    } finally {
      setGuardandoCuenta(false)
    }
  }

  if (cuentasQuery.isLoading) {
    return <p className="text-sm text-muted">Cargando cuentas...</p>
  }

  const hayCuentas = (cuentasQuery.data?.length ?? 0) > 0

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight text-ink">Importar archivos</h1>
        <p className="text-sm text-muted">Sube un extracto en CSV o PDF (Trade Republic, ING) para esa cuenta</p>
      </div>

      <ChartCard titulo="Cuentas">
        {hayCuentas && (
          <label className="mb-4 flex flex-col gap-1 text-sm">
            <span className="text-ink-2">Cuenta</span>
            <select
              className="w-72 rounded-xl border border-line px-3 py-2 text-sm"
              value={cuentaSeleccionada ?? ''}
              onChange={(e) => {
                setCuentaId(Number(e.target.value))
                setMostrarEdicionCuenta(false)
              }}
            >
              {cuentasQuery.data!.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.alias} ({c.banco})
                </option>
              ))}
            </select>
          </label>
        )}

        {hayCuentas && cuentaSeleccionadaDatos && (
          <div className="mb-4">
            {!mostrarEdicionCuenta ? (
              <button
                className="text-sm font-medium text-link hover:opacity-80"
                onClick={() => {
                  setEdicionCuenta({
                    saldoInicial: cuentaSeleccionadaDatos.saldoInicial,
                    tasaInteresAnual:
                      cuentaSeleccionadaDatos.tasaInteresAnual != null ? cuentaSeleccionadaDatos.tasaInteresAnual * 100 : '',
                  })
                  setMostrarEdicionCuenta(true)
                }}
              >
                ✎ Editar saldo inicial / interés de "{cuentaSeleccionadaDatos.alias}"
              </button>
            ) : (
              <div className="flex flex-wrap items-end gap-3 rounded-xl bg-surface-2 p-4">
                <label className="flex flex-col gap-1 text-sm">
                  <span className="text-ink-2">Saldo inicial</span>
                  <input
                    type="number"
                    className="w-28 rounded-xl border border-line px-3 py-2 text-sm"
                    value={edicionCuenta.saldoInicial}
                    onChange={(e) => setEdicionCuenta((c) => ({ ...c, saldoInicial: Number(e.target.value) }))}
                  />
                </label>
                <label className="flex flex-col gap-1 text-sm">
                  <span className="text-ink-2">Interés anual % (opcional)</span>
                  <input
                    type="number"
                    step="0.01"
                    className="w-28 rounded-xl border border-line px-3 py-2 text-sm"
                    value={edicionCuenta.tasaInteresAnual}
                    onChange={(e) =>
                      setEdicionCuenta((c) => ({ ...c, tasaInteresAnual: e.target.value === '' ? '' : Number(e.target.value) }))
                    }
                  />
                </label>
                <button
                  onClick={onGuardarCuenta}
                  disabled={guardandoCuenta}
                  className="rounded-xl bg-accent px-4 py-2 text-sm font-medium text-on-accent hover:opacity-90 disabled:opacity-50"
                >
                  {guardandoCuenta ? 'Guardando...' : 'Guardar'}
                </button>
                <button className="text-sm text-muted hover:text-ink" onClick={() => setMostrarEdicionCuenta(false)}>
                  Cancelar
                </button>
              </div>
            )}
          </div>
        )}

        {!mostrarFormCuenta ? (
          <button
            className="text-sm font-medium text-link hover:opacity-80"
            onClick={() => setMostrarFormCuenta(true)}
          >
            + Añadir cuenta nueva
          </button>
        ) : (
          <div className="mt-2 flex flex-wrap items-end gap-3 rounded-xl bg-surface-2 p-4">
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-ink-2">Banco</span>
              <input
                className="w-40 rounded-xl border border-line px-3 py-2 text-sm"
                value={nuevaCuenta.banco}
                onChange={(e) => setNuevaCuenta((c) => ({ ...c, banco: e.target.value }))}
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-ink-2">Alias</span>
              <input
                className="w-40 rounded-xl border border-line px-3 py-2 text-sm"
                value={nuevaCuenta.alias}
                onChange={(e) => setNuevaCuenta((c) => ({ ...c, alias: e.target.value }))}
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-ink-2">Tipo</span>
              <select
                className="rounded-xl border border-line px-3 py-2 text-sm"
                value={nuevaCuenta.tipo}
                onChange={(e) => setNuevaCuenta((c) => ({ ...c, tipo: e.target.value as TipoCuenta }))}
              >
                <option value="NOMINA">Nómina</option>
                <option value="TARJETA">Tarjeta</option>
                <option value="AHORRO">Ahorro</option>
              </select>
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-ink-2">Moneda</span>
              <input
                className="w-20 rounded-xl border border-line px-3 py-2 text-sm"
                value={nuevaCuenta.moneda}
                onChange={(e) => setNuevaCuenta((c) => ({ ...c, moneda: e.target.value }))}
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-ink-2">Saldo inicial</span>
              <input
                type="number"
                className="w-28 rounded-xl border border-line px-3 py-2 text-sm"
                value={nuevaCuenta.saldoInicial}
                onChange={(e) => setNuevaCuenta((c) => ({ ...c, saldoInicial: Number(e.target.value) }))}
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-ink-2" title="Solo si es una cuenta remunerada (p. ej. una 'cuenta naranja')">
                Interés anual % (opcional)
              </span>
              <input
                type="number"
                step="0.01"
                className="w-28 rounded-xl border border-line px-3 py-2 text-sm"
                value={nuevaCuenta.tasaInteresAnual}
                onChange={(e) =>
                  setNuevaCuenta((c) => ({ ...c, tasaInteresAnual: e.target.value === '' ? '' : Number(e.target.value) }))
                }
              />
            </label>
            <button
              onClick={onCrearCuenta}
              disabled={creandoCuenta}
              className="rounded-xl bg-accent px-4 py-2 text-sm font-medium text-on-accent hover:opacity-90 disabled:opacity-50"
            >
              {creandoCuenta ? 'Creando...' : 'Crear cuenta'}
            </button>
            <button className="text-sm text-muted hover:text-ink" onClick={() => setMostrarFormCuenta(false)}>
              Cancelar
            </button>
          </div>
        )}
      </ChartCard>

      {hayCuentas && (
        <ChartCard titulo="Subir extracto">
          <div
            className="flex h-40 cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed border-muted/40 bg-surface-2 text-center text-sm text-muted hover:border-ink-2"
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
                <span className="font-medium">Arrastra un fichero aquí o haz clic para elegirlo</span>
                <span className="mt-1 text-xs text-muted">CSV o PDF</span>
              </>
            )}
          </div>
          <input
            ref={inputRef}
            type="file"
            accept=".csv,.pdf"
            className="hidden"
            onChange={(e) => onFileChange(e.target.files?.[0] ?? null)}
          />

          {error && <p className="mt-4 text-sm text-down">{error}</p>}

          {resultado && (
            <div className="mt-4 grid grid-cols-4 gap-4">
              <div className="rounded-xl bg-surface-2 p-4 text-center">
                <div className="text-2xl font-semibold text-ink">{resultado.totalEncontrados}</div>
                <div className="text-xs text-muted">Encontrados en el fichero</div>
              </div>
              <div className="rounded-xl bg-up/10 p-4 text-center">
                <div className="text-2xl font-semibold text-up">{resultado.nuevos}</div>
                <div className="text-xs text-up">Nuevos importados</div>
              </div>
              <div className="rounded-xl bg-warn/10 p-4 text-center">
                <div className="text-2xl font-semibold text-warn">{resultado.duplicados}</div>
                <div className="text-xs text-warn">Duplicados (ya existían)</div>
              </div>
              <div className="rounded-xl bg-warn/10 p-4 text-center">
                <div className="text-2xl font-semibold text-warn">{resultado.duplicadosProbables}</div>
                <div className="text-xs text-warn" title="Misma cuenta, fecha e importe que uno ya existente, pero con descripción distinta (p. ej. el mismo banco en dos formatos de extracto)">
                  Probables duplicados
                </div>
              </div>
            </div>
          )}
          {resultado && resultado.cuentasCreadas.length > 0 && (
            <p className="mt-3 text-sm text-ink-2">
              El fichero traía más de una cuenta y se ha creado: <strong>{resultado.cuentasCreadas.join(', ')}</strong>. Cada
              movimiento se ha llevado a la suya. Si es una cuenta remunerada, indica su interés en «Editar saldo inicial / interés».
            </p>
          )}
        </ChartCard>
      )}
    </div>
  )
}
