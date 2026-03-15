# Plan de Implementación: Amazon Clone E-Commerce

## Resumen

Este documento describe el plan de implementación para una plataforma e-commerce tipo Amazon con arquitectura de microservicios. El sistema incluye 10 microservicios backend (Java 21 + Spring Boot), frontend (React 18 + TypeScript), y infraestructura completa con MySQL, Redis, Elasticsearch y Kafka.

## Tareas

- [x] 1. Configurar infraestructura base y servicios compartidos
  - Crear estructura de proyecto multi-módulo con Maven
  - Configurar Docker Compose para desarrollo local (MySQL, Redis, Elasticsearch, Kafka)
  - Crear módulo común con DTOs, excepciones y utilidades compartidas
  - Configurar Spring Cloud Config para gestión centralizada de configuración
  - _Requisitos: 20, 23_

- [x] 2. Configurar Load Balancer (Nginx)
  - [x] 2.1 Instalar y configurar Nginx
    - Crear Dockerfile para Nginx con configuración personalizada
    - Configurar upstream pool con múltiples instancias de API Gateway
    - Configurar algoritmo de balanceo least_conn
    - _Requisitos: 21.1, 21.2_

  - [x] 2.2 Implementar SSL/TLS termination
    - Configurar certificados SSL/TLS en Nginx
    - Configurar protocolos TLSv1.2 y TLSv1.3
    - Configurar ciphers seguros y ssl_prefer_server_ciphers
    - Configurar redirección automática de HTTP a HTTPS
    - Agregar headers X-Forwarded-For, X-Real-IP, X-Forwarded-Proto
    - _Requisitos: 22.1, 22.2, 22.3, 22.4, 22.5, 22.6, 22.7, 22.8, 22.9, 22.10_

  - [x] 2.3 Configurar health checks para instancias backend
    - Implementar health check endpoint /actuator/health en API Gateway
    - Configurar Nginx para verificar salud cada 10 segundos
    - Configurar max_fails=3 y fail_timeout=30s
    - Marcar instancias como down después de 3 fallos consecutivos
    - Implementar endpoint /health en Nginx para verificar su propia salud
    - _Requisitos: 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 23.7_

  - [x] 2.4 Implementar rate limiting a nivel de Load Balancer
    - Configurar limit_req_zone con límite de 100 req/s por IP
    - Configurar burst de 200 peticiones con nodelay
    - Retornar 429 Too Many Requests cuando se excede el límite
    - Agregar headers X-RateLimit-Limit y X-RateLimit-Remaining
    - Configurar reseteo de contadores cada segundo
    - _Requisitos: 24.1, 24.2, 24.3, 24.4, 24.5, 24.6, 24.7_

  - [x] 2.5 Configurar retry y failover logic
    - Configurar proxy_next_upstream para reintentar en otras instancias
    - Configurar proxy_next_upstream_tries=2 para máximo 2 reintentos
    - Configurar proxy_next_upstream_timeout=10s
    - Reintentar en casos de error, timeout, http_500, http_502, http_503
    - Configurar backup server para failover
    - _Requisitos: 25.1, 25.2, 25.3, 25.4, 25.5, 25.6, 25.7, 25.8, 25.9, 25.10_

  - [x] 2.6 Configurar buffering y optimizaciones de performance
    - Habilitar proxy_buffering con buffer_size=4k y buffers=8
    - Configurar timeouts: connect=60s, send=60s, read=60s
    - Habilitar compresión gzip para respuestas >1KB
    - Configurar keepalive connections al backend
    - Configurar client_max_body_size=10M
    - _Requisitos: 26.1, 26.2, 26.3, 26.4, 26.5, 26.6, 26.7_

  - [x] 2.7 Crear archivo de configuración nginx.conf completo
    - Integrar todas las configuraciones en nginx.conf
    - Configurar logging de acceso y errores
    - Documentar cada sección de configuración
    - _Requisitos: 21, 22, 23, 24, 25, 26_

  - [x] 2.8 Escribir tests para configuración de Nginx
    - Test de distribución de carga entre instancias
    - Test de SSL/TLS termination
    - Test de health checks y failover
    - Test de rate limiting
    - _Requisitos: 21, 22, 23, 24, 25, 26_


- [x] 3. Configurar Redis Cache Cluster
  - [x] 3.1 Configurar Redis Cluster con 3 masters + 3 replicas
    - Crear configuración redis-cluster.conf
    - Configurar cluster-enabled=yes y cluster-node-timeout=5000
    - Configurar cluster-require-full-coverage=no
    - Distribuir slots: Master1(0-5460), Master2(5461-10922), Master3(10923-16383)
    - Configurar replicación con repl-diskless-sync
    - _Requisitos: 27.1, 27.2, 27.3, 27.4, 27.5, 27.6_

  - [x] 3.2 Configurar persistencia y políticas de memoria
    - Configurar appendonly=yes y appendfsync=everysec
    - Configurar snapshots: save 900 1, save 300 10, save 60 10000
    - Configurar maxmemory=2gb
    - Configurar maxmemory-policy=allkeys-lru
    - Configurar eviction cuando memoria alcanza 80%
    - _Requisitos: 30.1, 30.2, 30.3, 30.4, 30.5, 30.6, 30.7, 30.8, 30.9, 31.1, 31.2, 31.3, 31.4, 31.5, 31.6, 31.7_

  - [x] 3.3 Implementar RedisCacheService con cache-aside pattern
    - Crear clase RedisCacheService con métodos getFromCache, setInCache, invalidate
    - Implementar método getOrLoad con cache-aside pattern
    - Implementar invalidatePattern para invalidación por patrón
    - Configurar RedisTemplate con serialización JSON
    - Implementar manejo de errores con logging
    - _Requisitos: 28.1, 28.2, 28.3_

  - [x] 3.4 Configurar TTL para diferentes tipos de datos
    - Configurar TTL de 1 hora para productos (product:{productId})
    - Configurar TTL de 30 minutos para búsquedas (search:{queryHash})
    - Configurar TTL de 24 horas para sesiones (session:{userId})
    - Configurar TTL de 7 días para carritos (cart:{userId})
    - Configurar TTL de 24 horas para categorías (categories:all)
    - Configurar TTL de 2 horas para recomendaciones (recommendations:{userId})
    - _Requisitos: 29.1, 29.2, 29.3, 29.4, 29.5, 29.6, 29.7, 29.8, 29.9, 29.10, 29.11, 29.12, 29.13_

  - [x] 3.5 Integrar cache en Product Service
    - Implementar cache de productos con key pattern product:{productId}
    - Implementar cache-aside: intentar obtener de Redis antes de MySQL
    - Almacenar en cache al obtener de MySQL con TTL de 1 hora
    - Invalidar cache al actualizar o eliminar producto
    - _Requisitos: 17.1, 17.2, 17.3, 28.1_

  - [x] 3.6 Integrar cache en Cart Service
    - Implementar cache de carritos con key pattern cart:{userId}
    - Almacenar carrito en Redis para acceso rápido
    - Sincronizar con MySQL para persistencia
    - Implementar estrategia de recuperación desde MySQL si Redis falla
    - _Requisitos: 17.4, 28.2_

  - [x] 3.7 Integrar cache en Search Service
    - Implementar cache de resultados de búsqueda con key pattern search:{queryHash}
    - Calcular hash de query con parámetros de búsqueda
    - Almacenar SearchResponse en cache con TTL de 30 minutos
    - Invalidación automática por TTL
    - _Requisitos: 28.3_

  - [x] 3.8 Implementar invalidación de cache basada en eventos
    - Consumir ProductUpdatedEvent y invalidar cache de producto
    - Consumir ProductDeletedEvent y invalidar cache de producto
    - Consumir CategoryUpdatedEvent y invalidar cache de categorías
    - Implementar invalidación por patrón para búsquedas relacionadas
    - _Requisitos: 28.7, 33.1, 33.2, 33.3, 33.4, 33.5, 33.6_

  - [x] 3.9 Configurar monitoreo y métricas de Redis
    - Exponer métricas de hit rate, memory usage, eviction rate
    - Configurar alertas para hit rate <80%
    - Configurar alertas para memory usage >80%
    - Exponer métricas en formato Prometheus
    - Implementar logging de operaciones de cache
    - _Requisitos: 32.1, 32.2, 32.3, 32.4, 32.5, 32.6, 32.7, 32.8, 32.9, 32.10_

  - [x] 3.10 Escribir tests para funcionalidad de cache
    - Test de cache-aside pattern con hit y miss
    - Test de invalidación de cache
    - Test de TTL y expiración automática
    - Test de failover a MySQL cuando Redis falla
    - _Requisitos: 27, 28, 29, 30, 31, 32, 33_

- [x] 4. Checkpoint - Verificar Load Balancer y Redis Cache
  - Asegurar que Nginx distribuya carga correctamente, que SSL/TLS funcione, que health checks detecten instancias caídas. Verificar que Redis Cluster esté operativo y que el cache mejore los tiempos de respuesta. Preguntar al usuario si hay dudas.


