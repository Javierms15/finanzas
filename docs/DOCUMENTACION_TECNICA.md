# Documentación técnica

## 1. Visión general

Aplicación de finanzas personales con dos partes:

- **Backend** (`/`): API REST en Spring Boot que modela usuarios, cuentas bancarias, movimientos, categorías, cartera de inversión y patrimonio, e importa extractos bancarios (CSV/PDF) parseándolos hasta convertirlos en movimientos.
- **Frontend** (`/frontend`): SPA en React que consume esa API y presenta la información con tablas y gráficas.

El backend está organizado **por feature**, no por capa (no hay paquetes `controller`/`service`/`repository` globales): cada paquete (`usuario`, `cuenta`, `movimiento`, `categoria`, `importacion`, `inversion`, `patrimonio`, `security`, `config`) contiene sus propias entidades, repositorios, servicios y controladores. `Movimiento` referencia `Categoria` y `CuentaBancaria`; `Categoria` no depende de `movimiento` salvo en `CategorizacionService`, que sí necesita `MovimientoRepository` para poder recategorizar — es la única dependencia cruzada real entre paquetes de features.

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

- **`CuentaBancaria.saldoInicial`**: un extracto bancario no siempre cubre toda la vida de la cuenta, así que el saldo real de una cuenta no se puede derivar solo de los movimientos importados. El saldo actual se calcula como `saldoInicial + suma(movimientos.importe)`.
- **`Movimiento.hashDedup`**: hash SHA-256 de `cuentaId|fecha|importe|descripcionOriginal`, columna `unique`. Es la clave de deduplicación al reimportar un fichero que se solape con datos ya importados.
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

- **`IngPdfParser`**: el texto se extrae en orden de lectura normal (`PDFTextStripper` por defecto). Cada movimiento empieza en una línea que matchea `dd/MM/yyyy`; las líneas siguientes que no empiezan por fecha se consideran continuación del mismo movimiento (necesario porque algunas descripciones —p. ej. una transferencia de Hacienda— ocupan varias líneas). El importe y el saldo se extraen como los dos últimos números con formato `-?\d{1,3}(,\d{3})*\.\d{2}` de la línea combinada (formato con coma de millares y punto decimal).
- **`TradeRepublicPdfParser`**: extrae cada fragmento de texto con su coordenada X (subclase de `PDFTextStripper` que sobreescribe `writeString`), y clasifica cada fragmento por la columna en la que cae (fecha/tipo/descripción/entrada/salida/balance) usando rangos de X fijos, calibrados contra un extracto real. Es la única forma fiable de saber si un importe es un ingreso o un gasto en este formato, porque la columna de "entrada de dinero" y la de "salida de dinero" están separadas — el tipo de movimiento (p. ej. "Operar") no basta por sí solo (un "Operar" podría ser una compra o una venta). Los meses en español se resuelven con un mapa fijo (`ene`...`dic`, con caso especial `sept`/`sep`).
- **`CsvGenericoParser`**: sin ningún banco real de referencia todavía. Detecta separador (`,` vs `;`), busca columnas de fecha/descripción/importe por nombre de cabecera habitual, y detecta el formato de importe (coma o punto como separador decimal) mirando cuál de los dos aparece en último lugar en el texto. Es un punto de partida a ajustar en cuanto haya un CSV real de un banco.

### Cómo añadir un parser nuevo

1. Implementar `FileParser` (paquete `importacion`), anotado `@Component` para que Spring lo registre automáticamente (se inyecta como `List<FileParser>` en `ImportacionService`).
2. `soporta(...)` debe basarse en contenido del fichero (texto característico), no solo en la extensión, porque puede haber varios parsers de PDF.
3. Antes de escribir el regex/parsing, extraer el texto real con PDFBox (`PDFTextStripper`, con y sin `setSortByPosition(true)`) para ver cómo llega de verdad — el orden de lectura de un PDF no es necesariamente el orden visual, y el texto puede traer espacios de no separación (` `) entre número y símbolo de moneda que `String.trim()` no elimina (bug real encontrado al probar contra el extracto de Trade Republic; la solución fue limpiar con `replaceAll("[^0-9,.-]", "")` en vez de `replace`/`trim`).

## 5. Categorización (`categoria`)

`CategorizacionService.categorizar(descripcion)` recorre las `ReglaCategorizacion` por `prioridad` ascendente y devuelve la categoría de la primera regla cuyo `patron` aparece como subcadena de la descripción, **sin distinguir mayúsculas ni acentos** (normalización vía `java.text.Normalizer`, forma NFD, eliminando marcas diacríticas). Es una coincidencia de subcadena simple a propósito, no un motor de expresiones regulares — así lo pedía el enunciado original ("palabra clave → categoría").

