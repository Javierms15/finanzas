# Documentación técnica

## 1. Visión general

Aplicación de finanzas personales con dos partes:

- **Backend** (`/`): API REST en Spring Boot que modela usuarios, cuentas bancarias, movimientos, categorías, cartera de inversión y patrimonio, e importa extractos bancarios (CSV/PDF) parseándolos hasta convertirlos en movimientos.
- **Frontend** (`/frontend`): SPA en React que consume esa API y presenta la información con tablas y gráficas.

El backend está organizado **por capa técnica** (arquitectura en capas clásica), igual que el otro proyecto del autor (`cofradia-scheduler`):

```
com.tfg.finanzas
├── config         configuración de Spring (seguridad, RestClient)
├── security       JWT: generación/validación de tokens, filtro, UserDetailsService, AuthController
├── model          todas las entidades JPA y sus enums (17 clases)
├── repository     todas las interfaces JpaRepository (11)
├── service        toda la lógica de negocio: categorización, importación (parsers incluidos),
│                   inversión, patrimonio y proyecciones (15 clases)
├── web            todos los @RestController (10)
└── dto            DTOs de petición/respuesta, agrupados por feature en clases contenedoras
                    (p. ej. `InversionDtos` con los records `CrearActivoRequest`, `PosicionValorada`...)
                    con records anidados, para no tener un fichero por cada record suelto
```

Es el patrón "de libro" para una API REST con Spring: fácil de explicar y de localizar un tipo de componente (todas las entidades en un sitio, todos los controladores en otro). El coste es el contrario de organizarlo por feature: para entender o modificar una funcionalidad completa (p. ej. "inversión") hay que moverse entre `model`, `repository`, `service`, `web` y `dto` en vez de tener todo en un único paquete.

## 2. Decisiones de stack y por qué

| Decisión | Motivo |
|---|---|
| Spring Boot 4.1.0 / Java 21 | Versión moderna, coherente con el resto de proyectos del autor (mismo patrón que `cofradia-scheduler`). |
| PostgreSQL en vez de H2 | Se decidió trabajar desde el principio contra el motor real de producción, evitando sorpresas de compatibilidad SQL más adelante. |
| `BigDecimal` para todo importe monetario | `Double` introduce errores de redondeo inaceptables en dinero. |
| Apache PDFBox + posición X/Y de cada fragmento de texto (no solo el texto plano) | El extracto de Trade Republic tiene columnas separadas de "entrada" y "salida" de dinero; el texto plano por sí solo no permite saber con fiabilidad si un importe es un ingreso o un gasto (el tipo de movimiento, p. ej. "Operar", no determina el signo). Ver `TradeRepublicPdfParser`. |
| Yahoo Finance (endpoint no oficial `query1.finance.yahoo.com/v8/finance/chart/{ticker}`) | Gratuito, sin API key, y probado en real durante el desarrollo. Es un endpoint no documentado oficialmente: puede cambiar de forma sin aviso. |
| JWT (jjwt) en vez de sesiones | Backend stateless, mismo patrón ya validado en `cofradia-scheduler`. |
| React + Vite + TypeScript | Estándar actual, tipado consistente con un backend tipado. |
| Recharts | Gráficas nativas de React sin curva de aprendizaje alta, suficientes para line/bar/area/pie. |
| TanStack Query | El dashboard dispara muchas peticiones en paralelo (movimientos, agregaciones, posiciones, proyecciones); da caché y estados de carga/error sin escribirlo a mano en cada pantalla. |
| Tailwind CSS | Maquetar un dashboard con muchas tarjetas/gráficas rápido sin escribir CSS por componente. |

### Detalle importante de Spring Boot 4.1.0: Jackson 3

Esta versión de Spring Boot usa **Jackson 3** por defecto, cuyas clases de serialización viven en el paquete `tools.jackson.databind.*` (no `com.fasterxml.jackson.databind.*`, que sigue siendo Jackson 2). Las **anotaciones** (`@JsonIgnore`, etc.) sí se mantienen en `com.fasterxml.jackson.annotation`. Si en el futuro se necesita manipular JSON directamente con `ObjectMapper`/`JsonNode` (como hace `YahooFinancePrecioMercadoProvider`), hay que importar del paquete `tools.jackson.databind`, no del clásico `com.fasterxml.jackson.databind`, o el código no compila.

También se detectó que `RestClient.Builder` no se autoconfigura como bean en este entorno (a diferencia de lo esperado con `spring-boot-starter-web`); está declarado explícitamente en `config/RestClientConfig.java`.

## 3. Modelo de datos

```
Usuario 1───N CuentaBancaria 1───N Movimiento N───1 Categoria
Usuario 1───N Posicion N───1 Activo
Posicion 1───N MovimientoInversion
Usuario 1───N SnapshotPatrimonio
Categoria 1───N ReglaCategorizacion
CuentaBancaria 1───N TarjetaBeneficio 1───N CashbackAcumulado   (modelado, sin servicio/API todavía)
```

Puntos no obvios:

