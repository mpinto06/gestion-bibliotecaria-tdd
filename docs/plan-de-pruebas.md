# Plan de Pruebas — Sistema de Gestión Bibliotecaria

> Estructura alineada a ISO/IEC/IEEE 29119-3 (contenido de documentación de pruebas) y técnicas de
> ISO/IEC/IEEE 29119-4. Todos los números de este documento se obtuvieron ejecutando `mvn test`,
> `mvn jacoco:report` y `python3 scripts/publish_metrics.py --dry-run` directamente sobre el
> repositorio el día de emisión — no son estimaciones.

---

## 1. Identificador único del plan

| Campo | Valor |
|---|---|
| Identificador | `PP-BIBLIOTECA-2026-001` |
| Proyecto | `gestion_bibliotecaria` (Maven: `58_1:biblioteca-backend:0.0.1-SNAPSHOT`) |
| Repositorio | `gestion-bibliotecaria-tdd`, rama `testing` |
| Versión del documento | 1.0 |
| Fecha de emisión | 2026-07-19 |
| Fecha de entrega | 2026-07-19 |
| Autor / responsable de consolidación | Miguel Pinto |

---

## 2. Introducción y alcance

Este plan cubre la verificación de seguridad y funcionalidad del backend Spring Boot del sistema
de Gestión Bibliotecaria, en **tres niveles de prueba** que se complementan sin solaparse:

1. **Pruebas unitarias** (JUnit 5 + Mockito, sin contexto de Spring): verifican lógica de negocio
   y autorización escrita a mano dentro de servicios y del controller, en aislamiento total.
2. **Pruebas de integración** (`@WebMvcTest` / `@SpringBootTest` + MockMvc): verifican lo que solo
   el contexto real de Spring puede exponer — reglas de `SecurityConfig` a nivel de filtro HTTP,
   protección CSRF, y Bean Validation (`@Valid`) sobre los `@RequestBody`.
3. **Pruebas de caja negra** (JUnit 5 + `TestRestTemplate` sobre un puerto HTTP real, sin
   `MockMvc` ni llamadas directas a servicios): verifican el contrato HTTP expuesto por la
   aplicación en ejecución, exactamente como lo vería un cliente externo, sin conocimiento del
   código interno.

El objetivo declarado no es alcanzar el 100% de pruebas en verde: varias pruebas se escriben
deliberadamente para **fallar hoy** y así documentar, de forma ejecutable y trazable, defectos de
seguridad reales encontrados en el análisis previo del proyecto (catálogo de 20 hallazgos,
sección 4). Una prueba en rojo con el comentario `DEFECTO DETECTADO` es un resultado válido y
esperado de esta ronda, no un fallo del plan.

El alcance incluye, además, el **ecosistema de métricas automatizado**: en cada `push`, GitHub
Actions ejecuta la suite completa, genera cobertura con JaCoCo, y publica un resumen (pruebas por
nivel, cobertura por componente, tasa de confirmación de controles de seguridad, densidad y
distribución de hallazgos) hacia una página de Confluence, además de abrir un issue de Jira
agregado cuando hay fallos.

---

## 3. Elementos de prueba

| Componente | Capa | Nivel(es) de prueba que lo cubren |
|---|---|---|
| `Controller` | Presentación / REST | Unitaria (`ControllerAuthorizationTest`) · Integración (`SecurityConfigAccessRulesIntegrationTest`, `CsrfProtectionIntegrationTest`, `BeanValidationRequestBodyIntegrationTest`) · Caja negra (las 3 clases) |
| `UsuarioService` | Negocio | Unitaria (`UsuarioServiceTest`) · Integración (`PrivilegeEscalationEndToEndIntegrationTest`) · Caja negra (`AuthFunctionalTest`, `AccessControlSecurityTest`) |
| `LibroService` | Negocio | Unitaria (`LibroServiceTest`) · Caja negra (`LibroFunctionalTest`, `AccessControlSecurityTest`) |
| `PrestamoService` | Negocio | Unitaria (`PrestamoServiceTest`) |
| `AmonestacionService` | Negocio | Unitaria (`AmonestacionServiceTest`) · Caja negra (`AccessControlSecurityTest`) |
| `ResenaService` | Negocio | Solo indirectamente, mockeado (`ControllerAuthorizationTest`, `BeanValidationRequestBodyIntegrationTest`, `SecurityConfigAccessRulesIntegrationTest`, `CsrfProtectionIntegrationTest`) — **sin clase de test unitario dedicada** |
| `ComentarioResenaService` | Negocio | Solo indirectamente, mockeado (mismos tests que `ResenaService`) — **sin clase de test unitario dedicada** |
| `SecurityConfig` | Seguridad / configuración | Integración (`SecurityConfigAccessRulesIntegrationTest`, `CsrfProtectionIntegrationTest`) · Caja negra (`AccessControlSecurityTest`) |
| `CustomUserDetailsService` | Seguridad | Unitaria (`CustomUserDetailsServiceTest`) |
| `Usuario`, `Prestamo`, `Amonestacion` (modelos) | Dominio / persistencia | Unitaria — serialización JSON (`ModelSerializationTest`) |
| `ResenaRequest`, `ComentarioResenaRequest`, `PrestamoRequest`, `Libro` (como `@RequestBody`) | DTO / contrato HTTP | Integración — Bean Validation (`BeanValidationRequestBodyIntegrationTest`) |
| `UsuarioRepository`, `LibroRepository`, `PrestamoRepository`, `ResenaRepository`, `ComentarioResenaRepository`, `AmonestacionRepository` | Persistencia | No probados directamente (ver sección 5) |

