package cl.duoc.pc_mania.app.controller;

import cl.duoc.pc_mania.app.model.Producto;
import cl.duoc.pc_mania.app.messaging.MessagingEventClient;
import cl.duoc.pc_mania.app.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;
    private final MessagingEventClient messagingEventClient;

    public ProductoController(ProductoService productoService, MessagingEventClient messagingEventClient) {
        this.productoService = productoService;
        this.messagingEventClient = messagingEventClient;
    }

    @GetMapping
    public List<Producto> listar() {
        return productoService.listarTodos();
    }

    @GetMapping("/{id}")
    public Producto obtener(@PathVariable Long id) {
        return productoService.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Producto crear(
            @Valid @RequestBody Producto nuevo,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        Producto creado = productoService.crear(nuevo);
        messagingEventClient.publishProductEvent("product.created", creado, authorizationHeader);
        return creado;
    }

    @PutMapping("/{id}")
    public Producto actualizar(
            @PathVariable Long id,
            @Valid @RequestBody Producto datos,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        Producto actualizado = productoService.actualizar(id, datos);
        messagingEventClient.publishProductEvent("product.updated", actualizado, authorizationHeader);
        return actualizado;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        Producto eliminado = productoService.buscarPorId(id);
        productoService.eliminar(id);
        messagingEventClient.publishProductEvent("product.deleted", eliminado, authorizationHeader);
    }
}
