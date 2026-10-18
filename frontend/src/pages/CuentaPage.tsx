import { useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { cambiarPassword } from '../api/auth'
import { ApiError } from '../api/client'
import { ChartCard } from '../components/ChartCard'

const MIN_PASSWORD = 8

export function CuentaPage() {
  const { usuario, logout } = useAuth()
  const [actual, setActual] = useState('')
  const [nueva, setNueva] = useState('')
  const [repetida, setRepetida] = useState('')
  const [ver, setVer] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [hecho, setHecho] = useState(false)

  const onSubmit = async (evento: FormEvent) => {
    evento.preventDefault()
    setHecho(false)
    if (!actual) return setError('Indica tu contraseña actual')
    if (nueva.length < MIN_PASSWORD) return setError(`La contraseña nueva debe tener al menos ${MIN_PASSWORD} caracteres`)
    if (nueva !== repetida) return setError('Las contraseñas nuevas no coinciden')
    setError(null)
    setEnviando(true)
    try {
      await cambiarPassword(actual, nueva)
      setActual('')
      setNueva('')
      setRepetida('')
      setHecho(true)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se ha podido cambiar la contraseña')
    } finally {
      setEnviando(false)
    }
  }

  const campo = 'w-full max-w-xs rounded-xl border border-line px-3 py-2.5 text-sm'

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight text-ink">Mi cuenta</h1>
        <p className="text-sm text-muted">
          {usuario?.nombre} · {usuario?.email}
        </p>
      </div>

      <ChartCard
        titulo="Cambiar contraseña"
        subtitulo="No hay recuperación por correo: si la olvidas, tendrá que restablecerla quien administre esta instalación"
      >
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-ink">Contraseña actual</span>
            <input
              type={ver ? 'text' : 'password'}
              className={campo}
              value={actual}
              autoComplete="current-password"
              onChange={(e) => setActual(e.target.value)}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-ink">Contraseña nueva</span>
            <input
              type={ver ? 'text' : 'password'}
              className={campo}
              value={nueva}
              autoComplete="new-password"
              onChange={(e) => setNueva(e.target.value)}
            />
            <span className="text-xs text-muted">Mínimo {MIN_PASSWORD} caracteres</span>
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-ink">Repite la contraseña nueva</span>
            <input
              type={ver ? 'text' : 'password'}
              className={campo}
              value={repetida}
              autoComplete="new-password"
              onChange={(e) => setRepetida(e.target.value)}
            />
          </label>
          <label className="flex items-center gap-2 text-sm text-ink-2">
            <input type="checkbox" checked={ver} onChange={(e) => setVer(e.target.checked)} />
            Mostrar contraseñas
          </label>

          {error && (
            <p role="alert" className="text-sm text-down">
              {error}
            </p>
          )}
          {hecho && <p className="text-sm text-up">Contraseña cambiada. Úsala la próxima vez que inicies sesión.</p>}

          <div>
            <button
              type="submit"
              disabled={enviando}
              className="rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-on-accent hover:opacity-90 disabled:opacity-50"
            >
              {enviando ? 'Guardando...' : 'Cambiar contraseña'}
            </button>
          </div>
        </form>
      </ChartCard>

      <ChartCard titulo="Sesión">
        <button onClick={logout} className="rounded-xl bg-surface-2 px-4 py-2.5 text-sm font-medium text-ink hover:opacity-90">
          Cerrar sesión
        </button>
      </ChartCard>
    </div>
  )
}
