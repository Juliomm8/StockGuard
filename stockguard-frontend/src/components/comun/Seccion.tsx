import type { ReactNode } from 'react'

interface SeccionProps {
  titulo: string
  descripcion?: ReactNode
  acciones?: ReactNode
  children: ReactNode
  className?: string
}

/** Bloque de contenido con título, sobre una superficie blanca. */
export function Seccion({ titulo, descripcion, acciones, children, className }: SeccionProps) {
  return (
    <section className={`seccion${className ? ` ${className}` : ''}`}>
      <header className="seccion__cabecera">
        <div>
          <h2 className="seccion__titulo">{titulo}</h2>
          {descripcion && <p className="seccion__descripcion">{descripcion}</p>}
        </div>
        {acciones && <div className="seccion__acciones">{acciones}</div>}
      </header>
      {children}
    </section>
  )
}
