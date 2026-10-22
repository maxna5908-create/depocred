import SwiftUI

struct NumericField: View {
    @Environment(\.palette) private var p
    let title: String
    let key: String
    let form: Form
    var suffix = "₽"
    var integer = false
    var enabled = true
    var calculated = false
    var locked = false
    let focus: FocusState<String?>.Binding
    let next: String?
    let edit: (String, String) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            FieldLabel(text: title).padding(.leading, 4)
            HStack(spacing: 6) {
                TextField("", text: Binding(get: { form.text(key) }, set: { raw in
                    let value = raw.replacingOccurrences(of: ",", with: ".").replacingOccurrences(of: " ", with: "").replacingOccurrences(of: "\u{00a0}", with: "")
                    let pattern = integer ? "^[0-9]{0,3}$" : "^[0-9]{0,12}(\\.[0-9]{0,2})?$"
                    if value.range(of: pattern, options: .regularExpression) != nil { edit(key, value) }
                }))
                .font(.inter(16, .semibold)).foregroundStyle(calculated ? p.primary : p.text).tint(p.primary)
                .keyboardType(integer ? .numberPad : .decimalPad).autocorrectionDisabled().textInputAutocapitalization(.never)
                .focused(focus, equals: key).submitLabel(next == nil ? .done : .next)
                .onSubmit { focus.wrappedValue = next }.disabled(!enabled || locked)
                .accessibilityLabel(title).accessibilityIdentifier("field_" + key)
                .accessibilityHint(calculated ? "Рассчитано автоматически" : "")
                if !form.text(key).isEmpty {
                    Text(suffix).font(.inter(12)).foregroundStyle(p.muted).accessibilityHidden(true)
                    if enabled && !locked {
                        Button { edit(key, ""); focus.wrappedValue = key } label: { Glyph(name: "x", size: 13).foregroundStyle(p.muted).frame(width: 24, height: 24).contentShape(Rectangle()) }.buttonStyle(.plain).accessibilityLabel("Очистить " + title)
                    }
                }
            }.padding(.horizontal, 16).padding(.vertical, 12).frame(minHeight: 48)
                .background(!enabled || locked ? p.field.opacity(0.5) : .clear, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(focus.wrappedValue == key ? p.primary : calculated ? p.primary.opacity(0.4) : p.border, lineWidth: 1))
        }.id("input_" + key)
    }
}
struct TwoFields<Content: View>: View {
    @Environment(\.dynamicTypeSize) private var textSize
    @ViewBuilder var content: Content
    var body: some View {
        let layout = textSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 16)) : AnyLayout(HStackLayout(alignment: .bottom, spacing: 16))
        layout { content }.frame(maxWidth: .infinity)
    }
}
struct OptionField: View {
    @Environment(\.palette) private var p
    @State private var open = false
    let title: String
    let value: String
    let options: [Choice]
    let onSelect: (String) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            FieldLabel(text: title)
            Button { open = true } label: {
                HStack(spacing: 8) {
                    Text(options.first { $0.id == value }?.title ?? "Выбрать").font(.inter(14, .medium)).foregroundStyle(p.text).frame(maxWidth: .infinity, alignment: .leading)
                    Circle().fill(p.primary).frame(width: 6, height: 6)
                }.padding(.horizontal, 14).padding(.vertical, 12).frame(minHeight: 48).overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(p.border, lineWidth: 1)).contentShape(RoundedRectangle(cornerRadius: 12))
            }.buttonStyle(PressStyle()).accessibilityLabel(title).accessibilityValue(options.first { $0.id == value }?.title ?? "Выбрать")
        }.frame(maxWidth: .infinity)
        .sheet(isPresented: $open) {
            DialogPage(title: title) {
                ForEach(options) { option in
                    Button { onSelect(option.id); open = false } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(option.title).font(.inter(16, .semibold))
                            if !option.detail.isEmpty { Text(option.detail).font(.inter(12)).lineSpacing(4).opacity(0.9) }
                        }.foregroundStyle(option.id == value ? Color.white : p.text).frame(maxWidth: .infinity, alignment: .leading).padding(16)
                            .background(option.id == value ? p.primary : p.field, in: RoundedRectangle(cornerRadius: 12))
                    }.buttonStyle(PressStyle()).accessibilityAddTraits(option.id == value ? .isSelected : [])
                }
            }.environment(\.palette, p)
        }
    }
}
struct DialogPage<Content: View>: View {
    @Environment(\.palette) private var p
    @Environment(\.dismiss) private var dismiss
    let title: String
    @ViewBuilder var content: Content
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text(title).font(.inter(18, .bold)).foregroundStyle(p.text).frame(maxWidth: .infinity, alignment: .leading)
                Button { dismiss() } label: { Glyph(name: "x", size: 20).foregroundStyle(p.muted).frame(width: 44, height: 44) }.accessibilityLabel("Закрыть окно")
            }.padding(.horizontal, 20).padding(.vertical, 8)
            Divider().overlay(p.border)
            ScrollView { VStack(alignment: .leading, spacing: 20) { content }.frame(maxWidth: .infinity, alignment: .leading).padding(24) }
            Divider().overlay(p.border)
            ActionButton(title: "ПОНЯТНО") { dismiss() }.padding(16)
        }.background(p.solid).presentationDragIndicator(.visible).presentationDetents([.large])
    }
}
