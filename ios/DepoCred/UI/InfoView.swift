import SwiftUI
import StoreKit

let privacyText = """
DepoCred работает без подключения к интернету, не содержит рекламы, аналитики и регистрации. Настройки и последние 10 явно сохранённых расчётов находятся во внутреннем хранилище устройства. Введённые поля сохраняются при сворачивании, но очищаются после полного закрытия приложения.

PDF сохраняется в выбранную папку или передаётся другому приложению только после вашего действия. Расчёты не передаются разработчику.

Обновление приложения сохраняет настройки и историю. Удаление приложения удаляет его локальные данные. PDF, сохранённые вами в «Файлы», остаются в выбранной папке. Системная резервная копия устройства может содержать настройки и историю; её хранением управляет iOS и ваша учётная запись Apple.

Контакт: support@depocred.ru
"""

struct InfoView: View {
    @ObservedObject var model: AppModel
    @Environment(\.palette) private var p
    @Environment(\.openURL) private var openURL
    @Environment(\.requestReview) private var requestReview
    @State private var document: String?
    @State private var share = false
    private var storeID: String { Bundle.main.object(forInfoDictionaryKey: "AppStoreID") as? String ?? "" }
    private var appURL: URL { URL(string: storeID.isEmpty ? "https://depocred.ru" : "https://apps.apple.com/app/id\(storeID)")! }
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Настройки").font(.inter(20, .bold, relativeTo: .title3)).foregroundStyle(p.text).accessibilityIdentifier("settings_heading")
            Glass(gap: 24) {
                VStack(alignment: .leading, spacing: 12) {
                    FieldLabel(text: "Тема оформления")
                    HStack(spacing: 4) {
                        themeOption("light", "Светлая", "sun")
                        themeOption("dark", "Темная", "moon")
                    }.padding(4).background(p.field, in: RoundedRectangle(cornerRadius: 12)).overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(p.border, lineWidth: 1))
                }
                VStack(alignment: .leading, spacing: 14) {
                    FieldLabel(text: "Цветовая схема")
                    HStack {
                        ForEach(skins) { skin in
                            Button { model.updateSettings { $0.skin = skin.id } } label: {
                                RoundedRectangle(cornerRadius: 12).fill(skin.color.opacity(model.settings.skin == skin.id ? 1 : 0.4)).frame(width: 40, height: 40)
                                    .padding(4).background(model.settings.skin == skin.id ? p.field : .clear, in: RoundedRectangle(cornerRadius: 16))
                            }.buttonStyle(PressStyle()).accessibilityLabel(skin.name).accessibilityIdentifier("skin_" + skin.id).accessibilityAddTraits(model.settings.skin == skin.id ? .isSelected : [])
                            if skin.id != skins.last?.id { Spacer(minLength: 0) }
                        }
                    }
                }
                Toggle(isOn: Binding(get: { model.settings.hints }, set: { value in model.updateSettings { $0.hints = value } })) {
                    FieldLabel(text: "Подсказки в приложении")
                }.tint(p.primary).accessibilityLabel("Подсказки в приложении")
            }
            Text("О приложении").font(.inter(20, .bold, relativeTo: .title3)).foregroundStyle(p.text)
            infoCard("DepoCred — финансовый инструмент для расчета кредитов, депозитов и лизинга. Прямые и обратные расчёты, подробные графики и PDF-отчёты.")
            feature("shield", "Конфиденциальность", "Все расчеты хранятся локально на вашем устройстве. Мы не собираем финансовые данные.", 0x10B981)
            feature("sparkles", "Умные алгоритмы", "Расчёты аннуитетных и дифференцированных платежей, сложного банковского процента и стоимости лизинга.", 0x3B82F6)
            feature("smartphone", "Платформа", "Нативное приложение для iPhone и iPad. Удобный ввод, наглядная детализация и сохранение расчетов.", 0x8B5CF6)
            VStack(spacing: 0) {
                Divider().overlay(p.primary.opacity(0.2)).padding(.vertical, 16)
                link("globe", "depocred.ru") { openURL(URL(string: "https://depocred.ru")!) }
                link("mail", "support@depocred.ru") { openURL(URL(string: "mailto:support@depocred.ru")!) }
                link("share_2", "Поделиться приложением") { share = true }
                link("star", "Поставить оценку") {
                    if storeID.isEmpty { requestReview() }
                    else { openURL(URL(string: "https://apps.apple.com/app/id\(storeID)?action=write-review")!) }
                }
                link("shield", "Политика конфиденциальности") { document = "Конфиденциальность" }
                link("info", "Открытые лицензии") { document = "Открытые лицензии" }
                Divider().overlay(p.primary.opacity(0.2)).padding(.vertical, 16)
            }
            Text("ВЕРСИЯ \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0")\n\n• • •\n\nСделано с ❤️ для вашей финансовой свободы")
                .font(.inter(10)).tracking(1).foregroundStyle(p.muted).multilineTextAlignment(.center).frame(maxWidth: .infinity).accessibilityIdentifier("info_footer")
        }.sheet(isPresented: Binding(get: { document != nil }, set: { if !$0 { document = nil } })) {
            DialogPage(title: document ?? "") { Text(document == "Конфиденциальность" ? privacyText : licenses).font(.inter(13)).foregroundStyle(p.muted).lineSpacing(6) }.environment(\.palette, p)
        }.sheet(isPresented: $share) { ShareSheet(items: [appURL]) }
    }
    private var licenses: String { ["Third-party-notices-ios", "Inter-OFL", "Lucide-LICENSE"].compactMap { name in Bundle.main.url(forResource: name, withExtension: "txt").flatMap { try? String(contentsOf: $0, encoding: .utf8) } }.joined(separator: "\n\n") }
    private func themeOption(_ key: String, _ title: String, _ icon: String) -> some View {
        Button { model.updateSettings { $0.theme = key } } label: {
            HStack(spacing: 8) { Glyph(name: icon, size: 18); Text(title).font(.inter(14, .semibold)) }
                .foregroundStyle(model.settings.theme == key ? Color.white : p.muted).frame(maxWidth: .infinity, minHeight: 44).background(model.settings.theme == key ? p.primary : .clear, in: RoundedRectangle(cornerRadius: 8))
        }.buttonStyle(.plain).accessibilityIdentifier("theme_" + key).accessibilityAddTraits(model.settings.theme == key ? .isSelected : [])
    }
    private func infoCard(_ text: String) -> some View { Text(text).font(.inter(14)).foregroundStyle(p.muted).lineSpacing(6).frame(maxWidth: .infinity, alignment: .leading).padding(24).background(p.field, in: RoundedRectangle(cornerRadius: 16)).overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(p.border, lineWidth: 1)) }
    private func feature(_ icon: String, _ title: String, _ text: String, _ color: UInt32) -> some View {
        HStack(spacing: 16) {
            Glyph(name: icon).foregroundStyle(Color(hex: color)).frame(width: 48, height: 48).background(Color(hex: color).opacity(0.1), in: RoundedRectangle(cornerRadius: 8))
            VStack(alignment: .leading, spacing: 5) { Text(title.uppercased()).font(.inter(11, .bold)).foregroundStyle(p.text); Text(text).font(.inter(13)).foregroundStyle(p.muted).lineSpacing(5) }
        }.frame(maxWidth: .infinity, alignment: .leading).padding(24).background(p.field, in: RoundedRectangle(cornerRadius: 16)).overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(p.border, lineWidth: 1))
    }
    private func link(_ icon: String, _ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) { HStack(spacing: 12) { Glyph(name: icon, size: 20); Text(title).font(.inter(14)); Spacer() }.foregroundStyle(p.muted).padding(12).frame(minHeight: 48).contentShape(Rectangle()) }.buttonStyle(PressStyle())
    }
}
