import { BASE_URL } from '../api/client'

const INTERVALO_MS = 5000

/**
 * Avisa al servidor de que hay una pagina abierta (un latido cada pocos segundos) y de cuando se cierra.
 * El lanzador de escritorio lo usa para apagar todo al cerrar la web. Son peticiones simples y sin
 * cabeceras propias (no necesitan preflight CORS) y se ignoran sus errores: nunca deben afectar a la app.
 */
export function iniciarLatido(): () => void {
  const latido = () => {
    fetch(`${BASE_URL}/sistema/latido`, { method: 'POST', mode: 'no-cors', keepalive: true }).catch(() => {})
  }
  // sendBeacon sobrevive al cierre de la pagina; con cuerpo de texto es una peticion simple (sin preflight)
  const cierre = () => {
    navigator.sendBeacon(`${BASE_URL}/sistema/cierre`, '')
  }

  latido()
  const temporizador = window.setInterval(latido, INTERVALO_MS)
  window.addEventListener('pagehide', cierre)
  return () => {
    window.clearInterval(temporizador)
    window.removeEventListener('pagehide', cierre)
  }
}
