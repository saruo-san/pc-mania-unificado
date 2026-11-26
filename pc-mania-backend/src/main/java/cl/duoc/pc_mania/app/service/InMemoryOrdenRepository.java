package cl.duoc.pc_mania.app.service;

import cl.duoc.pc_mania.app.model.Orden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@ConditionalOnProperty(name = "pc-mania.orders.persistence", havingValue = "memory", matchIfMissing = true)
public class InMemoryOrdenRepository implements OrdenRepository {

    private final ConcurrentMap<String, Orden> ordenes = new ConcurrentHashMap<>();

    @Override
    public void guardar(Orden orden) {
        ordenes.put(orden.id(), orden);
    }

    @Override
    public void eliminar(String orderId) {
        ordenes.remove(orderId);
    }
}
