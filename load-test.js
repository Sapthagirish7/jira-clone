import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import encoding from 'k6/encoding';

// ─────────────────────────────────────────────────────────
// k6 Load Test: 100 concurrent board viewers + issue mutations
//
// Run with:
//   k6 run load-test.js --env BASE_URL=http://localhost:8080
//
// Demonstrates:
// - 100 concurrent users each holding a WebSocket connection to the board
// - Simultaneous REST mutations (create issue, transition status)
// - Optimistic lock conflict scenario
// ─────────────────────────────────────────────────────────

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PROJECT_ID = __ENV.PROJECT_ID || 'replace-with-real-project-id';

const boardLoadTime = new Trend('board_load_time');
const transitionErrors = new Counter('transition_errors');
const conflictRate = new Rate('conflict_409_rate');

export const options = {
    scenarios: {
        // 100 concurrent board viewers (WebSocket connections)
        board_viewers: {
            executor: 'constant-vus',
            vus: 100,
            duration: '60s',
            exec: 'boardViewer',
        },
        // 20 concurrent users creating and transitioning issues
        issue_mutations: {
            executor: 'constant-vus',
            vus: 20,
            duration: '60s',
            exec: 'issueMutator',
            startTime: '5s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1000'],  // 95th percentile < 500ms
        http_req_failed:   ['rate<0.01'],                  // < 1% errors
        conflict_409_rate: ['rate<0.1'],                   // < 10% optimistic lock conflicts
    },
};

const AUTH_HEADERS = {
    'Content-Type': 'application/json',
    'Authorization': 'Basic ' + encoding.b64encode('admin:admin123'),
};

// Scenario 1: Keep a WebSocket connection open, receive live board updates
export function boardViewer() {
    const url = BASE_URL.replace('http', 'ws') + '/ws/websocket';

    const res = ws.connect(url, {}, function (socket) {
        socket.on('open', () => {
            // STOMP CONNECT frame
            socket.send('CONNECT\naccept-version:1.2\nhost:localhost\n\n\x00');
        });

        socket.on('message', (data) => {
            if (data.includes('CONNECTED')) {
                // Subscribe to board topic
                socket.send(
                    `SUBSCRIBE\nid:sub-0\ndestination:/topic/projects/${PROJECT_ID}/board\n\n\x00`
                );
            }
        });

        // Hold the connection for the test duration
        socket.setTimeout(() => socket.close(), 55000);
    });

    check(res, { 'WS connected': (r) => r && r.status === 101 });
}

// Scenario 2: Create issue then transition it (REST mutations under load)
export function issueMutator() {
    // GET board — measures board load time
    const boardStart = Date.now();
    const boardRes = http.get(
        `${BASE_URL}/api/v1/projects/${PROJECT_ID}/board`,
        { headers: AUTH_HEADERS }
    );
    boardLoadTime.add(Date.now() - boardStart);
    check(boardRes, { 'board 200': (r) => r.status === 200 });

    // POST new issue
    const createRes = http.post(
        `${BASE_URL}/api/v1/projects/${PROJECT_ID}/issues`,
        JSON.stringify({ issueType: 'TASK', title: `Load test task ${Date.now()}` }),
        { headers: AUTH_HEADERS }
    );
    check(createRes, { 'issue created 201': (r) => r.status === 201 });

    if (createRes.status !== 201) {
        transitionErrors.add(1);
        return;
    }

    const issue = JSON.parse(createRes.body);

    // PATCH update — concurrent with other VUs to trigger optimistic lock conflicts
    const patchRes = http.patch(
        `${BASE_URL}/api/v1/issues/${issue.id}`,
        JSON.stringify({ priority: 'HIGH' }),
        { headers: AUTH_HEADERS }
    );

    if (patchRes.status === 409) {
        conflictRate.add(1);  // optimistic lock conflict — expected under high concurrency
    } else {
        conflictRate.add(0);
        check(patchRes, { 'update 200': (r) => r.status === 200 });
    }

    sleep(1);
}
