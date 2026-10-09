import XCTest

final class OfflineBetaUITests:XCTestCase {
    func testSDKMetricsConsentCanBeDeclinedAcceptedAndWithdrawn() {
        let app = XCUIApplication();app.launchArguments = ["--offline-beta","--reset-sdk-consent"];app.launch()
        XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout:15));app.buttons["Settings"].tap()
        let allow = app.buttons["Allow SDK metrics and camera counting"]
        for _ in 0..<5 { if allow.isHittable { break };app.swipeUp() }
        XCTAssertTrue(allow.exists)
        app.buttons["Not now"].tap()
        XCTAssertTrue(app.staticTexts["sdk-consent-declined"].exists)
        XCTAssertFalse(app.buttons["Withdraw camera SDK consent"].exists)
        allow.tap()
        let withdraw = app.buttons["Withdraw camera SDK consent"]
        XCTAssertTrue(withdraw.waitForExistence(timeout:5));withdraw.tap()
        XCTAssertTrue(allow.waitForExistence(timeout:5))
        XCTAssertFalse(withdraw.exists)
        let image = XCTAttachment(screenshot:app.screenshot());image.lifetime = .keepAlways;add(image)
    }

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
