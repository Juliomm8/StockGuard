import { contar } from './formato'
import { estadoStock, requiereAtencion, type EstadoStock } from './stock'
import type { Movimiento, Producto, TipoMovimiento } from './tipos'

export type FiltroEstado = 'todos' | EstadoStock
export type CampoOrden = 'codigo' | 'nombre' | 'cantidadDisponible'
export type Direccion = 'asc' | 'desc'

export interface CriteriosInventario {
  texto: string
  estado: FiltroEstado
  orden: CampoOrden
  direccion: Direccion
}

const comparadorTexto = new Intl.Collator('es', { numeric: true, sensitivity: 'base' })

function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .trim()
}

export function filtrarYOrdenar(productos: Producto[], criterios: CriteriosInventario): Producto[] {
  const consulta = normalizar(criterios.texto)
  const filtrados = productos.filter((p) => {
    if (criterios.estado !== 'todos' && estadoStock(p.cantidadDisponible) !== criterios.estado) {
      return false
    }
    if (!consulta) return true
    return normalizar(p.codigo).includes(consulta) || normalizar(p.nombre).includes(consulta)
  })

  const signo = criterios.direccion === 'asc' ? 1 : -1
  return filtrados.sort((a, b) => {
    const resultado =
      criterios.orden === 'cantidadDisponible'
        ? a.cantidadDisponible - b.cantidadDisponible
        : comparadorTexto.compare(a[criterios.orden], b[criterios.orden])
    return resultado * signo || comparadorTexto.compare(a.codigo, b.codigo)
  })
}

export function contarPorEstado(productos: Producto[]): Record<FiltroEstado, number> {
  const conteo: Record<FiltroEstado, number> = { todos: 0, disponible: 0, bajo: 0, agotado: 0 }
  for (const p of productos) {
    conteo.todos++
    conteo[estadoStock(p.cantidadDisponible)]++
  }
  return conteo
}

export interface ResumenInventario {
  totalProductos: number
  totalUnidades: number
  bajoUmbral: number
  agotados: number
}

export function resumirInventario(productos: Producto[]): ResumenInventario {
  return {
    totalProductos: productos.length,
    totalUnidades: productos.reduce((suma, p) => suma + p.cantidadDisponible, 0),
    bajoUmbral: productos.filter((p) => requiereAtencion(p.cantidadDisponible)).length,
    agotados: productos.filter((p) => p.cantidadDisponible <= 0).length,
  }
}

/** Productos bajo el umbral de alerta, empezando por los de menor existencia. */
export function productosQueRequierenAtencion(productos: Producto[]): Producto[] {
  return productos
    .filter((p) => requiereAtencion(p.cantidadDisponible))
    .sort(
      (a, b) =>
        a.cantidadDisponible - b.cantidadDisponible || comparadorTexto.compare(a.codigo, b.codigo),
    )
}

export function describirConteoTipos(movimientos: Movimiento[]): string {
  const conteo = contarPorTipo(movimientos)
  return [
    contar(conteo.ENTRADA, 'entrada'),
    contar(conteo.SALIDA, 'salida'),
    contar(conteo.AJUSTE, 'ajuste'),
  ].join(', ')
}

export function contarPorTipo(movimientos: Movimiento[]): Record<TipoMovimiento, number> {
  const conteo: Record<TipoMovimiento, number> = { ENTRADA: 0, SALIDA: 0, AJUSTE: 0 }
  for (const m of movimientos) {
    if (m.tipoMovimiento in conteo) conteo[m.tipoMovimiento]++
  }
  return conteo
}