- [x] 5. Implementar API Gateway Service
  - [x] 5.1 Crear proyecto Spring Cloud Gateway con configuración base
    - Configurar enrutamiento a microservicios
    - Implementar filtros de logging y CORS
    - _Requisitos: 13.1, 13.4_

  - [x] 5.2 Implementar autenticación JWT en el gateway
    - Crear filtro de validación de tokens JWT
    - Implementar extracción de userId y agregación a headers
    - Manejar errores 401 Unauthorized para tokens inválidos
    - _Requisitos: 13.2, 13.3, 15.5_

  - [x] 5.3 Implementar rate limiting y circuit breaker
    - Configurar rate limiting de 100 req/s por usuario
    - Implementar circuit breaker con Resilience4j
    - Configurar timeouts y fallbacks
    - _Requisitos: 13.5, 13.6, 13.8, 14.1, 14.2_

  - [x] 5.4 Escribir tests de integración para API Gateway
    - Test de enrutamiento correcto a microservicios
    - Test de validación JWT y rate limiting
    - Test de circuit breaker con servicios caídos
    - _Requisitos: 13, 14_


- [x] 6. Implementar User Service (Autenticación y Gestión de Usuarios)
  - [x] 6.1 Crear modelos de datos y repositorios JPA
    - Implementar entidades User y Address con validaciones
    - Crear repositorios UserRepository y AddressRepository
    - Configurar índices en MySQL para email y status
    - _Requisitos: 1.1, 2.3, 15.4_

  - [x] 6.2 Implementar registro de usuarios
    - Crear endpoint POST /api/users/register
    - Validar unicidad de email y formato de datos
    - Encriptar contraseña con BCrypt (factor 12)
    - Generar token JWT al registrar
    - _Requisitos: 1.1, 1.2, 1.5, 1.8, 15.2, 15.3_

  - [x] 6.3 Escribir property test para validación de contraseñas
    - **Propiedad 4: Unicidad de Emails**
    - **Valida: Requisitos 1.2, 15.4**

  - [x] 6.4 Implementar autenticación de usuarios
    - Crear endpoint POST /api/users/login
    - Validar credenciales con BCrypt
    - Generar token JWT válido por 24 horas
    - Actualizar campo lastLoginAt
    - _Requisitos: 1.3, 1.4, 1.5, 1.7, 15.10_

  - [x] 6.5 Escribir property test para validez de tokens JWT
    - **Propiedad 5: Validez de Tokens JWT**
    - **Valida: Requisitos 1.5, 1.6**

  - [x] 6.6 Implementar gestión de perfiles de usuario
    - Crear endpoints GET/PUT /api/users/{userId}
    - Validar autorización (usuario solo puede ver/editar su propio perfil)
    - Excluir contraseña de respuestas
    - _Requisitos: 2.1, 2.2, 15.7_

  - [x] 6.7 Implementar gestión de direcciones de envío
    - Crear endpoints POST/GET /api/users/{userId}/addresses
    - Validar formato de dirección y teléfono internacional
    - Implementar lógica de dirección predeterminada única
    - _Requisitos: 2.3, 2.4, 2.5, 2.6_

  - [x] 6.8 Escribir tests unitarios para User Service
    - Test de registro exitoso y email duplicado
    - Test de autenticación válida e inválida
    - Test de gestión de direcciones
    - _Requisitos: 1, 2_

- [x] 7. Checkpoint - Verificar User Service
  - Asegurar que todos los tests pasen, verificar que el registro y login funcionen correctamente. Preguntar al usuario si hay dudas.


- [x] 8. Implementar Product Service (Catálogo de Productos)
  - [x] 8.1 Crear modelos de datos para productos y categorías
    - Implementar entidades Product, Category y Review
    - Crear repositorios con índices optimizados
    - Configurar relaciones jerárquicas de categorías
    - _Requisitos: 3.1, 3.8, 17.6_

  - [x] 8.2 Implementar CRUD de productos
    - Crear endpoints POST/GET/PUT /api/products
    - Validar precio > 0 y discountPrice < price
    - Validar unicidad de SKU
    - Asignar estado ACTIVE por defecto
    - _Requisitos: 3.1, 3.2, 3.4, 3.5, 3.6, 3.7_

  - [x] 8.3 Implementar sistema de categorías
    - Crear endpoint GET /api/products/categories
    - Implementar estructura jerárquica con subcategorías
    - _Requisitos: 3.8_

  - [x] 8.4 Implementar sistema de reseñas y calificaciones
    - Crear endpoints POST/GET /api/products/{id}/reviews
    - Validar rating entre 1 y 5
    - Recalcular rating promedio al agregar reseña
    - Incrementar contador de reseñas
    - Implementar paginación de reseñas
    - _Requisitos: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 8.5 Implementar cache de productos con Redis
    - Configurar RedisTemplate para ProductResponse
    - Implementar cache con TTL de 1 hora
    - Invalidar cache al actualizar producto
    - _Requisitos: 17.1, 17.2, 17.3_

  - [x] 8.6 Configurar publicación de eventos a Kafka
    - Publicar ProductCreatedEvent al crear producto
    - Publicar ProductUpdatedEvent al actualizar producto
    - Publicar ProductDeletedEvent al eliminar producto
    - _Requisitos: 3.3, 5.9_

  - [x] 8.7 Escribir property test para integridad de precios
    - **Propiedad 3: Integridad de Precios**
    - **Valida: Requisitos 3.4, 3.5**

  - [x] 8.8 Escribir tests unitarios para Product Service
    - Test de creación de producto con validaciones
    - Test de sistema de reseñas y recálculo de rating
    - Test de cache de productos
    - _Requisitos: 3, 4, 17_

- [ ] 9. Implementar Search Service (Búsqueda con Elasticsearch)
  - [x] 9.1 Configurar cliente de Elasticsearch
    - Configurar conexión a Elasticsearch
    - Crear índice de productos con mappings optimizados
    - Configurar analizadores con sinónimos en español
    - _Requisitos: 5.9, 17.8_

  - [x] 9.2 Implementar consumidor de eventos de productos
    - Consumir ProductCreatedEvent y indexar en Elasticsearch
    - Consumir ProductUpdatedEvent y actualizar índice
    - Consumir ProductDeletedEvent y eliminar del índice
    - _Requisitos: 5.9_

  - [x] 9.3 Implementar búsqueda full-text con filtros
    - Crear endpoint GET /api/search
    - Implementar búsqueda multi-match en nombre, descripción y marca
    - Aplicar fuzzy matching para errores tipográficos
    - Implementar filtros por categoría, precio y rating
    - Filtrar solo productos con estado ACTIVE
    - _Requisitos: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [x] 9.4 Implementar autocompletado y sugerencias
    - Crear endpoint GET /api/search/autocomplete
    - Crear endpoint GET /api/search/suggestions
    - Implementar sugerencias basadas en prefijo
    - _Requisitos: 5.7_

  - [x] 9.5 Implementar ordenamiento y paginación
    - Soportar ordenamiento por relevancia, precio y rating
    - Implementar paginación eficiente (máximo 100 items)
    - _Requisitos: 5.8, 17.7_

  - [-] 9.6 Escribir tests de integración con Testcontainers
    - Test de indexación de productos desde Kafka
    - Test de búsqueda con filtros múltiples
    - Test de autocompletado
    - _Requisitos: 5, 19.4_

- [ ] 10. Checkpoint - Verificar Product y Search Services
  - Asegurar que todos los tests pasen, verificar que la búsqueda y filtrado funcionen correctamente. Preguntar al usuario si hay dudas.


- [x] 11. Implementar Cart Service (Carrito de Compras)
  - [x] 11.1 Crear modelos de datos para carrito
    - Implementar entidades Cart y CartItem
    - Crear repositorios para MySQL
    - Configurar serialización para Redis
    - _Requisitos: 6.1_

  - [x] 11.2 Implementar gestión de items del carrito
    - Crear endpoint POST /api/cart/{userId}/items
    - Validar existencia y disponibilidad de producto
    - Incrementar cantidad si producto ya existe en carrito
    - _Requisitos: 6.1, 6.2_

  - [x] 11.3 Implementar actualización y eliminación de items
    - Crear endpoint PUT /api/cart/{userId}/items/{itemId}
    - Crear endpoint DELETE /api/cart/{userId}/items/{itemId}
    - Recalcular totales al modificar items
    - _Requisitos: 6.3, 6.4_

  - [x] 11.4 Implementar cálculo de totales del carrito
    - Calcular subtotal como suma de precio × cantidad
    - Implementar lógica de cálculo de totales
    - _Requisitos: 6.5_

  - [x] 11.5 Implementar persistencia dual (Redis + MySQL)
    - Almacenar carrito en Redis para acceso rápido
    - Sincronizar con MySQL para persistencia
    - Implementar estrategia de recuperación desde MySQL si Redis falla
    - _Requisitos: 6.6, 6.7, 17.4_

  - [x] 11.6 Implementar sistema de cupones de descuento
    - Crear endpoint POST /api/cart/{userId}/apply-coupon
    - Validar cupón y aplicar descuento al total
    - _Requisitos: 6.8_

  - [x] 11.7 Escribir tests unitarios para Cart Service
    - Test de agregar items y incrementar cantidad
    - Test de cálculo de totales
    - Test de persistencia en Redis y MySQL
    - _Requisitos: 6, 17_

