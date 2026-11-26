package cl.duoc.pc_mania.app;

import cl.duoc.pc_mania.app.controller.CrearOrdenRequest;
import cl.duoc.pc_mania.app.messaging.MessagingEventClient;
import cl.duoc.pc_mania.app.model.Orden;
import cl.duoc.pc_mania.app.model.Producto;
import cl.duoc.pc_mania.app.service.OrdenService;
import cl.duoc.pc_mania.app.service.OrdenRepository;
import cl.duoc.pc_mania.app.service.ProductoService;
import cl.duoc.pc_mania.app.service.StockInsuficienteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

class OrdenServiceTests {

    private ProductoService productoService;
    private MessagingEventClient messagingEventClient;
    private OrdenRepository ordenRepository;
    private OrdenService ordenService;

    @BeforeEach
    void setUp() {
        productoService = new ProductoService();
        productoService.crear(new Producto(
                null, "Producto orden test", "PC Mania", "Test", new BigDecimal("1250"), 3));
        messagingEventClient = mock(MessagingEventClient.class);
        ordenRepository = mock(OrdenRepository.class);
        ordenService = new OrdenService(productoService, messagingEventClient, ordenRepository);
    }

    @Test
    void crearOrdenDescuentaStockYPublicaEvento() {
        Orden orden = ordenService.crear(request(2), "Bearer test-token");

        assertEquals("CREATED", orden.estado());
        assertEquals(new BigDecimal("2500"), orden.total());
        assertEquals(1, productoService.buscarPorId(1L).getStock());
        verify(ordenRepository).guardar(orden);
        verify(messagingEventClient).publishOrderEvent(orden, "Bearer test-token");
    }

    @Test
    void noDescuentaStockSiNoPuedeCompletarOrden() {
        assertThrows(StockInsuficienteException.class,
                () -> ordenService.crear(request(4), "Bearer test-token"));

        assertEquals(3, productoService.buscarPorId(1L).getStock());
        verifyNoInteractions(ordenRepository, messagingEventClient);
    }

    @Test
    void restauraStockSiFallaLaPublicacionDelEvento() {
        doThrow(new IllegalStateException("mensajería no disponible"))
                .when(messagingEventClient)
                .publishOrderEvent(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());

        assertThrows(IllegalStateException.class,
                () -> ordenService.crear(request(2), "Bearer test-token"));

        assertEquals(3, productoService.buscarPorId(1L).getStock());
        org.mockito.Mockito.verify(ordenRepository).eliminar(org.mockito.ArgumentMatchers.anyString());
    }

    private CrearOrdenRequest request(int quantity) {
        return new CrearOrdenRequest(List.of(new CrearOrdenRequest.Linea(1L, quantity)));
    }
}
