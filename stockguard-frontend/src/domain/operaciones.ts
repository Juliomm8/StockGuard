import type { TipoMovimiento } from './tipos'

/** Máximo representable por el `int` de Java que usa el backend. */
export const CANTIDAD_MAXIMA = 2_147_483_647

export type Operacion = 'entrada' | 'salida' | 'ajuste'

export interface DescripcionOperacion {
  titulo: string
  accion: string
  exito: string
  etiquetaCantidad: string
  ayuda: string
  conflicto: string
}

export const OPERACIONES: Record<Operacion, DescripcionOperacion> = {
  entrada: {
    titulo: 'Entrada de inventario',
    accion: 'Registrar entrada',
    exito: 'Entrada registrada',
    etiquetaCantidad: 'Unidades que ingresan',
    ayuda: 'Se suman a la existencia actual del producto.',
    conflicto:
      'La entrada no se registró: la existencia resultante supera el máximo permitido.',
  },
  salida: {
    titulo: 'Salida de inventario',
    accion: 'Registrar salida',
    exito: 'Salida registrada',
    etiquetaCantidad: 'Unidades que salen',
    ayuda: 'Se descuentan de la existencia actual del producto.',
    conflicto:
      'La salida no se registró: supera las existencias disponibles del producto.',
  },
  ajuste: {
    titulo: 'Ajuste de inventario',
    accion: 'Aplicar ajuste',
    exito: 'Ajuste aplicado',
    etiquetaCantidad: 'Nueva existencia total',
    ayuda:
      'Reemplaza la existencia actual por la cantidad indicada, por ejemplo tras un conteo físico.',
    conflicto: 'El ajuste no es compatible con las existencias actuales del producto.',
  },
}

export const ETIQUETA_TIPO: Record<TipoMovimiento, string> = {
  ENTRADA: 'Entrada',
  SALIDA: 'Salida',
  AJUSTE: 'Ajuste',
}

/** Existencia que quedará tras aplicar la operación. */
export function existenciaResultante(
  operacion: Operacion,
  actual: number,
  cantidad: number,
): number {
  switch (operacion) {
    case 'entrada':
      return actual + cantidad
    case 'salida':
      return actual - cantidad
    case 'ajuste':
      return cantidad
  }
}

/** Variación con signo que produce un movimiento sobre la existencia. */
export function variacionMovimiento(
  existenciaAnterior: number,
  existenciaPosterior: number,
): number {
  return existenciaPosterior - existenciaAnterior
}

export type ResultadoValidacion =
  | { valido: true; cantidad: number }
  | { valido: false; mensaje: string }

/**
 * Valida la cantidad con las mismas reglas que `ProductosResource` y el
 * repositorio de Inventario, para avisar antes de enviar la petición.
 * El servidor sigue siendo quien decide; esta validación solo anticipa.
 */
export function validarCantidad(
  operacion: Operacion,
  texto: string,
  existenciaActual: number,
): ResultadoValidacion {
  const valor = texto.trim()
  if (valor === '') {
    return { valido: false, mensaje: 'Indique una cantidad.' }
  }
  if (!/^\d+$/.test(valor)) {
    return {
      valido: false,
      mensaje: 'La cantidad debe ser un número entero, sin decimales ni signos.',
    }
  }
  const cantidad = Number(valor)
  if (cantidad > CANTIDAD_MAXIMA) {
    return { valido: false, mensaje: 'La cantidad supera el máximo permitido.' }
  }

  if (operacion === 'ajuste') {
    if (cantidad === existenciaActual) {
      return {
        valido: false,
        mensaje: 'La nueva existencia coincide con la actual; no hay diferencia que ajustar.',
      }
    }
    return { valido: true, cantidad }
  }

  if (cantidad === 0) {
    return { valido: false, mensaje: 'La cantidad debe ser mayor que cero.' }
  }
  if (operacion === 'salida' && cantidad > existenciaActual) {
    return {
      valido: false,
      mensaje: `La salida supera las existencias disponibles (${existenciaActual}).`,
    }
  }
  if (operacion === 'entrada' && existenciaActual + cantidad > CANTIDAD_MAXIMA) {
    return {
      valido: false,
      mensaje: 'La existencia resultante supera el máximo permitido.',
    }
  }
  return { valido: true, cantidad }
}
