// AlpLibre IDE branding assets. Licensed under Apache License 2.0.
// Run from the repository root on macOS.
import AppKit
let base = "nb/ide.branding/"
let logo = NSImage(contentsOfFile: base + "alplibre/logo.png")!
func render(_ width: Int, _ height: Int, _ path: String, splash: Bool = false, dock: Bool = false) {
    let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: width, pixelsHigh: height, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
    NSGraphicsContext.current?.imageInterpolation = .high
    if splash {
        NSColor(calibratedRed: 0.015, green: 0.025, blue: 0.10, alpha: 1).setFill()
        NSRect(x: 0, y: 0, width: width, height: height).fill()
        NSGraphicsContext.saveGraphicsState()
        let logoRect = NSRect(x: 180, y: 150, width: 120, height: 120)
        NSBezierPath(roundedRect: logoRect,
                     xRadius: logoRect.width * 0.27,
                     yRadius: logoRect.height * 0.27).addClip()
        logo.draw(in: logoRect)
        NSGraphicsContext.restoreGraphicsState()
        func text(_ value: String, _ y: CGFloat, _ size: CGFloat, _ color: NSColor) {
            let attrs: [NSAttributedString.Key: Any] = [.font: NSFont.systemFont(ofSize: size, weight: size > 24 ? .semibold : .regular), .foregroundColor: color]
            let string = value as NSString
            let w = string.size(withAttributes: attrs).width
            string.draw(at: NSPoint(x: (CGFloat(width) - w) / 2, y: y), withAttributes: attrs)
        }
        text("AlpLibre IDE", 110, 32, .white)
        text("Build. Understand. Explore.", 84, 14, NSColor(calibratedRed: 0.52, green: 0.96, blue: 0.83, alpha: 1))
        text("Based on Apache NetBeans · Independent distribution", 59, 10, .lightGray)
        text("Apache NetBeans is a trademark of The Apache Software Foundation.", 46, 8, .lightGray)
    } else {
        // The source artwork has opaque white corners. Clip them rather than
        // treating white as transparent, which would also erase the snow cap.
        // Native macOS icons need breathing room inside the square ICNS canvas.
        let side = CGFloat(min(width, height))
        let inset = dock ? side * 0.10 : 0
        let rect = NSRect(x: inset, y: inset,
                          width: CGFloat(width) - inset * 2,
                          height: CGFloat(height) - inset * 2)
        NSBezierPath(roundedRect: rect,
                     xRadius: rect.width * 0.27,
                     yRadius: rect.height * 0.27).addClip()
        logo.draw(in: rect)
    }
    NSGraphicsContext.restoreGraphicsState()
    let type: NSBitmapImageRep.FileType = path.hasSuffix(".gif") ? .gif : .png
    try! bitmap.representation(using: type, properties: [:])!.write(to: URL(fileURLWithPath: path))
}
let startup = base + "core.startup/src/org/netbeans/core/startup/"
for suffix in ["", "_dark"] {
    for (name, size) in [("frame", 16), ("frame32", 32), ("frame48", 48)] {
        render(size, size, startup + name + suffix + "_nb.gif")
    }
    render(480, 300, startup + "splash" + suffix + "_nb.gif", splash: true)
}
render(480, 300, base + "autoupdate.services/libsrc/org/netbeans/updater/resources/updatersplash_nb.gif", splash: true)
let iconset = "/tmp/alplibre.iconset"
try! FileManager.default.createDirectory(atPath: iconset, withIntermediateDirectories: true)
for size in [16,32,128,256,512] {
    render(size, size, "\(iconset)/icon_\(size)x\(size).png", dock: true)
    render(size * 2, size * 2, "\(iconset)/icon_\(size)x\(size)@2x.png", dock: true)
}
for suffix in ["", "_dark"] {
    render(480, 300, startup + "about" + suffix + "_nb.png", splash: true)
}
for size in [48,256,512,1024] {
    render(size, size, base + "release/apache-netbeans\(size).png")
}
render(48, 48, base + "release/netbeans.png")
render(128, 128, base + "src/org/netbeans/modules/ide/branding/apache-netbeans.png")
// ICNS supports PNG payloads for these modern icon representations.
var chunks = Data()
func bigEndian(_ value: Int) -> Data {
    var v = UInt32(value).bigEndian
    return withUnsafeBytes(of: &v) { Data($0) }
}
for (kind, size) in [("icp4",16),("icp5",32),("ic07",128),("ic08",256),("ic09",512),("ic10",1024)] {
    let path = size == 1024 ? "\(iconset)/icon_512x512@2x.png" : "\(iconset)/icon_\(size)x\(size).png"
    let png = try! Data(contentsOf: URL(fileURLWithPath: path))
    chunks.append(kind.data(using: .ascii)!); chunks.append(bigEndian(png.count + 8)); chunks.append(png)
}
var icns = "icns".data(using: .ascii)!
icns.append(bigEndian(chunks.count + 8)); icns.append(chunks)
try! icns.write(to: URL(fileURLWithPath: base + "release/netbeans.icns"))
var ico = Data()
func u16(_ value: Int) { var v = UInt16(value).littleEndian; withUnsafeBytes(of: &v) { ico.append(contentsOf: $0) } }
func u32(_ value: Int) { var v = UInt32(value).littleEndian; withUnsafeBytes(of: &v) { ico.append(contentsOf: $0) } }
let sizes = [16,32,128,256]
for size in sizes {
    render(size, size, "\(iconset)/windows_\(size).png")
}
let images = sizes.map { try! Data(contentsOf: URL(fileURLWithPath: "\(iconset)/windows_\($0).png")) }
u16(0); u16(1); u16(sizes.count)
var offset = 6 + 16 * sizes.count
for (size, data) in zip(sizes, images) {
    ico.append(contentsOf: [UInt8(size % 256), UInt8(size % 256), 0, 0])
    u16(1); u16(32); u32(data.count); u32(offset)
    offset += data.count
}
for data in images { ico.append(data) }
for path in [base + "release/apache-netbeans.ico", "nb/ide.launcher/windows/netbeans.ico"] {
    try! ico.write(to: URL(fileURLWithPath: path))
}
// Keep the existing SVG resource path used by downstream desktop packaging.
let png = try! Data(contentsOf: URL(fileURLWithPath: base + "alplibre/logo.png"))
let svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"0 0 1024 1024\"><title>AlpLibre IDE</title><desc>Original AlpLibre mountain artwork. Apache License 2.0.</desc><image width=\"1024\" height=\"1024\" xlink:href=\"data:image/png;base64,\(png.base64EncodedString())\"/></svg>"
try! svg.write(toFile: base + "release/apache-netbeans.svg", atomically: true, encoding: .utf8)
