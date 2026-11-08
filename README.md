# 🛡️ F.Y.E.O — Zero-Yield Visual AEAD & Chameleon Document Vault

> *An open-source initiative by **Java for the Street (JFS)** — Enterprise Code, Grassroots Soul.*

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mosh-py/fyeo-spring-boot-starter?color=blue&label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.mosh-py/fyeo-spring-boot-starter)
[![Documentation](https://img.shields.io/badge/Docs-Live%20Portal-10b981.svg)](https://mosh-py.github.io/fyeo-ecosystem/)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Zero External Runtime Dependencies](https://img.shields.io/badge/fyeo--core-0%20Dependencies-brightgreen.svg)]()
[![Java for the Street](https://img.shields.io/badge/Movement-Java%20for%20the%20Street%20%E2%9B%A3%EF%B8%8F%E2%98%95%EF%B8%8F-black.svg)]()

> *"You cannot stop a determined hacker from breaching the perimeter. Firewalls get misconfigured, IAM credentials leak, and zero-day exploits happen. When attackers breach your cloud storage, they expect a jackpot of customer identity documents. Instead, we deceive them into stealing harmless UI pixels: Zero readable PII. Zero metadata. Zero forensic yield."*

> **F.Y.E.O (*For Your Eyes Only*)** is an institutional-grade document security and steganographic envelope encryption platform for Java and Spring Boot. Built on the doctrine that **perimeter breaches are inevitable, but data leakage is not**, it transforms sensitive KYC files (Passports, BVN/NIN printouts, CAC Incorporation Certificates) into innocent corporate PNG images before bytes ever touch physical storage, uncloaking strictly in volatile RAM with automated memory zeroing.

---

## ⚡ The Problem: Why Traditional Storage Encryption Fails

When traditional compliance applications store sensitive documents in AWS S3 or on-prem disks:
1. **Target Identification**: Even if files are encrypted at rest with AES-256-GCM, the storage index displays clear targets:
   ```text
   s3://bank-kyc-bucket/passports/Adeyemi_Folashade_NIN.pdf.enc   <-- LEAK!
   s3://bank-kyc-bucket/cac/Apex_Logistics_RC194820.pdf.enc      <-- LEAK!
   ```
   Attackers and compromised staff immediately know which files contain high-value PII.
2. **Metadata Enumeration**: File names, sizes, timestamps, and customer names leak through directory listings.
3. **Cloud KMS Server-Side Encryption (SSE-S3) is an Illusion**: SSE only protects against physical hard drive theft. If an attacker gains an IAM role, API key, or finds an SSRF vulnerability, AWS transparently decrypts and serves every document in plaintext.
4. **Reviewer Disk Persistence**: Once downloaded for compliance review, files linger in browser download caches, reviewer OS temp folders, and local swap space.

---

## 💡 The F.Y.E.O Solution: Zero-Yield Storage Reality

F.Y.E.O solves this by decoupling physical storage from plaintext reality:

* **Chameleon Stego-AEAD**: Your sensitive PDF or image is deflated, authenticated with AES-256-GCM, and scattered into the least-significant bits of a **harmless corporate image** (e.g. `assets_icons_verified_shield.png`, 18 KB).
* **Zero Metadata Leakage**: Real filenames, customer names, and document types are encapsulated inside an encrypted binary `MetadataEnvelope`. S3 only sees innocent UI assets.
* **Ephemeral Volatile Review**: Compliance officers view documents on an anti-save HTML5 canvas rendered directly from memory. Pixels self-destruct and zero out after 15 seconds.

---

## 📦 Quick Start (30 Seconds)

### 1. Add Maven Dependency

```xml
<!-- For Spring Boot 3 / 2.7+ Applications -->
<dependency>
    <groupId>io.github.mosh-py</groupId>
    <artifactId>fyeo-spring-boot-starter</artifactId>
    <version>3.0.2</version>
</dependency>

<!-- Or Pure Java / Standalone CLI (Zero External Dependencies) -->
<dependency>
    <groupId>io.github.mosh-py</groupId>
    <artifactId>fyeo-core</artifactId>
    <version>3.0.2</version>
</dependency>
```

*(Gradle: `implementation 'io.github.mosh-py:fyeo-spring-boot-starter:3.0.2'`)*

---

### 2. Option A: Pure Java (`fyeo-core`)

```java
import io.fyeo.core.FyeoVault;
import io.fyeo.core.model.ChameleonPayload;

// 1. Initialize vault with your master key or user PIN
FyeoVault vault = new FyeoVault(System.getenv("APP_MASTER_KEY"));

// 2. Cloak sensitive document bytes into innocent PNG carrier
byte[] cloakedPng = vault.cloakDocument(rawFileBytes, null).getCarrierBytes();

// 3. Uncloak strictly in volatile RAM (Zero temp files on disk)
byte[] plainBytes = vault.uncloakRawBytes(cloakedPng, null);
```

---

### 3. Option B: Spring Boot (`fyeo-spring-boot-starter`)

**`application.yml`**:
```yaml
fyeo:
  vault:
    master-key: ${FYEO_VAULT_MASTER_KEY:DevSecretKeyMinimum32Characters#2026}
    ephemeral-view-duration-seconds: 15
```

**Controller / Service**:
```java
@RestController
@RequestMapping("/api/kyc")
public class KycIngestionController {

    @Autowired
    private FyeoVaultService fyeoVaultService;

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam("customerName") String customerName) throws IOException {

        MetadataEnvelope envelope = new MetadataEnvelope(
                file.getOriginalFilename(),
                customerName,
                "CAC_CERTIFICATE",
                System.currentTimeMillis(),
                file.getBytes()
        );

        // Cloak into harmless decoy asset
        ChameleonPayload payload = fyeoVaultService.protectDocumentWithEnvelope(envelope, null);
        String storageKey = fyeoVaultService.generateChameleonDecoyKey(101);

        Files.write(Paths.get("/var/storage/kyc/" + storageKey), payload.getCarrierBytes());
        return ResponseEntity.ok("Stored as innocent decoy: " + storageKey);
    }
}
```

---

## 🎨 Dual Storage Cloaking Modes

| Feature | Chameleon Stego-AEAD | Visual AEAD Noise |
| :--- | :--- | :--- |
| **Physical Appearance** | Innocent corporate icon, badge, or navigation asset | Scrambled visual static (random luminance entropy) |
| **Metadata Protection** | Binary `MetadataEnvelope` (Zero PII on disk) | Opaque HMAC-SHA256 hash key (`obj_...png`) |
| **Supported Formats** | **Any binary file**: PDF, PNG, JPG, DOCX, scans | Any image or visual asset |
| **Storage Destination** | Public or private AWS S3, GCP Buckets, disk | Private S3 buckets, on-prem disk |
| **Attacker Visibility** | Attacker does not know a document exists | Attacker sees noise, zero plaintext |

---

## 🏃 Try the Live CLI Playground (`fyeo-playpen`)

Test the entire round-trip in 30 seconds on your local machine:

```bash
cd fyeo-playpen
mvn clean compile exec:java
```
Creates a sample CAC document, cloaks it into an 18 KB PNG carrier, uncloaks it in RAM, and asserts 100% bit-level cryptographic parity.

---

## 📖 Complete Documentation & Guides

* 📘 [Comprehensive Developer Guide](docs/DEVELOPER_GUIDE.md) — Production architecture, Spring Boot patterns, AWS S3 integration, User PIN flows, and RAM zeroing.
* ⚛️ [Headless Frontend Guide](docs/HEADLESS_INTEGRATION_GUIDE.md) — Production React/TypeScript hook with anti-save and memory wiping.

---

## 📄 License

F.Y.E.O is open-source software licensed under the [Apache License, Version 2.0](LICENSE).

---

```text
  ______ __   __ _____ ____  
 |  ____|\ \ / /|  ___/ __ \ 
 | |__    \ V / | |__| |  | |
 |  __|    \ /  |  __| |  | |
 | |       | |  | |__| |__| |
 |_|       |_|  |_____\____/ 
```
**Java for the Street. 🛣️☕️**  
*Enterprise Code. Grassroots Soul.*
