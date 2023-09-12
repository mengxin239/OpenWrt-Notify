import asyncio
import json
import os
import time
from aiohttp import web
import aiohttp

unread_messages=[]
class NotificationServer:
    def __init__(self):
        self.notification_dir = "notifications"
        if not os.path.exists(self.notification_dir):
            os.makedirs(self.notification_dir)
        self.connected_clients = {}
        self.telegram_token = "6479771649:AAFD-wKW0ayd1OvUGdjPaegMiZ7Ofv6djfI"  # 修改为你的
        self.telegram_chat_id = "6201219229"  # 你的chat id
        self.enable_telegram_notify = True

    async def start(self):
        app = web.Application()
        app.router.add_post('/notification', self.handle_http_request)
        app.router.add_get('/ws', self.handle_websocket)
        runner = web.AppRunner(app)
        await runner.setup()
        site = web.TCPSite(runner, '0.0.0.0', 8080)
        await site.start()
        print("Server started on port 8080")
        asyncio.create_task(self.cleanup_notifications())

    async def handle_http_request(self, request):
        data = await request.json()
        notification_text = data.get('text')
        if notification_text:
            self.store_notification(notification_text)
            if self.enable_telegram_notify:
                await self.send_to_telegram(notification_text)
            
            # New: Send notification to all connected WebSocket clients
            for client_ip, ws in self.connected_clients.items():
                unread_messages = self.get_unread_messages(client_ip)
                for message in unread_messages:
                    await ws.send_str(message['text'])
                    self.mark_as_read(client_ip, message['timestamp'])
            return web.Response(status=200)
        else:
            return web.Response(status=400)

    async def handle_websocket(self, request):
        ws = web.WebSocketResponse()
        await ws.prepare(request)
        client_ip = request.remote
        self.connected_clients[client_ip] = ws  # New: Add connected client to the dictionary
        try:
            for message in unread_messages:
                await ws.send_str(json.dumps(message))
                self.mark_as_read(client_ip, message['timestamp'])
            async for msg in ws:
                pass  # handle potential incoming messages or commands from clients
        except Exception as e:
            print(f"WebSocket error: {e}")
        finally:
            if client_ip in self.connected_clients:
                del self.connected_clients[client_ip]  # New: Remove client from dictionary when connection is closed
        return ws

    def store_notification(self, text):
        timestamp = int(time.time())
        notification = {
            "text": text,
            "timestamp": timestamp,
            "read_by": []
        }
        filename = os.path.join(self.notification_dir, f"{timestamp}.json")
        with open(filename, 'w') as file:
            json.dump(notification, file)

    def get_unread_messages(self, client_ip):
        messages = []
        for filename in sorted(os.listdir(self.notification_dir)):
            filepath = os.path.join(self.notification_dir, filename)
            with open(filepath, 'r') as file:
                message = json.load(file)
                if client_ip not in message['read_by']:
                    messages.append(message)
        return messages

    def mark_as_read(self, client_ip, timestamp):
        filepath = os.path.join(self.notification_dir, f"{timestamp}.json")
        with open(filepath, 'r') as file:
            message = json.load(file)
        message['read_by'].append(client_ip)
        with open(filepath, 'w') as file:
            json.dump(message, file)

    async def cleanup_notifications(self):
        while True:
            current_time = time.time()
            for filename in os.listdir(self.notification_dir):
                filepath = os.path.join(self.notification_dir, filename)
                timestamp = int(filename.split('.')[0])
                if current_time - timestamp > 30 * 24 * 60 * 60:  # Older than 30 days
                    os.remove(filepath)
            await asyncio.sleep(24 * 60 * 60)  # Run once every day
    async def send_to_telegram(self, message):
        url = f"https://api.telegram.org/bot{self.telegram_token}/sendMessage"
        data = {
            "chat_id": self.telegram_chat_id,
            "text": message
        }
        async with aiohttp.ClientSession() as session:
            async with session.post(url, data=data) as response:
                return await response.text()

if __name__ == "__main__":
    server = NotificationServer()
    loop = asyncio.get_event_loop()
    loop.run_until_complete(server.start())
    loop.run_forever()