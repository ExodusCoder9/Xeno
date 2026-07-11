# Contributing to Xeno

Thanks for looking into contributing. Xeno is currently focused on  optimization paths for Minecraft 26.2.

Before opening a pull request or rewriting a system, look through the guidelines below to make sure the code aligns with the engine's current goals and licensing requirements.

## Licensing Agreement

By contributing code to this repository, you agree that all submitted modifications, patches, or new features will be licensed under the PolyForm Shield License 1.0.0.

Ensure you review the noncompete conditions outlined in the project root LICENSE file before submitting work. Contributions that do not align with these source-available parameters cannot be merged.

## Branch Workflow

* All active development, patches, and feature additions must target the `dev` branch.
* Do not submit pull requests directly to the `26.2 stable` or `26.1.2 stable` branches. Those tracks are strictly reserved for frozen, verified builds.

## Code Style & Performance Standards

Xeno runs on  rendering paths where object allocation spikes cause  and latency issues. Keep code direct and performance driven:

* **Zero Allocation on Render Paths:** Avoid allocating objects inside loops or execution blocks that run every frame or per-chunk tick. Utilize primitive arrays, primitive collections (like Fastutil), and pooled `ThreadLocal` structures instead of instantiation.
* **Keep Code Clean and Uncluttered:** Minimize inline code comments as much as possible. Write clear, self-documenting method names and logic rather than filling files with dense prose blocks.
* **Package Integrity:** Always include precise package declarations and fully qualified imports. Do not use wildcard imports.
* **Bypass Wrapper Overheads:** Where possible, bypass heavy object-oriented virtual method calls on hot paths in favor of direct array mapping or primitive lookups.
* **Use of Deprecated API's:** The use of Deprecated Java APIs are prohibited , especially the use of Sun.Misc.Unsafe. 

## Opening Pull Requests

1. Fork the repo and create your branch from `dev`.
2. Keep your PR focused on solving a single problem or optimizing a specific subsystem. Mass rewrites covering multiple unrelated files are difficult to review and test for regressions.
3. Ensure the project builds successfully locally using `./gradlew build` before submitting.
4. Provide a clear description of what your change does and include any before/after frame time metrics if applicable.