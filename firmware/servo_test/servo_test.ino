// 스노우봇 책상 시험용 (제품 펌웨어 아님)
// 보드: XIAO_ESP32S3, USB CDC On Boot: Enabled
// 라이브러리: 없음 (ESP32 내장 LEDC 로 서보 펄스를 직접 만든다)
// 시리얼 모니터 115200. 명령 예: p90  n120  l60  b90  c  h  t
//
// 2026-11-07: ESP32Servo 3.2.1 + esp32 코어 3.3.12 조합에서 서보 4개를 붙이면
// 같은 타이머를 쓰는 짝 중 먼저 붙인 쪽(D0, D3)의 펄스가 끊겼다(자가 진단 t 로 확인).
// 그래서 라이브러리 없이 ledcAttach 로 핀마다 50Hz 펄스를 낸다.

#include "driver/gpio.h"

const int PIN_PAN  = 1;  // D0  고개 좌우 (MG90S)
const int PIN_NOD  = 2;  // D1  끄덕임 (MG90S)
const int PIN_LID  = 4;  // D3  눈꺼풀 (작은 서보)
const int PIN_BROW = 6;  // D5  눈썹 (작은 서보)
const int PINS[] = {PIN_PAN, PIN_NOD, PIN_LID, PIN_BROW};

const int FREQ = 50;               // 50Hz = 20ms 마다 펄스 하나
const int RES_BITS = 14;           // 20ms 를 16384 칸으로 나눔
const int US_MIN = 500, US_MAX = 2400;   // 0도·180도 펄스 길이(마이크로초)

uint32_t dutyFor(int angle) {
  long us = US_MIN + (long)(US_MAX - US_MIN) * angle / 180;
  return (uint32_t)(us * (1L << RES_BITS) / 20000);
}

void setAngle(int pin, int angle) {
  if (angle < 0) angle = 0;
  if (angle > 180) angle = 180;
  ledcWrite(pin, dutyFor(angle));
}

void help() {
  Serial.println("p=좌우 n=끄덕임 l=눈꺼풀 b=눈썹 + 각도(0~180)");
  Serial.println("예: p60  /  c = 모두 90도  /  h = 도움말  /  t = 핀 자가 진단");
}

void selfTest() {          // 각 핀이 실제로 펄스를 내보내는지 200ms 동안 잰다 (90도면 약 7.3%)
  for (int p : PINS) {
    gpio_input_enable((gpio_num_t)p);
    long hi = 0, n = 0, edges = 0; int last = gpio_get_level((gpio_num_t)p);
    unsigned long t0 = micros();
    while (micros() - t0 < 200000) {
      int v = gpio_get_level((gpio_num_t)p);
      hi += v; n++; if (v != last) { edges++; last = v; }
    }
    Serial.printf("GPIO%d high=%.1f%% edges=%ld\n", p, 100.0 * hi / n, edges);
  }
}

void setup() {
  Serial.begin(115200);
  delay(800);
  for (int p : PINS) {
    bool ok = ledcAttach(p, FREQ, RES_BITS);
    setAngle(p, 90);        // 켜지면 모두 가운데(90도)
    Serial.printf("GPIO%d attach=%d\n", p, ok);
  }
  Serial.println("ready");
  help();
}

void loop() {
  if (!Serial.available()) return;
  char which = Serial.read();
  if (which == '\n' || which == '\r' || which == ' ') return;
  if (which == 'h') { help(); return; }
  if (which == 't') { selfTest(); return; }
  if (which == 'c') {
    for (int p : PINS) setAngle(p, 90);
    Serial.println("all 90");
    return;
  }
  int angle = Serial.parseInt();
  if (angle < 0) angle = 0;
  if (angle > 180) angle = 180;
  if (which == 'p') setAngle(PIN_PAN, angle);
  else if (which == 'n') setAngle(PIN_NOD, angle);
  else if (which == 'l') setAngle(PIN_LID, angle);
  else if (which == 'b') setAngle(PIN_BROW, angle);
  else return;
  Serial.print(which);
  Serial.println(angle);
}