La cobertura real por componente (JaCoCo, línea) confirma esta tabla: `ResenaService` (6.2%) y
`ComentarioResenaService` (5.3%) son, con diferencia, los componentes menos probados del proyecto,
exactamente los dos sin clase de test unitario dedicada.

---

## 4. Características a probar (por subcaracterística ISO/IEC 25010)

Catálogo real de 20 hallazgos (`SECURITY_FINDINGS` en `scripts/publish_metrics.py`), evaluados
sobre 9 componentes (`COMPONENTS`). Se numeran `DEF-01`…`DEF-20` para trazabilidad dentro de este
plan.

### 4.1 Confidencialidad (4 hallazgos — 20.0% del catálogo)

| ID | Hallazgo | Componente |
|---|---|---|
| DEF-01 | `Usuario.contrasena` serializada en JSON | `service/UsuarioService` |
| DEF-02 | `Prestamo → Usuario.contrasena` serializada | `service/PrestamoService` |
| DEF-03 | Sin validación de fortaleza de contraseña (registro) | `service/UsuarioService` |
| DEF-04 | Sin validación de fortaleza de contraseña (cambio) | `service/UsuarioService` |

### 4.2 Integridad (11 hallazgos — 55.0% del catálogo)

| ID | Hallazgo | Componente |
|---|---|---|
| DEF-05 | CSRF deshabilitado globalmente | `config/SecurityConfig` |
| DEF-06 | Sin Bean Validation — `Usuario` (registro) | `controller/Controller` |
| DEF-07 | Sin Bean Validation — `Libro` | `controller/Controller` |
| DEF-08 | Sin Bean Validation — `ResenaRequest` | `controller/Controller` |
| DEF-09 | Sin Bean Validation — `ComentarioResenaRequest` | `controller/Controller` |
| DEF-10 | Sin Bean Validation — `PrestamoRequest` | `controller/Controller` |
| DEF-11 | Validación de ISBN rota con negativos (`length()==13`) | `service/LibroService` |
| DEF-12 | `actualizarLibroPorIsbn` copia `cantidad` sin validar | `service/LibroService` |
| DEF-13 | `renovarPrestamo` decrementa stock sin chequear disponibilidad | `service/PrestamoService` |
| DEF-14 | `permitAll` en `/api/libros/**` habilita `DELETE`/`PUT` sin autenticar *(también Autenticidad)* | `config/SecurityConfig` |
| DEF-20 | Registro persiste rol malicioso enviado por el cliente — A3 *(también Autenticidad)* | `service/UsuarioService` |

### 4.3 Autenticidad (7 hallazgos — 35.0% del catálogo)

| ID | Hallazgo | Componente |
|---|---|---|
| DEF-14 | *(compartido con Integridad, ver arriba)* | `config/SecurityConfig` |
| DEF-15 | `crearResena` confía en `usuarioId` del body sin verificar identidad | `controller/Controller` |
| DEF-16 | `crearComentarioResena`, mismo patrón | `controller/Controller` |
| DEF-17 | `verificarAmonestacion` sin `Authentication` (B7) | `controller/Controller` |
| DEF-18 | `getTodasAmonestaciones` sin `Authentication` (B8) | `controller/Controller` |
| DEF-19 | Login con JSON nunca llega al controller real | `config/SecurityConfig` |
| DEF-20 | *(compartido con Integridad, ver arriba)* | `service/UsuarioService` |

Los porcentajes suman más de 100% porque DEF-14 y DEF-20 están etiquetados con dos
subcaracterísticas cada uno (22 etiquetas sobre 20 hallazgos).

