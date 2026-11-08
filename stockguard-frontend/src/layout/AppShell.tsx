import { BellRing, Boxes, LayoutDashboard, Menu, RefreshCw, X } from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet } from 'react-router'
import { useInventario, type EstadoConexion } from '../context/inventario'
import { formatearHora } from '../domain/formato'
import { requiereAtencion } from '../domain/stock'
import { RUTAS } from '../rutas'

const TEXTO_CONEXION: Record<EstadoConexion, string> = {
  comprobando: 'Conectando con el servidor',
  conectado: 'Conectado',
  'sin-conexion': 'Sin conexión con el servidor',
  'servicio-no-disponible': 'Inventario no disponible',
}

export function AppShell() {
  const [menuAbierto, setMenuAbierto] = useState(false)

  return (
    <div className={`aplicacion${menuAbierto ? ' aplicacion--menu-abierto' : ''}`}>
      <a className="saltar-contenido" href="#contenido">
        Ir al contenido
      </a>
      <BarraLateral onCerrar={() => setMenuAbierto(false)} />
      {menuAbierto && <div className="aplicacion__velo" onClick={() => setMenuAbierto(false)} aria-hidden />}
      <div className="aplicacion__principal">
        <BarraSuperior onAbrirMenu={() => setMenuAbierto(true)} />
        <main id="contenido" className="contenido" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}

function BarraLateral({ onCerrar }: { onCerrar: () => void }) {
  const { productos } = useInventario()
  const enAlerta = productos?.filter((p) => requiereAtencion(p.cantidadDisponible)).length ?? 0

  const enlaces = [
    { a: RUTAS.panel, texto: 'Panel general', icono: LayoutDashboard, fin: true },
    { a: RUTAS.inventario, texto: 'Inventario', icono: Boxes, fin: false },
    { a: RUTAS.alertas, texto: 'Alertas', icono: BellRing, fin: false, contador: enAlerta },
  ]

  return (
    <aside className="barra-lateral" aria-label="Navegación principal">
      <div className="barra-lateral__marca">
        <img src="/favicon.svg" alt="" width={28} height={28} />
        <div>
          <p className="barra-lateral__nombre">StockGuard</p>
          <p className="barra-lateral__lema">Gestión de inventario</p>
        </div>
        <button
          type="button"
          className="boton-icono barra-lateral__cerrar"
          onClick={onCerrar}
          aria-label="Cerrar menú"
        >
          <X size={18} aria-hidden />
        </button>
      </div>
      <nav>
        <ul className="navegacion">
          {enlaces.map(({ a, texto, icono: Icono, fin, contador }) => (
            <li key={a}>
              <NavLink to={a} end={fin} className="navegacion__enlace" onClick={onCerrar}>
                <Icono size={18} aria-hidden />
                <span>{texto}</span>
                {contador ? (
                  <span className="navegacion__contador" aria-label={`${contador} productos bajo el umbral`}>
                    {contador}
                  </span>
                ) : null}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </aside>
  )
}

function BarraSuperior({ onAbrirMenu }: { onAbrirMenu: () => void }) {
  const { conexion, actualizadoEn, cargando, recargar } = useInventario()

  return (
    <header className="barra-superior">
      <button type="button" className="boton-icono barra-superior__menu" onClick={onAbrirMenu} aria-label="Abrir menú">
        <Menu size={20} aria-hidden />
      </button>
      <p className="barra-superior__marca-movil">StockGuard</p>

      <div className="barra-superior__estado">
        <span className={`conexion conexion--${conexion}`} role="status">
          <span className="conexion__punto" aria-hidden />
          <span className="conexion__texto">{TEXTO_CONEXION[conexion]}</span>
        </span>
        {actualizadoEn && (
          <span className="barra-superior__hora">Actualizado a las {formatearHora(actualizadoEn)}</span>
        )}
        <button
          type="button"
          className="boton boton--secundario boton--pequeno"
          onClick={recargar}
          disabled={cargando}
          aria-label="Actualizar datos"
        >
          <RefreshCw className={cargando ? 'girando' : undefined} size={15} aria-hidden />
          <span className="ocultar-movil">Actualizar</span>
        </button>
      </div>
    </header>
  )
}
