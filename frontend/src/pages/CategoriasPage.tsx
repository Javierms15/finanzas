import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import {
  cargarSugeridas,
  crearCategoria,
  crearRegla,
  eliminarRegla,
  listarCategorias,
  listarReglas,
  recategorizar,
} from '../api/categorias'
import { ApiError } from '../api/client'
import { ChartCard } from '../components/ChartCard'
import type { TipoCategoria } from '../api/types'

const TIPOS: Array<{ tipo: TipoCategoria; titulo: string; ayuda: string }> = [
  { tipo: 'GASTO', titulo: 'Gastos', ayuda: 'Salidas de dinero reales' },
  { tipo: 'INGRESO', titulo: 'Ingresos', ayuda: 'Entradas de dinero reales' },
  {
    tipo: 'NEUTRO',
    titulo: 'Neutras',
    ayuda: 'Mueven dinero pero no son ni ingreso ni gasto (transferencias entre tus cuentas, aportaciones a inversión): no cuentan en ingresos/gastos ni en la proyección',
  },
]

export function CategoriasPage() {
  const { usuario } = useAuth()
  const usuarioId = usuario!.id
  const queryClient = useQueryClient()

  const categoriasQuery = useQuery({ queryKey: ['categorias'], queryFn: listarCategorias })
  const reglasQuery = useQuery({ queryKey: ['reglas'], queryFn: listarReglas })

  const [aviso, setAviso] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [trabajando, setTrabajando] = useState(false)
  const [nuevaCategoria, setNuevaCategoria] = useState({ nombre: '', tipo: 'GASTO' as TipoCategoria })
  const [nuevaRegla, setNuevaRegla] = useState({ patron: '', categoriaId: '' })

  const categorias = categoriasQuery.data ?? []
  const reglas = reglasQuery.data ?? []

  const refrescar = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['categorias'] }),
      queryClient.invalidateQueries({ queryKey: ['reglas'] }),
      queryClient.invalidateQueries({ queryKey: ['movimientos'] }),
      queryClient.invalidateQueries({ queryKey: ['agregacion-cuenta'] }),
      queryClient.invalidateQueries({ queryKey: ['agregacion-usuario'] }),
      queryClient.invalidateQueries({ queryKey: ['proyeccion-efectivo'] }),
    ])
  }

  const ejecutar = async (accion: () => Promise<string>) => {
    setTrabajando(true)
    setError(null)
    setAviso(null)
    try {
      setAviso(await accion())
      await refrescar()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se ha podido completar la operación')
    } finally {
      setTrabajando(false)
    }
  }

  const onSugeridas = () =>
    ejecutar(async () => {
      const r = await cargarSugeridas(usuarioId)
      const recategorizados = await recategorizar(true)
      return `${r.categoriasCreadas} categorías y ${r.reglasCreadas} reglas nuevas. ${recategorizados} movimientos recategorizados.`
    })

  const onRecategorizar = () =>
    ejecutar(async () => `${await recategorizar(true)} movimientos cambiaron de categoría (los elegidos a mano no se tocan).`)

  const onCrearCategoria = () =>
    ejecutar(async () => {
      if (!nuevaCategoria.nombre.trim()) throw new ApiError('Indica un nombre', 400)
      await crearCategoria(nuevaCategoria.nombre.trim(), nuevaCategoria.tipo)
      setNuevaCategoria((n) => ({ ...n, nombre: '' }))
      return 'Categoría creada.'
    })

  const onCrearRegla = () =>
    ejecutar(async () => {
      if (!nuevaRegla.patron.trim() || !nuevaRegla.categoriaId) throw new ApiError('Indica el texto a buscar y la categoría', 400)
      // Las reglas propias van antes que las sugeridas (que empiezan en 100)
      const prioridad = Math.min(99, Math.max(0, ...reglas.filter((r) => r.prioridad < 100).map((r) => r.prioridad)) + 1)
      await crearRegla(Number(nuevaRegla.categoriaId), nuevaRegla.patron.trim(), prioridad)
      const cambiados = await recategorizar(true)
      setNuevaRegla((n) => ({ ...n, patron: '' }))
      return `Regla creada. ${cambiados} movimientos recategorizados.`
    })

  const onEliminarRegla = (id: number) =>
    ejecutar(async () => {
      await eliminarRegla(id)
      return 'Regla eliminada. Pulsa "Recategorizar" para aplicar el cambio a los movimientos.'
    })

  if (categoriasQuery.isLoading || reglasQuery.isLoading) {
    return <p className="text-sm text-muted">Cargando categorías...</p>
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight text-ink">Categorías</h1>
        <p className="text-sm text-muted">
          Cada movimiento recibe la categoría de la primera regla (por orden de prioridad) cuyo texto aparezca en su descripción.
        </p>
      </div>

      <ChartCard
        titulo="Acciones"
        subtitulo="Las categorías sugeridas cubren los extractos de ING y Trade Republic; se pueden cargar varias veces sin duplicar nada"
      >
        <div className="flex flex-wrap items-center gap-3">
          <button
            onClick={onSugeridas}
            disabled={trabajando}
            className="rounded-xl bg-accent px-4 py-2 text-sm font-medium text-on-accent hover:opacity-90 disabled:opacity-50"
          >
            Cargar categorías y reglas sugeridas
          </button>
          <button
            onClick={onRecategorizar}
            disabled={trabajando}
            className="rounded-xl bg-surface-2 px-4 py-2 text-sm font-medium text-ink hover:opacity-90 disabled:opacity-50"
          >
            Recategorizar movimientos
          </button>
        </div>
        {aviso && <p className="mt-3 text-sm text-up">{aviso}</p>}
        {error && <p className="mt-3 text-sm text-down">{error}</p>}
      </ChartCard>

      {TIPOS.map(({ tipo, titulo, ayuda }) => {
        const delTipo = categorias.filter((c) => c.tipo === tipo)
        return (
          <ChartCard key={tipo} titulo={titulo} subtitulo={ayuda}>
            {delTipo.length === 0 ? (
              <p className="text-sm text-muted">Ninguna todavía.</p>
            ) : (
              <ul className="flex flex-col divide-y divide-line">
                {delTipo.map((c) => {
                  const suyas = reglas.filter((r) => r.categoria.id === c.id)
                  return (
                    <li key={c.id} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-start sm:gap-6">
                      <div className="w-48 shrink-0 text-sm font-medium text-ink">{c.nombre}</div>
                      <div className="flex flex-wrap gap-1.5">
                        {suyas.length === 0 && <span className="text-xs text-muted">Sin reglas</span>}
                        {suyas.map((r) => (
                          <span key={r.id} className="inline-flex items-center gap-1 rounded-full bg-surface-2 px-2.5 py-1 text-xs text-ink-2">
                            {r.patron}
                            <button
                              onClick={() => onEliminarRegla(r.id)}
                              disabled={trabajando}
                              className="text-muted hover:text-down"
                              aria-label={`Eliminar regla ${r.patron}`}
                            >
                              ×
                            </button>
                          </span>
                        ))}
                      </div>
                    </li>
                  )
                })}
              </ul>
            )}
          </ChartCard>
        )
      })}

      <ChartCard titulo="Añadir una regla" subtitulo="Si la descripción contiene este texto (sin distinguir mayúsculas ni acentos), se asigna la categoría">
        <div className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-ink-2">Texto a buscar</span>
            <input
              className="w-56 rounded-xl border border-line px-3 py-2 text-sm"
              placeholder="Ej.: mercadona"
              value={nuevaRegla.patron}
              onChange={(e) => setNuevaRegla((n) => ({ ...n, patron: e.target.value }))}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-ink-2">Categoría</span>
            <select
              className="rounded-xl border border-line px-3 py-2 text-sm"
              value={nuevaRegla.categoriaId}
              onChange={(e) => setNuevaRegla((n) => ({ ...n, categoriaId: e.target.value }))}
            >
              <option value="">Selecciona...</option>
              {categorias.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nombre}
                </option>
              ))}
            </select>
          </label>
          <button
            onClick={onCrearRegla}
            disabled={trabajando}
            className="rounded-xl bg-accent px-4 py-2 text-sm font-medium text-on-accent hover:opacity-90 disabled:opacity-50"
          >
            Añadir regla
          </button>
        </div>
      </ChartCard>

      <ChartCard titulo="Añadir una categoría">
        <div className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-ink-2">Nombre</span>
            <input
              className="w-56 rounded-xl border border-line px-3 py-2 text-sm"
              value={nuevaCategoria.nombre}
              onChange={(e) => setNuevaCategoria((n) => ({ ...n, nombre: e.target.value }))}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-ink-2">Tipo</span>
            <select
              className="rounded-xl border border-line px-3 py-2 text-sm"
              value={nuevaCategoria.tipo}
              onChange={(e) => setNuevaCategoria((n) => ({ ...n, tipo: e.target.value as TipoCategoria }))}
            >
              <option value="GASTO">Gasto</option>
              <option value="INGRESO">Ingreso</option>
              <option value="NEUTRO">Neutra</option>
            </select>
          </label>
          <button
            onClick={onCrearCategoria}
            disabled={trabajando}
            className="rounded-xl bg-accent px-4 py-2 text-sm font-medium text-on-accent hover:opacity-90 disabled:opacity-50"
          >
            Añadir categoría
          </button>
        </div>
      </ChartCard>
    </div>
  )
}
