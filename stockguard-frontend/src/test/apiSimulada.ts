import { vi } from 'vitest'
import type { Movimiento, Producto } from '../domain/tipos'

export interface LlamadaRegistrada {
  metodo: string
  url: string
  cuerpo: unknown
}

type Manejador = (llamada: LlamadaRegistrada) => Response | Promise<Response>

/** Respuesta JSON con el código indicado. */
export function json(datos: unknown, estado = 200): Response {
  return new Response(JSON.stringify(datos), {
    status: estado,
    headers: { 'Content-Type': 'application/json' },
  })
}

export function sinContenido(): Response {
  return new Response(null, { status: 204 })
}

export function errorApi(estado: number, mensaje: string): Response {
  return json({ mensaje }, estado)
}

/**
 * Sustituye `fetch` por un enrutador en memoria. Las rutas se indican como
 * `'GET /api/productos'`; una petición sin ruta registrada falla la prueba.
 */
export function simularApi(rutas: Record<string, Manejador>) {
  const llamadas: LlamadaRegistrada[] = []
  const simulado = vi.fn(async (entrada: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof entrada === 'string' ? entrada : entrada.toString()
    const metodo = init?.method ?? 'GET'
    const cuerpo = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined
    const llamada = { metodo, url, cuerpo }
    llamadas.push(llamada)
    const manejador = rutas[`${metodo} ${url}`]
    if (!manejador) throw new Error(`Petición no esperada en la prueba: ${metodo} ${url}`)
    return manejador(llamada)
  })
  vi.stubGlobal('fetch', simulado)
  return {
    llamadas,
    posts: () => llamadas.filter((l) => l.metodo === 'POST'),
    contar: (metodo: string, url: string) =>
      llamadas.filter((l) => l.metodo === metodo && l.url === url).length,
  }
}

export const PRODUCTOS: Producto[] = [
  { codigo: 'P-001', nombre: 'Teclado mecánico Logitech', cantidadDisponible: 12 },
  { codigo: 'P-002', nombre: 'Mouse inalámbrico Logitech', cantidadDisponible: 3 },
  { codigo: 'P-004', nombre: 'Cable HDMI 2 metros', cantidadDisponible: 0 },
]

export function movimiento(parcial: Partial<Movimiento> & Pick<Movimiento, 'id'>): Movimiento {
  return {
    codigoProducto: 'P-001',
    tipoMovimiento: 'ENTRADA',
    cantidadModificada: 5,
    existenciaAnterior: 12,
    existenciaPosterior: 17,
    fechaMovimiento: '2026-11-08T05:54:53.266877Z',
    observacion: 'Entrada de inventario',
    ...parcial,
  }
}
