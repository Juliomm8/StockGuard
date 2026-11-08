import { MapPinOff } from 'lucide-react'
import { Link } from 'react-router'
import { EstadoVacio } from '../components/comun/Estados'
import { RUTAS } from '../rutas'

export function NoEncontrada() {
  return (
    <EstadoVacio
      icono={MapPinOff}
      titulo="Esta página no existe"
      accion={
        <Link className="boton boton--secundario" to={RUTAS.panel}>
          Ir al panel general
        </Link>
      }
    >
      La dirección no corresponde a ninguna sección de StockGuard.
    </EstadoVacio>
  )
}
