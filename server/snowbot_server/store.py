"""기기·앱·짝(pair) 저장소 (SQLite).

비밀값(기기 키·등록 코드·앱 토큰)은 SHA-256 해시로만 저장한다. 모두 무작위 값이라 소금(salt) 없이 충분하다.
"""
import hashlib
import hmac
import secrets
import sqlite3
import time

SCHEMA = """
create table if not exists devices(
  device_id text primary key, key_hash text not null, claim_hash text not null, created integer not null);
create table if not exists apps(
  app_id text primary key, token_hash text not null unique, kind text not null, name text not null,
  created integer not null);
create table if not exists pairs(
  app_id text not null, device_id text not null, created integer not null, primary key(app_id, device_id));
"""


def _hash(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


class Store:
    def __init__(self, path: str):
        self.db = sqlite3.connect(path, check_same_thread=False)
        self.db.executescript(SCHEMA)

    def add_device(self, prefix: str = "SNW-01") -> tuple[str, str, str]:
        """새 기기 등록. (device_id, 기기 키 — 펌웨어에, 등록 코드 — QR 스티커에) 를 한 번만 돌려준다."""
        while True:
            device_id = f"{prefix}-{secrets.token_hex(2).upper()}"
            if not self.db.execute("select 1 from devices where device_id=?", (device_id,)).fetchone():
                break
        key = secrets.token_urlsafe(24)
        claim = secrets.token_hex(5).upper()
        with self.db:
            self.db.execute("insert into devices values(?,?,?,?)",
                            (device_id, _hash(key), _hash(claim), int(time.time())))
        return device_id, key, claim

    def list_devices(self) -> list[str]:
        return [r[0] for r in self.db.execute("select device_id from devices order by created")]

    def check_device(self, device_id: str, key: str) -> bool:
        row = self.db.execute("select key_hash from devices where device_id=?", (device_id,)).fetchone()
        return bool(row) and hmac.compare_digest(row[0], _hash(key))

    def app_by_token(self, token: str) -> str | None:
        row = self.db.execute("select app_id from apps where token_hash=?", (_hash(token),)).fetchone()
        return row[0] if row else None

    def pair(self, device_id: str, claim: str, kind: str, name: str,
             app_id: str | None = None) -> tuple[str, str | None] | None:
        """등록 코드가 맞으면 앱과 기기를 잇는다.

        app_id 가 없으면 새 앱을 만들고 토큰을 돌려준다. 이미 있는 앱이면 토큰은 None(앱이 이미 갖고 있음).
        """
        row = self.db.execute("select claim_hash from devices where device_id=?", (device_id,)).fetchone()
        if not row or not hmac.compare_digest(row[0], _hash(claim.upper())):
            return None
        token = None
        now = int(time.time())
        with self.db:
            if app_id is None:
                app_id = "app-" + secrets.token_hex(6)
                token = secrets.token_urlsafe(32)
                self.db.execute("insert into apps values(?,?,?,?,?)", (app_id, _hash(token), kind, name, now))
            self.db.execute("insert or ignore into pairs values(?,?,?)", (app_id, device_id, now))
        return app_id, token

    def devices_of(self, app_id: str) -> list[str]:
        return [r[0] for r in self.db.execute("select device_id from pairs where app_id=? order by rowid", (app_id,))]

    def apps_of(self, device_id: str) -> list[str]:
        return [r[0] for r in self.db.execute("select app_id from pairs where device_id=?", (device_id,))]
