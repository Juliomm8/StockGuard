import { describe, expect, it } from 'vitest'
import {
  ApiError,
  AVISO_COMPROBAR_HISTORIAL,
  MENSAJE_SIN_CONEXION,
  describirError,
  esResultadoIncierto,
} from './errores'

describe('resultado incierto', () => {
  const incierto = (tipo: 'conexion' | 'respuesta', tiempoAgotado = false) =>
    new ApiError(tipo, null, null, { resultadoIncierto: true, tiempoAgotado })

  it('advierte que el movimiento pudo registrarse y pide revisar el historial', () => {
    const mensaje = describirError(incierto('conexion'))
    expect(mensaje).toMatch(/pudo haberse registrado/)
    expect(mensaje).toContain(AVISO_COMPROBAR_HISTORIAL)
    expect(mensaje).not.toBe(MENSAJE_SIN_CONEXION)
  })

  it('distingue el tiempo agotado de la pérdida de comunicación', () => {
    expect(describirError(incierto('conexion', true))).toMatch(/tardó demasiado en responder/)
    expect(describirError(incierto('conexion'))).toMatch(/Se perdió la comunicación/)
    expect(describirError(incierto('respuesta'))).toMatch(/no pudo interpretarse/)
  })

  it('no presenta un rechazo HTTP como incierto ni afirma que se registró', () => {
    for (const estado of [400, 409]) {
      const error = new ApiError('http', estado, 'Mensaje')
      expect(esResultadoIncierto(error)).toBe(false)
      expect(describirError(error)).not.toMatch(/pudo haberse registrado/)
    }
    expect(esResultadoIncierto(new ApiError('conexion', null))).toBe(false)
    expect(esResultadoIncierto(new Error('x'))).toBe(false)
  })
})

describe('describirError', () => {
  it('explica la falta de conexión con Karaf', () => {
    expect(describirError(new ApiError('conexion', null))).toBe(MENSAJE_SIN_CONEXION)
  })

  it('usa el mensaje de validación del servidor en un 400', () => {
    const error = new ApiError('http', 400, 'La cantidad debe ser mayor que cero.')
    expect(describirError(error)).toBe('La cantidad debe ser mayor que cero.')
  })

  it('describe 404, 409, 503 y 500 sin detalles técnicos', () => {
    expect(describirError(new ApiError('http', 404))).toMatch(/No se encontró el producto/)
    expect(describirError(new ApiError('http', 409))).toMatch(/existencias actuales/)
    expect(describirError(new ApiError('http', 503))).toMatch(/servicio de inventario no está disponible/)
    expect(describirError(new ApiError('http', 500))).toMatch(/no pudo procesar/)
  })

  it('prioriza el mensaje de conflicto propio de la operación', () => {
    expect(describirError(new ApiError('http', 409), { conflicto: 'Sin stock.' })).toBe('Sin stock.')
  })

  it('no expone errores desconocidos', () => {
    expect(describirError(new Error('NullPointerException at ...'))).toBe(
      'Se produjo un error inesperado en la aplicación.',
    )
  })
})
