import { CircleAlert, Info, Loader, TriangleAlert } from 'lucide-react'
import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useInventario } from '../../context/inventario'
import { formatearNumero, formatearVariacion, unidades } from '../../domain/formato'
import {
  OPERACIONES,
  existenciaResultante,
  validarCantidad,
  type Operacion,
} from '../../domain/operaciones'
import type { Producto } from '../../domain/tipos'
import { useRegistrarOperacion } from '../../hooks/useRegistrarOperacion'
import { rutaDetalle } from '../../rutas'
import { AvisoError, Cargando } from '../comun/Estados'
import { InsigniaEstado } from '../comun/Insignias'
import { Modal } from '../comun/Modal'

interface DialogoMovimientoProps {
  operacion: Operacion
  codigoInicial?: string
  onCerrar: () => void
}

type Paso = 'datos' | 'confirmacion'

/** Datos de un envío sin respuesta, cuyo resultado se desconoce. */
interface EnvioIncierto {
  mensaje: string
  producto: Producto
  /** Existencia que tenía el producto cuando se envió la operación. */
  existenciaPrevia: number
}

export function DialogoMovimiento({ operacion, codigoInicial, onCerrar }: DialogoMovimientoProps) {
  const { productos, error: errorCatalogo, recargar } = useInventario()
  const { enviar, enviando, error: errorEnvio, limpiarError } = useRegistrarOperacion(operacion)
  const descripcion = OPERACIONES[operacion]
  const idFormulario = useId()
  const idCantidad = useId()
  const idProducto = useId()

  const [codigo, setCodigo] = useState(codigoInicial ?? '')
  const [texto, setTexto] = useState('')
  const [intentado, setIntentado] = useState(false)
  const [paso, setPaso] = useState<Paso>('datos')
  const [confirmado, setConfirmado] = useState(false)
  const [incierto, setIncierto] = useState<EnvioIncierto | null>(null)
  const navegar = useNavigate()

  const producto = productos?.find((p) => p.codigo === codigo) ?? null
  const validacion = producto ? validarCantidad(operacion, texto, producto.cantidadDisponible) : null
  const mensajeValidacion = intentado && validacion && !validacion.valido ? validacion.mensaje : null
  // Si el catálogo se recarga y la cantidad deja de ser válida, se vuelve al formulario.
  const enConfirmacion = paso === 'confirmacion' && producto !== null && validacion?.valido === true

  const formulario = useRef<HTMLFormElement>(null)
  useEffect(() => {
    const elemento = formulario.current
    if (!elemento) return
    const destino =
      elemento.querySelector<HTMLElement>('[data-autofocus]') ??
      document.querySelector<HTMLElement>(`button[type="submit"][form="${CSS.escape(idFormulario)}"]`)
    destino?.focus()
  }, [enConfirmacion, idFormulario])

  function alEnviar(evento: FormEvent) {
    evento.preventDefault()
    if (enviando) return
    if (!producto || !validacion) {
      setIntentado(true)
      return
    }
    if (!enConfirmacion) {
      setIntentado(true)
      if (validacion.valido) {
        setConfirmado(false)
        setPaso('confirmacion')
      }
      return
    }
    if (!validacion.valido || (operacion === 'ajuste' && !confirmado)) return
    const existenciaPrevia = producto.cantidadDisponible
    void enviar(producto, validacion.cantidad).then((resultado) => {
      if (resultado.estado === 'exito') onCerrar()
      else if (resultado.estado === 'incierta') {
        setIncierto({ mensaje: resultado.mensaje, producto, existenciaPrevia })
      } else setPaso('datos')
    })
  }

  const puedeEnviar = !enConfirmacion || (!enviando && (operacion !== 'ajuste' || confirmado))

  const pie = incierto ? (
    <>
      <button type="button" className="boton boton--secundario" onClick={onCerrar}>
        Cerrar
      </button>
      <button
        type="button"
        className="boton boton--primario"
        onClick={() => {
          navegar(rutaDetalle(incierto.producto.codigo))
          onCerrar()
        }}
      >
        Ver historial del producto
      </button>
    </>
  ) : (
    <>
      {enConfirmacion ? (
        <button
          type="button"
          className="boton boton--secundario"
          onClick={() => setPaso('datos')}
          disabled={enviando}
        >
          Volver
        </button>
      ) : (
        <button type="button" className="boton boton--secundario" onClick={onCerrar}>
          Cancelar
        </button>
      )}
      <button
        type="submit"
        form={idFormulario}
        className={`boton ${operacion === 'ajuste' ? 'boton--advertencia' : 'boton--primario'}`}
        disabled={!puedeEnviar}
      >
        {enviando && <Loader className="girando" size={16} aria-hidden />}
        {!enConfirmacion ? 'Continuar' : enviando ? 'Enviando…' : descripcion.accion}
      </button>
    </>
  )

  return (
    <Modal
      titulo={descripcion.titulo}
      subtitulo={
        incierto
          ? 'No se pudo confirmar el resultado.'
          : enConfirmacion
            ? 'Revise los datos antes de confirmar.'
            : descripcion.ayuda
      }
      onCerrar={onCerrar}
      bloqueado={enviando}
      pie={pie}
    >
      {incierto ? (
        <ResultadoIncierto envio={incierto} />
      ) : (
      <form ref={formulario} id={idFormulario} onSubmit={alEnviar} noValidate>
        {!productos && errorCatalogo ? (
          <AvisoError error={errorCatalogo} onReintentar={recargar} compacto />
        ) : !productos ? (
          <Cargando texto="Cargando productos…" />
        ) : !enConfirmacion ? (
          <>
            {errorEnvio && (
              <div className="aviso aviso--error aviso--compacto" role="alert">
                <CircleAlert className="aviso__icono" size={18} aria-hidden />
                <p className="aviso__texto">{errorEnvio}</p>
              </div>
            )}

            {codigoInicial ? null : (
              <div className="campo">
                <label className="campo__etiqueta" htmlFor={idProducto}>
                  Producto
                </label>
                <select
                  id={idProducto}
                  className="campo__control"
                  value={codigo}
                  data-autofocus
                  onChange={(evento) => {
                    setCodigo(evento.target.value)
                    limpiarError()
                  }}
                  aria-invalid={intentado && !producto}
                >
                  <option value="">Seleccione un producto</option>
                  {productos.map((p) => (
                    <option key={p.codigo} value={p.codigo}>
                      {p.codigo} - {p.nombre}
                    </option>
                  ))}
                </select>
                {intentado && !producto && (
                  <p className="campo__error">Seleccione el producto del movimiento.</p>
                )}
              </div>
            )}

            {codigoInicial && !producto ? (
              <div className="aviso aviso--error aviso--compacto" role="alert">
                <CircleAlert className="aviso__icono" size={18} aria-hidden />
                <p className="aviso__texto">
                  El producto {codigoInicial} no figura en el inventario actual.
                </p>
              </div>
            ) : null}

            {producto && <FichaProducto producto={producto} />}

            {producto && operacion === 'ajuste' && (
              <div className="aviso aviso--advertencia aviso--compacto">
                <Info className="aviso__icono" size={18} aria-hidden />
                <p className="aviso__texto">
                  Un ajuste no suma ni resta unidades: reemplaza la existencia total. Para registrar
                  mercadería recibida o despachada use una entrada o una salida.
                </p>
              </div>
            )}

            {producto && (
              <div className="campo">
                <label className="campo__etiqueta" htmlFor={idCantidad}>
                  {descripcion.etiquetaCantidad}
                </label>
                <input
                  id={idCantidad}
                  className="campo__control campo__control--cantidad"
                  type="text"
                  inputMode="numeric"
                  autoComplete="off"
                  value={texto}
                  data-autofocus={codigoInicial ? true : undefined}
                  onChange={(evento) => {
                    setTexto(evento.target.value)
                    limpiarError()
                  }}
                  aria-invalid={Boolean(mensajeValidacion)}
                  aria-describedby={`${idCantidad}-ayuda`}
                />
                <p id={`${idCantidad}-ayuda`} className={mensajeValidacion ? 'campo__error' : 'campo__ayuda'}>
                  {mensajeValidacion ??
                    (operacion === 'salida'
                      ? `Máximo disponible: ${unidades(producto.cantidadDisponible)}.`
                      : operacion === 'ajuste'
                        ? 'Cantidad total que quedará registrada después del conteo.'
                        : 'Número entero mayor que cero.')}
                </p>
              </div>
            )}

            {producto && validacion?.valido && (
              <Proyeccion
                operacion={operacion}
                actual={producto.cantidadDisponible}
                cantidad={validacion.cantidad}
              />
            )}
          </>
        ) : (
          producto &&
          validacion?.valido && (
            <Confirmacion
              operacion={operacion}
              producto={producto}
              cantidad={validacion.cantidad}
              confirmado={confirmado}
              onConfirmar={setConfirmado}
              deshabilitado={enviando}
            />
          )
        )}
      </form>
      )}
    </Modal>
  )
}

