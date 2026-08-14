# Guía de contribución — Personal Bar

¡Gracias por tu interés en contribuir! 🍸 Este proyecto es mantenido principalmente por **jaminsmoke** con ayuda de agentes de IA. Tu contribución — humana o asistida por IA — es bienvenida.

Antes de empezar, lee el [README](README.md) y [`AGENTS.md`](AGENTS.md): este repo es el **puesto del negocio** (barra + nodo de sala LAN). La app del camarero vive en Personal Comander; Identity es el registro canónico de identidades. No conviertas Bar en un Commander «modo barra».

## Cómo empezar

1. **Crea un fork** del repositorio.
2. **Crea una rama** desde `main` con un nombre descriptivo:
   - `feature/<descripción>` para nuevas funcionalidades
   - `fix/<descripción>` para correcciones
   - `chore/<descripción>` para tareas técnicas
   - `infra/<descripción>` para infraestructura (CI, docs del repo, GitHub)
3. **Abre un Pull Request** contra `main` cuando tu cambio esté listo.

## Issues

- Antes de abrir un issue, busca si ya existe uno similar.
- Usa títulos descriptivos y explica: qué ocurre, qué esperabas, pasos para reproducir y versión/dispositivo.
- **No reportes vulnerabilidades de seguridad aquí**: consulta [SECURITY.md](SECURITY.md) para el proceso de reporte privado.
- Los cambios que siguen el flujo kanban interno (proyecto GitHub) se gestionan aparte; los issues públicos son bienvenidos igualmente.

## Convenciones de commits

Usamos **Conventional Commits**:

```
feat(ui): añade la expo de colas por destino
fix(lan): corrige el crash del FGS tras pm clear
docs(repo): landing y sitio de la familia
chore(build): actualiza dependencias
```

Tipos habituales: `feat`, `fix`, `docs`, `chore`, `refactor`, `test`, `perf`, `build`, `ci`.

## Requisitos de calidad

Antes de abrir un PR, verifica localmente:

```bash
./gradlew assembleDebug   # compila
./gradlew test            # tests unitarios
./gradlew lint            # lint
```

Para cambios de documentación/sitio:

```bash
python scripts/check_docs_links.py   # enlaces locales de README y docs
mkdocs build --strict                # build del sitio
```

## Documentación

- El **README** es la landing del producto; la **wiki** es el manual técnico (nodo LAN, colas, lista blanca, contrato).
- El **sitio** (`jaminsmoke.github.io/PersonalBar`) se despliega solo con el workflow `docs.yml` al llegar a `main`.
- Las capturas y assets se regeneran con `scripts/capture_screens_bar.sh` y `scripts/generate_assets_bar.py` (ver README).

## Kanban

El trabajo se planifica en el [kanban del proyecto](https://github.com/users/jaminsmoke/projects/11) con el flujo Detectado → Debate → Roadmap → Ejecutando → Verificando → Changelog (detalle en `AGENTS.md`). No saltes fases ni conviertas drafts en issues antes de Ejecutando.