`recategorizarPendientes()` reevalúa los movimientos con `categoria = null`, útil tras añadir reglas nuevas después de haber importado.

## 6. Patrimonio y proyecciones (`patrimonio`)

- **Saldo efectivo total** (`PatrimonioService.calcularSaldoEfectivoTotal`): suma, para cada cuenta del usuario, `saldoInicial + Σ movimientos.importe`.
- **Valor de cartera de inversión**: delega en `InversionService.valorTotalCartera` (ver sección 7), es decir, usa precio de mercado real cuando está disponible.
- **`SnapshotPatrimonio`**: fotografía puntual (`saldoEfectivoTotal`, `valorCarteraInversion`, `patrimonioTotal`) que se genera bajo demanda (`POST /usuarios/{id}/patrimonio/snapshots`), no automáticamente.
- **Proyección lineal** (`PatrimonioProyeccionService`): `patrimonio_actual + (ingreso_medio − gasto_medio) × meses`. Los promedios se calculan sobre los **meses ya cerrados** (se excluye el mes en curso, porque al estar incompleto sesgaría la media a la baja) usando `AgregacionMensualService`. `gastoMedio` se expresa siempre en positivo (magnitud), no con el signo negativo interno de los movimientos.
- **Simulación "qué pasaría si"** (`simular(usuarioId, meses, categoriaId, ajusteMensual)`): resta `ajusteMensual` al gasto medio de esa categoría concreta y recalcula. Matemáticamente equivale a restar `ajusteMensual` del gasto medio total (porque el total es la suma de todas las categorías), pero se calcula también el detalle por categoría para que la respuesta sea útil en la UI. **No hay clamping**: si `ajusteMensual` es mayor que el gasto medio real de la categoría, el resultado simulado se vuelve negativo — es una simplificación deliberada del modelo, no un bug.

## 7. Cartera de inversión (`inversion`)

- **`PosicionService`**: registra `MovimientoInversion` (COMPRA/VENTA/DIVIDENDO) y mantiene `Posicion.cantidad`/`precioMedioCompra` (coste medio ponderado en compras; una venta reduce cantidad sin tocar el precio medio, y lanza si se intenta vender más de lo que hay).
- **`PrecioMercadoProvider`** (interfaz) + **`YahooFinancePrecioMercadoProvider`**: llama a `https://query1.finance.yahoo.com/v8/finance/chart/{ticker}` y extrae `chart.result[0].meta.regularMarketPrice`. Cualquier fallo (ticker no reconocido, red caída, JSON inesperado) se traga como `Optional.empty()`, nunca lanza — así una cotización no disponible no rompe el cálculo de toda la cartera.
- **`InversionService.valorar(Posicion)`**: si el proveedor no devuelve precio, usa `precioMedioCompra` como respaldo (mejor una valoración aproximada por coste que no poder valorar la posición).
- **Limitación real, no resuelta**: `Activo.ticker` debe ser un ticker de mercado real (`AAPL`, `MSFT`...). Los extractos que identifican activos por **ISIN** (como Trade Republic: `US5949181045`) no se resuelven automáticamente a un ticker — si se crea un `Activo` con un ISIN como "ticker", `InversionService` cae de forma controlada al precio medio de compra, pero no hay ninguna resolución ISIN→ticker implementada.
- **Proyección compuesta** (`ProyeccionInversionService`): `valor_actual × (1 + retorno_esperado)^años + aportaciones periódicas`. Las aportaciones se suman en total (`aportacionAnual × años`) **sin componer su propio retorno** — misma simplificación deliberada que la proyección lineal, no un cálculo de anualidad completo.

## 8. Seguridad (`security` + `config/SecurityConfig`)

- JWT firmado con HMAC (`jjwt`), sesiones stateless (`SessionCreationPolicy.STATELESS`).
- `POST /auth/register` y `POST /auth/login` son las únicas rutas públicas (`permitAll`); todo lo demás exige `Authorization: Bearer <token>` válido (`anyRequest().authenticated()`).
- Contraseñas con BCrypt (`PasswordEncoder`).
- **Limitación conocida y deliberada**: ningún endpoint comprueba que el `usuarioId` de la URL coincida con el usuario del token — cualquier usuario autenticado puede pedir datos de cualquier `usuarioId`. El enunciado original describe la app como "login simple, un único usuario o multi-perfil básico", así que se priorizó no hacer un refactor grande de todos los controladores para añadir autorización por recurso. Si se necesita en el futuro, el punto de partida sería añadir un `CurrentUserProvider` (patrón ya usado en el proyecto hermano `cofradia-scheduler`) y sustituir el `usuarioId` de path por el usuario resuelto desde el `Authentication`.

