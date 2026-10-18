import { useState, type ReactNode } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

function Icono({ children }: { children: ReactNode }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5 shrink-0"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  )
}

const ENLACES = [
  {
    to: '/',
    etiqueta: 'Resumen',
    corta: 'Resumen',
    fin: true,
    icono: (
      <Icono>
        <path d="M4 11.5 12 5l8 6.5V19a1 1 0 0 1-1 1h-4v-5h-6v5H5a1 1 0 0 1-1-1z" />
      </Icono>
    ),
  },
  {
    to: '/finanzas-diarias',
    etiqueta: 'Finanzas diarias',
    corta: 'Diario',
    icono: (
      <Icono>
        <rect x="3.5" y="5" width="17" height="15" rx="2.5" />
        <path d="M3.5 10h17M8 3.5v3M16 3.5v3" />
      </Icono>
    ),
  },
  {
    to: '/inversion',
    etiqueta: 'Inversión',
    corta: 'Inversión',
    icono: (
      <Icono>
        <path d="M4 17l5-5 3.5 3.5L20 8" />
        <path d="M15 8h5v5" />
      </Icono>
    ),
  },
  {
    to: '/categorias',
    etiqueta: 'Categorías',
    corta: 'Categorías',
    icono: (
      <Icono>
        <path d="M3.5 12.5V5a1.5 1.5 0 0 1 1.5-1.5h7.5L21 12l-8.5 8.5z" />
        <circle cx="8" cy="8" r="1.2" />
      </Icono>
    ),
  },
  {
    to: '/importar',
    etiqueta: 'Importar archivos',
    corta: 'Importar',
    icono: (
      <Icono>
        <path d="M12 16V4M7.5 8.5 12 4l4.5 4.5" />
        <path d="M4.5 15v3.5a1 1 0 0 0 1 1h13a1 1 0 0 0 1-1V15" />
      </Icono>
    ),
  },
  {
    to: '/evolucion',
    etiqueta: 'Evolución histórica',
    corta: 'Histórico',
    icono: (
      <Icono>
        <path d="M12 7v5l3 2" />
        <path d="M4.5 12a7.5 7.5 0 1 0 2.2-5.3L4.5 9" />
        <path d="M4.5 4.5V9H9" />
      </Icono>
    ),
  },
]

type Tema = 'dark' | 'light'

function temaActual(): Tema {
  return document.documentElement.dataset.theme === 'light' ? 'light' : 'dark'
}

function BotonTema() {
  const [tema, setTema] = useState<Tema>(temaActual)

  const alternar = () => {
    const siguiente: Tema = tema === 'dark' ? 'light' : 'dark'
    document.documentElement.dataset.theme = siguiente
    try {
      localStorage.setItem('finanzas.tema', siguiente)
    } catch {
      // sin almacenamiento disponible: el cambio solo dura la sesion
    }
    setTema(siguiente)
  }

  return (
    <button
      onClick={alternar}
      className="flex items-center gap-2 text-sm text-muted transition-colors hover:text-ink"
      title="Cambiar entre tema oscuro y claro"
    >
      <Icono>
        {tema === 'dark' ? (
          <path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z" />
        ) : (
          <>
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4" />
          </>
        )}
      </Icono>
      {tema === 'dark' ? 'Tema oscuro' : 'Tema claro'}
    </button>
  )
}

export function Layout() {
  const { usuario, logout } = useAuth()

  return (
    <div className="min-h-screen bg-page text-ink md:flex">
      <aside className="hidden w-60 shrink-0 flex-col justify-between border-r border-line px-4 py-6 md:flex">
        <div>
          <div className="px-3 pb-6 text-xl font-bold tracking-tight">Finanzas</div>
          <nav className="flex flex-col gap-1">
            {ENLACES.map((enlace) => (
              <NavLink
                key={enlace.to}
                to={enlace.to}
                end={enlace.fin}
                className={({ isActive }) =>
                  `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors ${
                    isActive ? 'bg-surface text-ink' : 'text-muted hover:bg-surface hover:text-ink'
                  }`
                }
              >
                {enlace.icono}
                {enlace.etiqueta}
              </NavLink>
            ))}
          </nav>
        </div>
        <div className="flex flex-col gap-3 px-3 text-sm">
          <BotonTema />
          <div className="border-t border-line pt-3">
            <div className="mb-1 text-ink-2">{usuario?.nombre}</div>
            <button onClick={logout} className="text-muted transition-colors hover:text-ink">
              Cerrar sesión
            </button>
          </div>
        </div>
      </aside>

      <main className="min-w-0 flex-1 px-4 pb-28 pt-6 sm:px-8 md:pb-10 md:pt-10">
        <div className="mx-auto flex max-w-5xl flex-col gap-6">
          <Outlet />
        </div>
      </main>

      <nav className="fixed inset-x-0 bottom-0 z-20 flex justify-around border-t border-line bg-page/95 px-2 pb-3 pt-2 backdrop-blur md:hidden">
        {ENLACES.map((enlace) => (
          <NavLink
            key={enlace.to}
            to={enlace.to}
            end={enlace.fin}
            className={({ isActive }) =>
              `flex min-w-0 flex-1 flex-col items-center gap-1 rounded-lg py-1 text-[11px] font-medium ${
                isActive ? 'text-ink' : 'text-muted'
              }`
            }
          >
            {enlace.icono}
            <span className="truncate">{enlace.corta}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
