# Presentation Mode

Use **View → Presentation → Presentation Mode** to turn presentation tools on or off. This is one reversible workspace mode. The old Normal/Live Talk/YouTube entries have been removed from the View menu; their actions remain available in Keymap for existing assignments.

## Presenting

A compact control strip appears below the main toolbars, reserving its own space so it does not cover code or terminal output:

- **A− / A+** adjust the editor's target size in two-point steps (14–48 pt).
- **Explain code / Run a demo / Debug** select a panel layout.
- **Spotlight** toggles emphasis on selected code.
- **Show commands** toggles the fading command-name/shortcut overlay.
- **Code Walkthrough** opens the ordered sequence and presenter notes.
- **Previous / Next / Resume** navigate from the strip; the current point number and title remain visible.
- **Settings** opens configuration and live preview.
- **Exit** restores the normal workspace.

The strip scrolls horizontally in narrow windows, keeping Exit available. Main toolbars are not hidden, so Run and Debug remain accessible. Changes made with the strip are temporary; use Apply in Settings to save them to the selected preset.

## Layouts

| Layout | Open docked panels retained |
| --- | --- |
| Explain code | Editor focus; tool windows collapse |
| Run a demo | Output and Terminal |
| Debug | Output, Terminal, and recognized debugger views such as variables, call stack, watches, threads and breakpoints |

Layouts recognize panels by their window-system IDs. Third-party views with unfamiliar IDs may need to be reopened manually. Closed panels are not opened automatically. Panels opened during presentation are arranged for the selected layout. Manual changes remain until a layout is reapplied. Floating windows are left alone. Exit restores the original minimized state of each still-open docked panel, including panels temporarily expanded for a demo. This does not undo deliberate docking or floating changes made by the user.

## Settings and live preview

**View → Presentation → Presentation Settings…** offers separate editor and Terminal/Output target sizes. Editor size is measured against each language's configured default font; existing zoom is restored on exit. Output and Terminal retain their normal font family and style, and their preferences are not changed.

The optional presets are starting points, not separate modes:

| Preset | Editor | Terminal / Output | Layout | Command overlay |
| --- | --- | --- | --- | --- |
| Live Talk | 26 pt | 22 pt | Explain code | Bottom right |
| Recording | 22 pt | 20 pt | Run a demo | Bottom left |

Each preset saves its own sizes, layout, command-overlay toggle and corner. Earlier font-increase settings migrate to a target size using a 14-point baseline. Former toolbar-hiding settings are intentionally ignored.

Choose **Preview in workspace** to apply a draft to the actual workspace. The settings window is modeless; it can be moved while inspecting the code, terminal and panels. Subsequent control changes update the live preview. **Apply** saves the preset drafts and starts or updates Presentation Mode. **Cancel**, Escape or closing the settings window restores the earlier font zooms, panel states and presentation configuration. Merely opening Settings changes nothing. A preview that is active during IDE shutdown is cleaned up without reopening Presentation Mode.

Choose any command-overlay corner to avoid the part of the recording occupied by your webcam. The normal IDE layout reserves space for the strip; custom window layouts fall back to a floating strip with bottom command overlays raised above it.

## Spotlight and command labels

Select code and use **Spotlight**, or **View → Presentation → Spotlight Selection**. Surrounding text dims while the selection retains syntax colors. Toggle again to clear. Editing or switching editors clears the spotlight. It never changes source text. A shortcut can be assigned through Options → Keymap.

The command overlay shows supported IDE action names and available shortcuts for 1.7 seconds, then fades over 0.5 seconds. It does not intercept clicks or take focus. It reads only action metadata from NetBeans action notifications; ordinary typing, clipboard data and source contents are not displayed. Basic caret, selection and deletion actions are excluded. Some third-party actions do not emit these notifications. Actual shortcuts are used when available, otherwise configured accelerators; commands without known shortcuts show only their name.

