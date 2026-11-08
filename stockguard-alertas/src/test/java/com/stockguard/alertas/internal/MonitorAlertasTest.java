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
    @DisplayName("No repite una alerta mientras el producto siga bajo el umbral")
    void evitaAlertasDuplicadasEnCiclosConsecutivos() {
        InventarioService inventario = mock(InventarioService.class);
        when(inventario.listarProductos()).thenReturn(List.of(
                new Producto("P-002", "Mouse inalámbrico Logitech", 3)
        ));

        monitor.vincularInventario(inventario);
        monitor.activar();
        Runnable tarea = tareaProgramada(programador);

        tarea.run();
        tarea.run();
        tarea.run();

        assertEquals(1, mensajesDeAlerta().size());
    }

    @Test
    @DisplayName("Notifica una sola recuperación al alcanzar exactamente cinco")
    void notificaRecuperacionAlLlegarAExactamenteCinco() {
        InventarioService inventario = mock(InventarioService.class);
        when(inventario.listarProductos())
                .thenReturn(List.of(new Producto(
                        "P-002", "Mouse inalámbrico Logitech", 3
                )))
                .thenReturn(List.of(new Producto(
                        "P-002", "Mouse inalámbrico Logitech", 5
                )))
                .thenReturn(List.of(new Producto(
                        "P-002", "Mouse inalámbrico Logitech", 5
                )));

        monitor.vincularInventario(inventario);
        monitor.activar();
        Runnable tarea = tareaProgramada(programador);

        tarea.run();
        tarea.run();
        tarea.run();

        assertEquals(1, mensajesDeAlerta().size());
        assertEquals(1, mensajesDeRecuperacion().size());
        assertTrue(mensajesDeRecuperacion().get(0).contains(
                "RECUPERACIÓN: Mouse inalámbrico Logitech (P-002) "
                        + "recuperó su stock normal con 5 unidades disponibles."
        ));
    }

    @Test
    @DisplayName("Notifica una sola recuperación por encima del umbral")
    void notificaRecuperacionAlSuperarElUmbral() {
        InventarioService inventario = mock(InventarioService.class);
        when(inventario.listarProductos())
                .thenReturn(List.of(new Producto(
                        "P-004", "Cable HDMI 2 metros", 1
                )))
                .thenReturn(List.of(new Producto(
                        "P-004", "Cable HDMI 2 metros", 8
                )))
                .thenReturn(List.of(new Producto(
                        "P-004", "Cable HDMI 2 metros", 8
                )));

        monitor.vincularInventario(inventario);
        monitor.activar();
        Runnable tarea = tareaProgramada(programador);

        tarea.run();
        tarea.run();
        tarea.run();

        assertEquals(1, mensajesDeAlerta().size());
        assertEquals(1, mensajesDeRecuperacion().size());
        assertTrue(mensajesDeRecuperacion().get(0).contains(
                "Cable HDMI 2 metros (P-004) recuperó su stock normal "
                        + "con 8 unidades disponibles."
        ));
    }

    @Test
    @DisplayName("Genera una nueva alerta después de recuperación y nuevo descenso")
    void generaNuevaAlertaTrasUnaNuevaCaidaDeStock() {
        InventarioService inventario = mock(InventarioService.class);
        when(inventario.listarProductos())
                .thenReturn(List.of(new Producto(
                        "P-007", "Audífonos HyperX Cloud", 4
                )))
                .thenReturn(List.of(new Producto(
                        "P-007", "Audífonos HyperX Cloud", 6
                )))
                .thenReturn(List.of(new Producto(
                        "P-007", "Audífonos HyperX Cloud", 2
                )));

        monitor.vincularInventario(inventario);
        monitor.activar();
        Runnable tarea = tareaProgramada(programador);

        tarea.run();
        tarea.run();
        tarea.run();

        assertEquals(2, mensajesDeAlerta().size());
        assertEquals(1, mensajesDeRecuperacion().size());
        assertTrue(mensajesDeAlerta().get(0).contains("4 unidades"));
        assertTrue(mensajesDeAlerta().get(1).contains("2 unidades"));
    }

    @Test
    @DisplayName("Mantiene ciclos de alerta independientes para varios productos")
    void administraEstadosIndependientesParaVariosProductos() {
        InventarioService inventario = mock(InventarioService.class);
        when(inventario.listarProductos())
                .thenReturn(List.of(
                        new Producto("P-002", "Mouse", 3),
                        new Producto("P-004", "Cable", 1)
                ))
                .thenReturn(List.of(
                        new Producto("P-002", "Mouse", 5),
                        new Producto("P-004", "Cable", 1)
                ))
                .thenReturn(List.of(
                        new Producto("P-002", "Mouse", 2),
                        new Producto("P-004", "Cable", 5)
                ));

        monitor.vincularInventario(inventario);
        monitor.activar();
        Runnable tarea = tareaProgramada(programador);

        tarea.run();
        tarea.run();
        tarea.run();

        assertEquals(3, mensajesDeAlerta().size());
        assertEquals(Set.of("P-002", "P-004"), mensajesDeAlerta().stream()
                .map(this::codigoDeAlerta)
                .collect(Collectors.toSet()));
        assertEquals(2, mensajesDeRecuperacion().size());
        assertTrue(mensajesDeRecuperacion().stream()
                .anyMatch(mensaje -> mensaje.contains("P-002")));
        assertTrue(mensajesDeRecuperacion().stream()
                .anyMatch(mensaje -> mensaje.contains("P-004")));
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
    @DisplayName("Conserva el estado bajo durante la desconexión y evita duplicados")
    void conservaAlertaActivaDuranteDesconexionYReconectaSinDuplicar() {
        InventarioService inventarioInicial = mock(InventarioService.class);
        InventarioService inventarioReconectado = mock(InventarioService.class);
        List<Producto> stockBajo = List.of(
                new Producto("P-009", "Adaptador USB-C", 2)
        );
        when(inventarioInicial.listarProductos()).thenReturn(stockBajo);
        when(inventarioReconectado.listarProductos()).thenReturn(stockBajo);

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
        assertEquals(1, mensajesDeAlerta().size());
        assertTrue(mensajesDeAlerta().get(0).contains("P-009"));
        assertTrue(mensajesDeRecuperacion().isEmpty());
    }

    @Test
    @DisplayName("Detecta recuperación ocurrida mientras Inventario estuvo desconectado")
    void detectaRecuperacionAlReconectarElServicio() {
        InventarioService inventarioInicial = mock(InventarioService.class);
        InventarioService inventarioReconectado = mock(InventarioService.class);
        when(inventarioInicial.listarProductos()).thenReturn(List.of(
                new Producto("P-009", "Adaptador USB-C", 2)
        ));
        when(inventarioReconectado.listarProductos()).thenReturn(List.of(
                new Producto("P-009", "Adaptador USB-C", 7)
        ));

        monitor.activar();
        Runnable tarea = tareaProgramada(programador);
        monitor.vincularInventario(inventarioInicial);
        tarea.run();

        monitor.desvincularInventario(inventarioInicial);
        tarea.run();
        assertTrue(mensajesDeRecuperacion().isEmpty());

        monitor.vincularInventario(inventarioReconectado);
        tarea.run();

        assertEquals(1, mensajesDeAlerta().size());
        assertEquals(1, mensajesDeRecuperacion().size());
        assertTrue(mensajesDeRecuperacion().get(0).contains(
                "Adaptador USB-C (P-009) recuperó su stock normal "
                        + "con 7 unidades disponibles."
        ));
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
                .thenReturn(List.of(new Producto("P-007", "Audífonos", 4)))
                .thenThrow(new IllegalStateException("Fallo temporal"))
                .thenReturn(List.of(new Producto("P-007", "Audífonos", 4)))
                .thenReturn(List.of(new Producto("P-007", "Audífonos", 5)));

        monitor.activar();
        monitor.vincularInventario(inventario);
        Runnable tarea = tareaProgramada(programador);

        assertDoesNotThrow(tarea::run);
        assertEquals(1, mensajesDeAlerta().size());

        assertDoesNotThrow(tarea::run);
        assertTrue(mensajesDeRecuperacion().isEmpty());

        assertDoesNotThrow(tarea::run);
        assertEquals(1, mensajesDeAlerta().size());
        assertTrue(mensajesDeRecuperacion().isEmpty());

        assertDoesNotThrow(tarea::run);

        assertTrue(appender.list.stream().anyMatch(event ->
                event.getLevel().equals(Level.WARN)
                        && event.getFormattedMessage()
                        .contains("No se pudo consultar")
        ));
        assertTrue(mensajesDeAlerta().stream()
                .anyMatch(mensaje -> mensaje.contains("P-007")));
        assertEquals(1, mensajesDeRecuperacion().size());
        verify(inventario, times(4)).listarProductos();
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
    @DisplayName("Evita alertas duplicadas ante revisiones concurrentes")
    void evitaDuplicadosAnteRevisionesConcurrentes() throws Exception {
        InventarioService inventario = mock(InventarioService.class);
        when(inventario.listarProductos()).thenReturn(List.of(
                new Producto("P-002", "Mouse inalámbrico Logitech", 3)
        ));

        monitor.vincularInventario(inventario);
        monitor.activar();
        Runnable tarea = tareaProgramada(programador);
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        try {
            Future<?> primeraRevision = ejecutor.submit(() -> {
                inicio.await();
                tarea.run();
                return null;
            });
            Future<?> segundaRevision = ejecutor.submit(() -> {
                inicio.await();
                tarea.run();
                return null;
            });

            inicio.countDown();
            primeraRevision.get(5, TimeUnit.SECONDS);
            segundaRevision.get(5, TimeUnit.SECONDS);
        } finally {
            inicio.countDown();
            ejecutor.shutdownNow();
            assertTrue(ejecutor.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertEquals(1, mensajesDeAlerta().size());
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

    private List<String> mensajesDeRecuperacion() {
        return appender.list.stream()
                .filter(event -> event.getLevel().equals(Level.INFO))
                .map(ILoggingEvent::getFormattedMessage)
                .filter(mensaje -> mensaje.startsWith("RECUPERACIÓN:"))
                .toList();
    }

    private String codigoDeAlerta(String mensaje) {
        int inicioCodigo = mensaje.indexOf('(') + 1;
        int finCodigo = mensaje.indexOf(')', inicioCodigo);
        return mensaje.substring(inicioCodigo, finCodigo);
    }
}
