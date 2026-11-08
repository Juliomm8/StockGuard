import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/errores'
import { movimiento } from '../test/apiSimulada'
import { consolidarMovimientos } from './useMovimientosRecientes'

describe('consolidarMovimientos', () => {
  it('une los historiales y los ordena del más reciente al más antiguo', async () => {
    const consultar = vi.fn(async (codigo: string) =>
      codigo === 'P-001'
        ? [movimiento({ id: 1, fechaMovimiento: '2026-11-08T05:00:00Z' })]
        : [movimiento({ id: 2, codigoProducto: codigo, fechaMovimiento: '2026-11-08T06:00:00Z' })],
    )
    const resultado = await consolidarMovimientos(['P-001', 'P-002'], new AbortController().signal, consultar)
    expect(resultado.movimientos.map((m) => m.id)).toEqual([2, 1])
    expect(resultado.productosFallidos).toBe(0)
  })

  it('cuenta los productos cuyo historial no pudo consultarse', async () => {
    const consultar = vi.fn(async (codigo: string) => {
      if (codigo === 'P-002') throw new ApiError('http', 503)
      return [movimiento({ id: 1 })]
    })
    const resultado = await consolidarMovimientos(
      ['P-001', 'P-002', 'P-003'],
      new AbortController().signal,
      consultar,
    )
    expect(resultado).toMatchObject({ productosConsultados: 3, productosFallidos: 1 })
    expect(resultado.movimientos).toHaveLength(2)
  })
})
