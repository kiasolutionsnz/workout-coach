import XCTest
import UIKit
import AVFoundation
import MediaPipeTasksVision
import WorkoutCore
@testable import WorkoutCoach

final class CameraTests: XCTestCase {
    func testBundledModelRunsOfflineOnBlankFrame() throws {
        let image = UIGraphicsImageRenderer(size:CGSize(width:64,height:64)).image { context in UIColor.black.setFill();context.fill(CGRect(x:0,y:0,width:64,height:64)) }
        let processor = try IOSPoseProcessor()
        let frame = try XCTUnwrap(processor.process(image:image,atMillis:0))
        XCTAssertTrue(frame.joints.isEmpty)
        XCTAssertNil(try processor.process(image:image,atMillis:0))
        XCTAssertNotNil(try processor.process(image:image,atMillis:1))
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
        let camera = IOSCameraController(clock:{0},onPose:{frame in delivered += 1;XCTAssertEqual(frame.sequence,99)})
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
