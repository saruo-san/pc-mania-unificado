package cl.duoc.pc_mania.app.service;

public class StockInsuficienteException extends RuntimeException {

    public StockInsuficienteException(Long productoId) {
        super("Stock insuficiente para el producto " + productoId);
    }
}