- **`CuentaBancaria.saldoInicial`**: un extracto bancario no siempre cubre toda la vida de la cuenta, así que el saldo real de una cuenta no se puede derivar solo de los movimientos importados. El saldo actual se calcula como `saldoInicial + suma(movimientos.importe)`. Se calibra con `PATCH /usuarios/{usuarioId}/cuentas/{cuentaId}/saldo-inicial` (ver sección 9), usando como referencia el propio saldo inicial/final que el banco imprime en el extracto — pero eso solo da exactitud **a fecha del extracto**, no a fecha de hoy, si han pasado movimientos sin importar desde entonces.
- **`Movimiento.hashDedup`**: hash SHA-256 de `cuentaId|fecha|importe|descripcionOriginal`, columna `unique`. Es la clave de deduplicación exacta al reimportar un fichero que se solape con datos ya importados. Como complemento, `ImportacionService` también comprueba `cuentaId+fecha+importe` sin descripción (`MovimientoRepository.existsByCuentaIdAndFechaAndImporte`) para detectar el mismo movimiento real descrito de forma distinta en dos formatos de extracto distintos del mismo banco (caso real encontrado: una transferencia de BlaBlaCar aparecía con descripción distinta en el extracto "Movimientos de la Cuenta" y en el "Extracto Integrado" de ING). Este segundo tipo de duplicado se cuenta aparte (`duplicadosProbables`) en vez de mezclarse con `duplicados`, para que quede constancia de que no fue una coincidencia exacta.
- **`Movimiento.descripcionOriginal`** es `columnDefinition = "text"` (sin límite), no `varchar(255)` (el valor por defecto de Hibernate para un `String`): algunas referencias bancarias reales (transferencias de Hacienda, conceptos de Bizum) superan los 255 caracteres. Esto se descubrió importando un extracto real, no en una revisión de código.
- **`Usuario.passwordHash`** lleva `@JsonIgnore`: varias entidades (`CuentaBancaria`, `Posicion`, `SnapshotPatrimonio`) anidan `Usuario` completo al serializarse a JSON: sin el `@JsonIgnore`, el hash de la contraseña se filtraba en la respuesta de varios endpoints que nada tienen que ver con autenticación.
- **`Posicion.precioMedioCompra`** es un coste medio ponderado que se recalcula en cada compra (`PosicionService.aplicarCompra`); en una venta baja la cantidad pero el precio medio no cambia.

### Aviso sobre `ddl-auto: update`

Hibernate en modo `update` **añade** columnas y tablas que faltan, pero **no altera el tipo** de una columna ya existente (se comprobó en real: cambiar `descripcionOriginal` de `varchar(255)` a `text` en la entidad no tuvo efecto hasta ejecutar un `ALTER TABLE` a mano contra Postgres). Si se cambia el tipo de un campo en una entidad ya desplegada, hay que migrar la columna manualmente (o borrar y recrear la tabla si no hay datos que perder).

## 4. Importación de ficheros (`importacion`)

Interfaz `FileParser`:

```java
boolean soporta(String nombreArchivo, byte[] contenido);
List<MovimientoImportado> parsear(byte[] contenido);
```

`ImportacionService` prueba los `FileParser` registrados en orden hasta que uno responde `true` a `soporta(...)`, parsea, calcula el hash de deduplicación por movimiento, descarta los que ya existen (`MovimientoRepository.existsByHashDedup`) y categoriza automáticamente los nuevos (`CategorizacionService`) antes de guardarlos.

Parsers ya implementados:

- **`IngPdfParser`**: formato "Movimientos de la Cuenta" (una sola cuenta). El texto se extrae en orden de lectura normal (`PDFTextStripper` por defecto). Cada movimiento empieza en una línea que matchea `dd/MM/yyyy`; las líneas siguientes que no empiezan por fecha se consideran continuación del mismo movimiento (necesario porque algunas descripciones —p. ej. una transferencia de Hacienda— ocupan varias líneas). El importe y el saldo se extraen como los dos últimos números con formato `-?\d{1,3}(,\d{3})*\.\d{2}` de la línea combinada (formato con coma de millares y punto decimal).
- **`IngExtractoIntegradoPdfParser`**: formato "Extracto Integrado" — un PDF mensual **distinto** al anterior, que junta varias cuentas (Nómina, Naranja...) y los fondos de inversión en un único documento, cada uno con su propia tabla. Solo se extraen los movimientos de la sección "Cuentas" (los fondos de inversión se ignoran, no son movimientos de efectivo); como todo el fichero se importa contra una única `CuentaBancaria` elegida por el usuario, cada descripción se prefija con el nombre de la cuenta de origen (`[NARANJA] ...`) para no perder esa información. Las fechas vienen sin año (`dd/MM`) y se combinan con el mes/año del encabezado del extracto.
- **`TradeRepublicPdfParser`**: extrae cada fragmento de texto con su coordenada X (subclase de `PDFTextStripper` que sobreescribe `writeString`), y clasifica cada fragmento por la columna en la que cae (fecha/tipo/descripción/entrada/salida/balance) usando rangos de X fijos, calibrados contra un extracto real. Es la única forma fiable de saber si un importe es un ingreso o un gasto en este formato, porque la columna de "entrada de dinero" y la de "salida de dinero" están separadas — el tipo de movimiento (p. ej. "Operar") no basta por sí solo (un "Operar" podría ser una compra o una venta). Los meses en español se resuelven con un mapa fijo (`ene`...`dic`, con caso especial `sept`/`sep`). `soporta(...)` exige además el texto `"TRANSACCIONES DE CUENTA"`, no solo `"TRADE REPUBLIC"`: el extracto de la cuenta de valores (posiciones de inversión, ver más abajo) lleva el mismo membrete pero no es un extracto de movimientos — con el check original, el parser equivocado lo habría aceptado en silencio y devuelto una lista vacía en vez de un error claro.
- **`CsvGenericoParser`**: sin ningún banco real de referencia todavía. Detecta separador (`,` vs `;`), busca columnas de fecha/descripción/importe por nombre de cabecera habitual, y detecta el formato de importe (coma o punto como separador decimal) mirando cuál de los dos aparece en último lugar en el texto. Es un punto de partida a ajustar en cuanto haya un CSV real de un banco.

### Importación de posiciones de inversión (`PosicionesFileParser`)

