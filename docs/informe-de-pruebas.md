# Informe de Pruebas — Sistema de Gestión Bibliotecaria

> Artefacto ISO/IEC/IEEE 29119-3 distinto y complementario a `docs/plan-de-pruebas.md` (el Plan
> de Pruebas). Donde el Plan describe *qué se va a probar y con qué criterios*, este Informe
> reporta *cómo se probó, con qué patrones, qué evidencia de ejecución existe y qué reflexión deja
> la casuística elegida*. Todos los datos (números, nombres de clase, salidas de comandos) son
> reales, tomados de la última corrida de `mvn clean test`, `mvn jacoco:report` y
> `python3 scripts/publish_metrics.py --dry-run` sobre el repositorio — no son estimaciones.

---

## 1. Pruebas unitarias

### 1.1 Patrones utilizados por el dueño del sistema

Autor: **Miguel Pinto**, dueño/responsable de las 7 clases unitarias
(`UsuarioServiceTest`, `LibroServiceTest`, `PrestamoServiceTest`, `AmonestacionServiceTest`,
`CustomUserDetailsServiceTest`, `ControllerAuthorizationTest`, `ModelSerializationTest`).

- **Patrón AAA (Arrange-Act-Assert)** en los 63 métodos de test, con los tres bloques separados y
  comentados explícitamente — decisión deliberada para que cada test sea legible como
  especificación ejecutable sin tener que leer el código de producción en paralelo.
- **Dobles de prueba**, elegidos caso por caso según qué se necesitaba observar:
  - **Stub** (`Mockito.when(...)`) cuando solo hace falta una respuesta fija de un repositorio
    (p. ej. `usuarioRepository.findByCorreo(...)` devolviendo un `Usuario` prearmado).
  - **Mock + `verify()`/`never()`** cuando lo que importa es si una interacción **ocurrió o no**
    — p. ej. `verify(usuarioRepository, never()).save(any())` para probar que una contraseña
    débil nunca llega a persistirse.
  - **`ArgumentCaptor`** cuando hace falta **interceptar el objeto exacto** que se habría
    persistido, para inspeccionar un campo después del hecho (el hash guardado, el rol asignado,
    la cantidad de stock resultante). Usado en 14 de los 63 métodos.
  - **Instanciación por reflexión pura de Java** (`java.lang.reflect.Field`/`Method`) en
    `ControllerAuthorizationTest`: el `Controller` se crea con `new Controller()` y sus
    dependencias `@Autowired` se inyectan a mano, sin contenedor de Spring. Para los defectos
    B7/B8/C4/C5 (ausencia de parámetro `Authentication`), se usa además introspección de
    `Method.getParameterTypes()` — porque el defecto **es la ausencia de una rama de código**, no
    hay nada que estubear ni verificar con un mock.
- **Aislamiento total:** cero dependencias externas — ni base de datos, ni filtros HTTP, ni
  contenedor de Spring. La única excepción deliberada es instanciar un `BCryptPasswordEncoder`
  real (no mockeado) cuando el propio hash criptográfico es lo que se está verificando (DEF-01,
  DEF-03, DEF-04) — mockear el encoder habría ocultado precisamente el comportamiento bajo prueba.

### 1.2 Justificación de las pruebas abordadas, dada la característica evaluada

