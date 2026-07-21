# Informe de Auditoría de Seguridad
## Sistema de Gestión Bibliotecaria — Segunda Iteración

**Elaborado por:** Arturo Pinto (Escriba / Auditor, según rol asignado en PAC v1.0, Sección 2.1)
**Fecha:** 2026-07-19
**Documento complementario de:** PAC — Segunda Iteración, Plan de Pruebas (ISO/IEC/IEEE 29119-3)
**Alcance:** Backend del Sistema de Gestión Bibliotecaria (`github.com/Sgontardo/gestion-bibliotecaria`)

---

## 1. Propósito y alcance

Este informe documenta la auditoría de cumplimiento de seguridad realizada sobre el backend del Sistema de Gestión Bibliotecaria, cerrando el ciclo de Verificación de la Segunda Iteración. A diferencia del Informe de Pruebas —que reporta *qué se probó y con qué resultado*— este informe responde a una pregunta distinta: **¿el sistema, en su estado actual, cumple con los marcos normativos que el equipo se comprometió a seguir, y qué grado de confianza puede depositar un tercero en esa afirmación?**

El alcance se limita a la característica de **Seguridad** de ISO/IEC 25010 (Confidencialidad, Integridad, Autenticidad) sobre el backend. No cubre la característica de Adecuación Funcional auditada en la Primera Iteración (ERS/DAS), ni aspectos de infraestructura de despliegue fuera del código de la aplicación.

---

## 2. Razones para la selección de los estándares considerados

La auditoría no se realizó contra un único estándar, sino contra tres marcos complementarios, cada uno elegido por una razón distinta y verificable — no por acumulación arbitraria:

### 2.1 ISO/IEC 25010 — ¿Qué debe cumplir el sistema?

Es el estándar que el propio PAC v1.0 adoptó desde la Primera Iteración como modelo de calidad de producto. Se mantiene como marco raíz porque define el vocabulario de *qué* se está auditando — las subcaracterísticas Confidencialidad, Integridad y Autenticidad— sin prescribir *cómo* verificarlo a nivel de código. Por sí solo, es insuficiente para una auditoría técnica: es un modelo de calidad, no un catálogo de controles ni de pruebas.

### 2.2 ISO/IEC 27001:2022, Anexo A — ¿Por qué le importa a la organización?

Se incorporó para resolver la insuficiencia anterior desde el ángulo de gestión: cada hallazgo técnico se cruza contra un control específico del Anexo A (ej. A.8.2 Derechos de acceso privilegiado, A.5.15 Control de acceso, A.8.28 Codificación segura), de modo que un defecto de código no queda aislado como curiosidad técnica, sino vinculado a una obligación de gestión de seguridad de la información reconocida internacionalmente. Esto es lo que permite que la auditoría hable el idioma de un evaluador de procesos, no solo el de un desarrollador.

### 2.3 OWASP ASVS — ¿Cómo se verifica objetivamente?

Se eligió ASVS por sobre el OWASP Top 10 porque el Top 10 es una lista de *categorías de riesgo* (útil para priorizar), no de *requisitos verificables*. ASVS provee identificadores numerados y comprobables (ej. V2.1.1 — longitud mínima de contraseña; V4.1.3 — control de acceso aplicado del lado del servidor), que es precisamente lo que un instrumento de auditoría necesita: un criterio binario de aprobado/no aprobado por ítem, no una categoría general.

### 2.4 ISO/IEC/IEEE 29119 — ¿Cómo se generó la evidencia?

Referenciado por completitud metodológica: la evidencia que sustenta esta auditoría (Sección 4) no es una revisión manual de código, sino la ejecución repetible de pruebas dinámicas diseñadas conforme a las técnicas de la Parte 4 de esta norma (partición de equivalencia, análisis de valores límite, pruebas basadas en defectos). La auditoría es, en ese sentido, tan confiable como la suite de pruebas que la respalda.

**Síntesis:** ISO 25010 dice *qué*, ISO 27001 dice *por qué le importa a la organización*, ASVS dice *cómo se verifica objetivamente*, y 29119 dice *cómo se generó la evidencia*. Ningún estándar por sí solo cubre las cuatro preguntas.

---

## 3. Instrumento de auditoría diseñado

