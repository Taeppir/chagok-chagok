# Personal soundtrack bridge

A small local server connects the Pleos app to Google Lyria 3.5. It sends a travel story, selected weather and an arrangement summary, then stores the returned Korean lyrics and MP3. Python 3.9 or later is sufficient; no packages are required.

## Start on the development Mac

1. Put `GEMINI_API_KEY=your_key` in `server/.env`. This file is ignored by Git. Do not put the key in the Android app.
2. From the repository root run:

```sh
python3 -B server/server.py --daily-limit 10
```

3. In another terminal connect the running emulator:

```sh
adb -s emulator-5554 reverse tcp:8787 tcp:8787
```

4. Finish a recorded trip while parked, select a genre in **주행 리포트**, and choose **이야기를 더해 노래 만들기**. Review the story in **이야기 노트**, choose **노래 만들기**, then confirm the optional names and generation cost. Generation continues on the local server while the app polls for the result.

The bridge binds only to `127.0.0.1:8787`. Cleartext Android traffic is allowed only for that loopback address. It rejects browser-origin requests and does not expose credentials or personal stories in access logs. The process must remain running while generating/downloading a new song. A downloaded song remains playable after stopping the server.

## Cost controls

- Playback uses an MP3 saved in app-private storage. Replays make **zero Google API requests** and incur no generation/token charges.
- Story edits, genre changes and vehicle events do not generate songs automatically.
- A deterministic hash of the normalized story and arrangement identifies a request. Repeated identical requests reuse its existing job or song, including after restarting the server.
- Only one new generation can be in flight. The default daily cap is 10 new requests (server-local calendar day). Failed or uncertain requests also count. Reading saved songs is unaffected by this cap.
- There are no automatic retries of paid POST requests. If the server loses a response, the result is marked uncertain; do not delete the job and regenerate without checking the provider's usage first.
- Status polling contacts this local bridge, not Google. The bridge does not separately call a language model for lyrics: Lyria returns lyrics and audio in one generation.
- The official price checked on 2026-11-14 was $0.08 per Lyria 3.5 song, excluding applicable taxes. A 10-request limit corresponds to $0.80 in base generation charges per day at that price; it is not a cap on other projects or services in the billing account.

Files in `server/generated/` include lyrics, source story and audio and are ignored by Git. Keep this folder to preserve the deduplication cache. Back it up privately if the project matters. Removing it clears this local generation history and daily counter.

## Validation

```sh
python3 -B -m unittest discover -s server -v
```

Tests cover request bounds, prompt mapping, response parsing, duplicate suppression across restarts, interrupted jobs and daily limits. A real example about a family trip to Gangneung produced a 130-second MP3 and Korean lyrics, and was played in the Pleos emulator. Generated duration is model-controlled; the 90-second prompt is a preference, not a hard limit.

## Deployment boundary

This is a **single-user local development bridge**, not a publicly deployable multi-user backend. Production needs authenticated users, per-user private song storage, an atomic durable job queue, per-user/project budgets, HTTPS, retention/deletion controls and provider usage reconciliation. Do not expose this process to the public network. In a multi-user service, cache keys must be scoped to the owning user; never share family stories or song files across accounts.

[Google music generation documentation](https://ai.google.dev/gemini-api/docs/music-generation) · [Pricing](https://ai.google.dev/gemini-api/docs/pricing/)

## Optional lyric timing

`ALIGN_LYRICS=1` enables one additional audio-analysis request after a song is generated. It is off by default to avoid extra inference costs. Timing is best-effort; failure preserves the song and plain lyrics without automatically retrying. The app shows plain scrollable lyrics when timing is unavailable. The $0.08 music price does not include this optional analysis.

Names are optional and part of the request cache key. Instrument change counts determine sparse, steady or rich texture instructions. The generator receives the arrangement as text, not the locally synthesized waveform.