- [x] 12. Implementar Inventory Service (Gestión de Inventario)
  - [x] 12.1 Crear modelos de datos para inventario
    - Implementar entidad Inventory con campos disponible y reservado
    - Crear InventoryRepository con índices
    - _Requisitos: 10.1, 10.2_

  - [x] 12.2 Implementar operaciones de inventario
    - Crear endpoints GET/PUT /api/inventory/product/{productId}
    - Implementar reserva temporal de inventario
    - Implementar liberación de inventario
    - Validar que inventario disponible nunca sea negativo
    - _Requisitos: 10.1, 10.2, 10.3_

  - [x] 12.3 Implementar actualización de estado de productos
    - Actualizar producto a OUT_OF_STOCK cuando inventario llega a cero
    - Publicar eventos de cambio de inventario a Kafka
    - _Requisitos: 10.4, 10.5_

  - [x] 12.4 Implementar consumidor de eventos de pedidos
    - Consumir OrderCreatedEvent para reservar inventario
    - Consumir OrderCancelledEvent para liberar inventario
    - Publicar InventoryReservedEvent o InventoryUnavailableEvent
    - _Requisitos: 7.5, 7.6, 7.7, 7.13_

  - [x] 12.5 Escribir property test para consistencia de inventario
    - **Propiedad 1: Consistencia de Inventario**
    - **Valida: Requisitos 10.6**

  - [x] 12.6 Escribir tests unitarios para Inventory Service
    - Test de reserva y liberación de inventario
    - Test de validación de inventario no negativo
    - Test de actualización de estado de productos
    - _Requisitos: 10_


- [x] 13. Implementar Payment Service (Procesamiento de Pagos)
  - [x] 13.1 Crear modelos de datos para pagos
    - Implementar entidades Payment y PaymentMethod
    - Crear repositorios con índices
    - Configurar encriptación AES-256 para datos de tarjetas
    - _Requisitos: 9.1, 9.4, 15.4_

  - [x] 13.2 Implementar integración con pasarela de pago
    - Configurar cliente de Stripe/PayPal
    - Crear servicio de procesamiento de pagos
    - Implementar manejo de webhooks de pasarela
    - _Requisitos: 9.1, 9.6_

  - [x] 13.3 Implementar procesamiento de pagos
    - Crear endpoint POST /api/payments/process
    - Comunicarse con pasarela externa
    - Almacenar ID de transacción y estado
    - Registrar intentos de pago para auditoría
    - _Requisitos: 9.1, 9.2, 9.3, 9.7_

  - [x] 13.4 Implementar gestión de métodos de pago
    - Crear endpoints POST/GET /api/payments/methods
    - Validar pertenencia de método de pago al usuario
    - Encriptar datos sensibles de tarjetas
    - _Requisitos: 9.4, 9.5_

  - [x] 13.5 Implementar sistema de reembolsos
    - Crear endpoint POST /api/payments/refund
    - Procesar reembolso a través de pasarela
    - _Requisitos: 9.6_

  - [x] 13.6 Implementar consumidor de eventos de inventario
    - Consumir InventoryReservedEvent para procesar pago
    - Publicar PaymentSuccessEvent o PaymentFailedEvent
    - _Requisitos: 7.8, 7.9, 7.10_

  - [x] 13.7 Escribir tests unitarios para Payment Service
    - Test de procesamiento de pago exitoso
    - Test de manejo de pago fallido
    - Test de encriptación de datos de tarjetas
    - _Requisitos: 9, 15_


- [x] 14. Implementar Order Service (Gestión de Pedidos con Saga Pattern)
  - [x] 14.1 Crear modelos de datos para pedidos
    - Implementar entidades Order, OrderItem y ShippingAddress
    - Crear repositorios con índices optimizados
    - _Requisitos: 7.1, 7.14, 7.16_

  - [x] 14.2 Implementar generación de número de pedido
    - Crear método para generar número único formato ORD-YYYYMMDD-XXXXXX
    - Validar unicidad en base de datos
    - _Requisitos: 7.2_

  - [x] 14.3 Implementar cálculo de totales del pedido
    - Calcular subtotal como suma de items
    - Implementar cálculo de impuestos basado en dirección
    - Implementar cálculo de costo de envío
    - Validar que total = subtotal + tax + shipping - discount
    - _Requisitos: 7.14, 7.15, 7.16_

  - [x] 14.4 Implementar creación de pedido (inicio de Saga)
    - Crear endpoint POST /api/orders
    - Validar que carrito no esté vacío
    - Crear pedido en estado PENDING
    - Almacenar precios de productos al momento de compra
    - Publicar OrderCreatedEvent a Kafka
    - _Requisitos: 7.1, 7.2, 7.3, 7.4, 7.16_

  - [x] 14.5 Implementar orquestación de Saga
    - Consumir InventoryReservedEvent y continuar saga
    - Consumir InventoryUnavailableEvent y cancelar pedido
    - Consumir PaymentSuccessEvent y confirmar pedido
    - Consumir PaymentFailedEvent y revertir saga
    - _Requisitos: 7.5, 7.6, 7.7, 7.8, 7.9, 7.10, 7.11, 7.12, 7.13_

  - [x] 14.6 Implementar gestión de estados de pedidos
    - Crear endpoint PUT /api/orders/{id}/status
    - Validar transiciones de estado válidas
    - Establecer timestamps (confirmedAt, shippedAt, deliveredAt)
    - Asignar número de rastreo al cambiar a SHIPPED
    - _Requisitos: 8.1, 8.2, 8.3, 8.4_

  - [x] 14.7 Implementar cancelación de pedidos
    - Crear endpoint PUT /api/orders/{id}/cancel
    - Validar que estado sea PENDING o CONFIRMED
    - Rechazar cancelación si estado es SHIPPED o DELIVERED
    - _Requisitos: 8.5, 8.6_

  - [x] 14.8 Implementar consulta de pedidos
    - Crear endpoints GET /api/orders/{id}
    - Crear endpoint GET /api/orders/user/{userId}
    - Implementar paginación de historial de pedidos
    - Validar autorización (usuario solo ve sus pedidos)
    - _Requisitos: 15.7_

  - [x] 14.9 Implementar tracking de pedidos
    - Crear endpoint GET /api/orders/{id}/tracking
    - Retornar número de rastreo y estado actual
    - _Requisitos: 8.7_

  - [x] 14.10 Escribir property test para atomicidad de Saga
    - **Propiedad 2: Atomicidad de Pedidos (Saga)**
    - **Valida: Requisitos 7.5-7.13**

  - [x] 14.11 Escribir tests de integración para Order Service
    - Test de creación de pedido con saga exitosa
    - Test de saga fallida por inventario insuficiente
    - Test de saga fallida por pago rechazado
    - Test de transiciones de estado
    - _Requisitos: 7, 8, 19.4_

- [ ] 15. Checkpoint - Verificar flujo completo de pedidos
  - Asegurar que todos los tests pasen, verificar que el flujo de pedidos con Saga funcione correctamente. Preguntar al usuario si hay dudas.


- [ ] 16. Implementar Notification Service (Sistema de Notificaciones)
  - [ ] 16.1 Crear modelos de datos para notificaciones
    - Implementar entidad Notification
    - Crear NotificationRepository
    - _Requisitos: 12.1_

  - [ ] 16.2 Configurar integraciones externas
    - Configurar cliente de SendGrid/AWS SES para emails
    - Configurar cliente de Twilio para SMS
    - _Requisitos: 12.6, 12.7_

  - [ ] 16.3 Implementar servicio de envío de emails
    - Crear templates de email para diferentes eventos
    - Implementar envío de email transaccional
    - _Requisitos: 12.1, 12.6_

  - [ ] 16.4 Implementar servicio de envío de SMS
    - Implementar envío de SMS para verificación y alertas
    - _Requisitos: 12.7_

  - [x] 16.5 Implementar consumidores de eventos
    - Consumir OrderConfirmedEvent y enviar email de confirmación
    - Consumir OrderShippedEvent y enviar email con tracking
    - Consumir OrderDeliveredEvent y enviar email de entrega
    - Consumir OrderCancelledEvent y enviar email de cancelación
    - _Requisitos: 12.1, 12.2, 12.3, 12.4, 12.5_

  - [ ] 16.6 Implementar gestión de notificaciones de usuario
    - Crear endpoints GET /api/notifications/user/{userId}
    - Crear endpoint PUT /api/notifications/{id}/read
    - Implementar paginación de notificaciones
    - _Requisitos: 12.1_

  - [ ] 16.7 Escribir tests unitarios para Notification Service
    - Test de envío de emails
    - Test de consumo de eventos y envío automático
    - _Requisitos: 12_

- [x] 17. Implementar Recommendation Service (Recomendaciones Personalizadas)
  - [x] 17.1 Crear modelos de datos para actividad de usuario
    - Implementar entidad UserActivity para tracking de visualizaciones
    - Crear repositorios necesarios
    - _Requisitos: 11.1_

  - [x] 17.2 Implementar algoritmo de collaborative filtering
    - Implementar cálculo de similitud Jaccard entre usuarios
    - Encontrar usuarios con comportamiento similar
    - _Requisitos: 11.2_

  - [x] 17.3 Implementar recomendaciones personalizadas
    - Crear endpoint GET /api/recommendations/user/{userId}
    - Analizar historial de compras y visualizaciones
    - Obtener productos de usuarios similares
    - Combinar con productos populares de categorías de interés
    - Excluir productos ya comprados
    - _Requisitos: 11.1, 11.2, 11.3_

  - [x] 17.4 Implementar productos similares
    - Crear endpoint GET /api/recommendations/product/{id}/similar
    - Retornar productos de misma categoría con características similares
    - _Requisitos: 11.4_

  - [x] 17.5 Implementar productos en tendencia
    - Crear endpoint GET /api/recommendations/trending
    - Retornar productos con mayor número de ventas recientes
    - _Requisitos: 11.6_

  - [x] 17.6 Implementar productos frecuentemente comprados juntos
    - Crear endpoint GET /api/recommendations/frequently-bought-together/{id}
    - Analizar patrones de compra históricos
    - _Requisitos: 11.5_

  - [x] 17.7 Implementar consumidor de eventos para actualizar preferencias
    - Consumir OrderCompletedEvent para actualizar preferencias
    - Consumir ProductViewedEvent para tracking de visualizaciones
    - _Requisitos: 11.7_

  - [x] 17.8 Escribir tests unitarios para Recommendation Service
    - Test de cálculo de similitud entre usuarios
    - Test de generación de recomendaciones personalizadas
    - Test de productos similares
    - _Requisitos: 11_


