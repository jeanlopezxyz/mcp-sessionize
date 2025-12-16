# Sessionize MCP Server

[![npm](https://img.shields.io/npm/v/mcp-sessionize)](https://www.npmjs.com/package/mcp-sessionize)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

MCP server for accessing Sessionize event data - speakers, sessions, and schedules.

## Get Your Event ID

1. Log in to [Sessionize](https://sessionize.com)
2. Select your event → **API / Embed** → Enable **API**
3. Copy your Event ID from the URL: `https://sessionize.com/api/v2/{EVENT_ID}/view/All`

The `SESSIONIZE_EVENT_ID` environment variable is used to set the default event. You can also pass `eventId` directly in tool calls.

## Installation

### Claude Desktop

Add to `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) or `%APPDATA%\Claude\claude_desktop_config.json` (Windows):

```json
{
  "mcpServers": {
    "sessionize": {
      "command": "npx",
      "args": ["-y", "mcp-sessionize"],
      "env": {
        "SESSIONIZE_EVENT_ID": "your-event-id"
      }
    }
  }
}
```

### Claude Code

```bash
claude mcp add sessionize -e SESSIONIZE_EVENT_ID="your-event-id" -- npx -y mcp-sessionize
```

### Cursor / Windsurf / VS Code

| Editor | Config File |
|--------|-------------|
| Cursor | `~/.cursor/mcp.json` |
| Windsurf | `~/.codeium/windsurf/mcp_config.json` |
| VS Code | `code --add-mcp '{"name":"sessionize","command":"npx","args":["-y","mcp-sessionize"],"env":{"SESSIONIZE_EVENT_ID":"your-event-id"}}'` |

### Docker

```bash
docker run -i --rm -e SESSIONIZE_EVENT_ID=your-event-id ghcr.io/jeanlopezxyz/mcp-sessionize
```

## Usage Examples

```
"Show me all speakers"
"Find speaker John Doe"
"What sessions does Jane Smith have?"
"List all sessions about Kubernetes"
"What's the schedule?"
```

## Tools

| Tool | Description |
|------|-------------|
| `getSpeakers` | List all speakers |
| `findSpeaker` | Search speaker by name |
| `getSessions` | List all sessions |
| `findSession` | Search sessions by topic |
| `getSchedule` | Get event schedule |
| `getSessionsBySpeaker` | Get sessions by speaker |

## Prompts

| Prompt | Description |
|--------|-------------|
| `event_overview` | Get conference overview |
| `find_speaker_info` | Find speaker details |
| `sessions_by_topic` | Find sessions on a topic |
| `conference_schedule` | Get full schedule |
| `speaker_sessions` | List sessions by speaker |
| `recommend_sessions` | Get session recommendations |

## Tech Stack

- **Java 25** + **Mandrel 25**
- **Quarkus 3.30**
- **Quarkus MCP Server 1.8.1**

## License

[Apache-2.0](LICENSE)
