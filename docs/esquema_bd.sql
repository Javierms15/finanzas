-- Esquema real de la base de datos "finanzas" (extraido de Postgres con pg_dump,
-- limpiado de ruido de psql/sequences/ACLs para poder pegarlo tal cual en el
-- plugin de FigJam "Entity Relationship Diagram" -> https://www.figma.com/community/plugin/1495824087433821208/entity-relationship-diagram

CREATE TABLE usuario (
    id BIGINT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE cuenta_bancaria (
    id BIGINT PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    banco VARCHAR(255) NOT NULL,
    tipo VARCHAR(255) NOT NULL CHECK (tipo IN ('NOMINA', 'TARJETA', 'AHORRO')),
    alias VARCHAR(255) NOT NULL,
    moneda VARCHAR(255) NOT NULL,
    saldo_inicial NUMERIC NOT NULL
);

CREATE TABLE categoria (
    id BIGINT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    tipo VARCHAR(255) NOT NULL CHECK (tipo IN ('GASTO', 'INGRESO')),
    color VARCHAR(255)
);

CREATE TABLE regla_categorizacion (
    id BIGINT PRIMARY KEY,
    categoria_id BIGINT NOT NULL REFERENCES categoria(id),
    patron VARCHAR(255) NOT NULL,
    prioridad INTEGER NOT NULL
);

CREATE TABLE movimiento (
    id BIGINT PRIMARY KEY,
    cuenta_id BIGINT NOT NULL REFERENCES cuenta_bancaria(id),
    categoria_id BIGINT REFERENCES categoria(id),
    fecha DATE NOT NULL,
    importe NUMERIC NOT NULL,
    descripcion_original TEXT NOT NULL,
    etiqueta_manual VARCHAR(255),
    origen VARCHAR(255) NOT NULL CHECK (origen IN ('CSV', 'PDF', 'MANUAL')),
    hash_dedup VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE tarjeta_beneficio (
    id BIGINT PRIMARY KEY,
    cuenta_id BIGINT NOT NULL REFERENCES cuenta_bancaria(id),
    categoria_aplicable_id BIGINT REFERENCES categoria(id),
    tipo VARCHAR(255) NOT NULL CHECK (tipo IN ('CASHBACK', 'PUNTOS')),
    porcentajeotasa NUMERIC NOT NULL
);

CREATE TABLE cashback_acumulado (
    id BIGINT PRIMARY KEY,
    cuenta_id BIGINT NOT NULL REFERENCES cuenta_bancaria(id),
    mes DATE NOT NULL,
    importe_generado NUMERIC NOT NULL
);

CREATE TABLE activo (
    id BIGINT PRIMARY KEY,
    ticker VARCHAR(255) NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    tipo VARCHAR(255) NOT NULL CHECK (tipo IN ('ACCION', 'ETF', 'FONDO', 'CRIPTO'))
);

CREATE TABLE posicion (
    id BIGINT PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    activo_id BIGINT NOT NULL REFERENCES activo(id),
    cantidad NUMERIC NOT NULL,
    precio_medio_compra NUMERIC NOT NULL
);

CREATE TABLE movimiento_inversion (
    id BIGINT PRIMARY KEY,
    posicion_id BIGINT NOT NULL REFERENCES posicion(id),
    fecha DATE NOT NULL,
    tipo VARCHAR(255) NOT NULL CHECK (tipo IN ('COMPRA', 'VENTA', 'DIVIDENDO')),
    cantidad NUMERIC NOT NULL,
    precio NUMERIC NOT NULL
);

CREATE TABLE snapshot_patrimonio (
    id BIGINT PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha DATE NOT NULL,
    saldo_efectivo_total NUMERIC NOT NULL,
    valor_cartera_inversion NUMERIC NOT NULL,
    patrimonio_total NUMERIC NOT NULL
);
