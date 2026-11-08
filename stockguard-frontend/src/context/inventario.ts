import { createContext, useContext } from 'react'
import type { Producto } from '../domain/tipos'

export type EstadoConexion = 'comprobando' | 'conectado' | 'sin-conexion' | 'servicio-no-disponible'

export interface ValorInventario {
  productos: Producto[] | null
  cargando: boolean
  error: unknown
  actualizadoEn: Date | null
  /** Aumenta cada vez que los datos del inventario deben volver a consultarse. */
  revision: number
  conexion: EstadoConexion
  recargar: () => void
}

export const InventarioContext = createContext<ValorInventario | null>(null)

export function useInventario(): ValorInventario {
  const valor = useContext(InventarioContext)
  if (!valor) throw new Error('useInventario debe usarse dentro de InventarioProvider')
  return valor
}
