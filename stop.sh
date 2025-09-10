#!/bin/bash

# Havq Task Service - Stop Script

# Note: Not using 'set -e' to allow script to continue even if individual commands fail

SERVICE_NAME="havq-task-service"
PID_FILE="$SERVICE_NAME.pid"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🛑 Stopping Havq Task Service${NC}"

# Check if PID file exists
if [ ! -f "$PID_FILE" ]; then
    echo -e "${YELLOW}⚠️  No PID file found${NC}"
    
    # Try to find any running process
    RUNNING_PIDS=$(pgrep -f "havq-task-service" || true)
    if [ -n "$RUNNING_PIDS" ]; then
        echo -e "${YELLOW}   Found running processes: $RUNNING_PIDS${NC}"
        echo -e "${YELLOW}   Attempting to stop...${NC}"
        echo $RUNNING_PIDS | xargs kill -TERM 2>/dev/null || true
        sleep 2
        
        # Force kill if still running
        STILL_RUNNING=$(pgrep -f "havq-task-service" || true)
        if [ -n "$STILL_RUNNING" ]; then
            echo -e "${YELLOW}   Force stopping...${NC}"
            echo $STILL_RUNNING | xargs kill -KILL 2>/dev/null || true
        fi
        
        echo -e "${GREEN}✅ Service stopped${NC}"
    else
        echo -e "${GREEN}✅ Service is not running${NC}"
    fi
    exit 0
fi

# Read PID from file
PID=$(cat "$PID_FILE")

# Check if process is running
if ! ps -p $PID > /dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  Process with PID $PID is not running${NC}"
    rm -f "$PID_FILE"
    echo -e "${GREEN}✅ Cleaned up stale PID file${NC}"
    exit 0
fi

echo -e "${BLUE}📋 Found service running with PID: $PID${NC}"

# Graceful shutdown
echo -e "${BLUE}🔄 Attempting graceful shutdown...${NC}"
kill -TERM $PID

# Wait up to 10 seconds for graceful shutdown
COUNTER=0
while [ $COUNTER -lt 10 ]; do
    if ! ps -p $PID > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Service stopped gracefully${NC}"
        rm -f "$PID_FILE"
        exit 0
    fi
    sleep 1
    COUNTER=$((COUNTER + 1))
done

# Force kill if still running
echo -e "${YELLOW}⚠️  Graceful shutdown timed out, force stopping...${NC}"
kill -KILL $PID 2>/dev/null || true

# Wait a moment and verify
sleep 1
if ! ps -p $PID > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Service stopped (force killed)${NC}"
    rm -f "$PID_FILE"
else
    echo -e "${RED}❌ Failed to stop service${NC}"
    exit 1
fi