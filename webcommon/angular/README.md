# Angular workspace support

## Build and launch NetBeans

From the repository root, build and launch the IDE with one command:

```sh
ant run-angular
```

To build without launching, use `ant build-angular`. These shortcuts use the
normal incremental IDE build, including Angular support, and skip ZIP packaging.
They require the same JDK and Ant setup as the regular NetBeans build.

## RxJS Guide and Pipeline Explorer

Open **Window > RxJS Guide and Pipeline Explorer**. The dockable window provides:

- A searchable offline reference for 22 RxJS operators and Angular interop APIs,
  with examples, conceptual value flow, pitfalls and official documentation links.
- **Explain selection / file**: select a complete `.pipe(...)` expression in a
  TypeScript editor, then click the button. Without a selection, it reads the
  current editor buffer, including unsaved changes.
- Review suggestions for nested subscriptions, cancellation, error recovery,
  replay caching and component subscription ownership.
- **Stream comparison**: a deterministic timeline comparing `switchMap`,
  `concatMap`, `mergeMap` and `exhaustMap` with identical input events.
- **Marble testing**: edit a source timeline and choose `map`, `filter`, `take(2)`
  or `distinctUntilChanged` to see colored input/output marbles and a generated,
  copyable `TestScheduler` test. Numeric labels show the values behind each
  marble (`a=1`, `b=2`, etc.). Completion, errors and same-frame completion are
  shown explicitly. Input accepts up to 80 characters using `-`, `a`–`z`, and a
  final `|` or `#`; source groups and time-progression syntax are not supported.
  Tests use Jasmine/Jest-style assertions; adapt them to your test framework.
- Documentation uses theme-aware heading, value-flow, code and caution colors.

In a TypeScript editor, right-click an operator and choose **RxJS Operator
Documentation** to open its reference entry. The editor documentation query also
provides offline RxJS documentation in Angular workspaces that import RxJS.
Example buttons copy code; they do not change your source or insert imports.

The explorer is a syntax-based educational aid. It recognizes conventional,
unaliased operator calls; custom operators, aliases, regular-expression literals
and template interpolation expressions are not resolved. Suggestions require
review and are not compiler diagnostics. Value-flow labels are conceptual,
not inferred TypeScript types. The comparison is a teaching simulation, not a
live runtime debugger. Automatic refactoring, semantic type overlays and runtime
emission capture are not included in this implementation.

To package this module incrementally, then restart the development IDE:

```sh
ant -f webcommon/angular/build.xml netbeans
```

## Full Stack Connections (Angular and Spring)

Open **Window > Full Stack Connections** to inspect Angular HTTP calls and
candidate backend routes across open projects. Open both the Angular workspace
and Spring project, let Java indexing finish, save your TypeScript files, then
click **Refresh**. The backend does not need to be running.

### Build and enable the view

From the repository root:

```sh
ant -f enterprise/micronaut/build.xml netbeans
ant enable-fullstack
```

Close and restart the development IDE, then open **Window > Full Stack Connections**.
The first command packages the endpoint provider and its Full Stack Connections dependency; the second enables both in
`nbbuild/testuserdir`. No full IDE rebuild is needed for this module update.

The view lives in the independent Full Stack Connections module. The existing
Micronaut/Spring endpoint index supplies routes through a provider interface.
NetBeans feature-on-demand configuration can disable these modules.
`enable-fullstack` writes enabled module entries to the development profile,
overriding the disabled entry. Run it again if you recreate that profile.
For an IDE launched with another user directory, use its absolute path:

```sh
ant enable-fullstack -Dfullstack.userdir=/absolute/path/to/your/userdir
```

This command changes only the selected development profile; it does not activate
the module in other installations or packaged application profiles.

### Use the connections table

- Search by URL, source file, HTTP method, or status with **Filter**.
- Select **Unresolved only** to focus on calls without a matching indexed route.
- Double-click the Angular column to open the call, or the backend column to open
  the endpoint. The counter shows visible rows versus total rows.
- **Candidate** means the HTTP method and path match. **Ambiguous** means several
  routes match; **Unresolved** means no indexed route matches. Candidates are
  static suggestions, not proof that the runtime request reaches that endpoint.