/**
 * Sustituye al formulario cuando no hubo respuesta del servidor. No ofrece
 * reenviar: el usuario debe comprobar el historial antes de repetir la operación.
 */
function ResultadoIncierto({ envio }: { envio: EnvioIncierto }) {
  const { productos, cargando, error } = useInventario()
  const aviso = useRef<HTMLDivElement>(null)
  useEffect(() => {
    aviso.current?.focus()
  }, [])

  const actual = productos?.find((p) => p.codigo === envio.producto.codigo)?.cantidadDisponible
  const consultaFallida = Boolean(error) && !cargando

  return (
    <div className="modal__cuerpo-incierto">
      <div ref={aviso} tabIndex={-1} className="aviso aviso--advertencia" role="alert">
        <TriangleAlert className="aviso__icono" size={18} aria-hidden />
        <div className="aviso__cuerpo">
          <p className="aviso__titulo">El movimiento pudo haberse registrado</p>
          <p className="aviso__texto">{envio.mensaje}</p>
        </div>
      </div>

      <dl className="resumen-operacion" aria-live="polite">
        <div>
          <dt>Producto</dt>
          <dd>
            <span className="fuerte">{envio.producto.codigo}</span> {envio.producto.nombre}
          </dd>
        </div>
        <div>
          <dt>Existencia antes del envío</dt>
          <dd>{unidades(envio.existenciaPrevia)}</dd>
        </div>
        <div className="resumen-operacion__total">
          <dt>Existencia consultada ahora</dt>
          <dd>
            {cargando ? (
              <span className="estado-inline">
                <Loader className="girando" size={14} aria-hidden /> Consultando…
              </span>
            ) : consultaFallida || actual === undefined ? (
              'No se pudo consultar'
            ) : (
              unidades(actual)
            )}
          </dd>
        </div>
      </dl>

      {!cargando && actual !== undefined && !consultaFallida && (
        <p className="campo__ayuda">
          {actual !== envio.existenciaPrevia
            ? 'La existencia cambió desde el envío, lo que sugiere que el movimiento se registró. Confírmelo en el historial.'
            : 'La existencia no ha cambiado todavía. Espere unos segundos y compruebe el historial antes de repetir la operación.'}
        </p>
      )}
    </div>
  )
}

