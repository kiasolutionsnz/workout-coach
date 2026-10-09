import XCTest
import UIKit
import AVFoundation
import MediaPipeTasksVision
import WorkoutCore
@testable import WorkoutCoach

final class CameraTests: XCTestCase {
    func testBundledModelRunsOfflineOnBlankFrame() throws {
        let image = UIGraphicsImageRenderer(size:CGSize(width:64,height:64)).image { context in UIColor.black.setFill();context.fill(CGRect(x:0,y:0,width:64,height:64)) }
        let processor = try IOSPoseProcessor(consent:{true})
        let frame = try XCTUnwrap(processor.process(image:image,atMillis:0))
        XCTAssertTrue(frame.joints.isEmpty)
        XCTAssertNil(try processor.process(image:image,atMillis:0))
        XCTAssertNotNil(try processor.process(image:image,atMillis:1))
    }
    func testConsentRequiredBeforeSDKConstructionOrCameraStart() {
        XCTAssertThrowsError(try IOSPoseProcessor(bundle:Bundle(for:CameraTests.self),consent:{false})) { error in
            guard case PoseAdapterError.consentRequired = error else { return XCTFail("SDK initialized before consent check") }
        }
        let complete = expectation(description:"consent required")
        let camera = IOSCameraController(clock:{0},consent:{false})
        camera.start(permission:.authorized)
        DispatchQueue.main.async { XCTAssertEqual(camera.status,.consentRequired);XCTAssertFalse(camera.session.isRunning);camera.close();complete.fulfill() }
        wait(for:[complete],timeout:5)
    }
    func testWithdrawalDropsQueuedFramesAndPreventsFurtherInference() throws {
        var allowed = true
        let processor = try IOSPoseProcessor(consent:{allowed})
        let image = UIGraphicsImageRenderer(size:CGSize(width:64,height:64)).image { _ in }
        allowed = false
        XCTAssertThrowsError(try processor.process(image:image,atMillis:0))
        let complete = expectation(description:"withdrawal drops delivery")
        var delivered = 0
        allowed = true
        let camera = IOSCameraController(clock:{0},onPose:{_ in delivered += 1},consent:{allowed})
        DispatchQueue.main.async {
            camera.deliver(canonicalPose([],width:100,height:200,sequence:1,atMillis:0))
            allowed = false
            camera.stopForConsent()
            DispatchQueue.main.async { XCTAssertEqual(delivered,0);camera.close();complete.fulfill() }
        }
        wait(for:[complete],timeout:5)
    }
    func testConsentIsOptInAndWithdrawalPersists() {
        let name = "workout-consent-test-" + UUID().uuidString
        let defaults = UserDefaults(suiteName:name)!
        defer { defaults.removePersistentDomain(forName:name) }
        let consent = IOSSDKConsent(defaults:defaults)
        XCTAssertFalse(consent.allowed)
        consent.accept()
        XCTAssertTrue(IOSSDKConsent(defaults:defaults).allowed)
        consent.withdraw()
        XCTAssertFalse(IOSSDKConsent(defaults:defaults).allowed)
    }
    func testCanonicalMappingUsesCurrentConfidenceAndUprightCoordinates() {
        let landmarks = (0..<33).map { _ in NormalizedLandmark(x:0.25,y:0.5,z:0,visibility:NSNumber(value:0.8),presence:NSNumber(value:0.4)) }
        let frame = canonicalPose(landmarks,width:200,height:100,sequence:1,atMillis:30)
        XCTAssertEqual(frame.joints.count,15)
        XCTAssertEqual(frame.joints[.leftKnee]?.confidence ?? -1,0.4,accuracy:0.0001)
        XCTAssertFalse(frame.transform.mirrored)
        XCTAssertEqual(frame.transform.height,100)
    }
    func testDeniedPermissionDoesNotStartCapture() {
        let expected = expectation(description:"permission denied")
        let camera = IOSCameraController(clock:{0})
        camera.start(permission:.denied)
        DispatchQueue.main.async { XCTAssertEqual(camera.status,.permissionRequired);XCTAssertFalse(camera.session.isRunning);camera.close();camera.close();expected.fulfill() }
        wait(for:[expected],timeout:5)
    }
    func testLatestDeliveryIsBoundedAndClosingDropsQueuedFrames() {
        let complete = expectation(description:"latest delivery")
        var delivered = 0
        let camera = IOSCameraController(clock:{0},onPose:{frame in delivered += 1;XCTAssertEqual(frame.sequence,99)},consent:{true})
        DispatchQueue.main.async {
            for sequence in 0..<100 { camera.deliver(canonicalPose([],width:100,height:200,sequence:Int64(sequence),atMillis:Int64(sequence))) }
            DispatchQueue.main.async {
                XCTAssertEqual(delivered,1);camera.close()
                camera.deliver(canonicalPose([],width:100,height:200,sequence:100,atMillis:100))
                DispatchQueue.main.async { XCTAssertEqual(delivered,1);complete.fulfill() }
            }
        }
        wait(for:[complete],timeout:5)
    }
    func testInterruptionNotificationIsObservableAndCloseRemovesObservers() {
        let complete = expectation(description:"interruption")
        var interruptions = 0
        let camera = IOSCameraController(clock:{0},onInterruption:{interruptions += 1})
        NotificationCenter.default.post(name:AVCaptureSession.wasInterruptedNotification,object:camera.session)
        DispatchQueue.main.async {
            XCTAssertEqual(camera.status,.interrupted);XCTAssertEqual(interruptions,1)
            camera.close();NotificationCenter.default.post(name:AVCaptureSession.wasInterruptedNotification,object:camera.session)
            XCTAssertEqual(interruptions,1);complete.fulfill()
        }
        wait(for:[complete],timeout:5)
    }
}
