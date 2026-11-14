import base64
import json
from pathlib import Path
import tempfile
import threading
import unittest
from server import MusicStore, parse_output, prompt_for, validate


class MusicTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)
        self.body = {'story': '가족과 바다를 보고 웃었다.'}

    def test_requires_story_and_bounded_numbers(self):
        for body in ({}, {'story': 'x' * 2001}, dict(self.body, bpm=True), dict(self.body, windows=65)):
            with self.assertRaises(ValueError):
                validate(body)

    def test_prompt_contains_lyrics_weather_and_vehicle_instruments(self):
        prompt = prompt_for(validate(dict(self.body, genre='HipHop', weather='Rain', wipers=3,
                                         windows=2, doors=1, signals=2, rapMode=True)))
        for text in ('Korean', 'rap verse', 'Rain', 'HipHop', 'shaker', 'bell', 'claps', 'rimshot', self.body['story']):
            self.assertIn(text, prompt)

    def test_parses_audio_and_lyrics_from_steps(self):
        audio = b'ID3' + bytes(2048)
        result = parse_output({'steps': [{'type': 'model_output', 'content': [
            {'type': 'text', 'text': '함께 떠난 우리'},
            {'type': 'audio', 'mime_type': 'audio/mpeg', 'data': base64.b64encode(audio).decode()}]}]})
        self.assertEqual(result, (audio, '함께 떠난 우리'))

    def test_missing_audio_is_not_success(self):
        with self.assertRaises(ValueError):
            parse_output({'steps': [{'type': 'model_output', 'content': [{'type': 'text', 'text': 'lyrics'}]}]})

    def test_duplicate_creation_calls_provider_once_and_survives_restart(self):
        release = threading.Event()
        called = []
        def provider(_):
            called.append(True)
            release.wait(2)
            return b'ID3' + bytes(2048), '가사'
        store = MusicStore(self.path, 'test', provider)
        first = store.start(self.body)
        second = store.start(self.body)
        self.assertEqual(first['id'], second['id'])
        release.set()
        import time
        for _ in range(50):
            if store.get(first['id'])['status'] == 'ready': break
            time.sleep(.01)
        reopened = MusicStore(self.path, 'test', provider)
        self.assertEqual(reopened.start(self.body)['status'], 'ready')
        self.assertEqual(len(called), 1)
        self.assertEqual(reopened.latest()['lyrics'], '가사')

    def test_daily_limit_does_not_block_cached_songs(self):
        import time
        store = MusicStore(self.path, 'test', lambda _: (bytes(2048), '가사'), daily_limit=1)
        first = store.start(self.body)
        for _ in range(50):
            if store.get(first['id'])['status'] == 'ready': break
            time.sleep(.01)
        self.assertEqual(store.start(self.body)['id'], first['id'])
        with self.assertRaisesRegex(ValueError, '하루 생성 한도'):
            store.start({'story': '다른 가족 여행 이야기'})

    def test_inflight_after_restart_does_not_retry(self):
        job = dict(id='a'*32, status='generating', created=0)
        (self.path / ('a'*32 + '.json')).write_text(json.dumps(job))
        store = MusicStore(self.path, 'test', lambda _: self.fail('must not generate'))
        self.assertEqual(store.get('a'*32)['status'], 'uncertain')
        self.assertIsNone(store.get('../secret'))


if __name__ == '__main__':
    unittest.main()
