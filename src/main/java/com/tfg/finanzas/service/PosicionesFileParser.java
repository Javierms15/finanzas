package com.tfg.finanzas.service;

import com.tfg.finanzas.dto.InversionDtos.PosicionImportada;

import java.util.List;

/**
 * Parser de un extracto de cuenta de valores (foto de las posiciones actuales
 * de una cartera), distinto de {@link FileParser}: ese parsea movimientos de
 * efectivo de una cuenta bancaria, este parsea un snapshot de posiciones de
 * inversion (cantidad + precio actual, no un historial de compras).
 */
public interface PosicionesFileParser {

    boolean soporta(String nombreArchivo, byte[] contenido);

    List<PosicionImportada> parsear(byte[] contenido);
}
