# F.Y.E.O — Zero-Cost Maven Central Publishing Guide

This guide details the complete, zero-cost ($0) process to publish `fyeo-core` and `fyeo-spring-boot-starter` to **Maven Central** via Sonatype's modern Central Publishing Portal.

---

## 1. Prerequisites Already Configured

- **Maven Coordinates**: `io.github.mosh-py:fyeo-core` and `io.github.mosh-py:fyeo-spring-boot-starter`.
- **Packaging Verified**: `mvn clean install` passes with all tests, generating:
  - Binary JARs
  - `-sources.jar`
  - `-javadoc.jar`
- **Release Profile**: Configured in root `pom.xml` with `maven-gpg-plugin` (3.2.7) and `central-publishing-maven-plugin` (0.7.0).

---

## 2. Step 1: Generate Local GPG Key ($0 Cost)

Maven Central requires every artifact to be cryptographically signed.

Run the following command in your terminal:
```bash
gpg --gen-key
```
1. Enter your name: `Adageje Moshood`
2. Enter your email: `adagejem@gmail.com`
3. Enter a secure passphrase (remember this passphrase).

Once generated, list your key:
```bash
gpg --list-keys --keyid-format LONG
```
Look for the line starting with `pub`. Example:
```text
pub   rsa3072/8A3B1C2D4E5F6A7B 2026-10-31 [SC]
```
The key ID is the string after the slash (e.g., `8A3B1C2D4E5F6A7B`).

Distribute your public key to standard public keyservers (free):
```bash
gpg --keyserver keys.openpgp.org --send-keys <YOUR_KEY_ID>
gpg --keyserver keyserver.ubuntu.com --send-keys <YOUR_KEY_ID>
```

---

## 3. Step 2: Claim Namespace on Central Portal ($0 Cost)

1. Open your browser and navigate to: **https://central.sonatype.com/**
2. Click **Sign In** and choose **Sign in with GitHub** (authenticate with your `Mosh-py` account).
3. In the top right, click on your profile avatar -> **View Account** -> **Namespaces**.
4. Click **Add Namespace** and enter:
   ```text
   io.github.mosh-py
   ```
5. Sonatype will display a short verification code (e.g. `splendid-whale-1234`).
6. In your terminal, run GitHub CLI to create a temporary public repository named with that code:
   ```bash
   gh repo create Mosh-py/<verification-code> --public
   ```
7. Click **Verify Namespace** on the Sonatype portal. It will verify ownership within 10 seconds.
8. Once verified, delete the temporary repo:
   ```bash
   gh repo delete Mosh-py/<verification-code> --yes
   ```

---

## 4. Step 3: Generate Sonatype User Token

1. On https://central.sonatype.com/, navigate to your Account page.
2. Click **Generate User Token**.
3. Copy the generated `Username` and `Password` token.
4. Save them in `~/.m2/settings.xml` on your machine:
   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>YOUR_CENTRAL_TOKEN_USERNAME</username>
         <password>YOUR_CENTRAL_TOKEN_PASSWORD</password>
       </server>
     </servers>
   </settings>
   ```

---

## 5. Step 4: Publish to Maven Central

Once your token and GPG key are set up, release to Maven Central with a single command from `fyeo-ecosystem`:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
cd /home/adageje-moshood/Documents/Moshood/personalSpringProject/fyeo-ecosystem
mvn clean deploy -P release -DskipTests
```

The `central-publishing-maven-plugin` will:
1. Compile the code.
2. Generate sources and Javadocs.
3. Cryptographically sign all files using your GPG key.
4. Bundle and upload the deployment bundle to Sonatype Central Portal.
5. Automatically validate and publish it to the global Maven Central repository.

Within 15–30 minutes, `io.github.mosh-py:fyeo-spring-boot-starter` will be live on [search.maven.org](https://search.maven.org) and accessible to every developer worldwide!
