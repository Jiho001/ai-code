import asyncio
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain_mcp_adapters.tools import load_mcp_tools
from langchain_core.runnables import RunnableLambda

from design_request import request_design_img
from code_request import request_code
from dotenv import load_dotenv

async def main():
    load_dotenv()
    # MCP Client 연결
    client = MultiServerMCPClient({
        "file_server": {
            "url": "http://localhost:8000/mcp",
            "transport": "streamable_http"
        }
    })

    # MCP → LangChain Tool 변환 
    async with client.session("file_server") as session:
        tools = await load_mcp_tools(session)

    # LangChain 노드 구성
    user_node = RunnableLambda(lambda x: x)
    design_node = RunnableLambda(request_design_img)
    code_node = RunnableLambda(request_code)


    chain = user_node | design_node | code_node

    # 실행
    result = await chain.ainvoke("개인 블로그 웹페이지를 만들어줘. 색상은 파스텔톤으로 깔끔하게 만들어.")
    print("Generated code saved:", result)


if __name__ == "__main__":
    asyncio.run(main())
