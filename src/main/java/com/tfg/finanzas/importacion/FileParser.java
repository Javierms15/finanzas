package com.tfg.finanzas.importacion;

import java.util.List;

/**
 * Parser de un formato de fichero concreto (un banco/exportacion determinados).
 * La implementacion decide si sabe leer el fichero via {@link #soporta}, y el
 * servicio de importacion prueba las implementaciones en orden hasta encontrar
 * una que lo soporte.
 */
public interface FileParser {

    boolean soporta(String nombreArchivo, byte[] contenido);

    List<MovimientoImportado> parsear(byte[] contenido);
}
