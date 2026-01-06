# Springboard Plugin

A Gradle plugin for managing and configuring Edifice applications built on the ENT Core framework.

## Overview

The Springboard Plugin simplifies the development workflow for Edifice applications by providing automated tasks for:
- Configuration file generation
- Deployment extraction and organization
- Translation and help documentation management
- Theme extraction
- Integration testing with Gatling
- Module version management from GitHub

## Requirements

- Java 8
- Gradle 4.5
- Docker and Docker Compose (for build and deployment)

## Installation

### Using the Build Script

The plugin includes a build script that uses Docker Compose for consistent builds:

```bash
# Clean the project
./build.sh clean

# Install the plugin locally
./build.sh install

# Publish to repository (requires credentials)
./build.sh publish
```

### Manual Installation

```bash
gradle install
```

## Usage

### Applying the Plugin

In your project's `build.gradle`:

```groovy
plugins {
    id 'fr.wseduc.springboard' version '3.2-zookeeper-SNAPSHOT'
}
```

### Available Tasks

#### `init`
Initialize a new Edifice project with all necessary files and directories:
- Creates directory structure (mods, deployments, i18n, etc.)
- Extracts deployments, helps, and translations
- Generates sample data and configuration files
- Sets up Docker Compose configuration
- Creates integration test scaffolding

```bash
gradle init
```

#### `generateConf`
Generate the `ent-core.json` configuration file from templates:

```bash
gradle generateConf
```

This task:
- Reads `conf.properties` and `gradle.properties`
- Merges with `default.properties`
- Automatically fetches missing base modules versions from GitHub
- Generates `ent-core.json` from `ent-core.json.template`

#### `extractDeployments`
Extract deployment artifacts from dependencies:

```bash
gradle extractDeployments
```

#### `extractHelps`
Extract help documentation from dependencies:

```bash
gradle extractHelps
```

#### `extractTranslations`
Extract i18n translation files:

```bash
gradle extractTranslations
```

#### `extractTheme`
Extract theme assets:

```bash
gradle extractTheme
```

#### `integrationTest`
Run Gatling integration tests:

```bash
gradle integrationTest
```

## Module Version Management

The plugin can automatically fetch the latest versions of Edifice modules from GitHub if they're not specified in your `gradle.properties`. Supported modules include:

- `mod-pdf-generator`
- `mod-mongo-persistor`
- `mod-image-resizer`
- `mod-zip`
- `mod-postgresql`
- `mod-json-schema-validator`
- `mod-sftp`
- `mod-webdav`
- `mod-sms-sender`

### Environment Variables

- `MODS_DEFAULT_BRANCH`: Set the default branch to fetch module versions from (defaults to trying `master`, then `main`)

## Project Structure

After running the `init` task, your project will have:

```
.
├── conf.properties              # User configuration
├── default.properties           # Default configuration values
├── ent-core.json.template      # Configuration template
├── ent-core.json               # Generated configuration
├── package.json                # Node.js dependencies
├── docker-compose.yml          # Docker setup
├── deployments/                # Extracted deployment artifacts
├── i18n/                       # Translation files
├── static/help/                # Help documentation
├── mods/                       # Application modules
├── sample-be1d/                # Sample data
├── neo4j-conf/                 # Neo4j configuration
├── docker-entrypoint-initdb.d/ # Database initialization scripts
└── src/
    └── test/
        └── scala/
            └── org/entcore/test/
                ├── scenarios/  # Gatling test scenarios
                └── simulations/ # Gatling simulations
```

## Configuration Files

### conf.properties
User-specific configuration properties that override defaults.

### default.properties
Default configuration values merged during configuration generation.

### gradle.properties
Module versions and build configuration.

## Docker Support

The plugin includes Docker Compose configuration with platform-specific support:
- Automatically detects M1/ARM64 Macs
- Uses appropriate Docker images for the platform
- Configures PostgreSQL, MongoDB, and Neo4j services

## Development

### Building the Plugin

```bash
./build.sh install
```

### Publishing

```bash
# Set credentials in ~/.gradle/gradle.properties:
# odeUsername=<username>
# odePassword=<password>

./build.sh publish
```

## License

See LICENSE file for details.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## Support

For issues and questions, please refer to the Edifice documentation or create an issue in the repository.