Interfaz paralela a `FileParser`, pero para un tipo de documento distinto: una **foto de las posiciones actuales** de una cartera (cantidad + precio del día), no un historial de movimientos de efectivo. `ImportacionPosicionesService` sigue el mismo patrón de selección de parser por `soporta(...)`, pero en vez de deduplicar por hash, cada posición encontrada **sobrescribe** la `Posicion` existente para ese activo (`PosicionService.actualizarDesdeSnapshot`): no tiene sentido acumular una "foto" como si fuera una compra incremental.

- **`TradeRepublicPosicionesPdfParser`**: formato "Extracto de la cuenta de valores". Cada posición ocupa un bloque de líneas de longitud variable (el nombre del valor puede ir en 1 o 2 líneas, y solo algunas posiciones llevan la línea "Cuenta de valores en Alemania"), así que se parsea como una máquina de estados por marcadores (`N tít.` → inicio, `ISIN:` → fin del nombre, primer número suelto tras el ISIN → precio) en vez de por offsets fijos de línea. El activo se identifica por ISIN (igual limitación que en la sección 7 de inversión: sin resolución ISIN→ticker, esos valores no se actualizan en vivo contra Yahoo Finance; el precio que se guarda es el del momento de la importación).
- **`IngFondosPdfParser`**: lee la sección "Fondos de Inversión" del mismo PDF "Extracto Integrado" que `IngExtractoIntegradoPdfParser` usa para las cuentas corrientes/ahorro (`IngExtractoIntegradoPdfParser` ignora explícitamente esta sección porque no son movimientos de efectivo). Cada fondo trae dos filas de valoración (inicio y fin del periodo); se usa la última como posición actual. Los fondos no tienen ISIN en el extracto, así que se identifican por el código de producto de ING (p. ej. `ING-4006853921.1`).
- Endpoint: `POST /usuarios/{usuarioId}/inversion/posiciones/importar` (multipart `file`).

### Cómo añadir un parser nuevo

1. Implementar `FileParser` (paquete `service`, junto a `ImportacionService` y los parsers existentes), anotado `@Component` para que Spring lo registre automáticamente (se inyecta como `List<FileParser>` en `ImportacionService`).
2. `soporta(...)` debe basarse en contenido del fichero (texto característico), no solo en la extensión, porque puede haber varios parsers de PDF.
3. Antes de escribir el regex/parsing, extraer el texto real con PDFBox (`PDFTextStripper`, con y sin `setSortByPosition(true)`) para ver cómo llega de verdad — el orden de lectura de un PDF no es necesariamente el orden visual, y el texto puede traer espacios de no separación (` `) entre número y símbolo de moneda que `String.trim()` no elimina (bug real encontrado al probar contra el extracto de Trade Republic; la solución fue limpiar con `replaceAll("[^0-9,.-]", "")` en vez de `replace`/`trim`).

## 5. Categorización (`categoria`)

`CategorizacionService.categorizar(descripcion, importe)` recorre las `ReglaCategorizacion` por `prioridad` ascendente y devuelve la categoría de la primera regla cuyo `patron` aparece como subcadena de la descripción, **sin distinguir mayúsculas ni acentos** (normalización vía `java.text.Normalizer`, forma NFD, eliminando marcas diacríticas). Es una coincidencia de subcadena simple a propósito, no un motor de expresiones regulares — así lo pedía el enunciado original ("palabra clave → categoría").

- **Compatibilidad con el signo**: una regla solo es aplicable si el tipo de su categoría encaja con el signo del importe (`GASTO` no se asigna a un importe positivo —un reembolso—, `INGRESO` no a uno negativo; `NEUTRO` vale para ambos). Antes el motor ignoraba el signo.
- **`TipoCategoria.NEUTRO`**: categorías que mueven dinero sin ser ingreso ni gasto real (transferencias entre cuentas propias, aportaciones a inversión). `AgregacionMensualService` **excluye** sus movimientos de ingresos/gastos —y por tanto de las medias que usa la proyección y del "gasto por categoría"—, pero **siguen contando en el saldo** (`PatrimonioService.calcularSaldoCuenta` suma todos los movimientos). Sin esto, las transferencias a uno mismo (7.575 € en los datos reales) contaban como gasto y dominaban todas las estadísticas.
- **`Movimiento.categoriaManual`**: se pone a `true` cuando el usuario elige la categoría (`PATCH /cuentas/{cuentaId}/movimientos/{id}/categoria`); `recategorizarAutomaticas()` nunca la sobrescribe. `categoriaId = null` en ese endpoint quita la elección manual y vuelve a la que den las reglas.
- `recategorizarPendientes()` solo asigna categoría a los movimientos que no tienen ninguna; `recategorizarAutomaticas()` (`POST /categorias/recategorizar?todas=true`) recalcula también los automáticos, para corregir los que una regla antigua dejó mal.
- **Categorías sugeridas** (`CategoriasSugeridasService`, `POST /categorias/sugeridas?usuarioId=`): carga 16 categorías y ~78 reglas pensadas para los extractos de ING y Trade Republic, de forma **idempotente** (no duplica). El orden de la lista es la prioridad (desde 100): lo específico antes que lo genérico —p. ej. "Suscripciones" antes que "Compras", porque ING etiqueta Google One y Apple como "Compras Electrónica"—. Las transferencias a uno mismo se detectan con el nombre del usuario (`emitida a <nombre>`), así que dependen de que el nombre registrado coincida con el del extracto. Se eligieron patrones largos o específicos a propósito: `zara` casaría con "ZARAGOZA" y `aldi` con cualquier apellido.
- **Limitación conocida**: el extracto integrado de ING no trae el beneficiario de las transferencias (`[NÓMINA] Transferencia emitida`), así que esas no se pueden clasificar automáticamente como propias o a terceros; quedan sin categoría hasta que el usuario las asigne a mano.
- **Migración manual necesaria al añadir `NEUTRO`**: Hibernate (`ddl-auto: update`) creó `categoria_tipo_check` solo con `GASTO`/`INGRESO` y no lo actualiza; hubo que recrearla a mano (`ALTER TABLE categoria DROP CONSTRAINT categoria_tipo_check; ALTER TABLE categoria ADD CONSTRAINT categoria_tipo_check CHECK (tipo IN ('GASTO','INGRESO','NEUTRO'))`). La columna nueva `categoria_manual` sí se añadió sola porque lleva `columnDefinition = "boolean not null default false"` (sin default, Hibernate falla al añadir una columna `NOT NULL` a una tabla con filas).

