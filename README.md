# Distributed Share Market System (DSMS)

This project implements a fault-tolerant and highly available **Distributed Share Market System (DSMS)** as part of the course **COMP 6231: Distributed System Design** at Concordia University, Winter 2025.

The system ensures continuous availability and correctness in the presence of failures, including both process crashes and software (Byzantine) failures. It uses active replication, a sequencer for total ordering, and replica managers for failure detection and recovery.

---

## Features

- **Front-End (FE)**: Handles client requests, manages dynamic timeouts, and aggregates responses.
- **Sequencer**: Assigns sequence numbers to requests and ensures total order delivery to all replicas.
- **Replica Manager (RM)**: Monitors replicas, detects failures, and initiates recovery procedures.
- **Test Client**: Simulates share market operations and failure scenarios to validate system behavior.

---

## Team Members and Responsibilities

| Member                    | Module                        | Description                                                                 |
|---------------------------|-------------------------------|-----------------------------------------------------------------------------|
| Pratim Mandal             | Front-End (FE)                | Handles client request routing, timeout handling, and response aggregation. |
| Nanthan Srikumar Radha    | Replica Manager (RM)          | Manages replicas, detects faults, and performs recovery operations.         |
| Gurleen Pannu             | Sequencer                     | Assigns sequence IDs and ensures total ordering of requests.                |
| Diba Pourzandi            | Test Client & Failure Simulation | Designs test cases, simulates failure scenarios, and validates system correctness. |

---

## How to Compile and Run

### Compile

```bash
javac dsms/**/*.java dsms/*.java
```

### Execution (Use separate terminal windows)

#### Terminal 1: Start Replicas

```bash
java dsms.replicas.ReplicaLauncher NY RM1
java dsms.replicas.ReplicaLauncher NY RM2
java dsms.replicas.ReplicaLauncher NY RM3
java dsms.replicas.ReplicaLauncher NY RM4
```

#### Terminal 2: Start Sequencer

```bash
java dsms.components.Sequencer
```

#### Terminal 3: Start Replica Manager

```bash
java dsms.components.ReplicaManager
```

#### Terminal 4: Start Front-End

```bash
java dsms.components.FrontEnd
```

#### Terminal 5: Start Test Client

```bash
java dsms.test.TestClient
```

---

## Sample Test Commands

```plaintext
addShare:EQUITY NYKE010425 100
listShareAvailability:EQUITY
purchaseShare:NYKB1000 NYKE010425 EQUITY 3
getShares:NYKB1000
sellShare:NYKB1000 NYKE010425 1
addShare:EQUITY NYKE010426 100
swapShares:NYKB1000 NYKE010425 EQUITY NYKE010426 EQUITY
removeShare:NYKE010425 EQUITY
```

---

## System Architecture Overview

```plaintext
[Client] → [FrontEnd] → [Sequencer] → [Replicas (4x)]
                                ↓
                      [Replica Manager (per replica)]
```

- All components communicate over **UDP**, with mechanisms in place to ensure **reliable message delivery**.
- **Active replication** ensures consistency and fault tolerance.
- The system waits for **a quorum of two matching responses** to validate correctness and deliver results to clients.

---

## Course Information

- **Course**: COMP 6231 – Distributed System Design  
- **Instructor**: Dr. R. Jayakumar  
- **Institution**: Concordia University, Montreal  
- **Semester**: Winter 2025  

---

## License

This project is intended solely for academic use.