**Densidad de hallazgos por componente** (hallazgos ÷ 1, ya que no hay LOC como denominador
natural): `controller/Controller` = 9, `service/UsuarioService` = 4, `config/SecurityConfig` = 3,
`service/LibroService` = 2, `service/PrestamoService` = 2, `service/ResenaService` = 0,
`service/ComentarioResenaService` = 0, `service/AmonestacionService` = 0,
`security/CustomUserDetailsService` = 0. **Densidad global = 20 ÷ 9 componentes = 2.22**.

---

## 5. Características que NO se probarán

| Elemento excluido | Justificación |
|---|---|
| Frontend Vue (`gestion_bibliotecaria-front` si existe fuera de este repo backend) | Fuera del alcance de este plan, que cubre exclusivamente el backend Spring Boot |
| SQL Injection en los 6 repositorios (`UsuarioRepository`, `LibroRepository`, etc.) | Ya evaluado en la Fase 1 de análisis (hallazgo F1): son interfaces `JpaRepository` con métodos derivados por nombre, sin ningún `@Query` nativo ni concatenación de strings — no existe superficie de ataque que ejercitar; Spring Data parametriza siempre |
| Rate limiting / anti-automatización en `/api/login` (ASVS V2.2.1) | No existe ningún filtro o interceptor en el código; no hay rama ejecutable que probar en ningún nivel |
| Subida de imagen en `POST /api/libros` (`@RequestPart MultipartFile`) | No cubierto por ninguno de los tres niveles actuales; requeriría pruebas multipart dedicadas, pendiente para una ronda futura |
| Rendimiento / carga / concurrencia | No hay herramienta de carga (JMeter, Gatling, k6) en el repositorio; fuera del alcance de un plan de pruebas funcional/seguridad |
| Autenticación HTTP Basic u OAuth2 | `SecurityConfig` solo configura `formLogin`; no hay otro mecanismo de autenticación en el código que probar |
| Cobertura de `Repository` (JPA derivado) | Sin lógica propia que probar unitariamente más allá de lo que Spring Data ya garantiza por contrato |

---

## 6. Enfoque de prueba

### 6.1 Nivel unitario

- **Framework:** JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), sin `@SpringBootTest`,
  sin `@WebMvcTest`, sin `MockMvc`.
- **Dobles de prueba:** `@Mock`/`@InjectMocks` para repositorios y servicios (stub de respuestas
  fijas); `ArgumentCaptor` para interceptar el objeto persistido antes de `save()`;
  `verify()`/`never()` para confirmar o descartar interacciones; en `ControllerAuthorizationTest`,
  el `Controller` se instancia con `new` y sus dependencias `@Autowired` se inyectan por reflexión
  pura de Java (`java.lang.reflect.Field`), sin contenedor de Spring.
- **Patrón AAA:** los tres bloques (Arrange / Act / Assert) separados y comentados en cada método.
- **Aislamiento:** cero dependencias externas — ni base de datos, ni filtros HTTP, ni contexto de
  aplicación. El único caso que instancia una clase real no mockeada es `BCryptPasswordEncoder`
  (para producir hashes reales verificables).
- **Clases:** `UsuarioServiceTest`, `LibroServiceTest`, `PrestamoServiceTest`,
  `AmonestacionServiceTest`, `CustomUserDetailsServiceTest`, `ControllerAuthorizationTest`,
  `ModelSerializationTest` (7 clases, 63 métodos, 82 ejecuciones).

### 6.2 Nivel de integración

- **Framework:** JUnit 5 + `@WebMvcTest(Controller.class)` + `@Import(SecurityConfig.class)` para
  los casos que solo necesitan capa web + filtros de seguridad reales (3 de 4 clases); `@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc` solo para el caso que necesita
  persistencia real de extremo a extremo (`PrivilegeEscalationEndToEndIntegrationTest`).
- **Simulación de identidad:** `@WithMockUser(authorities = "...")`, `@WithAnonymousUser`, y
  `SecurityMockMvcRequestPostProcessors.user(...)/csrf()` de `spring-security-test`.
- **Base de datos:** H2 en memoria (`jdbc:h2:mem:testdb`, `MODE=MySQL`, `ddl-auto=create-drop`),
  activada vía `@ActiveProfiles("test")` + `src/test/resources/application-test.properties`. Los 3
  casos `@WebMvcTest` no cargan `DataSource` en absoluto (los 6 servicios están mockeados con
  `@MockitoBean`); solo `PrivilegeEscalationEndToEndIntegrationTest` toca H2 realmente.
- **Clases:** `SecurityConfigAccessRulesIntegrationTest`, `CsrfProtectionIntegrationTest`,
  `BeanValidationRequestBodyIntegrationTest`, `PrivilegeEscalationEndToEndIntegrationTest`
  (4 clases, 21 métodos, 29 ejecuciones).