Se diseñó un único instrumento — la **Matriz de Verificación de Controles de Seguridad** — que integra los tres marcos normativos en una sola estructura evaluable, evitando tener listas de chequeo desconectadas por norma.

### 3.1 Estructura del instrumento

| Campo | Propósito |
|---|---|
| ID de componente | Identificador único (hereda la numeración A1-F1 del análisis de seguridad, reconciliada en el catálogo consolidado DEF-01…DEF-20 usado por el ecosistema de métricas) |
| Componente / método | Ubicación exacta en el código |
| Subcaracterística ISO/IEC 25010 | Confidencialidad, Integridad o Autenticidad |
| Control ISO/IEC 27001 Anexo A | Control(es) de gestión aplicable(s) |
| Requisito OWASP ASVS | Identificador específico (ej. V4.1.3) |
| Criterio de aprobación | Enunciado verificable en presente ("el sistema no debe...", "el sistema debe...") |
| Evidencia | Prueba automatizada (unitaria/integración/caja negra) que ejecuta el criterio |
| Resultado | Cumple / No cumple |
| Severidad de la no conformidad (si aplica) | Alta / Media / Baja |

### 3.2 Naturaleza del instrumento: checklist ejecutable, no checklist estático

A diferencia de la lista de chequeo de la Primera Iteración (PAC v1.0, Sección 9.2 — aplicada manualmente sobre documentos ERS/DAS), este instrumento tiene una propiedad distinta: **cada fila es verificable de forma automática y repetible**, porque el campo "Evidencia" enlaza a una prueba de código que se ejecuta en cada `push`. Esto convierte al instrumento en autoactualizable — su resultado no depende de que un humano lo vuelva a aplicar manualmente en la próxima auditoría, sino de que la suite de pruebas siga corriendo en CI.

### 3.3 Aplicación del instrumento

El instrumento se aplicó en tres pasadas complementarias, correspondientes a los tres niveles de prueba del proyecto:

1. **Pasada de caja blanca — nivel unitario:** aplicado sobre componentes aislados (services, modelos), evidencia mediante JUnit 5 + Mockito (7 clases, 82 ejecuciones).
2. **Pasada de caja blanca — nivel integración:** aplicado sobre comportamiento que depende del contexto de Spring activo (`SecurityConfig`, CSRF, validación), evidencia mediante `@WebMvcTest`/`@SpringBootTest` (4 clases, 29 ejecuciones).
3. **Pasada de caja negra — nivel sistema:** aplicado sobre el contrato HTTP expuesto, sin conocimiento del código interno, evidencia mediante una suite JUnit 5 + `TestRestTemplate` que hace peticiones HTTP reales contra un puerto asignado dinámicamente (3 clases, 10 ejecuciones). *Corrección respecto a la descripción original de este instrumento: no se utilizó Postman/Newman — la suite de caja negra se implementó como pruebas JUnit 5 estándar para poder ejecutarse dentro del mismo `mvn test`, sin herramienta ni runner externo.*

La triangulación entre las tres pasadas es en sí misma un hallazgo de auditoría: cuando un mismo criterio de aprobación se verifica de forma independiente desde caja blanca y caja negra y ambas coinciden, la confianza en el resultado aumenta; cuando divergen, se documenta como observación en la Sección 5. En la práctica, de los 20 hallazgos del catálogo, solo 3 (DEF-14, DEF-17, DEF-20 — ver Sección 5.2) llegaron a confirmarse simultáneamente en los tres niveles; el resto quedó confirmado en uno o dos niveles según qué capa del sistema es capaz de observar cada defecto.

---

## 4. Evidencia recolectada

| Fuente de evidencia | Aporta a |
|---|---|
| Matrices de trazabilidad — pruebas unitarias e integración | Resultado por componente, nivel caja blanca |
| Colección de pruebas de caja negra (JUnit 5 + `TestRestTemplate`, 10 casos) | Resultado por endpoint, nivel caja negra |
| Reporte de cobertura JaCoCo (`target/site/jacoco/jacoco.xml`) | Alcance real de la verificación (qué proporción del código con lógica de seguridad fue efectivamente ejercitada) |
| Definición versionada del pipeline (`.github/workflows/tests.yml`) | Repetibilidad — el mismo `mvn clean test` + `jacoco:report` + `publish_metrics.py` se dispara en cada `push`/`pull_request`; no se auditó un historial de corridas pasadas en la interfaz de GitHub Actions, sino que se reprodujo localmente la secuencia completa el día de esta auditoría, con salida idéntica a la que produciría el pipeline |
| Página de métricas en Confluence (actualizada vía API en cada push) | Registro histórico consultable de la evolución del cumplimiento — publicación en vivo confirmada operativa (secrets/vars de GitHub cargados) |

