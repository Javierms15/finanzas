# Preparación para la defensa del TFG

Este documento no repite la documentación técnica/funcional tal cual — está pensado para que te lo estudies y puedas responder con seguridad a cualquier pregunta, con el **porqué** de cada cosa, no solo el qué. Incluye ejemplos numéricos reales (de datos tuyos, ya verificados), los problemas de verdad que aparecieron al construirlo, y preguntas típicas de tribunal con respuesta preparada.

---

## 1. El proyecto en una frase

Una aplicación de finanzas personales que **importa** extractos bancarios reales (PDF/CSV), los **categoriza automáticamente**, calcula tu **patrimonio** (efectivo + inversión) y lo **proyecta hacia el futuro** con dos modelos distintos (lineal para efectivo, compuesto para inversión), con una cartera de inversión que se valora con **precio de mercado real** (no inventado), todo protegido por login, y con un frontend en React que lo visualiza con gráficas.

Si te preguntan "¿qué lo diferencia de una hoja de Excel?": la importación automática con deduplicación, la categorización por reglas, y que el valor de la cartera se consulta en vivo contra un proveedor de mercado real — no son números que tú metas a mano.

## 2. Arquitectura general

```
┌─────────────┐      HTTP + JWT       ┌──────────────────┐      JDBC      ┌────────────┐
│  Frontend   │ ───────────────────▶  │     Backend      │ ─────────────▶ │ PostgreSQL │
│  React/Vite │ ◀───────────────────  │  Spring Boot 4.1  │ ◀───────────── │            │
└─────────────┘        JSON           └──────────────────┘                └────────────┘
                                               │
                                               ▼
                                     ┌───────────────────┐
                                     │  Yahoo Finance     │  (precio de mercado en vivo)
                                     └───────────────────┘
```

- **Backend**: arquitectura **en capas** (`model`, `repository`, `service`, `web`, `dto`, más `security` y `config` como paquetes transversales) — el patrón clásico de una API REST en Spring, y el mismo que ya usas en tu otro proyecto (`cofradia-scheduler`). Todas las entidades JPA viven en `model`, todos los repositorios en `repository`, toda la lógica de negocio (categorización, parsers de importación, inversión, patrimonio) en `service`, y todos los controladores REST en `web`. Si te preguntan por qué: es el patrón más fácil de explicar y de localizar un tipo de componente concreto (todas las entidades en un sitio, todos los controladores en otro), a cambio de tener que moverte entre varios paquetes para seguir una funcionalidad completa de principio a fin.
- **Frontend**: SPA (Single Page Application) separada del backend, habla con él solo por HTTP/JSON — se podría sustituir por otra app (móvil, otro framework) sin tocar el backend.
- **Base de datos**: PostgreSQL desde el principio, no H2 ni una base en memoria — para no tener sorpresas de compatibilidad SQL al pasar a producción.

## 3. Stack tecnológico: qué se eligió y por qué (con la alternativa descartada)

