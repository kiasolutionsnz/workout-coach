import XCTest
final class AccountUITests:XCTestCase {
    func testFailedSignInKeepsGuestWorkoutsAvailable(){
        let app = XCUIApplication();app.launch()
        XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout:15));app.buttons["Settings"].tap()
        let email = app.textFields["Email"];XCTAssertTrue(email.waitForExistence(timeout:10));email.tap();email.typeText("invalid-agent@qualification.invalid")
        let password = app.secureTextFields["Password"];password.tap();password.typeText("invalid-synthetic-password")
        app.buttons["Sign in"].tap()
        XCTAssertTrue(app.staticTexts["Couldn’t sign in. Check your details or try again. Guest workouts are available."].waitForExistence(timeout:30))
        let image = XCTAttachment(screenshot:app.screenshot());image.lifetime = .keepAlways;add(image)
    }
}
