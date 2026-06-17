# AI Integration

KRender is already prepared for AI-assisted development. The repository provides structured context files so coding agents can understand the architecture, module layout, and tool boundaries before making changes.

## Main Entry Point

The primary agent-facing document is:

- `AGENTS.md`

This file explains:

- the overall project structure
- the backend-neutral core and LibGDX backend boundary
- module responsibilities
- engine lifecycle and rendering flow
- coding rules, logging rules, UI rules, backend rules, and testing expectations

In practice, `AGENTS.md` acts as the main orientation guide for an AI coding agent entering the repository.

## Focused Context Files

The repository also contains deeper context under:

- `docs/agents/`

These files break the project into focused areas such as:

- core engine runtime
- rendering pipeline
- asset system
- backend abstraction
- UI system
- logging

There is also a tool-specific folder:

- `docs/agents/tools/`

It contains one context file per editor tool, so an agent can read only the parts relevant to the task it is working on.

## Why This Helps

This documentation structure helps AI agents work more safely and accurately because it:

- gives a clear architectural map before code changes begin
- explains rules that are easy to violate accidentally, such as the backend boundary
- points agents to the exact tool or subsystem they should read first
- keeps project-specific conventions close to the codebase rather than relying on generic assumptions

## Typical Workflow

A typical AI-assisted workflow in this repository is:

1. read `AGENTS.md`
2. open the relevant subsystem doc in `docs/agents/`
3. if the task touches a tool, read the matching file in `docs/agents/tools/`
4. make the code change
5. update documentation if the architecture or behavior changed

## User-Facing vs Agent-Facing Docs

This MkDocs site is intended to stay user-friendly and SDK-oriented.

- user-facing pages explain the SDK, tools, quality checks, and general project structure
- agent-facing files stay in `AGENTS.md` and `docs/agents/` as internal context for AI coding workflows

That split keeps the public documentation easier to read while preserving the rich internal context needed for automated or assisted development.
