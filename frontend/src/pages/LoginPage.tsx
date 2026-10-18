import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/client'

const MIN_PASSWORD = 8
const EMAIL_VALIDO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function mensajeDeError(e: unknown, registro: boolean): string {
  if (e instanceof ApiError) {
    if (e.status === 401) return 'Email o contraseña incorrectos'
    if (e.status === 409) return 'Ya existe una cuenta con ese email. Inicia sesión.'
    if (e.status === 400) return e.message
    return registro ? 'No se ha podido crear la cuenta. Inténtalo de nuevo.' : 'No se ha podido iniciar sesión. Inténtalo de nuevo.'
  }
  // fetch lanza TypeError si no hay conexion con el servidor
  return 'No se puede conectar con el servidor. ¿Está en marcha la aplicación?'
}

export function LoginPage() {
  const { usuario, login, register } = useAuth()
  const navigate = useNavigate()
  const [modoRegistro, setModoRegistro] = useState(false)
  const [nombre, setNombre] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmacion, setConfirmacion] = useState('')
  const [verPassword, setVerPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  if (usuario) {
    return <Navigate to="/" replace />
  }

  const validar = (): string | null => {
    if (modoRegistro && !nombre.trim()) return 'Indica tu nombre'
    if (!EMAIL_VALIDO.test(email.trim())) return 'El email no es válido'
    if (!password) return 'Indica tu contraseña'
    if (modoRegistro) {
      if (password.length < MIN_PASSWORD) return `La contraseña debe tener al menos ${MIN_PASSWORD} caracteres`
      if (password !== confirmacion) return 'Las contraseñas no coinciden'
    }
    return null
  }

  const onSubmit = async (evento: FormEvent) => {
    evento.preventDefault()
    const problema = validar()
    if (problema) {
      setError(problema)
      return
    }
    setError(null)
    setEnviando(true)
    try {
      if (modoRegistro) {
        await register(nombre.trim(), email.trim(), password)
      } else {
        await login(email.trim(), password)
      }
      navigate('/')
    } catch (e) {
      setError(mensajeDeError(e, modoRegistro))
    } finally {
      setEnviando(false)
    }
  }

  const alternarModo = () => {
    setModoRegistro((m) => !m)
    setError(null)
    setPassword('')
    setConfirmacion('')
    setVerPassword(false)
  }

  const campo = 'w-full rounded-xl border border-line px-3 py-2.5 text-sm'

  return (
    <div className="flex min-h-screen items-center justify-center bg-page px-4">
      <div className="w-full max-w-sm rounded-3xl bg-surface p-8">
        <h1 className="mb-1 text-3xl font-bold tracking-tight text-ink">Finanzas</h1>
        <p className="mb-6 text-sm text-muted">
          {modoRegistro ? 'Crea tu cuenta. Tus datos se guardan solo en este ordenador.' : 'Inicia sesión para continuar'}
        </p>

        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          {modoRegistro && (
            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium text-ink">Nombre</span>
              <input
                className={campo}
                value={nombre}
                autoComplete="name"
                maxLength={80}
                onChange={(e) => setNombre(e.target.value)}
              />
            </label>
          )}
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-ink">Email</span>
            <input
              type="email"
              className={campo}
              value={email}
              autoComplete="email"
              autoFocus
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-ink">Contraseña</span>
            <span className="relative">
              <input
                type={verPassword ? 'text' : 'password'}
                className={`${campo} pr-20`}
                value={password}
                autoComplete={modoRegistro ? 'new-password' : 'current-password'}
                onChange={(e) => setPassword(e.target.value)}
              />
              <button
                type="button"
                onClick={() => setVerPassword((v) => !v)}
                className="absolute inset-y-0 right-3 text-xs font-medium text-muted hover:text-ink"
                aria-label={verPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
              >
                {verPassword ? 'Ocultar' : 'Mostrar'}
              </button>
            </span>
            {modoRegistro && <span className="text-xs text-muted">Mínimo {MIN_PASSWORD} caracteres</span>}
          </label>
          {modoRegistro && (
            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium text-ink">Repite la contraseña</span>
              <input
                type={verPassword ? 'text' : 'password'}
                className={campo}
                value={confirmacion}
                autoComplete="new-password"
                onChange={(e) => setConfirmacion(e.target.value)}
              />
            </label>
          )}

          {error && (
            <p role="alert" className="text-sm text-down">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={enviando}
            className="mt-2 rounded-xl bg-accent py-2.5 text-sm font-semibold text-on-accent transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {enviando ? 'Un momento...' : modoRegistro ? 'Crear cuenta' : 'Entrar'}
          </button>
        </form>

        <button className="mt-4 text-sm text-muted hover:text-ink" onClick={alternarModo}>
          {modoRegistro ? '¿Ya tienes cuenta? Inicia sesión' : '¿No tienes cuenta? Regístrate'}
        </button>
      </div>
    </div>
  )
}
