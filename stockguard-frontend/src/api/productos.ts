import type { Operacion } from '../domain/operaciones'
import type { Movimiento, Producto } from '../domain/tipos'
import { peticion } from './cliente'
import { ApiError } from './errores'

const RECURSO_POR_OPERACION: Record<Operacion, string> = {
  entrada: 'entradas',
  salida: 'salidas',
  ajuste: 'ajustes',
}

function rutaProducto(codigo: string): string {
  return `/productos/${encodeURIComponent(codigo)}`
}

function exigirLista<T>(datos: T[] | undefined): T[] {
  if (!Array.isArray(datos)) throw new ApiError('respuesta', 200)
  return datos
}

function exigirObjeto<T>(datos: T | undefined): T {
  if (datos === undefined || datos === null || typeof datos !== 'object') {
    throw new ApiError('respuesta', 200)
  }
  return datos
}

/** GET /api/productos */
export async function listarProductos(signal?: AbortSignal): Promise<Producto[]> {
  return exigirLista(await peticion<Producto[]>('/productos', { signal }))
}

/** GET /api/productos/{codigo} */
export async function obtenerProducto(codigo: string, signal?: AbortSignal): Promise<Producto> {
  return exigirObjeto(await peticion<Producto>(rutaProducto(codigo), { signal }))
}

/** GET /api/productos/{codigo}/movimientos, del más reciente al más antiguo. */
export async function listarMovimientos(
  codigo: string,
  signal?: AbortSignal,
): Promise<Movimiento[]> {
  return exigirLista(
    await peticion<Movimiento[]>(`${rutaProducto(codigo)}/movimientos`, { signal }),
  )
}

/**
 * POST /api/productos/{codigo}/entradas | salidas | ajustes
 *
 * En entradas y salidas `cantidad` es la variación; en ajustes es la nueva
 * existencia total. Si tiene éxito el servidor responde 204 sin cuerpo.
 */
export async function registrarOperacion(
  operacion: Operacion,
  codigo: string,
  cantidad: number,
): Promise<void> {
  await peticion<never>(`${rutaProducto(codigo)}/${RECURSO_POR_OPERACION[operacion]}`, {
    method: 'POST',
    body: { cantidad },
  })
}