### 6.3 Nivel de caja negra

- **Herramienta:** no se usó Postman/Newman. Se implementó como pruebas **JUnit 5 +
  `TestRestTemplate`** de Spring Boot Test, con `@SpringBootTest(webEnvironment = RANDOM_PORT)`:
  la aplicación completa arranca en un puerto HTTP real y las pruebas hacen peticiones HTTP reales
  contra ese puerto (sin `MockMvc`, sin invocar servicios directamente), usando AssertJ para las
  aserciones. Funcionalmente equivalente a una colección de Postman/Newman, pero ejecutada como
  parte de la misma suite de Maven/Surefire.
- **Qué se prueba:** únicamente el contrato HTTP público — código de estado, cuerpo de respuesta,
  cookies de sesión — sin ningún conocimiento de las clases internas (no hay imports de
  `service.*` salvo el modelo `Usuario` para deserializar la respuesta JSON de `/api/usuarios/me`).
- **Base de datos:** H2 en memoria, mismo `application-test.properties` que el nivel de
  integración, vía `@ActiveProfiles("test")`.
- **Clases:** `AuthFunctionalTest` (4 casos, flujo feliz de registro/login), `LibroFunctionalTest`
  (2 casos, lectura pública de libros), `AccessControlSecurityTest` (4 casos, regresión de
  vulnerabilidades de control de acceso confirmadas — diseñados para fallar hasta que la app se
  corrija). 3 clases, 10 métodos, 10 ejecuciones.

### 6.4 Técnicas de prueba aplicadas por nivel (ISO/IEC/IEEE 29119-4)

| Técnica (29119-4) | Dónde se aplica |
|---|---|
| **Partición de equivalencia** | Roles válidos vs. inválidos en pruebas parametrizadas (`@ValueSource`/`@CsvSource`) — p. ej. `USUARIO`/`BIBLIOTECARIO`/`ADMIN`/`""` en `LibroServiceTest`, `PrestamoServiceTest`, `AmonestacionServiceTest`; payloads JSON válidos vs. inválidos en `BeanValidationRequestBodyIntegrationTest` |
| **Análisis de valores límite** | Cantidades de libro (`0`, `-1`, `-100` en `registrarLibroConImagen`), longitud de ISBN alrededor del umbral de 13 caracteres (incluido el caso adversarial del signo negativo, DEF-11), contraseñas de longitud mínima (`""`, `"1"`, `"abc"` vs. ≥12 caracteres) |
| **Pruebas basadas en decisión** | Reglas de `SecurityConfig` (`permitAll` / `authenticated()` / `hasAuthority("BIBLIOTECARIO")`) evaluadas como tabla de decisión rol × ruta × método HTTP en `SecurityConfigAccessRulesIntegrationTest` |
| **Pruebas basadas en defectos** | Los 20 hallazgos del catálogo (DEF-01…DEF-20) se convierten cada uno en al menos un caso de prueba que documenta el defecto conocido como regresión ejecutable — el núcleo metodológico de las tres rondas |
| **Pruebas de caja negra / especificación** | Todo el nivel 3: los casos se derivan del contrato HTTP documentado (rutas, métodos, códigos de estado esperados), sin mirar el código fuente del `Controller`/`Service` |

### 6.5 Criterio de "prueba correcta" vs. "defecto documentado"

Aplicado de forma idéntica en los tres niveles:

- 🟢 **Verde = control de seguridad confirmado.** La aserción expresa el comportamiento *seguro*
  esperado según ISO/IEC 25010, ISO/IEC 27001 Anexo A u OWASP ASVS, y el sistema hoy lo cumple.
- 🔴 **Rojo = defecto real documentado, no un fallo del plan.** La aserción también expresa el
  comportamiento seguro esperado, pero el sistema **no** lo cumple hoy. El test queda en rojo a
  propósito, con un comentario `DEFECTO DETECTADO` explicando el riesgo — nunca se relaja la
  aserción para forzar el verde. Un rojo aquí es información (una vulnerabilidad real, pinneada
  como regresión ejecutable), no ruido.

Bajo este criterio, el **65.6% de pass rate global no es un indicador de mala calidad de la
suite** — es la proporción de controles de seguridad evaluados que hoy están correctamente
implementados, contra un denominador que incluye a propósito 42 pruebas que documentan
vulnerabilidades reales y confirmadas.

---

## 7. Criterios de entrada y salida

### Criterios de entrada

