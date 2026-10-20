package cl.duoc.pc_mania.app.service;

public class ProductoDuplicadoException extends RuntimeException {

    public ProductoDuplicadoException(String nombre, String marca, String categoria) {
        super(String.format("Ya existe un producto con nombre '%s', marca '%s' y categoria '%s'", nombre, marca, categoria));
    }
}