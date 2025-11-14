import asyncio
from fastmcp import Client

client = Client("http://localhost:8000/mcp")

async def call_name_tool(name: str):
    async with client:
        result = await client.call_tool("greet", {"name": name})
        print(result.data)
        
async def read_file_tool(path: str):
    async with client:
        result = await client.call_tool("read_file", {"path": path})
        print(result.data["b64_data"])
        
asyncio.run(call_name_tool("지호"))
asyncio.run(read_file_tool("gi1_b933e108.png"))