<img src="static/jemuxg_logo2.png" width="256" height="256">

# jemuxg

Эмулятор старых консолей. Реализованы NES и Atari 2600.

## Сборка

Нужен JDK 25.

```bash
JAVA_HOME=/path/to/temurin-25 mvn clean package
```

## Запуск

Без аргументов открывается окно выбора ROM:

```bash
java -cp target/classes ru.viise.jemuxg.app.Main
```

Можно передать ROM сразу. Консоль определяется по расширению:
`.nes` → NES, `.a26` → Atari 2600.

```bash
java -cp target/classes ru.viise.jemuxg.app.Main game.nes
java -cp target/classes ru.viise.jemuxg.app.Main game.a26
# или
java -cp target/classes ru.viise.jemuxg.app.Main --rom game.nes
```

Для неоднозначных файлов консоль можно указать явно:

```bash
java -cp target/classes ru.viise.jemuxg.app.Main --console atari2600 game.bin
```

Схему картриджа Atari 2600 можно задать явно:

```bash
java -cp target/classes ru.viise.jemuxg.app.Main game.a26 --mapper e0
```

Поддерживаются `auto` (по умолчанию), `2k`, `4k`, `f8`, `f6`, `f4`,
`f8sc`, `f6sc`, `f4sc`, `3f`, `3e`, `e0`, `e7`, `fe`. Автоопределение
идёт по размеру: 2K/4K — фиксированные, 8K — F8, 12K — E7, 16K — F6,
32K — F4. Для 8K имя файла может подсказать `e0`, `fe` или `f8sc`;
16K — `e7`/`f6sc`; 32K — `f4sc`. Схемы 3F/3E неоднозначны и задаются
только явно.

Проверка без окна (headless):

```bash
java -Djava.awt.headless=true -cp target/classes ru.viise.jemuxg.app.Main --smoke
```

## Управление

| Клавиша | Кнопка |
| --- | --- |
| Стрелки | Крестовина / джойстик |
| Z / A / Space | A (на Atari 2600 — fire) |
| X / S | B |
| Enter | Start |
| Shift / Backspace | Select |
| F2 | Atari 2600: Reset |
| F3 | Atari 2600: Select |
| F4 | Atari 2600: Color/B&W |
| F12 | Отладочный снимок |

Звук выводится через `javax.sound.sampled`; при отсутствии аудиоустройства
эмулятор продолжает работать без звука.

Окно открывается в трёхкратном размере кадра консоли: NES — 768×720,
Atari 2600 — 480×576. При изменении размера окна картинка масштабируется
с сохранением пропорций.

## Тесты

Контрактные тесты и тестовые ROM запускаются на фазе `test`:

```bash
JAVA_HOME=/path/to/temurin-25 mvn test
```

`mvn package` также прогоняет их перед сборкой jar. При необходимости весь
набор можно запустить вручную:

```bash
JAVA_HOME=/path/to/temurin-25 mvn -q -B test-compile
java -cp target/classes:target/test-classes \
  ru.viise.jemuxg.console.nes.AllTests
```

Набор включает:

- PPU: рендеринг, mirroring, NMI, sprite 0 hit, save/restore.
- APU: каналы, frame counter, IRQ, DC removal, save/restore.
- Мапперы: NROM, MMC1, CNROM, MMC3, PRG/CHR banking, PRG RAM, IRQ.
- Ввод: `$4016/$4017`, strobe, два порта, сброс.
- nestest: полный golden-log на 8991 инструкцию.
- Blargg PPU и sprite-hit ROM: проверка `framebuffer.idx` по эталонному
  SHA-256. ROM лежат в `src/test/resources/test-roms/`.

## Отладка

F12 сохраняет снимок текущего кадра в каталог `debug/` рядом с рабочим
каталогом запуска:

```text
debug/<имя-ROM>/frame-<номер>/
├── screenshot.png      # скриншот 256×240
├── framebuffer.argb    # ARGB, 4 байта на пиксель, little-endian
├── framebuffer.idx     # индекс палитры NES (0..63), 1 байт на пиксель
├── cpu-ram.bin
├── prg-ram.bin
├── vram.bin
├── palette.bin
├── oam.bin
├── registers.txt       # PC, A, X, Y, SP, P
├── hashes.txt          # SHA-256 для бинарных файлов
└── meta.json           # ROM, кадр, размеры, маппер, формат буферов
```

`framebuffer.idx` удобно сравнивать с эталонным эмулятором: он не зависит
от RGB-палитры и различий в цветовых каналах.

Для Atari 2600 снимок содержит:

```text
debug/<имя-ROM>/frame-<номер>/
├── screenshot.png      # скриншот 160×192
├── framebuffer.argb    # ARGB, 4 байта на пиксель, little-endian
├── framebuffer.idx     # индекс палитры TIA (0..127), 1 байт на пиксель
├── riot-ram.bin        # 128 байт RAM RIOT
├── tia-registers.bin   # 64 регистра TIA (защёлки и текущие позиции)
├── riot-registers.bin  # 32 регистра RIOT
├── mapper-ram.bin      # RAM маппера (пустой для схем без RAM)
├── registers.txt       # CPU, TIA, RIOT, маппер
├── hashes.txt          # SHA-256 для бинарных файлов
└── meta.json           # ROM, кадр, размеры, маппер
```

## nestest

Для проверки CPU можно запустить диагностический ROM nestest и сравнить его
с эталонным логом:

```bash
java -cp target/jemuxg-1.0-SNAPSHOT.jar \
  ru.viise.jemuxg.console.nes.debug.NestestRunner \
  nestest.nes nestest.log
```

Runner стартует с `PC=$C000`, `SP=$FD`, `P=$24`, выполняет по одной инструкции
и сравнивает `PC/A/X/Y/P/SP`, байты опкодов и накопленные циклы.

Чтобы сохранить фактический лог:

```bash
java -cp target/jemuxg-1.0-SNAPSHOT.jar \
  ru.viise.jemuxg.console.nes.debug.NestestRunner \
  nestest.nes nestest.log --write-log nestest.actual.log
```

Формат фактического лога:

```text
# jemuxg nestest CPU log: PC, opcode bytes, A, X, Y, P, SP, cumulative CYC
C000  4C F5 C5  A:00 X:00 Y:00 P:24 SP:FD CYC:7
C5F5  A2 00     A:00 X:00 Y:00 P:24 SP:FD CYC:10
```

При первом расхождении runner печатает номер строки и что ожидалось/получилось,
завершается с кодом 1, а `--write-log` сохраняет пре-состояние проблемной
инструкции. Nestest проверяет только CPU, не PPU, APU или мапперы.

### Авторы:
- @viise
- @knbik
