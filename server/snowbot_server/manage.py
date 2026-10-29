"""관리 명령.

  python -m snowbot_server.manage add-device [--prefix SNW-01]   새 기기 등록 (키·등록 코드는 이때 한 번만 보임)
  python -m snowbot_server.manage list-devices
DB 경로는 SNOWBOT_DB (기본 relay.db).
"""
import argparse
import os

from .store import Store


def main() -> None:
    parser = argparse.ArgumentParser(prog="snowbot_server.manage")
    sub = parser.add_subparsers(dest="cmd", required=True)
    add = sub.add_parser("add-device")
    add.add_argument("--prefix", default="SNW-01")
    sub.add_parser("list-devices")
    args = parser.parse_args()

    store = Store(os.environ.get("SNOWBOT_DB", "relay.db"))
    if args.cmd == "add-device":
        device_id, key, claim = store.add_device(args.prefix)
        print(f"device_id  : {device_id}")
        print(f"device_key : {key}    <- 펌웨어에 넣는다 (다시 볼 수 없음)")
        print(f"claim_code : {claim}    <- QR 스티커에 넣는다 (다시 볼 수 없음)")
    else:
        for device_id in store.list_devices():
            print(device_id)


if __name__ == "__main__":
    main()