**Cobertura de la evidencia:** cobertura global de línea **66.7%**, cobertura de rama **51.9%** (JaCoCo, corrida real del 2026-07-19). El detalle por componente muestra una distribución muy desigual, con dos componentes cuya evidencia ejecutable es prácticamente inexistente:

| Componente | Cobertura de línea | Lectura para esta auditoría |
|---|---|---|
| `security/CustomUserDetailsService` | 100.0% | Conclusiones de "cumple" completamente respaldadas por evidencia |
| `service/PrestamoService` | 94.6% | Bien respaldado |
| `config/SecurityConfig` | 93.1% | Bien respaldado |
| `service/UsuarioService` | 92.7% | Bien respaldado |
| `service/LibroService` | 88.1% | Bien respaldado |
| `service/AmonestacionService` | 72.7% | Razonablemente respaldado |
| `controller/Controller` | 54.2% | Parcialmente respaldado — más de un tercio del componente con mayor densidad de hallazgos (9 de 20) no está ejercitado por ninguna prueba |
| `service/ResenaService` | **6.2%** | **No respaldado.** El catálogo no le atribuye ningún hallazgo (densidad = 0), pero esa conclusión de "cumple" descansa casi enteramente en el análisis manual inicial, no en evidencia ejecutable — no existe clase de test unitario dedicada a este componente |
| `service/ComentarioResenaService` | **5.3%** | **No respaldado**, mismo motivo que `ResenaService` |

Esta auditoría señala explícitamente que **el "0 hallazgos" de `ResenaService` y `ComentarioResenaService` no debe leerse como "componente seguro confirmado"**, sino como "componente sin evidencia ejecutable suficiente para pronunciarse" — es una brecha de cobertura, no una conformidad verificada.

---

## 5. Conclusiones de cumplimiento

### 5.1 Resumen por subcaracterística

Metodología: para cada subcaracterística, un **componente** (de los 9 del catálogo `COMPONENTS`) se cuenta como "No cumple" si tiene al menos un hallazgo (`DEF-XX`) catalogado en esa subcaracterística, y como "Cumple" en caso contrario. La fila **Total** usa la unión de componentes con al menos un hallazgo en cualquier subcaracterística (no la suma de las filas, porque un mismo componente puede aparecer como no conforme en más de una subcaracterística — p. ej. `SecurityConfig` y `UsuarioService` aparecen tanto en Integridad como en Autenticidad).

| Subcaracterística | Componentes evaluados | Cumple | No cumple (no conformidad) | % de cumplimiento |
|---|---|---|---|---|
| Confidencialidad | 9 | 7 | 2 (`UsuarioService`, `PrestamoService`) | 77.8% |
| Integridad | 9 | 4 | 5 (`SecurityConfig`, `Controller`, `LibroService`, `PrestamoService`, `UsuarioService`) | 44.4% |
| Autenticidad | 9 | 6 | 3 (`Controller`, `SecurityConfig`, `UsuarioService`) | 66.7% |
| **Total (unión)** | **9** | **4** (`CustomUserDetailsService`, `AmonestacionService`, `ComentarioResenaService`, `ResenaService`) | **5** (`SecurityConfig`, `Controller`, `LibroService`, `PrestamoService`, `UsuarioService`) | **44.4%** |

A nivel de hallazgo individual (no de componente), el catálogo completo de 20 no conformidades se distribuye: Confidencialidad 4 (20.0%), Integridad 11 (55.0%), Autenticidad 7 (35.0%) — los porcentajes suman más de 100% porque 2 hallazgos (DEF-14, DEF-20) están etiquetados con dos subcaracterísticas cada uno.

### 5.2 No conformidades críticas (severidad Alta)

