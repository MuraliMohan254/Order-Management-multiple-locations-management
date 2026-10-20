#!/usr/bin/env bash
# ==============================================================================
# OM Order Management - AWS EC2 Fast Deployment Script (HTTPS / SSL Enabled)
# Supports: Amazon Linux 2023, Amazon Linux 2, Ubuntu 22.04/24.04, Debian
# ==============================================================================

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=====================================================${NC}"
echo -e "${BLUE}   OM Order Management - AWS EC2 Deployer (HTTPS)    ${NC}"
echo -e "${BLUE}=====================================================${NC}"

# 1. Check Root / Sudo
if [ "$EUID" -ne 0 ]; then
  SUDO='sudo'
else
  SUDO=''
fi

# 2. Check / Install Docker & OpenSSL
if ! command -v docker &> /dev/null || ! command -v openssl &> /dev/null; then
  echo -e "\n${YELLOW}[1/6] Installing required dependencies (Docker, OpenSSL)...${NC}"
  if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS=$ID
  fi

  if [[ "$OS" == "amzn" ]]; then
    $SUDO dnf update -y || $SUDO yum update -y
    $SUDO dnf install -y docker openssl || $SUDO amazon-linux-extras install docker -y
    $SUDO systemctl enable --now docker
    # Install docker compose plugin
    $SUDO mkdir -p /usr/local/lib/docker/cli-plugins
    $SUDO curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" -o /usr/local/lib/docker/cli-plugins/docker-compose
    $SUDO chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
  elif [[ "$OS" == "ubuntu" || "$OS" == "debian" ]]; then
    $SUDO apt-get update -y
    $SUDO apt-get install -y ca-certificates curl gnupg openssl
    $SUDO install -m 0755 -d /etc/apt/keyrings
    $SUDO curl -fsSL https://download.docker.com/linux/$OS/gpg -o /etc/apt/keyrings/docker.asc
    $SUDO chmod a+r /etc/apt/keyrings/docker.asc
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/$OS $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | $SUDO tee /etc/apt/sources.list.d/docker.list > /dev/null
    $SUDO apt-get update -y
    $SUDO apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    $SUDO systemctl enable --now docker
  else
    echo -e "${RED}Unsupported OS: $OS. Please install Docker and OpenSSL manually.${NC}"
    exit 1
  fi

  $SUDO usermod -aG docker $USER || true
  echo -e "${GREEN}Dependencies installed successfully.${NC}"
else
  echo -e "\n${GREEN}[1/6] Docker and OpenSSL are already installed.${NC}"
fi

# 3. Check Artifacts
echo -e "\n${YELLOW}[2/6] Checking pre-built artifacts...${NC}"
JAR_COUNT=$(find backend/order-management/target -maxdepth 1 -name "*.jar" 2>/dev/null | grep -v "original" | wc -l || echo 0)
if [ "$JAR_COUNT" -eq 0 ]; then
  echo -e "${RED}[ERROR] No .jar file found under backend/order-management/target/!${NC}"
  echo -e "Please build the backend first before running this script:"
  echo -e "  cd backend/order-management && ./mvnw clean package -DskipTests && cd ../.."
  exit 1
fi
echo -e "${GREEN}  ✓ Found Spring Boot JAR in target/${NC}"

if [ ! -d "frontend/order-management/dist/order-management" ]; then
  echo -e "${RED}[ERROR] No dist/ directory found under frontend/order-management/dist/order-management!${NC}"
  echo -e "Please build the Angular frontend first before running this script:"
  echo -e "  cd frontend/order-management && npm run build && cd ../.."
  exit 1
fi
echo -e "${GREEN}  ✓ Found Angular production dist directory${NC}"

# 4. Verify / Generate OpenSSL Certificates for HTTPS
echo -e "\n${YELLOW}[3/6] Setting up OpenSSL HTTPS Certificates...${NC}"
mkdir -p ssl
if [ ! -f "ssl/wildbar.crt" ] || [ ! -f "ssl/wildbar.key" ]; then
  echo -e "  Generating self-signed OpenSSL certificate for HTTPS..."
  if [ -f "ssl/generate-ssl.sh" ]; then
    bash ssl/generate-ssl.sh
  else
    openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
      -keyout ssl/wildbar.key \
      -out ssl/wildbar.crt \
      -subj "/C=IN/ST=Telangana/L=Hyderabad/O=WildBar/OU=IT/CN=wildbar.local"
  fi
  chmod 600 ssl/wildbar.key 2>/dev/null || true
  chmod 644 ssl/wildbar.crt 2>/dev/null || true
  echo -e "${GREEN}  ✓ Generated SSL certificate in ssl/wildbar.crt${NC}"
else
  echo -e "${GREEN}  ✓ Found existing SSL certificate & key in ssl/${NC}"
fi

# 5. Setup Environment File (.env)
echo -e "\n${YELLOW}[4/6] Setting up environment configuration...${NC}"
if [ ! -f .env ]; then
  if [ -f .env.example ]; then
    cp .env.example .env
  else
    cat <<EOF > .env
DB_NAME=order_management
DB_USER=postgres
DB_PASSWORD=$(openssl rand -base64 18 | tr -dc 'a-zA-Z0-9' | head -c 16)
JWT_SECRET=$(openssl rand -base64 32)
CORS_ALLOWED_ORIGINS=http://localhost,http://127.0.0.1,https://localhost,https://127.0.0.1
HIKARI_MAX_POOL_SIZE=50
HIKARI_MIN_IDLE=15
EOF
  fi
  echo -e "${GREEN}  ✓ Created .env file with generated credentials.${NC}"
else
  echo -e "${GREEN}  ✓ Using existing .env file.${NC}"
fi

# 6. Launch Docker Compose
echo -e "\n${YELLOW}[5/6] Building & Launching Docker containers...${NC}"
$SUDO docker compose down --remove-orphans || true
$SUDO docker compose up -d --build

# 7. Verify Health
echo -e "\n${YELLOW}[6/6] Verifying services health...${NC}"
echo -e "Waiting for PostgreSQL, Spring Boot, and Nginx SSL gateway to initialize..."
sleep 10

$SUDO docker compose ps

# Fetch Public IP
PUBLIC_IP=$(curl -s -m 3 http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || curl -s -m 3 https://ifconfig.me 2>/dev/null || echo "YOUR-EC2-PUBLIC-IP")

echo -e "\n${GREEN}=====================================================${NC}"
echo -e "${GREEN}  Deployment Complete! System is LIVE with HTTPS     ${NC}"
echo -e "${GREEN}=====================================================${NC}"
echo -e "Frontend (HTTPS):      ${BLUE}https://${PUBLIC_IP}${NC} (Port 443, SSL)"
echo -e "Frontend (HTTP):       ${BLUE}http://${PUBLIC_IP}${NC} (Redirects 301 to HTTPS)"
echo -e "API Endpoints:         ${BLUE}https://${PUBLIC_IP}/api/v1${NC}"
echo -e "Database:              ${BLUE}PostgreSQL 15 (127.0.0.1:5432 internally)${NC}"
echo -e "\n${YELLOW}Note: If using self-signed certificate, modern browsers will display a warning.${NC}"
echo -e "${YELLOW}Click 'Advanced' -> 'Proceed to ${PUBLIC_IP}' to open the app.${NC}"
echo -e "\nTo view live backend logs:"
echo -e "  ${YELLOW}sudo docker compose logs -f backend${NC}"
echo -e "To view all container statuses:"
echo -e "  ${YELLOW}sudo docker compose ps${NC}"
echo -e "=====================================================\n"
