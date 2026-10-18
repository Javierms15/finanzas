import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { renderConProveedores } from '../test/utils'
import { LoginPage } from './LoginPage'

const auth = {
  usuario: null as { id: number; nombre: string; email: string } | null,
  cargandoSesion: false,
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
}

vi.mock('../auth/AuthContext', () => ({ useAuth: () => auth }))

function dibujar() {
  return renderConProveedores(
    <Routes>
      <Route path="/" element={<p>Pantalla de inicio</p>} />
      <Route path="/login" element={<LoginPage />} />
    </Routes>,
    '/login',
  )
}

const campoEmail = () => screen.getByLabelText('Email')
const campoPassword = () => screen.getByLabelText(/^Contraseña/)

beforeEach(() => {
  auth.usuario = null
  auth.login.mockReset().mockResolvedValue(undefined)
  auth.register.mockReset().mockResolvedValue(undefined)
})

describe('LoginPage: iniciar sesion', () => {
  it('con los campos vacios pide un email valido y no llama al servidor', async () => {
    dibujar()

    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(screen.getByRole('alert')).toHaveTextContent('El email no es válido')
    expect(auth.login).not.toHaveBeenCalled()
  })

  it('con email correcto pero sin contraseña la pide', async () => {
    dibujar()

    await userEvent.type(campoEmail(), 'ana@correo.es')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Indica tu contraseña')
  })

  it('un email sin dominio se rechaza en el navegador', async () => {
    dibujar()

    await userEvent.type(campoEmail(), 'ana@correo')
    await userEvent.type(campoPassword(), 'x')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(screen.getByRole('alert')).toHaveTextContent('El email no es válido')
  })

  it('entra con las credenciales (el email se recorta) y va a la pantalla de inicio', async () => {
    dibujar()

    await userEvent.type(campoEmail(), '  ana@correo.es  ')
    await userEvent.type(campoPassword(), 'mi-contrasena')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    await waitFor(() => expect(auth.login).toHaveBeenCalledWith('ana@correo.es', 'mi-contrasena'))
    expect(await screen.findByText('Pantalla de inicio')).toBeInTheDocument()
  })

  it('con credenciales incorrectas (401) muestra el mensaje y se queda en el login', async () => {
    auth.login.mockRejectedValue(new ApiError('lo que sea', 401))
    dibujar()

    await userEvent.type(campoEmail(), 'ana@correo.es')
    await userEvent.type(campoPassword(), 'mala')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Email o contraseña incorrectos')
    expect(screen.queryByText('Pantalla de inicio')).not.toBeInTheDocument()
  })

  it('si no hay conexion con el servidor lo explica', async () => {
    auth.login.mockRejectedValue(new TypeError('Failed to fetch'))
    dibujar()

    await userEvent.type(campoEmail(), 'ana@correo.es')
    await userEvent.type(campoPassword(), 'x')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No se puede conectar con el servidor')
  })

  it('un error inesperado del servidor da un mensaje generico', async () => {
    auth.login.mockRejectedValue(new ApiError('boom', 500))
    dibujar()

    await userEvent.type(campoEmail(), 'ana@correo.es')
    await userEvent.type(campoPassword(), 'x')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No se ha podido iniciar sesión')
  })

  it('el boton mostrar/ocultar cambia el tipo del campo de contraseña', async () => {
    dibujar()

    expect(campoPassword()).toHaveAttribute('type', 'password')
    await userEvent.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))
    expect(campoPassword()).toHaveAttribute('type', 'text')
    await userEvent.click(screen.getByRole('button', { name: 'Ocultar contraseña' }))
    expect(campoPassword()).toHaveAttribute('type', 'password')
  })

  it('si ya hay sesion redirige a la pantalla de inicio', () => {
    auth.usuario = { id: 1, nombre: 'Ana', email: 'ana@correo.es' }
    dibujar()

    expect(screen.getByText('Pantalla de inicio')).toBeInTheDocument()
  })
})

describe('LoginPage: crear cuenta', () => {
  async function irARegistro() {
    dibujar()
    await userEvent.click(screen.getByRole('button', { name: '¿No tienes cuenta? Regístrate' }))
  }

  it('el formulario de registro pide nombre y repetir la contraseña', async () => {
    await irARegistro()

    expect(screen.getByLabelText('Nombre')).toBeInTheDocument()
    expect(screen.getByLabelText('Repite la contraseña')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear cuenta' })).toBeInTheDocument()
  })

  it('exige el nombre', async () => {
    await irARegistro()

    await userEvent.type(campoEmail(), 'ana@correo.es')
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Indica tu nombre')
  })

  it('exige al menos 8 caracteres', async () => {
    await irARegistro()

    await userEvent.type(screen.getByLabelText('Nombre'), 'Ana')
    await userEvent.type(campoEmail(), 'ana@correo.es')
    await userEvent.type(campoPassword(), 'corta')
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    expect(screen.getByRole('alert')).toHaveTextContent('al menos 8 caracteres')
    expect(auth.register).not.toHaveBeenCalled()
  })

  it('exige que las dos contraseñas coincidan', async () => {
    await irARegistro()

    await userEvent.type(screen.getByLabelText('Nombre'), 'Ana')
    await userEvent.type(campoEmail(), 'ana@correo.es')
    await userEvent.type(campoPassword(), 'contrasena-1')
    await userEvent.type(screen.getByLabelText('Repite la contraseña'), 'contrasena-2')
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Las contraseñas no coinciden')
  })

  it('crea la cuenta con los datos recortados y entra', async () => {
    await irARegistro()

    await userEvent.type(screen.getByLabelText('Nombre'), '  Ana Prueba ')
    await userEvent.type(campoEmail(), 'ana@correo.es')
    await userEvent.type(campoPassword(), 'contrasena-1')
    await userEvent.type(screen.getByLabelText('Repite la contraseña'), 'contrasena-1')
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    await waitFor(() => expect(auth.register).toHaveBeenCalledWith('Ana Prueba', 'ana@correo.es', 'contrasena-1'))
    expect(await screen.findByText('Pantalla de inicio')).toBeInTheDocument()
  })

  it('un email ya registrado (409) invita a iniciar sesion', async () => {
    auth.register.mockRejectedValue(new ApiError('Ya existe', 409))
    await irARegistro()

    await userEvent.type(screen.getByLabelText('Nombre'), 'Ana')
    await userEvent.type(campoEmail(), 'ana@correo.es')
    await userEvent.type(campoPassword(), 'contrasena-1')
    await userEvent.type(screen.getByLabelText('Repite la contraseña'), 'contrasena-1')
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ya existe una cuenta con ese email. Inicia sesión.')
  })

  it('un 400 del servidor muestra su mensaje', async () => {
    auth.register.mockRejectedValue(new ApiError('La contraseña debe tener entre 8 y 72 caracteres', 400))
    await irARegistro()

    await userEvent.type(screen.getByLabelText('Nombre'), 'Ana')
    await userEvent.type(campoEmail(), 'ana@correo.es')
    await userEvent.type(campoPassword(), 'contrasena-1')
    await userEvent.type(screen.getByLabelText('Repite la contraseña'), 'contrasena-1')
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('La contraseña debe tener entre 8 y 72 caracteres')
  })

  it('al cambiar de modo se limpian el error y las contraseñas', async () => {
    await irARegistro()
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))
    expect(screen.getByRole('alert')).toBeInTheDocument()
    await userEvent.type(campoPassword(), 'algo')

    await userEvent.click(screen.getByRole('button', { name: '¿Ya tienes cuenta? Inicia sesión' }))

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(campoPassword()).toHaveValue('')
  })
})
