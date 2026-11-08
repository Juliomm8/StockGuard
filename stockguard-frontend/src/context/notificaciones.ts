import { createContext, useContext } from 'react'

export type TipoNotificacion = 'exito' | 'error'

export interface ValorNotificaciones {
  notificar: (tipo: TipoNotificacion, titulo: string, detalle?: string) => void
}

export const NotificacionesContext = createContext<ValorNotificaciones | null>(null)

export function useNotificaciones(): ValorNotificaciones {
  const valor = useContext(NotificacionesContext)
  if (!valor) throw new Error('useNotificaciones debe usarse dentro de NotificacionesProvider')
  return valor
}
