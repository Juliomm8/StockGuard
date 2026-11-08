import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { DialogoMovimiento } from '../components/movimientos/DialogoMovimiento'
import type { Operacion } from '../domain/operaciones'
import { OperacionesContext } from './operaciones'

interface SolicitudOperacion {
  operacion: Operacion
  /** Producto preseleccionado; si falta, el diálogo permite elegirlo. */
  codigo?: string
}

/** Expone un único diálogo de movimientos accesible desde cualquier pantalla. */
export function OperacionesProvider({ children }: { children: ReactNode }) {
  const [solicitud, setSolicitud] = useState<SolicitudOperacion | null>(null)

  const abrirOperacion = useCallback((operacion: Operacion, codigo?: string) => {
    setSolicitud({ operacion, codigo })
  }, [])

  const valor = useMemo(() => ({ abrirOperacion }), [abrirOperacion])

  return (
    <OperacionesContext.Provider value={valor}>
      {children}
      {solicitud && (
        <DialogoMovimiento
          operacion={solicitud.operacion}
          codigoInicial={solicitud.codigo}
          onCerrar={() => setSolicitud(null)}
        />
      )}
    </OperacionesContext.Provider>
  )
}
