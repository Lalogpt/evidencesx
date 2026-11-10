# EVIDENCE X - Sistema de Investigaciones Digitales con Cadena de Custodia (Arquitectura Offline-First)

Este documento detalla la arquitectura, el diseño técnico y los componentes clave implementados para **EVIDENCE X**, garantizando integridad criptográfica, persistencia local con Room, sincronización resiliente con WorkManager y Firebase, y generación de informes forenses en PDF.

---

## 1. Stack Tecnológico y Arquitectura
- **Clean Architecture / MVVM**: Separación estricta entre Capas de Datos (`data/local`, `data/repository`, `data/sync`, `data/pdf`), Dominio (`domain/crypto`) y Presentación (`ui/viewmodel`, `ui/screens`).
- **Offline-First**: La base de datos local Room actúa como fuente de verdad absoluta para la interfaz de usuario.
- **Persistencia y Concurrencia**: Transacciones atómicas estrictas en Room que garantizan la consistencia transaccional entre inserción de evidencias, eventos de auditoría y tareas en cola.

---

## 2. Componentes Principales Implementados

### A. Criptografía e Integridad (`CryptoUtils.kt`)
- **UUID v4**: Generación local para expedientes, evidencias y auditoría (idempotencia en sincronización).
- **`file_hash`**: Cálculo SHA-256 leyendo archivos binarios en bloques de memoria constante de 8 KB (soporta archivos pesados de video/foto sin fugas de memoria).
- **`metadata_hash`**: Representación canónica estricta (orden fijo de claves, codificación UTF-8, marcas de tiempo UTC ISO 8601 y precisión GPS fija a 6 decimales).
- **`evidence_hash`**: Combinación SHA-256 de `file_hash || metadata_hash`.
- **Bitácora Encadenada**: Cada evento genera un `event_hash = SHA-256(contenido_evento || event_hash_previo)`. Si se altera un registro intermedio, la cadena se rompe.

### B. Base de Datos Local (Room)
- **`CaseEntity`**: Almacenamiento local de expedientes.
- **`EvidenceEntity`**: Almacenamiento de evidencias y metadatos criptográficos en almacenamiento interno privado.
- **`AuditEventEntity`**: Bitácora de cadena de custodia encadenada.
- **`SyncTaskEntity`**: Cola de sincronización persistente con estados (`PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`) y reintentos.
- **DAOs Atómicos**: `insertEvidenceAtomically` realiza inserciones transaccionales que agrupan evidencia, auditoría y tareas de sincronización.

### C. Sincronización en Segundo Plano (`SyncWorker.kt`)
- Implementado con **WorkManager**:
  - Restricción de red: `NetworkType.CONNECTED`.
  - Retroceso exponencial (`BackoffPolicy.EXPONENTIAL`).
  - Tolerancia a reinicios del dispositivo y control de duplicados.

### D. Generador de Informes PDF (`PdfReportGenerator.kt`)
- Uso de `PdfDocument` nativo de Android para compilar informes periciales con:
  - Portada oficial de la institución y datos del expediente.
  - Tabla resumen de evidencias capturadas y sus hashes SHA-256.
  - Sección de trazabilidad de auditoría y **Hash Global de Integridad** de la cadena de custodia.

### E. Interfaz de Usuario (Jetpack Compose & Material 3)
- **Dashboard de Expedientes**: Visualización de expedientes activos, creación mediante diálogo validado, botones de captura segura y exportación a PDF.
- **Visualizador de Cadena de Custodia**: Pantalla dedicada para inspeccionar la bitácora encadenada (`event_hash` vs `prev_event_hash`).