| Hallazgo | Componente | Control ISO 27001 | ASVS | Riesgo |
|---|---|---|---|---|
| DEF-20 — Registro persiste el rol enviado por el cliente | `service/UsuarioService` | A.8.2, A.5.15 | V4.1.5, V4.2.1 | Un cliente **no autenticado** puede autoasignarse el rol `BIBLIOTECARIO` en `POST /api/usuarios/registro` (ruta `permitAll`). Confirmado en los 3 niveles, incluida persistencia real en base de datos — **la no conformidad de mayor severidad del sistema** |
| DEF-14 — `permitAll` en `/api/libros/**` deja `DELETE`/`PUT` sin protección de rol | `config/SecurityConfig` | A.8.2 | V4.1.1 | La regla `permitAll` se declara antes que la regla específica de rol para el mismo prefijo de ruta; Spring Security aplica la primera coincidencia, dejando la protección de rol inalcanzable. Confirmado en integración y caja negra |
| DEF-17 — `verificarAmonestacion` sin `Authentication` (B7) | `controller/Controller` | A.8.2, A.5.15 | V4.1.3 | Cualquier usuario autenticado, sin importar su rol, puede verificar/aprobar amonestaciones ajenas. Confirmado en los 3 niveles |
| DEF-18 — `getTodasAmonestaciones` sin `Authentication` (B8) | `controller/Controller` | A.8.2, A.8.3 | V4.1.3 | Expone datos financieros de todos los usuarios a cualquier usuario autenticado, sin restricción de rol. Confirmado en unitaria e integración |
| DEF-15 / DEF-16 — `crearResena`/`crearComentarioResena` confían en el `usuarioId` del body | `controller/Controller` | — *(no mapeado a un control específico del Anexo A en el catálogo actual)* | V4.2.1 | Un usuario autenticado puede publicar una reseña o comentario **suplantando la identidad de otro usuario**, ya que el identificador se toma del cuerpo JSON sin cruzarlo contra la sesión autenticada |
| DEF-19 — `POST /api/login` con JSON nunca ejecuta el controller real | `config/SecurityConfig` | A.5.17, A.8.26 | V2.2.2, V4.1.1 | El filtro `formLogin` intercepta la petición antes que el `DispatcherServlet`; el flujo de login realmente expuesto **no es el que fue revisado ni probado unitariamente**, lo que invalida cualquier conclusión de cumplimiento basada solo en la lectura del código de `Controller.loginUsuario` |

### 5.3 Conclusión general de cumplimiento

El sistema presenta un **cumplimiento parcial** respecto a los marcos evaluados: a nivel de componente, 4 de 9 (44.4%) no tienen ninguna no conformidad catalogada, mientras que los 5 restantes concentran las 20 no conformidades del catálogo. La subcaracterística **Confidencialidad muestra la mayor madurez relativa** (77.8% de componentes conformes), mientras que **Integridad es la más comprometida** (44.4%), arrastrada principalmente por un patrón sistémico único — la ausencia total de Bean Validation en 5 puntos de entrada distintos (DEF-06 a DEF-10) — más que por defectos aislados.

Al observar las 6 no conformidades de severidad Alta (Sección 5.2), emerge un patrón de causa raíz común, no fallas independientes: **cuatro de las seis (DEF-20, DEF-15, DEF-16, y en menor medida DEF-17/DEF-18 por ausencia total de verificación) comparten la misma falla conceptual — el sistema confía en datos suministrados por el cliente (un campo `rol` en el body, un `usuarioId` en el body, la simple ausencia de chequeo) en vez de derivar la identidad y el privilegio exclusivamente de la sesión autenticada (`Authentication`)**. Las dos restantes (DEF-14, DEF-19) comparten otro patrón: **la configuración declarada en `SecurityConfig` no se comporta como el código sugiere** — una por orden de reglas, la otra porque un filtro de framework intercepta la petición antes que el código de aplicación. Esta segunda familia es particularmente relevante para la auditoría porque **ninguna revisión de código por sí sola la habría detectado** sin ejecutar la cadena de filtros real — es evidencia directa del valor del enfoque de tres niveles frente a una auditoría puramente estática.

Vale la pena señalar, como hallazgo de auditoría de proceso y no solo de producto: el hecho de que el equipo haya diseñado deliberadamente las pruebas para quedar en rojo ante cada no conformidad detectada —en vez de ajustar las aserciones para forzar un resultado verde— es en sí mismo evidencia de la integridad del proceso de auditoría. Un instrumento cuyo resultado nunca puede salir desfavorable no es un instrumento de auditoría, es una formalidad.