The initial scanner recognizes conventional `http` and `httpClient` calls with
quoted URLs. Whole path segments such as `/api/products/${id}` and
`/api/products/${product.id}` can match Spring's `/api/products/{id}`.
Dynamic base URLs, proxy configuration, complex expressions, and DTO compatibility
are not resolved. The scanner reads saved files; comments and unconventional
HttpClient variable names can affect results.

## Create an Angular application

Choose **File > New Project > HTML5/JavaScript > Angular Application**.
Enter a project name and an existing parent location, then choose stylesheets,
routing, server-side rendering, and whether to install npm dependencies.
The wizard displays the detected Angular CLI version with a Refresh button and CLI path tooltip.
Optionally select **Add Angular Material** and a **Material theme** (CLI default or
one of the four Material 3 prebuilt themes for Angular 18+). Material enables npm
dependency installation and runs its setup schematic after creating the application,
using the generated Angular major version to select a compatible Material release.
Click **Finish** to generate and open the application. Creation runs in the
background with CLI output and a Stop action in the Output window.

Install Node.js and Angular CLI first (`npm install -g @angular/cli`), or configure
their paths under **Tools > Options > HTML/JS > Angular**. The wizard uses that CLI
version. It creates a standalone application with strict checks and does not initialize
Git. The destination may be new or an existing empty writable folder. If generation fails or is stopped,
partial files are kept for inspection; empty destinations can be retried; nonempty destinations remain protected.

## Open an Angular application

Open the folder containing `angular.json` with **File > Open Project**.
The project menu keeps Run, Debug, Build and Test directly accessible.
**Generate** groups Angular code generators. **Angular Tools** contains Routes,
Firefox debug settings and Angular settings. Render Timeline and Component Picker
are marked experimental inside that submenu.
The Firefox toolbar button explains which project to select when disabled.
Language-server startup failures offer an Angular settings link; details remain
in the IDE log.
Workspaces with several projects prompt for the project to use.
Run starts `ng serve --open`; Test runs `ng test --watch=false`.
Test File is supported for a selected `.spec.ts` or a companion component file.
Commands run in the workspace directory and appear in the Output window, where they can be stopped.
Files are saved before project actions and refreshed when commands finish.

## Setup

Install the workspace dependencies using its package manager before invoking Angular commands.
The module does not install dependencies automatically.

Under **Tools > Options > HTML/JS > Angular**, configure Node.js, the Angular CLI,
and the Angular language server, or leave their paths empty for automatic discovery.
For the CLI, select `node_modules/@angular/cli/bin/ng.js`; Windows `.cmd` wrappers are not supported.
The local workspace CLI is preferred over global installations.
Language server probe locations can be supplied as comma-separated directories.
Paths can also be overridden with the `angular.node.path`, `angular.ng.cli.path`, and
`angular.ngserver.path` JVM properties.

Editor language features require an installed `@angular/language-server` compatible
with the workspace, and resolvable TypeScript and Angular language service packages.
The module connects it to TypeScript and HTML editors only for Angular projects.

## Validation

From the repository root:

```sh
ant -f webcommon/angular/build.xml test-unit
ant build
```

Regression tests cover multi-project parsing, source roots, project recognition,
selector offsets, command arguments with spaces and shell characters, and Windows wrappers.

Manual acceptance checks after launching the built IDE:

1. Open a workspace and confirm its project menu and source files appear.
2. Set paths in Angular settings; Apply and reopen to confirm persistence.
3. Edit a component and build; confirm saved changes are compiled and errors appear in Output.
4. Run the app, open it in a browser, then stop it from Output.
5. Generate a component and confirm new files appear in the project tree.
6. Run all tests and a single spec; check the CLI exit status and output.
7. In a multi-project workspace, build and test each selected project.
8. Confirm template diagnostics, completion, and navigation with the language server installed.

Test Results window integration, Component Explorer, and Java–Angular
navigation/refactoring are outside this milestone. CLI target availability and flags depend
on the builders configured in `angular.json`.

## Generate components and templates

Use the project menu **Generate Component**, or **New File > Angular > Angular Component**.
Both open the same CLI-backed wizard and generate a coherent component with its
companion template, stylesheet and tests. The selected project and workspace-relative
folder are explicit, including in multi-project workspaces. New File preselects the
selected folder and its Angular subproject where possible.

