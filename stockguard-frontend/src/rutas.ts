export const RUTAS = {
  panel: '/',
  inventario: '/inventario',
  alertas: '/alertas',
} as const

export function rutaDetalle(codigo: string): string {
  return `${RUTAS.inventario}/${encodeURIComponent(codigo)}`
}
