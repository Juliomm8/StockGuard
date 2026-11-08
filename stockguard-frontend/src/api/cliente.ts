import { ApiError } from './errores'

const TIEMPO_MAXIMO_MS = 15_000

/** Estados con los que el proxy de desarrollo indica que Karaf no respondió. */
const ESTADOS_SIN_CONEXION = new Set([502, 504])

export function urlBaseApi(): string {
  const configurada = import.meta.env.VITE_API_BASE_URL?.trim()
  return (configurada || '/api').replace(/\/+$/, '')
}

interface OpcionesPeticion {
  method?: 'GET' | 'POST'
  body?: unknown
  signal?: AbortSignal
}

/**
 * Ejecuta una petición contra la API. Devuelve `undefined` cuando la respuesta
 * no tiene cuerpo, como el HTTP 204 que sigue a un movimiento registrado.
 */
export async function peticion<T>(
  ruta: string,
  { method = 'GET', body, signal }: OpcionesPeticion = {},
): Promise<T | undefined> {
  const limite = AbortSignal.timeout(TIEMPO_MAXIMO_MS)
  const senal = signal ? AbortSignal.any([signal, limite]) : limite

  // Si una operación que modifica datos no obtiene respuesta, no se sabe si el
  // servidor llegó a aplicarla. Nunca se reintenta automáticamente.
  const escritura = method !== 'GET'

  let respuesta: Response
  try {
    respuesta = await fetch(`${urlBaseApi()}${ruta}`, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: senal,
    })
  } catch (error) {
    // Una cancelación pedida por el llamador no es un fallo de conexión.
    if (signal?.aborted) throw error
    throw new ApiError('conexion', null, null, {
      resultadoIncierto: escritura,
      tiempoAgotado: limite.aborted,
    })
  }

  const texto = await leerTexto(respuesta)

  if (!respuesta.ok) {
    if (ESTADOS_SIN_CONEXION.has(respuesta.status)) {
      // El proxy o la pasarela no obtuvo respuesta del servidor de aplicaciones.
      throw new ApiError('conexion', respuesta.status, null, {
        resultadoIncierto: escritura,
        tiempoAgotado: respuesta.status === 504,
      })
    }
    throw new ApiError('http', respuesta.status, extraerMensaje(texto))
  }

  if (respuesta.status === 204 || texto.trim() === '') {
    return undefined
  }
  try {
    return JSON.parse(texto) as T
  } catch {
    // Un 2xx ilegible en una escritura indica que el servidor respondió, pero
    // no se pudo confirmar el resultado.
    throw new ApiError('respuesta', respuesta.status, null, { resultadoIncierto: escritura })
  }
}

async function leerTexto(respuesta: Response): Promise<string> {
  try {
    return await respuesta.text()
  } catch {
    return ''
  }
}

function extraerMensaje(texto: string): string | null {
  if (!texto) return null
  try {
    const datos: unknown = JSON.parse(texto)
    if (
      datos !== null &&
      typeof datos === 'object' &&
      'mensaje' in datos &&
      typeof datos.mensaje === 'string'
    ) {
      return datos.mensaje
    }
  } catch {
    // Cuerpo no JSON: se descarta para no mostrar trazas técnicas.
  }
  return null
}
