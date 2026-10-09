import XCTest

final class OfflineBetaUITests:XCTestCase {
    func testOfflineBetaHasNoAccountControlsAndKeepsRoutinesAvailable(){
        let app = XCUIApplication();app.launchArguments = ["--offline-beta"];app.launch()
        XCTAssertTrue(app.buttons["New routine"].waitForExistence(timeout:15))
        XCTAssertTrue(app.buttons["New routine"].isEnabled)
        XCTAssertTrue(app.buttons["History"].exists)
        XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout:15))
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.staticTexts["Offline beta: workouts and camera processing stay on your phone. Accounts and cloud sync are not included."].waitForExistence(timeout:10))
        XCTAssertFalse(app.textFields["Email"].exists)
        XCTAssertFalse(app.secureTextFields["Password"].exists)
        XCTAssertFalse(app.buttons["Sign in"].exists)
        let image = XCTAttachment(screenshot:app.screenshot());image.lifetime = .keepAlways;add(image)
    }
}
