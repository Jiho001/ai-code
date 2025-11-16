import base64
from fastmcp import Client
from openai import OpenAI
from dotenv import load_dotenv
import os
from datetime import datetime

load_dotenv()
api_key = os.environ["OPENAI_KEY"]
openai_client = OpenAI(api_key=api_key)

MCP_SERVER_URL = "http://localhost:8000/mcp"
OUTPUT_DIR = "auto_web_agent/output/generated_code"
os.makedirs(OUTPUT_DIR, exist_ok=True)

MODEL = "gpt-4.1"

async def request_code(png_path: str):
    """
    1) fastMCP read_file로 PNG base64 로드
    2) OpenAI GPT 모델에 넣어 HTML/CSS/JS 코드 생성
    3) 생성된 코드를 파일로 저장
    4) 저장경로를 반환
    """
    
    mcp_client = Client(MCP_SERVER_URL)
    
    async with mcp_client:
        resp = await mcp_client.call_tool(
            "read_file",
            {"path": png_path}
        )
        
        base64_image = resp.data["b64_data"]
    
    # ------- 코드 생성 ------- #
        prompt = """
        당신은 UI/Frontend 전문가입니다.  
        아래 이미지를 보고, 동일한 웹페이지를 구현하는 HTML/CSS/JS 코드를 작성하세요.

        조건:
        - 구조는 Figma-style 웹 디자인을 그대로 반영
        - 클래스를 체계적으로 작성
        - CSS는 가능한 깔끔하고 모듈화
        - Tailwind가 아니라 순수 CSS 사용
        - 생성된 코드는 하나의 index.html에 포함되도록 작성

        이미지 분석 후 바로 코드만 출력하세요.
        """
        
        response = openai_client.responses.create(
            model=MODEL,
            input=[
                {
                    "role": "user",
                    "content":[
                        {"type": "input_text", "text": prompt},
                        {
                            "type": "input_image", 
                            "image_url": f"data:image/png;base64,{base64_image}",
                        }
                    ]
                }
            ]
        )
    
        code = response.output_text
    
        # ------ 코드 저장 --------- #
        file_name = f"code_{datetime.now().strftime('%m%d_%H%M%S')}.html"
        save_path = os.path.join(OUTPUT_DIR, file_name)
    
        await mcp_client.call_tool(
            "write_text_file",
            {"path": save_path, "text": code}
        )

    return save_path