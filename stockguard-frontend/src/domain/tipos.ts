/** Producto tal como lo publica `ProductoDto` en la API REST. */
export interface Producto {
  codigo: string
  nombre: string
  cantidadDisponible: number
}

/** Valores de `TipoMovimiento` en el bundle de Inventario. */
export type TipoMovimiento = 'ENTRADA' | 'SALIDA' | 'AJUSTE'

/** Movimiento tal como lo publica `MovimientoDto` en la API REST. */
export interface Movimiento {
  id: number
  codigoProducto: string
  tipoMovimiento: TipoMovimiento
  cantidadModificada: number
  existenciaAnterior: number
  existenciaPosterior: number
  /** Instante en formato ISO-8601 (UTC). */
  fechaMovimiento: string
  observacion: string | null
}
