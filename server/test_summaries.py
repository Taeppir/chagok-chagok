import tempfile
import unittest
from pathlib import Path
from summaries import summarize

class SummaryTests(unittest.TestCase):
    def test_cache_prevents_second_request(self):
        with tempfile.TemporaryDirectory() as d:
            calls=[]
            def generate(t): calls.append(t); return '바다로 가는 길에 노래를 불렀다.'
            first=summarize('바다로 가자. 노래 부를까?', '', d, generate)
            self.assertEqual(first,summarize('바다로 가자. 노래 부를까?', '', d, generate))
            self.assertEqual(len(calls),1)
    def test_uncertain_request_is_not_retried(self):
        with tempfile.TemporaryDirectory() as d:
            def fail(t): raise TimeoutError()
            with self.assertRaises(TimeoutError): summarize('오늘도 여행', '', d, fail)
            with self.assertRaises(ValueError): summarize('오늘도 여행', '', d, lambda t:self.fail('paid retry'))
    def test_bad_input_never_calls_model(self):
        with tempfile.TemporaryDirectory() as d:
            for t in [None,{},'', '가'*60001]:
                with self.assertRaises(ValueError): summarize(t,'',d,lambda t:self.fail())
    def test_long_result_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(ValueError): summarize('우리 여행', '',d,lambda t:'가'*2001)

if __name__=='__main__': unittest.main()
