package com.stockguard.api.rest.internal;

import com.stockguard.api.rest.internal.dto.CantidadRequest;
import com.stockguard.api.rest.internal.dto.ErrorDto;
import com.stockguard.api.rest.internal.dto.MovimientoDto;
import com.stockguard.api.rest.internal.dto.ProductoDto;
import com.stockguard.inventario.api.InventarioConsultaService;
import com.stockguard.inventario.api.InventarioMovimientosService;
import com.stockguard.inventario.api.InventarioNoDisponibleException;
import com.stockguard.inventario.api.InventarioService;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Recursos HTTP del catálogo y de las operaciones de existencias. */
@Path("productos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Component(
        service = ProductosResource.class,
        scope = ServiceScope.PROTOTYPE,
        property = {
                "osgi.jaxrs.resource=true",
                "osgi.jaxrs.application.select=(osgi.jaxrs.name=stockguard)"
        }
)
public class ProductosResource {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ProductosResource.class);
    private static final Pattern CODIGO_VALIDO =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");

    private final AtomicReference<InventarioService> inventario =
            new AtomicReference<>();
    private final AtomicReference<InventarioMovimientosService> movimientos =
            new AtomicReference<>();
    private final AtomicReference<InventarioConsultaService> consultas =
            new AtomicReference<>();

    @Reference(
            service = InventarioService.class,
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "desvincularInventario"
    )
    void vincularInventario(InventarioService servicio) {
        inventario.set(servicio);
    }

    void desvincularInventario(InventarioService servicio) {
        inventario.compareAndSet(servicio, null);
    }

    @Reference(
            service = InventarioMovimientosService.class,
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "desvincularMovimientos"
    )
    void vincularMovimientos(InventarioMovimientosService servicio) {
        movimientos.set(servicio);
    }

    void desvincularMovimientos(InventarioMovimientosService servicio) {
        movimientos.compareAndSet(servicio, null);
    }

    @Reference(
            service = InventarioConsultaService.class,
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "desvincularConsultas"
    )
    void vincularConsultas(InventarioConsultaService servicio) {
        consultas.set(servicio);
    }

    void desvincularConsultas(InventarioConsultaService servicio) {
        consultas.compareAndSet(servicio, null);
    }

    @GET
    public Response listarProductos() {
        InventarioService servicio = inventario.get();
        if (servicio == null) {
            return servicioNoDisponible();
        }
        try {
            List<ProductoDto> productos = servicio.listarProductos().stream()
                    .map(ProductoDto::desde)
                    .toList();
            return Response.ok(productos).build();
        } catch (RuntimeException error) {
            return manejarError(Fallo.CONSULTA, "listar productos", error);
        }
    }

    @GET
    @Path("{codigo}")
    public Response buscarProducto(@PathParam("codigo") String codigo) {
        if (!codigoValido(codigo)) {
            return error(
                    Response.Status.BAD_REQUEST,
                    "El código del producto no tiene un formato válido."
            );
        }
        InventarioConsultaService servicio = consultas.get();
        if (servicio == null) {
            return servicioNoDisponible();
        }
        try {
            return servicio.buscarProducto(codigo)
                    .<Response>map(producto -> Response.ok(
                            ProductoDto.desde(producto)
                    ).build())
                    .orElseGet(this::productoNoEncontrado);
        } catch (RuntimeException error) {
            return manejarError(Fallo.CONSULTA, "buscar un producto", error);
        }
    }

    @POST
    @Path("{codigo}/entradas")
    public Response registrarEntrada(
            @PathParam("codigo") String codigo,
            CantidadRequest solicitud
    ) {
        Response validacion = validarCodigoYCantidad(codigo, solicitud, true);
        if (validacion != null) {
            return validacion;
        }
        InventarioMovimientosService servicio = movimientos.get();
        if (servicio == null) {
            return servicioNoDisponible();
        }
        try {
            if (!servicio.registrarEntrada(codigo, solicitud.cantidad())) {
                return productoNoEncontrado();
            }
            return Response.noContent().build();
        } catch (RuntimeException error) {
            return manejarError(Fallo.ENTRADA, "registrar una entrada", error);
        }
    }

    @POST
    @Path("{codigo}/salidas")
    public Response registrarSalida(
            @PathParam("codigo") String codigo,
            CantidadRequest solicitud
    ) {
        Response validacion = validarCodigoYCantidad(codigo, solicitud, true);
        if (validacion != null) {
            return validacion;
        }
        InventarioMovimientosService servicio = movimientos.get();
        if (servicio == null) {
            return servicioNoDisponible();
        }
        try {
            if (!servicio.registrarSalida(codigo, solicitud.cantidad())) {
                return productoNoEncontrado();
            }
            return Response.noContent().build();
        } catch (RuntimeException error) {
            return manejarError(Fallo.SALIDA, "registrar una salida", error);
        }
    }

    @POST
    @Path("{codigo}/ajustes")
    public Response ajustarExistencias(
            @PathParam("codigo") String codigo,
            CantidadRequest solicitud
    ) {
        Response validacion = validarCodigoYCantidad(codigo, solicitud, false);
        if (validacion != null) {
            return validacion;
        }
        InventarioService servicio = inventario.get();
        if (servicio == null) {
            return servicioNoDisponible();
        }
        try {
            if (!servicio.actualizarExistencias(codigo, solicitud.cantidad())) {
                return productoNoEncontrado();
            }
            return Response.noContent().build();
        } catch (RuntimeException error) {
            return manejarError(Fallo.AJUSTE, "ajustar existencias", error);
        }
    }

    @GET
    @Path("{codigo}/movimientos")
    public Response buscarMovimientos(@PathParam("codigo") String codigo) {
        if (!codigoValido(codigo)) {
            return error(
                    Response.Status.BAD_REQUEST,
                    "El código del producto no tiene un formato válido."
            );
        }
        InventarioConsultaService servicio = consultas.get();
        if (servicio == null) {
            return servicioNoDisponible();
        }
        try {
            return servicio.buscarMovimientos(codigo)
                    .<Response>map(historial -> Response.ok(
                            historial.stream()
                                    .map(MovimientoDto::desde)
                                    .toList()
                    ).build())
                    .orElseGet(this::productoNoEncontrado);
        } catch (RuntimeException error) {
            return manejarError(Fallo.CONSULTA, "consultar movimientos", error);
        }
    }

    private Response validarCodigoYCantidad(
            String codigo,
            CantidadRequest solicitud,
            boolean debeSerPositiva
    ) {
        if (!codigoValido(codigo)) {
            return error(
                    Response.Status.BAD_REQUEST,
                    "El código del producto no tiene un formato válido."
            );
        }
        if (solicitud == null || solicitud.cantidad() == null) {
            return error(
                    Response.Status.BAD_REQUEST,
                    "El campo cantidad es obligatorio."
            );
        }
        if (debeSerPositiva && solicitud.cantidad() <= 0) {
            return error(
                    Response.Status.BAD_REQUEST,
                    "La cantidad debe ser mayor que cero."
            );
        }
        if (!debeSerPositiva && solicitud.cantidad() < 0) {
            return error(
                    Response.Status.BAD_REQUEST,
                    "La cantidad disponible no puede ser negativa."
            );
        }
        return null;
    }

    private boolean codigoValido(String codigo) {
        return codigo != null && CODIGO_VALIDO.matcher(codigo).matches();
    }

    /**
     * Contexto en el que ocurre un fallo. Un {@link IllegalArgumentException}
     * del servicio significa cosas distintas según la operación, por lo que no
     * puede traducirse siempre al mismo estado HTTP.
     */
    private enum Fallo {
        /** Lecturas: ningún argumento del cliente puede ser inválido aquí. */
        CONSULTA,
        /** El servicio rechaza la entrada si la existencia superaría el máximo. */
        ENTRADA,
        /** El servicio rechaza la salida si supera las existencias. */
        SALIDA,
        /** El servicio solo rechaza cantidades negativas, ya validadas aquí. */
        AJUSTE
    }

    private Response manejarError(Fallo fallo, String operacion, RuntimeException error) {
        if (error instanceof InventarioNoDisponibleException) {
            LOGGER.warn("No está disponible el servicio de Inventario.", error);
            return servicioNoDisponible();
        }
        if (error instanceof IllegalArgumentException) {
            switch (fallo) {
                case ENTRADA -> {
                    LOGGER.warn("Entrada rechazada por Inventario: {}", error.getMessage());
                    return error(
                            Response.Status.CONFLICT,
                            "La entrada supera la cantidad máxima que admite el inventario."
                    );
                }
                case SALIDA -> {
                    LOGGER.warn("Salida rechazada por Inventario: {}", error.getMessage());
                    return error(
                            Response.Status.CONFLICT,
                            "La salida supera las existencias disponibles del producto."
                    );
                }
                case AJUSTE -> {
                    // La cantidad ya se validó al recibir la solicitud: si
                    // Inventario aun así la rechaza, el dato es inválido.
                    LOGGER.warn("Ajuste rechazado por Inventario: {}", error.getMessage());
                    return error(
                            Response.Status.BAD_REQUEST,
                            "La cantidad indicada no es válida para el ajuste."
                    );
                }
                default -> {
                    // Un dato persistido que no se puede interpretar (por
                    // ejemplo, un tipo de movimiento desconocido) no es un
                    // conflicto de negocio: se trata como fallo interno.
                    LOGGER.error("Inconsistencia interna al {}.", operacion, error);
                    return errorInterno();
                }
            }
        }
        LOGGER.error("No fue posible {}.", operacion, error);
        return errorInterno();
    }

    private Response errorInterno() {
        return error(
                Response.Status.INTERNAL_SERVER_ERROR,
                "No fue posible procesar la solicitud."
        );
    }

    private Response productoNoEncontrado() {
        return error(
                Response.Status.NOT_FOUND,
                "No se encontró el producto solicitado."
        );
    }

    private Response servicioNoDisponible() {
        return error(
                Response.Status.SERVICE_UNAVAILABLE,
                "El servicio de inventario no está disponible."
        );
    }

    private Response error(Response.Status estado, String mensaje) {
        return Response.status(estado)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorDto(mensaje))
                .build();
    }
}
