import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { BASE_URL } from '../api/client'
import { iniciarLatido } from './latido'

const fetchMock = vi.fn()
const beaconMock = vi.fn()

beforeEach(() => {
  vi.useFakeTimers()
  fetchMock.mockReset().mockResolvedValue(new Response(null, { status: 204 }))
  beaconMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  Object.defineProperty(navigator, 'sendBeacon', { value: beaconMock, configurable: true, writable: true })
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('iniciarLatido', () => {
  it('envia un latido nada mas empezar, como peticion simple sin preflight', () => {
    const parar = iniciarLatido()

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledWith(`${BASE_URL}/sistema/latido`, { method: 'POST', mode: 'no-cors', keepalive: true })
    parar()
  })

  it('repite el latido cada cinco segundos', () => {
    const parar = iniciarLatido()

    vi.advanceTimersByTime(5000)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    vi.advanceTimersByTime(10000)
    expect(fetchMock).toHaveBeenCalledTimes(4)
    parar()
  })

  it('avisa del cierre con sendBeacon cuando la pagina se descarga', () => {
    const parar = iniciarLatido()

    window.dispatchEvent(new Event('pagehide'))

    expect(beaconMock).toHaveBeenCalledWith(`${BASE_URL}/sistema/cierre`, '')
    parar()
  })

  it('al parar deja de latir y de avisar del cierre', () => {
    const parar = iniciarLatido()
    parar()

    vi.advanceTimersByTime(30000)
    window.dispatchEvent(new Event('pagehide'))

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(beaconMock).not.toHaveBeenCalled()
  })

  it('un fallo de red no rompe nada', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

    const parar = iniciarLatido()
    await vi.advanceTimersByTimeAsync(5000)

    expect(fetchMock).toHaveBeenCalledTimes(2)
    parar()
  })
})
