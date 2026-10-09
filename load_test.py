import urllib.request
import urllib.parse
import json
import uuid
import time
import concurrent.futures
import random

BASE_URL = "http://localhost:8080/api/v1"

def request_json(method, url, data=None, params=None):
    if params:
        query = urllib.parse.urlencode(params)
        url = f"{url}?{query}"
    
    headers = {
        'Content-Type': 'application/json'
    }
    req_data = None
    if data:
        req_data = json.dumps(data).encode('utf-8')
    
    req = urllib.request.Request(url, data=req_data, headers=headers, method=method)
    with urllib.request.urlopen(req) as response:
        if response.status not in (200, 201):
            raise Exception(f"HTTP {response.status}")
        body = response.read()
        return json.loads(body) if body else None

def get_items():
    try:
        items = request_json('GET', f"{BASE_URL}/menu/items")
        return [item['id'] for item in items]
    except Exception as e:
        print("Error fetching items:", e)
    return []

items = get_items()
if not items:
    print("No items found. Run skipped.")
    import sys
    sys.exit(1)

def run_workflow(worker_id):
    try:
        table_number = f"T-{random.randint(10000, 99999)}-{worker_id}"
        table = request_json('POST', f"{BASE_URL}/tables", params={"tableNumber": table_number})
        table_id = table['id']
        
        item_ids = random.sample(items, min(len(items), 3))
        order_requests = [{"itemId": i, "quantity": random.randint(1, 3)} for i in item_ids]
        
        order = request_json('POST', f"{BASE_URL}/orders/dine-in", params={"tableId": table_id}, data=order_requests)
        order_id = order['id']
        grand_total = order['grandTotal']
        
        if random.random() > 0.5:
            request_json('POST', f"{BASE_URL}/orders/{order_id}/items", data=[{"itemId": items[0], "quantity": 1}])
            
        request_json('POST', f"{BASE_URL}/orders/{order_id}/pay", params={"method": "CARD", "amount": grand_total})
        return True
    except Exception as e:
        return False

print(f"Loaded {len(items)} items. Starting Load Test...")
start_time = time.time()
successful = 0
failed = 0
TOTAL_REQUESTS = 500

with concurrent.futures.ThreadPoolExecutor(max_workers=50) as executor:
    futures = [executor.submit(run_workflow, i) for i in range(TOTAL_REQUESTS)]
    for future in concurrent.futures.as_completed(futures):
        if future.result():
            successful += 1
        else:
            failed += 1

end_time = time.time()
print(f"Total time: {end_time - start_time:.2f}s")
print(f"Successful Orders: {successful}")
print(f"Failed Orders: {failed}")
print(f"Throughput: {TOTAL_REQUESTS / (end_time - start_time):.2f} orders/sec")
