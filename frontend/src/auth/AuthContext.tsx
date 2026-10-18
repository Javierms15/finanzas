import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import * as authApi from '../api/auth'
import { getToken, onUnauthorizedResponse, setToken } from '../api/client'
import type { Usuario } from '../api/types'

interface AuthContextValue {
  usuario: Usuario | null
  cargandoSesion: boolean
  login: (email: string, password: string) => Promise<void>
  register: (nombre: string, email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

const USUARIO_KEY = 'finanzas.usuario'

function leerSesionGuardada(): Usuario | null {
  const guardado = localStorage.getItem(USUARIO_KEY)
  if (!getToken() || !guardado) return null
  try {
    return JSON.parse(guardado) as Usuario
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  // La sesion se lee una sola vez al montar (inicializador), no en un efecto: asi no hay un primer render sin usuario
  const [usuario, setUsuario] = useState<Usuario | null>(leerSesionGuardada)
  const navigate = useNavigate()

  useEffect(() => {
    onUnauthorizedResponse(() => {
      setToken(null)
      localStorage.removeItem(USUARIO_KEY)
      setUsuario(null)
      navigate('/login')
    })
  }, [navigate])

  const value = useMemo<AuthContextValue>(() => {
    const guardarSesion = (respuesta: { token: string; usuario: Usuario }) => {
      setToken(respuesta.token)
      localStorage.setItem(USUARIO_KEY, JSON.stringify(respuesta.usuario))
      setUsuario(respuesta.usuario)
    }
    return {
      usuario,
      cargandoSesion: false,
      login: async (email, password) => guardarSesion(await authApi.login(email, password)),
      register: async (nombre, email, password) => guardarSesion(await authApi.register(nombre, email, password)),
      logout: () => {
        setToken(null)
        localStorage.removeItem(USUARIO_KEY)
        setUsuario(null)
        navigate('/login')
      },
    }
  }, [usuario, navigate])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth debe usarse dentro de AuthProvider')
  return ctx
}
