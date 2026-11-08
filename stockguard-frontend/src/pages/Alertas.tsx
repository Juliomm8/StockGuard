import { BellOff, CircleCheck, Info } from 'lucide-react'
import { EncabezadoPagina } from '../components/comun/EncabezadoPagina'
import { AvisoError, Cargando, EstadoVacio } from '../components/comun/Estados'
import { Seccion } from '../components/comun/Seccion'
import { TablaProductos } from '../components/productos/TablaProductos'
import { useInventario } from '../context/inventario'
import { productosQueRequierenAtencion } from '../domain/inventario'
import { INTERVALO_MONITOR_SEGUNDOS, UMBRAL_STOCK } from '../domain/stock'
import type { Producto } from '../domain/tipos'

export function Alertas() {
  const { productos, error, recargar } = useInventario()

  return (
    <>
      <EncabezadoPagina
        titulo="Alertas de stock"
        descripcion="Productos con existencias por debajo del umbral del monitor de alertas."
      />

      <div className="aviso aviso--informacion">
        <Info className="aviso__icono" size={18} aria-hidden />
        <div className="aviso__cuerpo">
          <p className="aviso__titulo">Cómo genera alertas StockGuard</p>
          <p className="aviso__texto">
            El módulo de alertas revisa el inventario cada {INTERVALO_MONITOR_SEGUNDOS} segundos.
            Cuando un producto queda con menos de {UMBRAL_STOCK} unidades registra una alerta, y
            cuando se repone registra su recuperación. Cada producto genera una sola alerta hasta
            que vuelve a la normalidad.
          </p>
        </div>
      </div>

      <Seccion
        titulo="Productos bajo el umbral"
        descripcion={`Calculado con la existencia actual de cada producto y la misma regla del monitor: menos de ${UMBRAL_STOCK} unidades.`}
      >
        {!productos && error ? (
          <AvisoError error={error} onReintentar={recargar} compacto />
        ) : !productos ? (
          <Cargando texto="Consultando el inventario…" />
        ) : (
          <ProductosBajoUmbral productos={productos} />
        )}
      </Seccion>

      <Seccion
        titulo="Historial de alertas"
        descripcion="Alertas y recuperaciones emitidas por el monitor."
      >
        <EstadoVacio icono={BellOff} titulo="La consulta del historial aún no está disponible">
          El monitor escribe sus alertas en el registro de Apache Karaf y la API REST todavía no
          ofrece un servicio para consultarlas. Mientras tanto, pueden revisarse con{' '}
          <code>log:display</code> en la consola de Karaf.
        </EstadoVacio>
      </Seccion>
    </>
  )
}

function ProductosBajoUmbral({ productos }: { productos: Producto[] }) {
  const enAlerta = productosQueRequierenAtencion(productos)
  if (enAlerta.length === 0) {
    return (
      <EstadoVacio icono={CircleCheck} titulo="Ningún producto está bajo el umbral">
        Todos los productos tienen al menos {UMBRAL_STOCK} unidades disponibles.
      </EstadoVacio>
    )
  }
  return <TablaProductos productos={enAlerta} titulo="Productos bajo el umbral de alerta" />
}
