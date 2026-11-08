import type { ReactNode } from 'react'

interface EncabezadoPaginaProps {
  titulo: ReactNode
  descripcion?: ReactNode
  antes?: ReactNode
  acciones?: ReactNode
}

export function EncabezadoPagina({ titulo, descripcion, antes, acciones }: EncabezadoPaginaProps) {
  return (
    <header className="encabezado-pagina">
      {antes}
      <div className="encabezado-pagina__fila">
        <div className="encabezado-pagina__titulos">
          <h1 className="encabezado-pagina__titulo">{titulo}</h1>
          {descripcion && <p className="encabezado-pagina__descripcion">{descripcion}</p>}
        </div>
        {acciones && <div className="encabezado-pagina__acciones">{acciones}</div>}
      </div>
    </header>
  )
}
