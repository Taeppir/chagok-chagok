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

    def test_personalization_changes_the_recipe_without_breaking_empty_name_cache(self):
        base = validate(self.body)
        self.assertEqual(base, validate(dict(self.body, names='  ')))
        variants = [dict(self.body, story='캠핑에서 별을 보았다.'), dict(self.body, names='하나, 두리'),
                    dict(self.body, bpm=104, syncopated=True), dict(self.body, wipers=2),
                    dict(self.body, wipers=16)]
        prompts = [prompt_for(base)] + [prompt_for(validate(v)) for v in variants]
        self.assertEqual(len(set(prompts)), len(prompts))
        self.assertIn('하나, 두리', prompts[2])
        self.assertIn('sparse accents', prompts[4])
        self.assertIn('rich rhythmic layer', prompts[5])
        for names in (None, ['name'], 'x' * 61):
            with self.assertRaises(ValueError):
                validate(dict(self.body, names=names))

    def test_saved_song_retains_the_vehicle_recipe_used_for_generation(self):
        import time
        sent = []
        def provider(recipe):
            sent.append(recipe)
            return bytes(2048), '가사'
        store = MusicStore(self.path, 'test', provider)
        body = dict(self.body, names='하나', bpm=104, wipers=12, windows=4, doors=2, signals=9)
        job = store.start(body)
        for _ in range(50):
            if store.get(job['id'])['status'] == 'ready': break
            time.sleep(.01)
        saved = store.get(job['id'])
        self.assertEqual(saved['recipe'], validate(body))
        self.assertEqual(saved['recipe'], sent[0])
        self.assertEqual(store.start(dict(body))['id'], job['id'])
        self.assertEqual(len(sent), 1)

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
