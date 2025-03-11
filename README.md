# Developing a Spring AI Enhanced Restaurant Booking System Employing an API-first Approach

[![GA](https://img.shields.io/badge/Release-Alpha-darkred)](https://img.shields.io/badge/Release-Alpha-darkred) ![Github Action CI Workflow Status](https://github.com/pacphi/spring-ai-resos/actions/workflows/ci.yml/badge.svg) [![Known Vulnerabilities](https://snyk.io/test/github/pacphi/spring-ai-resos/badge.svg?style=plastic)](https://snyk.io/test/github/pacphi/spring-ai-resos)

This multi-module project hosts a client code-generated from an OpenAPI derivative of the ResOs API combined with a Spring AI implementation.
It also includes an MCP server, MCP client configuration for use with Claude and a standalone ReactJS powered chatbot UI.

* [Background](#background)
* [Getting started](#getting-started)
* [Prerequisites](#prerequisites)
* How to
    * [Clone](#how-to-clone)
    * [Build](#how-to-build)
    * [Consume](#how-to-consume)
    * [Run](#how-to-run)
* Also see
  * [ResoOS API](https://documenter.getpostman.com/view/3308304/SzzehLGp?version=latest)
  * the [spark](docs/SPARK.md) that lit this project up
  * [Roadmap](docs/ROADMAP.md)

## Background

As a Spring Boot and Spring AI developer, I want
to consume libraries that make it convenient to add capabilities to my application(s)
as for the following

Use-case:

* Imagine instead of using OpenTable or Tock you could converse with a chatbot to search for restaurant(s) and make reservation(s) on your behalf.

## Getting started

Start with:

* A Github [account](https://github.com/signup)
* (Optional) An [API key](https://resos.com/support/how-to-use-resos-rest-api/) from ResOS
  * you only need one if you intend to register as a restaurateur!
  * we will spin up a [backend](backend) that is API-compatible, implemented with Spring Boot Starter Data JDBC
* An LLM provider
  * e.g., HuggingFace, Gemini, Ollama, or OpenAI

## Prerequisites

* Git CLI (2.43.0 or better)
* Github CLI (2.65.0 or better)
* httpie CLI (3.2.2 or better)
* Java SDK (21 or better)
* Maven (3.9.9 or better)
* an LLM provider account (if using public cloud or commercially hosted models)

## How to clone

with Git CLI

```bash
git clone https://github.com/pacphi/spring-ai-resos
```

with Github CLI

```bash
gh repo clone pacphi/spring-ai-resos
```

## How to build

Open a terminal shell, then execute:

```bash
cd spring-ai-resos
./mvnw clean install
```

## How to consume

If you want to incorporate any of the starters as dependencies in your own projects, you would:

### Add dependency

Maven

```maven
<dependency>
    <groupId>me.pacphi</groupId>
    <artifactId>spring-ai-resos-client</artifactId>
    <version>{release-version}</version>
</dependency>
```

Gradle

```gradle
implementation 'me.pacphi:spring-ai-resos-client:{release-version}'
```

> Replace occurrences of {release-version} above with a valid artifact release version number

### Add configuration

Following Spring Boot conventions, you would add a stanza like this to your:

application.properties

```properties
default.url=${RESOS_API_ENDPOINT:https://api.resos.com/v1}
```

application.yml

```yaml
default:
  url: ${RESOS_API_ENDPOINT:https://api.resos.com/v1}
```

> To activate the client, specify an API key (if required), and tune other associated configuration.

Consult the [chatbot](chatbot) module's configuration for alternative
`dependencies` and `configuration` that are available to add.

Configuration will be found in labeled `spring.config.activate.on-profile` sections of the [pom.xml](chatbot/pom.xml) file.

## How to run

You're going to need to launch the [backend](backend) module first, unless you're a restaurateur and  have a valid API key for interacting with the ResOS v1.2 API.

To launch the backend

```bash
cd backend
./mvnw clean install -DskipTests spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.jvmArguments="--add-opens java.base/java.net=ALL-UNNAMED"
```

There's the [chatbot](chatbot) module.

But there's also a way to integrate with Claude desktop via MCP client configuration that will consume an MCP server implementation.

Follow these instructions.

Add the following stanza to a file called `claude_desktop_config.json`:

```json
"spring-ai-resos": {
  "command": "java",
  "args": [
    "-jar",
    "<path-to-project>/target/spring-ai-resos-mcp-server-0.0.1-SNAPSHOT.jar"
  ]
}
```

or for testing with backend

```json
"spring-ai-resos": {
  "command": "java",
  "args": [
    "-Dspring.profiles.active=dev",
    "-jar",
    "<path-to-project>/target/spring-ai-resos-mcp-server-0.0.1-SNAPSHOT.jar"
  ]
}
```

Restart Claude Desktop instance.
Verify that you have a new set of tool calls available.
Chat with Claude.