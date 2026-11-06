# Firefox debugging and render timeline

## Debug TypeScript in Firefox

1. Build the Angular and LSP client modules and restart AlpLibre IDE.
2. Select the Angular project or one of its files and click the orange fox button beside Run/Debug in
   the main **Run** toolbar. The fox and its dropdown are disabled for non-Angular projects.
   Its dropdown offers **Debug settings…**, **Restart session**, and **Stop Angular + Firefox**.
   Alternatively, right-click the Angular project and choose **Debug Angular in Firefox** (or Debug
   Project). With one serve application, Node.js, Firefox, and its adapter detected,
   first use launches directly using localhost:4200 and the development configuration
   when available. Otherwise the configuration dialog opens; later runs use saved settings.
   Use **Firefox Debug Settings…** to change these defaults.
3. Set the application URL and local web root. Select the Angular serve target and
   configuration. `development` is preselected when available; script source maps must
   be enabled in that build. Leave **Start Angular server and wait until ready** checked
   to start the selected app automatically without opening another browser. Startup
   waits up to two minutes for an HTTP response and reports compiler output in the
   session Output tab. If a server is already responding, choose **Use running server** in the startup
   prompt. That server stays running when the debug session stops. Cancel leaves it untouched.
4. The IDE discovers Firefox and the Mozilla Firefox debug adapter in existing VS Code
   / VSCodium extension directories. Otherwise choose `dist/adapter.bundle.js`, the
   complete extension directory, or import a VSIX using **Browse / Import VSIX…**. Keep
   neighboring files, including `mappings.wasm`, together. Change saved settings later
   using **Firefox Debug Settings…**.
5. Choose Launch. A unique temporary debugging profile is created under the IDE cache
   (`angular/firefox-profiles`), rather than the inherited OS temporary directory.
   Output prints its parent directory for diagnostics. No manual Firefox profile setup
   is required. The adapter removes its temporary profile after the session; `reAttach` and
   `keepProfileChanges` are false. The normal browser profile is not used directly.
   Launch also sets `MOZ_APP_DATA` and `MOZ_LOCAL_APP_DATA` for the adapter child
   process to `app-data` and `local-app-data` under that parent. This avoids accessing
   protected personal Firefox data on macOS 27, even when `-profile` is supplied.
   Attach preserves the environment. Output prints the isolated application-data path.
   Launch chooses an available loopback debugger port and prints it in Output, so
   a leftover Firefox listener on port 6000 cannot receive the new session's connection.
   The port in Debug Settings applies to Attach only.
6. Set a TypeScript gutter breakpoint and exercise the application. Use the IDE's
   Continue, Pause, Step Into/Over/Out, Variables, Call Stack and Watches views.
   Firefox may require a reload for startup breakpoints.
7. For unresolved breakpoints, check source maps and web root. Optional path mappings
   are a JSON array, for example:

   ```json
   [{"url":"webpack:///./","path":"${workspaceFolder}/"}]
   ```

   Here `${workspaceFolder}` means the chosen web root. Vite/esbuild and multi-project
   layouts can need different mappings; no bundler-specific URLs are guessed.
8. Stop the session using the debugger toolbar or **Stop Angular + Firefox** in its
   Output tab. The server, adapter and their child processes started by this session
   should exit. An external server, or a Firefox instance used via Attach, stays running.
   Stopping during startup or a startup failure also cleans up owned processes. IDE
   shutdown closes owned debug sessions. Check the project-specific **Angular Firefox**
   Output for compiler output, startup progress and adapter messages.

For Attach, manually enable Firefox remote debugging and start its debugger server;
choose Attach and its port (normally 6000). Use a dedicated development profile.
The UI's setup-guide button links to Mozilla's adapter documentation:
https://github.com/firefox-devtools/vscode-firefox-debug

## Observe component checks

1. Open **Angular Render Timeline…** from the Angular project menu.
2. Choose **Set up companion…** and save a new `alplibre-render-probe.ts` within the
   source tree. Existing files are never overwritten and components are not rewritten.
3. Pick a component in the resulting setup dialog. Copy its imports and probe field,
   then merge the supplied calls into that component's existing `ngOnChanges` and
   `ngDoCheck` methods. Keep existing lifecycle logic. Add optional `mark()` calls in
   event handlers to record your own contextual observations.
4. Set the exact application origin (scheme, hostname and port, without its route)
   and choose **Start receiver**. Copy the displayed connection command into Firefox's
   web console. Call it outside Angular's zone; otherwise a polling timer could cause
   the very checks being measured. The helper rejects an in-zone connection.
5. Change an input and trigger a check. The timeline should show the component's
   instance, previous/current primitive values, input changes and check events.
   Object values use reference IDs: getters are never traversed and in-place mutations
   are not observed. A check is not proof of a DOM update or a particular cause.
