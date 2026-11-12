#include <Wire.h>
#include <SPI.h>
#include <DHT.h>
#include <BH1750.h>
#include <LittleFS.h>
#include <MFRC522.h>
#include <math.h>

// =====================================================
// EXACT PHYSICAL HARDWARE PIN CONFIGURATION
// =====================================================

// DHT11
#define DHTPIN 4
#define DHTTYPE DHT11
DHT dht(DHTPIN, DHTTYPE);

// I2C
#define SDA_PIN 21
#define SCL_PIN 22

// MPU6500
#define MPU6500_ADDR 0x68

// BH1750
BH1750 lightMeter;

// LittleFS
#define LOG_FILE "/journey.csv"
#define CONFIG_FILE "/config.json"

// Reed Switch
#define REED_PIN 27

// RC522 RFID (SPI)
#define SS_PIN 5
#define RST_PIN 15
#define SPI_SCK 18
#define SPI_MISO 19
#define SPI_MOSI 23
MFRC522 rfid(SS_PIN, RST_PIN);

// Dynamic Parameters
float shockThreshold = 2.0; // g-force default threshold
unsigned long lastSensorLog = 0;
const unsigned long LOG_INTERVAL = 20000; // 20 seconds

// =====================================================
// CRC32 CHECKSUM HELPER FUNCTION
// =====================================================
uint32_t calculateFileCRC32(const char *path) {
  File file = LittleFS.open(path, "r");
  if (!file) return 0;

  uint32_t crc = 0xFFFFFFFF;
  while (file.available()) {
    uint8_t b = file.read();
    crc ^= b;
    for (int i = 0; i < 8; i++) {
      uint32_t mask = -(crc & 1);
      crc = (crc >> 1) ^ (0xEDB88320 & mask);
    }
  }
  file.close();
  return ~crc;
}

// =====================================================
// MPU6500 ACCELEROMETER READING
// =====================================================
void readMPU6500(float &x, float &y, float &z) {
  Wire.beginTransmission(MPU6500_ADDR);
  Wire.write(0x3B);
  Wire.endTransmission(false);
  Wire.requestFrom((uint8_t)MPU6500_ADDR, (size_t)6);

  int16_t rawX = Wire.read() << 8 | Wire.read();
  int16_t rawY = Wire.read() << 8 | Wire.read();
  int16_t rawZ = Wire.read() << 8 | Wire.read();

  // Scale factor for ±2g mode
  x = rawX / 16384.0;
  y = rawY / 16384.0;
  z = rawZ / 16384.0;
}

// =====================================================
// SAVE SENSOR DATA TO LITTLEFS FLASH
// =====================================================
void saveData(float temp, float hum, float x, float y, float z, float accel, float light, String reedStatus, String event) {
  File file = LittleFS.open(LOG_FILE, "a");
  if (!file) {
    Serial.println("Failed to open journey.csv");
    return;
  }

  unsigned long timeSec = millis() / 1000;
  file.print(timeSec); file.print(",");
  file.print(temp, 2); file.print(",");
  file.print(hum, 2); file.print(",");
  file.print(x, 2); file.print(",");
  file.print(y, 2); file.print(",");
  file.print(z, 2); file.print(",");
  file.print(accel, 2); file.print(",");
  file.print(light, 2); file.print(",");
  file.print(reedStatus); file.print(",");
  file.println(event);

  file.close();
  Serial.println("Data saved to LittleFS Flash");
}

// =====================================================
// READ STORED JOURNEY DATA + COMPUTE CRC32 HASH
// =====================================================
void readStoredData() {
  File file = LittleFS.open(LOG_FILE, "r");
  if (!file) {
    Serial.println("Failed to open journey.csv");
    return;
  }

  Serial.println();
  Serial.println("========== STORED JOURNEY DATA ==========");
  while (file.available()) {
    Serial.write(file.read());
  }
  file.close();
  Serial.println("==========================================");

  uint32_t crc32Val = calculateFileCRC32(LOG_FILE);
  Serial.print("CRC32 Checksum Hash: 0x");
  Serial.println(crc32Val, HEX);
}

// =====================================================
// RFID / NFC CARD SCAN CHECK
// =====================================================
void checkRFID() {
  if (!rfid.PICC_IsNewCardPresent() || !rfid.PICC_ReadCardSerial()) return;

  Serial.println();
  Serial.println("======================================");
  Serial.println("       RFID CARD DETECTED");
  Serial.println("======================================");

  String uidStr = "";
  for (byte i = 0; i < rfid.uid.size; i++) {
    if (rfid.uid.uidByte[i] < 0x10) uidStr += "0";
    uidStr += String(rfid.uid.uidByte[i], HEX);
    if (i < rfid.uid.size - 1) uidStr += " ";
  }
  uidStr.toUpperCase();

  Serial.print("UID: "); Serial.println(uidStr);
  Serial.print("UID Length: "); Serial.print(rfid.uid.size); Serial.println(" bytes");

  // Save RFID Event to CSV
  File file = LittleFS.open(LOG_FILE, "a");
  if (file) {
    unsigned long timeSec = millis() / 1000;
    file.print(timeSec);
    file.print(",,,,,,,NFC,NFC_TAG_DETECTED_");
    file.println(uidStr);
    file.close();
    Serial.println("NFC event saved to Flash");
  }

  rfid.PICC_HaltA();
  rfid.PCD_StopCrypto1();
  delay(1000);
}