- [x] 18. Implementar seguridad y manejo de errores
  - [x] 18.1 Implementar manejo global de excepciones
    - Crear GlobalExceptionHandler con @RestControllerAdvice
    - Manejar MethodArgumentNotValidException con detalles de campos
    - Manejar excepciones genéricas sin exponer detalles internos
    - _Requisitos: 14.4, 14.5_

  - [x] 18.2 Implementar validación de entrada con Bean Validation
    - Agregar anotaciones de validación en todos los DTOs
    - Validar formato de email, contraseña, teléfono
    - Sanitizar entradas para prevenir XSS
    - _Requisitos: 15.8, 15.9_

  - [x] 18.3 Implementar sistema de auditoría
    - Crear entidad AuditLog y repositorio
    - Implementar AuditService para logging asíncrono
    - Registrar eventos de login, logout, creación de pedidos
    - Registrar intentos de autenticación fallidos
    - _Requisitos: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6, 16.7_

  - [x] 18.4 Configurar HTTPS/TLS en todos los servicios
    - Configurar SSL/TLS en application.yml
    - Forzar HTTPS en todas las peticiones
    - _Requisitos: 15.1_

  - [x] 18.5 Escribir tests de seguridad
    - Test de validación de entrada
    - Test de manejo de errores
    - Test de auditoría de eventos
    - _Requisitos: 14, 15, 16_


- [ ] 19. Implementar observabilidad y monitoreo
  - [ ] 19.1 Configurar Spring Boot Actuator en todos los servicios
    - Exponer endpoints /actuator/health y /actuator/prometheus
    - Configurar health checks personalizados
    - _Requisitos: 20.5, 24.1_

  - [ ] 19.2 Implementar logging estructurado
    - Configurar Logback con formato JSON
    - Incluir trace IDs para correlación entre servicios
    - Implementar niveles de log apropiados
    - _Requisitos: 13.7, 14.6, 24.2, 24.3_

  - [ ] 19.3 Configurar métricas de performance
    - Medir tiempos de respuesta de todas las operaciones
    - Medir tasa de errores
    - Configurar alertas para umbrales excedidos
    - _Requisitos: 24.4, 24.5, 24.6_

  - [ ] 19.4 Escribir tests de observabilidad
    - Test de health checks
    - Test de generación de métricas
    - _Requisitos: 24_

- [ ] 20. Checkpoint - Verificar servicios backend completos
  - Asegurar que todos los tests pasen, verificar que todos los microservicios funcionen correctamente. Preguntar al usuario si hay dudas.


- [-] 21. Implementar Frontend - Configuración Base
  - [x] 21.1 Crear proyecto React con Vite y TypeScript
    - Inicializar proyecto con Vite
    - Configurar TypeScript con strict mode
    - Instalar dependencias: React Router, Redux Toolkit, React Query, Tailwind CSS
    - _Requisitos: 18_

  - [ ] 21.2 Configurar estructura de carpetas y routing
    - Crear estructura de carpetas (components, pages, hooks, services, store)
    - Configurar React Router v6 con rutas principales
    - _Requisitos: 18_

  - [ ] 21.3 Configurar Redux Toolkit y React Query
    - Configurar store de Redux con slices de auth, cart y ui
    - Configurar QueryClient de React Query
    - _Requisitos: 18_

  - [ ] 21.4 Configurar Tailwind CSS y componentes base
    - Configurar Tailwind CSS
    - Crear componentes comunes (Button, Input, Modal, Spinner, Toast)
    - _Requisitos: 18_

  - [ ] 21.5 Configurar internacionalización (i18n)
    - Configurar react-i18next
    - Crear archivos de traducción en español e inglés
    - Implementar selector de idioma
    - _Requisitos: 18.1, 18.2, 18.3, 18.4, 18.5, 18.6_

  - [ ] 21.6 Crear tipos TypeScript para modelos de datos
    - Definir interfaces para User, Product, Cart, Order
    - Definir enums para estados y roles
    - _Requisitos: 18_


- [ ] 22. Implementar Frontend - Autenticación y Usuario
  - [ ] 22.1 Crear servicios API para autenticación
    - Implementar authApi con métodos register, login, getProfile
    - Configurar axios con interceptores para JWT
    - _Requisitos: 1_

  - [ ] 22.2 Implementar componentes de autenticación
    - Crear LoginForm con validación
    - Crear RegisterForm con validación de contraseña
    - Implementar manejo de errores y mensajes
    - _Requisitos: 1.1, 1.3_

  - [ ] 22.3 Implementar custom hook useAuth
    - Crear hook con login, logout y estado de autenticación
    - Integrar con Redux para estado global
    - Persistir token en localStorage
    - _Requisitos: 1.3, 1.5_

  - [ ] 22.4 Implementar páginas de login y registro
    - Crear página de Login con formulario
    - Crear página de Register con formulario
    - Implementar redirección después de login exitoso
    - _Requisitos: 1_

  - [ ] 22.5 Implementar perfil de usuario y direcciones
    - Crear página UserProfile con edición de datos
    - Crear componente para gestión de direcciones
    - Implementar validación de formularios con React Hook Form y Zod
    - _Requisitos: 2_

  - [ ] 22.6 Escribir tests con Vitest y React Testing Library
    - Test de LoginForm y RegisterForm
    - Test de useAuth hook
    - Test de validación de formularios
    - _Requisitos: 1, 2, 19.3_


- [ ] 23. Implementar Frontend - Catálogo y Búsqueda de Productos
  - [ ] 23.1 Crear servicios API para productos
    - Implementar productApi con searchProducts, getProduct, getReviews
    - _Requisitos: 3, 4, 5_

  - [ ] 23.2 Implementar componentes de productos
    - Crear ProductCard para mostrar producto en grid
    - Crear ProductGrid para lista de productos
    - Crear ProductFilters para filtros de búsqueda
    - _Requisitos: 3, 5_

  - [ ] 23.3 Implementar página de lista de productos
    - Crear página ProductList con búsqueda y filtros
    - Implementar paginación
    - Integrar con useProducts hook y React Query
    - _Requisitos: 5_

  - [ ] 23.4 Implementar página de detalle de producto
    - Crear página ProductDetail con información completa
    - Mostrar imágenes, especificaciones, precio
    - Implementar selector de cantidad
    - Mostrar rating y reseñas
    - _Requisitos: 3, 4_

  - [ ] 23.5 Implementar componente de reseñas
    - Crear ProductReviews para mostrar reseñas paginadas
    - Crear formulario para agregar reseña
    - Mostrar rating promedio y distribución
    - _Requisitos: 4_

  - [ ] 23.6 Implementar barra de búsqueda con autocompletado
    - Crear SearchBar con autocompletado
    - Integrar con endpoint de sugerencias
    - Implementar debounce para optimizar peticiones
    - _Requisitos: 5.7_

  - [ ] 23.7 Escribir tests para componentes de productos
    - Test de ProductCard y ProductGrid
    - Test de ProductFilters
    - Test de ProductDetail
    - _Requisitos: 3, 4, 5, 19.3_


- [ ] 24. Implementar Frontend - Carrito de Compras
  - [ ] 24.1 Crear servicios API para carrito
    - Implementar cartApi con getCart, addItem, updateItem, removeItem
    - _Requisitos: 6_

  - [ ] 24.2 Implementar custom hook useCart
    - Crear hook con operaciones de carrito
    - Integrar con React Query para cache y sincronización
    - _Requisitos: 6_

  - [ ] 24.3 Implementar componentes de carrito
    - Crear CartItem para mostrar item individual
    - Crear CartSummary para mostrar totales
    - Crear CartDrawer para vista lateral del carrito
    - _Requisitos: 6_

  - [ ] 24.4 Implementar página de carrito
    - Crear página Cart con lista de items
    - Implementar actualización de cantidades
    - Implementar eliminación de items
    - Mostrar subtotal, impuestos, envío y total
    - Botón para proceder al checkout
    - _Requisitos: 6_

  - [ ] 24.5 Integrar botón "Agregar al Carrito" en productos
    - Agregar botón en ProductCard y ProductDetail
    - Mostrar feedback visual al agregar
    - Actualizar contador de items en header
    - _Requisitos: 6.1, 6.2_

  - [ ] 24.6 Escribir tests para componentes de carrito
    - Test de CartItem y CartSummary
    - Test de useCart hook
    - Test de actualización y eliminación de items
    - _Requisitos: 6, 19.3_


