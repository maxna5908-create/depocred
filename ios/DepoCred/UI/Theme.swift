import SwiftUI

struct Skin: Identifiable {
    let id: String
    let name: String
    let hex: UInt32
    var color: Color { Color(hex: hex) }
}
let skins = [Skin(id: "emerald", name: "Изумрудная", hex: 0x10B981), Skin(id: "blue", name: "Синяя", hex: 0x3B82F6), Skin(id: "amber", name: "Янтарная", hex: 0xF59E0B), Skin(id: "rose", name: "Розовая", hex: 0xF43F5E), Skin(id: "violet", name: "Фиолетовая", hex: 0x8B5CF6)]
extension Color {
    init(hex: UInt32) { self.init(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1) }
}
struct Palette {
    var dark = false
    var primary = Color(hex: 0xF59E0B)
    var text: Color { dark ? .white : Color(hex: 0x0F172A) }
    var muted: Color { Color(hex: dark ? 0xCBD5E1 : 0x64748B) }
    var glass: Color { .white.opacity(dark ? 0.12 : 0.60) }
    var field: Color { dark ? .white.opacity(0.08) : .black.opacity(0.05) }
    var border: Color { dark ? .white.opacity(0.18) : .black.opacity(0.10) }
    var solid: Color { Color(hex: dark ? 0x0F172A : 0xF9FAFB) }
    var background: RadialGradient {
        RadialGradient(colors: [Color(hex: dark ? 0x374151 : 0xF9FAFB), Color(hex: dark ? 0x111827 : 0xE5E7EB)], center: .topLeading, startRadius: 0, endRadius: 960)
    }
}
private struct PaletteKey: EnvironmentKey { static let defaultValue = Palette() }
extension EnvironmentValues { var palette: Palette { get { self[PaletteKey.self] } set { self[PaletteKey.self] = newValue } } }
extension Font {
    static func inter(_ size: CGFloat, _ weight: Font.Weight = .regular, relativeTo style: Font.TextStyle = .body) -> Font {
        .custom("Inter-Regular", size: size, relativeTo: style).weight(weight)
    }
}
struct Glyph: View {
    let name: String
    var size: CGFloat = 22
    var body: some View { Image("lucide_" + name).renderingMode(.template).resizable().scaledToFit().frame(width: size, height: size).accessibilityHidden(true) }
}
struct FieldLabel: View {
    @Environment(\.palette) private var p
    let text: String
    var body: some View { Text(text.uppercased()).font(.inter(9, .semibold, relativeTo: .caption2)).tracking(0.7).foregroundStyle(p.muted).fixedSize(horizontal: false, vertical: true) }
}
struct Glass<Content: View>: View {
    @Environment(\.palette) private var p
    var padding: CGFloat = 24
    var gap: CGFloat = 16
    @ViewBuilder var content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: gap) { content }.frame(maxWidth: .infinity, alignment: .leading).padding(padding)
            .background(p.glass, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(p.border, lineWidth: 1))
    }
}
struct AttachedShape: Shape {
    var selected: Int
    var tabHeight: CGFloat
    func path(in r: CGRect) -> Path {
        let w = r.width, h = r.height, half = w / 2, t = tabHeight, radius = min(16.0, half / 2)
        var path = Path()
        path.move(to: CGPoint(x: radius, y: 0)); path.addLine(to: CGPoint(x: half-radius,y: 0))
        path.addQuadCurve(to: CGPoint(x: half,y: radius), control: CGPoint(x: half,y: 0))
        path.addLine(to: CGPoint(x: half,y: t)); path.addLine(to: CGPoint(x: w-radius,y: t))
        path.addQuadCurve(to: CGPoint(x: w,y: t+radius), control: CGPoint(x: w,y: t))
        path.addLine(to: CGPoint(x: w,y: h-radius)); path.addQuadCurve(to: CGPoint(x: w-radius,y: h), control: CGPoint(x: w,y: h))
        path.addLine(to: CGPoint(x: radius,y: h)); path.addQuadCurve(to: CGPoint(x: 0,y: h-radius), control: CGPoint(x: 0,y: h))
        path.addLine(to: CGPoint(x: 0,y: radius)); path.addQuadCurve(to: CGPoint(x: radius,y: 0), control: .zero); path.closeSubpath()
        return selected == 0 ? path : path.applying(CGAffineTransform(a: -1,b: 0,c: 0,d: 1,tx: w,ty: 0))
    }
}
struct PanelTabStyle: ButtonStyle {
    @Binding var pressed: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.contentShape(Rectangle()).onChange(of: configuration.isPressed) { pressed = $0 }
    }
}
struct AttachedPanel<Content: View>: View {
    @Environment(\.palette) private var p
    @ScaledMetric(relativeTo: .caption) private var tabHeight = 42.0
    @State private var pressed = false
    let titles: [String]
    let selected: Int
    let onSelect: (Int) -> Void
    @ViewBuilder var content: Content
    var body: some View {
        let shape = AttachedShape(selected: selected, tabHeight: tabHeight)
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                ForEach(titles.indices, id: \.self) { index in
                    Button { onSelect(index) } label: {
                        Text(titles[index]).font(.inter(12, .bold, relativeTo: .caption)).foregroundStyle(selected == index ? p.text : p.muted)
                            .multilineTextAlignment(.center).frame(maxWidth: .infinity, minHeight: tabHeight).contentShape(Rectangle())
                    }.buttonStyle(PanelTabStyle(pressed: $pressed)).accessibilityAddTraits(selected == index ? .isSelected : [])
                }
            }.frame(height: tabHeight)
            VStack(alignment: .leading, spacing: 16) { content }.frame(maxWidth: .infinity, alignment: .leading).padding(24)
        }.background(p.glass, in: shape).overlay(shape.stroke(p.border, lineWidth: 1))
            .overlay(shape.fill(p.dark ? Color.white.opacity(pressed ? 0.10 : 0) : Color.black.opacity(pressed ? 0.10 : 0)).allowsHitTesting(false))
            .animation(.easeOut(duration: 0.12), value: pressed)
    }
}
struct PressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label.opacity(configuration.isPressed ? 0.72 : 1).scaleEffect(configuration.isPressed ? 0.98 : 1).animation(.easeOut(duration: 0.12), value: configuration.isPressed) }
}
struct ActionButton: View {
    @Environment(\.palette) private var p
    let title: String
    var icon: String? = nil
    var primary = true
    var busy = false
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if busy { ProgressView().tint(primary ? .white : p.primary) }
                else if let icon { Glyph(name: icon, size: 18) }
                Text(title).font(.inter(14, .bold)).multilineTextAlignment(.center)
            }.foregroundStyle(primary ? Color.white : p.text).frame(maxWidth: .infinity, minHeight: 24).padding(12)
                .background(primary ? p.primary : p.field, in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(primary ? .clear : p.border, lineWidth: 1))
        }.buttonStyle(PressStyle()).disabled(busy)
    }
}
struct IconButton: View {
    @Environment(\.palette) private var p
    let icon: String
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) { Glyph(name: icon).foregroundStyle(p.text).frame(width: 48, height: 48).background(p.field, in: RoundedRectangle(cornerRadius: 16)).overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(p.border, lineWidth: 1)) }
            .buttonStyle(PressStyle()).accessibilityLabel(label)
    }
}
struct Hint: View {
    @Environment(\.palette) private var p
    let text: String
    var body: some View { Text(text).font(.inter(12)).foregroundStyle(p.primary).lineSpacing(4).frame(maxWidth: .infinity, alignment: .leading).padding(12).background(p.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 12)).overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(p.primary.opacity(0.2), lineWidth: 1)) }
}
struct Metric: View {
    @Environment(\.palette) private var p
    let title: String
    let value: String
    var accent = false
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title.uppercased()).font(.inter(9, .bold, relativeTo: .caption2)).tracking(0.4).foregroundStyle(p.muted)
            Text(value).font(.inter(15, .semibold)).foregroundStyle(accent ? p.primary : p.text)
        }.frame(maxWidth: .infinity, alignment: .leading).fixedSize(horizontal: false, vertical: true)
    }
}
struct MetricPair: View {
    @Environment(\.dynamicTypeSize) private var textSize
    let left: (String, String)
    let right: (String, String)
    var accent = false
    var body: some View {
        let layout = textSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 12)) : AnyLayout(HStackLayout(alignment: .top, spacing: 16))
        layout { Metric(title: left.0, value: left.1, accent: accent); Metric(title: right.0, value: right.1, accent: accent) }
    }
}
struct InfoLink: View {
    @Environment(\.palette) private var p
    let title: String
    let action: () -> Void
    var body: some View { Button(action: action) { Text(title).underline().font(.inter(13, .medium)).foregroundStyle(p.primary).multilineTextAlignment(.center).frame(maxWidth: .infinity, minHeight: 48) }.buttonStyle(PressStyle()) }
}
