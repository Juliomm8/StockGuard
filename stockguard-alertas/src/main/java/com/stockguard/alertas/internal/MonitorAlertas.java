package com.stockguard.alertas.internal;

import com.stockguard.inventario.api.InventarioService;
import com.stockguard.inventario.api.Producto;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Monitor de existencias bajas.
 *
 * Consume InventarioService de forma opcional y dinámica,
 * por lo que permanece activo aunque Inventario se detenga.
 */
@Component(immediate = true)
public class MonitorAlertas {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MonitorAlertas.class);
    private static final int UMBRAL_STOCK = 5;
    private static final long INTERVALO_REVISION_SEGUNDOS = 10;
    private static final long ESPERA_CIERRE_SEGUNDOS = 2;

    private final AtomicReference<InventarioService> inventarioService =
            new AtomicReference<>();

    private volatile ScheduledExecutorService programador;

    /**
     * OSGi invoca este método cuando encuentra el servicio.
     */
    @Reference(
            service = InventarioService.class,
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "desvincularInventario"
    )
    void vincularInventario(InventarioService servicio) {
        inventarioService.set(servicio);
        LOGGER.info("Servicio de Inventario conectado.");
    }

    /**
     * OSGi invoca este método cuando el servicio desaparece.
     */
    void desvincularInventario(InventarioService servicio) {
        if (inventarioService.compareAndSet(servicio, null)) {
            LOGGER.info(
                    "Servicio de Inventario desconectado. "
                    + "El monitor continuará esperando la reconexión."
            );
        }
    }

    /**
     * Inicia la revisión periódica del inventario.
     */
    @Activate
    void activar() {
        ScheduledExecutorService nuevoProgramador =
                Executors.newSingleThreadScheduledExecutor(
                        tarea -> {
                            Thread hilo = new Thread(
                                    tarea,
                                    "stockguard-monitor-alertas"
                            );
                            hilo.setDaemon(true);
                            return hilo;
                        }
                );
        programador = nuevoProgramador;

        nuevoProgramador.scheduleAtFixedRate(
                this::revisarInventario,
                0,
                INTERVALO_REVISION_SEGUNDOS,
                TimeUnit.SECONDS
        );

        LOGGER.info(
                "Monitor de alertas iniciado; revisión cada {} segundos "
                + "y umbral de stock menor que {}.",
                INTERVALO_REVISION_SEGUNDOS,
                UMBRAL_STOCK
        );
    }

    /**
     * Detiene el monitor cuando se desactiva el bundle.
     */
    @Deactivate
    void desactivar() {
        inventarioService.set(null);
        ScheduledExecutorService programadorActual = programador;
        programador = null;

        if (programadorActual != null) {
            programadorActual.shutdownNow();
            try {
                if (!programadorActual.awaitTermination(
                        ESPERA_CIERRE_SEGUNDOS,
                        TimeUnit.SECONDS
                )) {
                    LOGGER.warn(
                            "El hilo del monitor no terminó dentro de {} "
                            + "segundos.",
                            ESPERA_CIERRE_SEGUNDOS
                    );
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                LOGGER.warn(
                        "Se interrumpió la espera para detener el monitor.",
                        error
                );
            }
        }

        LOGGER.info("Monitor de alertas detenido.");
    }

    /**
     * Detecta productos con existencias inferiores al umbral.
     */
    private void revisarInventario() {
        InventarioService servicio = inventarioService.get();

        if (servicio == null) {
            LOGGER.debug(
                    "Servicio de Inventario no disponible; "
                    + "se esperará la reconexión."
            );
            return;
        }

        try {
            List<Producto> productos = servicio.listarProductos();

            for (Producto producto : productos) {
                if (producto.cantidadDisponible() < UMBRAL_STOCK) {
                    LOGGER.warn(
                            "ALERTA: {} ({}) tiene {} unidades disponibles; "
                            + "el umbral es menor que {}.",
                            producto.nombre(),
                            producto.codigo(),
                            producto.cantidadDisponible(),
                            UMBRAL_STOCK
                    );
                }
            }
        } catch (RuntimeException error) {
            LOGGER.warn(
                    "No se pudo consultar el servicio de Inventario.",
                    error
            );
        }
    }
}
