# Context Management Rules

## Simple Tasks
- Read only files required for the requested task.
- Do not read `AGENTS.md` unless relevant.
- Do not load large or unrelated files.
- Avoid repository-wide exploration.

## New Phase Implementation
1. Read `AGENTS.md` first.
2. Create `PHASE_<N>_GETTING_STARTED.md` before implementation.
3. Read only files required for the current phase.
4. Do not load the entire repository.
5. Implement and test the phase.
6. Create `PHASE_<N>_COMPLETE.md` after successful completion.
7. Update `AGENTS.md` with the phase status, decisions, changed components, validation, known issues, and next-phase context.

## File Access
- Read only files explicitly mentioned, referenced by project context, or necessary for implementation/testing.
- Treat unrelated files as reference-only.
- Prefer targeted reads and relevant sections over entire large files.

## Token Optimization
- Keep context limited to the current task/phase.
- Do not reread information already captured in `AGENTS.md` or phase documents.
- Avoid unnecessary searches, scans, and file loading.