| Criterio | Estado |
|---|---|
| Código fuente compila (`mvn compile`) | ✅ Cumplido |
| Entorno de test configurado (H2, `application-test.properties`, dependencias `spring-security-test`/`spring-boot-starter-validation`/`h2`/`jacoco-maven-plugin` en `pom.xml`) | ✅ Cumplido |
| Catálogo de hallazgos de seguridad disponible y trazado a componentes (`SECURITY_FINDINGS`/`COMPONENTS` en `scripts/publish_metrics.py`) | ✅ Cumplido — 20 hallazgos / 9 componentes |
| Casos de prueba diseñados con trazabilidad a un requisito ISO25010/ISO27001/ASVS documentado en comentario | ✅ Cumplido en las 14 clases de las 3 rondas |

### Criterios de salida

| Criterio | Estado |
|---|---|
| Las 15 clases de test (14 propias + smoke test preexistente) compilan y ejecutan sin `Errors` (solo `Failures` intencionales) | ✅ Cumplido — 122 ejecuciones, 0 `Errors` |
| Cada prueba en rojo tiene su comentario `DEFECTO DETECTADO` y su hallazgo trazado en el catálogo | ✅ Cumplido |
| Reporte de cobertura JaCoCo generado exitosamente | ✅ Cumplido — línea 66.7%, rama 51.9% |
| Pipeline de métricas hacia Jira/Confluence probado (al menos en modo `--dry-run`) | ✅ Cumplido — ver salida real en sección 15 |
| Publicación real (en vivo) a la página de Confluence configurada | ⚠️ **[PENDIENTE — no verificable desde el repositorio]**: depende de que los secrets/vars de GitHub (`JIRA_BASE_URL`, `JIRA_EMAIL`, `JIRA_API_TOKEN`, `JIRA_PROJECT_KEY`, `CONFLUENCE_PAGE_ID`) estén configurados en `Settings → Secrets and variables` del repositorio. Esa configuración no es parte del código versionado y no se puede confirmar leyendo el repo. **Necesito que confirmes si esos 5 valores ya están cargados en GitHub** — si no lo están, el script se salta la publicación con una advertencia (por diseño) y el resto del pipeline sigue en verde |
| 100% de las pruebas en verde | ❌ **No aplica como criterio de salida** — el diseño intencional del plan (sección 6.5) incluye 42 rojos que documentan defectos reales; forzar el verde violaría la metodología |

---

## 8. Criterios de suspensión y reanudación

**Suspensión:**
- Si `mvn compile` falla (error de compilación en código de producción, no de test) — bloquea
  cualquier nivel.
- Si el entorno no puede resolver las dependencias de test (`spring-security-test`,
  `spring-boot-starter-validation`, `h2`, `jacoco-maven-plugin`) — bloquea integración y caja
  negra específicamente.
- Si aparece un `Error` (no `Failure`) inesperado en cualquier clase — indica un problema de
  entorno/configuración, no un defecto de seguridad documentado, y debe investigarse antes de
  seguir sumando pruebas sobre una base inestable.

**Reanudación:**
- Tras corregir la causa de la suspensión, se re-ejecuta `mvn clean test` completo (no parcial)
  para evitar reportes de Surefire obsoletos de clases renombradas o eliminadas — riesgo ya
  documentado en `risks.md` y observado localmente durante el renombrado de las clases de
  integración.

---

## 9. Entregables de esta ronda

**Clases de test (14, en `src/test/java/com/biblioteca/`):**
- Unitarias: `service/UsuarioServiceTest.java`, `service/LibroServiceTest.java`,
  `service/PrestamoServiceTest.java`, `service/AmonestacionServiceTest.java`,
  `security/CustomUserDetailsServiceTest.java`, `controller/ControllerAuthorizationTest.java`,
  `model/ModelSerializationTest.java`
- Integración: `integration/SecurityConfigAccessRulesIntegrationTest.java`,
  `integration/CsrfProtectionIntegrationTest.java`,
  `integration/BeanValidationRequestBodyIntegrationTest.java`,
  `integration/PrivilegeEscalationEndToEndIntegrationTest.java`
- Caja negra: `blackbox/functional/AuthFunctionalTest.java`,
  `blackbox/functional/LibroFunctionalTest.java`, `blackbox/security/AccessControlSecurityTest.java`

**Documentos de trazabilidad:**
- `docs/matriz-trazabilidad-pruebas-unitarias.md` + `docs/reporte-pruebas-seguridad.md`
- `docs/matriz-trazabilidad-pruebas-integracion.md` + `docs/reporte-pruebas-seguridad-integracion.md`
- `docs/plan-de-pruebas.md` (este documento)

**Configuración y ecosistema de métricas:**
- `pom.xml` — dependencias de test + `jacoco-maven-plugin` (versión 0.8.12)
- `src/test/resources/application-test.properties` — datasource H2 de test
- `.github/workflows/tests.yml` — pipeline de CI completo
- `scripts/publish_metrics.py` — puente de métricas Surefire + JaCoCo → Jira/Confluence
- `risks.md` — bitácora de riesgos y decisiones técnicas de la construcción de este ecosistema

