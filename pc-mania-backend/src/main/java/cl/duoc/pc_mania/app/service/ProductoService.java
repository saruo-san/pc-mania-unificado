package cl.duoc.pc_mania.app.service;

import cl.duoc.pc_mania.app.model.Producto;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class ProductoService {

    private final Map<Long, Producto> inventario = new ConcurrentHashMap<>();
    private final AtomicLong secuenciaId = new AtomicLong(0);

    @PostConstruct
    public void cargarDatosDeEjemplo() {
        crear(new Producto(null, "Procesador Ryzen 5 5600", "AMD", "Procesadores", new BigDecimal("129990"), 12));
        crear(new Producto(null, "Tarjeta grafica RTX 4060", "NVIDIA", "Tarjetas graficas", new BigDecimal("349990"), 6));
        crear(new Producto(null, "Memoria RAM 16GB DDR4", "Kingston", "Memorias RAM", new BigDecimal("45990"), 20));
    }

    public List<Producto> listarTodos() {
        return inventario.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    public Producto buscarPorId(Long id) {
        Producto producto = inventario.get(id);
        if (producto == null) {
            throw new ProductoNoEncontradoException(id);
        }
        return producto;
    }

    public synchronized Producto crear(Producto nuevo) {
        verificarNoDuplicado(nuevo, null);
        long id = secuenciaId.incrementAndGet();
        nuevo.setId(id);
        inventario.put(id, nuevo);
        return nuevo;
    }

    public synchronized Producto actualizar(Long id, Producto datos) {
        Producto existente = buscarPorId(id);
        verificarNoDuplicado(datos, id);
        existente.setNombre(datos.getNombre());
        existente.setMarca(datos.getMarca());
        existente.setCategoria(datos.getCategoria());
        existente.setPrecio(datos.getPrecio());
        existente.setStock(datos.getStock());
        return existente;
    }

    public void eliminar(Long id) {
        if (!inventario.containsKey(id)) {
            throw new ProductoNoEncontradoException(id);
        }
        inventario.remove(id);
    }

    private void verificarNoDuplicado(Producto datos, Long idIgnorado) {
        boolean duplicado = inventario.values().stream()
                .anyMatch(producto -> !producto.getId().equals(idIgnorado)
                        && mismoProducto(producto, datos));
        if (duplicado) {
            throw new ProductoDuplicadoException(datos.getNombre(), datos.getMarca(), datos.getCategoria());
        }
    }

    private boolean mismoProducto(Producto primero, Producto segundo) {
        return normalizar(primero.getNombre()).equals(normalizar(segundo.getNombre()))
                && normalizar(primero.getMarca()).equals(normalizar(segundo.getMarca()))
                && normalizar(primero.getCategoria()).equals(normalizar(segundo.getCategoria()));
    }

    private String normalizar(String valor) {
        return valor.trim().toLowerCase(Locale.ROOT);
    }
}
