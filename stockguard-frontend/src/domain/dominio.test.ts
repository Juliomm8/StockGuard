import { describe, expect, it } from 'vitest'
import { PRODUCTOS, movimiento } from '../test/apiSimulada'
import { contar } from './formato'
import {
  describirConteoTipos,
  filtrarYOrdenar,
  productosQueRequierenAtencion,
  resumirInventario,
} from './inventario'
import { CANTIDAD_MAXIMA, existenciaResultante, validarCantidad } from './operaciones'
import { UMBRAL_STOCK, estadoStock, requiereAtencion } from './stock'

describe('reglas de stock', () => {
  it('replica el umbral del monitor de alertas (menor que 5)', () => {
    expect(UMBRAL_STOCK).toBe(5)
    expect(requiereAtencion(4)).toBe(true)
    expect(requiereAtencion(5)).toBe(false)
    expect(estadoStock(0)).toBe('agotado')
    expect(estadoStock(4)).toBe('bajo')
    expect(estadoStock(5)).toBe('disponible')
  })
})

describe('validarCantidad', () => {
  it('exige un entero sin signos ni decimales', () => {
    expect(validarCantidad('entrada', '', 3)).toMatchObject({ valido: false })
    expect(validarCantidad('entrada', '2.5', 3)).toMatchObject({ valido: false })
    expect(validarCantidad('entrada', '-1', 3)).toMatchObject({ valido: false })
    expect(validarCantidad('entrada', ' 4 ', 3)).toEqual({ valido: true, cantidad: 4 })
  })

  it('rechaza cero en entradas y salidas', () => {
    expect(validarCantidad('entrada', '0', 3)).toMatchObject({ valido: false })
    expect(validarCantidad('salida', '0', 3)).toMatchObject({ valido: false })
  })

  it('impide una salida mayor que la existencia', () => {
    expect(validarCantidad('salida', '4', 3)).toMatchObject({
      valido: false,
      mensaje: expect.stringMatching(/supera las existencias disponibles \(3\)/),
    })
    expect(validarCantidad('salida', '3', 3)).toEqual({ valido: true, cantidad: 3 })
  })

  it('permite ajustar a cero, pero no a la misma existencia', () => {
    expect(validarCantidad('ajuste', '0', 3)).toEqual({ valido: true, cantidad: 0 })
    expect(validarCantidad('ajuste', '3', 3)).toMatchObject({ valido: false })
  })

  it('respeta el máximo del entero de Java', () => {
    expect(validarCantidad('entrada', String(CANTIDAD_MAXIMA), 1)).toMatchObject({ valido: false })
    expect(validarCantidad('ajuste', String(CANTIDAD_MAXIMA + 1), 1)).toMatchObject({ valido: false })
  })
})

describe('existenciaResultante', () => {
  it('suma, resta o reemplaza según la operación', () => {
    expect(existenciaResultante('entrada', 10, 4)).toBe(14)
    expect(existenciaResultante('salida', 10, 4)).toBe(6)
    expect(existenciaResultante('ajuste', 10, 4)).toBe(4)
  })
})

describe('inventario', () => {
  it('resume productos, unidades y productos bajo el umbral', () => {
    expect(resumirInventario(PRODUCTOS)).toEqual({
      totalProductos: 3,
      totalUnidades: 15,
      bajoUmbral: 2,
      agotados: 1,
    })
  })

  it('ordena por existencia los productos que requieren atención', () => {
    expect(productosQueRequierenAtencion(PRODUCTOS).map((p) => p.codigo)).toEqual(['P-004', 'P-002'])
  })

  it('busca sin distinguir mayúsculas ni tildes y filtra por estado', () => {
    const base = { estado: 'todos', orden: 'codigo', direccion: 'asc' } as const
    expect(filtrarYOrdenar(PRODUCTOS, { ...base, texto: 'MECANICO' }).map((p) => p.codigo)).toEqual(['P-001'])
    expect(filtrarYOrdenar(PRODUCTOS, { ...base, texto: 'p-00' })).toHaveLength(3)
    expect(filtrarYOrdenar(PRODUCTOS, { ...base, texto: '', estado: 'bajo' }).map((p) => p.codigo)).toEqual(['P-002'])
  })

  it('ordena por cantidad en ambos sentidos', () => {
    const criterios = { texto: '', estado: 'todos', orden: 'cantidadDisponible' } as const
    expect(filtrarYOrdenar(PRODUCTOS, { ...criterios, direccion: 'asc' }).map((p) => p.codigo)).toEqual(['P-004', 'P-002', 'P-001'])
    expect(filtrarYOrdenar(PRODUCTOS, { ...criterios, direccion: 'desc' }).map((p) => p.codigo)).toEqual(['P-001', 'P-002', 'P-004'])
  })

  it('describe el conteo por tipo con singular y plural', () => {
    const movimientos = [
      movimiento({ id: 1 }),
      movimiento({ id: 2 }),
      movimiento({ id: 3, tipoMovimiento: 'AJUSTE' }),
    ]
    expect(describirConteoTipos(movimientos)).toBe('2 entradas, 0 salidas, 1 ajuste')
    expect(contar(1, 'unidad', 'unidades')).toBe('1 unidad')
  })
})
