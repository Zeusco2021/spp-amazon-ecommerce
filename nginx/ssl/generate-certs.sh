#!/bin/sh
# =============================================================================
# generate-certs.sh - Script de generación de certificados SSL self-signed
# Amazon Clone E-Commerce - Nginx Load Balancer
# =============================================================================
# Este script genera certificados SSL/TLS self-signed para desarrollo y testing.
# En producción, reemplazar con certificados emitidos por una CA de confianza
# (Let's Encrypt, DigiCert, etc.)
# =============================================================================

SSL_DIR="/etc/nginx/ssl"
CERT_FILE="${SSL_DIR}/cert.pem"
KEY_FILE="${SSL_DIR}/key.pem"

# Solo generar si no existen los certificados
if [ ! -f "${CERT_FILE}" ] || [ ! -f "${KEY_FILE}" ]; then
    echo "[INFO] Generating self-signed SSL certificate..."

    openssl req -x509 \
        -nodes \
        -days 365 \
        -newkey rsa:2048 \
        -keyout "${KEY_FILE}" \
        -out "${CERT_FILE}" \
        -subj "/C=MX/ST=CDMX/L=Mexico City/O=Amazon Clone/OU=Engineering/CN=localhost" \
        -addext "subjectAltName=DNS:localhost,DNS:*.localhost,IP:127.0.0.1"

    chmod 600 "${KEY_FILE}"
    chmod 644 "${CERT_FILE}"

    echo "[INFO] SSL certificate generated successfully."
    echo "[INFO] Certificate: ${CERT_FILE}"
    echo "[INFO] Private key: ${KEY_FILE}"
    echo "[WARN] This is a self-signed certificate. Use a CA-signed certificate in production."
else
    echo "[INFO] SSL certificates already exist, skipping generation."
fi