| Subcaracterística ISO 25010 | Por qué el nivel unitario es el correcto para esa característica | Ejemplo real |
|---|---|---|
| **Confidencialidad** | Verificar qué campos *salen* de un objeto al serializarlo, o qué se hashea antes de persistir, es una propiedad del **objeto y del mapeador JSON**, no del transporte HTTP — se puede (y debe) probar sin levantar un servidor. Usar `ObjectMapper` puro (sin Spring) prueba exactamente eso, aislado de cualquier configuración web. | `ModelSerializationTest` serializa un `Usuario` con `ObjectMapper` puro y confirma (en rojo) que el JSON expone `contrasena` (DEF-01) |
| **Integridad** | Las reglas de negocio que corrompen datos (stock negativo, ISBN inválido) están **dentro de la lógica del servicio**, no en la capa web — probarlas a nivel unitario aísla la causa exacta sin que un filtro HTTP o una base de datos real puedan enmascarar o distorsionar el resultado. | `PrestamoServiceTest.renovarPrestamo_DEFECTO_decrementaStockSinVerificarDisponibilidad` captura el `Libro` guardado y confirma que `cantidad` queda en `-1` (DEF-13) |
| **Autenticidad** | La autorización **escrita a mano dentro del código** (comparaciones `rol.equals("BIBLIOTECARIO")`, o la ausencia de un parámetro `Authentication`) es, por definición, código Java puro — no requiere el `SecurityFilterChain` real para observarse; de hecho, probarla sin Spring aísla la causa raíz (código) de su síntoma (qué deja pasar el filtro), que es exactamente lo que la ronda de integración prueba después, por separado. | `ControllerAuthorizationTest` usa reflexión para confirmar que `verificarAmonestacion(Integer)` no declara un parámetro `Authentication` (DEF-17/B7) |

La elección de casos dentro de cada característica siguió partición de equivalencia (roles
válidos/inválidos) y análisis de valores límite (contraseñas de longitud 0/1/3/10 caracteres
contra el mínimo de 12; cantidades `0`/`-1`/`-100`; el caso adversarial del ISBN negativo, donde
el signo `-` ocupa el lugar de un dígito y burla `length()==13`) — ver técnicas detalladas en la
sección 6.

---

## 2. Pruebas de integración

### 2.1 Aspectos de la simulación considerados por el DevOps

- **Identidad simulada, no autenticada de verdad:** `@WithMockUser(authorities = "...")` y
  `@WithAnonymousUser` inyectan directamente un `Authentication` en el `SecurityContextHolder`
  **antes** de que la petición entre a la cadena de filtros — evita depender de un
  `UserDetailsService`/`PasswordEncoder` real para simular un rol, manteniendo la prueba rápida.
  Para el caso donde hace falta un correo específico en la sesión (no solo un rol genérico), se
  usa `SecurityMockMvcRequestPostProcessors.user("correo").authorities(...)` en su lugar.
- **`@Import(SecurityConfig.class)` explícito:** un slice `@WebMvcTest` no escanea clases
  `@Configuration` arbitrarias (solo `@Controller`/`@ControllerAdvice`/convertidores); sin este
  import, Spring Boot generaría una configuración de seguridad por defecto (todo autenticado,
  usuario generado aleatorio) que **no** reflejaría las reglas reales de `SecurityConfig` —
  invalidando cualquier conclusión sobre `permitAll`/`hasAuthority`.
- **Servicios mockeados vs. base de datos real, decidido caso por caso:** 3 de las 4 clases
  (`SecurityConfigAccessRulesIntegrationTest`, `CsrfProtectionIntegrationTest`,
  `BeanValidationRequestBodyIntegrationTest`) mockean los 6 servicios con `@MockitoBean` porque
  solo necesitan observar el comportamiento del filtro HTTP o del binding de Spring MVC — no
  cargan `DataSource` en absoluto. La cuarta (`PrivilegeEscalationEndToEndIntegrationTest`) usa
  `@SpringBootTest` + H2 real deliberadamente, porque el objetivo específico de esa clase es
  demostrar el impacto de un defecto **de principio a fin**, no solo que un mock lo permite.
- **CSRF simulado explícitamente en ambos sentidos:** `.with(csrf())` para el caso de control
  positivo, y su **ausencia deliberada** (sin ese post-processor) para el caso que documenta
  DEF-05 — la ausencia es en sí misma el caso de prueba, no un descuido.

### 2.2 Evidencia de haber realizado el ejercicio de IC/DC

