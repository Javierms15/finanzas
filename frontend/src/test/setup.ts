import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Las fechas se formatean en hora local: se fija la zona para que los tests no dependan de la maquina
process.env.TZ = 'Europe/Madrid'

// jsdom no trae ResizeObserver y los graficos de Recharts lo necesitan para medir su contenedor
globalThis.ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
}

afterEach(() => {
  cleanup()
  localStorage.clear()
})
