import { afterEach, describe, expect, it, vi } from 'vitest'
import { errorApi, json, simularApi, sinContenido } from '../test/apiSimulada'
import { peticion } from './cliente'
import { ApiError } from './errores'

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

async function capturarError(promesa: Promise<unknown>): Promise<ApiError> {
  try {
    await promesa
  } catch (error) {
    expect(error).toBeInstanceOf(ApiError)
    return error as ApiError
  }
  throw new Error('Se esperaba un error')
}

describe('peticion', () => {
  it('interpreta el cuerpo JSON de una respuesta 200', async () => {
    simularApi({ 'GET /api/productos': () => json([{ codigo: 'P-001' }]) })
    await expect(peticion('/productos')).resolves.toEqual([{ codigo: 'P-001' }])
  })

  it('acepta un 204 sin cuerpo sin intentar leer JSON', async () => {
    const api = simularApi({ 'POST /api/productos/P-001/entradas': () => sinContenido() })
    await expect(
      peticion('/productos/P-001/entradas', { method: 'POST', body: { cantidad: 5 } }),
    ).resolves.toBeUndefined()
    expect(api.posts()).toEqual([
      { metodo: 'POST', url: '/api/productos/P-001/entradas', cuerpo: { cantidad: 5 } },
    ])
  })

  it('acepta un 200 con cuerpo vacío', async () => {
    simularApi({ 'POST /api/x': () => new Response('', { status: 200 }) })
    await expect(peticion('/x', { method: 'POST', body: {} })).resolves.toBeUndefined()
  })

  it('envía la cabecera Content-Type solo cuando hay cuerpo', async () => {
    simularApi({ 'GET /api/x': () => json([]), 'POST /api/x': () => sinContenido() })
    await peticion('/x')
    await peticion('/x', { method: 'POST', body: { cantidad: 1 } })
    const llamadas = vi.mocked(fetch).mock.calls
    expect(new Headers(llamadas[0][1]?.headers).has('Content-Type')).toBe(false)
    expect(new Headers(llamadas[1][1]?.headers).get('Content-Type')).toBe('application/json')
  })

  it.each([400, 404, 409, 503])('convierte HTTP %i en ApiError con el mensaje del servidor', async (estado) => {
    simularApi({ 'GET /api/x': () => errorApi(estado, 'Mensaje del servidor') })
    const error = await capturarError(peticion('/x'))
    expect(error.tipo).toBe('http')
    expect(error.estado).toBe(estado)
    expect(error.mensajeServidor).toBe('Mensaje del servidor')
  })

  it('descarta cuerpos de error que no son JSON', async () => {
    simularApi({ 'GET /api/x': () => new Response('<html>Stacktrace</html>', { status: 500 }) })
    const error = await capturarError(peticion('/x'))
    expect(error.estado).toBe(500)
    expect(error.mensajeServidor).toBeNull()
  })

  it('trata un fallo de red como falta de conexión', async () => {
    simularApi({
      'GET /api/x': () => {
        throw new TypeError('Failed to fetch')
      },
    })
    const error = await capturarError(peticion('/x'))
    expect(error.tipo).toBe('conexion')
  })

  it('trata el 502 del proxy de desarrollo como falta de conexión', async () => {
    simularApi({ 'GET /api/x': () => new Response('Bad Gateway', { status: 502 }) })
    const error = await capturarError(peticion('/x'))
    expect(error.tipo).toBe('conexion')
  })

  it('informa una respuesta 200 que no es JSON válido', async () => {
    simularApi({ 'GET /api/x': () => new Response('{no-json', { status: 200 }) })
    const error = await capturarError(peticion('/x'))
    expect(error.tipo).toBe('respuesta')
  })
})

describe('operaciones de escritura con resultado incierto', () => {
  const escribir = () =>
    peticion('/productos/P-001/entradas', { method: 'POST', body: { cantidad: 5 } })

  it('marca como incierto un fallo de red en un POST y no lo reenvía', async () => {
    const api = simularApi({
      'POST /api/productos/P-001/entradas': () => {
        throw new TypeError('Failed to fetch')
      },
    })
    const error = await capturarError(escribir())
    expect(error.tipo).toBe('conexion')
    expect(error.resultadoIncierto).toBe(true)
    expect(error.tiempoAgotado).toBe(false)
    expect(api.posts()).toHaveLength(1)
  })

  it('marca como incierto y como tiempo agotado un POST que supera el límite', async () => {
    const limite = new AbortController()
    vi.spyOn(AbortSignal, 'timeout').mockReturnValue(limite.signal)
    const fetchSimulado = vi.fn(
      (_url: unknown, init?: RequestInit) =>
        new Promise<Response>((_resolver, rechazar) => {
          init?.signal?.addEventListener('abort', () => rechazar(init.signal?.reason))
          limite.abort(new DOMException('El tiempo de espera se agotó', 'TimeoutError'))
        }),
    )
    vi.stubGlobal('fetch', fetchSimulado)

    const error = await capturarError(escribir())
    expect(error.resultadoIncierto).toBe(true)
    expect(error.tiempoAgotado).toBe(true)
    expect(fetchSimulado).toHaveBeenCalledTimes(1)
  })

  it.each([502, 504])('marca como incierto un POST que recibe HTTP %i del proxy', async (estado) => {
    const api = simularApi({
      'POST /api/productos/P-001/entradas': () => new Response('Bad Gateway', { status: estado }),
    })
    const error = await capturarError(escribir())
    expect(error.resultadoIncierto).toBe(true)
    expect(error.tiempoAgotado).toBe(estado === 504)
    expect(api.posts()).toHaveLength(1)
  })

  it('marca como incierto un POST con 2xx cuyo cuerpo no se puede interpretar', async () => {
    simularApi({
      'POST /api/productos/P-001/entradas': () => new Response('{no-json', { status: 200 }),
    })
    const error = await capturarError(escribir())
    expect(error.tipo).toBe('respuesta')
    expect(error.resultadoIncierto).toBe(true)
  })

  it.each([400, 404, 409, 500, 503])(
    'trata el HTTP %i de un POST como rechazo definitivo, no como incierto',
    async (estado) => {
      const api = simularApi({
        'POST /api/productos/P-001/entradas': () => errorApi(estado, 'Rechazado'),
      })
      const error = await capturarError(escribir())
      expect(error.tipo).toBe('http')
      expect(error.resultadoIncierto).toBe(false)
      expect(api.posts()).toHaveLength(1)
    },
  )

  it('no marca como incierto un fallo de red en una consulta GET', async () => {
    simularApi({
      'GET /api/productos': () => {
        throw new TypeError('Failed to fetch')
      },
    })
    const error = await capturarError(peticion('/productos'))
    expect(error.tipo).toBe('conexion')
    expect(error.resultadoIncierto).toBe(false)
  })
})
