# AI Build - Minecraft Plugin

A Minecraft Paper plugin that uses AI to help build structures in-game. Players can describe what they want to build, and the AI will generate the structure using Minecraft blocks.

## Features

- 🤖 AI-powered building using OpenAI
- 🏗️ Natural language structure generation
- 📝 Build history tracking
- ✅ Build validation
- 🔄 Support for block rotation

## Requirements

- Java 17 or higher
- Paper MC 1.20.1 or compatible version
- OpenAI API key

## Building

Build the plugin using Gradle:

```bash
./gradlew build
```

Or using the Makefile:

```bash
make build
```

The compiled JAR will be in `build/libs/ai-build-0.1.0.jar`.

## Installation

1. Build the plugin (see above)
2. Copy the JAR file to your Paper server's `plugins/` folder
3. Configure your OpenAI API key (see below)
4. Restart the server

## Environment variables 🔒

Set your OpenAI API key via environment variable:

```bash
export OPENAI_API_KEY="sk-..."
```

Alternatively, copy `.env.example` to `.env` in the project root and set the key there:

```bash
cp .env.example .env
# then edit .env and set OPENAI_API_KEY
```

> **Security:** Do **not** commit real API keys to source control. Add `.env` to `.gitignore`.

### Using the included utility

A small Java helper `EnvConfig.getOpenAiApiKey()` checks in this order:
1. `System.getenv("OPENAI_API_KEY")`
2. `System.getProperty("OPENAI_API_KEY")`
3. A `.env` file in the project root

This lets you keep secrets in env vars or a local `.env` file for development.

## Usage

Use the `/aibuild` command in-game to create structures:

```
/aibuild <description>
```

Examples:
- `/aibuild create a small wooden house`
- `/aibuild build a stone tower 10 blocks tall`
- `/aibuild make a bridge across this gap`

### Undo Command

Undo the last build:
```
/aibuild undo
```

## Configuration

The plugin creates `plugins/AIBuild/config.yml` with these options:

```yaml
openai:
  api_key: "sk-proj-PUT_YOUR_ACTUAL_KEY_HERE"
  model: "gpt-4o-mini"
  max_blocks: 500
  timeout_ms: 60000

build:
  place_per_tick: 150           # Blocks placed per game tick
  cooldown_seconds: 30          # Cooldown between builds
  forward_offset_blocks: 3      # Distance in front of player
  replace_only_air: true        # Only replace air blocks
  allowed_materials:            # List of allowed materials
    - OAK_PLANKS
    - COBBLESTONE
    # ... more materials

debug:
  enabled: false                # Enable performance timing logs
```

## Project Structure

```
src/main/java/com/example/aibuild/
├── AIBuildCommand.java       # Main command handler
├── AIBuildPlugin.java        # Plugin entry point
├── BlockPlacer.java          # Block placement logic
├── BuildHistory.java         # Build tracking
├── BuildValidator.java       # Build validation
├── EnvConfig.java            # Environment configuration
├── OpenAIClient.java         # OpenAI API integration
├── Rotation.java             # Block rotation utilities
├── exception/
│   ├── BuildValidationException.java
│   ├── OpenAIException.java
│   └── PlanParseException.java
├── model/
│   ├── BlockSpec.java
│   ├── BuildPlan.java
│   └── Size.java
├── service/
│   ├── ConfigService.java
│   └── PlanParser.java
└── util/
    └── DebugTimer.java
```

## Testing

Run tests using Gradle:

```bash
./gradlew test
```

Test coverage includes:
- **BuildValidator**: 18 unit tests for validation logic
- **Rotation**: 17 unit tests for coordinate transformations
- **PlanParser**: 6 unit tests for JSON parsing
- **OpenAIException**: 7 unit tests for error handling

See [REFACTORING.md](REFACTORING.md) for details on the production-ready improvements.

## Dependencies

- Paper API 1.20.1
- OkHttp 4.12.0 (HTTP client)
- Gson 2.10.1 (JSON parsing)

## License

This project is provided as-is for educational and personal use.

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.
