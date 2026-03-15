# Documento de Requisitos: Amazon Clone E-Commerce

## Introducción

Este documento especifica los requisitos funcionales y no funcionales para una plataforma de comercio electrónico tipo Amazon. El sistema implementa una arquitectura de microservicios con Java 21 y Spring Boot en el backend, React con TypeScript en el frontend, y soporta funcionalidades completas de e-commerce incluyendo gestión de usuarios, catálogo de productos, carrito de compras, procesamiento de pedidos, pagos, búsqueda avanzada, recomendaciones personalizadas y soporte multiidioma (Español/Inglés).

## Glosario

- **Sistema**: La plataforma completa de e-commerce incluyendo todos los microservicios
- **Load_Balancer**: Componente que distribuye tráfico entre múltiples instancias de API Gateway (Nginx/HAProxy)
- **Redis_Cache**: Cluster de Redis configurado con arquitectura master-replica para cache distribuido
- **API_Gateway**: Servicio que actúa como punto de entrada único para todas las peticiones
- **User_Service**: Microservicio responsable de autenticación y gestión de usuarios
- **Product_Service**: Microservicio responsable del catálogo de productos
- **Cart_Service**: Microservicio responsable de la gestión del carrito de compras
- **Order_Service**: Microservicio responsable del procesamiento de pedidos
- **Payment_Service**: Microservicio responsable del procesamiento de pagos
- **Search_Service**: Microservicio responsable de búsqueda y filtrado de productos
- **Recommendation_Service**: Microservicio responsable de recomendaciones personalizadas
- **Notification_Service**: Microservicio responsable de envío de notificaciones
- **Inventory_Service**: Microservicio responsable de gestión de inventario
- **Usuario**: Persona que interactúa con la plataforma (cliente, vendedor o administrador)
- **Cliente**: Usuario con rol CUSTOMER que puede comprar productos
- **Vendedor**: Usuario con rol SELLER que puede gestionar productos
- **Administrador**: Usuario con rol ADMIN con permisos completos
- **Producto**: Artículo disponible para la venta en la plataforma
- **Pedido**: Solicitud de compra realizada por un cliente
- **Carrito**: Colección temporal de productos seleccionados por un usuario
- **Token_JWT**: Token de autenticación JSON Web Token con expiración de 24 horas
- **Saga**: Patrón de transacción distribuida para garantizar consistencia entre microservicios
- **Inventario_Disponible**: Cantidad de producto disponible para reserva
- **Inventario_Reservado**: Cantidad de producto reservada en pedidos confirmados
- **Cache_Hit_Rate**: Porcentaje de peticiones que encuentran datos en cache sin consultar la base de datos
- **TTL**: Time To Live - tiempo de expiración configurado para datos en cache
- **Upstream**: Pool de instancias backend configuradas en el Load Balancer
- **Health_Check**: Verificación periódica del estado de salud de instancias backend
- **Rate_Limit**: Límite de peticiones permitidas por unidad de tiempo por cliente
- **Sticky_Session**: Sesión persistente donde peticiones del mismo cliente van a la misma instancia backend
- **Cache_Aside**: Estrategia de cache donde se verifica cache primero y se carga de DB si no existe
- **Write_Through**: Estrategia de cache donde se actualiza DB y cache simultáneamente
- **Redis_Cluster**: Configuración de Redis con múltiples nodos master y réplicas para alta disponibilidad
- **Master_Node**: Nodo Redis que acepta escrituras y las replica a sus réplicas
- **Replica_Node**: Nodo Redis que mantiene copia de datos de un master para alta disponibilidad
- **Eviction_Policy**: Política que determina qué datos eliminar cuando Redis alcanza el límite de memoria
- **AOF**: Append Only File - archivo de persistencia de Redis que registra todas las operaciones de escritura

## Requisitos

### Requisito 1: Autenticación y Gestión de Usuarios

**User Story:** Como usuario, quiero registrarme y autenticarme en la plataforma, para que pueda acceder a funcionalidades personalizadas y realizar compras.

#### Acceptance Criteria

1. WHEN un usuario proporciona email, contraseña, nombre y apellido válidos, THE User_Service SHALL crear una cuenta de usuario con estado ACTIVE
2. WHEN un usuario intenta registrarse con un email ya existente, THE User_Service SHALL rechazar el registro y retornar un mensaje de error
3. WHEN un usuario proporciona credenciales válidas para login, THE User_Service SHALL generar un Token_JWT válido por 24 horas
4. WHEN un usuario proporciona credenciales inválidas, THE User_Service SHALL rechazar la autenticación y retornar un mensaje de error
5. WHEN un Token_JWT es generado, THE User_Service SHALL incluir userId, email y role en el token
6. WHEN un Token_JWT es validado, THE User_Service SHALL verificar la firma, expiración y existencia del usuario
7. WHILE un usuario está autenticado, THE Sistema SHALL actualizar el campo lastLoginAt con la fecha y hora actual
8. THE User_Service SHALL encriptar todas las contraseñas usando BCrypt con factor de trabajo 12

### Requisito 2: Gestión de Perfiles y Direcciones

**User Story:** Como cliente, quiero gestionar mi perfil y direcciones de envío, para que pueda mantener mi información actualizada y facilitar el proceso de compra.

#### Acceptance Criteria

1. WHEN un usuario autenticado solicita su perfil, THE User_Service SHALL retornar los datos del usuario sin incluir la contraseña
2. WHEN un usuario actualiza su perfil, THE User_Service SHALL validar los datos y actualizar la información
3. WHEN un usuario agrega una dirección de envío, THE User_Service SHALL validar que contenga nombre completo, dirección, ciudad, estado, código postal y país
4. WHEN un usuario marca una dirección como predeterminada, THE User_Service SHALL desmarcar cualquier otra dirección predeterminada existente
5. WHEN un usuario tiene múltiples direcciones, THE Sistema SHALL permitir seleccionar cualquiera durante el checkout
6. THE User_Service SHALL validar que el número de teléfono siga el formato internacional

### Requisito 3: Catálogo de Productos

**User Story:** Como vendedor, quiero gestionar productos en el catálogo, para que los clientes puedan descubrir y comprar mis artículos.

#### Acceptance Criteria

1. WHEN un vendedor crea un producto, THE Product_Service SHALL validar que incluya nombre, descripción, precio, categoría, marca y SKU
2. WHEN un producto es creado, THE Product_Service SHALL asignar estado ACTIVE por defecto
3. WHEN un producto es actualizado, THE Product_Service SHALL publicar un evento en Kafka para sincronizar con Search_Service
4. THE Product_Service SHALL validar que el precio sea mayor que cero
5. WHERE un producto tiene precio de descuento, THE Product_Service SHALL validar que sea menor que el precio regular
6. THE Product_Service SHALL validar que el SKU sea único en el sistema
7. WHEN un producto es consultado, THE Product_Service SHALL incluir imágenes, especificaciones, rating promedio y cantidad de reseñas
8. THE Product_Service SHALL organizar productos en categorías jerárquicas con subcategorías

### Requisito 4: Sistema de Reseñas y Calificaciones

**User Story:** Como cliente, quiero dejar reseñas y calificaciones de productos, para que pueda compartir mi experiencia y ayudar a otros compradores.

#### Acceptance Criteria

1. WHEN un cliente que ha comprado un producto envía una reseña, THE Product_Service SHALL validar que incluya rating entre 1 y 5, título y comentario
2. WHEN una reseña es creada, THE Product_Service SHALL recalcular el rating promedio del producto
3. WHEN una reseña es creada, THE Product_Service SHALL incrementar el contador de reseñas del producto
4. THE Product_Service SHALL permitir que los clientes incluyan imágenes en sus reseñas
5. WHEN se consultan reseñas de un producto, THE Product_Service SHALL retornarlas paginadas ordenadas por fecha de creación descendente

### Requisito 5: Búsqueda y Filtrado de Productos

**User Story:** Como cliente, quiero buscar y filtrar productos, para que pueda encontrar rápidamente lo que necesito.

#### Acceptance Criteria