El pipeline versionado en `.github/workflows/tests.yml` ejecuta, en cada `push` a cualquier rama y
en cada `pull_request` hacia `main`: checkout → JDK 21 → `mvn -B clean test` → `mvn -B
jacoco:report` (paso separado, `if: always()`) → publicación de resultados JUnit
(`dorny/test-reporter`) → subida del reporte de cobertura como artefacto → `scripts/publish_metrics.py` hacia Jira/Confluence. Esta no es una descripción teórica: es exactamente la
secuencia que se ejecutó localmente para producir este informe, con salida real capturada:

```
$ mvn clean test
[INFO] Tests run: 122, Failures: 42, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE   ← esperado: 42 son tests DEFECTO intencionales, no errores de entorno

$ mvn jacoco:report
[INFO] Analyzed bundle 'gestion_bibliotecaria' with 20 classes
[INFO] BUILD SUCCESS

$ python3 scripts/publish_metrics.py --dry-run
Overall: 122 tests, 80 passed, 42 failed, 0 errors, 0 skipped, 65.6% pass rate
[Pruebas unitarias] {'tests': 82, 'passed': 62, 'failed': 20} pass_rate=75.6%
[Pruebas de integracion] {'tests': 29, 'passed': 11, 'failed': 18} pass_rate=37.9%
[Pruebas de caja negra] {'tests': 10, 'passed': 6, 'failed': 4} pass_rate=60.0%
Coverage overall: line=66.7% branch=51.9%
Security-tagged tests found in source: 79
Security controls summary: {'total': 99, 'failing': 26, 'rate': 73.7}
Marker check: ... idempotent=True, preexisting_content_preserved=True
```

Esta última línea (`idempotent=True`) es la prueba de que la lógica de reemplazo del fragmento
marcado en la página de Confluence (`<!-- CI-METRICS-START/END -->`) no duplica contenido en
corridas sucesivas — condición necesaria para que el ejercicio de IC/DC pueda repetirse en cada
`push` sin degradar la página destino. La publicación **en vivo** hacia Confluence/Jira quedó
confirmada operativa una vez cargados los 5 secrets/vars de GitHub requeridos (`JIRA_BASE_URL`,
`JIRA_EMAIL`, `JIRA_API_TOKEN`, `JIRA_PROJECT_KEY`, `CONFLUENCE_PAGE_ID`), según lo registrado en
la sección 7 del Plan de Pruebas.

---

## 3. Pruebas de caja negra

### 3.1 Características evaluadas

`AuthFunctionalTest` y `LibroFunctionalTest` evalúan **funcionalidad** (flujo feliz de
registro/login/lectura pública), sirviendo de línea base de que el pipeline de caja negra
funciona de extremo a extremo. `AccessControlSecurityTest` evalúa específicamente
**Autenticidad** e **Integridad**: 4 casos que confirman, sobre HTTP real, 3 de los 20 hallazgos
del catálogo (DEF-14, DEF-17, DEF-20) — los únicos tres que llegaron a validarse en los **tres**
niveles de prueba a la vez.

### 3.2 Estrategia de pruebas

Caja negra estricta: `@SpringBootTest(webEnvironment = RANDOM_PORT)` levanta la aplicación
completa en un puerto real; las pruebas usan `TestRestTemplate` para hacer peticiones HTTP reales
contra ese puerto — sin `MockMvc`, sin invocar ningún `Service`/`Repository` directamente. La
única clase de producción importada es el modelo `Usuario`, y únicamente para deserializar el
cuerpo JSON de una respuesta (`GET /api/usuarios/me`), nunca para invocar lógica. Esto es
equivalente en espíritu a una colección de Postman/Newman, pero implementado como pruebas JUnit 5
estándar para poder correr dentro del mismo `mvn test` sin herramienta externa.

### 3.3 Ambiente de pruebas considerado

