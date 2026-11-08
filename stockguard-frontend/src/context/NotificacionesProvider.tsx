import { CircleAlert, CircleCheck, X } from 'lucide-react'
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { NotificacionesContext, type TipoNotificacion, type ValorNotificaciones } from './notificaciones'

interface Notificacion {
  id: number
  tipo: TipoNotificacion
  titulo: string
  detalle?: string
}

const DURACION_MS: Record<TipoNotificacion, number> = { exito: 5000, error: 9000 }

export function NotificacionesProvider({ children }: { children: ReactNode }) {
  const [lista, setLista] = useState<Notificacion[]>([])
  const siguienteId = useRef(1)

  const cerrar = useCallback((id: number) => {
    setLista((actual) => actual.filter((n) => n.id !== id))
  }, [])

  const notificar = useCallback<ValorNotificaciones['notificar']>((tipo, titulo, detalle) => {
    const id = siguienteId.current++
    setLista((actual) => [...actual.slice(-3), { id, tipo, titulo, detalle }])
  }, [])

  const valor = useMemo(() => ({ notificar }), [notificar])

  return (
    <NotificacionesContext.Provider value={valor}>
      {children}
      <div className="notificaciones" role="region" aria-label="Notificaciones">
        {lista.map((n) => (
          <ElementoNotificacion key={n.id} notificacion={n} onCerrar={cerrar} />
        ))}
      </div>
    </NotificacionesContext.Provider>
  )
}

function ElementoNotificacion({
  notificacion,
  onCerrar,
}: {
  notificacion: Notificacion
  onCerrar: (id: number) => void
}) {
  const { id, tipo, titulo, detalle } = notificacion

  useEffect(() => {
    const temporizador = window.setTimeout(() => onCerrar(id), DURACION_MS[tipo])
    return () => window.clearTimeout(temporizador)
  }, [id, tipo, onCerrar])

  const Icono = tipo === 'exito' ? CircleCheck : CircleAlert
  return (
    <div
      className={`notificacion notificacion--${tipo}`}
      role={tipo === 'error' ? 'alert' : 'status'}
    >
      <Icono className="notificacion__icono" size={18} aria-hidden />
      <div className="notificacion__texto">
        <p className="notificacion__titulo">{titulo}</p>
        {detalle && <p className="notificacion__detalle">{detalle}</p>}
      </div>
      <button
        type="button"
        className="boton-icono notificacion__cerrar"
        onClick={() => onCerrar(id)}
        aria-label="Cerrar notificación"
      >
        <X size={16} aria-hidden />
      </button>
    </div>
  )
}
