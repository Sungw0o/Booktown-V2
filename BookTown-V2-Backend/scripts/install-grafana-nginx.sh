#!/usr/bin/env bash
set -euo pipefail

DOMAIN="${GRAFANA_DOMAIN:-grafana.booktown.shop}"
CONFIG_SOURCE="${GRAFANA_NGINX_CONFIG:-/opt/booktown/ops/nginx/grafana.booktown.shop.conf}"
CONFIG_TARGET="/etc/nginx/sites-available/${DOMAIN}"
CONFIG_LINK="/etc/nginx/sites-enabled/${DOMAIN}"

if [ ! -f "$CONFIG_SOURCE" ]; then
  echo "Nginx config not found: $CONFIG_SOURCE"
  exit 1
fi

if ! command -v nginx >/dev/null 2>&1; then
  echo "nginx is required"
  exit 1
fi

if [ ! -f "/etc/letsencrypt/live/${DOMAIN}/fullchain.pem" ]; then
  if [ -z "${CERTBOT_EMAIL:-}" ]; then
    echo "Certificate is missing. Set CERTBOT_EMAIL and rerun, or issue the certificate manually."
    echo "Example: CERTBOT_EMAIL=you@example.com $0"
    exit 1
  fi

  if ! command -v certbot >/dev/null 2>&1; then
    echo "certbot is required"
    exit 1
  fi

  sudo mkdir -p /var/www/html
  sudo tee "$CONFIG_TARGET" >/dev/null <<NGINX
server {
    listen 80;
    listen [::]:80;
    server_name ${DOMAIN};

    location /.well-known/acme-challenge/ {
        root /var/www/html;
    }

    location / {
        return 200 "Grafana certificate bootstrap";
        add_header Content-Type text/plain;
    }
}
NGINX
  sudo ln -sf "$CONFIG_TARGET" "$CONFIG_LINK"
  sudo nginx -t
  sudo systemctl reload nginx
  sudo certbot certonly --webroot -w /var/www/html -d "$DOMAIN" --non-interactive --agree-tos --email "$CERTBOT_EMAIL"
fi

sudo cp "$CONFIG_SOURCE" "$CONFIG_TARGET"
sudo ln -sf "$CONFIG_TARGET" "$CONFIG_LINK"
sudo nginx -t
sudo systemctl reload nginx

echo "Grafana Nginx route is installed for https://${DOMAIN}"