| Elección | Alternativa considerada | Por qué esta y no la otra |
|---|---|---|
| Spring Boot 4.1 / Java 21 | Node.js/Express, Django | Backend tipado, ecosistema maduro para JPA/seguridad, coherente con tu otro proyecto |
| PostgreSQL | H2 en memoria | H2 es más rápido para desarrollar pero esconde problemas de compatibilidad SQL que aparecerían en producción; se decidió no posponerlo |
| JPA/Hibernate con `ddl-auto: update` | Flyway/Liquibase (migraciones versionadas) | Para el ritmo de desarrollo de un TFG, cambiar el modelo constantemente sin escribir una migración SQL por cambio fue más ágil. **Contrapartida real**: `update` solo añade columnas/tablas, nunca cambia el tipo de una ya existente (lo sufrimos de verdad, ver sección 7) — en un proyecto de producción real, Flyway sería la elección correcta |
| `BigDecimal` para dinero | `double`/`float` | `double` acumula errores de redondeo binario; con dinero real eso es inaceptable (ej. 0.1 + 0.2 en binario no da exactamente 0.3) |
| PDFBox + OpenCSV | Librería de terceros todo-en-uno, o parseo manual con regex sobre texto plano sin más | PDFBox da acceso a la posición X/Y de cada fragmento de texto, imprescindible para el extracto de Trade Republic (ver sección 6.1) |
| Yahoo Finance (endpoint no oficial) | Alpha Vantage, API de pago | Sin coste, sin necesidad de API key, funciona para probar en real. Contrapartida: no es una API oficialmente soportada, puede cambiar sin aviso — está detrás de una interfaz (`PrecioMercadoProvider`) precisamente para poder cambiar de proveedor sin tocar el resto del código |
| JWT sin estado (stateless) | Sesiones de servidor | Backend sin estado, escala mejor, no depende de mantener sesión en memoria del servidor |
| React + Vite + TypeScript | JavaFX (cliente de escritorio nativo), Vue | Vite da arranque instantáneo; TypeScript da tipado consistente con el backend tipado; React es el ecosistema con más documentación/soporte |
| TanStack Query | `fetch` + `useState`/`useEffect` a mano | El dashboard dispara muchas peticiones en paralelo (movimientos, agregaciones, posiciones, proyecciones a la vez); sin esto habría que escribir caché y estados de carga/error a mano en cada pantalla |
| Tailwind CSS | CSS módulos, styled-components | Montar un dashboard con muchas tarjetas/gráficas rápido sin escribir una hoja de estilos por componente |

## 4. El modelo de datos (10 tablas)

Ya lo tienes en detalle en el mensaje anterior y en `docs/DOCUMENTACION_TECNICA.md` — aquí el resumen de **relaciones** para tenerlo mental:

```
usuario ──┬── cuenta_bancaria ─── movimiento ──── categoria ──── regla_categorizacion
          ├── posicion ──┬── activo
          │              └── movimiento_inversion
          ├── snapshot_patrimonio
          └── movimiento_previsto
```

Todo cuelga de `usuario`. `categoria` es compartida entre `movimiento` y `regla_categorizacion`.

**Pregunta trampa típica**: "¿por qué `movimiento.categoria_id` es `NULL`able pero `movimiento.cuenta_id` no?" — porque un movimiento siempre pertenece a una cuenta (obligatorio), pero puede no coincidir con ninguna regla de categorización todavía (opcional, se puede categorizar después).

## 5. Flujo completo: de un PDF a una gráfica (para explicar de carrerilla)

1. Subes un PDF en la pantalla "Importar archivos" → se envía como `multipart/form-data` a `POST /cuentas/{id}/importar`.
2. `ImportacionService` prueba cada `FileParser` registrado (`IngPdfParser`, `IngExtractoIntegradoPdfParser`, `TradeRepublicPdfParser`, `CsvGenericoParser`) llamando a su método `soporta(...)` hasta que uno dice que sí, según el contenido del fichero (no la extensión).
3. Ese parser extrae el texto del PDF con Apache PDFBox y lo convierte en una lista de `MovimientoImportado` (fecha, importe, descripción) — sin tocar la base de datos todavía, es solo texto → objetos en memoria.
4. Para cada uno, se calcula un hash SHA-256 de `cuentaId|fecha|importe|descripción` y se comprueba si ya existe (`existsByHashDedup`) — si existe, se cuenta como duplicado y se descarta; si no, sigue.
5. `CategorizacionService` mira si alguna `ReglaCategorizacion` existente encaja con la descripción (subcadena, sin acentos ni mayúsculas) y le asigna la categoría si la hay.
6. Se guarda como `Movimiento` en la base de datos.
7. El frontend, al entrar en "Finanzas diarias", pide `GET /cuentas/{id}/movimientos` y `GET /cuentas/{id}/movimientos/agregacion-mensual`, y Recharts dibuja las barras/donut con esos datos.
8. Si entras en "Resumen", se piden además `GET /patrimonio/proyeccion` y `GET /inversion/proyeccion`, se suman los dos valores actuales y los dos proyectados, y se dibuja la línea con tramo histórico (sólido) y proyectado (discontinuo).

## 6. Cada funcionalidad explicada en profundidad

### 6.1. Importación de PDF — el caso interesante: Trade Republic

