import AVFoundation
import WorkoutCore

final class IOSSpeaker:NSObject,CueSink,AVSpeechSynthesizerDelegate {
    private let synthesizer = AVSpeechSynthesizer()
    private let voice = AVSpeechSynthesisVoice.speechVoices().first { $0.language == "en-NZ" && $0.quality == .default } ?? AVSpeechSynthesisVoice.speechVoices().first { $0.language == "en-US" && $0.quality == .default }
    private var identities:[ObjectIdentifier:String] = [:]
    private var closed = false
    private(set) var available = false
    var onFinished:(String)->Void = {_ in}
    var onInterruption:(Bool)->Void = {_ in}
    private var observers:[NSObjectProtocol] = []
    override init() {
        super.init();available = voice != nil;synthesizer.delegate = self
        observers.append(NotificationCenter.default.addObserver(forName:AVAudioSession.interruptionNotification,object:nil,queue:.main){[weak self] notification in
            guard let raw = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt, let type = AVAudioSession.InterruptionType(rawValue:raw) else { return }
            if type == .began { self?.stop();self?.onInterruption(true) }else{self?.onInterruption(false)}
        })
        observers.append(NotificationCenter.default.addObserver(forName:AVAudioSession.routeChangeNotification,object:nil,queue:.main){[weak self] notification in
            guard let reason = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt, reason == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue else { return }
            self?.stop();self?.onInterruption(true)
        })
    }
    func speak(cue:SpeechCue)->Bool {
        guard !closed,available,let voice else { return false }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback,mode:.spokenAudio,options:[.duckOthers]);try session.setActive(true)
            let utterance = AVSpeechUtterance(string:cue.text);utterance.voice = voice;utterance.rate = AVSpeechUtteranceDefaultSpeechRate
            identities[ObjectIdentifier(utterance)] = cue.id;synthesizer.speak(utterance);return true
        } catch { available = false;return false }
    }
    func stop() { identities.removeAll();synthesizer.stopSpeaking(at:.immediate);try? AVAudioSession.sharedInstance().setActive(false,options:.notifyOthersOnDeactivation) }
    func speechSynthesizer(_ synthesizer:AVSpeechSynthesizer,didFinish utterance:AVSpeechUtterance) {
        guard !closed,let id = identities.removeValue(forKey:ObjectIdentifier(utterance)) else { return }
        try? AVAudioSession.sharedInstance().setActive(false,options:.notifyOthersOnDeactivation);onFinished(id)
    }
    func speechSynthesizer(_ synthesizer:AVSpeechSynthesizer,didCancel utterance:AVSpeechUtterance) { identities.removeValue(forKey:ObjectIdentifier(utterance)) }
    func close() { guard !closed else { return };closed = true;stop();synthesizer.delegate = nil;available = false;observers.forEach{NotificationCenter.default.removeObserver($0)};observers.removeAll() }
    deinit { observers.forEach{NotificationCenter.default.removeObserver($0)} }
}
