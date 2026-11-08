import { useCallback, useState } from 'react'
import { ApiError, describirError, esResultadoIncierto } from '../api/errores'
import { registrarOperacion } from '../api/productos'
import { useInventario } from '../context/inventario'
import { useNotificaciones } from '../context/notificaciones'
import { unidades } from '../domain/formato'
import { OPERACIONES, type Operacion } from '../domain/operaciones'
import type { Producto } from '../domain/tipos'

function mensajeExito(operacion: Operacion, producto: Producto, cantidad: number): string {
  switch (operacion) {
    case 'entrada':
      return `Ingresaron ${unidades(cantidad)} de ${producto.codigo} ${producto.nombre}.`
    case 'salida':
      return `Salieron ${unidades(cantidad)} de ${producto.codigo} ${producto.nombre}.`
    case 'ajuste':
      return `La existencia de ${producto.codigo} ${producto.nombre} quedó en ${unidades(cantidad)}.`
  }
}

/**
 * - `exito`: el servidor confirmó la operación.
 * - `rechazada`: el servidor respondió que no se realizó (400, 404, 409, 503…).
 * - `incierta`: no hubo respuesta, así que el movimiento pudo haberse registrado.
 */
export type ResultadoEnvio =
  | { estado: 'exito' }
  | { estado: 'rechazada' }
  | { estado: 'incierta'; mensaje: string }

/**
 * Envía una operación de inventario, notifica el resultado y solicita recargar
 * los datos. La petición nunca se reenvía automáticamente.
 */
export function useRegistrarOperacion(operacion: Operacion) {
  const { recargar } = useInventario()
  const { notificar } = useNotificaciones()
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const enviar = useCallback(
    async (producto: Producto, cantidad: number): Promise<ResultadoEnvio> => {
      const descripcion = OPERACIONES[operacion]
      setEnviando(true)
      setError(null)
      try {
        await registrarOperacion(operacion, producto.codigo, cantidad)
        notificar('exito', descripcion.exito, mensajeExito(operacion, producto, cantidad))
        recargar()
        return { estado: 'exito' }
      } catch (fallo) {
        if (esResultadoIncierto(fallo)) {
          // Sin confirmación no hay mensaje de éxito ni de rechazo. Se vuelven
          // a consultar producto e historial para que el usuario compruebe qué
          // ocurrió antes de repetir la operación.
          recargar()
          return { estado: 'incierta', mensaje: describirError(fallo) }
        }
        const mensaje = describirError(fallo, { conflicto: descripcion.conflicto })
        setError(mensaje)
        notificar('error', `No se pudo completar: ${descripcion.titulo.toLowerCase()}`, mensaje)
        // Ante un conflicto o un producto inexistente las existencias pueden
        // haber cambiado en el servidor: se vuelven a consultar.
        if (fallo instanceof ApiError && (fallo.estado === 404 || fallo.estado === 409)) {
          recargar()
        }
        return { estado: 'rechazada' }
      } finally {
        setEnviando(false)
      }
    },
    [operacion, notificar, recargar],
  )

  return { enviar, enviando, error, limpiarError: () => setError(null) }
}