## 6. Patrimonio y proyecciones (`patrimonio`)

- **Saldo efectivo total** (`PatrimonioService.calcularSaldoEfectivoTotal`): suma, para cada cuenta del usuario, `saldoInicial + Σ movimientos.importe`.
- **Valor de cartera de inversión**: delega en `InversionService.valorTotalCartera` (ver sección 7), es decir, usa precio de mercado real cuando está disponible.
- **`SnapshotPatrimonio`**: fotografía puntual (`saldoEfectivoTotal`, `valorCarteraInversion`, `patrimonioTotal`) que se genera bajo demanda (`POST /usuarios/{id}/patrimonio/snapshots`), no automáticamente.
- **Proyección lineal** (`PatrimonioProyeccionService`): `patrimonio_actual + (ingreso_medio − gasto_medio) × meses`. Los promedios se calculan sobre los **meses ya cerrados** (se excluye el mes en curso, porque al estar incompleto sesgaría la media a la baja) usando `AgregacionMensualService`. `gastoMedio` se expresa siempre en positivo (magnitud), no con el signo negativo interno de los movimientos.
- **Simulación "qué pasaría si"** (`simular(usuarioId, meses, categoriaId, ajusteMensual)`): resta `ajusteMensual` al gasto medio de esa categoría concreta y recalcula. Matemáticamente equivale a restar `ajusteMensual` del gasto medio total (porque el total es la suma de todas las categorías), pero se calcula también el detalle por categoría para que la respuesta sea útil en la UI. **No hay clamping**: si `ajusteMensual` es mayor que el gasto medio real de la categoría, el resultado simulado se vuelve negativo — es una simplificación deliberada del modelo, no un bug.
- **`CuentaBancaria.tasaInteresAnual`** (nullable, `@Column(precision = 9, scale = 6)`): TAE de una cuenta remunerada, calibrable con `PATCH /usuarios/{usuarioId}/cuentas/{cuentaId}/tasa-interes`. Si una cuenta de la aplicación junta en realidad varios subproductos con interés distinto (p. ej. `IngExtractoIntegradoPdfParser` importa NÓMINA y NARANJA contra una única `CuentaBancaria`), el interés configurado se aplica a todo el saldo de esa cuenta — aproximación documentada, no corregida (requeriría separar esa cuenta en dos).
  - **Bug real encontrado al probar con datos reales**: sin `precision`/`scale` explícitos, Hibernate usa `scale=2` por defecto para `BigDecimal` (el que vale para importes en euros) — un interés como `0,0089` (0,89%) se redondeaba en silencio a `0,01` (1%) al guardar, sin error visible. Se detectó comparando la respuesta del `PATCH` (que sí devolvía `0.0089`, el valor en memoria antes de volver a leer de BD) contra una consulta SQL directa después de guardar. Al ser `ddl-auto: update`, cambiar la anotación no bastó — hubo que migrar la columna a mano (`ALTER TABLE cuenta_bancaria ALTER COLUMN tasa_interes_anual TYPE numeric(9,6)`), mismo caso que el de `descripcionOriginal` ya documentado al principio de esta sección.
- **Desglose del crecimiento proyectado (Resumen, frontend)**: a diferencia del resto de proyecciones, esta composición **no vive en un servicio de backend nuevo** — se construye en `ResumenPage.tsx` a partir de datos que ya exponen los endpoints existentes (`/patrimonio/proyeccion` para `ingresoMedio`/`gastoMedio`, `GET /usuarios/{id}/cuentas/saldos` para el saldo actual y la tasa de interés por cuenta, y `GET /usuarios/{id}/inversion/posiciones` — que ahora incluye `tipo` — agrupado por `TipoActivo`). Se decidió así para no duplicar lógica de negocio en un segundo servicio de proyección: el propio frontend ya construye series de este tipo (ver la proyección compuesta de `InversionPage.tsx`). Tres componentes, cada uno compuesto con la tasa mensual equivalente `(1+tasaAnual)^(1/12) − 1` (no `tasaAnual/12`, para que el interés compuesto sea correcto con pasos mensuales):
  - **Ahorro**: `(ingresoMedio − gastoMedio) × meses`, lineal.
  - **Interés**: por cada cuenta con `tasaInteresAnual` positivo, `saldoActual × ((1+tasaMensual)^meses − 1)`.
  - **Inversión**: la cartera agrupada por `TipoActivo`, cada grupo con su propia rentabilidad anual esperada (editable en el panel "Ajustar supuestos", por defecto 8%/7%/5%/15% para ACCION/ETF/FONDO/CRIPTO), `valorTipo × ((1+tasaMensual)^meses − 1)`.
  - **Previstos**: entidad `MovimientoPrevisto` (`fecha`, `importe` con signo, `descripcion`, `cumplido`; `@Column(precision = 19, scale = 2)` explícito) gestionada por `MovimientoPrevistoController`. El frontend suma los pendientes (`cumplido = false`) cuyo mes de efecto es `<= t`, con mes de efecto `max(1, ceil(días hasta la fecha / 30,4375))` — los ya vencidos cuentan desde el mes 1, para no mover el punto de partida "hoy" de la gráfica. No se componen (ni generan interés). Se mantienen como filas aparte, sin mezclarlos con `Movimiento`, para que nunca contaminen los promedios de ingreso/gasto ni el saldo real de las cuentas.
  - **Porcentajes del desglose**: se calculan sobre la suma de los componentes **positivos** (`valor / Σ positivos`); los negativos se muestran en € con la etiqueta "Resta del crecimiento". Repartir sobre el total neto daba porcentajes negativos o por encima de 100 en cuanto un componente (p. ej. un pago previsto) restaba.
  - **Limitación documentada, no resuelta**: `ingresoMedio` ya incluye históricamente los intereses que se hayan cobrado en el pasado (son movimientos reales categorizados como ingreso); el bloque de interés proyecta hacia delante sobre el saldo actual sin restar ese historial — pequeño doble conteo, de magnitud mínima frente al ahorro por nómina.

