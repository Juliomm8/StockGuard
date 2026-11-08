import { afterEach, describe, expect, it, vi } from 'vitest'
import { json, simularApi, sinContenido } from '../test/apiSimulada'
import { describirError } from './errores'
import { listarMovimientos, listarProductos, obtenerProducto, registrarOperacion } from './productos'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('servicio de productos', () => {
  it('consulta los tres recursos GET de la API', async () => {
    const api = simularApi({
      'GET /api/productos': () => json([]),
      'GET /api/productos/P-001': () => json({ codigo: 'P-001', nombre: 'X', cantidadDisponible: 1 }),
      'GET /api/productos/P-001/movimientos': () => json([]),
    })
    await listarProductos()
    await obtenerProducto('P-001')
    await listarMovimientos('P-001')
    expect(api.llamadas.map((l) => l.url)).toEqual([
      '/api/productos',
      '/api/productos/P-001',
      '/api/productos/P-001/movimientos',
    ])
  })

  it('codifica el código del producto en la URL', async () => {
    const api = simularApi({ 'GET /api/productos/A%2FB': () => json({ codigo: 'A/B' }) })
    await obtenerProducto('A/B')
    expect(api.llamadas[0].url).toBe('/api/productos/A%2FB')
  })

  it('rechaza un listado que no es un arreglo', async () => {
    simularApi({ 'GET /api/productos': () => json({ inesperado: true }) })
    await expect(listarProductos()).rejects.toMatchObject({ tipo: 'respuesta' })
  })

  it.each([
    ['entrada', 'entradas'],
    ['salida', 'salidas'],
    ['ajuste', 'ajustes'],
  ] as const)('envía la %s al recurso /%s con la cantidad', async (operacion, recurso) => {
    const api = simularApi({ [`POST /api/productos/P-002/${recurso}`]: () => sinContenido() })
    await expect(registrarOperacion(operacion, 'P-002', 7)).resolves.toBeUndefined()
    expect(api.posts()[0].cuerpo).toEqual({ cantidad: 7 })
  })

  it('propaga el 409 con un mensaje comprensible para la operación', async () => {
    simularApi({
      'POST /api/productos/P-002/salidas': () =>
        json({ mensaje: 'La operación no es compatible con las existencias actuales.' }, 409),
    })
    const error = await registrarOperacion('salida', 'P-002', 99).catch((e: unknown) => e)
    expect(describirError(error, { conflicto: 'Supera las existencias.' })).toBe(
      'Supera las existencias.',
    )
  })
})