Este es probablemente el punto técnico más defendible del proyecto. El extracto de Trade Republic tiene columnas separadas de "ENTRADA DE DINERO" y "SALIDA DE DINERO". Si solo lees el texto plano del PDF, **no puedes saber con seguridad si un importe es un ingreso o un gasto** — por ejemplo, un movimiento de tipo "Operar" (comprar o vender una inversión) podría ser cualquiera de los dos según el caso, el texto no lo dice.

**Solución**: en vez de usar `PDFTextStripper` de forma normal (que solo da el texto en orden de lectura), se sobreescribe su método `writeString` para capturar la **coordenada X** de cada fragmento de texto. Como cada columna de la tabla del PDF está en un rango de X fijo (calibrado inspeccionando el PDF real con una herramienta de diagnóstico), se puede saber con certeza en qué columna cae cada número, y por tanto su signo real.

**Bug real encontrado** (buen ejemplo para defensa — demuestra depuración real, no copiar código): al extraer el texto, PDFBox devolvía un **espacio de no separación** (carácter Unicode U+00A0, no un espacio normal) entre el número y el símbolo €. `String.trim()` **no elimina** ese carácter (solo elimina espacios ≤ U+0020), así que `"8,44 €".replace("€","").trim()` dejaba un carácter invisible al final, y `new BigDecimal(...)` lanzaba excepción al parsear. Se solucionó limpiando con una expresión regular (`replaceAll("[^0-9,.-]", "")`) que elimina cualquier carácter que no sea dígito o separador, en vez de depender de `replace`/`trim`.

### 6.2. Categorización

Coincidencia de **subcadena simple**, no expresiones regulares — a propósito, porque el objetivo era "palabra clave → categoría", lo más simple posible. Se normaliza con `java.text.Normalizer` (forma NFD, eliminando marcas diacríticas) para que "cafeteria" encuentre "Cafeterías" sin distinguir acentos. Las reglas tienen prioridad: gana la de menor número de prioridad que encaje primero.

### 6.3. Patrimonio y proyección lineal

**Saldo efectivo** = para cada cuenta del usuario, `saldoInicial + suma(todos sus movimientos.importe)`. Sumado entre todas las cuentas.

**Proyección lineal**: `patrimonio_actual + (ingreso_medio − gasto_medio) × meses`

Los promedios se calculan sobre los **meses ya cerrados** (se excluye el mes en curso porque, al estar incompleto, bajaría la media artificialmente).

**Ejemplo real verificado** (con tus datos de ING): ingreso medio 2.580,53 €, gasto medio 2.553,33 €, patrimonio actual 114,82 € → proyección a 3 meses = 114,82 + (2.580,53 − 2.553,33) × 3 = **196,42 €**. Este número se verificó dos veces: calculado a mano y comparado con la respuesta real de la API — coincide exacto.

**Simulador "qué pasaría si"**: resta un ajuste al gasto medio de una categoría concreta y recalcula. Matemáticamente equivale a restar ese ajuste del gasto medio total (porque el total es la suma de las categorías), pero se devuelve también el detalle por categoría para que la UI sea más informativa. **No tiene límite inferior**: si pides reducir más de lo que esa categoría gasta de media, el resultado se vuelve negativo — es una simplificación del modelo, no un error (si te preguntan, puedes decir que un clamping a cero sería trivial de añadir pero no se pidió explícitamente).

### 6.4. Inversión y proyección compuesta

**Precio de mercado real**: `PrecioMercadoProvider` es una interfaz; `YahooFinancePrecioMercadoProvider` la implementa llamando a `query1.finance.yahoo.com/v8/finance/chart/{ticker}` (endpoint no oficial, sin API key) y leyendo `regularMarketPrice`. Si falla (ticker no reconocido, red caída), se usa el precio medio de compra como respaldo — **nunca se rompe el cálculo de toda la cartera por un solo activo que falle**.

**Limitación real, no resuelta**: los extractos de Trade Republic identifican los activos por **ISIN** (p. ej. `US5949181045`), no por ticker de mercado (`MSFT`). No hay resolución automática ISIN→ticker — si creas un `Activo` con un ISIN como "ticker", cae al precio de compra porque Yahoo no lo reconoce. Se probó en real: con AAPL (ticker válido) el precio se actualizó a 319,97 $ en vivo; con el ISIN de Microsoft, se quedó en el precio de compra (400 €, sin cambios).