---

## 6. Recomendaciones

Ordenadas por prioridad, según severidad de la no conformidad que atienden:

### 6.1 Correcciones de código (severidad Alta — atender primero)

1. **DEF-20:** forzar `rol = "USUARIO"` en `UsuarioService.registrarUsuario` de forma incondicional para el alta pública, sin importar el valor recibido del cliente en el campo `rol`.
2. **DEF-14:** reordenar las reglas de `SecurityConfig` para que la regla específica de rol sobre `/api/libros/isbn/**` (`DELETE`/`PUT`) se declare **antes** que el `permitAll` general de `/api/libros/**`, o acotar este último explícitamente a `GET`.
3. **DEF-17 / DEF-18:** agregar el parámetro `Authentication` y validar `rol == "BIBLIOTECARIO"` en `verificarAmonestacion` y `getTodasAmonestaciones`, siguiendo el mismo patrón ya usado en `eliminarAmonestacion`.
4. **DEF-15 / DEF-16:** derivar el `usuarioId` de la sesión autenticada (`Authentication`) en `crearResena` y `crearComentarioResena`, en vez de aceptar el valor enviado en el cuerpo de la petición.
5. **DEF-19:** decidir explícitamente el contrato real de `/api/login` (JSON vs. formulario) y alinear `Controller.loginUsuario` con el filtro `formLogin` realmente activo, o eliminar el código muerto y documentar el flujo de formulario como el oficial.

### 6.2 Mejoras de proceso

- Incorporar Bean Validation (`@Valid`, `@NotBlank`, `@Size`, `@Email`) en los 5 `@RequestBody`/`@RequestPart` que actualmente carecen de ella (DEF-06 a DEF-10), para que las pruebas de integración de validación tengan un mecanismo real que verificar.
- Establecer una política explícita de fortaleza de contraseña (longitud mínima de 12 caracteres, verificada tanto en registro como en cambio de contraseña — DEF-03/DEF-04) — actualmente ausente en ambos flujos.
- Cerrar la brecha de cobertura de `ResenaService` (6.2%) y `ComentarioResenaService` (5.3%) con clases de test unitario dedicadas, para que la conclusión de "0 hallazgos" en esos componentes deje de depender únicamente del análisis manual inicial.
- Evaluar la incorporación de un analizador estático continuo (SonarCloud, evaluado y pospuesto en esta iteración — ver PAC, Sección 10) para complementar la cobertura dinámica con detección de patrones de código inseguro que las pruebas no cubren por diseño.

### 6.3 Mejoras de gobernanza de la auditoría

- Automatizar la generación de este informe de auditoría a partir de la Matriz de Verificación (Sección 3), del mismo modo que ya se automatizó la publicación de métricas hacia Confluence, para que las conclusiones de cumplimiento se actualicen en cada push en vez de requerir una revisión manual por iteración.
- Definir un umbral mínimo de cobertura JaCoCo como criterio de salida obligatorio para futuras iteraciones (actualmente documentado como objetivo en el PAC pero sin umbral numérico cerrado) — con especial atención a exigir un mínimo por componente, no solo un promedio global, dado que el 66.7% global de esta ronda oculta componentes por debajo del 10%.

---

## 7. Limitaciones de esta auditoría

- No incluye pruebas de penetración manual ni análisis de infraestructura de despliegue (servidor, red, gestión de secretos en producción).
- Las no conformidades de severidad Alta que dependen de condiciones de concurrencia real (ej. operaciones TOCTOU sobre inventario) fueron documentadas en el análisis pero no verificadas bajo carga real — quedan señaladas como riesgo, no como no conformidad confirmada con evidencia ejecutable.
- La conclusión de "cumple" sobre `ResenaService` y `ComentarioResenaService` (Sección 4) es la de menor confianza de todo este informe, por ausencia casi total de evidencia ejecutable dedicada.
- El instrumento evalúa el código en el commit `7a9ace9` (rama `testing`); cualquier cambio posterior requiere una nueva pasada.

---

*Este informe, junto con el Plan de Pruebas y las matrices de trazabilidad, constituye el conjunto completo de documentación de aseguramiento de la calidad exigido para la defensa de esta iteración.*
