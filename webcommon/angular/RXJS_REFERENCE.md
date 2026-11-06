# Maintaining the RxJS reference

The editor completion popup, documentation, and RxJS guide share one bundled offline catalog.
No Java changes are needed to edit content or add a reference entry.

## Content files

- `src/org/netbeans/modules/angular/rxjs/reference/operators.xml`: operator names, categories,
  summaries, behavior, conceptual type flow, examples, cautions, official URLs, parameters,
  and optional marble examples.
- `src/org/netbeans/modules/angular/rxjs/reference/mapping-comparison.html`: shared mapping
  operator comparison. `{{timeline}}` placeholders are rendered as colored marble timelines.

## Editing or adding an operator

Copy an `<operator>` entry and choose a unique `name`. Required fields are `category`, `summary`,
`behavior`, `types`, `example`, `caution`, and `url`. Optional fields are `parameters`,
`marble` (with `source`, `result`, and `expression`), and `<comparison>mapping</comparison>`.
Use category `Creation` for creation functions, which are excluded from pipe-operator suggestions.

Use UTF-8 text and normal newlines in examples. XML requires `&lt;` for `<` and `&amp;` for `&`;
CDATA sections are also supported for code examples. Content fields are plain text, escaped by
the renderer; do not put HTML in them. Keep summaries short because they appear in completion rows.

For marble data, `-` is a frame, letters identify values, `|` is completion, and parentheses
represent simultaneous events. These are teaching examples, not runtime observations.

Verify semantics against the linked official RxJS or Angular documentation when updating entries.
Keep examples, parameter descriptions, and cautions consistent with the API version being described.
The loader rejects missing required fields, duplicate names, unsupported schema versions,
unknown comparison groups, and documentation URLs outside the official HTTPS domains.

## Rendering and loading

`RxjsReference.java` loads and validates the resources once from the module classpath.
`RxjsOperators.java` renders the shared data. `RxjsDocumentation.java` owns theme styling.
The catalog has no network dependency and ships inside the Angular module JAR.

After edits, run from the repository root:

```sh
ant -f webcommon/angular/build.xml test-unit '-Dtest.includes=**/rxjs/*Test.class'
```

Rebuild the Angular module and restart the development IDE to load updated resources.
