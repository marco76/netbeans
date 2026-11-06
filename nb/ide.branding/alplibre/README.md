# AlpLibre IDE branding

AlpLibre IDE Preview is an independently modified distribution based on Apache NetBeans.
Apache NetBeans is a trademark of The Apache Software Foundation. This distribution
is not affiliated with or endorsed by The Apache Software Foundation.

The original LICENSE, NOTICE and third-party notices remain applicable and must
ship with distributions. Internal module names, branding token `nb`, launcher
filenames and resource filenames are retained for upstream compatibility.

`logo.png` is the original generated Alpine mountain / code-bracket artwork.
AlpLibre branding additions and artwork are provided under Apache License 2.0.
The name is provisional and has not undergone trademark clearance.

Run `swift -module-cache-path /tmp/alplibre-swift-cache nb/ide.branding/alplibre/generate-assets.swift` from the repository root
on macOS to regenerate splash and application icons from the logo.
Application icons clip the opaque source corners; macOS ICNS representations also
use a transparent 10% inset on each side to fit the Dock. White snow is preserved. Existing resource
names are deliberately retained so the standard branding build picks them up.

Use the normal build / tryme targets. Native Windows launcher executables must be
rebuilt to embed the updated ICO and version resources; existing prebuilt executables
will retain their original embedded branding. Legacy installers and bundled module
help may still contain upstream names and should be audited before public release.
