#!/bin/bash
# =============================================================================
# generate-keys.sh — RSA key pair generation for JWT signing
#
# Run this script ONCE per environment to generate the RSA-2048 key pair used
# by the auth-service (for signing) and the api-gateway (for verification).
#
# OUTPUT:
#   keys/private.pem — RSA private key (auth-service only — keep secret)
#   keys/public.pem  — RSA public key  (api-gateway — safe to distribute within infra)
#
# PRODUCTION: Do NOT run this manually. Keys are provisioned by Terraform and
# stored in AWS Secrets Manager. This script is for local development only.
#
# TASK: FEAT-003
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEYS_DIR="${SCRIPT_DIR}/keys"

mkdir -p "${KEYS_DIR}"

if [[ -f "${KEYS_DIR}/private.pem" ]]; then
    echo "WARNING: ${KEYS_DIR}/private.pem already exists."
    read -r -p "Overwrite? This will invalidate all existing tokens. [y/N]: " confirm
    if [[ "${confirm}" != "y" && "${confirm}" != "Y" ]]; then
        echo "Aborted."
        exit 0
    fi
fi

echo "Generating RSA-2048 private key..."
openssl genrsa -out "${KEYS_DIR}/private.pem" 2048

echo "Extracting RSA public key..."
openssl rsa -in "${KEYS_DIR}/private.pem" -pubout -out "${KEYS_DIR}/public.pem"

# Restrict private key permissions
chmod 600 "${KEYS_DIR}/private.pem"
chmod 644 "${KEYS_DIR}/public.pem"

echo ""
echo "Keys generated successfully:"
echo "  Private key: ${KEYS_DIR}/private.pem  (KEEP SECRET — auth-service only)"
echo "  Public key:  ${KEYS_DIR}/public.pem   (api-gateway, safe within infra)"
echo ""
echo "IMPORTANT: Add keys/ to .gitignore. Never commit private keys to version control."