// =====================================================
// SENSOR LOGGING & TELEMETRY
// =====================================================
void readAndSaveSensors() {
  float temp = dht.readTemperature();
  float hum = dht.readHumidity();
  if (isnan(temp) || isnan(hum)) {
    temp = 0.0;
    hum = 0.0;
  }

  float x, y, z;
  readMPU6500(x, y, z);
  float acceleration = sqrt((x * x) + (y * y) + (z * z));
  float light = lightMeter.readLightLevel();

  String reedStatus = (digitalRead(REED_PIN) == LOW) ? "CLOSED" : "OPEN";
  String event = "NORMAL";

  if (acceleration >= shockThreshold && reedStatus == "OPEN") {
    event = "SHOCK_AND_PACKAGE_OPEN";
  } else if (acceleration >= shockThreshold) {
    event = "SHOCK";
  } else if (reedStatus == "OPEN") {
    event = "PACKAGE_OPEN";
  }

  Serial.println();
  Serial.println("========== SENSOR READING ==========");
  Serial.print("Temperature: "); Serial.print(temp); Serial.println(" °C");
  Serial.print("Humidity: "); Serial.print(hum); Serial.println(" %");
  Serial.print("X: "); Serial.print(x); Serial.println(" g");
  Serial.print("Y: "); Serial.print(y); Serial.println(" g");
  Serial.print("Z: "); Serial.print(z); Serial.println(" g");
  Serial.print("Total Acceleration: "); Serial.print(acceleration); Serial.println(" g");
  Serial.print("Light: "); Serial.print(light); Serial.println(" lux");
  Serial.print("Package: "); Serial.println(reedStatus);
  Serial.print("Event: "); Serial.println(event);

  saveData(temp, hum, x, y, z, acceleration, light, reedStatus, event);
}

// =====================================================
// PROCESS SERIAL INCOMING COMMANDS / CONFIG
// =====================================================
void checkSerialCommand() {
  if (Serial.available()) {
    String input = Serial.readStringUntil('\n');
    input.trim();

    if (input.equalsIgnoreCase("R")) {
      readStoredData();
    } else if (input.startsWith("{")) {
      File cfgFile = LittleFS.open(CONFIG_FILE, "w");
      if (cfgFile) {
        cfgFile.print(input);
        cfgFile.close();
      }
      Serial.println("[USB RX @ 115200] ESP32: Config written to LittleFS /NVS memory");
    }
  }
}

// =====================================================
// SETUP
// =====================================================
void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println();
  Serial.println("======================================");
  Serial.println("       CARGO CANARY SYSTEM");
  Serial.println("======================================");

  Wire.begin(SDA_PIN, SCL_PIN);
  Serial.println("I2C started");

  dht.begin();
  Serial.println("DHT11 initialized");

  if (lightMeter.begin(BH1750::CONTINUOUS_HIGH_RES_MODE)) {
    Serial.println("BH1750 detected");
  } else {
    Serial.println("BH1750 NOT detected");
  }

  // MPU6500 Init (Wake up & set +-2g)
  Wire.beginTransmission(MPU6500_ADDR); Wire.write(0x6B); Wire.write(0x00); Wire.endTransmission();
  delay(100);
  Wire.beginTransmission(MPU6500_ADDR); Wire.write(0x1C); Wire.write(0x00); Wire.endTransmission();
  Serial.println("MPU6500 initialized");

  pinMode(REED_PIN, INPUT_PULLUP);
  Serial.println("Reed switch initialized");

  if (!LittleFS.begin(true)) {
    Serial.println("LittleFS mount failed");
  } else {
    Serial.println("LittleFS mounted successfully");

    // Log power reboot marker event
    File file = LittleFS.open(LOG_FILE, "a");
    if (file) {
      unsigned long timeSec = millis() / 1000;
      file.print(timeSec);
      file.println(",0,0,0,0,0,0,0,CLOSED,POWER_REBOOT");
      file.close();
    }
  }

  if (!LittleFS.exists(LOG_FILE)) {
    File file = LittleFS.open(LOG_FILE, "w");
    if (file) {
      file.println("Time,Temperature,Humidity,X,Y,Z,Acceleration,Light,Reed,Event");
      file.close();
      Serial.println("journey.csv created");
    }
  }

  SPI.begin(SPI_SCK, SPI_MISO, SPI_MOSI, SS_PIN);
  rfid.PCD_Init();
  delay(100);

  byte version = rfid.PCD_ReadRegister(rfid.VersionReg);
  Serial.print("RC522 Version: 0x");
  if (version < 0x10) Serial.print("0");
  Serial.println(version, HEX);

  if (version == 0x00 || version == 0xFF) {
    Serial.println("WARNING: RC522 not detected!");
  } else {
    Serial.println("RC522 detected successfully!");
  }

  Serial.println();
  Serial.println("--------------------------------------");
  Serial.println("Cargo Canary is ready");
  Serial.println("--------------------------------------");
}

// =====================================================
// MAIN LOOP
// =====================================================
void loop() {
  checkRFID();
  checkSerialCommand();

  if (millis() - lastSensorLog >= LOG_INTERVAL) {
    lastSensorLog = millis();
    readAndSaveSensors();
  }
}
