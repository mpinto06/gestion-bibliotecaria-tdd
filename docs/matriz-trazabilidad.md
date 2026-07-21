# Matriz de Trazabilidad - Fase 2

Este documento establece la trazabilidad bidireccional entre los requisitos, los casos de prueba dinámicos (Unitarias, Integración, Sistema, Seguridad) y los resultados obtenidos en el ecosistema automatizado. Esta trazabilidad garantiza el cumplimiento de la norma ISO 29119 y evidencia un nivel de madurez CMMI 5.

## 1. Pruebas de Seguridad (Caja Blanca/Gris) - ISO 25010

| ID Req / Hallazgo | ID Caso Prueba | Componente | Herramienta / Técnica | Resultado (GitHub Actions) | ID Defecto / Estado |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **DEF-01** (Fuga Hash) | **CP-01** | `Usuario.java` | JUnit / Mockito | ✅ Passed | Cerrado |
| **DEF-05** (Privilegios) | **CP-02** | `Controller.getTodasAmonestaciones` | JUnit / @WebMvcTest | ✅ Passed | Cerrado |
| **DEF-10** (Stock Negativo) | **CP-03** | `PrestamoService.renovarPrestamo` | JUnit / Mockito | ❌ Failed | BUG-001 (Abierto) |
| **DEF-12** (Falta CSRF) | **CP-04** | `SecurityConfig.filterChain` | JUnit / @WebMvcTest | ❌ Failed | BUG-002 (Abierto) |
| **DEF-14** (Escalada Privilegios) | **CP-05** | `UsuarioService.registrarUsuario` | JUnit / Mockito | ✅ Passed | Cerrado |
| **DEF-16** (Suplantación) | **CP-06** | `LibroService.registrarLibroConImagen`| JUnit / @WebMvcTest | ✅ Passed | Cerrado |

## 2. Pruebas Funcionales (Caja Negra - Sistema/Aceptación)

| ID Requisito | ID Caso Prueba | Flujo Validado | Herramienta / Técnica | Resultado | Estado |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **REQ-F01** | **CP-FUNC-01** | Registro de préstamo exitoso | Postman / Caja Negra | ✅ Passed | Aprobado |
| **REQ-F02** | **CP-FUNC-02** | Cálculo de multas por retraso | Postman / Partición Equivalencia | ✅ Passed | Aprobado |
| **REQ-F03** | **CP-FUNC-03** | Límite de 3 libros por estudiante | Postman / Valores Límite | ✅ Passed | Aprobado |

## 3. Métricas de Cobertura (Trazabilidad a Código)

*   **Herramienta:** JaCoCo / SonarQube
*   **Cobertura de Líneas (Line Coverage):** 84.5% (Cumple meta SMART > 80%)
*   **Cobertura de Ramas (Branch Coverage):** 78.2%
*   **Densidad de Defectos Inyectados:** 0.3/KLOC (Cumple meta SMART < 0.5/KLOC)

> **Nota del Escriba / Líder de QA:** La matriz demuestra que el 100% de los hallazgos críticos de la Fase 1 han sido transformados en casos de prueba automatizados. Los defectos BUG-001 y BUG-002 están reportados en el gestor de incidencias (Jira/GitHub) y asignados al equipo de desarrollo para la iteración final.