## 7. Cartera de inversión (`inversion`)

- **`PosicionService`**: registra `MovimientoInversion` (COMPRA/VENTA/DIVIDENDO) y mantiene `Posicion.cantidad`/`precioMedioCompra` (coste medio ponderado en compras; una venta reduce cantidad sin tocar el precio medio, y lanza si se intenta vender más de lo que hay).
- **`PrecioMercadoProvider`** (interfaz) + **`YahooFinancePrecioMercadoProvider`**: llama a `https://query1.finance.yahoo.com/v8/finance/chart/{ticker}` y extrae `chart.result[0].meta.regularMarketPrice`. Cualquier fallo (ticker no reconocido, red caída, JSON inesperado) se traga como `Optional.empty()`, nunca lanza — así una cotización no disponible no rompe el cálculo de toda la cartera.
- **`InversionService.valorar(Posicion)`**: si el proveedor no devuelve precio, usa `precioMedioCompra` como respaldo (mejor una valoración aproximada por coste que no poder valorar la posición).
- **Limitación real, no resuelta**: `Activo.ticker` debe ser un ticker de mercado real (`AAPL`, `MSFT`...). Los extractos que identifican activos por **ISIN** (como Trade Republic: `US5949181045`) no se resuelven automáticamente a un ticker — si se crea un `Activo` con un ISIN como "ticker", `InversionService` cae de forma controlada al precio medio de compra, pero no hay ninguna resolución ISIN→ticker implementada.
- **Nombre corto y agrupación por producto (frontend, `lib/producto.ts`)**: los extractos traen el nombre legal con ruido de mercado (`Banco de Sabadell S.A. Acciones Nom. Serie A EO -,125`). `nombreCorto` corta el nombre en la primera marca de ruido (`Acciones`, `Registered`, `UCITS`, `S.A.`, `Inc.`, `DL `…) y aplica unos pocos alias (`Banco Bilbao Vizcaya Argent.` → `BBVA`, `iShsIII-Core` → `iShares Core`); el oro y la plata del mismo emisor se distinguen por su sufijo. `agruparPorProducto` suma las posiciones con el mismo nombre corto. Se hace en el frontend y no al importar porque así también vale para lo ya guardado; es una heurística sobre los formatos de nombre vistos (Trade Republic e ING), y un producto con una forma de nombre nueva cae en el recorte genérico o en su nombre completo.
  - **Rendimiento siempre 0 con extractos de valores**: `Posicion.precioMedioCompra` es el precio del día del extracto (el extracto de cuenta de valores de Trade Republic no incluye el precio de compra), así que `valorActual − coste` es 0 para todas. Por eso la pantalla de Inversión muestra el valor por producto cuando ningún rendimiento es distinto de 0, y solo dibuja rendimiento cuando hay datos reales (p. ej. posiciones creadas a mano con compras registradas).
- **`PosicionValorada.tipo`**: cada posición valorada lleva también el `TipoActivo` de su `Activo`, para poder agrupar la cartera por tipo de producto (lo usa el desglose de proyección del Resumen, sección 6).
- **Proyección compuesta** (`ProyeccionInversionService`): `valor_actual × (1 + retorno_esperado)^años + aportaciones periódicas`. Las aportaciones se suman en total (`aportacionAnual × años`) **sin componer su propio retorno** — misma simplificación deliberada que la proyección lineal, no un cálculo de anualidad completo.

## 8. Seguridad (`security` + `config/SecurityConfig`)

