package cl.duoc.pc_mania.app.service;

import cl.duoc.pc_mania.app.model.Orden;

public interface OrdenRepository {

    void guardar(Orden orden);

    void eliminar(String orderId);
}
