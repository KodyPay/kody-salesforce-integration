#!/bin/bash
#
# Enhanced run script with environment variable support
# Usage: 
#   ./run.sh <class_name> [environment]                    # Uses YAML config or .env.{environment} if exists
#   ./run.sh <class_name> --env-file <env_file>           # Uses specific env file
#   ./run.sh <class_name>                                  # Uses environment variables already set
#
# Examples:
#   ./run.sh kody.integration.KodyPaymentService sandbox   # Uses .env.sandbox if exists, else YAML
#   ./run.sh kody.integration.KodyPaymentService --env-file .env.production
#   source .env.sandbox && ./run.sh kody.integration.KodyPaymentService
#

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to load environment file
load_env_file() {
    local env_file=$1
    if [ -f "$env_file" ]; then
        echo -e "${GREEN}✅ Loading environment from: $env_file${NC}"
        # Export variables from env file, ignoring comments and empty lines
        set -a  # automatically export all variables
        source "$env_file"
        set +a
        return 0
    else
        echo -e "${YELLOW}⚠️  Environment file not found: $env_file (will use YAML config)${NC}"
        return 1
    fi
}

# Parse arguments
APPLICATION=$1
if [ -z "$APPLICATION" ]; then
    echo -e "${RED}❌ Error: Application class name is required${NC}"
    echo ""
    echo "Usage: ./run.sh <class_name> [options]"
    echo ""
    echo "Options:"
    echo "  [environment]           Use .env.{environment} file or YAML config"
    echo "  --env-file <file>      Use specific environment file"
    echo ""
    echo "Examples:"
    echo "  ./run.sh kody.integration.KodyPaymentService sandbox"
    echo "  ./run.sh kody.integration.KodyPaymentService --env-file .env.production"
    echo "  ./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.InitiatePayment '{...}' 'api-key'"
    echo ""
    echo "Available classes:"
    echo "  - kody.integration.KodyPaymentService"
    echo "  - kody.integration.KodyPaymentQuickTest"
    echo "  - samples.KodyPaymentPublisher"
    exit 1
fi

shift  # Remove class name from arguments

# Check for environment configuration
ENV_LOADED=false
ENVIRONMENT=""

# Check for --env-file flag
if [ "$1" = "--env-file" ] || [ "$1" = "-e" ]; then
    if [ -z "$2" ]; then
        echo -e "${RED}❌ Error: --env-file requires a file path${NC}"
        exit 1
    fi
    load_env_file "$2"
    ENV_LOADED=$?
    shift 2
# Check if first argument is an environment name (not a flag)
elif [ -n "$1" ] && [[ "$1" != -* ]] && [[ "$1" != "{" ]]; then
    ENVIRONMENT=$1
    # Try to load corresponding env file if it exists
    ENV_FILE=".env.${ENVIRONMENT}"
    if [ -f "$ENV_FILE" ]; then
        echo -e "${BLUE}🔍 Found environment file: $ENV_FILE${NC}"
        load_env_file "$ENV_FILE"
        ENV_LOADED=0
    else
        echo -e "${YELLOW}📄 No env file found, will use YAML config: arguments-${ENVIRONMENT}.yaml${NC}"
    fi
    # Don't shift here - we need to pass environment to Java app for YAML loading
fi

# Build the project if JAR doesn't exist
if [ ! -f target/pubsub-java-1.0-SNAPSHOT.jar ]; then
    echo -e "${YELLOW}🔨 JAR file not found. Building project...${NC}"
    mvn clean install -DskipTests
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ Build failed!${NC}"
        exit 1
    fi
fi

# Display configuration status
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}🚀 Starting Application${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "  Class: ${GREEN}$APPLICATION${NC}"

if [ "$ENV_LOADED" = "0" ]; then
    echo -e "  Config: ${GREEN}Environment Variables${NC}"
    [ -n "$PUBSUB_HOST" ] && echo -e "  Pub/Sub: ${PUBSUB_HOST}"
    [ -n "$LOGIN_URL" ] && echo -e "  Salesforce: $(echo $LOGIN_URL | sed 's/https:\/\///' | cut -d'.' -f1)"
    [ -n "$TOPIC" ] && echo -e "  Topic: $TOPIC"
    [ -n "$KODY_HOSTNAME" ] && echo -e "  Kody: $KODY_HOSTNAME"
elif [ -n "$ENVIRONMENT" ]; then
    echo -e "  Config: ${YELLOW}YAML (arguments-${ENVIRONMENT}.yaml)${NC}"
    echo -e "  Environment: $ENVIRONMENT"
else
    echo -e "  Config: ${BLUE}System Environment Variables${NC}"
fi
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Run the Java application
java -cp target/pubsub-java-1.0-SNAPSHOT.jar "$APPLICATION" "$@"