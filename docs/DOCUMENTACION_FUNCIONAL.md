# Documentación funcional

## ¿Qué es esta aplicación?

Un panel de finanzas personales. Permite subir los extractos de tus cuentas bancarias (en PDF o CSV), ver todos tus movimientos ya clasificados por categoría, entender cómo evoluciona tu patrimonio (dinero en cuenta + inversiones) y proyectar cómo podría evolucionar en el futuro si nada cambia — o si decides cambiar algo, como gastar menos en una categoría concreta.

No sustituye a la app de tu banco: es un sitio donde juntar la información de **varias** cuentas y bancos distintos, y verla de forma consolidada.

## Antes de empezar

1. **Regístrate** con tu nombre, un email y una contraseña de **al menos 8 caracteres** (se pide dos veces para evitar erratas; hay un botón «Mostrar» para verla). El email no distingue mayúsculas de minúsculas. No hay recuperación de contraseña por correo, así que anótala: si la olvidas, quien administre la instalación tendrá que restablecerla. La primera vez que entras ves una guía de **primeros pasos** (crear cuenta, subir extracto, cargar categorías sugeridas).
2. **Crea al menos una cuenta bancaria** (pantalla "Importar archivos" → "+ Añadir cuenta nueva"): banco, un alias para reconocerla (p. ej. "Nómina ING"), tipo (nómina/tarjeta/ahorro), moneda, si lo sabes el saldo que tenía la cuenta antes de tu primer extracto importado (si no lo sabes, déjalo en 0 — solo afecta a que el saldo mostrado sea exacto o aproximado), y si es una cuenta remunerada (p. ej. una "cuenta naranja"), opcionalmente su interés anual — se usa para proyectar cuánto te va a dar esa cuenta en el Resumen, aparte del ahorro por nómina.
3. **Sube un extracto** de esa cuenta.

## Pantallas

### Resumen

La portada. Muestra tu patrimonio total ahora mismo (efectivo + inversiones), y una gráfica con dos tramos: uno **sólido** (tu patrimonio real hasta hoy, si has generado algún "snapshot" — ver más abajo) y uno **discontinuo** (una proyección mes a mes de hacia dónde vas, con un punto intermedio por cada paso, no solo el valor final). El horizonte es libre: hay botones rápidos (6 meses / 1 año / 3 años / 5 años / 10 años) y también puedes escribir cualquier número de meses.

Debajo de la gráfica, "De dónde viene el crecimiento proyectado" reparte en euros y en porcentaje el crecimiento estimado entre cuatro orígenes distintos (el porcentaje reparte solo lo que suma; un componente negativo se indica como "resta del crecimiento" en vez de dar porcentajes negativos o por encima del 100 %):

- **Ahorro (ingresos de trabajo)**: tu ingreso medio menos tu gasto medio de los meses ya cerrados, acumulado mes a mes.
- **Intereses / remuneración**: el interés compuesto de tus cuentas remuneradas (las que tengan un interés anual indicado en "Importar archivos") sobre su saldo actual. Si ninguna cuenta tiene un interés configurado, este apartado sale en 0€.
- **Previstos (manuales)**: pagos o ingresos que esperas y todavía no aparecen en ningún extracto (p. ej. dinero que le prestaste a un amigo y te va a devolver, o un pago que sabes que vas a tener). Los añades en el bloque "Pagos e ingresos previstos" con una descripción, si es ingreso o pago, el importe y la fecha prevista; cuentan en la proyección a partir de esa fecha (si la fecha ya pasó, desde el mes siguiente). Cuando el movimiento real aparezca en un extracto importado, márcalo como "cumplido" o elimínalo — si no, se contaría dos veces. Un previsto cumplido deja de contar pero se conserva en la lista por si quieres reactivarlo.
- **Crecimiento de inversión**: tu cartera agrupada por tipo de producto (acciones, ETFs, fondos, cripto), cada uno creciendo compuesto a la rentabilidad anual que le asignes. Hay un panel "Ajustar supuestos de crecimiento" donde ves cuánto tienes de cada tipo y puedes cambiar la rentabilidad esperada de cada uno — son supuestos editables, no una recomendación de inversión.

### Finanzas diarias

El día a día de una cuenta concreta (elígela en el desplegable de arriba):

- Todos sus movimientos, con la categoría que se le haya asignado automáticamente (si tiene alguna regla que le encaje).
- Una gráfica de ingresos frente a gastos, mes a mes.
- Un donut con en qué categorías se te va el dinero.
- Un simulador: "si redujera X euros al mes en esta categoría, ¿cómo cambiaría mi proyección a 3 meses?" — útil para ver el impacto real de un propósito de ahorro antes de intentarlo.

