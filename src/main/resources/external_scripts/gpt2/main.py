import argparse
import json
import time

from transformers import T5Tokenizer, AutoModelForCausalLM

parser = argparse.ArgumentParser(description='gpt2コマンドで使用するGPT-2による文章生成スクリプト')
parser.add_argument(
    "--text",
    help='始まりのテキスト'
)

if __name__ == '__main__':
    args = parser.parse_args()
    from_text = args.text
    print(json.dumps({
        "generated": False,
        "phase": 1,
        "message": "処理開始"
    }))
    before_time = time.time()
    print(json.dumps({
        "generated": False,
        "phase": 2,
        "message": "モデル準備"
    }))
    tokenizer = T5Tokenizer.from_pretrained("rinna/japanese-gpt2-medium")
    model = AutoModelForCausalLM.from_pretrained("rinna/japanese-gpt2-medium")
    print(json.dumps({
        "generated": False,
        "phase": 3,
        "message": "トークン処理中"
    }))
    input = tokenizer.encode(from_text, return_tensors="pt")
    print(json.dumps({
        "generated": False,
        "phase": 4,
        "message": "生成中"
    }))
    output = model.generate(input, do_sample=True, max_length=200, num_return_sequences=1)
    text = tokenizer.batch_decode(output)[0]
    after_time = time.time()
    print(json.dumps({
        "generated": True,
        "phase": 5,
        "text": text,
        "process_time": format(after_time - before_time, '.3f')
    }))
