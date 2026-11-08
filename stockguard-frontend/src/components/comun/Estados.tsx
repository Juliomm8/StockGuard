import { CircleAlert, Loader, RefreshCw, WifiOff, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { describirError, esErrorDeConexion } from '../../api/errores'

export function Cargando({ texto = 'Cargando…' }: { texto?: string }) {
  return (
    <div className="estado-carga" role="status">
      <Loader className="girando" size={18} aria-hidden />
      <span>{texto}</span>
    </div>
  )
}

interface EstadoVacioProps {
  icono: LucideIcon
  titulo: string
  children?: ReactNode
  accion?: ReactNode
}

export function EstadoVacio({ icono: Icono, titulo, children, accion }: EstadoVacioProps) {
  return (
    <div className="estado-vacio">
      <Icono className="estado-vacio__icono" size={28} strokeWidth={1.5} aria-hidden />
      <p className="estado-vacio__titulo">{titulo}</p>
      {children && <div className="estado-vacio__texto">{children}</div>}
      {accion}
    </div>
  )
}

interface AvisoErrorProps {
  error?: unknown
  titulo?: string
  /** Sustituye al mensaje derivado de `error`. */
  mensaje?: string
  onReintentar?: () => void
  compacto?: boolean
}

/** Error de carga con mensaje comprensible y opción de reintentar. */
export function AvisoError({ error, titulo, mensaje, onReintentar, compacto = false }: AvisoErrorProps) {
  const sinConexion = esErrorDeConexion(error)
  const Icono = sinConexion ? WifiOff : CircleAlert
  return (
    <div className={`aviso aviso--error${compacto ? ' aviso--compacto' : ''}`} role="alert">
      <Icono className="aviso__icono" size={18} aria-hidden />
      <div className="aviso__cuerpo">
        <p className="aviso__titulo">
          {titulo ?? (sinConexion ? 'Sin conexión con el servidor' : 'No se pudieron cargar los datos')}
        </p>
        <p className="aviso__texto">{mensaje ?? describirError(error)}</p>
      </div>
      {onReintentar && (
        <button type="button" className="boton boton--secundario" onClick={onReintentar}>
          <RefreshCw size={16} aria-hidden />
          Reintentar
        </button>
      )}
    </div>
  )
}
