import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, getToken, setToken } from '../api/client'
import { renderConProveedores } from '../test/utils'
import { AuthProvider, useAuth } from './AuthContext'

vi.mock('../api/auth', () => ({ login: vi.fn(), register: vi.fn() }))
import * as authApi from '../api/auth'

const ana = { id: 7, nombre: 'Ana', email: 'ana@correo.es' }

function Sonda() {
  const { usuario, login, register, logout } = useAuth()
  return (
    <div>
      <p data-testid="quien">{usuario ? usuario.email : 'nadie'}</p>
      <button onClick={() => login('ana@correo.es', 'x')}>login</button>
      <button onClick={() => register('Ana', 'ana@correo.es', 'x')}>registro</button>
      <button onClick={logout}>logout</button>
    </div>
  )
}

function dibujar() {
  return renderConProveedores(
    <AuthProvider>
      <Routes>
        <Route path="/" element={<Sonda />} />
        <Route path="/login" element={<p>Pantalla de login</p>} />
      </Routes>
    </AuthProvider>,
  )
}

beforeEach(() => {
  setToken(null)
  vi.mocked(authApi.login).mockReset().mockResolvedValue({ token: 'tok-login', usuario: ana })
  vi.mocked(authApi.register).mockReset().mockResolvedValue({ token: 'tok-registro', usuario: ana })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('AuthProvider', () => {
  it('empieza sin sesion si no hay nada guardado', () => {
    dibujar()
    expect(screen.getByTestId('quien')).toHaveTextContent('nadie')
  })

  it('iniciar sesion guarda el token y el usuario', async () => {
    dibujar()

    await userEvent.click(screen.getByText('login'))

    await waitFor(() => expect(screen.getByTestId('quien')).toHaveTextContent('ana@correo.es'))
    expect(getToken()).toBe('tok-login')
    expect(JSON.parse(localStorage.getItem('finanzas.usuario')!)).toEqual(ana)
  })

  it('registrarse inicia la sesion directamente', async () => {
    dibujar()

    await userEvent.click(screen.getByText('registro'))

    await waitFor(() => expect(screen.getByTestId('quien')).toHaveTextContent('ana@correo.es'))
    expect(getToken()).toBe('tok-registro')
  })

  it('cerrar sesion borra todo y vuelve al login', async () => {
    dibujar()
    await userEvent.click(screen.getByText('login'))
    await waitFor(() => expect(screen.getByTestId('quien')).toHaveTextContent('ana@correo.es'))

    await userEvent.click(screen.getByText('logout'))

    expect(await screen.findByText('Pantalla de login')).toBeInTheDocument()
    expect(getToken()).toBeNull()
    expect(localStorage.getItem('finanzas.usuario')).toBeNull()
  })

  it('recupera la sesion guardada al recargar la pagina', () => {
    setToken('tok-guardado')
    localStorage.setItem('finanzas.usuario', JSON.stringify(ana))

    dibujar()

    expect(screen.getByTestId('quien')).toHaveTextContent('ana@correo.es')
  })

  it('un usuario guardado ilegible no rompe: se arranca sin sesion', () => {
    setToken('tok-guardado')
    localStorage.setItem('finanzas.usuario', '{esto no es json')

    dibujar()

    expect(screen.getByTestId('quien')).toHaveTextContent('nadie')
  })

  it('sin token no se recupera el usuario aunque este guardado', () => {
    localStorage.setItem('finanzas.usuario', JSON.stringify(ana))

    dibujar()

    expect(screen.getByTestId('quien')).toHaveTextContent('nadie')
  })

  it('una respuesta 401 del servidor cierra la sesion y lleva al login', async () => {
    setToken('tok-caducado')
    localStorage.setItem('finanzas.usuario', JSON.stringify(ana))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 401 })))
    dibujar()
    expect(screen.getByTestId('quien')).toHaveTextContent('ana@correo.es')

    await apiFetch('/cosas').catch(() => {})

    expect(await screen.findByText('Pantalla de login')).toBeInTheDocument()
    expect(getToken()).toBeNull()
    expect(localStorage.getItem('finanzas.usuario')).toBeNull()
  })
})

describe('useAuth', () => {
  it('fuera del proveedor lanza un error claro', () => {
    const consola = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => renderConProveedores(<Sonda />)).toThrow('useAuth debe usarse dentro de AuthProvider')
    consola.mockRestore()
  })
})
