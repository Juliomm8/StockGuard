import { listarMovimientos } from '../api/productos'
import { useInventario } from '../context/inventario'
import type { Movimiento } from '../domain/tipos'
import { useRecurso, type Recurso } from './useRecurso'

const PETICIONES_SIMULTANEAS = 4

export interface MovimientosConsolidados {
  /** Movimientos de todos los productos consultados, del más reciente al más antiguo. */
  movimientos: Movimiento[]
  productosConsultados: number
  productosFallidos: number
}

/**
 * La API solo ofrece el historial por producto, así que el resumen general se
 * obtiene consultando `/productos/{codigo}/movimientos` para cada producto.
 */
export async function consolidarMovimientos(
  codigos: string[],
  signal: AbortSignal,
  consultar: typeof listarMovimientos = listarMovimientos,
): Promise<MovimientosConsolidados> {
  const movimientos: Movimiento[] = []
  let fallidos = 0
  let siguiente = 0

  async function trabajador() {
    while (siguiente < codigos.length && !signal.aborted) {
      const codigo = codigos[siguiente++]
      try {
        movimientos.push(...(await consultar(codigo, signal)))
      } catch {
        fallidos++
      }
    }
  }

  await Promise.all(
    Array.from({ length: Math.min(PETICIONES_SIMULTANEAS, codigos.length) }, trabajador),
  )

  movimientos.sort(
    (a, b) =>
      Date.parse(b.fechaMovimiento) - Date.parse(a.fechaMovimiento) || b.id - a.id,
  )
  return { movimientos, productosConsultados: codigos.length, productosFallidos: fallidos }
}

export function useMovimientosRecientes(): Recurso<MovimientosConsolidados> {
  const { productos, revision } = useInventario()
  const codigos = productos?.map((p) => p.codigo) ?? null
  const base = codigos ? `movimientos:${codigos.join(',')}` : null

  return useRecurso(base, revision, (signal) => consolidarMovimientos(codigos ?? [], signal))
}
