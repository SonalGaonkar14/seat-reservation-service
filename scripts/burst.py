#!/usr/bin/env python3
import json, os, sys, time, urllib.request, urllib.error, concurrent.futures

BASE=os.environ.get('BASE_URL', sys.argv[1] if len(sys.argv)>1 else 'http://localhost:8080').rstrip('/')
COUNT=int(os.environ.get('COUNT','1000'))
HOT=os.environ.get('SEAT','A1')
ADMIN=os.environ.get('ADMIN_TOKEN','admin-secret')
SHOW_NAME='burst-' + str(int(time.time()))

def request(method,path,body=None,token=None):
    data=None if body is None else json.dumps(body).encode()
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=urllib.request.Request(BASE+path,data=data,headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=30) as r:
            return r.status,json.loads(r.read() or b'{}')
    except urllib.error.HTTPError as e:
        try: payload=json.loads(e.read() or b'{}')
        except Exception: payload={}
        return e.code,payload

def main():
    status,payload=request('POST','/shows',{'name':SHOW_NAME,'seats':[HOT,'A2','A3','A4','A5','A6','A7','A8','A9','A10'],'pricePaise':25000},ADMIN)
    if status!=201:
        print('show creation failed',status,payload); sys.exit(1)
    show=payload['id']; print('show=',show,'hot_seat=',HOT,'requests=',COUNT)
    def one(i):
        # Unique user per request; every 100th request repeats an idempotency key for replay testing.
        user='load-user-'+str(i if i%100 else 0)
        key='key-'+str(i if i%100 else 0)
        return request('POST',f'/shows/{show}/reserve',{'seats':[HOT],'idempotencyKey':key},user)[0]
    with concurrent.futures.ThreadPoolExecutor(max_workers=min(1000,COUNT)) as ex:
        results=list(ex.map(one,range(COUNT)))
    from collections import Counter
    c=Counter(results); print('HTTP outcomes:',dict(sorted(c.items())))
    s,final=request('GET',f'/shows/{show}')
    print('reconciliation:',{k:final.get(k) for k in ('totalSeats','available','held','confirmed')},'GET=',s)
    if s==200 and final['available']+final['held']+final['confirmed']==final['totalSeats']:
        print('RECONCILIATION OK')
    else: print('RECONCILIATION FAILED')

if __name__=='__main__': main()
