#!/bin/zsh

# 📥 Accept optional input file for TestClient
INPUT_FILE=$1

echo "🚀 Compiling project..."
javac dsms/**/*.java dsms/*.java

echo "🧠 Launching Replicas..."
osascript -e 'tell app "Terminal" to do script "cd \"'$(pwd)'\"; java dsms.replicas.ReplicaLauncher NYK RM1; exit"'
osascript -e 'tell app "Terminal" to do script "cd \"'$(pwd)'\"; java dsms.replicas.ReplicaLauncher NYK RM2; exit"'
osascript -e 'tell app "Terminal" to do script "cd \"'$(pwd)'\"; java dsms.replicas.ReplicaLauncher NYK RM3; exit"'

sleep 2

echo "📦 Starting Sequencer..."
osascript -e 'tell app "Terminal" to do script "cd \"'$(pwd)'\"; java dsms.components.Sequencer; exit"'

sleep 2

echo "🛡️ Starting Replica Manager..."
osascript -e 'tell app "Terminal" to do script "cd \"'$(pwd)'\"; java dsms.components.ReplicaManager; exit"'

sleep 2

echo "🌐 Starting FrontEnd..."
osascript -e 'tell app "Terminal" to do script "cd \"'$(pwd)'\"; java dsms.components.FrontEnd; exit"'

sleep 2

# 🧪 Decide how to run TestClient
if [[ -n "$INPUT_FILE" && -f "$INPUT_FILE" ]]; then
    echo "🧪 Running TestClient with input from: $INPUT_FILE"
    java dsms.test.TestClient < "$INPUT_FILE"
else
    echo "📟 No input file provided. Launching TestClient for manual input..."
    java dsms.test.TestClient
fi
