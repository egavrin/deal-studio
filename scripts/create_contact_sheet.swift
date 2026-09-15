import AppKit
import Foundation

struct Tile {
    let label: String
    let path: String
}

guard CommandLine.arguments.count == 3 else {
    fputs("usage: create_contact_sheet MODE OUTPUT.png\n", stderr)
    exit(64)
}

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
    .appendingPathComponent("artifacts/paired-surprise-embedded-ui-v7-20260915")
let mode = CommandLine.arguments[1]
let output = URL(fileURLWithPath: CommandLine.arguments[2])
let scenarios = [
    "chore-wheel", "bookmark-box", "walking-goal", "focus-timer", "meeting-notes",
    "garden-log", "mood-checkin", "language-sprint", "recipe-box", "plant-budget"
]

let titles = [
    "Chore wheel", "Bookmark box", "Walking goal", "Focus timer", "Meeting notes",
    "Garden log", "Mood check-in", "Language sprint", "Recipe box", "Plant budget"
]

let tiles: [Tile] = zip(scenarios.indices, scenarios).map { index, scenario in
    let folder = root.appendingPathComponent(String(format: "%02d-%@", index + 1, scenario))
    let file: String
    if mode == "deal" {
        file = scenario == "focus-timer" ? "deal-runtime-stepper-fixed.png" :
            (scenario == "meeting-notes" ? "deal-control-runtime.png" : "deal-runtime.png")
    } else {
        file = "html5-runtime.png"
    }
    return Tile(label: String(format: "%02d  %@", index + 1, titles[index]), path: folder.appendingPathComponent(file).path)
}

let columns = 5
let tileWidth: CGFloat = 480
let imageHeight: CGFloat = 1067
let labelHeight: CGFloat = 46
let gap: CGFloat = 20
let outer: CGFloat = 24
let header: CGFloat = 72
let rows = Int(ceil(Double(tiles.count) / Double(columns)))
let size = NSSize(
    width: outer * 2 + CGFloat(columns) * tileWidth + CGFloat(columns - 1) * gap,
    height: outer * 2 + header + CGFloat(rows) * (labelHeight + imageHeight) + CGFloat(rows - 1) * gap
)

let image = NSImage(size: size)
image.lockFocus()
NSColor(calibratedWhite: 0.11, alpha: 1).setFill()
NSBezierPath(rect: NSRect(origin: .zero, size: size)).fill()

let heading = mode == "deal" ? "DEAL — runtime screenshots (01–10)" : "HTML5 — runtime screenshots (01–10)"
let headingStyle: [NSAttributedString.Key: Any] = [
    .font: NSFont.systemFont(ofSize: 32, weight: .bold),
    .foregroundColor: NSColor.white
]
heading.draw(at: NSPoint(x: outer, y: size.height - outer - 40), withAttributes: headingStyle)

let labelStyle: [NSAttributedString.Key: Any] = [
    .font: NSFont.monospacedSystemFont(ofSize: 18, weight: .semibold),
    .foregroundColor: NSColor(calibratedWhite: 0.92, alpha: 1)
]
for (index, tile) in tiles.enumerated() {
    let column = index % columns
    let row = index / columns
    let x = outer + CGFloat(column) * (tileWidth + gap)
    let rowTop = size.height - outer - header - CGFloat(row) * (labelHeight + imageHeight + gap)
    let labelY = rowTop - 27
    tile.label.draw(at: NSPoint(x: x, y: labelY), withAttributes: labelStyle)
    let target = NSRect(x: x, y: rowTop - labelHeight - imageHeight, width: tileWidth, height: imageHeight)
    guard let screenshot = NSImage(contentsOfFile: tile.path) else {
        fputs("Cannot load \(tile.path)\n", stderr)
        image.unlockFocus()
        exit(1)
    }
    screenshot.draw(in: target, from: NSRect(origin: .zero, size: screenshot.size), operation: .sourceOver, fraction: 1)
}
image.unlockFocus()

guard let data = image.tiffRepresentation,
      let bitmap = NSBitmapImageRep(data: data),
      let png = bitmap.representation(using: .png, properties: [:]) else {
    fputs("Cannot encode PNG\n", stderr)
    exit(1)
}
try png.write(to: output)
