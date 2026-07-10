# Xeno (26.2 Alpha)

Xeno is a rendering optimization mod for Minecraft 26.2

## Architecture


* **Asynchronous Occlusion :** A dedicated background thread isolates macro-occlusion graphs, bounding box evaluations, and frustum culling visibility checks, ensuring the render thread never stalls on geometry evaluation.
* **Custom Chunk Meshing :** Overtakes Vanilla chunks meshing.

## Repository Branch Layout

This repository maintains three operational tracks:

* `dev`: The default branch. Contains current experimental ports, active alpha revisions, and daily optimization passes.
* `26.2 stable`: Production branch reserved for thoroughly verified, stable releases on the 26.2 modding ecosystem.
* `26.1.2 stable`: Production branch tracking mature optimization sets for the 26.1.2 game version.

## Environment Requirements

* **Minecraft Version:** 26.2
* **Java Environment:** Java 25
* **Environment:** Client-side only