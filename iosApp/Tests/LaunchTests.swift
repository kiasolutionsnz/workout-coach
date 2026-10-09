import XCTest
final class LaunchTests: XCTestCase {
    func testNativeAppLaunch() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["Workout Coach"].waitForExistence(timeout: 15))
    }
}