---

## 10. Ambiente de pruebas

| Aspecto | Valor |
|---|---|
| Lenguaje / plataforma | Java 21 (`pom.xml`), compilado localmente con JDK 17 vía `-Dmaven.compiler.release=17` por una limitación de certificados del entorno de desarrollo local (no aplica en CI, que usa JDK 21 nativo) |
| Framework de aplicación | Spring Boot 3.4.5 (Spring Security 6.4.5, Spring Framework 6.2.x) |
| Framework de test | JUnit 5.12.2, Mockito (vía `spring-boot-starter-test`), AssertJ, `spring-security-test` 6.4.5 |
| Build | Maven (`mvnw`), `maven-surefire-plugin` 3.5.4, `jacoco-maven-plugin` 0.8.12 |
| CI | GitHub Actions, workflow único `.github/workflows/tests.yml`, runner `ubuntu-latest`, JDK 21 Temurin |
| Base de datos — unitarias | Ninguna (todo mockeado con Mockito) |
| Base de datos — integración | H2 en memoria (`MODE=MySQL`), 3 de 4 clases ni siquiera la cargan (`@WebMvcTest` sin `DataSource`) |
| Base de datos — caja negra | H2 en memoria, misma configuración (`@ActiveProfiles("test")`) |
| Base de datos — producción (no usada en tests) | MySQL (`application.properties`, `localhost:3306/biblioteca_db`) |
| Ecosistema de métricas | Python 3.11 (`actions/setup-python`) + `scripts/publish_metrics.py` (solo librería estándar, sin dependencias externas) → Confluence Cloud (REST API `/wiki/rest/api/content/{id}`) + Jira Cloud (REST API `/rest/api/3/issue`) |

---

## 11. Preparación de datos de prueba

- **Unitario:** los datos se construyen inline en cada método de test (objetos `Usuario`, `Libro`,
  `Prestamo`, etc. armados a mano o con métodos factoría privados como `usuarioConRol(...)`,
  `libroValido(...)`); no hay fixtures externas ni archivos de datos.
- **Integración:** igual que unitario para los 3 casos `@WebMvcTest` (JSON inline como
  `String`); `PrivilegeEscalationEndToEndIntegrationTest` genera usuarios con correos únicos
  (`atacante-e2e@test.com`, `normal-e2e@test.com`) para no colisionar con otras pruebas dentro de
  la misma base H2 en memoria.
- **Caja negra:** cada método de test registra sus propios usuarios vía HTTP real
  (`POST /api/usuarios/registro`) con **correos globalmente únicos por método**
  (`nuevo-funcional@test.com`, `attacker-mass-assignment@test.com`,
  `usuario-comun-idor@test.com`, etc.) — convención necesaria porque, según `risks.md`, las
  pruebas comparten un único contexto de Spring / instancia H2 por corrida de JVM (context
  caching de Spring Test), y una colisión de correo haría fallar el registro con "Correo ya
  registrado." en un test que no la espera. No hay `@AfterEach` de limpieza: aceptable porque H2
  es en memoria y se recrea en cada corrida de JVM (`ddl-auto=create-drop`).

---

## 12. Automatización y ciclos de ejecución

Flujo completo, disparado en **cada `push` a cualquier rama** y en cada **pull request hacia
`main`** (`.github/workflows/tests.yml`):

```
push / pull_request
        │
        ▼
actions/checkout
        │
        ▼
actions/setup-java (Temurin 21, cache maven)
        │
        ▼
mvn -B clean test            ← corre los 3 niveles juntos (Surefire no distingue nivel;
        │                       la categorización unitaria/integración/caja-negra la hace
        │                       scripts/publish_metrics.py por convención de nombre/paquete)
        │  (este paso puede terminar en FAILURE por los tests DEFECTO intencionales —
        │   es un resultado esperado, no se trata como fallo de pipeline)
        ▼
mvn -B jacoco:report          ← paso separado con if: always(), porque el agente JaCoCo ya
        │                        escribió target/jacoco.exec durante "test" aunque el build
        │                        haya terminado en failure
        ▼
dorny/test-reporter           ← publica target/surefire-reports/*.xml como check de PR
        │  (if: always())
        ▼
actions/upload-artifact       ← sube target/site/jacoco/ como artefacto descargable
        │  (if: always())
        ▼
actions/setup-python (3.11)
        │  (if: always())
        ▼
python3 scripts/publish_metrics.py
        │  (if: always())
        ├─→ parsea target/surefire-reports/*.xml (resumen global + por clase)
        ├─→ categoriza cada clase en unitarias / integración / caja_negra
        ├─→ parsea target/site/jacoco/jacoco.xml (cobertura global + por componente)
        ├─→ escanea src/test/java buscando comentarios ISO25010/ISO27001/ASVS
        │   sobre @Test/@ParameterizedTest → tasa de confirmación de controles
        ├─→ calcula densidad y distribución de hallazgos (catálogo hardcodeado)
        ├─→ PUT a Confluence: reemplaza el fragmento entre
        │   <!-- CI-METRICS-START/END --> en la página CONFLUENCE_PAGE_ID
        │   (si faltan JIRA_BASE_URL/JIRA_EMAIL/JIRA_API_TOKEN/CONFLUENCE_PAGE_ID,
        │   se omite con una advertencia, sin romper el pipeline)
        └─→ POST a Jira: si hay ≥1 fallo, crea UN issue Bug agregado con la lista
            de tests fallidos (si faltan credenciales, se omite igual)
```

