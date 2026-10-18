import type { ReactElement } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

/** Los importes salen con un espacio de no separacion antes del euro: se normaliza para comparar con texto normal. */
export function normalizar(texto: string | null | undefined) {
  return (texto ?? '').replace(/ /g, ' ').replace(/\s+/g, ' ').trim()
}

export function renderConProveedores(ui: ReactElement, ruta = '/') {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={cliente}>
      <MemoryRouter initialEntries={[ruta]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
}
