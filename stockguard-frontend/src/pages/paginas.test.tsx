import { screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PRODUCTOS, errorApi, json, movimiento, simularApi } from '../test/apiSimulada'
import { renderizar } from '../test/renderizar'
import { Alertas } from './Alertas'
import { DetalleProducto } from './DetalleProducto'
import { Inventario } from './Inventario'
import { PanelGeneral } from './PanelGeneral'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('PanelGeneral', () => {
  it('calcula los indicadores con los datos de la API', async () => {
    simularApi({
      'GET /api/productos': () => json(PRODUCTOS),
      'GET /api/productos/P-001/movimientos': () =>
        json([movimiento({ id: 2, tipoMovimiento: 'SALIDA' }), movimiento({ id: 1 })]),
      'GET /api/productos/P-002/movimientos': () => json([]),
      'GET /api/productos/P-004/movimientos': () => json([]),
    })
    renderizar(<PanelGeneral />)

    const indicadores = await screen.findByText('Productos registrados')
    const franja = indicadores.closest('dl') as HTMLElement
    expect(within(franja).getByText('15')).toBeInTheDocument()
    expect(within(franja).getByText('1 sin existencias')).toBeInTheDocument()
    expect(await within(franja).findByText('1 entrada, 1 salida, 0 ajustes')).toBeInTheDocument()

    const atencion = screen.getByRole('table', { name: 'Productos que requieren atención' })
    expect(within(atencion).getByText('P-004')).toBeInTheDocument()
    expect(within(atencion).getByText('P-002')).toBeInTheDocument()
    expect(within(atencion).queryByText('P-001')).not.toBeInTheDocument()
  })

  it('indica claramente cuando no hay conexión con el servidor', async () => {
    simularApi({
      'GET /api/productos': () => {
        throw new TypeError('Failed to fetch')
      },
    })
    renderizar(<PanelGeneral />)

    expect(await screen.findByText('Sin conexión con el servidor')).toBeInTheDocument()
    expect(screen.getByText(/Apache Karaf y la API REST/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})

describe('Inventario', () => {
  it('filtra por texto y por estado', async () => {
    simularApi({ 'GET /api/productos': () => json(PRODUCTOS) })
    const { usuario } = renderizar(<Inventario />, { ruta: '/inventario' })

    const tabla = await screen.findByRole('table', { name: 'Productos del inventario' })
    expect(within(tabla).getAllByRole('row')).toHaveLength(4)

    await usuario.type(screen.getByLabelText('Buscar productos por código o nombre'), 'mouse')
    expect(within(tabla).getAllByRole('row')).toHaveLength(2)

    await usuario.click(screen.getByRole('button', { name: 'Borrar búsqueda' }))
    await usuario.click(screen.getByRole('button', { name: /Agotados/ }))
    expect(within(tabla).getByText('P-004')).toBeInTheDocument()
    expect(within(tabla).getAllByRole('row')).toHaveLength(2)
  })

  it('muestra un estado vacío cuando ningún producto coincide', async () => {
    simularApi({ 'GET /api/productos': () => json(PRODUCTOS) })
    const { usuario } = renderizar(<Inventario />, { ruta: '/inventario' })

    await usuario.type(await screen.findByLabelText('Buscar productos por código o nombre'), 'zzz')
    expect(screen.getByText('Ningún producto coincide con la búsqueda')).toBeInTheDocument()
  })
})

describe('DetalleProducto', () => {
  it('muestra el producto y su historial', async () => {
    simularApi({
      'GET /api/productos': () => json(PRODUCTOS),
      'GET /api/productos/P-001': () => json(PRODUCTOS[0]),
      'GET /api/productos/P-001/movimientos': () =>
        json([
          movimiento({
            id: 2,
            tipoMovimiento: 'SALIDA',
            cantidadModificada: 2,
            existenciaAnterior: 17,
            existenciaPosterior: 15,
            observacion: 'Salida de inventario',
          }),
          movimiento({ id: 1 }),
        ]),
    })
    renderizar(<DetalleProducto />, { ruta: '/inventario/P-001', patron: '/inventario/:codigo' })

    expect(await screen.findByRole('heading', { name: 'Teclado mecánico Logitech' })).toBeInTheDocument()
    const historial = screen.getByRole('table', { name: 'Historial de movimientos del producto' })
    expect(within(historial).getByText('−2')).toBeInTheDocument()
    expect(within(historial).getByText('+5')).toBeInTheDocument()
    expect(within(historial).getByText('Salida de inventario')).toBeInTheDocument()
  })

  it('informa cuando el producto no existe (404)', async () => {
    simularApi({
      'GET /api/productos': () => json(PRODUCTOS),
      'GET /api/productos/P-999': () => errorApi(404, 'No se encontró el producto solicitado.'),
      'GET /api/productos/P-999/movimientos': () =>
        errorApi(404, 'No se encontró el producto solicitado.'),
    })
    renderizar(<DetalleProducto />, { ruta: '/inventario/P-999', patron: '/inventario/:codigo' })

    expect(await screen.findByText('No existe ningún producto con el código P-999')).toBeInTheDocument()
  })
})

describe('Alertas', () => {
  it('lista los productos bajo el umbral y aclara que el historial no está disponible', async () => {
    simularApi({ 'GET /api/productos': () => json(PRODUCTOS) })
    renderizar(<Alertas />, { ruta: '/alertas' })

    const tabla = await screen.findByRole('table', { name: 'Productos bajo el umbral de alerta' })
    expect(within(tabla).getAllByRole('row')).toHaveLength(3)
    expect(screen.getByText('La consulta del historial aún no está disponible')).toBeInTheDocument()
  })
})
