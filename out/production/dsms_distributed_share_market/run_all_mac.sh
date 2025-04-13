#!/bin/bash

echo "[INFO] Compiling all Java files..."
find dsms -name "*.java" > sources.txt
javac @sources.txt
rm sources.txt

run_in_terminal() {
  osascript <<END
tell application "Terminal"
  do script "cd $(pwd); $1"
end tell
END
}

echo "[INFO] Starting Front-End..."
run_in_terminal "java dsms.components.FrontEnd"

echo "[INFO] Starting Sequencer..."
run_in_terminal "java dsms.components.Sequencer"

echo "[INFO] Starting Replica Managers..."
run_in_terminal "java dsms.components.ReplicaManager RM1"
run_in_terminal "java dsms.components.ReplicaManager RM2"
run_in_terminal "java dsms.components.ReplicaManager RM3"
run_in_terminal "java dsms.components.ReplicaManager RM4"

echo "[INFO] Starting Replicas..."
run_in_terminal "java dsms.replicas.ReplicaLauncher RM1"
run_in_terminal "java dsms.replicas.ReplicaLauncher RM2"
run_in_terminal "java dsms.replicas.ReplicaLauncher RM3"
run_in_terminal "java dsms.replicas.ReplicaLauncher RM4"

echo "[INFO] All components launched."