Exiting removes the overlay and its observer, stops timers, and rejects stale callbacks from previous sessions. Temporary action-logger settings are restored while preserving more detailed logging or settings changed elsewhere. Existing IDE logging handlers retain their normal behavior. No command history is stored by this feature.

## Code walkthrough

Open **View → Presentation → Code Walkthrough…**, or **Code Walkthrough** in the presentation strip. This modeless console shows the ordered code points, their target files, titles and presenter notes.

- **Add current code…** captures the last focused source editor, including a selection if requested.
- Edit a point's title and notes (up to 4000 characters) and choose **Save details**. Valid edits are also saved when changing points, navigating, or closing the console.
- **Jump to point** or double-click a row to open its destination and restore its optional Spotlight range.
- **Previous / Next** follow the prepared order. They do not wrap at the ends.
- Explore other files or edit code freely. Your place remains unchanged; **Resume walkthrough** returns to the current point (or starts at the first point if not started).
- **Reorder / remove…** opens the existing sequence editor. **Validate Code Points** validates destinations before presenting.

The console follows successful navigation and shows the current point's notes. The strip and status bar show progress, such as “2 of 4 · Spring controller.” Notes belong to the separate console window; keep that window out of the captured area when recording or sharing. There is no separate Audience Window yet.

Navigation opens files in the background, chooses a visible matching editor rather than assuming the first split pane is usable, positions the code with context above it, and applies Spotlight directly without waiting for editor-registry focus. Nested editors are supported when their documents identify a source file. Failed jumps leave the current point unchanged. Current sequence position is session-only; titles, destinations, order and notes persist.

## Code points

Code-point management lives in **Code Walkthrough**, including adding, reordering,
removing and validating destinations. The separate Demo Checkpoints menu has been
removed. Existing navigation shortcuts and stored sequences remain compatible.

Defaults are Ctrl+Shift+Page Down/Up on Linux and Windows, and Command+Shift+Page Down/Up on macOS. Customize them in Options → Keymap. Navigation stops at either end instead of wrapping. Failed jumps do not advance your place. The status bar shows the stop's number and name.

**Reorder / remove…** supports rename, reorder and remove. Changes are staged until OK. Saving a changed list restarts navigation from the beginning.

Up to 100 stops persist in IDE preferences. File references store a project-directory URL and relative path when possible, otherwise the containing folder and filename. Positions follow edits in open documents and are saved on navigation and normal shutdown. Reopened files use saved offsets clamped to their length; this is positional navigation, not semantic symbol tracking. Renaming or moving files/projects may invalidate stops; Validate Code Points reports this. Loading and checks run in the background, and pending work is cancelled during shutdown. No source or Run/Debug state is changed.

## Validation

Run the presenter module tests:

```sh
ant -f ide/presenter/build.xml test-unit \
  -Dtest.run.args='-ea -Djava.awt.headless=true --add-opens=java.base/java.net=ALL-UNNAMED'
```

Run the Output font regression test:

```sh
ant -f platform/core.output2/build.xml test-unit \
  -Dtest.includes='**/PresenterFontTest.class' \
  -Dtest.run.args='-ea -Djava.awt.headless=true --add-opens=java.base/java.net=ALL-UNNAMED'
```

Tests cover target-size zoom and restoration, preset persistence/migration, layout classification, spotlight isolation, checkpoint persistence and live positions, metadata-only command parsing, typing exclusion, observer cleanup, fade rendering, corner placement, resize handling, click-through behavior and stale-session rejection. Output tests cover target-size rendering and restoration without changing normal settings. Terminal is built separately.

Interactive release checks remain: actual point sizes across languages, preview Apply/Cancel, main-menu simplification, strip controls at narrow widths, mixed docked/floating panel restoration, debugger view IDs, checkpoint navigation, IDE restart, and macOS/Linux/Windows font rendering.

Walkthrough regression tests cover notes persistence and independent drafts, resume/next/previous destinations, hidden-first split panes, focus-independent pane selection, and direct Spotlight activation before registry focus catches up. Live IDE verification remains required for actual window focus, code positioning and the modeless console on each platform.