Mismo ambiente que integración: H2 en memoria (`jdbc:h2:mem:testdb`, `MODE=MySQL`,
`ddl-auto=create-drop`) activado vía `@ActiveProfiles("test")` sobre
`src/test/resources/application-test.properties`, con el `SecurityFilterChain` real de
`SecurityConfig` (sin `@Import` explícito porque aquí sí se carga el contexto completo de la
aplicación, no un slice). La diferencia frente a integración es que aquí la aplicación corre como
un proceso HTTP real con puerto asignado dinámicamente, no como un `MockMvc` en memoria.

### 3.4 Preparación de los datos de prueba

Cada método registra sus propios usuarios vía HTTP real (`POST /api/usuarios/registro`) con
**correos globalmente únicos por método** (`nuevo-funcional@test.com`,
`attacker-mass-assignment@test.com`, `usuario-comun-idor@test.com`, etc.). Esta convención es
obligatoria, no estilística: las pruebas de caja negra comparten un único contexto de Spring / una
única instancia H2 por corrida de JVM (context caching de Spring Test); una colisión de correo
haría fallar el registro de un test con `"Correo ya registrado."` cuando ese test no lo espera.
No hay `@AfterEach` de limpieza — aceptable porque H2 se recrea en cada corrida de JVM completa.

### 3.5 Procesos de automatización y ciclos de ejecución

Ningún paso adicional: al ser clases JUnit 5 estándar bajo `src/test/java/com/biblioteca/blackbox`,
Surefire las descubre y ejecuta en el mismo `mvn test` que las otras 11 clases — mismo ciclo,
mismo comando, mismo pipeline de CI descrito en la sección 2.2. No existe un runner ni una
colección separada que mantener o disparar aparte.

### 3.6 Integración con el ecosistema de métricas

`scripts/publish_metrics.py` categoriza automáticamente cualquier clase bajo el paquete
`com.biblioteca.blackbox.*` como `"caja_negra"` (por convención de paquete, sin lista explícita a
mantener a mano, a diferencia de las clases unitarias). Esto habilita, específicamente para esta
naturaleza de prueba, dos métricas que no tienen equivalente en los otros niveles:

- Una fila propia **"Pruebas de caja negra"** en la tabla de categorías publicada en Confluence,
  con su propio `pass rate` (60.0% en la última corrida) — separado del de unitarias/integración,
  precisamente porque una caída aquí significa un defecto **confirmado sobre el sistema en
  ejecución real**, con la mayor severidad interpretativa de las tres categorías.
- Su contribución a la **tasa de confirmación de controles de seguridad** (sección 4 del Plan):
  los 4 métodos de `AccessControlSecurityTest` están etiquetados con comentarios
  ISO25010/ISO27001/ASVS igual que cualquier test unitario o de integración, así que se cuentan
  dentro de los 99 controles evaluados / 26 fallando — la única diferencia es que, al no haber
  mocks de por medio, un fallo aquí es la evidencia más directa posible de que el hallazgo es
  explotable, no solo teóricamente posible.

---

## 4. Especificaciones de prueba

Una fila por hallazgo del catálogo (`DEF-01`…`DEF-20`), con precondición, entrada, resultado
esperado (el comportamiento *seguro*, no el actual), nivel(es) donde se verifica y resultado real
de la última corrida.

