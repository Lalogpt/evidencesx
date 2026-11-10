# Prompt de Especificación Técnica para EVIDENCE X

Copia y pega el siguiente texto en cualquier IA o asistente de desarrollo para documentar o continuar el desarrollo de **EVIDENCE X**:

```text
Actúa como Arquitecto de Software Senior y Desarrollador Android experto en Kotlin, Jetpack Compose, Material 3 y arquitecturas Offline-First.

Necesito que mantengas y desarrolles el sistema móvil "EVIDENCE X" (Sistema de Investigaciones Digitales con Cadena de Custodia), diseñado para peritos y policías que operan en zonas con nula o intermitente conectividad.

A continuación se detallan los requerimientos técnicos y funcionales obligatorios que deben cumplirse estrictamente:

1. Stack Tecnológico y Arquitectura:
- Plataforma: Android nativo (Kotlin, Jetpack Compose, Material 3).
- Arquitectura: Offline-First con Clean Architecture / MVVM. La base de datos local (Room) es la fuente de verdad absoluta para la interfaz.
- Sincronización Bidireccional (Push & Pull): WorkManager con restricciones de red (CONNECTED), retroceso exponencial y sincronización automática (bajada desde Firestore y subida de tareas pendientes).
- Backend (Firebase): Firebase Authentication (con inicio de sesión online/offline y persistencia de cuentas de peritos en Room) y Cloud Firestore (expedientes, evidencias y bitácora de auditoría encadenada).

2. Módulos y Requerimientos Funcionales:
- Autenticación y Roles:
  * Administrador (admin@evidencex.com / admin123): Control total, gestión/creación de cuentas de peritos (con ID único no repetible y contraseña), edición y eliminación de expedientes (con auditoría de borrado/edición), y visibilidad global de todos los expedientes y evidencias del sistema.
  * Perito (ej. PERITO-001): Acceso aislado que solo puede ver, crear y consultar sus propios expedientes.
- Captura de Evidencias Seguras:
  * Módulo multimedia integrado: Captura fotográfica con CameraX, grabación de video restringida a un máximo de 20 segundos (MediaStore.ACTION_VIDEO_CAPTURE), y opción de subida/selección desde la galería.
  * Etiquetado de "Evidencia Sensible / Clasificada" [⚠️].
  * Almacenamiento exclusivamente en el almacenamiento interno privado de la app (nunca en galería pública).
  * Captura automática de geolocalización GPS real en el momento del levantamiento (Latitud y Longitud mediante FusedLocationProviderClient).
- Criptografía e Integridad (SHA-256):
  * Generación de UUID v4 local para expedientes, evidencias y eventos de auditoría (idempotencia).
  * Cálculo de `file_hash` (SHA-256 leído en bloques de memoria constante de 8KB).
  * Cálculo de `metadata_hash` a partir de una representación canónica estricta (orden fijo de campos, UTF-8, UTC ISO 8601, GPS fijo).
  * Hash combinado: `evidence_hash = SHA-256(file_hash || metadata_hash)`.
  * Bitácora de Auditoría Encadenada: Cada evento registra localmente un `event_hash = SHA-256(contenido || event_hash del evento previo)`.
- Verificador de Integridad y Paquetes Forenses:
  * Pantalla de verificación en tiempo real que recalcula hashes y valida la cadena de custodia.
  * Exportación de paquetes forenses cifrados `.evx` (archivos comprimidos con manifiestos JSON y hashes para transferencia offline punto a punto).
  * Generación de Informes PDF forenses locales utilizando `PdfDocument` de Android (portada, resumen de evidencias con ubicación GPS y etiquetas sensibles, hashes, estados de verificación y hash global de la bitácora) con visor integrado mediante FileProvider.
```
