# Angular template intelligence and quick fixes

Requires the updated Angular and LSP client modules, installed project dependencies,
and a running compatible `@angular/language-server`. Configure its path under
Angular Settings when it cannot be discovered. These interactive checks have not
yet been run; automated tests cover expression boundaries and deferred completion edits.

1. Open an Angular standalone project with a component exposing `name`, `users`,
   and `save()`, plus a child component with an input and an output.
2. In its external HTML template, type `{{ na`, `[title]="na`, and
   `(click)="sa`. Check automatic suggestions and Ctrl+Space. Repeat inside
   `@if (us` and `@for (user of us`. Suggestions must come from the component's
   Angular compilation context, including accessible inherited members and pipes.
3. Navigate from `name`, `save`, a pipe name, a child selector, and input/output
   names using the IDE's declaration hyperlink gesture. Each should navigate to
   the server's target. Native `href` links and HTML attributes must retain HTML
   navigation; comments and ordinary text must not become Angular hyperlinks.
4. Choose a completion whose server response resolves import edits. Confirm the
   completion and all extra imports are inserted, including any completion command
   supplied by the server. Undo and check the resulting source.
5. Remove a standalone child from a consuming component's imports. Wait for the
   unknown-element diagnostic and press Alt+Enter. When the server offers an import
   action, select it, read the explanation, and Apply. Check TypeScript imports and
   component metadata, then confirm the diagnostic disappears.
6. Repeat with `[formGroup]` and `ReactiveFormsModule`, and with a missing pipe.
   Availability of individual import fixes depends on Angular's server version and
   the application's NgModule/standalone compilation scope. No guessed imports
   should be offered by the client when the server supplies none.
7. Reference a nonexistent component property or method in HTML. Alt+Enter should
   include guidance about spelling, template scope, and the receiver's type. If
   Angular supplies a create-property/method action, apply it and set a meaningful
   type and implementation. For `user.missing`, verify guidance does not suggest
   creating `missing` directly on the component.
8. Cancel an explained action and verify source is unchanged. Verify disabled server
   actions are absent. Ordinary HTML outside an Angular workspace must retain its
   existing completion and navigation behavior.
