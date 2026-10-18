package com.tfg.finanzas.service;

import com.tfg.finanzas.dto.InversionDtos.PosicionImportada;
import com.tfg.finanzas.dto.InversionDtos.ResultadoImportacionPosiciones;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ImportacionPosicionesService {

    private final List<PosicionesFileParser> parsers;
    private final PosicionService posicionService;

    @Transactional
    public ResultadoImportacionPosiciones importar(Long usuarioId, String nombreArchivo, byte[] contenido) {
        PosicionesFileParser parser = parsers.stream()
                .filter(p -> p.soporta(nombreArchivo, contenido))
                .findFirst()
                .orElseThrow(() -> new ImportacionException("Formato de fichero no reconocido: " + nombreArchivo));

        List<PosicionImportada> posiciones = parser.parsear(contenido);
        for (PosicionImportada posicion : posiciones) {
            posicionService.actualizarDesdeSnapshot(usuarioId, posicion);
        }
        return new ResultadoImportacionPosiciones(posiciones.size(), posiciones.size());
    }
}
