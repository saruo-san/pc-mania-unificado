package cl.duoc.pc_mania.app.service;

import cl.duoc.pc_mania.app.controller.CrearOrdenRequest;
import cl.duoc.pc_mania.app.messaging.MessagingEventClient;
import cl.duoc.pc_mania.app.model.Orden;
import cl.duoc.pc_mania.app.model.Producto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrdenService {

    private final ProductoService productoService;
    private final MessagingEventClient messagingEventClient;
    private final OrdenRepository ordenRepository;

    public OrdenService(
            ProductoService productoService,
            MessagingEventClient messagingEventClient,
            OrdenRepository ordenRepository) {
        this.productoService = productoService;
        this.messagingEventClient = messagingEventClient;
        this.ordenRepository = ordenRepository;
    }

    public synchronized Orden crear(CrearOrdenRequest request, String authorizationHeader) {
        Map<Long, Integer> cantidades = request.lineas().stream()
                .collect(Collectors.toMap(
                        CrearOrdenRequest.Linea::productoId,
                        CrearOrdenRequest.Linea::cantidad,
                        Math::addExact,
                        LinkedHashMap::new));
        Map<Long, Producto> productosReservados = productoService.descontarStock(cantidades);

        List<Orden.LineaOrden> lineas = cantidades.entrySet().stream()
                .map(entry -> {
                    Producto producto = productosReservados.get(entry.getKey());
                    BigDecimal cantidad = BigDecimal.valueOf(entry.getValue());
                    BigDecimal subtotal = producto.getPrecio().multiply(cantidad);
                    return new Orden.LineaOrden(
                            producto.getId(),
                            producto.getNombre(),
                            entry.getValue(),
                            producto.getPrecio(),
                            subtotal);
                })
                .toList();
        BigDecimal total = lineas.stream()
                .map(Orden.LineaOrden::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Orden orden = new Orden(
                UUID.randomUUID().toString(),
                "CREATED",
                Instant.now(),
                lineas,
                total);

        boolean guardada = false;
        try {
            ordenRepository.guardar(orden);
            guardada = true;
            messagingEventClient.publishOrderEvent(orden, authorizationHeader);
            return orden;
        } catch (RuntimeException exception) {
            if (guardada) {
                try {
                    ordenRepository.eliminar(orden.id());
                } catch (RuntimeException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            productoService.restaurarStock(cantidades);
            throw exception;
        }
    }
}
