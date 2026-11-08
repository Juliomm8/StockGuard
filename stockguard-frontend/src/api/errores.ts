export type TipoFallo = 'conexion' | 'http' | 'respuesta'

export interface DetalleFallo {
  /**
   * Se envió una operación que modifica datos y no se recibió una respuesta
   * del servidor que confirme o rechace el resultado: pudo haberse aplicado.
   */
  resultadoIncierto?: boolean
  /** El fallo se debió a que se agotó el tiempo de espera. */
  tiempoAgotado?: boolean
}

/** Fallo de comunicación con la API REST, sin detalles internos del servidor. */
export class ApiError extends Error {
  readonly tipo: TipoFallo
  readonly estado: number | null
  /** Contenido del campo `mensaje` de `ErrorDto`, si el servidor lo envió. */
  readonly mensajeServidor: string | null
  readonly resultadoIncierto: boolean
  readonly tiempoAgotado: boolean

  constructor(
    tipo: TipoFallo,
    estado: number | null,
    mensajeServidor: string | null = null,
    { resultadoIncierto = false, tiempoAgotado = false }: DetalleFallo = {},
  ) {
    super(mensajeServidor ?? `Fallo de la API (${tipo}${estado ? ` ${estado}` : ''})`)
    this.name = 'ApiError'
    this.tipo = tipo
    this.estado = estado
    this.mensajeServidor = mensajeServidor
    this.resultadoIncierto = resultadoIncierto
    this.tiempoAgotado = tiempoAgotado
  }
}

export const MENSAJE_SIN_CONEXION =
  'No se puede conectar con el servidor de StockGuard. Compruebe que Apache Karaf y la API REST estén en ejecución.'

export const AVISO_COMPROBAR_HISTORIAL =
  'Compruebe el historial del producto antes de volver a realizar la operación.'

export interface ContextoError {
  /** Mensaje específico para HTTP 409 según la operación realizada. */
  conflicto?: string
}

/** Traduce cualquier fallo a un mensaje comprensible para el usuario. */
export function describirError(error: unknown, contexto: ContextoError = {}): string {
  if (!(error instanceof ApiError)) {
    return 'Se produjo un error inesperado en la aplicación.'
  }
  if (error.resultadoIncierto) {
    const causa = error.tiempoAgotado
      ? 'El servidor tardó demasiado en responder'
      : error.tipo === 'respuesta'
        ? 'La respuesta del servidor no pudo interpretarse'
        : 'Se perdió la comunicación con el servidor'
    return `${causa} y no se recibió confirmación. El movimiento pudo haberse registrado. ${AVISO_COMPROBAR_HISTORIAL}`
  }
  if (error.tipo === 'conexion') {
    return MENSAJE_SIN_CONEXION
  }
  if (error.tipo === 'respuesta') {
    return 'El servidor envió una respuesta que no se pudo interpretar.'
  }

  switch (error.estado) {
    case 400:
      return error.mensajeServidor ?? 'Los datos enviados no son válidos.'
    case 404:
      return 'No se encontró el producto solicitado. Compruebe que el código exista.'
    case 409:
      return (
        contexto.conflicto ??
        'La operación no es compatible con las existencias actuales del producto.'
      )
    case 503:
      return 'El servicio de inventario no está disponible en este momento. Inténtelo de nuevo en unos segundos.'
    case 500:
      return 'El servidor no pudo procesar la solicitud. Inténtelo de nuevo más tarde.'
    default:
      return `El servidor respondió con un estado inesperado (HTTP ${error.estado}).`
  }
}

export function esErrorDeConexion(error: unknown): boolean {
  return error instanceof ApiError && error.tipo === 'conexion'
}

/** El resultado de una operación de escritura es desconocido: no repetirla a ciegas. */
export function esResultadoIncierto(error: unknown): boolean {
  return error instanceof ApiError && error.resultadoIncierto
}

export function esNoEncontrado(error: unknown): boolean {
  return error instanceof ApiError && error.estado === 404
}