| ID | Precondición | Entrada | Resultado esperado (seguro) | Nivel(es) | Resultado real |
|---|---|---|---|---|---|
| DEF-01 | `Usuario` con `contrasena` seteada | Serializar con `ObjectMapper` | JSON sin campo `contrasena` | Unitaria | 🔴 |
| DEF-02 | `Prestamo` con `Usuario` anidado | Serializar `Prestamo` | JSON sin `contrasena` del usuario anidado | Unitaria | 🔴 |
| DEF-03 | Ninguna | Registro con contraseña `""`/`"1"`/`"abc"`/10 caracteres | Rechazo, sin persistir | Unitaria | 🔴 (×4) |
| DEF-04 | Usuario existente | Cambio de contraseña a valor débil | Rechazo, sin persistir | Unitaria | 🔴 (×3) |
| DEF-05 | Usuario autenticado (sesión) | `POST /api/resenas` sin token CSRF | `403 Forbidden` | Integración | 🔴 |
| DEF-06 | Ninguna | `POST /api/usuarios/registro` con nombre/correo/contraseña inválidos | `400 Bad Request` | Integración | 🔴 (×4) |
| DEF-07 | Rol `BIBLIOTECARIO` | `PUT /api/libros/isbn/{isbn}` con título vacío / cantidad negativa | `400 Bad Request` | Integración | 🔴 (×2) |
| DEF-08 | Rol autenticado | `POST /api/resenas` con `libroId` nulo / texto vacío | `400 Bad Request` | Integración | 🔴 (×2) |
| DEF-09 | Rol autenticado | `POST /api/comentarios-resena` con `resenaId` nulo / texto vacío | `400 Bad Request` | Integración | 🔴 (×2) |
| DEF-10 | Rol `BIBLIOTECARIO` | `POST /api/prestar` con correo vacío / ISBN nulo | `400 Bad Request` | Integración | 🔴 (×2) |
| DEF-11 | Rol `BIBLIOTECARIO` | ISBN negativo de 12 dígitos de magnitud | Rechazo, sin persistir | Unitaria | 🔴 |
| DEF-12 | Rol `BIBLIOTECARIO`, libro existente | `cantidad: -1`/`-50` vía `PUT` | Rechazo, sin persistir | Unitaria + Integración | 🔴 |
| DEF-13 | Préstamo finalizado, libro con `cantidad=0` | `renovarPrestamo` | `cantidad` del libro nunca negativa | Unitaria | 🔴 |
| DEF-14 | Sin rol `BIBLIOTECARIO` (o sin autenticar) | `DELETE`/`PUT /api/libros/isbn/{isbn}` | `401`/`403` | Integración + Caja negra | 🔴 (los 2 niveles) |
| DEF-15 | — | Firma de `crearResena(ResenaRequest)` | Debería declarar `Authentication` | Unitaria | 🔴 |
| DEF-16 | — | Firma de `crearComentarioResena(...)` | Debería declarar `Authentication` | Unitaria | 🔴 |
| DEF-17 | Rol `USUARIO` (no `BIBLIOTECARIO`) | `PUT /api/amonestaciones-usuario/verificar/{id}` | `403 Forbidden` | Unitaria + Integración + Caja negra | 🔴 (los 3 niveles) |
| DEF-18 | Rol `USUARIO` | `GET /api/amonestaciones-usuario/todas` | `403 Forbidden` | Unitaria + Integración | 🔴 |
| DEF-19 | Sin autenticar | `POST /api/login` con cuerpo JSON `{correo,contrasena}` | Debería invocar `UsuarioService.autenticarYObtenerUsuario` | Integración | 🔴 |
| DEF-20 | Sin autenticar | `POST /api/usuarios/registro` con `"rol":"BIBLIOTECARIO"` | Persistir siempre como `"USUARIO"` | Unitaria + Integración + Caja negra | 🔴 (los 3 niveles) |

---

## 5. Reflexiones sobre la casuística

- **Solo 3 de 20 hallazgos (DEF-14, DEF-17, DEF-20) llegaron a confirmarse en los tres niveles a
  la vez.** No es una debilidad del plan: cada nivel existe para observar algo que los otros
  estructuralmente no pueden ver (código interno vs. filtro HTTP vs. contrato externo). Que
  precisamente estos tres sean los que atraviesan los tres niveles refuerza que son los hallazgos
  de mayor severidad real del proyecto — escalada de privilegios en registro (DEF-20) y ausencia
  total de control de rol sobre operaciones de bibliotecario (DEF-14, DEF-17).
