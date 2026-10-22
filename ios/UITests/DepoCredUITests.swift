import XCTest

final class DepoCredUITests: XCTestCase {
    var app: XCUIApplication!
    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--reset-state", "-AppleLanguages", "(ru)", "-AppleLocale", "ru_RU"]
        app.launch()
        XCTAssertTrue(app.buttons["tab_deposit"].waitForExistence(timeout: 15))
        let ready = NSPredicate(format: "exists == false")
        expectation(for: ready, evaluatedWith: app.otherElements["startup"])
        waitForExpectations(timeout: 10)
    }
    func reveal(_ element: XCUIElement) {
        for _ in 0..<10 {
            if element.isHittable { return }
            app.scrollViews.firstMatch.swipeUp()
        }
        XCTAssertTrue(element.isHittable, "Element is not reachable: \(element)")
    }
    func fill(_ key: String, _ value: String) {
        let field = app.textFields["field_" + key]
        XCTAssertTrue(field.waitForExistence(timeout: 5)); reveal(field); field.tap(); field.typeText(value)
    }
    func closeKeyboard() {
        if app.buttons["keyboard_next"].exists {
            // Move through remaining fields without hiding the keyboard between them.
            for _ in 0..<15 {
                let button = app.buttons["keyboard_next"]
                guard button.exists else { break }
                let done = button.label == "Применить"
                button.tap()
                if done { break }
            }
        }
    }
    func calculate() {
        closeKeyboard()
        let button = app.buttons["calculate"]; reveal(button); button.tap()
        XCTAssertTrue(app.buttons["save_calculation"].waitForExistence(timeout: 10))
        reveal(app.buttons["save_calculation"])
    }
    func capture(_ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = name; shot.lifetime = .keepAlways; add(shot)
    }
    func testFirstLaunchRequiredFocusAndNextKeyboard() {
        XCTAssertTrue(app.staticTexts["Депозитный калькулятор"].exists)
        XCTAssertEqual(app.textFields["field_amount"].value as? String, "")
        capture("01-deposit-light-amber")
        reveal(app.buttons["calculate"]); app.buttons["calculate"].tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        let amount = app.textFields["field_amount"]
        amount.typeText("100000")
        let next = app.buttons["keyboard_next"]
        XCTAssertTrue(next.waitForExistence(timeout: 5)); XCTAssertEqual(next.label,"Далее"); next.tap()
        app.textFields["field_rate"].typeText("12"); next.tap()
        app.textFields["field_term"].typeText("12"); XCTAssertEqual(next.label,"Применить"); next.tap()
        calculate(); capture("02-deposit-result")
    }
    func testDepositHistorySurvivesRelaunchButDraftsDoNot() {
        fill("amount","100000"); fill("rate","12"); fill("term","12"); calculate()
        app.buttons["save_calculation"].tap()
        app.buttons["tab_history"].tap()
        XCTAssertTrue(app.otherElements["history_entry_deposit"].waitForExistence(timeout: 5)); capture("03-history")
        app.terminate(); app.launchArguments.removeAll { $0 == "--reset-state" }; app.launch()
        XCTAssertTrue(app.textFields["field_amount"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.textFields["field_amount"].value as? String,"")
        let ready = NSPredicate(format: "exists == false"); expectation(for: ready,evaluatedWith: app.otherElements["startup"]); waitForExpectations(timeout: 10)
        app.buttons["tab_history"].tap(); XCTAssertTrue(app.otherElements["history_entry_deposit"].exists)
    }
    func testLoanSwitchingResultsKeepsPositionAndShowsExplanation() {
        app.buttons["tab_loan"].tap()
        fill("amount","1000000"); fill("rate","12"); fill("term","36"); calculate()
        for _ in 0..<3 {
            let diff = app.buttons["Дифференцированный"]; reveal(diff); diff.tap()
            XCTAssertTrue(app.buttons["Аннуитетный"].isHittable)
            app.buttons["Аннуитетный"].tap()
            XCTAssertFalse(app.textFields["field_amount"].isHittable)
        }
        let info = app.buttons["Как рассчитана эффективная ставка"]; reveal(info); info.tap()
        XCTAssertTrue(app.staticTexts["Эффективная ставка"].waitForExistence(timeout: 5)); capture("04-loan-rate-explanation")
        app.buttons["ПОНЯТНО"].tap()
        reveal(app.buttons["open_schedule"]); app.buttons["open_schedule"].tap()
        XCTAssertTrue(app.scrollViews["schedule"].waitForExistence(timeout: 5)); capture("05-loan-schedule")
        app.buttons["Отправить PDF"].tap()
        XCTAssertTrue(app.buttons["Закрыть"].waitForExistence(timeout: 5) || app.otherElements.matching(identifier: "ActivityListView").firstMatch.exists || app.navigationBars.count > 0)
    }
    func testLeasingDirectAndReverse() {
        app.buttons["tab_leasing"].tap()
        fill("amount","1000000"); fill("advance","20"); fill("rate","18"); fill("term","36"); calculate(); capture("06-leasing-direct")
        app.buttons["save_calculation"].tap()
        app.buttons["tab_deposit"].tap(); app.buttons["tab_leasing"].tap()
        app.buttons["Обратный"].tap()
        fill("amount","10300000"); fill("advance","10"); fill("count0","34"); fill("payment0","359210.80"); fill("buyout","20000"); calculate()
        XCTAssertTrue(app.staticTexts["20,06%"].exists); capture("07-leasing-reverse")
        app.buttons["save_calculation"].tap()
        app.buttons["tab_history"].tap(); app.buttons["history_filter_leasing"].tap()
        XCTAssertEqual(app.otherElements.matching(identifier: "history_entry_leasing").count,2)
    }
    func testThemesPersistAndInfoAlwaysReopensAtTop() {
        app.buttons["tab_info"].tap()
        app.buttons["theme_dark"].tap(); app.buttons["skin_blue"].tap(); capture("08-settings-dark-blue")
        let content = app.scrollViews.firstMatch
        for _ in 0..<5 { content.swipeUp() }
        app.buttons["tab_deposit"].tap(); app.buttons["tab_info"].tap()
        XCTAssertTrue(app.staticTexts["settings_heading"].isHittable)
        XCTAssertTrue(app.buttons["theme_dark"].isSelected)
        app.buttons["theme_light"].tap(); app.buttons["skin_amber"].tap(); capture("09-settings-light-amber")
    }
}