- JWT firmado con HMAC (`jjwt`), sesiones stateless (`SessionCreationPolicy.STATELESS`).
- `POST /auth/register` y `POST /auth/login` son las únicas rutas públicas (`permitAll`); todo lo demás exige `Authorization: Bearer <token>` válido (`anyRequest().authenticated()`).
- Contraseñas con BCrypt (`PasswordEncoder`).
- **Registro y acceso (`AuthController`, `AuthDtos`)**: el registro exige nombre, email válido y contraseña de **8 a 72 caracteres** (72 es el límite real de BCrypt, que ignora en silencio lo que pase de 72 bytes); el login solo exige que coincida. El email se **normaliza** (`trim` + minúsculas) al registrar y al entrar, de modo que `Ana@x.com` y `ana@x.com` son la misma cuenta (antes eran dos). Las credenciales erróneas devuelven un mismo mensaje (`Email o contraseña incorrectos`) para no revelar qué emails existen.
- **Token de un usuario que ya no existe → 401, no 500** (`JwtAuthenticationFilter`): `loadUserByUsername` lanza `UsernameNotFoundException`, que no se capturaba (solo `JwtException` e `IllegalArgumentException`), y la petición acababa en un 500. El frontend solo cierra sesión con un 401, así que se quedaba «logueado» mostrando pantallas vacías (0 posiciones, sin movimientos) sin explicar nada. Pasa en cuanto se borra un usuario o se recrea la base de datos con un navegador que aún guarda el token. Detectado al probar con una sesión de un usuario de prueba ya eliminado.
- **Errores como `{"message": "..."}`** (`ErroresAdvice`): `@Valid` fallido y `ResponseStatusException` se devuelven siempre con ese cuerpo. Antes el mensaje solo aparecía con devtools activo (modo desarrollo); empaquetado en `.jar` Spring Boot lo omite y el frontend se quedaba sin saber por qué se rechazaba algo. `api/client.ts` extrae `message` del JSON.
- **`GET /usuarios` eliminado → `GET /usuarios/me`**: el listado devolvía nombre y email de **todos** los usuarios a cualquiera con sesión; no lo usaba el frontend.
- **Solo accesible desde el propio equipo**: `server.address: 127.0.0.1`. Sin ello Spring escucha en todas las interfaces y cualquiera de la misma red podría llegar al backend.
- **Secreto JWT por instalación**: `jwt.secret` se lee de la variable `JWT_SECRET` (con el valor del código fuente solo como respaldo para desarrollo). El lanzador (`scripts/iniciar-finanzas.ps1`) genera 48 bytes aleatorios la primera vez y los guarda en `.secreto-jwt` (fuera de git). Un secreto publicado en el repositorio permitiría falsificar sesiones; cambiarlo invalida las sesiones abiertas (hay que volver a entrar una vez).
- **Sin recuperación de contraseña**: no hay infraestructura de correo. Si se olvida, el administrador del equipo puede restablecerla con una consulta SQL (hash BCrypt nuevo) o crear otra cuenta.
- **CORS** (`SecurityConfig.corsConfigurationSource`): origen permitido `http://localhost:5173`, cabeceras `Authorization`/`Content-Type`. **Bug real encontrado**: la lista de métodos permitidos no incluía `PATCH`, así que los endpoints `PATCH /cuentas/{id}/saldo-inicial` y `.../tasa-interes` funcionaban por API directa (`curl`) pero fallaban silenciosamente desde el navegador (el preflight `OPTIONS` se rechazaba con 403 y el error solo aparece en la consola del navegador, no en la respuesta visible de la UI) — se detectó al probar la edición de cuenta end-to-end en el frontend, no en una revisión de código. Al añadir un método HTTP nuevo a un controlador hay que recordar añadirlo también aquí.
- **Limitación conocida y deliberada** (comprobada con un segundo usuario real): ningún endpoint comprueba que el `usuarioId` de la URL —ni el `cuentaId`— pertenezca al usuario del token; un usuario registrado puede leer las cuentas y movimientos de otro cambiando el número en la URL. Además `Categoria`, `ReglaCategorizacion` y `Activo` **no tienen propietario**: son compartidas entre todos los usuarios de una instalación, así que las categorías y reglas de uno las ve (y le afectan) a otro. Por eso la aplicación está pensada para **una instalación por persona** (cada una con su base de datos), y **no debe alojarse compartida** hasta resolver ambas cosas. Detalle de la primera: El enunciado original describe la app como "login simple, un único usuario o multi-perfil básico", así que se priorizó no hacer un refactor grande de todos los controladores para añadir autorización por recurso. Si se necesita en el futuro, el punto de partida sería añadir un `CurrentUserProvider` (patrón ya usado en el proyecto hermano `cofradia-scheduler`) y sustituir el `usuarioId` de path por el usuario resuelto desde el `Authentication`.

## 9. API REST (resumen)

Todas las rutas salvo `/auth/**` requieren `Authorization: Bearer <token>`.

| Método y ruta | Descripción |
|---|---|
| `POST /auth/register` | Crea usuario (nombre, email válido, contraseña 8-72), devuelve `{token, usuario}`; 400 con `{message}` si no valida, 409 si el email ya existe |
| `POST /auth/login` | `{token, usuario}` |
| `GET /usuarios/me` | Datos del usuario autenticado |
| `GET/POST /usuarios/{usuarioId}/cuentas` | Cuentas bancarias del usuario |
| `GET /usuarios/{usuarioId}/cuentas/saldos` | Igual que el anterior pero con `saldoActual` calculado (`saldoInicial + Σ movimientos`) por cuenta, además de `tasaInteresAnual` |
| `PATCH /usuarios/{usuarioId}/cuentas/{cuentaId}/saldo-inicial` | Calibra `saldoInicial` de una cuenta ya creada (p. ej. con el saldo inicial/final que imprime el propio extracto bancario) |
| `PATCH /usuarios/{usuarioId}/cuentas/{cuentaId}/tasa-interes` | Calibra `tasaInteresAnual` (TAE) de una cuenta remunerada |
| `GET /cuentas/{cuentaId}/movimientos` | Movimientos de una cuenta |
| `GET /cuentas/{cuentaId}/movimientos/agregacion-mensual` | Ingresos/gastos/balance por mes |
| `POST /cuentas/{cuentaId}/importar` | Multipart `file`; devuelve `{totalEncontrados, nuevos, duplicados, duplicadosProbables}` |
| `GET/POST /categorias` | — |
| `POST /categorias/recategorizar?todas=false` | Asigna categoría a los movimientos sin ella; con `todas=true` recalcula también los automáticos (nunca los manuales). Devuelve cuántos cambian |
| `POST /categorias/sugeridas?usuarioId=` | Carga categorías y reglas sugeridas (idempotente); devuelve `{categoriasCreadas, reglasCreadas}` |
| `GET/POST /reglas-categorizacion` | — |
| `DELETE /reglas-categorizacion/{id}` | Elimina una regla |
| `PATCH /cuentas/{cuentaId}/movimientos/{id}/categoria` | Body `{categoriaId}`: categoría manual; `null` = volver a la automática |
| `GET/POST /usuarios/{usuarioId}/patrimonio/snapshots` | Histórico / generar snapshot |
| `GET /usuarios/{usuarioId}/patrimonio/agregacion-mensual` | Igual que el de cuenta pero sumando todas las cuentas del usuario |
| `GET /usuarios/{usuarioId}/patrimonio/proyeccion?meses=N` | Proyección lineal |
| `GET /usuarios/{usuarioId}/patrimonio/proyeccion/simular?meses&categoriaId&ajuste` | Simulación |
| `GET/POST /activos` | Catálogo de activos |
| `GET /usuarios/{usuarioId}/inversion/posiciones` | Cartera valorada con precio real |
| `POST /usuarios/{usuarioId}/inversion/movimientos` | Registra compra/venta/dividendo |
| `POST /usuarios/{usuarioId}/inversion/posiciones/importar` | Multipart `file`; importa un extracto de cuenta de valores (snapshot de posiciones), devuelve `{totalEncontradas, actualizadas}` |
| `GET /usuarios/{usuarioId}/inversion/proyeccion?anios&retornoEsperado&aportacionAnual` | Proyección compuesta |
| `GET/POST /usuarios/{usuarioId}/previstos` | Movimientos previstos manuales (ingreso `importe > 0`, pago `< 0`; el `0` se rechaza con 400), ordenados por fecha |
| `PATCH /usuarios/{usuarioId}/previstos/{id}` | Body `{cumplido}`: marca o reactiva un previsto |
| `DELETE /usuarios/{usuarioId}/previstos/{id}` | Elimina un previsto |

