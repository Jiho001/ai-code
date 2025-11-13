import os
import uuid 
import base64

from openai import OpenAI
from langchain.tools import tool
from dotenv import load_dotenv


load_dotenv()
api_key = os.environ["OPENAI_KEY"]
if not api_key:
    raise ValueError("OpenAI API Key가 .env 파일에 설정되지 않았습니다.")
client = OpenAI(api_key=api_key)

OUTPUT_DIR = "auto_web_agent/output"
MODEL = "dall-e-3"
os.makedirs(OUTPUT_DIR, exist_ok=True)


def generate_design_img(prompt: str) -> str:
    """
    랭체인 설명서
    """
    print(f"🤖{MODEL} 실행")
    
    try:
        response = client.images.generate(
            model=MODEL,
            prompt=prompt,
            size="1792x1024",
            response_format="b64_json"
        )
        
        # Base64 인코딩 이미지 추출
        image_base64 = response.data[0].b64_json
        # Base64 디코딩
        image_bytes = base64.b64decode(image_base64)
        
        # 고유 이름으로 output 폴더에 저장
        file_name = f"d3_{uuid.uuid4().hex[:8]}.png"
        save_path = os.path.join(OUTPUT_DIR, file_name)
        
        with open(save_path, "wb") as f:
            f.write(image_bytes)
            
        print(f"✅ 생성된 이미지를 {save_path}에 저장")
        
        return save_path
    
    except Exception as e:
        print(f"{MODEL} 오류 발생")
        return f"{MODEL} 실패: {e}"

if __name__ == "__main__":
    print("----개별 테스트----")
    test_prompt = """
    개인 블로그 웹페이지 디자인을 만들어줘. 피그마로 만든 것처럼 레이아웃에만 집중해.
    레이아웃: 왼쪽 - 내가 쓴 글 목록, 나머지 공간은 내가 쓴 글들이 타일형으로 배치, 블로그 이름은 상단에 배너로 배치.
    색상: 파스텔톤
    스타일: fancy
    """
    result_path = generate_design_img(test_prompt)
    print(f"{result_path}에 저장 완료")