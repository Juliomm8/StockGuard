import { createContext, useContext } from 'react'
import type { Operacion } from '../domain/operaciones'

export interface ValorOperaciones {
  /** Abre el diálogo de la operación; sin código, permite elegir el producto. */
  abrirOperacion: (operacion: Operacion, codigo?: string) => void
}

export const OperacionesContext = createContext<ValorOperaciones | null>(null)

export function useOperaciones(): ValorOperaciones {
  const valor = useContext(OperacionesContext)
  if (!valor) throw new Error('useOperaciones debe usarse dentro de OperacionesProvider')
  return valor
}
