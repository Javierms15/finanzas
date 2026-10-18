export interface Usuario {
  id: number
  nombre: string
  email: string
}

export interface TokenResponse {
  token: string
  usuario: Usuario
}

export type TipoCuenta = 'NOMINA' | 'TARJETA' | 'AHORRO'

export interface CuentaBancaria {
  id: number
  banco: string
  tipo: TipoCuenta
  alias: string
  moneda: string
  saldoInicial: number
  tasaInteresAnual: number | null
}

export interface CuentaConSaldoActual {
  id: number
  banco: string
  tipo: TipoCuenta
  alias: string
  moneda: string
  saldoInicial: number
  tasaInteresAnual: number | null
  saldoActual: number
}

export type TipoCategoria = 'GASTO' | 'INGRESO' | 'NEUTRO'

export interface Categoria {
  id: number
  nombre: string
  tipo: TipoCategoria
  color: string | null
}

export interface ReglaCategorizacion {
  id: number
  categoria: Categoria
  patron: string
  prioridad: number
}

export type OrigenMovimiento = 'CSV' | 'PDF' | 'MANUAL'

export interface Movimiento {
  id: number
  fecha: string
  importe: number
  descripcionOriginal: string
  categoria: Categoria | null
  etiquetaManual: string | null
  categoriaManual: boolean
  origen: OrigenMovimiento
}

export interface ResultadoSugeridas {
  categoriasCreadas: number
  reglasCreadas: number
}

export interface AgregacionMensual {
  mes: string
  totalIngresos: number
  totalGastos: number
  balance: number
}

export interface ResultadoImportacion {
  totalEncontrados: number
  nuevos: number
  duplicados: number
  duplicadosProbables: number
}

export interface SnapshotPatrimonio {
  id: number
  fecha: string
  saldoEfectivoTotal: number
  valorCarteraInversion: number
  patrimonioTotal: number
}

export interface ProyeccionEfectivo {
  patrimonioActual: number
  ingresoMedio: number
  gastoMedio: number
  meses: number
  patrimonioProyectado: number
}

export interface SimulacionAjusteCategoria {
  categoriaId: number
  meses: number
  ajusteMensual: number
  gastoMedioCategoriaActual: number
  gastoMedioCategoriaSimulado: number
  gastoMedioTotalActual: number
  gastoMedioTotalSimulado: number
  patrimonioActual: number
  patrimonioProyectadoActual: number
  patrimonioProyectadoSimulado: number
}

export type TipoActivo = 'ACCION' | 'ETF' | 'FONDO' | 'CRIPTO'

export interface Activo {
  id: number
  ticker: string
  nombre: string
  tipo: TipoActivo
}

export type TipoMovimientoInversion = 'COMPRA' | 'VENTA' | 'DIVIDENDO'

export interface PosicionValorada {
  posicionId: number
  ticker: string
  nombreActivo: string
  tipo: TipoActivo
  cantidad: number
  precioMedioCompra: number
  precioActual: number
  valorActual: number
  rendimiento: number
}

export interface ProyeccionInversion {
  valorActual: number
  retornoEsperado: number
  anios: number
  aportacionAnual: number
  valorProyectado: number
}

export interface MovimientoPrevisto {
  id: number
  fecha: string
  importe: number
  descripcion: string
  cumplido: boolean
}

export interface ResultadoImportacionPosiciones {
  totalEncontradas: number
  actualizadas: number
}