1. WHEN un usuario ingresa una consulta de búsqueda, THE Search_Service SHALL buscar en nombre, descripción y marca del producto usando Elasticsearch
2. WHEN se realiza una búsqueda, THE Search_Service SHALL aplicar fuzzy matching para tolerar errores tipográficos
3. WHERE el usuario especifica filtros de categoría, THE Search_Service SHALL retornar solo productos de esas categorías
4. WHERE el usuario especifica rango de precios, THE Search_Service SHALL retornar solo productos dentro del rango
5. WHERE el usuario especifica rating mínimo, THE Search_Service SHALL retornar solo productos con rating igual o superior
6. THE Search_Service SHALL retornar solo productos con estado ACTIVE
7. WHEN se solicita autocompletado, THE Search_Service SHALL sugerir términos basados en el prefijo ingresado
8. THE Search_Service SHALL ordenar resultados por relevancia, precio o rating según lo especificado
9. WHEN un producto es creado o actualizado, THE Search_Service SHALL actualizar el índice de Elasticsearch consumiendo eventos de Kafka

### Requisito 6: Gestión del Carrito de Compras

**User Story:** Como cliente, quiero agregar productos a mi carrito, para que pueda revisar mi selección antes de comprar.

#### Acceptance Criteria

1. WHEN un cliente agrega un producto al carrito, THE Cart_Service SHALL validar que el producto exista y esté disponible
2. WHEN un cliente agrega un producto al carrito, THE Cart_Service SHALL incrementar la cantidad si el producto ya existe en el carrito
3. WHEN un cliente actualiza la cantidad de un item, THE Cart_Service SHALL recalcular el total del carrito
4. WHEN un cliente elimina un item del carrito, THE Cart_Service SHALL recalcular el total del carrito
5. THE Cart_Service SHALL calcular el subtotal como la suma de precio unitario por cantidad de todos los items
6. THE Cart_Service SHALL almacenar el carrito en Redis para acceso rápido
7. THE Cart_Service SHALL sincronizar el carrito con MySQL para persistencia
8. WHEN se aplica un cupón de descuento, THE Cart_Service SHALL validar el cupón y aplicar el descuento al total

### Requisito 7: Procesamiento de Pedidos con Saga Pattern

**User Story:** Como cliente, quiero crear pedidos de compra, para que pueda adquirir los productos seleccionados.

#### Acceptance Criteria

1. WHEN un cliente crea un pedido, THE Order_Service SHALL validar que el carrito no esté vacío
2. WHEN un pedido es creado, THE Order_Service SHALL generar un número de pedido único con formato ORD-YYYYMMDD-XXXXXX
3. WHEN un pedido es creado, THE Order_Service SHALL asignar estado PENDING inicialmente
4. WHEN un pedido es creado, THE Order_Service SHALL publicar evento OrderCreatedEvent en Kafka
5. WHEN Inventory_Service recibe OrderCreatedEvent, THE Inventory_Service SHALL verificar disponibilidad de todos los productos
6. IF el inventario es suficiente, THEN THE Inventory_Service SHALL reservar el inventario y publicar InventoryReservedEvent
7. IF el inventario es insuficiente, THEN THE Inventory_Service SHALL publicar InventoryUnavailableEvent
8. WHEN Payment_Service recibe InventoryReservedEvent, THE Payment_Service SHALL procesar el pago
9. IF el pago es exitoso, THEN THE Payment_Service SHALL publicar PaymentSuccessEvent
10. IF el pago falla, THEN THE Payment_Service SHALL publicar PaymentFailedEvent
11. WHEN Order_Service recibe PaymentSuccessEvent, THE Order_Service SHALL actualizar el estado del pedido a CONFIRMED
12. WHEN Order_Service recibe PaymentFailedEvent o InventoryUnavailableEvent, THE Order_Service SHALL actualizar el estado del pedido a CANCELLED
13. WHEN Order_Service recibe PaymentFailedEvent, THE Inventory_Service SHALL liberar el inventario reservado
14. THE Order_Service SHALL calcular el total como subtotal más impuestos más envío menos descuentos
15. THE Order_Service SHALL calcular impuestos basándose en la dirección de envío
16. THE Order_Service SHALL almacenar los precios de los productos al momento de la compra

### Requisito 8: Gestión de Estados de Pedidos

**User Story:** Como cliente, quiero rastrear el estado de mis pedidos, para que pueda saber cuándo recibiré mis productos.

#### Acceptance Criteria

1. WHEN un pedido es confirmado, THE Order_Service SHALL establecer el campo confirmedAt con la fecha y hora actual
2. WHEN un pedido cambia a estado SHIPPED, THE Order_Service SHALL establecer el campo shippedAt y asignar un número de rastreo
3. WHEN un pedido cambia a estado DELIVERED, THE Order_Service SHALL establecer el campo deliveredAt
4. THE Order_Service SHALL permitir transiciones de estado solo en el orden: PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
5. WHEN un cliente solicita cancelar un pedido, THE Order_Service SHALL permitir la cancelación solo si el estado es PENDING o CONFIRMED
6. IF un pedido tiene estado SHIPPED o DELIVERED, THEN THE Order_Service SHALL rechazar solicitudes de cancelación
7. WHEN un cliente consulta el rastreo de un pedido, THE Order_Service SHALL retornar el número de rastreo y estado actual

### Requisito 9: Procesamiento de Pagos

**User Story:** Como cliente, quiero pagar mis pedidos de forma segura, para que pueda completar mis compras.

#### Acceptance Criteria

1. WHEN un pago es procesado, THE Payment_Service SHALL comunicarse con la pasarela de pago externa (Stripe/PayPal)
2. WHEN un pago es exitoso, THE Payment_Service SHALL almacenar el ID de transacción y estado COMPLETED
3. WHEN un pago falla, THE Payment_Service SHALL almacenar el motivo del fallo y estado FAILED
4. THE Payment_Service SHALL encriptar los datos de tarjetas de crédito usando AES-256
5. WHEN un cliente agrega un método de pago, THE Payment_Service SHALL validar que pertenezca al usuario autenticado
6. WHEN se solicita un reembolso, THE Payment_Service SHALL procesar el reembolso a través de la pasarela de pago
7. THE Payment_Service SHALL registrar todos los intentos de pago para auditoría

### Requisito 10: Gestión de Inventario

**User Story:** Como sistema, quiero gestionar el inventario de productos, para que se mantenga la consistencia entre stock disponible y pedidos.

#### Acceptance Criteria

1. WHEN se reserva inventario, THE Inventory_Service SHALL decrementar el inventario disponible
2. WHEN se libera inventario, THE Inventory_Service SHALL incrementar el inventario disponible
3. THE Inventory_Service SHALL validar que el inventario disponible nunca sea negativo
4. WHEN el inventario de un producto llega a cero, THE Inventory_Service SHALL actualizar el estado del producto a OUT_OF_STOCK
5. WHEN se actualiza el inventario de un producto, THE Inventory_Service SHALL publicar evento en Kafka
6. FOR ALL productos, THE Sistema SHALL mantener la invariante: inventario_total = inventario_disponible + inventario_reservado

### Requisito 11: Recomendaciones Personalizadas

**User Story:** Como cliente, quiero recibir recomendaciones personalizadas, para que pueda descubrir productos relevantes para mí.

#### Acceptance Criteria

1. WHEN un cliente solicita recomendaciones, THE Recommendation_Service SHALL analizar su historial de compras y visualizaciones
2. WHEN se generan recomendaciones, THE Recommendation_Service SHALL usar collaborative filtering para encontrar usuarios similares
3. WHEN se generan recomendaciones, THE Recommendation_Service SHALL excluir productos ya comprados por el usuario
4. WHEN se solicitan productos similares, THE Recommendation_Service SHALL retornar productos de la misma categoría con características similares
5. WHEN se solicitan productos frecuentemente comprados juntos, THE Recommendation_Service SHALL analizar patrones de compra históricos
6. WHEN se solicitan productos en tendencia, THE Recommendation_Service SHALL retornar productos con mayor número de ventas recientes
7. THE Recommendation_Service SHALL consumir eventos de pedidos y visualizaciones desde Kafka para actualizar preferencias

### Requisito 12: Sistema de Notificaciones

