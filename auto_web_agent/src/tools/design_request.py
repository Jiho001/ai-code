import base64
from datetime import datetime
from fastmcp import Client
from openai import OpenAI
from dotenv import load_dotenv
import os


load_dotenv()
api_key = os.environ["OPENAI_KEY"]
openai_client = OpenAI(api_key=api_key)

OUTPUT_DIR = "auto_web_agent/output"
MODEL = "gpt-image-1"
os.makedirs(OUTPUT_DIR, exist_ok=True)

MCP_SERVER_URL = "http://localhost:8000/mcp"

async def request_design_img(user_req: str):
    """
    사용자 요구사항(user_req)을 기반으로 gpt-image-1 모델에 웹 디자인 이미지를 생성시키고,
    fastMCP Client를 이용해 MCP 서버의 write_file로 저장한 후
    저장된 파일 경로를 문자열로 반환합니다.
    """
    
    # ---------- OpenAI API 호출 ----------- #
    prompt = f"""
        Create a web page design prototype in a Figma-style layout.  
        Focus strictly on UI layout and structure.
        No people, no mockups, no devices, no 3D, no illustrations, no realistic elements. 
        Just a clean flat web design.
        
        User Requirements:
        {user_req}
        """
    
    response = openai_client.images.generate(
        model=MODEL,
        prompt=prompt,
        size="1536x1024",
        n=1
    )
    
    img_b64 = response.data[0].b64_json
    img_bytes = base64.b64encode(img_b64)
    
    # ------------ FastMCP Client로 write_file 호출 ----------- #
    mcp_client = Client(MCP_SERVER_URL)
    
    file_name = f"design_{datetime.now().strftime('%m%d_%H%M%S')}.png"
    save_path = f"designs/{file_name}"  # auto_web_agent/output/designs/design_...
    
    async with mcp_client:
        await mcp_client.call_tool(
            "write_file", 
            {
                "path": save_path,
                "b64_data": img_bytes
            }
        )

    return save_path
