import { useEffect, useRef, useState } from 'react'

export interface Recurso<T> {
  datos: T | null
  error: unknown
  cargando: boolean
  actualizadoEn: Date | null
}

/** Resultado de la última petición completada. */
interface Resultado<T> {
  base: string | null
  revision: number
  datos: T | null
  error: unknown
  actualizadoEn: Date | null
}

/**
 * Carga datos asíncronos y cancela la petición anterior cuando cambian los
 * parámetros.
 *
 * - `base` identifica el recurso; si cambia, los datos previos se descartan.
 * - `revision` fuerza una recarga conservando los datos previos mientras llega
 *   la respuesta, para no vaciar la pantalla tras registrar un movimiento.
 * - Con `base === null` no se carga nada.
 */
export function useRecurso<T>(
  base: string | null,
  revision: number,
  cargar: (signal: AbortSignal) => Promise<T>,
): Recurso<T> {
  const cargarRef = useRef(cargar)
  useEffect(() => {
    cargarRef.current = cargar
  })

  const [resultado, setResultado] = useState<Resultado<T>>({
    base: null,
    revision: -1,
    datos: null,
    error: null,
    actualizadoEn: null,
  })

  useEffect(() => {
    if (base === null) return
    const controlador = new AbortController()

    cargarRef.current(controlador.signal).then(
      (datos) => {
        if (controlador.signal.aborted) return
        setResultado({ base, revision, datos, error: null, actualizadoEn: new Date() })
      },
      (error: unknown) => {
        if (controlador.signal.aborted) return
        setResultado((previo) =>
          previo.base === base
            ? { ...previo, revision, error }
            : { base, revision, datos: null, error, actualizadoEn: null },
        )
      },
    )

    return () => controlador.abort()
  }, [base, revision])

  const mismaBase = base !== null && resultado.base === base
  const cargando = base !== null && (!mismaBase || resultado.revision !== revision)

  return {
    datos: mismaBase ? resultado.datos : null,
    error: mismaBase && !cargando ? resultado.error : null,
    cargando,
    actualizadoEn: mismaBase ? resultado.actualizadoEn : null,
  }
}
