#!/bin/bash

# Havq Task Service - Status Script

set -e

SERVICE_NAME="havq-task-service"
PID_FILE="$SERVICE_NAME.pid"
LOG_DIR="logs"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${BLUE}📊 Havq Task Service Status${NC}"
echo -e "${BLUE}================================${NC}"

# Check PID file
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    echo -e "${CYAN}PID File: ${NC}Found ($PID_FILE)"
    echo -e "${CYAN}Process ID: ${NC}$PID"
    
    # Check if process is actually running
    if ps -p $PID > /dev/null 2>&1; then
        echo -e "${GREEN}Service Status: ${NC}✅ RUNNING"
        
        # Get process info
        PROCESS_INFO=$(ps -p $PID -o pid,ppid,comm,etime,pcpu,pmem 2>/dev/null | tail -n +2 || echo "Process info unavailable")
        echo -e "${CYAN}Process Info: ${NC}$PROCESS_INFO"
        
        # Get Java version being used
        JAVA_PROCESSES=$(pgrep -f java || true)
        if [ -n "$JAVA_PROCESSES" ]; then
            JAVA_VERSION=$(ps -p $PID -o args 2>/dev/null | tail -n +2 | grep -o 'java.*' | head -1 || echo "Unknown")
            echo -e "${CYAN}Java Process: ${NC}$JAVA_VERSION"
        fi
        
        # Check service health
        echo ""
        echo -e "${BLUE}🏥 Health Check${NC}"
        HEALTH_RESPONSE=$(curl -s http://localhost:5002/actuator/health 2>/dev/null || echo "failed")
        
        if [[ "$HEALTH_RESPONSE" == *"UP"* ]]; then
            echo -e "${GREEN}Health Status: ${NC}✅ HEALTHY"
            
            # Parse and display components
            if command -v jq &> /dev/null; then
                MONGO_STATUS=$(echo "$HEALTH_RESPONSE" | jq -r '.components.mongo.status' 2>/dev/null || echo "unknown")
                echo -e "${CYAN}MongoDB: ${NC}$MONGO_STATUS"
            fi
        elif [[ "$HEALTH_RESPONSE" == "failed" ]]; then
            echo -e "${RED}Health Status: ${NC}❌ UNREACHABLE (Service may be starting up)"
        else
            echo -e "${YELLOW}Health Status: ${NC}⚠️ DEGRADED"
        fi
        
        # Check active profile
        echo ""
        echo -e "${BLUE}⚙️ Configuration${NC}"
        
        # Check which profile is active by looking at logs or environment
        if [ -f "$LOG_DIR/service.log" ]; then
            ACTIVE_PROFILE=$(grep -o "The following .* profile.*active:" "$LOG_DIR/service.log" 2>/dev/null | tail -1 || echo "")
            if [ -n "$ACTIVE_PROFILE" ]; then
                echo -e "${CYAN}Active Profile: ${NC}$ACTIVE_PROFILE"
            fi
        fi
        
        echo -e "${CYAN}Service Port: ${NC}5002"
        echo -e "${CYAN}API Base URL: ${NC}http://localhost:5002/api/tasks"
        
    else
        echo -e "${RED}Service Status: ${NC}❌ NOT RUNNING (Stale PID file)"
        echo -e "${YELLOW}Recommendation: ${NC}Run ./stop.sh to clean up, then ./start.sh"
    fi
else
    echo -e "${CYAN}PID File: ${NC}Not found"
    echo -e "${RED}Service Status: ${NC}❌ NOT RUNNING"
    
    # Check if any related processes are running
    RUNNING_PROCESSES=$(pgrep -f "havq-task-service" || true)
    if [ -n "$RUNNING_PROCESSES" ]; then
        echo -e "${YELLOW}Warning: ${NC}Found orphaned processes: $RUNNING_PROCESSES"
        echo -e "${YELLOW}Recommendation: ${NC}Run ./stop.sh to clean up"
    fi
fi

# Log file information
echo ""
echo -e "${BLUE}📁 Log Files${NC}"
if [ -d "$LOG_DIR" ]; then
    echo -e "${CYAN}Log Directory: ${NC}$LOG_DIR/"
    
    if [ -f "$LOG_DIR/service.log" ]; then
        LOG_SIZE=$(ls -lh "$LOG_DIR/service.log" | awk '{print $5}')
        LOG_MODIFIED=$(ls -l "$LOG_DIR/service.log" | awk '{print $6, $7, $8}')
        echo -e "${CYAN}Service Log: ${NC}$LOG_DIR/service.log ($LOG_SIZE, modified: $LOG_MODIFIED)"
        
        # Show last few log lines if file is not too large
        LOG_SIZE_BYTES=$(stat -f%z "$LOG_DIR/service.log" 2>/dev/null || stat -c%s "$LOG_DIR/service.log" 2>/dev/null || echo 0)
        if [ "$LOG_SIZE_BYTES" -lt 10485760 ]; then  # Less than 10MB
            echo ""
            echo -e "${BLUE}📝 Recent Log Entries (last 5 lines)${NC}"
            echo -e "${PURPLE}$(tail -5 "$LOG_DIR/service.log")${NC}"
        fi
    else
        echo -e "${CYAN}Service Log: ${NC}Not found"
    fi
else
    echo -e "${CYAN}Log Directory: ${NC}Not found"
fi

# Network port check
echo ""
echo -e "${BLUE}🌐 Network Status${NC}"
PORT_CHECK=$(lsof -i :5002 2>/dev/null || echo "")
if [ -n "$PORT_CHECK" ]; then
    echo -e "${GREEN}Port 5002: ${NC}✅ IN USE"
    echo -e "${CYAN}Port Details: ${NC}"
    echo "$PORT_CHECK"
else
    echo -e "${RED}Port 5002: ${NC}❌ NOT IN USE"
fi

# Quick actions
echo ""
echo -e "${BLUE}🛠️ Quick Actions${NC}"
echo -e "${CYAN}Start Service: ${NC}./start.sh [local|staging|prod]"
echo -e "${CYAN}Stop Service: ${NC}./stop.sh"
echo -e "${CYAN}View Logs: ${NC}tail -f $LOG_DIR/service.log"
echo -e "${CYAN}Health Check: ${NC}curl http://localhost:5002/actuator/health"

echo ""