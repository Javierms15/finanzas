import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './AuthContext'

export function ProtectedRoute() {
  const { usuario, cargandoSesion } = useAuth()

  if (cargandoSesion) {
    return null
  }

  if (!usuario) {
    return <Navigate to="/login" replace />
  }

  return <Outlet />
}
