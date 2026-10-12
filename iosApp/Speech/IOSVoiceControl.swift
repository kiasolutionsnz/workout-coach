import SwiftUI
import Speech
import AVFoundation
import WorkoutCore

/** Foreground, opt-in, on-device only. No audio or transcripts are saved. */
final class IOSVoiceControl: ObservableObject {
    @Published private(set) var enabled = false
    @Published private(set) var status = "Voice control is off"
    var onCommand: (VoiceAction) -> Void = { _ in }
    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var generation = 0
    private var tapped = false
    static private(set) var capturing = false
    private var endObserver: NSObjectProtocol?
    private var interruptionObserver: NSObjectProtocol?
    init() {
        endObserver = NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification, object:nil, queue:.main) { [weak self] _ in self?.disable() }
        interruptionObserver = NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object:nil, queue:.main) { [weak self] notification in
            if let value = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt, value == AVAudioSession.InterruptionType.began.rawValue { self?.disable() }
        }
    }
    deinit { if let interruptionObserver { NotificationCenter.default.removeObserver(interruptionObserver) }; if let endObserver { NotificationCenter.default.removeObserver(endObserver) } }
    func enable() {
        guard !enabled else { return }
        generation += 1; let token = generation
        guard recognizer?.supportsOnDeviceRecognition == true else { status = "On-device English voice control unavailable. Use the buttons."; return }
        status = "Requesting voice permissions"
        SFSpeechRecognizer.requestAuthorization { [weak self] permission in
            DispatchQueue.main.async {
                guard let self, self.generation == token else { return }
                guard permission == .authorized else { self.status = "Speech permission is off. Use the buttons."; return }
                AVAudioSession.sharedInstance().requestRecordPermission { [weak self] allowed in
                    DispatchQueue.main.async {
                        guard let self, self.generation == token else { return }
                        guard allowed else { self.status = "Microphone permission is off. Use the buttons."; return }
                        self.enabled = true; self.listen()
                    }
                }
            }
        }
    }
    private func stopCapture() {
        Self.capturing = false; engine.stop(); if tapped { engine.inputNode.removeTap(onBus:0); tapped = false }
        request?.endAudio(); task?.cancel(); task = nil; request = nil
    }
    private func listen() {
        guard enabled, UIApplication.shared.applicationState == .active, let recognizer, recognizer.isAvailable, recognizer.supportsOnDeviceRecognition else { disable(); status = "On-device voice control unavailable. Use the buttons."; return }
        generation += 1; let token = generation
        stopCapture()
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode:.default, options:[.defaultToSpeaker, .duckOthers])
            try session.setActive(true)
            let request = SFSpeechAudioBufferRecognitionRequest(); request.requiresOnDeviceRecognition = true; request.shouldReportPartialResults = false
            self.request = request
            let input = engine.inputNode; let format = input.outputFormat(forBus:0)
            guard format.sampleRate > 0, format.channelCount > 0 else { throw VoiceError.unavailable }
            input.installTap(onBus:0, bufferSize:1024, format:format) { buffer, _ in request.append(buffer) }; tapped = true
            task = recognizer.recognitionTask(with:request) { [weak self] result, error in
                DispatchQueue.main.async {
                    guard let self, self.enabled, self.generation == token else { return }
                    if let result, result.isFinal {
                        let action = VoiceCommandsKt.parseVoiceCommand(text:result.bestTranscription.formattedString)
                        self.generation += 1; let restartToken = self.generation
                        self.stopCapture()
                        if let action { self.onCommand(action) }
                        DispatchQueue.main.asyncAfter(deadline:.now()+0.3) { [weak self] in
                            guard let self, self.enabled, self.generation == restartToken else { return }; self.listen()
                        }
                    } else if error != nil {
                        self.disable(); self.status = "Voice listening stopped. Tap Enable voice control to retry."
                    }
                }
            }
            engine.prepare(); try engine.start(); Self.capturing = true; status = "Listening on-device. Begin commands with Coach."
        } catch { disable(); status = "Voice control unavailable. Use the buttons." }
    }
    func disable() { enabled = false; generation += 1; stopCapture(); status = "Voice control is off" }
    private enum VoiceError: Error { case unavailable }
}

struct VoiceControlPanel: View {
    @ObservedObject var voice: IOSVoiceControl
    var body: some View {
        VStack(alignment:.leading, spacing:6) {
            Button(voice.enabled ? "Disable voice control" : "Enable voice control") { if voice.enabled { voice.disable() } else { voice.enable() } }
            if voice.enabled || voice.status != "Voice control is off" { Text(voice.status).font(.caption) }
            Text("Optional on-device English voice control. Audio and transcripts are not saved.").font(.caption)
            if voice.enabled { Text("Say Coach start, pause, resume, skip rest, end set, or end workout.").font(.caption) }
        }
    }
}