### Inversión

Tu cartera: qué tienes, cuánto vale ahora mismo (con precio de mercado real, no el precio al que compraste), y cuánto has ganado o perdido en cada posición. También un simulador de proyección: si tu cartera sigue creciendo a un % anual esperado y además aportas una cantidad cada año, ¿cuánto podría valer dentro de N años?

La cartera se muestra **agrupada por producto**, con un color por tipo de activo (ETFs, acciones, fondos, cripto) que es el mismo en el gráfico circular y en las barras, y con nombres legibles (BBVA, Unicaja Banco, iShares Core S&P 500…) en vez del código ISIN: un gráfico de barras con un producto por barra y una tabla ordenada de mayor a menor valor, con el peso de cada uno en la cartera. Si el extracto no trae el precio al que compraste (el de Trade Republic no lo trae), el rendimiento de todos los productos sale 0 €; en ese caso el gráfico muestra el **valor** de cada producto en vez de un rendimiento vacío, y lo avisa en el subtítulo.

Arriba del todo tienes una zona para **subir un extracto de tu cuenta de valores** (el documento que te da tu broker con la foto de tus posiciones actuales: cuántos títulos tienes de cada cosa y a qué precio). Al subirlo, tus posiciones se actualizan para que coincidan exactamente con ese extracto — útil para corregir o refrescar tu cartera de un golpe en vez de ir una por una.

Esta misma zona también acepta el **"Extracto Integrado" de ING** (el mismo PDF que subes en "Importar archivos" para tus movimientos de cuenta): de ahí se lee la sección de **Fondos de Inversión** y se actualizan como posiciones, aunque sea el mismo fichero que usas para otra cosa en otra pantalla.

**Importante**: el precio en vivo solo funciona si el activo tiene un **ticker de mercado real** (p. ej. `AAPL` para Apple), no un ISIN (el código que usan algunos brokers como identificador, tipo `US0378331005`). Si el ticker no se reconoce, la aplicación muestra el precio del momento en que importaste el extracto en vez de fallar — pero no se actualiza solo después; para refrescarlo, vuelve a subir un extracto más reciente.

### Importar archivos

Aquí subes tus extractos. Elige la cuenta, arrastra el fichero (o haz clic para elegirlo) y en unos segundos verás cuántos movimientos había en el fichero, cuántos son nuevos (se han añadido) y cuántos ya existían (no se duplican, aunque subas el mismo extracto dos veces o extractos que se solapen en fechas).

Formatos que la aplicación ya sabe leer:

- **PDF de ING, "Movimientos de la Cuenta"** (extracto de una sola cuenta)
- **PDF de ING, "Extracto Integrado"** (el resumen mensual que junta varias cuentas tuyas en un solo PDF — nómina, ahorro... Los movimientos de cada cuenta se marcan con el nombre de esa cuenta en la descripción, ya que todos se importan contra la cuenta que elijas en esta pantalla)
- **PDF de Trade Republic, "Extracto de cuenta"** (movimientos de efectivo)
- **CSV genérico**: intenta reconocer columnas de fecha/descripción/importe por su nombre de cabecera; si tu banco exporta CSV y no lo reconoce bien, puede necesitar ajustes.

El extracto de **cuenta de valores** (posiciones de inversión) no se sube aquí — tiene su propia zona de subida en la pantalla de **Inversión**, porque no es un listado de movimientos sino una foto de lo que tienes ahora mismo.

Si subes un banco no soportado en PDF, la aplicación no sabrá leerlo (los PDF de banco a banco tienen formatos muy distintos entre sí).

### Evolución histórica

Cómo ha ido tu patrimonio en el tiempo, con filtros de rango (3 meses / 6 meses / 1 año / todo) y de qué quieres ver (total, solo efectivo, o solo inversión).

Esta gráfica se basa en **snapshots**: fotografías de tu patrimonio en un momento dado. La aplicación no los genera sola — hay que pedirlos explícitamente (de momento vía la API, `POST /usuarios/{id}/patrimonio/snapshots`; no tiene botón en esta versión de la pantalla). Si todavía no tienes ninguno, la gráfica reconstruye una aproximación a partir de tus movimientos mes a mes (solo la parte de efectivo, no la de inversión), y te avisa de que es una aproximación.

## Cómo funciona por dentro (explicado sin código)

