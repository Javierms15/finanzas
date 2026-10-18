package com.tfg.finanzas.service;

import com.tfg.finanzas.dto.ImportacionDtos.MovimientoImportado;
import com.tfg.finanzas.dto.ImportacionDtos.ResultadoImportacion;
import com.tfg.finanzas.model.Categoria;
import com.tfg.finanzas.model.CuentaBancaria;
import com.tfg.finanzas.model.Movimiento;
import com.tfg.finanzas.model.OrigenMovimiento;
import com.tfg.finanzas.model.TipoCuenta;
import com.tfg.finanzas.repository.CuentaBancariaRepository;
import com.tfg.finanzas.repository.MovimientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ImportacionService {

    private final List<FileParser> parsers;
    private final MovimientoRepository movimientoRepository;
    private final CategorizacionService categorizacionService;
    private final CuentaBancariaRepository cuentaBancariaRepository;

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
        Map<String, CuentaBancaria> destinos = new HashMap<>();
        List<String> cuentasCreadas = new ArrayList<>();
        for (MovimientoImportado mov : extraidos) {
            CuentaBancaria destino = destinoDe(cuenta, mov, destinos, cuentasCreadas);
            String hash = calcularHash(destino.getId(), mov);
            if (movimientoRepository.existsByHashDedup(hash)) {
                duplicados++;
                continue;
            }
            // Mismo banco, mismo movimiento real, pero descrito de forma distinta en
            // dos formatos de extracto distintos (p. ej. ING "Movimientos de la Cuenta"
            // vs "Extracto Integrado"): el hash exacto no lo detecta porque la
            // descripcion no coincide letra a letra, asi que se comprueba tambien por
            // cuenta+fecha+importe antes de darlo por nuevo.
            if (movimientoRepository.existsByCuentaIdAndFechaAndImporte(destino.getId(), mov.fecha(), mov.importe())) {
                duplicadosProbables++;
                continue;
            }
            Categoria categoria = categorizacionService.categorizar(mov.descripcionOriginal(), mov.importe());
            Movimiento movimiento = Movimiento.builder()
                    .cuenta(destino)
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
        return new ResultadoImportacion(extraidos.size(), nuevos, duplicados, duplicadosProbables, cuentasCreadas);
    }

    /** La cuenta a la que va un movimiento: la elegida por el usuario, o la de su subcuenta si el fichero trae varias. */
    private CuentaBancaria destinoDe(CuentaBancaria elegida, MovimientoImportado mov, Map<String, CuentaBancaria> destinos,
                                      List<String> cuentasCreadas) {
        if (mov.subcuenta() == null || mov.subcuenta().isBlank()) {
            return elegida;
        }
        String clave = CategorizacionService.normalizar(mov.subcuenta()).trim();
        CuentaBancaria destino = destinos.get(clave);
        if (destino == null) {
            destino = resolverSubcuenta(elegida, mov.subcuenta().trim(), clave, cuentasCreadas);
            destinos.put(clave, destino);
        }
        return destino;
    }

    /**
     * Busca entre las cuentas del mismo banco la que corresponde a la subcuenta y, si no existe, la crea. Orden:
     * 1) una cuya alias contenga el nombre ("ING Naranja"); 2) la elegida, si es del tipo que corresponde;
     * 3) otra del banco de ese tipo; 4) crear una nueva. La Naranja de ING es una cuenta remunerada (AHORRO) y la
     * Nomina una cuenta corriente normal (NOMINA): por eso no pueden compartir cuenta ni tasa de interes.
     */
    private CuentaBancaria resolverSubcuenta(CuentaBancaria elegida, String nombre, String clave, List<String> cuentasCreadas) {
        TipoCuenta tipo = clave.contains("naranja") || clave.contains("ahorro") ? TipoCuenta.AHORRO : TipoCuenta.NOMINA;
        List<CuentaBancaria> delBanco = cuentaBancariaRepository.findByUsuarioId(elegida.getUsuario().getId()).stream()
                .filter(c -> c.getBanco().equalsIgnoreCase(elegida.getBanco()))
                .toList();
        for (CuentaBancaria c : delBanco) {
            if (CategorizacionService.normalizar(c.getAlias()).contains(clave)) {
                return c;
            }
        }
        if (elegida.getTipo() == tipo) {
            return elegida;
        }
        for (CuentaBancaria c : delBanco) {
            if (c.getTipo() == tipo) {
                return c;
            }
        }
        String etiqueta = nombre.substring(0, 1).toUpperCase(Locale.ROOT) + nombre.substring(1).toLowerCase(Locale.ROOT);
        CuentaBancaria nueva = cuentaBancariaRepository.save(CuentaBancaria.builder()
                .usuario(elegida.getUsuario())
                .banco(elegida.getBanco())
                .tipo(tipo)
                .alias(elegida.getBanco() + " " + etiqueta)
                .moneda(elegida.getMoneda())
                .saldoInicial(BigDecimal.ZERO)
                .build());
        cuentasCreadas.add(nueva.getAlias());
        return nueva;
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