## 9. API REST (resumen)

Todas las rutas salvo `/auth/**` requieren `Authorization: Bearer <token>`.

| Método y ruta | Descripción |
|---|---|
| `POST /auth/register` | Crea usuario, devuelve `{token, usuario}` |
| `POST /auth/login` | `{token, usuario}` |
| `GET /usuarios` | Lista usuarios (sin `passwordHash`) |
| `GET/POST /usuarios/{usuarioId}/cuentas` | Cuentas bancarias del usuario |
| `GET /cuentas/{cuentaId}/movimientos` | Movimientos de una cuenta |
| `GET /cuentas/{cuentaId}/movimientos/agregacion-mensual` | Ingresos/gastos/balance por mes |
| `POST /cuentas/{cuentaId}/importar` | Multipart `file`; devuelve `{totalEncontrados, nuevos, duplicados}` |
| `GET/POST /categorias` | — |
| `POST /categorias/recategorizar` | Reevalúa movimientos sin categoría |
| `GET/POST /reglas-categorizacion` | — |
| `GET/POST /usuarios/{usuarioId}/patrimonio/snapshots` | Histórico / generar snapshot |
| `GET /usuarios/{usuarioId}/patrimonio/agregacion-mensual` | Igual que el de cuenta pero sumando todas las cuentas del usuario |
| `GET /usuarios/{usuarioId}/patrimonio/proyeccion?meses=N` | Proyección lineal |
| `GET /usuarios/{usuarioId}/patrimonio/proyeccion/simular?meses&categoriaId&ajuste` | Simulación |
| `GET/POST /activos` | Catálogo de activos |
| `GET /usuarios/{usuarioId}/inversion/posiciones` | Cartera valorada con precio real |
| `POST /usuarios/{usuarioId}/inversion/movimientos` | Registra compra/venta/dividendo |
| `GET /usuarios/{usuarioId}/inversion/proyeccion?anios&retornoEsperado&aportacionAnual` | Proyección compuesta |

## 10. Frontend

```
frontend/src/
├── api/           cliente fetch (client.ts) + funciones por feature + types.ts (interfaces TS = DTOs backend)
├── auth/          AuthContext (token + usuario en localStorage) y ProtectedRoute
├── components/    Layout (sidebar), StatCard, ChartCard
├── lib/format.ts  formateo de moneda/fecha/mes + wrappers de formato para los Tooltip de Recharts
└── pages/         una por pantalla (Login, Resumen, FinanzasDiarias, Inversion, Importar, EvolucionHistorica)
```

- El cliente API (`api/client.ts`) añade el header `Authorization` si hay token guardado, y si el backend responde `401` dispara un callback (`onUnauthorizedResponse`) que `AuthContext` usa para cerrar sesión y redirigir a `/login`.
- **"Gasto por categoría"** (pantalla Finanzas diarias) se calcula **en el frontend**, agrupando la lista de movimientos ya traída — no hay endpoint de agregación por categoría en el backend; para un uso personal con pocos movimientos no hace falta.
- **Nota de tipado con Recharts**: los props `formatter`/`labelFormatter` de `<Tooltip>` están tipados de forma muy laxa (`ReactNode`, `ValueType | undefined`); pasar directamente una función `(valor: number) => string` no compila. Los wrappers `formatoTooltipMoneda`/`formatoTooltipFecha`/`formatoTooltipMes` en `lib/format.ts` resuelven esto aceptando `unknown` y convirtiendo internamente.
- **Node.js**: si no está en el `PATH` del sistema (o es una instalación "portable"), hay que añadir su carpeta al `PATH` de la sesión antes de `npm`/`node`, o instalarlo de forma estándar desde nodejs.org.

## 11. Cómo verificar que todo funciona (checklist manual)

No hay tests automatizados todavía; la verificación durante el desarrollo se hizo siempre contra datos reales:

1. `docker compose up -d` (Postgres en 5433).
2. `./mvnw spring-boot:run` — comprobar en el log que Hibernate crea/actualiza las tablas sin errores.
3. `cd frontend && npm run build` — debe compilar sin errores de TypeScript.
4. `npm run dev`, abrir `http://localhost:5173`, registrarse, crear una cuenta, importar un extracto real (CSV/PDF), comprobar que los importes y fechas coinciden con el fichero original, y que reimportar el mismo fichero da `0 nuevos`.
5. Revisar la consola del navegador (sin errores) y la pestaña de red (sin `401`/`500` inesperados).