**User Story:** Como cliente, quiero recibir notificaciones sobre mis pedidos, para que esté informado del estado de mis compras.

#### Acceptance Criteria

1. WHEN un pedido es confirmado, THE Notification_Service SHALL enviar un email de confirmación al cliente
2. WHEN un pedido cambia a estado SHIPPED, THE Notification_Service SHALL enviar un email con el número de rastreo
3. WHEN un pedido es entregado, THE Notification_Service SHALL enviar un email de confirmación de entrega
4. WHEN un pedido es cancelado, THE Notification_Service SHALL enviar un email explicando el motivo
5. THE Notification_Service SHALL consumir eventos desde Kafka para enviar notificaciones automáticas
6. THE Notification_Service SHALL usar SendGrid o AWS SES para envío de emails
7. WHERE un usuario ha configurado notificaciones SMS, THE Notification_Service SHALL enviar SMS usando Twilio

### Requisito 13: API Gateway y Enrutamiento

**User Story:** Como sistema, quiero un punto de entrada único para todas las peticiones, para que pueda aplicar políticas de seguridad y enrutamiento centralizadas.

#### Acceptance Criteria

1. WHEN una petición llega al API_Gateway, THE API_Gateway SHALL validar el Token_JWT si la ruta requiere autenticación
2. WHEN un Token_JWT es válido, THE API_Gateway SHALL extraer el userId y agregarlo a los headers de la petición
3. WHEN un Token_JWT es inválido o está expirado, THE API_Gateway SHALL retornar error 401 Unauthorized
4. THE API_Gateway SHALL enrutar peticiones a los microservicios correspondientes basándose en el path
5. THE API_Gateway SHALL aplicar rate limiting de 100 peticiones por segundo por usuario
6. WHEN se excede el rate limit, THE API_Gateway SHALL retornar error 429 Too Many Requests
7. THE API_Gateway SHALL registrar todas las peticiones para monitoreo y auditoría
8. THE API_Gateway SHALL implementar circuit breaker para manejar fallos de microservicios

### Requisito 14: Manejo de Errores y Resiliencia

**User Story:** Como sistema, quiero manejar errores de forma robusta, para que pueda recuperarme de fallos y mantener la disponibilidad.

#### Acceptance Criteria

1. WHEN un microservicio no responde dentro de 5 segundos, THE Sistema SHALL activar el circuit breaker
2. WHEN el circuit breaker está abierto, THE Sistema SHALL retornar una respuesta de fallback o error 503 Service Unavailable
3. WHEN un servicio se recupera, THE Sistema SHALL cerrar el circuit breaker automáticamente después de 30 segundos
4. WHEN ocurre un error de validación, THE Sistema SHALL retornar error 400 Bad Request con detalles específicos de cada campo inválido
5. WHEN ocurre un error interno, THE Sistema SHALL retornar error 500 Internal Server Error sin exponer detalles internos
6. THE Sistema SHALL registrar todos los errores con stack trace completo para debugging
7. WHEN falla la comunicación con Kafka, THE Sistema SHALL reintentar con backoff exponencial hasta 3 veces

### Requisito 15: Seguridad y Autorización

**User Story:** Como sistema, quiero proteger los datos y operaciones, para que solo usuarios autorizados puedan acceder a recursos específicos.

#### Acceptance Criteria

1. THE Sistema SHALL usar HTTPS/TLS para todas las comunicaciones
2. THE Sistema SHALL validar que las contraseñas tengan mínimo 8 caracteres, incluyan mayúsculas, minúsculas, números y caracteres especiales
3. THE Sistema SHALL encriptar todas las contraseñas usando BCrypt con factor de trabajo 12
4. THE Sistema SHALL encriptar datos de tarjetas de crédito usando AES-256
5. WHEN un usuario intenta acceder a un recurso, THE Sistema SHALL verificar que tenga los permisos necesarios
6. WHEN un usuario con rol CUSTOMER intenta acceder a rutas de administrador, THE Sistema SHALL retornar error 403 Forbidden
7. WHEN un usuario intenta acceder a un pedido, THE Sistema SHALL verificar que el pedido pertenezca al usuario o que el usuario sea administrador
8. THE Sistema SHALL sanitizar todas las entradas de usuario para prevenir ataques XSS
9. THE Sistema SHALL usar consultas parametrizadas para prevenir ataques SQL Injection
10. THE Sistema SHALL registrar todos los intentos de autenticación fallidos para detectar ataques

### Requisito 16: Auditoría y Logging

**User Story:** Como administrador, quiero tener registros de auditoría, para que pueda rastrear acciones importantes y detectar problemas.

#### Acceptance Criteria

1. WHEN un usuario inicia sesión exitosamente, THE Sistema SHALL registrar el evento con userId, timestamp e IP
2. WHEN un usuario falla al iniciar sesión, THE Sistema SHALL registrar el intento con email, timestamp e IP
3. WHEN se crea un pedido, THE Sistema SHALL registrar el evento con userId, orderId y monto total
4. WHEN se procesa un pago, THE Sistema SHALL registrar el evento con paymentId, orderId y resultado
5. WHEN se actualiza un producto, THE Sistema SHALL registrar el evento con productId, userId y cambios realizados
6. THE Sistema SHALL almacenar logs de auditoría en una tabla separada con retención de 1 año
7. THE Sistema SHALL incluir en cada log: userId, acción, tipo de entidad, entityId, detalles, IP y timestamp

### Requisito 17: Performance y Caching

**User Story:** Como sistema, quiero optimizar el rendimiento, para que pueda proporcionar respuestas rápidas a los usuarios.

#### Acceptance Criteria

1. WHEN se consulta un producto popular, THE Product_Service SHALL intentar obtenerlo de Redis cache antes de consultar MySQL
2. WHEN un producto es obtenido de MySQL, THE Product_Service SHALL almacenarlo en Redis cache con TTL de 1 hora
3. WHEN un producto es actualizado, THE Product_Service SHALL invalidar su entrada en cache
4. THE Cart_Service SHALL almacenar carritos en Redis para acceso rápido
5. THE Sistema SHALL usar connection pooling con mínimo 5 y máximo 20 conexiones para MySQL
6. THE Sistema SHALL usar índices en MySQL para columnas frecuentemente consultadas (email, status, category_id, price)
7. WHEN se consultan productos, THE Sistema SHALL usar paginación con máximo 100 items por página
8. THE Search_Service SHALL configurar Elasticsearch con 3 shards y 2 réplicas
9. THE Sistema SHALL comprimir mensajes de Kafka usando snappy compression

### Requisito 18: Internacionalización

**User Story:** Como usuario, quiero usar la plataforma en mi idioma preferido, para que pueda entender mejor el contenido.

#### Acceptance Criteria

1. THE Sistema SHALL soportar Español e Inglés como idiomas
2. WHEN un usuario selecciona un idioma, THE Frontend SHALL mostrar toda la interfaz en ese idioma
3. THE Frontend SHALL usar react-i18next para gestión de traducciones
4. THE Sistema SHALL almacenar textos traducibles en archivos JSON separados por idioma
5. WHEN se muestra un precio, THE Sistema SHALL formatearlo según la configuración regional del usuario
6. WHEN se muestra una fecha, THE Sistema SHALL formatearla según la configuración regional del usuario

### Requisito 19: Testing y Calidad

**User Story:** Como equipo de desarrollo, quiero tener cobertura de tests completa, para que pueda garantizar la calidad del código.

#### Acceptance Criteria

1. THE Sistema SHALL tener mínimo 80% de cobertura de código en tests unitarios
2. THE Sistema SHALL usar JUnit 5 y Mockito para tests unitarios en backend
3. THE Sistema SHALL usar Vitest y React Testing Library para tests unitarios en frontend
4. THE Sistema SHALL usar Testcontainers para tests de integración con MySQL, Redis y Kafka
5. THE Sistema SHALL usar Playwright para tests end-to-end
6. THE Sistema SHALL ejecutar tests automáticamente en cada commit mediante CI/CD
7. WHEN se ejecutan tests de carga, THE Sistema SHALL soportar 1000 búsquedas concurrentes con tiempo de respuesta menor a 500ms para el 95% de peticiones
8. WHEN se ejecutan tests de carga, THE Sistema SHALL soportar 100 creaciones de pedidos concurrentes con tiempo de respuesta menor a 2 segundos para el 95% de peticiones

