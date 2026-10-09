import AVFoundation
import SwiftUI
import WorkoutCore

enum IOSCameraStatus: Equatable { case stopped, consentRequired, permissionRequired, starting, running, interrupted, unavailable }

final class IOSCameraController: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    let session = AVCaptureSession()
    @Published private(set) var status: IOSCameraStatus = .stopped
    private let queue = DispatchQueue(label: "workout.camera.inference")
    private let clock: () -> Int64
    private let onPose: (PoseFrame) -> Void
    private let onInterruption: () -> Void
    private let front: Bool
    private let consent: () -> Bool
    @Published private(set) var previewMirrored = true
    private var observers: [NSObjectProtocol] = []
    private var processor: IOSPoseProcessor?
    private var configured = false
    private var offset: Int64?
    private let lock = NSLock()
    private var closed = false
    private var latestPose: PoseFrame?
    private var deliveryScheduled = false
    init(clock: @escaping () -> Int64, onPose: @escaping (PoseFrame) -> Void = { _ in }, onInterruption: @escaping () -> Void = {}, front:Bool = true, consent: @escaping () -> Bool = { IOSSDKConsent.permitted }) {
        self.clock = clock; self.onPose = onPose; self.onInterruption = onInterruption;self.front = front;self.consent = consent
        super.init()
        observers.append(NotificationCenter.default.addObserver(forName:IOSSDKConsent.changed,object:nil,queue:.main) { [weak self] _ in if let self, !self.consent() { self.stopForConsent() } })
        observers.append(NotificationCenter.default.addObserver(forName:AVCaptureSession.wasInterruptedNotification,object:session,queue:.main) { [weak self] _ in self?.onInterruption();self?.publish(.interrupted) })
        observers.append(NotificationCenter.default.addObserver(forName:AVCaptureSession.runtimeErrorNotification,object:session,queue:.main) { [weak self] _ in self?.onInterruption();self?.publish(.unavailable) })
        observers.append(NotificationCenter.default.addObserver(forName:AVCaptureSession.interruptionEndedNotification,object:session,queue:.main) { [weak self] _ in self?.start() })
    }
    deinit { observers.forEach { NotificationCenter.default.removeObserver($0) } }
    private func publish(_ value: IOSCameraStatus) { DispatchQueue.main.async { self.lock.lock();let stopped = self.closed;self.lock.unlock();if !stopped || value == .stopped { self.status = value } } }
    func start(permission: AVAuthorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)) {
        guard permission == .authorized else { publish(.permissionRequired); return }
        guard consent() else { stopForConsent(); return }
        lock.lock(); let stopped = closed; lock.unlock()
        guard !stopped else { return }
        publish(.starting)
        queue.async { [self] in
            do {
                guard consent() else { stopForConsent(); return }
                if !configured {
                    guard let device = AVCaptureDevice.default(.builtInWideAngleCamera,for:.video,position:front ? .front:.back) ?? AVCaptureDevice.default(for:.video) else { publish(.unavailable); return }
                    DispatchQueue.main.async { self.previewMirrored = device.position == .front }
                    session.beginConfiguration(); defer { session.commitConfiguration() }
                    session.sessionPreset = .medium
                    let input = try AVCaptureDeviceInput(device:device)
                    let output = AVCaptureVideoDataOutput()
                    output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String:kCVPixelFormatType_32BGRA]
                    output.alwaysDiscardsLateVideoFrames = true
                    guard session.canAddInput(input), session.canAddOutput(output) else { publish(.unavailable); return }
                    session.addInput(input); session.addOutput(output)
                    output.setSampleBufferDelegate(self,queue:queue)
                    if let connection = output.connection(with:.video) {
                        if connection.isVideoOrientationSupported { connection.videoOrientation = .portrait }
                        if connection.isVideoMirroringSupported { connection.automaticallyAdjustsVideoMirroring = false; connection.isVideoMirrored = false }
                    }
                    configured = true
                }
                lock.lock(); let stopped = closed; lock.unlock()
                guard !stopped else { return }
                guard consent() else { stopForConsent(); return }
                if processor == nil { processor = try IOSPoseProcessor(consent:consent) }
                guard consent() else { stopForConsent(); return }
                session.startRunning(); publish(.running)
            } catch { publish(.unavailable) }
        }
    }
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        lock.lock(); let stopped = closed; lock.unlock()
        guard !stopped, consent() else { return }
        let seconds = CMSampleBufferGetPresentationTimeStamp(sampleBuffer).seconds
        guard seconds.isFinite, seconds >= 0, seconds < Double(Int64.max)/1000 else { return }
        let capture = Int64(seconds*1000)
        if offset == nil { offset = clock() - capture }
        let atMillis = capture + (offset ?? 0)
        guard atMillis >= 0, atMillis <= clock(), clock()-atMillis <= 250 else { return }
        do {
            if let frame = try processor?.process(sample:sampleBuffer,atMillis:atMillis) { deliver(frame) }
        } catch { session.stopRunning(); processor = nil; publish(.unavailable) }
    }
    func deliver(_ frame: PoseFrame) {
        guard consent() else { return }
        lock.lock();if closed { lock.unlock();return };latestPose = frame
        if deliveryScheduled { lock.unlock(); return }
        deliveryScheduled = true; lock.unlock()
        DispatchQueue.main.async { [self] in
            lock.lock(); let frame = latestPose; latestPose = nil; deliveryScheduled = false; let stopped = closed; lock.unlock()
            if !stopped, consent(), let frame { onPose(frame) }
        }
    }
    func stopForConsent() {
        lock.lock(); latestPose = nil; let stopped = closed; lock.unlock()
        guard !stopped else { return }
        onInterruption()
        publish(.consentRequired)
        queue.async { [self] in session.stopRunning(); processor = nil; offset = nil }
    }
    func close() {
        lock.lock(); if closed { lock.unlock(); return }; closed = true; latestPose = nil; lock.unlock()
        observers.forEach { NotificationCenter.default.removeObserver($0) };observers.removeAll()
        queue.async { [self] in session.stopRunning(); processor = nil; publish(.stopped) }
    }
    func suspend() { lock.lock();let stopped = closed;lock.unlock();guard !stopped else { return };onInterruption();queue.async { [self] in session.stopRunning();publish(.interrupted) } }
}

