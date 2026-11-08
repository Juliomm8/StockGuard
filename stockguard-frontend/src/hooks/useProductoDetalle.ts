import { listarMovimientos, obtenerProducto } from '../api/productos'
import { useInventario } from '../context/inventario'
import type { Movimiento, Producto } from '../domain/tipos'
import { useRecurso, type Recurso } from './useRecurso'

export interface DetalleProducto {
  producto: Producto
  movimientos: Movimiento[]
}

/** Producto y su historial; se recargan después de cada movimiento registrado. */
export function useProductoDetalle(codigo: string): Recurso<DetalleProducto> {
  const { revision } = useInventario()
  return useRecurso(`producto:${codigo}`, revision, async (signal) => {
    const [producto, movimientos] = await Promise.all([
      obtenerProducto(codigo, signal),
      listarMovimientos(codigo, signal),
    ])
    return { producto, movimientos }
  })
}
