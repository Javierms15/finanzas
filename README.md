# Finanzas

Dashboard de finanzas personales (TFG): importación de extractos bancarios (CSV/PDF), categorización automática por reglas, patrimonio con proyecciones, cartera de inversión valorada con precio de mercado real, y un frontend con gráficas sobre todo lo anterior.

Documentación completa:

- [Documentación técnica](docs/DOCUMENTACION_TECNICA.md) — arquitectura, modelo de datos, API, decisiones de diseño.
- [Documentación funcional](docs/DOCUMENTACION_FUNCIONAL.md) — qué hace la aplicación y cómo se usa, pensada para cualquiera que no vaya a tocar el código.
- [Preparación para la defensa](docs/PREPARACION_DEFENSA.md) — guía de estudio con el porqué de cada decisión, los problemas reales encontrados y preguntas típicas de tribunal ya respondidas.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 4.1.0, Spring Data JPA, Spring Security + JWT |
| Base de datos | PostgreSQL 17 |
| Parsing de ficheros | Apache PDFBox (PDF), OpenCSV (CSV) |
| Precios de mercado | Yahoo Finance (endpoint no oficial, sin API key) |
| Frontend | React 19 + TypeScript, Vite, TanStack Query, Recharts, Tailwind CSS |
| Tests | JUnit 5 + Mockito + Testcontainers (PostgreSQL real) + MockMvc; Vitest + React Testing Library |

## Requisitos previos

- **Java 21** (JDK) — el proyecto usa Maven Wrapper (`mvnw`/`mvnw.cmd`), no hace falta tener Maven instalado aparte.
- **Docker Desktop** — para levantar PostgreSQL sin instalarlo a mano.
- **Node.js 20+** y **npm** — para el frontend.

## Arranque con un clic (Windows)

Con Java, Node y Docker Desktop ya instalados, hay una forma de usar Finanzas como una aplicación de escritorio, sin abrir terminales:

```powershell
.\scripts\crear-acceso-directo.ps1
```

Se ejecuta **una sola vez** y crea el icono **Finanzas** en el escritorio y en el menú Inicio. Al abrirlo:

1. Aparece un banner de progreso mientras arranca todo (base de datos, servidor e interfaz; Docker Desktop se abre solo si no estaba abierto).
2. Se abre la aplicación en una **ventana propia** (Edge o Chrome en modo aplicación: sin pestañas ni barra de direcciones). Conserva el login entre usos.
3. Al **cerrar la web se apaga todo** lo que arrancó el icono (si ya tenías algo en marcha, lo deja como estaba). Funciona también si abres `http://localhost:5173` en una pestaña normal del navegador: la página avisa al servidor mientras está abierta y al cerrarse. Tarda ~7 s al cerrar la ventana de la aplicación y ~14 s si cierras una pestaña; si el navegador se cierra a la fuerza sin avisar, ~2,5 min.

Tiempos orientativos: ~10-15 s desde cero; la **primera vez** además compila el servidor a un `.jar` (~20 s), y se vuelve a compilar solo cuando cambia el código. Los registros quedan en `logs/`. Si algo se queda colgado: `.\scripts\detener-finanzas.ps1` lo apaga todo.

Seguridad: la primera vez el icono genera un secreto aleatorio para firmar las sesiones (`.secreto-jwt`, fuera de git) y el servidor solo escucha en `127.0.0.1`, es decir, solo es accesible desde ese mismo ordenador. Cada persona debe usar **su propia instalación** (sus datos se guardan en su base de datos local); no está pensada para alojarla compartida (ver «Limitaciones conocidas»).

Notas: Java se busca en `JAVA_HOME`, `~\.jdks`, `Program Files\Java`...; Node en el `PATH` o en instalaciones portables conocidas, así que no hace falta tocar el `PATH`. El script solo contiene ASCII a propósito (Windows PowerShell 5.1 lee los `.ps1` sin BOM como ANSI y rompe los acentos).

## Puesta en marcha (manual, para desarrollo)

### 1. Base de datos

Desde la raíz del repositorio:

```bash
docker compose up -d
```

Levanta Postgres en `localhost:5433` (ver [docker-compose.yml](docker-compose.yml)). Se usa el puerto 5433 en vez del 5432 por defecto a propósito, para no chocar con otro Postgres que puedas tener corriendo en la misma máquina para otro proyecto.

### 2. Backend

```bash
./mvnw spring-boot:run
```

