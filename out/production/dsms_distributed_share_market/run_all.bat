@echo off
echo [INFO] Compiling all Java files...
dir /S /B dsms\*.java > sources.txt
javac @sources.txt
del sources.txt

echo [INFO] Starting Front-End...
start cmd /k "cd /d %cd% && java dsms.components.FrontEnd"

echo [INFO] Starting Sequencer...
start cmd /k "cd /d %cd% && java dsms.components.Sequencer"

echo [INFO] Starting Replica Managers...
start cmd /k "cd /d %cd% && java dsms.components.ReplicaManager RM1"
start cmd /k "cd /d %cd% && java dsms.components.ReplicaManager RM2"
start cmd /k "cd /d %cd% && java dsms.components.ReplicaManager RM3"
start cmd /k "cd /d %cd% && java dsms.components.ReplicaManager RM4"

echo [INFO] Starting Replicas...
start cmd /k "cd /d %cd% && java dsms.replicas.ReplicaLauncher RM1"
start cmd /k "cd /d %cd% && java dsms.replicas.ReplicaLauncher RM2"
start cmd /k "cd /d %cd% && java dsms.replicas.ReplicaLauncher RM3"
start cmd /k "cd /d %cd% && java dsms.replicas.ReplicaLauncher RM4"

echo [INFO] All components launched.
pause