**Proyección compuesta**: `valor_actual × (1 + retorno_esperado)^años + aportación_anual × años`. Las aportaciones **no generan su propio interés** en este modelo (se suman en bruto) — simplificación deliberada, igual que en la proyección lineal.

**Ejemplo real verificado**: cartera de 1.039,94 €, retorno esperado 7%, aportación 1.000 €/año, 5 años → 1.039,94 × 1,07^5 + 1.000 × 5 = 1.039,94 × 1,402552 + 5.000 = 1.458,57 + 5.000 = **6.458,57 €**. Verificado también contra la respuesta real de la API.

### 6.5. Seguridad (JWT)

Flujo: `POST /auth/login` con email+contraseña → Spring Security valida contra `UsuarioDetailsService` (que compara el hash BCrypt) → si es correcto, `JwtService` genera un token firmado (HMAC) con el email como "subject" y una fecha de expiración → el frontend lo guarda en `localStorage` → cada petición siguiente lleva `Authorization: Bearer <token>` → `JwtAuthenticationFilter` lo valida en cada petición y, si es válido, rellena el contexto de seguridad de Spring para esa petición (sin guardar sesión en el servidor — stateless).

**Fallo real encontrado y corregido durante la sesión**: varias entidades (`CuentaBancaria`, `Posicion`, `SnapshotPatrimonio`) tienen un campo `Usuario usuario` anidado, y al serializarse a JSON se llevaban por delante el `Usuario` completo — **incluido su `passwordHash`** (cifrado, pero no debería salir nunca en una respuesta HTTP). Se corrigió con una única anotación `@JsonIgnore` en el campo `passwordHash` de la entidad `Usuario`, que lo oculta en todas las serializaciones a la vez sin tener que tocar cada controlador uno por uno.

**Limitación conocida y deliberada**: ningún endpoint comprueba que el `usuarioId` de la URL coincida con el usuario del token — cualquier usuario autenticado podría pedir, en teoría, datos de otro `usuarioId`. Se dejó así a propósito porque el objetivo original era "login simple, un único usuario", no un sistema multiusuario con aislamiento estricto; si te preguntan, explica que el punto de extensión sería un `CurrentUserProvider` que resuelva el usuario desde el token en vez de confiar en el parámetro de la URL.

### 6.6. Frontend

Cada pantalla usa TanStack Query para pedir sus datos (con caché automática), y Recharts para las gráficas. El `api/client.ts` centraliza el añadido del header `Authorization` y, si el backend responde `401`, dispara un callback que cierra sesión y redirige a login — así ninguna pantalla individual tiene que gestionar ese caso a mano.

El **"gasto por categoría"** de Finanzas diarias se calcula **en el frontend**, agrupando los movimientos ya traídos — no hay un endpoint de agregación por categoría en el backend; con el volumen de datos de un uso personal, no hacía falta montar uno nuevo solo para eso.

## 7. Problemas reales encontrados durante el desarrollo (material de oro para la defensa)

Un tribunal valora mucho ver que entiendes los problemas reales que aparecieron, no solo que "todo funcionó a la primera". Estos son los más defendibles, con causa y solución:

1. **`varchar(255)` insuficiente**: al importar un extracto real de ING, una transferencia de Hacienda con una descripción larga superó los 255 caracteres que Hibernate reserva por defecto para un `String`, y la importación fallaba con `value too long for type character varying(255)`. Se cambió `Movimiento.descripcionOriginal` a tipo `text` (sin límite).
2. **`ddl-auto: update` no altera columnas existentes**: el cambio anterior, en la entidad Java, no se reflejó solo al reiniciar la app — hubo que ejecutar un `ALTER TABLE` manual contra Postgres, porque Hibernate en modo `update` solo añade lo que falta, nunca cambia el tipo de algo que ya existe.
3. **Espacio de no separación invisible** (sección 6.1) — el bug de PDFBox con el símbolo €.
4. **Conflicto de puertos entre proyectos**: al levantar Docker para `finanzas`, ya había un Postgres de otro proyecto (`cofradia-scheduler`) ocupando el puerto 5432. Se cambió `finanzas` al puerto 5433 para que ambos convivan.
5. **Jackson 3 en Spring Boot 4.1**: esta versión usa Jackson 3 por defecto, cuyo `ObjectMapper`/`JsonNode` viven en el paquete `tools.jackson.databind`, no en el clásico `com.fasterxml.jackson.databind` (que sigue existiendo solo para las anotaciones). El código que llama a Yahoo Finance y parsea su JSON tuvo que ajustarse a ese paquete nuevo.
6. **`RestClient.Builder` no se autoconfiguraba** como bean de Spring en este entorno — hubo que declararlo explícitamente en una clase de configuración.
7. **Filtración de `passwordHash`** (sección 6.5) — no es un "bug que rompe algo", es un fallo de seguridad real que se encontró y corrigió durante el desarrollo, antes de que nadie lo explotara.
8. **Formatos de banco que parecían iguales y no lo eran**: ING tiene dos exportaciones PDF completamente distintas ("Movimientos de la Cuenta" de una sola cuenta, y "Extracto Integrado" mensual con varias cuentas y fondos en el mismo documento) — hizo falta un segundo parser (`IngExtractoIntegradoPdfParser`), con su propia máquina de estados porque el nombre del valor y algunas líneas de metadatos tienen longitud variable.
9. **Un `soporta()` demasiado permisivo casi causa un fallo silencioso**: `TradeRepublicPdfParser.soporta()` solo comprobaba que el PDF contuviera el texto `"TRADE REPUBLIC"`. Cuando apareció un extracto de la *cuenta de valores* (posiciones de inversión, no movimientos) con el mismo membrete, ese parser lo habría aceptado igualmente y devuelto una lista vacía de movimientos **sin avisar de nada** — el fallo más peligroso de los dos tipos (uno que no dice nada es peor que uno que lanza un error claro). Se corrigió exigiendo además el texto `"TRANSACCIONES DE CUENTA"`, exclusivo del extracto de movimientos, y se verificó con una matriz cruzada de los 4 parsers contra los 4 ficheros reales para confirmar que ninguno se solapa con otro.

## 8. Limitaciones conocidas (y por qué, no por despiste)

| Limitación | Por qué se dejó así |
|---|---|
| No hay aislamiento estricto entre usuarios (ver 6.5) | El objetivo era "un único usuario", no multiusuario con seguridad estricta |
| ISIN no se resuelve a ticker automáticamente | Requeriría integrar un servicio adicional de mapeo ISIN→ticker; fuera del alcance de "conectar un proveedor de precios" |
| Snapshots de patrimonio solo al arrancar la app | Se guardan solos al arrancar (una por día y usuario, en segundo plano); no hay tarea programada con la app abierta, así que un día sin arrancarla no tiene foto |
| CSV genérico sin un banco real de referencia | No se dispuso de un export CSV real de ningún banco durante el desarrollo, solo PDFs — el parser CSV es una base configurable a falta de calibrarlo contra un caso real |
| Sin tests de navegador de extremo a extremo | Hay ≈ 280 tests automáticos (backend con PostgreSQL real, parsers con PDFs sintéticos, frontend), pero la interfaz real se sigue comprobando a mano; Playwright sería la siguiente mejora natural |

## 9. Preguntas típicas de tribunal (con respuesta preparada)

**"¿Por qué paquetes por capa (`model`/`repository`/`service`/`web`) y no por feature?"**
Es el patrón clásico de una API REST en Spring: localizas de inmediato un tipo de componente (todas las entidades en `model`, todos los controladores en `web`...), y es coherente con la organización que ya usas en tu otro proyecto. El coste es que seguir una funcionalidad completa de principio a fin (p. ej. "inversión") obliga a moverse por varios paquetes en vez de tenerlo todo junto en uno solo — una alternativa habitual es organizar por feature (todo lo de "inversión" en un único paquete), que facilita ese segundo caso a cambio de dificultar el primero. Los DTOs se agrupan por feature dentro de `dto` (p. ej. `InversionDtos` con varios records anidados) precisamente para mitigar ese problema en la capa donde más se nota.