### Requisito 20: Despliegue y Containerización

**User Story:** Como equipo de operaciones, quiero desplegar el sistema usando contenedores, para que pueda gestionar la infraestructura de forma eficiente.

#### Acceptance Criteria

1. THE Sistema SHALL proporcionar Dockerfiles para cada microservicio
2. THE Sistema SHALL proporcionar docker-compose.yml para desarrollo local
3. WHEN se construye una imagen Docker, THE Sistema SHALL usar multi-stage builds para optimizar el tamaño
4. THE Sistema SHALL usar imágenes base Alpine para minimizar el tamaño de contenedores
5. THE Sistema SHALL exponer métricas de salud en endpoint /actuator/health para cada microservicio
6. THE Sistema SHALL configurar health checks en Docker para reiniciar contenedores fallidos
7. THE Sistema SHALL usar variables de entorno para configuración específica del ambiente

### Requisito 21: Load Balancer - Distribución de Carga

**User Story:** Como sistema, quiero distribuir el tráfico entrante entre múltiples instancias de API Gateway, para que pueda garantizar alta disponibilidad y escalabilidad.

#### Acceptance Criteria

1. WHEN una petición HTTP llega al Load_Balancer, THE Load_Balancer SHALL distribuir la petición entre las instancias disponibles de API Gateway usando el algoritmo least_conn
2. WHEN el Load_Balancer usa algoritmo least_conn, THE Load_Balancer SHALL enviar cada petición a la instancia con menor número de conexiones activas
3. WHERE se configura algoritmo round_robin, THE Load_Balancer SHALL distribuir peticiones de forma circular entre todas las instancias
4. WHERE se configura algoritmo ip_hash, THE Load_Balancer SHALL enviar peticiones del mismo cliente a la misma instancia backend (sticky sessions)
5. WHERE se configura algoritmo weighted, THE Load_Balancer SHALL distribuir peticiones según pesos asignados a cada instancia
6. THE Load_Balancer SHALL mantener un pool de al menos 3 instancias de API Gateway en el upstream
7. WHEN se agregan o eliminan instancias del pool, THE Load_Balancer SHALL actualizar la configuración sin interrumpir el servicio

### Requisito 22: Load Balancer - SSL/TLS Termination

**User Story:** Como sistema, quiero terminar conexiones SSL/TLS en el Load Balancer, para que pueda centralizar la gestión de certificados y reducir carga en los backends.

#### Acceptance Criteria

1. THE Load_Balancer SHALL escuchar en puerto 443 para conexiones HTTPS
2. THE Load_Balancer SHALL escuchar en puerto 80 para conexiones HTTP
3. THE Load_Balancer SHALL usar certificados SSL/TLS válidos para conexiones HTTPS
4. THE Load_Balancer SHALL soportar protocolos TLSv1.2 y TLSv1.3 exclusivamente
5. THE Load_Balancer SHALL usar ciphers seguros (HIGH:!aNULL:!MD5)
6. THE Load_Balancer SHALL preferir ciphers del servidor sobre ciphers del cliente
7. WHEN una conexión SSL/TLS es establecida, THE Load_Balancer SHALL terminar la conexión y comunicarse con backends usando HTTP
8. THE Load_Balancer SHALL agregar headers X-Forwarded-Proto para indicar el protocolo original de la petición
9. THE Load_Balancer SHALL agregar headers X-Real-IP con la dirección IP del cliente original
10. THE Load_Balancer SHALL agregar headers X-Forwarded-For con la cadena de IPs de proxies

### Requisito 23: Load Balancer - Health Checks

**User Story:** Como sistema, quiero verificar la salud de las instancias backend, para que pueda enrutar tráfico solo a instancias saludables.

#### Acceptance Criteria

1. THE Load_Balancer SHALL realizar health checks a cada instancia de API Gateway cada 10 segundos
2. WHEN una instancia falla 3 health checks consecutivos, THE Load_Balancer SHALL marcar la instancia como no disponible
3. WHEN una instancia es marcada como no disponible, THE Load_Balancer SHALL dejar de enviar tráfico a esa instancia
4. WHEN una instancia no disponible pasa un health check, THE Load_Balancer SHALL esperar 30 segundos antes de reincorporarla al pool
5. THE Load_Balancer SHALL configurar timeout de 30 segundos para health checks fallidos
6. THE Load_Balancer SHALL exponer endpoint /health para verificar su propia salud
7. WHEN se consulta /health del Load Balancer, THE Load_Balancer SHALL retornar status 200 con mensaje "healthy"

### Requisito 24: Load Balancer - Rate Limiting

**User Story:** Como sistema, quiero limitar la tasa de peticiones por cliente, para que pueda proteger el sistema contra abuso y ataques DDoS.

#### Acceptance Criteria

1. THE Load_Balancer SHALL implementar rate limiting basado en dirección IP del cliente
2. THE Load_Balancer SHALL permitir máximo 100 peticiones por segundo por dirección IP
3. THE Load_Balancer SHALL permitir burst de hasta 200 peticiones por dirección IP
4. WHEN un cliente excede el rate limit, THE Load_Balancer SHALL retornar error 429 Too Many Requests
5. THE Load_Balancer SHALL usar zona de memoria compartida de 10MB para almacenar contadores de rate limiting
6. THE Load_Balancer SHALL aplicar rate limiting sin delay (nodelay) para peticiones dentro del burst
7. THE Load_Balancer SHALL resetear contadores de rate limiting cada segundo

### Requisito 25: Load Balancer - Failover y Retry

**User Story:** Como sistema, quiero reintentar peticiones fallidas en otras instancias, para que pueda proporcionar resiliencia ante fallos temporales.

#### Acceptance Criteria

1. WHEN una instancia backend retorna error 500, THE Load_Balancer SHALL reintentar la petición en otra instancia
2. WHEN una instancia backend retorna error 502, THE Load_Balancer SHALL reintentar la petición en otra instancia
3. WHEN una instancia backend retorna error 503, THE Load_Balancer SHALL reintentar la petición en otra instancia
4. WHEN una instancia backend no responde (timeout), THE Load_Balancer SHALL reintentar la petición en otra instancia
5. WHEN una instancia backend retorna header inválido, THE Load_Balancer SHALL reintentar la petición en otra instancia
6. THE Load_Balancer SHALL realizar máximo 2 intentos de retry por petición
7. THE Load_Balancer SHALL configurar timeout de conexión de 60 segundos
8. THE Load_Balancer SHALL configurar timeout de envío de 60 segundos
9. THE Load_Balancer SHALL configurar timeout de lectura de 60 segundos
10. WHEN todos los reintentos fallan, THE Load_Balancer SHALL retornar el último error al cliente

### Requisito 26: Load Balancer - Buffering y Performance

**User Story:** Como sistema, quiero optimizar el manejo de peticiones y respuestas, para que pueda mejorar el rendimiento y reducir latencia.

#### Acceptance Criteria

1. THE Load_Balancer SHALL habilitar buffering de respuestas de backends
2. THE Load_Balancer SHALL configurar buffer size de 4KB para respuestas
3. THE Load_Balancer SHALL configurar 8 buffers de 4KB cada uno
4. THE Load_Balancer SHALL usar HTTP/1.1 para comunicación con backends
5. THE Load_Balancer SHALL usar HTTP/2 para comunicación con clientes cuando esté disponible
6. THE Load_Balancer SHALL mantener header Host original en peticiones a backends
7. THE Load_Balancer SHALL habilitar compresión gzip para respuestas mayores a 1KB

### Requisito 27: Redis Cache Cluster - Arquitectura y Alta Disponibilidad

**User Story:** Como sistema, quiero un cache distribuido con alta disponibilidad, para que pueda mantener el servicio incluso si un nodo falla.

#### Acceptance Criteria