- [ ] 25. Implementar Frontend - Checkout y Pedidos
  - [ ] 25.1 Crear servicios API para pedidos
    - Implementar orderApi con createOrder, getOrder, getUserOrders, trackOrder
    - _Requisitos: 7, 8_

  - [ ] 25.2 Implementar custom hook useOrders
    - Crear hook con operaciones de pedidos
    - Integrar con React Query
    - _Requisitos: 7, 8_

  - [ ] 25.3 Implementar página de checkout
    - Crear CheckoutForm con pasos múltiples
    - Implementar selección de dirección de envío
    - Implementar selección de método de pago
    - Mostrar resumen del pedido
    - _Requisitos: 7_

  - [ ] 25.4 Implementar procesamiento de pedido
    - Validar datos antes de enviar
    - Mostrar loading durante procesamiento
    - Manejar errores de inventario y pago
    - Redirigir a página de confirmación al éxito
    - _Requisitos: 7_

  - [ ] 25.5 Implementar página de confirmación de pedido
    - Mostrar número de pedido y detalles
    - Mostrar estado del pedido
    - Botón para ver tracking
    - _Requisitos: 7, 8_

  - [ ] 25.6 Implementar historial de pedidos
    - Crear página OrderHistory con lista paginada
    - Mostrar estado de cada pedido
    - Permitir ver detalles de pedido
    - Permitir cancelar pedidos elegibles
    - _Requisitos: 8_

  - [ ] 25.7 Implementar página de tracking de pedido
    - Crear página OrderTracking con estado actual
    - Mostrar número de rastreo si está disponible
    - Mostrar timeline de estados del pedido
    - _Requisitos: 8.7_

  - [ ] 25.8 Escribir tests para componentes de checkout y pedidos
    - Test de CheckoutForm
    - Test de OrderHistory
    - Test de useOrders hook
    - _Requisitos: 7, 8, 19.3_

- [ ] 26. Checkpoint - Verificar frontend completo
  - Asegurar que todos los tests pasen, verificar que el flujo completo de usuario funcione. Preguntar al usuario si hay dudas.


- [ ] 27. Implementar Frontend - Recomendaciones y Página Principal
  - [ ] 27.1 Crear servicios API para recomendaciones
    - Implementar recommendationApi con métodos para recomendaciones personalizadas, similares, trending
    - _Requisitos: 11_

  - [ ] 27.2 Implementar componentes de recomendaciones
    - Crear RecommendationSection para mostrar productos recomendados
    - Crear TrendingProducts para productos en tendencia
    - Crear SimilarProducts para productos similares
    - _Requisitos: 11_

  - [ ] 27.3 Implementar página principal (Home)
    - Mostrar productos en tendencia
    - Mostrar recomendaciones personalizadas si usuario está autenticado
    - Mostrar categorías principales
    - Implementar carrusel de productos destacados
    - _Requisitos: 11_

  - [ ] 27.4 Integrar recomendaciones en página de producto
    - Mostrar productos similares en ProductDetail
    - Mostrar productos frecuentemente comprados juntos
    - _Requisitos: 11.4, 11.5_

  - [ ] 27.5 Escribir tests para componentes de recomendaciones
    - Test de RecommendationSection
    - Test de integración en Home
    - _Requisitos: 11, 19.3_


- [ ] 28. Implementar Frontend - Layout y Navegación
  - [ ] 28.1 Implementar componente Header
    - Crear Header con logo, barra de búsqueda, carrito y usuario
    - Mostrar contador de items en carrito
    - Implementar menú de usuario con dropdown
    - Implementar selector de idioma
    - _Requisitos: 18_

  - [ ] 28.2 Implementar componente Footer
    - Crear Footer con enlaces y información
    - _Requisitos: 18_

  - [ ] 28.3 Implementar navegación y rutas protegidas
    - Configurar rutas públicas y privadas
    - Implementar ProtectedRoute para rutas que requieren autenticación
    - Implementar redirección a login si no autenticado
    - _Requisitos: 15.5, 15.7_

  - [ ] 28.4 Implementar componente de notificaciones Toast
    - Crear sistema de notificaciones toast para feedback
    - Integrar en operaciones de carrito, pedidos, etc.
    - _Requisitos: 18_

  - [ ] 28.5 Escribir tests para componentes de layout
    - Test de Header y Footer
    - Test de navegación y rutas protegidas
    - _Requisitos: 18, 19.3_


- [ ] 29. Implementar tests end-to-end con Playwright
  - [ ] 29.1 Configurar Playwright
    - Instalar y configurar Playwright
    - Configurar navegadores y entornos de prueba
    - _Requisitos: 19.5_

  - [ ] 29.2 Escribir tests E2E para flujo de registro y login
    - Test de registro de usuario completo
    - Test de login exitoso y fallido
    - Test de logout
    - _Requisitos: 1, 19.5_

  - [ ] 29.3 Escribir tests E2E para flujo de búsqueda y productos
    - Test de búsqueda de productos
    - Test de filtrado por categoría y precio
    - Test de visualización de detalle de producto
    - _Requisitos: 3, 5, 19.5_

  - [ ] 29.4 Escribir tests E2E para flujo de carrito
    - Test de agregar productos al carrito
    - Test de actualizar cantidades
    - Test de eliminar items del carrito
    - _Requisitos: 6, 19.5_

  - [ ] 29.5 Escribir tests E2E para flujo completo de compra
    - Test de checkout completo desde carrito hasta confirmación
    - Test de selección de dirección y método de pago
    - Test de visualización de pedido confirmado
    - _Requisitos: 7, 8, 19.5_

  - [ ] 29.6 Escribir tests E2E para gestión de pedidos
    - Test de visualización de historial de pedidos
    - Test de tracking de pedido
    - Test de cancelación de pedido
    - _Requisitos: 8, 19.5_


- [ ] 30. Optimización de performance y caching
  - [ ] 30.1 Optimizar consultas a base de datos
    - Revisar y optimizar queries N+1
    - Agregar índices faltantes en MySQL
    - Implementar fetch strategies apropiadas (LAZY/EAGER)
    - _Requisitos: 17.6, 23.5_

  - [ ] 30.2 Optimizar configuración de connection pooling
    - Configurar HikariCP con parámetros optimizados
    - Configurar Lettuce para Redis
    - _Requisitos: 17.5_

  - [ ] 30.3 Optimizar configuración de Kafka
    - Configurar batch size y compression en productores
    - Configurar max poll records en consumidores
    - _Requisitos: 17.9_

  - [ ] 30.4 Implementar lazy loading en frontend
    - Implementar code splitting con React.lazy
    - Implementar lazy loading de imágenes
    - _Requisitos: 18_

  - [ ] 30.5 Optimizar bundle size del frontend
    - Analizar bundle con Vite bundle analyzer
    - Eliminar dependencias no utilizadas
    - Implementar tree shaking
    - _Requisitos: 18_

  - [ ] 30.6 Ejecutar tests de carga con JMeter/Gatling
    - Test de búsqueda con 1000 usuarios concurrentes
    - Test de creación de pedidos con 100 usuarios concurrentes
    - Verificar tiempos de respuesta según requisitos
    - _Requisitos: 19.7, 19.8_

- [ ] 31. Checkpoint - Verificar performance y optimizaciones
  - Asegurar que los tests de carga pasen, verificar que los tiempos de respuesta cumplan con los requisitos. Preguntar al usuario si hay dudas.


- [ ] 32. Configurar Docker y despliegue
  - [ ] 32.1 Crear Dockerfiles para cada microservicio
    - Crear Dockerfile multi-stage para servicios Spring Boot
    - Optimizar tamaño de imágenes con Alpine
    - _Requisitos: 20.1, 20.3, 20.4_

  - [ ] 32.2 Crear Dockerfile para frontend
    - Crear Dockerfile multi-stage con build de Vite
    - Configurar nginx para servir aplicación React
    - _Requisitos: 20.1, 20.3, 20.4_

  - [ ] 32.3 Actualizar docker-compose.yml completo
    - Incluir todos los microservicios
    - Configurar redes y volúmenes
    - Configurar variables de entorno
    - Configurar health checks
    - _Requisitos: 20.2, 20.6_

  - [ ] 32.4 Crear scripts de inicialización de base de datos
    - Crear scripts SQL para crear esquemas
    - Crear datos de prueba (categorías, productos de ejemplo)
    - _Requisitos: 20_

  - [ ] 32.5 Documentar proceso de despliegue
    - Crear README con instrucciones de instalación
    - Documentar variables de entorno necesarias
    - Documentar comandos de Docker Compose
    - _Requisitos: 20, 23.4_


