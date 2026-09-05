import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const ENLACES = [
  { to: '/', etiqueta: 'Resumen', fin: true },
  { to: '/finanzas-diarias', etiqueta: 'Finanzas diarias' },
  { to: '/inversion', etiqueta: 'Inversión' },
  { to: '/importar', etiqueta: 'Importar archivos' },
  { to: '/evolucion', etiqueta: 'Evolución histórica' },
]

export function Layout() {
  const { usuario, logout } = useAuth()

  return (
    <div className="flex min-h-screen">
      <aside className="flex w-64 shrink-0 flex-col justify-between bg-slate-900 text-slate-100">
        <div>
          <div className="px-6 py-5 text-lg font-semibold tracking-tight">Finanzas</div>
          <nav className="flex flex-col gap-1 px-3">
            {ENLACES.map((enlace) => (
              <NavLink
                key={enlace.to}
                to={enlace.to}
                end={enlace.fin}
                className={({ isActive }) =>
                  `rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                    isActive ? 'bg-slate-700 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                  }`
                }
              >
                {enlace.etiqueta}
              </NavLink>
            ))}
          </nav>
        </div>
        <div className="border-t border-slate-800 px-6 py-4 text-sm">
          <div className="mb-2 text-slate-300">{usuario?.nombre}</div>
          <button onClick={logout} className="text-slate-400 hover:text-white">
            Cerrar sesión
          </button>
        </div>
      </aside>
      <main className="flex-1 overflow-y-auto bg-slate-50 p-8">
        <Outlet />
      </main>
    </div>
  )
}
