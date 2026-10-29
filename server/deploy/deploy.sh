#!/usr/bin/env bash
# 맥에서 실행: 코드를 프로토타입 서버에 올리고 서비스를 다시 시작한다.
#   server/deploy/deploy.sh [ssh별칭]   (기본 snowbot-proto)
# 처음 실행이면 snowbot 사용자·폴더·가상환경·systemd 서비스를 만든다. 여러 번 실행해도 된다.
set -euo pipefail
HOST="${1:-snowbot-proto}"
export COPYFILE_DISABLE=1   # 맥 tar 가 ._ 숨김 파일을 넣지 않게
cd "$(dirname "$0")/.."

ssh "$HOST" 'bash -s' <<'EOF'
set -euo pipefail
id snowbot >/dev/null 2>&1 || useradd --system --home-dir /var/lib/snowbot --shell /sbin/nologin snowbot
install -d -o snowbot -g snowbot -m 700 /var/lib/snowbot
install -d -m 755 /opt/snowbot-relay
EOF
tar czf - snowbot_server requirements.txt deploy/snowbot-relay.service | ssh "$HOST" 'tar xzf - -C /opt/snowbot-relay'

ssh "$HOST" 'bash -s' <<'EOF'
set -euo pipefail
cd /opt/snowbot-relay
[ -x .venv/bin/python ] || python3.11 -m venv .venv
.venv/bin/pip install -q --upgrade pip
.venv/bin/pip install -q -r requirements.txt
install -m 644 deploy/snowbot-relay.service /etc/systemd/system/snowbot-relay.service
systemctl daemon-reload
systemctl enable -q snowbot-relay
systemctl restart snowbot-relay
sleep 2
systemctl is-active snowbot-relay
curl -s http://127.0.0.1:3000/health; echo
EOF
