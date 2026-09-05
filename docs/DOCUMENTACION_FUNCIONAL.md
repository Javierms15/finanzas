# Documentación funcional

## ¿Qué es esta aplicación?

Un panel de finanzas personales. Permite subir los extractos de tus cuentas bancarias (en PDF o CSV), ver todos tus movimientos ya clasificados por categoría, entender cómo evoluciona tu patrimonio (dinero en cuenta + inversiones) y proyectar cómo podría evolucionar en el futuro si nada cambia — o si decides cambiar algo, como gastar menos en una categoría concreta.

No sustituye a la app de tu banco: es un sitio donde juntar la información de **varias** cuentas y bancos distintos, y verla de forma consolidada.

## Antes de empezar

1. **Regístrate** con tu nombre, un email y una contraseña.
2. **Crea al menos una cuenta bancaria** (pantalla "Importar archivos" → "+ Añadir cuenta nueva"): banco, un alias para reconocerla (p. ej. "Nómina ING"), tipo (nómina/tarjeta/ahorro), moneda y, si lo sabes, el saldo que tenía la cuenta antes de tu primer extracto importado (si no lo sabes, déjalo en 0 — solo afecta a que el saldo mostrado sea exacto o aproximado).
3. **Sube un extracto** de esa cuenta.

## Pantallas

### Resumen

La portada. Muestra tu patrimonio total ahora mismo (efectivo + inversiones), y una gráfica con dos tramos: uno **sólido** (tu patrimonio real hasta hoy, si has generado algún "snapshot" — ver más abajo) y uno **discontinuo** (una proyección de hacia dónde vas si tus ingresos y gastos medios se mantienen, y tus inversiones crecen al ritmo esperado). Puedes cambiar el horizonte de la proyección entre 1, 3 y 5 años.

### Finanzas diarias

El día a día de una cuenta concreta (elígela en el desplegable de arriba):

- Todos sus movimientos, con la categoría que se le haya asignado automáticamente (si tiene alguna regla que le encaje).
- Una gráfica de ingresos frente a gastos, mes a mes.
- Un donut con en qué categorías se te va el dinero.
- Un simulador: "si redujera X euros al mes en esta categoría, ¿cómo cambiaría mi proyección a 3 meses?" — útil para ver el impacto real de un propósito de ahorro antes de intentarlo.

### Inversión

Tu cartera: qué tienes, cuánto vale ahora mismo (con precio de mercado real, no el precio al que compraste), y cuánto has ganado o perdido en cada posición. También un simulador de proyección: si tu cartera sigue creciendo a un % anual esperado y además aportas una cantidad cada año, ¿cuánto podría valer dentro de N años?

**Importante**: el precio en vivo solo funciona si el activo tiene un **ticker de mercado real** (p. ej. `AAPL` para Apple), no un ISIN (el código que usan algunos brokers como identificador, tipo `US0378331005`). Si el ticker no se reconoce, la aplicación muestra el valor al precio al que compraste en vez de fallar — pero no es el valor de mercado real.

### Importar archivos

Aquí subes tus extractos. Elige la cuenta, arrastra el fichero (o haz clic para elegirlo) y en unos segundos verás cuántos movimientos había en el fichero, cuántos son nuevos (se han añadido) y cuántos ya existían (no se duplican, aunque subas el mismo extracto dos veces o extractos que se solapen en fechas).

Formatos que la aplicación ya sabe leer:

- **PDF de ING** ("Movimientos de la Cuenta")
- **PDF de Trade Republic** ("Extracto de cuenta")
- **CSV genérico**: intenta reconocer columnas de fecha/descripción/importe por su nombre de cabecera; si tu banco exporta CSV y no lo reconoce bien, puede necesitar ajustes.

Si subes un banco no soportado en PDF, la aplicación no sabrá leerlo (los PDF de banco a banco tienen formatos muy distintos entre sí).

### Evolución histórica

Cómo ha ido tu patrimonio en el tiempo, con filtros de rango (3 meses / 6 meses / 1 año / todo) y de qué quieres ver (total, solo efectivo, o solo inversión).

Esta gráfica se basa en **snapshots**: fotografías de tu patrimonio en un momento dado. La aplicación no los genera sola — hay que pedirlos explícitamente (de momento vía la API, `POST /usuarios/{id}/patrimonio/snapshots`; no tiene botón en esta versión de la pantalla). Si todavía no tienes ninguno, la gráfica reconstruye una aproximación a partir de tus movimientos mes a mes (solo la parte de efectivo, no la de inversión), y te avisa de que es una aproximación.

## Cómo funciona por dentro (explicado sin código)

- **Categorización automática**: cuando importas movimientos, si has creado alguna regla ("si la descripción contiene esta palabra, va a esta categoría"), se aplica sola. Puedes crear varias reglas con distinta prioridad; gana la de mayor prioridad que encaje. No distingue mayúsculas ni acentos.
- **Deduplicación**: cada movimiento se identifica por su cuenta, fecha, importe y descripción exactos. Si subes un extracto que se solapa con movimientos ya importados, esos no se duplican — solo se añaden los que faltan.
- **Proyección de efectivo (lineal)**: mira tu ingreso medio y tu gasto medio de los meses ya terminados (el mes actual no cuenta, porque está a medias) y proyecta: patrimonio de hoy + (ingreso medio − gasto medio) × meses que elijas.
- **Proyección de inversión (compuesta)**: valor actual de tu cartera, multiplicado por (1 + rentabilidad esperada) elevado a los años que elijas, más lo que aportes cada año (sin que esas aportaciones generen a su vez rentabilidad — es una proyección simplificada, no un simulador financiero de precisión).
- **Simulador "qué pasaría si"**: te deja probar mentalmente un cambio de hábito (gastar X euros menos al mes en una categoría) sin tener que cambiar nada de verdad.

## Preguntas frecuentes

**¿Puedo tener varias cuentas de bancos distintos?** Sí, todas las que quieras; cada una se importa y se ve por separado en "Finanzas diarias", pero se suman en "Resumen" y "Evolución histórica".

**¿Por qué mi cartera muestra el precio de compra en vez del precio actual?** Porque el ticker del activo no se ha podido resolver contra Yahoo Finance (ticker incorrecto, o es un ISIN en vez de un ticker de mercado).

**¿Por qué no veo cashback?** Todavía no está implementado en esta versión, aunque está en los planes iniciales del proyecto.

**¿Es seguro usarla con mis datos reales?** Las contraseñas se guardan cifradas y cada pantalla exige haber iniciado sesión. Ten en cuenta que, en esta versión, cualquier usuario que inicie sesión podría, en teoría, consultar datos de otro usuario si supiera su identificador — está pensada para uso personal, no como servicio multiusuario con aislamiento estricto entre cuentas.
