import UIKit
import CoreMedia
import MediaPipeTasksVision
import WorkoutCore

enum PoseAdapterError: Error { case missingModel, invalidImage, consentRequired }

final class IOSPoseProcessor {
    private let model: PoseLandmarker
    private let consent: () -> Bool
    private var lastTimestamp: Int64 = -1
    private var sequence: Int64 = 0
    init(bundle: Bundle = .main, consent: @escaping () -> Bool = { IOSSDKConsent.permitted }) throws {
        guard consent() else { throw PoseAdapterError.consentRequired }
        self.consent = consent
        guard let path = bundle.path(forResource: "pose_landmarker_lite", ofType: "task") else { throw PoseAdapterError.missingModel }
        let options = PoseLandmarkerOptions()
        options.baseOptions.modelAssetPath = path
        options.runningMode = .video
        options.numPoses = 1
        options.minPoseDetectionConfidence = 0.65
        options.minPosePresenceConfidence = 0.65
        options.minTrackingConfidence = 0.65
        model = try PoseLandmarker(options: options)
    }
    func process(image: UIImage, atMillis: Int64) throws -> PoseFrame? {
        guard consent() else { throw PoseAdapterError.consentRequired }
        // UIKit draws EXIF orientation into pixels. Inference always receives an upright image.
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        let upright = UIGraphicsImageRenderer(size: image.size, format: format).image { _ in image.draw(in: CGRect(origin: .zero, size: image.size)) }
        guard let cg = upright.cgImage else { throw PoseAdapterError.invalidImage }
        return try process(image: MPImage(uiImage: upright), width: cg.width, height: cg.height, atMillis: atMillis)
    }
    func process(sample: CMSampleBuffer, atMillis: Int64) throws -> PoseFrame? {
        guard consent() else { throw PoseAdapterError.consentRequired }
        guard let pixel = CMSampleBufferGetImageBuffer(sample) else { throw PoseAdapterError.invalidImage }
        return try process(image: MPImage(sampleBuffer: sample), width: CVPixelBufferGetWidth(pixel), height: CVPixelBufferGetHeight(pixel), atMillis: atMillis)
    }
    private func process(image: MPImage, width: Int, height: Int, atMillis: Int64) throws -> PoseFrame? {
        guard consent() else { throw PoseAdapterError.consentRequired }
        guard atMillis >= 0, atMillis > lastTimestamp else { return nil }
        lastTimestamp = atMillis
        let result = try model.detect(videoFrame: image, timestampInMilliseconds: Int(atMillis))
        let frame = canonicalPose(result.landmarks.first ?? [], width: width, height: height, sequence: sequence, atMillis: atMillis)
        sequence += 1
        return frame
    }
}

func canonicalPose(_ landmarks: [NormalizedLandmark], width: Int, height: Int, sequence: Int64, atMillis: Int64) -> PoseFrame {
    let indices: [(Joint, Int)] = [(.nose,0),(.leftEar,7),(.rightEar,8),(.leftShoulder,11),(.rightShoulder,12),(.leftElbow,13),(.rightElbow,14),(.leftWrist,15),(.rightWrist,16),(.leftHip,23),(.rightHip,24),(.leftKnee,25),(.rightKnee,26),(.leftAnkle,27),(.rightAnkle,28)]
    var joints: [Joint: WorkoutCore.Landmark] = [:]
    for (joint, index) in indices where index < landmarks.count {
        let landmark = landmarks[index]
        let x = Double(landmark.x), y = Double(landmark.y)
        let confidence = min(landmark.visibility?.doubleValue ?? 0, landmark.presence?.doubleValue ?? 0)
        guard x.isFinite, y.isFinite, confidence.isFinite else { continue }
        joints[joint] = WorkoutCore.Landmark(point: Point2(x:x,y:y), confidence: max(0,min(1,confidence)))
    }
    return PoseFrame(sequence:sequence,capturedAtMillis:atMillis,transform:ImageTransform(width:Int32(width),height:Int32(height),rotation:.none,mirrored:false),joints:joints)
}
