# Maven Central Setup Guide

This document summarizes configuring this multi-module Maven project for publication to Maven Central using a GitHub account.

## Excluding Playground Module from Maven Central Release

To configure the multi-module Maven project to exclude the playground module from being published to Maven Central:

Add a specific `<modules>` section in the Maven-central profile:

```xml
<profiles>
    <profile>
        <id>maven-central</id>
        <!-- Add modules section here -->
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-gpg-plugin</artifactId>
                    <!-- existing configuration -->
                </plugin>
                <plugin>
                    <groupId>org.sonatype.central</groupId>
                    <artifactId>central-publishing-maven-plugin</artifactId>
                    <!-- existing configuration -->
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

In the playground module's pom.xml, add:

```xml
<properties>
    <maven.deploy.skip>true</maven.deploy.skip>
    <maven.install.skip>true</maven.install.skip>
</properties>
```

## Setting Up Maven Central Account with GitHub

Here's a detailed guide for setting up a Maven Central account using a GitHub account instead of a domain:

### 1. Initial Setup

1. Create an account on [Sonatype's OSSRH Jira](https://issues.sonatype.org/secure/Signup!default.jspa)

2. Create a New Project ticket:
   - Click "Create" in JIRA
   - Select "Community Support - Open Source Project Repository Hosting" (OSSRH)
   - Project: "Community Support - Open Source Project Repository Hosting"
   - Issue Type: "New Project"

### 2. Project Information

Provide the following in your ticket:

- Group Id: `io.github.pacphi`
- Project URL: `https://github.com/pacphi/spring-ai-resos`
- SCM URL: `https://github.com/pacphi/spring-ai-resos.git`
- Username(s): Your Sonatype JIRA username

### 3. GitHub Verification

- Create a repository named `OSSRH-XXXXX` (XXXXX = your ticket number) in your GitHub account, or
- Add a specific comment to your existing repository's issue tracker

### 4. POM Configuration

Update your project's `pom.xml` with the required elements:

```xml
<groupId>io.github.pacphi</groupId>
<name>spring-ai-resos</name>
<description>Spring AI starters for conversational AI with support for alternative model providers</description>
<url>https://github.com/pacphi/spring-ai-resos</url>

<licenses>
    <license>
        <name>Apache License, Version 2.0</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0</url>
    </license>
</licenses>

<developers>
    <developer>
        <id>pacphi</id>
        <name>Chris Phillipson</name>
        <url>https://github.com/pacphi</url>
    </developer>
</developers>

<scm>
    <connection>scm:git:git://github.com/pacphi/spring-ai-resos.git</connection>
    <developerConnection>scm:git:ssh://github.com:pacphi/spring-ai-resos.git</developerConnection>
    <url>https://github.com/pacphi/spring-ai-resos/tree/main</url>
</scm>
```

### 5. GPG Setup

Generate and distribute your GPG key:

```bash
gpg --gen-key
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

### 6. Maven Settings

Add your credentials to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>your-jira-username</username>
      <password>your-jira-password</password>
    </server>
  </servers>
</settings>
```

### 7. Token Generation

1. Generate a token from [Sonatype's Central Portal](https://central.sonatype.org/register/central-portal/)
2. Store the token securely

### 8. Deployment

Deploy using:

```bash
mvn clean deploy -P maven-central
```

### Important Notes

- Group ID will be `io.github.pacphi`
- First deployment may take up to 2 hours to sync
- Subsequent deployments are faster
- Use proper version format (e.g., `1.0.0` not `1.0.0-SNAPSHOT` for releases)
- Approval process typically takes 1-2 business days
- First publication requires manual verification by Sonatype staff
- Subsequent publications are automated

## Usage

To release the project (excluding playground module):

```bash
mvn clean deploy -P maven-central
```

This will process all modules except playground and publish them to Maven Central.