1. THE Redis_Cache SHALL configurarse como Redis Cluster con 3 nodos master
2. THE Redis_Cache SHALL configurar 1 réplica para cada nodo master (total 6 nodos)
3. THE Redis_Cache SHALL distribuir slots 0-5460 al Master 1
4. THE Redis_Cache SHALL distribuir slots 5461-10922 al Master 2
5. THE Redis_Cache SHALL distribuir slots 10923-16383 al Master 3
6. WHEN un nodo master falla, THE Redis_Cache SHALL promover automáticamente su réplica a master
7. THE Redis_Cache SHALL configurar timeout de nodo de 5000ms para detección de fallos
8. THE Redis_Cache SHALL configurar cluster-require-full-coverage como no para permitir operación con nodos caídos
9. THE Redis_Cache SHALL habilitar modo cluster con cluster-enabled yes
10. THE Redis_Cache SHALL mantener configuración de nodos en archivo nodes.conf

### Requisito 28: Redis Cache Cluster - Estrategias de Cache

**User Story:** Como sistema, quiero implementar diferentes estrategias de cache, para que pueda optimizar el rendimiento según el tipo de datos.

#### Acceptance Criteria

1. WHEN se consulta un producto, THE Sistema SHALL implementar cache-aside pattern: verificar cache, si no existe cargar de DB y almacenar en cache
2. WHEN se actualiza un producto, THE Sistema SHALL implementar write-through pattern: actualizar DB y cache simultáneamente
3. WHEN se crea un pedido, THE Sistema SHALL invalidar el cache del carrito del usuario
4. WHEN se actualiza inventario, THE Sistema SHALL invalidar el cache del producto afectado
5. WHEN se actualiza una categoría, THE Sistema SHALL invalidar el cache de todas las categorías
6. THE Sistema SHALL usar cache-aside como estrategia predeterminada para lecturas
7. THE Sistema SHALL usar event-based invalidation mediante Kafka para mantener consistencia

### Requisito 29: Redis Cache Cluster - TTL y Políticas de Expiración

**User Story:** Como sistema, quiero configurar tiempos de expiración apropiados para diferentes tipos de datos, para que pueda balancear frescura de datos con rendimiento.

#### Acceptance Criteria

1. WHEN se almacena un producto en cache, THE Redis_Cache SHALL configurar TTL de 1 hora
2. WHEN se almacenan resultados de búsqueda en cache, THE Redis_Cache SHALL configurar TTL de 30 minutos
3. WHEN se almacena una sesión de usuario en cache, THE Redis_Cache SHALL configurar TTL de 24 horas
4. WHEN se almacena un carrito en cache, THE Redis_Cache SHALL configurar TTL de 7 días
5. WHEN se almacena el árbol de categorías en cache, THE Redis_Cache SHALL configurar TTL de 24 horas
6. WHEN se almacenan recomendaciones en cache, THE Redis_Cache SHALL configurar TTL de 2 horas
7. WHEN un valor expira por TTL, THE Redis_Cache SHALL eliminarlo automáticamente
8. THE Redis_Cache SHALL usar key pattern `product:{productId}` para productos
9. THE Redis_Cache SHALL usar key pattern `search:{queryHash}` para búsquedas
10. THE Redis_Cache SHALL usar key pattern `session:{userId}` para sesiones
11. THE Redis_Cache SHALL usar key pattern `cart:{userId}` para carritos
12. THE Redis_Cache SHALL usar key pattern `categories:all` para categorías
13. THE Redis_Cache SHALL usar key pattern `recommendations:{userId}` para recomendaciones

### Requisito 30: Redis Cache Cluster - Gestión de Memoria

**User Story:** Como sistema, quiero gestionar eficientemente la memoria de Redis, para que pueda evitar problemas de memoria llena y mantener el rendimiento.

#### Acceptance Criteria

1. THE Redis_Cache SHALL configurar maxmemory de 2GB por nodo
2. THE Redis_Cache SHALL usar política de eviction allkeys-lru (Least Recently Used)
3. WHEN la memoria alcanza el límite maxmemory, THE Redis_Cache SHALL eliminar las keys menos recientemente usadas
4. THE Redis_Cache SHALL monitorear el uso de memoria y alertar cuando supere 80% de maxmemory
5. THE Redis_Cache SHALL monitorear la tasa de eviction y alertar cuando supere 1% de requests
6. THE Redis_Cache SHALL priorizar mantener sesiones de usuario sobre otros tipos de cache durante eviction
7. THE Redis_Cache SHALL configurar save 900 1 para persistir si hay al menos 1 cambio en 15 minutos
8. THE Redis_Cache SHALL configurar save 300 10 para persistir si hay al menos 10 cambios en 5 minutos
9. THE Redis_Cache SHALL configurar save 60 10000 para persistir si hay al menos 10000 cambios en 1 minuto

### Requisito 31: Redis Cache Cluster - Persistencia y Replicación

**User Story:** Como sistema, quiero persistir datos de cache y replicarlos, para que pueda recuperarme de fallos sin pérdida completa de datos.

#### Acceptance Criteria

1. THE Redis_Cache SHALL habilitar persistencia AOF (Append Only File) con appendonly yes
2. THE Redis_Cache SHALL configurar appendfsync everysec para sincronizar AOF cada segundo
3. THE Redis_Cache SHALL habilitar replicación diskless con repl-diskless-sync yes
4. THE Redis_Cache SHALL configurar delay de 5 segundos para replicación diskless
5. WHEN un master recibe una escritura, THE Redis_Cache SHALL replicar el cambio a su réplica de forma asíncrona
6. WHEN una réplica se reconecta, THE Redis_Cache SHALL sincronizar todos los cambios perdidos desde el master
7. THE Redis_Cache SHALL mantener archivos RDB y AOF para recuperación en caso de fallo completo del cluster

### Requisito 32: Redis Cache Cluster - Monitoreo y Métricas

**User Story:** Como equipo de operaciones, quiero monitorear el rendimiento de Redis, para que pueda detectar problemas y optimizar la configuración.

#### Acceptance Criteria

1. THE Sistema SHALL monitorear el cache hit rate de Redis con objetivo mayor a 80%
2. THE Sistema SHALL monitorear el uso de memoria de Redis con objetivo menor a 80% de maxmemory
3. THE Sistema SHALL monitorear la tasa de eviction de Redis con objetivo menor a 1% de requests
4. THE Sistema SHALL monitorear la latencia promedio de Redis con objetivo menor a 1ms
5. THE Sistema SHALL monitorear el número de conexiones activas a Redis
6. THE Sistema SHALL monitorear el número de comandos ejecutados por segundo en Redis
7. THE Sistema SHALL alertar cuando el cache hit rate caiga por debajo de 70%
8. THE Sistema SHALL alertar cuando el uso de memoria supere 85% de maxmemory
9. THE Sistema SHALL alertar cuando la latencia promedio supere 5ms
10. THE Sistema SHALL exponer métricas de Redis en formato Prometheus

### Requisito 33: Redis Cache Cluster - Invalidación de Cache

**User Story:** Como sistema, quiero invalidar cache de forma eficiente, para que pueda mantener consistencia entre cache y base de datos.

#### Acceptance Criteria

1. WHEN se actualiza un producto, THE Sistema SHALL invalidar la key `product:{productId}` en Redis
2. WHEN se actualiza un producto, THE Sistema SHALL invalidar todas las keys con pattern `search:*` que puedan contener ese producto
3. WHEN se actualiza una categoría, THE Sistema SHALL invalidar la key `categories:all` en Redis
4. WHEN se crea un pedido, THE Sistema SHALL invalidar la key `cart:{userId}` en Redis
5. WHEN se actualiza inventario, THE Sistema SHALL invalidar la key `product:{productId}` en Redis
6. THE Sistema SHALL proporcionar método invalidate(key) para invalidar una key específica
7. THE Sistema SHALL proporcionar método invalidatePattern(pattern) para invalidar múltiples keys por patrón
8. WHEN se invoca invalidatePattern, THE Sistema SHALL usar comando KEYS de Redis para encontrar keys coincidentes
9. WHEN se encuentran keys coincidentes, THE Sistema SHALL eliminarlas usando comando DEL
10. THE Sistema SHALL publicar eventos de invalidación en Kafka para sincronizar múltiples instancias de servicios

### Requisito 38: Panel de Administración

**User Story:** Como administrador, quiero un panel centralizado de gestión, para que pueda supervisar y controlar todos los aspectos de la plataforma.

