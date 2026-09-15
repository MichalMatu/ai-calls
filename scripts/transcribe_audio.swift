#!/usr/bin/env swift

import Foundation
import Speech

struct Options {
    let audioPath: String
    let locale: String
    let requestAuthorization: Bool
}

func usage() -> Never {
    fputs("usage: transcribe_audio.swift [--locale pl-PL] [--request-authorization] <audio.wav>\n", stderr)
    exit(2)
}

func parseOptions() -> Options {
    var args = Array(CommandLine.arguments.dropFirst())
    var locale = "pl-PL"
    var requestAuthorization = false
    var audioPath: String?

    while !args.isEmpty {
        let arg = args.removeFirst()
        switch arg {
        case "--locale":
            guard !args.isEmpty else { usage() }
            locale = args.removeFirst()
        case "--request-authorization":
            requestAuthorization = true
        default:
            guard !arg.hasPrefix("-"), audioPath == nil else { usage() }
            audioPath = arg
        }
    }

    guard let audioPath else { usage() }
    return Options(audioPath: audioPath, locale: locale, requestAuthorization: requestAuthorization)
}

func authorizationName(_ status: SFSpeechRecognizerAuthorizationStatus) -> String {
    switch status {
    case .notDetermined: return "not_determined"
    case .denied: return "denied"
    case .restricted: return "restricted"
    case .authorized: return "authorized"
    @unknown default: return "unknown"
    }
}

func requestAuthorizationIfNeeded(_ requested: Bool) -> SFSpeechRecognizerAuthorizationStatus {
    var status = SFSpeechRecognizer.authorizationStatus()
    if status == .notDetermined && requested {
        let semaphore = DispatchSemaphore(value: 0)
        SFSpeechRecognizer.requestAuthorization { newStatus in
            status = newStatus
            semaphore.signal()
        }
        _ = semaphore.wait(timeout: .now() + 30)
        status = SFSpeechRecognizer.authorizationStatus()
    }
    return status
}

let options = parseOptions()
let audioURL = URL(fileURLWithPath: options.audioPath)
guard FileManager.default.fileExists(atPath: audioURL.path) else {
    fputs("error=audio file does not exist\n", stderr)
    exit(2)
}

let authorization = requestAuthorizationIfNeeded(options.requestAuthorization)
print("speech_authorization=\(authorizationName(authorization))")
guard authorization == .authorized else {
    fputs("error=speech recognition is not authorized; rerun with --request-authorization when ready\n", stderr)
    exit(3)
}

guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: options.locale)) else {
    fputs("error=recognizer unavailable for locale \(options.locale)\n", stderr)
    exit(4)
}

let request = SFSpeechURLRecognitionRequest(url: audioURL)
request.shouldReportPartialResults = false
if recognizer.supportsOnDeviceRecognition {
    request.requiresOnDeviceRecognition = true
    print("recognition_mode=on_device")
} else {
    print("recognition_mode=system_default")
}

let semaphore = DispatchSemaphore(value: 0)
var finalText: String?
var finalError: Error?

let task = recognizer.recognitionTask(with: request) { result, error in
    if let result, result.isFinal {
        finalText = result.bestTranscription.formattedString
        semaphore.signal()
    } else if let error {
        finalError = error
        semaphore.signal()
    }
}

if semaphore.wait(timeout: .now() + 30) == .timedOut {
    task.cancel()
    fputs("error=recognition timed out\n", stderr)
    exit(5)
}

if let finalError {
    fputs("error=\(finalError.localizedDescription)\n", stderr)
    exit(6)
}

guard let finalText, !finalText.isEmpty else {
    fputs("error=no transcription produced\n", stderr)
    exit(7)
}

print("transcript=\(finalText.replacingOccurrences(of: "\n", with: " "))")
