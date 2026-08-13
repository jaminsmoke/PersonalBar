# Tools (PersonalHostel)

Kanban CLI y skills para agentes. Copiados desde el patrón de Personal Comander (`jaminsmoke/jarvis-skills`).

```bash
cd tools/kanban-cli
bun install
cd ../..

# Copiar IDs locales (gitignored)
copy .kanbanrc.json.template .kanbanrc.json   # Windows
# cp .kanbanrc.json.template .kanbanrc.json

# Validar contra el GitHub Project
bun run tools/kanban-cli/cli.ts config validate

# Listar ítems
bun run tools/kanban-cli/cli.ts list
```

Skills (leer antes de crear ítems): `tools/agent-skills/jarvis-github-kanban/SKILL.md` y `jarvis-github-agentuse/SKILL.md`.

Tras crear o cambiar opciones de un SingleSelect, regenerar IDs:

```bash
bun run tools/kanban-cli/cli.ts config generate --project PVT_...
# Restaurar repoId y repo; luego config validate
```
