#!/bin/bash

# Rhythmai Task Service - Start Script
# Usage: ./start.sh [profile]
# Profiles: local (default), staging, prod

set -e

PROFILE=${1:-local}
SERVICE_NAME="rhythmai-task-service"
LOG_DIR="logs"
PID_FILE="$SERVICE_NAME.pid"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 Starting Rhythmai Task Service${NC}"
echo -e "${BLUE}Profile: ${PROFILE}${NC}"
echo -e "${BLUE}Port: 5002${NC}"

# Check if service is already running
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p $PID > /dev/null 2>&1; then
        echo -e "${YELLOW}⚠️  Service is already running (PID: $PID)${NC}"
        echo -e "${YELLOW}   Use ./stop.sh to stop the service first${NC}"
        exit 1
    else
        echo -e "${YELLOW}⚠️  Stale PID file found, removing...${NC}"
        rm -f "$PID_FILE"
    fi
fi

# Create logs directory
mkdir -p "$LOG_DIR"

# Set Java home to ensure Java 17
if [ -d "/usr/local/opt/openjdk@17" ]; then
    export JAVA_HOME="/usr/local/opt/openjdk@17"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Verify Java version
JAVA_VERSION=$(java -version 2>&1 | head -1)
if [[ "$JAVA_VERSION" != *"17."* ]]; then
    echo -e "${RED}❌ Java 17 is required. Current: $JAVA_VERSION${NC}"
    echo -e "${RED}   Please install Java 17 or check your JAVA_HOME${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Java 17 detected${NC}"

# Set Spring profile
export SPRING_PROFILES_ACTIVE="$PROFILE"

# Check if MongoDB is running (optional check)
if command -v mongo &> /dev/null || command -v mongosh &> /dev/null; then
    echo -e "${GREEN}✓ MongoDB client detected${NC}"
fi

echo -e "${BLUE}📦 Building application...${NC}"
# Skip tests for faster startup (known issue - see CLAUDE.md)
./gradlew build -x test -q

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Build successful${NC}"
else
    echo -e "${RED}❌ Build failed${NC}"
    exit 1
fi

echo -e "${BLUE}🔄 Starting service in background...${NC}"

# Start the service in background
nohup ./gradlew bootRun --args="--spring.profiles.active=$PROFILE" > "$LOG_DIR/service.log" 2>&1 &
SERVICE_PID=$!

# Save PID
echo $SERVICE_PID > "$PID_FILE"

# Wait a moment and check if service started successfully
sleep 3

if ps -p $SERVICE_PID > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Service started successfully!${NC}"
    echo -e "${GREEN}   PID: $SERVICE_PID${NC}"
    echo -e "${GREEN}   Profile: $PROFILE${NC}"
    echo -e "${GREEN}   Port: 5002${NC}"
    echo -e "${GREEN}   Logs: tail -f $LOG_DIR/service.log${NC}"
    echo ""
    echo -e "${BLUE}🔗 Service URLs:${NC}"
    echo -e "${BLUE}   Health Check: http://localhost:5002/actuator/health${NC}"
    echo -e "${BLUE}   API Base: http://localhost:5002/api/tasks${NC}"
    echo ""
    echo -e "${YELLOW}💡 Tip: Use ./status.sh to check service status${NC}"
    echo -e "${YELLOW}💡 Tip: Use ./stop.sh to stop the service${NC}"
else
    echo -e "${RED}❌ Service failed to start${NC}"
    echo -e "${RED}   Check logs: cat $LOG_DIR/service.log${NC}"
    rm -f "$PID_FILE"
    exit 1
fi