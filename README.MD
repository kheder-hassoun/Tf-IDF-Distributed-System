# Distributed TF-IDF Search System

This project implements a distributed, fault-tolerant search engine based on the TF-IDF algorithm. It is designed to index and search large sets of text files efficiently, using a leader/worker architecture coordinated via Apache Zookeeper. The system is built with Spring Boot and deployed on Kubernetes.

---

##  Features

* Distributed text indexing using Lucene
* TF-IDF based scoring across multiple workers
* Leader election and service discovery via Apache Zookeeper
* REST APIs for file upload, search, and download
* Local data processing on each worker node for network efficiency
* Dynamic load balancing during file upload based on index size

---

##  System Components

* **Worker Node**: Handles document storage, indexing, and searching. Each worker maintains a local Lucene index.
* **Leader Node**: Distributes search queries and coordinates uploads based on worker load (index size).
* **Zookeeper**: Provides leader election and service registry functionality.

---

##  API Endpoints

### Worker API (default port: `8085`)

* `POST /worker/upload` — Upload a document
* `GET /worker/download?path={relative_path}` — Download document
* `POST /worker/process` — Search query execution
* `GET /worker/index-size` — Get local index size in bytes

### Leader API (default port: `8085`)

* `POST /leader/start` — Start search query
* `POST /leader/upload` — Upload file via leader (delegates to best worker)
* `GET /leader/download?path={relative_path}` — Retrieve file from system

---

## 📦 Kubernetes Deployment

A basic deployment is included to run the distributed search nodes in a Kubernetes environment.

### Example `Deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: tfidf-node
  namespace: kh-pipeline
spec:
  replicas: 3
  selector:
    matchLabels:
      app: tfidf-node
  template:
    metadata:
      labels:
        app: tfidf-node
    spec:
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchLabels:
                  app: tfidf-node
              topologyKey: kubernetes.io/hostname

      securityContext:
        fsGroup: 2000

      containers:
        - name: tfidf-node
          image: 172.29.3.41:5000/search_sys_node:2.2
          imagePullPolicy: IfNotPresent
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: default
            - name: ZK_HOSTS
              value: zookeeper:2181
            - name: POD_IP
              valueFrom:
                fieldRef:
                  fieldPath: status.podIP
            - name: SERVER_PORT
              value: "8085"
          ports:
            - containerPort: 8085
          volumeMounts:
            - name: documents
              mountPath: /app/documents
            - name: index
              mountPath: /app/lucene-index

      volumes:
        - name: documents
          hostPath:
            path: /mnt/data/documents
            type: DirectoryOrCreate
        - name: index
          hostPath:
            path: /mnt/data/lucene-index
            type: DirectoryOrCreate
```

> ✅ Note: In production, use persistent volumes instead of `hostPath`

---

##  How It Works

* When deployed, each worker joins the system and registers via Zookeeper.
* One leader is elected among the running nodes.
* When a file is uploaded, the leader checks all workers for their current index size and sends the file to the least-loaded worker.
* All search queries are handled by the leader, which dispatches the query to all workers and aggregates the results.

---

## 📁 Project Structure

```text
src/
├── leader/               # Handles coordination and aggregation
├── worker/               # Handles indexing and query execution
├── registry/             # Zookeeper integration
├── document/             # TF-IDF data models
├── config/               # Spring Boot + Lucene config
├── controller/           # REST API endpoints
└── app/                  # Application entry point
```

---

##  Technologies

* Java 17
* Spring Boot
* Apache Lucene
* Apache Zookeeper
* Kubernetes
* Docker

---

##  Future Work

* Add support for non-text files (PDF, DOCX)
* Implement dashboard UI
* Enable dynamic horizontal scaling via autoscalers
* Enhance fault recovery and redundancy (multi-leader failover)

---

## 📄 License

KH khdrhswn32@gmail.com 

---
