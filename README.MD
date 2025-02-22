# TF-IDF-System

## Introduction

This project implements a distributed system for computing TF-IDF values efficiently, with a user-friendly web interface. The design elegantly handles file management and communication between nodes, while also addressing challenges such as leader changes during runtime.
To include your GitHub repository URL in a cool and visually appealing way in your README file, you can use badges, links, or custom sections. Here's an example of how to make it stand out:

---

### 🔗 **Repository Link**

[![Visit Repository](https://img.shields.io/badge/GitHub-Visit%20Repository-blue?style=for-the-badge&logo=github)](https://github.com/kheder-hassoun/My-Tf-IDF-Distributed-System-Web-based-)

---

### 💻 **Clone the Repository**

```bash
git clone https://github.com/kheder-hassoun/My-Tf-IDF-Distributed-System-Web-based-.git
```

---

### 📂 **Quick Access**

- 🌐 **URL**: [My-Tf-IDF-Distributed-System-Web-based](https://github.com/kheder-hassoun/My-Tf-IDF-Distributed-System-Web-based-)
- 📥 **Download ZIP**: [Direct Link](https://github.com/kheder-hassoun/My-Tf-IDF-Distributed-System-Web-based-/archive/refs/heads/main.zip)

---

---

## Key Points of the Project

1. **Worker-Specific Files:**  
   Each worker has its own set of files. This is achieved by specifying the file directory path in the `application.properties` file.
   - When launching each worker instance, the path to its files must be defined to ensure proper data separation.

2. **Leader (Coordinator):**  
   The leader does not handle or access the files directly. Its primary role is to forward the query to all available workers for processing.

3. **Communication Between the Leader and Web Server:**  
   The issue of leader changes (and the associated change in the port number) is addressed using Zookeeper.
   - A Zookeeper node named `leader_info` is created to store the port information of the current leader.
   - When the search button is clicked on the web interface, the web server contacts this node to retrieve the current leader's connection details and then sends the query to it.

---

## How to Run

1. **Set Up Worker Files:**
   - Define the file directory path for each worker in the `application.properties` file.

2. **Start the Leader and Workers:**
   - Start the leader first, followed by the workers, ensuring each worker has its file directory configured.

3. **Use the Web Interface:**
   - Once the system is running, use the web interface to submit queries.
   - When a search query is submitted, the web server connects to the Zookeeper node to fetch the current leader's information and forwards the query to the leader for processing.

---

## Additional Notes

- Ensure Zookeeper is set up and running correctly before launching the system.
- The system is designed for horizontal scalability, allowing you to add more workers as needed.
-

kheder hassoun
- For more details about configuring the system, refer to the configuration files.

---

## kheder hassoun
