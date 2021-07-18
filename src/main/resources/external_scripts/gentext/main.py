import argparse
import json
import os
import time
from concurrent.futures import ThreadPoolExecutor, ProcessPoolExecutor, as_completed

import markovify

parser = argparse.ArgumentParser(description='gentextコマンドで使用するマルコフ連鎖による文章生成スクリプト')
parser.add_argument(
    "--source",
    default="default.json",
    help='文章ソースとするJSONファイル'
)
parser.add_argument(
    "--count",
    type=int,
    default=1,
    help='いくつ文章を生成するか',
)
parser.add_argument(
    "--long-generate",
    action='store_false',
    help='文章生成時に文字数制限をしないか',
)


def generate(model, long_generate=False):
    if long_generate:
        sentence = model.make_sentence()
    else:
        sentence = model.make_short_sentence(100)

    if sentence is None:
        if long_generate:
            sentence = model.make_sentence()
        else:
            sentence = model.make_short_sentence(100)

    if sentence is None:
        return None

    return "".join(sentence.split())


if __name__ == '__main__':
    print(json.dumps({
        "generated": False,
        "phase": 1,
        "message": "処理開始"
    }))
    before_time = time.time()

    args = parser.parse_args()
    filename = args.source
    generate_count = args.count
    if generate_count > 100:
        print(json.dumps({
            "message": "CANNOT SET GENERATE COUNT MORE THAN 100."
        }))
        exit(1)

    if not filename.endswith(".json"):
        filename = filename + ".json"

    if "/" in filename:
        print(json.dumps({
            "message": "SOURCE FILE NAME IS NOT VALID."
        }))
        exit(1)

    path = os.path.dirname(os.path.abspath(__file__)) + "/sources/" + filename

    if not os.path.exists(path):
        print(json.dumps({
            "message": "SOURCE FILE ({}) IS NOT FOUND.".format(filename)
        }))
        exit(1)

    print(json.dumps({
        "generated": False,
        "phase": 2,
        "message": "ソースファイル読み込み中"
    }))
    with open(path, "r") as f:
        text_model = markovify.NewlineText.from_json(f.read())

    print(json.dumps({
        "generated": False,
        "phase": 3,
        "message": "文章生成中"
    }))
    if generate_count == 1:
        text = None
        while text is None:
            if args.long_generate:
                text = text_model.make_sentence()
            else:
                text = text_model.make_short_sentence(100)
        texts = ["".join(text.split())]
    else:
        with ProcessPoolExecutor() as executor:
            futures = []
            for i in range(generate_count):
                futures.append(executor.submit(generate, text_model, args.long_generate))

            texts = [f.result() for f in as_completed(futures)]

    countFailed = len(list(filter(lambda x: x is None, texts)))
    countDuplicated = len(list(set(filter(lambda x: x is not None, texts)))) - len(list(filter(lambda x: x is not None, texts)))

    texts = list(set(filter(lambda x: x is not None, texts)))

    after_time = time.time()
    print(json.dumps({
        "generated": True,
        "phase": 4,
        "texts": texts,
        "countFailed": countFailed,
        "countDuplicated": countDuplicated,
        "process_time": format(after_time - before_time, '.3f')
    }))