#### Acceptance Criteria

1. WHEN un usuario con rol ADMIN accede al dashboard, THE Sistema SHALL mostrar métricas del negocio incluyendo ventas totales, pedidos del día, usuarios nuevos y productos más vendidos
2. WHEN un administrador solicita la lista de usuarios, THE Sistema SHALL retornar todos los usuarios paginados con sus datos y estado de cuenta
3. WHEN un administrador suspende una cuenta de usuario, THE Sistema SHALL cambiar el estado del usuario a SUSPENDED e impedir su acceso
4. WHEN un administrador reactiva una cuenta suspendida, THE Sistema SHALL cambiar el estado del usuario a ACTIVE y restaurar su acceso
5. WHEN un administrador elimina una cuenta de usuario, THE Sistema SHALL marcar la cuenta como DELETED y anonimizar sus datos personales
6. WHEN un administrador aprueba un producto de vendedor, THE Sistema SHALL cambiar el estado del producto a ACTIVE y publicarlo en el catálogo
7. WHEN un administrador rechaza un producto de vendedor, THE Sistema SHALL cambiar el estado del producto a REJECTED y notificar al vendedor con el motivo
8. WHEN un administrador edita cualquier producto, THE Product_Service SHALL aplicar los cambios y publicar evento de actualización en Kafka
9. WHEN un administrador crea una categoría, THE Product_Service SHALL validar que el nombre sea único y crear la categoría con su jerarquía
10. WHEN un administrador elimina una categoría, THE Product_Service SHALL verificar que no tenga productos activos antes de eliminarla
11. WHEN un administrador aprueba una reseña, THE Product_Service SHALL cambiar el estado de la reseña a APPROVED y hacerla visible
12. WHEN un administrador rechaza o elimina una reseña inapropiada, THE Product_Service SHALL cambiar el estado a REJECTED y notificar al autor
13. WHEN un administrador consulta todos los pedidos, THE Order_Service SHALL retornar todos los pedidos del sistema paginados con filtros por estado, fecha y usuario
14. WHEN un administrador crea un cupón, THE Sistema SHALL validar el código único y almacenar la configuración del cupón
15. WHEN un administrador solicita los logs de auditoría, THE Sistema SHALL retornar los registros de auditoría paginados con filtros por fecha, usuario y tipo de acción
16. WHEN un administrador solicita un reporte de ventas, THE Sistema SHALL generar y retornar el reporte en formato CSV o PDF según lo especificado

### Requisito 39: Portal de Vendedores (Seller Portal)

**User Story:** Como vendedor, quiero un portal dedicado para gestionar mi negocio, para que pueda administrar mis productos, inventario y pedidos de forma independiente.

#### Acceptance Criteria

1. WHEN un usuario solicita registro como vendedor, THE User_Service SHALL crear una solicitud con estado PENDING_APPROVAL y notificar a los administradores
2. WHEN un administrador aprueba la solicitud de vendedor, THE User_Service SHALL cambiar el rol del usuario a SELLER y notificarle por email
3. WHEN un vendedor accede a su dashboard, THE Sistema SHALL mostrar sus métricas incluyendo ventas totales, ingresos del período y productos activos
4. WHEN un vendedor crea un producto, THE Product_Service SHALL asociar el producto al vendedor y asignar estado PENDING_APPROVAL para revisión del administrador
5. WHEN un vendedor actualiza un producto propio, THE Product_Service SHALL aplicar los cambios y publicar evento de actualización en Kafka
6. WHEN un vendedor elimina un producto propio, THE Product_Service SHALL verificar que no tenga pedidos activos antes de eliminarlo
7. WHEN un vendedor actualiza el inventario de un producto propio, THE Inventory_Service SHALL aplicar los cambios y publicar evento de actualización
8. WHEN un vendedor consulta sus pedidos, THE Order_Service SHALL retornar solo los pedidos que contienen productos de ese vendedor
9. WHEN un vendedor consulta sus ingresos, THE Sistema SHALL retornar el historial de pagos y el balance pendiente de liquidación
10. WHEN un vendedor actualiza su perfil de tienda, THE User_Service SHALL validar y almacenar el nombre, descripción y logo de la tienda
11. IF un vendedor intenta gestionar productos de otro vendedor, THEN THE Sistema SHALL retornar error 403 Forbidden

### Requisito 40: Wishlist / Lista de Deseos

**User Story:** Como cliente, quiero gestionar listas de deseos, para que pueda guardar productos de interés y compartirlos con otros.

#### Acceptance Criteria

1. WHEN un cliente crea una lista de deseos, THE Sistema SHALL validar que incluya un nombre y crear la lista asociada al usuario
2. WHEN un cliente agrega un producto a una lista de deseos, THE Sistema SHALL verificar que el producto exista y no esté ya en la lista
3. WHEN un cliente elimina un producto de una lista de deseos, THE Sistema SHALL remover el producto y retornar la lista actualizada
4. WHEN un cliente marca una lista como pública, THE Sistema SHALL permitir que otros usuarios la consulten mediante un enlace compartible
5. WHEN un cliente marca una lista como privada, THE Sistema SHALL restringir el acceso solo al propietario de la lista
6. WHEN un cliente comparte una lista de deseos, THE Sistema SHALL generar un enlace único y retornarlo al cliente
7. WHEN un cliente mueve un producto de una lista de deseos al carrito, THE Cart_Service SHALL agregar el producto al carrito y opcionalmente removerlo de la lista
8. WHEN el precio de un producto en una lista de deseos disminuye, THE Notification_Service SHALL enviar una notificación al propietario de la lista
9. WHEN un producto en una lista de deseos vuelve a estar disponible tras estar agotado, THE Notification_Service SHALL enviar una notificación al propietario de la lista
10. IF un usuario no autenticado intenta acceder a una lista privada, THEN THE Sistema SHALL retornar error 403 Forbidden

### Requisito 41: Gestión de Cupones y Promociones

**User Story:** Como administrador, quiero gestionar cupones y promociones, para que pueda incentivar compras y fidelizar clientes.

#### Acceptance Criteria

1. WHEN un administrador crea un cupón, THE Sistema SHALL validar que el código sea único y almacenar el tipo de descuento, valor, fechas de vigencia y límites de uso
2. THE Sistema SHALL soportar tres tipos de descuento: porcentaje sobre el total, monto fijo y envío gratis
3. WHEN se configura un cupón con fecha de inicio y fin, THE Sistema SHALL activar el cupón en la fecha de inicio y desactivarlo automáticamente en la fecha de fin
4. WHEN se configura un límite de usos totales en un cupón, THE Sistema SHALL rechazar el cupón cuando se alcance dicho límite
5. WHEN se configura un límite de usos por usuario en un cupón, THE Sistema SHALL rechazar el cupón si el usuario ya lo ha usado el número máximo de veces
6. WHEN se configura un monto mínimo de compra en un cupón, THE Sistema SHALL rechazar el cupón si el subtotal del carrito es menor al monto mínimo
7. WHEN un cliente aplica un cupón en el carrito, THE Cart_Service SHALL validar el código, verificar todas las condiciones y aplicar el descuento correspondiente
8. IF un cupón es inválido, expirado o no cumple las condiciones, THEN THE Cart_Service SHALL retornar un mensaje de error descriptivo sin aplicar descuento
9. WHEN un cupón expira por fecha, THE Sistema SHALL desactivarlo automáticamente y marcarlo como EXPIRED

### Requisito 42: Devoluciones y Disputas

**User Story:** Como cliente, quiero solicitar devoluciones de productos, para que pueda obtener un reembolso cuando un producto no cumple mis expectativas.

#### Acceptance Criteria

