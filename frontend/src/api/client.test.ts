import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, BASE_URL, apiFetch, getToken, onUnauthorizedResponse, setToken } from './client'

const fetchMock = vi.fn()

function respuesta(cuerpo: string | null, status = 200) {
  return new Response(cuerpo, { status })
}

function ultimaLlamada() {
  const [url, init] = fetchMock.mock.calls.at(-1)!
  return { url: url as string, init: init as RequestInit & { headers: Record<string, string> } }
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  setToken(null)
  onUnauthorizedResponse(() => {})
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('token', () => {
  it('se guarda en memoria y en localStorage, y se borra al cerrar sesion', () => {
    setToken('abc')
    expect(getToken()).toBe('abc')
    expect(localStorage.getItem('finanzas.token')).toBe('abc')

    setToken(null)
    expect(getToken()).toBeNull()
    expect(localStorage.getItem('finanzas.token')).toBeNull()
  })
})

describe('apiFetch', () => {
  it('hace GET a la URL base sin cabecera de autorizacion si no hay sesion', async () => {
    fetchMock.mockResolvedValue(respuesta('[1,2]'))

    await expect(apiFetch<number[]>('/cosas')).resolves.toEqual([1, 2])

    const { url, init } = ultimaLlamada()
    expect(url).toBe(`${BASE_URL}/cosas`)
    expect(init.method).toBe('GET')
    expect(init.headers.Authorization).toBeUndefined()
    expect(init.body).toBeUndefined()
  })

  it('envia el token como Bearer cuando hay sesion', async () => {
    setToken('mi-token')
    fetchMock.mockResolvedValue(respuesta('{}'))

    await apiFetch('/cosas')

    expect(ultimaLlamada().init.headers.Authorization).toBe('Bearer mi-token')
  })

  it('serializa el cuerpo a JSON y marca el tipo de contenido', async () => {
    fetchMock.mockResolvedValue(respuesta('{"id":1}'))

    await apiFetch('/cosas', { method: 'POST', body: { nombre: 'x' } })

    const { init } = ultimaLlamada()
    expect(init.method).toBe('POST')
    expect(init.body).toBe('{"nombre":"x"}')
    expect(init.headers['Content-Type']).toBe('application/json')
  })

  it('un FormData se envia tal cual, sin forzar el tipo de contenido', async () => {
    fetchMock.mockResolvedValue(respuesta('{"nuevos":1}'))
    const datos = new FormData()
    datos.append('file', new Blob(['a']), 'a.csv')

    await apiFetch('/importar', { method: 'POST', body: datos, isFormData: true })

    const { init } = ultimaLlamada()
    expect(init.body).toBe(datos)
    expect(init.headers['Content-Type']).toBeUndefined()
  })

  it('una respuesta 204 devuelve undefined sin intentar leer JSON', async () => {
    fetchMock.mockResolvedValue(respuesta(null, 204))
    await expect(apiFetch('/cosas', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('el error muestra el mensaje que manda el servidor', async () => {
    fetchMock.mockResolvedValue(respuesta('{"message":"El email no es válido"}', 400))

    const error = (await apiFetch('/auth/register').catch((e) => e)) as ApiError

    expect(error).toBeInstanceOf(ApiError)
    expect(error.message).toBe('El email no es válido')
    expect(error.status).toBe(400)
  })

  it('si el error no es JSON se usa el texto tal cual', async () => {
    fetchMock.mockResolvedValue(respuesta('Formato de fichero no reconocido: x.txt', 400))
    await expect(apiFetch('/importar')).rejects.toThrow('Formato de fichero no reconocido: x.txt')
  })

  it('un error sin cuerpo usa un mensaje generico con el codigo', async () => {
    fetchMock.mockResolvedValue(respuesta('', 500))
    await expect(apiFetch('/cosas')).rejects.toThrow('Error 500')
  })

  it('un JSON de error sin campo message se devuelve entero como texto', async () => {
    fetchMock.mockResolvedValue(respuesta('{"error":"raro"}', 500))
    await expect(apiFetch('/cosas')).rejects.toThrow('{"error":"raro"}')
  })

  it('un 401 avisa para cerrar la sesion y tambien lanza el error', async () => {
    const alRechazar = vi.fn()
    onUnauthorizedResponse(alRechazar)
    fetchMock.mockResolvedValue(respuesta('', 401))

    await expect(apiFetch('/cosas')).rejects.toMatchObject({ status: 401 })
    expect(alRechazar).toHaveBeenCalledOnce()
  })

  it('un error de red se propaga sin convertirse en ApiError', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    await expect(apiFetch('/cosas')).rejects.toBeInstanceOf(TypeError)
  })
})