- **El caso adversarial del ISBN negativo (DEF-11)** ilustra por qué el análisis de valores
  límite necesita pensar en *representación*, no solo en magnitud: el límite relevante no es
  "¿el ISBN es razonable?" sino "¿cuántos caracteres produce `String.valueOf(isbn)`?" — un
  atacante no necesita un ISBN grande, necesita uno cuyo signo negativo compense exactamente la
  pérdida de un dígito frente al umbral de 13 caracteres.
- **DEF-19 (login JSON muerto) no se descubrió por diseño previo, sino explorando el nivel de
  integración.** Ningún hallazgo de la Fase 1 de análisis lo anticipó — apareció al construir
  `SecurityConfigAccessRulesIntegrationTest` y notar que `formLogin().loginProcessingUrl(...)`
  intercepta la petición antes que el `DispatcherServlet`. Esto es evidencia de que la ronda de
  integración no es redundante con la unitaria: encontró un defecto que el análisis de código por
  sí solo no había capturado.
- **`ResenaService` (6.2%) y `ComentarioResenaService` (5.3%) son un vacío de cobertura real, no
  un hallazgo de seguridad catalogado.** Ningún DEF-XX los señala directamente porque no se les
  dedicó una clase de test unitario — quedan probados solo indirectamente, como mocks, en
  `ControllerAuthorizationTest` y en la ronda de integración. Es la brecha más clara para una
  ronda futura.
- **La tasa de confirmación de controles (73.7% sobre 99 controles etiquetados) es más informativa
  que el pass rate global (65.6%)** porque el denominador global incluye tests de flujo feliz
  (`AuthFunctionalTest`, `LibroFunctionalTest`) que no verifican ningún control de seguridad
  específico — mezclarlos diluye la lectura de "cuántos controles de seguridad están realmente
  implementados". El 73.7% es la cifra que debería citarse cuando se hable de postura de
  seguridad del sistema, no el 65.6%.

---

## 6. Técnicas de prueba aplicadas (ISO/IEC/IEEE 29119-4) — declaración explícita

| Técnica (29119-4) | Nivel(es) donde se aplica | Casos representativos |
|---|---|---|
| **Partición de equivalencia** | Unitaria, Integración | Roles válidos vs. inválidos (`USUARIO`/`BIBLIOTECARIO`/`ADMIN`/`""`); payloads JSON válidos vs. inválidos en los 5 DTOs de `BeanValidationRequestBodyIntegrationTest` |
| **Análisis de valores límite** | Unitaria | Longitud de contraseña (0/1/3/10 vs. mínimo 12 caracteres); cantidades de libro (`0`, `-1`, `-100`); ISBN alrededor del umbral de 13 caracteres, incluido el caso adversarial del signo negativo (DEF-11) |
| **Tablas de decisión** | Integración | Reglas de `SecurityConfig` (`permitAll` / `authenticated()` / `hasAuthority("BIBLIOTECARIO")`) evaluadas como combinación rol × ruta × método HTTP en `SecurityConfigAccessRulesIntegrationTest` |
| **Pruebas basadas en defectos** | Unitaria, Integración, Caja negra | Los 20 hallazgos del catálogo, cada uno convertido en al menos un caso de prueba que pinnea el defecto conocido como regresión ejecutable — técnica central de las tres rondas |
| **Pruebas de especificación / caja negra** | Caja negra | Los 10 casos de `blackbox.*` se derivan exclusivamente del contrato HTTP documentado (rutas, métodos, códigos de estado esperados), sin inspeccionar el código fuente de `Controller`/`Service` |

---

*Este Informe se apoya en, y no duplica, el detalle exhaustivo test-por-test disponible en
`docs/matriz-trazabilidad-pruebas-unitarias.md`, `docs/reporte-pruebas-seguridad.md`,
`docs/matriz-trazabilidad-pruebas-integracion.md` y `docs/reporte-pruebas-seguridad-integracion.md`,
y en los criterios de entrada/salida y riesgos ya documentados en `docs/plan-de-pruebas.md`.*
