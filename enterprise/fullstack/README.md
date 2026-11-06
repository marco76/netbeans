# Full Stack Connections

Open **Window → Full Stack Connections** to inspect saved frontend HTTP calls and
candidate indexed backend routes. This module owns the view, client-call scanner,
route matching and source navigation. It has no Angular or Micronaut dependency.

Backend integrations implement `org.netbeans.modules.fullstack.spi.EndpointProvider`
and register it with `@ServiceProvider`. The Micronaut module adapts its existing
Spring/Micronaut index; the view does not access index implementation classes.
The existing action ID and window ID are retained for shortcut and layout compatibility.

Build the integration with `ant -f enterprise/micronaut/build.xml netbeans`.
The view and Spring/Micronaut provider are enabled by default, including in fresh
profiles; the feature-on-demand build excludes both from its disabled overrides.
For existing development installations, use `ant enable-fullstack` to override
older disabled configuration entries.
Restart the running IDE after updating modules.

## Responsibilities

| Module | Owns |
| --- | --- |
| `webcommon/angular` | Angular project detection, CLI, language-server setup, generation and Firefox tools |
| `ide/presenter` | Temporary presentation workspace, spotlight and code walkthroughs |
| `enterprise/fullstack` | Framework-independent connection view, matching and endpoint provider contract |
| `enterprise/micronaut` | Existing Spring/Micronaut endpoint indexing and its provider adapter |
| `java/spring.beans` | Spring bean model, Java completion, navigation and Spring wizards |
| `java/maven` and `java/maven.hints` | Maven setup, POM changes and related quick fixes |
| `platform/o.n.swing.laf.flatlaf` | Shared control spacing, typography and theme colour defaults |

Keep framework actions in their project context. Group specialist inspection tools
beneath Angular Tools, and manage code points inside Code Walkthrough. New UI
components should inherit theme fonts and colours instead of defining a parallel
palette. The presentation command overlay uses tooltip theme colours.

Review changes by these module boundaries. Keep branding, editor appearance,
framework behaviour and experimental tools in separately reviewable changes;
avoid mixing unrelated fixes into a feature patch.

## Validation

`ant -f enterprise/fullstack/build.xml test-unit` checks literal paths, HTTP methods,
path variables, template segments and unresolved base URLs. Test the index adapter
with `ant -f enterprise/micronaut/build.xml test-unit`. Live IDE checks are still
needed for module activation and source navigation.