1. WHEN un cliente solicita una devolución, THE Order_Service SHALL verificar que el pedido tenga estado DELIVERED y que hayan transcurrido menos de 30 días desde la entrega
2. WHEN se crea una solicitud de devolución, THE Order_Service SHALL asignar estado REQUESTED y notificar al vendedor y al administrador
3. THE Order_Service SHALL gestionar las transiciones de estado de devolución en el orden: REQUESTED → APPROVED o REJECTED → IN_TRANSIT → COMPLETED
4. WHEN un administrador o vendedor aprueba una solicitud de devolución, THE Order_Service SHALL cambiar el estado a APPROVED y generar una etiqueta de envío de retorno
5. WHEN un administrador o vendedor rechaza una solicitud de devolución, THE Order_Service SHALL cambiar el estado a REJECTED y notificar al cliente con el motivo
6. WHEN una devolución cambia a estado COMPLETED, THE Payment_Service SHALL procesar automáticamente el reembolso al método de pago original
7. WHEN el estado de una devolución cambia, THE Notification_Service SHALL enviar una notificación al cliente con el nuevo estado y detalles
8. WHEN un cliente abre una disputa sobre una devolución rechazada, THE Sistema SHALL crear un ticket de disputa y notificar al equipo de administración para revisión
9. IF un pedido no tiene estado DELIVERED o han pasado más de 30 días desde la entrega, THEN THE Order_Service SHALL rechazar la solicitud de devolución con mensaje de error

### Requisito 43: Verificación de Email y Autenticación de Dos Factores (2FA)

**User Story:** Como usuario, quiero verificar mi email y habilitar autenticación de dos factores, para que pueda proteger mi cuenta contra accesos no autorizados.

#### Acceptance Criteria

1. WHEN un usuario se registra, THE User_Service SHALL enviar un email de verificación con un token único válido por 24 horas
2. WHEN un usuario intenta realizar una compra sin haber verificado su email, THE Sistema SHALL rechazar la operación y solicitar la verificación
3. WHEN un usuario solicita reenvío del email de verificación con token expirado, THE User_Service SHALL generar un nuevo token y enviar un nuevo email
4. WHEN un usuario verifica su email con un token válido, THE User_Service SHALL marcar el email como verificado y activar todas las funcionalidades de compra
5. WHEN un usuario habilita 2FA, THE User_Service SHALL generar un secreto TOTP, crear un código QR compatible con Google Authenticator y retornarlo al usuario
6. WHEN un usuario completa la configuración de 2FA con un código TOTP válido, THE User_Service SHALL activar 2FA en la cuenta y generar códigos de respaldo de un solo uso
7. WHEN un usuario con 2FA activo inicia sesión con credenciales válidas, THE User_Service SHALL solicitar el código TOTP antes de emitir el Token_JWT
8. WHEN un usuario proporciona un código TOTP válido durante el login, THE User_Service SHALL emitir el Token_JWT y completar la autenticación
9. IF un usuario proporciona un código TOTP inválido, THEN THE User_Service SHALL rechazar la autenticación y retornar error 401
10. WHEN un usuario utiliza un código de respaldo para recuperar su cuenta, THE User_Service SHALL invalidar ese código de respaldo y permitir el acceso

### Requisito 44: Gestión de Imágenes y Almacenamiento de Archivos

**User Story:** Como vendedor, quiero subir imágenes de productos, para que los clientes puedan ver claramente lo que están comprando.

#### Acceptance Criteria

1. WHEN un vendedor sube una imagen de producto, THE Sistema SHALL validar que el archivo sea JPG, PNG o WebP con tamaño máximo de 5MB
2. WHEN una imagen válida es subida, THE Sistema SHALL almacenarla en el servicio de almacenamiento en la nube (S3 o compatible) y retornar la URL pública
3. WHEN una imagen es almacenada, THE Sistema SHALL generar automáticamente miniaturas en tres tamaños: thumbnail (150x150), medium (400x400) y large (800x800)
4. THE Sistema SHALL servir todas las imágenes a través de CDN para optimizar el tiempo de carga
5. WHEN un producto es eliminado, THE Sistema SHALL eliminar todas las imágenes asociadas del almacenamiento en la nube
6. WHEN un usuario actualiza su foto de perfil, THE Sistema SHALL validar el archivo, almacenarlo y actualizar la URL en el perfil del usuario
7. WHEN un vendedor actualiza el logo de su tienda, THE Sistema SHALL validar el archivo, almacenarlo y actualizar la URL en el perfil de la tienda
8. IF un vendedor intenta agregar más de 10 imágenes a un producto, THEN THE Sistema SHALL rechazar la operación y retornar un mensaje de error
9. IF el archivo subido no cumple con el formato o tamaño permitido, THEN THE Sistema SHALL rechazar la operación y retornar un mensaje de error descriptivo

### Requisito 45: SEO y Rendimiento del Frontend

**User Story:** Como plataforma, quiero optimizar el SEO y rendimiento del frontend, para que los productos sean descubiertos fácilmente y los usuarios tengan una experiencia fluida.

#### Acceptance Criteria

1. WHEN se accede a la página de un producto, THE Frontend SHALL generar una URL amigable con el formato /productos/{nombre-del-producto}-{id}
2. WHEN se accede a la página de una categoría, THE Frontend SHALL generar una URL amigable con el formato /categoria/{nombre-categoria}
3. WHEN se renderiza cualquier página, THE Frontend SHALL incluir meta tags dinámicos de title, description y Open Graph específicos para esa página
4. THE Sistema SHALL generar automáticamente un sitemap XML con todas las URLs de productos y categorías activos
5. WHEN se renderiza la página de un producto, THE Frontend SHALL incluir structured data en formato JSON-LD con precio, disponibilidad y rating del producto
6. WHEN se renderizan listas de productos, THE Frontend SHALL aplicar lazy loading a las imágenes para cargar solo las visibles en el viewport
7. WHEN se navega a una página de categoría o búsqueda, THE Frontend SHALL precargar los datos críticos usando React Query prefetching para reducir el tiempo de carga percibido

### Requisito 46: Gestión de Envíos e Integración con Carriers

**User Story:** Como cliente, quiero conocer las opciones y costos de envío, para que pueda elegir la opción que mejor se adapte a mis necesidades.

#### Acceptance Criteria

1. WHEN un cliente ingresa su dirección de envío durante el checkout, THE Sistema SHALL calcular el costo de envío basado en el peso total, dimensiones del paquete y destino
2. WHEN se calculan opciones de envío, THE Sistema SHALL mostrar al menos las opciones de envío estándar y express con su precio y tiempo estimado de entrega
3. THE Sistema SHALL integrarse con al menos un carrier (Estafeta, FedEx o DHL) para obtener tarifas y generar guías de envío
4. WHEN un pedido cambia a estado SHIPPED, THE Sistema SHALL generar un número de rastreo real a través del carrier integrado y almacenarlo en el pedido
5. WHEN el carrier envía una actualización de estado mediante webhook, THE Sistema SHALL procesar el evento y actualizar el estado de rastreo del pedido
6. WHEN se crea un pedido confirmado, THE Sistema SHALL calcular y almacenar la fecha estimada de entrega basada en el carrier y la opción de envío seleccionada
7. IF el servicio del carrier no está disponible, THEN THE Sistema SHALL retornar un mensaje de error y permitir al cliente reintentar la operación

### Requisito 47: Privacidad de Datos y Cumplimiento (LFPDPPP/GDPR)

**User Story:** Como usuario, quiero que mis datos personales sean gestionados de forma transparente y segura, para que pueda ejercer mis derechos de privacidad conforme a la ley.

#### Acceptance Criteria

1. WHEN un usuario se registra, THE Sistema SHALL mostrar el aviso de privacidad y solicitar consentimiento explícito antes de crear la cuenta
2. WHEN un usuario visita la plataforma por primera vez, THE Frontend SHALL mostrar un banner de gestión de consentimiento de cookies con opciones para cookies necesarias, analíticas y de marketing
3. WHEN un usuario solicita acceso a sus datos personales, THE Sistema SHALL generar y entregar un archivo descargable con todos los datos del usuario dentro de 72 horas
4. WHEN un usuario solicita la eliminación de su cuenta y datos, THE Sistema SHALL anonimizar o eliminar todos sus datos personales dentro de 30 días, conservando solo los registros requeridos por ley
5. WHEN un usuario solicita rectificación de sus datos personales, THE User_Service SHALL validar y actualizar los datos incorrectos
6. THE Sistema SHALL eliminar automáticamente los datos de usuarios que hayan estado inactivos por más de 3 años, previa notificación por email
7. WHEN un usuario otorga o revoca un consentimiento, THE Sistema SHALL registrar la acción con el tipo de consentimiento, decisión y timestamp para auditoría
8. WHEN se detecta una brecha de seguridad que afecte datos personales, THE Sistema SHALL notificar a los usuarios afectados dentro de 72 horas con información sobre los datos comprometidos y las medidas tomadas

