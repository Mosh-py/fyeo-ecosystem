# 🧪 F.Y.E.O Hands-On Developer Playpen
**Java for the Street. 🛣️☕️**

> *Enterprise Code, Grassroots Soul.*

A live, zero-dependency CLI sandbox created for developers and security architects to test encryption, steganographic cloaking, and volatile RAM decryption using `io.github.mosh-py:fyeo-core:3.0.2` without spinning up web servers, containers, or database infrastructure.

---

## ⚡ Architecture: The Singleton Service
[`FileEncryptorService.java`](src/main/java/io/jfs/playpen/service/FileEncryptorService.java) implements the canonical **JVM ClassLoader Singleton Pattern** (Bill Pugh Idiom):
- `FyeoVault` is initialized lazily and atomically by the JVM ClassLoader upon first reference.
- Zero synchronization locks or overhead during execution.
- 100% thread-safe across concurrent high-throughput requests.

---

## 🚀 How to Run the Live Sandbox

### 1. Run with the Default CAC Sample Document
```bash
cd fyeo-playpen
mvn clean compile exec:java
```

### 2. Test with Any File on Your Machine (PDF, PNG, JPG, DOCX)
Pass any absolute or relative path via `-Dexec.args`:
```bash
mvn compile exec:java -Dexec.args="/path/to/your/cac_certificate.pdf"
```

---

## 📁 Generated Outputs

When the test executes, it automatically writes artifacts into the [`output/`](output/) directory:
1. `output/cloaked_document.png`: An innocent corporate badge PNG. Opening this image in any standard image viewer displays a harmless corporate graphic; the sensitive document is invisible and encrypted with AES-256-GCM in the low-order bits.
2. `output/restored_<filename>`: The uncloaked document restored from volatile RAM. Verified 100% bit-perfect with zero byte corruption.