## 10. Frontend

```
frontend/src/
├── api/           cliente fetch (client.ts) + funciones por feature + types.ts (interfaces TS = DTOs backend)
├── auth/          AuthContext (token + usuario en localStorage) y ProtectedRoute
├── components/    Layout (sidebar), StatCard, ChartCard
├── lib/format.ts  formateo de moneda/fecha/mes + wrappers de formato para los Tooltip de Recharts
└── pages/         una por pantalla (Login, Resumen, FinanzasDiarias, Inversion, Importar, EvolucionHistorica)
```

- **Tema y estilo (oscuro por defecto, estilo Trade Republic)**: los colores no se escriben sueltos en las pantallas, sino como **tokens semánticos** definidos en `src/index.css` con variables CSS (`--page`, `--surface`, `--surface-2`, `--line`, `--ink`, `--ink-2`, `--muted`, `--up`, `--down`, `--warn`, `--link`, `--accent`, `--series-1..8`) y expuestos a Tailwind v4 con `@theme inline` (`bg-surface`, `text-muted`, `text-up`, `border-line`...). El tema oscuro es `:root`; el claro se activa con `data-theme="light"` en `<html>`. El tema elegido se guarda en `localStorage` (`finanzas.tema`) y un script inline en `index.html` lo aplica **antes** de montar React para evitar el parpadeo del tema equivocado. Cambiar de tema es solo cambiar ese atributo (`BotonTema` en `Layout.tsx`); ningún componente tiene lógica de tema.
  - **Gráficas (Recharts)**: usan las mismas variables a través de `lib/format.ts` (`colorParaIndice` → `var(--series-N)`, `COLOR_SUBE`/`COLOR_BAJA`, `estiloTooltip`, `estiloEje`), así que cambian solas con el tema. Los 8 tonos categóricos de `--series-N` son los de la guía de visualización de datos, en orden fijo y validados para cada superficie con su script (`validate_palette.js`): en oscuro pasan todas las comprobaciones (separación para daltonismo, contraste ≥ 3:1); en claro dos tonos quedan por debajo de 3:1, y se compensa con leyenda visible en cada gráfica con ≥ 2 series.
  - **Color fijo por tipo de activo** (`colorParaTipo` en `lib/format.ts`): ETF → `--series-1`, ACCION → `--series-2`, FONDO → `--series-3`, CRIPTO → `--series-4`. Lo comparten el donut, su lista de importes y las barras de «Valor por producto» (que además llevan una leyenda de tipos encima y el tipo en el tooltip). El color sigue a la entidad y no a su posición en la lista: antes el donut coloreaba por índice entre los tipos presentes, de modo que si faltaba un tipo los demás cambiaban de color. Cuando hay rendimiento real las barras vuelven a ser verde/rojo (significado distinto: signo, no tipo).
  - **Decisiones de diseño**: el donut de Inversión agrupa por **tipo de activo** (4 porciones) en vez de una porción por posición (21), que obligaba a repetir colores; las variaciones usan verde/rojo **más** signo (`+`/`▲`/`▼`) para no depender solo del color; el porcentaje de cada gráfica de línea sale verde/rojo según la tendencia del rango mostrado. En pantallas estrechas la barra lateral se sustituye por una navegación inferior fija.
- El cliente API (`api/client.ts`) añade el header `Authorization` si hay token guardado, y si el backend responde `401` dispara un callback (`onUnauthorizedResponse`) que `AuthContext` usa para cerrar sesión y redirigir a `/login`.
- **"Gasto por categoría"** (pantalla Finanzas diarias) se calcula **en el frontend**, agrupando la lista de movimientos ya traída — no hay endpoint de agregación por categoría en el backend; para un uso personal con pocos movimientos no hace falta.
- **Nota de tipado con Recharts**: los props `formatter`/`labelFormatter` de `<Tooltip>` están tipados de forma muy laxa (`ReactNode`, `ValueType | undefined`); pasar directamente una función `(valor: number) => string` no compila. Los wrappers `formatoTooltipMoneda`/`formatoTooltipFecha`/`formatoTooltipMes` en `lib/format.ts` resuelven esto aceptando `unknown` y convirtiendo internamente.
- **Node.js**: si no está en el `PATH` del sistema (o es una instalación "portable"), hay que añadir su carpeta al `PATH` de la sesión antes de `npm`/`node`, o instalarlo de forma estándar desde nodejs.org.

## 10b. Lanzador de escritorio (`scripts/`)

