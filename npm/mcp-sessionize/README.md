# mcp-sessionize

MCP Server for Sessionize Event Data. Access speakers, sessions, and schedules from any Sessionize-powered conference or event.

## Quick Start

```bash
# With a default event ID
SESSIONIZE_EVENT_ID=your-event-id npx mcp-sessionize

# Without default (provide eventId in each tool call)
npx mcp-sessionize
```

## Sessionize API Setup

Before using this server, enable the API in your Sessionize event:

1. Log in to [Sessionize](https://sessionize.com)
2. Go to **API / Embed** in your event dashboard
3. Toggle **Enable API** to ON
4. Copy your Event ID from the API URL: `https://sessionize.com/api/v2/{EVENT_ID}/view/All`

## Configuration

Add to `~/.claude/settings.json` (Claude Code) or your MCP client config:

```json
{
  "mcpServers": {
    "sessionize": {
      "command": "npx",
      "args": ["-y", "mcp-sessionize@latest"],
      "env": {
        "SESSIONIZE_EVENT_ID": "your-event-id"
      }
    }
  }
}
```

## Requirements

- Node.js 16+ (for npx)
- Sessionize Event ID with API enabled

> **Note:** No Java required! Native binaries are automatically downloaded for your platform.

## Tools (6)

| Tool | Description |
|------|-------------|
| `getSpeakers` | List all speakers for an event |
| `findSpeaker` | Search for a speaker by name |
| `getSessionsBySpeaker` | Get sessions for a specific speaker |
| `getSessions` | List all sessions for an event |
| `findSession` | Search sessions by title or description |
| `getSchedule` | Get the event schedule/agenda |

## Example Prompts

```
"Show me all speakers"
"Find sessions about Kubernetes"
"What's the schedule for today?"
"Search for speaker John Doe"
"Get sessions by Jane Smith"
```

## Supported Platforms

- macOS (ARM64, x64)
- Linux (x64)
- Windows (x64)

## Documentation

Full docs: https://github.com/jeanlopezxyz/mcp-sessionize

## License

Apache-2.0
