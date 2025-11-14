import asyncio
from fastmcp import Client

client = Client("http://localhost:8000/mcp")

async def call_name_tool(name: str):
    async with client:
        result = await client.call_tool("greet", {"name": name})
        print(result.d)
        
async def write_file_tool()
asyncio.run(call_name_tool("지호"))