package com.tfg.finanzas.service;

import com.tfg.finanzas.dto.ImportacionDtos.MovimientoImportado;
import com.tfg.finanzas.dto.ImportacionDtos.ResultadoImportacion;
import com.tfg.finanzas.model.Categoria;
import com.tfg.finanzas.model.CuentaBancaria;
import com.tfg.finanzas.model.Movimiento;
import com.tfg.finanzas.repository.MovimientoRepository;
import com.tfg.finanzas.model.OrigenMovimiento;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImportacionService {

    private final List<FileParser> parsers;
    private final MovimientoRepository movimientoRepository;
    private final CategorizacionService categorizacionService;

    @Transactional
    public ResultadoImportacion importar(CuentaBancaria cuenta, String nombreArchivo, byte[] contenido) {
        FileParser parser = parsers.stream()
                .filter(p -> p.soporta(nombreArchivo, contenido))
                .findFirst()
                .orElseThrow(() -> new ImportacionException("Formato de fichero no reconocido: " + nombreArchivo));

        OrigenMovimiento origen = parser instanceof CsvGenericoParser ? OrigenMovimiento.CSV : OrigenMovimiento.PDF;
        List<MovimientoImportado> extraidos = parser.parsear(contenido);

        int nuevos = 0;
        int duplicados = 0;
        int duplicadosProbables = 0;
        for (MovimientoImportado mov : extraidos) {
            String hash = calcularHash(cuenta.getId(), mov);
            if (movimientoRepository.existsByHashDedup(hash)) {
                duplicados++;
                continue;
            }
            // Mismo banco, mismo movimiento real, pero descrito de forma distinta en
            // dos formatos de extracto distintos (p. ej. ING "Movimientos de la Cuenta"
            // vs "Extracto Integrado"): el hash exacto no lo detecta porque la
            // descripcion no coincide letra a letra, asi que se comprueba tambien por
            // cuenta+fecha+importe antes de darlo por nuevo.
            if (movimientoRepository.existsByCuentaIdAndFechaAndImporte(cuenta.getId(), mov.fecha(), mov.importe())) {
                duplicadosProbables++;
                continue;
            }
            Categoria categoria = categorizacionService.categorizar(mov.descripcionOriginal(), mov.importe());
            Movimiento movimiento = Movimiento.builder()
                    .cuenta(cuenta)
                    .fecha(mov.fecha())
                    .importe(mov.importe())
                    .descripcionOriginal(mov.descripcionOriginal())
                    .categoria(categoria)
                    .origen(origen)
                    .hashDedup(hash)
                    .build();
            movimientoRepository.save(movimiento);
            nuevos++;
        }
        return new ResultadoImportacion(extraidos.size(), nuevos, duplicados, duplicadosProbables);
    }

    private String calcularHash(Long cuentaId, MovimientoImportado mov) {
        String base = cuentaId + "|" + mov.fecha() + "|" + mov.importe().toPlainString() + "|" + mov.descripcionOriginal().trim();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(base.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