- **Categorización automática**: cuando importas movimientos, si hay alguna regla ("si la descripción contiene esta palabra, va a esta categoría"), se aplica sola; gana la de mayor prioridad que encaje. No distingue mayúsculas ni acentos, y una categoría de gasto nunca se asigna a un ingreso (ni al revés). En la pantalla **Categorías** puedes cargar un juego de categorías y reglas ya preparado para ING y Trade Republic (se puede pulsar varias veces sin duplicar), añadir tus propias categorías y reglas, borrar reglas y recategorizar. En **Finanzas diarias** puedes cambiar la categoría de cualquier movimiento desde su desplegable; lo que elijas a mano no lo vuelve a tocar la recategorización automática (se marca con ✎), y hay un filtro "solo sin categoría" para repasar lo pendiente.
- **Categorías neutras**: son las que mueven dinero sin ser ni ingreso ni gasto, como las transferencias entre tus propias cuentas o lo que aportas a inversión. **No cuentan** como ingreso ni gasto (ni en el gráfico de gasto por categoría, ni en las medias que usa la proyección), pero sí cuentan en el saldo de la cuenta. Así tus transferencias a ti mismo no inflan los gastos. Las transferencias del extracto integrado de ING no indican a quién van, por lo que esas hay que asignarlas tú a mano si son entre tus cuentas.
- **Deduplicación**: cada movimiento se identifica por su cuenta, fecha, importe y descripción exactos. Si subes un extracto que se solapa con movimientos ya importados, esos no se duplican — solo se añaden los que faltan. Además, si el mismo movimiento real aparece descrito de forma distinta en dos formatos de extracto distintos del mismo banco (p. ej. "Movimientos de la Cuenta" vs "Extracto Integrado" de ING), también se detecta por tener la misma cuenta, fecha e importe aunque la descripción no coincida — se cuenta aparte como "probable duplicado" para que quede constancia de que no fue una coincidencia exacta.
- **Saldo inicial de una cuenta**: el saldo mostrado es siempre "lo que tenía antes de tu primer extracto" más la suma de todo lo importado. Si pusiste 0 al crear la cuenta (o tu primer extracto no empieza en el primer movimiento real de la cuenta), el saldo mostrado quedará por debajo del real. Puedes corregirlo en "Importar archivos" (el enlace "✎ Editar saldo inicial / interés" de la cuenta seleccionada), señalando el saldo inicial o final que el propio extracto imprime en su resumen. Ten en cuenta que esa corrección solo es exacta **a la fecha de ese extracto**: si han pasado semanas desde entonces sin subir más movimientos, el saldo mostrado seguirá sin reflejar lo que tienes hoy.
- **Interés de una cuenta remunerada**: se edita en el mismo sitio que el saldo inicial. Si una cuenta junta varios subproductos con interés distinto (p. ej. un extracto que combina nómina y una cuenta naranja en una sola cuenta de la aplicación), el interés que pongas se aplica a **todo** el saldo de esa cuenta, no solo a la parte que de verdad es remunerada — pon un interés más bajo (ponderado) si quieres compensar esa aproximación.
- **Proyección de efectivo (lineal)**: mira tu ingreso medio y tu gasto medio de los meses ya terminados (el mes actual no cuenta, porque está a medias) y proyecta: patrimonio de hoy + (ingreso medio − gasto medio) × meses que elijas.
- **Proyección de inversión (compuesta)**: valor actual de tu cartera, multiplicado por (1 + rentabilidad esperada) elevado a los años que elijas, más lo que aportes cada año (sin que esas aportaciones generen a su vez rentabilidad — es una proyección simplificada, no un simulador financiero de precisión).
- **Simulador "qué pasaría si"**: te deja probar mentalmente un cambio de hábito (gastar X euros menos al mes en una categoría) sin tener que cambiar nada de verdad.

## Preguntas frecuentes

**¿Puedo tener varias cuentas de bancos distintos?** Sí, todas las que quieras; cada una se importa y se ve por separado en "Finanzas diarias", pero se suman en "Resumen" y "Evolución histórica".

**¿Por qué mi cartera muestra el precio de compra en vez del precio actual?** Porque el ticker del activo no se ha podido resolver contra Yahoo Finance (ticker incorrecto, o es un ISIN en vez de un ticker de mercado).

**¿Por qué no veo cashback?** Todavía no está implementado en esta versión, aunque está en los planes iniciales del proyecto.

**¿Es seguro usarla con mis datos reales?** Las contraseñas se guardan cifradas y cada pantalla exige haber iniciado sesión. Ten en cuenta que, en esta versión, cualquier usuario que inicie sesión podría, en teoría, consultar datos de otro usuario si supiera su identificador — está pensada para uso personal, no como servicio multiusuario con aislamiento estricto entre cuentas.