**Ejecución local equivalente** (usada para recolectar todos los números de este documento):

```bash
mvn clean test               # corre las 15 clases (122 ejecuciones)
mvn jacoco:report            # genera target/site/jacoco/jacoco.xml y el HTML
python3 scripts/publish_metrics.py --dry-run   # calcula y muestra por consola todas las
                                                # métricas SIN llamar a Jira/Confluence
```

La suite de caja negra **no requiere ningún paso adicional**: al ser clases JUnit 5 estándar bajo
`src/test/java`, Surefire las descubre y ejecuta como cualquier otra clase de test en el mismo
`mvn test` — no hay una colección Postman separada que invocar ni un runner distinto.

---

## 13. Riesgos del plan de pruebas

Riesgos reales, documentados en `risks.md` durante la construcción de las rondas de integración,
caja negra y del ecosistema de métricas (traducidos/consolidados aquí):

1. **Confusión sin contexto.** Los 4 tests de regresión en `AccessControlSecurityTest` (y, en
   general, los 42 tests `DEFECTO` de las tres rondas) están diseñados para fallar hasta que la
   app se corrija. Cualquiera que corra `mvn test` sin leer los comentarios verá rojo sin
   explicación — mitigado por este mismo plan y las matrices de trazabilidad.
2. **Llamadas en vivo a Jira/Confluence.** `publish_metrics.py` hace peticiones HTTP reales en
   cada corrida de CI una vez configurados los secrets; un `CONFLUENCE_PAGE_ID` mal configurado se
   omite en silencio (por diseño) en vez de romper el build — riesgo de "publicación silenciosa
   fallida" pasar desapercibida.
3. **Cobertura baja sin gate.** Cobertura actual línea 66.7% / rama 51.9%, sin umbral mínimo
   configurado a propósito (proyecto aún construyendo su suite); un gate estricto haría fallar CI
   por razones no relacionadas con lo que se está evaluando ahora.
4. **Reportes de Surefire obsoletos.** `target/surefire-reports` puede arrastrar XML de clases
   previas a un renombrado si `mvn test` corre sin `clean` primero — ya ocurrió localmente durante
   el renombrado de las clases `*IntegrationTest`; el CI actual corre `mvn -B clean test` para
   evitarlo, pero un caché de `target/` entre corridas podría reintroducirlo.
5. **Catálogo de hallazgos mantenido a mano.** `SECURITY_FINDINGS` (20 filas) y `COMPONENTS`
   (9 filas) en `scripts/publish_metrics.py` son constantes editadas manualmente; si un hallazgo
   se corrige o aparece uno nuevo, alguien debe actualizar el catálogo o la densidad/distribución
   reportadas a Confluence quedan desactualizadas en silencio.
6. **Detección de controles de seguridad frágil.** `find_security_tagged_tests` depende de una
   convención textual (comentarios `//` contiguos justo arriba de `@Test` mencionando
   ISO25010/ISO27001/ASVS); reformatear un comentario (línea en blanco, `/* */`) hace que un test
   se caiga silenciosamente de la tasa de confirmación de controles, sin ningún error visible.
7. **Colisión de datos en caja negra.** Las pruebas de `blackbox` comparten un único contexto de
   Spring / instancia H2 por corrida de JVM; dependen de que cada método use un correo
   globalmente único. Nuevas pruebas de caja negra que no sigan esa convención podrían colisionar
   y producir fallos espurios no relacionados con seguridad.
8. **Workflows duplicados (riesgo ya resuelto).** Se detectó y corrigió durante la construcción:
   hubo un momento en que existían `ci.yml` y `tests.yml` en paralelo, duplicando minutos de CI;
   se consolidó todo en `tests.yml` y se eliminó `ci.yml`.