6. Filter a component or instance, pause the display, inspect event details and choose
   **Open component source**. Navigation opens the component declaration, not an
   inferred signal-write line.
7. If live posting is blocked by browser policy or CSP, run
   `window.alpRenderProbe.download()` in the browser and **Import trace…** in the IDE.
   Browser history is limited to 2000 events and pending transport to 500; this is a
   sampled development aid, not a lossless profiler. Import adds to the IDE timeline.
8. Stop the receiver or close the timeline, and call
   `window.alpRenderProbe.disconnect()` in the browser to stop retries. Instrumentation
   is inactive when Angular's `isDevMode()` is false, and never connects remotely.

## Automated and live validation

Run the targeted Java tests and the companion runtime test:

```sh
ant -f webcommon/angular/build.xml test-unit '-Dtest.includes=**/FirefoxDebugConfigurationTest.class,**/RenderTraceTest.class,**/RenderTraceReceiverTest.class'
ant -f ide/lsp.client/build.xml test-unit '-Dtest.includes=**/DAPProtocolTest.class' -Dtest-unit-sys-prop.java.awt.headless=true
node webcommon/angular/test/manual/render-probe-runtime.cjs
```

The Firefox adapter's initialize handshake was checked with installed adapter 2.15.0.
Java tests cover configuration, archive extraction, trace retention/source boundaries,
receiver request handling and optional DAP configurationDone behavior. The TypeScript
companion passes strict compilation against a minimal Angular API contract and runtime
checks with mocked Angular/browser APIs. A full Angular compilation and live Firefox
breakpoint/stepping/receiver session still require manual verification: the automated
execution environment rejects local socket binding.

## Browser component picker

1. Start an Angular development application in Firefox. Use **Angular Component
   Picker…** from the Angular project menu. No lifecycle companion or application
   code changes are needed for this picker.
2. Enter the exact application origin, then **Start connection**. Paste the displayed
   command once into Firefox's web console to load the session-local picker. If CSP
   blocks `eval`, use **Show full connection script…** and paste that script directly.
   Browser/CSP rules must still allow loopback fetches; otherwise this bridge cannot
   connect. It relies only on Angular's development inspection globals:
   https://angular.dev/api/core/globals/getOwningComponent
3. Hover in the browser to outline a component, then click to open its TypeScript
   declaration in the IDE. Picking consumes that click so the app's click action does
   not run. Escape cancels. Choose **Pick in browser** to select again.
4. The IDE table shows workspace components and source paths. Choose **Open template**
   to open an external template when available, or **Open source** for the component.
   Duplicate class names are disambiguated with the host selector where possible;
   unresolved duplicates require explicit source selection. Library/minified components
   without a local match are reported, rather than opening a guessed file.
5. Select a component in the IDE and choose **Highlight in browser**. Its first mounted
   matching instance is scrolled into view and outlined temporarily. If it is not mounted,
   Firefox's console reports that fact. Refresh the IDE component list after code changes.
6. Stop the connection or close the panel. The browser picker removes its listeners,
   outline and polling timer after three failed receiver polls. You can disconnect
   immediately with `window.alpComponentPicker.dispose()`. Production builds without
   Angular's inspection APIs are rejected.

Additional focused checks:

```sh
ant -f webcommon/angular/build.xml test-unit '-Dtest.includes=**/AngularDebugServerTest.class,**/FirefoxDebugSessionTest.class,**/BrowserCommandsTest.class,**/AngularComponentPickerTest.class,**/RenderTraceReceiverTest.class'
node webcommon/angular/test/manual/component-picker-runtime.cjs
```

These checks cover serve arguments, readiness failure/cancel/timeout, process ownership,
command bounds, source disambiguation, HTTP endpoint authorization and browser picker
behavior with a mocked DOM/Angular globals. Full one-click startup and real Firefox
click-to-source/highlighting still need verification outside the socket-restricted
execution environment.

## Toolbar and startup checks

- Select an Angular project or source file: the fox/bug toolbar button becomes enabled.
  With no Angular selection, it uses the main project if that is Angular.
- Launch with no server: Output shows starting, waiting, then opening Firefox.
- Launch with a responding server: choose Use running server; stopping debugging
  must leave that server running. Cancel must not launch Firefox.
- Use the dropdown to open settings, restart, or stop the selected project's session.
- Output reports "Firefox debugger connected" only after launch/attach and breakpoint
  configuration complete. Startup runs in the background. A failed handshake reports
  its error in Angular Firefox Output; a stalled connection times out within 35 seconds
  and stops the owned processes. Verify real breakpoints in the Debugger views.