(en Windows, `mvnw.cmd spring-boot:run` desde PowerShell/CMD). Arranca en `http://localhost:8080`. En el primer arranque, Hibernate crea el esquema completo automáticamente (`spring.jpa.hibernate.ddl-auto: update` en [application.yml](src/main/resources/application.yml)) — no hace falta ejecutar ningún script SQL a mano.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Arranca en `http://localhost:5173`. El backend tiene CORS configurado específicamente para ese origen (ver `SecurityConfig`), así que si cambias el puerto del frontend tendrás que actualizarlo también ahí.

#### Si `npm`/`node` no se reconocen ("no se reconoce como nombre de un cmdlet...")

En este equipo, Node.js está instalado como versión **portable** en `C:\Users\javie\AppData\Local\Programs\nodejs-portable\node-v24.18.1-win-x64`, no con el instalador oficial — por eso `npm`/`node` no funcionan en una terminal nueva hasta que esa carpeta esté en el `PATH`.

- **Ya debería estar arreglado de forma permanente**: se añadió esa carpeta al `PATH` del usuario de Windows. Solo hace falta **abrir una terminal nueva** (las que ya tenías abiertas no recogen el cambio) y `npm`/`node` deberían funcionar directamente.
- **Si en alguna terminal sigue sin funcionar** (por ejemplo, si se reinstala Node en otra ruta más adelante), usa el script de arranque de respaldo, que no depende del `PATH`:
  ```powershell
  cd frontend
  .\iniciar.ps1
  ```
  Instala dependencias si hace falta y arranca `npm run dev`, usando la ruta de Node directamente si no la encuentra en el `PATH`.
- **Para instalar Node "de verdad"** (recomendado a medio plazo, evita todo esto): descarga el instalador LTS desde [nodejs.org](https://nodejs.org) — el instalador oficial sí se añade solo al `PATH`.

### 4. Primer uso

1. Abre `http://localhost:5173`.
2. Pulsa "¿No tienes cuenta? Regístrate" y crea tu usuario (nombre, email, contraseña).
3. Ve a **Importar archivos** y pulsa "+ Añadir cuenta nueva" para crear tu primera cuenta bancaria (banco, alias, tipo, moneda, saldo inicial).
4. Sube un extracto en CSV o PDF de esa cuenta. Parsers ya construidos para PDF: **ING** y **Trade Republic**; para CSV hay un parser genérico configurable por nombre de columna (ver documentación técnica).
5. Explora **Resumen**, **Finanzas diarias**, **Inversión** y **Evolución histórica**.

Para categorizar automáticamente los movimientos importados, crea categorías y reglas de categorización vía API (`POST /categorias`, `POST /reglas-categorizacion` — no tienen pantalla propia todavía) y luego usa el botón de recategorizar o vuelve a importar.

## Tests

```
scripts\ejecutar-tests.ps1        # backend + frontend (≈ 280 tests)
```

Por separado: `mvnw.cmd test` y, en `frontend/`, `npm test`. Los tests de integración del backend arrancan su propio PostgreSQL en Docker (Testcontainers), así que **Docker debe estar en marcha**; sin Docker se omiten y solo corren los unitarios. Más detalle en `docs/DOCUMENTACION_TECNICA.md`, apartado 11.

## Estructura del repositorio

```
finanzas/
├── src/main/java/com/tfg/finanzas/   backend, organizado por capa (model, repository, service,
│                                      web, dto, más security y config transversales)
├── src/main/resources/application.yml
├── frontend/                          app React (Vite + TypeScript)
├── src/test/java/com/tfg/finanzas/    tests del backend (unitarios, parsers con PDFs sintéticos, API)
├── scripts/                           lanzador de escritorio y ejecutar-tests.ps1
├── docker-compose.yml                 PostgreSQL para desarrollo
├── docs/
│   ├── DOCUMENTACION_TECNICA.md
│   ├── DOCUMENTACION_FUNCIONAL.md
│   └── PREPARACION_DEFENSA.md
└── pom.xml
```

## Limitaciones conocidas

- **Aislamiento entre usuarios**: no hay comprobación de que el `usuarioId`/`cuentaId` de la URL pertenezca al usuario del token (comprobado: un usuario registrado puede leer los datos de otro cambiando el número en la URL), y las categorías y reglas son compartidas entre todos los usuarios de una instalación. Está pensado para **una instalación por persona**, no para alojarlo compartido (ver documentación técnica).
- No hay recuperación de contraseña por correo (sí se puede cambiar estando dentro, en «Mi cuenta»).
- El precio de mercado en vivo funciona por ticker (p. ej. `AAPL`); los extractos que identifican activos por ISIN (como Trade Republic) no se resuelven automáticamente a un ticker.
- Las fotos (snapshots) del patrimonio se guardan solas **al arrancar la aplicación** (una por día): si la app no se arranca un día, ese día no hay foto. No hay una tarea programada que corra con la app abierta, y el usuario no puede crearlas a mano.
