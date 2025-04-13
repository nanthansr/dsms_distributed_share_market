#!/bin/zsh

echo "Stopping all running DSMS Java processes..."

# Kill all matching Java processes (case-insensitive)
pkill -f 'java dsms.replicas.ReplicaLauncher'
pkill -f 'java dsms.components.Sequencer'
pkill -f 'java dsms.components.ReplicaManager'
pkill -f 'java dsms.components.FrontEnd'
pkill -f 'java test.Client'

echo "All DSMS components terminated."

