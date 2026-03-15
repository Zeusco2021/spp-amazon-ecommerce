# =============================================================================
# Dockerfile - Nginx Load Balancer
# Amazon Clone E-Commerce
# =============================================================================
# Requisitos: 20.3, 20.4, 21, 22, 23, 24, 25, 26
# =============================================================================

# Imagen base Alpine para minimizar tamaño (Requisito 20.4)
FROM nginx:1.25-alpine

# Metadatos de la imagen
LABEL maintainer="Amazon Clone E-Commerce"
LABEL description="Nginx Load Balancer with SSL/TLS termination, rate limiting and health checks"
LABEL version="1.0.0"

# Instalar openssl para generación de certificados self-signed
RUN apk add --no-cache openssl

# Crear directorios necesarios
RUN mkdir -p /etc/nginx/ssl \
    && mkdir -p /var/log/nginx \
    && mkdir -p /var/cache/nginx

# Copiar configuración personalizada de Nginx
COPY nginx.conf /etc/nginx/nginx.conf

# Copiar script de generación de certificados SSL
COPY ssl/generate-certs.sh /docker-entrypoint.d/10-generate-certs.sh
RUN chmod +x /docker-entrypoint.d/10-generate-certs.sh

# Exponer puertos HTTP y HTTPS (Requisito 22.1, 22.2)
EXPOSE 80 443

# Health check del contenedor (Requisito 20.6)
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost/health || exit 1

# Comando por defecto: iniciar Nginx en foreground
CMD ["nginx", "-g", "daemon off;"]