function FichaProducto({ producto }: { producto: Producto }) {
  return (
    <dl className="ficha">
      <div>
        <dt>Código</dt>
        <dd className="fuerte">{producto.codigo}</dd>
      </div>
      <div className="ficha__ancha">
        <dt>Producto</dt>
        <dd>{producto.nombre}</dd>
      </div>
      <div>
        <dt>Existencia actual</dt>
        <dd className="ficha__cantidad">
          {formatearNumero(producto.cantidadDisponible)}
          <InsigniaEstado cantidad={producto.cantidadDisponible} />
        </dd>
      </div>
    </dl>
  )
}

interface ProyeccionProps {
  operacion: Operacion
  actual: number
  cantidad: number
}

/** Existencia antes y después de aplicar la operación. */
function Proyeccion({ operacion, actual, cantidad }: ProyeccionProps) {
  const resultante = existenciaResultante(operacion, actual, cantidad)
  const variacion = resultante - actual
  return (
    <div className="proyeccion" aria-live="polite">
      <div className="proyeccion__valor">
        <span className="proyeccion__etiqueta">Actual</span>
        <span className="proyeccion__numero">{formatearNumero(actual)}</span>
      </div>
      <div className={`proyeccion__cambio ${variacion < 0 ? 'variacion--negativa' : 'variacion--positiva'}`}>
        {formatearVariacion(variacion)}
      </div>
      <div className="proyeccion__valor">
        <span className="proyeccion__etiqueta">Resultante</span>
        <span className="proyeccion__numero">{formatearNumero(resultante)}</span>
      </div>
      <InsigniaEstado cantidad={resultante} />
    </div>
  )
}

interface ConfirmacionProps {
  operacion: Operacion
  producto: Producto
  cantidad: number
  confirmado: boolean
  onConfirmar: (valor: boolean) => void
  deshabilitado: boolean
}

function Confirmacion({ operacion, producto, cantidad, confirmado, onConfirmar, deshabilitado }: ConfirmacionProps) {
  const resultante = existenciaResultante(operacion, producto.cantidadDisponible, cantidad)
  const idCasilla = useId()
  return (
    <>
      <dl className="resumen-operacion">
        <div>
          <dt>Producto</dt>
          <dd>
            <span className="fuerte">{producto.codigo}</span> {producto.nombre}
          </dd>
        </div>
        <div>
          <dt>Existencia actual</dt>
          <dd>{unidades(producto.cantidadDisponible)}</dd>
        </div>
        <div>
          <dt>{OPERACIONES[operacion].etiquetaCantidad}</dt>
          <dd className="fuerte">{unidades(cantidad)}</dd>
        </div>
        {operacion === 'ajuste' && (
          <div>
            <dt>Diferencia</dt>
            <dd>
              {formatearVariacion(resultante - producto.cantidadDisponible)}{' '}
              {Math.abs(resultante - producto.cantidadDisponible) === 1 ? 'unidad' : 'unidades'}
            </dd>
          </div>
        )}
        <div className="resumen-operacion__total">
          <dt>Existencia resultante</dt>
          <dd>{unidades(resultante)}</dd>
        </div>
      </dl>

      {operacion === 'ajuste' && (
        <label className="casilla" htmlFor={idCasilla}>
          <input
            id={idCasilla}
            type="checkbox"
            checked={confirmado}
            disabled={deshabilitado}
            data-autofocus
            onChange={(evento) => onConfirmar(evento.target.checked)}
          />
          <span>
            Confirmo que la existencia total de {producto.codigo} debe quedar en{' '}
            {unidades(cantidad)}. La existencia registrada ahora es de{' '}
            {unidades(producto.cantidadDisponible)}.
          </span>
        </label>
      )}
    </>
  )
}
