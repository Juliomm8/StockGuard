import { X } from 'lucide-react'
import { useEffect, useId, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface ModalProps {
  titulo: string
  subtitulo?: ReactNode
  onCerrar: () => void
  /** Impide cerrar mientras hay una petición en curso. */
  bloqueado?: boolean
  children: ReactNode
  pie?: ReactNode
}

const SELECTOR_ENFOCABLE =
  'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

export function Modal({ titulo, subtitulo, onCerrar, bloqueado = false, children, pie }: ModalProps) {
  const idTitulo = useId()
  const panel = useRef<HTMLDivElement>(null)
  const cerrarRef = useRef(onCerrar)
  const bloqueadoRef = useRef(bloqueado)

  useEffect(() => {
    cerrarRef.current = onCerrar
    bloqueadoRef.current = bloqueado
  })

  useEffect(() => {
    const previo = document.activeElement as HTMLElement | null
    const elemento = panel.current
    const primero =
      elemento?.querySelector<HTMLElement>('[data-autofocus]') ??
      elemento?.querySelector<HTMLElement>(SELECTOR_ENFOCABLE)
    primero?.focus()

    const desbordamiento = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    function alPulsarTecla(evento: KeyboardEvent) {
      if (evento.key === 'Escape' && !bloqueadoRef.current) {
        evento.preventDefault()
        cerrarRef.current()
        return
      }
      if (evento.key !== 'Tab' || !elemento) return
      const enfocables = Array.from(elemento.querySelectorAll<HTMLElement>(SELECTOR_ENFOCABLE))
      if (enfocables.length === 0) return
      const inicio = enfocables[0]
      const fin = enfocables[enfocables.length - 1]
      if (evento.shiftKey && document.activeElement === inicio) {
        evento.preventDefault()
        fin.focus()
      } else if (!evento.shiftKey && document.activeElement === fin) {
        evento.preventDefault()
        inicio.focus()
      }
    }

    document.addEventListener('keydown', alPulsarTecla)
    return () => {
      document.removeEventListener('keydown', alPulsarTecla)
      document.body.style.overflow = desbordamiento
      previo?.focus()
    }
  }, [])

  return createPortal(
    <div
      className="modal__fondo"
      onMouseDown={(evento) => {
        if (evento.target === evento.currentTarget && !bloqueado) onCerrar()
      }}
    >
      <div
        ref={panel}
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby={idTitulo}
      >
        <header className="modal__cabecera">
          <div>
            <h2 id={idTitulo} className="modal__titulo">
              {titulo}
            </h2>
            {subtitulo && <p className="modal__subtitulo">{subtitulo}</p>}
          </div>
          <button
            type="button"
            className="boton-icono"
            onClick={onCerrar}
            disabled={bloqueado}
            aria-label="Cerrar"
          >
            <X size={18} aria-hidden />
          </button>
        </header>
        <div className="modal__cuerpo">{children}</div>
        {pie && <footer className="modal__pie">{pie}</footer>}
      </div>
    </div>,
    document.body,
  )
}
