# AI Build Plugin - AI Agent Instructions

## Architecture Overview

This is a **Paper Minecraft plugin** that generates structures using OpenAI's API. The architecture follows an async workflow:

1. **Command Handler** ([AIBuildCommand.java](../src/main/java/com/example/aibuild/AIBuildCommand.java)) receives `/aibuild <description>` from player
2. **OpenAI Client** ([OpenAIClient.java](../src/main/java/com/example/aibuild/OpenAIClient.java)) calls OpenAI Responses API (async thread) with strict JSON schema
3. **Build Validator** ([BuildValidator.java](../src/main/java/com/example/aibuild/BuildValidator.java)) validates structure rules (foundation, no floating blocks)
4. **Block Placer** ([BlockPlacer.java](../src/main/java/com/example/aibuild/BlockPlacer.java)) places blocks gradually (batched over game ticks) on main thread
5. **Build History** tracks placements for undo functionality

## Critical Patterns

### Thread Safety
- **Network calls MUST be async**: Use `plugin.getServer().getScheduler().runTaskAsynchronously()` for OpenAI calls
- **Block placement MUST be sync**: Use `runTask()` or `BukkitRunnable` for world modifications
- Never touch Bukkit API from async threads (causes crashes)

### Configuration System
- API key sourced via `${OPENAI_API_KEY}` env var in [config.yml](../src/main/resources/config.yml)
- Use `EnvConfig.getOpenAiApiKey()` for dev environments (checks env vars → system properties → `.env` file)
- **Security**: `.env` is gitignored, never commit API keys (see `.env.example` for template)

### Build Validation Rules
See [BuildValidator.java](../src/main/java/com/example/aibuild/BuildValidator.java):
- Structures must have **solid foundation** at y=0 (no gaps)
- **No floating blocks**: every block above ground must have support below
- Size caps at 80×80×80 (configurable `openai.max_blocks`)
- Only materials from whitelist in `config.yml` → `build.allowed_materials`

### Rotation System
[Rotation.java](../src/main/java/com/example/aibuild/Rotation.java) handles cardinal direction mapping:
- AI generates structure facing north (dx/dz coordinates)
- `rotateXZ()` transforms coords based on player's facing direction (BlockFace)
- Origin point is `forward_offset_blocks` (default 3) in front of player

## Development Workflow

### Build & Test
```bash
./gradlew build          # Compile to build/libs/ai-build-0.1.0.jar
make build               # Alternative using Makefile
```

### Plugin Installation
1. Copy JAR to Paper server's `plugins/` folder
2. Set `export OPENAI_API_KEY="sk-..."` before starting server
3. Server auto-generates `plugins/AIBuild/config.yml` from [src/main/resources/config.yml](../src/main/resources/config.yml)

### Debugging
- Use `plugin.getLogger().info()` for logging (avoid System.out)
- Check server console for stack traces
- Common issue: "Not running on main thread" = missed sync/async boundary

## Key Files

- **[plugin.yml](../src/main/resources/plugin.yml)**: Plugin metadata, commands, permissions
- **[config.yml](../src/main/resources/config.yml)**: User-facing config (API settings, material whitelist, cooldowns)
- **[build.gradle](../build.gradle)**: Paper API + OkHttp + Gson dependencies
- **[AIBuildCommand.java](../src/main/java/com/example/aibuild/AIBuildCommand.java)**: Main logic (200+ lines) - orchestrates entire flow

## Common Tasks

### Adding New Materials
Edit `config.yml` → `build.allowed_materials` array with Material enum names from Paper API

### Changing AI Prompt
Modify `OpenAIClient.generateBuildPlanJson()` instructions string (includes JSON schema + rules)

### Adjusting Performance
- `build.place_per_tick`: blocks placed per game tick (default 150)
- `openai.timeout_ms`: API call timeout (default 20000ms)
- `build.cooldown_seconds`: per-player cooldown between builds

## External Dependencies

- **Paper API 1.20.1**: Bukkit/Spigot fork with modern Minecraft API
- **OkHttp 4.12.0**: HTTP client for OpenAI API calls
- **Gson 2.10.1**: JSON parsing (build plans from AI)
- **OpenAI Responses API**: Endpoint at `https://api.openai.com/v1/responses` (uses `gpt-4o` model by default)