## Requisitos No Funcionales

### Requisito 34: Disponibilidad

**User Story:** Como usuario, quiero que la plataforma esté disponible, para que pueda acceder cuando lo necesite.

#### Acceptance Criteria

1. THE Sistema SHALL tener una disponibilidad objetivo de 99.9% (máximo 8.76 horas de downtime por año)
2. THE Sistema SHALL implementar health checks en todos los microservicios
3. THE Sistema SHALL usar réplicas de base de datos para alta disponibilidad
4. THE Sistema SHALL distribuir carga entre múltiples instancias de cada microservicio

### Requisito 35: Escalabilidad

**User Story:** Como sistema, quiero escalar horizontalmente, para que pueda manejar incrementos en la carga de usuarios.

#### Acceptance Criteria

1. THE Sistema SHALL permitir agregar instancias adicionales de cualquier microservicio sin downtime
2. THE Sistema SHALL usar balanceo de carga para distribuir peticiones entre instancias
3. THE Sistema SHALL usar Kafka para desacoplar microservicios y permitir procesamiento asíncrono
4. THE Sistema SHALL usar Redis para compartir estado de sesión entre instancias

### Requisito 36: Mantenibilidad

**User Story:** Como equipo de desarrollo, quiero código mantenible, para que pueda realizar cambios de forma eficiente.

#### Acceptance Criteria

1. THE Sistema SHALL seguir principios SOLID en el diseño de clases
2. THE Sistema SHALL usar inyección de dependencias para facilitar testing
3. THE Sistema SHALL documentar todas las APIs usando OpenAPI/Swagger
4. THE Sistema SHALL usar nombres descriptivos para variables, métodos y clases
5. THE Sistema SHALL mantener métodos con máximo 50 líneas de código
6. THE Sistema SHALL mantener clases con máximo 500 líneas de código

### Requisito 37: Observabilidad

**User Story:** Como equipo de operaciones, quiero monitorear el sistema, para que pueda detectar y resolver problemas rápidamente.

#### Acceptance Criteria

1. THE Sistema SHALL exponer métricas de Prometheus en endpoint /actuator/prometheus
2. THE Sistema SHALL registrar logs estructurados en formato JSON
3. THE Sistema SHALL incluir trace IDs en logs para correlacionar peticiones entre microservicios
4. THE Sistema SHALL medir tiempos de respuesta de todas las operaciones
5. THE Sistema SHALL alertar cuando el tiempo de respuesta exceda umbrales definidos
6. THE Sistema SHALL medir tasa de errores y alertar cuando exceda 1%

### Requisito 48: Refresh Tokens y Gestión de Sesiones

**User Story:** Como usuario, quiero que mi sesión se renueve automáticamente mientras estoy activo, para que no tenga que volver a iniciar sesión constantemente.

#### Acceptance Criteria

1. WHEN un usuario se autentica exitosamente, THE User_Service SHALL emitir un access token JWT con expiración de 15 minutos y un refresh token opaco con expiración de 7 días
2. WHEN un cliente envía un refresh token válido al endpoint de renovación, THE User_Service SHALL emitir un nuevo access token JWT y un nuevo refresh token (rotación)
3. WHEN se emite un nuevo refresh token, THE User_Service SHALL invalidar el refresh token anterior
4. THE User_Service SHALL almacenar los refresh tokens en Redis con TTL de 7 días usando key pattern `refresh_token:{tokenHash}`
5. WHEN un refresh token es utilizado, THE User_Service SHALL verificar que exista en Redis y que pertenezca al usuario correcto
6. WHEN un usuario cierra sesión, THE User_Service SHALL invalidar el refresh token activo eliminándolo de Redis
7. WHEN se detecta reutilización de un refresh token ya invalidado, THE User_Service SHALL invalidar todos los refresh tokens del usuario (detección de robo de token)
8. WHEN un refresh token está expirado o no existe en Redis, THE User_Service SHALL retornar error 401 Unauthorized
9. THE User_Service SHALL permitir al usuario cerrar sesión en todos los dispositivos invalidando todos sus refresh tokens activos
10. THE Frontend SHALL almacenar el access token en memoria (no localStorage) y el refresh token en una cookie HttpOnly con flags Secure y SameSite=Strict

### Requisito 49: Métodos de Pago Locales (OXXO y SPEI)

**User Story:** Como cliente en México, quiero pagar con efectivo en OXXO o mediante transferencia SPEI, para que pueda completar mis compras sin necesidad de tarjeta de crédito.

#### Acceptance Criteria

1. THE Payment_Service SHALL soportar los métodos de pago: tarjeta de crédito/débito, OXXO y SPEI además de los métodos internacionales existentes
2. WHEN un cliente selecciona OXXO como método de pago, THE Payment_Service SHALL generar un voucher OXXO a través de Stripe con número de referencia y monto a pagar
3. WHEN se genera un voucher OXXO, THE Payment_Service SHALL establecer una fecha de expiración de 72 horas para el pago
4. WHEN se genera un voucher OXXO, THE Notification_Service SHALL enviar al cliente el voucher por email con instrucciones de pago
5. WHEN un cliente selecciona SPEI como método de pago, THE Payment_Service SHALL generar una CLABE interbancaria de 18 dígitos y los datos bancarios necesarios a través de Stripe
6. WHEN se genera una referencia SPEI, THE Payment_Service SHALL establecer una fecha de expiración de 24 horas para la transferencia
7. WHEN Stripe notifica el pago exitoso de OXXO o SPEI mediante webhook, THE Payment_Service SHALL procesar el evento y publicar PaymentSuccessEvent en Kafka
8. WHEN un voucher OXXO o referencia SPEI expira sin pago, THE Payment_Service SHALL publicar PaymentFailedEvent con motivo EXPIRED en Kafka
9. WHEN se muestra el checkout, THE Frontend SHALL mostrar las opciones OXXO y SPEI con sus instrucciones específicas y tiempos de acreditación
10. THE Payment_Service SHALL registrar el método de pago utilizado (CARD, OXXO, SPEI) en cada transacción para reportes

### Requisito 50: Rate Limiting Granular por Endpoint

**User Story:** Como sistema, quiero aplicar límites de tasa específicos por tipo de endpoint, para que pueda proteger operaciones sensibles contra abuso y ataques de fuerza bruta.

#### Acceptance Criteria

1. THE API_Gateway SHALL aplicar rate limiting de 5 intentos por minuto por IP al endpoint POST /api/users/login
2. WHEN se exceden los 5 intentos de login fallidos consecutivos desde la misma IP, THE API_Gateway SHALL bloquear esa IP por 15 minutos retornando error 429
3. THE API_Gateway SHALL aplicar rate limiting de 3 intentos por hora por IP al endpoint POST /api/users/register
4. THE API_Gateway SHALL aplicar rate limiting de 3 solicitudes por hora por usuario al endpoint POST /api/users/resend-verification
5. THE API_Gateway SHALL aplicar rate limiting de 10 intentos por minuto por IP al endpoint POST /api/users/{userId}/2fa/verify
6. THE API_Gateway SHALL aplicar rate limiting de 20 peticiones por minuto por usuario a los endpoints de creación de pedidos y pagos
7. WHEN se excede el rate limit de un endpoint específico, THE API_Gateway SHALL retornar error 429 con header Retry-After indicando los segundos hasta que se restablezca el límite
8. THE API_Gateway SHALL almacenar los contadores de rate limiting granular en Redis con TTL correspondiente a la ventana de tiempo del límite
9. THE API_Gateway SHALL incluir en las respuestas los headers X-RateLimit-Limit, X-RateLimit-Remaining y X-RateLimit-Reset para endpoints con rate limiting granular
10. THE Sistema SHALL registrar en el log de auditoría cada vez que se exceda el rate limit de un endpoint sensible, incluyendo IP, endpoint y timestamp
