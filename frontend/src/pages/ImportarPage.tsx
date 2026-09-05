import { useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { crearCuenta, listarCuentas } from '../api/cuentas'
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
  const [nuevaCuenta, setNuevaCuenta] = useState({ banco: '', alias: '', tipo: 'NOMINA' as TipoCuenta, moneda: 'EUR', saldoInicial: 0 })
  const [creandoCuenta, setCreandoCuenta] = useState(false)

  const cuentaSeleccionada = cuentaId ?? cuentasQuery.data?.[0]?.id ?? null

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
      const creada = await crearCuenta(usuarioId, nuevaCuenta)
      await queryClient.invalidateQueries({ queryKey: ['cuentas', usuarioId] })
      setCuentaId(creada.id)
      setMostrarFormCuenta(false)
      setNuevaCuenta({ banco: '', alias: '', tipo: 'NOMINA', moneda: 'EUR', saldoInicial: 0 })
    } catch {
      setError('No se ha podido crear la cuenta')
    } finally {
      setCreandoCuenta(false)
    }
  }

  if (cuentasQuery.isLoading) {
    return <p className="text-sm text-slate-500">Cargando cuentas...</p>
  }

  const hayCuentas = (cuentasQuery.data?.length ?? 0) > 0

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Importar archivos</h1>
        <p className="text-sm text-slate-500">Sube un extracto en CSV o PDF (Trade Republic, ING) para esa cuenta</p>
      </div>

      <ChartCard titulo="Cuentas">
        {hayCuentas && (
          <label className="mb-4 flex flex-col gap-1 text-sm">
            <span className="text-slate-600">Cuenta</span>
            <select
              className="w-72 rounded-lg border border-slate-300 px-3 py-2 text-sm"
              value={cuentaSeleccionada ?? ''}
              onChange={(e) => setCuentaId(Number(e.target.value))}
            >
              {cuentasQuery.data!.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.alias} ({c.banco})
                </option>
              ))}
            </select>
          </label>
        )}

        {!mostrarFormCuenta ? (
          <button
            className="text-sm font-medium text-indigo-600 hover:text-indigo-800"
            onClick={() => setMostrarFormCuenta(true)}
          >
            + Añadir cuenta nueva
          </button>
        ) : (
          <div className="mt-2 flex flex-wrap items-end gap-3 rounded-lg bg-slate-50 p-4">
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-slate-600">Banco</span>
              <input
                className="w-40 rounded-lg border border-slate-300 px-3 py-2 text-sm"
                value={nuevaCuenta.banco}
                onChange={(e) => setNuevaCuenta((c) => ({ ...c, banco: e.target.value }))}
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-slate-600">Alias</span>
              <input
                className="w-40 rounded-lg border border-slate-300 px-3 py-2 text-sm"
                value={nuevaCuenta.alias}
                onChange={(e) => setNuevaCuenta((c) => ({ ...c, alias: e.target.value }))}
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-slate-600">Tipo</span>
              <select
                className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
                value={nuevaCuenta.tipo}
                onChange={(e) => setNuevaCuenta((c) => ({ ...c, tipo: e.target.value as TipoCuenta }))}
              >
                <option value="NOMINA">Nómina</option>
                <option value="TARJETA">Tarjeta</option>
                <option value="AHORRO">Ahorro</option>
              </select>
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-slate-600">Moneda</span>
              <input
                className="w-20 rounded-lg border border-slate-300 px-3 py-2 text-sm"
                value={nuevaCuenta.moneda}
                onChange={(e) => setNuevaCuenta((c) => ({ ...c, moneda: e.target.value }))}
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-slate-600">Saldo inicial</span>
              <input
                type="number"
                className="w-28 rounded-lg border border-slate-300 px-3 py-2 text-sm"
                value={nuevaCuenta.saldoInicial}
                onChange={(e) => setNuevaCuenta((c) => ({ ...c, saldoInicial: Number(e.target.value) }))}
              />
            </label>
            <button
              onClick={onCrearCuenta}
              disabled={creandoCuenta}
              className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
            >
              {creandoCuenta ? 'Creando...' : 'Crear cuenta'}
            </button>
            <button className="text-sm text-slate-500 hover:text-slate-700" onClick={() => setMostrarFormCuenta(false)}>
              Cancelar
            </button>
          </div>
        )}
      </ChartCard>

      {hayCuentas && (
        <ChartCard titulo="Subir extracto">
          <div
            className="flex h-40 cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 text-center text-sm text-slate-500 hover:border-slate-400"
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
                <span className="mt-1 text-xs text-slate-400">CSV o PDF</span>
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

          {error && <p className="mt-4 text-sm text-rose-600">{error}</p>}

          {resultado && (
            <div className="mt-4 grid grid-cols-3 gap-4">
              <div className="rounded-lg bg-slate-100 p-4 text-center">
                <div className="text-2xl font-semibold text-slate-900">{resultado.totalEncontrados}</div>
                <div className="text-xs text-slate-500">Encontrados en el fichero</div>
              </div>
              <div className="rounded-lg bg-emerald-50 p-4 text-center">
                <div className="text-2xl font-semibold text-emerald-700">{resultado.nuevos}</div>
                <div className="text-xs text-emerald-600">Nuevos importados</div>
              </div>
              <div className="rounded-lg bg-amber-50 p-4 text-center">
                <div className="text-2xl font-semibold text-amber-700">{resultado.duplicados}</div>
                <div className="text-xs text-amber-600">Duplicados (ya existían)</div>
              </div>
            </div>
          )}
        </ChartCard>
      )}
    </div>
  )
}
