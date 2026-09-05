# Finanzas

Dashboard de finanzas personales (TFG): importación de extractos bancarios (CSV/PDF), categorización automática por reglas, patrimonio con proyecciones, cartera de inversión valorada con precio de mercado real, y un frontend con gráficas sobre todo lo anterior.

Documentación completa:

- [Documentación técnica](docs/DOCUMENTACION_TECNICA.md) — arquitectura, modelo de datos, API, decisiones de diseño.
- [Documentación funcional](docs/DOCUMENTACION_FUNCIONAL.md) — qué hace la aplicación y cómo se usa, pensada para cualquiera que no vaya a tocar el código.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 4.1.0, Spring Data JPA, Spring Security + JWT |
| Base de datos | PostgreSQL 17 |
| Parsing de ficheros | Apache PDFBox (PDF), OpenCSV (CSV) |
| Precios de mercado | Yahoo Finance (endpoint no oficial, sin API key) |
| Frontend | React 19 + TypeScript, Vite, TanStack Query, Recharts, Tailwind CSS |

## Requisitos previos

- **Java 21** (JDK) — el proyecto usa Maven Wrapper (`mvnw`/`mvnw.cmd`), no hace falta tener Maven instalado aparte.
- **Docker Desktop** — para levantar PostgreSQL sin instalarlo a mano.
- **Node.js 20+** y **npm** — para el frontend.

## Puesta en marcha

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

### 4. Primer uso

1. Abre `http://localhost:5173`.
2. Pulsa "¿No tienes cuenta? Regístrate" y crea tu usuario (nombre, email, contraseña).
3. Ve a **Importar archivos** y pulsa "+ Añadir cuenta nueva" para crear tu primera cuenta bancaria (banco, alias, tipo, moneda, saldo inicial).
4. Sube un extracto en CSV o PDF de esa cuenta. Parsers ya construidos para PDF: **ING** y **Trade Republic**; para CSV hay un parser genérico configurable por nombre de columna (ver documentación técnica).
5. Explora **Resumen**, **Finanzas diarias**, **Inversión** y **Evolución histórica**.

Para categorizar automáticamente los movimientos importados, crea categorías y reglas de categorización vía API (`POST /categorias`, `POST /reglas-categorizacion` — no tienen pantalla propia todavía) y luego usa el botón de recategorizar o vuelve a importar.

## Estructura del repositorio

```
finanzas/
├── src/main/java/com/tfg/finanzas/   backend, organizado por feature (usuario, cuenta, movimiento,
│                                      categoria, importacion, inversion, patrimonio, security, config)
├── src/main/resources/application.yml
├── frontend/                          app React (Vite + TypeScript)
├── docker-compose.yml                 PostgreSQL para desarrollo
├── docs/
│   ├── DOCUMENTACION_TECNICA.md
│   └── DOCUMENTACION_FUNCIONAL.md
└── pom.xml
```

## Limitaciones conocidas

- No hay comprobación de que el `usuarioId` de la URL coincida con el usuario autenticado por el token — pensado para uso personal/single-user, no multi-tenant (ver documentación técnica).
- El precio de mercado en vivo funciona por ticker (p. ej. `AAPL`); los extractos que identifican activos por ISIN (como Trade Republic) no se resuelven automáticamente a un ticker.
- Cashback (`TarjetaBeneficio`/`CashbackAcumulado`) está modelado en la base de datos pero no tiene servicio, API ni pantalla todavía.
- Los snapshots de patrimonio se generan bajo demanda (`POST /usuarios/{id}/patrimonio/snapshots`), no hay una tarea programada que los cree solos.
