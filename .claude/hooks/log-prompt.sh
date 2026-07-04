#!/usr/bin/env bash
# Auto-log every prompt submitted to Claude Code → raw material for ai/prompts.md
LOG_FILE="$CLAUDE_PROJECT_DIR/ai/prompt-log.md"
PROMPT=$(cat | python3 -c "import sys, json; print(json.load(sys.stdin).get('prompt',''))")
{
  echo ""
  echo "---"
  echo "**$(date '+%Y-%m-%d %H:%M')**"
  echo ""
  echo "$PROMPT"
} >> "$LOG_FILE"
exit 0