---

## 14. Roles y responsabilidades

| Rol | Persona | Responsabilidad |
|---|---|---|
| Líder General del plan de pruebas / Autor de pruebas unitarias | **Miguel Pinto** | Análisis de seguridad inicial (Fase 1), diseño e implementación de las 7 clases de test unitario, diseño e implementación de las 4 clases de test de integración, consolidación de matrices de trazabilidad y de este plan de pruebas |
| DevOps de integración y CI | **Eduard Velasco** | Infraestructura de integración continua y del pipeline base de ejecución de pruebas |
| Implementación de caja negra e integración con Confluence/Jira | **Arturo Pinto** | Suite de pruebas de caja negra (`blackbox/functional`, `blackbox/security`), cobertura JaCoCo, y el puente de métricas `scripts/publish_metrics.py` hacia Jira/Confluence |

> **Nota de trazabilidad:** según el historial de git, los commits de la suite de caja negra, la
> cobertura JaCoCo y el puente de métricas (`feat: add JaCoCo coverage...`,
> `test: add black-box security regression suite and Jira/Confluence CI metrics bridge`) están
> firmados por el usuario `ArtP10 <10arturojpinto@gmail.com>`; se usa aquí el nombre
> **Arturo Pinto** inferido de esa dirección de correo. Los commits de pruebas unitarias,
> integración y del workflow base de CI (`.github/workflows/tests.yml` original) están firmados
> por `Miguel Pinto <miguelep0106@gmail.com>`. No se encontró en el historial de git ningún commit
> a nombre de Eduard Velasco; su responsabilidad de DevOps se registra aquí por indicación directa
> del equipo, no por evidencia en el control de versiones.

---

## 15. Resultados de la ejecución

Última corrida real (`mvn clean test` + `mvn jacoco:report`, 2026-07-19):

| Nivel | Total de pruebas | En verde | En rojo | Cobertura (línea / rama) | Hallazgos documentados |
|---|---|---|---|---|---|
| **Unitarias** | 82 (63 métodos) | 62 (75.6%) | 20 (24.4%) | — *(ver nota)* | 9 causas raíz distintas |
| **Integración** | 29 (21 métodos) | 11 (37.9%) | 18 (62.1%) | — *(ver nota)* | 8 causas raíz (3 nuevas, 5 confirman hallazgos previos) |
| **Caja negra** | 10 (10 métodos) | 6 (60.0%) | 4 (40.0%) | — *(ver nota)* | 3 vulnerabilidades confirmadas vía HTTP real (DEF-14, DEF-17, DEF-20) |
| **Total** | **122** (94 métodos) | **80 (65.6%)** | **42 (34.4%)** | **Global: línea 66.7% / rama 51.9%** | **20 hallazgos únicos en el catálogo (DEF-01…DEF-20)** |

*Nota sobre cobertura por nivel:* JaCoCo instrumenta el proceso completo de `mvn test` y acumula
un único `target/jacoco.exec` para las 122 ejecuciones juntas; el plugin no separa qué porcentaje
de cobertura aporta cada nivel de forma aislada (requeriría tres builds independientes con
`jacoco:report` entre cada uno). La cifra global y por componente sí es real y se reporta abajo.

**Cobertura de línea por componente (JaCoCo, real):**

| Componente | Cobertura de línea |
|---|---|
| `security/CustomUserDetailsService` | 100.0% |
| `service/PrestamoService` | 94.6% |
| `service/UsuarioService` | 92.7% |
| `config/SecurityConfig` | 93.1% |
| `service/LibroService` | 88.1% |
| `service/AmonestacionService` | 72.7% |
| `controller/Controller` | 54.2% |
| `service/ResenaService` | 6.2% |
| `service/ComentarioResenaService` | 5.3% |

**Tasa de confirmación de controles de seguridad** (pruebas cuyo comentario cita
ISO25010/ISO27001/ASVS, cruzadas contra su resultado real): **99 pruebas etiquetadas evaluadas,
26 fallando, 73.7% de tasa de confirmación** — es decir, casi 3 de cada 4 controles de seguridad
verificados por esta suite están hoy correctamente implementados; el resto (26) son defectos reales
ya documentados y trazados al catálogo de la sección 4.

**Distribución de hallazgos por subcaracterística ISO/IEC 25010:** Confidencialidad 4 (20.0%),
Integridad 11 (55.0%), Autenticidad 7 (35.0%) — Integridad concentra más de la mitad del catálogo,
principalmente por la ausencia total de Bean Validation en 5 puntos de entrada distintos
(DEF-06…DEF-10).
