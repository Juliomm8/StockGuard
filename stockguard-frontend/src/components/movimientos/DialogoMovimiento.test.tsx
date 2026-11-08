import { screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useOperaciones } from '../../context/operaciones'
import type { Operacion } from '../../domain/operaciones'
import { PRODUCTOS, errorApi, json, simularApi, sinContenido } from '../../test/apiSimulada'
import { renderizar } from '../../test/renderizar'

function Abridor({ operacion, codigo }: { operacion: Operacion; codigo?: string }) {
  const { abrirOperacion } = useOperaciones()
  return (
    <button type="button" onClick={() => abrirOperacion(operacion, codigo)}>
      Abrir
    </button>
  )
}

afterEach(() => {
  vi.unstubAllGlobals()
})

async function abrirDialogo(operacion: Operacion, codigo?: string) {
  const vista = renderizar(<Abridor operacion={operacion} codigo={codigo} />)
  await waitFor(() => expect(fetch).toHaveBeenCalled())
  await vista.usuario.click(screen.getByRole('button', { name: 'Abrir' }))
  const dialogo = await screen.findByRole('dialog')
  return { ...vista, dialogo }
}

describe('DialogoMovimiento', () => {
  it('registra una entrada, interpreta el 204 y recarga el inventario', async () => {
    const api = simularApi({
      'GET /api/productos': () => json(PRODUCTOS),
      'POST /api/productos/P-001/entradas': () => sinContenido(),
    })
    const { usuario, dialogo } = await abrirDialogo('entrada', 'P-001')

    expect(within(dialogo).getByText('Teclado mecánico Logitech')).toBeInTheDocument()
    await usuario.type(within(dialogo).getByLabelText('Unidades que ingresan'), '5')
    expect(within(dialogo).getByText('17')).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Continuar' }))
    expect(within(dialogo).getByText('Existencia resultante')).toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Registrar entrada' }))

    expect(await screen.findByText('Entrada registrada')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(api.posts()).toEqual([
      { metodo: 'POST', url: '/api/productos/P-001/entradas', cuerpo: { cantidad: 5 } },
    ])
    await waitFor(() => expect(api.contar('GET', '/api/productos')).toBe(2))
  })

  it('no envía una salida superior a la existencia disponible', async () => {
    const api = simularApi({ 'GET /api/productos': () => json(PRODUCTOS) })
    const { usuario, dialogo } = await abrirDialogo('salida', 'P-002')

    await usuario.type(within(dialogo).getByLabelText('Unidades que salen'), '4')
    await usuario.click(screen.getByRole('button', { name: 'Continuar' }))

    expect(within(dialogo).getByText(/supera las existencias disponibles \(3\)/)).toBeInTheDocument()
    expect(within(dialogo).getByLabelText('Unidades que salen')).toHaveAttribute('aria-invalid', 'true')
    expect(api.posts()).toHaveLength(0)
  })

  it('muestra el conflicto 409 y mantiene el diálogo abierto', async () => {
    simularApi({
      'GET /api/productos': () => json(PRODUCTOS),
      'POST /api/productos/P-002/salidas': () =>
        errorApi(409, 'La operación no es compatible con las existencias actuales.'),
    })
    const { usuario, dialogo } = await abrirDialogo('salida', 'P-002')

    await usuario.type(within(dialogo).getByLabelText('Unidades que salen'), '2')
    await usuario.click(screen.getByRole('button', { name: 'Continuar' }))
    await usuario.click(screen.getByRole('button', { name: 'Registrar salida' }))

    const avisos = await within(dialogo).findAllByText(/supera las existencias disponibles del producto/)
    expect(avisos.length).toBeGreaterThan(0)
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.queryByText('Salida registrada')).not.toBeInTheDocument()
  })

  it('informa que el servicio de inventario no está disponible (503)', async () => {
    simularApi({
      'GET /api/productos': () => json(PRODUCTOS),
      'POST /api/productos/P-001/entradas': () =>
        errorApi(503, 'El servicio de inventario no está disponible.'),
    })
    const { usuario, dialogo } = await abrirDialogo('entrada', 'P-001')

    await usuario.type(within(dialogo).getByLabelText('Unidades que ingresan'), '1')
    await usuario.click(screen.getByRole('button', { name: 'Continuar' }))
    await usuario.click(screen.getByRole('button', { name: 'Registrar entrada' }))

    expect(
      await within(dialogo).findByText(/servicio de inventario no está disponible en este momento/),
    ).toBeInTheDocument()
  })

  it('exige confirmación explícita antes de aplicar un ajuste', async () => {
    const api = simularApi({
      'GET /api/productos': () => json(PRODUCTOS),
      'POST /api/productos/P-002/ajustes': () => sinContenido(),
    })
    const { usuario, dialogo } = await abrirDialogo('ajuste', 'P-002')

    expect(within(dialogo).getByText(/no suma ni resta unidades/)).toBeInTheDocument()
    await usuario.type(within(dialogo).getByLabelText('Nueva existencia total'), '10')
    await usuario.click(screen.getByRole('button', { name: 'Continuar' }))

    const aplicar = screen.getByRole('button', { name: 'Aplicar ajuste' })
    expect(aplicar).toBeDisabled()
    await usuario.click(within(dialogo).getByRole('checkbox'))
    expect(aplicar).toBeEnabled()
    await usuario.click(aplicar)

    expect(await screen.findByText('Ajuste aplicado')).toBeInTheDocument()
    expect(api.posts()[0]).toEqual({
      metodo: 'POST',
      url: '/api/productos/P-002/ajustes',
      cuerpo: { cantidad: 10 },
    })
  })

  it('no permite un ajuste a la misma existencia', async () => {
    const api = simularApi({ 'GET /api/productos': () => json(PRODUCTOS) })
    const { usuario, dialogo } = await abrirDialogo('ajuste', 'P-002')

    await usuario.type(within(dialogo).getByLabelText('Nueva existencia total'), '3')
    await usuario.click(screen.getByRole('button', { name: 'Continuar' }))

    expect(within(dialogo).getByText(/coincide con la actual/)).toBeInTheDocument()
    expect(api.posts()).toHaveLength(0)
  })

  it('pide elegir el producto cuando se abre desde un acceso rápido', async () => {
    simularApi({ 'GET /api/productos': () => json(PRODUCTOS) })
    const { usuario, dialogo } = await abrirDialogo('entrada')

    await usuario.click(screen.getByRole('button', { name: 'Continuar' }))
    expect(within(dialogo).getByText('Seleccione el producto del movimiento.')).toBeInTheDocument()

    await usuario.selectOptions(within(dialogo).getByLabelText('Producto'), 'P-002')
    expect(within(dialogo).getByText('Mouse inalámbrico Logitech')).toBeInTheDocument()
    expect(within(dialogo).getByLabelText('Unidades que ingresan')).toBeInTheDocument()
  })

  it('se cierra con Escape sin enviar nada', async () => {
    const api = simularApi({ 'GET /api/productos': () => json(PRODUCTOS) })
    const { usuario } = await abrirDialogo('salida', 'P-001')

    await usuario.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(api.posts()).toHaveLength(0)
  })

  describe('cuando el servidor no confirma el resultado', () => {
    async function enviarEntradaSinRespuesta(respuestaPost: () => Response) {
      const api = simularApi({
        'GET /api/productos': () => json(PRODUCTOS),
        'POST /api/productos/P-001/entradas': respuestaPost,
      })
      const vista = await abrirDialogo('entrada', 'P-001')
      await vista.usuario.type(within(vista.dialogo).getByLabelText('Unidades que ingresan'), '5')
      await vista.usuario.click(screen.getByRole('button', { name: 'Continuar' }))
      await vista.usuario.click(screen.getByRole('button', { name: 'Registrar entrada' }))
      return { api, ...vista }
    }

    const caidaDeRed = (): Response => {
      throw new TypeError('Failed to fetch')
    }

    it('avisa que pudo registrarse, no muestra éxito y no permite repetir la operación', async () => {
      const { api, dialogo } = await enviarEntradaSinRespuesta(caidaDeRed)

      expect(
        await within(dialogo).findByText('El movimiento pudo haberse registrado'),
      ).toBeInTheDocument()
      expect(within(dialogo).getByText(/Compruebe el historial del producto/)).toBeInTheDocument()
      expect(screen.queryByText('Entrada registrada')).not.toBeInTheDocument()
      expect(screen.queryByText(/No se pudo completar/)).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Registrar entrada' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Continuar' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Ver historial del producto' })).toBeInTheDocument()
      expect(api.posts()).toHaveLength(1)
    })

    it('consulta de nuevo el producto mediante GET sin reenviar el POST', async () => {
      const { api, dialogo } = await enviarEntradaSinRespuesta(caidaDeRed)

      await within(dialogo).findByText('El movimiento pudo haberse registrado')
      await waitFor(() => expect(api.contar('GET', '/api/productos')).toBe(2))
      expect(api.posts()).toHaveLength(1)
    })

    it('muestra la existencia anterior y la consultada ahora para decidir', async () => {
      const { dialogo } = await enviarEntradaSinRespuesta(caidaDeRed)

      await within(dialogo).findByText('Existencia antes del envío')
      expect(within(dialogo).getAllByText('12 unidades').length).toBeGreaterThan(0)
      expect(await within(dialogo).findByText(/no ha cambiado todavía/)).toBeInTheDocument()
    })

    it('trata un 504 del proxy como resultado incierto', async () => {
      const { api, dialogo } = await enviarEntradaSinRespuesta(
        () => new Response('Gateway Timeout', { status: 504 }),
      )

      expect(
        await within(dialogo).findByText('El movimiento pudo haberse registrado'),
      ).toBeInTheDocument()
      expect(api.posts()).toHaveLength(1)
    })

    it('un rechazo HTTP 409 sigue siendo un error definitivo, no un resultado incierto', async () => {
      const { dialogo } = await enviarEntradaSinRespuesta(() =>
        errorApi(409, 'La operación no es compatible con las existencias actuales.'),
      )

      expect(
        await within(dialogo).findAllByText(/La entrada no se registró/),
      ).not.toHaveLength(0)
      expect(
        within(dialogo).queryByText('El movimiento pudo haberse registrado'),
      ).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Continuar' })).toBeInTheDocument()
    })
  })
})