- [ ] 33. Implementar Panel de Administración (Admin Dashboard)
  - [ ] 33.1 Crear Admin Service con endpoints de gestión de usuarios
    - Implementar endpoints GET/PUT/DELETE /api/admin/users con paginación y filtros
    - Implementar endpoint PUT /api/admin/users/{id}/status para suspender/activar usuarios
    - Implementar endpoint GET /api/admin/users/{id} para ver detalle de usuario
    - Restringir acceso a rol ADMIN mediante Spring Security
    - _Requisitos: 38.1, 38.2_

  - [ ] 33.2 Implementar gestión de productos y categorías desde admin
    - Crear endpoints POST/PUT/DELETE /api/admin/products para CRUD completo
    - Crear endpoints POST/PUT/DELETE /api/admin/categories para gestión de categorías
    - Implementar aprobación/rechazo de productos de vendedores
    - _Requisitos: 38.3, 38.4_

  - [ ] 33.3 Implementar gestión de reseñas y pedidos desde admin
    - Crear endpoint DELETE /api/admin/reviews/{id} para eliminar reseñas inapropiadas
    - Crear endpoint GET /api/admin/orders con filtros por estado, fecha y usuario
    - Crear endpoint PUT /api/admin/orders/{id}/status para actualizar estado manualmente
    - _Requisitos: 38.5, 38.6_

  - [ ] 33.4 Implementar gestión de cupones desde admin
    - Crear endpoints CRUD /api/admin/coupons para gestión completa de cupones
    - _Requisitos: 38.7_

  - [ ] 33.5 Implementar reportes y métricas del dashboard
    - Crear endpoint GET /api/admin/reports/sales con ventas por período
    - Crear endpoint GET /api/admin/reports/users con métricas de usuarios
    - Crear endpoint GET /api/admin/reports/products con productos más vendidos
    - Crear endpoint GET /api/admin/dashboard/summary con KPIs principales
    - _Requisitos: 38.8_

  - [ ] 33.6 Implementar frontend del panel de administración
    - Crear rutas protegidas /admin/* con verificación de rol ADMIN
    - Crear página AdminDashboard con KPIs y gráficas (recharts)
    - Crear páginas de gestión: AdminUsers, AdminProducts, AdminOrders, AdminCoupons
    - _Requisitos: 38.1, 38.2, 38.3, 38.5, 38.6, 38.7, 38.8_

  - [ ] 33.7 Escribir tests para Admin Service
    - Test de autorización (solo ADMIN puede acceder)
    - Test de endpoints de gestión de usuarios y productos
    - Test de generación de reportes
    - _Requisitos: 38_


- [ ] 34. Implementar Portal de Vendedores (Seller Portal)
  - [ ] 34.1 Implementar registro y aprobación de vendedores
    - Crear endpoint POST /api/sellers/register con datos de tienda (nombre, descripción, RFC)
    - Implementar flujo de aprobación por admin (estado PENDING → APPROVED/REJECTED)
    - Agregar campo sellerStatus a entidad User y lógica de transición
    - _Requisitos: 39.1, 39.2_

  - [ ] 34.2 Implementar dashboard de vendedor
    - Crear endpoint GET /api/sellers/{sellerId}/dashboard con ventas, pedidos y métricas
    - Crear endpoint GET /api/sellers/{sellerId}/orders con pedidos de sus productos
    - _Requisitos: 39.3, 39.6_

  - [ ] 34.3 Implementar gestión de catálogo propio del vendedor
    - Crear endpoints CRUD /api/sellers/{sellerId}/products para gestión de su catálogo
    - Validar que vendedor solo pueda modificar sus propios productos
    - Implementar publicación de ProductCreatedEvent/ProductUpdatedEvent al Kafka
    - _Requisitos: 39.4_

  - [ ] 34.4 Implementar gestión de inventario del vendedor
    - Crear endpoint PUT /api/sellers/{sellerId}/products/{productId}/inventory
    - Validar que inventario no sea negativo
    - _Requisitos: 39.5_

  - [ ] 34.5 Implementar perfil de tienda del vendedor
    - Crear endpoints GET/PUT /api/sellers/{sellerId}/profile para ver y editar perfil de tienda
    - Incluir nombre, descripción, logo, calificación promedio
    - _Requisitos: 39.7_

  - [ ] 34.6 Implementar frontend del portal de vendedores
    - Crear rutas protegidas /seller/* con verificación de rol SELLER
    - Crear página SellerDashboard con métricas de ventas
    - Crear páginas SellerProducts, SellerOrders, SellerProfile
    - _Requisitos: 39.1, 39.3, 39.4, 39.5, 39.6, 39.7_

  - [ ] 34.7 Escribir tests para Seller Portal
    - Test de registro y flujo de aprobación
    - Test de aislamiento (vendedor solo ve sus productos/pedidos)
    - _Requisitos: 39_


- [ ] 35. Implementar Wishlist / Lista de Deseos
  - [ ] 35.1 Crear modelos de datos y repositorios para wishlist
    - Implementar entidades Wishlist y WishlistItem en MySQL
    - Crear WishlistRepository con índices por userId
    - Definir campo isPublic para visibilidad pública/privada
    - _Requisitos: 40.1, 40.4_

  - [ ] 35.2 Implementar CRUD de wishlists
    - Crear endpoint POST /api/wishlists para crear lista con nombre
    - Crear endpoint GET /api/wishlists/user/{userId} para listar wishlists del usuario
    - Crear endpoint PUT /api/wishlists/{id} para editar nombre y visibilidad
    - Crear endpoint DELETE /api/wishlists/{id}
    - _Requisitos: 40.1, 40.4_

  - [ ] 35.3 Implementar gestión de productos en wishlist
    - Crear endpoint POST /api/wishlists/{id}/items para agregar producto
    - Crear endpoint DELETE /api/wishlists/{id}/items/{productId} para eliminar
    - Crear endpoint POST /api/wishlists/{id}/items/{productId}/move-to-cart para mover al carrito
    - _Requisitos: 40.2, 40.3, 40.5_

  - [ ] 35.4 Implementar compartir wishlist y notificaciones de precio
    - Crear endpoint GET /api/wishlists/{id}/share para obtener URL pública
    - Implementar consumidor de ProductPriceChangedEvent para notificar usuarios con el producto en wishlist
    - _Requisitos: 40.4, 40.6_

  - [ ] 35.5 Implementar frontend de wishlist
    - Crear botón "Agregar a lista de deseos" en ProductCard y ProductDetail
    - Crear página WishlistPage con gestión de listas y productos
    - Implementar selector de lista al agregar producto
    - _Requisitos: 40.1, 40.2, 40.3, 40.5_

  - [ ] 35.6 Escribir tests para Wishlist Service
    - Test de CRUD de wishlists y items
    - Test de mover item al carrito
    - Test de visibilidad pública/privada
    - _Requisitos: 40_


- [ ] 36. Implementar Gestión de Cupones y Promociones
  - [ ] 36.1 Crear modelos de datos para cupones
    - Implementar entidad Coupon con campos: code, discountType (PERCENTAGE/FIXED), discountValue, minOrderAmount, maxUses, usedCount, startDate, endDate, isActive
    - Crear CouponRepository con índice único en code
    - _Requisitos: 41.1, 41.2, 41.3, 41.4, 41.5_

  - [ ] 36.2 Implementar CRUD de cupones (admin)
    - Crear endpoints CRUD /api/admin/coupons
    - Validar que endDate > startDate y discountValue > 0
    - _Requisitos: 41.1, 41.2, 41.3, 41.4, 41.5_

  - [ ] 36.3 Implementar algoritmo de validación de cupones
    - Crear endpoint POST /api/coupons/validate con código y monto del pedido
    - Validar: cupón existe, está activo, dentro de fechas de vigencia, no excede límite de usos, monto mínimo cumplido
    - Retornar monto de descuento calculado
    - _Requisitos: 41.6_

  - [ ] 36.4 Integrar validación de cupones en Cart Service
    - Actualizar endpoint POST /api/cart/{userId}/apply-coupon para usar CouponService
    - Incrementar usedCount al confirmar pedido
    - Publicar CouponAppliedEvent a Kafka
    - _Requisitos: 41.6_

  - [ ] 36.5 Escribir property test para validación de cupones
    - **Propiedad 6: Invariantes de Cupones**
    - **Valida: Requisitos 41.6**

  - [ ] 36.6 Escribir tests unitarios para Coupon Service
    - Test de validación con cupón expirado, límite excedido, monto insuficiente
    - Test de cálculo de descuento porcentual y fijo
    - _Requisitos: 41_


- [ ] 37. Implementar Devoluciones y Disputas
  - [ ] 37.1 Crear modelos de datos para devoluciones
    - Implementar entidad Return con campos: orderId, userId, reason, status (REQUESTED/APPROVED/REJECTED/COMPLETED), returnLabel, refundAmount, createdAt
    - Implementar entidad Dispute con campos: returnId, description, status (OPEN/RESOLVED)
    - Crear repositorios con índices por orderId y userId
    - _Requisitos: 42.1, 42.2, 42.6_

  - [ ] 37.2 Implementar solicitud y gestión de devoluciones
    - Crear endpoint POST /api/returns con orderId y motivo
    - Validar que pedido esté en estado DELIVERED y dentro del período de devolución
    - Crear endpoint GET /api/returns/user/{userId} para historial de devoluciones
    - _Requisitos: 42.1, 42.2_

  - [ ] 37.3 Implementar aprobación/rechazo y etiqueta de retorno
    - Crear endpoint PUT /api/admin/returns/{id}/approve para aprobar devolución
    - Crear endpoint PUT /api/admin/returns/{id}/reject con motivo de rechazo
    - Al aprobar, generar URL de etiqueta de retorno y actualizar estado
    - _Requisitos: 42.3, 42.4_

  - [ ] 37.4 Implementar reembolso automático y disputas
    - Al completar devolución, publicar ReturnCompletedEvent a Kafka
    - Consumir ReturnCompletedEvent en Payment Service para procesar reembolso automático
    - Crear endpoint POST /api/returns/{id}/dispute para abrir disputa
    - Crear endpoint PUT /api/admin/disputes/{id}/resolve para resolver disputa
    - _Requisitos: 42.5, 42.6_

  - [ ] 37.5 Implementar frontend de devoluciones
    - Agregar botón "Solicitar devolución" en OrderDetail para pedidos entregados
    - Crear página ReturnRequest con formulario de motivo
    - Crear página ReturnHistory con estado de devoluciones
    - _Requisitos: 42.1, 42.2, 42.3_

  - [ ] 37.6 Escribir tests para Return Service
    - Test de validación de período de devolución
    - Test de flujo completo: solicitud → aprobación → reembolso
    - _Requisitos: 42_


- [ ] 38. Implementar Verificación de Email y 2FA
  - [ ] 38.1 Implementar verificación de email al registro
    - Al registrar usuario, generar token de verificación UUID y almacenar en Redis con TTL 24h
    - Publicar EmailVerificationEvent a Kafka para que Notification Service envíe email
    - Crear endpoint GET /api/users/verify-email?token={token} para confirmar verificación
    - Actualizar campo emailVerified=true al verificar
    - _Requisitos: 43.1, 43.2_

  - [ ] 38.2 Implementar reenvío de email de verificación
    - Crear endpoint POST /api/users/resend-verification con rate limiting (máx 3 por hora)
    - Invalidar token anterior y generar nuevo
    - _Requisitos: 43.3_

  - [ ] 38.3 Implementar TOTP con Google Authenticator
    - Agregar dependencia de librería TOTP (e.g., java-otp o similar)
    - Crear endpoint POST /api/users/{userId}/2fa/setup para generar secreto TOTP y QR code
    - Crear endpoint POST /api/users/{userId}/2fa/verify para verificar código TOTP y activar 2FA
    - Almacenar secreto TOTP encriptado con AES-256 en base de datos
    - _Requisitos: 43.4, 43.5_

  - [ ] 38.4 Implementar códigos de respaldo y flujo de login con 2FA
    - Al activar 2FA, generar 8 códigos de respaldo de un solo uso y retornarlos al usuario
    - Almacenar hashes de códigos de respaldo en base de datos
    - Modificar endpoint de login para solicitar código TOTP si 2FA está activo
    - Crear endpoint POST /api/users/{userId}/2fa/disable para desactivar 2FA
    - _Requisitos: 43.6, 43.7_

  - [ ] 38.5 Implementar frontend de verificación y 2FA
    - Mostrar banner de "Verifica tu email" si emailVerified=false
    - Crear página TwoFactorSetup con QR code y campo de verificación
    - Modificar LoginForm para mostrar campo de código 2FA cuando aplique
    - Mostrar códigos de respaldo en modal al activar 2FA
    - _Requisitos: 43.1, 43.4, 43.5, 43.6_

  - [ ] 38.6 Escribir tests para verificación y 2FA
    - Test de flujo de verificación de email
    - Test de generación y validación de TOTP
    - Test de códigos de respaldo de un solo uso
    - _Requisitos: 43_


- [ ] 39. Implementar Gestión de Imágenes y Almacenamiento
  - [ ] 39.1 Crear File Storage Service con integración a S3
    - Crear módulo file-storage-service con cliente AWS S3 (SDK v2)
    - Implementar método uploadFile(MultipartFile, bucket, key) que retorne URL pública
    - Implementar método deleteFile(bucket, key) para eliminar objeto de S3
    - Configurar bucket, región y credenciales vía variables de entorno
    - _Requisitos: 44.1_

  - [ ] 39.2 Implementar validación de formato y tamaño
    - Validar que el archivo sea imagen (JPEG, PNG, WebP, GIF)
    - Validar que el tamaño no exceda 5MB
    - Retornar error 400 con mensaje descriptivo si validación falla
    - _Requisitos: 44.2_

  - [ ] 39.3 Implementar generación automática de miniaturas
    - Al subir imagen, generar miniaturas de 150x150 y 400x400 píxeles usando Thumbnailator
    - Subir miniaturas a S3 con sufijos _thumb y _medium en el key
    - Retornar URLs de imagen original y miniaturas en la respuesta
    - _Requisitos: 44.3_

  - [ ] 39.4 Configurar CDN y eliminación al borrar producto
    - Configurar CloudFront distribution apuntando al bucket S3
    - Retornar URLs de CloudFront en lugar de URLs directas de S3
    - Consumir ProductDeletedEvent de Kafka para eliminar imágenes asociadas de S3
    - _Requisitos: 44.4, 44.5_

  - [ ] 39.5 Integrar File Storage Service en Product Service
    - Crear endpoint POST /api/products/{id}/images que use FileStorageService
    - Almacenar URLs de imágenes en tabla product_images
    - Incluir URLs de imágenes en ProductResponse
    - _Requisitos: 44.1, 44.3, 44.4_

  - [ ] 39.6 Escribir tests para File Storage Service
    - Test de validación de formato y tamaño
    - Test de generación de miniaturas
    - Test de eliminación al borrar producto (mock S3)
    - _Requisitos: 44_


- [ ] 40. Implementar SEO y Rendimiento del Frontend
  - [ ] 40.1 Implementar URLs amigables y meta tags dinámicos
    - Configurar React Router con slugs en URLs (e.g., /products/{slug}-{id})
    - Instalar react-helmet-async para gestión de meta tags
    - Implementar meta tags dinámicos (title, description, og:image) en ProductDetail, CategoryPage y HomePage
    - _Requisitos: 45.1, 45.2_

  - [ ] 40.2 Implementar sitemap XML y JSON-LD structured data
    - Crear endpoint GET /sitemap.xml en un servicio backend que genere sitemap dinámico con productos y categorías
    - Implementar JSON-LD structured data (Product schema) en ProductDetail
    - Implementar JSON-LD BreadcrumbList en páginas de categoría
    - _Requisitos: 45.3, 45.4_

  - [ ] 40.3 Implementar lazy loading y prefetching
    - Aplicar React.lazy y Suspense en todas las rutas de la aplicación
    - Implementar lazy loading de imágenes con atributo loading="lazy" y componente LazyImage
    - Implementar prefetching de rutas frecuentes con React Router prefetch
    - _Requisitos: 45.5, 45.6_

  - [ ] 40.4 Escribir tests para SEO y performance
    - Test de renderizado de meta tags correctos por página
    - Test de lazy loading de componentes
    - _Requisitos: 45_


- [ ] 41. Implementar Gestión de Envíos e Integración con Carriers
  - [ ] 41.1 Crear Shipping Service con cálculo de costos
    - Crear módulo shipping-service con entidad ShippingRate
    - Implementar endpoint POST /api/shipping/calculate con origen, destino y peso
    - Implementar lógica de cálculo para opciones STANDARD (3-5 días) y EXPRESS (1-2 días)
    - _Requisitos: 46.1, 46.2_

  - [ ] 41.2 Implementar integración con carriers (Estafeta/FedEx/DHL)
    - Crear interfaz CarrierClient con métodos calculateRate y createShipment
    - Implementar adaptadores EstafetaClient, FedExClient, DhlClient con sus APIs REST
    - Implementar fallback a tarifa calculada localmente si carrier no responde
    - _Requisitos: 46.3_

  - [ ] 41.3 Implementar creación de envío y número de rastreo real
    - Al cambiar pedido a SHIPPED, llamar a carrier API para crear envío
    - Almacenar número de rastreo real retornado por carrier en Order
    - Publicar OrderShippedEvent con número de rastreo a Kafka
    - _Requisitos: 46.4_

  - [ ] 41.4 Implementar webhooks de carriers y fecha estimada
    - Crear endpoint POST /api/shipping/webhooks/{carrier} para recibir actualizaciones de estado
    - Actualizar estado del pedido al recibir webhook de entrega
    - Calcular y almacenar estimatedDeliveryDate al crear envío
    - _Requisitos: 46.5, 46.6_

  - [ ] 41.5 Integrar Shipping Service en checkout frontend
    - Mostrar opciones de envío con costo y tiempo estimado en paso de checkout
    - Actualizar total del pedido al seleccionar opción de envío
    - Mostrar número de rastreo y fecha estimada en OrderTracking
    - _Requisitos: 46.1, 46.2, 46.4, 46.6_

  - [ ] 41.6 Escribir tests para Shipping Service
    - Test de cálculo de tarifas con diferentes pesos y destinos
    - Test de fallback cuando carrier no responde
    - Test de procesamiento de webhooks
    - _Requisitos: 46_


- [ ] 42. Implementar Privacidad de Datos LFPDPPP/GDPR
  - [ ] 42.1 Implementar aviso de privacidad y consentimiento de cookies
    - Crear endpoint GET /api/privacy/notice para retornar aviso de privacidad vigente
    - Crear entidad ConsentRecord con campos: userId, consentType, granted, timestamp, ipAddress
    - Crear endpoint POST /api/privacy/consent para registrar consentimiento
    - Implementar banner de cookies en frontend con opciones de aceptar/rechazar por categoría
    - _Requisitos: 47.1, 47.2, 47.6_

  - [ ] 42.2 Implementar derecho de acceso a datos personales
    - Crear endpoint GET /api/privacy/my-data para exportar todos los datos del usuario en JSON
    - Incluir: perfil, direcciones, pedidos, reseñas, consentimientos y actividad
    - Implementar generación asíncrona del reporte y notificación por email cuando esté listo
    - _Requisitos: 47.3_

  - [ ] 42.3 Implementar derecho al olvido y rectificación
    - Crear endpoint DELETE /api/privacy/my-data para anonimizar datos del usuario
    - Anonimizar: email → anon_{id}@deleted.com, nombre → "Usuario Eliminado", teléfono → null
    - Conservar pedidos con datos anonimizados para integridad contable
    - Crear endpoint PUT /api/privacy/my-data para rectificación de datos personales
    - _Requisitos: 47.4, 47.5_

  - [ ] 42.4 Implementar retención de datos y notificación de brechas
    - Crear job programado (@Scheduled) que elimine cuentas inactivas >3 años y logs >1 año
    - Crear endpoint POST /api/admin/privacy/breach-notification para notificar a usuarios afectados
    - Enviar email de notificación de brecha a usuarios afectados vía Notification Service
    - _Requisitos: 47.7, 47.8_

  - [ ] 42.5 Implementar frontend de privacidad
    - Crear página PrivacyCenter con opciones de acceso, rectificación y eliminación de datos
    - Implementar banner de consentimiento de cookies con react-cookie-consent
    - Agregar enlace a aviso de privacidad en Footer
    - _Requisitos: 47.1, 47.2, 47.3, 47.4, 47.5_

  - [ ] 42.6 Escribir tests para módulo de privacidad
    - Test de exportación de datos del usuario
    - Test de anonimización (derecho al olvido)
    - Test de registro de consentimientos
    - _Requisitos: 47_

- [ ] 43. Implementar Refresh Tokens y Gestión de Sesiones
  - [ ] 43.1 Implementar emisión de refresh tokens en User Service
    - Modificar endpoint POST /api/users/login para emitir access token (15min) y refresh token opaco (7 días)
    - Almacenar hash del refresh token en Redis con key `refresh_token:{sha256(token)}` y TTL 7 días
    - Retornar access token en body y refresh token en cookie HttpOnly Secure SameSite=Strict
    - _Requisitos: 48.1, 48.4_

  - [ ] 43.2 Implementar endpoint de renovación de tokens
    - Crear endpoint POST /api/users/refresh que lea el refresh token de la cookie
    - Validar existencia en Redis y pertenencia al usuario
    - Emitir nuevo access token y nuevo refresh token (rotación), invalidar el anterior
    - Implementar detección de reutilización: si token ya fue invalidado, revocar todos los tokens del usuario
    - _Requisitos: 48.2, 48.3, 48.5, 48.7_

  - [ ] 43.3 Implementar logout y logout de todos los dispositivos
    - Modificar endpoint POST /api/users/logout para eliminar refresh token de Redis
    - Crear endpoint POST /api/users/logout-all que elimine todos los refresh tokens del usuario
    - _Requisitos: 48.6, 48.9_

  - [ ] 43.4 Actualizar frontend para gestión segura de tokens
    - Almacenar access token en memoria (variable de módulo, no localStorage)
    - Configurar interceptor de axios para renovar access token automáticamente al recibir 401
    - Implementar lógica de retry con el nuevo access token tras renovación exitosa
    - _Requisitos: 48.10_

  - [ ] 43.5 Escribir tests para refresh tokens
    - Test de rotación de tokens (nuevo refresh invalida el anterior)
    - Test de detección de reutilización de token robado
    - Test de logout y logout-all
    - _Requisitos: 48_


- [ ] 44. Implementar Métodos de Pago Locales (OXXO y SPEI)
  - [ ] 44.1 Implementar generación de voucher OXXO
    - Crear endpoint POST /api/payments/oxxo con orderId y amount
    - Crear PaymentIntent en Stripe con payment_method_types=['oxxo'] y expiración de 72 horas
    - Almacenar paymentIntentId, voucherNumber y expiresAt en base de datos
    - Publicar OxxoVoucherCreatedEvent a Kafka para que Notification Service envíe el voucher por email
    - _Requisitos: 49.2, 49.3, 49.4_

  - [ ] 44.2 Implementar generación de referencia SPEI
    - Crear endpoint POST /api/payments/spei con orderId y amount
    - Crear PaymentIntent en Stripe con customer_balance y expiración de 24 horas
    - Almacenar CLABE, datos bancarios y expiresAt en base de datos
    - Publicar SpeiReferenceCreatedEvent a Kafka para notificación por email
    - _Requisitos: 49.5, 49.6_

  - [ ] 44.3 Implementar manejo de webhooks para OXXO y SPEI
    - Actualizar endpoint POST /api/payments/webhooks/stripe para manejar payment_intent.succeeded de OXXO y SPEI
    - Publicar PaymentSuccessEvent al confirmar pago
    - Publicar PaymentFailedEvent con motivo EXPIRED cuando el voucher/referencia expire sin pago
    - _Requisitos: 49.7, 49.8_

  - [ ] 44.4 Registrar método de pago en transacciones
    - Agregar campo paymentMethodType (CARD, OXXO, SPEI) a entidad Payment
    - Incluir paymentMethodType en reportes y logs de auditoría
    - _Requisitos: 49.10_

  - [ ] 44.5 Actualizar frontend de checkout con métodos locales
    - Agregar opciones OXXO y SPEI en el selector de método de pago del checkout
    - Mostrar instrucciones específicas y tiempo de acreditación para cada método
    - Mostrar voucher OXXO (número de referencia y barcode) o datos SPEI (CLABE) en página de confirmación
    - _Requisitos: 49.9_

  - [ ] 44.6 Escribir tests para pagos locales
    - Test de generación de voucher OXXO con expiración correcta
    - Test de generación de referencia SPEI
    - Test de procesamiento de webhook de pago exitoso
    - _Requisitos: 49_


- [ ] 45. Implementar Rate Limiting Granular por Endpoint
  - [ ] 45.1 Implementar filtro de rate limiting granular en API Gateway
    - Crear GranularRateLimitFilter como GlobalFilter en Spring Cloud Gateway
    - Implementar lógica de conteo con Redis usando INCR y EXPIRE
    - Configurar límites por endpoint: login (5/min), register (3/hora), 2fa/verify (10/min), orders y payments (20/min)
    - _Requisitos: 50.1, 50.3, 50.5, 50.6, 50.8_

  - [ ] 45.2 Implementar bloqueo temporal para login fallido
    - Detectar 5 intentos fallidos consecutivos de login desde la misma IP
    - Almacenar bloqueo en Redis con key `blocked:login:{ip}` y TTL de 15 minutos
    - Verificar bloqueo activo antes de procesar el intento de login
    - _Requisitos: 50.2_

  - [ ] 45.3 Agregar headers de rate limiting en respuestas
    - Incluir X-RateLimit-Limit, X-RateLimit-Remaining y X-RateLimit-Reset en todas las respuestas de endpoints con límite granular
    - Incluir header Retry-After en respuestas 429
    - _Requisitos: 50.7, 50.9_

  - [ ] 45.4 Registrar excesos de rate limit en auditoría
    - Al exceder el límite de un endpoint sensible, publicar evento de auditoría con IP, endpoint y timestamp
    - Almacenar en tabla audit_logs con tipo RATE_LIMIT_EXCEEDED
    - _Requisitos: 50.10_

  - [ ] 45.5 Escribir tests para rate limiting granular
    - Test de bloqueo de IP tras 5 intentos fallidos de login
    - Test de headers X-RateLimit-* en respuestas
    - Test de Retry-After en respuesta 429
    - _Requisitos: 50_


- [ ] 46. Checkpoint - Verificar nuevos módulos implementados
  - Asegurar que todos los tests de los módulos 33-45 pasen. Verificar integración entre Admin, Seller Portal, Wishlist, Cupones, Devoluciones, 2FA, Almacenamiento, SEO, Envíos, Privacidad, Refresh Tokens, Pagos Locales y Rate Limiting Granular. Preguntar al usuario si hay dudas.


- [ ] 47. Documentación y finalización
  - [ ] 44.1 Generar documentación de APIs con Swagger/OpenAPI
    - Configurar Springdoc OpenAPI en cada microservicio
    - Agregar anotaciones de documentación en controllers
    - Generar especificaciones OpenAPI
    - _Requisitos: 23.3_

  - [ ] 44.2 Crear documentación técnica
    - Documentar arquitectura del sistema
    - Documentar flujos principales (Saga de pedidos)
    - Documentar configuración de infraestructura
    - _Requisitos: 23_

  - [ ] 44.3 Crear guía de desarrollo
    - Documentar cómo agregar nuevos microservicios
    - Documentar estándares de código
    - Documentar proceso de testing
    - _Requisitos: 23_

  - [ ] 44.4 Verificar cobertura de tests
    - Ejecutar análisis de cobertura en backend
    - Ejecutar análisis de cobertura en frontend
    - Verificar que se alcance mínimo 80% de cobertura
    - _Requisitos: 19.1_

  - [ ] 44.5 Revisión final de seguridad
    - Verificar que todas las contraseñas estén encriptadas
    - Verificar que datos sensibles estén protegidos
    - Verificar que validaciones estén implementadas
    - Verificar que auditoría esté funcionando
    - _Requisitos: 15, 16_

- [ ] 48. Checkpoint final - Sistema completo
  - Ejecutar todos los tests (unitarios, integración, E2E), verificar que el sistema completo funcione correctamente. Preguntar al usuario si hay dudas o ajustes finales.

## Notas

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia los requisitos específicos que implementa para trazabilidad
- Los checkpoints aseguran validación incremental del progreso
- Los property tests validan propiedades universales de corrección
- Los tests unitarios validan ejemplos específicos y casos edge
- Se recomienda ejecutar tests frecuentemente durante el desarrollo
- El proyecto sigue arquitectura de microservicios con comunicación asíncrona vía Kafka
- La implementación usa Saga Pattern para transacciones distribuidas en pedidos
- El sistema soporta internacionalización en Español e Inglés

