#!/bin/bash
# Redis Cluster Initialization Script
# Creates a 3 master + 3 replica cluster
# Slot distribution:
#   Master 1 (redis-node-1:7001): slots 0-5460
#   Master 2 (redis-node-2:7002): slots 5461-10922
#   Master 3 (redis-node-3:7003): slots 10923-16383
# Requirements: 27.1-27.6

set -e

echo "Waiting for Redis nodes to be ready..."
sleep 5

# Create cluster with 3 masters and 3 replicas (--cluster-replicas 1)
redis-cli --cluster create \
  redis-node-1:7001 \
  redis-node-2:7002 \
  redis-node-3:7003 \
  redis-node-4:7004 \
  redis-node-5:7005 \
  redis-node-6:7006 \
  --cluster-replicas 1 \
  --cluster-yes

echo "Redis Cluster initialized successfully!"
echo "Cluster info:"
redis-cli -h redis-node-1 -p 7001 cluster info
echo ""
echo "Cluster nodes:"
redis-cli -h redis-node-1 -p 7001 cluster nodes
