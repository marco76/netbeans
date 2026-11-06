# Educational diagnostic checks

These scenarios require a running IDE with the updated Angular, LSP client,
Maven, and Micronaut modules. They supplement the automated tests; they have
not been verified interactively.

## Angular template scope

Use a standalone Angular application with its matching Angular language server
running. Wait for a template diagnostic, then open its quick-fix menu (Alt+Enter).
The menu should include **Explain this diagnostic...**. Its dialog should show
cause, inspection guidance, expected result, and official documentation.

- **Missing pipe (NG8004 / -998004):** put `{{ 'hello' | uppercase }}` in a
  component template without importing `UpperCasePipe` or `CommonModule` in
  that component's metadata. The explanation should distinguish a TypeScript
  import from a template-scope import.
- **Unknown element (NG8001 / -998001):** create a standalone child with selector
  `app-child`, use `<app-child />` in a parent, and omit the child from the
  parent's imports. The explanation should cover selector spelling and imports.
- **Unknown binding (NG8002 / -998002):** use `<input [ngModel]="'Ada'">` without
  importing `FormsModule`. The explanation should cover missing directives and
  misspelled inputs.

When the server offers a fix for one of these diagnostics, selecting it should
show the explanation and the server's action title. Cancel must leave source
unchanged; Apply should run that server action. Recheck the diagnostic after
applying. If the server supplies no fix, only inspection guidance is offered.
An unrelated TypeScript error and an HTML file outside an Angular workspace
should retain their existing quick-fix behavior.

## Unresolved Spring annotation

- In a Spring project with Spring Context on the compile classpath, remove the
  import for `@Service`. On the unresolved annotation, choose **Explain unresolved
  Spring annotation...**. The explanation should say the type is available and
  suggest inspecting the import or using the existing Java import fix.
- In a minimal Java project, use the fully qualified annotation
  `@org.springframework.stereotype.Service` without the Spring Context dependency.
  The explanation should suggest checking dependencies, scope, downloads, and
  project reload. It must not insert a dependency or select a version.
- An unresolved `@example.Service` must not receive Spring guidance. An error in
  an argument to a resolved Spring annotation must not be described as an
  unresolved annotation.

## Spring Boot application class

In a Maven Boot project with no resolved configured main class, invoke a Run
mapping containing `${packageClassName}`. If needed, configure an unresolved
`start-class` property so the main-class chooser opens.

Expand **Why does Spring Boot need an application class?**. It should explain
which class to choose, session versus permanent action mappings, and the expected
result. Cancel should preserve the configuration. Selecting a main class should
retain the existing chooser behavior. A plain Java Maven project without the
Boot plugin or a Boot goal should not show the Boot explanation.
