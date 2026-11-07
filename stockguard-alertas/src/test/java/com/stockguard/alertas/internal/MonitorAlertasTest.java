package com.stockguard.alertas.internal;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.stockguard.inventario.api.InventarioService;
import com.stockguard.inventario.api.Producto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Monitor de alertas")
class MonitorAlertasTest {

    private static final long INTERVALO_SEGUNDOS = 10;

    private MonitorAlertas monitor;
    private ScheduledExecutorService programador;
    private Logger logger;
    private Level nivelAnterior;
    private boolean aditivoAnterior;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void prepararMonitor() throws Exception {
        programador = crearProgramadorSimulado();
        monitor = new MonitorAlertas(() -> programador);

        logger = (Logger) LoggerFactory.getLogger(MonitorAlertas.class);
        nivelAnterior = logger.getLevel();
        aditivoAnterior = logger.isAdditive();
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);

        appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void limpiarMonitor() {
        monitor.desactivar();
        logger.detachAppender(appender);
        logger.setLevel(nivelAnterior);
        logger.setAdditive(aditivoAnterior);
        appender.stop();
    }

    @Test
    @DisplayName("Programa la primera revisión inmediata y repite cada diez segundos")
    void programaRevisionConIntervaloConfigurado() {
        monitor.activar();

        Runnable tarea = tareaProgramada(programador);

        assertNotNull(tarea);
        verify(programador).scheduleAtFixedRate(
                any(Runnable.class),
                eq(0L),
                eq(INTERVALO_SEGUNDOS),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("Genera alertas para los productos bajo el umbral, incluido cero")
    void generaAlertasSoloBajoElUmbral() {
        InventarioService inventario = mock(InventarioService.class);
        when(inventario.listarProductos()).thenReturn(List.of(
                new Producto("P-001", "Sin unidades", 0),
                new Producto("P-002", "Mouse inalámbrico Logitech", 3),
                new Producto("P-003", "Monitor Samsung 24 pulgadas", 8),
                new Producto("P-004", "Cable HDMI 2 metros", 1),
                new Producto("P-007", "Audífonos HyperX Cloud", 4),
                new Producto("P-009", "Adaptador USB-C", 2),
                new Producto("P-015", "Soporte para laptop", 5)
        ));

        monitor.vincularInventario(inventario);
        monitor.activar();
        tareaProgramada(programador).run();

        List<String> alertas = mensajesDeAlerta();
        Set<String> codigosAlertados = alertas.stream()
                .map(this::codigoDeAlerta)
                .collect(Collectors.toSet());
        assertEquals(5, alertas.size());
        assertEquals(
                Set.of("P-001", "P-002", "P-004", "P-007", "P-009"),
                codigosAlertados
        );
    }

    @Test
    @DisplayName("No genera alertas para existencias iguales o superiores a cinco")
    void noAlertaEnElUmbralNiPorEncima() {
        InventarioService inventario = mock(InventarioService.class);
        when(inventario.listarProductos()).thenReturn(List.of(
                new Producto("P-015", "Soporte para laptop", 5),
                new Producto("P-016", "Producto con stock normal", 25)
        ));

        monitor.vincularInventario(inventario);
        monitor.activar();
        tareaProgramada(programador).run();

        assertTrue(mensajesDeAlerta().isEmpty());
    }

    @Test
    @DisplayName("Sigue activo sin Inventario y consulta al reconectarse")
    void continuaYSeReconectaAlServicioDeInventario() {
        InventarioService inventarioInicial = mock(InventarioService.class);
        InventarioService inventarioReconectado = mock(InventarioService.class);
        when(inventarioInicial.listarProductos()).thenReturn(List.of());
        when(inventarioReconectado.listarProductos()).thenReturn(List.of(
                new Producto("P-009", "Adaptador USB-C", 2)
        ));

        monitor.activar();
        Runnable tarea = tareaProgramada(programador);

        assertDoesNotThrow(tarea::run);
        verify(programador, never()).shutdownNow();
        verifyNoInteractions(inventarioInicial);

        monitor.vincularInventario(inventarioInicial);
        tarea.run();
        verify(inventarioInicial).listarProductos();

        monitor.desvincularInventario(inventarioInicial);
        tarea.run();
        verify(inventarioInicial).listarProductos();

        monitor.vincularInventario(inventarioReconectado);
        tarea.run();
        verify(inventarioReconectado).listarProductos();
        assertTrue(mensajesDeAlerta().stream()
                .anyMatch(mensaje -> mensaje.contains("P-009")));
    }

    @Test
    @DisplayName("Ununbind tardío no elimina la referencia al servicio nuevo")
    void conservaElServicioNuevoAnteUnUnbindAnterior() {
        InventarioService anterior = mock(InventarioService.class);
        InventarioService actual = mock(InventarioService.class);
        when(actual.listarProductos()).thenReturn(List.of());

        monitor.activar();
        monitor.vincularInventario(anterior);
        monitor.vincularInventario(actual);
        monitor.desvincularInventario(anterior);
        tareaProgramada(programador).run();

        verifyNoInteractions(anterior);
        verify(actual).listarProductos();
    }

    @Test
    @DisplayName("Registra fallos de consulta y permite las siguientes revisiones")
    void manejaErroresDeConsultaSinDetenerElMonitor() {
        InventarioService inventario = mock(InventarioService.class);
        when(inventario.listarProductos())
                .thenThrow(new IllegalStateException("Fallo temporal"))
                .thenReturn(List.of(new Producto("P-007", "Audífonos", 4)));

        monitor.activar();
        monitor.vincularInventario(inventario);
        Runnable tarea = tareaProgramada(programador);

        assertDoesNotThrow(tarea::run);
        assertDoesNotThrow(tarea::run);

        assertTrue(appender.list.stream().anyMatch(event ->
                event.getLevel().equals(Level.WARN)
                        && event.getFormattedMessage()
                        .contains("No se pudo consultar")
        ));
        assertTrue(mensajesDeAlerta().stream()
                .anyMatch(mensaje -> mensaje.contains("P-007")));
        verify(inventario, times(2)).listarProductos();
    }

    @Test
    @DisplayName("Al desactivarse detiene el scheduler y libera la referencia")
    void desactivarDetieneElSchedulerYLimpiaElServicio() throws Exception {
        InventarioService inventario = mock(InventarioService.class);
        monitor.vincularInventario(inventario);
        monitor.activar();
        Runnable tarea = tareaProgramada(programador);

        monitor.desactivar();
        tarea.run();

        verify(programador).shutdownNow();
        verify(programador).awaitTermination(2, TimeUnit.SECONDS);
        verifyNoInteractions(inventario);
    }

    @Test
    @DisplayName("Las reactivaciones crean y cierran un scheduler por ciclo")
    void liberaCadaSchedulerEnCiclosRepetidos() throws Exception {
        ScheduledExecutorService primero = crearProgramadorSimulado();
        ScheduledExecutorService segundo = crearProgramadorSimulado();
        AtomicInteger creaciones = new AtomicInteger();
        MonitorAlertas monitorReactivado = new MonitorAlertas(() ->
                creaciones.getAndIncrement() == 0 ? primero : segundo
        );

        try {
            monitorReactivado.activar();
            monitorReactivado.desactivar();
            monitorReactivado.activar();
            monitorReactivado.desactivar();

            assertEquals(2, creaciones.get());
            verify(primero).scheduleAtFixedRate(
                    any(Runnable.class), eq(0L), eq(INTERVALO_SEGUNDOS),
                    eq(TimeUnit.SECONDS)
            );
            verify(segundo).scheduleAtFixedRate(
                    any(Runnable.class), eq(0L), eq(INTERVALO_SEGUNDOS),
                    eq(TimeUnit.SECONDS)
            );
            verify(primero).shutdownNow();
            verify(segundo).shutdownNow();
            verify(primero).awaitTermination(2, TimeUnit.SECONDS);
            verify(segundo).awaitTermination(2, TimeUnit.SECONDS);
        } finally {
            monitorReactivado.desactivar();
        }
    }

    @Test
    @DisplayName("Tolera cambios concurrentes de servicio durante las consultas")
    void permiteCambiosConcurrentesDeReferencia() throws Exception {
        InventarioService primero = mock(InventarioService.class);
        InventarioService segundo = mock(InventarioService.class);
        when(primero.listarProductos()).thenReturn(List.of());
        when(segundo.listarProductos()).thenReturn(List.of());

        monitor.activar();
        Runnable tarea = tareaProgramada(programador);
        logger.setLevel(Level.OFF);

        int iteraciones = 500;
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        try {
            Future<?> cambios = ejecutor.submit(() -> {
                inicio.await();
                for (int iteracion = 0; iteracion < iteraciones; iteracion++) {
                    monitor.vincularInventario(primero);
                    monitor.desvincularInventario(primero);
                    monitor.vincularInventario(segundo);
                    monitor.desvincularInventario(segundo);
                }
                return null;
            });
            Future<?> consultas = ejecutor.submit(() -> {
                inicio.await();
                for (int iteracion = 0; iteracion < iteraciones * 2; iteracion++) {
                    tarea.run();
                }
                return null;
            });

            inicio.countDown();
            cambios.get(10, TimeUnit.SECONDS);
            consultas.get(10, TimeUnit.SECONDS);
        } finally {
            inicio.countDown();
            ejecutor.shutdownNow();
            assertTrue(ejecutor.awaitTermination(5, TimeUnit.SECONDS));
        }

        monitor.vincularInventario(primero);
        tarea.run();
        verify(primero, atLeastOnce()).listarProductos();
    }

    private ScheduledExecutorService crearProgramadorSimulado()
            throws InterruptedException {
        ScheduledExecutorService simulado = mock(ScheduledExecutorService.class);
        when(simulado.awaitTermination(2, TimeUnit.SECONDS)).thenReturn(true);
        return simulado;
    }

    private Runnable tareaProgramada(ScheduledExecutorService scheduler) {
        ArgumentCaptor<Runnable> capturador =
                ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(
                capturador.capture(),
                eq(0L),
                eq(INTERVALO_SEGUNDOS),
                eq(TimeUnit.SECONDS)
        );
        return capturador.getValue();
    }

    private List<String> mensajesDeAlerta() {
        return appender.list.stream()
                .filter(event -> event.getLevel().equals(Level.WARN))
                .map(ILoggingEvent::getFormattedMessage)
                .filter(mensaje -> mensaje.startsWith("ALERTA:"))
                .toList();
    }

    private String codigoDeAlerta(String mensaje) {
        int inicioCodigo = mensaje.indexOf('(') + 1;
        int finCodigo = mensaje.indexOf(')', inicioCodigo);
        return mensaje.substring(inicioCodigo, finCodigo);
    }
}
