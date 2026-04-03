# Release Management Logic (Make Release)

The pipeline now supports a dual-mode execution on the `master` branch: **Full Release Cycle** and **Development/Preview Mode**.

## Overview

When you open "Build with Parameters" on the `master` branch, you will see a new checkbox: **Make Release**.

### Mode 1: Make Release = ON (Default)
This is the standard production-ready flow.
* **Goal**: Prepare an official version and create a Git tag.
* **Stages**: Run Tests -> Code Style Check -> Build Image -> Push Release Image -> Create Tag.
* **UI Behavior**: To prevent configuration errors, the **Build parameters** and **Deployment environment** selections are **hidden**. The pipeline follows a strictly predefined sequence.
* **Versioning**: You must select the `Release Type` (PATCH, MINOR, or MAJOR) to increment the semantic version.

### Mode 2: Make Release = OFF
This mode allows you to treat the `master` branch like a feature branch.
* **Goal**: Test current master code or deploy to non-prod environments without creating a formal release/tag.
* **Stages**: The **Build parameters** list becomes visible. You can manually select:
    - Build application
    - Run tests
    - Run code style check
    - Deploy application
* **Deployment**: The **Deployment environment** selector becomes available (e.g., `preprod`, `test`).
* **Git Tag**: No tag will be created.

---

## Interaction with Parameters

| Parameter | Impact | UI Changes |
| :--- | :--- | :--- |
| **Reload** | Updates pipeline script | Hides all other parameters |
| **Make Release** | Triggers full release flow | Hides manual stage selection and deployment options |

> **Note**: If you don't see the checkbox list for stages, ensure that neither `Reload` nor `Make Release` is checked.