Keep **Workspace default** to honor workspace/project schematic settings and CLI
version conventions. Override HTML and styles as inline or separate files, stylesheet
format, tests, standalone/NgModule, OnPush change detection, and subfolder layout.
**Preview Files (dry run)** reports the actual proposed files in Angular Output
without writing them. Finish generates files and opens the new component source.
Existing files are protected by the CLI; generation never passes `--force`.

The standalone **Angular Component Template** file template provides semantic starter
HTML without depending on an undeclared component property. To generate a wired
component and external HTML together, use the Angular Component wizard.

### Angular icon attribution

The Angular icon is the unmodified 16px favicon from Google’s Angular project:
https://github.com/angular/angular/blob/main/adev/src/assets/icons/favicon-16x16.png

Angular logo: Google / Angular contributors, licensed under CC BY 4.0 as described
in https://angular.dev/press-kit (https://creativecommons.org/licenses/by/4.0/).
Angular is a trademark of Google LLC.

## Firefox debugger and component check timeline

Use **Debug Angular in Firefox** to launch or attach through Mozilla's Firefox debug adapter
and the IDE's DAP debugger. The orange fox button sits beside Run/Debug in the main
Run toolbar and is enabled only for the selected Angular project (or the main Angular
project when no project is selected). Its dropdown contains settings and session controls.
Existing adapter installations are discovered; a downloaded
VSIX can also be imported. On first use select a serve target, configuration and URL;
later runs start the selected Angular app, wait until it is ready, and launch Firefox.
The session Output stop action cleans up only owned processes. You can disable automatic
startup to use an external server. Enable script source maps in the development build. Debugging works on local TypeScript sources with
editable URL-to-file mappings.

**Angular Render Timeline…** provides an opt-in, development-only companion, a local
receiver, bounded event history, filtering and component source navigation. It records
input changes and lifecycle checks, with optional developer marks; it does not infer
signal causality or equate a check with a DOM render. No component source is rewritten.

See [setup and acceptance checks](test/manual/firefox-debugging.md).

**Angular Component Picker…** connects Firefox DOM selection to workspace component
source and external templates, and highlights a selected component back in the browser.
Paste a session connection script into Firefox's console once; no app source changes or
browser extension are required. Angular's development inspection APIs must be available.

### Firefox icon attribution

The Firefox browser icon is Mozilla’s official artwork from
[about-logo.svg](https://github.com/mozilla-firefox/firefox/blob/main/browser/branding/official/content/about-logo.svg).
Firefox and its logo are trademarks of the Mozilla Foundation. Only the SVG display
size and viewBox are adapted for the IDE toolbar; the artwork is unchanged.

### Material completion imports

Accepting a `mat-*` HTML completion also adds its TypeScript import and registers
it in the owning standalone component's literal `imports` array. When the installed
Material declarations expose a single NgModule for the entry point, that module is
preferred (for example, `MatCardModule`); otherwise the component declaration is used.
Existing imports, aliases, and array entries are preserved without duplicates.
The TypeScript edit remains unsaved and can be undone in its editor.

The initial implementation resolves external templates referenced by a TypeScript
file in the same folder. Ambiguous owners, `standalone: false`, computed imports,
and complex metadata are left for manual editing, with a status message. Existing
unknown tags can be completed again using Ctrl+Space inside the opening tag.

### Signals completion guidance

TypeScript language-server completions with signal types show writable or read-only badges. The documentation popup explains signal reads, `set()` and `update()`, with TypeScript and template examples. Angular `signal` and `computed` auto-import entries also receive introductory examples. This changes presentation only; server insertion edits and imports remain authoritative. A value typed as `Signal<T>` is labeled read-only because its type does not reveal whether it was created by `computed()` or `asReadonly()`.

### Signals inspections

In Angular TypeScript editors, inline hints identify bare signal functions in `if`/`while` conditions, `.set()`/`.update()` on locally declared computed signals, and simple effects that copy one signal into another. Alt+Enter offers **Read the signal value with ()** for the condition; the fix checks that the source has not changed since inspection. Derived-state effects receive guidance to consider `computed()` rather than an automatic rewrite.

These are conservative local inspections of `this.field` references and Angular core imports (including aliases), not a replacement for TypeScript checking. They currently skip files with multiple classes or traditional nested functions, and do not inspect external templates, inherited signals, or arbitrary signal references passed between functions.
