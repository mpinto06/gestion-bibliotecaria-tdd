# Especificación de Casos de Prueba — Sistema de Gestión Bibliotecaria

> Estructura alineada a ISO/IEC/IEEE 29119-3 (contenido del documento "Especificación de Casos de
> Prueba"). Este documento es el detalle test-por-test que `docs/plan-de-pruebas.md` referencia
> pero no desarrolla: cada fila se extrajo directamente del código fuente de las 14 clases de
> prueba en `src/test/java/com/biblioteca/` (bloques Arrange/Act/Assert y comentarios de
> trazabilidad ISO 25010/ISO 27001/ASVS), no de memoria ni de un resumen. El campo "Resultado
> obtenido" se tomó de la ejecución real `mvn clean test` corrida el **2026-07-19** sobre la rama
> `testing` (`Tests run: 122, Failures: 42, Errors: 0, Skipped: 0`), parseando directamente
> `target/surefire-reports/*.xml` — no se asumió ningún resultado.
>
> Se puede leer de forma independiente: no es necesario abrir el código fuente para entender qué
> verifica cada caso, con qué datos, y qué pasó al ejecutarlo.

---

## Identificación y trazabilidad del documento

| Campo | Valor |
|---|---|
| Identificador | `ECP-BIBLIOTECA-2026-001` |
| Documento padre | `docs/plan-de-pruebas.md` (`PP-BIBLIOTECA-2026-001`) |
| Documentos relacionados | `docs/matriz-trazabilidad-pruebas-unitarias.md`, `docs/matriz-trazabilidad-pruebas-integracion.md`, `docs/reporte-pruebas-seguridad.md`, `docs/reporte-pruebas-seguridad-integracion.md`, `docs/informe-de-pruebas.md`, `docs/informe-auditoria-seguridad.md` |
| Fecha de emisión | 2026-07-19 |
| Autor / responsable | Miguel Pinto |
| Convención de ID | `CPU-##` (unitaria), `CPI-##` (integración), `CPN-##` (caja negra) — correlativo por nivel, una fila por **ejecución** (cada variante de `@ParameterizedTest`/`@CsvSource`/`@ValueSource` es una fila propia, no una fila genérica por método) |
| Convención de severidad | `Alta` / `Media` / `Baja` heredada del hallazgo `DEF-XX` asociado (catálogo de `scripts/publish_metrics.py`, severidad tomada de `docs/reporte-pruebas-seguridad.md` §"Tabla resumen" y `docs/reporte-pruebas-seguridad-integracion.md` §"Tabla resumen"; la severidad `Crítica` de esos reportes para DEF-20 se homologa aquí a `Alta`, el techo de la escala de 3 niveles pedida). `N/A — control ya conforme` cuando el caso confirma un comportamiento correcto sin hallazgo asociado |
| Convención de técnica (29119-4) | La columna "Técnica" nombra, sin siglas, la técnica de diseño de casos con la que se construyó cada caso (partición de equivalencia, análisis de valores límite, tabla de decisión, pruebas basadas en defectos, o pruebas de especificación/caja negra) — cuando un caso combina dos, se listan ambas separadas por `+` |

---

# 1. Casos de Prueba Unitarios

**Alcance:** JUnit 5 + Mockito, sin contexto de Spring, sin `MockMvc`. 7 clases, **63 métodos, 82
ejecuciones**. Los dobles de prueba (stub, mock+`verify`, `ArgumentCaptor`, reflexión pura) están
indicados por grupo de clase; el detalle exacto de cuál se usa en cada caso está en la columna
"Precondiciones".

## 1.1 `UsuarioServiceTest` (CPU-01 a CPU-21)

Componente bajo prueba: `service/UsuarioService`. Repositorios mockeados: `UsuarioRepository`,
`PrestamoRepository`, `AmonestacionRepository`, `ResenaRepository`, `ComentarioResenaRepository`.

| ID | Caso de prueba | Método de producción | Objetivo | Precondiciones (Arrange) | Entrada / pasos (Act) | Resultado esperado (Assert) | Resultado obtenido | Prioridad | Técnica |
|---|---|---|---|---|---|---|---|---|---|
| CPU-01 | `registrarUsuario_debeAlmacenarContrasenaHasheadaConBCrypt_V2_4_1` | `UsuarioService.registrarUsuario` | ISO27001 A.8.28 (codificación segura) · ASVS V2.4.1 (hash aprobado, BCrypt) | Stub `existsByCorreo("ana@test.com")→false`; usuario con contraseña `"ClaveSegura123!"` | `registrarUsuario(entrada)`; se captura el `Usuario` pasado a `save()` | El hash guardado es distinto del texto plano y empieza con `$2a$` o `$2b$` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-02 | `registrarUsuario_correoYaRegistrado_noPersisteYRechaza` | `UsuarioService.registrarUsuario` | ISO25010 Integridad (no duplicar identidades) | Stub `existsByCorreo("dup@test.com")→true` | `registrarUsuario(entrada)` | Devuelve `"Correo ya registrado."`; `save()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-03 | `registrarUsuario_sinRolEspecificado_asignaRolUsuarioPorDefecto` | `UsuarioService.registrarUsuario` | ISO27001 A.8.2 (derechos de acceso privilegiado) · ASVS V4.1.3 (mínimo privilegio) | Stub `existsByCorreo→false`; usuario con `rol=null` | `registrarUsuario(entrada)`; se captura el `Usuario` guardado | El rol persistido es `"USUARIO"` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-04 | `registrarUsuario_DEFECTO_permiteEscaladaDePrivilegiosPorRolArbitrario` — rol malicioso `"BIBLIOTECARIO"` | `UsuarioService.registrarUsuario` | ISO25010 Integridad/Autenticidad · ISO27001 A.8.2, A.5.15 · ASVS V4.1.5, V4.2.1 | Stub `existsByCorreo("atacante@test.com")→false`; usuario con `rol="BIBLIOTECARIO"` enviado por el cliente | `registrarUsuario(entrada)`; se captura el `Usuario` guardado | El rol persistido debería ser siempre `"USUARIO"` en el alta pública, sin importar lo enviado | 🔴 Rojo — **DEF-20**: el registro público (`permitAll`) persiste el rol tal cual llega, permitiendo autoasignarse `BIBLIOTECARIO` | Alta | Basada en defectos + partición de equivalencia |
| CPU-05 | ídem, rol malicioso `"ADMIN"` | `UsuarioService.registrarUsuario` | (mismo objetivo que CPU-04) | (mismo, con `rol="ADMIN"`) | (mismo) | (mismo) | 🔴 Rojo — DEF-20 | Alta | Basada en defectos + partición de equivalencia |
| CPU-06 | ídem, rol malicioso `"SUPERUSER"` | `UsuarioService.registrarUsuario` | (mismo objetivo que CPU-04) | (mismo, con `rol="SUPERUSER"`) | (mismo) | (mismo) | 🔴 Rojo — DEF-20 | Alta | Basada en defectos + partición de equivalencia |
| CPU-07 | `registrarUsuario_DEFECTO_aceptaContrasenaDebilSinValidarFortaleza` — contraseña `""` | `UsuarioService.registrarUsuario` | ASVS V2.1.1 (longitud mínima de contraseña, 12 caracteres) | Stub `existsByCorreo("debil@test.com")→false`; contraseña `""` | `registrarUsuario(entrada)` | `save()` nunca debería invocarse con una contraseña que no cumple el mínimo | 🔴 Rojo — **DEF-03**: no existe validación de longitud/fortaleza; se persiste igual | Media | Valor límite + basada en defectos |
| CPU-08 | ídem, contraseña `"1"` | `UsuarioService.registrarUsuario` | (mismo objetivo que CPU-07) | (mismo, contraseña `"1"`) | (mismo) | (mismo) | 🔴 Rojo — DEF-03 | Media | Valor límite + basada en defectos |
| CPU-09 | ídem, contraseña `"abc"` | `UsuarioService.registrarUsuario` | (mismo objetivo que CPU-07) | (mismo, contraseña `"abc"`) | (mismo) | (mismo) | 🔴 Rojo — DEF-03 | Media | Valor límite + basada en defectos |
| CPU-10 | ídem, contraseña `"aaaaaaaaaa"` (10 caracteres, bajo el mínimo de 12) | `UsuarioService.registrarUsuario` | (mismo objetivo que CPU-07) | (mismo, contraseña de 10 caracteres) | (mismo) | (mismo) | 🔴 Rojo — DEF-03 | Media | Valor límite + basada en defectos |
| CPU-11 | `autenticarYObtenerUsuario_correoInexistente_devuelveNull` | `UsuarioService.autenticarYObtenerUsuario` | ASVS V2.2.2 (prevención de enumeración de usuarios) | Stub `findByCorreo("noexiste@test.com")→Optional.empty()` | `autenticarYObtenerUsuario("noexiste@test.com","cualquiera")` | Devuelve `null` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-12 | `autenticarYObtenerUsuario_contrasenaIncorrecta_devuelveNullSinDistinguirCausa` | `UsuarioService.autenticarYObtenerUsuario` | ASVS V2.2.2 (mensaje/resultado no distingue causa del fallo) | Stub con hash BCrypt real de `"ClaveReal123!"` para `bob@test.com` | `autenticarYObtenerUsuario("bob@test.com","ClaveIncorrecta")` | Devuelve `null` (mismo resultado que correo inexistente) | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-13 | `autenticarYObtenerUsuario_credencialesCorrectas_devuelveUsuario` | `UsuarioService.autenticarYObtenerUsuario` | ASVS V2.4.1 (verificación correcta contra hash almacenado) | Stub con hash BCrypt real de `"ClaveReal123!"` para `carla@test.com` | `autenticarYObtenerUsuario("carla@test.com","ClaveReal123!")` | Devuelve el `Usuario`, con `correo="carla@test.com"` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-14 | `cambiarContrasena_actualIncorrecta_rechazaYNoPersiste` | `UsuarioService.cambiarContrasena` | ASVS V2.1.6 (cambio de contraseña exige la contraseña actual) | Stub con hash BCrypt real de `"ClaveOriginal123!"` para `dora@test.com` | `cambiarContrasena("dora@test.com","ClaveIncorrecta","NuevaClave123!")` | Devuelve `"La contraseña actual no es correcta."`; `save()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-15 | `cambiarContrasena_actualCorrecta_actualizaHash` | `UsuarioService.cambiarContrasena` | ASVS V2.1.6 + V2.4.1 | Stub con hash BCrypt real de `"ClaveOriginal123!"` para `eva@test.com` | `cambiarContrasena("eva@test.com","ClaveOriginal123!","NuevaClave456!")` | Devuelve `"Contraseña actualizada correctamente."`; el hash guardado es distinto del original | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-16 | `cambiarContrasena_DEFECTO_aceptaContrasenaNuevaDebil` — nueva contraseña `""` | `UsuarioService.cambiarContrasena` | ASVS V2.1.1 (longitud mínima) | Stub con hash BCrypt real de `"ClaveOriginal123!"` para `fer@test.com` | `cambiarContrasena("fer@test.com","ClaveOriginal123!","")` | `save()` nunca debería invocarse con una contraseña nueva débil | 🔴 Rojo — **DEF-04**: no valida fortaleza de la contraseña nueva | Media | Valor límite + basada en defectos |
| CPU-17 | ídem, nueva contraseña `"1"` | `UsuarioService.cambiarContrasena` | (mismo objetivo que CPU-16) | (mismo, nueva `"1"`) | (mismo) | (mismo) | 🔴 Rojo — DEF-04 | Media | Valor límite + basada en defectos |
| CPU-18 | ídem, nueva contraseña `"abc"` | `UsuarioService.cambiarContrasena` | (mismo objetivo que CPU-16) | (mismo, nueva `"abc"`) | (mismo) | (mismo) | 🔴 Rojo — DEF-04 | Media | Valor límite + basada en defectos |
| CPU-19 | `eliminarUsuario_conPrestamosActivos_lanzaExcepcionYNoElimina` | `UsuarioService.eliminarUsuario` | ISO25010 Integridad (no eliminar cuentas con préstamos activos) | Usuario `id=1`; stub `existsByUsuarioIdAndFechaDevolucionIsNull(1)→true` | `eliminarUsuario("gus@test.com")` | Lanza `RuntimeException`; `delete()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-20 | `eliminarUsuario_conAmonestacionesPendientes_lanzaExcepcionYNoElimina` | `UsuarioService.eliminarUsuario` | ISO25010 Integridad (no eliminar cuentas con amonestaciones pendientes) | Usuario `id=2`; sin préstamos activos; stub `existsByUsuarioIdAndPagadaFalse(2)→true` | `eliminarUsuario("hilda@test.com")` | Lanza `RuntimeException`; `delete()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-21 | `eliminarUsuario_sinBloqueos_eliminaComentariosResenasYUsuario` | `UsuarioService.eliminarUsuario` | ISO25010 Integridad (borrado consistente de datos relacionados) | Usuario `id=3` sin bloqueos; stubs de comentarios/reseñas asociados | `eliminarUsuario("ivan@test.com")` | Se eliminan comentarios, reseñas y el propio usuario, en ese orden | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |

## 1.2 `CustomUserDetailsServiceTest` (CPU-22 a CPU-23)

Componente bajo prueba: `security/CustomUserDetailsService`. Repositorio mockeado:
`UsuarioRepository`.

| ID | Caso de prueba | Método de producción | Objetivo | Precondiciones (Arrange) | Entrada / pasos (Act) | Resultado esperado (Assert) | Resultado obtenido | Prioridad | Técnica |
|---|---|---|---|---|---|---|---|---|---|
| CPU-22 | `loadUserByUsername_usuarioExistente_devuelveUserDetailsConHashYRolCorrectos` | `CustomUserDetailsService.loadUserByUsername` | ISO27001 A.5.17 (información de autenticación) · ASVS V2.4.1 | Stub `findByCorreo("lucia@test.com")` con hash `"$2a$10$hashSimuladoDePrueba"` y rol `"BIBLIOTECARIO"` | `loadUserByUsername("lucia@test.com")` | `UserDetails` con `username`, `password` (hash) y única `authority="BIBLIOTECARIO"` idénticos a los datos del repositorio | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-23 | `loadUserByUsername_usuarioInexistente_lanzaUsernameNotFoundException` | `CustomUserDetailsService.loadUserByUsername` | ASVS V2.2.2 (no revelar existencia de cuenta mediante excepción distinta) | Stub `findByCorreo("fantasma@test.com")→Optional.empty()` | `loadUserByUsername("fantasma@test.com")` | Lanza `UsernameNotFoundException` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |

## 1.3 `LibroServiceTest` (CPU-24 a CPU-42)

Componente bajo prueba: `service/LibroService`. Repositorios mockeados: `LibroRepository`,
`UsuarioRepository`.

| ID | Caso de prueba | Método de producción | Objetivo | Precondiciones (Arrange) | Entrada / pasos (Act) | Resultado esperado (Assert) | Resultado obtenido | Prioridad | Técnica |
|---|---|---|---|---|---|---|---|---|---|
| CPU-24 | `registrarLibroConImagen_conRolBibliotecario_persisteLibro` | `LibroService.registrarLibroConImagen` | ISO27001 A.8.2 · ASVS V4.1.1/V4.1.3 (autorización en capa de servicio) | Operador con rol `"BIBLIOTECARIO"`; ISBN `1234567890123` no existe aún | `registrarLibroConImagen(libro, null, "op@test.com")` | Devuelve `"Libro registrado exitosamente."`; el libro guardado conserva el ISBN | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-25 | `registrarLibroConImagen_conRolNoBibliotecario_lanzaAccessDeniedException` — rol `"USUARIO"` | `LibroService.registrarLibroConImagen` | ISO27001 A.8.2 · ASVS V4.1.3 (mínimo privilegio) | Operador con rol `"USUARIO"` | `registrarLibroConImagen(libro, null, "op@test.com")` | Lanza `AccessDeniedException`; `save()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-26 | ídem, rol `"ADMIN"` | `LibroService.registrarLibroConImagen` | (mismo objetivo que CPU-25) | (mismo, rol `"ADMIN"`) | (mismo) | (mismo) | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-27 | ídem, rol `"bibliotecario"` (minúsculas) | `LibroService.registrarLibroConImagen` | (mismo objetivo que CPU-25; confirma sensibilidad a mayúsculas/minúsculas del rol) | (mismo, rol `"bibliotecario"`) | (mismo) | (mismo) | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-28 | `registrarLibroConImagen_isbnDuplicado_rechazaYNoPersiste` | `LibroService.registrarLibroConImagen` | ISO25010 Integridad (no duplicar identificadores únicos de negocio) | Ya existe un libro con ISBN `1234567890123` | `registrarLibroConImagen(libro, null, "op@test.com")` | Devuelve `"El libro con ese ISBN ya existe."`; `save()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-29 | `registrarLibroConImagen_cantidadNoPositiva_rechazaYNoPersiste` — cantidad `0` | `LibroService.registrarLibroConImagen` | ASVS V5.1.3 (validación de entrada positiva: cantidad > 0) | Operador `"BIBLIOTECARIO"`; libro con `cantidad=0` | `registrarLibroConImagen(libro, null, "op@test.com")` | Devuelve `"Debe haber al menos una copia física del libro."`; `save()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Valor límite |
| CPU-30 | ídem, cantidad `-1` | `LibroService.registrarLibroConImagen` | (mismo objetivo que CPU-29) | (mismo, `cantidad=-1`) | (mismo) | (mismo) | 🟢 Verde | N/A — control ya conforme | Valor límite |
| CPU-31 | ídem, cantidad `-100` | `LibroService.registrarLibroConImagen` | (mismo objetivo que CPU-29) | (mismo, `cantidad=-100`) | (mismo) | (mismo) | 🟢 Verde | N/A — control ya conforme | Valor límite |
| CPU-32 | `registrarLibroConImagen_DEFECTO_isbnNegativoBurlaValidacionDeLongitud` | `LibroService.registrarLibroConImagen` | ASVS V5.1.3 (validación de formato del ISBN) | Operador `"BIBLIOTECARIO"`; ISBN `-123456789012` (el signo `-` ocupa el lugar de un dígito, `String.valueOf(isbn).length()==13`) | `registrarLibroConImagen(libro, null, "op@test.com")` | Un ISBN negativo nunca debería persistirse; `save()` no debería invocarse | 🔴 Rojo — **DEF-11**: la validación `length()==13` no distingue el signo negativo de un dígito y deja pasar el ISBN inválido | Media (reporte de origen: Baja/Media) | Valor límite + basada en defectos |
| CPU-33 | `actualizarLibroPorIsbn_conRolBibliotecario_actualizaCamposPermitidos` | `LibroService.actualizarLibroPorIsbn` | ISO27001 A.8.2 · ASVS V4.1.1 | Operador `"BIBLIOTECARIO"`; libro existente con ISBN `1234567890123` | `actualizarLibroPorIsbn(1234567890123L, datosNuevos{titulo="Nuevo Titulo", cantidad=9}, "op@test.com")` | Devuelve `"Libro actualizado exitosamente."`; el libro guardado tiene `titulo="Nuevo Titulo"` y `cantidad=9` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-34 | `actualizarLibroPorIsbn_conRolNoBibliotecario_lanzaAccessDeniedException` — rol `"USUARIO"` | `LibroService.actualizarLibroPorIsbn` | ISO27001 A.8.2 · ASVS V4.1.3 | Operador con rol `"USUARIO"` | `actualizarLibroPorIsbn(1234567890123L, datosNuevos, "op@test.com")` | Lanza `AccessDeniedException`; `save()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-35 | ídem, rol `""` (vacío) | `LibroService.actualizarLibroPorIsbn` | (mismo objetivo que CPU-34) | (mismo, rol `""`) | (mismo) | (mismo) | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-36 | `actualizarLibroPorIsbn_DEFECTO_permiteCantidadNegativa` — cantidad `-1` | `LibroService.actualizarLibroPorIsbn` | ISO25010 Integridad · ASVS V5.1.3 (validación server-side consistente en todos los endpoints) | Operador `"BIBLIOTECARIO"`; libro existente; `datosNuevos.cantidad=-1` | `actualizarLibroPorIsbn(1234567890123L, datosNuevos, "op@test.com")` | La cantidad negativa nunca debería persistirse; `save()` no debería invocarse | 🔴 Rojo — **DEF-12**: `actualizarLibroPorIsbn` copia `cantidad` sin repetir la validación `>0` que sí existe en el alta | Media (reporte de origen: Baja/Media) | Valor límite + basada en defectos |
| CPU-37 | ídem, cantidad `-50` | `LibroService.actualizarLibroPorIsbn` | (mismo objetivo que CPU-36) | (mismo, `cantidad=-50`) | (mismo) | (mismo) | 🔴 Rojo — DEF-12 | Media (reporte de origen: Baja/Media) | Valor límite + basada en defectos |
| CPU-38 | `actualizarLibroPorIsbn_libroNoEncontrado_lanzaExcepcion` | `LibroService.actualizarLibroPorIsbn` | ISO25010 Integridad (no operar sobre recursos inexistentes) | ISBN `999` no existe | `actualizarLibroPorIsbn(999L, datosNuevos, "op@test.com")` | Lanza `RuntimeException` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-39 | `eliminarLibroPorIsbn_conRolBibliotecario_eliminaLibro` | `LibroService.eliminarLibroPorIsbn` | ISO27001 A.8.2 · ASVS V4.1.1 | Operador `"BIBLIOTECARIO"`; libro existente | `eliminarLibroPorIsbn(1234567890123L, "op@test.com")` | Devuelve `"Libro eliminado correctamente."`; se invoca `delete()` sobre el libro | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-40 | `eliminarLibroPorIsbn_conRolNoBibliotecario_lanzaAccessDeniedException` — rol `"USUARIO"` | `LibroService.eliminarLibroPorIsbn` | ISO27001 A.8.2 · ASVS V4.1.3 | Operador con rol `"USUARIO"` | `eliminarLibroPorIsbn(1234567890123L, "op@test.com")` | Lanza `AccessDeniedException`; `delete()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-41 | ídem, rol `"ADMIN"` | `LibroService.eliminarLibroPorIsbn` | (mismo objetivo que CPU-40) | (mismo, rol `"ADMIN"`) | (mismo) | (mismo) | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-42 | `eliminarLibroPorIsbn_libroNoEncontrado_lanzaExcepcion` | `LibroService.eliminarLibroPorIsbn` | ISO25010 Integridad | ISBN `999` no existe | `eliminarLibroPorIsbn(999L, "op@test.com")` | Lanza `RuntimeException` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |

## 1.4 `PrestamoServiceTest` (CPU-43 a CPU-60)

Componente bajo prueba: `service/PrestamoService`. Repositorios mockeados: `PrestamoRepository`,
`UsuarioRepository`, `LibroRepository`, `AmonestacionRepository`.

| ID | Caso de prueba | Método de producción | Objetivo | Precondiciones (Arrange) | Entrada / pasos (Act) | Resultado esperado (Assert) | Resultado obtenido | Prioridad | Técnica |
|---|---|---|---|---|---|---|---|---|---|
| CPU-43 | `crearPrestamo_conRolUsuarioYStockDisponible_registraYDecrementaStock` | `PrestamoService.crearPrestamo` | ISO27001 A.8.2 · ASVS V4.1.1/V4.1.3 | Usuario `id=1`, rol `"USUARIO"`; 0 préstamos activos; sin amonestaciones; libro ISBN `111` con `cantidad=3` | `crearPrestamo("user@test.com", 111L, "2026-07-19")` | Devuelve mensaje que empieza con `"Préstamo registrado"`; se guarda el préstamo; el libro guardado queda con `cantidad=2` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-44 | `crearPrestamo_conRolNoUsuario_rechazaYNoPersiste` — rol `"BIBLIOTECARIO"` | `PrestamoService.crearPrestamo` | ISO27001 A.8.2 · ASVS V4.1.3 (mínimo privilegio: solo rol USUARIO pide préstamos) | Usuario con rol `"BIBLIOTECARIO"` | `crearPrestamo("user@test.com", 111L, "2026-07-19")` | Devuelve `"Solo se pueden asociar préstamos a usuarios con rol USUARIO."`; `save()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-45 | ídem, rol `"ADMIN"` | `PrestamoService.crearPrestamo` | (mismo objetivo que CPU-44) | (mismo, rol `"ADMIN"`) | (mismo) | (mismo) | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-46 | ídem, rol `""` | `PrestamoService.crearPrestamo` | (mismo objetivo que CPU-44) | (mismo, rol `""`) | (mismo) | (mismo) | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-47 | `crearPrestamo_conDosPrestamosActivos_rechazaYNoPersiste` | `PrestamoService.crearPrestamo` | ASVS V11.1 (límite de negocio: máximo de préstamos simultáneos) | Usuario con 2 préstamos activos ya registrados | `crearPrestamo("user@test.com", 111L, "2026-07-19")` | Devuelve `"El usuario ya tiene 2 préstamos activos."`; `save()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Valor límite |
| CPU-48 | `crearPrestamo_conAmonestacionesPendientes_rechazaYNoPersiste` | `PrestamoService.crearPrestamo` | ASVS V11.1 (amonestaciones pendientes bloquean nuevos préstamos) | Usuario con amonestación no verificada pendiente | `crearPrestamo("user@test.com", 111L, "2026-07-19")` | Devuelve `"El usuario tiene amonestaciones activas."`; `save()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-49 | `crearPrestamo_libroSinStock_rechazaYNoPersiste` | `PrestamoService.crearPrestamo` | ASVS V11.1 (no permitir préstamo de un libro sin stock) | Libro ISBN `111` con `cantidad=0` | `crearPrestamo("user@test.com", 111L, "2026-07-19")` | Devuelve `"Libro no disponible para préstamo."`; `save()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Valor límite |
| CPU-50 | `crearPrestamo_usuarioNoRegistrado_rechaza` | `PrestamoService.crearPrestamo` | ISO25010 Integridad (no operar sobre identidades inexistentes) | `findByCorreo("fantasma@test.com")→Optional.empty()` | `crearPrestamo("fantasma@test.com", 111L, "2026-07-19")` | Devuelve `"Usuario no registrado."` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-51 | `devolverPrestamo_yaDevuelto_rechazaYNoDuplicaEfectos` | `PrestamoService.devolverPrestamo` | ASVS V11.1 (no permitir doble devolución del mismo préstamo) | Préstamo ya con `fechaDevolucion` seteada | `devolverPrestamo(1)` | Devuelve `"El préstamo ya fue devuelto."`; `libroRepository.save()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-52 | `devolverPrestamo_vencido_generaAmonestacion` | `PrestamoService.devolverPrestamo` | ISO25010 Integridad · ASVS V11.1 (mora genera sanción) | Préstamo con `fechaLimite` 3 días en el pasado | `devolverPrestamo(1)` | Se guarda una `Amonestacion` con `monto=100.0` y `pagada=false` | 🟢 Verde | N/A — control ya conforme | Valor límite |
| CPU-53 | `devolverPrestamo_aTiempo_noGeneraAmonestacion` | `PrestamoService.devolverPrestamo` | ISO25010 Integridad (no sancionar devoluciones a tiempo) | Préstamo con `fechaLimite` 3 días en el futuro | `devolverPrestamo(1)` | `amonestacionRepository.save()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Valor límite |
| CPU-54 | `renovarPrestamo_conRolNoBibliotecario_rechaza` — rol `"USUARIO"` | `PrestamoService.renovarPrestamo` | ISO27001 A.8.2 · ASVS V4.1.1 (rol resuelto server-side) | Ninguna (el rechazo ocurre antes de tocar el repositorio) | `renovarPrestamo(1, "USUARIO")` | Devuelve `"Solo los bibliotecarios pueden renovar préstamos."`; `findById()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-55 | ídem, rol `""` | `PrestamoService.renovarPrestamo` | (mismo objetivo que CPU-54) | (mismo, rol `""`) | (mismo) | (mismo) | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-56 | `renovarPrestamo_noEncontrado_rechaza` | `PrestamoService.renovarPrestamo` | ISO25010 Integridad | `findById(99)→Optional.empty()` | `renovarPrestamo(99, "BIBLIOTECARIO")` | Devuelve `"Préstamo no encontrado."` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-57 | `renovarPrestamo_noFinalizado_rechaza` | `PrestamoService.renovarPrestamo` | ASVS V11.1 (solo se renuevan préstamos finalizados) | Préstamo con `fechaDevolucion=null` | `renovarPrestamo(1, "BIBLIOTECARIO")` | Devuelve `"Solo se pueden renovar préstamos finalizados."` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-58 | `renovarPrestamo_conAmonestacionesPendientes_rechaza` | `PrestamoService.renovarPrestamo` | ASVS V11.1 (no renovar con sanciones pendientes de verificación) | Préstamo finalizado; amonestación no verificada pendiente | `renovarPrestamo(1, "BIBLIOTECARIO")` | Devuelve mensaje de rechazo por amonestaciones pendientes; `save()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-59 | `renovarPrestamo_conStockDisponible_actualizaFechaLimiteYEstado` | `PrestamoService.renovarPrestamo` | ISO25010 Integridad (estado consistente tras renovación) | Préstamo finalizado; libro con `cantidad=5`; sin amonestaciones pendientes | `renovarPrestamo(1, "BIBLIOTECARIO")` | Devuelve mensaje que empieza con `"Préstamo renovado con éxito"`; el préstamo guardado tiene `estado="activo"` y `fechaDevolucion=null` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-60 | `renovarPrestamo_DEFECTO_decrementaStockSinVerificarDisponibilidad` | `PrestamoService.renovarPrestamo` | ISO25010 Integridad · ASVS V11.1.4 (límites de negocio aplicados de forma consistente) | Préstamo finalizado; libro con `cantidad=0`; sin amonestaciones pendientes | `renovarPrestamo(1, "BIBLIOTECARIO")` | El stock del libro nunca debería quedar negativo (`cantidad >= 0`) | 🔴 Rojo — **DEF-13**: decrementa `cantidad-1` sin comprobar disponibilidad; el libro queda con `cantidad=-1` | Media | Valor límite + basada en defectos |

## 1.5 `AmonestacionServiceTest` (CPU-61 a CPU-65)

Componente bajo prueba: `service/AmonestacionService`. Repositorio mockeado:
`AmonestacionRepository`.

| ID | Caso de prueba | Método de producción | Objetivo | Precondiciones (Arrange) | Entrada / pasos (Act) | Resultado esperado (Assert) | Resultado obtenido | Prioridad | Técnica |
|---|---|---|---|---|---|---|---|---|---|
| CPU-61 | `eliminarAmonestacion_conRolBibliotecario_elimina` | `AmonestacionService.eliminarAmonestacion` | ISO27001 A.8.2 · ASVS V4.1.1/V4.1.3 (rol resuelto server-side por el controller) | `existsById(5)→true` | `eliminarAmonestacion(5, "BIBLIOTECARIO")` | Devuelve `"Amonestación eliminada con éxito."`; se invoca `deleteById(5)` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-62 | `eliminarAmonestacion_conRolNoBibliotecario_rechazaYNoConsultaRepositorio` — rol `"USUARIO"` | `AmonestacionService.eliminarAmonestacion` | ISO27001 A.8.2 · ASVS V4.1.3 (mínimo privilegio) | Ninguna (el rechazo ocurre antes de tocar el repositorio) | `eliminarAmonestacion(5, "USUARIO")` | Devuelve `"Solo los bibliotecarios pueden eliminar amonestaciones."`; ni `existsById` ni `deleteById` se invocan | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-63 | ídem, rol `""` | `AmonestacionService.eliminarAmonestacion` | (mismo objetivo que CPU-62) | (mismo, rol `""`) | (mismo) | (mismo) | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-64 | ídem, rol `"admin"` (minúsculas) | `AmonestacionService.eliminarAmonestacion` | (mismo objetivo que CPU-62; confirma sensibilidad a mayúsculas/minúsculas) | (mismo, rol `"admin"`) | (mismo) | (mismo) | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-65 | `eliminarAmonestacion_noEncontrada_rechaza` | `AmonestacionService.eliminarAmonestacion` | ISO25010 Integridad (no operar sobre recursos inexistentes) | `existsById(404)→false` | `eliminarAmonestacion(404, "BIBLIOTECARIO")` | Devuelve `"Amonestación no encontrada."`; `deleteById()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |

## 1.6 `ControllerAuthorizationTest` (CPU-66 a CPU-79)

Componente bajo prueba: `controller/Controller`. Instanciación pura (`new Controller()`) con
inyección de mocks por reflexión (`java.lang.reflect.Field`), sin contenedor de Spring; los
defectos por ausencia de parámetro de seguridad se prueban por introspección de firma
(`Method.getParameterTypes()`), no con mocks.

| ID | Caso de prueba | Método de producción | Objetivo | Precondiciones (Arrange) | Entrada / pasos (Act) | Resultado esperado (Assert) | Resultado obtenido | Prioridad | Técnica |
|---|---|---|---|---|---|---|---|---|---|
| CPU-66 | `editarResena_usuarioDistintoAlAutor_devuelve403` | `Controller.editarResena` | ASVS V4.2.1 (protección contra IDOR/BOLA en edición de recurso ajeno) | Reseña `id=10` con autor `usuario id=1`; autenticado `usuario id=2` (atacante) | `editarResena(10, {"texto":"hackeado"}, authentication)` | Código de estado `403`; `guardar()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-67 | `editarResena_usuarioAutor_permiteEdicion` | `Controller.editarResena` | ASVS V4.2.1 (control positivo: el dueño legítimo sí puede editar) | Reseña `id=10` con autor `usuario id=1`; autenticado el mismo autor | `editarResena(10, {"texto":"actualizado"}, authentication)` | Código de estado `200`; se invoca `guardar(resena)`; `texto` queda actualizado | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-68 | `eliminarResena_usuarioDistintoAlAutor_devuelve403` | `Controller.eliminarResena` | ASVS V4.2.1 | Reseña `id=10` con autor distinto al autenticado | `eliminarResena(10, authentication)` | Código de estado `403`; `eliminar()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-69 | `editarComentarioResena_usuarioDistintoAlAutor_devuelve403` | `Controller.editarComentarioResena` | ASVS V4.2.1 | Comentario `id=20` con autor distinto al autenticado | `editarComentarioResena(20, {"texto":"hack"}, authentication)` | Código de estado `403`; `guardar()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-70 | `eliminarComentarioResena_usuarioDistintoAlAutor_devuelve403` | `Controller.eliminarComentarioResena` | ASVS V4.2.1 | Comentario `id=20` con autor distinto al autenticado | `eliminarComentarioResena(20, authentication)` | Código de estado `403`; `eliminar()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-71 | `pagarAmonestacion_usuarioDistintoAlPropietario_devuelve403` | `Controller.pagarAmonestacion` | ASVS V4.2.1 | Amonestación `id=30` con propietario `usuario id=1`; autenticado `usuario id=2` | `pagarAmonestacion(authentication, {amonestacionId:30,...})` | Código de estado `403`; `guardar()` nunca se invoca | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-72 | `pagarAmonestacion_usuarioPropietario_permitePago` | `Controller.pagarAmonestacion` | ASVS V4.2.1 (control positivo) | Amonestación `id=30` con propietario igual al autenticado | `pagarAmonestacion(authentication, {amonestacionId:30,...})` | Código de estado `200`; se invoca `guardar()`; `amonestacion.isPagada()==true` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-73 | `verificarAmonestacion_DEFECTO_metodoNoValidaRolNiPropietario` | `Controller.verificarAmonestacion` | ISO27001 A.8.2/A.5.15 · ASVS V4.1.3 (mínimo privilegio server-side) | Ninguna — introspección pura de la firma con `Controller.class.getMethod("verificarAmonestacion", Integer.class)` | Se inspeccionan los tipos de parámetro del método | El método debería declarar un parámetro `Authentication` para poder validar rol `BIBLIOTECARIO` | 🔴 Rojo — **DEF-17 (B7)**: no recibe `Authentication`; cualquier usuario autenticado puede verificar amonestaciones ajenas | Alta | Basada en defectos |
| CPU-74 | `getTodasAmonestaciones_DEFECTO_metodoNoValidaRol` | `Controller.getTodasAmonestaciones` | ISO27001 A.8.2/A.8.3 · ASVS V4.1.3 | Ninguna — introspección pura de `Controller.class.getMethod("getTodasAmonestaciones")` | Se inspeccionan los tipos de parámetro del método | El método debería declarar un parámetro `Authentication` | 🔴 Rojo — **DEF-18 (B8)**: no recibe `Authentication`; expone amonestaciones de todos los usuarios a cualquier autenticado | Alta | Basada en defectos |
| CPU-75 | `crearResena_DEFECTO_noCruzaUsuarioIdConAutenticado` | `Controller.crearResena` | ISO25010 Autenticidad · ASVS V4.2.1 (IDOR en creación, no solo en edición/borrado) | Ninguna — introspección pura de `Controller.class.getMethod("crearResena", ResenaRequest.class)` | Se inspeccionan los tipos de parámetro del método | El método debería declarar `Authentication` para cruzar el `usuarioId` con la sesión | 🔴 Rojo — **DEF-15**: usa `request.getUsuarioId()` del body sin validar contra el autenticado | Alta | Basada en defectos |
| CPU-76 | `crearComentarioResena_DEFECTO_noCruzaUsuarioIdConAutenticado` | `Controller.crearComentarioResena` | ISO25010 Autenticidad · ASVS V4.2.1 | Ninguna — introspección pura de `Controller.class.getMethod("crearComentarioResena", ComentarioResenaRequest.class)` | Se inspeccionan los tipos de parámetro del método | (mismo objetivo que CPU-75) | 🔴 Rojo — **DEF-16**: mismo patrón que `crearResena`, en comentarios | Alta | Basada en defectos |
| CPU-77 | `loginUsuario_credencialesValidas_noExponeContrasenaEnRespuesta` | `Controller.loginUsuario` | ISO25010 Confidencialidad · ISO27001 A.8.3 · ASVS V8.3.4 | Stub `autenticarYObtenerUsuario` devuelve un `Usuario` con hash seteado | `loginUsuario(credenciales)` | Código de estado `200`; el cuerpo de la respuesta tiene `contrasena==null` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-78 | `loginUsuario_credencialesInvalidas_devuelve401MensajeGenerico` | `Controller.loginUsuario` | ASVS V2.2.2 (mensaje genérico, no distingue causa del fallo) | Stub `autenticarYObtenerUsuario→null` | `loginUsuario(credenciales)` | Código de estado `401`; cuerpo `"Credenciales inválidas"` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |
| CPU-79 | `getUsuarioAutenticado_devuelveUsuarioSinContrasena` | `Controller.getUsuarioAutenticado` | ISO25010 Confidencialidad · ASVS V8.3.4 | Usuario autenticado con hash seteado | `getUsuarioAutenticado(authentication)` | El cuerpo de la respuesta tiene `contrasena==null` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |

## 1.7 `ModelSerializationTest` (CPU-80 a CPU-82)

Componente bajo prueba: modelos `Usuario`, `Prestamo`, `Amonestacion`. Serialización con
`com.fasterxml.jackson.databind.ObjectMapper` puro (sin contexto Spring), `JavaTimeModule`
registrado a mano.

| ID | Caso de prueba | Método de producción | Objetivo | Precondiciones (Arrange) | Entrada / pasos (Act) | Resultado esperado (Assert) | Resultado obtenido | Prioridad | Técnica |
|---|---|---|---|---|---|---|---|---|---|
| CPU-80 | `usuario_DEFECTO_serializacionExponeHashDeContrasena` | `Usuario` (serialización JSON) | ISO25010 Confidencialidad · ISO27001 A.8.3 · ASVS V8.3.4 (minimización de exposición de datos sensibles) | `Usuario` con `contrasena="$2a$10$hashSuperSecreto1234567890"` | `objectMapper.writeValueAsString(usuario)` | El JSON nunca debería contener el hash de la contraseña | 🔴 Rojo — **DEF-01**: `Usuario.contrasena` no tiene `@JsonIgnore`; se serializa igual que cualquier otro campo | Media | Basada en defectos |
| CPU-81 | `prestamo_DEFECTO_serializacionExponeHashDeContrasenaDelUsuarioAnidado` | `Prestamo` (serialización JSON) | ISO25010 Confidencialidad · ISO27001 A.8.3 · ASVS V8.3.4 | `Prestamo` con `usuario` anidado (mismo hash de contraseña) y `libro` | `objectMapper.writeValueAsString(prestamo)` | El JSON nunca debería contener el hash del usuario anidado | 🔴 Rojo — **DEF-02**: `Prestamo.usuario` no tiene `@JsonIgnoreProperties` (a diferencia de `Amonestacion.usuario`); filtra el hash en `GET /api/prestamos` | Media | Basada en defectos |
| CPU-82 | `amonestacion_serializacionOcultaContrasenaDelUsuarioAnidado` | `Amonestacion` (serialización JSON) | ISO25010 Confidencialidad · ISO27001 A.8.3 (control positivo) | `Amonestacion` con `usuario` anidado (mismo hash de contraseña) | `objectMapper.writeValueAsString(amonestacion)` | El JSON no debería contener el hash del usuario anidado | 🟢 Verde | N/A — control ya conforme (confirma que `@JsonIgnoreProperties({"contrasena"})` sí protege este caso) | Partición de equivalencia |

---

# 2. Casos de Prueba de Integración

**Alcance:** JUnit 5 + `@WebMvcTest`/`@SpringBootTest` + MockMvc + `spring-security-test`,
verificando lo que solo el contexto real de Spring puede exponer (reglas de `SecurityConfig` a
nivel de filtro HTTP, CSRF, Bean Validation). 4 clases, **21 métodos, 29 ejecuciones**. Base de
datos: H2 en memoria (`@ActiveProfiles("test")`); 3 de 4 clases mockean los 6 servicios con
`@MockitoBean` y no cargan `DataSource`.

## 2.1 `SecurityConfigAccessRulesIntegrationTest` (CPI-01 a CPI-13)

Tipo: `@WebMvcTest(Controller.class)` + `@Import(SecurityConfig.class)`. Técnica dominante: **tabla
de decisión** (rol × ruta × método HTTP), evaluada contra las reglas reales de
`SecurityFilterChain`.

| ID | Caso de prueba | Ruta / regla de `SecurityConfig` bajo prueba | Objetivo | Precondiciones (Arrange) | Entrada / pasos (Act) | Resultado esperado (Assert) | Resultado obtenido | Prioridad | Técnica |
|---|---|---|---|---|---|---|---|---|---|
| CPI-01 | `getLibros_sinAutenticar_esAccesible_permitAll` | `GET /api/libros` (`permitAll`) | ASVS V4.1.1 (la regla configurada se cumple tal como está declarada) | `@WithAnonymousUser`; sin autenticación | `mockMvc.perform(get("/api/libros"))` | Código de estado `200 OK` | 🟢 Verde | N/A — control ya conforme | Tabla de decisión |
| CPI-02 | `postRegistroUsuario_sinAutenticar_esAccesible_permitAll` | `POST /api/usuarios/registro` (`permitAll`) | ASVS V4.1.1 | `@WithAnonymousUser`; body JSON con nombre/correo/contraseña/rol válidos | `mockMvc.perform(post("/api/usuarios/registro").with(csrf())...)` | Código de estado `200 OK` | 🟢 Verde | N/A — control ya conforme | Tabla de decisión |
| CPI-03 | `getResenasPorLibro_sinAutenticar_esAccesible_permitAllSoloGet` | `GET /api/resenas/libro/{id}` (`permitAll` solo en GET) | ASVS V4.1.1 | `@WithAnonymousUser` | `mockMvc.perform(get("/api/resenas/libro/1"))` | Código de estado `200 OK` | 🟢 Verde | N/A — control ya conforme | Tabla de decisión |
| CPI-04 | `postResenas_sinAutenticar_esRechazada` | `POST /api/resenas` (`authenticated()`) | ASVS V4.1.1 (rechazo de escritura anónima en ruta protegida) | `@WithAnonymousUser`; body JSON de reseña | `mockMvc.perform(post("/api/resenas").with(csrf())...)` | Redirección `3xx` (con `formLogin` configurado, el acceso anónimo a ruta protegida no da 401/403 directo sino redirect a login; en cualquier caso no se concede acceso) | 🟢 Verde | N/A — control ya conforme | Tabla de decisión |
| CPI-05 | `postResenas_autenticadoConCualquierRol_esAceptada` — rol `"USUARIO"` | `POST /api/resenas` (`authenticated()`, sin rol específico) | ASVS V4.1.1 (cualquier rol autenticado basta) | Usuario autenticado `cliente@test.com` con authority `"USUARIO"` | `mockMvc.perform(post("/api/resenas").with(csrf()).with(user(...).authorities("USUARIO"))...)` | Código de estado `200 OK` | 🟢 Verde | N/A — control ya conforme | Tabla de decisión |
| CPI-06 | ídem, rol `"BIBLIOTECARIO"` | `POST /api/resenas` | (mismo objetivo que CPI-05) | (mismo, authority `"BIBLIOTECARIO"`) | (mismo) | Código de estado `200 OK` | 🟢 Verde | N/A — control ya conforme | Tabla de decisión |
| CPI-07 | `postPrestar_conRolUsuario_esRechazadaPorElFiltro` | `POST /api/prestar` (`hasAuthority("BIBLIOTECARIO")`) | ISO27001 A.8.2 · ASVS V4.1.3 (mínimo privilegio aplicado por el filtro HTTP) | `@WithMockUser(authorities="USUARIO")` | `mockMvc.perform(post("/api/prestar").with(csrf())...)` | Código de estado `403 Forbidden` | 🟢 Verde | N/A — control ya conforme | Tabla de decisión |
| CPI-08 | `postPrestar_conRolBibliotecario_esAceptadaPorElFiltro` | `POST /api/prestar` | ISO27001 A.8.2 · ASVS V4.1.1 | `@WithMockUser(authorities="BIBLIOTECARIO")`; stub `crearPrestamo(...)→"Préstamo registrado con éxito."` | `mockMvc.perform(post("/api/prestar").with(csrf())...)` | Código de estado `200 OK` | 🟢 Verde | N/A — control ya conforme | Tabla de decisión |
| CPI-09 | `getPrestamos_conRolUsuario_esRechazadaPorElFiltro` | `GET /api/prestamos` (`hasAuthority("BIBLIOTECARIO")`) | ISO27001 A.8.2 · ASVS V4.1.3 | `@WithMockUser(authorities="USUARIO")` | `mockMvc.perform(get("/api/prestamos"))` | Código de estado `403 Forbidden` | 🟢 Verde | N/A — control ya conforme | Tabla de decisión |
| CPI-10 | `deleteLibroPorIsbn_DEFECTO_reglaPermitAllPrevaleceSobreReglaDeRolEspecifica` | `DELETE /api/libros/isbn/{isbn}` | ISO27001 A.8.2 · ASVS V4.1.1 | `@WithMockUser(authorities="USUARIO")`; stub `eliminarLibroPorIsbn(...)→"Libro eliminado correctamente."` | `mockMvc.perform(delete("/api/libros/isbn/1234567890123").with(csrf()))` | Sin rol `BIBLIOTECARIO` debería responder `403` | 🔴 Rojo — **DEF-14**: `.requestMatchers("/api/libros","/api/libros/**").permitAll()` se declara antes que la regla de rol para `DELETE`; Spring Security aplica la primera coincidencia y la regla de rol queda inalcanzable | Alta | Tabla de decisión + basada en defectos |
| CPI-11 | `putVerificarAmonestacion_DEFECTO_rolUsuarioNoDeberiaPoderVerificar` | `PUT /api/amonestaciones-usuario/verificar/{id}` | ISO27001 A.8.2, A.5.15 · ASVS V4.1.3 | `@WithMockUser(authorities="USUARIO")`; stub `findById(1)` devuelve una amonestación | `mockMvc.perform(put("/api/amonestaciones-usuario/verificar/1").with(csrf()))` | Sin rol `BIBLIOTECARIO` debería responder `403` | 🔴 Rojo — **DEF-17 (B7)**: la ruta solo cae en el catch-all `.anyRequest().authenticated()`, sin regla de rol propia | Alta | Tabla de decisión + basada en defectos |
| CPI-12 | `getTodasAmonestaciones_DEFECTO_rolUsuarioNoDeberiaVerTodas` | `GET /api/amonestaciones-usuario/todas` | ISO27001 A.8.2, A.8.3 · ASVS V4.1.3 | `@WithMockUser(authorities="USUARIO")` | `mockMvc.perform(get("/api/amonestaciones-usuario/todas"))` | Sin rol `BIBLIOTECARIO` debería responder `403` | 🔴 Rojo — **DEF-18 (B8)**: mismo patrón que CPI-11; expone datos financieros de todos los usuarios | Alta | Tabla de decisión + basada en defectos |
| CPI-13 | `postLogin_conCuerpoJson_DEFECTO_nuncaInvocaAlControllerReal` | `POST /api/login` | ISO27001 A.5.17, A.8.26 · ASVS V2.2.2, V4.1.1 (consistencia entre el código revisado y el flujo realmente expuesto) | `@WithAnonymousUser`; body JSON `{"correo":...,"contrasena":...}` | `mockMvc.perform(post("/api/login").with(csrf())...)` | El JSON enviado debería procesarse en `Controller.loginUsuario`, invocando `usuarioService.autenticarYObtenerUsuario(...)` | 🔴 Rojo — **DEF-19**: `formLogin().loginProcessingUrl("/api/login")` intercepta cualquier POST a esa URL antes del `DispatcherServlet` y espera parámetros de formulario, no JSON; `loginUsuario` es código muerto en producción | Alta | Tabla de decisión + basada en defectos |

## 2.2 `CsrfProtectionIntegrationTest` (CPI-14 a CPI-15)

Tipo: `@WebMvcTest(Controller.class)` + `@Import(SecurityConfig.class)`.

| ID | Caso de prueba | Ruta bajo prueba | Objetivo | Precondiciones (Arrange) | Entrada / pasos (Act) | Resultado esperado (Assert) | Resultado obtenido | Prioridad | Técnica |
|---|---|---|---|---|---|---|---|---|---|
| CPI-14 | `postResenas_DEFECTO_sinTokenCsrfDeberiaSerRechazada` | `POST /api/resenas` | ISO25010 Integridad · ISO27001 A.8.26 · ASVS V4.2.2 (anti-CSRF en apps basadas en sesión/cookie) | `@WithMockUser(authorities="USUARIO", username="cliente@test.com")`; stub `resenaService.save(...)`; **sin** `.with(csrf())` deliberadamente | `mockMvc.perform(post("/api/resenas")...)` sin token CSRF | Sin token CSRF debería responder `403 Forbidden` | 🔴 Rojo — **DEF-05**: `.csrf(csrf -> csrf.disable())` elimina el `CsrfFilter`; una escritura autenticada sin token se acepta igual | Media | Partición de equivalencia + basada en defectos |
| CPI-15 | `postResenas_conTokenCsrfExplicito_esAceptada` | `POST /api/resenas` | ASVS V4.2.2 (control positivo) | Mismo usuario mockeado; **con** `.with(csrf())` | `mockMvc.perform(post("/api/resenas").with(csrf())...)` | Código de estado `200 OK` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |

## 2.3 `BeanValidationRequestBodyIntegrationTest` (CPI-16 a CPI-27)

Tipo: `@WebMvcTest(Controller.class)` + `@Import(SecurityConfig.class)`. Todas las filas de esta
clase están en rojo: ningún `@RequestBody` de los 5 evaluados tiene `@Valid` ni anotaciones de
Bean Validation.

| ID | Caso de prueba | DTO / entidad bajo prueba | Objetivo | Precondiciones (Arrange) | Entrada / pasos (Act) | Resultado esperado (Assert) | Resultado obtenido | Prioridad | Técnica |
|---|---|---|---|---|---|---|---|---|---|
| CPI-16 | `postRegistroUsuario_DEFECTO_aceptaPayloadInvalido` — `nombre` vacío | `Usuario` vía `POST /api/usuarios/registro` | ASVS V5.1.3 (validación de entrada positiva) · ISO27001 A.8.26 | Stub `registrarUsuario(any())→"Usuario registrado con éxito."` | `POST /api/usuarios/registro` con `{"nombre":"","correo":"ana@test.com","contrasena":"ClaveSegura123456","rol":"USUARIO"}` | Payload inválido debería responder `400 Bad Request` | 🔴 Rojo — **DEF-06**: sin `@NotBlank`/`@Email` ni `@Valid`, se acepta cualquier dato | Media | Partición de equivalencia + basada en defectos |
| CPI-17 | ídem, `correo` vacío | `Usuario` | (mismo objetivo que CPI-16) | (mismo) | `{"nombre":"Ana","correo":"","contrasena":"ClaveSegura123456","rol":"USUARIO"}` | (mismo) | 🔴 Rojo — DEF-06 | Media | Partición de equivalencia + basada en defectos |
| CPI-18 | ídem, `correo` con formato inválido | `Usuario` | (mismo objetivo que CPI-16) | (mismo) | `{"nombre":"Ana","correo":"esto-no-es-un-correo","contrasena":"ClaveSegura123456","rol":"USUARIO"}` | (mismo) | 🔴 Rojo — DEF-06 | Media | Partición de equivalencia + basada en defectos |
| CPI-19 | ídem, `contrasena` vacía | `Usuario` | (mismo objetivo que CPI-16) | (mismo) | `{"nombre":"Ana","correo":"ana@test.com","contrasena":"","rol":"USUARIO"}` | (mismo) | 🔴 Rojo — DEF-06 | Media | Partición de equivalencia + basada en defectos |
| CPI-20 | `putLibroPorIsbn_DEFECTO_aceptaPayloadInvalido` — `titulo` vacío | `Libro` vía `PUT /api/libros/isbn/{isbn}` | ASVS V5.1.3 · ISO27001 A.8.26 | `@WithMockUser(authorities="BIBLIOTECARIO")`; stub `actualizarLibroPorIsbn(...)→"Libro actualizado exitosamente."` | `PUT /api/libros/isbn/1234567890123` con `titulo:""` | Payload inválido debería responder `400 Bad Request` | 🔴 Rojo — **DEF-07**: `Libro` no tiene anotaciones de Bean Validation; `actualizarLibroPorIsbn` no usa `@Valid` | Media | Partición de equivalencia + basada en defectos |
| CPI-21 | ídem, `cantidad:-5` | `Libro` | (mismo objetivo que CPI-20) | (mismo) | `PUT /api/libros/isbn/1234567890123` con `cantidad:-5` | (mismo) | 🔴 Rojo — DEF-07 | Media | Partición de equivalencia + basada en defectos |
| CPI-22 | `postResenas_DEFECTO_aceptaPayloadInvalido` — `libroId` nulo | `ResenaRequest` vía `POST /api/resenas` | ASVS V5.1.3 | `@WithMockUser(authorities="USUARIO")`; stub `resenaService.save(...)` | `POST /api/resenas` con `{"libroId":null,"usuarioId":1,"texto":"Buen libro"}` | Payload inválido debería responder `400 Bad Request` | 🔴 Rojo — **DEF-08**: `ResenaRequest` sin Bean Validation; `crearResena` no usa `@Valid` | Media | Partición de equivalencia + basada en defectos |
| CPI-23 | ídem, `texto` vacío | `ResenaRequest` | (mismo objetivo que CPI-22) | (mismo) | `{"libroId":1,"usuarioId":1,"texto":""}` | (mismo) | 🔴 Rojo — DEF-08 | Media | Partición de equivalencia + basada en defectos |
| CPI-24 | `postComentariosResena_DEFECTO_aceptaPayloadInvalido` — `resenaId` nulo | `ComentarioResenaRequest` vía `POST /api/comentarios-resena` | ASVS V5.1.3 | `@WithMockUser(authorities="USUARIO")`; stub `comentarioResenaService.save(...)` | `POST /api/comentarios-resena` con `{"resenaId":null,"usuarioId":1,"texto":"comentario"}` | Payload inválido debería responder `400 Bad Request` | 🔴 Rojo — **DEF-09**: mismo patrón que `ResenaRequest`, aplicado a `ComentarioResenaRequest` | Media | Partición de equivalencia + basada en defectos |
| CPI-25 | ídem, `texto` vacío | `ComentarioResenaRequest` | (mismo objetivo que CPI-24) | (mismo) | `{"resenaId":1,"usuarioId":1,"texto":""}` | (mismo) | 🔴 Rojo — DEF-09 | Media | Partición de equivalencia + basada en defectos |
| CPI-26 | `postPrestar_DEFECTO_aceptaPayloadInvalido` — `correoUsuario` vacío | `PrestamoRequest` vía `POST /api/prestar` | ASVS V5.1.3 | `@WithMockUser(authorities="BIBLIOTECARIO")`; stub `crearPrestamo(...)→"Préstamo registrado con éxito."` | `POST /api/prestar` con `{"correoUsuario":"","isbn":1234567890123,"fechaPrestamo":"2026-07-19"}` | Payload inválido debería responder `400 Bad Request` | 🔴 Rojo — **DEF-10**: `PrestamoRequest` sin Bean Validation; `registrarPrestamo` no usa `@Valid` | Media | Partición de equivalencia + basada en defectos |
| CPI-27 | ídem, `isbn` nulo | `PrestamoRequest` | (mismo objetivo que CPI-26) | (mismo) | `{"correoUsuario":"cliente@test.com","isbn":null,"fechaPrestamo":"2026-07-19"}` | (mismo) | 🔴 Rojo — DEF-10 | Media | Partición de equivalencia + basada en defectos |

## 2.4 `PrivilegeEscalationEndToEndIntegrationTest` (CPI-28 a CPI-29)

Tipo: `@SpringBootTest(webEnvironment=MOCK)` + `@AutoConfigureMockMvc`. Única clase de integración
que usa la pila completa real (filtros + `Controller` + `UsuarioService` + `UsuarioRepository` +
H2 real), sin ningún mock, para confirmar el impacto de principio a fin del hallazgo A3.

| ID | Caso de prueba | Ruta bajo prueba | Objetivo | Precondiciones (Arrange) | Entrada / pasos (Act) | Resultado esperado (Assert) | Resultado obtenido | Prioridad | Técnica |
|---|---|---|---|---|---|---|---|---|---|
| CPI-28 | `registro_sinAutenticar_DEFECTO_persisteRolBibliotecarioEnBaseDeDatosReal` | `POST /api/usuarios/registro` (pila completa + H2 real) | ISO25010 Integridad/Autenticidad · ISO27001 A.8.2, A.5.15 · ASVS V4.1.5, V4.2.1 | Ninguna; base H2 real vía `@ActiveProfiles("test")` | `POST /api/usuarios/registro` con `{"nombre":"Atacante","correo":"atacante-e2e@test.com","contrasena":"ClaveSegura123456","rol":"BIBLIOTECARIO"}`; luego se consulta `usuarioRepository.findByCorreo(...)` | Debería haberse persistido con `rol="USUARIO"` | 🔴 Rojo — **DEF-20 (A3, end-to-end)**: el rol enviado por un cliente no autenticado queda realmente persistido en la base de datos | Alta | Basada en defectos |
| CPI-29 | `registro_sinRolEspecificado_persisteRolUsuarioPorDefectoEnBaseDeDatosReal` | `POST /api/usuarios/registro` (pila completa + H2 real) | ISO25010 Integridad (control positivo) | Ninguna; base H2 real | `POST /api/usuarios/registro` con `{"nombre":"Cliente Normal","correo":"normal-e2e@test.com","contrasena":"ClaveSegura123456"}` (sin campo `rol`) | El usuario persistido tiene `rol="USUARIO"` | 🟢 Verde | N/A — control ya conforme | Partición de equivalencia |

---

# 3. Casos de Prueba de Caja Negra

**Alcance:** no se usó Postman/Newman — la suite se implementó como JUnit 5 +
`TestRestTemplate` de Spring Boot Test, con `@SpringBootTest(webEnvironment=RANDOM_PORT)`: la
aplicación completa arranca en un puerto HTTP real y cada caso hace peticiones HTTP reales contra
ese puerto, sin `MockMvc` ni llamadas directas a servicios — funcionalmente equivalente a una
colección de Postman/Newman ejecutada dentro del mismo `mvn test`. 3 clases, **10 métodos, 10
ejecuciones** (sin `@ParameterizedTest` en este nivel, así que cada fila corresponde a un método).

## 3.1 `AuthFunctionalTest` (CPN-01 a CPN-04)

Flujo feliz de registro/login, exercised sobre HTTP real. Sirve de línea base de que el pipeline
de caja negra funciona de extremo a extremo (no verifica un control de seguridad específico).

| ID | Caso de prueba | Endpoint bajo prueba | Objetivo | Precondiciones (Arrange) | Entrada / pasos (Act) | Resultado esperado (Assert) | Resultado obtenido | Prioridad | Técnica |
|---|---|---|---|---|---|---|---|---|---|
| CPN-01 | `registro_conDatosValidos_devuelve200YMensajeDeExito` | `POST /api/usuarios/registro` | Contrato HTTP: alta pública de usuario funciona sobre puerto real | App levantada en puerto aleatorio; H2 en memoria | `POST /api/usuarios/registro` con nombre `"Nuevo Usuario"`, correo `nuevo-funcional@test.com`, contraseña `"ClaveSegura123456"`, `rol:"USUARIO"` | Código de estado `200`; cuerpo `"Usuario registrado con éxito."` | 🟢 Verde | N/A — control ya conforme | Caja negra/especificación |
| CPN-02 | `registro_conCorreoYaRegistrado_devuelveMensajeDeRechazo` | `POST /api/usuarios/registro` | Contrato HTTP: rechazo de correo duplicado | Se registra primero `duplicado-funcional@test.com` | Segundo `POST` con el mismo correo | Código de estado `200`; cuerpo `"Correo ya registrado."` | 🟢 Verde | N/A — control ya conforme | Caja negra/especificación |
| CPN-03 | `login_conCredencialesValidas_autenticaCorrectamente` | `POST /api/login` (form-urlencoded) | Contrato HTTP: login exitoso vía `formLogin` real | Usuario `login-ok-funcional@test.com` ya registrado | `POST /api/login` con `username`/`password` correctos | Código de estado `200` | 🟢 Verde | N/A — control ya conforme | Caja negra/especificación |
| CPN-04 | `login_conContrasenaIncorrecta_noEstableceSesionAutenticadaYSirvePaginaDeError` | `POST /api/login` | Contrato HTTP: comportamiento real de Spring Security ante credenciales inválidas (no hay `failureHandler` propio) | Usuario `login-fail-funcional@test.com` ya registrado | `POST /api/login` con contraseña incorrecta; `TestRestTemplate` sigue el redirect | Código de estado final `200` con `Content-Type` no nulo y cuerpo que contiene `"<html"` (página de error autogenerada, distinta del successHandler que no trae cuerpo) | 🟢 Verde | N/A — control ya conforme | Caja negra/especificación |

## 3.2 `LibroFunctionalTest` (CPN-05 a CPN-06)

Lectura pública de libros.

| ID | Caso de prueba | Endpoint bajo prueba | Objetivo | Precondiciones (Arrange) | Entrada / pasos (Act) | Resultado esperado (Assert) | Resultado obtenido | Prioridad | Técnica |
|---|---|---|---|---|---|---|---|---|---|
| CPN-05 | `listarLibros_devuelve200` | `GET /api/libros` | Contrato HTTP: lectura pública accesible | App levantada en puerto aleatorio | `GET /api/libros` | Código de estado `200` | 🟢 Verde | N/A — control ya conforme | Caja negra/especificación |
| CPN-06 | `buscarLibroPorIsbnInexistente_devuelve404` | `GET /api/libros/isbn/{isbn}` | Contrato HTTP: recurso inexistente devuelve `404` | ISBN `9999999999999` no existe | `GET /api/libros/isbn/9999999999999` | Código de estado `404` | 🟢 Verde | N/A — control ya conforme | Caja negra/especificación |

## 3.3 `AccessControlSecurityTest` (CPN-07 a CPN-10)

Regresión de vulnerabilidades de control de acceso confirmadas sobre el sistema en ejecución real
(sin mocks). Diseñadas deliberadamente para fallar hasta que el defecto de producción se corrija;
son la evidencia más directa de explotabilidad de las tres capas de prueba.

| ID | Caso de prueba | Endpoint bajo prueba | Objetivo | Precondiciones (Arrange) | Entrada / pasos (Act) | Resultado esperado (Assert) | Resultado obtenido | Prioridad | Técnica |
|---|---|---|---|---|---|---|---|---|---|
| CPN-07 | `registro_conRolBibliotecarioEnElBody_noDeberiaQuedarPersistidoComoBibliotecario` | `POST /api/usuarios/registro` + `GET /api/usuarios/me` | Confirmar sobre HTTP real que el registro público no debería permitir autoasignarse un rol privilegiado | Se registra `attacker-mass-assignment@test.com` con `rol:"BIBLIOTECARIO"`; se hace login y se obtiene cookie de sesión | `GET /api/usuarios/me` con la cookie de sesión | El rol devuelto debería ser `"USUARIO"` | 🔴 Rojo — **DEF-20**: el cliente no autenticado logró auto-registrarse como `BIBLIOTECARIO`, confirmado end-to-end vía HTTP real | Alta | Caja negra/especificación + basada en defectos |
| CPN-08 | `deleteLibroPorIsbn_sinAutenticar_deberiaSerRechazado` | `DELETE /api/libros/isbn/{isbn}` | Confirmar sobre HTTP real que un `DELETE` sin autenticar debe ser rechazado por el filtro | Sin credenciales; `HttpEntity.EMPTY` | `DELETE /api/libros/isbn/9999999999999` sin autenticación | Código de estado `401` o `403` | 🔴 Rojo — **DEF-14**: el `permitAll` de `/api/libros/**` deja pasar la petición (falla luego dentro de la lógica de negocio, típicamente `500`), en vez de cortarla en el filtro | Alta | Caja negra/especificación + basada en defectos |
| CPN-09 | `putLibroPorIsbn_sinAutenticar_deberiaSerRechazado` | `PUT /api/libros/isbn/{isbn}` | Confirmar sobre HTTP real que un `PUT` sin autenticar debe ser rechazado por el filtro | Sin credenciales; body JSON con datos de libro modificados | `PUT /api/libros/isbn/9999999999999` sin autenticación | Código de estado `401` o `403` | 🔴 Rojo — **DEF-14**: misma familia que CPN-08; `PUT` también cae bajo el `permitAll` de `/api/libros/**` | Alta | Caja negra/especificación + basada en defectos |
| CPN-10 | `verificarAmonestacion_conRolUsuario_deberiaSerRechazadaPorFaltaDePermisoBibliotecario` | `PUT /api/amonestaciones-usuario/verificar/{id}` | Confirmar sobre HTTP real que un rol `USUARIO` no puede verificar amonestaciones | Se registra y loguea `usuario-comun-idor@test.com` con `rol:"USUARIO"`; cookie de sesión real | `PUT /api/amonestaciones-usuario/verificar/1` con la cookie de sesión | Código de estado `403 Forbidden` | 🔴 Rojo — **DEF-17**: no hay chequeo de rol ni en `SecurityConfig` ni en el controller; la petición llega a la lógica de negocio sin ser rechazada por permisos | Alta | Caja negra/especificación + basada en defectos |

---

# 4. Resumen y verificación de consistencia

## 4.1 Totales por nivel

| Nivel | Casos documentados en esta especificación | 🟢 Verde | 🔴 Rojo |
|---|---|---|---|
| Unitarias (CPU) | 82 (CPU-01…CPU-82) | 62 | 20 |
| Integración (CPI) | 29 (CPI-01…CPI-29) | 11 | 18 |
| Caja negra (CPN) | 10 (CPN-01…CPN-10) | 6 | 4 |
| **Subtotal (3 niveles)** | **121** | **79** | **42** |

## 4.2 Conciliación con `mvn test`

La última corrida real (`mvn -B clean test -Dmaven.compiler.release=17`, JDK 17, 2026-07-19,
19:33:16 hora local) reportó:

```
[INFO] Tests run: 122, Failures: 42, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE   ← esperado: los 42 fallos son DEFECTO DETECTADO intencionales, no errores de entorno
```

La diferencia entre el **121** de esta especificación y el **122** de Surefire es exactamente
**1 test**: `__1.spring_boot.GestionBibliotecariaApplicationTests.contextLoads` — el smoke test
preexistente de arranque de contexto de Spring Boot, generado por el arquetipo del proyecto. No
pertenece a ninguno de los tres niveles de seguridad/funcionalidad de este documento (no lo
categoriza `scripts/publish_metrics.py`, no tiene comentario de trazabilidad ISO/ASVS, y no ejercita
ningún componente bajo prueba de la Sección 3 del Plan de Pruebas), por lo que se excluye de esta
especificación pero se cuenta en el total de Surefire. Su resultado fue 🟢 Verde.

| Verificación | Cálculo | Resultado |
|---|---|---|
| Total ejecutado por Surefire | — | 122 |
| Total documentado en esta especificación (3 niveles) | 82 + 29 + 10 | 121 |
| Diferencia | 122 − 121 | 1 (`contextLoads`, fuera de alcance) |
| Verdes documentados + `contextLoads` | 79 + 1 | **80**, coincide con `Tests run: 122, Failures: 42` (122 − 42 = 80) |
| Rojos documentados | 20 + 18 + 4 | **42**, coincide exactamente con `Failures: 42` reportado por Surefire |

Cada una de las 121 filas de este documento se contrastó individualmente contra
`target/surefire-reports/TEST-*.xml` (presencia o ausencia de `<failure>`/`<error>` dentro del
`<testcase>` correspondiente) antes de fijar su columna "Resultado obtenido" — no se copió el
resultado desde `docs/matriz-trazabilidad-pruebas-unitarias.md` ni desde
`docs/matriz-trazabilidad-pruebas-integracion.md`, aunque coincide con ambos en el 100% de los
casos, lo cual es evidencia adicional de estabilidad de la suite entre rondas.

## 4.3 Resultado de la colección de caja negra

No existe una colección Postman/Newman separada que conciliar: como se documenta en la Sección 3,
el nivel de caja negra se implementó como las 3 clases JUnit 5 bajo
`src/test/java/com/biblioteca/blackbox/`, descubiertas y ejecutadas por Surefire dentro del mismo
`mvn test` que los otros dos niveles. Su resultado ya está incluido en la conciliación de la
Sección 4.2: **10 ejecuciones, 6 verdes (CPN-01…CPN-06), 4 rojas (CPN-07…CPN-10)**, estas últimas
confirmando de forma independiente y sobre HTTP real 3 de los 20 hallazgos del catálogo
(`DEF-14`, `DEF-17`, `DEF-20`).

## 4.4 Trazabilidad hacia el catálogo de hallazgos

De los 20 hallazgos del catálogo (`DEF-01`…`DEF-20`, `scripts/publish_metrics.py`), esta
especificación documenta un caso de prueba en rojo para cada uno de los siguientes; los no
listados (ninguno, en este catálogo) no tienen fila roja asociada:

| DEF | Nivel(es) donde aparece en esta especificación | Severidad |
|---|---|---|
| DEF-01 | CPU-80 | Media |
| DEF-02 | CPU-81 | Media |
| DEF-03 | CPU-07…CPU-10 | Media |
| DEF-04 | CPU-16…CPU-18 | Media |
| DEF-05 | CPI-14 | Media |
| DEF-06 | CPI-16…CPI-19 | Media |
| DEF-07 | CPI-20…CPI-21 | Media |
| DEF-08 | CPI-22…CPI-23 | Media |
| DEF-09 | CPI-24…CPI-25 | Media |
| DEF-10 | CPI-26…CPI-27 | Media |
| DEF-11 | CPU-32 | Media (fuente: Baja/Media) |
| DEF-12 | CPU-36…CPU-37 | Media (fuente: Baja/Media) |
| DEF-13 | CPU-60 | Media |
| DEF-14 | CPI-10, CPN-08, CPN-09 | Alta |
| DEF-15 | CPU-75 | Alta |
| DEF-16 | CPU-76 | Alta |
| DEF-17 | CPU-73, CPI-11, CPN-10 | Alta |
| DEF-18 | CPU-74, CPI-12 | Alta |
| DEF-19 | CPI-13 | Alta |
| DEF-20 | CPU-04…CPU-06, CPI-28, CPN-07 | Alta (fuente: Crítica, homologada al techo de la escala de 3 niveles) |

Los 20 hallazgos del catálogo tienen al menos un caso de prueba rojo asociado en esta
especificación — no hay hallazgos "huérfanos" sin evidencia ejecutable, y no hay ningún caso rojo
sin un `DEF-XX` trazable.
