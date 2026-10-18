import { useEffect } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { ResumenPage } from './pages/ResumenPage'
import { FinanzasDiariasPage } from './pages/FinanzasDiariasPage'
import { InversionPage } from './pages/InversionPage'
import { ImportarPage } from './pages/ImportarPage'
import { CategoriasPage } from './pages/CategoriasPage'
import { CuentaPage } from './pages/CuentaPage'
import { EvolucionHistoricaPage } from './pages/EvolucionHistoricaPage'
import { iniciarLatido } from './lib/latido'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
})

export default function App() {
  useEffect(() => iniciarLatido(), [])

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<ProtectedRoute />}>
              <Route element={<Layout />}>
                <Route path="/" element={<ResumenPage />} />
                <Route path="/finanzas-diarias" element={<FinanzasDiariasPage />} />
                <Route path="/inversion" element={<InversionPage />} />
                <Route path="/categorias" element={<CategoriasPage />} />
                <Route path="/cuenta" element={<CuentaPage />} />
                <Route path="/importar" element={<ImportarPage />} />
                <Route path="/evolucion" element={<EvolucionHistoricaPage />} />
              </Route>
            </Route>
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
