/**
 * Umbral del monitor de alertas (`MonitorAlertas.UMBRAL_STOCK`).
 * Un producto genera alerta cuando su existencia es estrictamente menor.
 */
export const UMBRAL_STOCK = 5

/** Intervalo con que `MonitorAlertas` revisa el inventario. */
export const INTERVALO_MONITOR_SEGUNDOS = 10

export type EstadoStock = 'agotado' | 'bajo' | 'disponible'

export function estadoStock(cantidad: number): EstadoStock {
  if (cantidad <= 0) return 'agotado'
  if (cantidad < UMBRAL_STOCK) return 'bajo'
  return 'disponible'
}

/** Aplica la misma condición que el monitor de alertas. */
export function requiereAtencion(cantidad: number): boolean {
  return cantidad < UMBRAL_STOCK
}

export const ETIQUETA_ESTADO: Record<EstadoStock, string> = {
  agotado: 'Agotado',
  bajo: 'Stock bajo',
  disponible: 'Disponible',
}