**"¿Cómo garantizas que no se importen movimientos duplicados?"**
Dos capas. La principal: hash SHA-256 de cuenta+fecha+importe+descripción, con restricción `UNIQUE` en base de datos — antes de guardar cada movimiento se comprueba si ese hash ya existe. La segunda, añadida tras un caso real: el mismo banco (ING) describe la misma operación de forma distinta en dos formatos de extracto distintos ("Movimientos de la Cuenta" vs "Extracto Integrado"), así que el hash exacto no basta — se comprueba también por cuenta+fecha+importe sin la descripción, y ese segundo tipo de coincidencia se cuenta aparte (`duplicadosProbables`) en vez de mezclarse con la primera, para que la respuesta de la API siga siendo transparente sobre cómo se detectó cada caso.

**"¿Qué pasa si Yahoo Finance cambia su API o deja de funcionar?"**
`PrecioMercadoProvider` es una interfaz — el resto de la aplicación no sabe que detrás hay Yahoo Finance. Cambiar de proveedor (o añadir un segundo como respaldo) no afecta a `InversionService` ni a nada que lo use. Y aunque falle, nunca lanza excepción: devuelve `Optional.empty()` y el sistema usa el precio de compra como respaldo.

**"¿Por qué BigDecimal y no double para el dinero?"**
`double` representa decimales en binario con errores de redondeo acumulativos (0.1 + 0.2 ≠ 0.3 exacto en binario). Con dinero real eso es inaceptable a cualquier escala.

**"¿Cómo sabes que el parser de Trade Republic funciona de verdad y no solo 'compila'?"**
Se probó contra el PDF real del usuario, extrayendo el texto con una herramienta de diagnóstico antes de escribir el regex, y comparando cada movimiento extraído (fecha, importe, signo) contra el PDF original uno por uno — 19 movimientos, todos correctos, incluyendo el caso con bug del espacio invisible que se encontró así.

**"¿Cómo sabes que no rompes algo al cambiar el código?"**
Hay una suite de ≈ 280 tests automáticos (`scripts\ejecutar-tests.ps1`): unitarios de la lógica (categorización, JWT, proyección, parsers), integración de toda la API con `MockMvc` contra un PostgreSQL real levantado por Testcontainers (el mismo motor que producción, porque varios fallos reales del proyecto eran propios de Postgres), y tests de frontend con Vitest. Los PDFs de los parsers son sintéticos, generados al vuelo, para no guardar datos bancarios en el repositorio. Además comprobé que los tests detectan fallos de verdad rompiendo a propósito el código (la media ponderada del precio, la compatibilidad de signos al categorizar) y viendo que fallaban. Y ya encontraron un fallo real: el campo de meses del Resumen perdía el foco en cada tecla.

**"¿Por qué JWT y no sesiones?"**
Backend sin estado: no hay que guardar sesión en memoria del servidor ni preocuparse de sincronizarla si hubiera varias instancias del backend corriendo. El coste es que revocar un token antes de que expire no es trivial (no implementado aquí, válido hasta que caduca).

**"¿Qué harías diferente si tuvieras más tiempo?"** (pregunta casi garantizada)
Tests de navegador de extremo a extremo (Playwright), resolución ISIN→ticker, aislamiento de datos entre usuarios, snapshots de patrimonio generados también por una tarea programada diaria (hoy solo se guardan al arrancar).

## 10. Cómo repasar esto antes de la defensa

1. Lee este documento de principio a fin una vez.
2. Abre la aplicación real (`npm run dev` + backend) y repite tú mismo el flujo: registro → crear cuenta → importar un PDF real → ver cómo aparece categorizado → ver el resumen → simular un ajuste. Que la secuencia te salga de memoria.
3. Abre el código de `TradeRepublicPdfParser.java` y `PatrimonioProyeccionService.java` — son los dos sitios con más "chicha" técnica, léelos despacio una vez más.
4. Repasa la sección 9 en voz alta como si te la preguntaran.