struct NativeCameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    var mirrored = true
    func makeUIView(context:Context) -> PreviewSurface { let view = PreviewSurface(); view.preview.session = session; view.preview.videoGravity = .resizeAspect; return view }
    func updateUIView(_ view:PreviewSurface,context:Context) { view.preview.session = session
        if let connection = view.preview.connection { if connection.isVideoOrientationSupported { connection.videoOrientation = .portrait }; if connection.isVideoMirroringSupported { connection.automaticallyAdjustsVideoMirroring = false; connection.isVideoMirrored = mirrored } }
    }
}
final class PreviewSurface: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var preview: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
}

struct CameraSetupView: View {
    @ObservedObject private var consent = IOSSDKConsent.shared
    @StateObject private var camera: IOSCameraController
    init(frontCamera:Bool = true) { let clock = MonotonicWorkoutClock();_camera = StateObject(wrappedValue:IOSCameraController(clock:{clock.nowMillis()},front:frontCamera)) }
    @State private var permission = AVCaptureDevice.authorizationStatus(for:.video)
    @Environment(\.scenePhase) private var scenePhase
    var body: some View {
        VStack(spacing:16) {
            Text("Place your phone where your whole body is visible. Camera processing stays on your phone.")
            if !consent.allowed {
                CameraSDKConsentView(onAccept:refreshPermission)
            } else if permission == .authorized {
                NativeCameraPreview(session:camera.session,mirrored:camera.previewMirrored)
                Text(camera.status == .unavailable ? "Camera unavailable. Close this screen and try again." : "Camera setup")
            } else {
                Text("Enable camera access to check your position.")
                Button(permission == .notDetermined ? "Enable camera" : "Open Settings") {
                    if permission == .notDetermined { AVCaptureDevice.requestAccess(for:.video) { _ in DispatchQueue.main.async { refreshPermission() } } }
                    else if let url = URL(string:UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                }.buttonStyle(.borderedProminent)
            }
        }.padding().navigationTitle("Camera setup")
        .onAppear { refreshPermission() }.onDisappear { camera.close() }
        .onChange(of:consent.allowed) { allowed in if allowed { refreshPermission() } else { camera.stopForConsent() } }
        .onChange(of:scenePhase) { phase in if phase == .active { refreshPermission() } else { camera.suspend() } }
    }
    private func refreshPermission() { permission = AVCaptureDevice.authorizationStatus(for:.video); if permission == .authorized && consent.allowed { camera.start() } }
}
