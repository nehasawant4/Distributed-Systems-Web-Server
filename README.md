# Distributed-Systems-Web-Server

## Santa Clara University Web Server

A functional web server using Java, implementing basics of distributed programming, client/server architecture, and the challenges in building high-performance servers.

### Features
- Implements basic HTTP/1.0 and HTTP/1.1 protocols.
- Supports file retrieval from a document root.
- Manages persistent connections to improve performance.
- Handles both single-threaded and multi-threaded server configurations.

### Usage
The server can be started with the following commands:

- javac Server.java
- java Server –document_root server_files -port 8080
- Go to the browser and open http://localhost:8080/
