package com.tfg.finanzas.scripts;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprobaciones estaticas del lanzador de escritorio. PowerShell 5.1 lee los .ps1 sin BOM en la pagina de
 * codigos del sistema, asi que cualquier caracter no ASCII (tildes, ñ, comillas tipograficas, guiones largos)
 * corrompe el script o provoca errores de sintaxis en otros equipos: por eso deben ser ASCII puro.
 */
class ScriptsLanzadorTest {

    static final Path RAIZ = Path.of("").toAbsolutePath();

    static Stream<Path> scripts() throws IOException {
        try (Stream<Path> ficheros = Files.list(RAIZ.resolve("scripts"))) {
            return Stream.concat(ficheros.filter(f -> f.toString().endsWith(".ps1")), Stream.of(RAIZ.resolve("frontend/iniciar.ps1")))
                    .filter(Files::exists).toList().stream();
        }
    }

    @Test
    void estanTodosLosScriptsDelLanzador() throws IOException {
        List<String> nombres = scripts().map(p -> p.getFileName().toString()).toList();
        assertThat(nombres).contains("_comun.ps1", "iniciar-finanzas.ps1", "detener-finanzas.ps1",
                "crear-acceso-directo.ps1", "crear-icono.ps1");
    }

    @ParameterizedTest(name = "{0} es ASCII puro")
    @MethodSource("scripts")
    void losScriptsSonAsciiPuro(Path script) throws IOException {
        byte[] bytes = Files.readAllBytes(script);
        for (int i = 0; i < bytes.length; i++) {
            assertThat(bytes[i] >= 0)
                    .as("%s: byte no ASCII en la posicion %d (linea %d)", script.getFileName(), i, lineaDe(bytes, i))
                    .isTrue();
        }
    }

    @ParameterizedTest(name = "{0} tiene sintaxis PowerShell valida")
    @MethodSource("scripts")
    void losScriptsSeAnalizanSinErroresDeSintaxis(Path script) throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("win"), "Solo se puede analizar en Windows");

        String orden = "$e=$null;$t=$null;[void][System.Management.Automation.Language.Parser]::ParseFile('"
                + script.toString().replace("'", "''") + "',[ref]$t,[ref]$e);"
                + "if($e.Count -gt 0){$e|ForEach-Object{$_.Extent.StartLineNumber.ToString()+': '+$_.Message};exit 1}";
        Process proceso = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-Command", orden).redirectErrorStream(true).start();
        String salida = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(proceso.waitFor(60, TimeUnit.SECONDS)).as("PowerShell no termino").isTrue();
        assertThat(proceso.exitValue()).as("Errores de sintaxis en %s:%n%s", script.getFileName(), salida).isZero();
    }

    @Test
    void elIniciadorAceptaLosParametrosDocumentados() throws IOException {
        String texto = Files.readString(RAIZ.resolve("scripts/iniciar-finanzas.ps1"), StandardCharsets.US_ASCII);
        assertThat(texto).contains("[switch]$SinNavegador", "[switch]$NoDetener", "[switch]$NoAbrirNavegador");
    }

    @Test
    void elIniciadorYElDetenedorUsanLasMismasFuncionesComunes() throws IOException {
        String comun = Files.readString(RAIZ.resolve("scripts/_comun.ps1"), StandardCharsets.US_ASCII);
        assertThat(comun).contains("function Detener-Servicios", "function Estado-Actividad", "function Resolver-JavaHome");
        for (String script : List.of("iniciar-finanzas.ps1", "detener-finanzas.ps1")) {
            assertThat(Files.readString(RAIZ.resolve("scripts").resolve(script), StandardCharsets.US_ASCII))
                    .as(script).contains("_comun.ps1");
        }
    }

    @Test
    void elIconoEsUnFicheroIcoValido() throws IOException {
        byte[] ico = Files.readAllBytes(RAIZ.resolve("scripts/finanzas.ico"));
        assertThat(ico.length).isGreaterThan(100);
        // cabecera ICONDIR: reservado = 0, tipo = 1 (icono)
        assertThat(ico[0]).isZero();
        assertThat(ico[1]).isZero();
        assertThat(ico[2]).isEqualTo((byte) 1);
        assertThat(ico[3]).isZero();
    }

    private static int lineaDe(byte[] bytes, int posicion) {
        int linea = 1;
        for (int i = 0; i < posicion; i++) {
            if (bytes[i] == '\n') {
                linea++;
            }
        }
        return linea;
    }
}
