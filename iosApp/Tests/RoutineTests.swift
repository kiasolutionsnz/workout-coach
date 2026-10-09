import XCTest
final class RoutineTests:XCTestCase {
    func testInvalidDraftAndSavedRoutineSurviveRelaunch() {
        let app = XCUIApplication();app.launch()
        let new = app.buttons["New routine"];let ready = new.waitForExistence(timeout:15)
        if !ready { print(app.debugDescription) }
        XCTAssertTrue(ready);new.tap()
        let save = app.buttons["Save routine"];save.tap()
        XCTAssertTrue(app.staticTexts["form-error"].waitForExistence(timeout:5))
        let field = app.textFields["routine-name"];field.tap()
        let name = "Agent routine \(UUID().uuidString.prefix(8))";field.typeText(name)
        app.keyboards.buttons["Done"].tap();save.tap()
        XCTAssertTrue(new.waitForExistence(timeout:10))
        app.terminate();app.launch()
        let routine = app.staticTexts[name];if !routine.exists { app.swipeUp() }
        XCTAssertTrue(routine.waitForExistence(timeout:10));routine.tap()
        XCTAssertTrue(app.buttons["Prepare workout"].waitForExistence(timeout:5))
        let screenshot = XCTAttachment(screenshot:app.screenshot());screenshot.lifetime = .keepAlways;add(screenshot)
    }
}
