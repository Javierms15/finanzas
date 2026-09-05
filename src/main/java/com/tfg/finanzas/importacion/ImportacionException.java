package com.tfg.finanzas.importacion;

public class ImportacionException extends RuntimeException {

    public ImportacionException(String message) {
        super(message);
    }

    public ImportacionException(String message, Throwable cause) {
        super(message, cause);
    }
}