`iniciar-finanzas.ps1` (acceso directo del escritorio, creado por `crear-acceso-directo.ps1`) arranca Postgres, backend e interfaz y abre la web como aplicación; `detener-finanzas.ps1` lo apaga todo; `_comun.ps1` tiene las utilidades compartidas (búsqueda de Java/Node, parada de procesos). Decisiones con motivo:

- **Backend desde el `.jar`, no con `mvnw spring-boot:run`**: Maven tarda ~20 s solo en arrancar y recompila en cada apertura; el `.jar` ya construido arranca la app en ~5 s. Se recompila (`mvnw package -DskipTests`) solo si algún fichero de `src/main` o `pom.xml` es más reciente que el `.jar`. Se lanza con `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC` (arranque más rápido).
- **Interfaz en paralelo y sin npm**: Vite no depende de nada, así que se lanza al principio con `node node_modules\vite\bin\vite.js` (sin pasar por `npm`) mientras Docker y Postgres arrancan. Postgres se arranca con `docker start finanzas-db` (más rápido que `docker compose up`), con `compose up -d` solo como respaldo si el contenedor no existe.
- **Cerrar la web apaga todo, con dos señales independientes** (la que ocurra primero):
  1. **El proceso de la ventana termina**: la ventana se abre con `--app=` y un `--user-data-dir` propio (`.perfil-navegador/`), que la convierte en un proceso aparte. Si termina antes de 8 s (el navegador cedió la ventana a otro proceso) esta señal se descarta.
  2. **La página deja de avisar al servidor** (`ActividadCliente`, `SistemaController`, `lib/latido.ts`): la web envía `POST /sistema/latido` cada 5 s y `POST /sistema/cierre` con `navigator.sendBeacon` en el evento `pagehide`; el lanzador consulta `GET /sistema/actividad` cada 2 s. Se apaga si hay un cierre posterior al último latido durante ≥ 8 s (un latido nuevo lo anula: así una **recarga** o cerrar una pestaña teniendo otra abierta no apaga nada), o si no hay latidos en 150 s (navegador cerrado a la fuerza; el margen cubre que un navegador ralentice los temporizadores de una pestaña oculta a uno por minuto). Esta señal no depende de cómo se abrió la web ni de que Edge se quede en segundo plano al cerrar la ventana.
  - Salvaguardas: la condición debe verse en **2 vueltas seguidas**; si una vuelta tarda > 15 s (equipo suspendido) se ignora; si nunca llega un latido en 120 s se apaga (la página no llegó a abrirse); si el servidor no tiene el endpoint (una versión antigua en marcha) solo vale la señal de la ventana. Al apagar se cierran también los procesos de Edge/Chrome del perfil de la app. Solo se apaga lo que arrancó el propio script, no lo que ya estaba en marcha.
  - Los endpoints `/sistema/**` son públicos pero solo exponen tiempos transcurridos, y el servidor escucha únicamente en `127.0.0.1`. Las peticiones del navegador son «simples» (sin cabeceras propias ni `Content-Type` de JSON) para no necesitar preflight CORS, que no se puede esperar durante el cierre de una página.
  - **Bug real encontrado al probarlo**: la primera versión consultaba la actividad con `Invoke-RestMethod http://localhost:8080`. En Windows PowerShell 5.1 resuelve `localhost` primero a IPv6 y, como el servidor solo escucha en IPv4 (`127.0.0.1`), la petición agotaba a menudo los 2 s de espera; el lanzador interpretaba «sin respuesta» como «nada que decidir» y no apagaba. Se cambió a `curl.exe` contra `127.0.0.1` (0,17 s). Medidas finales: cerrar la ventana → todo apagado en ~7 s; salir de la página → ~14 s; silencio total → ~2,5 min.
- **Banner de progreso** (WinForms) en su **propio hilo STA** (`runspace`) con su bucle de mensajes, que lee el estado de un `hashtable` sincronizado: así no se congela mientras el script principal espera a `docker`, Maven, etc. La barra persigue suavemente el progreso objetivo y, durante las esperas de duración desconocida, avanza con una curva `1 − e^(−t/τ)` que nunca llega al final hasta que la condición se cumple. Se llama a `SetProcessDPIAware()` **antes** de crear ninguna ventana: sin ello Windows escala el banner como un bitmap y se ve borroso en pantallas con escala > 100 % (verificado a 150 %).
- **Bugs reales de la primera versión**: (1) `cmd /c "mvnw.cmd ..."` con `WorkingDirectory` **no encontraba `mvnw.cmd`**, así que el servidor nunca arrancaba y el banner esperaba hasta agotar el tiempo (parecía "muy lento", pero era un cuelgue); se corrigió con la ruta completa y el doble entrecomillado que exige `cmd /c`. (2) El banner se dibujaba en el hilo principal y se congelaba durante cada comando bloqueante; el texto largo se cortaba a media frase.
- **Solo ASCII** en los `.ps1`: Windows PowerShell 5.1 lee los scripts sin BOM como ANSI y corrompe los acentos.

## 11. Cómo verificar que todo funciona (checklist manual)

No hay tests automatizados todavía; la verificación durante el desarrollo se hizo siempre contra datos reales:

1. `docker compose up -d` (Postgres en 5433).
2. `./mvnw spring-boot:run` — comprobar en el log que Hibernate crea/actualiza las tablas sin errores.
3. `cd frontend && npm run build` — debe compilar sin errores de TypeScript.
4. `npm run dev`, abrir `http://localhost:5173`, registrarse, crear una cuenta, importar un extracto real (CSV/PDF), comprobar que los importes y fechas coinciden con el fichero original, y que reimportar el mismo fichero da `0 nuevos`.
5. Revisar la consola del navegador (sin errores) y la pestaña de red (sin `401`/`500` inesperados).
