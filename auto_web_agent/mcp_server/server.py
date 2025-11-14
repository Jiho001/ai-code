from fastmcp import FastMCP
import os
import base64

CURRENT_DIR = os.path.dirname(__file__)
PROJECT_ROOT = os.path.dirname(os.path.dirname(CURRENT_DIR))  # .../auto_web_agent
BASE_DIR = os.path.join(PROJECT_ROOT, "output")  # .../auto_web_agent/output

mcp = FastMCP("file-tools-server")

@mcp.tool
def write_file(path: str, b64_data: str):
    """
    base64 인코딩된 바이너리를 받아 파일로 저장
    """
    abs_path = os.path.join(BASE_DIR, path)
    os.makedirs(os.paht.dirname(abs_path), exist_ok=True)
    
    with open(abs_path, "wb") as f:
        f.write(base64.b64decode(b64_data))
        
    return {"status": "ok", "save_path": abs_path}

@mcp.tool
def read_file(path: str):
    """
    path 경로의 파일을 base64로 읽어서 반환
    """
    abs_path = os.path.join(BASE_DIR, path)
    
    with open(abs_path, "rb") as f:
        b64 = base64.b64encode(f.read()).decode("utf-8")
    
    return {"status": "ok", "b64_data": b64}

if __name__ == "__main__":
    mcp.run(transport="http", port=8000)
    
    