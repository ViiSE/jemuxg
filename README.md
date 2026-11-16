<img src="static/jemuxg_logo2.png" width="256" height="256">

# jemuxg

Эмулятор старых консолей. Реализованы NES и каркас Atari 2600.

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

Проверка без окна (headless):

```bash
java -Djava.awt.headless=true -cp target/classes ru.viise.jemuxg.app.Main --smoke
```

## Управление

| Клавиша | Кнопка |
| --- | --- |
| Стрелки | Крестовина |
| Z / A / Space | A |
| X / S | B |
| Enter | Start |
| Shift / Backspace | Select |
| F12 | Отладочный снимок |

Звук выводится через `javax.sound.sampled`; при отсутствии аудиоустройства
эмулятор продолжает работать без звука.

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
