import XCTest
final class WorkoutTests:XCTestCase {
    func testSyntheticWorkoutProgressesHandsFreeAndSavesBothSets(){
        let app = XCUIApplication();app.launchArguments = ["--workout-fixture"];app.launch()
        let begin = app.buttons["Begin countdown"];XCTAssertTrue(begin.waitForExistence(timeout:15))
        XCUIDevice.shared.press(.home);app.activate()
        let resume = app.buttons["Resume"];XCTAssertTrue(resume.waitForExistence(timeout:10));resume.tap()
        let enabled = NSPredicate(format:"enabled == true")
        expectation(for:enabled,evaluatedWith:begin);waitForExpectations(timeout:10);begin.tap()
        XCTAssertTrue(app.staticTexts["Workout complete"].waitForExistence(timeout:20))
        XCTAssertTrue(app.staticTexts["Completed sets saved on this phone."].waitForExistence(timeout:5))
        let summary = XCTAttachment(screenshot:app.screenshot());summary.lifetime = .keepAlways;add(summary)
        app.buttons["Done"].tap()
        app.terminate();app.launchArguments = [];app.launch()
        let history = app.buttons["History"];XCTAssertTrue(history.waitForExistence(timeout:15));history.tap()
        let routine = app.staticTexts["Synthetic two holds"].firstMatch;XCTAssertTrue(routine.waitForExistence(timeout:10));routine.tap()
        XCTAssertTrue(app.buttons["Export JSON"].waitForExistence(timeout:5))
        let detail = XCTAttachment(screenshot:app.screenshot());detail.lifetime = .keepAlways;add(detail)
        app.buttons["Delete workout"].tap();let cancel = app.buttons["Cancel"];if !cancel.waitForExistence(timeout:5){print(app.debugDescription)};XCTAssertTrue(cancel.exists);cancel.tap()
        XCTAssertTrue(app.buttons["Export JSON"].exists)
        app.buttons["Delete workout"].tap();app.buttons["Delete"].tap()
        XCTAssertTrue(app.navigationBars["History"].waitForExistence(timeout:10))
        let image = XCTAttachment(screenshot:app.screenshot());image.lifetime = .keepAlways;add(image)
    }
}
