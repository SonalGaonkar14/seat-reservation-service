#!/usr/bin/env python3

import json
import os
import sys
import time
import urllib.request
import urllib.error
import concurrent.futures
from collections import Counter


BASE = os.environ.get(
    'BASE_URL',
    sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8080'
).rstrip('/')

COUNT = int(os.environ.get('COUNT', '20000'))
HOT = os.environ.get('SEAT', 'A1')
ADMIN = os.environ.get('ADMIN_TOKEN', 'admin-secret')

SHOW_NAME = 'burst-' + str(int(time.time()))


def request(method, path, body=None, token=None):
    data = None if body is None else json.dumps(body).encode()

    headers = {
        'Content-Type': 'application/json'
    }

    if token:
        headers['Authorization'] = 'Bearer ' + token

    req = urllib.request.Request(
        BASE + path,
        data=data,
        headers=headers,
        method=method
    )

    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, json.loads(response.read() or b'{}')

    except urllib.error.HTTPError as e:
        try:
            payload = json.loads(e.read() or b'{}')
        except Exception:
            payload = {}

        return e.code, payload

    except Exception as e:
        print(f'Request error: {e}')
        return 599, {}


def main():

    print('========================================')
    print('SEAT RESERVATION BURST TEST')
    print('========================================')
    print('BASE URL :', BASE)
    print('HOT SEAT :', HOT)
    print('REQUESTS :', COUNT)
    print('========================================')
    print()

    # ---------------------------------------------------------
    # 1. Create a fresh show
    # ---------------------------------------------------------

    status, payload = request(
        'POST',
        '/shows',
        {
            'name': SHOW_NAME,
            'seats': [
                HOT,
                'A2',
                'A3',
                'A4',
                'A5',
                'A6',
                'A7',
                'A8',
                'A9',
                'A10'
            ],
            'pricePaise': 25000
        },
        ADMIN
    )

    if status != 201:
        print('Show creation failed')
        print('HTTP status:', status)
        print('Response:', payload)
        sys.exit(1)

    show = payload['id']

    print('Show created successfully')
    print('SHOW ID    :', show)
    print('HOT SEAT   :', HOT)
    print('REQUESTS   :', COUNT)
    print()

    # ---------------------------------------------------------
    # 2. Send concurrent reservations
    # ---------------------------------------------------------

    def one(i):

        # Every request uses a valid token-derived user.
        user = f'user-{i}'

        # Every request gets a unique idempotency key.
        key = f'key-{i}'

        status, _ = request(
            'POST',
            f'/shows/{show}/reserve',
            {
                'seats': [HOT],
                'idempotencyKey': key
            },
            user
        )

        return status

    print('Starting concurrent reservations...')
    print()

    with concurrent.futures.ThreadPoolExecutor(
        max_workers=min(1000, COUNT)
    ) as executor:

        results = list(
            executor.map(one, range(COUNT))
        )

    # ---------------------------------------------------------
    # 3. Print HTTP outcome distribution
    # ---------------------------------------------------------

    counter = Counter(results)

    print()
    print('========================================')
    print('HTTP OUTCOMES')
    print('========================================')

    for status, count in sorted(counter.items()):
        print(f'{status}: {count}')

    success = counter.get(201, 0)
    conflicts = counter.get(409, 0)
    errors_5xx = sum(
        count
        for status, count in counter.items()
        if 500 <= status <= 599
    )

    print()
    print('201 Created :', success)
    print('409 Conflict:', conflicts)
    print('5xx Errors  :', errors_5xx)

    # ---------------------------------------------------------
    # 4. Get final show state
    # ---------------------------------------------------------

    print()
    print('========================================')
    print('FINAL SHOW STATE')
    print('========================================')

    status, final = request(
        'GET',
        f'/shows/{show}'
    )

    if status != 200:
        print('Failed to get final show state')
        print('HTTP status:', status)
        print('Response:', final)
        sys.exit(1)

    total = final.get('totalSeats')
    available = final.get('available')
    held = final.get('held')
    confirmed = final.get('confirmed')

    print('Total seats :', total)
    print('Available   :', available)
    print('Held        :', held)
    print('Confirmed   :', confirmed)

    # ---------------------------------------------------------
    # 5. Reconciliation
    # ---------------------------------------------------------

    print()
    print('========================================')
    print('RECONCILIATION')
    print('========================================')

    if (
        total is not None
        and available is not None
        and held is not None
        and confirmed is not None
        and available + held + confirmed == total
    ):
        print('RECONCILIATION OK')
        print(
            f'{available} + {held} + {confirmed} = {total}'
        )
    else:
        print('RECONCILIATION FAILED')

    # ---------------------------------------------------------
    # 6. Correctness checks
    # ---------------------------------------------------------

    print()
    print('========================================')
    print('CORRECTNESS CHECK')
    print('========================================')

    passed = True

    if success == 1:
        print('PASS: Exactly one request received 201 Created')
    else:
        print(
            f'FAIL: Expected exactly 1 successful reservation, got {success}'
        )
        passed = False

    if errors_5xx == 0:
        print('PASS: Zero 5xx errors')
    else:
        print(
            f'FAIL: Found {errors_5xx} 5xx errors'
        )
        passed = False

    if (
        total is not None
        and available is not None
        and held is not None
        and confirmed is not None
        and available + held + confirmed == total
    ):
        print('PASS: Reconciliation invariant holds')
    else:
        print('FAIL: Reconciliation invariant does not hold')
        passed = False

    print()

    if passed:
        print('========================================')
        print('BURST TEST PASSED')
        print('========================================')
    else:
        print('========================================')
        print('BURST TEST FAILED')
        print('========================================')
        sys.exit(1)


if __name__ == '__main__':
    main()
