# Telephony

Incoming answering uses AI or manual mode on the selected SIM. Takeover returns control to the user. Resuming needs authorization. The runtime requests END_CALL after TTS.

Outgoing tasks require user approval, a time window and an exact SIM. BUSY and NO_ANSWER can be retried under the approved policy. The new task wizard selects WAV recording by default. Untick it to skip recording. Copies preserve the original choice.

## Development priorities

1. Finish consented English and Polish calls on the S22+. Verify the exact Call and SIM, two-way audio, silence after STOP, takeover and the full goodbye before END_CALL. Pin results to the APK SHA.
2. Measure time to GSM readiness after install, reboot and a Wi-Fi change. Show pairing, authorization and host readiness separately so a user can recover at the failed step.
3. Count useful callback notes, missed calls and manual recoveries. Use the results to decide whether to run a paid pilot on one business SIM.

## Patterns from AI Call Agent

AI Call Agent confirms a reply using two UI signals, a cleared input field and a new reply in the transcript. Apply the pattern to our GSM tests by checking the playback completion event and what the caller actually hears. [Upstream reply verification](https://github.com/MKtalk/AICallAgent/blob/222a3c2504767159260a55f34a9ca1e426732212/app/src/main/java/com/aicallagent/app/model/AutoReplyFlow.java#L492-L517)

Use Samsung Text Call as a setup benchmark on a compatible Galaxy. Keep Telecom and PCM as the MVP path. For any future code reuse, record the upstream SHA, check file licenses, mark changes and carry the applicable license and notices. [Upstream NOTICE](https://github.com/MKtalk/AICallAgent/blob/222a3c2504767159260a55f34a9ca1e426732212/NOTICE) · [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)
