import AVFoundation
import Speech
import SwiftUI

/// Turns a spoken dish name into text for the food search. Listens only while the microphone button is on.
@MainActor
final class DishDictation: ObservableObject {
    @Published private(set) var listening = false

    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: Lang.en ? "en-US" : "vi-VN"))
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?

    /// Starts listening, or stops if already listening. `onText` gets the words heard so far.
    func toggle(onText: @escaping (String) -> Void) {
        if listening {
            stop()
            return
        }
        SFSpeechRecognizer.requestAuthorization { status in
            Task { @MainActor in
                guard status == .authorized else { return }
                self.begin(onText: onText)
            }
        }
    }

    private func begin(onText: @escaping (String) -> Void) {
        guard let recognizer, recognizer.isAvailable else { return }
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        self.request = request

        let input = engine.inputNode
        input.removeTap(onBus: 0)
        input.installTap(onBus: 0, bufferSize: 1024, format: input.outputFormat(forBus: 0)) { buffer, _ in
            request.append(buffer)
        }
        do {
            try AVAudioSession.sharedInstance().setCategory(.record, mode: .measurement, options: .duckOthers)
            try AVAudioSession.sharedInstance().setActive(true, options: .notifyOthersOnDeactivation)
            engine.prepare()
            try engine.start()
        } catch {
            stop()
            return
        }
        listening = true
        task = recognizer.recognitionTask(with: request) { result, error in
            Task { @MainActor in
                if let result { onText(result.bestTranscription.formattedString) }
                if error != nil || result?.isFinal == true { self.stop() }
            }
        }
    }

    func stop() {
        if engine.isRunning { engine.stop() }
        engine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        task?.cancel()
        request = nil
        task = nil
        listening = false
    }
}
