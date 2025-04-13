@echo off
echo Stopping all running DSMS Java processes...

wmic process where "CommandLine like '%%dsms.replicas.ReplicaLauncher%%'" delete
wmic process where "CommandLine like '%%dsms.components.Sequencer%%'" delete
wmic process where "CommandLine like '%%dsms.components.ReplicaManager%%'" delete
wmic process where "CommandLine like '%%dsms.components.FrontEnd%%'" delete
wmic process where "CommandLine like '%%test.Client%%'" delete

echo All DSMS components terminated.
pause
