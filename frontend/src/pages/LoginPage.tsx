import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/client'

export function LoginPage() {
  const { usuario, login, register } = useAuth()
  const navigate = useNavigate()
  const [modoRegistro, setModoRegistro] = useState(false)
  const [nombre, setNombre] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  if (usuario) {
    return <Navigate to="/" replace />
  }

  const onSubmit = async (evento: FormEvent) => {
    evento.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      if (modoRegistro) {
        await register(nombre, email, password)
      } else {
        await login(email, password)
      }
      navigate('/')
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        setError('Credenciales inválidas')
      } else if (e instanceof ApiError && e.status === 409) {
        setError('Ya existe un usuario con ese email')
      } else {
        setError('No se ha podido conectar con el servidor')
      }
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100">
      <div className="w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="mb-1 text-xl font-semibold text-slate-900">Finanzas</h1>
        <p className="mb-6 text-sm text-slate-500">
          {modoRegistro ? 'Crea una cuenta nueva' : 'Inicia sesión para continuar'}
        </p>

        <form onSubmit={onSubmit} className="flex flex-col gap-4">
          {modoRegistro && (
            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium text-slate-700">Nombre</span>
              <input
                className="rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
                value={nombre}
                onChange={(e) => setNombre(e.target.value)}
                required
              />
            </label>
          )}
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-slate-700">Email</span>
            <input
              type="email"
              className="rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-slate-700">Contraseña</span>
            <input
              type="password"
              className="rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>

          {error && <p className="text-sm text-rose-600">{error}</p>}

          <button
            type="submit"
            disabled={enviando}
            className="mt-2 rounded-lg bg-slate-900 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:opacity-50"
          >
            {enviando ? 'Enviando...' : modoRegistro ? 'Crear cuenta' : 'Entrar'}
          </button>
        </form>

        <button
          className="mt-4 text-sm text-slate-500 hover:text-slate-700"
          onClick={() => {
            setModoRegistro((m) => !m)
            setError(null)
          }}
        >
          {modoRegistro ? '¿Ya tienes cuenta? Inicia sesión' : '¿No tienes cuenta? Regístrate'}
        </button>
      </div>
    </div>
  )
}
